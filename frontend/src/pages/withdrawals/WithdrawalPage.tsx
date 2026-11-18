import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  ListFilter,
  ListFilters,
  ListView,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { koreanToday } from "../../shared/time";
import { AssignmentDetail } from "../assignments/AssignmentDetail";
import { AssignmentRequestError } from "../assignments/assignments";
import { getCommonCodes } from "../codegroups/codeGroups";
import { ProjectLink } from "../projects/ProjectLink";
import { projectFailure } from "../projects/projects";
import { useWithdrawalTitleHost } from "./WithdrawalWorkspace";
import { WithdrawalRecommendations } from "./WithdrawalRecommendations";
import {
  getCommonWithdrawals,
  getCommonWithdrawalsExcel,
  notifyWithdrawalChanged,
  putCommonWithdrawalExtensionPossibility,
  withdrawalRefreshEvent,
  type WithdrawalFilter,
  type WithdrawalList,
  type WithdrawalRow,
} from "./withdrawals";
import "../projects/projects.css";
import "./withdrawals.css";

type Filter = Omit<WithdrawalFilter, "year">;
type Option = { code: string; codeName: string };
const svg = (d: string, size: number) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={d} />
  </svg>
);
// 시안 아이콘(ic): 반짝임·정보·내려받기·자물쇠
const sparkle = (size: number) =>
  svg("M12 3l1.8 4.6L18 9l-4.2 1.4L12 15l-1.8-4.6L6 9l4.2-1.4zM19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z", size);
const infoIcon = svg("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01", 16);
const downIcon = svg("M12 4v12M6 10l6 6 6-6M4 20h16", 16);
const lockIcon = svg("M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4", 12);
const PERIODS = ["3개월 안", "3개월 뒤", "이미 철수"];
/** 시안 다음 배정 정렬 값: 가배정 2, 추천 있음 1, 그 밖 0. */
const nextRank = (row: WithdrawalRow) =>
  row.nextAssignment.hasTentativeAssignment
    ? 2
    : row.nextAssignment.recommendationCount > 0
      ? 1
      : 0;
const sortValue = (row: WithdrawalRow, key: string): string | number => {
  switch (key) {
    case "remainingDays":
      return row.remainingDays;
    case "extensionCount":
      return row.extensionCount;
    case "nextAssignment":
      return nextRank(row);
    default:
      return String((row as unknown as Record<string, unknown>)[key] ?? "");
  }
};

/** 시안 철수 리스트(UI-05): 연도 줄, 안내 띠 2개, 조건, 공통 목록, 다음 자리 추천 창. */
export function WithdrawalPage({ roles = [] }: { roles?: readonly string[] }) {
  const currentYear = Number(koreanToday().slice(0, 4));
  const [year, setYear] = useState(currentYear);
  // 조건은 고른 값(draft)과 [조회]로 적용한 값(applied)을 나눈다(투입 배정과 같게).
  const [draft, setDraft] = useState<Filter>({});
  const [applied, setApplied] = useState<Filter>({});
  const [data, setData] = useState<WithdrawalList | null>(null);
  const [error, setError] = useState("");
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({});
  const [jobs, setJobs] = useState<Option[]>([]);
  const [extensions, setExtensions] = useState<Option[]>([]);
  const [revision, setRevision] = useState(0);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [detail, setDetail] = useState<string | null>(null);
  const [recommendation, setRecommendation] = useState<{
    row: WithdrawalRow;
    replace: boolean;
  } | null>(null);
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const pending = useRef(new Set<string>());
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const [downloading, setDownloading] = useState(false);
  const presentation = useListPresentation();
  const host = useWithdrawalTitleHost();
  const refresh = () => setRevision((n) => n + 1);
  const toast = (message: string, kind: ToastKind = "ok") => {
    setNoticeKind(kind);
    setNotice(message);
  };
  useEffect(() => {
    const active = () => {
      if (document.visibilityState !== "hidden") refresh();
    };
    window.addEventListener("focus", active);
    window.addEventListener(withdrawalRefreshEvent, active);
    document.addEventListener("visibilitychange", active);
    return () => {
      window.removeEventListener("focus", active);
      window.removeEventListener(withdrawalRefreshEvent, active);
      document.removeEventListener("visibilitychange", active);
    };
  }, []);
  // 다른 조건으로 읽을 때만 목록을 비운다. 같은 조건을 다시 읽을 때(포커스·변경 알림)는 지금 줄을 둔 채 바꾼다(깜빡임 없음).
  const loadedKey = useRef("");
  useEffect(() => {
    const abort = new AbortController();
    const key = JSON.stringify({ year, ...applied });
    const same = loadedKey.current === key;
    if (!same) {
      loadedKey.current = "";
      setData(null);
    }
    setError("");
    getCommonWithdrawals({ year, ...applied }, abort.signal)
      .then((result) => {
        if (abort.signal.aborted) return;
        loadedKey.current = key;
        setData(result);
      })
      .catch((reason) => {
        if (abort.signal.aborted || reason?.name === "AbortError") return;
        // 지금 줄이 있는 채로 다시 읽다 실패하면 줄은 두고 알림만 띄운다.
        if (same) toast(projectFailure(reason), "warn");
        else setError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [year, applied, revision]);
  useEffect(() => {
    let active = true;
    Promise.all([getCommonCodes("JOB"), getCommonCodes("EXT_PSBL")])
      .then(([j, e]) => {
        if (active) {
          setJobs(j.items ?? []);
          setExtensions(e.items ?? []);
        }
      })
      .catch((reason) => {
        if (active) setError(projectFailure(reason));
      });
    return () => {
      active = false;
    };
  }, []);
  const extensionLabel = (code: string) =>
    extensions.find((option) => option.code === code)?.codeName ?? "미정";
  const changeExtension = async (row: WithdrawalRow, code: string) => {
    if (
      pending.current.has(row.assignmentId) ||
      row.extensionPossibilityCode === code
    )
      return;
    pending.current.add(row.assignmentId);
    setBusy((b) => ({ ...b, [row.assignmentId]: true }));
    setRowErrors((errors) => ({ ...errors, [row.assignmentId]: "" }));
    try {
      await putCommonWithdrawalExtensionPossibility(row, code);
      toast(
        `${row.name}님의 연장 가능성을 '${extensionLabel(code)}'으로 바꿨습니다.`,
      );
      notifyWithdrawalChanged();
    } catch (reason) {
      const fields =
        reason instanceof AssignmentRequestError
          ? reason.errors.map((e) => e.message).join(" · ")
          : "";
      setRowErrors((errors) => ({
        ...errors,
        [row.assignmentId]: fields || projectFailure(reason),
      }));
      if (reason instanceof AssignmentRequestError && reason.status === 409)
        refresh();
    } finally {
      pending.current.delete(row.assignmentId);
      setBusy((b) => ({ ...b, [row.assignmentId]: false }));
    }
  };
  // 시안 남은 일수: D-30 이하 bad, D-60 이하 warn, 그 밖 neutral, 지나면 자물쇠 「철수 완료」
  const days = (row: WithdrawalRow) =>
    row.withdrawalCompleted ? (
      <Badge kind="lock">
        {lockIcon}
        철수 완료
      </Badge>
    ) : (
      <Badge
        kind={
          row.remainingDays <= 30
            ? "bad"
            : row.remainingDays <= 60
              ? "warn"
              : "neutral"
        }
      >
        D-{row.remainingDays}
      </Badge>
    );
  const sub = (text = "–") => <span className="project-sub">{text}</span>;
  const next = (row: WithdrawalRow) => {
    const n = row.nextAssignment;
    if (n.hasTentativeAssignment)
      return (
        <span className="withdrawal-next">
          <Badge kind="info">가배정 · {n.opportunityName}</Badge>
          {!row.withdrawalCompleted && (
            <button
              className="project-lnk"
              type="button"
              data-testid={`withdrawal-replace-${row.assignmentId}`}
              onClick={(event) => {
                event.stopPropagation();
                setRecommendation({ row, replace: true });
              }}
            >
              바꾸기
            </button>
          )}
        </span>
      );
    if (row.withdrawalCompleted) return sub();
    const target = n.recommendationTarget ?? true;
    if (!target)
      return (
        <span className="withdrawal-next" data-testid={`withdrawal-next-${row.assignmentId}`}>
          {sub()} <span className="ls-cap">{n.note ?? "3개월 뒤 철수"}</span>
        </span>
      );
    return n.recommendationCount > 0 ? (
      <button
        className="ls-btn ls-btn-sm"
        type="button"
        data-testid={`withdrawal-recommend-${row.assignmentId}`}
        onClick={(event) => {
          event.stopPropagation();
          setRecommendation({ row, replace: false });
        }}
      >
        {sparkle(14)} 추천 {n.recommendationCount}곳
      </button>
    ) : (
      <span className="withdrawal-next" data-testid={`withdrawal-next-${row.assignmentId}`}>
        <span className="project-bad">없음</span>{" "}
        <span className="ls-cap">맞는 자리 없음</span>
      </span>
    );
  };
  // 시안 연장 가능성 칸: 작은 고르기(in-sm), 미정·높음·보통·낮음 순
  const extension = (row: WithdrawalRow) => (
    <div
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
    >
      <select
        aria-label="연장 가능성"
        className="ls-in ls-in-sm"
        data-testid={`withdrawal-extension-${row.assignmentId}`}
        disabled={busy[row.assignmentId]}
        value={row.extensionPossibilityCode}
        onChange={(event) => void changeExtension(row, event.target.value)}
      >
        <option value="">미정</option>
        {extensions.map((option) => (
          <option key={option.code} value={option.code}>
            {option.codeName}
          </option>
        ))}
      </select>
      {rowErrors[row.assignmentId] && (
        <p
          role="alert"
          className="project-bad"
          data-testid={`withdrawal-error-${row.assignmentId}`}
        >
          {rowErrors[row.assignmentId]}
        </p>
      )}
    </div>
  );
  const columns: ListColumn<WithdrawalRow>[] = [
    {
      key: "name",
      label: "이름",
      required: true,
      render: (row) => (
        <span className="withdrawal-pname">
          {row.name}
          {row.managementNumber && <small>{row.managementNumber}</small>}
        </span>
      ),
    },
    {
      key: "projectName",
      label: "프로젝트",
      render: (row) => (
        <ProjectLink
          projectId={row.projectId}
          name={row.projectName}
          testId={`withdrawal-project-${row.assignmentId}`}
        />
      ),
    },
    { key: "jobName", label: "직무" },
    { key: "endDate", label: "철수일" },
    { key: "remainingDays", label: "남은 일수", render: days },
    {
      key: "extensionCount",
      label: "연장·조기 철수",
      render: (row) => (
        <span title={row.earlyWithdrawalHistory.join("\n") || undefined}>
          {row.extensionCount ? <span>연장 {row.extensionCount}회</span> : sub()}
          {row.earlyWithdrawalHistory.length > 0 && (
            <>
              {" "}
              <Badge kind="warn">조기 철수 이력</Badge>
            </>
          )}
        </span>
      ),
    },
    {
      key: "extensionPossibilityCode",
      label: "연장 가능성",
      sortable: false,
      render: (row) => extension(row),
    },
    { key: "nextAssignment", label: "다음 배정", render: next },
  ];
  const rows = useMemo(() => {
    const result = [...(data?.items ?? [])];
    // 시안 기본 정렬: 철수일 빠른 순(서버 순서)
    if (sort) {
      const dir = sort.dir === "asc" ? 1 : -1;
      result.sort((a, b) => {
        const x = sortValue(a, sort.key);
        const y = sortValue(b, sort.key);
        const compared =
          typeof x === "number" && typeof y === "number"
            ? x - y
            : String(x).localeCompare(String(y), "ko");
        return compared * dir || a.assignmentId.localeCompare(b.assignmentId);
      });
    }
    return result;
  }, [data, sort]);
  const reset = () => {
    setDraft({});
    setApplied({});
    setPage(1);
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonWithdrawalsExcel({ year, ...applied });
      toast(`엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`);
    } catch (reason) {
      toast(projectFailure(reason), "warn");
    } finally {
      setDownloading(false);
    }
  };
  const downloadButton = (
    <button
      type="button"
      className="ls-btn"
      data-testid="withdrawal-download"
      disabled={downloading}
      onClick={() => void download()}
    >
      {downIcon} 엑셀 다운로드
    </button>
  );
  const select = (
    key: "period" | "jobCode" | "extensionPossibilityCode",
    label: string,
    options: Option[],
  ) => (
    <ListFilter label={label} htmlFor={`withdrawal-filter-${key}`}>
      <select
        id={`withdrawal-filter-${key}`}
        data-testid={`withdrawal-filter-${key}`}
        className="ls-in"
        value={draft[key] ?? "__all"}
        onChange={(event) => {
          const value = event.target.value;
          setDraft((f) => ({
            ...f,
            [key]: value === "__all" ? undefined : value,
          }));
        }}
      >
        <option value="__all">전체</option>
        {options.map((option) => (
          <option key={option.code} value={option.code}>
            {option.codeName}
          </option>
        ))}
      </select>
    </ListFilter>
  );
  const banner = data?.banner;
  // 시안 nextSummary 띠는 올해만 보인다. 자료가 아직 없거나 0건이어도 띠는 0명으로 보인다.
  const soon = banner?.soonCount ?? 0;
  const unassigned = banner?.unassignedCount ?? 0;
  const matched = banner?.matchedCount ?? 0;
  return (
    <section
      className="screen-body withdrawal-page"
      data-testid="withdrawal-page"
      data-screen-id="UI-05"
    >
      {host ? createPortal(downloadButton, host) : downloadButton}
      <YearBar
        year={year}
        currentYear={currentYear}
        min={1}
        max={currentYear + 1}
        pastReadOnly={false}
        testId="withdrawal-year"
        caption="그 해 철수하는·했던 인력"
        onChange={(value) => {
          setYear(value);
          setPage(1);
        }}
      />
      {year === currentYear && (
        <Banner kind={unassigned > 0 ? "warn" : "info"}>
          {sparkle(16)}
          <span data-testid="withdrawal-next-banner">
            3개월 안 철수 {soon}명 중 연장 가능성이 높지 않고 다음 배정도 없는 사람{" "}
            <strong>{unassigned}명</strong> — 이 중 {matched}명은 맞는 자리가 있습니다. [다음 배정] 칸의{" "}
            <strong>추천</strong>을 눌러 가배정하세요.
          </span>
        </Banner>
      )}
      <Banner kind="info">
        {infoIcon}
        <span data-testid="withdrawal-info-banner">
          {" "}
          {year}년에 철수하는(했던) 인력입니다. [기간] 조건으로 &apos;3개월 안&apos;만 볼 수 있습니다. 연장 가능성을 바꾸면 인력 운영 보드와 수요 예측에 바로 반영됩니다.
        </span>
      </Banner>
      <ListFilters
        testId="withdrawal-filters"
        onSearch={() => {
          setApplied({ ...draft });
          setPage(1);
        }}
        onReset={reset}
        activeCount={
          Object.values(applied).filter((v) => v !== undefined).length
        }
      >
        {select(
          "period",
          "기간",
          PERIODS.map((code) => ({ code, codeName: code })),
        )}
        {select("extensionPossibilityCode", "연장 가능성", [
          ...extensions,
          { code: "", codeName: "미정" },
        ])}
        {select("jobCode", "직무", jobs)}
      </ListFilters>
      <ListView
        screenId="UI-05"
        testId="withdrawal-list"
        columns={columns}
        visibleColumns={columns}
        hiddenColumns={[]}
        onToggleColumn={() => undefined}
        onResetColumns={() => undefined}
        columnSettings={false}
        rows={
          !data && !error
            ? null
            : error && !data
              ? []
              : rows.slice(
                  (page - 1) * presentation.pageSize,
                  page * presentation.pageSize,
                )
        }
        rowKey={(row) => row.assignmentId}
        total={rows.length}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={(size) => {
          presentation.setPageSize(size);
          setPage(1);
        }}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={sort}
        onSort={(value) => {
          setSort(value);
          setPage(1);
        }}
        onRowClick={(row) => setDetail(row.assignmentId)}
        onResetFilters={reset}
        mobileRows={data ? rows : null}
        renderCard={(row) => (
          // 시안 휴대폰 카드: 이름·직무 + D-n / 프로젝트 · 철수일 / 연장 가능성 · 가배정 여부(카드 안에는 단추를 두지 않는다)
          <>
            <div className="ls-r1">
              <span className="withdrawal-pname">
                {row.name}
                <small>{row.jobName}</small>
              </span>
              <Badge kind={row.remainingDays <= 30 ? "bad" : "warn"}>
                D-{row.remainingDays}
              </Badge>
            </div>
            <div className="ls-r2">
              <span>{row.projectName}</span>
              <span>{row.endDate}</span>
            </div>
            <div className="ls-r2">
              <span>연장 {row.extensionPossibilityName || "미정"}</span>
              <span>
                {row.nextAssignment.hasTentativeAssignment
                  ? "가배정 있음"
                  : "다음 배정 없음"}
              </span>
            </div>
          </>
        )}
        error={error && !data ? error : undefined}
        onRetry={refresh}
      />
      {recommendation && (
        <WithdrawalRecommendations
          key={recommendation.row.assignmentId}
          row={recommendation.row}
          replaceCurrent={recommendation.replace}
          onClose={() => setRecommendation(null)}
          onSaved={(message) => {
            toast(message);
            refresh();
          }}
        />
      )}
      {detail && (
        <AssignmentDetail
          key={detail}
          id={detail}
          roles={roles}
          readOnly={false}
          onClose={() => setDetail(null)}
          onChanged={(message, kind) => {
            notifyWithdrawalChanged();
            if (message) toast(message, kind);
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          testId="withdrawal-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

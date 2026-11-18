import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Toast } from "../../shared/feedback/Dialog";
import {
  Banner,
  ListFilter,
  ListFilters,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { koreanToday } from "../../shared/time";
import { AssignmentDetail } from "../assignments/AssignmentDetail";
import { AssignmentRequestError } from "../assignments/assignments";
import { getCommonCodes } from "../codegroups/codeGroups";
import { projectFailure } from "../projects/projects";
import { useProjectTitleHost } from "../projects/ProjectWorkspace";
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

export function WithdrawalPage({ roles = [] }: { roles?: readonly string[] }) {
  const currentYear = Number(koreanToday().slice(0, 4));
  const [year, setYear] = useState(currentYear);
  const [filter, setFilter] = useState<Omit<WithdrawalFilter, "year">>({});
  const [data, setData] = useState<WithdrawalList | null>(null);
  const [error, setError] = useState("");
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({});
  const [jobs, setJobs] = useState<{ code: string; codeName: string }[]>([]);
  const [extensions, setExtensions] = useState<
    { code: string; codeName: string }[]
  >([]);
  const [revision, setRevision] = useState(0);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [detail, setDetail] = useState<string | null>(null);
  const [recommendation, setRecommendation] = useState<{
    id: string;
    replace: boolean;
  } | null>(null);
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const pending = useRef(new Set<string>());
  const [notice, setNotice] = useState("");
  const [downloading, setDownloading] = useState(false);
  const [recommendedOnly, setRecommendedOnly] = useState(false);
  const presentation = useListPresentation();
  const host = useProjectTitleHost();
  const refresh = () => setRevision((n) => n + 1);
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
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    setData(null);
    getCommonWithdrawals({ year, ...filter }, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [year, filter, revision]);
  useEffect(() => {
    let active = true;
    Promise.all([getCommonCodes("JOB"), getCommonCodes("EXT_PSBL")])
      .then(([j, e]) => {
        if (active) {
          setJobs(j.items);
          setExtensions(e.items);
        }
      })
      .catch((reason) => {
        if (active) setError(projectFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [revision]);
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
  const days = (row: WithdrawalRow) => (
    <span
      className={`withdrawal-days withdrawal-${row.withdrawalCompleted ? "complete" : row.remainingDays <= 30 ? "red" : row.remainingDays <= 60 ? "orange" : "gray"}`}
    >
      {row.withdrawalCompleted ? "철수 완료" : `D-${row.remainingDays}`}
    </span>
  );
  const next = (row: WithdrawalRow) =>
    row.withdrawalCompleted ? (
      "–"
    ) : (
      <span
        onClick={(event) => event.stopPropagation()}
        onKeyDown={(event) => event.stopPropagation()}
      >
        {row.nextAssignment.hasTentativeAssignment ? (
          <>
            가배정 · {row.nextAssignment.opportunityName}{" "}
            <button
              className="project-lnk"
              type="button"
              data-testid={`withdrawal-replace-${row.assignmentId}`}
              onClick={() =>
                setRecommendation({ id: row.assignmentId, replace: true })
              }
            >
              바꾸기
            </button>
          </>
        ) : row.nextAssignment.recommendationCount > 0 ? (
          <button
            className="project-lnk"
            type="button"
            data-testid={`withdrawal-recommend-${row.assignmentId}`}
            onClick={() =>
              setRecommendation({ id: row.assignmentId, replace: false })
            }
          >
            추천 {row.nextAssignment.recommendationCount}곳
          </button>
        ) : (
          "없음 · 맞는 자리 없음"
        )}
      </span>
    );
  const extension = (row: WithdrawalRow, suffix = "") => (
    <div
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
    >
      <select
        aria-label={`${row.name} 연장 가능성`}
        className="ls-in"
        data-testid={`withdrawal-extension-${row.assignmentId}${suffix}`}
        disabled={busy[row.assignmentId]}
        value={row.extensionPossibilityCode}
        onChange={(event) => void changeExtension(row, event.target.value)}
      >
        {extensions.map((option) => (
          <option key={option.code} value={option.code}>
            {option.codeName}
          </option>
        ))}
        <option value="">미정</option>
      </select>
      {rowErrors[row.assignmentId] && (
        <p
          role="alert"
          className="withdrawal-red"
          data-testid={`withdrawal-error-${row.assignmentId}${suffix}`}
        >
          {rowErrors[row.assignmentId]}
        </p>
      )}
    </div>
  );
  const columns: ListColumn<WithdrawalRow>[] = [
    { key: "name", label: "이름", required: true },
    { key: "projectName", label: "프로젝트" },
    { key: "jobName", label: "직무" },
    { key: "endDate", label: "철수일" },
    { key: "remainingDays", label: "남은 일수", render: days },
    {
      key: "extensionCount",
      label: "연장·조기 철수",
      render: (row) => (
        <>
          연장 {row.extensionCount}회
          {row.earlyWithdrawalHistory.map((h, i) => (
            <div key={i}>조기 철수 {h}</div>
          ))}
        </>
      ),
    },
    {
      key: "extensionPossibilityCode",
      label: "연장 가능성",
      render: (row) => extension(row),
    },
    {
      key: "nextAssignment",
      label: "다음 배정",
      sortable: false,
      render: next,
    },
  ];
  const cols = useListColumns("UI-05", columns);
  const rows = useMemo(() => {
    const result = [...(data?.items ?? [])].filter(
      (row) => !recommendedOnly || row.nextAssignment.recommendationCount > 0,
    );
    if (sort)
      result.sort((a, b) => {
        const left = a[sort.key as keyof WithdrawalRow],
          right = b[sort.key as keyof WithdrawalRow];
        const compared =
          typeof left === "number" && typeof right === "number"
            ? left - right
            : String(left).localeCompare(String(right), "ko");
        return (
          (sort.dir === "asc" ? compared : -compared) ||
          a.assignmentId.localeCompare(b.assignmentId)
        );
      });
    return result;
  }, [data, sort, recommendedOnly]);
  const reset = () => {
    setFilter({});
    setRecommendedOnly(false);
    setPage(1);
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonWithdrawalsExcel({ year, ...filter });
      setNotice(`엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`);
    } catch (reason) {
      setNotice(projectFailure(reason));
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
      엑셀 다운로드
    </button>
  );
  const select = (
    key: "period" | "jobCode" | "extensionPossibilityCode",
    label: string,
    options: { code: string; codeName: string }[],
  ) => (
    <ListFilter label={label} htmlFor={`withdrawal-filter-${key}`}>
      <select
        id={`withdrawal-filter-${key}`}
        data-testid={`withdrawal-filter-${key}`}
        className="ls-in"
        value={filter[key] ?? "__all"}
        onChange={(event) => {
          const value = event.target.value;
          setFilter((f) => ({
            ...f,
            [key]: value === "__all" ? undefined : value,
          }));
          setPage(1);
          setRecommendedOnly(false);
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
          setRecommendedOnly(false);
        }}
      />
      <ListFilters
        testId="withdrawal-filters"
        onSearch={refresh}
        onReset={reset}
        activeCount={
          Object.values(filter).filter((v) => v !== undefined).length
        }
      >
        {select(
          "period",
          "기간",
          ["3개월 안", "3개월 뒤", "이미 철수"].map((code) => ({
            code,
            codeName: code,
          })),
        )}
        {select("extensionPossibilityCode", "연장 가능성", [
          ...extensions,
          { code: "", codeName: "미정" },
        ])}
        {select("jobCode", "직무", jobs)}
      </ListFilters>
      {data?.banner && (
        <Banner kind="info">
          <span>
            다음 배정 없는 인력 {data.banner.unassignedCount}명 · 맞는 자리 있는
            인력 {data.banner.matchedCount}명
          </span>{" "}
          <button
            type="button"
            className="project-lnk"
            data-testid="withdrawal-banner-recommend"
            disabled={data.banner.matchedCount === 0}
            onClick={() => {
              setFilter((f) => ({ ...f, period: "3개월 안" }));
              setRecommendedOnly(true);
              setPage(1);
            }}
          >
            추천
          </button>
        </Banner>
      )}
      <Banner kind="info">
        연장 가능성 변경은 인력 운영 보드·인력 수요 예측에 즉시 반영됩니다.
      </Banner>
      <ListView
        screenId="UI-05"
        testId="withdrawal-list"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        rows={
          !data && !error
            ? null
            : error
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
          <>
            <div className="ls-r1">
              <strong>
                {row.name} · {row.jobName}
              </strong>
              {days(row)}
            </div>
            <p>
              {row.projectName} · {row.endDate}
            </p>
            <p>
              연장 {row.extensionCount}회 ·{" "}
              {row.earlyWithdrawalHistory.join(" · ")}
            </p>
            {extension(row, "-mobile")}
            {next(row)}
          </>
        )}
        error={error}
        onRetry={refresh}
      />
      {recommendation && (
        <WithdrawalRecommendations
          assignmentId={recommendation.id}
          replaceCurrent={recommendation.replace}
          onClose={() => setRecommendation(null)}
          onSaved={() => {
            setRecommendation(null);
            refresh();
          }}
        />
      )}
      {detail && (
        <AssignmentDetail
          id={detail}
          roles={roles}
          readOnly={false}
          onClose={() => setDetail(null)}
          onChanged={() => notifyWithdrawalChanged()}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          testId="withdrawal-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

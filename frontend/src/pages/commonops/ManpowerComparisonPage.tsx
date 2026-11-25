import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useSavedYear } from "../userpreferences/useSavedYear";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  ListFilter,
  ListFilters,
  ListView,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { koreanToday } from "../../shared/time";
import { ProjectDetail } from "../projects/ProjectDetail";
import { projectDetailIssues } from "../projects/projectDetailIssues";
import { useOpenProjectEdit } from "../projects/projectEdit";
import {
  getCommonProjects,
  projectFailure,
  ProjectRequestError,
  type Project,
} from "../projects/projects";
import {
  getCommonManpowerComparisons,
  getCommonManpowerComparisonsExcel,
  type DifferenceStatus,
  type ManpowerComparisonFilter,
  type ManpowerComparisonResult,
  type ManpowerComparisonRow,
} from "./manpowerComparisons";
import "../projects/projects.css";
import "./manpowerComparisons.css";

const overlaps = (
  project: Pick<Project, "startDate" | "endDate">,
  year: number,
) => project.startDate <= `${year}-12-31` && project.endDate >= `${year}-01-01`;
/** 시안 mmf: 소수 둘째 자리(천 단위 쉼표 없음), 값이 없으면 「–」. */
const mmf = (value: number | null | undefined) =>
  value == null ? "–" : Number(value).toFixed(2);
// 시안 ic('info'): 안내 띠 아이콘
const infoIcon = (
  <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
  </svg>
);
/** 시안 gapBadge: 부족 1.50(빨강) · 초과 0.40(주황) · 맞음(초록, 숫자 없음). */
const gapBadge = (row: ManpowerComparisonRow) =>
  row.differenceStatus === "부족" ? (
    <Badge kind="bad">부족 {mmf(Math.abs(row.differenceMm))}</Badge>
  ) : row.differenceStatus === "초과" ? (
    <Badge kind="warn">초과 {mmf(Math.abs(row.differenceMm))}</Badge>
  ) : (
    <Badge kind="ok">맞음</Badge>
  );
const rowKey = (row: ManpowerComparisonRow) => `${row.projectId}-${row.jobCode}`;
const sortValue = (row: ManpowerComparisonRow, key: string): string | number => {
  switch (key) {
    case "plannedMm":
      return row.plannedMm;
    case "actualMm":
      return row.actualMm;
    case "differenceMm":
      return row.differenceMm ?? -1e9;
    case "jobName":
      return row.jobName;
    default:
      return row.projectName;
  }
};
const tie = (a: ManpowerComparisonRow, b: ManpowerComparisonRow) =>
  a.projectId.localeCompare(b.projectId) || a.jobCode.localeCompare(b.jobCode);

/** 시안 인원 계획 대비 투입(UI-11): 연도 줄, 안내 띠, 조건, 공통 목록(합계 줄), 줄을 누르면 프로젝트 상세 [인원 계획] 탭. */
export function ManpowerComparisonPage() {
  const currentYear = Number(koreanToday().slice(0, 4));
  const [draft, setDraft] = useState<ManpowerComparisonFilter>(() => ({
    year: Math.min(2027, Math.max(2025, currentYear)),
  }));
  const [applied, setApplied] = useState<ManpowerComparisonFilter>(draft);
  // 고른 연도를 기억한다(FR-0105, 2026-11-24 사용자 결정). 다시 열면 저장된 해로 바로 조회한다.
  const [, saveYear] = useSavedYear("UI-11", draft.year, 2025, 2027, (year) => {
    setDraft((previous) => ({ ...previous, year }));
    setApplied((previous) => ({ ...previous, year }));
  });
  const [revision, setRevision] = useState(0);
  const openEdit = useOpenProjectEdit();
  const [data, setData] = useState<ManpowerComparisonResult | null>(null);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [projects, setProjects] = useState<Project[]>([]);
  const [optionError, setOptionError] = useState("");
  const [optionRevision, setOptionRevision] = useState(0);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [detail, setDetail] = useState<{ projectId: string; tab: string } | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const presentation = useListPresentation();
  const id = useId();
  const refresh = () => setRevision((value) => value + 1);
  const toast = (message: string, kind: ToastKind = "ok") => {
    setNoticeKind(kind);
    setNotice(message);
  };

  useEffect(() => {
    const abort = new AbortController();
    setOptionError("");
    getCommonProjects(abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setProjects(result.items);
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setOptionError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [optionRevision]);

  // 다른 조건으로 읽을 때만 목록을 비운다. 같은 조건을 다시 읽을 때(상세 저장·닫기 뒤)는 지금 줄을 둔 채 바꾼다(깜빡임 없음).
  const loadedKey = useRef("");
  useEffect(() => {
    const abort = new AbortController();
    const key = JSON.stringify(applied);
    const same = loadedKey.current === key;
    if (!same) {
      loadedKey.current = "";
      setData(null);
    }
    setError("");
    setFields({});
    getCommonManpowerComparisons(applied, abort.signal)
      .then((result) => {
        if (abort.signal.aborted) return;
        loadedKey.current = key;
        setData(result);
      })
      .catch((reason) => {
        if (abort.signal.aborted || reason?.name === "AbortError") return;
        loadedKey.current = "";
        setData(null);
        setError(projectFailure(reason));
        if (reason instanceof ProjectRequestError)
          setFields(
            Object.fromEntries(
              reason.errors.map((field) => [field.field, field.message]),
            ),
          );
      });
    return () => abort.abort();
  }, [applied, revision]);

  // 시안 기간 줄: 연도를 바꾸면 그 해로 바로 다시 조회한다(다른 연도 화면과 같음). 조회한 조건은 두고,
  // 그 해에 걸치지 않는 프로젝트 선택만 푼다. 아직 [조회]하지 않은 조건은 입력 칸에만 남는다.
  const keepProject = (projectId: string | undefined, year: number) =>
    projectId &&
    projects.some((p) => p.projectId === projectId && overlaps(p, year))
      ? projectId
      : undefined;
  const changeYear = (year: number) => {
    saveYear(year);
    setDraft((previous) => ({
      ...previous,
      year,
      projectId: keepProject(previous.projectId, year),
    }));
    setApplied((previous) => ({
      ...previous,
      year,
      projectId: keepProject(previous.projectId, year),
    }));
    setPage(1);
  };
  const search = () => {
    setApplied({ ...draft });
    setPage(1);
    refresh();
  };
  // 시안 freset: 조건을 처음 값으로 되돌리고 바로 다시 조회한다(연도는 그대로).
  const reset = () => {
    const next = { year: draft.year };
    setDraft(next);
    setApplied(next);
    setPage(1);
    refresh();
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonManpowerComparisonsExcel(applied);
      // 시안 xls: 「엑셀 파일을 만들었습니다 (조회 결과 n건)」, 0건이면 건수 없이
      toast(`엑셀 파일을 만들었습니다${count ? ` (조회 결과 ${count}건)` : ""}`);
    } catch (reason) {
      toast(projectFailure(reason), "warn");
    } finally {
      setDownloading(false);
    }
  };
  const fieldError = (name: string) =>
    fields[name] ? (
      <span
        className="field-error"
        data-testid={`manpower-comparison-${name}-error`}
      >
        {fields[name]}
      </span>
    ) : null;

  const columns: ListColumn<ManpowerComparisonRow>[] = [
    {
      key: "projectName",
      label: "프로젝트",
      required: true,
      // 시안 projLink: 이름을 누르면 프로젝트 상세(기본 정보), 줄을 누르면 [인원 계획] 탭
      render: (row) => (
        <button
          type="button"
          className="link-button"
          data-testid={`manpower-comparison-project-${rowKey(row)}`}
          onClick={(event) => {
            event.stopPropagation();
            setDetail({ projectId: row.projectId, tab: "basic" });
          }}
        >
          {row.projectName}
        </button>
      ),
    },
    { key: "jobName", label: "직무" },
    {
      key: "plannedMm",
      label: "계획 MM",
      align: "right",
      render: (row) => mmf(row.plannedMm),
    },
    {
      key: "actualMm",
      label: "실제·예정 MM",
      align: "right",
      render: (row) => mmf(row.actualMm),
    },
    { key: "differenceMm", label: "차이", align: "right", render: gapBadge },
  ];
  const rows = useMemo(() => {
    const result = [...(data?.items ?? [])];
    if (sort) {
      const dir = sort.dir === "asc" ? 1 : -1;
      result.sort((a, b) => {
        const x = sortValue(a, sort.key);
        const y = sortValue(b, sort.key);
        const compared =
          typeof x === "number" && typeof y === "number"
            ? x - y
            : String(x).localeCompare(String(y), "ko");
        return compared * dir || tie(a, b);
      });
    } else {
      // 시안 sortDef: 차이 오름차순(부족 큰 순), 값 없는 줄은 맨 뒤
      result.sort(
        (a, b) =>
          Number(a.differenceMm == null) - Number(b.differenceMm == null) ||
          (a.differenceMm ?? 0) - (b.differenceMm ?? 0) ||
          tie(a, b),
      );
    }
    return result;
  }, [data, sort]);
  const pages = Math.max(1, Math.ceil(rows.length / presentation.pageSize));
  const current = Math.min(page, pages);

  return (
    <section
      className="screen-body manpower-comparison-page"
      data-testid="manpower-comparison-page"
      data-screen-id="SCR-UI-11"
      data-action-id="M25-183"
    >
      <div data-action-id="M25-184">
        <YearBar
          year={draft.year}
          currentYear={currentYear}
          min={2025}
          max={2027}
          caption="그 해 1~12월 계획 MM과 실제(예정 포함) MM"
          onChange={changeYear}
          testId="manpower-comparison-year"
        />
        {fieldError("year")}
      </div>
      {/* 시안 .banner: 아이콘과 글을 나란히 둔다(철수 리스트와 같은 꼴). 자료가 없어도 늘 보인다. */}
      <div
        className="ls-banner ls-banner-info"
        role="status"
        data-testid="manpower-comparison-banner"
      >
        {infoIcon}
        <span>
          계획 MM은 프로젝트 상세 [인원 계획] 탭에서 직무별로 입력한 값입니다.{" "}
          {applied.year}년 몫 = 계획 MM × ({applied.year}년에 걸친 프로젝트 개월 수 ÷ 전체 개월 수). 줄을 누르면 그
          프로젝트의 인원 계획이 열립니다.
        </span>
      </div>
      <ListFilters
        onSearch={search}
        onReset={reset}
        testId="manpower-comparison-filters"
        activeCount={
          Number(!!applied.projectId) + Number(!!applied.differenceStatus)
        }
      >
        <ListFilter label="프로젝트" htmlFor={`${id}-project`}>
          <select
            id={`${id}-project`}
            className="ls-in"
            data-testid="manpower-comparison-project"
            value={draft.projectId ?? ""}
            onChange={(event) =>
              setDraft({ ...draft, projectId: event.target.value || undefined })
            }
          >
            <option value="">전체</option>
            {projects
              .filter((project) => overlaps(project, draft.year))
              .map((project) => (
                <option key={project.projectId} value={project.projectId}>
                  {project.projectName}
                </option>
              ))}
          </select>
          {fieldError("projectId")}
        </ListFilter>
        <ListFilter label="차이" htmlFor={`${id}-difference`}>
          <select
            id={`${id}-difference`}
            className="ls-in"
            data-testid="manpower-comparison-difference"
            value={draft.differenceStatus ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                differenceStatus: (event.target.value || undefined) as
                  | DifferenceStatus
                  | undefined,
              })
            }
          >
            <option value="">전체</option>
            <option value="부족">부족</option>
            <option value="초과">초과</option>
            <option value="맞음">맞음</option>
          </select>
          {fieldError("differenceStatus")}
        </ListFilter>
      </ListFilters>
      {optionError && (
        <div role="alert" data-testid="manpower-comparison-options-error">
          프로젝트 조건을 불러오지 못했습니다. {optionError}{" "}
          <button
            type="button"
            className="ls-btn"
            data-testid="manpower-comparison-options-retry"
            onClick={() => setOptionRevision((value) => value + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      <ListView
        screenId="UI-11"
        testId="manpower-comparison-list"
        columns={columns}
        visibleColumns={columns}
        hiddenColumns={[]}
        onToggleColumn={() => undefined}
        onResetColumns={() => undefined}
        columnSettings={false}
        onDownload={() => void download()}
        downloading={downloading}
        rows={
          error
            ? []
            : data
              ? rows.slice(
                  (current - 1) * presentation.pageSize,
                  current * presentation.pageSize,
                )
              : null
        }
        rowKey={rowKey}
        total={rows.length}
        page={current}
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
        onRowClick={(row) => setDetail({ projectId: row.projectId, tab: "plan" })}
        onResetFilters={reset}
        // 시안 foot: 계획 MM·실제·예정 MM 합계(지금 조건의 전체 결과). 0건이면 0.00, 읽는 중에는 「–」.
        footer={{
          plannedMm: mmf(data ? (data.totals?.plannedMm ?? 0) : null),
          actualMm: mmf(data ? (data.totals?.actualMm ?? 0) : null),
        }}
        mobileRows={data && !error ? rows : null}
        renderCard={(row) => (
          // 시안 휴대폰 카드: 프로젝트 + 차이 배지 / 직무 · 「계획 x / 실제 y」
          <>
            <div className="ls-r1">
              <strong>{row.projectName}</strong>
              {gapBadge(row)}
            </div>
            <div className="ls-r2">
              <span>{row.jobName}</span>
              <b>
                계획 {mmf(row.plannedMm)} / 실제 {mmf(row.actualMm)}
              </b>
            </div>
          </>
        )}
        error={error || undefined}
        onRetry={refresh}
      />
      {detail && (
        <ProjectDetail
          key={`${detail.projectId}-${detail.tab}`}
          dirtyScreenId="UI-11"
          projectId={detail.projectId}
          initialTab={detail.tab}
          // 프로젝트 화면에서 연 상세와 같은 기능: [이번 주 상태 적기]·[이슈 등록] 등(2026-11-19 사용자 결정).
          boardOperations
          renderIssues={projectDetailIssues(false, "UI-11")}
          onClose={() => {
            setDetail(null);
            refresh();
          }}
          onEdit={(project) => {
            // 시안 editform: 상세를 닫고 프로젝트 화면의 수정 폼을 연다.
            setDetail(null);
            openEdit(project);
          }}
          onChanged={(message, kind) => {
            // 인원 계획을 저장하면 이 목록에 바로 반영한다.
            refresh();
            if (message) toast(message, kind);
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          testId="manpower-comparison-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

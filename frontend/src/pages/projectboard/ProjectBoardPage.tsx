import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  KpiCard,
  KpiCards,
  ListFilter,
  ListFilters,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { daysBetween, koreanToday } from "../../shared/time";
import { getCommonCodes, type CodeItem } from "../codegroups/codeGroups";
import { getCommonParts, type Part } from "../parts/parts";
import { ProjectDetail } from "../projects/ProjectDetail";
import { useOpenProjectEdit } from "../projects/projectEdit";
import { HQ_PART, projectFailure } from "../projects/projects";
import { ProjectIssuesView } from "../projectissues/ProjectIssuesView";
import { projectDetailIssues } from "../projects/projectDetailIssues";
import {
  getCommonProjectBoard,
  getCommonProjectBoardExcel,
  type BoardSignal,
  type ProjectBoardFilter,
  type ProjectBoardResult,
  type ProjectBoardRow,
} from "./projectBoard";
import { useProjectBoardTitleHost } from "./ProjectBoardWorkspace";
import "../projects/projects.css";
import "./projectBoard.css";

/** 시안 LORD: 위험 → 주의 → 정상. */
const LIGHT_ORDER: Record<string, number> = { 위험: 0, 주의: 1, 정상: 2 };
const lightKind = (signal: string) =>
  signal === "위험" ? "bad" : signal === "주의" ? "warn" : "ok";
/** 진척률·원가 사용률은 소수 첫째 자리(Q-식-72). */
const rate = (value: number | null | undefined) =>
  value == null ? "–" : `${Number(value).toFixed(1)}%`;
const gap = (value: number) =>
  `${value >= 0 ? "+" : ""}${Number(value).toFixed(1)}%p`;
const costClass = (value: number | null) =>
  value == null
    ? ""
    : value >= 15
      ? "project-bad"
      : value >= 5
        ? "project-warn-text"
        : "";
const levelClass = (level: string | undefined) =>
  level === "위험" ? "project-bad" : level === "주의" ? "project-warn-text" : "";
/** 계약유형 이름(시안: 내부(자체 개발)은 「내부」). */
const typeName = (types: CodeItem[], code: string) =>
  code === "IN"
    ? "내부"
    : (types.find((type) => type.code === code)?.codeName ?? code);

/** 시안 pbar2: 계획·실적 막대 두 줄. 실적이 계획보다 10%p 이상 낮으면 빨강. */
export function ProgressBars({
  plan,
  actual,
}: {
  plan: number | null;
  actual: number | null;
}) {
  const bar = (label: string, value: number | null, cls: string) => (
    <div className="project-bar">
      <span className="project-cap project-bar-label">{label}</span>
      <div className="project-track">
        <div
          className={`project-fill${cls}`}
          style={{ width: `${Math.min(100, Math.max(0, value ?? 0))}%` }}
        />
      </div>
      <span className="project-cap project-board-bar-value">{rate(value)}</span>
    </div>
  );
  const late = plan != null && actual != null && actual <= plan - 10;
  return (
    <div className="project-bars project-board-bars">
      {bar("계획", plan, " project-fill-plan")}
      {bar("실적", actual, late ? " project-board-fill-bad" : "")}
    </div>
  );
}

const icon = (d: string) => (
  <svg
    width="16"
    height="16"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);
const warnIcon = icon("M12 3l9 16H3zM12 10v4M12 17h.01");
const infoIcon = icon(
  "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01",
);

/** 시안 fxBox('UI-31'): 계산 방법 표. 문구는 시안 그대로. */
const FORMULAS: [string, ReactNode, string][] = [
  [
    "계획 진척률",
    "오늘까지 끝냈어야 할 단계 비중의 합 (진행 중인 단계는 지난 날짜만큼)",
    "단계 비중 — 턴키·내부: 착수 5 · 분석 15 · 설계 20 · 개발 35 · 테스트 15 · 오픈 5 · 종료 5 / 월단위·파견: 착수 5 · 인수인계 10 · 안정화 15 · 정기 운영 65 · 종료 5",
  ],
  [
    "실적 진척률",
    "PM이 주간 메모에 적은 값",
    "월단위·파견은 기간 기준(계획 진척률과 같음)",
  ],
  [
    "원가 사용률",
    "누적 원가 ÷ 예산 × 100 (예산 기본값은 계약금액 × (1 − 목표 마진율), 프로젝트 상세에서 바꿀 수 있음)",
    "턴키만 — 프로젝트 원가 분석의 예산 소진율과 같은 값",
  ],
  [
    "진척 대비 원가",
    "원가 사용률 − 실적 진척률",
    "5%p 이상 주의 · 15%p 이상 위험(원가 초과 위험)",
  ],
  [
    "신호등",
    <>
      위험: 일정 10%p 이상 늦음 · 진척 대비 원가 15%p 이상 · 기한 지난 높음 이슈
      — 하나라도 있으면
      <br />
      주의: 일정 5%p 이상 늦음 · 진척 대비 원가 5%p 이상 · 기한 지난 이슈 · 2주
      넘게 업데이트 없음 — 하나라도 있으면
      <br />
      정상: 그 밖
    </>,
    "시스템이 자동으로 정합니다",
  ],
  ["업데이트 없음", "오늘 − 마지막 주간 메모 날짜 > 14일", ""],
];
function CalculationMethod() {
  return (
    <details
      className="project-board-fx"
      open
      data-testid="project-board-calculation"
    >
      <summary>
        {infoIcon} 계산 방법{" "}
        <span className="project-cap project-board-fx-sub">
          이 화면의 숫자를 이렇게 계산합니다
        </span>
      </summary>
      <div className="ls-twrap">
        <table className="ls-t project-board-fx-table">
          <thead>
            <tr>
              <th className="project-board-fx-name">항목</th>
              <th>계산식</th>
              <th className="project-board-fx-ref">참고</th>
            </tr>
          </thead>
          <tbody>
            {FORMULAS.map(([name, formula, note]) => (
              <tr key={name}>
                <td>{name}</td>
                <td>{formula}</td>
                <td className="project-cap">{note}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="project-note">
        수주예정 프로젝트도 선투입이 있으면 함께 봅니다. 종료된 프로젝트는
        빠집니다.
      </p>
      {/* SFR-131-03(Q-식-91): 위험·주의는 1차 경보이며 원가 분석(UI-04) 상세 확인을 안내한다. */}
      <p className="project-note">
        위험·주의는 1차 경보입니다. 원가가 걱정되면 프로젝트 원가 분석에서 자세히
        확인해 주세요.
      </p>
    </details>
  );
}

type Filter = { signal: string; contractTypeCode: string; salesPartId: string };
const blank: Filter = { signal: "", contractTypeCode: "", salesPartId: "" };
const sortValue = (row: ProjectBoardRow, key: string): string | number => {
  switch (key) {
    case "signal":
      return LIGHT_ORDER[row.signal] ?? 3;
    case "pmName":
      return row.pmName ?? "";
    case "currentStepName":
      return row.currentStepName ?? "";
    case "costUsageRate":
      return row.costUsageRate ?? -1;
    case "openIssueCount":
      return row.openIssueCount;
    case "overdueIssueCount":
      return row.overdueIssueCount;
    case "lastUpdate":
      return row.lastUpdateDate ?? row.latestMemoDate ?? "";
    default:
      return row.projectName;
  }
};
const defaultOrder = (a: ProjectBoardRow, b: ProjectBoardRow) =>
  (LIGHT_ORDER[a.signal] ?? 3) - (LIGHT_ORDER[b.signal] ?? 3) ||
  a.projectName.localeCompare(b.projectName, "ko");

/** SCR-UI-31 프로젝트 현황판. 공유 /screens/:scrnId 라우팅을 사용한다. */
export function ProjectBoardPage() {
  const [tab, setTab] = useState<"board" | "issues">("board");
  const [issuesOpened, setIssuesOpened] = useState(false);
  const [openCount, setOpenCount] = useState<number | null>(null);
  const [draft, setDraft] = useState<Filter>(blank);
  const [applied, setApplied] = useState<Filter>(blank);
  const [data, setData] = useState<ProjectBoardResult | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [optionRevision, setOptionRevision] = useState(0);
  const [options, setOptions] = useState<{ types: CodeItem[]; parts: Part[] }>({
    types: [],
    parts: [],
  });
  const [optionError, setOptionError] = useState("");
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [detail, setDetail] = useState<{
    projectId: string;
    tab: string;
  } | null>(null);
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const presentation = useListPresentation();
  const openEdit = useOpenProjectEdit();
  const host = useProjectBoardTitleHost();
  const refresh = () => setRevision((value) => value + 1);
  const toast = (message?: string, kind: ToastKind = "ok") => {
    if (!message) return;
    setNoticeKind(kind);
    setNotice(message);
  };

  // 시안처럼 한 번에 전부 불러오고(요약 5칸은 조건과 상관없이 전체) 조건은 화면에서 거른다.
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    getCommonProjectBoard({}, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (abort.signal.aborted) return;
        setData(null);
        setError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [revision]);
  useEffect(() => {
    const abort = new AbortController();
    setOptionError("");
    Promise.all([
      getCommonCodes("CTRT_TYPE", abort.signal),
      getCommonParts(abort.signal),
    ])
      .then(([types, parts]) => {
        if (!abort.signal.aborted)
          setOptions({ types: types.items ?? [], parts: parts.items ?? [] });
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setOptionError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [optionRevision]);

  const today = koreanToday();
  const items = data?.items ?? [];
  const filtered = useMemo(() => {
    const rows = items.filter(
      (row) =>
        (!applied.signal || row.signal === applied.signal) &&
        (!applied.contractTypeCode ||
          row.contractTypeCode === applied.contractTypeCode) &&
        (!applied.salesPartId || row.salesPartId === applied.salesPartId),
    );
    rows.sort((a, b) => {
      if (!sort) return defaultOrder(a, b);
      const x = sortValue(a, sort.key);
      const y = sortValue(b, sort.key);
      const order =
        typeof x === "number" && typeof y === "number"
          ? x - y
          : String(x).localeCompare(String(y), "ko");
      return (sort.dir === "asc" ? order : -order) || defaultOrder(a, b);
    });
    return rows;
  }, [items, applied, sort]);

  const reset = () => {
    setDraft(blank);
    setApplied(blank);
    setPage(1);
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const filter: ProjectBoardFilter = {
        signal: (applied.signal || undefined) as BoardSignal | undefined,
        contractTypeCode: applied.contractTypeCode || undefined,
        salesPartId: applied.salesPartId || undefined,
      };
      await getCommonProjectBoardExcel(filter);
      // 시안 xls: 「엑셀 파일을 만들었습니다 (조회 결과 n건)」, 0건이면 건수 없이
      toast(
        `엑셀 파일을 만들었습니다${filtered.length ? ` (조회 결과 ${filtered.length}건)` : ""}`,
      );
    } catch (reason) {
      toast(projectFailure(reason), "warn");
    } finally {
      setDownloading(false);
    }
  };
  const open = (projectId: string, detailTab = "progress") =>
    setDetail({ projectId, tab: detailTab });

  const columns: ListColumn<ProjectBoardRow>[] = [
    {
      key: "signal",
      label: "신호등",
      render: (row) => (
        <span
          tabIndex={0}
          title={row.signalReasons.join(" / ") || "계획대로 진행"}
          aria-label={`${row.signal}: ${row.signalReasons.join(" / ") || "계획대로 진행"}`}
          data-testid={`project-board-signal-${row.projectId}`}
        >
          <Badge kind={lightKind(row.signal)}>{row.signal}</Badge>
        </span>
      ),
    },
    {
      key: "projectName",
      label: "프로젝트",
      required: true,
      render: (row) => (
        <span className="project-board-pname">
          {row.projectName}
          <small>
            {row.projectId} · {typeName(options.types, row.contractTypeCode)}
          </small>
          {row.signalReasons.length > 0 && (
            <small
              className={`project-board-reasons ${levelClass(row.signalReasonLevels?.[0])}`}
              data-testid={`project-board-reasons-${row.projectId}`}
            >
              {row.signalReasons.join(" · ")}
            </small>
          )}
        </span>
      ),
    },
    {
      key: "pmName",
      label: "PM",
      render: (row) => (
        <span title={row.pmUserId ?? undefined}>{row.pmName ?? "–"}</span>
      ),
    },
    {
      key: "currentStepName",
      label: "현재 단계",
      render: (row) =>
        row.currentStepName ? (
          <>
            {row.currentStepName}{" "}
            {row.stepDelayed && <Badge kind="bad">지연</Badge>}
          </>
        ) : (
          "–"
        ),
    },
    {
      key: "progress",
      label: "진척률 (계획 / 실적)",
      sortable: false,
      render: (row) => (
        <ProgressBars plan={row.plannedProgress} actual={row.actualProgress} />
      ),
    },
    {
      key: "costUsageRate",
      label: "원가 사용률",
      align: "right",
      render: (row) =>
        row.costUsageRate == null ? (
          <span
            className="project-sub"
            title={
              row.contractTypeCode === "TK"
                ? "예산이 없어 계산하지 않습니다"
                : "턴키만 비교합니다"
            }
          >
            –
          </span>
        ) : (
          <>
            <span className={costClass(row.costProgressGap)}>
              {rate(row.costUsageRate)}
            </span>
            {row.costProgressGap != null && (
              <>
                <br />
                <small className="project-sub">
                  진척 대비 {gap(row.costProgressGap)}
                </small>
              </>
            )}
          </>
        ),
    },
    { key: "openIssueCount", label: "열린 이슈", align: "right" },
    {
      key: "overdueIssueCount",
      label: "기한 지난 이슈",
      align: "right",
      render: (row) =>
        row.overdueIssueCount ? (
          <span className="project-bad">{row.overdueIssueCount}</span>
        ) : (
          "0"
        ),
    },
    {
      key: "lastUpdate",
      label: "마지막 업데이트",
      render: (row) => {
        const date = row.lastUpdateDate ?? row.latestMemoDate;
        if (!date) return "–";
        const days = row.daysWithoutMemo ?? daysBetween(date, today);
        return (
          <span data-testid={`project-board-update-${row.projectId}`}>
            {date}{" "}
            {row.stale ? (
              <Badge kind="warn">{days}일 전</Badge>
            ) : (
              <span className="project-sub">{days}일 전</span>
            )}
          </span>
        );
      },
    },
  ];
  const cols = useListColumns("UI-31", columns);
  const current = Math.min(
    page,
    Math.max(1, Math.ceil(filtered.length / presentation.pageSize)),
  );
  const summary = data?.summary;
  const count = (value: number | undefined) =>
    value == null ? "–" : `${value}건`;
  const parts: [string, string][] = [
    ...(options.parts ?? [])
      .filter((part) => part.useYn !== "N" && part.partId !== HQ_PART)
      .map((part): [string, string] => [part.partId, part.partName]),
    [HQ_PART, "본사(직접 영업)"],
  ];
  const stale = data?.staleProjects ?? [];
  const issueCount = openCount ?? summary?.openIssues.total;

  return (
    <section
      className="screen-body project-board-page"
      data-testid="project-board-page"
      data-screen-id="SCR-UI-31"
    >
      <KpiCards aria-label="프로젝트 현황 요약" className="project-board-kpis">
        <KpiCard
          testId="project-board-kpi-total"
          label="진행 프로젝트"
          value={count(summary?.progress.total)}
          description={
            summary
              ? `정상 ${summary.progress.normal} · 주의 ${summary.progress.caution} · 위험 ${summary.progress.danger}`
              : ""
          }
        />
        <KpiCard
          testId="project-board-kpi-risk"
          label="위험"
          value={
            <span className={summary?.riskCount ? "project-bad" : ""}>
              {count(summary?.riskCount)}
            </span>
          }
          description="일정·원가·중요 이슈 기준"
        />
        <KpiCard
          testId="project-board-kpi-open"
          label="열린 이슈"
          value={count(summary?.openIssues.total)}
          description={summary ? `높음 ${summary.openIssues.high}건` : ""}
        />
        <KpiCard
          testId="project-board-kpi-overdue"
          label="기한 지난 이슈"
          value={
            <span className={summary?.overdueIssueCount ? "project-bad" : ""}>
              {count(summary?.overdueIssueCount)}
            </span>
          }
          description={
            summary
              ? summary.overdueIssueCount
                ? "담당자 확인 필요"
                : "없음"
              : ""
          }
        />
        <KpiCard
          testId="project-board-kpi-stale"
          label="업데이트 없음"
          value={count(summary?.staleProjectCount)}
          description="2주 넘게 상태를 적지 않음"
        />
      </KpiCards>
      <div className="project-board-tabs" role="tablist" aria-label="현황판 보기">
        <button
          type="button"
          className={`project-board-tab${tab === "board" ? " on" : ""}`}
          role="tab"
          aria-selected={tab === "board"}
          aria-controls="project-board-panel"
          data-testid="project-board-tab"
          onClick={() => setTab("board")}
        >
          현황판
        </button>
        <button
          type="button"
          className={`project-board-tab${tab === "issues" ? " on" : ""}`}
          role="tab"
          aria-selected={tab === "issues"}
          aria-controls="project-board-issues"
          data-testid="project-board-issues-tab"
          onClick={() => {
            setIssuesOpened(true);
            setTab("issues");
          }}
        >
          이슈 모아 보기 ({issueCount ?? "–"})
        </button>
      </div>
      <div
        id="project-board-panel"
        className="project-board-panel"
        role="tabpanel"
        aria-label="현황판"
        hidden={tab !== "board"}
        data-testid="project-board-panel"
      >
        {stale.length > 0 && (
          <div data-testid="project-board-stale-banner">
            <Banner kind="warn" icon={warnIcon}>
              2주 넘게 상태를 적지 않은 프로젝트:{" "}
              {stale.map((project, index) => (
                <span
                  key={project.projectId}
                  data-testid={`project-board-stale-${project.projectId}`}
                >
                  {index > 0 && ", "}
                  <button
                    type="button"
                    className="project-lnk"
                    onClick={() => open(project.projectId)}
                  >
                    {project.projectName}
                  </button>{" "}
                  ({project.daysWithoutMemo ?? "–"}일)
                </span>
              ))}
            </Banner>
          </div>
        )}
        <ListFilters
          testId="project-board-filters"
          activeCount={Object.values(applied).filter(Boolean).length}
          onSearch={() => {
            setApplied({ ...draft });
            setPage(1);
          }}
          onReset={reset}
        >
          <ListFilter label="신호등" htmlFor="project-board-signal">
            <select
              className="ls-in"
              id="project-board-signal"
              data-testid="project-board-signal"
              value={draft.signal}
              onChange={(event) =>
                setDraft({ ...draft, signal: event.target.value })
              }
            >
              <option value="">전체</option>
              {["위험", "주의", "정상"].map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </ListFilter>
          <ListFilter label="계약유형" htmlFor="project-board-contract-type">
            <select
              className="ls-in"
              id="project-board-contract-type"
              data-testid="project-board-contract-type"
              value={draft.contractTypeCode}
              onChange={(event) =>
                setDraft({ ...draft, contractTypeCode: event.target.value })
              }
            >
              <option value="">전체</option>
              {(options.types ?? []).map((type) => (
                <option key={type.code} value={type.code}>
                  {typeName(options.types, type.code)}
                </option>
              ))}
            </select>
          </ListFilter>
          <ListFilter label="영업 파트" htmlFor="project-board-sales-part">
            <select
              className="ls-in"
              id="project-board-sales-part"
              data-testid="project-board-sales-part"
              value={draft.salesPartId}
              onChange={(event) =>
                setDraft({ ...draft, salesPartId: event.target.value })
              }
            >
              <option value="">전체</option>
              {parts.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </ListFilter>
        </ListFilters>
        {optionError && (
          <div role="alert" className="project-board-option-error">
            {optionError}{" "}
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="project-board-options-retry"
              onClick={() => setOptionRevision((value) => value + 1)}
            >
              다시 시도
            </button>
          </div>
        )}
        <ListView
          screenId="UI-31"
          testId="project-board-list"
          columns={columns}
          visibleColumns={cols.visible}
          hiddenColumns={cols.hidden}
          onToggleColumn={cols.toggle}
          onResetColumns={cols.reset}
          rows={
            error
              ? []
              : data
                ? filtered.slice(
                    (current - 1) * presentation.pageSize,
                    current * presentation.pageSize,
                  )
                : null
          }
          rowKey={(row) => row.projectId}
          total={filtered.length}
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
          onSort={(next) => {
            setSort(next);
            setPage(1);
          }}
          onDownload={() => void download()}
          downloading={downloading}
          error={error || undefined}
          onRetry={refresh}
          onResetFilters={reset}
          onRowClick={(row) => open(row.projectId)}
          mobileRows={error ? [] : data ? filtered : null}
          renderCard={(row) => (
            <>
              <div className="ls-r1">
                <strong>{row.projectName}</strong>
                <Badge kind={lightKind(row.signal)}>{row.signal}</Badge>
              </div>
              <div className="ls-r2">
                <span>
                  {row.currentStepName ?? "–"} · 계획{" "}
                  {rate(row.plannedProgress)} / 실적 {rate(row.actualProgress)}
                </span>
                <span>
                  이슈 {row.openIssueCount}
                  {row.overdueIssueCount
                    ? ` (지남 ${row.overdueIssueCount})`
                    : ""}
                </span>
              </div>
              {row.signalReasons.length > 0 && (
                <div className="ls-r2">
                  <span className="project-cap">{row.signalReasons[0]}</span>
                </div>
              )}
            </>
          )}
        />
        <CalculationMethod />
      </div>
      <div
        id="project-board-issues"
        className="project-board-panel"
        role="tabpanel"
        aria-label="이슈 모아 보기"
        hidden={tab !== "issues"}
        data-testid="project-board-issues-panel"
      >
        {issuesOpened && (
          <ProjectIssuesView
            revision={revision}
            createHost={tab === "issues" ? host : null}
            onOpenCount={setOpenCount}
            onChanged={(message) => {
              refresh();
              toast(message);
            }}
          />
        )}
      </div>
      {detail && (
        <ProjectDetail
          key={`${detail.projectId}-${detail.tab}`}
          projectId={detail.projectId}
          initialTab={detail.tab}
          boardOperations
          dirtyScreenId="UI-31"
          renderIssues={projectDetailIssues(false, "UI-31")}
          onClose={() => {
            setDetail(null);
            refresh();
          }}
          onEdit={(project) => {
            setDetail(null);
            openEdit(project);
          }}
          onChanged={(message, kind) => {
            refresh();
            toast(message, kind ?? "ok");
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          onDismiss={() => setNotice("")}
          testId="project-board-toast"
        />
      )}
    </section>
  );
}

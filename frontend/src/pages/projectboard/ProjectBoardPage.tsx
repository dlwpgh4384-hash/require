import { useEffect, useState } from "react";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  ListFilter,
  ListFilters,
  ListView,
  useListPresentation,
  type ListColumn,
} from "../../shared/list";
import { getCommonCodes, type CodeItem } from "../codegroups/codeGroups";
import { getCommonParts, type Part } from "../parts/parts";
import { ProjectDetail } from "../projects/ProjectDetail";
import { useOpenProjectEdit } from "../projects/projectEdit";
import { projectFailure, ProjectRequestError } from "../projects/projects";
import { ProjectIssuesView } from "../projectissues/ProjectIssuesView";
import { ProjectIssuesPanel } from "../projectissues/ProjectIssuesPanel";
import {
  getCommonProjectBoard,
  getCommonProjectBoardExcel,
  type BoardSignal,
  type ProjectBoardFilter,
  type ProjectBoardResult,
  type ProjectBoardRow,
} from "./projectBoard";
import "../projects/projects.css";
import "./projectBoard.css";

const percent = (value: number | null) =>
  value == null ? "–" : `${Number(value).toFixed(1)}%`;
function ProgressBar({
  label,
  value,
}: {
  label: string;
  value: number | null;
}) {
  return (
    <div className="project-board-progress">
      <span>
        {label} {percent(value)}
      </span>
      {value != null && <progress aria-label={label} value={value} max={100} />}
    </div>
  );
}

/** SCR-UI-31: M005/G003의 본문. 공유 /screens/:scrnId 라우팅을 사용한다. */
export function ProjectBoardPage() {
  const [tab, setTab] = useState<"board" | "issues">("board");
  const [issuesOpened, setIssuesOpened] = useState(false);
  const [openCount, setOpenCount] = useState<number | null>(null);
  const [draft, setDraft] = useState<ProjectBoardFilter>({});
  const [applied, setApplied] = useState<ProjectBoardFilter>({});
  const [data, setData] = useState<ProjectBoardResult | null>(null);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [revision, setRevision] = useState(0);
  const [optionRevision, setOptionRevision] = useState(0);
  const [options, setOptions] = useState<{ types: CodeItem[]; parts: Part[] }>({
    types: [],
    parts: [],
  });
  const [optionError, setOptionError] = useState("");
  const [page, setPage] = useState(1);
  const [downloading, setDownloading] = useState(false);
  const [detail, setDetail] = useState<{
    projectId: string;
    tab: string;
  } | null>(null);
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const presentation = useListPresentation();
  const openEdit = useOpenProjectEdit();
  const refresh = () => setRevision((value) => value + 1);

  useEffect(() => {
    const abort = new AbortController();
    setError("");
    setFields({});
    setData(null);
    getCommonProjectBoard(applied, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (abort.signal.aborted) return;
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

  const reset = () => {
    setDraft({});
    setApplied({});
    setPage(1);
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      await getCommonProjectBoardExcel(applied);
      setNoticeKind("ok");
      setNotice("엑셀 파일을 만들었습니다.");
    } catch (reason) {
      setNoticeKind("warn");
      setNotice(projectFailure(reason));
    } finally {
      setDownloading(false);
    }
  };
  const columns: ListColumn<ProjectBoardRow>[] = [
    {
      key: "signal",
      label: "신호등",
      render: (row) => (
        <span
          tabIndex={0}
          title={row.signalReasons.join(" · ") || row.reasonSummary}
          aria-label={`${row.signal}: ${row.reasonSummary}`}
        >
          <Badge
            kind={
              row.signal === "위험"
                ? "bad"
                : row.signal === "주의"
                  ? "warn"
                  : "ok"
            }
          >
            {row.signal}
          </Badge>
        </span>
      ),
    },
    {
      key: "projectName",
      label: "프로젝트",
      render: (row) => (
        <div className="project-board-project">
          <strong>{row.projectName}</strong>
          <div className="project-cap">
            {row.projectId} ·{" "}
            {options.types.find((type) => type.code === row.contractTypeCode)
              ?.codeName ?? row.contractTypeCode}
          </div>
          <div className="project-cap project-wrap">{row.reasonSummary}</div>
        </div>
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
      render: (row) => (
        <>
          {row.currentStepName ?? "–"}
          {row.stepDelayed && <Badge kind="warn">지연</Badge>}
        </>
      ),
    },
    {
      key: "plannedProgress",
      label: "진척률",
      render: (row) => (
        <>
          <ProgressBar label="계획" value={row.plannedProgress} />
          <ProgressBar label="실적" value={row.actualProgress} />
        </>
      ),
    },
    {
      key: "costUsageRate",
      label: "원가 사용률",
      render: (row) => (
        <>
          {percent(row.costUsageRate)}
          <div className="project-cap">
            {row.costProgressGap == null
              ? "–"
              : `진척 대비 ${Number(row.costProgressGap).toFixed(1)}%p`}
          </div>
        </>
      ),
    },
    { key: "openIssueCount", label: "열린 이슈", align: "right" },
    { key: "overdueIssueCount", label: "기한 지난 이슈", align: "right" },
    {
      key: "latestMemoDate",
      label: "마지막 업데이트",
      render: (row) => (
        <span className={row.stale ? "project-board-overdue" : ""}>
          {row.latestMemoDate ?? "작성한 메모 없음"}
          {row.stale && <div>{row.daysWithoutMemo}일 초과 경고</div>}
        </span>
      ),
    },
  ];
  columns.forEach((column) => {
    column.sortable = false;
  });
  const current = Math.min(
    page,
    Math.max(1, Math.ceil((data?.items.length ?? 0) / presentation.pageSize)),
  );
  const summary = data?.summary;
  const fieldError = (name: string) =>
    fields[name] ? <span className="field-error">{fields[name]}</span> : null;

  return (
    <section
      className="screen-body project-board-page"
      data-testid="project-board-page"
      data-screen-id="SCR-UI-31"
    >
      <div className="project-board-summary" aria-label="프로젝트 현황 요약">
        <div>
          <span>진행 프로젝트</span>
          <strong>{summary?.progress.total ?? "–"}</strong>
          <small>
            정상 {summary?.progress.normal ?? "–"} · 주의{" "}
            {summary?.progress.caution ?? "–"} · 위험{" "}
            {summary?.progress.danger ?? "–"}
          </small>
        </div>
        <div>
          <span>위험</span>
          <strong>{summary?.riskCount ?? "–"}</strong>
        </div>
        <div>
          <span>열린 이슈</span>
          <strong>{summary?.openIssues.total ?? "–"}</strong>
          <small>높음 {summary?.openIssues.high ?? "–"}</small>
        </div>
        <div>
          <span>기한 지난 이슈</span>
          <strong>{summary?.overdueIssueCount ?? "–"}</strong>
        </div>
        <div>
          <span>업데이트 없음</span>
          <strong>{summary?.staleProjectCount ?? "–"}</strong>
        </div>
      </div>
      {!!data?.staleProjects.length && (
        <div
          className="ls-banner ls-banner-info"
          data-testid="project-board-stale-banner"
        >
          2주 넘게 업데이트 없음:{" "}
          {data.staleProjects.map((project) => (
            <span
              key={project.projectId}
              data-testid={`project-board-stale-${project.projectId}`}
            >
              {project.projectName} · {project.daysWithoutMemo}일{" "}
            </span>
          ))}
        </div>
      )}
      <div
        className="project-board-tabs"
        role="tablist"
        aria-label="현황판 보기"
      >
        <button
          type="button"
          className="ls-btn"
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
          className="ls-btn"
          role="tab"
          aria-selected={tab === "issues"}
          aria-controls="project-board-issues"
          data-testid="project-board-issues-tab"
          onClick={() => {
            setIssuesOpened(true);
            setTab("issues");
          }}
        >
          이슈 모아 보기 ({openCount ?? summary?.openIssues.total ?? "–"})
        </button>
      </div>
      <div
        id="project-board-panel"
        role="tabpanel"
        aria-label="현황판"
        hidden={tab !== "board"}
        data-testid="project-board-panel"
      >
        <ListFilters
          testId="project-board-filters"
          onSearch={() => {
            setApplied({ ...draft });
            setPage(1);
            refresh();
          }}
          onReset={reset}
        >
          <ListFilter label="신호등" htmlFor="project-board-signal">
            <select
              className="ls-in"
              id="project-board-signal"
              data-testid="project-board-signal"
              value={draft.signal ?? ""}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  signal: (event.target.value || undefined) as
                    | BoardSignal
                    | undefined,
                })
              }
            >
              <option value="">전체</option>
              <option value="위험">위험</option>
              <option value="주의">주의</option>
              <option value="정상">정상</option>
            </select>
            {fieldError("signal")}
          </ListFilter>
          <ListFilter label="계약유형" htmlFor="project-board-contract-type">
            <select
              className="ls-in"
              id="project-board-contract-type"
              data-testid="project-board-contract-type"
              value={draft.contractTypeCode ?? ""}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  contractTypeCode: event.target.value || undefined,
                })
              }
            >
              <option value="">전체</option>
              {options.types.map((type) => (
                <option key={type.code} value={type.code}>
                  {type.codeName}
                </option>
              ))}
            </select>
            {fieldError("contractTypeCode")}
          </ListFilter>
          <ListFilter label="영업 파트" htmlFor="project-board-sales-part">
            <select
              className="ls-in"
              id="project-board-sales-part"
              data-testid="project-board-sales-part"
              value={draft.salesPartId ?? ""}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  salesPartId: event.target.value || undefined,
                })
              }
            >
              <option value="">전체</option>
              {options.parts.map((part) => (
                <option key={part.partId} value={part.partId}>
                  {part.partName}
                </option>
              ))}
            </select>
            {fieldError("salesPartId")}
          </ListFilter>
        </ListFilters>
        {optionError && (
          <div role="alert">
            {optionError}{" "}
            <button
              type="button"
              className="ls-btn"
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
          visibleColumns={columns}
          hiddenColumns={[]}
          columnSettings={false}
          onToggleColumn={() => undefined}
          onResetColumns={() => undefined}
          rows={
            error
              ? []
              : (data?.items.slice(
                  (current - 1) * presentation.pageSize,
                  current * presentation.pageSize,
                ) ?? null)
          }
          rowKey={(row) => row.projectId}
          total={data?.items.length ?? 0}
          page={current}
          pageSize={presentation.pageSize}
          onPage={setPage}
          onPageSize={(size) => {
            presentation.setPageSize(size);
            setPage(1);
          }}
          dense={presentation.dense}
          onDense={presentation.setDense}
          sort={null}
          onSort={() => undefined}
          onDownload={() => void download()}
          downloading={downloading}
          error={error || undefined}
          onRetry={refresh}
          onResetFilters={reset}
          onRowClick={(row) =>
            setDetail({ projectId: row.projectId, tab: "progress" })
          }
          mobileRows={data?.items ?? null}
          renderCard={(row) => (
            <>
              <div className="ls-r1">
                <strong>{row.projectName}</strong>
                <Badge
                  kind={
                    row.signal === "위험"
                      ? "bad"
                      : row.signal === "주의"
                        ? "warn"
                        : "ok"
                  }
                >
                  {row.signal}
                </Badge>
              </div>
              <div className="project-wrap">{row.reasonSummary}</div>
              <div>
                계획 {percent(row.plannedProgress)} / 실적{" "}
                {percent(row.actualProgress)} · 원가{" "}
                {percent(row.costUsageRate)}
              </div>
            </>
          )}
        />
        {data && (
          <p
            className="project-note project-pre"
            data-testid="project-board-calculation"
          >
            {data.calculationMethod}
          </p>
        )}
      </div>
      <div
        id="project-board-issues"
        role="tabpanel"
        aria-label="이슈 모아 보기"
        hidden={tab !== "issues"}
        data-testid="project-board-issues-panel"
      >
        {issuesOpened && (
          <ProjectIssuesView
            revision={revision}
            onOpenCount={setOpenCount}
            onChanged={(message) => {
              refresh();
              if (message) {
                setNotice(message);
                setNoticeKind("ok");
              }
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
          renderIssues={(project, changed) => (
            <ProjectIssuesPanel project={project} onChanged={changed} />
          )}
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
            if (message) {
              setNotice(message);
              setNoticeKind(kind ?? "ok");
            }
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

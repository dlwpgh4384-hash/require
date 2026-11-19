import { useEffect, useMemo, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Toast } from "../../shared/feedback/Dialog";
import {
  Badge,
  ListFilter,
  ListFilters,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { formatDate } from "../../shared/time";
import { getCommonCodes, type CodeItem } from "../codegroups/codeGroups";
import { getCommonProjects } from "../projects/projects";
import {
  ProjectIssueDialog,
  projectIssueCanCreate,
  type ProjectIssueOption,
} from "./ProjectIssueDialog";
import * as api from "./projectIssues";
import "./projectIssues.css";

/** 시안 sevBadge·istBadge 색. */
export const importanceKind = (code: string) =>
  code === "HIGH" ? "bad" : code === "MID" ? "warn" : "neutral";
export const issueStatusKind = (code: string) =>
  code === "OPEN" ? "info" : code === "ACT" ? "warn" : "lock";
const IMPORTANCE_ORDER: Record<string, number> = { HIGH: 0, MID: 1, LOW: 2 };
const overdue = (row: api.ProjectIssue) =>
  row.statusCode !== "CLOSE" && row.overdueDays > 0;
const plusIcon = (
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
    <path d="M12 5v14M5 12h14" />
  </svg>
);

/** 시안 상태 조건: 열림·조치 중(기본) / 기한 지남 / 종료, 그리고 전체. */
type StatusChoice = "open" | "overdue" | "closed" | "all";
const statusQuery = (choice: StatusChoice): api.ProjectIssueFilter =>
  choice === "overdue"
    ? { overdue: true }
    : choice === "closed"
      ? { statusCode: "CLOSE" }
      : choice === "all"
        ? { all: true }
        : {};
type Filter = { status: StatusChoice; importanceCode: string; projectId: string };
const initialFilter: Filter = { status: "open", importanceCode: "", projectId: "" };

/**
 * SCR-UI-31 이슈. project를 주면 프로젝트 상세 [이슈] 탭(시안 pmTabs: 안내 글·[이슈 등록]·간단한 표, 종료 이슈는 맨 뒤),
 * 주지 않으면 현황판 [이슈 모아 보기] 목록(조건·열 설정·엑셀). 이슈 줄을 누르면 수정 창이 열린다.
 * createHost를 주면 [이슈 등록]을 그 자리(제목줄)에 그리고, null이면 그리지 않는다. 주지 않으면 목록 위에 그린다.
 */
export function ProjectIssuesPanel({
  project,
  onOpenProject,
  onChanged,
  onOpenCount,
  revision = 0,
  dirtyScreenId = "UI-31",
  createHost,
}: {
  project?: ProjectIssueOption;
  onOpenProject?: (projectId: string) => void;
  onChanged?: (message?: string) => void;
  onOpenCount?: (count: number) => void;
  revision?: number;
  dirtyScreenId?: string;
  createHost?: HTMLElement | null;
}) {
  const [draft, setDraft] = useState<Filter>(initialFilter);
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [reload, setReload] = useState(0);
  const [optionRevision, setOptionRevision] = useState(0);
  const [data, setData] = useState<api.ProjectIssueList | null>(null);
  const [error, setError] = useState("");
  const [options, setOptions] = useState<{
    projects: ProjectIssueOption[];
    importance: CodeItem[];
    statuses: CodeItem[];
  } | null>(null);
  const [optionError, setOptionError] = useState("");
  const [editing, setEditing] = useState<api.ProjectIssue | "new" | null>(null);
  const [notice, setNotice] = useState("");
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [downloading, setDownloading] = useState(false);
  const presentation = useListPresentation();
  const projectId = project?.projectId;
  const query = useMemo<api.ProjectIssueFilter>(
    () =>
      projectId
        ? { projectId, all: true }
        : {
            ...statusQuery(filter.status),
            ...(filter.importanceCode
              ? { importanceCode: filter.importanceCode }
              : {}),
            ...(filter.projectId ? { projectId: filter.projectId } : {}),
          },
    [filter, projectId],
  );
  useEffect(() => {
    const abort = new AbortController();
    setOptionError("");
    Promise.all([
      projectId
        ? Promise.resolve({ items: [] as ProjectIssueOption[] })
        : getCommonProjects(abort.signal),
      getCommonCodes("IMPR", abort.signal),
      getCommonCodes("ISSUE_STAT", abort.signal),
    ])
      .then(([projects, importance, statuses]) => {
        if (!abort.signal.aborted)
          setOptions({
            projects: projects.items ?? [],
            importance: importance.items ?? [],
            statuses: statuses.items ?? [],
          });
      })
      .catch((reason) => {
        if (!abort.signal.aborted)
          setOptionError(api.projectIssueFailure(reason));
      });
    return () => abort.abort();
  }, [optionRevision, projectId]);
  useEffect(() => {
    const abort = new AbortController();
    setData(null);
    setError("");
    api
      .getCommonProjectBoardIssues(query, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) {
          setData({ items: result.items ?? [], openCount: result.openCount });
          if (!projectId) onOpenCount?.(result.openCount);
        }
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setError(api.projectIssueFailure(reason));
      });
    return () => abort.abort();
  }, [query, reload, revision, onOpenCount, projectId]);
  const saved = (_issue: api.ProjectIssue, created: boolean) => {
    // 시안 toast: 새 이슈 「이슈를 등록했습니다.」, 고친 이슈 「이슈를 저장했습니다.」
    const message = created ? "이슈를 등록했습니다." : "이슈를 저장했습니다.";
    setEditing(null);
    setReload((v) => v + 1);
    if (onChanged) onChanged(message);
    else setNotice(message);
  };
  const label = (items: CodeItem[] | undefined, code: string) =>
    items?.find((c) => c.code === code)?.codeName ?? code;
  const importanceBadge = (row: api.ProjectIssue) => (
    <Badge kind={importanceKind(row.importanceCode)}>
      {label(options?.importance, row.importanceCode)}
    </Badge>
  );
  const statusBadge = (row: api.ProjectIssue) => (
    <Badge kind={issueStatusKind(row.statusCode)}>
      {label(options?.statuses, row.statusCode)}
    </Badge>
  );
  const dueText = (row: api.ProjectIssue) =>
    overdue(row) ? (
      <span
        className="project-bad"
        data-testid={`project-issue-due-${row.issueId}`}
      >
        {row.dueDate} ({row.overdueDays}일 지남)
      </span>
    ) : (
      <span data-testid={`project-issue-due-${row.issueId}`}>
        {row.dueDate}
      </span>
    );
  const canCreate =
    !!options &&
    (project
      ? projectIssueCanCreate(project)
      : options.projects.some(projectIssueCanCreate));
  const createButton = (small: boolean) => (
    <button
      type="button"
      className={`ls-btn ls-btn-p${small ? " ls-btn-sm" : ""}`}
      data-testid="project-issue-create"
      onClick={() => setEditing("new")}
    >
      {plusIcon} 이슈 등록
    </button>
  );
  const dialog = editing && options && (
    <ProjectIssueDialog
      key={editing === "new" ? "new" : editing.issueId}
      issue={editing === "new" ? undefined : editing}
      projectId={projectId}
      projects={project ? [project] : options.projects}
      importance={options.importance}
      statuses={options.statuses}
      onClose={() => setEditing(null)}
      onSaved={saved}
      dirtyScreenId={dirtyScreenId}
    />
  );
  const optionAlert = optionError && (
    <p role="alert" data-testid="project-issue-options-error">
      {optionError}{" "}
      <button
        type="button"
        className="ls-btn ls-btn-sm"
        data-testid="project-issue-options-retry"
        onClick={() => setOptionRevision((v) => v + 1)}
      >
        다시 시도
      </button>
    </p>
  );
  const toast = notice && (
    <Toast
      message={notice}
      kind="ok"
      testId="project-issue-toast"
      onDismiss={() => setNotice("")}
    />
  );

  const columns: ListColumn<api.ProjectIssue>[] = [
    { key: "importanceCode", label: "중요도", render: importanceBadge },
    {
      key: "title",
      label: "제목",
      required: true,
      render: (row) => <span className="project-issue-title">{row.title}</span>,
    },
    {
      key: "projectName",
      label: "프로젝트",
      render: (row) =>
        onOpenProject ? (
          <button
            type="button"
            className="project-lnk"
            data-testid={`project-issue-project-${row.issueId}`}
            onClick={(event) => {
              event.stopPropagation();
              onOpenProject(row.projectId);
            }}
          >
            {row.projectName}
          </button>
        ) : (
          row.projectName
        ),
    },
    { key: "assigneeName", label: "담당자" },
    { key: "dueDate", label: "기한", render: dueText },
    { key: "statusCode", label: "상태", render: statusBadge },
    {
      key: "actionText",
      label: "조치 내용",
      render: (row) =>
        row.actionText ? (
          <span className="project-issue-title">{row.actionText}</span>
        ) : (
          <span className="project-sub">–</span>
        ),
    },
    {
      key: "createdAt",
      label: "등록일",
      render: (row) => formatDate(row.createdAt),
    },
  ];
  const cols = useListColumns("UI-31i", columns);
  const rows = useMemo(() => {
    const result = [...(data?.items ?? [])];
    if (sort) {
      const value = (row: api.ProjectIssue): string | number =>
        sort.key === "importanceCode"
          ? (IMPORTANCE_ORDER[row.importanceCode] ?? 3)
          : String(row[sort.key as keyof api.ProjectIssue] ?? "");
      result.sort((a, b) => {
        const x = value(a);
        const y = value(b);
        const order =
          typeof x === "number" && typeof y === "number"
            ? x - y
            : String(x).localeCompare(String(y), "ko");
        return sort.dir === "asc" ? order : -order;
      });
    }
    return result;
  }, [data, sort]);

  if (project) {
    // 프로젝트 상세 [이슈] 탭(시안): 종료·실주 프로젝트는 안내 글·[이슈 등록] 없이 표만.
    const writable = projectIssueCanCreate(project);
    return (
      <section
        className="project-issue-panel"
        data-testid="project-issues-panel"
        data-screen-id="SCR-UI-31"
      >
        {writable && (
          <div className="project-issue-head">
            <span className="project-cap">
              기한이 지난 이슈는 빨간색으로 보입니다. 줄을 누르면 고칠 수
              있습니다.
            </span>
            {canCreate && createButton(true)}
          </div>
        )}
        {optionAlert}
        {error && (
          <p role="alert" data-testid="project-issue-error">
            {error}{" "}
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="project-issue-retry"
              onClick={() => setReload((v) => v + 1)}
            >
              다시 시도
            </button>
          </p>
        )}
        {!data && !error && (
          <p className="project-sub" data-testid="project-issue-loading">
            불러오는 중입니다.
          </p>
        )}
        {data &&
          (rows.length ? (
            <div className="ls-twrap">
              <table className="ls-t" data-testid="project-issue-table">
                <thead>
                  <tr>
                    <th>중요도</th>
                    <th>제목 · 조치 내용</th>
                    <th>담당자</th>
                    <th>기한</th>
                    <th>상태</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr
                      key={row.issueId}
                      data-issue-row
                      data-testid={`project-issue-row-${row.issueId}`}
                      tabIndex={0}
                      onClick={() => options && setEditing(row)}
                      onKeyDown={(event) => {
                        if ((event.key === "Enter" || event.key === " ") && options) {
                          event.preventDefault();
                          setEditing(row);
                        }
                      }}
                    >
                      <td>{importanceBadge(row)}</td>
                      <td className="project-issue-title">
                        {row.title}
                        {row.actionText && (
                          <div className="project-cap">{row.actionText}</div>
                        )}
                      </td>
                      <td>{row.assigneeName}</td>
                      <td className="project-issue-due-cell">
                        {overdue(row) ? (
                          <span
                            className="project-bad"
                            data-testid={`project-issue-due-${row.issueId}`}
                          >
                            {row.dueDate.slice(5)}
                            <br />
                            {row.overdueDays}일 지남
                          </span>
                        ) : (
                          <span data-testid={`project-issue-due-${row.issueId}`}>
                            {row.dueDate.slice(5)}
                          </span>
                        )}
                      </td>
                      <td>{statusBadge(row)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="project-sub" data-testid="project-issue-empty">
              등록된 이슈가 없습니다.
            </p>
          ))}
        {dialog}
        {toast}
      </section>
    );
  }

  const current = Math.min(
    page,
    Math.max(1, Math.ceil(rows.length / presentation.pageSize)),
  );
  const search = () => {
    setFilter({ ...draft });
    setPage(1);
    setReload((v) => v + 1);
  };
  const reset = () => {
    setDraft(initialFilter);
    setFilter(initialFilter);
    setPage(1);
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      await api.getCommonProjectBoardIssuesExcel(query);
      const message = `엑셀 파일을 만들었습니다${rows.length ? ` (조회 결과 ${rows.length}건)` : ""}`;
      if (onChanged) onChanged(message);
      else setNotice(message);
    } catch (reason) {
      setError(api.projectIssueFailure(reason));
    } finally {
      setDownloading(false);
    }
  };
  const create: ReactNode = canCreate ? createButton(false) : null;
  return (
    <section
      className="project-issue-panel"
      data-testid="project-issues-panel"
      data-screen-id="SCR-UI-31"
    >
      {createHost === undefined
        ? create && <div className="project-issue-head">{create}</div>
        : createHost && create
          ? createPortal(create, createHost)
          : null}
      <ListFilters
        onSearch={search}
        onReset={reset}
        testId="project-issue-filters"
        activeCount={
          (filter.status !== "open" ? 1 : 0) +
          (filter.importanceCode ? 1 : 0) +
          (filter.projectId ? 1 : 0)
        }
      >
        <ListFilter label="상태" htmlFor="project-issue-status-filter">
          <select
            id="project-issue-status-filter"
            className="ls-in"
            data-testid="project-issue-status-filter"
            value={draft.status}
            onChange={(e) =>
              setDraft({ ...draft, status: e.target.value as StatusChoice })
            }
          >
            <option value="all">전체</option>
            <option value="open">열림·조치 중</option>
            <option value="overdue">기한 지남</option>
            <option value="closed">종료</option>
          </select>
        </ListFilter>
        <ListFilter label="중요도" htmlFor="project-issue-importance-filter">
          <select
            id="project-issue-importance-filter"
            className="ls-in"
            data-testid="project-issue-importance-filter"
            value={draft.importanceCode}
            onChange={(e) =>
              setDraft({ ...draft, importanceCode: e.target.value })
            }
          >
            <option value="">전체</option>
            {(options?.importance ?? []).map((c) => (
              <option key={c.code} value={c.code}>
                {c.codeName}
              </option>
            ))}
          </select>
        </ListFilter>
        <ListFilter label="프로젝트" htmlFor="project-issue-project-filter">
          <select
            id="project-issue-project-filter"
            className="ls-in"
            data-testid="project-issue-project-filter"
            value={draft.projectId}
            onChange={(e) => setDraft({ ...draft, projectId: e.target.value })}
          >
            <option value="">전체</option>
            {(options?.projects ?? [])
              .filter(projectIssueCanCreate)
              .map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName}
                </option>
              ))}
          </select>
        </ListFilter>
      </ListFilters>
      {optionAlert}
      <ListView
        screenId="UI-31i"
        testId="project-issue-list"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
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
        rowKey={(row) => row.issueId}
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
        onSort={setSort}
        onDownload={() => void download()}
        downloading={downloading}
        onRowClick={(row) => {
          if (options) setEditing(row);
        }}
        error={error || undefined}
        onRetry={() => setReload((v) => v + 1)}
        onResetFilters={reset}
        mobileRows={error ? [] : data ? rows : null}
        renderCard={(row) => (
          <>
            <div className="ls-r1">
              <strong>{row.title}</strong>
              {importanceBadge(row)}
            </div>
            <div className="ls-r2">
              <span>
                {row.projectName} · {row.assigneeName}
              </span>
              {statusBadge(row)}
            </div>
            <div className="ls-r2">
              <span>기한 {dueText(row)}</span>
            </div>
          </>
        )}
      />
      {dialog}
      {toast}
    </section>
  );
}

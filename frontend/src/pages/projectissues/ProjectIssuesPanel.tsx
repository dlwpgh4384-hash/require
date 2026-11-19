import { useEffect, useMemo, useState } from "react";
import {
  ListFilter,
  ListFilters,
  ListView,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { formatDateTime } from "../../shared/time";
import { getCommonCodes, type CodeItem } from "../codegroups/codeGroups";
import { getCommonProjects } from "../projects/projects";
import {
  ProjectIssueDialog,
  projectIssueCanCreate,
  type ProjectIssueOption,
} from "./ProjectIssueDialog";
import * as api from "./projectIssues";
import "./projectIssues.css";

// SCR-UI-31: 모아 보기와 프로젝트 상세에서 같은 이슈 동작을 사용한다.
export function ProjectIssuesPanel({
  project,
  onOpenProject,
  onChanged,
  onOpenCount,
  revision = 0,
  dirtyScreenId = "UI-31",
}: {
  project?: ProjectIssueOption;
  onOpenProject?: (projectId: string) => void;
  onChanged?: (message?: string) => void;
  onOpenCount?: (count: number) => void;
  revision?: number;
  dirtyScreenId?: string;
}) {
  const [draft, setDraft] = useState<api.ProjectIssueFilter>({});
  const [filter, setFilter] = useState<api.ProjectIssueFilter>({});
  const [reload, setReload] = useState(0);
  const [optionRevision, setOptionRevision] = useState(0);
  const [data, setData] = useState<api.ProjectIssueList | null>(null);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
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
  const presentation = useListPresentation();
  const projectId = project?.projectId;
  const query = useMemo(
    () => ({ ...filter, ...(projectId ? { projectId } : {}) }),
    [filter, projectId],
  );
  useEffect(() => {
    const abort = new AbortController();
    setOptionError("");
    Promise.all([
      getCommonProjects(abort.signal),
      getCommonCodes("IMPR", abort.signal),
      getCommonCodes("ISSUE_STAT", abort.signal),
    ])
      .then(([projects, importance, statuses]) => {
        if (!abort.signal.aborted)
          setOptions({
            projects: projects.items,
            importance: importance.items,
            statuses: statuses.items,
          });
      })
      .catch((reason) => {
        if (!abort.signal.aborted)
          setOptionError(api.projectIssueFailure(reason));
      });
    return () => abort.abort();
  }, [optionRevision]);
  useEffect(() => {
    const abort = new AbortController();
    setData(null);
    setError("");
    setFields({});
    api
      .getCommonProjectBoardIssues(query, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) {
          setData(result);
          onOpenCount?.(result.openCount);
        }
      })
      .catch((reason) => {
        if (!abort.signal.aborted) {
          setError(api.projectIssueFailure(reason));
          if (reason instanceof api.ProjectIssueRequestError)
            setFields(
              Object.fromEntries(
                reason.errors.map((f) => [f.field, f.message]),
              ),
            );
        }
      });
    return () => abort.abort();
  }, [query, reload, revision, onOpenCount]);
  const saved = async () => {
    // 쓰기는 한 번만 수행한다. 후속 조회 실패는 목록 오류로 남기고 저장 재시도를 유도하지 않는다.
    setEditing(null);
    try {
      const result = await api.getCommonProjectBoardIssues(query);
      setData(result);
      setError("");
      onOpenCount?.(result.openCount);
    } catch (reason) {
      setData(null);
      setError(api.projectIssueFailure(reason));
    }
    setNotice("이슈를 저장했습니다.");
    onChanged?.("이슈를 저장했습니다.");
  };
  const label = (items: CodeItem[] | undefined, code: string) =>
    items?.find((c) => c.code === code)?.codeName ?? code;
  const columns: ListColumn<api.ProjectIssue>[] = [
    {
      key: "importanceCode",
      label: "중요도",
      render: (row) => label(options?.importance, row.importanceCode),
    },
    { key: "title", label: "제목", required: true },
    {
      key: "projectName",
      label: "프로젝트",
      render: (row) =>
        onOpenProject ? (
          <button
            type="button"
            className="link-button"
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
    {
      key: "dueDate",
      label: "기한",
      render: (row) => (
        <span
          className={row.overdueDays > 0 ? "project-bad" : undefined}
          data-testid={`project-issue-due-${row.issueId}`}
        >
          {row.dueDate}
          {row.overdueDays > 0 ? ` (${row.overdueDays}일 초과)` : ""}
        </span>
      ),
    },
    {
      key: "statusCode",
      label: "상태",
      render: (row) => label(options?.statuses, row.statusCode),
    },
    { key: "actionText", label: "조치" },
    {
      key: "createdAt",
      label: "등록일",
      render: (row) => formatDateTime(row.createdAt),
    },
  ];
  const rows = useMemo(() => {
    const result = [...(data?.items ?? [])];
    if (sort)
      result.sort((a, b) => {
        const x = a[sort.key as keyof api.ProjectIssue];
        const y = b[sort.key as keyof api.ProjectIssue];
        return (
          String(x ?? "").localeCompare(String(y ?? ""), "ko") *
          (sort.dir === "asc" ? 1 : -1)
        );
      });
    return result;
  }, [data, sort]);
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
    setDraft({});
    setFilter({});
    setPage(1);
  };
  const fieldError = (name: string) =>
    fields[name] && <span className="project-error">{fields[name]}</span>;
  return (
    <section
      className="project-issue-panel"
      data-testid="project-issues-panel"
      data-screen-id="SCR-UI-31"
    >
      <div className="project-issue-heading">
        <h3>이슈 모아 보기 {data ? `(열린 ${data.openCount}건)` : ""}</h3>
        {options &&
          (!project || projectIssueCanCreate(project)) &&
          options.projects.some(projectIssueCanCreate) && (
            <button
              type="button"
              className="ls-btn ls-btn-p"
              data-testid="project-issue-create"
              onClick={() => setEditing("new")}
            >
              이슈 등록
            </button>
          )}
      </div>
      <ListFilters
        onSearch={search}
        onReset={reset}
        testId="project-issue-filters"
      >
        <ListFilter label="상태" htmlFor="project-issue-status-filter">
          <select
            id="project-issue-status-filter"
            className="ls-in"
            data-testid="project-issue-status-filter"
            value={draft.statusCode ?? ""}
            onChange={(e) =>
              setDraft({ ...draft, statusCode: e.target.value || undefined })
            }
          >
            <option value="">열림·조치 중</option>
            {options?.statuses.map((c) => (
              <option key={c.code} value={c.code}>
                {c.codeName}
              </option>
            ))}
          </select>
          {fieldError("statusCode")}
        </ListFilter>
        <ListFilter label="기한 지남" htmlFor="project-issue-overdue-filter">
          <select
            id="project-issue-overdue-filter"
            className="ls-in"
            data-testid="project-issue-overdue-filter"
            value={draft.overdue ? "true" : ""}
            onChange={(e) =>
              setDraft({
                ...draft,
                overdue: e.target.value === "true" ? true : undefined,
              })
            }
          >
            <option value="">전체</option>
            <option value="true">기한 지남</option>
          </select>
        </ListFilter>
        <ListFilter label="중요도" htmlFor="project-issue-importance-filter">
          <select
            id="project-issue-importance-filter"
            className="ls-in"
            data-testid="project-issue-importance-filter"
            value={draft.importanceCode ?? ""}
            onChange={(e) =>
              setDraft({
                ...draft,
                importanceCode: e.target.value || undefined,
              })
            }
          >
            <option value="">전체</option>
            {options?.importance.map((c) => (
              <option key={c.code} value={c.code}>
                {c.codeName}
              </option>
            ))}
          </select>
          {fieldError("importanceCode")}
        </ListFilter>
        {!project && (
          <ListFilter label="프로젝트" htmlFor="project-issue-project-filter">
            <select
              id="project-issue-project-filter"
              className="ls-in"
              data-testid="project-issue-project-filter"
              value={draft.projectId ?? ""}
              onChange={(e) =>
                setDraft({ ...draft, projectId: e.target.value || undefined })
              }
            >
              <option value="">전체</option>
              {options?.projects.map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName}
                </option>
              ))}
            </select>
            {fieldError("projectId")}
          </ListFilter>
        )}
      </ListFilters>
      {optionError && (
        <p role="alert" data-testid="project-issue-options-error">
          {optionError}{" "}
          <button
            type="button"
            className="ls-btn"
            data-testid="project-issue-options-retry"
            onClick={() => setOptionRevision((v) => v + 1)}
          >
            조건 다시 시도
          </button>
        </p>
      )}
      <ListView
        screenId="UI-31"
        testId="project-issue-list"
        columns={columns}
        visibleColumns={columns}
        hiddenColumns={[]}
        onToggleColumn={() => undefined}
        onResetColumns={() => undefined}
        columnSettings={false}
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
        onRowClick={(row) => {
          if (options) setEditing(row);
        }}
        error={error || undefined}
        onRetry={() => setReload((v) => v + 1)}
        onResetFilters={reset}
        mobileRows={data && !error ? rows : null}
        renderCard={(row) => (
          <>
            <strong>{row.title}</strong>
            <div>
              {row.projectName} · {row.assigneeName}
            </div>
            <span className={row.overdueDays ? "project-bad" : undefined}>
              {row.dueDate}
              {row.overdueDays ? ` (${row.overdueDays}일 초과)` : ""}
            </span>
          </>
        )}
      />
      {notice && (
        <p role="status" data-testid="project-issue-notice">
          {notice}
        </p>
      )}
      {editing && options && (
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
      )}
    </section>
  );
}

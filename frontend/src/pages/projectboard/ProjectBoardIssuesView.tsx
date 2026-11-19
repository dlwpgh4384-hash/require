import { useEffect, useState } from "react";
import {
  ListFilter,
  ListFilters,
  ListView,
  useListPresentation,
  type ListColumn,
} from "../../shared/list";
import { getCommonCodes, type CodeItem } from "../codegroups/codeGroups";
import {
  getCommonProjects,
  projectFailure,
  projectRequest,
  ProjectRequestError,
} from "../projects/projects";

interface BoardIssue {
  issueId: string;
  projectId: string;
  projectName: string;
  title: string;
  importanceCode: string;
  statusCode: string;
  assigneeName: string;
  dueDate: string;
  overdueDays: number;
  actionText: string | null;
  createdAt: string;
}
interface IssueFilter {
  statusCode?: string;
  importanceCode?: string;
  overdue?: string;
  projectId?: string;
}

/** 이슈 API/편집은 이슈 기능 소유자가 제공하며 이 탭은 그 조회와 공유 상세를 연결한다. */
export function ProjectBoardIssuesView({
  onOpenProject,
  revision,
  onCount,
}: {
  onOpenProject: (id: string) => void;
  revision: number;
  onCount: (count: number) => void;
}) {
  const [draft, setDraft] = useState<IssueFilter>({});
  const [filter, setFilter] = useState<IssueFilter>({});
  const [items, setItems] = useState<BoardIssue[] | null>(null);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [retry, setRetry] = useState(0);
  const [page, setPage] = useState(1);
  const [options, setOptions] = useState<{
    states: CodeItem[];
    importance: CodeItem[];
    projects: { projectId: string; projectName: string }[];
  }>({ states: [], importance: [], projects: [] });
  const [optionError, setOptionError] = useState("");
  const [optionRevision, setOptionRevision] = useState(0);
  const presentation = useListPresentation();
  useEffect(() => {
    const abort = new AbortController();
    setOptionError("");
    Promise.all([
      getCommonCodes("ISSUE_STAT", abort.signal),
      getCommonCodes("IMPR", abort.signal),
      getCommonProjects(abort.signal),
    ])
      .then(([states, importance, projects]) => {
        if (!abort.signal.aborted)
          setOptions({
            states: states.items,
            importance: importance.items,
            projects: projects.items,
          });
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setOptionError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [optionRevision]);
  useEffect(() => {
    const abort = new AbortController();
    setItems(null);
    setError("");
    setFields({});
    const query = new URLSearchParams();
    Object.entries(filter).forEach(([key, value]) => {
      if (value) query.set(key, value);
    });
    projectRequest<{ items: BoardIssue[]; openCount: number }>(
      `/api/common/project-issues?${query}`,
      { signal: abort.signal },
    )
      .then((result) => {
        if (abort.signal.aborted) return;
        setItems(result.items);
        onCount(result.openCount);
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
  }, [filter, retry, revision, onCount]);
  const columns: ListColumn<BoardIssue>[] = [
    {
      key: "importanceCode",
      label: "중요도",
      render: (row) =>
        options.importance.find((code) => code.code === row.importanceCode)
          ?.codeName ?? row.importanceCode,
    },
    { key: "title", label: "제목" },
    {
      key: "projectName",
      label: "프로젝트",
      render: (row) => (
        <button
          type="button"
          className="project-lnk"
          data-testid={`board-issue-project-${row.issueId}`}
          onClick={(event) => {
            event.stopPropagation();
            onOpenProject(row.projectId);
          }}
        >
          {row.projectName}
        </button>
      ),
    },
    { key: "assigneeName", label: "담당자" },
    {
      key: "dueDate",
      label: "기한",
      render: (row) => (
        <span className={row.overdueDays > 0 ? "project-board-overdue" : ""}>
          {row.dueDate}
          {row.overdueDays > 0 ? ` (${row.overdueDays}일 초과)` : ""}
        </span>
      ),
    },
    {
      key: "statusCode",
      label: "상태",
      render: (row) =>
        options.states.find((code) => code.code === row.statusCode)?.codeName ??
        row.statusCode,
    },
    { key: "actionText", label: "조치" },
    {
      key: "createdAt",
      label: "등록일",
      render: (row) => row.createdAt.slice(0, 10),
    },
  ];
  columns.forEach((column) => {
    column.sortable = false;
  });
  const current = Math.min(
    page,
    Math.max(1, Math.ceil((items?.length ?? 0) / presentation.pageSize)),
  );
  const reset = () => {
    setDraft({});
    setFilter({});
    setPage(1);
  };
  return (
    <section
      data-testid="project-board-issues-panel"
      role="tabpanel"
      aria-label="이슈 모아 보기"
    >
      <ListFilters
        testId="project-board-issue-filters"
        onSearch={() => {
          setFilter({ ...draft });
          setPage(1);
        }}
        onReset={reset}
      >
        {(
          [
            [
              "statusCode",
              "상태",
              options.states.map((code) => [code.code, code.codeName]),
            ],
            ["overdue", "기한 지남", [["true", "기한 지남"]]],
            [
              "importanceCode",
              "중요도",
              options.importance.map((code) => [code.code, code.codeName]),
            ],
            [
              "projectId",
              "프로젝트",
              options.projects.map((project) => [
                project.projectId,
                project.projectName,
              ]),
            ],
          ] as [keyof IssueFilter, string, string[][]][]
        ).map(([key, label, choices]) => (
          <ListFilter key={key} label={label} htmlFor={`board-issue-${key}`}>
            <select
              className="ls-in"
              id={`board-issue-${key}`}
              data-testid={`board-issue-${key.replace(/[A-Z]/g, (match) => `-${match.toLowerCase()}`)}`}
              value={draft[key] ?? ""}
              onChange={(event) =>
                setDraft({ ...draft, [key]: event.target.value || undefined })
              }
            >
              <option value="">
                {key === "statusCode" ? "열림·조치 중" : "전체"}
              </option>
              {choices.map(([value, name]) => (
                <option key={value} value={value}>
                  {name}
                </option>
              ))}
            </select>
            {fields[key] && <span className="field-error">{fields[key]}</span>}
          </ListFilter>
        ))}
      </ListFilters>
      {optionError && (
        <div role="alert">
          {optionError}{" "}
          <button
            type="button"
            className="ls-btn"
            data-testid="board-issue-options-retry"
            onClick={() => setOptionRevision((value) => value + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      <ListView
        screenId="UI-31"
        testId="project-board-issues-list"
        columns={columns}
        visibleColumns={columns}
        hiddenColumns={[]}
        columnSettings={false}
        onToggleColumn={() => undefined}
        onResetColumns={() => undefined}
        rows={
          error
            ? []
            : (items?.slice(
                (current - 1) * presentation.pageSize,
                current * presentation.pageSize,
              ) ?? null)
        }
        rowKey={(row) => row.issueId}
        total={items?.length ?? 0}
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
        error={error || undefined}
        onRetry={() => setRetry((value) => value + 1)}
        onResetFilters={reset}
        onRowClick={(row) => onOpenProject(row.projectId)}
        mobileRows={items}
        renderCard={(row) => (
          <>
            <strong>{row.title}</strong>
            <div>
              {row.projectName} · {row.assigneeName} · {row.dueDate}
            </div>
          </>
        )}
      />
    </section>
  );
}

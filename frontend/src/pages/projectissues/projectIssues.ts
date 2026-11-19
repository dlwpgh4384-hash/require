export interface ProjectIssueInput {
  projectId: string;
  title: string;
  dueDate: string;
  importanceCode: string;
  statusCode: string;
  actionText: string | null;
}
export interface ProjectIssue extends ProjectIssueInput {
  issueId: string;
  projectName: string;
  assigneeId: string;
  assigneeName: string;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  overdueDays: number;
}
export interface ProjectIssueFilter {
  statusCode?: string;
  overdue?: boolean;
  importanceCode?: string;
  projectId?: string;
}
export interface ProjectIssueList {
  items: ProjectIssue[];
  openCount: number;
}
export class ProjectIssueRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
export const projectIssueFailure = (reason: unknown) =>
  reason instanceof Error
    ? reason.message
    : "이슈를 처리하지 못했습니다. 다시 시도해 주세요.";
const root = "/api/common/project-issues";
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new ProjectIssueRequestError(
      response.status,
      response.status === 404
        ? "대상이 사라졌습니다. 다시 조회해 주세요."
        : body.message || "이슈를 처리하지 못했습니다.",
      body.errors || [],
    );
  return body.data as T;
}
export const getCommonProjectBoardIssues = (
  filter: ProjectIssueFilter = {},
  signal?: AbortSignal,
) => {
  const query = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return request<ProjectIssueList>(`${root}${query.size ? `?${query}` : ""}`, {
    signal,
  });
};
const write = (method: string, input: ProjectIssueInput): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(input),
});
export const postCommonProjectBoardIssue = (input: ProjectIssueInput) =>
  request<ProjectIssue>(root, write("POST", input));
export const putCommonProjectBoardIssue = (
  issueId: string,
  input: ProjectIssueInput,
) =>
  request<ProjectIssue>(
    `${root}/${encodeURIComponent(issueId)}`,
    write("PUT", input),
  );

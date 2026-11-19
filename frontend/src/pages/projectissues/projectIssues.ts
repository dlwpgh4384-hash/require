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
  /** 등록·고친 사람 이름(사용자 표, 없으면 계정 ID). */
  createdByName?: string;
  updatedByName?: string | null;
  /** 동시 수정 확인 값(고친 시각, 없으면 등록 시각). 수정 때 그대로 보낸다. */
  modifiedAt?: string;
}
export interface ProjectIssueFilter {
  statusCode?: string;
  overdue?: boolean;
  importanceCode?: string;
  projectId?: string;
  /** true면 상태 상관없이 전부(종료는 맨 뒤). */
  all?: boolean;
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
const issueQuery = (filter: ProjectIssueFilter) => {
  const query = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return query;
};
/** 이슈 모아 보기 엑셀(목록과 같은 조건). 실패하면 서버 문구로 알린다. */
export async function getCommonProjectBoardIssuesExcel(
  filter: ProjectIssueFilter = {},
) {
  const query = issueQuery(filter);
  const response = await fetch(`${root}/excel${query.size ? `?${query}` : ""}`);
  if (
    !response.ok ||
    !response.headers
      .get("Content-Type")
      ?.includes(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
  ) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    try {
      message = (await response.json()).message || message;
    } catch {
      /* 기본 안내 유지 */
    }
    throw new Error(message);
  }
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const href = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = href;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || "프로젝트 이슈.xlsx";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 1000);
}
const write = (
  method: string,
  input: ProjectIssueInput & { modifiedAt?: string },
): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(input),
});
export const postCommonProjectBoardIssue = (input: ProjectIssueInput) =>
  request<ProjectIssue>(root, write("POST", input));
/** 이슈 수정: 조회 때 받은 modifiedAt을 함께 보낸다(그 사이 다른 사람이 고쳤으면 409). */
export const putCommonProjectBoardIssue = (
  issueId: string,
  input: ProjectIssueInput,
  modifiedAt?: string | null,
) =>
  request<ProjectIssue>(
    `${root}/${encodeURIComponent(issueId)}`,
    write("PUT", { ...input, modifiedAt: modifiedAt ?? undefined }),
  );


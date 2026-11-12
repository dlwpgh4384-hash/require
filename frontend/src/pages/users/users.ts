export interface UserAccount {
  userId: string;
  name: string;
  email: string;
  roleCds: string[];
  headquarters: boolean;
  projectIds: string[];
  userStatCd: string;
  modDttm: string | null;
}
export interface UserListItem {
  userId: string;
  name: string;
  roleCds: string[];
  roleNames: string[];
  assignedParts: string[];
  userStatCd: string;
  lastAccessAt: string | null;
  modDttm: string | null;
}
export interface UserProject {
  projectId: string;
  projectName: string;
}

export interface UserPageResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}
export interface UserImpact {
  projects: UserProject[];
  projectCount: number;
  blockedUsers: { userId: string; reason: string }[];
}
export interface UserSaveResult {
  account: UserAccount;
  transferredProjects: UserProject[];
}
export interface UserCreate {
  userId: string;
  name: string;
  email: string;
  roleCds: string[];
  headquarters: boolean;
  projectIds: string[];
}
export interface UserUpdate {
  roleCds: string[];
  headquarters: boolean;
  projectIds: string[];
  modDttm: string | null;
  confirmedProjectIds: string[];
}
export class UserRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function readResponse<T>(response: Response): Promise<T> {
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    const business = response.status === 400 || response.status === 409;
    throw new UserRequestError(
      response.status,
      business && typeof body.message === "string"
        ? body.message
        : "사용자 정보를 처리하지 못했습니다. 다시 시도해 주세요.",
      business && Array.isArray(body.errors) ? body.errors : [],
    );
  }
  if (!body.data || typeof body.data !== "object")
    throw new UserRequestError(500, "응답을 다시 확인해 주세요.");
  return body.data as T;
}
const jsonBody = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export function getCommonUsers(
  filters: {
    page: number;
    pageSize?: number;
    roleCd?: string;
    userStatCd?: string;
    keyword?: string;
  },
  signal?: AbortSignal,
) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== "") query.set(key, String(value));
  });
  return fetch(`/api/common/users?${query}`, { signal }).then(
    readResponse<UserPageResult<UserListItem>>,
  );
}
export const getCommonUser = (id: string, signal?: AbortSignal) =>
  fetch(`/api/common/users/${encodeURIComponent(id)}`, { signal }).then(
    readResponse<UserAccount>,
  );
export const getCommonUserAccessLogs = (
  id: string,
  page: number,
  pageSize?: number,
  signal?: AbortSignal,
) =>
  fetch(
    `/api/common/users/${encodeURIComponent(id)}/access-logs?page=${page}${pageSize ? `&pageSize=${pageSize}` : ""}`,
    { signal },
  ).then(
    readResponse<
      UserPageResult<{ occurredAt: string; action: string; target: string }>
    >,
  );
export function getCommonUserChangeImpact(
  ids: string[],
  action: "STOP" | "ROLE_CHANGE",
  roles?: string[],
) {
  const query = new URLSearchParams({ userIds: ids.join(","), action });
  if (roles !== undefined) query.set("roleCds", roles.join(","));
  return fetch(`/api/common/users/change-impact?${query}`).then(
    readResponse<UserImpact>,
  );
}
export const putCommonUsers = (body: UserCreate) =>
  fetch("/api/common/users", { method: "PUT", ...jsonBody(body) }).then(
    readResponse<UserSaveResult>,
  );
export const putCommonUser = (id: string, body: UserUpdate) =>
  fetch(`/api/common/users/${encodeURIComponent(id)}`, {
    method: "PUT",
    ...jsonBody(body),
  }).then(readResponse<UserSaveResult>);
export const putCommonUserStop = (
  users: { userId: string; modDttm: string | null }[],
  confirmedProjectIds: string[],
) =>
  fetch("/api/common/users/stop", {
    method: "PUT",
    ...jsonBody({ users, confirmedProjectIds }),
  }).then(
    readResponse<{
      processedUserIds: string[];
      unprocessedCount: number;
      unprocessed: { userId: string; reason: string }[];
    }>,
  );
export const putCommonUserPasswordReset = (
  id: string,
  modDttm: string | null,
) =>
  fetch(`/api/common/users/${encodeURIComponent(id)}/password-reset`, {
    method: "PUT",
    ...jsonBody({ modDttm }),
  }).then(
    readResponse<{ userId: string; tempPwdYn: string; modDttm: string | null }>,
  );
export const putCommonUserUnlock = (id: string, modDttm: string | null) =>
  fetch(`/api/common/users/${encodeURIComponent(id)}/unlock`, {
    method: "PUT",
    ...jsonBody({ modDttm }),
  }).then(
    readResponse<{
      userId: string;
      userStatCd: string;
      pwdErrCnt: number;
      modDttm: string | null;
    }>,
  );
export const putCommonUserResume = (id: string, modDttm: string | null) =>
  fetch(`/api/common/users/${encodeURIComponent(id)}/resume`, {
    method: "PUT",
    ...jsonBody({ modDttm }),
  }).then(readResponse<UserAccount>);

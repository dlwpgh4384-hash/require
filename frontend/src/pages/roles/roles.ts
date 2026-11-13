export type RoleFlag = "Y" | "N";
export interface Role {
  roleId: string;
  roleName: string;
  dataScopeCd: string;
  snstvViewYn: RoleFlag;
  useYn: RoleFlag;
  basicRole: boolean;
}
export const roleActions = [
  ["readYn", "조회"],
  ["createYn", "등록"],
  ["updateYn", "수정"],
  ["deleteYn", "삭제"],
  ["uploadYn", "업로드"],
  ["downloadYn", "다운로드"],
  ["cnfmYn", "확정"],
] as const;
export type RoleAction = (typeof roleActions)[number][0];
export type RoleMenuInput = { menuId: string } & Record<RoleAction, RoleFlag>;
export type RoleMenu = RoleMenuInput & {
  menuName: string;
  screenId: string;
  groupName: string;
};
export interface RoleSettings {
  role: Role;
  menuAuths: RoleMenu[];
  dataScopes: { code: string; name: string }[];
}
export interface RoleUpdate {
  dataScopeCd: string;
  snstvViewYn: RoleFlag;
  menuAuths: RoleMenuInput[];
  confirmedAffectedUserIds: string[];
}
/** sourceRoleId가 있으면 역할 복사, 없으면 역할 추가(모든 메뉴 권한 없음). */
export interface RoleCopy {
  sourceRoleId?: string;
  roleName: string;
  dataScopeCd: string;
}
export interface RoleImpact {
  headquartersUserCount: number;
  projectUserCount: number;
  affectedUserIds: string[];
  preservedPmUserIds: string[];
}
export class RoleRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function roleRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    throw new RoleRequestError(
      response.status,
      response.status === 400 && typeof body.message === "string"
        ? body.message
        : "권한 정보를 처리하지 못했습니다. 다시 시도해 주세요.",
      response.status === 400 && Array.isArray(body.errors) ? body.errors : [],
    );
  }
  if (!body.data || typeof body.data !== "object")
    throw new Error("응답을 다시 확인해 주세요.");
  return body.data as T;
}
const rolePath = (id: string) => `/api/common/roles/${encodeURIComponent(id)}`;
const roleBody = (input: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(input),
});
export const getCommonRoles = async (signal?: AbortSignal) => {
  const result = await roleRequest<{ items: Role[] }>("/api/common/roles", {
    signal,
  });
  if (!Array.isArray(result.items))
    throw new Error("역할 목록 응답을 다시 확인해 주세요.");
  return result;
};
export const getCommonRole = (id: string, signal?: AbortSignal) =>
  roleRequest<RoleSettings>(rolePath(id), { signal });
export const getCommonRoleChangeImpact = (id: string, dataScopeCd: string) =>
  roleRequest<RoleImpact>(
    `${rolePath(id)}/change-impact?${new URLSearchParams({ dataScopeCd })}`,
  );
export const postCommonRoleCopy = (input: RoleCopy) =>
  roleRequest<RoleSettings>("/api/common/roles", {
    method: "POST",
    ...roleBody(input),
  });
export interface RoleChange {
  changedAt: string;
  item: string;
  before: string | null;
  after: string | null;
  changedBy: string | null;
}
export const getCommonRoleChangeHistory = async (
  id: string,
  signal?: AbortSignal,
) => {
  const result = await roleRequest<{ items: RoleChange[] }>(
    `${rolePath(id)}/change-history`,
    { signal },
  );
  if (!Array.isArray(result.items))
    throw new Error("변경 이력 응답을 다시 확인해 주세요.");
  return result.items;
};
export const putCommonRole = (id: string, input: RoleUpdate) =>
  roleRequest<RoleSettings>(rolePath(id), {
    method: "PUT",
    ...roleBody(input),
  });
export const postCommonRoleStop = (id: string) =>
  roleRequest<{ roleId: string; useYn: "N" }>(`${rolePath(id)}/stop`, {
    method: "POST",
  });
export const roleDraft = (settings: RoleSettings): RoleUpdate => ({
  dataScopeCd: settings.role.dataScopeCd,
  snstvViewYn: settings.role.snstvViewYn,
  menuAuths: settings.menuAuths.map(
    ({
      menuId,
      readYn,
      createYn,
      updateYn,
      deleteYn,
      uploadYn,
      downloadYn,
      cnfmYn,
    }) => ({
      menuId,
      readYn,
      createYn,
      updateYn,
      deleteYn,
      uploadYn,
      downloadYn,
      cnfmYn,
    }),
  ),
  confirmedAffectedUserIds: [],
});

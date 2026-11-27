export interface AccountPermission {
  menuId: string;
  screenId: string;
  readYn: string;
  pcOnlyYn: string;
  createYn: string;
  updateYn: string;
  deleteYn: string;
  uploadYn: string;
  downloadYn: string;
  cnfmYn: string;
}
export interface AccountMeta {
  modifiedAt?: string;
  passwordChangedAt?: string | null;
  passwordChangeDays?: number;
  passwordChangeRequired?: boolean;
  permissions?: AccountPermission[];
  sensitive?: boolean;
  serverRespondedAt?: string;
  sessionMinutes?: number;
  remainingSeconds?: number;
  mfaDelivery?: {
    maskedEmail: string;
    expiresAt: string;
    resendAvailableAt: string;
  } | null;
  deliveryError?: string | null;
}
export interface AccountResponse<T> {
  success: true;
  data: T;
  meta: AccountMeta;
}
export interface AccountInfo {
  userId: string;
  name: string;
  roles: string[];
  /** roles와 같은 순서의 역할 이름(TB_SY_ROLE.ROLE_NM). 없으면 역할 ID를 그대로 보인다. */
  roleNames?: string[];
  parts: string[];
}
/** 비밀번호 단계 로그인 결과. userName은 첫 로그인 안내·로그인 토스트에 쓴다. */
export interface LoginResult {
  nextScreenId: string;
  userName: string;
}
export class AccountApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
export function createAccountAccessApi(
  request: typeof fetch = (...args) => fetch(...args),
) {
  async function call<T>(
    path: string,
    method = "GET",
    body?: object,
  ): Promise<AccountResponse<T>> {
    const response = await request(path, {
      method,
      credentials: "same-origin",
      ...(body
        ? {
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
          }
        : {}),
    });
    if (response.status === 204)
      return { success: true, data: undefined as T, meta: {} };
    const result = await response.json();
    if (!response.ok || result.success !== true)
      throw new AccountApiError(
        response.status,
        result.message ?? "요청을 처리하지 못했습니다.",
        result.errors ?? [],
      );
    return result;
  }
  return {
    login: (userId: string, password: string) =>
      call<LoginResult>("/api/common/login", "POST", {
        userId,
        password,
      }),
    logout: () => call<void>("/api/common/logout", "POST"),
    find: (userId: string, email: string) =>
      call<{ message: string }>("/api/common/password-find", "POST", {
        userId,
        email,
      }),
    myInfo: () => call<AccountInfo>("/api/common/my-info"),
    change: (
      currentPassword: string,
      newPassword: string,
      modifiedAt: string,
    ) =>
      call<{ passwordChangedAt: string; modifiedAt: string }>(
        "/api/common/my-password",
        "PUT",
        { currentPassword, newPassword, modifiedAt },
      ),
  };
}
export type AccountAccessApi = ReturnType<typeof createAccountAccessApi>;
/** 역할 이름 목록(내 정보 roleNames, roles와 같은 순서). 이름이 없으면 역할 ID를 그대로 보인다. */
export function roleLabels(info: Pick<AccountInfo, "roles" | "roleNames">) {
  return info.roles.map((role, index) => info.roleNames?.[index] || role);
}
/** 시안 roleShort: 헤더 사용자 단추·로그인 토스트에 쓰는 줄인 역할 이름(역할 ID 기준). */
const ROLE_SHORT: Record<string, string> = {
  ADMIN: "관리자",
  EXEC: "경영진",
  FIN: "재무",
  SALES_LDR: "파트장",
  PM: "PM",
};
/** 줄인 역할 이름 목록. 표에 없는 역할(관리자가 만든 역할)은 roleNames, 그것도 없으면 역할 ID. */
export function roleShortLabels(info: Pick<AccountInfo, "roles" | "roleNames">) {
  const names = roleLabels(info);
  return info.roles.map((role, index) => ROLE_SHORT[role] ?? names[index]);
}
/** 명세 ④-1: 역할이 여럿이면 「첫 역할 외 n」. 역할이 없으면 「역할 없음」. */
export function firstRoleLabel(labels: readonly string[]) {
  if (!labels.length) return "역할 없음";
  return `${labels[0]}${labels.length > 1 ? ` 외 ${labels.length - 1}` : ""}`;
}
/** 시안 loginDone: 「이름(역할)으로 로그인했습니다.」(줄인 역할 이름을 「·」로 잇는다). */
export function loginNotice(info: AccountInfo, userName = info.name) {
  const roles = roleShortLabels(info).join("·") || "역할 없음";
  return `${userName}(${roles})으로 로그인했습니다.`;
}
/** 시안 세션 만료 토스트(한 가지 문구). */
export const SESSION_EXPIRED_MESSAGE =
  "30분 동안 사용하지 않아 로그아웃되었습니다";
export const PASSWORD_CHANGED_MESSAGE =
  "비밀번호를 바꿨습니다. 다음 로그인부터 새 비밀번호를 씁니다.";
export const PASSWORD_RULE_HELP = "8자 이상, 영문·숫자·특수문자를 모두 넣습니다.";
export const PASSWORD_RULE_ERROR =
  "8자 이상, 영문·숫자·특수문자를 모두 넣어 주세요.";
/** 시안: 볼 화면이 하나도 없을 때 제목 + 안내 + [로그아웃]. */
export const NO_ACCOUNT_MENUS_TITLE = "볼 수 있는 화면이 없습니다";
export const NO_ACCOUNT_MENUS = "관리자에게 권한을 요청해 주세요.";
/** 시안 go(): 권한 없는 화면 주소로 들어왔을 때 토스트(첫 화면이 대시보드면 「대시보드로 갑니다」). */
export const forbiddenScreenMessage = (firstScreenId: string) =>
  firstScreenId === "UI-16"
    ? "이 화면을 볼 권한이 없습니다. 대시보드로 갑니다."
    : "이 화면을 볼 권한이 없습니다.";
export const NO_MOBILE_MENUS =
  "휴대폰에서 볼 수 있는 화면이 없습니다. PC에서 이용해 주세요.";
export function firstAccountScreen(
  permissions: AccountPermission[],
  mobile: boolean,
) {
  const readable = permissions.filter((item) => item.readYn === "Y");
  const visible = readable.filter((item) => !mobile || item.pcOnlyYn !== "Y");
  return {
    screenId:
      visible.find((item) => item.screenId === "UI-16")?.screenId ??
      visible[0]?.screenId ??
      "UI-34",
    message: visible.length
      ? ""
      : readable.length && mobile
        ? NO_MOBILE_MENUS
        : NO_ACCOUNT_MENUS,
  };
}

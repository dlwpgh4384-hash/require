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
  parts: string[];
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
      call<{ nextScreenId: string }>("/api/common/login", "POST", {
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
export const NO_ACCOUNT_MENUS =
  "볼 수 있는 메뉴가 없습니다. 관리자에게 권한을 요청해 주세요.";
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

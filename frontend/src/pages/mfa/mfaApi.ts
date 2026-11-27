export interface MfaDelivery {
  maskedEmail: string;
  expiresAt: string;
  resendAvailableAt: string;
}
export interface MfaLogin {
  nextScreenId: string;
  userName: string;
}
export interface MfaResponse<T> {
  success: true;
  meta: Record<string, unknown>;
  data: T;
}
export class MfaApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
    public meta: Record<string, unknown> = {},
  ) {
    super(message);
  }
}
export interface MfaApi {
  verify(code: string): Promise<MfaResponse<MfaLogin>>;
  resend(): Promise<MfaResponse<MfaDelivery>>;
}

// The shared session fetch owns same-origin cookies and X-XSRF-TOKEN bootstrap.
// Pass it in from the route owner rather than importing back through the API registry.
export function createMfaApi(request: typeof fetch): MfaApi {
  async function post<T>(path: string, body?: object): Promise<MfaResponse<T>> {
    const response = await request(path, {
      method: "POST",
      credentials: "same-origin",
      ...(body
        ? {
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
          }
        : {}),
    });
    const result = await response.json();
    if (!response.ok || result.success !== true) {
      throw new MfaApiError(
        response.status,
        result.message ?? "인증 요청을 처리하지 못했습니다.",
        result.errors ?? [],
        result.meta ?? {},
      );
    }
    return result as MfaResponse<T>;
  }
  return {
    verify: (code) => post<MfaLogin>("/api/common/mfa/verify", { code }),
    resend: () => post<MfaDelivery>("/api/common/mfa/resend"),
  };
}

import { hasNetworkTransport } from "../../shared/network";

export interface SessionTiming {
  remainingSeconds: number;
  sessionMinutes: number;
  serverRespondedAt: string;
}
export class SessionApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
export interface SessionApi {
  remaining(): Promise<SessionTiming>;
  extend(): Promise<SessionTiming>;
}
export function sessionTiming(value: unknown): SessionTiming | null {
  if (!value || typeof value !== "object") return null;
  const data = value as Partial<SessionTiming>;
  return Number.isSafeInteger(data.remainingSeconds) &&
    data.remainingSeconds! >= 0 &&
    Number.isSafeInteger(data.sessionMinutes) &&
    data.sessionMinutes! > 0 &&
    typeof data.serverRespondedAt === "string" &&
    Number.isFinite(Date.parse(data.serverRespondedAt))
    ? (data as SessionTiming)
    : null;
}
export function createSessionApi(request: typeof fetch): SessionApi {
  async function call(path: string, method: string): Promise<SessionTiming> {
    const response = await request(path, {
      method,
      credentials: "same-origin",
    });
    const result = await response.json();
    if (!response.ok || result.success !== true)
      throw new SessionApiError(
        response.status,
        result.message ?? "세션 요청을 처리하지 못했습니다.",
        result.errors ?? [],
      );
    const timing = sessionTiming(result.data);
    if (!timing) throw new Error("세션 시간을 확인하지 못했습니다.");
    return timing;
  }
  return {
    remaining: () => call("/api/common/session/remaining", "GET"),
    extend: () => call("/api/common/session/extend", "POST"),
  };
}

/** Install once at the application boundary, using the original fetch, never the installed wrapper. */
export function createSessionFetch(
  request: typeof fetch,
  callbacks: {
    onTiming: (timing: SessionTiming) => void;
    onExpired: () => void;
  },
): typeof fetch {
  // The bootstrap transport already owns CSRF, 401/403 and timing delivery.
  if (typeof window !== "undefined" && hasNetworkTransport(window))
    return window.fetch;
  let bootstrap: Promise<void> | null = null;
  function csrfCookie() {
    const value = document.cookie
      .split(";")
      .map((item) => item.trim())
      .find((item) => item.startsWith("XSRF-TOKEN="));
    if (!value) return null;
    try {
      return decodeURIComponent(value.substring("XSRF-TOKEN=".length));
    } catch {
      return null;
    }
  }
  async function token() {
    if (!csrfCookie()) {
      if (!bootstrap)
        bootstrap = request("/api/health", {
          credentials: "same-origin",
          cache: "no-store",
        })
          .then((response) => {
            if (!response.ok)
              throw new Error("요청 토큰을 준비하지 못했습니다.");
          })
          .finally(() => {
            bootstrap = null;
          });
      await bootstrap;
    }
    const value = csrfCookie();
    if (!value)
      throw new SessionApiError(403, "요청 토큰을 준비하지 못했습니다.");
    return value;
  }
  return (async (input: RequestInfo | URL, init?: RequestInit) => {
    const path =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.href
          : input.url;
    const url = new URL(path, window.location.origin);
    if (
      url.origin !== window.location.origin ||
      !url.pathname.startsWith("/api/")
    )
      return request(input, init);
    const method = (
      init?.method ?? (input instanceof Request ? input.method : "GET")
    ).toUpperCase();
    const headers = new Headers(
      input instanceof Request ? input.headers : undefined,
    );
    new Headers(init?.headers).forEach((value, key) => headers.set(key, value));
    if (!["GET", "HEAD", "OPTIONS"].includes(method))
      headers.set("X-XSRF-TOKEN", await token());
    const response = await request(input, {
      ...init,
      credentials: "same-origin",
      headers,
    });
    if (response.status === 401) {
      callbacks.onExpired();
      return response;
    }
    const seconds = response.headers.get("X-Session-Remaining-Seconds");
    const minutes = response.headers.get("X-Session-Minutes");
    let timing =
      seconds !== null && minutes !== null
        ? sessionTiming({
            remainingSeconds: Number(seconds),
            sessionMinutes: Number(minutes),
            serverRespondedAt: response.headers.get(
              "X-Session-Server-Responded-At",
            ),
          })
        : null;
    if (
      !timing &&
      response.headers.get("content-type")?.includes("application/json")
    ) {
      try {
        const body = await response.clone().json();
        timing = sessionTiming(body.meta) ?? sessionTiming(body.data);
      } catch {
        /* A malformed envelope must not renew the local countdown. */
      }
    }
    if (timing) callbacks.onTiming(timing);
    return response;
  }) as typeof fetch;
}

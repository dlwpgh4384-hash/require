/** 서버에 닿지 못했을 때(네트워크 끊김 등) 브라우저 영어 문구 「Failed to fetch」 대신 쓰는 우리말(2026-11-24 사용자 결정). */
export const NETWORK_MESSAGE =
  "서버에 연결하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.";

/**
 * fetch가 네트워크 오류(TypeError)로 실패하면 같은 TypeError를 우리말 문구로 다시 던진다.
 * 요청 취소(AbortError)와 서버 응답(4xx·5xx)은 그대로 둔다. 앱 시작 때 한 번만 건다.
 */
const wrapped = new WeakSet<object>();
const transports = new WeakSet<typeof fetch>();
export function hasNetworkTransport(target: { fetch: typeof fetch }) {
  return transports.has(target.fetch);
}
export function wrapNetworkErrors(target: { fetch: typeof fetch }) {
  if (wrapped.has(target)) return;
  wrapped.add(target);
  const original = target.fetch.bind(target);
  let bootstrap: Promise<unknown> | undefined;
  let notified = false;
  target.fetch = (async (...args: Parameters<typeof fetch>) => {
    try {
      const [input, init] = args;
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.href
            : input.url;
      const local =
        url.startsWith("/api/") ||
        (typeof location !== "undefined" &&
          new URL(url, location.href).origin === location.origin &&
          new URL(url, location.href).pathname.startsWith("/api/"));
      if (!local) return await original(...args);
      const method = (
        init?.method ?? (input instanceof Request ? input.method : "GET")
      ).toUpperCase();
      const headers = new Headers(
        init?.headers ?? (input instanceof Request ? input.headers : undefined),
      );
      const csrf = () =>
        typeof document === "undefined"
          ? undefined
          : document.cookie
              .split("; ")
              .find((item) => item.startsWith("XSRF-TOKEN="))
              ?.slice(11);
      if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        if (!csrf()) {
          bootstrap ??= original("/api/health", {
            credentials: "same-origin",
          }).finally(() => {
            bootstrap = undefined;
          });
          await bootstrap;
        }
        const token = csrf();
        if (token) headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
      }
      const response = await original(input, {
        ...init,
        headers,
        credentials: "same-origin",
      });
      if (typeof window !== "undefined") {
        const path = new URL(url, window.location.href).pathname;
        const publicAuth = [
          "/api/common/login",
          "/api/common/password-find",
          "/api/common/mfa/verify",
          "/api/common/mfa/resend",
        ].includes(path);
        if (response.status === 401 && !publicAuth && !notified) {
          notified = true;
          window.dispatchEvent(
            new CustomEvent("account-session-expired", {
              detail: "다시 로그인해 주세요.",
            }),
          );
        } else if (response.status === 403 && !notified) {
          notified = true;
          window.dispatchEvent(new Event("account-permissions-changed"));
          queueMicrotask(() => {
            notified = false;
          });
        } else if (response.ok) notified = false;
        if (response.status !== 401) {
          const seconds = response.headers.get("X-Session-Remaining-Seconds");
          const minutes = response.headers.get("X-Session-Minutes");
          const respondedAt = response.headers.get(
            "X-Session-Server-Responded-At",
          );
          if (seconds !== null && minutes !== null && respondedAt !== null) {
            window.dispatchEvent(
              new CustomEvent("account-session-timing", {
                detail: {
                  remainingSeconds: Number(seconds),
                  sessionMinutes: Number(minutes),
                  serverRespondedAt: respondedAt,
                },
              }),
            );
          }
        }
      }
      return response;
    } catch (failure) {
      if (failure instanceof TypeError) throw new TypeError(NETWORK_MESSAGE);
      throw failure;
    }
  }) as typeof fetch;
  transports.add(target.fetch);
}

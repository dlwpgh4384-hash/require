import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  createSessionApi,
  createSessionFetch,
  SessionApiError,
  type SessionTiming,
} from "./sessionApi";

const timing: SessionTiming = {
  remainingSeconds: 720,
  sessionMinutes: 12,
  serverRespondedAt: "2026-01-02T03:04:05Z",
};
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
beforeEach(() => {
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
});
afterEach(() => {
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
});

describe("session API contract", () => {
  it("uses the approved methods and paths without identity or body", async () => {
    const request = vi
      .fn()
      .mockImplementation(async () =>
        response({ success: true, meta: {}, data: timing }),
      );
    const api = createSessionApi(request as typeof fetch);
    expect(await api.remaining()).toEqual(timing);
    expect(await api.extend()).toEqual(timing);
    expect(request).toHaveBeenNthCalledWith(
      1,
      "/api/common/session/remaining",
      { method: "GET", credentials: "same-origin" },
    );
    expect(request).toHaveBeenNthCalledWith(2, "/api/common/session/extend", {
      method: "POST",
      credentials: "same-origin",
    });
  });
  it.each([400, 401, 403])(
    "preserves status and original Korean error (%s)",
    async (status) => {
      const request = vi.fn().mockResolvedValue(
        response(
          {
            success: false,
            meta: {},
            message: "다시 확인해 주세요.",
            errors: [{ field: "session", message: "세션을 확인해 주세요." }],
          },
          status,
        ),
      );
      await expect(
        createSessionApi(request as typeof fetch).extend(),
      ).rejects.toMatchObject({
        status,
        message: "다시 확인해 주세요.",
        errors: [{ field: "session", message: "세션을 확인해 주세요." }],
      });
    },
  );
  it("rejects missing timing instead of inventing a duration", async () => {
    const request = vi
      .fn()
      .mockResolvedValue(response({ success: true, meta: {}, data: {} }));
    await expect(
      createSessionApi(request as typeof fetch).remaining(),
    ).rejects.toThrow("세션 시간을 확인하지 못했습니다.");
  });
});

describe("single session fetch boundary", () => {
  it("bootstraps CSRF once through health then sends cookie token without recursion", async () => {
    const raw = vi.fn().mockImplementation(async (path: string) => {
      if (path === "/api/health") {
        document.cookie = "XSRF-TOKEN=csrf%2Btoken; path=/";
        return response({ status: "UP" });
      }
      return response({ success: true, meta: timing, data: {} });
    });
    const onTiming = vi.fn();
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming,
      onExpired: vi.fn(),
    });
    await Promise.all([
      wrapped("/api/common/session/extend", { method: "POST" }),
      wrapped("/api/common/session/extend", { method: "POST" }),
    ]);
    expect(
      raw.mock.calls.filter(([path]) => path === "/api/health"),
    ).toHaveLength(1);
    const writes = raw.mock.calls.filter(
      ([path]) => path === "/api/common/session/extend",
    );
    expect(writes).toHaveLength(2);
    for (const [, init] of writes) {
      expect(init.credentials).toBe("same-origin");
      expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("csrf+token");
    }
    expect(onTiming).toHaveBeenCalledWith(timing);
  });
  it("uses an existing CSRF cookie without a health request", async () => {
    document.cookie = "XSRF-TOKEN=csrf-token; path=/";
    const raw = vi
      .fn()
      .mockResolvedValue(response({ success: true, meta: timing }));
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming: vi.fn(),
      onExpired: vi.fn(),
    });
    await wrapped("/api/common/session/extend", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
    });
    expect(raw).toHaveBeenCalledTimes(1);
    expect(new Headers(raw.mock.calls[0][1].headers).get("Content-Type")).toBe(
      "application/json",
    );
  });
  it("GET never bootstraps or sends a state-changing request", async () => {
    const raw = vi
      .fn()
      .mockResolvedValue(response({ success: true, data: timing, meta: {} }));
    const onTiming = vi.fn();
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming,
      onExpired: vi.fn(),
    });
    const result = await wrapped("/api/common/session/remaining");
    expect(raw).toHaveBeenCalledTimes(1);
    expect(new Headers(raw.mock.calls[0][1].headers).has("X-XSRF-TOKEN")).toBe(
      false,
    );
    expect(onTiming).toHaveBeenCalledWith(timing);
    expect(await result.json()).toMatchObject({ success: true, data: timing });
  });
  it("binary responses supply timing through server-generated headers", async () => {
    const raw = vi.fn().mockResolvedValue(
      new Response(new Uint8Array([1, 2]), {
        headers: {
          "Content-Type": "application/octet-stream",
          "X-Session-Remaining-Seconds": "720",
          "X-Session-Minutes": "12",
          "X-Session-Server-Responded-At": timing.serverRespondedAt,
        },
      }),
    );
    const onTiming = vi.fn();
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming,
      onExpired: vi.fn(),
    });
    const result = await wrapped("/api/common/users/excel");
    expect(onTiming).toHaveBeenCalledWith(timing);
    expect(new Uint8Array(await result.arrayBuffer())).toEqual(
      new Uint8Array([1, 2]),
    );
  });
  it("401 expires session but 403 does not erase drafts", async () => {
    const raw = vi
      .fn()
      .mockResolvedValueOnce(response({ success: false }, 403))
      .mockResolvedValueOnce(response({ success: false }, 401));
    const onExpired = vi.fn();
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming: vi.fn(),
      onExpired,
    });
    await wrapped("/api/common/session/remaining");
    expect(onExpired).not.toHaveBeenCalled();
    await wrapped("/api/common/session/remaining");
    expect(onExpired).toHaveBeenCalledTimes(1);
  });
  it("a bootstrap without a cookie stops the write", async () => {
    const raw = vi.fn().mockResolvedValue(response({ status: "UP" }));
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming: vi.fn(),
      onExpired: vi.fn(),
    });
    await expect(
      wrapped("/api/common/session/extend", { method: "POST" }),
    ).rejects.toBeInstanceOf(SessionApiError);
    expect(raw).toHaveBeenCalledTimes(1);
    expect(raw.mock.calls[0][0]).toBe("/api/health");
  });
  it("never adds session credentials or CSRF to an external target", async () => {
    const raw = vi.fn().mockResolvedValue(new Response("external"));
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming: vi.fn(),
      onExpired: vi.fn(),
    });
    await wrapped("https://example.invalid/api/report", { method: "POST" });
    expect(raw).toHaveBeenCalledWith("https://example.invalid/api/report", {
      method: "POST",
    });
  });
  it("malformed timing never renews the timer", async () => {
    const raw = vi
      .fn()
      .mockResolvedValue(
        response({ success: true, meta: { ...timing, remainingSeconds: -1 } }),
      );
    const onTiming = vi.fn();
    const wrapped = createSessionFetch(raw as typeof fetch, {
      onTiming,
      onExpired: vi.fn(),
    });
    await wrapped("/api/common/session/remaining");
    expect(onTiming).not.toHaveBeenCalled();
  });
});

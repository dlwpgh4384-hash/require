import { afterEach, describe, expect, it, vi } from "vitest";
import { wrapNetworkErrors } from "../../shared/network";

afterEach(() => {
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  vi.restoreAllMocks();
});
describe("single shared session fetch", () => {
  it("bootstraps via health and sends same-origin credentials and cookie token", async () => {
    const request = vi.fn(async (input: RequestInfo | URL) => {
      if (input === "/api/health")
        document.cookie = "XSRF-TOKEN=issued-token; path=/";
      return new Response("{}", { status: 200 });
    });
    const target = { fetch: request as typeof fetch };
    wrapNetworkErrors(target);
    wrapNetworkErrors(target);
    await target.fetch("/api/common/login", { method: "POST", body: "{}" });
    expect(request).toHaveBeenCalledTimes(2);
    const calls = request.mock.calls as unknown as [
      RequestInfo | URL,
      RequestInit,
    ][];
    expect(calls[0][0]).toBe("/api/health");
    expect(calls[1][1].credentials).toBe("same-origin");
    expect(new Headers(calls[1][1].headers).get("X-XSRF-TOKEN")).toBe(
      "issued-token",
    );
  });
  it("never forwards CSRF to an external destination", async () => {
    document.cookie = "XSRF-TOKEN=local-token; path=/";
    const request = vi.fn().mockResolvedValue(new Response("{}"));
    const target = { fetch: request as typeof fetch };
    wrapNetworkErrors(target);
    await target.fetch("https://example.invalid/external-resource", {
      method: "POST",
    });
    expect(request.mock.calls[0][1]).toEqual({ method: "POST" });
  });
  it("coalesces expired-session notices without swallowing the 401 response", async () => {
    const expired = vi.fn();
    window.addEventListener("account-session-expired", expired);
    try {
      const request = vi
        .fn()
        .mockImplementation(async () => new Response("{}", { status: 401 }));
      const target = { fetch: request as typeof fetch };
      wrapNetworkErrors(target);
      expect((await target.fetch("/api/common/my-info")).status).toBe(401);
      await target.fetch("/api/common/projects");
      expect(expired).toHaveBeenCalledOnce();
    } finally {
      window.removeEventListener("account-session-expired", expired);
    }
  });
  it("does not classify incorrect login credentials as session expiry", async () => {
    const expired = vi.fn();
    window.addEventListener("account-session-expired", expired);
    document.cookie = "XSRF-TOKEN=local-token; path=/";
    try {
      const target = {
        fetch: vi
          .fn()
          .mockResolvedValue(
            new Response("{}", { status: 401 }),
          ) as typeof fetch,
      };
      wrapNetworkErrors(target);
      await target.fetch("/api/common/login", { method: "POST" });
      expect(expired).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener("account-session-expired", expired);
    }
  });
});

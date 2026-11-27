import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { wrapNetworkErrors } from "../../shared/network";
import { SessionProvider, SessionTimer } from "./SessionProvider";
import { createSessionApi, createSessionFetch } from "./sessionApi";

const started = "2026-01-02T03:04:05Z";
const initial = {
  remainingSeconds: 1800,
  sessionMinutes: 30,
  serverRespondedAt: started,
};
const raw = vi.fn<typeof fetch>();
const original = window.fetch;
const timingEvent = vi.fn();
const expiredEvent = vi.fn();

function reply(status = 200, seconds = 1800) {
  return new Response(
    JSON.stringify({ success: status === 200, meta: {}, data: initial }),
    {
      status,
      headers: {
        "Content-Type": "application/json",
        "X-Session-Remaining-Seconds": String(seconds),
        "X-Session-Minutes": "30",
        "X-Session-Server-Responded-At": new Date(Date.now()).toISOString(),
      },
    },
  );
}

beforeAll(() => {
  window.fetch = raw;
  wrapNetworkErrors(window);
  window.addEventListener("account-session-timing", timingEvent);
  window.addEventListener("account-session-expired", expiredEvent);
});
beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(started));
  raw.mockReset();
  timingEvent.mockClear();
  expiredEvent.mockClear();
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  raw.mockImplementation(async (input) => {
    if (input === "/api/health") {
      document.cookie = "XSRF-TOKEN=bootstrap-token; path=/";
      return new Response(JSON.stringify({ status: "UP" }), { status: 200 });
    }
    return reply();
  });
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
});
afterAll(() => {
  window.fetch = original;
  window.removeEventListener("account-session-timing", timingEvent);
  window.removeEventListener("account-session-expired", expiredEvent);
});

function transport() {
  const supplied = window.fetch.bind(window);
  const callbacks = { onTiming: vi.fn(), onExpired: vi.fn() };
  const request = createSessionFetch(supplied, callbacks);
  return { supplied, request, callbacks };
}

describe("UI-34 bootstrap-owned transport", () => {
  it("does not wrap bootstrap fetch and bootstraps CSRF only once", async () => {
    const { supplied, request, callbacks } = transport();
    expect(request).toBe(window.fetch);
    expect(request).not.toBe(supplied);
    await request("/api/common/session/extend", { method: "POST" });
    expect(raw).toHaveBeenCalledTimes(2);
    expect(raw.mock.calls[0][0]).toBe("/api/health");
    expect(raw.mock.calls[1][0]).toBe("/api/common/session/extend");
    expect(new Headers(raw.mock.calls[1][1]?.headers).get("X-XSRF-TOKEN")).toBe(
      "bootstrap-token",
    );
    expect(timingEvent).toHaveBeenCalledTimes(1);
    expect(callbacks.onTiming).not.toHaveBeenCalled();
    expect(callbacks.onExpired).not.toHaveBeenCalled();
  });

  it("ordinary business responses update the timer without a session or notification poll", async () => {
    const { request } = transport();
    const api = createSessionApi(request);
    render(
      <SessionProvider
        api={api}
        initial={initial}
        discardDrafts={vi.fn()}
        onExpired={vi.fn()}
      >
        <SessionTimer />
      </SessionProvider>,
    );
    act(() => vi.advanceTimersByTime(60000));
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:00");
    await act(async () => {
      await request("/api/common/my-info");
    });
    expect(screen.getByTestId("session-left")).toHaveTextContent("30:00");
    expect(raw).toHaveBeenCalledTimes(1);
    expect(raw.mock.calls[0][0]).toBe("/api/common/my-info");
    act(() => vi.advanceTimersByTime(10000));
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:50");
    expect(raw).toHaveBeenCalledTimes(1);
  });

  it("a protected 401 emits one expiration event and no timing or route callback", async () => {
    raw.mockResolvedValueOnce(reply(401));
    const { request, callbacks } = transport();
    await request("/api/common/session/remaining");
    expect(expiredEvent).toHaveBeenCalledTimes(1);
    expect(timingEvent).not.toHaveBeenCalled();
    expect(callbacks.onExpired).not.toHaveBeenCalled();
    // Reset the bootstrap's notification latch through the next successful response.
    await request("/api/common/session/remaining");
  });

  it("binary responses preserve the body while publishing timing headers", async () => {
    raw.mockResolvedValueOnce(
      new Response(new Uint8Array([1, 2]), {
        headers: {
          "Content-Type": "application/octet-stream",
          "X-Session-Remaining-Seconds": "90",
          "X-Session-Minutes": "30",
          "X-Session-Server-Responded-At": started,
        },
      }),
    );
    const { request } = transport();
    const result = await request("/api/common/users/excel");
    expect(new Uint8Array(await result.arrayBuffer())).toEqual(
      new Uint8Array([1, 2]),
    );
    expect(timingEvent).toHaveBeenCalledTimes(1);
    expect(timingEvent.mock.calls[0][0].detail.remainingSeconds).toBe(90);
  });
});

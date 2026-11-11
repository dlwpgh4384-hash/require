import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider } from "react-router-dom";
import type { ShellMenuResponse } from "./NavigationRoot";
import { createAppRouter } from "./router";

let router: ReturnType<typeof createAppRouter> | undefined;

beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
});
afterEach(() => {
  cleanup();
  router?.dispose();
  router = undefined;
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function response(): ShellMenuResponse {
  return {
    success: true,
    data: [
      {
        menuId: "route-leaf",
        upperMenuId: null,
        menuNm: "등록 화면",
        scrnId: "ADDED",
        menuLvl: 1,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [{ scrnId: "ADDED", available: true, pcOnlyYn: "N" }],
    },
  };
}
function respond(body = response()) {
  const request = vi.fn(async (url: string, _init?: RequestInit) => ({
    ok: true,
    json: async () =>
      url === "/api/common/menus" ? body : preferenceResponse(url),
  }));
  vi.stubGlobal("fetch", request);
  return request;
}
function preferenceResponse(url: string) {
  return {
    success: true,
    meta: {},
    data: {
      prefTypeCd: url.split("/").at(-2),
      prefKey: "GLOBAL",
      found: false,
      prefVal: null,
      modDttm: null,
    },
  };
}
function menuCalls(request: ReturnType<typeof vi.fn>) {
  return request.mock.calls.filter(([url]) => url === "/api/common/menus");
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  return render(<RouterProvider router={router} />);
}
function expectSingleShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("complementary")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
}

describe("application route registration", () => {
  it("root selects the database screen through the existing anonymous relative API call", async () => {
    const request = respond();
    mount("/");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    await waitFor(() =>
      expect(router?.state.location.pathname).toBe("/screens/ADDED"),
    );
    expectSingleShell();
    expect(request).toHaveBeenCalledWith("/api/common/menus", {
      signal: expect.any(AbortSignal),
    });
    expect(menuCalls(request)).toHaveLength(1);
  });

  it("dynamic direct entry and recreation render the registered placeholder, not a second shell", async () => {
    respond();
    const first = mount("/screens/ADDED");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expectSingleShell();
    first.unmount();
    router?.dispose();
    mount("/screens/ADDED");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expectSingleShell();
    expect(router?.state.location.pathname).toBe("/screens/ADDED");
  });

  it.each(["/screens/UNKNOWN", "/unregistered/path"])(
    "keeps %s in the common unknown state and returns to the first database screen",
    async (path) => {
      respond();
      mount(path);
      expect(await screen.findByText("없는 화면입니다")).toBeVisible();
      expectSingleShell();
      expect(router?.state.location.pathname).toBe(path);
      fireEvent.click(screen.getByRole("link", { name: "첫 화면으로 가기" }));
      expect(
        await screen.findByRole("region", { name: "ADDED 본문" }),
      ).toHaveTextContent("준비 중");
      expect(router?.state.location.pathname).toBe("/screens/ADDED");
    },
  );

  it("direct entry errors retry the existing client without redirecting or exposing stale content", async () => {
    const menuRequest = vi
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, json: async () => response() });
    const request = vi.fn(async (url: string) =>
      url === "/api/common/menus"
        ? menuRequest()
        : { ok: true, json: async () => preferenceResponse(url) },
    );
    vi.stubGlobal("fetch", request);
    mount("/screens/ADDED");
    const retries = await screen.findAllByRole("button", { name: "다시 시도" });
    expect(retries).toHaveLength(2);
    expect(
      screen.queryByRole("region", { name: "ADDED 본문" }),
    ).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/screens/ADDED");
    fireEvent.click(retries[0]);
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expect(router?.state.location.pathname).toBe("/screens/ADDED");
    expectSingleShell();
    expect(menuCalls(request)).toHaveLength(2);
  });

  it("root preserves a successful empty menu without a redirect or retry error", async () => {
    respond({ success: true, data: [], meta: { screenAccess: [] } });
    mount("/");
    expect(await screen.findAllByText("등록된 메뉴 없음")).toHaveLength(2);
    expect(
      screen.queryByRole("button", { name: "다시 시도" }),
    ).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/");
    expectSingleShell();
  });

  it.each(["/", "/screens/ADDED", "/unregistered/path"])(
    "provides one shared preferences scope at %s without creating settings or excluded API calls",
    async (path) => {
      const request = respond();
      mount(path);
      await waitFor(() =>
        expect(
          request.mock.calls.filter(([url]) => url !== "/api/common/menus"),
        ).toHaveLength(5),
      );
      await act(async () => {});
      expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
        "data-page-size",
      );
      expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
        "data-density",
      );
      expect(screen.getAllByTestId("user-preferences")).toHaveLength(1);
      expectSingleShell();
      const preferenceCalls = request.mock.calls.filter(
        ([url]) => url !== "/api/common/menus",
      );
      expect(preferenceCalls.map(([url]) => url).sort()).toEqual([
        "/api/common/user-preferences/DENSE/GLOBAL",
        "/api/common/user-preferences/FAV/GLOBAL",
        "/api/common/user-preferences/MENU_FOLD/GLOBAL",
        "/api/common/user-preferences/PAGE_SIZE/GLOBAL",
        "/api/common/user-preferences/RECENT/GLOBAL",
      ]);
      expect(preferenceCalls.every(([, init]) => !init?.method)).toBe(true);
    },
  );

  it("keeps the same preferences scope across child route navigation", async () => {
    const request = respond();
    mount("/screens/ADDED");
    await waitFor(() =>
      expect(
        request.mock.calls.filter(([url]) => url !== "/api/common/menus"),
      ).toHaveLength(5),
    );
    await act(async () => {});
    const provider = screen.getByTestId("user-preferences");
    const reads = request.mock.calls.filter(
      ([url]) => url !== "/api/common/menus",
    ).length;
    await act(async () => {
      await router!.navigate("/unregistered/path");
    });
    expect(await screen.findByText("없는 화면입니다")).toBeVisible();
    expect(screen.getByTestId("user-preferences")).toBe(provider);
    expect(
      request.mock.calls.filter(([url]) => url !== "/api/common/menus"),
    ).toHaveLength(reads);
    expectSingleShell();
  });

  it("keeps the shell available without claiming default settings when preference reads fail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (url !== "/api/common/menus")
          throw new Error("private connection error");
        return { ok: true, json: async () => response() };
      }),
    );
    mount("/screens/ADDED");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
      "data-page-size",
    );
    expect(
      screen.queryByText("private connection error"),
    ).not.toBeInTheDocument();
    expectSingleShell();
  });
});

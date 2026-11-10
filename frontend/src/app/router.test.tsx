import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
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
  const request = vi
    .fn()
    .mockResolvedValue({ ok: true, json: async () => body });
  vi.stubGlobal("fetch", request);
  return request;
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
    expect(request).toHaveBeenCalledTimes(1);
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
    const request = vi
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, json: async () => response() });
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
    expect(request).toHaveBeenCalledTimes(2);
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
});

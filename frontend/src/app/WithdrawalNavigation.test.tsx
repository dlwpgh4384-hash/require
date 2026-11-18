import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider } from "react-router-dom";
import type { ShellMenuResponse } from "./NavigationRoot";
import { createAppRouter } from "./router";

let router: ReturnType<typeof createAppRouter> | undefined;
const screens = [
  {
    id: "UI-05",
    menuId: "M007",
    parent: "G003",
    title: "철수 리스트",
    root: "withdrawal-page",
  },
  {
    id: "UI-28",
    menuId: "M009",
    parent: "G004",
    title: "인력 운영 보드",
    root: "staffing-board-page",
  },
  {
    id: "UI-17",
    menuId: "M010",
    parent: "G004",
    title: "인력 수요 예측",
    root: "workforce-forecast-page",
  },
  {
    id: "UI-29",
    menuId: "M011",
    parent: "G004",
    title: "영업 파이프라인",
    root: "opportunity-page",
  },
];

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

function respond() {
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      ...["G003", "G004"].map((menuId, index) => ({
        menuId,
        upperMenuId: null,
        menuNm: index === 0 ? "프로젝트·투입" : "인력 계획",
        scrnId: null,
        menuLvl: 1,
        sortOrd: index + 1,
        pcOnlyYn: "N" as const,
        iconNm: null,
      })),
      ...screens.map((entry, index) => ({
        menuId: entry.menuId,
        upperMenuId: entry.parent,
        menuNm: entry.title,
        scrnId: entry.id,
        menuLvl: 2,
        sortOrd: index + 1,
        pcOnlyYn: "N" as const,
        iconNm: null,
      })),
    ],
    meta: {
      screenAccess: screens.map((entry) => ({
        scrnId: entry.id,
        available: true,
        pcOnlyYn: "N",
      })),
    },
  };
  const ok = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const list = vi.fn(async () =>
    ok({ items: [], totalCount: 0, banner: null }),
  );
  const shared = vi.fn(async () => ok({ items: [] }));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/"))
      return ok({
        prefTypeCd: url.split("/").at(-2),
        prefKey: "GLOBAL",
        found: false,
        prefVal: null,
        modDttm: null,
      });
    if (url.startsWith("/api/common/withdrawals?")) return list();
    if (url.startsWith("/api/common/withdrawals/tentative-assignments?"))
      return shared();
    if (/^\/api\/common\/code-groups\/(JOB|EXT_PSBL)\/codes$/.test(url))
      return ok({ items: [] });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, shared, ok };
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  return render(<RouterProvider router={router} />);
}
function expectShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("complementary")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.getAllByTestId("user-preferences")).toHaveLength(1);
}

// REQ-577: intended business users, not authentication or menu-admission roles.
describe.each(["파트장", "관리자", "PM"])("UI-05 handover for %s", () => {
  it("keeps anonymous direct entry and the real feature client", async () => {
    const { request } = respond();
    mount("/screens/UI-05");
    expect(await screen.findByTestId("list-empty")).toBeVisible();
    expect(screen.getByTestId("withdrawal-page")).toHaveAttribute(
      "data-screen-id",
      "UI-05",
    );
    expect(
      screen.getByTestId("withdrawal-download").closest(".screen-actions"),
    ).not.toBeNull();
    expect(request).toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\/common\/withdrawals\?year=\d{4}$/),
      { signal: expect.any(AbortSignal) },
    );
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(
      request.mock.calls
        .filter(([url]) => url.startsWith("/api/common/withdrawals"))
        .every(([, init]) => !init?.headers),
    ).toBe(true);
    expect(router?.state.location.pathname).toBe("/screens/UI-05");
    expectShell();
  });
});

describe("withdrawal route composition", () => {
  it.each(screens)(
    "$id keeps legacy hash entry in the single shell",
    async (entry) => {
      const { request } = respond();
      mount(`/#${entry.id}`);
      expect(await screen.findByTestId(entry.root)).toBeVisible();
      await waitFor(() =>
        expect(router?.state.location.pathname).toBe(`/screens/${entry.id}`),
      );
      if (entry.id === "UI-05") await screen.findByTestId("list-empty");
      else {
        expect(await screen.findByText("해당 원천 자료 없음")).toBeVisible();
        expect(request).toHaveBeenCalledWith(
          "/api/common/withdrawals/tentative-assignments?",
          { signal: expect.any(AbortSignal) },
        );
        expect(
          within(screen.getByTestId(entry.root)).queryByRole("button", {
            name: /저장|등록|삭제/,
          }),
        ).not.toBeInTheDocument();
      }
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expectShell();
    },
  );

  it.each(screens)(
    "$id retries a failed read without leaving its route",
    async (entry) => {
      const { list, shared } = respond();
      const reader = entry.id === "UI-05" ? list : shared;
      reader.mockRejectedValueOnce(new Error("조회 실패"));
      mount(`/screens/${entry.id}`);
      fireEvent.click(
        await screen.findByTestId(
          entry.id === "UI-05" ? "list-retry" : "withdrawal-shared-retry",
        ),
      );
      if (entry.id === "UI-05")
        expect(await screen.findByTestId("list-empty")).toBeVisible();
      else expect(await screen.findByText("해당 원천 자료 없음")).toBeVisible();
      expect(reader).toHaveBeenCalledTimes(2);
      expect(router?.state.location.pathname).toBe(`/screens/${entry.id}`);
      expectShell();
    },
  );

  it("navigation preserves UI-05 filters and the existing preferences provider", async () => {
    respond();
    mount("/screens/UI-05");
    await screen.findByTestId("list-empty");
    fireEvent.change(screen.getByTestId("withdrawal-filter-period"), {
      target: { value: "이미 철수" },
    });
    const preferences = screen.getByTestId("user-preferences");
    await act(async () => {
      await router!.navigate("/screens/UI-28");
    });
    const sharedPage = await screen.findByTestId("staffing-board-page");
    expect(
      await within(sharedPage).findByText("해당 원천 자료 없음"),
    ).toBeVisible();
    expect(screen.getByTestId("withdrawal-download")).not.toBeVisible();
    await act(async () => {
      await router!.navigate("/screens/UI-05");
    });
    expect(screen.getByTestId("withdrawal-filter-period")).toHaveValue(
      "이미 철수",
    );
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expect(
      screen.getByTestId("withdrawal-download").closest(".screen-actions"),
    ).not.toBeNull();
    expectShell();
  });
});

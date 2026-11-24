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
const screens = [
  {
    id: "UI-05",
    menuId: "M007",
    parent: "G003",
    title: "철수 리스트",
    root: "withdrawal-page",
  },
];
/** 아직 만들지 않은 화면(화면 목록에 없음): 메뉴에는 있고 본문은 공통 「준비 중」. */
const pending = [
  { id: "UI-30", menuId: "M030", parent: "G004", title: "미구현 화면" },
];
const menuScreens = [...screens, ...pending];

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
      ...menuScreens.map((entry, index) => ({
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
      screenAccess: menuScreens.map((entry) => ({
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
    if (/^\/api\/common\/code-groups\/(JOB|EXT_PSBL)\/codes$/.test(url))
      return ok({ items: [] });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, ok };
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
    // 엑셀 다운로드는 목록 도구줄에만 있고 제목줄에는 없다(같은 기능 두 개를 하나로, 사용자 결정 2026-11-18).
    expect(
      screen
        .getByTestId("list-download")
        .closest("[data-testid=withdrawal-list]"),
    ).not.toBeNull();
    expect(
      document.querySelector(".screen-actions [data-testid=list-download]"),
    ).toBeNull();
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
      await screen.findByTestId("list-empty");
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expectShell();
    },
  );

  it.each(screens)(
    "$id retries a failed read without leaving its route",
    async (entry) => {
      const { list } = respond();
      list.mockRejectedValueOnce(new Error("조회 실패"));
      mount(`/screens/${entry.id}`);
      fireEvent.click(await screen.findByTestId("list-retry"));
      expect(await screen.findByTestId("list-empty")).toBeVisible();
      expect(list).toHaveBeenCalledTimes(2);
      expect(router?.state.location.pathname).toBe(`/screens/${entry.id}`);
      expectShell();
    },
  );

  // 인력 수요 예측은 아직 만들지 않았다: 공통 「준비 중」이 보이고 가배정 자료를 읽지 않는다.
  it.each(pending)(
    "$id is not registered yet and shows the common placeholder",
    async (entry) => {
      const { request } = respond();
      mount(`/screens/${entry.id}`);
      expect(
        await screen.findByRole("region", { name: `${entry.id} 본문` }),
      ).toHaveTextContent("준비 중");
      expect(
        request.mock.calls.some(([url]) =>
          url.startsWith("/api/common/withdrawals/tentative-assignments"),
        ),
      ).toBe(false);
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
      await router!.navigate("/screens/UI-30");
    });
    const placeholder = await screen.findByRole("region", {
      name: "UI-30 본문",
    });
    expect(placeholder).toHaveTextContent("준비 중");
    expect(screen.getByTestId("list-download")).not.toBeVisible();
    await act(async () => {
      await router!.navigate("/screens/UI-05");
    });
    expect(screen.getByTestId("withdrawal-filter-period")).toHaveValue(
      "이미 철수",
    );
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expect(
      screen
        .getByTestId("list-download")
        .closest("[data-testid=withdrawal-list]"),
    ).not.toBeNull();
    expectShell();
  });
});

import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider } from "react-router-dom";
import { createAppRouter } from "./router";
import type { ShellMenuResponse } from "./NavigationRoot";
import type { BillList } from "../pages/bills/bills";
import type { BillingOptions } from "../pages/commonops/billingLookups";
import type { BillCheckResult } from "../pages/billchecks/billChecks";

let router: ReturnType<typeof createAppRouter> | undefined;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
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
  vi.useRealTimers();
});

function respond(available = true) {
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      {
        menuId: "G005",
        upperMenuId: null,
        menuNm: "매출",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 5,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "M013",
        upperMenuId: "G005",
        menuNm: "청구·입금",
        scrnId: "UI-13",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "Y",
        iconNm: null,
      },
    ],
    meta: { screenAccess: [{ scrnId: "UI-13", available, pcOnlyYn: "Y" }] },
  };
  const result: BillList = {
    items: [],
    readOnly: false,
    summary: {
      yearBillCount: 0,
      yearBillTotal: 0,
      currentMonthBillTotal: 0,
      unpaidCount: 0,
      unpaidTotal: 0,
      overdueCount: 0,
    },
    totals: {
      supplyAmount: 0,
      vatAmount: 0,
      totalAmount: 0,
      receiptSumAmount: 0,
      unpaidAmount: 0,
    },
  };
  const options: BillingOptions = {
    projects: [],
    statusOptions: [],
    defaultBillMonth: "202611",
    defaultBillDate: "2026-11-15",
    defaultPayDueDate: "2026-12-25",
    estimatedSupplyAmount: null,
    vatRate: 10,
    monthClosed: false,
  };
  const checks: BillCheckResult = {
    checkMonth: "202611",
    items: [],
    plannedBasis: true,
    summary: {
      totalCount: 0,
      missingCount: 0,
      underCount: 0,
      overCount: 0,
      matchedCount: 0,
    },
    calculationMethod: "점검 API에서 읽은 계산 방법",
    months: ["202611"],
    lastClosedMonth: "202610",
  };
  const list = vi.fn(async () => ok(result));
  const checkList = vi.fn(async () => ok(checks));
  const dunning = vi.fn(async () => ok({ cards: [], items: [] }));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    const path = url.split("?")[0];
    if (path === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (path.startsWith("/api/common/user-preferences/"))
      return ok({
        prefTypeCd: path.split("/").at(-2),
        prefKey: "GLOBAL",
        found: false,
        prefVal: null,
        modDttm: null,
      });
    if (path === "/api/common/bills") return list();
    if (path === "/api/common/bill-options") return ok(options);
    if (path === "/api/common/bill-checks") return checkList();
    if (path === "/api/common/bill-dunning") return dunning();
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, checkList, dunning };
}
function mount(path = "/screens/UI-13") {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
function expectSingleShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("complementary")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
}

it.each(["/screens/UI-13", "/#UI-13"])(
  "%s dispatches the registered billing page and direct API clients without authentication",
  async (path) => {
    const { request, checkList, dunning } = respond();
    mount(path);
    await screen.findByTestId("list-empty");
    expect(screen.getAllByTestId("billing-page")).toHaveLength(1);
    expect(screen.getByTestId("billing-page")).toHaveAttribute(
      "data-menu-id",
      "M013",
    );
    expect(screen.getByTestId("billing-page")).toHaveAttribute(
      "data-group-id",
      "G005",
    );
    expect(
      within(screen.getByTestId("billing-page")).queryByText("준비 중"),
    ).not.toBeInTheDocument();
    expect(request).toHaveBeenCalledWith("/api/common/bills?year=2026", {
      signal: expect.any(AbortSignal),
    });
    expect(request).toHaveBeenCalledWith("/api/common/bill-options", {
      signal: expect.any(AbortSignal),
    });
    // 탭 이름의 건수(청구 누락 점검 n · 미수 알림 (n))를 처음에 한 번 읽는다(시안 billTabs).
    await waitFor(() => expect(checkList).toHaveBeenCalledTimes(1));
    expect(dunning).toHaveBeenCalledTimes(1);
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(
      screen.getByRole("columnheader", { name: /청구번호/ }),
    ).toBeVisible();
    expect(await screen.findByTestId("bill-register")).toBeVisible();
    expect(screen.getByTestId("billing-title-actions")).toContainElement(
      screen.getByTestId("bill-register"),
    );
    expect(screen.getByText("조건에 맞는 결과가 없습니다.")).toBeVisible();
    expect(screen.getByTestId("bill-tab-dunning")).toHaveTextContent("미수 알림 (0)");
    expectSingleShell();
  },
);

it("M013 under G005 opens UI-13 through the existing router and preference provider", async () => {
  respond();
  mount("/");
  const preferences = await screen.findByTestId("user-preferences");
  const group = await screen.findByTestId("menu-toggle-g005");
  // 첫 화면이 다 열린 뒤에 메뉴 묶음 상태를 본다(탭 건수를 읽는 요청이 더 있어 열리는 때가 늦을 수 있음).
  await screen.findByTestId("list-empty");
  // The first available screen opens automatically, expanding its menu group.
  if (group.getAttribute("aria-expanded") === "false") fireEvent.click(group);
  expect(group).toHaveAttribute("aria-expanded", "true");
  fireEvent.click(screen.getByRole("link", { name: "청구·입금" }));
  await screen.findByTestId("list-empty");
  expect(router?.state.location.pathname).toBe("/screens/UI-13");
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  expect(screen.getAllByTestId("billing-page")).toHaveLength(1);
  expectSingleShell();
});

it("workflow tabs call their feature-local clients within UI-13 without another route", async () => {
  const { request, checkList, dunning } = respond();
  mount();
  await screen.findByTestId("list-empty");
  await waitFor(() => expect(checkList).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByTestId("bill-tab-checks"));
  expect(
    await screen.findByText("이번 달은 아직 끝나지 않아 투입 예정 기준으로 봅니다."),
  ).toBeVisible();
  expect(screen.getByTestId("bill-checks-panel")).toBeVisible();
  expect(checkList).toHaveBeenCalledTimes(2);
  expect(request).toHaveBeenCalledWith("/api/common/bill-checks", {
    signal: expect.any(AbortSignal),
  });
  fireEvent.click(screen.getByTestId("bill-tab-dunning"));
  const panel = await screen.findByTestId("billing-dunning-panel");
  expect(await within(panel).findByText("조건에 맞는 결과가 없습니다.")).toBeVisible();
  expect(dunning).toHaveBeenCalledTimes(2);
  expect(request).toHaveBeenCalledWith("/api/common/bill-dunning", {
    signal: expect.any(AbortSignal),
  });
  expect(router?.state.location.pathname).toBe("/screens/UI-13");
  expectSingleShell();
});

it("prior-year navigation retains the billing list but removes workflow tabs and registration", async () => {
  const { request, checkList, dunning } = respond();
  mount();
  await screen.findByTestId("list-empty");
  await waitFor(() => expect(checkList).toHaveBeenCalledTimes(1));
  checkList.mockClear();
  dunning.mockClear();
  fireEvent.click(screen.getByTestId("bill-year-prev"));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith("/api/common/bills?year=2025", {
      signal: expect.any(AbortSignal),
    }),
  );
  await screen.findByTestId("list-empty");
  // 시안: 지난 해는 탭 없이 청구 목록만, 읽기 전용 띠
  expect(screen.queryByTestId("bill-tab-list")).not.toBeInTheDocument();
  expect(await screen.findByTestId("bill-lock-banner")).toHaveTextContent(
    "2025년은 지난 해라 읽기만 할 수 있습니다.",
  );
  expect(screen.queryByTestId("bill-tab-checks")).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-tab-dunning")).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-register")).not.toBeInTheDocument();
  expect(checkList).not.toHaveBeenCalled();
  expect(dunning).not.toHaveBeenCalled();
  expectSingleShell();
});

it("billing failure retries the real list client inside the same shell", async () => {
  const { list } = respond();
  list.mockRejectedValueOnce(new Error("청구 조회 연결 실패"));
  mount();
  fireEvent.click(await screen.findByTestId("list-retry"));
  expect(await screen.findByTestId("list-empty")).toBeVisible();
  expect(list).toHaveBeenCalledTimes(2);
  expect(router?.state.location.pathname).toBe("/screens/UI-13");
  expectSingleShell();
});

it("unavailable UI-13 stays behind the existing navigation guard before feature requests", async () => {
  const { list, request } = respond(false);
  mount();
  expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
  expect(screen.queryByTestId("billing-page")).not.toBeInTheDocument();
  expect(list).not.toHaveBeenCalled();
  expect(
    request.mock.calls.some(([url]) =>
      url.startsWith("/api/common/bill-options"),
    ),
  ).toBe(false);
  expectSingleShell();
});

it("mobile UI-13 preserves the PC-only notice without calling billing clients", async () => {
  const { list, checkList, dunning, request } = respond();
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: query === "(max-width: 767px)",
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  mount();
  expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
  expect(screen.queryByTestId("billing-page")).not.toBeInTheDocument();
  expect(list).not.toHaveBeenCalled();
  expect(checkList).not.toHaveBeenCalled();
  expect(dunning).not.toHaveBeenCalled();
  expect(
    request.mock.calls.some(([url]) =>
      url.startsWith("/api/common/bill-options"),
    ),
  ).toBe(false);
  expectSingleShell();
});

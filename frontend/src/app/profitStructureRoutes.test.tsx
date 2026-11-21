import { afterEach, beforeEach, expect, it, vi } from "vitest";
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
import { createAppRouter } from "./router";
import type { ShellMenuResponse } from "./NavigationRoot";
import type { ProfitStructureResult } from "../pages/profitstructures/profitStructures";

let router: ReturnType<typeof createAppRouter> | undefined;
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data: structuredClone(data) }),
});
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

function respond(failRead = false) {
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      {
        menuId: "G007",
        upperMenuId: null,
        menuNm: "재무·자금",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 7,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "M019",
        upperMenuId: "G007",
        menuNm: "전체 수익 구조",
        scrnId: "UI-07",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "M020",
        upperMenuId: "G007",
        menuNm: "영업이익표",
        scrnId: "UI-08",
        menuLvl: 2,
        sortOrd: 2,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "M021",
        upperMenuId: "G007",
        menuNm: "재무상태표",
        scrnId: "UI-09",
        menuLvl: 2,
        sortOrd: 3,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: ["UI-07", "UI-08", "UI-09"].map((scrnId) => ({
        scrnId,
        available: true,
        pcOnlyYn: "N",
      })),
    },
  };
  const summary = {
    revenue: 0,
    grossProfit: 0,
    grossProfitRate: null,
    operatingProfit: 0,
    operatingProfitRate: null,
    idleCostShare: null,
  };
  const data: ProfitStructureResult = {
    periodStart: "202602",
    periodEnd: "202602",
    summary,
    comparison: {
      previousPeriod: summary,
      previousYear: null,
      previousYearVisible: false,
      shareDifferenceUnit: "%p",
    },
    waterfall: {
      revenue: 0,
      costOfSales: 0,
      grossProfit: 0,
      sellingAdministrativeExpense: 0,
      previousAdjustment: 0,
      operatingProfit: 0,
      nonOperatingProfitLoss: 0,
      monthlyCorporateTax: 0,
      netProfit: 0,
    },
    trends: [],
    composition: null,
    compositionVisible: false,
    referenceRevenueVisible: false,
    provisional: false,
    provisionalNotice: null,
    numericRows: [],
  };
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ...ok(null), json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/")) {
      const input = init?.body ? JSON.parse(String(init.body)) : null;
      return ok(
        {
          prefTypeCd: url.split("/").at(-2),
          prefKey: url.split("/").at(-1),
          found: !!input,
          prefVal: input?.prefVal ?? null,
          modDttm: null,
        },
        {
          periodBasis: {
            latestYm: "202602",
            cashBaseYm: "202501",
            closedMonths: [],
          },
        },
      );
    }
    if (url.startsWith("/api/common/profit-structures/excel?"))
      return {
        ok: false,
        status: 400,
        json: async () => ({
          message: "엑셀 조건을 확인해 주세요.",
          errors: [
            { field: "baseMonth", message: "엑셀 조건을 확인해 주세요." },
          ],
        }),
      };
    if (url.startsWith("/api/common/profit-structures?")) {
      if (failRead)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            message: "기간을 확인해 주세요.",
            errors: [
              {
                field: "baseMonth",
                message: "같은 연도의 기준월이어야 합니다.",
              },
            ],
          }),
        };
      return ok(data, { previousPeriodAvailable: false });
    }
    if (url.startsWith("/api/common/operating-profit-statements?")) {
      if (failRead)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            message: "기간을 확인해 주세요.",
            errors: [
              {
                field: "baseMonth",
                message: "같은 연도의 기준월이어야 합니다.",
              },
            ],
          }),
        };
      const metric = {
        amount: null,
        previousYearChangePercent: null,
        previousMonthChangePercent: null,
      };
      return ok({
        year: 2026,
        baseMonth: "202602",
        provisional: true,
        provisionalNotice:
          "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다.",
        summary: {
          cumulativeRevenue: metric,
          cumulativeGrossProfit: metric,
          cumulativeOperatingProfit: metric,
          operatingProfitRate: null,
          baseMonthOperatingProfit: metric,
        },
        numericTable: { columns: [], rows: [] },
      });
    }
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return request;
}
function mount(path = "/screens/UI-07") {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
async function ready() {
  await waitFor(() =>
    expect(screen.getByTestId("profit-summary")).toHaveAttribute(
      "data-period-end",
      "202602",
    ),
  );
}

it("resolves UI-07 through the single shared shell and public feature client", async () => {
  const request = respond();
  mount();
  await ready();
  expect(screen.getAllByTestId("profit-structures-page")).toHaveLength(1);
  expect(
    screen.getAllByRole("heading", { level: 1, name: "전체 수익 구조" }),
  ).toHaveLength(1);
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.getByRole("link", { name: "전체 수익 구조" })).toHaveAttribute(
    "href",
    "/screens/UI-07",
  );
  expect(screen.getByTestId("profit-table-empty")).toBeVisible();
  expect(request).toHaveBeenCalledWith(
    "/api/common/profit-structures?year=2026&periodUnit=month&baseMonth=202602",
    { signal: expect.any(AbortSignal) },
  );
  expect(
    request.mock.calls.some(([url]) =>
      /auth|profit-structures\/options/.test(url),
    ),
  ).toBe(false);
  expect(router?.state.location.pathname).toBe("/screens/UI-07");
});

it.each(["/screens/UI-08", "/#UI-08"])(
  "opens M020/G007 UI-08 at %s in the same shell with public title actions",
  async (path) => {
    const request = respond();
    mount(path);
    expect(await screen.findByTestId("operating-empty")).toBeVisible();
    expect(
      screen.getByRole("heading", { level: 1, name: "영업이익표" }),
    ).toBeVisible();
    expect(screen.getByRole("link", { name: "영업이익표" })).toHaveAttribute(
      "href",
      "/screens/UI-08",
    );
    expect(
      within(screen.getByTestId("screen-actions")).getByRole("button", {
        name: "전년 손익 업로드",
      }),
    ).toBeVisible();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(
      screen.getAllByTestId("operating-profit-statements-page"),
    ).toHaveLength(1);
    expect(
      within(screen.getByTestId("screen-actions")).getByRole("button", {
        name: "전년 손익 양식",
      }),
    ).toBeVisible();
    expect(request).toHaveBeenCalledWith(
      "/api/common/operating-profit-statements?year=2026&baseMonth=202602",
      { signal: expect.any(AbortSignal) },
    );
    expect(
      request.mock.calls.some(([url]) => /auth|permission/.test(url)),
    ).toBe(false);
  },
);

it("opens UI-08 from its existing menu without replacing preferences or adding another shell", async () => {
  const request = respond();
  mount();
  await ready();
  const preferences = screen.getByTestId("user-preferences");
  fireEvent.click(
    within(screen.getByTestId("menu-item-m020")).getByRole("link", {
      name: "영업이익표",
    }),
  );
  expect(await screen.findByTestId("operating-empty")).toBeVisible();
  expect(router?.state.location.pathname).toBe("/screens/UI-08");
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.getAllByTestId("operating-title-actions")).toHaveLength(1);
  expect(request).toHaveBeenCalledWith(
    "/api/common/operating-profit-statements?year=2026&baseMonth=202602",
    { signal: expect.any(AbortSignal) },
  );
});

it("keeps UI-08 validation in the shared route without login redirection or empty success", async () => {
  const request = respond(true);
  mount("/screens/UI-08");
  expect(
    await screen.findByTestId("operating-error-baseMonth"),
  ).toHaveTextContent("같은 연도의 기준월이어야 합니다.");
  expect(screen.queryByTestId("operating-empty")).not.toBeInTheDocument();
  expect(screen.getByTestId("operating-excel")).toBeDisabled();
  expect(router?.state.location.pathname).toBe("/screens/UI-08");
  expect(request.mock.calls.some(([url]) => /auth|permission/.test(url))).toBe(
    false,
  );
});

it("uses the UI-08 Excel client with only the current period and amount unit", async () => {
  const request = respond();
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.startsWith("/api/common/operating-profit-statements/excel?"))
      return {
        ok: false,
        status: 400,
        json: async () => ({
          message: "엑셀 조건을 확인해 주세요.",
          errors: [
            { field: "baseMonth", message: "엑셀 조건을 확인해 주세요." },
          ],
        }),
      };
    return original(url, init);
  });
  mount("/screens/UI-08");
  await screen.findByTestId("operating-empty");
  fireEvent.click(
    within(screen.getByTestId("screen-actions")).getByTestId("operating-excel"),
  );
  expect(await screen.findByTestId("operating-toast")).toHaveTextContent(
    "엑셀 조건을 확인해 주세요.",
  );
  const exports = request.mock.calls.filter(([url]) =>
    url.startsWith("/api/common/operating-profit-statements/excel?"),
  );
  expect(exports).toHaveLength(1);
  expect(
    Object.fromEntries(new URLSearchParams(exports[0][0].split("?")[1])),
  ).toEqual({
    year: "2026",
    baseMonth: "202602",
    amountUnit: (
      screen.getByRole("combobox", { name: "단위" }) as HTMLSelectElement
    ).value,
  });
  expect(exports[0][1]).toBeUndefined();
  expect(router?.state.location.pathname).toBe("/screens/UI-08");
});

it.each(["UI-09"])(
  "keeps %s a placeholder while M019 opens UI-07 without replacing preferences",
  async (id) => {
    const request = respond();
    mount(`/screens/${id}`);
    expect(
      await screen.findByRole("region", { name: `${id} 본문` }),
    ).toHaveTextContent("준비 중");
    expect(
      request.mock.calls.some(([url]) => url.includes("profit-structures")),
    ).toBe(false);
    const preferences = screen.getByTestId("user-preferences");
    fireEvent.click(screen.getByRole("link", { name: "전체 수익 구조" }));
    await ready();
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    await act(async () => {
      await router!.navigate(`/screens/${id}`);
    });
    expect(
      screen.getByRole("region", { name: `${id} 본문` }),
    ).toHaveTextContent("준비 중");
  },
);

it("keeps API field errors on UI-07 instead of redirecting to login", async () => {
  respond(true);
  mount();
  expect(await screen.findByTestId("profit-error-baseMonth")).toHaveTextContent(
    "같은 연도의 기준월이어야 합니다.",
  );
  expect(screen.getByTestId("period-month")).toHaveFocus();
  expect(screen.queryByTestId("profit-table-empty")).not.toBeInTheDocument();
  expect(router?.state.location.pathname).toBe("/screens/UI-07");
});

it("uses the feature-owned Excel client with the current period and reports server validation", async () => {
  const request = respond();
  mount();
  await ready();
  fireEvent.click(
    within(screen.getByTestId("profit-title-actions")).getByTestId(
      "profit-excel",
    ),
  );
  expect(await screen.findByTestId("profit-toast")).toHaveTextContent(
    "엑셀 조건을 확인해 주세요.",
  );
  const exports = request.mock.calls.filter(([url]) =>
    url.startsWith("/api/common/profit-structures/excel?"),
  );
  expect(exports).toHaveLength(1);
  const params = new URLSearchParams(exports[0][0].split("?")[1]);
  expect(params.get("year")).toBe("2026");
  expect(params.get("periodUnit")).toBe("month");
  expect(params.get("baseMonth")).toBe("202602");
  expect(params.has("columns")).toBe(false);
  expect(exports[0][1]).toBeUndefined();
  expect(router?.state.location.pathname).toBe("/screens/UI-07");
});

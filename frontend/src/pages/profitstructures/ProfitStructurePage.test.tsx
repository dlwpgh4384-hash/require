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
import { MemoryRouter } from "react-router-dom";
import { ProfitStructurePage } from "./ProfitStructurePage";
import type {
  ProfitStructureResult,
  ProfitPeriodRow,
} from "./profitStructures";
const viewport = vi.hoisted(() => ({ mobile: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: viewport.mobile, tablet: false }),
}));
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
let request: ReturnType<typeof vi.fn>,
  failure: number,
  empty: boolean,
  migrated: boolean,
  previous: boolean;
function result(path: string): ProfitStructureResult {
  const params = new URL(path, "http://test.invalid").searchParams;
  const end = params.get("baseMonth") || "202602",
    unit = params.get("periodUnit");
  const start = unit === "month" ? end : end.slice(0, 4) + "01";
  const provisional = end.endsWith("02") && !migrated;
  const summary = {
    revenue: 2000,
    grossProfit: migrated ? null : 1020,
    grossProfitRate: migrated ? null : 51,
    operatingProfit: 825,
    operatingProfitRate: 41.25,
    idleCostShare: migrated ? null : 15.3,
  };
  const waterfall = {
    revenue: 2000,
    costOfSales: 980,
    grossProfit: summary.grossProfit,
    sellingAdministrativeExpense: 200,
    previousAdjustment: 5,
    operatingProfit: 825,
    nonOperatingProfitLoss: 30,
    monthlyCorporateTax: migrated ? null : 20,
    netProfit: migrated ? null : 835,
  };
  const trend = {
    period: start === end ? end : `${start}–${end}`,
    revenue: 2000,
    costOfSales: 980,
    grossProfit: summary.grossProfit,
    operatingProfit: 825,
    allocatedCost: migrated ? null : 700,
    idleCost: migrated ? null : 150,
    directExpense: migrated ? null : 130,
    referenceRevenue: migrated ? null : 2000,
  };
  const entry = { name: "월단위", revenue: 2000, share: 100, profitRate: 65 };
  const composition = migrated
    ? null
    : {
        contractTypes: [entry],
        topClients: [{ ...entry, name: "고객사" }],
        projectSalesParts: [{ ...entry, name: "프로젝트 영업" }],
        salesPartNotice:
          "영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다",
      };
  const row: ProfitPeriodRow = {
    period: trend.period,
    summary,
    waterfall,
    trend,
    composition,
    provisional,
  };
  return {
    periodStart: start,
    periodEnd: end,
    summary,
    waterfall,
    trends: empty ? [] : [trend],
    composition,
    compositionVisible: !migrated,
    referenceRevenueVisible: !migrated,
    provisional,
    provisionalNotice: provisional
      ? "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."
      : null,
    comparison: {
      previousPeriod: summary,
      previousYear: previous ? summary : null,
      previousYearVisible: previous,
      shareDifferenceUnit: "%p",
    },
    numericRows: empty ? [] : [row],
  };
}
beforeEach(() => {
  viewport.mobile = false;
  failure = 0;
  empty = false;
  migrated = false;
  previous = true;
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path.startsWith("/api/common/user-preferences/")) {
      const [prefTypeCd, prefKey] = path
        .split("/")
        .slice(-2)
        .map(decodeURIComponent);
      return ok(
        { prefTypeCd, prefKey, found: false, prefVal: null, modDttm: null },
        {
          periodBasis: {
            latestYm: "202602",
            cashBaseYm: "202601",
            closedMonths: ["202601"],
          },
        },
      );
    }
    if (path.startsWith("/api/common/profit-structures?")) {
      if (failure)
        return {
          ok: false,
          status: failure,
          json: async () => ({
            success: false,
            message: "기간을 확인해 주세요.",
            errors:
              failure === 400
                ? [
                    {
                      field: "baseMonth",
                      message: "같은 연도의 기준월이어야 합니다.",
                    },
                  ]
                : [],
          }),
        };
      return ok(result(path), { previousPeriodAvailable: previous });
    }
    return ok({});
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
const mount = () =>
  render(
    <MemoryRouter>
      <ProfitStructurePage />
    </MemoryRouter>,
  );
const ready = async () =>
  waitFor(() =>
    expect(screen.getByTestId("profit-summary")).toHaveAttribute(
      "data-period-end",
      "202602",
    ),
  );
it("renders the real nine-stage waterfall, charts, four summaries and exact sales-part notice", async () => {
  mount();
  await ready();

  expect(
    Array.from(
      screen
        .getByTestId("profit-waterfall")
        .querySelectorAll(".profit-waterfall-values dt"),
      (item) => item.textContent,
    ),
  ).toEqual([
    "매출",
    "매출원가",
    "매출총이익",
    "판관비",
    "전월 조정",
    "영업이익",
    "영업외손익",
    "법인세(월할)",
    "당기순이익",
  ]);
  expect(
    screen.getByTestId("profit-summary").querySelectorAll(".ls-kpi"),
  ).toHaveLength(4);
  expect(screen.getByTestId("profit-sales-trend")).toHaveTextContent(
    "배분 기준 매출",
  );
  expect(screen.getByTestId("profit-cost-stack")).toHaveTextContent(
    "직접 경비",
  );
  expect(screen.getByTestId("profit-sales-part-notice")).toHaveTextContent(
    "영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다",
  );
  expect(screen.getAllByTestId("list-download")).toHaveLength(1);
  expect(screen.getByText("계산 방법", { selector: "summary" })).toBeVisible();
});
it("changes every region together when switching month, quarter, year-to-date and year", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("tab", { name: "분기" }));
  await waitFor(() =>
    expect(screen.getByTestId("profit-summary")).toHaveAttribute(
      "data-period-start",
      "202601",
    ),
  );
  for (const id of [
    "profit-summary",
    "profit-waterfall",
    "profit-sales-trend",
    "profit-profit-trend",
    "profit-cost-stack",
    "profit-numeric-panel",
  ]) {
    expect(screen.getByTestId(id)).toHaveAttribute(
      "data-period-start",
      "202601",
    );
    expect(screen.getByTestId(id)).toHaveAttribute("data-period-end", "202602");
  }
  fireEvent.click(screen.getByRole("tab", { name: "연 누계" }));
  await waitFor(() =>
    expect(
      request.mock.calls.some(([url]) =>
        String(url).includes("periodUnit=year-to-date"),
      ),
    ).toBe(true),
  );
  fireEvent.change(screen.getByTestId("period-year"), {
    target: { value: "2025" },
  });
  // 누계 화면에서 지난 해를 선택하면 공통 기간 규칙에 따라 결산월로 전환한다.
  await waitFor(() =>
    expect(screen.getByTestId("profit-summary")).toHaveAttribute(
      "data-period-end",
      "202512",
    ),
  );
});
it("requeries the same period through the explicit search action", async () => {
  mount();
  await ready();
  const reads = () =>
    request.mock.calls.filter(([url]) =>
      String(url).startsWith("/api/common/profit-structures?"),
    );
  const before = reads().length;
  const path = reads().at(-1)?.[0];
  fireEvent.click(screen.getByTestId("profit-search"));
  await waitFor(() => expect(reads()).toHaveLength(before + 1));
  await ready();
  expect(reads().at(-1)?.[0]).toBe(path);
});
it("shows provisional banner and summary/numeric markers only for an open ending month", async () => {
  mount();
  await ready();
  expect(
    screen.getByText(
      "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다.",
    ),
  ).toBeVisible();
  expect(screen.getAllByText("잠정").length).toBeGreaterThanOrEqual(2);
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "1" },
  });
  await waitFor(() =>
    expect(screen.getByTestId("profit-summary")).toHaveAttribute(
      "data-period-end",
      "202601",
    ),
  );
  expect(screen.queryByText(/아직 마감 전이라/)).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("profit-summary")).queryByText("잠정"),
  ).not.toBeInTheDocument();
});
it("uses percentage points for comparisons and hides both missing comparisons", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("profit-summary")).toHaveTextContent("%p");
  previous = false;
  fireEvent.click(screen.getByRole("tab", { name: "분기" }));
  await waitFor(() =>
    expect(screen.getByTestId("profit-summary")).not.toHaveTextContent(
      "전년 같은 기간",
    ),
  );
  expect(screen.getByTestId("profit-summary")).not.toHaveTextContent(
    "직전 기간",
  );
});
it("retains the toolbar, period conditions and numeric headers in an empty period", async () => {
  empty = true;
  mount();
  await ready();
  expect(screen.getByTestId("profit-period-controls")).toBeVisible();
  expect(screen.getByTestId("list-empty")).toBeVisible();
  expect(
    within(screen.getByTestId("profit-numeric-panel")).getAllByRole(
      "columnheader",
    ).length,
  ).toBeGreaterThan(1);
  expect(screen.getByTestId("list-download")).toBeVisible();
});
it("does not disguise a failed request as an empty list and allows retry", async () => {
  failure = 500;
  mount();
  await screen.findAllByText("기간을 확인해 주세요.");
  expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
  failure = 0;
  fireEvent.click(screen.getByTestId("profit-retry"));
  await ready();
});
it("renders field validation and focuses the period control", async () => {
  failure = 400;
  mount();
  expect(await screen.findByTestId("profit-error-baseMonth")).toHaveTextContent(
    "같은 연도의 기준월이어야 합니다.",
  );
  expect(screen.getByTestId("period-year")).toHaveFocus();
});
it("shows permission-denied as an error without introducing a client role gate", async () => {
  failure = 403;
  mount();
  expect(
    (await screen.findAllByText("이 화면을 조회할 권한이 없습니다.")).length,
  ).toBeGreaterThan(0);
  expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
});
it("shows unknown migration values as dashes and omits composition, reference and provisional marks", async () => {
  migrated = true;
  mount();
  await ready();
  expect(screen.getByTestId("profit-summary")).toHaveTextContent("–");
  expect(
    screen.queryByTestId("profit-sales-part-notice"),
  ).not.toBeInTheDocument();
  expect(screen.queryByText(/아직 마감 전이라/)).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("profit-numeric-panel")).queryByRole(
      "columnheader",
      { name: "배분 기준 매출" },
    ),
  ).not.toBeInTheDocument();
});
it("ignores late responses from an earlier period", async () => {
  let release: ((value: ReturnType<typeof ok>) => void) | undefined;
  const original = request.getMockImplementation()!;
  request.mockImplementation((path: string, init?: RequestInit) =>
    path.includes("periodUnit=month") &&
    path.startsWith("/api/common/profit-structures?")
      ? new Promise((resolve) => {
          release = resolve;
        })
      : original(path, init),
  );
  mount();
  await screen.findByTestId("profit-unit-quarter");
  fireEvent.click(screen.getByTestId("profit-unit-quarter"));
  await ready();
  release?.(
    ok(
      result("/api/common/profit-structures?periodUnit=month&baseMonth=202601"),
    ),
  );
  await waitFor(() =>
    expect(screen.getByTestId("profit-summary")).toHaveAttribute(
      "data-period-end",
      "202602",
    ),
  );
});
it("keeps cards and one download in the numeric toolbar on mobile", async () => {
  viewport.mobile = true;
  mount();
  await ready();
  expect(screen.getByTestId("list-cards")).toBeVisible();
  expect(screen.getAllByRole("button", { name: "엑셀 다운로드" })).toHaveLength(
    1,
  );
  expect(
    within(screen.getByTestId("profit-mobile-toolbar")).getByRole("button", {
      name: "엑셀 다운로드",
    }),
  ).toBeEnabled();
});
it("downloads only visible column keys from the same period and reports success", async () => {
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => undefined);
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: vi.fn(() => "blob:profit"),
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: vi.fn(),
  });
  const original = request.getMockImplementation()!;
  request.mockImplementation((path: string, init?: RequestInit) =>
    path.startsWith("/api/common/profit-structures/excel?")
      ? Promise.resolve({
          ok: true,
          headers: new Headers(),
          blob: async () =>
            new Blob(["PK"], {
              type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            }),
        })
      : original(path, init),
  );
  mount();
  await ready();
  fireEvent.click(screen.getByRole("button", { name: "열 설정" }));
  fireEvent.click(screen.getByRole("checkbox", { name: /^매출총이익률$/ }));
  fireEvent.click(screen.getByTestId("list-download"));
  expect(
    await screen.findByTestId("profit-download-message"),
  ).toHaveTextContent("엑셀 파일을 내려받았습니다.");
  const call = request.mock.calls.find(([url]) =>
    String(url).startsWith("/api/common/profit-structures/excel?"),
  );
  const params = new URL(String(call?.[0]), "http://test.invalid").searchParams;
  expect(params.get("columns")?.split(",")).not.toContain("grossProfitRate");
  expect(params.get("baseMonth")).toBe("202602");
  expect(params.get("periodUnit")).toBe("month");
  expect(click).toHaveBeenCalledOnce();
});
it("reports download failures without presenting success", async () => {
  const original = request.getMockImplementation()!;
  request.mockImplementation((path: string, init?: RequestInit) =>
    path.startsWith("/api/common/profit-structures/excel?")
      ? Promise.reject(new Error("network"))
      : original(path, init),
  );
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("list-download"));
  expect(
    await screen.findByTestId("profit-download-message"),
  ).toHaveTextContent("엑셀 파일을 받지 못했습니다.");
});

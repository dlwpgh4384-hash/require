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
import { MemoryRouter, useLocation } from "react-router-dom";
import { ProfitStructurePage } from "./ProfitStructurePage";
import {
  ProfitStructureTitleActions,
  ProfitStructureWorkspaceProvider,
} from "./ProfitStructureWorkspace";
import type {
  ProfitNumericTable,
  ProfitStructureResult,
  ProfitSummary,
} from "./profitStructures";

const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
let request: ReturnType<typeof vi.fn>, failure: number;

const summary: ProfitSummary = {
  revenue: 110_000_000,
  grossProfit: 40_500_000,
  grossProfitRate: 36.8,
  operatingProfit: 20_000_000,
  operatingProfitRate: 18.2,
  idleCostShare: 12.5,
};
const previousSummary: ProfitSummary = {
  revenue: 100_000_000,
  grossProfit: 45_000_000,
  grossProfitRate: 45,
  operatingProfit: 20_000_000,
  operatingProfitRate: 20,
  idleCostShare: 10,
};
const lastYearSummary: ProfitSummary = {
  revenue: 120_000_000,
  grossProfit: 36_000_000,
  grossProfitRate: 30,
  operatingProfit: 25_000_000,
  operatingProfitRate: 20.8,
  idleCostShare: 15,
};
const tableRows = (columns: number): ProfitNumericTable["rows"] => {
  const money = (label: string, style: "bold" | "indent" | "plain", value: number) => ({
    label,
    style,
    format: "money" as const,
    values: Array.from({ length: columns }, () => value),
  });
  return [
    money("매출", "bold", 110_000_000),
    money("매출원가", "plain", 69_500_000),
    money("└ 배분 원가", "indent", 50_000_000),
    { label: "매출총이익률", style: "plain", format: "percent", values: [null, 36.8].slice(-columns) },
    {
      label: "매출 구성 · 계약 유형별",
      style: "group",
      format: "money",
      values: Array.from({ length: columns }, () => null),
    },
    money("월단위", "indent", 80_000_000),
  ];
};
function result(path: string): ProfitStructureResult {
  const params = new URL(path, "http://test.invalid").searchParams;
  const baseMonth = params.get("baseMonth") || "202602";
  const unit = params.get("periodUnit") || "month";
  const year = baseMonth.slice(0, 4);
  const month = Number(baseMonth.slice(4));
  const migrated = year < "2026";
  const provisional = baseMonth === "202602";
  const labels =
    unit === "quarter"
      ? [month < 3 ? `1분기(${month}월까지)` : "1분기"]
      : Array.from({ length: month }, (_, index) => `${index + 1}월`);
  const trend = (period: string) => ({
    period,
    revenue: 110_000_000,
    costOfSales: 69_500_000,
    grossProfit: 40_500_000,
    operatingProfit: 20_000_000,
    allocatedCost: 50_000_000,
    idleCost: 8_700_000,
    directExpense: 10_800_000,
    referenceRevenue: migrated ? null : 105_000_000,
  });
  const entry = (name: string) => ({ name, revenue: 80_000_000, share: 72.7, profitRate: 37.5 });
  return {
    periodStart: unit === "month" ? baseMonth : `${year}01`,
    periodEnd: baseMonth,
    periodName:
      unit === "month"
        ? `${year}-${baseMonth.slice(4)}`
        : unit === "quarter"
          ? `${year}년 ${labels.at(-1)}`
          : `${year}년 1~${month}월 누계`,
    summary,
    comparison: {
      previousPeriod: unit === "year-to-date" || month === 1 ? null : previousSummary,
      previousPeriodLabel:
        unit === "year-to-date" || month === 1
          ? null
          : unit === "quarter"
            ? "전 분기 같은 기간"
            : "전월",
      previousYear: migrated ? null : lastYearSummary,
      previousYearVisible: !migrated,
      shareDifferenceUnit: "%p",
    },
    waterfall: {
      revenue: 110_000_000,
      costOfSales: 69_500_000,
      grossProfit: 40_500_000,
      sellingAdministrativeExpense: 21_000_000,
      previousAdjustment: 500_000,
      operatingProfit: 20_000_000,
      nonOperatingProfitLoss: -1_000_000,
      monthlyCorporateTax: 2_000_000,
      netProfit: 17_000_000,
    },
    trends: labels.map(trend),
    composition: migrated
      ? null
      : {
          contractTypes: [entry("월단위")],
          topClients: [entry("가나 고객사")],
          projectSalesParts: [entry("1파트")],
          salesPartNotice: "영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다.",
        },
    compositionVisible: !migrated,
    referenceRevenueVisible: !migrated,
    provisional,
    provisionalNotice: provisional
      ? "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."
      : null,
    numericRows: [],
    numericTable: {
      columns: labels.map((label, index) => ({
        label,
        provisional: provisional && index === labels.length - 1,
      })),
      rows: tableRows(labels.length),
    },
  };
}
beforeEach(() => {
  failure = 0;
  request = vi.fn(async (path: string) => {
    if (path.startsWith("/api/common/user-preferences/")) {
      const [prefTypeCd, prefKey] = path.split("/").slice(-2).map(decodeURIComponent);
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
                ? [{ field: "baseMonth", message: "같은 연도의 기준월이어야 합니다." }]
                : [],
          }),
        };
      const params = new URL(path, "http://test.invalid").searchParams;
      return ok(result(path), {
        previousPeriodAvailable: params.get("periodUnit") !== "year-to-date",
      });
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
function Where() {
  return <div data-testid="where">{useLocation().pathname}</div>;
}
const mount = () =>
  render(
    <MemoryRouter initialEntries={["/screens/UI-07"]}>
      <ProfitStructureWorkspaceProvider>
        <ProfitStructureTitleActions />
        <ProfitStructurePage />
        <Where />
      </ProfitStructureWorkspaceProvider>
    </MemoryRouter>,
  );
const ready = async (end = "202602") =>
  waitFor(() =>
    expect(screen.getByTestId("profit-summary")).toHaveAttribute("data-period-end", end),
  );
const reads = () =>
  request.mock.calls.filter(([url]) => String(url).startsWith("/api/common/profit-structures?"));

it("shows the mockup period bar and 월/분기/연 누계 tabs without a second year bar or search button", async () => {
  mount();
  await ready();
  const bar = screen.getByTestId("period-selector");
  expect(bar).toHaveAttribute("data-variant", "cumulativeBar");
  expect(bar).toHaveTextContent("2026년");
  expect(bar).toHaveTextContent("누계 기준월");
  expect(bar).toHaveTextContent("1~2월 누계");
  expect(bar).toHaveTextContent("진행 중");
  expect(screen.getAllByTestId("period-selector")).toHaveLength(1);
  expect(screen.queryByTestId("year-bar")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "조회" })).not.toBeInTheDocument();
  const tabs = screen.getByRole("tablist", { name: "기간 단위" });
  expect(within(tabs).getAllByRole("tab").map((tab) => tab.textContent)).toEqual([
    "월",
    "분기",
    "연 누계",
  ]);
  expect(screen.queryByText("전체 수익 구조 · 조회 전용")).not.toBeInTheDocument();
  expect(screen.queryByText(/조회는 업무 손익을 저장하지 않습니다/)).not.toBeInTheDocument();
});

it("renders four mockup KPI cards with kor amounts and ▲/▼ comparisons", async () => {
  mount();
  await ready();
  const cards = screen.getByTestId("profit-summary-cards");
  expect(
    Array.from(cards.querySelectorAll(".ls-kpi .l"), (label) =>
      label.textContent?.replace("잠정", "").trim(),
    ),
  ).toEqual(["매출", "매출총이익", "영업이익", "대기 원가 비중"]);
  const revenue = screen.getByTestId("profit-kpi-revenue");
  expect(revenue).toHaveTextContent("1.1억 원");
  expect(revenue).toHaveTextContent("2026-02 · ▲ 10.0% 전월 대비");
  expect(revenue).toHaveTextContent("전년 같은 기간 대비 ▼ 8.3%");
  expect(within(revenue).getByText("▲ 10.0%")).toHaveClass("profit-ok");
  expect(within(revenue).getByText("▼ 8.3%")).toHaveClass("profit-bad");
  expect(screen.getByTestId("profit-kpi-gross")).toHaveTextContent(
    "4,050만 원매출총이익률 36.8% · ▼ 10.0% 전월 대비",
  );
  expect(screen.getByTestId("profit-kpi-operating")).toHaveTextContent(
    "영업이익률 18.2% · ▲ 0.0% 전월 대비",
  );
  const idle = screen.getByTestId("profit-kpi-idle");
  expect(idle).toHaveTextContent("12.5%");
  expect(idle).toHaveTextContent("대기 원가 870만 원 ÷ 매출원가 · +2.5%p 전월 대비");
  expect(idle).toHaveTextContent("전년 같은 기간 15.0% (−2.5%p)");
  expect(within(idle).getByText("+2.5%p")).toHaveClass("profit-bad");
  expect(within(idle).getByText("−2.5%p")).toHaveClass("profit-ok");
  // 영업이익 칸은 영업이익표(UI-08)로 간다(시안 kpi(...,'UI-08')).
  fireEvent.click(screen.getByTestId("profit-kpi-operating"));
  expect(screen.getByTestId("where")).toHaveTextContent("/screens/UI-08");
});

it("compares 연 누계 only with the same period last year", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("tab", { name: "연 누계" }));
  await waitFor(() =>
    expect(screen.getByTestId("profit-kpi-revenue")).toHaveTextContent("2026년 1~2월 누계"),
  );
  for (const id of ["profit-kpi-revenue", "profit-kpi-gross", "profit-kpi-operating", "profit-kpi-idle"])
    expect(screen.getByTestId(id)).not.toHaveTextContent(/전월 대비|전 분기/);
  expect(screen.getByTestId("profit-kpi-revenue")).toHaveTextContent("전년 같은 기간 대비 ▼ 8.3%");
  expect(screen.getByTestId("profit-numeric-panel")).toHaveTextContent("월별 누계 · 단위 천 원");
});

it("shows the provisional banner and 잠정 badges only when the ending month is open", async () => {
  mount();
  await ready();
  expect(
    screen.getByText("2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."),
  ).toBeVisible();
  expect(within(screen.getByTestId("profit-summary")).getAllByText("잠정")).toHaveLength(4);
  fireEvent.change(screen.getByTestId("period-month"), { target: { value: "1" } });
  await ready("202601");
  expect(screen.queryByText(/아직 마감 전이라/)).not.toBeInTheDocument();
  expect(within(screen.getByTestId("profit-summary")).queryByText("잠정")).not.toBeInTheDocument();
  // 1월은 직전 비교가 없다.
  expect(screen.getByTestId("profit-kpi-revenue")).not.toHaveTextContent("전월 대비");
});

it("draws the nine-step waterfall, trend charts and one stacked cost bar per period", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("profit-waterfall-card")).toHaveTextContent(
    "수익 구조 — 매출에서 순이익까지2026-02 · 단위: 백만 원",
  );
  const steps = screen.getAllByTestId("profit-waterfall-step");
  expect(steps).toHaveLength(9);
  expect(steps.map((step) => step.querySelector("title")?.textContent?.split(" ").slice(0, -1).join(" "))).toEqual([
    "매출",
    "매출원가",
    "매출 총이익",
    "판관비",
    "전월 조정",
    "영업이익",
    "영업외 손익",
    "법인세 (월할)",
    "당기 순이익",
  ]);
  expect(steps[0]).toHaveTextContent("110");
  expect(steps[1]).toHaveTextContent("-69");
  expect(steps[4]).toHaveTextContent("+1");
  const trend = screen.getByTestId("profit-trend-card");
  expect(trend).toHaveTextContent("매출·원가 추이");
  expect(trend).toHaveTextContent("배분 기준 매출(참고)");
  expect(within(trend).getByText("1월")).toBeInTheDocument();
  expect(within(trend).getByText("2월")).toBeInTheDocument();
  expect(screen.getByTestId("profit-profit-card")).toHaveTextContent("매출총이익");
  const cost = screen.getByTestId("profit-cost-card");
  expect(cost).toHaveTextContent("배분 원가 · 대기 원가 · 직접 경비 · 단위: 백만 원");
  expect(screen.getAllByTestId("profit-cost-chart-bar")).toHaveLength(2);
  for (const id of ["profit-trend-chart", "profit-profit-chart", "profit-cost-chart", "profit-waterfall"])
    expect(screen.getByTestId(id).getAttribute("viewBox")).toMatch(/^0 0 \d+ (220|200|280)$/);
});

it("uses a bar chart and the partial-quarter note for 분기", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("tab", { name: "분기" }));
  expect(await screen.findByTestId("profit-partial-quarter")).toHaveTextContent(
    "1분기(2월까지)는 진행 중이라 2개월만 더한 값입니다.",
  );
  expect(screen.getByTestId("profit-trend-chart")).toHaveAttribute("aria-label", "매출, 매출원가 비교");
  expect(screen.getByTestId("profit-kpi-revenue")).toHaveTextContent("▲ 10.0% 전 분기 같은 기간 대비");
  expect(screen.getByTestId("profit-numeric-panel")).toHaveTextContent("분기별 · 단위 천 원");
});

it("renders the 숫자로 보기 table with items as rows and periods as columns", async () => {
  mount();
  await ready();
  const table = screen.getByTestId("profit-numeric-table");
  const headers = within(table).getAllByRole("columnheader");
  expect(headers.map((header) => header.textContent)).toEqual(["항목", "1월", "2월 잠정"]);
  expect(headers[2]).toHaveClass("profit-cur");
  expect(headers[1]).not.toHaveClass("profit-cur");
  const rows = within(table).getAllByTestId("profit-numeric-row");
  expect(rows.map((row) => row.querySelector("td")?.textContent)).toEqual([
    "매출",
    "매출원가",
    "└ 배분 원가",
    "매출총이익률",
    "매출 구성 · 계약 유형별",
    "월단위",
  ]);
  expect(rows[0]).toHaveClass("profit-b");
  expect(rows[0]).toHaveTextContent("110,000");
  expect(rows[2].querySelector("td")).toHaveClass("profit-ind");
  expect(rows[3]).toHaveTextContent("–36.8%");
  expect(rows[4]).toHaveClass("profit-b");
  expect(Array.from(rows[4].querySelectorAll("td")).slice(1).map((cell) => cell.textContent)).toEqual(["", ""]);
  expect(rows[5].querySelectorAll("td")[2]).toHaveClass("profit-cur");
  expect(screen.queryByRole("button", { name: "열 설정" })).not.toBeInTheDocument();
  expect(screen.getByTestId("profit-numeric-note")).toHaveTextContent(
    "매출 구성(계약 유형·고객사·영업 파트)은 각 기간의 실제 매출입니다. 엑셀 다운로드는 이 표와 같습니다. 자세한 항목은 영업이익표·순이익표에서 봅니다. 같은 계산이라 숫자가 같습니다.",
  );
  fireEvent.click(screen.getByTestId("profit-link-UI-09"));
  expect(screen.getByTestId("where")).toHaveTextContent("/screens/UI-09");
  fireEvent.change(screen.getByTestId("money-unit"), { target: { value: "백만 원" } });
  expect(rows[0]).toHaveTextContent("110");
  expect(screen.getByTestId("profit-numeric-panel")).toHaveTextContent("월별 · 단위 백만 원");
});

it("shows three composition cards, and the migration text for 2025", async () => {
  mount();
  await ready();
  const mixes = screen.getByTestId("profit-compositions");
  expect(within(mixes).getAllByRole("heading").map((h) => h.textContent)).toEqual([
    "계약 유형별",
    "고객사 상위 5곳",
    "영업 파트별",
  ]);
  expect(screen.getByTestId("profit-mix-contract")).toHaveTextContent("월단위8,000만 원 (72.7%) · 이익률 37.5%");
  expect(mixes).toHaveTextContent("프로젝트를 따온 파트 기준");
  expect(mixes).toHaveTextContent("영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다.");
  fireEvent.click(screen.getByTestId("period-year-prev"));
  await ready("202512");
  expect(screen.queryByTestId("profit-compositions")).not.toBeInTheDocument();
  expect(screen.getByTestId("profit-composition-hidden")).toHaveTextContent(
    "2025년은 월별 손익만 옮겨 와서 계약 유형·고객사·영업 파트별 구성은 볼 수 없습니다.",
  );
  expect(screen.getByTestId("period-selector")).toHaveTextContent("결산 완료");
  expect(screen.getByTestId("profit-trend-card")).not.toHaveTextContent("배분 기준 매출(참고)");
});

it("puts one 엑셀 다운로드 in the title bar, sends no columns and reports with a toast", async () => {
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:profit") });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
  const original = request.getMockImplementation()!;
  let fail = false;
  request.mockImplementation((path: string, init?: RequestInit) =>
    path.startsWith("/api/common/profit-structures/excel?")
      ? fail
        ? Promise.reject(new Error("network"))
        : Promise.resolve({
            ok: true,
            headers: new Headers(),
            blob: async () => new Blob(["PK"]),
          })
      : original(path, init),
  );
  mount();
  await ready();
  expect(screen.getAllByRole("button", { name: "엑셀 다운로드" })).toHaveLength(1);
  const button = within(screen.getByTestId("profit-title-actions")).getByRole("button", {
    name: "엑셀 다운로드",
  });
  fireEvent.click(button);
  expect(await screen.findByTestId("profit-toast")).toHaveTextContent("엑셀 파일을 만들었습니다.");
  const call = request.mock.calls.find(([url]) =>
    String(url).startsWith("/api/common/profit-structures/excel?"),
  );
  const params = new URL(String(call?.[0]), "http://test.invalid").searchParams;
  expect(params.has("columns")).toBe(false);
  expect(params.get("year")).toBe("2026");
  expect(params.get("periodUnit")).toBe("month");
  expect(params.get("baseMonth")).toBe("202602");
  expect(click).toHaveBeenCalledOnce();
  fail = true;
  fireEvent.click(button);
  await waitFor(() =>
    expect(screen.getByTestId("profit-toast")).toHaveTextContent("엑셀 파일을 받지 못했습니다."),
  );
});

it("shows a failed request as an error banner with retry, not as an empty table", async () => {
  failure = 500;
  mount();
  expect(await screen.findByText("기간을 확인해 주세요.")).toBeVisible();
  expect(screen.queryByTestId("profit-table-empty")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "엑셀 다운로드" })).toBeDisabled();
  failure = 0;
  fireEvent.click(screen.getByTestId("profit-retry"));
  await ready();
  expect(screen.getByTestId("profit-numeric-table")).toBeVisible();
});

it("shows the 403 message", async () => {
  failure = 403;
  mount();
  expect(await screen.findByText("이 화면을 조회할 권한이 없습니다.")).toBeVisible();
});

it("shows field validation and focuses the period control", async () => {
  failure = 400;
  mount();
  expect(await screen.findByTestId("profit-error-baseMonth")).toHaveTextContent(
    "같은 연도의 기준월이어야 합니다.",
  );
  expect(screen.getByTestId("period-month")).toHaveFocus();
});

it("ignores a late response from an earlier period unit", async () => {
  let release: ((value: ReturnType<typeof ok>) => void) | undefined;
  const original = request.getMockImplementation()!;
  request.mockImplementation((path: string, init?: RequestInit) =>
    path.includes("periodUnit=month") && path.startsWith("/api/common/profit-structures?")
      ? new Promise((resolve) => {
          release = resolve;
        })
      : original(path, init),
  );
  mount();
  await screen.findByTestId("profit-unit-quarter");
  await waitFor(() => expect(reads().length).toBeGreaterThan(0));
  fireEvent.click(screen.getByTestId("profit-unit-quarter"));
  await waitFor(() =>
    expect(screen.getByTestId("profit-kpi-revenue")).toHaveTextContent("2026년 1분기(2월까지)"),
  );
  release?.(ok(result("/api/common/profit-structures?periodUnit=month&baseMonth=202602")));
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(screen.getByTestId("profit-kpi-revenue")).toHaveTextContent("2026년 1분기(2월까지)");
});

it("shows the mockup formula box text", async () => {
  mount();
  await ready();
  const box = screen.getByTestId("profit-formulas");
  expect(
    within(box)
      .getAllByRole("row")
      .slice(1)
      .map((row) => row.querySelector("td")?.textContent),
  ).toEqual([
    "기간",
    "매출총이익(률)",
    "영업이익(률)",
    "당기순이익",
    "대기 원가 비중",
    "배분 기준 매출(참고선)",
    "매출 구성",
  ]);
  expect(box).toHaveTextContent("숫자는 영업이익표·순이익표와 같은 계산입니다.");
});

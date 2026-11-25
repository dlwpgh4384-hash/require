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
import { MemoryRouter, useLocation } from "react-router-dom";
import {
  BusinessPlanComparisonPage,
  businessPlanPeriodOptions,
} from "./BusinessPlanComparisonPage";
import {
  BusinessPlanComparisonTitleActions,
  BusinessPlanComparisonWorkspaceProvider,
} from "./BusinessPlanComparisonWorkspace";
import type { BusinessPlanComparisonResult } from "./businessPlanComparisons";
import { isAllowed, monthRange } from "../../shared/period/periodRules";

const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data: structuredClone(data) }),
});
function report(month = "202611"): BusinessPlanComparisonResult {
  return {
    year: Number(month.slice(0, 4)),
    baseMonth: month,
    baseMonthClosed: month === "202610",
    hasTargets: true,
    summary: {
      cumulativeRevenueAchievementRate: 90.5,
      cumulativeRevenueActual: 1810000,
      cumulativeRevenueTarget: 2000000,
      cumulativeOperatingProfitAchievementRate: -50.1,
      cumulativeOperatingProfitActual: -501000,
      cumulativeOperatingProfitTarget: 1000000,
      baseMonthRevenueAchievementRate: 95,
      baseMonthRevenueTarget: 200000,
    },
    months: Array.from({ length: 12 }, (_, i) => ({
      yearMonth: `${month.slice(0, 4)}${String(i + 1).padStart(2, "0")}`,
      revenueTarget: i === 0 ? 0 : 200000,
      operatingProfitTarget: i === 0 ? 0 : 100000,
      revenueActual: i + 1 <= Number(month.slice(4)) ? 190000 : null,
      operatingProfitActual: i + 1 <= Number(month.slice(4)) ? -50000 : null,
      achievementRate: i === 0 || i + 1 > Number(month.slice(4)) ? null : 95,
      profitRateDifference:
        i === 0 || i + 1 > Number(month.slice(4)) ? null : -76.3,
    })),
  };
}
let request: ReturnType<typeof vi.fn>;
let failure: number;
let noTargets: boolean;
let noActuals: boolean;
let latest: string;
beforeEach(() => {
  failure = 0;
  noTargets = false;
  noActuals = false;
  latest = "202611";
  request = vi.fn(async (path: string, _init?: RequestInit) => {
    if (path.startsWith("/api/common/user-preferences/")) {
      const [prefTypeCd, prefKey] = path
        .slice("/api/common/user-preferences/".length)
        .split("/")
        .map(decodeURIComponent);
      return ok(
        { prefTypeCd, prefKey, found: false, prefVal: null, modDttm: null },
        {
          periodBasis: {
            latestYm: latest,
            cashBaseYm: "202603",
            closedMonths: ["202610"],
          },
        },
      );
    }
    if (path.startsWith("/api/common/business-plan-comparisons?")) {
      if (failure)
        return {
          ok: false,
          status: failure,
          json: async () => ({
            success: false,
            message: "비교 조회 실패",
            errors:
              failure === 400
                ? [{ field: "baseMonth", message: "기준월을 확인해 주세요." }]
                : [],
          }),
        };
      const result = report(
        new URL(path, "https://test.invalid").searchParams.get("baseMonth")!,
      );
      if (noTargets) {
        result.hasTargets = false;
        result.summary.cumulativeRevenueAchievementRate = null;
        result.summary.cumulativeOperatingProfitAchievementRate = null;
        result.summary.baseMonthRevenueAchievementRate = null;
        result.months.forEach((m) => {
          m.revenueTarget = 0;
          m.operatingProfitTarget = 0;
          m.achievementRate = null;
          m.profitRateDifference = null;
        });
      }
      if (noActuals)
        result.months.forEach((m) => {
          m.revenueActual = null;
          m.operatingProfitActual = null;
        });
      return ok(result);
    }
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
function Location() {
  const value = useLocation();
  return (
    <output data-testid="location">
      {JSON.stringify({ pathname: value.pathname, state: value.state })}
    </output>
  );
}
function mount() {
  render(
    <MemoryRouter initialEntries={["/screens/UI-18"]}>
      <BusinessPlanComparisonWorkspaceProvider>
        <BusinessPlanComparisonTitleActions />
        <BusinessPlanComparisonPage />
        <Location />
      </BusinessPlanComparisonWorkspaceProvider>
    </MemoryRouter>,
  );
}
async function ready(month = "202611") {
  await waitFor(() =>
    expect(screen.getByTestId("business-plan-summary")).toHaveAttribute(
      "data-base-month",
      month,
    ),
  );
}
const reads = () =>
  request.mock.calls.filter(([path]) =>
    String(path).startsWith("/api/common/business-plan-comparisons?"),
  );

it("renders three same-period summary cards with actual/target amounts and signed rates", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("business-plan-kpis").children).toHaveLength(3);
  expect(screen.getByTestId("business-plan-kpi-revenue")).toHaveTextContent(
    "90.5%",
  );
  expect(screen.getByTestId("business-plan-kpi-revenue")).toHaveTextContent(
    "181만 원 / 목표 200만 원",
  );
  expect(screen.getByTestId("business-plan-kpi-profit")).toHaveTextContent(
    "−50.1%",
  );
  expect(screen.getByTestId("business-plan-kpi-profit")).toHaveTextContent(
    "−50만 원 / 목표 100만 원",
  );
  expect(screen.getByTestId("business-plan-kpi-base")).toHaveTextContent(
    "2026-11 매출 달성률",
  );
  expect(screen.getByTestId("period-selector")).toHaveAttribute(
    "data-variant",
    "cumulativeBar",
  );
  expect(
    within(screen.getByTestId("business-plan-title-actions")).getAllByRole(
      "button",
    ),
  ).toHaveLength(2);
  expect(screen.queryByText("엑셀 다운로드")).toBeNull();
  expect(reads()[0][1]).not.toHaveProperty("headers");
});
it("shows exactly six monthly rows and twelve month columns without cumulative column", async () => {
  mount();
  await ready();
  const table = screen.getByTestId("business-plan-table");
  expect(table.querySelectorAll("tbody tr")).toHaveLength(6);
  expect(within(table).getAllByRole("columnheader")).toHaveLength(13);
  expect(table.querySelectorAll("tbody tr:first-child td")).toHaveLength(12);
  expect(
    within(table).queryByRole("columnheader", { name: "누계" }),
  ).toBeNull();
  expect(screen.getByTestId("business-plan-row-achievementRate")).toHaveClass(
    "business-plan-rate",
  );
  expect(
    screen.getByTestId("business-plan-profitRateDifference-202611"),
  ).toHaveTextContent("−76.3%p");
  expect(screen.getByTestId("business-plan-revenueActual-202611")).toHaveClass(
    "business-plan-current",
  );
});
it("shows future actual dashes but keeps target values", async () => {
  mount();
  await ready();
  expect(
    screen.getByTestId("business-plan-revenueActual-202612"),
  ).toHaveTextContent("–");
  expect(
    screen.getByTestId("business-plan-operatingProfitActual-202612"),
  ).toHaveTextContent("–");
  expect(
    screen.getByTestId("business-plan-revenueTarget-202612"),
  ).toHaveTextContent("200");
});
it("marks only valid underachievement red and gives both zero-target ratios a help dash", async () => {
  mount();
  await ready();
  for (const field of ["achievementRate", "profitRateDifference"]) {
    const cell = screen.getByTestId(`business-plan-${field}-202601`);
    expect(cell).toHaveTextContent("–");
    expect(cell).toHaveAttribute("title", "목표 없음");
    expect(cell).not.toHaveClass("business-plan-under");
  }
  expect(
    screen.getByTestId("business-plan-achievementRate-202611"),
  ).toHaveClass("business-plan-under");
  expect(
    screen.getByTestId("business-plan-achievementRate-202612"),
  ).not.toHaveClass("business-plan-under");
});
it("gives future zero-target months a plain dash without the no-target help", async () => {
  noTargets = true;
  mount();
  await ready();
  for (const field of ["achievementRate", "profitRateDifference"]) {
    expect(
      screen.getByTestId(`business-plan-${field}-202611`),
    ).toHaveAttribute("title", "목표 없음");
    const future = screen.getByTestId(`business-plan-${field}-202612`);
    expect(future).toHaveTextContent("–");
    expect(future).not.toHaveAttribute("title");
  }
});
it("uses the report amounts in the shared raw-won bar chart and retains million-won chart units", async () => {
  mount();
  await ready();
  const chart = screen.getByTestId("business-plan-chart");
  expect(chart.querySelectorAll("rect")).toHaveLength(11);
  expect(chart.querySelector("rect title")).toHaveTextContent(
    "1월 매출 실적 190,000원",
  );
  expect(chart.querySelector('[stroke-dasharray="6 4"]')).not.toBeNull();
  fireEvent.change(screen.getByTestId("money-unit"), {
    target: { value: "원" },
  });
  expect(
    screen.getByTestId("business-plan-revenueActual-202611"),
  ).toHaveTextContent("190,000");
  expect(screen.getByTestId("business-plan-chart-card")).toHaveTextContent(
    "단위: 백만 원",
  );
  expect(chart.querySelector("rect title")).toHaveTextContent("190,000원");
});
it("opens the requested four source tooltips without a calculation-method box", async () => {
  mount();
  await ready();
  for (const [id, text] of [
    ["M25-405", "[목표 업로드]로 올린 사업계획 엑셀의 그 달 매출 목표"],
    ["M25-406", "영업이익표의 그 달 매출액과 같음"],
    ["M25-407", "[목표 업로드]로 올린 사업계획 엑셀의 그 달 영업이익 목표"],
    ["M25-408", "영업이익표의 그 달 영업이익과 같음"],
  ]) {
    fireEvent.click(screen.getByTestId(id));
    expect(screen.getByRole("tooltip")).toHaveTextContent(text);
  }
  expect(screen.queryByText("계산 방법")).toBeNull();
});
it("keeps actuals and public upload entry when annual targets do not exist", async () => {
  noTargets = true;
  mount();
  await ready();
  expect(
    screen.getByText(
      "2026년 사업계획 목표가 아직 없습니다. [목표 업로드]로 올려 주세요.",
    ),
  ).toBeVisible();
  expect(screen.getByTestId("business-plan-kpi-revenue")).toHaveTextContent(
    "–",
  );
  expect(screen.getByTestId("business-plan-kpi-profit")).toHaveTextContent("–");
  expect(screen.getByTestId("business-plan-kpi-base")).toHaveTextContent("–");
  expect(
    screen.getByTestId("business-plan-revenueActual-202611"),
  ).toHaveTextContent("190");
  expect(screen.getByTestId("M25-400")).toBeEnabled();
});
it("shows empty actuals separately from failed request", async () => {
  noActuals = true;
  mount();
  await ready();
  expect(screen.getByTestId("business-plan-empty")).toHaveTextContent(
    "실적 자료가 없습니다",
  );
  expect(screen.queryByTestId("M25-414")).toBeNull();
});
it("shows field error and retries current period without disguising failure as empty", async () => {
  failure = 400;
  mount();
  await screen.findByText("비교 조회 실패");
  expect(screen.getByText("기준월을 확인해 주세요.")).toBeVisible();
  expect(screen.queryByTestId("business-plan-empty")).toBeNull();
  expect(screen.getByTestId("period-month")).toHaveFocus();
  failure = 0;
  fireEvent.click(screen.getByTestId("M25-414"));
  await ready();
  expect(reads().at(-1)?.[0]).toContain("baseMonth=202611");
});
it("allows start-year January and prior-year January/December while preserving other screen locks", async () => {
  latest = "202701";
  mount();
  await ready("202701");
  fireEvent.click(screen.getByTestId("period-year-prev"));
  await ready("202612");
  expect(screen.getByTestId("period-year-prev")).toBeDisabled();
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "1" },
  });
  await ready("202601");
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "12" },
  });
  await ready("202612");
  const basis = { latestYm: "202701", cashBaseYm: "202603", closedMonths: [] };
  expect(
    isAllowed({ year: 2025, month: 12 }, basis, businessPlanPeriodOptions),
  ).toBe(false);
  expect(monthRange(basis, { kind: "yearMonth" }, 2026).min).toBe(3);
  expect(monthRange(basis, businessPlanPeriodOptions, 2026).min).toBe(1);
});
it("shows closed-month banner and carries only the source month in React Router state", async () => {
  mount();
  await ready();
  expect(screen.queryByTestId("M25-398")).toBeNull();
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "10" },
  });
  await ready("202610");
  // 시안 lockBanner: 기준월만 굵게(<strong>2026-10</strong>는 마감되었습니다. …)
  expect(screen.getByTestId("banner-lock")).toHaveTextContent(
    "2026-10는 마감되었습니다. 고칠 내용은 다음 달 전월 조정으로 입력하세요.",
  );
  expect(screen.getByTestId("banner-lock").querySelector("strong")).toHaveTextContent(
    "2026-10",
  );
  fireEvent.click(screen.getByTestId("M25-398"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    '"pathname":"/screens/UI-10"',
  );
  expect(screen.getByTestId("location")).toHaveTextContent(
    '"financeAdjustmentSourceMonth":"202610"',
  );
  expect(
    request.mock.calls.some(
      ([, init]) =>
        init?.method === "PUT" && String(init.body).includes("adjustments"),
    ),
  ).toBe(false);
});
it("ignores stale responses after another base month is selected", async () => {
  let resolve!: (value: ReturnType<typeof ok>) => void;
  const original = request.getMockImplementation()!;
  request.mockImplementation((path: string, init?: RequestInit) =>
    path.includes("business-plan-comparisons?") &&
    path.includes("baseMonth=202611")
      ? new Promise<ReturnType<typeof ok>>((done) => {
          resolve = done;
        })
      : original(path, init),
  );
  mount();
  await waitFor(() => expect(reads()).toHaveLength(1));
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "10" },
  });
  await ready("202610");
  await act(async () => resolve(ok(report("202611"))));
  expect(screen.getByTestId("business-plan-summary")).toHaveAttribute(
    "data-base-month",
    "202610",
  );
});
it("opens the common B10 file dialog without writing business data", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("M25-400"));
  expect(screen.getByRole("dialog")).toHaveTextContent("목표 업로드");
  expect(screen.getByRole("dialog")).toHaveTextContent(
    "연월 / 매출 목표 / 영업이익 목표",
  );
  expect(
    request.mock.calls.some(([path]) => path === "/api/common/uploads"),
  ).toBe(false);
});

/** SFR-118-04 목표 업로드(B.10): 공통 업로드 검사 → (마감한 달 목표가 바뀌면 확인창) → 반영 → 결과 창. */
function uploadFlow(issues: { row: number; column: string; inputValue: string; reason: string; severity: "경고" }[]) {
  const checked = {
    uploadId: "31",
    modifiedAt: null,
    statusName: "검사 완료",
    successCount: 12 - issues.length,
    warningCount: issues.length,
    errorCount: 0,
    issues,
  };
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/uploads" && init?.method === "POST") return ok(checked);
    if (path === "/api/common/uploads/31") return ok(checked);
    if (path === "/api/common/uploads/31/apply")
      return ok({ ...checked, statusName: "반영 완료" });
    if (path.startsWith("/api/common/code-groups/") || path.includes("UPLD_STAT"))
      return ok({ items: [{ code: "APPLIED", codeName: "반영 완료" }] });
    return original(path, init);
  });
}
const applies = () =>
  request.mock.calls.filter(([path]) => path === "/api/common/uploads/31/apply");
async function checkFile() {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("M25-400"));
  fireEvent.change(screen.getByTestId("upload-file"), {
    target: { files: [new File(["x"], "plan.xlsx")] },
  });
  fireEvent.click(screen.getByTestId("upload-check"));
  await screen.findByTestId("upload-result-dialog");
  await waitFor(() => expect(screen.getByTestId("upload-apply")).toBeEnabled());
}
const changed = {
  row: 2,
  column: "연월",
  inputValue: "2026-10",
  reason: "마감한 달 목표가 지금 목표와 다릅니다.",
  severity: "경고" as const,
};
const empty = (month: string, row: number) => ({
  row,
  column: "연월",
  inputValue: month,
  reason: "목표가 빈 달 — 오류 없이 0(목표 없음)으로 올립니다.",
  severity: "경고" as const,
});
it("asks with the mockup's three choices when a closed month target changes and cancel writes nothing", async () => {
  uploadFlow([changed]);
  await checkFile();
  fireEvent.click(screen.getByTestId("upload-apply"));
  const dialog = await screen.findByTestId("business-plan-closed-confirm");
  expect(dialog).toHaveTextContent("목표 업로드 · 마감한 달 목표가 바뀝니다");
  expect(dialog).toHaveTextContent("마감한 달(10월)의 목표가 지금 목표와 다릅니다.");
  expect(within(dialog).getAllByRole("button").map((b) => b.textContent)).toEqual(
    expect.arrayContaining(["취소", "마감한 달은 두기", "전체 변경"]),
  );
  fireEvent.click(screen.getByTestId("business-plan-closed-cancel"));
  await waitFor(() => expect(screen.queryByTestId("business-plan-closed-confirm")).toBeNull());
  expect(applies()).toHaveLength(0);
});
it("sends keep with the common apply request and shows the result with empty months", async () => {
  uploadFlow([changed, empty("2026-11", 12), empty("2026-12", 13)]);
  await checkFile();
  fireEvent.click(screen.getByTestId("upload-apply"));
  fireEvent.click(await screen.findByTestId("business-plan-closed-keep"));
  await waitFor(() => expect(applies()).toHaveLength(1));
  expect(JSON.parse(String(applies()[0][1]?.body))).toEqual({
    modifiedAt: null,
    closedMonthPolicy: "keep",
  });
  const done = await screen.findByTestId("business-plan-upload-done");
  expect(done).toHaveTextContent("마감 안 된 달 목표만 새 파일대로 바꿨습니다. 마감한 10월은 그대로 두었습니다.");
  expect(done).toHaveTextContent("목표가 빈 달: 11월, 12월 — 오류 없이 0(목표 없음)으로 올렸습니다.");
});
it("sends replace for 전체 변경", async () => {
  uploadFlow([changed]);
  await checkFile();
  fireEvent.click(screen.getByTestId("upload-apply"));
  fireEvent.click(await screen.findByTestId("business-plan-closed-replace"));
  await waitFor(() => expect(applies()).toHaveLength(1));
  expect(JSON.parse(String(applies()[0][1]?.body)).closedMonthPolicy).toBe("replace");
  expect(await screen.findByTestId("business-plan-upload-done")).toHaveTextContent(
    "1~12월 목표를 새 파일대로 바꿨습니다. 마감한 달 달성률도 새 목표로 계산합니다.",
  );
});
it("uploads unchanged closed months without the confirm dialog or a choice value", async () => {
  uploadFlow([]);
  await checkFile();
  fireEvent.click(screen.getByTestId("upload-apply"));
  await waitFor(() => expect(applies()).toHaveLength(1));
  expect(JSON.parse(String(applies()[0][1]?.body))).toEqual({ modifiedAt: null });
  expect(screen.queryByTestId("business-plan-closed-confirm")).toBeNull();
  expect(await screen.findByTestId("business-plan-upload-done")).not.toHaveTextContent("목표가 빈 달");
});

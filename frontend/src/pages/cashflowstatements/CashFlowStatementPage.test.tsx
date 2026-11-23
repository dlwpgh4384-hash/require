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
import { MemoryRouter, RouterProvider, useLocation } from "react-router-dom";
import { createAppRouter } from "../../app/router";
import {
  CashFlowStatementPage,
  cashFlowBalanceSeries,
} from "./CashFlowStatementPage";
import {
  CashFlowStatementTitleActions,
  CashFlowStatementWorkspaceProvider,
} from "./CashFlowStatementWorkspace";
import { LineChart } from "../profitstructures/ProfitStructureCharts";
import type { CashFlowStatementResult } from "./cashFlowStatements";
import * as api from "./cashFlowStatements";

function report(
  year = 2026,
  empty = false,
  warnings = true,
): CashFlowStatementResult {
  const months = Array.from({ length: 12 }, (_, index) => ({
    yearMonth: `${year}${String(index + 1).padStart(2, "0")}`,
    closed: index < 10,
    openingBalance: empty ? null : 1000000000,
    closingBalance: empty ? null : 900000000,
    cashSales: empty ? null : 1000000,
    miscIncome: empty ? null : 0,
    loan: empty ? null : 0,
    incomeTotal: empty ? null : 1000000,
    regularPayroll: empty ? null : 100000000,
    contractFreelancePayroll: empty ? null : 1000000,
    outsourcing: empty ? null : 100000,
    unpaidPayroll: empty ? null : 0,
    overhead: empty ? null : 0,
    corporateTaxPayment: empty ? null : 0,
    expenseTotal: empty ? null : 101100000,
  }));
  const warningRows =
    empty || !warnings
      ? []
      : months.slice(10).map((m) => ({
          yearMonth: m.yearMonth,
          expectedBalance: 900000000,
          minimumCash: 1000000000,
        }));
  const labels = [
    "전월이월금",
    "수입",
    "현금매출",
    "잡수익",
    "대여금",
    "수입 합계",
    "지출",
    "인건비(정규직)",
    "인건비(계약직·프리랜서)",
    "용역비(외주)",
    "미지급 인건비",
    "제경비",
    "법인세 납부",
    "지출 합계",
    "이월금",
  ];
  return {
    year,
    minimumCash: 1000000000,
    months,
    warnings: warningRows,
    numericTable: {
      columns: [
        ...months.map((m, i) => ({
          label: `${i + 1}월`,
          actual: m.closed,
          warning: warningRows.some((w) => w.yearMonth === m.yearMonth),
        })),
        ...["상반기", "하반기", "연간"].map((label) => ({
          label,
          actual: null,
          warning: false,
        })),
      ],
      rows: labels.map((label, index) => ({
        label,
        style: [1, 6].includes(index)
          ? "group"
          : [0, 5, 13, 14].includes(index)
            ? "bold"
            : "indent",
        values: Array.from({ length: 15 }, () =>
          empty || [1, 6].includes(index) ? null : 1000000,
        ),
      })),
    },
  };
}
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data }),
});
let request: ReturnType<typeof vi.fn>;
let appRouter: ReturnType<typeof createAppRouter> | undefined;
let empty: boolean, failure: boolean, warning: boolean, missingBasis: boolean;
beforeEach(() => {
  empty = false;
  failure = false;
  warning = true;
  missingBasis = false;
  request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url.startsWith("/api/common/user-preferences/"))
      return ok(
        {
          prefTypeCd: url.split("/").at(-2),
          prefKey: url.split("/").at(-1),
          found: false,
          prefVal: null,
          modDttm: null,
        },
        missingBasis
          ? {
              periodBasis: null,
              currentYearMonth: "202611",
              periodBasisError: "기준 미설정",
            }
          : {
              periodBasis: {
                latestYm: "202611",
                cashBaseYm: "202501",
                closedMonths: ["202610"],
              },
            },
      );
    if (failure)
      return {
        ok: false,
        status: 400,
        json: async () => ({
          success: false,
          message: "연도 오류",
          errors: [{ field: "year", message: "조회 연도를 확인해 주세요." }],
        }),
      };
    return ok(
      report(
        Number(new URL(url, "http://test.invalid").searchParams.get("year")),
        empty,
        warning,
      ),
    );
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  appRouter?.dispose();
  appRouter = undefined;
  window.history.replaceState(null, "", "/");
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
function Location() {
  return (
    <output data-testid="current-location">{useLocation().pathname}</output>
  );
}
function open() {
  return render(
    <MemoryRouter initialEntries={["/screens/UI-21"]}>
      <CashFlowStatementWorkspaceProvider>
        <CashFlowStatementTitleActions />
        <CashFlowStatementPage />
        <Location />
      </CashFlowStatementWorkspaceProvider>
    </MemoryRouter>,
  );
}
it.each(["/screens/UI-21", "/"])(
  "renders UI-21 through the shared application router from %s",
  async (path) => {
    vi.stubGlobal(
      "matchMedia",
      vi.fn(() => ({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    const original = request.getMockImplementation()!;
    request.mockImplementation(async (url: string, init?: RequestInit) =>
      url === "/api/common/menus"
        ? ok(
            [
              {
                menuId: "G007",
                upperMenuId: null,
                menuNm: "재무·자금",
                scrnId: null,
                menuLvl: 1,
                sortOrd: 1,
                pcOnlyYn: "N",
                iconNm: null,
              },
              {
                menuId: "M023",
                upperMenuId: "G007",
                menuNm: "자금수지표",
                scrnId: "UI-21",
                menuLvl: 2,
                sortOrd: 1,
                pcOnlyYn: "N",
                iconNm: null,
              },
              {
                menuId: "M024",
                upperMenuId: "G007",
                menuNm: "준비 화면",
                scrnId: "UI-16",
                menuLvl: 2,
                sortOrd: 2,
                pcOnlyYn: "N",
                iconNm: null,
              },
            ],
            {
              screenAccess: [
                { scrnId: "UI-21", available: true, pcOnlyYn: "N" },
                { scrnId: "UI-16", available: true, pcOnlyYn: "N" },
              ],
            },
          )
        : original(url, init),
    );
    window.history.replaceState(null, "", path);
    appRouter = createAppRouter();
    render(<RouterProvider router={appRouter} />);
    if (path === "/")
      fireEvent.click(await screen.findByRole("link", { name: "자금수지표" }));
    expect(await screen.findByTestId("cash-flow-table")).toBeVisible();
    expect(window.location.pathname).toBe("/screens/UI-21");
    expect(
      within(screen.getByTestId("screen-actions")).getAllByTestId(
        "cash-flow-excel",
      ),
    ).toHaveLength(1);
    fireEvent.click(screen.getByRole("link", { name: "준비 화면" }));
    expect(
      await screen.findByRole("region", { name: "UI-16 본문" }),
    ).toHaveTextContent("준비 중");
  },
);
it("shows year-only selector and readonly fifteen-by-fifteen table", async () => {
  open();
  const table = await screen.findByTestId("cash-flow-table");
  expect(screen.getByTestId("period-selector")).toHaveAttribute(
    "data-kind",
    "year",
  );
  expect(screen.queryByTestId("period-month")).not.toBeInTheDocument();
  expect(screen.queryByText("누계 기준월")).not.toBeInTheDocument();
  expect(within(table).getAllByRole("row")).toHaveLength(16);
  expect(within(table).getAllByRole("row")[0].children).toHaveLength(16);
  expect(screen.getByText("2026년 자금수지 (부가세 포함)")).toBeVisible();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("기초잔액 입력")).not.toBeInTheDocument();
  expect(screen.getByTestId("cash-flow-row-2")).toHaveClass(
    "cash-flow-row-indent",
  );
  expect(screen.getByTestId("cash-flow-row-1")).toHaveClass(
    "cash-flow-row-group",
  );
  expect(screen.getByTestId("cash-flow-row-14")).toHaveClass(
    "cash-flow-row-bold",
  );
});
it("uses common year bounds and loads changed year without a month argument", async () => {
  open();
  await screen.findByTestId("cash-flow-table");
  const year = screen.getByTestId("period-year");
  fireEvent.change(year, { target: { value: "2025" } });
  await screen.findByText("2025년 자금수지 (부가세 포함)");
  const paths = request.mock.calls
    .map(([path]) => String(path))
    .filter((path) => path.startsWith("/api/common/cash-flow-statements?"));
  expect(paths).toContain("/api/common/cash-flow-statements?year=2025");
  expect(paths.every((path) => !path.includes("baseMonth"))).toBe(true);
});
// 46번 점검(2026-11-23): 시안처럼 부족한 달을 띠 하나에 묶고 예상 잔액은 첫 부족 달 값, [미수금 보기]는 단추.
it("shows one warning banner listing shortage months with focusable header explanations and UI-13 handoff", async () => {
  open();
  await screen.findByTestId("cash-flow-table");
  expect(screen.getAllByTestId("banner-bad")).toHaveLength(1);
  const banner = screen.getByTestId("cash-flow-warning");
  expect(within(banner).getByText("2026-11, 2026-12").tagName).toBe("STRONG");
  expect(banner).toHaveTextContent("예상 잔액 9.0억 원");
  expect(within(banner).getByRole("button", { name: "미수금 보기" })).toHaveClass("ls-btn", "ls-btn-sm");
  expect(
    screen.getByRole("img", { name: "11월 자금 부족 예상" }),
  ).toHaveAttribute("tabindex", "0");
  const icon = screen.getByRole("img", { name: "11월 자금 부족 예상" });
  expect(icon).toHaveClass("cash-flow-warning-icon");
  expect(
    document.getElementById(icon.getAttribute("aria-describedby")!),
  ).toHaveTextContent("예상 잔액 9.0억 원 · 최소 운영자금 10.0억 원");
  fireEvent.click(screen.getByTestId("cash-flow-receivables"));
  expect(screen.getByTestId("current-location")).toHaveTextContent(
    "/screens/UI-13",
  );
});
it("hides banners and receivable links without shortage", async () => {
  warning = false;
  open();
  await screen.findByTestId("cash-flow-table");
  expect(screen.queryByTestId("banner-bad")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "미수금 보기" }),
  ).not.toBeInTheDocument();
});
it("retains chart table and unit selector with dashes when no data", async () => {
  empty = true;
  open();
  const table = await screen.findByTestId("cash-flow-table");
  // 15줄 × 15칸, 「수입」·「지출」 묶음 줄은 시안처럼 빈칸(46번 점검)
  expect(within(table).getAllByText("–")).toHaveLength(13 * 15);
  expect(screen.getByTestId("cash-flow-balance-chart")).toBeInTheDocument();
  expect(screen.getByTestId("money-unit")).toBeVisible();
  expect(screen.getByText("단위: 백만 원 · 부가세 포함")).toBeVisible();
});
it("emphasizes the metadata month without introducing month selection controls", async () => {
  open();
  await screen.findByTestId("cash-flow-table");
  const cell = screen.getByTestId("cash-flow-row-2").children[11];
  expect(cell).toHaveClass("is-forecast", "is-selected");
  expect(screen.getByTestId("cash-flow-row-2").children[3]).not.toHaveClass(
    "is-selected",
  );
  expect(
    within(screen.getByTestId("cash-flow-table")).queryByRole("button"),
  ).not.toBeInTheDocument();
});
it("connects forecast to the last actual point and passes raw won to the guide", async () => {
  const data = report();
  const series = cashFlowBalanceSeries(data);
  expect(series[0].values[9]).toBe(900000000);
  expect(series[1].values[9]).toBe(series[0].values[9]);
  expect(series[1].values[8]).toBeNull();
  open();
  await screen.findByTestId("cash-flow-table");
  // 시안 lineChart guide: 가는 점선(5 4)과 글자, 범례에는 없음(46번 점검)
  const chart = screen.getByTestId("cash-flow-balance-chart");
  expect(within(chart as unknown as HTMLElement).getByText("최소 운영자금")).toBeInTheDocument();
  const paths = chart.querySelectorAll("path");
  expect(paths).toHaveLength(2);
  expect(paths[1]).toHaveAttribute("stroke-dasharray", "6 4");
  expect(chart.querySelector('line[stroke-dasharray="5 4"]')).not.toBeNull();
  expect(screen.getByText("월말 잔액(실적)")).toBeVisible();
  expect(screen.queryAllByText("최소 운영자금")).toHaveLength(1);
});
it("keeps existing guide-less line chart rendering unchanged", () => {
  const props = {
    series: [{ name: "실적", color: "blue", values: [1000000, 2000000] }],
    labels: ["1월", "2월"],
  };
  const { container, rerender } = render(<LineChart {...props} />);
  const before = container.innerHTML;
  rerender(<LineChart {...props} guide={null} />);
  expect(container.innerHTML).toBe(before);
  rerender(
    <LineChart
      {...props}
      guide={{ name: "기준", values: [1000000000, 1000000000] }}
    />,
  );
  expect(container.querySelectorAll("path")).toHaveLength(1);
  expect(container.querySelector('line[stroke-dasharray="5 4"]')).not.toBeNull();
  expect(screen.getByText("기준")).toBeInTheDocument();
});
it("renders errors and field validation then retries", async () => {
  failure = true;
  open();
  await screen.findByTestId("cash-flow-error");
  expect(
    within(screen.getByTestId("cash-flow-period")).getByText(
      "year: 조회 연도를 확인해 주세요.",
    ),
  ).toBeVisible();
  expect(screen.getByTestId("period-year")).toHaveFocus();
  expect(screen.getByTestId("cash-flow-table-pending")).toBeVisible();
  expect(screen.getByTestId("cash-flow-balance-chart")).toBeInTheDocument();
  expect(screen.getByTestId("cash-flow-excel")).toBeDisabled();
  failure = false;
  fireEvent.click(screen.getByTestId("cash-flow-retry"));
  await screen.findByTestId("cash-flow-table");
});
it("downloads the selected year and displayed unit from the title portal", async () => {
  const download = vi
    .spyOn(api, "getCommonCashFlowStatementsExcel")
    .mockResolvedValue("자금수지표.xlsx");
  open();
  await screen.findByTestId("cash-flow-table");
  fireEvent.change(screen.getByTestId("money-unit"), {
    target: { value: "원" },
  });
  const button = within(
    screen.getByTestId("cash-flow-title-actions"),
  ).getByRole("button", { name: /엑셀 다운로드/ });
  await waitFor(() => expect(button).toBeEnabled());
  fireEvent.click(button);
  await waitFor(() =>
    expect(download).toHaveBeenCalledWith({ year: 2026 }, "원"),
  );
  expect(await screen.findByTestId("cash-flow-toast")).toHaveTextContent(
    "내려받았습니다",
  );
});
it("reports failed export without a false success toast", async () => {
  vi.spyOn(api, "getCommonCashFlowStatementsExcel").mockRejectedValue(
    new Error("failed"),
  );
  open();
  await screen.findByTestId("cash-flow-table");
  await waitFor(() =>
    expect(screen.getByTestId("cash-flow-excel")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("cash-flow-excel"));
  expect(await screen.findByTestId("cash-flow-toast")).toHaveTextContent(
    "받지 못했습니다",
  );
});
it("uses DB Korean year fallback without fabricating a cash baseline", async () => {
  missingBasis = true;
  empty = true;
  open();
  await screen.findByTestId("cash-flow-table");
  expect(screen.getByTestId("cash-flow-year")).toHaveValue("2026");
  expect(screen.queryByTestId("period-month")).not.toBeInTheDocument();
  expect(screen.queryByTestId("banner-bad")).not.toBeInTheDocument();
});
it("places fallback year validation by its selector and focuses it", async () => {
  missingBasis = true;
  failure = true;
  open();
  await screen.findByTestId("cash-flow-error");
  expect(
    within(screen.getByTestId("cash-flow-period")).getByText(
      "year: 조회 연도를 확인해 주세요.",
    ),
  ).toBeVisible();
  expect(screen.getByTestId("cash-flow-year")).toHaveFocus();
  expect(screen.getByTestId("cash-flow-excel")).toBeDisabled();
});
it("keeps the chosen money unit when loading a different year", async () => {
  const realFetch = request.getMockImplementation()!;
  let resolveYear!: (value: unknown) => void;
  request.mockImplementation((url: string, init?: RequestInit) =>
    url === "/api/common/cash-flow-statements?year=2025"
      ? new Promise((resolve) => {
          resolveYear = resolve;
        })
      : realFetch(url, init),
  );
  open();
  await screen.findByTestId("cash-flow-table");
  fireEvent.change(screen.getByTestId("money-unit"), {
    target: { value: "원" },
  });
  fireEvent.change(screen.getByTestId("period-year"), {
    target: { value: "2025" },
  });
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/cash-flow-statements?year=2025",
      expect.anything(),
    ),
  );
  expect(screen.getByTestId("money-unit")).toHaveValue("원");
  expect(screen.getByTestId("cash-flow-table-pending")).toBeVisible();
  expect(
    screen.queryByTestId("cash-flow-warning-202611"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("cash-flow-excel")).toBeDisabled();
  await act(async () => resolveYear(ok(report(2025))));
  await screen.findByTestId("cash-flow-table");
  expect(screen.getByTestId("money-unit")).toHaveValue("원");
  expect(screen.getByTestId("cash-flow-row-2").children[11]).not.toHaveClass(
    "is-selected",
  );
});
it("ignores a slow old year response after the year changes", async () => {
  const realFetch = request.getMockImplementation()!;
  let resolveOld!: (value: unknown) => void;
  request.mockImplementation((url: string, init?: RequestInit) =>
    String(url) === "/api/common/cash-flow-statements?year=2026"
      ? new Promise((resolve) => {
          resolveOld = resolve;
        })
      : realFetch(url, init),
  );
  open();
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/cash-flow-statements?year=2026",
      expect.anything(),
    ),
  );
  expect(screen.getByTestId("cash-flow-loading")).toBeVisible();
  const pending = screen.getByTestId("cash-flow-table-pending");
  expect(pending).toHaveAttribute("aria-busy", "true");
  expect(within(pending).getAllByRole("columnheader")).toHaveLength(16);
  expect(screen.getByTestId("money-unit")).toBeVisible();
  expect(screen.getByTestId("cash-flow-balance-chart")).toBeInTheDocument();
  expect(screen.getByTestId("cash-flow-excel")).toBeDisabled();
  expect(screen.queryByTestId("banner-bad")).not.toBeInTheDocument();
  fireEvent.change(screen.getByTestId("period-year"), {
    target: { value: "2025" },
  });
  await screen.findByText("2025년 자금수지 (부가세 포함)");
  await act(async () => resolveOld(ok(report(2026))));
  expect(
    screen.queryByText("2026년 자금수지 (부가세 포함)"),
  ).not.toBeInTheDocument();
});

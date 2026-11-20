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
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { ProjectCostAnalysisPage } from "./ProjectCostAnalysisPage";
import type {
  CommonProjectCostAnalysisCauses,
  CommonProjectCostAnalysisRow,
  CommonProjectCostAnalysisResult,
} from "./projectCostAnalyses";

const view = vi.hoisted(() => ({ mobile: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: view.mobile, tablet: false }),
}));
let fetcher: ReturnType<typeof vi.fn>;
let result: CommonProjectCostAnalysisResult;
let causes: CommonProjectCostAnalysisCauses;
let saved: string | null;
let failed: boolean;
let monthlyFailed: boolean;
let validation: { field: string; message: string } | null;
let statusOptions: { code: string; codeName: string; useYn: string }[];
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
const row = (): CommonProjectCostAnalysisRow => ({
  projectId: "P",
  projectName: "프로젝트 알파",
  contractTypeCode: "TK",
  contractTypeName: "턴키",
  projectStatus: "진행",
  originalContractAmount: 1_000_000_000,
  contractAmount: 1_200_000_000,
  plannedMm: 12,
  actualMm: 4,
  cumulativeRevenue: 600_000_000,
  totalCost: 400_000_000,
  cumulativeProfit: 200_000_000,
  cumulativeMarginRate: 33.333,
  expectedFinalMarginRate: 25,
  monthlyMarginRate: null,
  targetDifference: 5,
  budgetUsageRate: 85,
  unitPricePerMm: 150_000_000,
  costPerMm: 100_000_000,
  profitPerMm: 50_000_000,
  freeSupportMm: 0,
  freeSupportCost: 0,
  researchCost: null,
  lowMargin: false,
  budgetExceeded: false,
  alertCount: 0,
  alerts: [],
  budgetColor: "주황",
  missingMonthlyCost: false,
  missingMonthlyCostReason: null,
  remainingCost: 50_000_000,
  remainingCostBasis: "투입 배정의 남은 달 MM × 월 인건비 (2명)",
  primaryCauseName: null,
  primaryCauseAmount: null,
  elapsedMonths: 4,
  totalMonths: 12,
  remainingMonths: 8,
  startDate: "2025-11-01",
  endDate: "2026-10-31",
});
const internalRow = (): CommonProjectCostAnalysisRow => ({
  ...row(),
  projectId: "I",
  projectName: "사내 플랫폼",
  contractTypeCode: "IN",
  contractTypeName: "내부",
  originalContractAmount: null,
  contractAmount: null,
  plannedMm: null,
  cumulativeRevenue: null,
  totalCost: 30_000_000,
  cumulativeProfit: null,
  cumulativeMarginRate: null,
  expectedFinalMarginRate: null,
  targetDifference: null,
  budgetUsageRate: null,
  unitPricePerMm: null,
  costPerMm: null,
  profitPerMm: null,
  freeSupportMm: null,
  freeSupportCost: null,
  researchCost: 30_000_000,
  budgetColor: null,
  remainingCost: null,
  remainingCostBasis: null,
});
beforeEach(() => {
  view.mobile = false;
  saved = null;
  failed = false;
  monthlyFailed = false;
  validation = null;
  statusOptions = [
    { code: "ING", codeName: "진행", useYn: "Y" },
    { code: "END", codeName: "종료", useYn: "Y" },
  ];
  result = {
    summary: {
      cumulativeRevenue: 4_697_239_063,
      cumulativeProfit: 2_041_092_934,
      lowMarginCount: 1,
      budgetExceededCount: 0,
      researchCost: 52_870_886,
    },
    items: [row()],
    totalCount: 1,
    projectOptions: [{ projectId: "P", projectName: "프로젝트 알파" }],
    targetMarginRate: 20,
    monthlyMarginNotice: null,
  };
  causes = {
    items: [
      {
        causeKey: "mm",
        causeName: "투입이 계획보다 많음",
        amount: 30_000_000,
        description: "실제 4.00 MM · 지금까지 계획 3.00 MM (1.00 MM 더 씀)",
        owner: "PM",
        task: "인원 계획을 다시 맞추고 남은 기간 투입을 줄입니다",
        targetScreenId: "UI-11",
        targetLabel: "인원 계획 대비 투입",
        navigationContext: JSON.stringify({ projectId: "P", year: 2026, baseMonth: 2 }),
      },
      {
        causeKey: "free",
        causeName: "무상 지원",
        amount: 5_000_000,
        description: "1.00 MM",
        owner: "영업 파트장",
        task: "고객과 유상 전환이나 추가 계약을 협의합니다",
        targetScreenId: null,
        targetLabel: null,
        navigationContext: null,
      },
      {
        causeKey: "pay",
        causeName: "원가가 단가의 80%를 넘는 인력",
        amount: 0,
        description: "0명 — 받는 단가에 비해 급여·원가가 높음",
        owner: "영업 파트장",
        task: "계약 갱신 때 단가를 다시 협상하거나 인력 교체를 검토합니다",
        targetScreenId: "UI-32",
        targetLabel: "단가 재협상 대상",
        navigationContext: JSON.stringify({ projectId: "P", year: 2026, baseMonth: 2 }),
      },
    ],
    actualProgressRate: 30,
    expectedCostAtProgress: 300_000_000,
    cumulativeCost: 400_000_000,
    progressExcessAmount: 100_000_000,
    remainderAmount: 65_000_000,
    primaryBarColor: "회색",
    referenceNotice: "경보가 없는 프로젝트라 참고용",
    costOverRate: 80,
  };
  fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path.includes("user-preferences"))
      return ok(
        {
          prefTypeCd: "PERIOD",
          prefKey: "UI-04",
          prefVal: path.includes("PERIOD") ? saved : null,
          modDttm: null,
          found: path.includes("PERIOD") && saved !== null,
        },
        {
          periodBasis: {
            cashBaseYm: "202501",
            latestYm: "202602",
            closedMonths: ["202601"],
          },
        },
      );
    if (path.includes("CTRT_TYPE"))
      return ok({
        items: [
          { code: "TK", codeName: "턴키", useYn: "Y" },
          { code: "MO", codeName: "월단위", useYn: "Y" },
          { code: "IN", codeName: "내부", useYn: "Y" },
        ],
      });
    if (path.includes("PROJ_STAT")) return ok({ items: statusOptions });
    if (path.includes("monthly-costs")) {
      if (monthlyFailed) throw new Error("월별 원가 통신 실패");
      return ok({
        items: [
          {
            yearMonth: "202601",
            mm: 2,
            cost: 4_000_000,
            revenue: null,
            distributionRevenue: 5_000_000,
            marginRate: 20,
          },
        ],
      });
    }
    if (path.includes("workforce"))
      return ok({
        items: [
          {
            developerId: "D",
            developerName: "선택 인력",
            jobName: "개발",
            monthlyMm: 0.5,
            allocatedCost: 2_000_000,
            revenue: 2_500_000,
            inputTypeCode: "FREE",
            inputTypeName: "무상지원",
          },
        ],
      });
    if (path.includes("causes")) return ok(causes);
    if (path.includes("simulation")) {
      const url = new URL(path, "http://test");
      const n = Number(url.searchParams.get("headcountChange"));
      return ok({
        virtual: true,
        marginRateBefore: 25,
        marginRateAfter: 25 - n * 5,
        marginRateChange: -n * 5,
        addedRevenue: 0,
        addedCost: n * 30_000_000,
        targetMarginRate: 20,
      });
    }
    if (path.includes("project-cost-analyses/excel"))
      return {
        ok: true,
        status: 200,
        headers: new Headers({
          "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        }),
        blob: async () => new Blob(["test transfer"]),
      };
    if (path.includes("project-cost-analyses")) {
      if (failed) throw new Error("목록 통신 실패");
      if (validation)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: validation!.message,
            errors: [validation],
            meta: {},
          }),
        };
      return ok(result);
    }
    throw new Error(`Unexpected request: ${path} ${init?.method ?? "GET"}`);
  });
  vi.stubGlobal("fetch", fetcher);
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
function show() {
  return render(
    <MemoryRouter>
      <ProjectCostAnalysisPage />
    </MemoryRouter>,
  );
}
async function ready() {
  return within(screen.getByTestId("project-cost-list")).findByRole("cell", {
    name: "프로젝트 알파",
  });
}
async function open() {
  fireEvent.click(await ready());
  return screen.findByTestId("project-cost-detail");
}
const listCalls = () =>
  fetcher.mock.calls
    .map(([path]) => new URL(String(path), "http://test"))
    .filter((url) => url.pathname === "/api/common/project-cost-analyses");

it("first visit shows the latest month (last closed + 1) in the mockup month box with 작업중", async () => {
  show();
  await ready();
  const select = screen.getByRole("combobox", { name: "조회 월" });
  expect(select).toHaveDisplayValue("2026년 2월");
  expect(screen.getByTestId("period-month-badge")).toHaveTextContent("작업중");
  expect(screen.queryByText("올해")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "다음 달" })).toBeDisabled();
  expect(listCalls().at(-1)?.searchParams.get("baseMonth")).toBe("2");
  fireEvent.click(screen.getByRole("button", { name: "이전 달" }));
  await waitFor(() => expect(listCalls().at(-1)?.searchParams.get("baseMonth")).toBe("1"));
  expect(screen.getByTestId("period-month-badge")).toHaveTextContent("마감");
});
it("saved months are used and out-of-range saved months fall back to the latest month", async () => {
  saved = JSON.stringify({ year: 2025, month: 3 });
  show();
  await ready();
  expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveDisplayValue("2025년 3월");
  cleanup();
  saved = JSON.stringify({ year: 2026, month: 7 });
  show();
  await ready();
  expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveDisplayValue("2026년 2월");
});
it("mobile keeps the month dropdown only and shows the table (no cards) with a sticky project column", async () => {
  view.mobile = true;
  show();
  await ready();
  expect(screen.queryByRole("button", { name: "이전 달" })).not.toBeInTheDocument();
  expect(screen.getByRole("combobox", { name: "조회 월" })).toBeVisible();
  expect(screen.queryByTestId("list-cards")).not.toBeInTheDocument();
  expect(screen.getByTestId("list-head-projectName")).toHaveClass("ls-sc");
});
it("five summary cards use mockup labels, 억/만 원 values, 건 and captions without extra sentences", async () => {
  show();
  await ready();
  const cards = screen.getByTestId("project-cost-summary");
  expect(cards.children).toHaveLength(5);
  expect(cards).toHaveTextContent("누적 매출47.0억 원진행·종료 프로젝트");
  expect(cards).toHaveTextContent("누적 이익20.4억 원내부 프로젝트 제외");
  expect(cards).toHaveTextContent("저마진 경보1건목표 마진율보다 5%p 넘게 낮음");
  expect(cards).toHaveTextContent("예산 초과0건예산 소진율 100% 이상");
  expect(cards).toHaveTextContent("연구개발비 누계5,287만 원내부(자체 개발) 프로젝트");
  expect(screen.queryByText(/요약은 목록 조건과 관계없이/)).not.toBeInTheDocument();
});
it("toolbar reads like the mockup and 보기 is a select sending alertOnly", async () => {
  show();
  await ready();
  expect(screen.getByTestId("project-cost-toolbar")).toHaveTextContent(
    "2026-02 기준 · 총 1건 · 행을 누르면 월별 원가·시뮬레이션",
  );
  const alertSelect = screen.getByRole("combobox", { name: "보기" });
  expect(within(alertSelect).getAllByRole("option").map((o) => o.textContent)).toEqual([
    "전체",
    "경보 있는 것만",
  ]);
  fireEvent.change(screen.getByRole("combobox", { name: "프로젝트명" }), { target: { value: "P" } });
  fireEvent.change(alertSelect, { target: { value: "1" } });
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() => {
    const url = listCalls().at(-1)!;
    expect(url.searchParams.get("projectId")).toBe("P");
    expect(url.searchParams.get("alertOnly")).toBe("true");
  });
});
it("table has the 19 mockup columns with ⓘ explanations and mockup number formats", async () => {
  show();
  const cell = await ready();
  const list = screen.getByTestId("project-cost-list");
  const heads = within(list)
    .getAllByRole("columnheader")
    .map((th) => th.textContent?.replace(" ⓘ", "").trim());
  expect(heads).toEqual([
    "프로젝트", "유형", "상태", "원 계약금액", "현재 계약금액", "계획 MM", "실제 MM", "누적 매출",
    "누적 원가", "이익", "누적 마진율", "예상 최종·월간 마진율", "목표 대비", "예산 소진율",
    "MM당 단가", "MM당 원가", "MM당 이익", "무상지원 MM·원가", "경보",
  ]);
  expect(
    within(list).getAllByTitle(/.+/).filter((el) => el.textContent?.includes("ⓘ")),
  ).toHaveLength(4);
  const tr = cell.closest("tr")!;
  expect(tr).toHaveTextContent("12.00");
  expect(tr).toHaveTextContent("4.00");
  expect(tr).toHaveTextContent("33.3%");
  expect(tr).toHaveTextContent("+5.0%p");
  expect(within(tr).getByText("85.0%")).toHaveClass("is-warn");
  expect(within(tr).getByText("진행")).toHaveClass("ls-b-info");
  expect(within(tr).getByTestId("project-cost-final-P")).toHaveAttribute(
    "title",
    "남은 예상 원가 5,000만 원 · 투입 배정의 남은 달 MM × 월 인건비 (2명)",
  );
  // 시안 표는 정렬하지 않는다
  expect(within(list).getByTestId("list-head-projectName")).not.toHaveAttribute("aria-sort");
});
it("missing salary keeps the margin and adds the orange 단가 없음 badge; missing plan says 계획 없음", async () => {
  result.items[0] = {
    ...row(),
    plannedMm: null,
    missingMonthlyCost: true,
    missingMonthlyCostReason: "단가 없음: 홍길동",
  };
  show();
  const tr = (await ready()).closest("tr")!;
  expect(within(tr).getByText("계획 없음")).toBeVisible();
  expect(within(tr).getByText("단가 없음")).toHaveClass("ls-b-warn");
  expect(within(tr).getByTestId("project-cost-final-P")).toHaveTextContent("25.0%");
});
it("internal rows show cost with 연구개발비 and dashes; monthly rows show 월간; footer excludes internal", async () => {
  result.items = [
    row(),
    internalRow(),
    {
      ...row(),
      projectId: "M",
      projectName: "월단위 건",
      contractTypeCode: "MO",
      contractTypeName: "월단위",
      expectedFinalMarginRate: null,
      monthlyMarginRate: 13.486,
      targetDifference: -6.514,
      remainingCost: null,
      remainingCostBasis: null,
      alerts: ["저마진"],
      alertCount: 1,
      primaryCauseName: "원가가 단가의 80%를 넘는 인력",
      primaryCauseAmount: 11_138_668,
      freeSupportMm: 18,
      freeSupportCost: 49_866_750,
    },
  ];
  result.totalCount = 3;
  show();
  await ready();
  const internal = screen.getByTestId("list-row-I");
  expect(within(internal).getByTestId("list-cell-I-totalCost")).toHaveTextContent("30,000 연구개발비");
  expect(within(internal).getByTestId("list-cell-I-contractAmount")).toHaveTextContent("–");
  expect(within(internal).getByTestId("list-cell-I-plannedMm")).toHaveTextContent("–");
  const monthly = screen.getByTestId("list-row-M");
  expect(within(monthly).getByTestId("list-cell-M-expectedFinalMarginRate")).toHaveTextContent(
    "13.5% 월간",
  );
  expect(within(monthly).getByText("−6.5%p")).toHaveClass("ls-neg");
  expect(within(monthly).getByTestId("list-cell-M-freeSupportMm")).toHaveTextContent("18.00 · 49,867");
  expect(within(monthly).getByText("저마진")).toHaveClass("ls-b-warn");
  expect(within(monthly).getByTestId("list-cell-M-alerts")).toHaveTextContent(
    "주원인: 원가가 단가의 80%를 넘는 인력 (1,114만 원)",
  );
  expect(screen.getByTestId("list-footer-projectName")).toHaveTextContent("합계(내부 제외)");
  // 내부 제외: 원가 400,000 + 400,000(천 원)
  expect(screen.getByTestId("list-footer-totalCost")).toHaveTextContent("800,000");
  expect(screen.getByTestId("list-footer-cumulativeMarginRate")).toHaveTextContent("33.3%");
});
it("formula box shows the mockup 11 rows and the target margin from the response", async () => {
  result.targetMarginRate = 18;
  show();
  await ready();
  const box = screen.getByTestId("project-cost-formulas");
  expect(within(box).getAllByRole("row")).toHaveLength(12);
  expect(box).toHaveTextContent("마진율 − 목표 마진율(18%)");
  expect(box).toHaveTextContent("목표 마진율 18%는 기준정보 값입니다.");
  expect(box).not.toHaveTextContent("프로젝트 알파");
});
it("open month without payroll shows the notice", async () => {
  result.monthlyMarginNotice = "2월 급여가 아직 반영되지 않아 월간 마진을 계산하지 않습니다";
  show();
  await ready();
  expect(screen.getByTestId("project-cost-monthly-notice")).toHaveTextContent(
    "2월 급여가 아직 반영되지 않아 월간 마진을 계산하지 않습니다",
  );
});
it("empty response preserves five summaries filters toolbar and table headers", async () => {
  result.items = [];
  result.totalCount = 0;
  show();
  await screen.findByText("조건에 맞는 결과가 없습니다.");
  const download = await within(screen.getByTestId("project-cost-list")).findByRole("button", {
    name: /엑셀/,
  });
  expect(screen.getByTestId("project-cost-summary").children).toHaveLength(5);
  expect(screen.getByRole("combobox", { name: "프로젝트명" })).toBeVisible();
  expect(download).toBeEnabled();
  expect(
    within(screen.getByTestId("project-cost-list")).getByRole("columnheader", { name: /누적 원가/ }),
  ).toBeVisible();
});
it("failed request is not an empty result and retries the same conditions", async () => {
  failed = true;
  show();
  await screen.findByText(/목록 통신 실패/);
  expect(screen.queryByText("조건에 맞는 결과가 없습니다.")).not.toBeInTheDocument();
  failed = false;
  fireEvent.click(screen.getByRole("button", { name: /다시 시도/ }));
  await ready();
});
it("field errors focus the filter, and period errors focus the month box", async () => {
  show();
  await ready();
  validation = { field: "contractTypeCode", message: "계약유형을 확인해 주세요." };
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  // 칸 아래 오류와 목록 오류(같은 문구) 둘 다 보인다
  expect(await screen.findAllByText("계약유형을 확인해 주세요.")).toHaveLength(2);
  expect(screen.getByRole("combobox", { name: "계약유형" })).toHaveFocus();
  validation = { field: "baseMonth", message: "2026년 2월까지 선택할 수 있습니다." };
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() => expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveFocus());
  expect(screen.getByTestId("project-cost-period")).toHaveTextContent(
    "2026년 2월까지 선택할 수 있습니다.",
  );
});
it.each([
  ["ING", "업무 진행 중", "진행"],
  ["END", "업무 종료됨", "종료"],
])("maps %s display names to the existing projectStatus request enum", async (code, label, value) => {
  statusOptions = [{ code, codeName: label, useYn: "Y" }];
  show();
  await ready();
  await screen.findByRole("option", { name: label });
  fireEvent.change(screen.getByRole("combobox", { name: "상태" }), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() => expect(listCalls().at(-1)?.searchParams.get("projectStatus")).toBe(value));
});
it("detail panel: project title, mockup sub line, [프로젝트 정보] and four tabs in order", async () => {
  show();
  const panel = await open();
  expect(panel).toHaveAttribute("aria-label", "프로젝트 알파");
  expect(panel).toHaveTextContent("P · 턴키 · 2025-11-01 ~ 2026-10-31");
  expect(within(panel).getByRole("button", { name: "프로젝트 정보" })).toBeVisible();
  await within(panel).findByRole("tab", { name: "투입 인력 (1)" });
  expect(within(panel).getAllByRole("tab").map((t) => t.textContent)).toEqual([
    "월별 원가",
    "투입 인력 (1)",
    "시뮬레이션",
    "원가 초과 원인",
  ]);
  // 월별 원가: 경과·마진 칸과 월/MM/원가/배분 기준 매출(참고)/마진율
  expect(panel).toHaveTextContent("경과4/12개월남은 기간 8개월");
  expect(panel).toHaveTextContent("예상 최종 마진율25.0%목표 대비 +5.0%p");
  const month = await within(panel).findByTestId("project-cost-month-202601");
  expect(month).toHaveTextContent("2026-012.004,000,0005,000,00020.0%");
  expect(within(panel).getByText("배분 기준 매출(참고)")).toBeVisible();
  expect(panel).toHaveTextContent("턴키의 누적 매출은 진행기준");
  fireEvent.click(within(panel).getByRole("tab", { name: "투입 인력 (1)" }));
  const person = await within(panel).findByTestId("project-cost-workforce-0");
  expect(person).toHaveTextContent("선택 인력개발0.502,000,0002,500,000무상지원");
  expect(within(person).getByText("무상지원")).toHaveClass("ls-b-warn");
});
it("internal detail has only the monthly and workforce tabs with 연구개발비 누계", async () => {
  result.items = [{ ...internalRow(), projectName: "프로젝트 알파" }];
  show();
  const panel = await open();
  await within(panel).findByRole("tab", { name: "투입 인력 (1)" });
  expect(within(panel).getAllByRole("tab")).toHaveLength(2);
  expect(panel).toHaveTextContent("연구개발비 누계3,000만 원판관비로 처리");
});
it("detail failure retries locally without losing the list", async () => {
  monthlyFailed = true;
  show();
  const panel = await open();
  await within(panel).findByText(/월별 원가 통신 실패/);
  monthlyFailed = false;
  fireEvent.click(within(panel).getByRole("button", { name: "다시 시도" }));
  expect(await within(panel).findByText("2026-01")).toBeVisible();
  expect(screen.getByTestId("project-cost-list")).toBeInTheDocument();
});
it("cause tab: progress banner, ordered causes with owner/task, buttons only where the mockup has them", async () => {
  function Destination() {
    const location = useLocation();
    return <pre data-testid="destination">{JSON.stringify(location.state)}</pre>;
  }
  render(
    <MemoryRouter initialEntries={["/screens/UI-04"]}>
      <Routes>
        <Route path="/screens/UI-04" element={<ProjectCostAnalysisPage />} />
        <Route path="/screens/UI-11" element={<Destination />} />
      </Routes>
    </MemoryRouter>,
  );
  const panel = await open();
  fireEvent.click(within(panel).getByRole("tab", { name: "원가 초과 원인" }));
  const first = await within(panel).findByTestId("project-cost-cause-0");
  expect(panel).toHaveTextContent(
    "실적 진척률 30%만큼이면 원가가 3.0억 원여야 하는데 실제로 4.0억 원를 썼습니다 → 1.0억 원 초과",
  );
  expect(panel).toHaveTextContent("경보가 없는 프로젝트라 참고용입니다(빨간색은 경보가 있을 때만).");
  expect(first).toHaveTextContent("투입이 계획보다 많음3,000만 원");
  expect(first).toHaveTextContent("고칠 사람: PM — 인원 계획을 다시 맞추고 남은 기간 투입을 줄입니다");
  expect(first.querySelector(".is-grey")).toBeInTheDocument();
  const free = within(panel).getByTestId("project-cost-cause-1");
  expect(free).toHaveTextContent("고칠 사람: 영업 파트장 — 고객과 유상 전환이나 추가 계약을 협의합니다");
  expect(within(free).queryByRole("button")).not.toBeInTheDocument();
  const zero = within(panel).getByTestId("project-cost-cause-2");
  expect(zero).toHaveTextContent("없음");
  expect(zero).not.toHaveTextContent("고칠 사람");
  expect(panel).toHaveTextContent("나머지 6,500만 원는 위 세 가지로 설명되지 않습니다");
  expect(panel).toHaveTextContent("단가 × MM × 80%");
  fireEvent.click(within(first).getByRole("button", { name: "인원 계획 대비 투입" }));
  expect(await screen.findByTestId("destination")).toHaveTextContent('"projectId":"P"');
});
it("red causes paint only the first bar", async () => {
  causes.primaryBarColor = "빨강";
  causes.referenceNotice = null;
  show();
  const panel = await open();
  fireEvent.click(within(panel).getByRole("tab", { name: "원가 초과 원인" }));
  const first = await within(panel).findByTestId("project-cost-cause-0");
  expect(first.querySelector(".is-bad")).toBeInTheDocument();
  expect(within(panel).getByTestId("project-cost-cause-1").querySelector(".is-bad")).not.toBeInTheDocument();
  expect(panel).not.toHaveTextContent("참고용입니다");
});
it("turnkey simulation starts from mockup defaults, recalculates on input and closes without a confirm", async () => {
  show();
  const panel = await open();
  fireEvent.click(within(panel).getByRole("tab", { name: "시뮬레이션" }));
  expect(panel).toHaveTextContent("인원을 더하거나 빼 보면서 마진이 어떻게 바뀌는지 봅니다.");
  expect(within(panel).getByLabelText("인원 변화 (명, 빼려면 −)")).toHaveValue("1");
  expect(within(panel).getByLabelText("1인 월 원가")).toHaveValue("100,000,000");
  expect(within(panel).getByLabelText("적용 개월")).toHaveValue("8");
  expect(panel).toHaveTextContent("남은 기간 8개월");
  expect(within(panel).queryByLabelText("추가 인원 1인 월 단가")).not.toBeInTheDocument();
  const out = within(panel).getByTestId("project-cost-simulation-result");
  await waitFor(() => expect(out).toHaveTextContent("지금 예상 최종 마진율25.0%"));
  expect(out).toHaveTextContent("바꾼 뒤 가상20.0%");
  expect(out).toHaveTextContent("차이−5.0%p");
  expect(out).toHaveTextContent(
    "원가 3,000만 원가 늘어납니다(턴키는 계약금액이 그대로라 매출은 변하지 않음). 목표 마진율 20% 대비 +0.0%p.",
  );
  fireEvent.change(within(panel).getByLabelText("인원 변화 (명, 빼려면 −)"), {
    target: { value: "-2명" },
  });
  expect(within(panel).getByLabelText("인원 변화 (명, 빼려면 −)")).toHaveValue("-2");
  await waitFor(() => expect(out).toHaveTextContent("원가 −6,000만 원가 줄어듭니다"));
  fireEvent.change(within(panel).getByLabelText("1인 월 원가"), { target: { value: "" } });
  expect(await within(panel).findByText("1인 월 원가를 입력해 주세요.")).toBeVisible();
  fireEvent.click(within(panel).getByRole("button", { name: "닫기" }));
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  expect(screen.queryByTestId("project-cost-detail")).not.toBeInTheDocument();
  expect(
    fetcher.mock.calls.filter(
      ([path, init]) =>
        String(path).includes("project-cost-analyses") && init?.method && init.method !== "GET",
    ),
  ).toHaveLength(0);
});
it("monthly-contract simulation adds the unit price field and shows the open-month notice instead of a result", async () => {
  result.items[0] = {
    ...row(),
    contractTypeCode: "MO",
    contractTypeName: "월단위",
    expectedFinalMarginRate: null,
    monthlyMarginRate: 20,
  };
  result.monthlyMarginNotice = "2월 급여가 아직 반영되지 않아 월간 마진을 계산하지 않습니다";
  show();
  const panel = await open();
  fireEvent.click(within(panel).getByRole("tab", { name: "시뮬레이션" }));
  expect(within(panel).getByLabelText("추가 인원 1인 월 단가")).toHaveValue("150,000,000");
  expect(within(panel).getByLabelText("적용 개월")).toHaveValue("1");
  expect(panel).toHaveTextContent("월단위·파견은 한 달 기준");
  expect(within(panel).getByTestId("project-cost-simulation-result")).toHaveTextContent(
    "2월 급여가 아직 반영되지 않아 월간 마진을 계산하지 않습니다",
  );
  await new Promise((resolve) => setTimeout(resolve, 400));
  expect(fetcher.mock.calls.some(([path]) => String(path).includes("simulation"))).toBe(false);
});

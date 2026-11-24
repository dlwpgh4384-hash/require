import { beforeEach, afterEach, expect, it, vi } from "vitest";
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
import { SalesPipelinePage } from "./SalesPipelinePage";
import * as api from "./salesPipeline";
import { SalesOpportunityForm } from "../salesopportunities/SalesOpportunityForm";
import { quoteCommonSalesOpportunity } from "../salesopportunities/salesOpportunities";
import type { SalesOpportunityOptions } from "../salesopportunities/salesOpportunities";

vi.mock("./salesPipeline", async () => {
  const actual =
    await vi.importActual<typeof import("./salesPipeline")>("./salesPipeline");
  return {
    ...actual,
    getCommonSalesPipeline: vi.fn(),
    getCommonSalesPipelineClosed: vi.fn(),
    getCommonSalesPipelineForecast: vi.fn(),
    getSalesPipelineOptions: vi.fn(),
    getCommonSalesPipelineForecastExcel: vi.fn(),
  };
});
vi.mock("../salesopportunities/salesOpportunities", async () => {
  const actual = await vi.importActual<
    typeof import("../salesopportunities/salesOpportunities")
  >("../salesopportunities/salesOpportunities");
  return { ...actual, quoteCommonSalesOpportunity: vi.fn() };
});
const options: SalesOpportunityOptions = {
  codes: {
    SALES_STEP: [{ code: "FIND", codeName: "발굴" }],
    JOB: [{ code: "BE", codeName: "백엔드" }],
    GRADE: [{ code: "MD", codeName: "중급" }],
    OPP_STAT: [
      { code: "ING", codeName: "진행" },
      { code: "WIN", codeName: "수주" },
      { code: "LOSE", codeName: "실주" },
      { code: "STOP", codeName: "중지" },
    ],
    APRV_STAT: [{ code: "WAIT", codeName: "승인 대기" }],
  },
  customers: [],
  parts: [],
  developers: [],
};
const card = {
  opportunityId: "opp",
  opportunityName: "고객 포털",
  customerId: "customer",
  customerName: "고객사",
  stageCode: "FIND",
  winningProbability: 10,
  expectedAmount: 100000000,
  startMonth: "2026-12",
  nextAction: "제안서 제출",
  nextActionDueDate: "2000-01-01",
  requiredJobs: ["BE", "PM"],
  stale: true,
  approvalStatusCode: "WAIT",
  modifiedAt: "2026-11-01T00:00:00Z",
};
const forecast: api.SalesForecast = {
  months: Array.from({ length: 6 }, (_, i) => ({
    month: i === 0 ? "2026-12" : `2027-0${i}`,
    projectRevenue: 30000000,
    weightedOpportunityRevenue: 15000000,
    allWonRevenue: 80000000,
    demand: 3.4,
    supply: 1.7,
  })),
  hiringReviews: [
    {
      jobCode: "BE",
      jobName: "백엔드",
      shortageStartMonth: "2026-12",
      maximumShortage: 1.7,
      recommendedHiringMonth: "지금",
      candidateCount: 4,
    },
  ],
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.getSalesPipelineOptions).mockResolvedValue(options);
  vi.mocked(api.getCommonSalesPipeline).mockResolvedValue({
    summary: {
      ongoingCount: 1,
      staleCount: 1,
      expectedAmount: 100000000,
      weightedAmount: 10000000,
      weightedManpower: 0.2,
    },
    stages: [
      {
        stageCode: "FIND",
        stageName: "발굴",
        defaultProbability: 10,
        count: 1,
        amount: 100000000,
        items: [card],
      },
    ],
  });
  vi.mocked(api.getCommonSalesPipelineClosed).mockResolvedValue({ items: [] });
  vi.mocked(api.getCommonSalesPipelineForecast).mockResolvedValue(
    structuredClone(forecast),
  );
  vi.mocked(api.getCommonSalesPipelineForecastExcel).mockResolvedValue(6);
  vi.mocked(quoteCommonSalesOpportunity).mockResolvedValue({
    expectedRevenue: 96000000,
    expectedCost: 81600000,
    profitRate: 15,
    minimumUnitPrice: 6800000,
    targetUnitPrice: 8500000,
    breakEvenAmount: 81600000,
    targetAmount: 102000000,
    profitBand: "목표 미달",
    approvalRate: 15,
    needQuotes: [
      { rowIndex: 0, minimumUnitPrice: 6800000, targetUnitPrice: 8500000 },
    ],
  });
});
afterEach(cleanup);
function Location() {
  const location = useLocation();
  return (
    <output data-testid="location">
      {location.pathname}
      {location.search}|{JSON.stringify(location.state)}
    </output>
  );
}
function page() {
  return render(
    <MemoryRouter>
      <SalesPipelinePage />
      <Location />
    </MemoryRouter>,
  );
}
it("renders real card fields, stale and approval labels, and overdue styling", async () => {
  page();
  const row = await screen.findByTestId("sales-card-opp");
  expect(row).toHaveTextContent("고객 포털");
  expect(row).toHaveTextContent("고객사");
  expect(row).toHaveTextContent("2026-12");
  expect(row).toHaveTextContent("백엔드, PM");
  expect(row).toHaveTextContent("미갱신");
  expect(row).toHaveTextContent("승인 대기");
  expect(within(row).getByText(/제안서 제출/)).toHaveClass("sales-overdue");
});
it("recommendation navigation passes opportunity, first job and start month without running AI", async () => {
  page();
  fireEvent.click(await screen.findByTestId("sales-recommend-opp"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    "/screens/UI-30?opportunityId=opp&jobCode=BE&startMonth=2026-12",
  );
  expect(screen.getByTestId("location")).toHaveTextContent('"jobCode":"BE"');
});
it("empty pipeline keeps its usable registration action", async () => {
  vi.mocked(api.getCommonSalesPipeline).mockResolvedValue({
    summary: {
      ongoingCount: 0,
      staleCount: 0,
      expectedAmount: 0,
      weightedAmount: 0,
      weightedManpower: 0,
    },
    stages: [],
  });
  page();
  expect(
    await screen.findByText("조건에 맞는 결과가 없습니다."),
  ).toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByTestId("sales-pipeline-create")).toBeEnabled(),
  );
});
it("failed request exposes retry and successful retry replaces error", async () => {
  vi.mocked(api.getCommonSalesPipeline).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  page();
  expect(await screen.findByText("조회 실패")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("sales-retry"));
  expect(await screen.findByTestId("sales-card-opp")).toBeInTheDocument();
});
it("loading and failed option states keep create disabled without inventing choices", async () => {
  vi.mocked(api.getSalesPipelineOptions).mockRejectedValueOnce(
    new Error("선택 자료 조회 실패"),
  );
  vi.mocked(api.getCommonSalesPipeline).mockReturnValue(new Promise(() => {}));
  page();
  expect(screen.getByText("목록을 불러오는 중입니다.")).toBeInTheDocument();
  expect(await screen.findByText("선택 자료 조회 실패")).toBeInTheDocument();
  expect(screen.getByTestId("sales-pipeline-create")).toBeDisabled();
});
it("closed view sends result and stopped flag separately", async () => {
  page();
  await screen.findByTestId("sales-card-opp");
  fireEvent.click(screen.getByTestId("sales-tab-closed"));
  await waitFor(() =>
    expect(api.getCommonSalesPipelineClosed).toHaveBeenCalledWith("", false),
  );
  fireEvent.change(screen.getByTestId("sales-result-filter"), {
    target: { value: "WIN" },
  });
  await waitFor(() =>
    expect(api.getCommonSalesPipelineClosed).toHaveBeenCalledWith("WIN", false),
  );
  fireEvent.click(screen.getByTestId("sales-include-stopped"));
  await waitFor(() =>
    expect(api.getCommonSalesPipelineClosed).toHaveBeenCalledWith("WIN", true),
  );
});
it("closed win row exposes a project link and loss row exposes only the reason", async () => {
  vi.mocked(api.getCommonSalesPipelineClosed).mockResolvedValue({
    items: [
      {
        opportunityId: "won",
        opportunityName: "수주 건",
        customerName: "고객",
        result: "WIN",
        expectedAmount: 1,
        resultDate: "2026-11-01",
        projectId: "p",
        lossReason: null,
      },
      {
        opportunityId: "lost",
        opportunityName: "실주 건",
        customerName: "고객",
        result: "LOSE",
        expectedAmount: 1,
        resultDate: "2026-11-02",
        projectId: null,
        lossReason: "가격 차이",
      },
    ],
  });
  page();
  await screen.findByTestId("sales-card-opp");
  fireEvent.click(screen.getByTestId("sales-tab-closed"));
  expect(await screen.findByTestId("sales-project-won")).toBeInTheDocument();
  expect(await screen.findByText("가격 차이")).toBeInTheDocument();
  expect(screen.queryByTestId("sales-project-lost")).not.toBeInTheDocument();
});
it("forecast renders six months, four cards, hiring navigation and display-only unit changes", async () => {
  const original = structuredClone(forecast);
  page();
  fireEvent.click(screen.getByTestId("sales-tab-forecast"));
  const panel = await screen.findByTestId("sales-forecast-panel");
  expect(
    within(panel).getByRole("group", { name: "매출·인력 예측 요약" }).children,
  ).toHaveLength(4);
  expect(within(panel).getAllByTestId(/^sales-revenue-/)).toHaveLength(6);
  expect(within(panel).getByText("모두 따면 (참고)")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("sales-money-unit"), {
    target: { value: "100000000" },
  });
  expect(screen.getByTestId("sales-revenue-2026-12")).toHaveTextContent("0.45");
  expect(forecast).toEqual(original);
  fireEvent.click(screen.getByTestId("sales-candidates-be"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    "/screens/UI-33?jobCode=BE",
  );
});
it("successful Excel completion uses returned count", async () => {
  page();
  fireEvent.click(screen.getByTestId("sales-tab-forecast"));
  fireEvent.click(await screen.findByTestId("sales-forecast-excel"));
  expect(
    await screen.findByText("엑셀 파일을 만들었습니다 (조회 결과 6건)"),
  ).toBeInTheDocument();
});
it("failed Excel never shows completion", async () => {
  vi.mocked(api.getCommonSalesPipelineForecastExcel).mockRejectedValue(
    new Error("다운로드 실패"),
  );
  page();
  fireEvent.click(screen.getByTestId("sales-tab-forecast"));
  fireEvent.click(await screen.findByTestId("sales-forecast-excel"));
  expect(await screen.findByText("다운로드 실패")).toBeInTheDocument();
  expect(
    screen.queryByText(/엑셀 파일을 만들었습니다/),
  ).not.toBeInTheDocument();
});
it("valid unsaved needs automatically request quote and show per-row prices and bands", async () => {
  const draft = api.salesPipelineDraft();
  draft.needs = [
    {
      jobCode: "BE",
      gradeCode: "MD",
      requiredCount: 2,
      startMonth: "2026-12",
      months: 6,
      monthlyMm: 1,
      estimatedUnitPrice: 8000000,
      tentativeDeveloperIds: [],
    },
  ];
  render(
    <SalesOpportunityForm
      initial={draft}
      options={options}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  expect(await screen.findByText(/손익분기 최소금액/)).toHaveTextContent(
    "81,600,000",
  );
  expect(screen.getByText(/목표 미달/)).toHaveTextContent("승인 기준 15%");
  fireEvent.change(screen.getByTestId("sales-need-0-requiredcount"), {
    target: { value: "3" },
  });
  await waitFor(() =>
    expect(quoteCommonSalesOpportunity).toHaveBeenLastCalledWith(
      expect.arrayContaining([expect.objectContaining({ requiredCount: 3 })]),
    ),
  );
});
it("incomplete draft stays editable and does not call quote", async () => {
  const draft = api.salesPipelineDraft();
  draft.needs = [
    {
      jobCode: "",
      gradeCode: null,
      requiredCount: 0,
      startMonth: "",
      months: 6,
      monthlyMm: 1,
      estimatedUnitPrice: null,
      tentativeDeveloperIds: [],
    },
  ];
  render(
    <SalesOpportunityForm
      initial={draft}
      options={options}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  expect(quoteCommonSalesOpportunity).not.toHaveBeenCalled();
  expect(screen.getByTestId("sales-need-0-requiredcount")).toBeEnabled();
});
it("closed opportunity form is read-only and never quotes", () => {
  const row = {
    ...api.salesPipelineDraft(),
    opportunityId: "closed",
    customerName: "고객",
    modifiedAt: "2026-11-01T00:00:00Z",
    opportunityStatusCode: "LOSE",
    approvalStatusCode: "NONE",
    projectId: null,
    resultDate: "2026-11-01",
    lossReasonCode: "PRICE",
    approvalHistory: [],
  };
  render(
    <SalesOpportunityForm
      initial={row}
      opportunity={row}
      options={options}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByTestId("sales-opportunity-save")).toBeDisabled();
  expect(
    screen.getByText("종료된 기회는 수정할 수 없습니다."),
  ).toBeInTheDocument();
  expect(quoteCommonSalesOpportunity).not.toHaveBeenCalled();
});

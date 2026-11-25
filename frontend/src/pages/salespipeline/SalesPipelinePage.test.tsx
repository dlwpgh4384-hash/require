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
import { SalesOpportunityForm } from "./SalesOpportunityForm";
import { quoteCommonSalesOpportunity } from "./salesOpportunities";
import type { SalesOpportunityOptions } from "./salesOpportunities";
import {
  subscribeAiRecommendation,
  type AiRecommendationEvent,
} from "../airecommendations/aiRecommendationRequest";
import { withdrawalRefreshEvent } from "../withdrawals/withdrawals";

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
    putCommonSalesPipelineStage: vi.fn(),
  };
});
vi.mock("./salesOpportunities", async () => {
  const actual = await vi.importActual<
    typeof import("./salesOpportunities")
  >("./salesOpportunities");
  return { ...actual, quoteCommonSalesOpportunity: vi.fn() };
});
const options: SalesOpportunityOptions = {
  codes: {
    SALES_STEP: [{ code: "FIND", codeName: "발굴" }],
    JOB: [
      { code: "BE", codeName: "백엔드 개발" },
      { code: "PM", codeName: "PM" },
    ],
    GRADE: [{ code: "MD", codeName: "중급" }],
    OPP_STAT: [
      { code: "ING", codeName: "진행" },
      { code: "WIN", codeName: "수주" },
      { code: "LOSE", codeName: "실주" },
      { code: "STOP", codeName: "사용 안 함" },
    ],
    APRV_STAT: [
      { code: "NONE", codeName: "승인 불필요" },
      { code: "WAIT", codeName: "승인 대기" },
    ],
  },
  customers: [],
  parts: [],
  developers: [],
};
const card: api.PipelineCard = {
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
const plain: api.PipelineCard = {
  ...card,
  opportunityId: "plain",
  opportunityName: "물류 개선",
  stageCode: "PROP",
  winningProbability: 30,
  expectedAmount: 54000000,
  nextAction: "담당자 미팅",
  nextActionDueDate: "2999-10-22",
  requiredJobs: ["BE"],
  requiredJobHeadcounts: [{ jobCode: "BE", jobName: "백엔드 개발", headcount: 2 }],
  stale: false,
  approvalStatusCode: "NONE",
};
const pipeline = (): api.SalesPipeline => ({
  summary: {
    ongoingCount: 7,
    staleCount: 2,
    expectedAmount: 9780000000,
    weightedAmount: 5104000000,
    weightedManpower: 23.4,
    closedCount: 2,
  },
  stages: [
    {
      stageCode: "FIND",
      stageName: "발굴",
      defaultProbability: 10,
      count: 1,
      amount: 1240000000,
      items: [{ ...card }],
    },
    {
      stageCode: "PROP",
      stageName: "제안",
      defaultProbability: 30,
      count: 1,
      amount: 54000000,
      items: [{ ...plain }],
    },
    {
      stageCode: "NEGO",
      stageName: "협상",
      defaultProbability: 60,
      count: 0,
      amount: 0,
      items: [],
    },
  ],
});
const forecast: api.SalesForecast = {
  months: Array.from({ length: 6 }, (_, i) => ({
    month: i === 0 ? "2026-12" : `2027-0${i}`,
    projectRevenue: 30000000,
    weightedOpportunityRevenue: 15000000,
    allWonRevenue: 80000000,
    demand: 3.4,
    supply: 1.7,
  })),
  // 기회별 줄(식-92): 달마다 줄을 더하면 weightedOpportunityRevenue(15,000,000).
  opportunityRevenues: [
    {
      opportunityId: "opp",
      opportunityName: "고객 포털",
      winningProbability: 10,
      months: Array.from({ length: 6 }, (_, i) => ({
        month: i === 0 ? "2026-12" : `2027-0${i}`,
        weightedRevenue: 10000000,
      })),
    },
    {
      opportunityId: "plain",
      opportunityName: "물류 개선",
      winningProbability: 30,
      months: Array.from({ length: 5 }, (_, i) => ({
        month: i === 0 ? "2026-12" : `2027-0${i}`,
        weightedRevenue: 5000000,
      })),
    },
    {
      opportunityId: "late",
      opportunityName: "늦은 기회",
      winningProbability: 60,
      months: [{ month: "2027-05", weightedRevenue: 5000000 }],
    },
  ],
  hiringReviews: [
    {
      jobCode: "BE",
      jobName: "백엔드 개발",
      shortageStartMonth: "2026-12",
      maximumShortage: 1.7,
      recommendedHiringMonth: "지금",
      candidateCount: 4,
    },
    {
      jobCode: "FE",
      jobName: "프론트엔드 개발",
      shortageStartMonth: "2027-03",
      maximumShortage: 0.6,
      recommendedHiringMonth: "2027-01",
      candidateCount: 0,
    },
  ],
};
const closedRows: api.SalesClosed[] = [
  {
    opportunityId: "won",
    opportunityName: "수주 건",
    customerName: "고객",
    result: "WIN",
    expectedAmount: 1260000000,
    resultDate: "2026-11-01",
    projectId: "p",
    lossReason: null,
  },
  {
    opportunityId: "lost",
    opportunityName: "실주 건",
    customerName: "고객",
    result: "LOSE",
    expectedAmount: 620000000,
    resultDate: "2026-11-02",
    projectId: null,
    lossReason: "가격 · 경쟁사 단가",
  },
];
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.getSalesPipelineOptions).mockResolvedValue(options);
  vi.mocked(api.getCommonSalesPipeline).mockImplementation(async () =>
    pipeline(),
  );
  vi.mocked(api.getCommonSalesPipelineClosed).mockResolvedValue({
    items: closedRows,
  });
  vi.mocked(api.getCommonSalesPipelineForecast).mockResolvedValue(
    structuredClone(forecast),
  );
  vi.mocked(api.getCommonSalesPipelineForecastExcel).mockResolvedValue(6);
  vi.mocked(api.putCommonSalesPipelineStage).mockResolvedValue({
    opportunityId: "opp",
    winningProbability: 30,
  });
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
it("shows the mockup summary cards, tab labels and hint without a page title or unit selector", async () => {
  page();
  const summary = await screen.findByTestId("sales-summary");
  const cards = summary.children;
  expect(cards).toHaveLength(4);
  expect(cards[0]).toHaveTextContent("진행 중인 기회7건2주 갱신 없음 2건");
  expect(cards[1]).toHaveTextContent("예상 금액 합계97.8억 원");
  expect(cards[2]).toHaveTextContent("확률 반영 금액51.0억 원수주 확률 × 예상 금액");
  expect(cards[3]).toHaveTextContent(
    "확률 반영 필요 인력23.4명필요 인원 × 월 MM × 수주 확률",
  );
  expect(await screen.findByTestId("sales-tab-ongoing")).toHaveTextContent(
    "진행 중 (7)",
  );
  await waitFor(() =>
    expect(screen.getByTestId("sales-tab-closed")).toHaveTextContent(
      "종료 · 수주·실주 (2)",
    ),
  );
  expect(screen.getByTestId("sales-tab-forecast")).toHaveTextContent(
    "매출·인력 예측",
  );
  expect(screen.getByTestId("sales-tab-ongoing")).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(
    screen.getByText(
      "카드를 누르면 상세가 열립니다. 카드를 끌어 다른 단계로 옮길 수 있고, 휴대폰에서는 카드 아래 '단계'에서 고릅니다.",
    ),
  ).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "영업 파이프라인" })).toBeNull();
  expect(screen.queryByTestId("money-unit")).toBeNull();
  await waitFor(() =>
    expect(screen.getByTestId("sales-pipeline-create")).toBeEnabled(),
  );
  expect(screen.getByTestId("sales-pipeline-create")).toHaveClass("ls-btn-p");
  fireEvent.click(screen.getByTestId("sales-kpi-manpower"));
  expect(screen.getByTestId("location")).toHaveTextContent("/screens/UI-17");
});
it("renders kanban heads and card fields like the mockup", async () => {
  page();
  const find = await screen.findByTestId("sales-stage-find");
  expect(find).toHaveTextContent("발굴 기본 10%");
  expect(find).toHaveTextContent("1건 · 12.4억 원");
  expect(screen.getByTestId("sales-stage-nego")).toHaveTextContent(
    "이 단계의 기회가 없습니다.",
  );
  const row = screen.getByTestId("sales-card-opp");
  expect(row).toHaveTextContent("고객 포털");
  expect(row).toHaveTextContent("고객사 · 2026-12 시작");
  expect(row).toHaveTextContent("1.0억 원");
  expect(row).toHaveTextContent("확률 10%");
  expect(within(row).getByText("백엔드 개발")).toHaveClass("ls-chip");
  expect(screen.getByTestId("sales-next-opp")).toHaveTextContent(
    "다음: 제안서 제출 · 01-01 (기한 지남)",
  );
  expect(screen.getByTestId("sales-next-opp")).toHaveClass("sales-overdue");
  expect(within(row).getByText("2주 갱신 없음")).toHaveClass("ls-b-warn");
  expect(within(row).getByText("견적 승인 대기")).toHaveClass("ls-b-bad");
  const other = screen.getByTestId("sales-card-plain");
  expect(other).toHaveTextContent("5,400만 원");
  expect(within(other).getByText("백엔드 개발 2")).toHaveClass("ls-chip");
  expect(screen.getByTestId("sales-next-plain")).toHaveTextContent(
    "다음: 담당자 미팅 · 10-22",
  );
  expect(screen.getByTestId("sales-next-plain")).not.toHaveTextContent(
    "기한 지남",
  );
  expect(other).not.toHaveTextContent("승인");
  expect(other).not.toHaveTextContent("NONE");
  expect(other).not.toHaveTextContent("2주 갱신 없음");
  expect(within(other).getByRole("combobox", { name: "단계" })).toHaveValue(
    "PROP",
  );
});
it("approved and rejected quotes show the mockup badges", async () => {
  const value = pipeline();
  value.stages[0].items[0].approvalStatusCode = "OK";
  value.stages[1].items[0].approvalStatusCode = "REJ";
  vi.mocked(api.getCommonSalesPipeline).mockResolvedValue(value);
  page();
  expect(
    within(await screen.findByTestId("sales-card-opp")).getByText("견적 승인"),
  ).toHaveClass("ls-b-ok");
  expect(
    within(screen.getByTestId("sales-card-plain")).getByText("견적 반려"),
  ).toHaveClass("ls-b-lock");
});
it("an AI recommendation tentative signal reloads the open pipeline", async () => {
  page();
  await screen.findByTestId("sales-card-opp");
  const before = vi.mocked(api.getCommonSalesPipeline).mock.calls.length;
  window.dispatchEvent(new Event(withdrawalRefreshEvent));
  await waitFor(() =>
    expect(vi.mocked(api.getCommonSalesPipeline).mock.calls.length).toBe(
      before + 1,
    ),
  );
});
it("recommendation opens UI-30 with opportunity, first job and start month", async () => {
  page();
  fireEvent.click(await screen.findByTestId("sales-recommend-opp"));
  // 주소 query·state 없이 UI-30 요청 통로(createScreenRequest)로 넘긴다.
  expect(screen.getByTestId("location")).toHaveTextContent(
    /^\/screens\/UI-30\|null$/,
  );
  const received: AiRecommendationEvent[] = [];
  subscribeAiRecommendation((event) => received.push(event))();
  expect(received).toEqual([
    expect.objectContaining({
      targetType: "opportunity",
      targetId: "opp",
      jobCode: "BE",
      startMonth: "2026-12",
    }),
  ]);
  expect(screen.queryByTestId("sales-pipeline-detail")).toBeNull();
});
it("the card stage select moves the card with its read time and shows the mockup toast", async () => {
  page();
  const select = await screen.findByTestId("sales-stage-select-opp");
  await waitFor(() => expect(select).toBeEnabled());
  fireEvent.change(select, { target: { value: "PROP" } });
  await waitFor(() =>
    expect(api.putCommonSalesPipelineStage).toHaveBeenCalledWith(
      expect.objectContaining({
        opportunityId: "opp",
        modifiedAt: "2026-11-01T00:00:00Z",
      }),
      "PROP",
    ),
  );
  expect(
    await screen.findByText(
      "「고객 포털」을 제안 단계로 옮겼습니다. 수주 확률 30%",
    ),
  ).toBeInTheDocument();
  await waitFor(() =>
    expect(api.getCommonSalesPipeline).toHaveBeenCalledTimes(2),
  );
  // 새로 고침은 파이프라인만 다시 읽고, 고르는 값(코드·고객사·파트·인력)은 화면을 열 때 한 번만 읽는다.
  expect(api.getSalesPipelineOptions).toHaveBeenCalledTimes(1);
  expect(api.getCommonSalesPipelineClosed).not.toHaveBeenCalled();
  expect(screen.queryByTestId("sales-pipeline-detail")).toBeNull();
});
it("dragging a card onto another column moves it; a kept manual probability is not announced", async () => {
  vi.mocked(api.putCommonSalesPipelineStage).mockResolvedValue({
    opportunityId: "plain",
    winningProbability: 30,
  });
  page();
  const moving = await screen.findByTestId("sales-card-plain");
  const data = new Map<string, string>();
  const dataTransfer = {
    setData: (k: string, v: string) => data.set(k, v),
    getData: (k: string) => data.get(k) || "",
  };
  fireEvent.dragStart(moving, { dataTransfer });
  const target = screen.getByTestId("sales-stage-nego");
  fireEvent.dragOver(target, { dataTransfer });
  expect(target).toHaveClass("is-drop");
  fireEvent.drop(target, { dataTransfer });
  await waitFor(() =>
    expect(api.putCommonSalesPipelineStage).toHaveBeenCalledWith(
      expect.objectContaining({ opportunityId: "plain" }),
      "NEGO",
    ),
  );
  expect(
    await screen.findByText("「물류 개선」을 협상 단계로 옮겼습니다."),
  ).toBeInTheDocument();
  expect(target).not.toHaveClass("is-drop");
});
it("a 409 stage move warns and reloads; the approval rule message is shown as is", async () => {
  vi.mocked(api.putCommonSalesPipelineStage).mockRejectedValueOnce(
    new api.DeveloperRequestError(
      409,
      "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
    ),
  );
  page();
  const select = await screen.findByTestId("sales-stage-select-opp");
  fireEvent.change(select, { target: { value: "PROP" } });
  const warn = await screen.findByText(
    "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
  );
  expect(warn.closest(".app-toast")).toHaveClass("app-toast-warn");
  await waitFor(() =>
    expect(api.getCommonSalesPipeline).toHaveBeenCalledTimes(2),
  );
  vi.mocked(api.putCommonSalesPipelineStage).mockRejectedValueOnce(
    new api.DeveloperRequestError(
      400,
      "「고객 포털」은 견적 승인 전이라 협상 단계로 옮길 수 없습니다.",
    ),
  );
  fireEvent.change(await screen.findByTestId("sales-stage-select-opp"), {
    target: { value: "NEGO" },
  });
  expect(
    await screen.findByText(
      "「고객 포털」은 견적 승인 전이라 협상 단계로 옮길 수 없습니다.",
    ),
  ).toBeInTheDocument();
  expect(screen.getByTestId("sales-stage-select-opp")).toHaveValue("FIND");
});
/** 상세 서랍이 읽는 기회 한 건(공통 요청 fetch). 처리 요청은 기록만 하고 상태를 바꾼다. */
function stubOpportunity() {
  const row = {
    opportunityId: "opp",
    opportunityName: "고객 포털",
    customerId: "customer",
    customerName: "고객사",
    salesPartId: "part",
    stageCode: "FIND",
    winningProbability: 10,
    expectedAmount: 100000000,
    contractTypeCode: null,
    startMonth: "2026-12",
    expectedMonths: 6,
    workplace: null,
    nextAction: null,
    nextActionDueDate: null,
    memo: null,
    needs: [],
    modifiedAt: "2026-11-01T00:00:00Z",
    approvalStatusCode: "NONE",
    opportunityStatusCode: "ING",
    projectId: null,
    resultDate: null,
    lossReasonCode: null,
    approvalHistory: [],
  };
  const request = vi.fn(async (path: string, init?: RequestInit) => {
    if (init?.method === "POST" && path.endsWith("/stop"))
      row.opportunityStatusCode = "STOP";
    return {
      ok: true,
      status: 200,
      json: async () => ({ success: true, meta: {}, data: structuredClone(row) }),
    };
  });
  vi.stubGlobal("fetch", request);
  return request;
}
it("clicking a card opens the shared detail drawer drawn by the opportunity detail", async () => {
  stubOpportunity();
  page();
  fireEvent.click(await screen.findByTestId("sales-card-opp"));
  const drawer = await screen.findByTestId("sales-pipeline-detail");
  expect(await within(drawer).findByText("고객사 · 발굴 · 확률 10%")).toBeVisible();
  expect(drawer).toHaveTextContent("고객 포털");
  expect(within(drawer).getByTestId("sales-opportunity-edit")).toHaveTextContent("수정");
  expect(within(drawer).getByTestId("sales-opportunity-more")).toBeVisible();
  // 바깥 서랍을 겹쳐 그리지 않는다.
  expect(screen.getAllByTestId("sales-pipeline-detail")).toHaveLength(1);
  vi.unstubAllGlobals();
});
it("deleting from the drawer closes it, shows the mockup toast and reloads the board", async () => {
  const request = stubOpportunity();
  page();
  fireEvent.click(await screen.findByTestId("sales-card-opp"));
  fireEvent.click(await screen.findByTestId("sales-opportunity-more"));
  fireEvent.click(screen.getByTestId("sales-opportunity-stop"));
  const before = vi.mocked(api.getCommonSalesPipeline).mock.calls.length;
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(
    await screen.findByText(
      "삭제했습니다. 종료 탭의 [사용 중지 포함]에서 다시 쓸 수 있습니다.",
    ),
  ).toBeVisible();
  expect(screen.getByTestId("app-toast")).toHaveClass("app-toast-ok");
  await waitFor(() =>
    expect(screen.queryByTestId("sales-pipeline-detail")).toBeNull(),
  );
  expect(vi.mocked(api.getCommonSalesPipeline).mock.calls.length).toBeGreaterThan(before);
  expect(request).toHaveBeenCalledWith(
    "/api/common/sales-opportunities/opp/stop",
    expect.objectContaining({ method: "POST" }),
  );
  vi.unstubAllGlobals();
});
it("[수정] in the drawer opens the full-screen form without a second heading", async () => {
  stubOpportunity();
  page();
  fireEvent.click(await screen.findByTestId("sales-card-opp"));
  fireEvent.click(await screen.findByTestId("sales-opportunity-edit"));
  expect(await screen.findByTestId("sales-pipeline-form")).toBeVisible();
  expect(screen.queryByTestId("sales-pipeline-detail")).toBeNull();
  expect(screen.queryByTestId("sales-ongoing-panel")).toBeNull();
  // 제목(「영업 기회 수정」)은 공통 제목줄 하나만 쓴다(폼·화면 본문에는 두지 않는다).
  expect(screen.queryByRole("heading", { name: /영업 기회 (등록|수정)/ })).toBeNull();
  vi.unstubAllGlobals();
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
  // [다시 시도]는 고르는 값만 다시 읽는다.
  fireEvent.click(screen.getByTestId("sales-options-retry"));
  await waitFor(() =>
    expect(screen.queryByText("선택 자료 조회 실패")).not.toBeInTheDocument(),
  );
  expect(api.getSalesPipelineOptions).toHaveBeenCalledTimes(2);
  expect(api.getCommonSalesPipeline).toHaveBeenCalledTimes(1);
});
it("closed view applies the result filter on 조회 and the stopped flag at once", async () => {
  page();
  await screen.findByTestId("sales-card-opp");
  // 탭 건수는 파이프라인 요약(closedCount)에서 읽어 종료 목록을 미리 받지 않는다.
  expect(api.getCommonSalesPipelineClosed).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("sales-tab-closed"));
  await waitFor(() =>
    expect(api.getCommonSalesPipelineClosed).toHaveBeenCalledTimes(1),
  );
  expect(api.getCommonSalesPipelineClosed).toHaveBeenLastCalledWith("", false);
  fireEvent.change(screen.getByTestId("sales-result-filter"), {
    target: { value: "WIN" },
  });
  expect(api.getCommonSalesPipelineClosed).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() =>
    expect(api.getCommonSalesPipelineClosed).toHaveBeenLastCalledWith(
      "WIN",
      false,
    ),
  );
  fireEvent.click(screen.getByTestId("sales-include-stopped"));
  await waitFor(() =>
    expect(api.getCommonSalesPipelineClosed).toHaveBeenLastCalledWith(
      "WIN",
      true,
    ),
  );
  expect(screen.getByTestId("sales-include-stopped").closest("label")).toHaveTextContent(
    "사용 중지 포함",
  );
});
it("closed list uses the mockup columns, badges, project link and loss reason", async () => {
  page();
  await screen.findByTestId("sales-card-opp");
  fireEvent.click(screen.getByTestId("sales-tab-closed"));
  const list = await screen.findByTestId("sales-closed-list");
  for (const head of [
    "기회명",
    "고객사",
    "결과",
    "예상 금액",
    "종료일",
    "프로젝트·실주 사유",
  ])
    expect(
      within(list).getByRole("columnheader", { name: new RegExp(head) }),
    ).toBeInTheDocument();
  expect(await screen.findByTestId("sales-project-won")).toHaveTextContent(
    "수주 건",
  );
  expect(within(list).getByText("1,260,000,000")).toBeInTheDocument();
  expect(within(list).getByText("가격 · 경쟁사 단가")).toBeInTheDocument();
  expect(within(list).getByText("수주")).toHaveClass("ls-b-ok");
  expect(within(list).getByText("실주")).toHaveClass("ls-b-lock");
  expect(screen.queryByTestId("sales-project-lost")).not.toBeInTheDocument();
});
it("forecast follows the mockup cards, charts, monthly table with unit and hiring table", async () => {
  const original = structuredClone(forecast);
  page();
  fireEvent.click(screen.getByTestId("sales-tab-forecast"));
  const panel = await screen.findByTestId("sales-forecast-panel");
  const cards = within(panel).getByRole("group", {
    name: "매출·인력 예측 요약",
  }).children;
  expect(cards).toHaveLength(4);
  expect(cards[0]).toHaveTextContent("앞으로 6개월 예상 매출2.7억 원확률 반영");
  expect(cards[1]).toHaveTextContent("그중 영업 기회9,000만 원모두 따면 3.0억 원");
  expect(cards[2]).toHaveTextContent("확률 반영 인력 수요3.4명/월공급 1.7명/월");
  expect(cards[3]).toHaveTextContent("채용 검토 직무2개백엔드 개발, 프론트엔드 개발");
  expect(within(panel).getByText("앞으로 6개월 예상 매출", { selector: "h2" })).toBeInTheDocument();
  expect(within(panel).getByText("인력 수요와 공급")).toBeInTheDocument();
  expect(within(panel).getByTestId("sales-revenue-chart")).toBeInTheDocument();
  expect(within(panel).getByTestId("sales-manpower-chart")).toBeInTheDocument();
  const table = within(panel).getByTestId("sales-revenue-table");
  expect(table).toHaveTextContent("12월");
  expect(table).toHaveTextContent("6개월 합계");
  expect(table).toHaveTextContent("예상 매출 (확률 반영)");
  expect(table).toHaveTextContent("참고: 기회를 모두 따면");
  // 시안 pipeForecast: 기회마다 「└ 기회명 n%」 한 줄, 그 달 값이 없으면 「–」.
  expect(table).not.toHaveTextContent("영업 기회(확률 반영)");
  const rows = within(table).getAllByTestId(/^sales-revenue-row-/);
  expect(rows.map((r) => r.firstElementChild?.textContent)).toEqual([
    "└ 고객 포털 10%",
    "└ 물류 개선 30%",
    "└ 늦은 기회 60%",
  ]);
  expect(within(rows[0]).getByText("10%")).toHaveClass("ls-cap");
  expect(screen.getByTestId("sales-revenue-opp-2026-12")).toHaveTextContent(
    "10,000",
  );
  expect(screen.getByTestId("sales-revenue-late-2026-12")).toHaveTextContent("–");
  expect(rows[2].lastElementChild).toHaveTextContent("5,000");
  expect(rows[1].lastElementChild).toHaveTextContent("25,000");
  fireEvent.change(within(table).getByTestId("money-unit"), {
    target: { value: "백만 원" },
  });
  expect(screen.getByTestId("sales-revenue-opp-2026-12")).toHaveTextContent(/^10$/);
  expect(forecast).toEqual(original);
  const hiring = within(panel).getByTestId("sales-hiring");
  expect(hiring).toHaveTextContent("확률 반영 수요가 공급보다 0.4명 넘게 많은 직무");
  expect(within(hiring).getByText("1.7명")).toHaveClass("sales-bad");
  expect(within(hiring).getByText("지금")).toHaveClass("ls-b-bad");
  expect(within(screen.getByTestId("sales-hiring-fe")).getByText("없음")).toBeInTheDocument();
  expect(within(panel).getByTestId("sales-formula")).toHaveTextContent(
    "수주 확률은 영업 기회의 단계별 기본값이며 직접 고칠 수 있습니다.",
  );
  fireEvent.click(screen.getByTestId("sales-candidates-be"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    "/screens/UI-33?jobCode=BE",
  );
});
it("forecast without shortages shows the mockup empty text", async () => {
  vi.mocked(api.getCommonSalesPipelineForecast).mockResolvedValue({
    ...structuredClone(forecast),
    hiringReviews: [],
  });
  page();
  fireEvent.click(screen.getByTestId("sales-tab-forecast"));
  expect(
    await screen.findByText("앞으로 6개월 동안 부족한 직무가 없습니다."),
  ).toBeInTheDocument();
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
  // 견적 점검 칸의 모양·문구는 기회 폼(SalesOpportunityForm) 시험이 본다. 여기서는 자동 요청과 띠만 본다.
  await waitFor(() =>
    expect(quoteCommonSalesOpportunity).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ requiredCount: 2 })]),
    ),
  );
  expect((await screen.findAllByText(/목표 미달/)).length).toBeGreaterThan(0);
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

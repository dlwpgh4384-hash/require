import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter } from "react-router-dom";
import { SalesPipelinePage } from "./SalesPipelinePage";
import * as api from "./salesPipeline";
import type {
  SalesNotice,
  SalesOpportunity,
} from "./salesOpportunities";

vi.mock("./salesPipeline", async () => {
  const actual =
    await vi.importActual<typeof import("./salesPipeline")>("./salesPipeline");
  return {
    ...actual,
    getCommonSalesPipeline: vi.fn(),
    getCommonSalesPipelineClosed: vi.fn(),
    getSalesPipelineOptions: vi.fn(),
  };
});
// 기회 폼은 다른 시험이 본다. 여기서는 저장 뒤 화면(SFR-129-03 6초 강조·알림)만 본다.
let savedMilliseconds = 6000;
let savedNotices: SalesNotice[] | undefined;
vi.mock("./SalesOpportunityForm", () => ({
  SalesOpportunityForm: ({
    onSaved,
    opportunity,
  }: {
    onSaved: (
      row: SalesOpportunity,
      ms: number,
      notices?: SalesNotice[],
    ) => void;
    opportunity?: SalesOpportunity;
  }) => (
    <button
      type="button"
      data-testid="fake-save"
      onClick={() =>
        onSaved(
          {
            opportunityId: "opp",
            opportunityName: "고객 포털",
          } as SalesOpportunity,
          savedMilliseconds,
          savedNotices,
        )
      }
    >
      {opportunity ? "수정 저장" : "등록 저장"}
    </button>
  ),
}));
const card: api.PipelineCard = {
  opportunityId: "opp",
  opportunityName: "고객 포털",
  customerId: "c",
  customerName: "고객사",
  stageCode: "FIND",
  winningProbability: 10,
  expectedAmount: 100000000,
  startMonth: "2026-12",
  nextAction: null,
  nextActionDueDate: null,
  requiredJobs: ["BE"],
  stale: false,
  approvalStatusCode: "NONE",
  modifiedAt: "2026-11-01T00:00:00Z",
};
beforeEach(() => {
  vi.clearAllMocks();
  savedMilliseconds = 6000;
  savedNotices = undefined;
  vi.mocked(api.getSalesPipelineOptions).mockResolvedValue({
    codes: {},
    customers: [],
    parts: [],
    developers: [],
  });
  vi.mocked(api.getCommonSalesPipeline).mockResolvedValue({
    summary: {
      ongoingCount: 1,
      staleCount: 0,
      expectedAmount: 100000000,
      weightedAmount: 10000000,
      weightedManpower: 0.1,
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
});
afterEach(cleanup);

function page() {
  render(
    <MemoryRouter>
      <SalesPipelinePage />
    </MemoryRouter>,
  );
}

it("registering opens the full-screen form and after saving highlights the card, then clears it", async () => {
  savedMilliseconds = 300;
  page();
  const create = await screen.findByTestId("sales-pipeline-create");
  await waitFor(() => expect(create).toBeEnabled());
  fireEvent.click(create);
  expect(screen.getByTestId("sales-pipeline-form")).toBeInTheDocument();
  expect(screen.queryByTestId("sales-ongoing-panel")).toBeNull();
  fireEvent.click(screen.getByTestId("fake-save"));
  expect(
    await screen.findByText("「고객 포털」 기회를 등록했습니다."),
  ).toBeInTheDocument();
  const saved = await screen.findByTestId("sales-card-opp");
  expect(saved).toHaveClass("is-hl");
  expect(screen.queryByTestId("sales-pipeline-detail")).toBeNull();
  await waitFor(() =>
    expect(screen.getByTestId("sales-card-opp")).not.toHaveClass("is-hl"),
  );
});
it("keeps the highlight for the time the form gives (6 seconds)", async () => {
  const timeout = vi.spyOn(window, "setTimeout");
  page();
  const create = await screen.findByTestId("sales-pipeline-create");
  await waitFor(() => expect(create).toBeEnabled());
  fireEvent.click(create);
  fireEvent.click(screen.getByTestId("fake-save"));
  expect(await screen.findByTestId("sales-card-opp")).toHaveClass("is-hl");
  expect(timeout.mock.calls.some(([, ms]) => ms === 6000)).toBe(true);
  timeout.mockRestore();
});
it("shows the form notices with the page toast, the approval info stacked below", async () => {
  savedNotices = [
    { kind: "ok", message: "저장했습니다." },
    {
      kind: "info",
      message: "예상 이익률이 기준보다 낮아 견적 승인 대기로 올렸습니다.",
    },
  ];
  page();
  const create = await screen.findByTestId("sales-pipeline-create");
  await waitFor(() => expect(create).toBeEnabled());
  fireEvent.click(create);
  fireEvent.click(screen.getByTestId("fake-save"));
  expect(await screen.findByText("저장했습니다.")).toBeInTheDocument();
  expect(screen.queryByText("「고객 포털」 기회를 등록했습니다.")).toBeNull();
  expect(screen.getByTestId("app-toast")).toHaveClass("app-toast-ok");
  const info = screen.getByTestId("sales-pipeline-toast-next");
  expect(info).toHaveClass("app-toast-info");
  expect(info).toHaveTextContent(
    "예상 이익률이 기준보다 낮아 견적 승인 대기로 올렸습니다.",
  );
  expect(await screen.findByTestId("sales-card-opp")).toHaveClass("is-hl");
});

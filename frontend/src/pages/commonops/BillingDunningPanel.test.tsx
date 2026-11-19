import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter } from "react-router-dom";
import { BillingDunningPanel } from "./BillingDunningPanel";
import type { BillingDunningResult, BillingDunningRow } from "./billingLookups";

const viewport = vi.hoisted(() => ({ mobile: false, tablet: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const row = (over: Partial<BillingDunningRow> = {}): BillingDunningRow => ({
  billId: "prior-year",
  projectId: "selected",
  projectName: "지난해 미수 프로젝트",
  unpaidAmount: 15000,
  payDueDate: "2025-10-25",
  daysOverdue: 386,
  currentStep: 3,
  history: [
    {
      step: 1,
      sentDate: "2025-11-01",
      recipientDescription: "영업 담당자",
      sendMethodCode: "BOTH",
      resend: false,
    },
    {
      step: 2,
      sentDate: "2025-11-24",
      recipientDescription: "영업 파트장",
      sendMethodCode: "BOTH",
      resend: true,
    },
  ],
  nextNotification: null,
  canResend: true,
  resendMessage: null,
  ...over,
});
let data: BillingDunningResult;
let request: ReturnType<typeof vi.fn>;
let closed: boolean;
let readFailure: boolean;
let resendFailure: boolean;
let detailFailure: boolean;
beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  closed = false;
  readFailure = false;
  resendFailure = false;
  detailFailure = false;
  data = {
    cards: [
      { step: 1, recipients: ["DB PM"] },
      { step: 2, recipients: ["DB PM", "DB 파트장"] },
      { step: 3, recipients: ["DB PM", "DB 파트장", "DB 재무"] },
    ],
    items: [row()],
  };
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/bill-dunning") {
      if (readFailure) throw new Error("미수 조회 실패");
      return ok(data);
    }
    if (path.endsWith("/dunning-resend")) {
      if (resendFailure)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: "현재 단계가 변경되었습니다",
            errors: [],
          }),
        };
      data.items = data.items.map((item) => ({
        ...item,
        canResend: false,
        resendMessage: "이 단계 다시 알림 보냄",
        history: [
          ...item.history,
          {
            step: 3,
            sentDate: "2026-11-15",
            recipientDescription: "재무",
            sendMethodCode: "BOTH",
            resend: true,
          },
        ],
      }));
      return ok({
        billId: "prior-year",
        history: data.items[0].history.at(-1),
      });
    }
    if (path === "/api/common/receipts" && init?.method === "POST") {
      data.items = [];
      return ok({
        billIds: ["prior-year"],
        receipts: [
          {
            receiptId: "123456789012",
            receiptDate: "2026-11-15",
            amount: 15000,
            cancelled: false,
            cancelReason: null,
            cancelledBy: null,
            cancelledAt: null,
            canCancel: true,
          },
        ],
      });
    }
    if (path.startsWith("/api/common/bills/")) {
      if (detailFailure) throw new Error("입금 가능 여부 실패");
      return ok({
        bill: {
          billId: "prior-year",
          projectName: "지난해 미수 프로젝트",
          unpaidAmount: 15000,
          canReceive: !closed,
        },
      });
    }
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
function open() {
  const onOpenBill = vi.fn();
  const onChanged = vi.fn();
  render(
    <MemoryRouter>
      <BillingDunningPanel onOpenBill={onOpenBill} onChanged={onChanged} />
    </MemoryRouter>,
  );
  return { onOpenBill, onChanged };
}
it("shows database stage cards, old unpaid bills, dated history and resend markers; detail uses selected bill id", async () => {
  const { onOpenBill } = open();
  expect(
    await screen.findByTestId("billing-dunning-receive-prior-year"),
  ).toBeVisible();
  expect(screen.getByTestId("billing-dunning-card-1")).toHaveTextContent(
    "DB PM",
  );
  expect(screen.getByText("지난해 미수 프로젝트")).toBeVisible();
  expect(
    screen.getByText("2025-11-24 2단계(다시) → 영업 파트장 · BOTH"),
  ).toBeVisible();
  fireEvent.click(screen.getByTestId("billing-dunning-detail-prior-year"));
  expect(onOpenBill).toHaveBeenCalledWith("prior-year");
  expect(request.mock.calls[0][0]).toBe("/api/common/bill-dunning");
});
it("hides receipt actions when the detail reports current month closed without hiding old bills or resend", async () => {
  closed = true;
  open();
  await screen.findByTestId("billing-dunning-detail-prior-year");
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/bills/prior-year",
      expect.anything(),
    ),
  );
  expect(
    screen.queryByTestId("billing-dunning-receive-prior-year"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("billing-dunning-resend-prior-year")).toBeVisible();
});
it("hides resend for a current-stage successful resend and displays the exact server explanation", async () => {
  data.items = [
    row({ canResend: false, resendMessage: "이 단계 다시 알림 보냄" }),
  ];
  open();
  expect(await screen.findByText("이 단계 다시 알림 보냄")).toBeVisible();
  expect(
    screen.queryByTestId("billing-dunning-resend-prior-year"),
  ).not.toBeInTheDocument();
});
it("does not invent a resend action before the first threshold", async () => {
  data.items = [
    row({
      currentStep: null,
      canResend: false,
      daysOverdue: 1,
      history: [],
      nextNotification: "2026-11-21 1단계 예정",
    }),
  ];
  open();
  expect(await screen.findByText("2026-11-21 1단계 예정")).toBeVisible();
  expect(
    screen.queryByTestId("billing-dunning-resend-prior-year"),
  ).not.toBeInTheDocument();
});
it("successful resend re-reads the GET, shows the persisted history and removes the action", async () => {
  const { onChanged } = open();
  fireEvent.click(
    await screen.findByTestId("billing-dunning-resend-prior-year"),
  );
  expect(await screen.findByText("이 단계 다시 알림 보냄")).toBeVisible();
  expect(
    screen.getByText("2026-11-15 3단계(다시) → 재무 · BOTH"),
  ).toBeVisible();
  expect(request).toHaveBeenCalledWith(
    "/api/common/bills/prior-year/dunning-resend",
    { method: "POST" },
  );
  expect(onChanged).toHaveBeenCalledOnce();
});
it("failed resend retains rows without a success notice or fabricated history", async () => {
  resendFailure = true;
  const { onChanged } = open();
  fireEvent.click(
    await screen.findByTestId("billing-dunning-resend-prior-year"),
  );
  expect(await screen.findByText("현재 단계가 변경되었습니다")).toBeVisible();
  expect(screen.getByTestId("billing-dunning-detail-prior-year")).toBeVisible();
  expect(
    screen.queryByTestId("billing-dunning-success"),
  ).not.toBeInTheDocument();
  expect(onChanged).not.toHaveBeenCalled();
});
it("full receipt of a prior-year bill posts the current date and refreshes dunning and parent after commit", async () => {
  const { onChanged } = open();
  fireEvent.click(
    await screen.findByTestId("billing-dunning-receive-prior-year"),
  );
  expect(screen.getByLabelText("입금일")).toHaveValue("2026-11-15");
  fireEvent.click(screen.getByTestId("billing-receipt-save"));
  expect(await screen.findByText("조회 결과가 없습니다")).toBeVisible();
  expect(
    screen.queryByTestId("billing-dunning-detail-prior-year"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("billing-receipt-dialog"),
  ).not.toBeInTheDocument();
  expect(onChanged).toHaveBeenCalledOnce();
  const receipt = request.mock.calls.find(
    ([path]) => path === "/api/common/receipts",
  );
  expect(JSON.parse(receipt?.[1]?.body as string)).toEqual({
    receiptDate: "2026-11-15",
    items: [{ billId: "prior-year", amount: 15000 }],
  });
});
it("empty data keeps stage cards and table headers", async () => {
  data.items = [];
  open();
  expect(await screen.findByText("조회 결과가 없습니다")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: /청구번호/ })).toBeVisible();
  expect(screen.getByTestId("billing-dunning-card-3")).toBeVisible();
});
it("load failure offers GET retry without losing table headings", async () => {
  readFailure = true;
  open();
  expect(await screen.findByText("미수 조회 실패")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: /청구번호/ })).toBeVisible();
  readFailure = false;
  fireEvent.click(screen.getByRole("button", { name: /다시 시도|재시도/ }));
  expect(
    await screen.findByTestId("billing-dunning-detail-prior-year"),
  ).toBeVisible();
});
it("detail failure leaves dunning readable but does not expose an unverified receipt action", async () => {
  detailFailure = true;
  open();
  expect(await screen.findByText("입금 가능 여부 실패")).toBeVisible();
  expect(
    screen.queryByTestId("billing-dunning-receive-prior-year"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("billing-dunning-detail-prior-year")).toBeVisible();
});

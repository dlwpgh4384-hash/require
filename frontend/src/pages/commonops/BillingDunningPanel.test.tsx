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
import { BillingDunningPanel } from "./BillingDunningPanel";
import type { BillingDunningResult, BillingDunningRow } from "./billingLookups";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const row = (over: Partial<BillingDunningRow> = {}): BillingDunningRow => ({
  billId: "B-202609-001",
  projectId: "P-1",
  projectName: "다솜생명 보험금",
  unpaidAmount: 1100000,
  payDueDate: "2026-10-14",
  daysOverdue: 32,
  currentStep: 2,
  history: [
    { step: 1, sentDate: "2026-10-21", recipientDescription: "김명희 (pm01)", sendMethodCode: "BOTH", resend: false },
    { step: 2, sentDate: "2026-11-13", recipientDescription: "황민호 (sales1)", sendMethodCode: "BOTH", resend: false },
  ],
  nextNotification: "2026-12-13 3단계 예정",
  canResend: true,
  resendMessage: null,
  canReceive: true,
  ...over,
});
let result: BillingDunningResult;
let request: ReturnType<typeof vi.fn>;
let resendFailure: boolean;
const onNotice = vi.fn();
const onOpenBill = vi.fn();
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
  resendFailure = false;
  onNotice.mockReset();
  onOpenBill.mockReset();
  result = {
    cards: [
      { step: 1, recipients: ["PM"], offsetDays: 7 },
      { step: 2, recipients: ["PM", "영업 파트장"], offsetDays: 30 },
      { step: 3, recipients: ["PM", "영업 파트장", "경영진", "재무 담당"], offsetDays: 60 },
    ],
    items: [
      row({ billId: "B-202611-002", daysOverdue: 3, currentStep: null, history: [], nextNotification: "2026-11-19 1단계 예정", canResend: false, payDueDate: "2026-11-12" }),
      row(),
      row({ billId: "B-202608-001", daysOverdue: 40, canResend: false, resendMessage: "이 단계 다시 알림 보냄", canReceive: false }),
    ],
  };
  request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path.startsWith("/api/common/user-preferences/")) return ok({ found: false, prefVal: null });
    if (path.endsWith("/dunning-resend") && init?.method === "POST") {
      if (resendFailure)
        return { ok: false, status: 400, json: async () => ({ success: false, message: "현재 단계의 받는 사람이 없습니다.", errors: [] }) };
      return ok({ billId: "B-202609-001", history: { step: 2, sentDate: "2026-11-15", recipientDescription: "황민호 (sales1)", sendMethodCode: "BOTH", resend: true } });
    }
    if (path === "/api/common/receipts" && init?.method === "POST")
      return ok({ billIds: ["B-202609-001"], receipts: [] });
    if (path === "/api/common/bill-dunning") return ok(result);
    throw new Error(`예상하지 않은 요청: ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
const mount = () =>
  render(<BillingDunningPanel soonCount={2} onOpenBill={onOpenBill} onNotice={onNotice} />);

it("시안 단계 카드(예정일 + n일 · n건 · 앞 단계 사람도 함께)와 표를 요청 한 번으로 보여 준다", async () => {
  mount();
  await screen.findByText("B-202609-001");
  const one = screen.getByTestId("billing-dunning-card-1");
  expect(one).toHaveTextContent("1단계 예정일 + 7일");
  expect(one).toHaveTextContent("0건");
  expect(one).toHaveTextContent("받는 사람: 영업 담당자(PM)");
  expect(one).not.toHaveTextContent("앞 단계");
  const two = screen.getByTestId("billing-dunning-card-2");
  expect(two).toHaveTextContent("2단계 예정일 + 30일");
  expect(two).toHaveTextContent("2건");
  expect(two).toHaveTextContent("받는 사람: 영업 파트장 (앞 단계 사람도 함께)");
  expect(screen.getByTestId("billing-dunning-card-3")).toHaveTextContent("경영진·재무 (앞 단계 사람도 함께)");
  expect(screen.getByText("입금 예정일이 지난 청구 3건")).toBeVisible();
  expect(screen.getByText("곧 예정일(7일 안) 2건")).toBeVisible();
  // 상세를 줄마다 다시 읽지 않는다(N+1 없음)
  expect(request.mock.calls.filter(([u]) => String(u).startsWith("/api/common/bills/"))).toHaveLength(0);
  const rows = within(screen.getByTestId("billing-dunning-list")).getAllByRole("row").slice(1);
  expect(within(rows[0]).getByText("B-202608-001")).toBeVisible();
  expect(within(rows[2]).getByText("3일 지남")).toBeVisible();
  expect(within(rows[1]).getByText("2단계 · 32일")).toBeVisible();
  expect(within(rows[1]).getByText("10-21 1단계 → 김명희 (pm01)")).toBeVisible();
  expect(within(rows[0]).getByText("이 단계 다시 알림 보냄")).toBeVisible();
  expect(within(rows[0]).queryByTestId("billing-dunning-receive-B-202608-001")).not.toBeInTheDocument();
  expect(within(rows[2]).getByText("아직 없음")).toBeVisible();
  expect(screen.queryByText(/로컬 보관함/)).not.toBeInTheDocument();
  expect(screen.getByText("입금을 등록하면 알림이 멈춥니다. 알림은 메일과 시스템 알림으로 함께 보냅니다.")).toBeVisible();
  fireEvent.click(screen.getByTestId("billing-dunning-detail-B-202609-001"));
  expect(onOpenBill).toHaveBeenCalledWith("B-202609-001");
});

it("[다시 알림]은 보낸 사람을 알리고 다시 읽는다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("billing-dunning-resend-B-202609-001"));
  await waitFor(() =>
    expect(onNotice).toHaveBeenCalledWith(
      "황민호 (sales1)에게 미수 알림을 다시 보냈습니다. 보낸 알림에 「2단계(다시)」로 남았습니다.",
    ),
  );
  await waitFor(() =>
    expect(request.mock.calls.filter(([u]) => u === "/api/common/bill-dunning")).toHaveLength(2),
  );
});

it("[다시 알림] 실패는 경고 알림만 띄운다", async () => {
  resendFailure = true;
  mount();
  fireEvent.click(await screen.findByTestId("billing-dunning-resend-B-202609-001"));
  await waitFor(() => expect(onNotice).toHaveBeenCalledWith("현재 단계의 받는 사람이 없습니다.", "warn"));
});

it("입금 등록은 목록 값으로 창을 열어 저장 뒤 알린다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("billing-dunning-receive-B-202609-001"));
  expect(await screen.findByTestId("billing-receipt-dialog")).toBeVisible();
  fireEvent.click(screen.getByTestId("billing-receipt-save"));
  await waitFor(() => expect(onNotice).toHaveBeenCalledWith("입금을 등록했습니다."));
  const post = request.mock.calls.find(([u, i]) => u === "/api/common/receipts" && i?.method === "POST");
  expect(JSON.parse(String(post?.[1]?.body))).toEqual({ receiptDate: "2026-11-15", items: [{ billId: "B-202609-001", amount: 1100000 }] });
});

it("자료가 없어도 단계 카드와 표 머리는 보인다", async () => {
  result = { ...result, items: [] };
  mount();
  expect(await screen.findByText("입금 예정일이 지난 청구 0건")).toBeVisible();
  expect(screen.getByTestId("billing-dunning-card-1")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: /지난 일수/ })).toBeVisible();
});

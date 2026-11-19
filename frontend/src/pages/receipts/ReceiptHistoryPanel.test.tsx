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
import { ReceiptHistoryPanel } from "./ReceiptHistoryPanel";
import { ReceiptCancelDialog } from "./ReceiptCancelDialog";
import type { Receipt, ReceiptBillDetail } from "./receipts";

const viewport = vi.hoisted(() => ({ mobile: false, tablet: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const receipt = (over: Partial<Receipt> = {}): Receipt => ({
  receiptId: "123456789012",
  receiptDate: "2026-10-20",
  amount: 400,
  cancelled: false,
  cancelReason: null,
  cancelledBy: null,
  cancelledAt: null,
  canCancel: true,
  ...over,
});
let detail: ReceiptBillDetail;
let request: ReturnType<typeof vi.fn>;
let readFailure: boolean;
let cancelFailure: boolean;
let fieldFailure: boolean;
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
  readFailure = false;
  cancelFailure = false;
  fieldFailure = false;
  detail = {
    bill: {
      billId: "old-bill",
      projectName: "지난해 청구",
      unpaidAmount: 600,
      canReceive: true,
      receiptSumAmount: 400,
      status: "LATE",
    },
    receipts: [receipt()],
    missingTaxInvoiceNumber: true,
  };
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/bills/old-bill") {
      if (readFailure) throw new Error("입금 내역 조회 실패");
      return ok(detail);
    }
    if (path === "/api/common/receipts/123456789012/cancel") {
      if (cancelFailure || fieldFailure)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: cancelFailure ? "입금월이 마감되었습니다" : "입력값 오류",
            errors: fieldFailure
              ? [{ field: "reason", message: "사유를 확인해 주세요" }]
              : [],
          }),
        };
      detail.receipts = [
        receipt({
          cancelled: true,
          cancelReason: JSON.parse(String(init?.body)).reason,
          cancelledBy: "admin",
          cancelledAt: "2026-11-15T01:02:03Z",
          canCancel: false,
        }),
      ];
      detail.bill.receiptSumAmount = 0;
      detail.bill.unpaidAmount = 1000;
      return ok({ billIds: ["old-bill"], receipts: detail.receipts });
    }
    if (path === "/api/common/receipts") {
      const body = JSON.parse(String(init?.body));
      detail.receipts = [
        ...detail.receipts,
        receipt({
          receiptId: "321",
          receiptDate: body.receiptDate,
          amount: body.items[0].amount,
        }),
      ];
      detail.bill.receiptSumAmount += body.items[0].amount;
      detail.bill.unpaidAmount -= body.items[0].amount;
      detail.bill.status = detail.bill.unpaidAmount === 0 ? "PAID" : "LATE";
      detail.bill.canReceive = detail.bill.unpaidAmount > 0;
      return ok({ billIds: ["old-bill"], receipts: detail.receipts });
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
  const onChanged = vi.fn();
  render(
    <MemoryRouter>
      <ReceiptHistoryPanel billId="old-bill" onChanged={onChanged} />
    </MemoryRouter>,
  );
  return { onChanged };
}
async function cancelDialog() {
  fireEvent.click(await screen.findByTestId("receipt-cancel-123456789012"));
  return screen.getByTestId("receipt-cancel-dialog");
}
it("renders prior-year bill receipts with their own server cancellation permission", async () => {
  open();
  expect(await screen.findByText("정상")).toBeVisible();
  expect(screen.getByTestId("receipt-balance")).toHaveTextContent(
    "미수금 600원",
  );
  expect(screen.getByTestId("receipt-cancel-123456789012")).toBeEnabled();
  expect(screen.getByTestId("receipt-history-register")).toBeVisible();
  expect(request.mock.calls[0][0]).toBe("/api/common/bills/old-bill");
});
it("hides cancellation for closed receipt months regardless of bill year", async () => {
  detail.receipts = [receipt({ canCancel: false })];
  open();
  expect(await screen.findByText("정상")).toBeVisible();
  expect(
    screen.queryByTestId("receipt-cancel-123456789012"),
  ).not.toBeInTheDocument();
});
it("preserves cancelled receipt amount, reason, actor and time without another cancel control", async () => {
  detail.receipts = [
    receipt({
      cancelled: true,
      cancelReason: "보존 사유",
      cancelledBy: "admin",
      cancelledAt: "2026-11-15T01:02:03Z",
      canCancel: false,
    }),
  ];
  open();
  expect(await screen.findByText("취소됨")).toBeVisible();
  for (const value of ["400", "보존 사유", "admin", "2026-11-15T01:02:03Z"])
    expect(screen.getByText(value)).toBeVisible();
  expect(
    screen.queryByTestId("receipt-cancel-123456789012"),
  ).not.toBeInTheDocument();
});
it("empty receipts keep table headers and register action", async () => {
  detail.receipts = [];
  open();
  expect(await screen.findByText("조회 결과가 없습니다")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "입금일" })).toBeVisible();
  expect(screen.getByTestId("receipt-history-register")).toBeEnabled();
});
it("current closure hides receipt registration", async () => {
  detail.bill.canReceive = false;
  open();
  await screen.findByText("정상");
  expect(
    screen.queryByTestId("receipt-history-register"),
  ).not.toBeInTheDocument();
});
it("zero balance hides registration even if a stale permission flag remains true", async () => {
  detail.bill.unpaidAmount = 0;
  open();
  await screen.findByText("정상");
  expect(
    screen.queryByTestId("receipt-history-register"),
  ).not.toBeInTheDocument();
});
it("loads with headers but no write controls until detail resolves", async () => {
  request.mockImplementationOnce(() => new Promise(() => undefined));
  open();
  expect(screen.getByText("목록을 불러오는 중입니다.")).toBeVisible();
  expect(
    screen.queryByTestId("receipt-history-register"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("receipt-cancel-123456789012"),
  ).not.toBeInTheDocument();
});
it("read failure offers the same GET retry without exposing stale actions", async () => {
  readFailure = true;
  open();
  expect(await screen.findByText("입금 내역 조회 실패")).toBeVisible();
  expect(
    screen.queryByTestId("receipt-history-register"),
  ).not.toBeInTheDocument();
  readFailure = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  expect(await screen.findByText("정상")).toBeVisible();
  expect(request).toHaveBeenCalledTimes(2);
});
it("requires a nonblank reason without sending cancellation", async () => {
  open();
  await cancelDialog();
  fireEvent.change(screen.getByLabelText("취소 사유"), {
    target: { value: "  " },
  });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  expect(screen.getByText("취소 사유를 입력해 주세요.")).toBeVisible();
  expect(request).toHaveBeenCalledTimes(1);
});
it("rejects reasons longer than the original 500-character field", async () => {
  open();
  await cancelDialog();
  fireEvent.change(screen.getByLabelText("취소 사유"), {
    target: { value: "가".repeat(501) },
  });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  expect(
    screen.getByText("취소 사유는 500자 이내로 입력해 주세요."),
  ).toBeVisible();
  expect(request).toHaveBeenCalledTimes(1);
});
it("cancels only the selected receipt with trimmed reason then re-reads original row and restored balance", async () => {
  const { onChanged } = open();
  await cancelDialog();
  fireEvent.change(screen.getByLabelText("취소 사유"), {
    target: { value: "  금액 정정  " },
  });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  expect(await screen.findByText("취소됨")).toBeVisible();
  expect(screen.getByText("금액 정정")).toBeVisible();
  expect(screen.getByText("admin")).toBeVisible();
  expect(screen.getByTestId("receipt-balance")).toHaveTextContent(
    "미수금 1,000원",
  );
  expect(
    screen.queryByTestId("receipt-cancel-123456789012"),
  ).not.toBeInTheDocument();
  const [path, init] = request.mock.calls[1];
  expect(path).toBe("/api/common/receipts/123456789012/cancel");
  expect(init.method).toBe("POST");
  expect(JSON.parse(init.body)).toEqual({ reason: "금액 정정" });
  expect(request.mock.calls[2][0]).toBe("/api/common/bills/old-bill");
  expect(onChanged).toHaveBeenCalledOnce();
});
it("closed-month rejection keeps reason, balance and original receipt with no success", async () => {
  cancelFailure = true;
  const { onChanged } = open();
  await cancelDialog();
  fireEvent.change(screen.getByLabelText("취소 사유"), {
    target: { value: "정정" },
  });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  expect(await screen.findByText("입금월이 마감되었습니다")).toBeVisible();
  expect(screen.getByLabelText("취소 사유")).toHaveValue("정정");
  expect(screen.getByTestId("receipt-balance")).toHaveTextContent(
    "미수금 600원",
  );
  expect(onChanged).not.toHaveBeenCalled();
  expect(request).toHaveBeenCalledTimes(2);
});
it("renders server reason errors beside the input", async () => {
  fieldFailure = true;
  open();
  await cancelDialog();
  fireEvent.change(screen.getByLabelText("취소 사유"), {
    target: { value: "정정" },
  });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  expect(await screen.findByText("사유를 확인해 주세요")).toBeVisible();
  expect(screen.getByLabelText("취소 사유")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
});
it("closing reason confirmation calls no write", async () => {
  open();
  await cancelDialog();
  fireEvent.click(screen.getByTestId("receipt-cancel-close"));
  expect(screen.queryByTestId("receipt-cancel-dialog")).not.toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(1);
});
it("full receipt reuses the existing dialog and refreshes detail to PAID and zero balance", async () => {
  const { onChanged } = open();
  fireEvent.click(await screen.findByTestId("receipt-history-register"));
  fireEvent.click(screen.getByTestId("billing-receipt-save"));
  await waitFor(() => expect(onChanged).toHaveBeenCalledOnce());
  await waitFor(() =>
    expect(screen.getByTestId("receipt-balance")).toHaveTextContent(
      "미수금 0원 · PAID",
    ),
  );
  expect(
    screen.queryByTestId("receipt-history-register"),
  ).not.toBeInTheDocument();
  expect(JSON.parse(request.mock.calls[1][1].body)).toEqual({
    receiptDate: "2026-11-15",
    items: [{ billId: "old-bill", amount: 600 }],
  });
  expect(request.mock.calls[2][0]).toBe("/api/common/bills/old-bill");
});
it.each([
  receipt({ canCancel: false }),
  receipt({ cancelled: true, canCancel: true }),
])("a directly opened locked or cancelled receipt cannot submit", (row) => {
  render(
    <ReceiptCancelDialog receipt={row} onClose={vi.fn()} onSaved={vi.fn()} />,
  );
  expect(screen.getByTestId("receipt-cancel-confirm")).toBeDisabled();
  expect(screen.getByLabelText("취소 사유")).toBeDisabled();
  fireEvent.submit(screen.getByTestId("receipt-cancel-form"));
  expect(request).not.toHaveBeenCalled();
});
it("pending cancellation prevents double submit and Escape close", async () => {
  let resolve!: (response: ReturnType<typeof ok>) => void;
  request.mockImplementationOnce(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const onClose = vi.fn();
  const onSaved = vi.fn();
  render(
    <ReceiptCancelDialog
      receipt={receipt()}
      onClose={onClose}
      onSaved={onSaved}
    />,
  );
  fireEvent.change(screen.getByLabelText("취소 사유"), {
    target: { value: "정정" },
  });
  fireEvent.submit(screen.getByTestId("receipt-cancel-form"));
  fireEvent.submit(screen.getByTestId("receipt-cancel-form"));
  fireEvent.keyDown(window, { key: "Escape" });
  expect(request).toHaveBeenCalledOnce();
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.getByTestId("receipt-cancel-confirm")).toBeDisabled();
  resolve(ok({ billIds: ["old-bill"], receipts: [] }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
});

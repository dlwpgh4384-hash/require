import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { ReceiptHistoryPanel } from "./ReceiptHistoryPanel";
import { ReceiptCancelDialog } from "./ReceiptCancelDialog";
import type { Receipt } from "./receipts";

const receipt = (over: Partial<Receipt> = {}): Receipt => ({
  receiptId: "101",
  receiptDate: "2026-10-20",
  amount: 400000,
  cancelled: false,
  cancelReason: null,
  cancelledBy: null,
  cancelledAt: null,
  canCancel: true,
  modifiedAt: "2026-10-20T01:00:00.123456Z",
  ...over,
});
let request: ReturnType<typeof vi.fn>;
let status: number;
beforeEach(() => {
  status = 200;
  request = vi.fn(async () =>
    status === 200
      ? { ok: true, status, json: async () => ({ success: true, meta: {}, data: { billIds: ["B-1"], receipts: [] } }) }
      : status === 409
        ? {
            ok: false,
            status,
            json: async () => ({
              success: false,
              message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
              errors: [{ field: "modifiedAt", message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요." }],
            }),
          }
        : {
            ok: false,
            status,
            json: async () => ({ success: false, message: "2026-10는 마감한 달이라 이 입금은 취소할 수 없습니다.", errors: [] }),
          },
  );
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("입금 1번에 1줄, 취소된 줄은 사유와 함께 남고 마감 달 입금은 [취소]가 없다", () => {
  const onCancel = vi.fn();
  render(
    <ReceiptHistoryPanel
      receipts={[
        receipt(),
        receipt({ receiptId: "102", cancelled: true, cancelReason: "금액 잘못 입력", cancelledBy: "admin", cancelledAt: "2026-10-21T01:00:00Z", canCancel: false }),
        receipt({ receiptId: "103", canCancel: false }),
      ]}
      onCancel={onCancel}
    />,
  );
  expect(screen.getByTestId("receipt-row-102")).toHaveTextContent("취소됨 · 금액 잘못 입력");
  expect(screen.queryByTestId("receipt-cancel-102")).not.toBeInTheDocument();
  expect(screen.queryByTestId("receipt-cancel-103")).not.toBeInTheDocument();
  expect(screen.getByText(/입금일이 마감한 달에 속한 입금은 취소할 수 없습니다/)).toBeVisible();
  fireEvent.click(screen.getByTestId("receipt-cancel-101"));
  expect(onCancel).toHaveBeenCalledWith(expect.objectContaining({ receiptId: "101" }));
});

it("읽기 전용이면 [취소]가 없고, 입금이 없으면 안내만 보인다", () => {
  const { unmount } = render(<ReceiptHistoryPanel receipts={[receipt()]} readOnly onCancel={vi.fn()} />);
  expect(screen.queryByTestId("receipt-cancel-101")).not.toBeInTheDocument();
  unmount();
  render(<ReceiptHistoryPanel receipts={[]} onCancel={vi.fn()} />);
  expect(screen.getByText("아직 입금이 없습니다.")).toBeVisible();
});

it("사유 없이는 보내지 않고, 사유와 수정 시각을 보내 취소한다", async () => {
  const onSaved = vi.fn();
  render(<ReceiptCancelDialog receipt={receipt()} onClose={vi.fn()} onSaved={onSaved} />);
  expect(screen.getByText("2026-10-20 · 400,000원 입금을 취소합니다. 미수금이 그만큼 다시 늘어납니다.")).toBeVisible();
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  expect(screen.getByTestId("receipt-cancel-error")).toHaveTextContent("취소 사유를 입력해 주세요.");
  await waitFor(() => expect(screen.getByTestId("receipt-cancel-reason")).toHaveFocus());
  expect(request).not.toHaveBeenCalled();
  fireEvent.change(screen.getByTestId("receipt-cancel-reason"), { target: { value: "  금액 정정 " } });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledWith("입금을 취소했습니다. 기록은 남습니다."));
  const [url, init] = request.mock.calls[0];
  expect(url).toBe("/api/common/receipts/101/cancel");
  expect(JSON.parse(String(init.body))).toEqual({ reason: "금액 정정", modifiedAt: "2026-10-20T01:00:00.123456Z" });
});

it("다른 사람이 먼저 바꾸면(409) 화면에 알리고 다시 읽게 한다", async () => {
  status = 409;
  const onConflict = vi.fn();
  render(<ReceiptCancelDialog receipt={receipt()} onClose={vi.fn()} onSaved={vi.fn()} onConflict={onConflict} />);
  fireEvent.change(screen.getByTestId("receipt-cancel-reason"), { target: { value: "정정" } });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  await waitFor(() => expect(onConflict).toHaveBeenCalledWith("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
});

it("서버가 막으면 창에 이유를 보인다", async () => {
  status = 400;
  render(<ReceiptCancelDialog receipt={receipt()} onClose={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByTestId("receipt-cancel-reason"), { target: { value: "정정" } });
  fireEvent.click(screen.getByTestId("receipt-cancel-confirm"));
  await waitFor(() =>
    expect(screen.getByTestId("receipt-cancel-error")).toHaveTextContent("2026-10는 마감한 달이라 이 입금은 취소할 수 없습니다."),
  );
});

it("사유를 적은 채 닫으면 청구 취소와 같은 공통 문구로 묻고, 비었으면 바로 닫는다", () => {
  const onClose = vi.fn();
  const { unmount } = render(<ReceiptCancelDialog receipt={receipt()} onClose={onClose} onSaved={vi.fn()} />);
  fireEvent.click(screen.getByTestId("receipt-cancel-close"));
  expect(onClose).toHaveBeenCalledOnce();
  unmount();
  onClose.mockClear();
  render(<ReceiptCancelDialog receipt={receipt()} onClose={onClose} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByTestId("receipt-cancel-reason"), { target: { value: "정정" } });
  fireEvent.click(screen.getByTestId("receipt-cancel-close"));
  expect(onClose).not.toHaveBeenCalled();
  const confirm = screen.getByTestId("receipt-cancel-dirty");
  expect(confirm).toHaveTextContent("저장하지 않은 내용이 있습니다");
  expect(confirm).toHaveTextContent("나가면 입력한 내용이 사라집니다.");
  fireEvent.click(within(confirm).getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledOnce();
  expect(request).not.toHaveBeenCalled();
});

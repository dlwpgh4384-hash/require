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
import { MemoryRouter } from "react-router-dom";
import { BillingReceiptDialog } from "./BillingReceiptDialog";
import type { BillingReceivable } from "./billingLookups";

let request: ReturnType<typeof vi.fn>;
const bills = (): BillingReceivable[] => [
  {
    billId: "prior-year",
    projectName: "지난해 미수",
    unpaidAmount: 15000,
    canReceive: true,
  },
  {
    billId: "current-year",
    projectName: "올해 미수",
    unpaidAmount: 30000,
    canReceive: true,
  },
  {
    billId: "paid",
    projectName: "잔액 없는 청구",
    unpaidAmount: 0,
    canReceive: false,
  },
];
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data }),
});
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-14T16:00:00Z"));
  request = vi
    .fn()
    .mockResolvedValue(
      ok({ billIds: ["prior-year", "current-year"], receipts: [] }),
    );
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
function open(rows = bills()) {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  render(
    <MemoryRouter>
      <BillingReceiptDialog bills={rows} onClose={onClose} onSaved={onSaved} />
    </MemoryRouter>,
  );
  return { onClose, onSaved };
}
const amount = (id = "prior-year") =>
  screen.getByRole("textbox", { name: `${id} 입금액` });

it("uses Korean today for old bills, defaults each balance, excludes zero balance and sends one atomic batch", async () => {
  const { onSaved } = open();
  expect(screen.getByTestId("billing-receipt-date")).toHaveValue("2026-11-15");
  // 여러 청구를 골랐을 때만 표, 입금액은 천 단위 쉼표
  expect(screen.getByRole("table", { name: "청구별 입금액" })).toBeVisible();
  expect(amount()).toHaveValue("15,000");
  expect(amount("current-year")).toHaveValue("30,000");
  expect(screen.queryByText("잔액 없는 청구")).not.toBeInTheDocument();
  fireEvent.change(amount(), { target: { value: "5000" } });
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(request).toHaveBeenCalledOnce();
  const [path, init] = request.mock.calls[0];
  expect(path).toBe("/api/common/receipts");
  expect(init.method).toBe("POST");
  expect(JSON.parse(init.body)).toEqual({
    receiptDate: "2026-11-15",
    items: [
      { billId: "prior-year", amount: 5000 },
      { billId: "current-year", amount: 30000 },
    ],
  });
});
it("one bill uses the mockup receipt window: subtitle, fields, won suffix, comma default and help", () => {
  open([{ ...bills()[0], unpaidAmount: 36410000 }]);
  const dialog = screen.getByRole("dialog", { name: "입금 등록" });
  expect(within(dialog).queryByRole("table")).not.toBeInTheDocument();
  expect(within(dialog).getByTestId("billing-receipt-subtitle")).toHaveTextContent("지난해 미수 · prior-year");
  expect(within(dialog).getByLabelText(/입금일/)).toHaveValue("2026-11-15");
  const field = within(dialog).getByLabelText(/입금액/);
  expect(field).toHaveValue("36,410,000");
  expect(field.parentElement).toHaveTextContent("원");
  expect(within(dialog).getByTestId("billing-receipt-help")).toHaveTextContent(
    "미수금 36,410,000원. 나눠 받으면 받은 금액만 적으세요. 더 많이 들어온 돈은 다른 청구를 골라 따로 등록합니다.",
  );
  expect(within(dialog).getByRole("button", { name: "취소" })).toBeVisible();
  expect(within(dialog).getByRole("button", { name: "저장" })).toBeVisible();
  fireEvent.change(field, { target: { value: "36410001" } });
  expect(field).toHaveValue("36,410,001");
  fireEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  expect(within(dialog).getByText("입금액이 미수금보다 큽니다.")).toBeVisible();
  expect(request).not.toHaveBeenCalled();
});
it("submits full payment directly without a generic confirmation", async () => {
  const { onSaved } = open([bills()[0]]);
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(JSON.parse(request.mock.calls[0][1].body).items).toEqual([
    { billId: "prior-year", amount: 15000 },
  ]);
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
});
it("rejects zero with the exact message and writes nothing", () => {
  open();
  fireEvent.change(amount(), { target: { value: "0" } });
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  expect(screen.getByText("입금액을 입력해 주세요.")).toBeVisible();
  expect(request).not.toHaveBeenCalled();
});
it("rejects overpayment without automatically allocating it to another bill", () => {
  open();
  fireEvent.change(amount(), { target: { value: "15001" } });
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  expect(screen.getByText("입금액이 미수금보다 큽니다.")).toBeVisible();
  expect(amount("current-year")).toHaveValue("30,000");
  expect(request).not.toHaveBeenCalled();
});
it.each([
  ["-1", "1"],
  ["0.5", "5"],
  ["1e3", "13"],
  ["12,3a4", "1,234"],
])("number field drops non-digits at once (%s -> %s)", (value, shown) => {
  open();
  fireEvent.change(amount(), { target: { value } });
  expect(amount()).toHaveValue(shown);
});
it("rejects an out-of-range currency", () => {
  open();
  fireEvent.change(amount(), { target: { value: "1000000000000000" } });
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  expect(
    screen.getByText("입금액은 15자리 이내의 원 단위 양수로 입력해 주세요."),
  ).toBeVisible();
  expect(request).not.toHaveBeenCalled();
});
it("requires the common receipt date", () => {
  open();
  fireEvent.change(screen.getByTestId("billing-receipt-date"), { target: { value: "" } });
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  expect(screen.getByText("입금일을 입력해 주세요.")).toBeVisible();
  expect(request).not.toHaveBeenCalled();
});
it("cannot save rows locked by current-month closure even when invoked directly", () => {
  open(bills().map((bill) => ({ ...bill, canReceive: false })));
  expect(screen.getByRole("button", { name: /^저장$/ })).toBeDisabled();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(request).not.toHaveBeenCalled();
});
it("rejects duplicate bill ids before sending a batch", () => {
  open([bills()[0], bills()[0]]);
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  expect(screen.getByText("같은 청구는 한 번만 선택해 주세요.")).toBeVisible();
  expect(request).not.toHaveBeenCalled();
});
it("keeps every input on atomic server rejection, shows row errors, and never signals success", async () => {
  request.mockResolvedValue({
    ok: false,
    status: 400,
    json: async () => ({
      success: false,
      message: "입금 처리 실패",
      errors: [{ field: "items[1].amount", message: "잔액이 변경되었습니다" }],
    }),
  });
  const { onSaved } = open();
  fireEvent.change(amount(), { target: { value: "5000" } });
  fireEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  expect(await screen.findByText("잔액이 변경되었습니다")).toBeVisible();
  // 서버가 준 줄 번호(items[1])의 줄에만 보인다
  expect(screen.getByTestId("billing-receipt-error-1")).toHaveTextContent("잔액이 변경되었습니다");
  expect(screen.queryByTestId("billing-receipt-error-0")).not.toBeInTheDocument();
  expect(amount()).toHaveValue("5,000");
  expect(amount("current-year")).toHaveValue("30,000");
  expect(onSaved).not.toHaveBeenCalled();
});
it("dirty close asks before discarding and cancellation writes nothing", () => {
  const { onClose } = open();
  fireEvent.change(amount(), { target: { value: "9000" } });
  fireEvent.click(screen.getByTestId("billing-receipt-cancel"));
  expect(onClose).not.toHaveBeenCalled();
  const confirmation = screen.getByTestId("billing-receipt-dirty");
  fireEvent.click(within(confirmation).getByRole("button", { name: "나가기" }));
  expect(onClose).toHaveBeenCalledOnce();
  expect(request).not.toHaveBeenCalled();
});
it("clean close returns without calling the API", () => {
  const { onClose } = open();
  fireEvent.click(screen.getByTestId("billing-receipt-cancel"));
  expect(onClose).toHaveBeenCalledOnce();
  expect(request).not.toHaveBeenCalled();
});
it("prevents duplicate clicks while awaiting the server", async () => {
  let resolve!: (response: ReturnType<typeof ok>) => void;
  request.mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  const { onSaved } = open();
  const save = screen.getByTestId("billing-receipt-save");
  fireEvent.click(save);
  fireEvent.click(save);
  expect(save).toBeDisabled();
  expect(request).toHaveBeenCalledOnce();
  resolve(ok({ billIds: ["prior-year"], receipts: [] }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
});

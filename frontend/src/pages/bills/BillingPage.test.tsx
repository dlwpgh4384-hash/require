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
import { BillingPage } from "./BillingPage";
import { BillForm } from "./BillForm";
import { BillDetailDialog } from "./BillDetailDialog";
import type { Bill, BillList } from "./bills";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, width: 1280 }),
}));
let bill: Bill,
  result: BillList,
  request: ReturnType<typeof vi.fn>,
  fail: boolean,
  duplicate: boolean;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
  fail = false;
  duplicate = false;
  bill = {
    billId: "B-202611-001",
    projectId: "P-0001",
    projectName: "시험 프로젝트",
    customerName: "시험 거래처",
    billOrder: 1,
    billMonth: "202611",
    billDate: "2026-11-15",
    supplyAmount: 1000,
    vatRate: 10,
    vatAmount: 100,
    totalAmount: 1100,
    receiptSumAmount: 0,
    unpaidAmount: 1100,
    payDueDate: "2026-12-25",
    taxInvoiceNo: null,
    status: "UNPAID",
    cancelled: false,
    cancelReason: null,
    cancelledBy: null,
    cancelledAt: null,
    dunningStep: null,
    daysOverdue: 0,
    canEdit: true,
    canCancel: true,
    canReceive: true,
    canCopy: true,
    canNextMonth: true,
  };
  result = {
    items: [bill],
    readOnly: false,
    summary: {
      yearBillCount: 1,
      yearBillTotal: 1100,
      currentMonthBillTotal: 1100,
      unpaidCount: 1,
      unpaidTotal: 1100,
      overdueCount: 0,
    },
    totals: {
      supplyAmount: 1000,
      vatAmount: 100,
      totalAmount: 1100,
      receiptSumAmount: 0,
      unpaidAmount: 1100,
    },
  };
  request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url.startsWith("/api/common/user-preferences/"))
      return ok({ found: false, prefVal: null });
    if (url.startsWith("/api/common/bill-options"))
      return ok({
        projects: [
          {
            projectId: "P-0001",
            projectName: "시험 프로젝트",
            customerName: "시험 거래처",
            projectType: "MO",
          },
        ],
        statusOptions: [{ code: "CNCL", name: "취소됨" }],
        defaultBillMonth: "202611",
        defaultBillDate: "2026-11-15",
        defaultPayDueDate: "2026-12-25",
        estimatedSupplyAmount: 1000,
        vatRate: 10,
      });
    if (fail)
      return {
        ok: false,
        status: 400,
        json: async () => ({
          success: false,
          message: "해당월 마감",
          errors: [{ field: "billMonth", message: "해당월 마감" }],
        }),
      };
    if (url.startsWith("/api/common/bills?") && !init?.method)
      return ok(result);
    if (
      url === "/api/common/bills" &&
      init?.method === "POST" &&
      duplicate &&
      !JSON.parse(String(init.body)).duplicateConfirmed
    )
      return {
        ok: true,
        status: 200,
        json: async () => ({
          success: true,
          meta: {},
          data: null,
          warning: "동월 청구가 있습니다. 다시 등록해 주세요.",
        }),
      };
    return ok({ bill, receipts: [], missingTaxInvoiceNumber: true });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

it("renders real list with ordered copy and next actions", async () => {
  render(<BillingPage />);
  await screen.findByTestId(`bill-copy-${bill.billId}`);
  const row = screen.getByTestId(`bill-copy-${bill.billId}`).closest("tr")!;
  expect(
    within(row)
      .getAllByRole("button")
      .map((button) => button.textContent),
  ).toEqual(expect.arrayContaining(["고쳐서 청구", "다음 달 청구"]));
  expect(screen.getByTestId("bill-totals")).toHaveTextContent("1,100");
});
it("turnkey only offers copy", async () => {
  bill.canNextMonth = false;
  render(<BillingPage />);
  expect(await screen.findByTestId(`bill-copy-${bill.billId}`)).toBeVisible();
  expect(
    screen.queryByTestId(`bill-next-${bill.billId}`),
  ).not.toBeInTheDocument();
});
it("nonlatest or ineligible rows have no executable quick actions", async () => {
  bill.canCopy = false;
  bill.canNextMonth = false;
  render(<BillingPage />);
  await screen.findByTestId(`bill-open-${bill.billId}`);
  expect(
    screen.queryByTestId(`bill-copy-${bill.billId}`),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId(`bill-next-${bill.billId}`),
  ).not.toBeInTheDocument();
});
it("prior-year hides register and workflow tabs", async () => {
  render(<BillingPage />);
  await screen.findByTestId(`bill-open-${bill.billId}`);
  fireEvent.click(screen.getByTestId("bill-year-prev"));
  expect(screen.queryByTestId("bill-register")).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-tab-dunning")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId(`bill-next-${bill.billId}`),
  ).not.toBeInTheDocument();
});
it("empty list preserves headings and summary", async () => {
  result.items = [];
  render(<BillingPage />);
  await waitFor(() =>
    expect(screen.queryByTestId("list-loading")).not.toBeInTheDocument(),
  );
  expect(screen.getByRole("columnheader", { name: /청구번호/ })).toBeVisible();
  expect(screen.getByTestId("bill-register")).toBeVisible();
  expect(await screen.findByTestId("bill-totals")).toBeVisible();
});
it("failed list offers retry", async () => {
  fail = true;
  render(<BillingPage />);
  expect(await screen.findByText("해당월 마감")).toBeVisible();
  fail = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  expect(await screen.findByTestId(`bill-open-${bill.billId}`)).toBeVisible();
});
it("next-month submits without opening a form and refreshes", async () => {
  render(<BillingPage />);
  fireEvent.click(await screen.findByTestId(`bill-next-${bill.billId}`));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      `/api/common/bills/${bill.billId}/next-month`,
      expect.objectContaining({ method: "POST" }),
    ),
  );
  expect(screen.queryByTestId("bill-form")).not.toBeInTheDocument();
  expect(await screen.findByTestId("bill-notice")).toHaveTextContent(
    "다음 달 청구를 등록",
  );
});
it("server rejection of next-month is displayed", async () => {
  render(<BillingPage />);
  const button = await screen.findByTestId(`bill-next-${bill.billId}`);
  fail = true;
  fireEvent.click(button);
  expect(await screen.findByTestId("bill-notice")).toHaveTextContent(
    "해당월 마감",
  );
  expect(screen.queryByTestId("bill-form")).not.toBeInTheDocument();
});
it("edit locks identity and sends only mutable values directly", async () => {
  const saved = vi.fn();
  render(<BillForm bill={bill} onClose={vi.fn()} onSaved={saved} />);
  expect(screen.getByLabelText("프로젝트")).toBeDisabled();
  expect(screen.getByLabelText("청구월")).toBeDisabled();
  fireEvent.change(screen.getByLabelText("공급가"), {
    target: { value: "2000" },
  });
  fireEvent.click(screen.getByTestId("bill-form-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  const call = request.mock.calls.find(([, init]) => init?.method === "PUT")!;
  expect(JSON.parse(String(call[1]?.body))).toEqual({
    supplyAmount: 2000,
    vatRate: 10,
    billDate: "2026-11-15",
    payDueDate: "2026-12-25",
    taxInvoiceNo: null,
  });
});
it("copy creates preserved identity source rather than using PUT", async () => {
  render(<BillForm bill={bill} copy onClose={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.click(screen.getByTestId("bill-form-save"));
  await waitFor(() =>
    expect(request.mock.calls.some(([, init]) => init?.method === "POST")).toBe(
      true,
    ),
  );
  const call = request.mock.calls.find(([url]) => url === "/api/common/bills")!;
  expect(JSON.parse(String(call[1]?.body))).toMatchObject({
    sourceBillId: bill.billId,
    projectId: bill.projectId,
    billMonth: bill.billMonth,
  });
});
it("duplicate warning does not close form and second click confirms", async () => {
  duplicate = true;
  const saved = vi.fn();
  render(<BillForm bill={bill} copy onClose={vi.fn()} onSaved={saved} />);
  fireEvent.click(screen.getByTestId("bill-form-save"));
  expect(await screen.findByTestId("bill-duplicate-warning")).toBeVisible();
  expect(saved).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("bill-form-save"));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  const calls = request.mock.calls.filter(
    ([url]) => url === "/api/common/bills",
  );
  expect(JSON.parse(String(calls[1][1]?.body)).duplicateConfirmed).toBe(true);
});
it("invalid due date prevents write", async () => {
  render(<BillForm bill={bill} onClose={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("입금 예정일"), {
    target: { value: "2026-11-14" },
  });
  fireEvent.click(screen.getByTestId("bill-form-save"));
  expect(
    await screen.findByText(
      "입금 예정일은 청구일 이후 또는 같은 날이어야 합니다.",
    ),
  ).toBeVisible();
  expect(request.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(
    false,
  );
});
it("receipt floor prevents update", async () => {
  bill.receiptSumAmount = 1100;
  render(<BillForm bill={bill} onClose={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("공급가"), {
    target: { value: "500" },
  });
  fireEvent.click(screen.getByTestId("bill-form-save"));
  expect(
    await screen.findByText("합계는 정상 입금 합계보다 작을 수 없습니다."),
  ).toBeVisible();
});
it("dirty close requires discard decision", async () => {
  const close = vi.fn();
  render(<BillForm bill={bill} onClose={close} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("공급가"), {
    target: { value: "3000" },
  });
  fireEvent.click(screen.getByTestId("bill-form-cancel"));
  expect(screen.getByText("저장하지 않고 닫을까요?")).toBeVisible();
  expect(close).not.toHaveBeenCalled();
});
it("closed or prior-year detail hides mutations", async () => {
  bill.canEdit = false;
  bill.canCancel = false;
  render(
    <BillDetailDialog
      billId={bill.billId}
      onClose={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  expect(
    await screen.findByText(
      "지난해·마감·취소 청구의 내용은 변경할 수 없습니다.",
    ),
  ).toBeVisible();
  expect(screen.queryByTestId("bill-detail-edit")).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-detail-cancel")).not.toBeInTheDocument();
});
it("cancellation asks for reason before request", async () => {
  render(
    <BillDetailDialog
      billId={bill.billId}
      onClose={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  fireEvent.click(await screen.findByTestId("bill-detail-cancel"));
  fireEvent.click(screen.getByTestId("bill-cancel-confirm"));
  expect(await screen.findByText("취소 사유를 입력해 주세요.")).toBeVisible();
  expect(request.mock.calls.some(([url]) => url.endsWith("/cancel"))).toBe(
    false,
  );
});
it("prior-year detail never offers receipt mutations", async () => {
  render(
    <BillDetailDialog
      billId={bill.billId}
      readOnly
      onClose={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  expect(await screen.findByTestId("bill-readonly-receipts")).toBeVisible();
  expect(
    screen.queryByTestId("receipt-history-register"),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-detail-edit")).not.toBeInTheDocument();
});
it("cancel success sends the reason and refreshes parent", async () => {
  const changed = vi.fn();
  render(
    <BillDetailDialog
      billId={bill.billId}
      onClose={vi.fn()}
      onChanged={changed}
    />,
  );
  fireEvent.click(await screen.findByTestId("bill-detail-cancel"));
  fireEvent.change(screen.getByLabelText("취소 사유"), {
    target: { value: "등록 정정" },
  });
  fireEvent.click(screen.getByTestId("bill-cancel-confirm"));
  await waitFor(() => expect(changed).toHaveBeenCalled());
  const call = request.mock.calls.find(([url]) => url.endsWith("/cancel"))!;
  expect(JSON.parse(String(call[1]?.body))).toEqual({ reason: "등록 정정" });
});
it("failed save keeps form open and does not signal success", async () => {
  const saved = vi.fn();
  render(<BillForm bill={bill} onClose={vi.fn()} onSaved={saved} />);
  fail = true;
  fireEvent.click(screen.getByTestId("bill-form-save"));
  expect((await screen.findAllByText("해당월 마감"))[0]).toBeVisible();
  expect(saved).not.toHaveBeenCalled();
  expect(screen.getByTestId("bill-form")).toBeVisible();
});
it("tax rate beyond range prevents write", async () => {
  render(<BillForm bill={bill} onClose={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("부가세율"), {
    target: { value: "101" },
  });
  fireEvent.click(screen.getByTestId("bill-form-save"));
  expect(
    await screen.findByText("세율은 0~100, 소수 둘째 자리 이내입니다."),
  ).toBeVisible();
  expect(request.mock.calls.some(([, init]) => init?.method === "PUT")).toBe(
    false,
  );
});

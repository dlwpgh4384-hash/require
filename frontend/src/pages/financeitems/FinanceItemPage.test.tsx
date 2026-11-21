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
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import { FinanceItemPage } from "./FinanceItemPage";
import type { FinanceMonth, FinancePayment } from "./financeItems";
import * as api from "./financeItems";
import { ProjectRequestError } from "../projects/projects";

const viewport = vi.hoisted(() => ({ mobile: false, tablet: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
vi.mock("../userpreferences/useScreenPeriod", async () => {
  const { useState } = await import("react");
  return {
    useScreenPeriod: () => {
      const [value, setValue] = useState({ year: 2026, month: 2 });
      return {
        state: {
          status: "ready",
          basis: {
            latestYm: "202602",
            cashBaseYm: "202601",
            closedMonths: ["202601"],
          },
          value,
        },
        setValue,
      };
    },
  };
});
vi.mock("../codegroups/codeGroups", () => ({
  getCommonCodes: vi.fn(async () => ({ items: null })),
}));
vi.mock("./financeItems", () => ({
  getCommonFinanceItems: vi.fn(),
  postCommonFinanceTaxYear: vi.fn(),
  putCommonFinanceItems: vi.fn(),
  postCommonFinanceTaxPaymentConfirm: vi.fn(),
  getCommonFinancePreviousItems: vi.fn(),
  postCommonFinanceItemsUpload: vi.fn(),
  checkCommonFinanceItemsUpload: vi.fn(),
  getCommonFinanceItemsExcel: vi.fn(),
  getCommonFinanceItemsTemplate: vi.fn(),
}));
let data: FinanceMonth;
const dirty = vi.fn();
const payment = (): FinancePayment => ({
  paymentId: "2026:MID",
  taxYear: 2026,
  paymentType: "MID",
  scheduledDate: "2026-08-31",
  paymentAmount: null,
  displayAmount: 0,
  paidYn: "N",
  actualPaymentDate: null,
  status: "예정",
  modifiedAt: null,
});
beforeEach(() => {
  vi.clearAllMocks();
  viewport.mobile = false;
  data = {
    yearMonth: "202602",
    closed: false,
    firstOpenYearMonth: "202603",
    modifiedAt: null,
    months: [
      { yearMonth: "202601", closed: true, itemCount: 0, future: false },
      { yearMonth: "202602", closed: false, itemCount: 0, future: false },
      { yearMonth: "202603", closed: false, itemCount: 0, future: true },
    ],
    items: [],
    loans: [],
    transfers: [],
    adjustments: [],
    cards: [],
    taxPayments: [payment()],
    taxEstimate: {
      taxYear: 2026,
      estimatedTaxAmount: 120000000,
      monthlyTaxAmount: 10000000,
      negativeTaxWarning: false,
      modifiedAt: null,
    },
    cardPayment: { paymentAmount: 0, manualOverride: false, modifiedAt: null },
    summary: {
      sgaAmount: 0,
      nonOperatingIncomeAmount: 0,
      nonOperatingExpenseAmount: 0,
      monthlyTaxAmount: 10000000,
      adjustmentAmount: 0,
      cardTotalAmount: 0,
      cardPaymentDifference: 0,
    },
    activeCardCount: 0,
    autoFillCount: 0,
  };
  vi.mocked(api.postCommonFinanceTaxYear).mockResolvedValue({});
  vi.mocked(api.getCommonFinanceItemsExcel).mockResolvedValue("재무 항목.xlsx");
  vi.mocked(api.getCommonFinanceItemsTemplate).mockResolvedValue(
    "finance-items-template.xlsx",
  );
  vi.mocked(api.getCommonFinanceItems).mockImplementation(async () =>
    structuredClone(data),
  );
  vi.mocked(api.putCommonFinanceItems).mockImplementation(
    async (_month, value) => structuredClone(value),
  );
});
afterEach(cleanup);
async function open() {
  render(
    <MemoryRouter>
      <ScreenDirtyContext.Provider value={dirty}>
        <FinanceItemPage />
      </ScreenDirtyContext.Provider>
    </MemoryRouter>,
  );
  await waitFor(() => expect(api.getCommonFinanceItems).toHaveBeenCalled());
  await screen.findByText("입력한 항목이 없습니다.").catch(() => undefined);
}
function add() {
  fireEvent.click(screen.getByTestId("finance-item-add"));
}
const change = (label: string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
it("prepares year before read and keeps empty table, both tabs, and common actions for null options", async () => {
  await open();
  expect(api.postCommonFinanceTaxYear).toHaveBeenCalledWith(
    2026,
    expect.anything(),
  );
  expect(
    vi.mocked(api.postCommonFinanceTaxYear).mock.invocationCallOrder[0],
  ).toBeLessThan(
    vi.mocked(api.getCommonFinanceItems).mock.invocationCallOrder[0],
  );
  expect(screen.getAllByRole("tab")).toHaveLength(2);
  expect(
    screen.getByRole("columnheader", { name: "세부 항목*" }),
  ).toBeVisible();
  expect(screen.getByTestId("finance-save")).toBeVisible();
  expect(screen.getByTestId("finance-cancel")).toBeVisible();
});
it("mobile shows only PC notice and never reads finance", async () => {
  viewport.mobile = true;
  render(<FinanceItemPage />);
  expect(screen.getByText(/PC 전용 화면/)).toBeVisible();
  expect(screen.queryByRole("tab")).toBeNull();
  expect(api.getCommonFinanceItems).not.toHaveBeenCalled();
});
it("does not send a request while editing numbers and normalizes VAT only on blur", async () => {
  await open();
  add();
  const vat = screen.getByLabelText("부가세 1");
  fireEvent.change(vat, { target: { value: "" } });
  expect(vat).toHaveValue(null);
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  fireEvent.blur(vat);
  expect(vat).toHaveValue(0);
});
it("blank required amount focuses without a write while explicit zero is saved", async () => {
  await open();
  add();
  change("세부 항목 1", "직접 항목");
  fireEvent.click(screen.getByTestId("finance-save"));
  expect(await screen.findByText("금액을 입력해 주세요")).toBeVisible();
  await waitFor(() => expect(screen.getByLabelText("금액* 1")).toHaveFocus());
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  change("금액* 1", "0");
  fireEvent.click(screen.getByTestId("finance-save"));
  await waitFor(() => expect(api.putCommonFinanceItems).toHaveBeenCalled());
  expect(
    vi.mocked(api.putCommonFinanceItems).mock.calls[0][1].items[0],
  ).toMatchObject({ itemName: "직접 항목", itemCode: null, amount: "0" });
});
it("saves both tabs together from first tab and cancel restores both", async () => {
  await open();
  add();
  change("세부 항목 1", "월 항목");
  change("금액* 1", "2");
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  fireEvent.click(screen.getByTestId("finance-card-add"));
  change("카드 이름 1", "회사 카드");
  change("끝 4자리 1", "1234");
  fireEvent.click(screen.getByTestId("finance-tab-0"));
  fireEvent.click(screen.getByTestId("finance-save"));
  await waitFor(() => expect(api.putCommonFinanceItems).toHaveBeenCalled());
  expect(
    vi.mocked(api.putCommonFinanceItems).mock.calls[0][1].cards[0].cardName,
  ).toBe("회사 카드");
  await screen.findByText("저장했습니다.");
  change("세부 항목 1", "버림");
  fireEvent.click(screen.getByTestId("finance-cancel"));
  expect(screen.getByLabelText("세부 항목 1")).toHaveValue("월 항목");
});
it("maps indexed server error below the field and focuses the right row", async () => {
  await open();
  add();
  add();
  change("세부 항목 1", "가");
  change("금액* 1", "1");
  change("세부 항목 2", "나");
  change("금액* 2", "2");
  vi.mocked(api.putCommonFinanceItems).mockRejectedValue(
    new ProjectRequestError("입력 오류", [
      { field: "items[1].amount", message: "둘째 금액 오류" },
    ]),
  );
  fireEvent.click(screen.getByTestId("finance-save"));
  expect(await screen.findByText("둘째 금액 오류")).toBeVisible();
  await waitFor(() => expect(screen.getByLabelText("금액* 2")).toHaveFocus());
});
it("future month is disabled and stay keeps both drafts", async () => {
  await open();
  add();
  expect(screen.getByTestId("finance-month-202603")).toBeDisabled();
  fireEvent.click(screen.getByTestId("finance-month-202601"));
  expect(screen.getByRole("alertdialog")).toHaveTextContent(
    "저장 안 한 내용이 있습니다",
  );
  fireEvent.click(within(screen.getByRole("alertdialog")).getByText("취소"));
  expect(screen.getByLabelText("세부 항목 1")).toBeVisible();
});
it("confirmation uses unsaved schedule and merges only confirmed row into cancel baseline", async () => {
  await open();
  change("납부예정액 2026:MID", "321");
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  fireEvent.click(screen.getByTestId("finance-card-add"));
  change("카드 이름 1", "유지할 초안");
  fireEvent.click(screen.getByTestId("finance-tab-0"));
  vi.mocked(api.postCommonFinanceTaxPaymentConfirm).mockResolvedValue({
    ...payment(),
    paidYn: "Y",
    paymentAmount: 321,
    actualPaymentDate: "2026-08-31",
    status: "납부",
  });
  fireEvent.click(screen.getByTestId("finance-payment-confirm-0"));
  expect(screen.getByLabelText("실제 납부액*")).toHaveValue(321);
  fireEvent.click(screen.getByTestId("finance-payment-submit"));
  await screen.findByText("납부를 확인했습니다.");
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  expect(screen.getByLabelText("카드 이름 1")).toHaveValue("유지할 초안");
  fireEvent.click(screen.getByTestId("finance-cancel"));
  fireEvent.click(screen.getByTestId("finance-tab-0"));
  expect(screen.getByTestId("finance-payment-0")).toHaveTextContent("321");
  expect(screen.queryByTestId("finance-payment-confirm-0")).toBeNull();
});
it("closed month hides ordinary editors but exposes unpaid confirmation and adjustment navigation", async () => {
  data.closed = true;
  await open();
  expect(screen.queryByTestId("finance-item-add")).toBeNull();
  expect(screen.queryByTestId("finance-upload")).toBeNull();
  expect(screen.getByTestId("finance-save")).toBeDisabled();
  expect(screen.getByTestId("finance-payment-confirm-0")).toBeVisible();
  expect(screen.getByTestId("finance-adjust-closed")).toBeVisible();
});
it("failed request preserves scaffold and retries same month", async () => {
  vi.mocked(api.getCommonFinanceItems).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  render(<FinanceItemPage />);
  expect(await screen.findByText("조회 실패")).toBeVisible();
  expect(screen.getAllByRole("tab")).toHaveLength(2);
  fireEvent.click(screen.getByTestId("finance-retry"));
  await screen.findByText("입력한 항목이 없습니다.");
});
it("previous import is local only until save", async () => {
  await open();
  vi.mocked(api.getCommonFinancePreviousItems).mockResolvedValue({
    count: 1,
    items: [
      {
        itemId: null,
        modifiedAt: null,
        classification: "SGA",
        itemCode: null,
        itemName: "지난달",
        amount: 42,
        vatAmount: 0,
        cashDate: "2026-02-28",
        repeatYn: "N",
        autoFillYn: "N",
        uploadId: null,
        memo: null,
      },
    ],
  });
  fireEvent.click(screen.getByTestId("finance-previous"));
  await screen.findByTestId("finance-previous-dialog");
  fireEvent.click(screen.getByTestId("finance-previous-apply"));
  expect(screen.getByLabelText("세부 항목 1")).toHaveValue("지난달");
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
});
it("downloads only visible columns", async () => {
  await open();
  fireEvent.click(screen.getByTestId("finance-column-vatamount"));
  fireEvent.click(screen.getByTestId("finance-excel"));
  await waitFor(() =>
    expect(api.getCommonFinanceItemsExcel).toHaveBeenCalled(),
  );
  expect(
    vi.mocked(api.getCommonFinanceItemsExcel).mock.calls[0][1],
  ).not.toContain("vatAmount");
});
it("dirty upload can stay without losing either tab", async () => {
  await open();
  add();
  change("세부 항목 1", "그대로");
  fireEvent.click(screen.getByTestId("finance-upload"));
  expect(
    screen.getByRole("alertdialog", { name: "저장하지 않은 내용이 있습니다" }),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("confirmation-cancel"));
  expect(screen.getByLabelText("세부 항목 1")).toHaveValue("그대로");
  expect(api.checkCommonFinanceItemsUpload).not.toHaveBeenCalled();
});
it("checks file before N/M replacement and never applies before final confirmation", async () => {
  await open();
  vi.mocked(api.checkCommonFinanceItemsUpload).mockResolvedValue({
    uploadId: "1",
    modifiedAt: null,
    statusName: "검사",
    successCount: 2,
    warningCount: 0,
    errorCount: 0,
    issues: [],
  });
  fireEvent.click(screen.getByTestId("finance-upload"));
  fireEvent.change(screen.getByTestId("finance-upload-file"), {
    target: { files: [new File(["xlsx"], "finance.xlsx")] },
  });
  fireEvent.click(screen.getByTestId("finance-upload-preview"));
  expect(await screen.findByText(/새 파일 2건으로 교체/)).toBeInTheDocument();
  expect(api.postCommonFinanceItemsUpload).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("finance-upload-cancel"));
});
it("keeps invalid upload issue row and column visible without applying", async () => {
  await open();
  vi.mocked(api.checkCommonFinanceItemsUpload).mockResolvedValue({
    uploadId: "2",
    modifiedAt: null,
    statusName: "오류",
    successCount: 0,
    warningCount: 0,
    errorCount: 1,
    issues: [
      {
        row: 2,
        column: "금액",
        inputValue: "",
        reason: "금액 필수",
        severity: "오류",
      },
    ],
  });
  fireEvent.click(screen.getByTestId("finance-upload"));
  fireEvent.change(screen.getByTestId("finance-upload-file"), {
    target: { files: [new File(["xlsx"], "finance.xlsx")] },
  });
  fireEvent.click(screen.getByTestId("finance-upload-preview"));
  expect(await screen.findByText("2행 금액: 금액 필수")).toBeInTheDocument();
  expect(api.postCommonFinanceItemsUpload).not.toHaveBeenCalled();
});
it("soft card removal and restore remain draft actions until common save", async () => {
  data.cards = [
    {
      cardId: "C",
      modifiedAt: null,
      monthlyModifiedAt: null,
      cardName: "회사 카드",
      lastFourDigits: "1234",
      useYn: "Y",
      amount: 10,
    },
  ];
  await open();
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  fireEvent.click(screen.getByTestId("finance-card-toggle-0"));
  expect(screen.getByTestId("finance-card-0")).toHaveTextContent("삭제됨");
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("finance-card-toggle-0"));
  expect(screen.getByTestId("finance-card-0")).toHaveTextContent("사용");
});
it("manual card total shows difference and automatic restoration", async () => {
  await open();
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  change("자금수지 반영 합계", "123");
  expect(screen.getByText("직접 고침 · 차액 123")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("finance-card-auto"));
  expect(screen.queryByText(/직접 고침/)).not.toBeInTheDocument();
  expect(screen.getByLabelText("자금수지 반영 합계")).toHaveValue(0);
});
it("negative tax warning preserves read-only calculated monthly row", async () => {
  data.taxEstimate.negativeTaxWarning = true;
  data.taxEstimate.monthlyTaxAmount = -1;
  await open();
  expect(screen.getByText("금액이 맞는지 확인하세요")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("finance-tax-focus"));
  expect(screen.getByLabelText("연간 예상 법인세")).toHaveFocus();
});

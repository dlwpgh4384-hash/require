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
import type { FinanceItem, FinanceMonth, FinancePayment } from "./financeItems";
import * as api from "./financeItems";
import * as uploads from "../uploads/uploads";

const viewport = vi.hoisted(() => ({ mobile: false, tablet: false }));
const start = vi.hoisted(() => ({ year: 2026, month: 2 }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
vi.mock("../userpreferences/useScreenPeriod", async () => {
  const { useState } = await import("react");
  return {
    useScreenPeriod: () => {
      const [value, setValue] = useState({ ...start });
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
  getCommonCodes: vi.fn(async (group: string) => ({
    items: group === "UPLD_STAT" ? [] : null,
  })),
}));
vi.mock("./financeItems", async (original) => ({
  ...(await original<typeof import("./financeItems")>()),
  getCommonFinanceItems: vi.fn(),
  postCommonFinanceTaxYear: vi.fn(),
  putCommonFinanceItems: vi.fn(),
  postCommonFinanceTaxPaymentConfirm: vi.fn(),
  getCommonFinancePreviousItems: vi.fn(),
}));
vi.mock("../uploads/uploads", async (original) => ({
  ...(await original<typeof import("../uploads/uploads")>()),
  postCommonUpload: vi.fn(),
  getCommonUpload: vi.fn(),
  postCommonUploadApply: vi.fn(),
  getCommonUploadTemplate: vi.fn(),
}));
let data: FinanceMonth;
const dirty = vi.fn();
const payment = (over: Partial<FinancePayment> = {}): FinancePayment => ({
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
  ...over,
});
const item = (over: Partial<FinanceItem> = {}): FinanceItem => ({
  itemId: "1",
  modifiedAt: "2026-02-01T00:00:00Z",
  classification: "SGA",
  itemCode: null,
  itemName: "임대료",
  amount: 1000,
  vatAmount: 100,
  cashDate: null,
  repeatYn: "N",
  autoFillYn: "N",
  uploadId: null,
  memo: null,
  ...over,
});
function month(key: string, closed: boolean): FinanceMonth {
  return {
    yearMonth: key,
    closed,
    firstOpenYearMonth: "202602",
    modifiedAt: null,
    months: [
      { yearMonth: "202601", closed: true, itemCount: 3, future: false },
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
}
beforeEach(() => {
  vi.clearAllMocks();
  viewport.mobile = false;
  start.year = 2026;
  start.month = 2;
  data = month("202602", false);
  vi.mocked(api.postCommonFinanceTaxYear).mockResolvedValue({});
  vi.mocked(api.getCommonFinanceItems).mockImplementation(async () =>
    structuredClone(data),
  );
  vi.mocked(api.putCommonFinanceItems).mockImplementation(
    async (_month, value) => structuredClone(value),
  );
});
afterEach(cleanup);
function mount() {
  render(
    <MemoryRouter>
      <ScreenDirtyContext.Provider value={dirty}>
        <FinanceItemPage />
      </ScreenDirtyContext.Provider>
    </MemoryRouter>,
  );
}
async function open() {
  mount();
  await waitFor(() => expect(api.getCommonFinanceItems).toHaveBeenCalled());
  await screen.findByTestId("finance-tax-row");
  if (!data.closed)
    await waitFor(() => expect(screen.getByTestId("finance-save")).toBeEnabled());
  else
    await waitFor(() =>
      expect(screen.getByTestId("finance-adjust-closed")).toBeEnabled(),
    );
}
const add = () => fireEvent.click(screen.getByTestId("finance-item-add"));
const type = (testId: string, value: string) =>
  fireEvent.change(screen.getByTestId(testId), { target: { value } });
const confirmBox = () => screen.getByRole("alertdialog");

it("prepares the year, then shows strip, both tabs, table header, tax row and empty texts", async () => {
  await open();
  expect(
    vi.mocked(api.postCommonFinanceTaxYear).mock.invocationCallOrder[0],
  ).toBeLessThan(vi.mocked(api.getCommonFinanceItems).mock.invocationCallOrder[0]);
  expect(screen.getByText("입력 현황")).toBeVisible();
  expect(screen.getByTestId("finance-month-202601")).toHaveTextContent("마감 · 3건");
  expect(screen.getByTestId("finance-month-202602")).toHaveTextContent("작업중 · 0건");
  expect(screen.getByTestId("finance-month-202603")).toBeDisabled();
  expect(screen.getByTestId("finance-month-202603")).toHaveTextContent("–");
  expect(screen.getByTestId("finance-tab-0")).toBeVisible();
  expect(screen.getByTestId("finance-tab-1")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "세부 항목 *" })).toBeVisible();
  expect(screen.getByTestId("finance-tax-row")).toHaveTextContent(
    "납부는 3월·8월 (자금수지)",
  );
  expect(screen.getByText("판관비 합계")).toBeVisible();
  expect(screen.getByText("2월 전월 조정이 없습니다.")).toBeVisible();
  expect(screen.queryByText("엑셀 다운로드")).toBeNull();
  expect(screen.queryByText("열 설정")).toBeNull();
  expect(screen.getByTestId("finance-previous")).toBeEnabled();
  expect(screen.getByTestId("upload-template")).toBeEnabled();
  expect(screen.getByTestId("upload-open")).toBeEnabled();
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  expect(screen.getByText("등록한 카드가 없습니다.")).toBeVisible();
  expect(screen.getByText("2월 계좌이체 지출이 없습니다.")).toBeVisible();
  expect(screen.getByText("2월 대여금 입출금이 없습니다.")).toBeVisible();
});
it("mobile shows the common PC-only state and never reads finance", async () => {
  viewport.mobile = true;
  render(
    <MemoryRouter>
      <FinanceItemPage />
    </MemoryRouter>,
  );
  expect(screen.getByText("PC에서 이용해 주세요")).toBeVisible();
  expect(screen.queryByRole("tab")).toBeNull();
  expect(api.getCommonFinanceItems).not.toHaveBeenCalled();
});
it("money inputs show commas while typing, keep digits, and VAT becomes 0 only on blur", async () => {
  await open();
  add();
  type("finance-item-amount-0", "1234567");
  expect(screen.getByTestId("finance-item-amount-0")).toHaveValue("1,234,567");
  const vat = screen.getByTestId("finance-item-vat-0");
  fireEvent.change(vat, { target: { value: "" } });
  expect(vat).toHaveValue("");
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  fireEvent.blur(vat);
  expect(vat).toHaveValue("0");
  type("finance-item-name-0", "직접 항목");
  fireEvent.click(screen.getByTestId("finance-save"));
  await waitFor(() => expect(api.putCommonFinanceItems).toHaveBeenCalled());
  expect(
    vi.mocked(api.putCommonFinanceItems).mock.calls[0][1].items[0],
  ).toMatchObject({ itemName: "직접 항목", amount: "1234567" });
});
it("blank amount shows the error under the field and focuses it; explicit zero saves", async () => {
  await open();
  add();
  type("finance-item-name-0", "직접 항목");
  fireEvent.click(screen.getByTestId("finance-save"));
  expect(
    await within(screen.getByTestId("finance-item-0")).findByText(
      "금액을 입력해 주세요.",
    ),
  ).toBeVisible();
  await waitFor(() =>
    expect(screen.getByTestId("finance-item-amount-0")).toHaveFocus(),
  );
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  type("finance-item-amount-0", "0");
  fireEvent.click(screen.getByTestId("finance-save"));
  await waitFor(() => expect(api.putCommonFinanceItems).toHaveBeenCalled());
});
it("Ctrl+S saves an open month with the mockup toast", async () => {
  data.items = [item()];
  await open();
  type("finance-item-amount-0", "2000");
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() =>
    expect(api.putCommonFinanceItems).toHaveBeenCalledTimes(1),
  );
  expect(await screen.findByText("저장했습니다.")).toBeVisible();
});
it("Ctrl+S in a closed month does not save", async () => {
  data.closed = true;
  await open();
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  expect(
    await screen.findByText("이 화면에는 저장할 내용이 없습니다."),
  ).toBeVisible();
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
});
it("row delete asks the mockup confirmation first", async () => {
  data.items = [item()];
  await open();
  fireEvent.click(screen.getByTestId("finance-item-delete-0"));
  expect(confirmBox()).toHaveTextContent("이 행을 삭제할까요?");
  expect(confirmBox()).toHaveTextContent("「임대료」 행을 지웁니다.");
  fireEvent.click(within(confirmBox()).getByText("취소"));
  expect(screen.getByTestId("finance-item-0")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("finance-item-delete-0"));
  fireEvent.click(within(confirmBox()).getByText("삭제"));
  expect(screen.queryByTestId("finance-item-0")).toBeNull();
});
it("tax payments show mockup kind names and status badges", async () => {
  data.taxPayments = [
    payment({
      paymentId: "2025:CNFM",
      taxYear: 2025,
      paymentType: "CNFM",
      scheduledDate: "2026-03-31",
      paymentAmount: 18400000,
      displayAmount: 18400000,
      paidYn: "Y",
      actualPaymentDate: "2026-03-31",
      status: "납부",
    }),
    payment(),
    payment({
      paymentId: "2026:CNFM",
      paymentType: "CNFM",
      scheduledDate: "2027-03-31",
      displayAmount: 120000000,
      status: "확인 필요",
    }),
  ];
  await open();
  const rows = within(screen.getByTestId("finance-payments"));
  expect(rows.getByText("2025년 확정 신고")).toBeVisible();
  expect(rows.getByText("2026년 중간예납")).toBeVisible();
  expect(rows.getByText("2026년 확정 신고")).toBeVisible();
  expect(screen.queryByText(/MID|CNFM/)).toBeNull();
  expect(screen.getByTestId("finance-payment-0")).toHaveTextContent(
    "18,400,000",
  );
  expect(screen.getByTestId("finance-payment-amount-1")).toHaveAttribute(
    "placeholder",
    "세무사 안내 금액",
  );
  expect(screen.getByTestId("finance-payment-amount-2")).toHaveValue(
    "120,000,000",
  );
  expect(screen.getByTestId("finance-payment-2")).toHaveTextContent(
    "자동 계산",
  );
  expect(screen.getByTestId("finance-payment-confirm-2")).toHaveClass(
    "ls-btn-p",
  );
});
it("closed month hides title buttons and the bottom bar but keeps confirmation and adjustment entry", async () => {
  data.closed = true;
  data.items = [item()];
  await open();
  expect(screen.queryByTestId("finance-previous")).toBeNull();
  expect(screen.queryByTestId("upload-open")).toBeNull();
  expect(screen.queryByTestId("upload-template")).toBeNull();
  expect(screen.queryByTestId("finance-save")).toBeNull();
  expect(screen.queryByTestId("finance-item-add")).toBeNull();
  expect(
    screen.getByText(
      "는 마감되었습니다. 고칠 내용은 다음 달 전월 조정으로 입력하세요.",
      { exact: false },
    ),
  ).toBeVisible();
  expect(screen.getByTestId("finance-item-0")).toHaveTextContent("1,000");
  expect(screen.getByTestId("finance-payment-confirm-0")).toBeVisible();
});
it("[전월 조정 입력] opens the first open month with the closed month preselected", async () => {
  start.month = 1;
  vi.mocked(api.getCommonFinanceItems).mockImplementation(async (key) =>
    structuredClone(month(key, key === "202601")),
  );
  mount();
  await waitFor(() =>
    expect(screen.getByTestId("finance-adjust-closed")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("finance-adjust-closed"));
  expect(
    await screen.findByText("2월 재무 항목의 「전월 조정」 표에 넣어 주세요."),
  ).toBeVisible();
  await waitFor(() =>
    expect(api.getCommonFinanceItems).toHaveBeenLastCalledWith(
      "202602",
      expect.anything(),
    ),
  );
  expect(await screen.findByTestId("finance-adjustment-target")).toHaveValue(
    "202601",
  );
});
it("adjustment input row validates with mockup toasts and adds a draft row with sum", async () => {
  await open();
  fireEvent.click(screen.getByTestId("finance-adjustment-add"));
  expect(
    await screen.findByText("금액을 넣어 주세요. 이익이 줄면 − 를 붙입니다."),
  ).toBeVisible();
  const card = within(screen.getByTestId("finance-adjustment-card"));
  fireEvent.change(card.getByLabelText("금액"), {
    target: { value: "-100,000" },
  });
  fireEvent.click(screen.getByTestId("finance-adjustment-add"));
  expect(await screen.findByText("사유를 넣어 주세요.")).toBeVisible();
  fireEvent.change(card.getByLabelText("사유"), {
    target: { value: "9월 급여 누락" },
  });
  fireEvent.click(screen.getByTestId("finance-adjustment-add"));
  expect(screen.getByTestId("finance-adjustment-0")).toHaveTextContent(
    "−100,000",
  );
  expect(screen.getByTestId("finance-adjustment-0")).toHaveTextContent(
    "저장 전",
  );
  expect(
    card.getByText("영업이익표·순이익표 2월 「전월 조정」 줄"),
  ).toBeVisible();
});
it("upload uses the shared parts with B2 and the selected month, confirming replacement first", async () => {
  data.items = [
    item({ itemId: "1", uploadId: "9" }),
    item({ itemId: "2", itemName: "통신비", uploadId: "9" }),
    item({ itemId: "3", itemName: "직접" }),
  ];
  const checked = {
    uploadId: "10",
    modifiedAt: null,
    statusName: "검사",
    successCount: 2,
    warningCount: 0,
    errorCount: 0,
    issues: [],
  };
  vi.mocked(uploads.postCommonUpload).mockResolvedValue(checked);
  vi.mocked(uploads.getCommonUpload).mockResolvedValue(checked);
  await open();
  fireEvent.click(screen.getByTestId("upload-open"));
  const dialog = within(screen.getByTestId("upload-dialog"));
  expect(dialog.getByText("엑셀 업로드 · 재무 항목")).toBeVisible();
  expect(dialog.getByTestId("upload-year-month")).toHaveValue("2026-02");
  fireEvent.change(dialog.getByTestId("upload-file"), {
    target: { files: [new File(["x"], "finance.xlsx")] },
  });
  fireEvent.click(dialog.getByText("올리기"));
  const ask = within(await screen.findByTestId("upload-check-confirm"));
  expect(
    ask.getByText(
      "엑셀로 올린 2월 항목 2줄을 지우고 새 엑셀 내용으로 바꿉니다. 직접 입력한 1줄은 그대로 둡니다.",
      { exact: false },
    ),
  ).toBeVisible();
  expect(uploads.postCommonUpload).not.toHaveBeenCalled();
  fireEvent.click(ask.getByText("바꾸기"));
  await waitFor(() =>
    expect(uploads.postCommonUpload).toHaveBeenCalledWith(
      "B2",
      expect.any(File),
      undefined,
      "2026-02",
    ),
  );
  expect(await screen.findByTestId("upload-result-dialog")).toBeInTheDocument();
});
it("dirty month change asks with the mockup title, body and button", async () => {
  await open();
  add();
  type("finance-item-name-0", "그대로");
  fireEvent.click(screen.getByTestId("finance-month-202601"));
  expect(confirmBox()).toHaveTextContent("저장 안 한 내용이 있습니다");
  expect(confirmBox()).toHaveTextContent(
    "판관비·영업외에서 바꾼 내용이 아직 저장되지 않았습니다. 저장하지 않고 다른 달로 갈까요?",
  );
  expect(within(confirmBox()).getByText("저장 안 하고 이동")).toBeVisible();
  fireEvent.click(within(confirmBox()).getByText("취소"));
  expect(screen.getByTestId("finance-item-name-0")).toHaveValue("그대로");
  expect(screen.getByTestId("finance-tab-0")).toHaveTextContent("저장 전");
});
it("409 shows the common conflict message, keeps the input and offers reload", async () => {
  data.items = [item()];
  await open();
  type("finance-item-name-0", "내 입력");
  vi.mocked(api.putCommonFinanceItems).mockRejectedValue(
    new api.FinanceRequestError(
      "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
      [],
      409,
    ),
  );
  fireEvent.click(screen.getByTestId("finance-save"));
  expect(
    await screen.findByText("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."),
  ).toBeVisible();
  expect(screen.getByTestId("finance-item-name-0")).toHaveValue("내 입력");
  fireEvent.click(screen.getByTestId("finance-reload"));
  await waitFor(() =>
    expect(api.getCommonFinanceItems).toHaveBeenCalledTimes(2),
  );
  expect(await screen.findByTestId("finance-item-name-0")).toHaveValue(
    "임대료",
  );
});
it("maps indexed server errors below the right field and focuses it", async () => {
  data.items = [item(), item({ itemId: "2", itemName: "통신비" })];
  data.taxPayments = [
    payment({
      paymentId: "2025:CNFM",
      taxYear: 2025,
      paymentType: "CNFM",
      paidYn: "Y",
      status: "납부",
    }),
    payment(),
  ];
  await open();
  vi.mocked(api.putCommonFinanceItems).mockRejectedValue(
    new api.FinanceRequestError("입력 오류", [
      { field: "items[1].amount", message: "둘째 금액 오류." },
      {
        field: "taxPayments[0].scheduledDate",
        message: "납부일을 입력해 주세요.",
      },
    ]),
  );
  fireEvent.click(screen.getByTestId("finance-save"));
  expect(
    await within(screen.getByTestId("finance-item-1")).findByText(
      "둘째 금액 오류.",
    ),
  ).toBeVisible();
  // 서버는 납부 확인 안 한 줄만 받으므로 서버 0번은 화면의 1번 줄이다.
  expect(
    within(screen.getByTestId("finance-payment-1")).getByText(
      "납부일을 입력해 주세요.",
    ),
  ).toBeVisible();
  await waitFor(() =>
    expect(screen.getByTestId("finance-item-amount-1")).toHaveFocus(),
  );
});
it("saves both tabs together and cancel restores both with the mockup toast", async () => {
  await open();
  add();
  type("finance-item-name-0", "월 항목");
  type("finance-item-amount-0", "2");
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  const cards = within(screen.getByTestId("finance-card-card"));
  fireEvent.change(cards.getByLabelText("카드 이름"), {
    target: { value: "회사 카드" },
  });
  fireEvent.change(cards.getByLabelText("끝 4자리"), {
    target: { value: "1234" },
  });
  fireEvent.click(screen.getByTestId("finance-card-add"));
  expect(screen.getByTestId("finance-card-0")).toHaveTextContent("새 카드");
  expect(screen.getByTestId("finance-tab-1")).toHaveTextContent("저장 전");
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(api.putCommonFinanceItems).toHaveBeenCalled());
  expect(
    vi.mocked(api.putCommonFinanceItems).mock.calls[0][1].cards[0].cardName,
  ).toBe("회사 카드");
  expect(
    await screen.findByText("저장했습니다. 법인카드도 함께 반영했습니다."),
  ).toBeVisible();
  fireEvent.click(screen.getByTestId("finance-tab-0"));
  type("finance-item-name-0", "버림");
  fireEvent.click(screen.getByTestId("finance-cancel"));
  expect(screen.getByTestId("finance-item-name-0")).toHaveValue("월 항목");
  expect(await screen.findByText("변경을 취소했습니다.")).toBeVisible();
});
it("card add validates with periods and only changed sections show 저장 전", async () => {
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
  expect(screen.getByTestId("finance-card-card")).not.toHaveTextContent(
    "저장 전",
  );
  expect(screen.getByTestId("finance-card-0")).not.toHaveTextContent("새 카드");
  fireEvent.click(screen.getByTestId("finance-card-add"));
  expect(await screen.findByText("카드 이름을 넣어 주세요.")).toBeVisible();
  fireEvent.click(screen.getByTestId("finance-card-toggle-0"));
  expect(screen.getByTestId("finance-card-0")).toHaveTextContent("삭제됨");
  expect(screen.getByTestId("finance-card-card")).toHaveTextContent("저장 전");
  fireEvent.click(screen.getByTestId("finance-card-toggle-0"));
  expect(screen.getByTestId("finance-card-card")).not.toHaveTextContent(
    "저장 전",
  );
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
});
it("manual card total shows the difference text and returns to the automatic sum", async () => {
  await open();
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  const total = screen.getByTestId("finance-card-total");
  fireEvent.focus(total);
  fireEvent.change(total, { target: { value: "123" } });
  fireEvent.blur(total);
  expect(screen.getByText("카드별 합계와 +123 차이")).toBeVisible();
  expect(screen.getByText("직접 고침")).toBeVisible();
  fireEvent.click(screen.getByTestId("finance-card-auto"));
  expect(screen.queryByText(/카드별 합계와/)).toBeNull();
  expect(screen.getByTestId("finance-card-total")).toHaveValue("0");
});
it("transfer and loan input rows validate and add draft rows with Korean labels", async () => {
  await open();
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  const transfer = within(screen.getByTestId("finance-transfer-card"));
  fireEvent.click(screen.getByTestId("finance-transfer-add"));
  expect(await screen.findByText("내용을 넣어 주세요.")).toBeVisible();
  fireEvent.change(transfer.getByLabelText("내용"), {
    target: { value: "장비 임대" },
  });
  fireEvent.change(transfer.getByLabelText("금액"), {
    target: { value: "50000" },
  });
  fireEvent.click(screen.getByTestId("finance-transfer-add"));
  expect(screen.getByTestId("finance-transfer-0")).toHaveTextContent("50,000");
  expect(screen.getByTestId("finance-transfer-0")).toHaveTextContent("저장 전");
  expect(transfer.getByText("1건")).toBeVisible();
  const loan = within(screen.getByTestId("finance-loan-card"));
  fireEvent.change(loan.getByLabelText("구분"), { target: { value: "OUT" } });
  fireEvent.change(loan.getByLabelText("금액"), { target: { value: "3000" } });
  fireEvent.click(screen.getByTestId("finance-loan-add"));
  expect(screen.getByTestId("finance-loan-0")).toHaveTextContent("갚음");
  expect(screen.getByTestId("finance-loan-0")).toHaveTextContent("−3,000");
});
it("payment confirmation uses the unsaved amount and shows the mockup toast", async () => {
  await open();
  type("finance-payment-amount-0", "321");
  vi.mocked(api.postCommonFinanceTaxPaymentConfirm).mockResolvedValue(
    payment({
      paidYn: "Y",
      paymentAmount: 321,
      actualPaymentDate: "2026-08-31",
      status: "납부",
    }),
  );
  fireEvent.click(screen.getByTestId("finance-payment-confirm-0"));
  const dialog = within(screen.getByTestId("finance-payment-dialog"));
  expect(dialog.getByText("법인세 납부 확인")).toBeVisible();
  expect(dialog.getByLabelText(/실제 납부액/)).toHaveValue("321");
  fireEvent.click(dialog.getByText("납부 확인"));
  expect(
    await screen.findByText(
      "2026년 중간예납 납부를 확인했습니다. 자금수지표 8월 법인세 납부에 321원으로 반영했습니다.",
    ),
  ).toBeVisible();
  expect(screen.queryByTestId("finance-payment-confirm-0")).toBeNull();
});
it("failed read keeps the strip and tabs and retries the same month", async () => {
  vi.mocked(api.getCommonFinanceItems).mockRejectedValueOnce(
    new Error("조회 실패"),
  );
  mount();
  expect(await screen.findByText("조회 실패")).toBeVisible();
  expect(screen.getByText("입력 현황")).toBeVisible();
  expect(screen.getByTestId("finance-tab-0")).toBeVisible();
  expect(screen.getByTestId("finance-save")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("finance-retry"));
  await waitFor(() => expect(screen.getByTestId("finance-save")).toBeEnabled());
});
it("previous import asks first, marks rows 자동 and shows the auto-fill banner", async () => {
  await open();
  vi.mocked(api.getCommonFinancePreviousItems).mockResolvedValue({
    count: 1,
    items: [item({ itemId: null, itemName: "지난달", amount: 42 })],
  });
  fireEvent.click(screen.getByTestId("finance-previous"));
  expect(await screen.findByText("2026-01 항목을 불러올까요?")).toBeVisible();
  fireEvent.click(within(confirmBox()).getByText("불러오기"));
  expect(screen.getByTestId("finance-item-name-0")).toHaveValue("지난달");
  expect(screen.getByTestId("finance-item-0")).toHaveTextContent("자동");
  expect(screen.getByText("1개 항목을 불러왔습니다.")).toBeVisible();
  expect(screen.getByText(/자동으로 채웠습니다/)).toBeVisible();
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
});
it("negative tax warning and the tax row jumps to the annual estimate", async () => {
  data.taxEstimate.negativeTaxWarning = true;
  data.taxEstimate.monthlyTaxAmount = -1;
  await open();
  expect(screen.getByText(/금액이 맞는지 확인하세요/)).toBeVisible();
  expect(screen.getByTestId("finance-tax-focus")).toHaveTextContent("−1");
  fireEvent.click(screen.getByTestId("finance-tax-focus"));
  expect(screen.getByLabelText("연간 예상 법인세")).toHaveFocus();
});
it("drops a previous-items answer that arrives after moving to another month", async () => {
  vi.mocked(api.getCommonFinanceItems).mockImplementation(async (key) =>
    key === "202602" ? structuredClone(data) : month(key, true),
  );
  await open();
  let answer: (value: { items: FinanceItem[]; count: number }) => void = () => undefined;
  vi.mocked(api.getCommonFinancePreviousItems).mockReturnValue(
    new Promise((resolve) => {
      answer = resolve;
    }),
  );
  fireEvent.click(screen.getByTestId("finance-previous"));
  fireEvent.click(screen.getByTestId("finance-month-202601"));
  await waitFor(() =>
    expect(api.getCommonFinanceItems).toHaveBeenLastCalledWith(
      "202601",
      expect.anything(),
    ),
  );
  await screen.findByTestId("finance-adjust-closed");
  answer({
    count: 1,
    items: [item({ itemId: null, itemName: "지난달", amount: 42 })],
  });
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(screen.queryByText("2026-01 항목을 불러올까요?")).toBeNull();
  expect(screen.queryByRole("alertdialog")).toBeNull();
});
it("text inputs stop at the DDL lengths", async () => {
  data.items = [item()];
  await open();
  expect(screen.getByTestId("finance-item-name-0")).toHaveAttribute("maxLength", "50");
  fireEvent.click(screen.getByTestId("finance-tab-1"));
  const card = within(screen.getByTestId("finance-card-card"));
  expect(card.getByLabelText("카드 이름")).toHaveAttribute("maxLength", "100");
  const transfer = within(screen.getByTestId("finance-transfer-card"));
  expect(transfer.getByLabelText("내용")).toHaveAttribute("maxLength", "100");
  expect(transfer.getByLabelText("받는 곳")).toHaveAttribute("maxLength", "100");
  expect(transfer.getByLabelText("메모")).toHaveAttribute("maxLength", "200");
  const loan = within(screen.getByTestId("finance-loan-card"));
  expect(loan.getByLabelText("상대")).toHaveAttribute("maxLength", "100");
  expect(loan.getByLabelText("메모")).toHaveAttribute("maxLength", "200");
});

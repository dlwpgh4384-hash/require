import { ProjectRequestError } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";
import type { UploadResult } from "../uploads/uploads";
export type Amount = number | string;
export interface FinanceItem {
  itemId: string | null;
  modifiedAt: string | null;
  classification: string;
  itemCode: string | null;
  itemName: string;
  amount: Amount;
  vatAmount: Amount;
  cashDate: string | null;
  repeatYn: string;
  autoFillYn: string;
  uploadId: string | null;
  memo: string | null;
}
export interface FinanceLoan {
  loanId: string | null;
  modifiedAt: string | null;
  date: string;
  direction: string;
  amount: Amount;
  counterparty: string | null;
  memo: string | null;
}
export interface FinanceTransfer {
  transferId: string | null;
  modifiedAt: string | null;
  date: string;
  classification: string;
  description: string;
  payee: string | null;
  amount: Amount;
  memo: string | null;
}
export interface FinanceAdjustment {
  adjustmentId: string | null;
  modifiedAt: string | null;
  targetYearMonth: string;
  adjustmentItem: string;
  amount: Amount;
  reason: string;
}
export interface FinanceCard {
  cardId: string | null;
  modifiedAt: string | null;
  cardName: string;
  lastFourDigits: string;
  useYn: string;
  amount: Amount;
  monthlyModifiedAt: string | null;
}
export interface FinancePayment {
  paymentId: string;
  taxYear: number;
  paymentType: string;
  scheduledDate: string;
  paymentAmount: Amount | null;
  displayAmount: number;
  paidYn: string;
  actualPaymentDate: string | null;
  status: string;
  modifiedAt: string | null;
}
export interface FinanceMonth {
  yearMonth: string;
  closed: boolean;
  firstOpenYearMonth: string | null;
  modifiedAt: string | null;
  months: {
    yearMonth: string;
    closed: boolean;
    itemCount: number;
    future: boolean;
  }[];
  items: FinanceItem[];
  loans: FinanceLoan[];
  transfers: FinanceTransfer[];
  adjustments: FinanceAdjustment[];
  cards: FinanceCard[];
  taxPayments: FinancePayment[];
  taxEstimate: {
    taxYear: number;
    estimatedTaxAmount: Amount;
    monthlyTaxAmount: number;
    negativeTaxWarning: boolean;
    modifiedAt: string | null;
  };
  cardPayment: {
    paymentAmount: Amount;
    manualOverride: boolean;
    modifiedAt: string | null;
  };
  summary: {
    sgaAmount: number;
    nonOperatingIncomeAmount: number;
    nonOperatingExpenseAmount: number;
    monthlyTaxAmount: number;
    adjustmentAmount: number;
    cardTotalAmount: number;
    cardPaymentDifference: number;
  };
  activeCardCount: number;
  autoFillCount: number;
}
const root = "/api/common/finance-items";
const path = (month: string) => `${root}/${encodeURIComponent(month)}`;
async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { credentials: "same-origin", ...init });
  const body = await response.json();
  if (!response.ok || body.success === false)
    throw new ProjectRequestError(
      body.message || "재무 항목을 처리하지 못했습니다.",
      body.errors ?? [],
    );
  return body.data;
}
export const getCommonFinanceItems = (month: string, signal?: AbortSignal) =>
  request<FinanceMonth>(path(month), { signal });
export const postCommonFinanceTaxYear = (year: number, signal?: AbortSignal) =>
  request(`${root}/tax-years/${year}`, { method: "POST", signal });
export const putCommonFinanceItems = (month: string, value: FinanceMonth) => {
  const numeric = <T extends { amount: Amount }>(row: T) => ({
    ...row,
    amount: row.amount === "" ? null : Number(row.amount),
  });
  return request<FinanceMonth>(path(month), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      modifiedAt: value.modifiedAt,
      items: value.items.map((row) => ({
        ...numeric(row),
        vatAmount: Number(row.vatAmount || 0),
      })),
      loans: value.loans.map(numeric),
      transfers: value.transfers.map(numeric),
      adjustments: value.adjustments.map(numeric),
      cards: value.cards.map(numeric),
      taxEstimate: {
        taxYear: value.taxEstimate.taxYear,
        estimatedTaxAmount: Number(value.taxEstimate.estimatedTaxAmount),
        modifiedAt: value.taxEstimate.modifiedAt,
      },
      taxPayments: value.taxPayments
        .filter((row) => row.paidYn !== "Y")
        .map((row) => ({
          paymentId: row.paymentId,
          scheduledDate: row.scheduledDate,
          paymentAmount:
            row.paymentAmount === null || row.paymentAmount === ""
              ? null
              : Number(row.paymentAmount),
          modifiedAt: row.modifiedAt,
        })),
      cardPayment: {
        ...value.cardPayment,
        paymentAmount: Number(value.cardPayment.paymentAmount || 0),
      },
    }),
  });
};
export const postCommonFinanceTaxPaymentConfirm = (
  row: FinancePayment,
  date: string,
  amount: Amount,
) =>
  request<FinancePayment>(
    `${root}/tax-payments/${encodeURIComponent(row.paymentId)}/confirm`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        actualPaymentDate: date || null,
        actualPaymentAmount: amount === "" ? null : Number(amount),
        modifiedAt: row.modifiedAt,
      }),
    },
  );
export const getCommonFinancePreviousItems = (
  month: string,
  rows: FinanceItem[],
) => {
  const query = new URLSearchParams();
  rows.forEach((row) =>
    query.append("existingKeys", `${row.classification}:${row.itemName}`),
  );
  return request<{ items: FinanceItem[]; count: number }>(
    `${path(month)}/previous-items?${query}`,
  );
};
export const postCommonFinanceItemsUpload = (
  month: string,
  file: File,
  modifiedAt: string | null,
) => {
  const body = new FormData();
  body.set("file", file);
  body.set("modifiedAt", modifiedAt ?? "null");
  return request<{
    uploadId: string;
    items: FinanceItem[];
    replacedCount: number;
    preservedCount: number;
  }>(`${path(month)}/uploads`, { method: "POST", body });
};
export const checkCommonFinanceItemsUpload = (month: string, file: File) => {
  const body = new FormData();
  body.set("formTypeCode", "B2");
  body.set("file", file);
  body.set("yearMonth", `${month.slice(0, 4)}-${month.slice(4)}`);
  return request<UploadResult>("/api/common/uploads", { method: "POST", body });
};
export const getCommonFinanceItemsExcel = (month: string, columns: string[]) =>
  downloadFile(
    `${path(month)}/excel?${new URLSearchParams({ columns: columns.join(",") })}`,
    "재무 항목.xlsx",
  );
export const getCommonFinanceItemsTemplate = () =>
  downloadFile(`${root}/upload-template`, "finance-items-template.xlsx");

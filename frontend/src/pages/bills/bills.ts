import { projectRequest, ProjectRequestError } from "../projects/projects";
import type { BillingReceiptResult } from "../commonops/billingLookups";

export interface Bill {
  billId: string;
  projectId: string;
  projectName: string;
  customerName: string;
  billOrder: number;
  billMonth: string;
  billDate: string;
  supplyAmount: number;
  vatRate: number;
  vatAmount: number;
  totalAmount: number;
  receiptSumAmount: number;
  unpaidAmount: number;
  payDueDate: string;
  taxInvoiceNo: string | null;
  status: string;
  cancelled: boolean;
  cancelReason: string | null;
  cancelledBy: string | null;
  cancelledAt: string | null;
  dunningStep: number | null;
  daysOverdue: number;
  canEdit: boolean;
  canCancel: boolean;
  canReceive: boolean;
  canCopy: boolean;
  canNextMonth: boolean;
}
export interface BillDetail {
  bill: Bill;
  receipts: BillingReceiptResult["receipts"];
  missingTaxInvoiceNumber: boolean;
}
export interface BillList {
  items: Bill[];
  readOnly: boolean;
  summary: {
    yearBillCount: number;
    yearBillTotal: number;
    currentMonthBillTotal: number;
    unpaidCount: number;
    unpaidTotal: number;
    overdueCount: number;
  };
  totals: Pick<
    Bill,
    | "supplyAmount"
    | "vatAmount"
    | "totalAmount"
    | "receiptSumAmount"
    | "unpaidAmount"
  >;
}
export interface BillValues {
  supplyAmount: number;
  vatRate: number;
  billDate: string;
  payDueDate: string;
  taxInvoiceNo: string | null;
}
export interface BillCreate extends BillValues {
  projectId: string;
  billMonth: string;
  sourceBillId?: string;
  duplicateConfirmed?: boolean;
}
export const getCommonBills = (
  filter: {
    year: number;
    status?: string;
    billMonth?: string;
    projectId?: string;
  },
  signal?: AbortSignal,
) => {
  const query = new URLSearchParams({ year: String(filter.year) });
  for (const key of ["status", "billMonth", "projectId"] as const)
    if (filter[key]) query.set(key, filter[key]!);
  return projectRequest<BillList>(`/api/common/bills?${query}`, { signal });
};
export const getCommonBill = (id: string, signal?: AbortSignal) =>
  projectRequest<BillDetail>(`/api/common/bills/${encodeURIComponent(id)}`, {
    signal,
  });
const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const putCommonBill = (id: string, body: BillValues) =>
  projectRequest<BillDetail>(`/api/common/bills/${encodeURIComponent(id)}`, {
    method: "PUT",
    ...json(body),
  });
export const postCommonBillCancel = (id: string, reason: string) =>
  projectRequest<BillDetail>(
    `/api/common/bills/${encodeURIComponent(id)}/cancel`,
    { method: "POST", ...json({ reason }) },
  );
export const postCommonBillNextMonth = (id: string) =>
  projectRequest<BillDetail>(
    `/api/common/bills/${encodeURIComponent(id)}/next-month`,
    { method: "POST" },
  );
// Preserve the successful duplicate-warning envelope: data=null is not a saved invoice.
export async function postCommonBill(
  body: BillCreate,
): Promise<{ data: BillDetail | null; warning?: string }> {
  const response = await fetch("/api/common/bills", {
    method: "POST",
    ...json(body),
  });
  const envelope = await response.json();
  if (!response.ok || !envelope.success)
    throw new ProjectRequestError(
      envelope.message ?? "청구를 저장하지 못했습니다.",
      envelope.errors ?? [],
    );
  return { data: envelope.data, warning: envelope.warning };
}
export const billMoney = (value: number) => value.toLocaleString("ko-KR");
export const billStatus = (value: string) =>
  ({ PAID: "입금 완료", UNPAID: "미수", LATE: "연체", CNCL: "취소됨" })[
    value
  ] ?? value;

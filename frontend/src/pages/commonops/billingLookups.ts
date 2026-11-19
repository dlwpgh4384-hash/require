import { projectRequest } from "../projects/projects";

export interface BillingProjectOption {
  projectId: string;
  projectName: string;
  customerName: string;
  projectType: string;
}
export interface BillingOptions {
  projects: BillingProjectOption[];
  statusOptions: { code: string; name: string }[];
  defaultBillMonth: string;
  defaultBillDate: string;
  defaultPayDueDate: string;
  estimatedSupplyAmount: number | null;
  vatRate: number;
  /** billMonth(없으면 이번 달)가 마감한 달인지. 마감한 달에는 새 청구를 만들 수 없다. */
  monthClosed: boolean;
}
export interface BillingDunningHistory {
  step: number;
  sentDate: string;
  recipientDescription: string;
  sendMethodCode: string;
  resend: boolean;
}
export interface BillingDunningRow {
  billId: string;
  projectId: string;
  projectName: string;
  unpaidAmount: number;
  payDueDate: string;
  daysOverdue: number;
  currentStep: number | null;
  history: BillingDunningHistory[];
  nextNotification: string | null;
  canResend: boolean;
  resendMessage: string | null;
  /** 이번 달이 마감 전이라 입금을 등록할 수 있는지(줄마다 상세를 다시 읽지 않는다). */
  canReceive: boolean;
}
export interface BillingDunningResult {
  cards: { step: number; recipients: string[]; offsetDays: number }[];
  items: BillingDunningRow[];
}
export interface BillingReceivable {
  billId: string;
  projectName: string;
  unpaidAmount: number;
  canReceive: boolean;
}
export interface BillingReceiptResult {
  billIds: string[];
  receipts: {
    receiptId: string;
    receiptDate: string;
    amount: number;
    cancelled: boolean;
    cancelReason: string | null;
    cancelledBy: string | null;
    cancelledAt: string | null;
    canCancel: boolean;
    modifiedAt: string;
  }[];
}
export function getCommonBillOptions(
  filter: { projectId?: string; billMonth?: string } = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams();
  if (filter.projectId) query.set("projectId", filter.projectId);
  if (filter.billMonth) query.set("billMonth", filter.billMonth);
  return projectRequest<BillingOptions>(
    `/api/common/bill-options${query.size ? `?${query}` : ""}`,
    { signal },
  );
}
export const getCommonBillDunning = (signal?: AbortSignal) =>
  projectRequest<BillingDunningResult>("/api/common/bill-dunning", { signal });
export const getBillingReceivable = (billId: string, signal?: AbortSignal) =>
  projectRequest<{ bill: BillingReceivable }>(
    `/api/common/bills/${encodeURIComponent(billId)}`,
    { signal },
  );
export const postBillingDunningResend = (billId: string) =>
  projectRequest<{ billId: string; history: BillingDunningHistory }>(
    `/api/common/bills/${encodeURIComponent(billId)}/dunning-resend`,
    { method: "POST" },
  );
export const postBillingReceipts = (body: {
  receiptDate: string;
  items: { billId: string; amount: number }[];
}) =>
  projectRequest<BillingReceiptResult>("/api/common/receipts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

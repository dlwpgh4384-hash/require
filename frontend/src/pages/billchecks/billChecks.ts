import { projectRequest } from "../projects/projects";

export type BillCheckAction =
  | "MISSING"
  | "ADDITIONAL"
  | "CORRECTION"
  | "DETAIL"
  | "NONE";
export interface BillCheckRow {
  projectId: string;
  projectName: string;
  projectType: string;
  judgement: string;
  headcount: number;
  monthlyMm: number;
  referenceAmount: number;
  billedSupplyAmount: number;
  difference: number;
  explanation: string;
  latestBillId: string | null;
  allowedAction: BillCheckAction;
}
export interface BillCheckResult {
  checkMonth: string;
  summary: {
    totalCount: number;
    missingCount: number;
    underCount: number;
    overCount: number;
    matchedCount: number;
  };
  items: BillCheckRow[];
  calculationMethod: string;
  plannedBasis: boolean;
}
export interface BillCheckInput {
  projectId: string;
  checkMonth: string;
  vatRate: number;
  billDate: string;
  payDueDate: string;
  taxInvoiceNo: string | null;
}
export interface BillCheckDetail {
  bill: {
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
  };
  receipts: {
    receiptId: string;
    receiptDate: string;
    amount: number;
    cancelled: boolean;
    cancelReason: string | null;
  }[];
  missingTaxInvoiceNumber: boolean;
}
export interface BillCheckOptions {
  defaultBillDate: string;
  defaultPayDueDate: string;
  vatRate: number;
  projects: { projectId: string; projectName: string; customerName: string }[];
}
const root = "/api/common/bill-checks";
export const getCommonBillChecks = (
  checkMonth?: string,
  signal?: AbortSignal,
) =>
  projectRequest<BillCheckResult>(
    `${root}${checkMonth ? `?${new URLSearchParams({ checkMonth })}` : ""}`,
    { signal },
  );
const command = (action: string, input: BillCheckInput) =>
  projectRequest<BillCheckDetail>(`${root}/${action}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      projectId: input.projectId,
      checkMonth: input.checkMonth,
      vatRate: input.vatRate,
      billDate: input.billDate,
      payDueDate: input.payDueDate,
      taxInvoiceNo: input.taxInvoiceNo,
    }),
  });
export const postCommonBillCheckMissing = (input: BillCheckInput) =>
  command("missing", input);
export const postCommonBillCheckAdditional = (input: BillCheckInput) =>
  command("additional", input);
export const postCommonBillCheckCorrection = (input: BillCheckInput) =>
  command("correction", input);
export const getBillCheckOptions = (billMonth: string, signal?: AbortSignal) =>
  // Check rows can belong to ended projects; only ordinary creation requires an active project.
  projectRequest<BillCheckOptions>(
    `/api/common/bill-options?${new URLSearchParams({ billMonth })}`,
    { signal },
  );
export const getBillCheckDetail = (billId: string, signal?: AbortSignal) =>
  projectRequest<BillCheckDetail>(
    `/api/common/bills/${encodeURIComponent(billId)}`,
    { signal },
  );

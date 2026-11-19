import { projectRequest } from "../projects/projects";
import type {
  BillingReceivable,
  BillingReceiptResult,
} from "../commonops/billingLookups";

export type Receipt = BillingReceiptResult["receipts"][number];
export interface ReceiptBillDetail {
  bill: BillingReceivable & { receiptSumAmount: number; status: string };
  receipts: Receipt[];
  missingTaxInvoiceNumber: boolean;
}
export const getReceiptBillDetail = (billId: string, signal?: AbortSignal) =>
  projectRequest<ReceiptBillDetail>(
    `/api/common/bills/${encodeURIComponent(billId)}`,
    { signal },
  );

export const postCommonReceiptCancel = (
  receiptId: string,
  reason: string,
  modifiedAt: string,
) =>
  projectRequest<BillingReceiptResult>(
    `/api/common/receipts/${encodeURIComponent(receiptId)}/cancel`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason, modifiedAt }),
    },
  );

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
  /** 마지막 수정 시각(없으면 등록 시각). 수정·취소 때 그대로 보낸다(00-1 동시 수정). */
  modifiedAt: string;
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
export interface BillFilter {
  year: number;
  status?: string;
  billMonth?: string;
  projectId?: string;
}
const query = (filter: BillFilter) => {
  const params = new URLSearchParams({ year: String(filter.year) });
  for (const key of ["status", "billMonth", "projectId"] as const)
    if (filter[key]) params.set(key, filter[key]!);
  return params;
};
export const getCommonBills = (filter: BillFilter, signal?: AbortSignal) =>
  projectRequest<BillList>(`/api/common/bills?${query(filter)}`, { signal });
export const getCommonBill = (id: string, signal?: AbortSignal) =>
  projectRequest<BillDetail>(`/api/common/bills/${encodeURIComponent(id)}`, {
    signal,
  });
const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const putCommonBill = (
  id: string,
  body: BillValues & { modifiedAt: string },
) =>
  projectRequest<BillDetail>(`/api/common/bills/${encodeURIComponent(id)}`, {
    method: "PUT",
    ...json(body),
  });
export const postCommonBillCancel = (
  id: string,
  reason: string,
  modifiedAt: string,
) =>
  projectRequest<BillDetail>(
    `/api/common/bills/${encodeURIComponent(id)}/cancel`,
    { method: "POST", ...json({ reason, modifiedAt }) },
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
const XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
/** SFR-011 청구 목록 엑셀: 목록과 같은 조건. 받은 건수를 돌려준다. */
export async function getCommonBillsExcel(filter: BillFilter): Promise<number> {
  const response = await fetch(`/api/common/bills/excel?${query(filter)}`);
  if (!response.ok || !response.headers.get("Content-Type")?.includes(XLSX)) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    try {
      message = (await response.json()).message || message;
    } catch {
      /* 본문이 JSON이 아니면 기본 문구 */
    }
    throw new Error(message);
  }
  const count = response.headers.get("X-Total-Count");
  if (count === null || !/^\d+$/.test(count))
    throw new Error("조회 결과 건수를 확인하지 못했습니다.");
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || "청구·입금.xlsx";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 1000);
  return Number(count);
}
export const billMoney = (value: number) => value.toLocaleString("ko-KR");
/** 시안 kor: 1억 이상 「17.7억 원」, 그 아래 「5,300만 원」. */
export const billShortMoney = (value: number) => {
  const a = Math.abs(value),
    sign = value < 0 ? "−" : "";
  return a >= 1e8
    ? `${sign}${(a / 1e8).toFixed(1)}억 원`
    : `${sign}${Math.round(a / 1e4).toLocaleString("ko-KR")}만 원`;
};
export const billStatus = (value: string) =>
  ({ PAID: "입금 완료", UNPAID: "미수", LATE: "연체", CNCL: "취소됨" })[
    value
  ] ?? value;
/** 상태 배지 색(시안): 입금 완료 ok, 미수 warn, 연체 bad. */
export const billStatusKind = (value: string) =>
  (({ PAID: "ok", UNPAID: "warn", LATE: "bad" }) as const)[
    value as "PAID" | "UNPAID" | "LATE"
  ] ?? "neutral";
/** YYYYMM → 「YYYY-MM」(시안 청구월 표기). */
export const billMonthLabel = (month: string) =>
  month.length === 6 ? `${month.slice(0, 4)}-${month.slice(4)}` : month;
/** YYYYMM의 다음 달 YYYYMM. */
export const nextBillMonth = (month: string) => {
  const y = Number(month.slice(0, 4)),
    m = Number(month.slice(4));
  return m === 12
    ? `${y + 1}01`
    : `${y}${String(m + 1).padStart(2, "0")}`;
};
/**
 * 입금 예정일 기본값(Q-식-10): 청구월 다음 달 25일.
 * 지난 달을 청구해 그 날이 청구일(기본 오늘)보다 빠르면 청구일 다음 달 25일로 한다.
 */
export const defaultPayDue = (month: string, billDate?: string) => {
  const next = nextBillMonth(month);
  const due = `${next.slice(0, 4)}-${next.slice(4)}-25`;
  if (!billDate || !/^\d{4}-\d{2}-\d{2}$/.test(billDate) || due >= billDate)
    return due;
  const after = nextBillMonth(billDate.slice(0, 4) + billDate.slice(5, 7));
  return `${after.slice(0, 4)}-${after.slice(4)}-25`;
};
/** 00-1 공통 동시 수정 문구(서버 409). */
export const CONFLICT_MESSAGE = "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.";
export const isConflict = (failure: unknown) =>
  failure instanceof ProjectRequestError &&
  (failure.message === CONFLICT_MESSAGE ||
    failure.errors.some((item) => item.field === "modifiedAt" && item.message === CONFLICT_MESSAGE));
export const failureMessage = (failure: unknown, fallback: string) =>
  failure instanceof Error && failure.message ? failure.message : fallback;

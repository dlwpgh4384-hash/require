export interface MonthCloseCheck {
  name: string;
  status: string;
  description: string;
  targetScreenId: string | null;
}
export interface MonthClose {
  yearMonth: string;
  isClosed: boolean;
  /** 가장 최근에 마감한 달이라 해제할 수 있는지(서버 판단). 없으면 해제할 수 없다고 본다. */
  reopenable?: boolean;
  modifiedAt: string | null;
  closedAt: string | null;
  closedBy: string | null;
  totalCount: number;
  attentionCount: number;
  checks: MonthCloseCheck[];
}
export interface MonthCloseHistoryItem {
  yearMonth: string;
  action: string;
  processor: string;
  occurredAt: string;
  reason: string | null;
  remainingChecks: string[];
}
export class MonthCloseRequestError extends Error {
  constructor(
    message: string,
    public fields: { field: string; message: string }[],
    public status: number,
  ) {
    super(message);
  }
}
/** 409(동시 수정): 다른 사람이 먼저 마감·해제했다. */
export const isMonthCloseConflict = (failure: unknown) =>
  failure instanceof MonthCloseRequestError && failure.status === 409;
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { credentials: "same-origin", ...init });
  // 프록시 오류 화면처럼 JSON이 아닌 응답도 공통 문구로 보인다.
  const body = await response.json().catch(() => null);
  if (!response.ok || !body || body.success === false)
    throw new MonthCloseRequestError(
      body?.message || "월 마감을 처리하지 못했습니다.",
      body?.errors ?? [],
      response.status,
    );
  return body.data;
}
const path = (month: string) =>
  `/api/common/month-closes/${encodeURIComponent(month)}`;
export const getCommonMonthClose = (month: string, signal?: AbortSignal) =>
  request<MonthClose>(path(month), { signal });
export const getCommonMonthCloseReopenImpact = (
  month: string,
  signal?: AbortSignal,
) =>
  request<{ yearMonth: string; previousAdjustmentCount: number }>(
    `${path(month)}/reopen-impact`,
    { signal },
  );
const write = (month: string, action: string, body: unknown) =>
  request<MonthClose>(`${path(month)}/${action}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
export const putCommonMonthCloseConfirm = (
  month: string,
  modifiedAt: string | null,
) => write(month, "confirm", { modifiedAt });
export const putCommonMonthCloseReopen = (
  month: string,
  modifiedAt: string | null,
  reason: string,
) => write(month, "reopen", { modifiedAt, reason });
export const getCommonMonthCloseHistory = (
  month: string,
  signal?: AbortSignal,
) =>
  request<{ items: MonthCloseHistoryItem[] }>(
    `/api/common/month-close-history?${new URLSearchParams({ yearMonth: month })}`,
    { signal },
  );
export const canReopenMonthClose = (roles: readonly string[]) =>
  roles.includes("ADMIN");

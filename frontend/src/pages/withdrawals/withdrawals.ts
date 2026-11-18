import { AssignmentRequestError } from "../assignments/assignments";

export interface WithdrawalRow {
  assignmentId: string;
  developerId: string;
  name: string;
  projectName: string;
  jobName: string;
  endDate: string;
  remainingDays: number;
  withdrawalCompleted: boolean;
  extensionCount: number;
  earlyWithdrawalHistory: string[];
  extensionPossibilityCode: string;
  extensionPossibilityName: string;
  nextAssignment: {
    opportunityName: string | null;
    recommendationCount: number;
    hasTentativeAssignment: boolean;
  };
  modifiedAt: string | null;
}
export interface WithdrawalFilter {
  year: number;
  period?: string;
  extensionPossibilityCode?: string;
  jobCode?: string;
}
export interface WithdrawalList {
  items: WithdrawalRow[];
  totalCount: number;
  banner: { unassignedCount: number; matchedCount: number } | null;
}
export interface WithdrawalRecommendation {
  needSn: number;
  opportunityId: string;
  opportunityName: string;
  jobName: string;
  gradeName: string;
  startMonth: string;
  score: number;
  reasons: string[];
  waitingDays: number;
  waitingLabel: string;
}
export interface WithdrawalRecommendationsData {
  items: WithdrawalRecommendation[];
  shortages: { month: string; jobName: string; shortageCount: number }[];
}
export interface WithdrawalTentative {
  assignmentId: string;
  developerId: string;
  opportunityId: string;
  opportunityName: string;
  needSn: number;
  startMonth: string;
}
export interface WithdrawalSharedRow {
  assignmentId: string;
  developerId: string;
  name: string;
  extensionPossibilityCode: string;
  extensionPossibilityName: string;
  tentativeAssignment: WithdrawalTentative | null;
}
const root = "/api/common/withdrawals";
const query = (filter: object) => {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  return params.toString();
};
async function withdrawalRequest<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new AssignmentRequestError(
      body.message || "요청을 처리하지 못했습니다.",
      body.errors || [],
      response.status,
    );
  return body.data as T;
}
const put = <T>(path: string, body: unknown) =>
  withdrawalRequest<T>(path, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
export const getCommonWithdrawals = (
  filter: WithdrawalFilter,
  signal?: AbortSignal,
) => withdrawalRequest<WithdrawalList>(`${root}?${query(filter)}`, { signal });
export const getCommonWithdrawalRecommendations = (
  id: string,
  replaceCurrent = false,
  signal?: AbortSignal,
) =>
  withdrawalRequest<WithdrawalRecommendationsData>(
    `${root}/${encodeURIComponent(id)}/recommendations?replaceCurrent=${replaceCurrent}`,
    { signal },
  );
export const putCommonWithdrawalExtensionPossibility = (
  row: WithdrawalRow,
  code: string,
) =>
  put<{
    assignmentId: string;
    extensionPossibilityCode: string;
    modifiedAt: string | null;
  }>(`${root}/${encodeURIComponent(row.assignmentId)}/extension-possibility`, {
    extensionPossibilityCode: code,
    modifiedAt: row.modifiedAt,
  });
export const getCommonWithdrawalTentativeAssignment = (id: string) =>
  withdrawalRequest<{ assignment: WithdrawalTentative | null }>(
    `${root}/${encodeURIComponent(id)}/tentative-assignment`,
  );
export const putCommonWithdrawalTentativeAssignment = (
  id: string,
  needSn: number,
) =>
  put<{ assignment: WithdrawalTentative }>(
    `${root}/${encodeURIComponent(id)}/tentative-assignment`,
    { needSn },
  );
export const getCommonWithdrawalAssignments = (
  filter: { developerId?: string; opportunityId?: string } = {},
  signal?: AbortSignal,
) =>
  withdrawalRequest<{ items: WithdrawalSharedRow[] }>(
    `${root}/tentative-assignments?${query(filter)}`,
    { signal },
  );
export async function getCommonWithdrawalsExcel(
  filter: WithdrawalFilter,
): Promise<number> {
  const response = await fetch(`${root}/excel?${query(filter)}`);
  if (
    !response.ok ||
    !response.headers
      .get("Content-Type")
      ?.includes(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
  ) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    try {
      message = (await response.json()).message || message;
    } catch {
      /* binary error */
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
    : plain || "철수 리스트.xlsx";
  link.dataset.testid = "withdrawal-download-link";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 1000);
  return Number(count);
}
export const withdrawalRefreshEvent = "withdrawal-source-changed";
export const notifyWithdrawalChanged = () =>
  window.dispatchEvent(new Event(withdrawalRefreshEvent));

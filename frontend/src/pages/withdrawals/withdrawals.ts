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
    /** 식-79 추천 대상(3개월 안·높음 아님·가배정 없음)인지. */
    recommendationTarget?: boolean;
    /** 시안 작은 글자: 연장 가능성 높음·3개월 뒤 철수·맞는 자리 없음. */
    note?: string | null;
  };
  modifiedAt: string | null;
  projectId?: string | null;
  managementNumber?: string | null;
}
export interface WithdrawalFilter {
  year: number;
  period?: string;
  extensionPossibilityCode?: string;
  jobCode?: string;
}
/** 식-82: soonCount(3개월 안 철수), unassignedCount(그중 높음 아님·다음 배정 없음), matchedCount(그중 맞는 자리 있음). */
export interface WithdrawalBanner {
  soonCount?: number;
  unassignedCount: number;
  matchedCount: number;
}
export interface WithdrawalList {
  items: WithdrawalRow[];
  totalCount: number;
  banner: WithdrawalBanner | null;
}
export interface WithdrawalRecommendation {
  needSn: number;
  opportunityId: string;
  opportunityName: string;
  jobName: string;
  gradeName: string;
  /** 자리 시작월(YYYY-MM). */
  startMonth: string;
  score: number;
  reasons: string[];
  waitingDays: number;
  waitingLabel: string;
  remainingCount?: number;
  customerName?: string | null;
  stageName?: string | null;
  /** 이 사람이 이미 이 기회에 가배정되어 있음(시안 [가배정됨]). */
  tentative?: boolean;
}
export interface WithdrawalRecommendationTarget {
  assignmentId: string;
  name: string;
  jobName: string;
  gradeName: string | null;
  projectName: string;
  endDate: string;
  skills: string[];
  extensionPossibilityName: string;
  waitingStartDate: string;
  tentativeOpportunityId: string | null;
  tentativeOpportunityName: string | null;
  tentativeStartMonth: string | null;
}
export interface WithdrawalRecommendationsData {
  items: WithdrawalRecommendation[];
  shortages: { month: string; jobName: string; shortageCount: number }[];
  target?: WithdrawalRecommendationTarget | null;
}
export interface WithdrawalTentative {
  assignmentId: string;
  developerId: string;
  opportunityId: string;
  opportunityName: string;
  needSn: number;
  startMonth: string;
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
/** 필요 인력 줄의 키는 (기회 ID, NEED_SN)이라 둘 다 보낸다. */
export const putCommonWithdrawalTentativeAssignment = (
  id: string,
  opportunityId: string,
  needSn: number,
) =>
  put<{ assignment: WithdrawalTentative }>(
    `${root}/${encodeURIComponent(id)}/tentative-assignment`,
    { opportunityId, needSn },
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

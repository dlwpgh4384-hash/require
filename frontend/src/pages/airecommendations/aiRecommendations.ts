import { AssignmentRequestError } from "../assignments/assignments";

export type AiTargetType = "project" | "opportunity";
export interface AiNeed {
  needSn: number;
  jobCode: string;
  jobName: string;
  requiredCount: number;
  startMonth: string;
}
export interface AiTarget {
  targetType: AiTargetType;
  targetId: string;
  targetName: string;
  startMonth: string;
  endMonth: string | null;
  needs: AiNeed[];
}
export interface AiOptions {
  targets: AiTarget[];
  jobs: { code: string; name: string }[];
  grades: { code: string; name: string }[];
  exclusionReasons: { code: string; name: string }[];
}
export interface AiCondition {
  targetType: AiTargetType;
  targetId: string;
  jobCode: string;
  startMonth: string;
  gradeCode: string;
  needSn?: number;
}
export interface AiInput extends AiCondition {
  requestId: string;
}
export interface AiScore {
  name: string;
  score: number;
  maximumScore: number;
}
export interface AiCard {
  developerId: string;
  developerName: string;
  managementNumber: string;
  jobName: string;
  gradeName: string;
  employeeTypeName: string;
  totalScore: number;
  scores: Record<string, AiScore>;
  reason: string;
  otherTentativeOpportunityName: string | null;
  assignmentCandidate: boolean;
}
export interface AiResult {
  recommendationId: string;
  condition: AiCondition;
  items: AiCard[];
}
export interface AiTentativeResult {
  assignment: {
    assignmentId: string;
    developerId: string;
    opportunityId: string;
    opportunityName: string;
    needSn: number;
    startMonth: string;
  };
  releasedOpportunityName: string | null;
  overCapacityCount: number;
  message: string;
}

/** 시안 카드 점수 줄 이름(순서도 시안 그대로). 서버가 다른 이름을 보내도 화면은 이 이름을 쓴다. */
export const AI_SCORE_LABELS: readonly [string, string][] = [
  ["job", "직무"],
  ["skill", "스킬"],
  ["customerIndustry", "고객·업종"],
  ["grade", "등급"],
  ["performance", "성과"],
  ["cost", "비용"],
  ["availability", "가용일"],
  ["workplace", "근무지"],
];

/** 시안 대상 목록: 영업 기회 먼저, 그다음 프로젝트(각각 받은 순서). */
export const orderedTargets = (targets: readonly AiTarget[]) => [
  ...targets.filter((t) => t.targetType === "opportunity"),
  ...targets.filter((t) => t.targetType !== "opportunity"),
];
export const targetLabel = (t: AiTarget) =>
  `${t.targetType === "opportunity" ? "[기회]" : "[프로젝트]"} ${t.targetName}`;
/** 가배정 자리(필요 인력 줄)는 NEED_SN 순서로 1번부터 센다. */
export const orderedNeeds = (needs: readonly AiNeed[]) =>
  [...needs].sort((a, b) => a.needSn - b.needSn);

/**
 * 시안 .why 문장. 서버가 늘 시안 문장(주 직무 → 같은 고객사 → 투입 가능 → 보유 스킬)을 보내므로
 * 화면은 그 글을 그대로 쓰고, 끝에 마침표만 맞춘다(화면에서 문장을 따로 만들지 않음).
 */
export function aiReasonText(card: AiCard) {
  const own = card.reason?.trim() ?? "";
  return !own || /[.!?]$/.test(own) ? own : `${own}.`;
}

const FAILURE = "요청을 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
async function request<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const response = await fetch(path, {
    method,
    ...(body === undefined
      ? {}
      : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
  });
  // 서버가 JSON이 아닌 응답(예: 프록시 오류 화면)을 주면 영어·HTML 대신 우리말로 알린다.
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.success)
    throw new AssignmentRequestError(
      payload?.message || FAILURE,
      payload?.errors ?? [],
      response.status,
    );
  return payload.data as T;
}
export function getCommonAiRecommendationOptions(
  target?: Pick<AiCondition, "targetType" | "targetId">,
) {
  const query = target
    ? `?${new URLSearchParams({ targetType: target.targetType, targetId: target.targetId }).toString()}`
    : "";
  return request<AiOptions>(`/api/common/ai-recommendation-options${query}`);
}
export const postCommonAiRecommendation = (input: AiInput) =>
  request<AiResult>("/api/common/ai-recommendations", "POST", input);
const actionPath = (id: string, action: string) =>
  `/api/common/ai-recommendations/${encodeURIComponent(id)}/${action}`;
export const postCommonAiRecommendationAssignment = (
  id: string,
  input: { developerId: string; assignmentId: string },
) =>
  request<{ userActCode: "ASGN" }>(actionPath(id, "assignment"), "POST", input);
export const postCommonAiRecommendationExclusion = (
  id: string,
  input: { developerId: string; reasonCode: string },
) => request<{ excluded: true }>(actionPath(id, "exclusions"), "POST", input);
export const putCommonAiRecommendationTentativeAssignment = (
  id: string,
  input: { developerId: string; needSn: number },
) =>
  request<AiTentativeResult>(
    actionPath(id, "tentative-assignment"),
    "PUT",
    input,
  );

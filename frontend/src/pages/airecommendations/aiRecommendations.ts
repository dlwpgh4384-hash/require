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
  costBasis?: { cost: number | null; price: number | null } | null;
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
  const payload = await response.json();
  if (!response.ok || !payload.success)
    throw new AssignmentRequestError(
      payload.message ?? "요청을 처리하지 못했습니다.",
      payload.errors ?? [],
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

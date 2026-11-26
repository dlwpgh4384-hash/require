import { useCallback } from "react";
import { createScreenRequest } from "../../shared/layout/screenRequest";
import type { AiTargetType } from "./aiRecommendations";

export interface AiRecommendationRequest {
  targetType?: AiTargetType;
  targetId?: string;
  jobCode?: string;
  startMonth?: string;
}
export interface AiRecommendationEvent extends AiRecommendationRequest {
  requestId: string;
}
const channel = createScreenRequest<AiRecommendationEvent>("UI-30");
/**
 * 추천 요청 번호. crypto.randomUUID()는 보안 페이지(https·localhost)에서만 있어
 * http://IP 주소로 열면 없다 → getRandomValues로 같은 모양(UUID v4)을 만들고, 그것도 없으면 시각+난수.
 */
export { newAiRequestId } from "../../shared/layout/aiRequestId";
import { newAiRequestId } from "../../shared/layout/aiRequestId";
export const requestAiRecommendation = (request: AiRecommendationRequest) =>
  channel.request({ ...request, requestId: newAiRequestId() });
export const subscribeAiRecommendation = channel.subscribe;
export function useOpenAiRecommendation() {
  const open = channel.useOpen();
  return useCallback(
    (request: AiRecommendationRequest) =>
      open({ ...request, requestId: newAiRequestId() }),
    [open],
  );
}

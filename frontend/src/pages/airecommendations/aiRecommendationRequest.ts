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
export const newAiRequestId = () => crypto.randomUUID();
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

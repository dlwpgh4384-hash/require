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
export function newAiRequestId(): string {
  const c = globalThis.crypto as Crypto | undefined;
  if (typeof c?.randomUUID === "function") return c.randomUUID();
  if (typeof c?.getRandomValues === "function") {
    const b = c.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const h = [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}
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

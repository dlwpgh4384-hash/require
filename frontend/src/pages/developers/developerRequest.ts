import { createScreenRequest } from "../../shared/layout/screenRequest";
import type { Developer } from "./developers";

/**
 * 시안 openPerson: 다른 화면(개인 원가·수익표 UI-03 등)에서 연 개발자 상세의 [수정]·[사용 중지]는
 * 상세를 닫고 개발자 풀(UI-02)로 가서 그 화면의 수정 폼·사용 중지 확인을 그대로 쓴다(코드 복사 없음).
 */
export type DeveloperRequest = { developer: Developer; action: "edit" | "stop" };

const developerRequest = createScreenRequest<DeveloperRequest>("UI-02");

export const requestDeveloperAction = developerRequest.request;
/** 개발자 풀 화면이 쓴다: 남아 있는 요청을 먼저 받고, 이후 요청을 기다린다. */
export const subscribeDeveloperAction = developerRequest.subscribe;
/** 다른 화면이 쓴다: 요청을 남기고 개발자 풀 탭으로 간다. */
export const useOpenDeveloperAction = developerRequest.useOpen;

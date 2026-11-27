import { createScreenRequest } from "../../shared/layout/screenRequest";

/**
 * 다른 화면에서 프로젝트 원가 분석(UI-04)으로 가서 그 프로젝트 상세를 연다
 * (시안 프로젝트 상세 [원가 분석] → costdet). 값은 프로젝트 ID.
 */
const channel = createScreenRequest<string>("UI-04");
export const useOpenProjectCostAnalysis = channel.useOpen;
export const subscribeProjectCostAnalysis = channel.subscribe;

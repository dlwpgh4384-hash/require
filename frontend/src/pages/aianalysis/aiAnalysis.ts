import { AssignmentRequestError } from "../assignments/assignments";
import { downloadFile } from "../../shared/list/download";
import type { ScreenPeriod } from "../../shared/layout/screenPeriodChannel";
export type AnalysisPeriod = ScreenPeriod;
export interface AnalysisInput {
  requestId: string;
  question: string;
  screenId: string;
  period?: AnalysisPeriod;
}
export interface AnalysisAction {
  label: string;
  targetScreenId: string;
  period: AnalysisPeriod;
}
export interface AnalysisAnswer {
  questionId: string;
  answerStatus: "OK" | "NO_ANS" | "ERR";
  conclusion: string;
  table?: {
    columns: { key: string; label: string }[];
    rows: (string | number | null)[][];
  };
  assumptions: string[];
  usedData: string;
  baseMonth: string;
  provisional: boolean;
  actions: AnalysisAction[];
  suggestedQuestions: string[];
}
export interface AnalysisHistoryItem {
  questionId: string;
  question: string;
  askedAt: string;
  answerStatus: AnalysisAnswer["answerStatus"];
}
export interface AnalysisProgress {
  requestId: string;
  stage: "데이터 찾는 중" | "계산 중" | "정리 중";
  completed: boolean;
}
async function request<T>(path: string, input?: AnalysisInput): Promise<T> {
  const response = await fetch(
    path,
    input
      ? {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(input),
        }
      : undefined,
  );
  const body = await response.json().catch(() => null);
  if (!response.ok || body?.success !== true)
    throw new AssignmentRequestError(
      body?.message || "요청을 처리하지 못했습니다.",
      body?.errors ?? [],
      response.status,
    );
  return body.data as T;
}
export const getCommonAiAnalysisQuestions = () =>
  request<{ items: AnalysisHistoryItem[] }>(
    "/api/common/ai-analysis/questions",
  );
export const postCommonAiAnalysisQuestion = (input: AnalysisInput) =>
  request<AnalysisAnswer>("/api/common/ai-analysis/questions", input);
export const getCommonAiAnalysisProgress = (id: string) =>
  request<AnalysisProgress>(
    `/api/common/ai-analysis/requests/${encodeURIComponent(id)}`,
  );
export async function getCommonAiAnalysisQuestionExcel(id: string) {
  return downloadFile(
    `/api/common/ai-analysis/questions/${encodeURIComponent(id)}/excel`,
    "AI분석.xlsx",
  );
}
export const ANALYSIS_SUGGESTIONS = [
  "다음 분기 대기 인력",
  "이번 달 적자 인력",
  "이번 달 매출·영업이익",
] as const;

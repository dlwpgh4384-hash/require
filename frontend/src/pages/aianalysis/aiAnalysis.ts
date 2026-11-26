import { AssignmentRequestError } from "../assignments/assignments";
import { downloadFile } from "../../shared/list/download";
import { formatMoney } from "../../shared/list/moneyUnit";
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
export type AnalysisFormat =
  | "text"
  | "money"
  | "rate"
  | "mm"
  | "count"
  | "month"
  | "date";
export interface AnalysisColumn {
  key: string;
  label: string;
  align?: "left" | "right";
  format?: AnalysisFormat;
}
export type AnalysisCell = string | number | null;
export type AnalysisTone = null | "bad" | "warn";
export interface AnalysisTable {
  title?: string;
  columns: AnalysisColumn[];
  rows: AnalysisCell[][];
  tones?: AnalysisTone[][];
  totalRow?: AnalysisCell[];
  /** true면 화면에 그리지 않는다(엑셀에만 씀, 시안 매출 답). */
  hidden?: boolean;
}
export interface AnalysisChart {
  type: "line";
  labels: string[];
  series: { name: string; values: (number | null)[] }[];
  /** 값 모양: money(원, 눈금 백만 원)·rate(%). 없으면 money. */
  format?: "money" | "rate";
}
/** 답 모양. 서버가 빠뜨린 칸이 있어도 화면은 빈 값으로 그린다. */
export interface AnalysisAnswer {
  questionId: string;
  answerStatus: "OK" | "NO_ANS" | "ERR";
  conclusion: string;
  forecast?: boolean;
  provisional?: boolean;
  provisionalMonth?: string | null;
  tables?: AnalysisTable[];
  chart?: AnalysisChart | null;
  assumptions?: string[];
  usedData?: string;
  baseMonth?: string;
  actions?: AnalysisAction[];
  excel?: boolean;
  suggestedQuestions?: string[];
  /** 추천 질문 단추 글(suggestedQuestions와 같은 순서). 없으면 질문 글 그대로. */
  suggestionLabels?: string[];
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
/** 시안 EXQ: 추천 질문 3개(글 그대로). */
export const ANALYSIS_SUGGESTIONS = [
  "다음 분기 대기 인력은?",
  "이번 달 적자 인력은?",
  "이번 달 매출과 영업이익은?",
] as const;
/** 질문 기록은 최근 20개까지 보인다. */
export const ANALYSIS_HISTORY_LIMIT = 20;
const NUMERIC_FORMATS = new Set<AnalysisFormat>(["money", "rate", "mm", "count"]);
const toNumber = (value: AnalysisCell): number | null => {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value === "string" && value.trim() !== "") {
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }
  return null;
};
const fixed = (value: number, digits: number) => {
  const text = Math.abs(value).toLocaleString("ko-KR", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
  return (value < 0 && Number(text.replace(/,/g, "")) !== 0 ? "−" : "") + text;
};
/**
 * 칸 모양: 금액은 시안 won()처럼 원 단위 천 단위 쉼표(「1,234,567」, 음수 「−」), 비율 소수 1자리 %,
 * MM 소수 2자리, 인원·건수는 정수. 「x.x억 원」 줄임은 결론 글에만 쓴다.
 */
export function formatAnalysisCell(
  value: AnalysisCell,
  format: AnalysisFormat = "text",
): string {
  if (value == null || value === "") return "–";
  if (!NUMERIC_FORMATS.has(format)) return String(value);
  const n = toNumber(value);
  if (n === null) return String(value);
  switch (format) {
    case "money":
      return formatMoney(n, "원");
    case "rate":
      return `${fixed(n, 1)}%`;
    case "mm":
      return fixed(n, 2);
    default:
      return fixed(Math.round(n), 0);
  }
}
/** 글자 칸은 왼쪽, 숫자 칸은 오른쪽(서버가 align을 주면 그대로). */
export const isRightAligned = (column: AnalysisColumn) =>
  column.align
    ? column.align === "right"
    : NUMERIC_FORMATS.has(column.format ?? "text");
/** 결론 글의 **굵게**만 굵게, 나머지는 글자 그대로(HTML로 해석하지 않음). */
export function splitBold(text: string): { text: string; bold: boolean }[] {
  const parts: { text: string; bold: boolean }[] = [];
  const pattern = /\*\*([\s\S]+?)\*\*/g;
  let last = 0;
  for (const match of text.matchAll(pattern)) {
    const at = match.index ?? 0;
    if (at > last) parts.push({ text: text.slice(last, at), bold: false });
    parts.push({ text: match[1], bold: true });
    last = at + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), bold: false });
  return parts;
}

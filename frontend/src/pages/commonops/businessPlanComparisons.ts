import { downloadFile } from "../../shared/list/download";
import { ProjectRequestError } from "../projects/projects";

export type ComparisonAmount = number | null;
export interface BusinessPlanComparisonMonth {
  yearMonth: string;
  revenueTarget: number;
  revenueActual: ComparisonAmount;
  operatingProfitTarget: number;
  operatingProfitActual: ComparisonAmount;
  achievementRate: ComparisonAmount;
  profitRateDifference: ComparisonAmount;
}
export interface BusinessPlanComparisonResult {
  year: number;
  baseMonth: string;
  baseMonthClosed: boolean;
  hasTargets: boolean;
  summary: {
    cumulativeRevenueAchievementRate: ComparisonAmount;
    cumulativeRevenueActual: ComparisonAmount;
    cumulativeRevenueTarget: ComparisonAmount;
    cumulativeOperatingProfitAchievementRate: ComparisonAmount;
    cumulativeOperatingProfitActual: ComparisonAmount;
    cumulativeOperatingProfitTarget: ComparisonAmount;
    baseMonthRevenueAchievementRate: ComparisonAmount;
    baseMonthRevenueTarget: ComparisonAmount;
  };
  months: BusinessPlanComparisonMonth[];
}
export interface BusinessPlanComparisonFilter {
  year: number;
  baseMonth: string;
}
export class BusinessPlanComparisonRequestError extends ProjectRequestError {
  constructor(
    message: string,
    errors: { field: string; message: string }[],
    public status: number,
  ) {
    super(message, errors);
  }
}
export async function getCommonBusinessPlanComparisons(
  filter: BusinessPlanComparisonFilter,
  signal?: AbortSignal,
): Promise<{ data: BusinessPlanComparisonResult }> {
  const query = new URLSearchParams({
    year: String(filter.year),
    baseMonth: filter.baseMonth,
  });
  const response = await fetch(
    `/api/common/business-plan-comparisons?${query}`,
    { signal },
  );
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new BusinessPlanComparisonRequestError(
      body.message || "사업계획 대비 실적을 불러오지 못했습니다.",
      body.errors || [],
      response.status,
    );
  return body;
}
/** 서버 B.10 검사 경고 이유(BusinessPlanUploadForm)와 같은 글. */
export const EMPTY_TARGET_REASON =
  "목표가 빈 달 — 오류 없이 0(목표 없음)으로 올립니다.";
export const CLOSED_CHANGED_REASON = "마감한 달 목표가 지금 목표와 다릅니다.";
const monthNumber = (yearMonth: string) =>
  Number(yearMonth.replace("-", "").slice(4));
/** 「1월~6월」처럼 이어진 달은 범위로, 아니면 「1월, 3월」. */
export function monthSpan(yearMonths: string[]) {
  const months = [...new Set(yearMonths.map(monthNumber))].sort(
    (a, b) => a - b,
  );
  if (!months.length) return "";
  const contiguous = months.every((m, i) => m === months[0] + i);
  return contiguous && months.length > 1
    ? `${months[0]}월~${months[months.length - 1]}월`
    : months.map((m) => `${m}월`).join(", ");
}
/** 목표 업로드 결과 창 문구(시안 planUpDone). closed: 그해 마감한 달(YYYYMM). */
export function planUploadMessage(
  policy: "keep" | "replace" | undefined,
  closed: string[],
) {
  if (policy === "replace")
    return "1~12월 목표를 새 파일대로 바꿨습니다. 마감한 달 달성률도 새 목표로 계산합니다.";
  if (policy === "keep") {
    const months = [...new Set(closed.map(monthNumber))].sort((a, b) => a - b);
    const last = months[months.length - 1];
    if (months.length && months.every((m, i) => m === i + 1))
      return last >= 12
        ? "마감 안 된 달이 없어 목표를 바꾸지 않았습니다."
        : `${last + 1}~12월 목표만 새 파일대로 바꿨습니다. 마감한 1~${last}월은 그대로 두었습니다.`;
    return `마감 안 된 달 목표만 새 파일대로 바꿨습니다. 마감한 ${monthSpan(closed)}은 그대로 두었습니다.`;
  }
  return "목표를 새 파일대로 올렸습니다. 실적은 그대로입니다.";
}
export function downloadBusinessPlanTemplate(year: number) {
  return downloadFile(
    `/api/common/uploads/templates/B10?${new URLSearchParams({ year: String(year) })}`,
    "사업계획-목표.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}

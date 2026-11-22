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
export function downloadBusinessPlanTemplate(year: number) {
  return downloadFile(
    `/api/common/uploads/templates/B10?${new URLSearchParams({ year: String(year) })}`,
    "사업계획-목표.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}

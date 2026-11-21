import { ProjectRequestError } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";

export type Amount = number | null;
export type PeriodUnit = "month" | "quarter" | "year-to-date";
export interface ProfitStructureFilter {
  year: number;
  periodUnit: PeriodUnit;
  baseMonth: string;
}
export interface ProfitSummary {
  revenue: Amount;
  grossProfit: Amount;
  grossProfitRate: Amount;
  operatingProfit: Amount;
  operatingProfitRate: Amount;
  idleCostShare: Amount;
}
export interface ProfitWaterfall {
  revenue: Amount;
  costOfSales: Amount;
  grossProfit: Amount;
  sellingAdministrativeExpense: Amount;
  previousAdjustment: Amount;
  operatingProfit: Amount;
  nonOperatingProfitLoss: Amount;
  monthlyCorporateTax: Amount;
  netProfit: Amount;
}
export interface ProfitTrend {
  period: string;
  revenue: Amount;
  costOfSales: Amount;
  grossProfit: Amount;
  operatingProfit: Amount;
  allocatedCost: Amount;
  idleCost: Amount;
  directExpense: Amount;
  referenceRevenue: Amount;
}
export interface ProfitCompositionRow {
  name: string;
  revenue: Amount;
  share: Amount;
  profitRate: Amount;
}
export interface ProfitComposition {
  contractTypes: ProfitCompositionRow[];
  topClients: ProfitCompositionRow[];
  projectSalesParts: ProfitCompositionRow[];
  salesPartNotice: string;
}
export interface ProfitPeriodRow {
  period: string;
  summary: ProfitSummary;
  waterfall: ProfitWaterfall;
  trend: ProfitTrend;
  composition: ProfitComposition | null;
  provisional: boolean;
}
export interface ProfitStructureResult {
  periodStart: string;
  periodEnd: string;
  summary: ProfitSummary;
  comparison: {
    previousPeriod: ProfitSummary;
    previousYear: ProfitSummary | null;
    previousYearVisible: boolean;
    shareDifferenceUnit: "%p";
  };
  waterfall: ProfitWaterfall;
  trends: ProfitTrend[];
  composition: ProfitComposition | null;
  compositionVisible: boolean;
  referenceRevenueVisible: boolean;
  provisional: boolean;
  provisionalNotice: string | null;
  numericRows: ProfitPeriodRow[];
}
export class ProfitStructureRequestError extends ProjectRequestError {
  constructor(
    message: string,
    errors: { field: string; message: string }[],
    public status: number,
  ) {
    super(message, errors);
  }
}
const root = "/api/common/profit-structures";
const query = (filter: ProfitStructureFilter) =>
  new URLSearchParams({
    year: String(filter.year),
    periodUnit: filter.periodUnit,
    baseMonth: filter.baseMonth,
  });
async function failure(response: Response): Promise<never> {
  const body = await response.json();
  throw new ProfitStructureRequestError(
    body.message || "전체 수익 구조를 불러오지 못했습니다.",
    body.errors || [],
    response.status,
  );
}
export async function getCommonProfitStructures(
  filter: ProfitStructureFilter,
  signal?: AbortSignal,
): Promise<{
  data: ProfitStructureResult;
  meta: { previousPeriodAvailable?: boolean };
}> {
  const response = await fetch(`${root}?${query(filter)}`, { signal });
  if (!response.ok) return failure(response);
  const body = await response.json();
  if (body.success !== true)
    throw new ProfitStructureRequestError(
      body.message || "전체 수익 구조를 불러오지 못했습니다.",
      body.errors || [],
      response.status,
    );
  return body;
}
export async function getCommonProfitStructuresExcel(
  filter: ProfitStructureFilter,
  columns: string[],
) {
  const params = query(filter);
  params.set("columns", columns.join(","));
  return downloadFile(
    `${root}/excel?${params}`,
    "전체 수익 구조.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}

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
/** 시안 「숫자로 보기」 열: 기간 이름과 잠정(마감 전) 여부. 마지막 열이 고른 기간이다. */
export interface ProfitNumericColumn {
  label: string;
  provisional: boolean;
}
/** 시안 rowsDef 한 줄: bold=굵게(tr.b), indent=들여쓰기(ind), group=「매출 구성 · …」 머리 줄. */
export interface ProfitNumericLine {
  label: string;
  style: "bold" | "indent" | "group" | "plain";
  format: "money" | "percent";
  values: Amount[];
}
export interface ProfitNumericTable {
  columns: ProfitNumericColumn[];
  rows: ProfitNumericLine[];
}
export interface ProfitStructureResult {
  periodStart: string;
  periodEnd: string;
  /** 시안 perName: 「2026-11」, 「2026년 4분기(11월까지)」, 「2026년 1~11월 누계」 */
  periodName?: string;
  summary: ProfitSummary;
  comparison: {
    /** 연 누계는 null(전년 같은 기간만 비교) */
    previousPeriod: ProfitSummary | null;
    /** 「전월」·「전 분기」·「전 분기 같은 기간」, 연 누계는 null */
    previousPeriodLabel?: string | null;
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
  numericTable?: ProfitNumericTable | null;
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
/** 「숫자로 보기」 표와 같은 엑셀(열 고르기 없음). */
export async function getCommonProfitStructuresExcel(
  filter: ProfitStructureFilter,
) {
  return downloadFile(
    `${root}/excel?${query(filter)}`,
    "전체 수익 구조.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}

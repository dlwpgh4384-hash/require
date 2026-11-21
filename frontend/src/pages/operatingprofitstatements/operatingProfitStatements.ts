import { downloadFile } from "../../shared/list/download";
import { ProjectRequestError } from "../projects/projects";
import type { MoneyUnit } from "../../shared/list";

export type Amount = number | null;
export interface OperatingMetric {
  amount: Amount;
  previousYearChangePercent: Amount;
  previousMonthChangePercent: Amount;
}
export interface OperatingCell {
  value: Amount;
  format: "money" | "percent" | "percentage-point";
}
export interface OperatingColumn {
  label: string;
  provisional: boolean;
  working: boolean;
}
export interface OperatingLine {
  label: string;
  style: "bold" | "indent" | "group" | "plain";
  isCost: boolean;
  values: OperatingCell[];
}
export interface OperatingResult {
  year: number;
  baseMonth: string;
  provisional: boolean;
  provisionalNotice: string | null;
  summary: {
    cumulativeRevenue: OperatingMetric;
    cumulativeGrossProfit: OperatingMetric;
    cumulativeOperatingProfit: OperatingMetric;
    operatingProfitRate: Amount;
    baseMonthOperatingProfit: OperatingMetric;
  };
  numericTable: { columns: OperatingColumn[]; rows: OperatingLine[] };
}
export interface OperatingFilter {
  year: number;
  baseMonth: string;
}
export class OperatingProfitStatementRequestError extends ProjectRequestError {
  constructor(
    message: string,
    errors: { field: string; message: string }[],
    public status: number,
  ) {
    super(message, errors);
  }
}
const root = "/api/common/operating-profit-statements";
const query = (filter: OperatingFilter) =>
  new URLSearchParams({
    year: String(filter.year),
    baseMonth: filter.baseMonth,
  });
export async function getCommonOperatingProfitStatements(
  filter: OperatingFilter,
  signal?: AbortSignal,
): Promise<{ data: OperatingResult }> {
  const response = await fetch(`${root}?${query(filter)}`, { signal });
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new OperatingProfitStatementRequestError(
      body.message || "영업이익표를 불러오지 못했습니다. 다시 시도해 주세요.",
      body.errors || [],
      response.status,
    );
  return body;
}
export function getCommonOperatingProfitStatementsExcel(
  filter: OperatingFilter,
  amountUnit: MoneyUnit,
) {
  const params = query(filter);
  params.set("amountUnit", amountUnit);
  return downloadFile(
    `${root}/excel?${params}`,
    "영업이익표.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}

import type { MoneyUnit } from "../../shared/list";
import { downloadFile } from "../../shared/list/download";
import { ProjectRequestError } from "../projects/projects";

export type CashFlowAmount = number | null;
export interface CashFlowMonth {
  yearMonth: string;
  closed: boolean;
  openingBalance: CashFlowAmount;
  closingBalance: CashFlowAmount;
  cashSales: CashFlowAmount;
  miscIncome: CashFlowAmount;
  loan: CashFlowAmount;
  incomeTotal: CashFlowAmount;
  regularPayroll: CashFlowAmount;
  contractFreelancePayroll: CashFlowAmount;
  outsourcing: CashFlowAmount;
  unpaidPayroll: CashFlowAmount;
  overhead: CashFlowAmount;
  corporateTaxPayment: CashFlowAmount;
  expenseTotal: CashFlowAmount;
}
export interface CashFlowStatementResult {
  year: number;
  minimumCash: number;
  months: CashFlowMonth[];
  warnings: {
    yearMonth: string;
    expectedBalance: number;
    minimumCash: number;
  }[];
  numericTable: {
    columns: { label: string; actual: boolean | null; warning: boolean }[];
    rows: {
      label: string;
      style: "bold" | "group" | "indent" | "plain";
      values: CashFlowAmount[];
    }[];
  };
}
export class CashFlowStatementRequestError extends ProjectRequestError {
  constructor(
    message: string,
    errors: { field: string; message: string }[],
    public status: number,
  ) {
    super(message, errors);
  }
}
const root = "/api/common/cash-flow-statements";
export async function getCommonCashFlowStatements(
  filter: { year: number },
  signal?: AbortSignal,
): Promise<{ data: CashFlowStatementResult }> {
  const response = await fetch(
    `${root}?${new URLSearchParams({ year: String(filter.year) })}`,
    { signal },
  );
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new CashFlowStatementRequestError(
      body.message || "자금수지표를 불러오지 못했습니다.",
      body.errors || [],
      response.status,
    );
  return body;
}
export function getCommonCashFlowStatementsExcel(
  filter: { year: number },
  amountUnit: MoneyUnit,
) {
  return downloadFile(
    `${root}/excel?${new URLSearchParams({ year: String(filter.year), amountUnit })}`,
    "자금수지표.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}

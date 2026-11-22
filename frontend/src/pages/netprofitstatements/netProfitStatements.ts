import type { MoneyUnit } from "../../shared/list";
import { downloadFile } from "../../shared/list/download";
import { ProjectRequestError } from "../projects/projects";
import type {
  OperatingFilter,
  OperatingResult,
} from "../operatingprofitstatements/operatingProfitStatements";

export interface NetProfitStatementResult {
  year: number;
  baseMonth: string;
  provisional: boolean;
  provisionalNotice: string | null;
  summary: {
    cumulativeNetProfit: number | null;
    netProfitRate: number | null;
    baseMonthNetProfit: number | null;
    previousMonthChangePercent: number | null;
  };
  numericTable: OperatingResult["numericTable"];
  contributionVisible: boolean;
  contributions: {
    contractTypeCode: string;
    contractTypeName: string;
    amount: number | null;
    sharePercent: number | null;
  }[];
}
export class NetProfitStatementRequestError extends ProjectRequestError {
  constructor(
    message: string,
    errors: { field: string; message: string }[],
    public status: number,
  ) {
    super(message, errors);
  }
}
const root = "/api/common/net-profit-statements";
const query = (filter: OperatingFilter) =>
  new URLSearchParams({
    year: String(filter.year),
    baseMonth: filter.baseMonth,
  });
export async function getCommonNetProfitStatements(
  filter: OperatingFilter,
  signal?: AbortSignal,
): Promise<{ data: NetProfitStatementResult }> {
  const response = await fetch(`${root}?${query(filter)}`, { signal });
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    throw new NetProfitStatementRequestError(
      body.message || "순이익표를 불러오지 못했습니다. 다시 시도해 주세요.",
      body.errors || [],
      response.status,
    );
  }
  return body;
}
export function getCommonNetProfitStatementsExcel(
  filter: OperatingFilter,
  amountUnit: MoneyUnit,
) {
  const params = query(filter);
  params.set("amountUnit", amountUnit);
  return downloadFile(
    `${root}/excel?${params}`,
    "순이익표.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}

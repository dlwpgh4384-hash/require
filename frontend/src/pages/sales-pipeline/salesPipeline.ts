import {
  developerRequest,
  developerFailure,
  getCommonDevelopers,
} from "../developers/developers";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonClients } from "../clients/clients";
import { getCommonParts } from "../parts/parts";
import type {
  SalesOpportunityInput,
  SalesOpportunityOptions,
} from "../salesopportunities/salesOpportunities";

export interface PipelineCard {
  opportunityId: string;
  opportunityName: string;
  customerId: string;
  customerName: string;
  stageCode: string;
  winningProbability: number;
  expectedAmount: number;
  startMonth: string;
  nextAction: string | null;
  nextActionDueDate: string | null;
  requiredJobs: string[];
  stale: boolean;
  approvalStatusCode: string;
  modifiedAt: string;
}
export interface SalesPipeline {
  summary: {
    ongoingCount: number;
    staleCount: number;
    expectedAmount: number;
    weightedAmount: number;
    weightedManpower: number;
  };
  stages: {
    stageCode: string;
    stageName?: string;
    defaultProbability?: number;
    count: number;
    amount: number;
    items: PipelineCard[];
  }[];
}
export interface SalesClosed {
  opportunityId: string;
  opportunityName: string;
  customerName: string;
  result: string;
  expectedAmount: number;
  resultDate: string;
  projectId: string | null;
  lossReason: string | null;
}
export interface ForecastMonth {
  month: string;
  projectRevenue: number;
  weightedOpportunityRevenue: number;
  allWonRevenue: number;
  demand: number;
  supply: number;
}
export interface SalesForecast {
  months: ForecastMonth[];
  hiringReviews: {
    jobCode: string;
    jobName?: string;
    shortageStartMonth: string;
    maximumShortage: number;
    recommendedHiringMonth: string;
    candidateCount: number;
  }[];
}
const root = "/api/common/sales-pipeline";
export const getCommonSalesPipeline = () =>
  developerRequest<SalesPipeline>(root);
export const getCommonSalesPipelineClosed = (
  result: string,
  includeStopped: boolean,
) => {
  const query = new URLSearchParams({ includeStopped: String(includeStopped) });
  if (result) query.set("result", result);
  return developerRequest<{ items: SalesClosed[] }>(`${root}/closed?${query}`);
};
export const getCommonSalesPipelineForecast = () =>
  developerRequest<SalesForecast>(`${root}/forecast`);
export async function getCommonSalesPipelineForecastExcel(): Promise<number> {
  const response = await fetch(`${root}/forecast/excel`);
  if (!response.ok) {
    const failure = await response.json().catch(() => null);
    throw new Error(failure?.message || "엑셀 파일을 만들지 못했습니다.");
  }
  const count = response.headers.get("X-Total-Count");
  if (
    !count ||
    !/^\d+$/.test(count) ||
    !response.headers.get("Content-Type")?.includes("spreadsheetml.sheet")
  )
    throw new Error("엑셀 응답을 확인해 주세요.");
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  try {
    const link = document.createElement("a");
    link.href = href;
    link.download = encoded
      ? decodeURIComponent(encoded)
      : plain || "매출·인력 예측.xlsx";
    link.dataset.testid = "sales-pipeline-download-link";
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    URL.revokeObjectURL(href);
  }
  return Number(count);
}
export async function getSalesPipelineOptions(): Promise<SalesOpportunityOptions> {
  const groups = [
    "SALES_STEP",
    "JOB",
    "GRADE",
    "CTRT_TYPE",
    "APRV_STAT",
    "OPP_STAT",
    "LOSE_RSN",
    "PROJ_STAT",
    "APRV_ACT",
  ];
  const [codes, clients, parts, developers] = await Promise.all([
    Promise.all(
      groups.map(
        async (group) =>
          [
            group,
            (await getCommonCodes(group)).items.filter((c) => c.useYn === "Y"),
          ] as const,
      ),
    ),
    getCommonClients({ useYn: "Y" }),
    getCommonParts(),
    getCommonDevelopers({}),
  ]);
  return {
    codes: Object.fromEntries(codes),
    customers: clients.items.filter((c) =>
      ["CUST", "BOTH"].includes(c.clientTypeCode),
    ),
    parts: parts.items.filter((p) => p.useYn === "Y"),
    developers: developers.items,
  };
}
export function salesPipelineDraft(): SalesOpportunityInput {
  return {
    opportunityName: "",
    customerId: "",
    salesPartId: "",
    stageCode: "",
    winningProbability: 0,
    expectedAmount: 0,
    contractTypeCode: null,
    startMonth: "",
    expectedMonths: null,
    workplace: null,
    nextAction: null,
    nextActionDueDate: null,
    memo: null,
    needs: [],
  };
}
export { developerFailure };

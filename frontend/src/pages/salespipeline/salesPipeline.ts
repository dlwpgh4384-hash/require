import {
  developerRequest,
  developerFailure,
  DeveloperRequestError,
  getCommonDevelopers,
} from "../developers/developers";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonClients } from "../clients/clients";
import { getCommonParts } from "../parts/parts";
import type {
  SalesOpportunityInput,
  SalesOpportunityOptions,
} from "./salesOpportunities";

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
  /** 필요 직무 코드(첫 필요 인력 줄 순서). */
  requiredJobs: string[];
  /** 직무마다 인원 합(시안 칩 「백엔드 개발 2」). 없는 응답이면 requiredJobs만 쓴다. */
  requiredJobHeadcounts?: {
    jobCode: string;
    jobName?: string | null;
    headcount: number;
  }[];
  stale: boolean;
  approvalStatusCode: string;
  modifiedAt: string;
}
/** 카드 칩에 쓸 직무 목록: 직무별 인원(requiredJobHeadcounts)을 먼저 쓰고, 없으면 직무 코드만. */
export const cardJobs = (
  card: Pick<PipelineCard, "requiredJobs" | "requiredJobHeadcounts">,
): { jobCode: string; jobName: string | null; count: number | null }[] =>
  card.requiredJobHeadcounts?.length
    ? card.requiredJobHeadcounts.map((job) => ({
        jobCode: job.jobCode,
        jobName: job.jobName || null,
        count: job.headcount,
      }))
    : card.requiredJobs.map((jobCode) => ({ jobCode, jobName: null, count: null }));
/** 시안 apprChip(): 승인이 필요 없으면(NONE·빈 값) 배지를 그리지 않는다. */
export function approvalBadge(
  code: string | null | undefined,
): { text: string; kind: "bad" | "lock" | "ok"; icon: boolean } | null {
  if (code === "WAIT") return { text: "견적 승인 대기", kind: "bad", icon: true };
  if (code === "REJ") return { text: "견적 반려", kind: "lock", icon: false };
  if (code === "OK") return { text: "견적 승인", kind: "ok", icon: false };
  return null;
}
export interface SalesPipeline {
  summary: {
    ongoingCount: number;
    staleCount: number;
    expectedAmount: number;
    weightedAmount: number;
    weightedManpower: number;
    /** 종료 탭 건수(수주·실주, 사용 중지 빼고). 종료 목록을 받지 않고 탭 이름에 쓴다. */
    closedCount?: number;
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
  resultDate: string | null;
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
/** 월별 예상 매출의 기회 한 줄(시안 「└ 기회명 n%」): 달마다 예상 금액 ÷ 예상 기간 × 수주 확률(식-92). */
export interface ForecastOpportunity {
  opportunityId: string;
  opportunityName: string;
  winningProbability: number;
  months: { month: string; weightedRevenue: number }[];
}
export interface SalesForecast {
  months: ForecastMonth[];
  /** 기회별 줄. 줄을 달마다 더하면 months[].weightedOpportunityRevenue. */
  opportunityRevenues: ForecastOpportunity[];
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
/**
 * SFR-129-07 단계 이동: 읽은 시각(modifiedAt)을 함께 보내고, 다르면 서버가 409로 막는다.
 * 확률은 서버가 정한다(직접 고친 확률이면 유지).
 */
export const putCommonSalesPipelineStage = (
  card: Pick<PipelineCard, "opportunityId" | "modifiedAt">,
  stageCode: string,
) =>
  developerRequest<{ opportunityId: string; winningProbability: number }>(
    `/api/common/sales-opportunities/${encodeURIComponent(card.opportunityId)}/stage`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ stageCode, modifiedAt: card.modifiedAt }),
    },
  );
/** startMonth(yyyy-MM)를 주면 그 달부터 예측한다(인력 운영 보드는 이번 달). */
export const getCommonSalesPipelineForecast = (startMonth?: string) =>
  developerRequest<SalesForecast>(
    startMonth
      ? `${root}/forecast?${new URLSearchParams({ startMonth })}`
      : `${root}/forecast`,
  );
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
      : plain || "영업 파이프라인.xlsx";
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
export { developerFailure, DeveloperRequestError };

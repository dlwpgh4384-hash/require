import {
  developerRequest,
  DeveloperRequestError,
} from "../developers/developers";
export { DeveloperRequestError as SalesOpportunityError };
export interface SalesNeed {
  needSn?: number;
  jobCode: string;
  gradeCode: string | null;
  requiredCount: number;
  startMonth: string;
  months: number;
  monthlyMm: number;
  estimatedUnitPrice: number | null;
  tentativeDeveloperIds: string[];
}
export interface SalesOpportunityInput {
  opportunityName: string;
  customerId: string;
  salesPartId: string;
  stageCode: string;
  winningProbability: number;
  expectedAmount: number;
  contractTypeCode: string | null;
  startMonth: string;
  expectedMonths: number | null;
  workplace: string | null;
  nextAction: string | null;
  nextActionDueDate: string | null;
  memo: string | null;
  needs: SalesNeed[];
}
export interface SalesOpportunity extends SalesOpportunityInput {
  opportunityId: string;
  customerName: string;
  modifiedAt: string;
  approvalStatusCode: string;
  opportunityStatusCode: string;
  projectId: string | null;
  resultDate: string | null;
  lossReasonCode: string | null;
  lossMemo?: string | null;
  approvalHistory: {
    actionCode: string;
    actedAt: string;
    reason: string | null;
  }[];
}
export interface SalesOpportunityOptions {
  codes: Record<string, { code: string; codeName: string }[]>;
  customers: { clientId: string; clientName: string }[];
  parts: { partId: string; partName: string }[];
  developers: { developerId: string; name: string }[];
}
export interface SalesQuote {
  expectedRevenue: number;
  expectedCost: number;
  profitRate: number;
  minimumUnitPrice: number;
  targetUnitPrice: number;
  breakEvenAmount?: number;
  targetAmount?: number;
  approvalRate?: number;
  profitBand?: string;
  needQuotes?: {
    rowIndex: number;
    minimumUnitPrice: number;
    targetUnitPrice: number;
  }[];
}
const root = "/api/common/sales-opportunities";
export const getCommonSalesOpportunity = (id: string) =>
  developerRequest<SalesOpportunity>(`${root}/${encodeURIComponent(id)}`);
export function salesOpportunityPayload(
  input: SalesOpportunityInput,
): SalesOpportunityInput {
  return {
    opportunityName: input.opportunityName,
    customerId: input.customerId,
    salesPartId: input.salesPartId,
    stageCode: input.stageCode,
    winningProbability: input.winningProbability,
    expectedAmount: input.expectedAmount,
    contractTypeCode: input.contractTypeCode || null,
    startMonth: input.startMonth,
    expectedMonths: input.expectedMonths || null,
    workplace: input.workplace || null,
    nextAction: input.nextAction || null,
    nextActionDueDate: input.nextActionDueDate || null,
    memo: input.memo || null,
    needs: input.needs.map((n) => ({
      ...n,
      gradeCode: n.gradeCode || null,
      estimatedUnitPrice: n.estimatedUnitPrice || null,
    })),
  };
}
export const postCommonSalesOpportunity = (input: SalesOpportunityInput) =>
  developerRequest<SalesOpportunity>(root, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(salesOpportunityPayload(input)),
  });
export const putCommonSalesOpportunity = (
  id: string,
  input: SalesOpportunityInput,
  modifiedAt: string,
) =>
  developerRequest<SalesOpportunity>(`${root}/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...salesOpportunityPayload(input), modifiedAt }),
  });
export type SalesOpportunityAction =
  | "approve"
  | "reject"
  | "stage"
  | "tentative-assignment"
  | "tentative-assignment-check"
  | "win"
  | "lose"
  | "stop"
  | "resume";
export function salesOpportunityAction<T = SalesOpportunity>(
  row: SalesOpportunity,
  action: SalesOpportunityAction,
  values: Record<string, unknown> = {},
) {
  return developerRequest<T>(
    `${root}/${encodeURIComponent(row.opportunityId)}/${action}`,
    {
      method: ["approve", "reject", "stage", "tentative-assignment"].includes(
        action,
      )
        ? "PUT"
        : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...values, modifiedAt: row.modifiedAt }),
    },
  );
}
export const quoteCommonSalesOpportunity = (needs: SalesNeed[]) =>
  developerRequest<SalesQuote>("/api/common/sales-pipeline/quote", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ needs }),
  });
export function salesOpportunityErrors(
  input: SalesOpportunityInput,
): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!input.opportunityName.trim() || [...input.opportunityName].length > 100)
    errors.opportunityName = "기회명은 100자 이내로 넣어 주세요.";
  for (const key of ["customerId", "salesPartId", "stageCode"] as const)
    if (!input[key]) errors[key] = "값을 골라 주세요.";
  if (
    !Number.isFinite(input.winningProbability) ||
    input.winningProbability < 0 ||
    input.winningProbability > 100
  )
    errors.winningProbability = "확률은 0~100으로 넣어 주세요.";
  if (!Number.isFinite(input.expectedAmount) || input.expectedAmount <= 0)
    errors.expectedAmount = "양수 금액을 넣어 주세요.";
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(input.startMonth))
    errors.startMonth = "시작월을 넣어 주세요.";
  if (
    input.expectedMonths != null &&
    (!Number.isInteger(input.expectedMonths) || input.expectedMonths < 1)
  )
    errors.expectedMonths = "기간은 1개월 이상으로 넣어 주세요.";
  if ([...(input.workplace || "")].length > 200)
    errors.workplace = "근무지는 200자 이내로 넣어 주세요.";
  if (!input.needs.length) errors.needs = "필요 인력을 한 줄 이상 넣어 주세요.";
  input.needs.forEach((n, i) => {
    const check = (field: string, bad: boolean, label: string) => {
      if (bad)
        errors[`needs[${i}].${field}`] =
          `${i + 1}번째 줄의 ${label} 칸을 확인해 주세요.`;
    };
    check("jobCode", !n.jobCode, "직무");
    check(
      "requiredCount",
      !Number.isInteger(n.requiredCount) || n.requiredCount < 1,
      "인원",
    );
    check(
      "startMonth",
      !/^\d{4}-(0[1-9]|1[0-2])$/.test(n.startMonth),
      "시작월",
    );
    check("months", !Number.isInteger(n.months) || n.months < 1, "개월");
    check(
      "monthlyMm",
      !Number.isFinite(n.monthlyMm) || n.monthlyMm <= 0 || n.monthlyMm > 1,
      "월 MM",
    );
    check(
      "estimatedUnitPrice",
      n.estimatedUnitPrice != null && n.estimatedUnitPrice <= 0,
      "단가",
    );
  });
  return errors;
}
export function salesWinDefaults(row: SalesOpportunity) {
  const [year, month] = row.startMonth.split("-").map(Number);
  const end = new Date(
    Date.UTC(year, month - 1 + (row.expectedMonths || 6), 0),
  );
  return {
    contractAmount: row.expectedAmount,
    contractTypeCode: row.contractTypeCode || "TK",
    contractStartDate: `${row.startMonth}-01`,
    contractEndDate: end.toISOString().slice(0, 10),
    projectStatusCode: "ING",
  };
}

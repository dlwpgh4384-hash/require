import { ProjectRequestError } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";

export interface PartSettlementSummary {
  leaderName: string;
  distributionRate: number;
  salesExpense: number;
  personCount: number;
  profitTotal: number;
  partProfitAmount: number;
}
export interface PartSettlementTab {
  partId: string;
  partName: string;
  summary: PartSettlementSummary;
}
export interface PartSettlementTabs {
  yearMonth: string;
  items: PartSettlementTab[] | null;
  notice: string | null;
}
export interface PartSettlementRow {
  developerId: string;
  developerName: string;
  employeeTypeCode: string;
  projectNames: string[];
  monthlyMm: number;
  unitPrice: number;
  // 급여·퇴직금·비용 계·이익·이익금: 민감 칸을 볼 수 없는 사람에게는 서버가 보내지 않는다.
  salary?: number | null;
  salesExpense: number;
  retirementAmount?: number | null;
  totalCost?: number | null;
  profit?: number | null;
  profitShare?: number | null;
  notes: string[];
  missingMonthlySalary: boolean;
}
export interface PartSettlement {
  partId: string;
  partName: string;
  yearMonth: string;
  summary: PartSettlementSummary;
  items: PartSettlementRow[];
  corpCardAmount: number;
  confirmed: boolean;
  closed: boolean;
  confirmedBy: string | null;
  confirmedAt: string | null;
  confirmationNote: string | null;
  /** 확정한 사람 이름. 옛 응답에는 없어 confirmedBy를 대신 보인다. */
  confirmedByName?: string | null;
  /** 그 달 턴키 프로젝트에 투입한 사람(프로젝트 칸의 「턴키」). 옛 응답에는 없다. */
  turnkeyDeveloperIds?: string[];
  modifiedAt: string | null;
}
export interface PartSettlementTrendMonth {
  yearMonth: string;
  monthlyMm: number;
  unitPrice: number;
  totalCost: number;
  profit: number;
  profitShare: number;
}
export interface PartSettlementTrends {
  partId: string;
  yearMonth: string;
  items: {
    developerId: string;
    developerName: string;
    months: PartSettlementTrendMonth[];
  }[];
  monthlyTotals: { yearMonth: string; partProfitAmount: number }[];
}
export interface PartSettlementDefinition {
  calcId: string;
  formula: string;
  details: string;
}
export interface PartSettlementEnvelope<T> {
  data: T;
  meta: { calculationRules?: PartSettlementDefinition[] };
}
const root = "/api/common/part-settlements";
async function request<T>(
  url: string,
  init?: RequestInit,
): Promise<PartSettlementEnvelope<T>> {
  const response = await fetch(url, { credentials: "same-origin", ...init });
  const body = await response.json();
  if (!response.ok || body.success === false)
    throw new ProjectRequestError(
      body.message || "파트 이익금을 처리하지 못했습니다.",
      body.errors ?? [],
      response.status,
    );
  return body;
}
const path = (partId: string, yearMonth: string) =>
  `${root}/${encodeURIComponent(partId)}/${encodeURIComponent(yearMonth)}`;
export const getCommonPartSettlements = (
  yearMonth: string,
  signal?: AbortSignal,
) =>
  request<PartSettlementTabs>(`${root}?${new URLSearchParams({ yearMonth })}`, {
    signal,
  });
export const getCommonPartSettlement = (
  partId: string,
  yearMonth: string,
  signal?: AbortSignal,
) => request<PartSettlement>(path(partId, yearMonth), { signal });
export const getCommonPartSettlementTrends = (
  partId: string,
  yearMonth: string,
  signal?: AbortSignal,
) =>
  request<PartSettlementTrends>(`${path(partId, yearMonth)}/trends`, {
    signal,
  });
const command = (
  partId: string,
  yearMonth: string,
  action: string,
  body: unknown,
) =>
  request<PartSettlement>(`${path(partId, yearMonth)}/${action}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
export const putCommonPartSettlementCard = (
  partId: string,
  yearMonth: string,
  corpCardAmount: number,
  modifiedAt: string | null,
) => command(partId, yearMonth, "card", { corpCardAmount, modifiedAt });
export const putCommonPartSettlementConfirm = (
  partId: string,
  yearMonth: string,
  modifiedAt: string | null,
) => command(partId, yearMonth, "confirm", { modifiedAt });
export const putCommonPartSettlementCancel = (
  partId: string,
  yearMonth: string,
  cancelReason: string,
  modifiedAt: string | null,
) => command(partId, yearMonth, "cancel", { cancelReason, modifiedAt });
export const getCommonPartSettlementsExcel = (
  partId: string,
  yearMonth: string,
  view: "monthly" | "trend",
  columns?: string[],
) =>
  downloadFile(
    `${root}/excel?${new URLSearchParams({
      partId,
      yearMonth,
      view,
      // 이번 달 표는 화면에 보이는 열만 내보낸다(SFR-011, UI-03과 같은 방식).
      ...(view === "monthly" && columns?.length
        ? { columns: columns.join(",") }
        : {}),
    })}`,
    "영업 파트 이익금.xlsx",
  );
export const putCommonPartSettlementCloseConfirm = (body: {
  yearMonth: string;
  items: { partId: string; confirmed: boolean; modifiedAt: string | null }[];
}) =>
  request<{
    yearMonth: string;
    items: PartSettlement[];
    unconfirmedCount: number;
  }>(`${root}/close-confirm`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

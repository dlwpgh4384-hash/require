import { projectRequest, ProjectRequestError } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";

export type Amount = number | null;
export interface DashboardResult {
  selectedMonth: string;
  today: string;
  closingState: string;
  lastClosedMonth: string | null;
  provisional: boolean;
  mixedBasisNotice: string | null;
  summary: {
    revenue: Amount;
    revenuePreviousComparison: Amount;
    revenueTargetRate: Amount;
    operatingProfit: Amount;
    operatingProfitRate: Amount;
    operatingProfitPreviousComparison: Amount;
    cumulativeOperatingProfit: Amount;
    cumulativeAchievementRate: Amount;
    utilizationRate: Amount;
    utilizationTargetRate: Amount;
    idlePeople: Amount;
    unpaidAmount: Amount;
    overdueCount: number;
    overdueAmount: Amount;
  };
  todos: {
    title: string;
    count: number;
    description: string;
    targetScreenId: string | null;
    urgent: boolean;
  }[];
  closing: {
    completedCount: number;
    totalCount: number;
    remainingItems: string[];
  };
  projectAlerts: {
    projectId: string;
    projectName: string;
    alertName: string;
    mainCause: string | null;
  }[];
  trend: {
    month: string;
    revenue: Amount;
    cost: Amount;
    targetRevenue: Amount;
  }[];
  manpower: {
    month: string;
    supply: number;
    demand: number;
    difference: number;
    shortage: boolean;
  }[];
  salesParts: {
    partId: string;
    partName: string;
    revenue: Amount;
    profitRate: Amount;
  }[];
  pipeline: {
    activeCount: number;
    weightedExpectedRevenue: Amount;
    upcoming: { opportunityName: string; startMonth: string }[];
  };
  cash: {
    month: string;
    monthEndBalance: Amount;
    shortage: boolean;
    warning: string | null;
  }[];
}
const root = "/api/common/dashboard";
export const getCommonDashboard = (yearMonth: string, signal?: AbortSignal) =>
  projectRequest<DashboardResult>(
    `${root}?${new URLSearchParams({ yearMonth })}`,
    { signal },
  );
export async function getCommonDashboardExcel(yearMonth: string) {
  let count = 0;
  await downloadFile(
    `${root}/excel?${new URLSearchParams({ yearMonth })}`,
    "대시보드.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
    (response) => {
      const total = response.headers.get("X-Total-Count");
      if (total === null || !/^\d+$/.test(total))
        throw new Error("조회 결과 건수를 확인하지 못했습니다.");
      count = Number(total);
    },
  );
  return count;
}

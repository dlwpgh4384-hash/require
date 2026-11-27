import { projectRequest, ProjectRequestError } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";

export type Amount = number | null;
export type DashboardTone = "bad" | "warn";
export interface DashboardResult {
  /** 대시보드 모양(명세 ⑧-4): 전체 / 파트장(담당 파트) / PM(담당 프로젝트). 옛 응답에는 없다(전체로 본다).
   *  part·pm이면 summary·closing·pipeline·minimumCash는 null, cash·trend·salesParts는 빈 배열. */
  dashMode?: "all" | "part" | "pm";
  selectedMonth: string;
  today: string;
  closingState: "작업중" | "마감";
  lastClosedMonth: string | null;
  provisional: boolean;
  /** 시안 기준 줄 「오늘 yyyy-MM-dd · n월까지 마감」. */
  asOfText: string;
  /** 잠정 배지 뒤 글(고른 달이 마감 전일 때). 배지는 화면이 그린다. */
  provisionalNotice: string | null;
  /** 지난달을 고르면 「… 오늘 기준」 안내(시안 글). */
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
    idlePeople: number;
    unpaidAmount: Amount;
    unpaidCount: number;
    overdueCount: number;
    overdueAmount: Amount;
  } | null;
  /** 시안 확인할 일. 제목에 건수가 이미 들어 있다. visible=true 줄만 그린다. */
  todos: {
    key: string;
    title: string;
    count: number;
    description: string;
    amount: Amount;
    targetScreenId: string | null;
    /** 시안 오른쪽 「청구·입금 ›」. */
    targetScreenName: string | null;
    tone: DashboardTone;
    urgent: boolean;
    /** 0건이어도 보이는 줄(철수 예정). */
    alwaysVisible: boolean;
    visible: boolean;
  }[];
  closing: {
    completedCount: number;
    totalCount: number;
    remainingItems: {
      name: string;
      status: string;
      description: string | null;
      targetScreenId: string | null;
    }[];
  } | null;
  projectAlerts: {
    projectId: string;
    projectName: string;
    contractType: string | null;
    /** 「예상 최종 마진율」(턴키) 또는 「월간 마진율」. */
    marginLabel: string;
    marginRate: Amount;
    budgetBurnRate: Amount;
    alerts: string[];
    alertBadges: { name: string; tone: DashboardTone }[];
    alertName: string;
    mainCause: string | null;
    mainCauseAmount: Amount;
  }[];
  /** 프로젝트 경보를 읽은 달 yyyy-MM(첫 미마감 달과 오늘 달 중 빠른 달). 경보를 누르면 이 달로 원가 상세를 연다. */
  projectAlertMonth: string;
  /** 고른 해 1~12월. 매출·원가는 고른 달까지, 목표 매출은 12달. */
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
    upcoming: {
      opportunityId: string;
      opportunityName: string;
      startMonth: string;
      stageName: string | null;
      probability: Amount;
    }[];
  } | null;
  cash: {
    month: string;
    monthEndBalance: Amount;
    shortage: boolean;
    warning: string | null;
  }[];
  /** 최소 운영자금(운영 기준값 MIN_CASH). */
  minimumCash: Amount;
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

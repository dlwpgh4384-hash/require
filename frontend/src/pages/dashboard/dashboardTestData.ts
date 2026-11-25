import type { DashboardResult } from "./dashboard";

export function dashboardResult(selectedMonth = "2026-11"): DashboardResult {
  const forward = [
    "2026-11",
    "2026-12",
    "2027-01",
    "2027-02",
    "2027-03",
    "2027-04",
  ];
  return {
    selectedMonth,
    today: "2026-11-25",
    closingState: "작업중",
    lastClosedMonth: "2026-10",
    provisional: true,
    mixedBasisNotice:
      selectedMonth === "2026-11" ? null : "일부 영역은 오늘 기준입니다.",
    summary: {
      revenue: 1234,
      revenuePreviousComparison: null,
      revenueTargetRate: 75,
      operatingProfit: -10,
      operatingProfitRate: -0.8,
      operatingProfitPreviousComparison: -5,
      cumulativeOperatingProfit: -40,
      cumulativeAchievementRate: null,
      utilizationRate: null,
      utilizationTargetRate: 87.5,
      idlePeople: 0,
      unpaidAmount: 120,
      overdueCount: 1,
      overdueAmount: 120,
    },
    todos: [
      {
        title: "연체 미수",
        count: 1,
        description: "입금 예정일 경과",
        targetScreenId: "UI-13",
        urgent: true,
      },
    ],
    closing: { completedCount: 5, totalCount: 6, remainingItems: ["급여"] },
    projectAlerts: [],
    trend: [
      { month: selectedMonth, revenue: 1234, cost: 100, targetRevenue: null },
    ],
    manpower: forward.map((month) => ({
      month,
      supply: 2,
      demand: 1,
      difference: 1,
      shortage: false,
    })),
    salesParts: [],
    pipeline: { activeCount: 0, weightedExpectedRevenue: 0, upcoming: [] },
    cash: forward.slice(0, 3).map((month) => ({
      month,
      monthEndBalance: null,
      shortage: false,
      warning: null,
    })),
  };
}

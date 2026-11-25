import type { DashboardResult } from "./dashboard";

/** 시안 UI-16 전체 모양 시험 자료(2026-11-25, 10월까지 마감). */
export function dashboardResult(
  selectedMonth = "2026-11",
  overrides: Partial<DashboardResult> = {},
): DashboardResult {
  const forward = [
    "2026-11",
    "2026-12",
    "2027-01",
    "2027-02",
    "2027-03",
    "2027-04",
  ];
  const year = selectedMonth.slice(0, 4);
  const months = Array.from(
    { length: 12 },
    (_, k) => `${year}-${String(k + 1).padStart(2, "0")}`,
  );
  return {
    selectedMonth,
    today: "2026-11-25",
    closingState: "작업중",
    lastClosedMonth: "2026-10",
    provisional: selectedMonth > "2026-10",
    asOfText: "오늘 2026-11-25 · 10월까지 마감",
    provisionalNotice: `${Number(selectedMonth.slice(5))}월은 마감 전이라 숫자는 잠정입니다`,
    mixedBasisNotice:
      selectedMonth === "2026-11"
        ? null
        : "미수금·확인할 일·프로젝트 경보·인력 수급·파이프라인·자금은 오늘 기준(청구 누락 의심은 고른 달)",
    summary: {
      revenue: 523_000_000,
      revenuePreviousComparison: 3.2,
      revenueTargetRate: 75,
      operatingProfit: -10_000_000,
      operatingProfitRate: -0.8,
      operatingProfitPreviousComparison: -5,
      cumulativeOperatingProfit: 40_000_000,
      cumulativeAchievementRate: null,
      utilizationRate: 92.5,
      utilizationTargetRate: 90,
      idlePeople: 3,
      unpaidAmount: 120_000_000,
      unpaidCount: 4,
      overdueCount: 1,
      overdueAmount: 12_000_000,
    },
    todos: [
      {
        key: "overdueBills",
        title: "미수 연체 1건",
        count: 1,
        amount: 12_000_000,
        description: "1,200만 원 · 알파",
        targetScreenId: "UI-13",
        targetScreenName: "청구·입금",
        tone: "bad",
        urgent: true,
        alwaysVisible: false,
        visible: true,
      },
      {
        key: "overAllocated",
        title: "MM 1.0 초과 0명",
        count: 0,
        amount: null,
        description: "한 사람의 이번 달 투입 합계가 1.0을 넘음",
        targetScreenId: "UI-12",
        targetScreenName: "투입 배정",
        tone: "warn",
        urgent: false,
        alwaysVisible: false,
        visible: false,
      },
      {
        key: "withdrawal",
        title: "3개월 안 철수 예정 0명",
        count: 0,
        amount: null,
        description:
          "다음 달 0명 · 다음 배정 없는 0명 중 0명은 추천 자리 있음",
        targetScreenId: "UI-05",
        targetScreenName: "철수 리스트",
        tone: "warn",
        urgent: false,
        alwaysVisible: true,
        visible: true,
      },
      {
        key: "unassignedSalesPart",
        title: "영업 파트 미지정 프로젝트 0건",
        count: 0,
        amount: null,
        description: "파트별 실적·권한·정산에서 빠짐",
        targetScreenId: "UI-01",
        targetScreenName: "프로젝트",
        tone: "warn",
        urgent: false,
        alwaysVisible: false,
        visible: false,
      },
    ],
    closing: {
      completedCount: 5,
      totalCount: 6,
      remainingItems: [
        {
          name: "청구 누락 점검",
          status: "warn",
          description: "2건 — 투입 대비 청구 확인",
          targetScreenId: "UI-13",
        },
      ],
    },
    projectAlerts: [
      {
        projectId: "P-1",
        projectName: "알파 차세대",
        contractType: "턴키",
        marginLabel: "예상 최종 마진율",
        marginRate: 5.2,
        budgetBurnRate: 104.3,
        alerts: ["예산 초과"],
        alertBadges: [{ name: "예산 초과", tone: "bad" }],
        alertName: "예산 초과",
        mainCause: "외주비",
        mainCauseAmount: 3_000_000,
      },
    ],
    projectAlertMonth: "2026-11",
    trend: months.map((month) => ({
      month,
      revenue: month <= selectedMonth ? 500_000_000 : null,
      cost: month <= selectedMonth ? 400_000_000 : null,
      targetRevenue: 600_000_000,
    })),
    manpower: forward.map((month) => ({
      month,
      supply: 20,
      demand: month === "2027-01" ? 21.5 : 18,
      difference: month === "2027-01" ? -1.5 : 2,
      shortage: month === "2027-01",
    })),
    salesParts: [
      { partId: "PT1", partName: "1파트", revenue: 300_000_000, profitRate: 12.5 },
      { partId: "PT2", partName: "2파트", revenue: 100_000_000, profitRate: -3 },
    ],
    pipeline: {
      activeCount: 3,
      weightedExpectedRevenue: 150_000_000,
      upcoming: [
        {
          opportunityId: "O-01",
          opportunityName: "베타 차세대",
          startMonth: "2026-12",
          stageName: "제안",
          probability: 60,
        },
      ],
    },
    cash: forward.slice(0, 3).map((month) => ({
      month,
      monthEndBalance: month === "2027-01" ? 800_000_000 : 1_500_000_000,
      shortage: month === "2027-01",
      warning: null,
    })),
    minimumCash: 1_000_000_000,
    ...overrides,
  };
}

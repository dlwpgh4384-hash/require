export interface PersonalCostProfitFilter {
  year: number;
  baseMonth: number;
  displayMode: "연누계" | "월평균";
  name?: string;
  employeeTypeCode?: string;
  /** 소속. 「자사」는 SELF_AFFILIATION(소속 거래처 없음). */
  affiliationId?: string;
  salesPartId?: string;
  jobCode?: string;
  view?: "전체" | "적자만" | "동시 투입·1.0 초과만";
  includeTurnkey?: boolean;
  columns?: string[];
}
/** 소속 조건 「자사」 값(서버와 같은 값). */
export const SELF_AFFILIATION = "__self";
/** 펼친 줄: 투입ID마다 한 줄(조회 기간 합, 월 평균이면 사람 줄처럼 나눔). 대기(IDLE)는 맨 아래. */
export interface PersonalCostProfitAllocation {
  projectId: string | null;
  assignmentId: string;
  projectName: string;
  jobName: string | null;
  /** IN 내부(연구개발), TK 턴키. 대기는 null. */
  contractTypeCode: string | null;
  /** 무상지원 투입. */
  free: boolean;
  mm: number;
  unitPrice: number;
  allocatedCost: number;
  operatingProfit: number;
}
export interface PersonalCostProfitRow {
  developerId: string;
  developerName: string;
  managementNumber?: string | null;
  affiliationName: string;
  positionName: string;
  employeeTypeName: string;
  joinDate: string | null;
  operatingRate: number;
  mm: number;
  unitPrice: number;
  /** 민감 열(시안 SENS03): 권한이 없으면 서버가 뺀다. */
  monthlySalary?: number;
  allowance?: number;
  companyInsurance?: number;
  retirementReserve?: number;
  personalCost?: number;
  teamExpenses: number;
  projectExpenses: number;
  totalCost: number;
  operatingProfit: number;
  profitRate: number | null;
  personalInsurance?: number;
  withholdingTax?: number;
  incomeTax?: number;
  netPay?: number;
  mmOverOne: boolean;
  allocations: PersonalCostProfitAllocation[];
}
export interface PersonalCostProfitContribution {
  jobCode: string;
  jobName: string;
  gradeCode: string;
  gradeName: "초급" | "중급" | "고급" | "특급";
  people: number;
  contributionPerPerson: number;
}
export interface PersonalCostProfitResult {
  items: PersonalCostProfitRow[];
  summary: {
    totalPeople: number;
    averageOperatingPeople: number;
    averageEarningPeople: number;
    averageUnitPrice: number;
    averageCost: number;
    averageOperatingProfit: number;
    lossPeople: number;
    operatingRate: number;
    earningRate: number;
    /** 총 인원 중 기준월 말 휴직 인원. */
    leavePeople: number;
    /** 평균 단가 분모 인원(단가 칸이 있는 사람). */
    unitPricePeople: number;
    /** 월평균 재직 직접 인력(소수 첫째 자리). */
    averageDirectPeople: number;
  };
  totals: Partial<Record<keyof PersonalCostProfitRow, number>>;
  contributions: PersonalCostProfitContribution[];
}
export type PersonalCostProfitOption = { value: string; label: string };
export type PersonalCostProfitOptions = Record<
  "employeeTypeCode" | "affiliationId" | "salesPartId" | "jobCode",
  PersonalCostProfitOption[]
>;

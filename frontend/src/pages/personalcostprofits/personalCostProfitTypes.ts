export interface PersonalCostProfitFilter {
  year: number;
  baseMonth: number;
  displayMode: "연누계" | "월평균";
  name?: string;
  employeeTypeCode?: string;
  affiliationId?: string;
  salesPartId?: string;
  jobCode?: string;
  view?: "전체" | "적자만" | "동시 투입·1.0 초과만";
  includeTurnkey?: boolean;
  columns?: string[];
}
export interface PersonalCostProfitAllocation {
  yearMonth: string;
  projectId: string | null;
  assignmentId: string;
  projectName: string;
  jobName: string | null;
  mm: number;
  unitPrice: number;
  allocatedCost: number;
  operatingProfit: number;
}
export interface PersonalCostProfitRow {
  developerId: string;
  developerName: string;
  affiliationName: string;
  positionName: string;
  employeeTypeName: string;
  joinDate: string | null;
  operatingRate: number;
  mm: number;
  unitPrice: number;
  monthlySalary?: number;
  allowance: number;
  companyInsurance: number;
  retirementReserve: number;
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
  };
  totals: Partial<Record<keyof PersonalCostProfitRow, number>>;
  contributions: PersonalCostProfitContribution[];
}
export type PersonalCostProfitOption = { value: string; label: string };
export type PersonalCostProfitOptions = Record<
  "employeeTypeCode" | "affiliationId" | "salesPartId" | "jobCode",
  PersonalCostProfitOption[]
>;

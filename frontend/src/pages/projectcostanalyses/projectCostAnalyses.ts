import { projectRequest } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";

export interface ProjectCostAnalysisPeriod {
  year: number;
  baseMonth: number;
}
export interface ProjectCostAnalysisFilter extends ProjectCostAnalysisPeriod {
  projectId?: string;
  contractTypeCode?: string;
  projectStatus?: string;
  alertOnly?: boolean;
}
export interface CommonProjectCostAnalysisRow {
  projectId: string;
  projectName: string;
  contractTypeCode: string;
  contractTypeName: string;
  projectStatus: string;
  originalContractAmount: number | null;
  contractAmount: number | null;
  cumulativeRevenue: number | null;
  totalCost: number | null;
  cumulativeProfit: number | null;
  cumulativeMarginRate: number | null;
  monthlyMarginRate: number | null;
  expectedFinalMarginRate: number | null;
  targetDifference: number | null;
  budgetUsageRate: number | null;
  unitPricePerMm: number | null;
  costPerMm: number | null;
  profitPerMm: number | null;
  freeSupportMm: number | null;
  freeSupportCost: number | null;
  alerts: string[];
  plannedMm: number | null;
  actualMm: number;
  researchCost: number | null;
  lowMargin: boolean;
  budgetExceeded: boolean;
  budgetColor: string | null;
  missingMonthlyCost: boolean;
  missingMonthlyCostReason: string | null;
  alertCount: number;
  /** 진행 중 턴키의 남은 예상 원가(식-20)와 마우스 근거 */
  remainingCost: number | null;
  remainingCostBasis: string | null;
  /** 경보 칸 아래 「주원인」(식-91) */
  primaryCauseName: string | null;
  primaryCauseAmount: number | null;
  elapsedMonths: number;
  totalMonths: number;
  remainingMonths: number;
  /** 기준월 당시 계약 기간(YYYY-MM-DD). 상세 창 부제에 쓴다. */
  startDate: string;
  endDate: string;
}
export interface CommonProjectCostAnalysisResult {
  summary: {
    cumulativeRevenue: number;
    cumulativeProfit: number;
    lowMarginCount: number;
    budgetExceededCount: number;
    researchCost: number;
  };
  projectOptions: { projectId: string; projectName: string }[];
  items: CommonProjectCostAnalysisRow[];
  totalCount: number;
  targetMarginRate: number;
  /** 작업중 달에 급여가 없을 때 월간 마진을 계산하지 않는다는 안내 */
  monthlyMarginNotice: string | null;
}
export interface CommonProjectCostAnalysisMonthlyCosts {
  items: {
    yearMonth: string;
    mm: number;
    cost: number;
    revenue: number | null;
    distributionRevenue: number | null;
    marginRate: number | null;
  }[];
}
export interface CommonProjectCostAnalysisWorkforce {
  items: {
    developerId: string;
    developerName: string;
    jobName: string;
    monthlyMm: number;
    /** 민감 칸을 볼 수 없는 사람에게는 서버가 보내지 않는다. */
    allocatedCost?: number | null;
    revenue: number | null;
    inputTypeCode: string;
    inputTypeName: string;
  }[];
}
export interface CommonProjectCostAnalysisCause {
  causeKey: string;
  causeName: string;
  amount: number;
  description: string;
  owner: string;
  task: string;
  targetScreenId: string | null;
  targetLabel: string | null;
  navigationContext: string | null;
}
export interface CommonProjectCostAnalysisCauses {
  items: CommonProjectCostAnalysisCause[];
  actualProgressRate: number | null;
  expectedCostAtProgress: number | null;
  cumulativeCost: number;
  progressExcessAmount: number | null;
  remainderAmount: number | null;
  primaryBarColor: string;
  referenceNotice: string | null;
  /** 단가 대비 원가 기준(%) */
  costOverRate: number;
}
export interface CommonProjectCostAnalysisSimulation {
  virtual: boolean;
  marginRateBefore: number;
  marginRateAfter: number;
  marginRateChange: number;
  addedRevenue: number;
  addedCost: number;
  targetMarginRate: number;
}
export interface ProjectCostAnalysisSimulationInput {
  headcountChange: string;
  monthlyCostPerPerson: string;
  months: string;
  monthlyPricePerPerson: string;
}

/** 시안 mmf(): MM은 소수 둘째 자리, 값이 없으면 「–」. */
export const formatMm = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value) ? "–" : Number(value).toFixed(2);
/** 시안 pct(): 소수 한 자리 %, 음수는 「−」. */
export const formatPercent = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value)
    ? "–"
    : `${value < 0 ? "−" : ""}${Math.abs(value).toFixed(1)}%`;
/** 시안 pp(): 부호를 붙인 %p(+1.0%p, −6.5%p). */
export const formatPoint = (value: number) =>
  `${value < 0 ? "−" : "+"}${Math.abs(value).toFixed(1)}%p`;
/** YYYYMM → YYYY-MM(시안 월 표시). */
export const formatYearMonth = (value: string) =>
  /^\d{6}$/.test(value) ? `${value.slice(0, 4)}-${value.slice(4)}` : value;

const root = "/api/common/project-cost-analyses";
export function projectCostAnalysisQuery(value: object) {
  const query = new URLSearchParams();
  for (const [key, item] of Object.entries(value))
    if (item !== undefined && item !== "") query.set(key, String(item));
  return query.toString();
}
export const getCommonProjectCostAnalyses = (
  filter: ProjectCostAnalysisFilter,
  signal?: AbortSignal,
) =>
  projectRequest<CommonProjectCostAnalysisResult>(
    `${root}?${projectCostAnalysisQuery(filter)}`,
    { signal },
  );
export const getCommonProjectCostAnalysesExcel = (
  filter: ProjectCostAnalysisFilter,
) =>
  downloadFile(
    `${root}/excel?${projectCostAnalysisQuery(filter)}`,
    "프로젝트_원가_분석.xlsx",
  );
function detail<T>(
  id: string,
  suffix: string,
  query: object,
  signal?: AbortSignal,
) {
  return projectRequest<T>(
    `${root}/${encodeURIComponent(id)}/${suffix}?${projectCostAnalysisQuery(query)}`,
    { signal },
  );
}
export const getCommonProjectCostAnalysisMonthlyCosts = (
  id: string,
  period: ProjectCostAnalysisPeriod,
  signal?: AbortSignal,
) =>
  detail<CommonProjectCostAnalysisMonthlyCosts>(
    id,
    "monthly-costs",
    period,
    signal,
  );
export const getCommonProjectCostAnalysisWorkforce = (
  id: string,
  period: ProjectCostAnalysisPeriod,
  signal?: AbortSignal,
) =>
  detail<CommonProjectCostAnalysisWorkforce>(id, "workforce", period, signal);
export const getCommonProjectCostAnalysisCauses = (
  id: string,
  period: ProjectCostAnalysisPeriod,
  signal?: AbortSignal,
) => detail<CommonProjectCostAnalysisCauses>(id, "causes", period, signal);
export const getCommonProjectCostAnalysisSimulation = (
  id: string,
  period: ProjectCostAnalysisPeriod,
  input: ProjectCostAnalysisSimulationInput,
  signal?: AbortSignal,
) =>
  detail<CommonProjectCostAnalysisSimulation>(
    id,
    "simulation",
    { ...period, ...input },
    signal,
  );

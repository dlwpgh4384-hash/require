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
  costComposition: string;
  alertCount: number;
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
}
export interface CommonProjectCostAnalysisMonthlyCosts {
  items: {
    yearMonth: string;
    cost: number;
    distributionRevenue: number | null;
  }[];
}
export interface CommonProjectCostAnalysisWorkforce {
  items: {
    developerId: string;
    developerName: string;
    jobName: string;
    monthlyMm: number;
  }[];
}
export interface CommonProjectCostAnalysisCauses {
  primaryBarColor: string;
  referenceNotice: string | null;
  progressExcessAmount: number | null;
  remainderExplanation: string | null;
  items: {
    causeName: string;
    amount: number;
    task: string;
    targetScreenId: string;
    navigationContext: string;
  }[];
}
export interface CommonProjectCostAnalysisSimulation {
  virtual: boolean;
  marginRateBefore: number;
  marginRateAfter: number;
  marginRateChange: number;
  calculationBasis: string;
}
export interface ProjectCostAnalysisSimulationInput {
  headcountChange: string;
  monthlyCostPerPerson: string;
  months: string;
  monthlyPricePerPerson: string;
}
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

import { projectRequest, ProjectRequestError } from "../projects/projects";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonProjects } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";

export interface RateRenegotiationFilter {
  renewalMonths: number;
  profitRateThreshold: number;
  employeeTypeCode?: string;
  projectId?: string;
  increaseRate: number;
}
export interface RateRenegotiationRow {
  assignmentId: string;
  developerId: string;
  developerName: string;
  employeeTypeName: string;
  projectName: string;
  renewalDate: string;
  daysUntilRenewal: number;
  monthlyMm: number;
  currentUnitPrice: number;
  monthlyCost: number;
  profitRate: number;
  targetUnitPrice: number;
  requiredIncreaseRate: number;
  annualProfitIncrease: number;
  /** 프로젝트 이름을 누르면 여는 프로젝트 상세용 */
  projectId?: string;
  /** 시안 이름 아래 관리번호 */
  managementNumber?: string | null;
}
export interface RateRenegotiationResult {
  baseYearMonth: string;
  appliedIncreaseRate: number;
  items: RateRenegotiationRow[];
  summary: {
    targetCount: number;
    averageProfitRate: number | null;
    targetProfitRate: number;
    assumedAnnualProfitIncrease: number;
    targetAnnualProfitIncrease: number;
  };
  totalAnnualProfitIncrease: number;
  /** 이 달 급여 업로드 전이라 개발자 풀 월 급여로 계산한 대상자 */
  salaryEstimatedDeveloperIds?: string[];
  formulaHelp: Record<
    | "renewalDate"
    | "monthlyCost"
    | "profitRate"
    | "targetUnitPrice"
    | "requiredIncreaseRate"
    | "annualProfitIncrease",
    string
  >;
}
export type RateRenegotiationColumn = Exclude<
  keyof RateRenegotiationRow,
  | "assignmentId"
  | "developerId"
  | "daysUntilRenewal"
  | "projectId"
  | "managementNumber"
>;
export const rateRenegotiationColumns: readonly [
  RateRenegotiationColumn,
  string,
][] = [
  ["developerName", "이름"],
  ["employeeTypeName", "구분"],
  ["projectName", "프로젝트"],
  ["renewalDate", "갱신일"],
  ["monthlyMm", "MM"],
  ["currentUnitPrice", "현재 단가"],
  ["monthlyCost", "월 원가"],
  ["profitRate", "이익률"],
  ["targetUnitPrice", "목표 단가"],
  ["requiredIncreaseRate", "필요 인상률"],
  ["annualProfitIncrease", "n% 올리면 연 이익"],
];
const root = "/api/common/rate-renegotiations";
export const rateRenegotiationDefaults = (): RateRenegotiationFilter => ({
  renewalMonths: 6,
  profitRateThreshold: 15,
  increaseRate: 5,
});
export function rateRenegotiationQuery(
  filter: RateRenegotiationFilter,
  columns?: readonly string[],
) {
  const query = new URLSearchParams();
  for (const key of [
    "renewalMonths",
    "profitRateThreshold",
    "employeeTypeCode",
    "projectId",
    "increaseRate",
  ] as const) {
    const value = filter[key];
    if (value !== undefined && value !== "") query.set(key, String(value));
  }
  columns?.forEach((key) => query.append("columns", key));
  return query;
}
export function getCommonRateRenegotiations(
  filter: RateRenegotiationFilter,
  signal?: AbortSignal,
) {
  return projectRequest<RateRenegotiationResult>(
    `${root}?${rateRenegotiationQuery(filter)}`,
    { signal },
  );
}
export function getCommonRateRenegotiationsExcel(
  filter: RateRenegotiationFilter,
  columns: readonly string[],
) {
  return downloadFile(
    `${root}/excel?${rateRenegotiationQuery(filter, columns)}`,
    "단가 재협상 대상.xlsx",
    (message, errors) => new ProjectRequestError(message, errors),
  );
}
export async function getRateRenegotiationOptions(signal?: AbortSignal) {
  const [employees, projects] = await Promise.all([
    getCommonCodes("EMP_TYPE", signal),
    getCommonProjects(signal),
  ]);
  return {
    employees: (employees?.items ?? [])
      .filter((c) => c.useYn === "Y")
      .map((c) => ({ value: c.code, label: c.codeName })),
    projects: (projects?.items ?? []).map((p) => ({
      value: p.projectId,
      label: p.projectName,
    })),
  };
}

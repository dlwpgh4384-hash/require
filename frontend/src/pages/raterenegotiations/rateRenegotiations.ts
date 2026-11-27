import { projectRequest, ProjectRequestError } from "../projects/projects";
import { getCommonCodes } from "../codegroups/codeGroups";
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
  /** 민감 칸을 볼 수 없는 사람에게는 서버가 보내지 않는다(목표 단가도 같음). */
  currentUnitPrice?: number | null;
  /** 민감 칸을 볼 수 없는 사람에게는 서버가 보내지 않는다. */
  monthlyCost?: number | null;
  profitRate: number;
  targetUnitPrice?: number | null;
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
  /** 조건 「프로젝트」 목록: 재협상 대상이 될 수 있는 프로젝트만(시안·SFR-132-02, 2026-11-24 사용자 결정). */
  projectOptions?: { projectId: string; projectName: string }[];
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
  // 프로젝트 목록은 조회 결과(projectOptions)에서 받는다(대상이 될 수 있는 프로젝트만).
  const employees = await getCommonCodes("EMP_TYPE", signal);
  return {
    employees: (employees?.items ?? [])
      .filter((c) => c.useYn === "Y")
      .map((c) => ({ value: c.code, label: c.codeName })),
  };
}

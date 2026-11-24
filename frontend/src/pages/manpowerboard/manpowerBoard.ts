import { downloadFile } from "../../shared/list/download";
import { projectRequest, ProjectRequestError } from "../projects/projects";

export interface ManpowerBoardFilter {
  year: number;
  jobCode?: string;
  gradeCode?: string;
  employeeTypeCode?: string;
  onlyAvailable?: boolean;
}
/** 달력 칸 상태(화면 클래스용 영문 값). 화면에 보이는 이름은 statusName. */
export type ManpowerBoardStatus =
  | "on"
  | "end"
  | "idle"
  | "tent"
  | "leave"
  | "ret"
  | "pre"
  | "none";
export interface ManpowerBoardCell {
  month: string;
  statusCode: ManpowerBoardStatus;
  statusName: string;
  totalMm: number;
  projects: { projectName: string; monthlyMm: number }[];
  /** 철수 예정 칸에만 있다(높음/보통/낮음). */
  extensionPossibilityName: string | null;
  winningProbability: number | null;
  tentative: boolean;
}
export interface ManpowerBoardPerson {
  developerId: string;
  /** 사번(관리번호). 없으면 화면은 developerId를 보여 준다. */
  managementNumber?: string | null;
  name: string;
  jobName: string;
  cells: ManpowerBoardCell[];
}
export interface ManpowerBoardResult {
  year: number;
  summary: {
    idleCount: number;
    availableMm: number;
    withdrawalCount: number;
    highExtensionCount: number;
    concurrentCount: number;
    overAllocatedCount: number;
    futureAvailableMm: number;
  };
  items: ManpowerBoardPerson[];
  monthTotals: {
    month: string;
    idleCount: number | null;
    availableMm: number | null;
  }[];
  totalCount: number;
}
const root = "/api/common/manpower-board";
export function manpowerBoardQuery(filter: ManpowerBoardFilter) {
  const query = new URLSearchParams({ year: String(filter.year) });
  if (filter.jobCode) query.set("jobCode", filter.jobCode);
  if (filter.gradeCode) query.set("gradeCode", filter.gradeCode);
  if (filter.employeeTypeCode)
    query.set("employeeTypeCode", filter.employeeTypeCode);
  if (filter.onlyAvailable) query.set("onlyAvailable", "true");
  return query;
}
export const getCommonManpowerBoard = (
  filter: ManpowerBoardFilter,
  signal?: AbortSignal,
) =>
  projectRequest<ManpowerBoardResult>(`${root}?${manpowerBoardQuery(filter)}`, {
    signal,
  });

/** 공통 엑셀 받기(downloadFile)로 저장하고, 서버가 준 전체 조회 건수(X-Total-Count)를 돌려준다. */
export async function getCommonManpowerBoardExcel(
  filter: ManpowerBoardFilter,
): Promise<number> {
  let count = 0;
  await downloadFile(
    `${root}/excel?${manpowerBoardQuery(filter)}`,
    `인력 운영 보드_${filter.year}년.xlsx`,
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

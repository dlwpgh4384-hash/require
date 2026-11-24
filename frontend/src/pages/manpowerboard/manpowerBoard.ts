import { projectRequest, ProjectRequestError } from "../projects/projects";

export interface ManpowerBoardFilter {
  year: number;
  jobCode?: string;
  gradeCode?: string;
  employeeTypeCode?: string;
  onlyAvailable?: boolean;
}
export interface ManpowerBoardCell {
  month: string;
  statusName: string;
  totalMm: number;
  projects: { projectName: string; monthlyMm: number }[];
  extensionPossibilityName: string | null;
  winningProbability: number | null;
  tentative: boolean;
}
export interface ManpowerBoardPerson {
  developerId: string;
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

export async function getCommonManpowerBoardExcel(
  filter: ManpowerBoardFilter,
): Promise<number> {
  const response = await fetch(`${root}/excel?${manpowerBoardQuery(filter)}`);
  const xlsx =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  if (!response.ok || !response.headers.get("Content-Type")?.includes(xlsx)) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    let errors: { field: string; message: string }[] = [];
    try {
      const body = await response.json();
      message = body.message || message;
      errors = body.errors || [];
    } catch {
      /* JSON이 아닌 오류는 공통 문구로 표시한다. */
    }
    throw new ProjectRequestError(message, errors);
  }
  const count = response.headers.get("X-Total-Count");
  if (count === null || !/^\d+$/.test(count))
    throw new Error("조회 결과 건수를 확인하지 못했습니다.");
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const blob = await response.blob();
  if (!blob.size) throw new Error("엑셀 파일이 비어 있습니다.");
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || "인력 운영 보드.xlsx";
  document.body.appendChild(link);
  try {
    link.click();
  } finally {
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(href), 1000);
  }
  return Number(count);
}

import { projectRequest } from "../projects/projects";

export type BoardSignal = "정상" | "주의" | "위험";
export interface ProjectBoardFilter {
  signal?: BoardSignal;
  contractTypeCode?: string;
  salesPartId?: string;
}
export interface ProjectBoardRow {
  projectId: string;
  projectName: string;
  contractTypeCode: string;
  signal: BoardSignal;
  signalReasons: string[];
  reasonSummary: string;
  pmUserId: string | null;
  pmName: string | null;
  currentStepName: string | null;
  stepDelayed: boolean;
  plannedProgress: number | null;
  actualProgress: number | null;
  costUsageRate: number | null;
  costProgressGap: number | null;
  openIssueCount: number;
  overdueIssueCount: number;
  latestMemoDate: string | null;
  daysWithoutMemo: number | null;
  stale: boolean;
}
export interface ProjectBoardResult {
  summary: {
    progress: {
      total: number;
      normal: number;
      caution: number;
      danger: number;
    };
    riskCount: number;
    openIssues: { total: number; high: number };
    overdueIssueCount: number;
    staleProjectCount: number;
  };
  staleProjects: {
    projectId: string;
    projectName: string;
    daysWithoutMemo: number | null;
  }[];
  items: ProjectBoardRow[];
  calculationMethod: string;
}
const query = (filter: ProjectBoardFilter) => {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== "") params.set(key, value);
  }
  return params.toString();
};
const root = "/api/common/project-board";
export async function getCommonProjectBoard(
  filter: ProjectBoardFilter,
  signal?: AbortSignal,
) {
  const result = await projectRequest<ProjectBoardResult>(
    `${root}?${query(filter)}`,
    { signal },
  );
  if (
    !result?.summary?.progress ||
    !result.summary.openIssues ||
    !Array.isArray(result.items) ||
    !Array.isArray(result.staleProjects) ||
    typeof result.calculationMethod !== "string"
  )
    throw new Error("현황판 응답을 확인하지 못했습니다. 다시 시도해 주세요.");
  return result;
}

export async function getCommonProjectBoardExcel(filter: ProjectBoardFilter) {
  const response = await fetch(`${root}/excel?${query(filter)}`);
  if (
    !response.ok ||
    !response.headers
      .get("Content-Type")
      ?.includes(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      )
  ) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    try {
      message = (await response.json()).message || message;
    } catch {
      /* 기본 안내 유지 */
    }
    throw new Error(message);
  }
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const href = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = href;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || "프로젝트 현황판.xlsx";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 1000);
}

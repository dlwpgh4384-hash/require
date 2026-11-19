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
  /** signalReasons와 같은 순서의 등급(위험·주의). */
  signalReasonLevels?: ("위험" | "주의")[];
  /** 마지막 업데이트(마지막 주간 메모 작성일, 없으면 단계를 처음 만든 날). */
  lastUpdateDate?: string | null;
  /** 이번 달 영업 파트(HQ = 본사 직접 영업, null = 미지정). */
  salesPartId?: string | null;
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
    !Array.isArray(result.items)
  )
    throw new Error("현황판 응답을 확인하지 못했습니다. 다시 시도해 주세요.");
  return {
    ...result,
    staleProjects: result.staleProjects ?? [],
    calculationMethod: result.calculationMethod ?? "",
  };

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

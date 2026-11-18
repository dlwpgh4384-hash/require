import { projectRequest } from "../projects/projects";

export type DifferenceStatus = "부족" | "초과" | "맞음";
export interface ManpowerComparisonFilter {
  year: number;
  projectId?: string;
  differenceStatus?: DifferenceStatus;
}
export interface ManpowerComparisonRow {
  projectId: string;
  projectName: string;
  jobCode: string;
  jobName: string;
  plannedMm: number;
  actualMm: number;
  differenceMm: number;
  differenceStatus: DifferenceStatus;
}
export interface ManpowerComparisonResult {
  items: ManpowerComparisonRow[];
  totals: { plannedMm: number; actualMm: number; differenceMm: number };
}
const root = "/api/common/manpower-comparisons";
const query = (filter: ManpowerComparisonFilter) => {
  const params = new URLSearchParams({ year: String(filter.year) });
  if (filter.projectId !== undefined) params.set("projectId", filter.projectId);
  if (filter.differenceStatus !== undefined)
    params.set("differenceStatus", filter.differenceStatus);
  return params;
};
export function getCommonManpowerComparisons(
  filter: ManpowerComparisonFilter,
  signal?: AbortSignal,
) {
  return projectRequest<ManpowerComparisonResult>(`${root}?${query(filter)}`, {
    signal,
  });
}

const XLSX =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
/**
 * SFR-011 엑셀 다운로드: 목록과 같은 조건의 전체 결과를 서버가 만든 .xlsx로 받아 저장한다.
 * 파일 이름은 서버 Content-Disposition을 따르고, 조회 결과 건수(X-Total-Count)를 돌려준다.
 */
export async function getCommonManpowerComparisonsExcel(
  filter: ManpowerComparisonFilter,
): Promise<number> {
  const response = await fetch(`${root}/excel?${query(filter)}`);
  if (!response.ok || !response.headers.get("Content-Type")?.includes(XLSX)) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    try {
      message = (await response.json()).message || message;
    } catch {
      /* 본문이 JSON이 아니면 기본 문구 */
    }
    throw new Error(message);
  }
  const count = response.headers.get("X-Total-Count");
  if (count === null || !/^\d+$/.test(count))
    throw new Error("조회 결과 건수를 확인하지 못했습니다.");
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || "인원 계획 대비 투입.xlsx";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 1000);
  return Number(count);
}

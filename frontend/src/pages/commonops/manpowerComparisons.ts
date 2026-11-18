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
export function getCommonManpowerComparisons(
  filter: ManpowerComparisonFilter,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ year: String(filter.year) });
  if (filter.projectId !== undefined) params.set("projectId", filter.projectId);
  if (filter.differenceStatus !== undefined)
    params.set("differenceStatus", filter.differenceStatus);
  return projectRequest<ManpowerComparisonResult>(
    `/api/common/manpower-comparisons?${params}`,
    { signal },
  );
}

import { downloadFile } from "../../shared/list/download";
import { projectRequest, ProjectRequestError } from "../projects/projects";

export interface ManpowerForecastMonth {
  month: string;
  supply: number;
  demand: number;
  unfilledDemand: number;
  difference: number;
}
export interface ManpowerForecastResult {
  months: ManpowerForecastMonth[];
  summary: {
    lastMonthSupply: number;
    lastMonthDemand: number;
    mostShortage: { jobName: string; maximumShortage: number } | null;
  };
  thresholds: { shortageThreshold: number; surplusThreshold: number };
}
const root = "/api/common/manpower-forecasts";
export const getCommonManpowerForecasts = (
  year: number,
  signal?: AbortSignal,
) =>
  projectRequest<ManpowerForecastResult>(
    `${root}?${new URLSearchParams({ year: String(year) })}`,
    { signal },
  );

export async function getCommonManpowerForecastsExcel(
  year: number,
): Promise<number> {
  let count = 0;
  await downloadFile(
    `${root}/excel?${new URLSearchParams({ year: String(year) })}`,
    `인력 수요 예측_${year}.xlsx`,
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

import { formatDecimal } from "../../shared/list";
import { downloadFile } from "../../shared/list/download";
import { projectRequest, ProjectRequestError } from "../projects/projects";

export interface ManpowerForecastMonth {
  month: string;
  supply: number;
  demand: number;
  unfilledDemand: number;
  /** 서버가 보내면 그대로 쓴다: round1(가용) − round1(필요). 없으면 화면이 같은 식으로 계산한다. */
  difference?: number | null;
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
/** 소수 첫째 자리 HALF_UP(서버·엑셀과 같게). 서버 값(소수 둘째 자리)을 화면 숫자로 바꾼다. */
export const round1 = (value: number) =>
  Number(formatDecimal(value).replace("−", "-"));
/** 차이 = 화면에 보이는 두 값(소수 첫째 자리)의 차. 인력 수요 예측 표와 인력 운영 보드 「수요·공급」 표가 같은 글을 보이게 한다. */
export function forecastDifference(month: {
  supply: number;
  demand: number;
  difference?: number | null;
}) {
  return month.difference != null
    ? round1(month.difference)
    : round1(round1(month.supply) - round1(month.demand));
}
/** 시안 sdTotTable: 「맞음」·「x.x명 부족」·「x.x명 남음」. */
export const forecastDifferenceText = (d: number) =>
  Math.abs(d) < 0.05
    ? "맞음"
    : `${formatDecimal(Math.abs(d))}명 ${d < 0 ? "부족" : "남음"}`;
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

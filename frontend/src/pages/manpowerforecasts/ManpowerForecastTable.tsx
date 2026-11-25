import { formatDecimal } from "../../shared/list";
import {
  forecastDifference,
  forecastDifferenceText,
  type ManpowerForecastResult,
} from "./manpowerForecasts";

/** 시안 sdTotTable 색: 기준(운영 기준값)보다 더 부족하면 bad(빨강), 더 남으면 warn(주황). */
export function manpowerForecastDifference(
  value: number,
  thresholds: ManpowerForecastResult["thresholds"],
) {
  return value < -thresholds.shortageThreshold
    ? "bad"
    : value > thresholds.surplusThreshold
      ? "warn"
      : undefined;
}
export function ManpowerForecastTable({
  months,
  thresholds,
}: Pick<ManpowerForecastResult, "months" | "thresholds">) {
  return (
    <div className="ls-twrap manpower-forecast-free">
      <table className="ls-t" aria-label="월별 전체 인력">
        <thead>
          <tr>
            <th scope="col" className="ls-sc">
              구분
            </th>
            {months.map((m) => (
              <th key={m.month} scope="col" className="ls-right">
                {m.month}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr data-testid="manpower-forecast-row-demand">
            <td className="ls-sc">필요 인력</td>
            {months.map((m) => (
              <td key={m.month} className="ls-right">
                {formatDecimal(m.demand)}명
              </td>
            ))}
          </tr>
          <tr data-testid="manpower-forecast-row-supply">
            <td className="ls-sc">가용 인력</td>
            {months.map((m) => (
              <td key={m.month} className="ls-right">
                {formatDecimal(m.supply)}명
              </td>
            ))}
          </tr>
          <tr data-testid="manpower-forecast-row-difference">
            <td className="ls-sc">
              <strong>차이</strong>
            </td>
            {months.map((m) => {
              const d = forecastDifference(m);
              return (
                <td key={m.month} className="ls-right">
                  <span className={manpowerForecastDifference(d, thresholds)}>
                    {forecastDifferenceText(d)}
                  </span>
                </td>
              );
            })}
          </tr>
        </tbody>
      </table>
    </div>
  );
}

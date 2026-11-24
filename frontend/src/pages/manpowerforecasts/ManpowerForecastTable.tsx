import { formatDecimal } from "../../shared/list";
import type {
  ManpowerForecastMonth,
  ManpowerForecastResult,
} from "./manpowerForecasts";

export function manpowerForecastDifference(
  value: number,
  thresholds: ManpowerForecastResult["thresholds"],
) {
  return value < -thresholds.shortageThreshold
    ? "shortage"
    : value > thresholds.surplusThreshold
      ? "surplus"
      : "balanced";
}
export function ManpowerForecastTable({
  months,
  thresholds,
}: Pick<ManpowerForecastResult, "months" | "thresholds">) {
  const rows: { field: "demand" | "supply" | "difference"; label: string }[] = [
    { field: "demand", label: "필요 인력" },
    { field: "supply", label: "가용 인력" },
    { field: "difference", label: "차이" },
  ];
  const difference = (m: ManpowerForecastMonth) => {
    const magnitude = formatDecimal(Math.abs(m.difference));
    return magnitude === "0.0"
      ? "맞음"
      : `${magnitude}명 ${m.difference < 0 ? "부족" : "남음"}`;
  };
  return (
    <div className="manpower-forecast-table-scroll">
      <table className="manpower-forecast-table" aria-label="월별 전체 인력">
        <thead>
          <tr>
            <th scope="col">구분</th>
            {months.map((m) => (
              <th key={m.month} scope="col">
                {m.month}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.field}
              data-testid={`manpower-forecast-row-${row.field}`}
            >
              <th scope="row">{row.label}</th>
              {months.map((m) => (
                <td
                  key={m.month}
                  className={
                    row.field === "difference"
                      ? manpowerForecastDifference(m.difference, thresholds)
                      : undefined
                  }
                >
                  {row.field === "difference"
                    ? difference(m)
                    : `${formatDecimal(m[row.field])}명`}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

import { formatDecimal } from "../../shared/list";
import {
  axisScale,
  ChartLegend,
  chartColor,
} from "../profitstructures/ProfitStructureCharts";
import type { ManpowerForecastMonth } from "./manpowerForecasts";

export function ManpowerForecastBars({
  months,
}: {
  months: ManpowerForecastMonth[];
}) {
  const series = [
    {
      name: "가용 인력",
      color: chartColor.c1,
      values: months.map((m) => m.supply),
    },
    {
      name: "필요 인력",
      color: chartColor.c3,
      values: months.map((m) => m.demand),
    },
  ];
  const W = 640,
    H = 220,
    PL = 52,
    PR = 16,
    PT = 12,
    PB = 28;
  const values = series.flatMap((s) => s.values);
  const { high, ticks } = axisScale(values.some((v) => v > 0) ? values : [1]);
  const y = (value: number) => PT + (H - PT - PB) * (1 - value / high);
  const gw = (W - PL - PR) / Math.max(1, months.length);
  const bw = Math.min(22, (gw - 12) / series.length);
  return (
    <section className="profit-card" aria-label="월별 가용 vs 필요">
      <div className="profit-card-h">
        <h2>월별 가용 vs 필요</h2>
        <span className="ls-cap">단위: 명</span>
      </div>
      <svg
        className="profit-chart"
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label="월별 가용 인력, 필요 인력 비교"
        data-testid="manpower-forecast-chart"
      >
        {ticks.map((value) => (
          <g key={value}>
            <line
              className={value === 0 ? "zl" : "gl"}
              x1={PL}
              x2={W - PR}
              y1={y(value)}
              y2={y(value)}
            />
            <text className="ax" x={PL - 8} y={y(value) + 4} textAnchor="end">
              {formatDecimal(value)}
            </text>
          </g>
        ))}
        {months.map((m, index) => {
          const cx = PL + gw * index + gw / 2;
          return (
            <g key={m.month} data-testid={`manpower-forecast-bars-${m.month}`}>
              <text className="ax" x={cx} y={H - 8} textAnchor="middle">
                {Number(m.month.slice(5))}월
              </text>
              {series.map((s, j) => {
                const value = Math.max(0, s.values[index]);
                return (
                  <rect
                    key={s.name}
                    x={cx - bw + bw * j + 1}
                    y={y(value)}
                    width={Math.max(0, bw - 2)}
                    height={Math.max(0, y(0) - y(value))}
                    rx={2}
                    style={{ fill: s.color }}
                  >
                    <title>{`${m.month} ${s.name} ${formatDecimal(value)}명${j === 1 ? ` · 그중 미충원 ${formatDecimal(m.unfilledDemand)}명` : ""}`}</title>
                  </rect>
                );
              })}
            </g>
          );
        })}
      </svg>
      <ChartLegend items={series} />
      <div className="manpower-forecast-unfilled">
        {months.map((m) => (
          <span
            key={m.month}
            data-testid={`manpower-forecast-unfilled-${m.month}`}
          >
            {m.month} · 그중 미충원 {formatDecimal(m.unfilledDemand)}명
          </span>
        ))}
      </div>
    </section>
  );
}

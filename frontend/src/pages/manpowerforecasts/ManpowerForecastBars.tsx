import { useLayoutEffect, useRef, useState } from "react";
import { formatDecimal } from "../../shared/list";
import {
  axisScale,
  ChartLegend,
  chartColor,
} from "../profitstructures/ProfitStructureCharts";
import { round1, type ManpowerForecastMonth } from "./manpowerForecasts";
type ForecastBarsMonth = Omit<ManpowerForecastMonth, "unfilledDemand"> & {
  unfilledDemand?: number;
};

/** 시안 barChart lab: 막대 묶음 위 두 줄 「필요 x.x」·「그중 미충원 x.x」(6달 넘으면 「미충원 x.x」). */
export function forecastBarLabels(months: ForecastBarsMonth[]) {
  return months.map((m) => [
    `필요 ${formatDecimal(m.demand)}`,
    m.unfilledDemand == null
      ? ""
      : `${months.length > 6 ? "" : "그중 "}미충원 ${formatDecimal(m.unfilledDemand)}`,
  ]);
}
export function ManpowerForecastBars({
  months,
}: {
  months: ForecastBarsMonth[];
}) {
  const series = [
    {
      name: "가용 인력",
      color: chartColor.c1,
      values: months.map((m) => round1(m.supply)),
    },
    {
      name: "필요 인력",
      color: chartColor.c2,
      values: months.map((m) => round1(m.demand)),
    },
  ];
  const labels = forecastBarLabels(months);
  const W = 640,
    H = 220,
    PL = 52,
    PR = 16,
    PT = 38,
    PB = 28;
  // 시안 fitCharts(): 이 그래프만 폭과 상관없이 글자를 화면 13px로 맞춘다(svg 단위 = 13 × viewBox 폭 ÷ 그려진 폭).
  const svgRef = useRef<SVGSVGElement>(null);
  const [unit, setUnit] = useState<number | null>(null);
  useLayoutEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    const measure = () => {
      const width = svg.getBoundingClientRect().width;
      setUnit(width > 0 ? W / width : null);
    };
    measure();
    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(measure);
    observer.observe(svg);
    return () => observer.disconnect();
  }, []);
  const fit = unit === null ? null : 13 * unit;
  const fs = fit ?? (months.length > 6 ? 9 : 11);
  const textStyle = fit === null ? undefined : { fontSize: `${fit}px` };
  const values = series.flatMap((s) => s.values);
  const { high, ticks } = axisScale(values.some((v) => v > 0) ? values : [1]);
  const y = (value: number) => PT + (H - PT - PB) * (1 - value / high);
  const gw = (W - PL - PR) / Math.max(1, months.length);
  const bw = Math.min(22, (gw - 12) / series.length);
  return (
    <section className="profit-card" aria-label="월별 가용 vs 필요">
      <div className="profit-card-h">
        <h2>월별 가용 vs 필요</h2>
        <span className="ls-cap">단위: 명(MM)</span>
      </div>
      <svg
        ref={svgRef}
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
            <text
              className="ax"
              x={PL - 8}
              y={y(value) + 4}
              textAnchor="end"
              style={textStyle}
            >
              {value.toFixed(0)}
            </text>
          </g>
        ))}
        {months.map((m, index) => {
          const cx = PL + gw * index + gw / 2;
          const top = Math.min(
            ...series.map((s) => y(Math.max(0, s.values[index]))),
          );
          const lines = labels[index];
          return (
            <g key={m.month} data-testid={`manpower-forecast-bars-${m.month}`}>
              <text
                className="ax"
                x={cx}
                y={H - 8}
                textAnchor="middle"
                style={textStyle}
              >
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
                    <title>{`${m.month} ${s.name} ${formatDecimal(value)}명`}</title>
                  </rect>
                );
              })}
              <text
                className="ax blab"
                data-testid={`manpower-forecast-label-${m.month}`}
                x={cx}
                y={top - 4 - (lines.length - 1) * (fs + 2)}
                textAnchor="middle"
                style={{ fontSize: `${fs}px` }}
              >
                {lines.map((line, q) => (
                  <tspan key={q} x={cx} dy={q ? fs + 2 : 0}>
                    {line}
                  </tspan>
                ))}
              </text>
            </g>
          );
        })}
      </svg>
      <ChartLegend items={series} />
    </section>
  );
}

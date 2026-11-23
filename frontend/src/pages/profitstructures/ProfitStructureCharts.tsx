import { formatKoreanMoney } from "../../shared/list";
import type {
  Amount,
  ProfitCompositionRow,
  ProfitWaterfall,
} from "./profitStructures";
import { percentage } from "./profitStructureFormulas";

/** 시안 차트 색(--c1 파랑 · --c2 주황 · --c3 초록 · --c4 보라 · --c6 회색). 값은 profitStructures.css. */
export const chartColor = {
  c1: "var(--pf-c1)",
  c2: "var(--pf-c2)",
  c3: "var(--pf-c3)",
  c4: "var(--pf-c4)",
  c6: "var(--pf-c6)",
  bad: "var(--ls-bad)",
} as const;

export interface ChartSeries {
  name: string;
  color: string;
  values: Amount[];
  dash?: boolean;
}

/** 시안 niceMax(): 눈금 맨 위 값을 1·1.2·1.5·2·2.5·3·4·5·6·8·10 × 10ⁿ으로 올린다. */
function niceMax(value: number) {
  if (!(value > 0)) return 1;
  const power = 10 ** Math.floor(Math.log10(value));
  for (const m of [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10])
    if (value <= m * power) return m * power;
  return 10 * power;
}
/**
 * 세로축 범위와 눈금. 모든 값이 0 이상이면 시안과 같다(0부터 niceMax까지 4칸).
 * 손실 같은 음수가 있으면 0을 반드시 지나는 깔끔한 간격으로 눈금을 잡되, 6칸 안에서 가장 촘촘한 간격을 골라
 * 0 아래(마이너스) 칸이 꼭 필요한 만큼만 생기게 한다.
 */
const MAX_STEPS = 6;
export function axisScale(values: number[], headroom = 1.05) {
  const top = Math.max(0, ...values) * headroom;
  const bottom = Math.min(0, ...values);
  if (bottom >= 0) {
    // 자료가 없어 모두 0이면 눈금이 「0 0 0 0」으로 겹치지 않게 0~4백만 원으로 둔다.
    const high = top > 0 ? niceMax(top) : 4e6;
    return { low: 0, high, ticks: [0, 1, 2, 3, 4].map((k) => (high * k) / 4) };
  }
  let step = niceMax((top - bottom) / MAX_STEPS);
  const fit = (size: number) => ({
    low: Math.floor(bottom / size) * size,
    high: Math.max(size, Math.ceil(top / size) * size),
  });
  // 바닥·천장을 간격 단위로 올리고 내리면 칸이 늘 수 있으므로, 6칸 안에 들 때까지 다음 깔끔한 간격으로 넓힌다.
  while ((fit(step).high - fit(step).low) / step > MAX_STEPS + 1e-9)
    step = niceMax(step * 1.0001);
  const { low, high } = fit(step);
  const ticks: number[] = [];
  for (let value = low; value <= high + step / 2; value += step)
    ticks.push(Math.round(value / step) * step);
  return { low, high, ticks };
}
/** 백만 원 단위 눈금 글자(시안 Math.round(v/1e6).toLocaleString). */
const million = (value: number) =>
  (Math.round(value / 1e6) + 0).toLocaleString("ko-KR"); // + 0: 「-0」을 「0」으로
const numbers = (values: Amount[]) =>
  values.filter(
    (value): value is number => value != null && Number.isFinite(value),
  );

/** 시안 legend(): 색 칸 + 이름. 점선 계열은 가는 줄. */
export function ChartLegend({
  items,
}: {
  items: { name: string; color: string; dash?: boolean }[];
}) {
  return (
    <div className="profit-legend">
      {items.map((item) => (
        <span key={item.name}>
          <i
            className={item.dash ? "is-dash" : undefined}
            style={{ background: item.color }}
          />
          {item.name}
        </span>
      ))}
    </div>
  );
}

const W = 640,
  PL = 52,
  PR = 16,
  PT = 12,
  PB = 28;

/** 눈금 줄. 0 줄은 진하게(zl) — 음수가 있어도 어디가 0인지 보인다. */
function Grid({
  ticks,
  y,
  left = PL,
  right = W - PR,
}: {
  ticks: number[];
  y: (value: number) => number;
  left?: number;
  right?: number;
}) {
  return (
    <>
      {ticks.map((value) => (
        <g key={value}>
          <line
            className={value === 0 ? "zl" : "gl"}
            x1={left}
            x2={right}
            y1={y(value)}
            y2={y(value)}
          />
          <text className="ax" x={left - 8} y={y(value) + 4} textAnchor="end">
            {million(value)}
          </text>
        </g>
      ))}
    </>
  );
}

/** 시안 lineChart(): 기간별 꺾은선, 끝점에 동그라미. 값은 원, 눈금은 백만 원. */
export function LineChart({
  series,
  labels,
  height = 220,
  guide,
  testId,
}: {
  series: ChartSeries[];
  labels: string[];
  height?: number;
  guide?: { name: string; values: Amount[] } | null;
  testId?: string;
}) {
  const H = height;
  const values = series.flatMap((item) => numbers(item.values));
  const { low, high, ticks } = axisScale([
    ...values,
    ...(guide ? numbers(guide.values) : []),
  ]);
  const y = (value: number) =>
    PT + (H - PT - PB) * (1 - (value - low) / (high - low));
  const x = (index: number) =>
    PL +
    (W - PL - PR) *
      (labels.length === 1 ? 0.5 : index / Math.max(1, labels.length - 1));
  return (
    <>
      <svg
        className="profit-chart"
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`${series.map((item) => item.name).join(", ")} 추이`}
        data-testid={testId}
      >
        <Grid ticks={ticks} y={y} />
        {labels.map((label, index) => (
          <text
            key={`${label}-${index}`}
            className="ax"
            x={x(index)}
            y={H - 8}
            textAnchor="middle"
          >
            {label}
          </text>
        ))}
        {[
          ...series,
          ...(guide ? [{ ...guide, color: chartColor.c6, dash: true }] : []),
        ].map((item) => {
          const points = item.values
            .map((value, index) =>
              value == null || !Number.isFinite(value)
                ? null
                : [x(index), y(value)],
            )
            .filter((point): point is number[] => point !== null);
          if (!points.length) return null;
          const end = points[points.length - 1];
          return (
            <g key={item.name}>
              <path
                d={`M${points.map((point) => point.join(",")).join("L")}`}
                fill="none"
                style={{ stroke: item.color }}
                strokeWidth={2.2}
                strokeDasharray={item.dash ? "6 4" : undefined}
              />
              <circle
                cx={end[0]}
                cy={end[1]}
                r={4}
                style={{ fill: item.color }}
              />
            </g>
          );
        })}
      </svg>
      <ChartLegend
        items={[
          ...series.map(({ name, color, dash }) => ({ name, color, dash })),
          ...(guide
            ? [{ name: guide.name, color: chartColor.c6, dash: true }]
            : []),
        ]}
      />
    </>
  );
}

/** 시안 barChart(): 기간별 묶음 막대, 참고선(guide)은 점선. 값은 원, 눈금은 백만 원. */
export function BarChart({
  series,
  labels,
  height = 220,
  guide,
  testId,
}: {
  series: ChartSeries[];
  labels: string[];
  height?: number;
  guide?: { name: string; values: Amount[] } | null;
  testId?: string;
}) {
  const H = height;
  const values = series.flatMap((item) => numbers(item.values));
  const guideValues = guide ? numbers(guide.values) : [];
  const { low, high, ticks } = axisScale([...values, ...guideValues]);
  const y = (value: number) =>
    PT + (H - PT - PB) * (1 - (value - low) / (high - low));
  const gw = (W - PL - PR) / Math.max(1, labels.length);
  const bw = Math.min(22, (gw - 12) / Math.max(1, series.length));
  const center = (index: number) => PL + gw * index + gw / 2;
  const guidePoints = guide
    ? guide.values
        .map((value, index) =>
          value == null || !Number.isFinite(value)
            ? null
            : [center(index), y(value)],
        )
        .filter((point): point is number[] => point !== null)
    : [];
  return (
    <>
      <svg
        className="profit-chart"
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`${series.map((item) => item.name).join(", ")} 비교`}
        data-testid={testId}
      >
        <Grid ticks={ticks} y={y} />
        {labels.map((label, index) => (
          <g key={`${label}-${index}`}>
            <text
              className="ax"
              x={center(index)}
              y={H - 8}
              textAnchor="middle"
            >
              {label}
            </text>
            {series.map((item, j) => {
              const value = item.values[index];
              if (value == null || !Number.isFinite(value)) return null;
              const bx = center(index) - (bw * series.length) / 2 + bw * j;
              const top = y(Math.max(0, value)),
                bottom = y(Math.min(0, value));
              return (
                <rect
                  key={item.name}
                  x={bx + 1}
                  y={top}
                  width={Math.max(0, bw - 2)}
                  height={Math.max(0, bottom - top)}
                  rx={2}
                  style={{ fill: item.color }}
                >
                  <title>{`${label} ${item.name} ${Math.round(value).toLocaleString("ko-KR")}원`}</title>
                </rect>
              );
            })}
          </g>
        ))}
        {guidePoints.length > 0 && (
          <path
            d={`M${guidePoints.map((point) => point.join(",")).join("L")}`}
            fill="none"
            style={{ stroke: chartColor.c6 }}
            strokeWidth={2}
            strokeDasharray="6 4"
          />
        )}
      </svg>
      <ChartLegend
        items={[
          ...series.map(({ name, color }) => ({ name, color })),
          ...(guide
            ? [{ name: guide.name, color: chartColor.c6, dash: true }]
            : []),
        ]}
      />
    </>
  );
}

/** 시안 stackChart(): 기간별 쌓은 막대 한 개(아래부터 계열 순서). */
export function StackChart({
  series,
  labels,
  height = 200,
  testId,
}: {
  series: ChartSeries[];
  labels: string[];
  height?: number;
  testId?: string;
}) {
  const H = height;
  const part = (value: Amount | undefined) =>
    value != null && Number.isFinite(value) ? Math.max(0, value) : 0;
  const totals = labels.map((_, index) =>
    series.reduce((sum, item) => sum + part(item.values[index]), 0),
  );
  const { high, ticks } = axisScale(totals);
  const y = (value: number) => PT + (H - PT - PB) * (1 - value / high);
  const gw = (W - PL - PR) / Math.max(1, labels.length);
  const bw = Math.min(34, gw * 0.6);
  return (
    <>
      <svg
        className="profit-chart"
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`${series.map((item) => item.name).join(", ")} 구성`}
        data-testid={testId}
      >
        <Grid ticks={ticks} y={y} />
        {labels.map((label, index) => {
          const cx = PL + gw * index + gw / 2;
          let acc = 0;
          return (
            <g
              key={`${label}-${index}`}
              data-testid={testId ? `${testId}-bar` : undefined}
            >
              <text className="ax" x={cx} y={H - 8} textAnchor="middle">
                {label}
              </text>
              {series.map((item) => {
                const value = part(item.values[index]);
                const top = y(acc + value),
                  bottom = y(acc);
                acc += value;
                return (
                  <rect
                    key={item.name}
                    x={cx - bw / 2}
                    y={top}
                    width={bw}
                    height={Math.max(0, bottom - top)}
                    style={{ fill: item.color }}
                  >
                    <title>{`${label} ${item.name} ${Math.round(value).toLocaleString("ko-KR")}원`}</title>
                  </rect>
                );
              })}
            </g>
          );
        })}
      </svg>
      <ChartLegend items={series.map(({ name, color }) => ({ name, color }))} />
    </>
  );
}

type StepKind = "base" | "delta" | "sub";
/** 시안 UI-07 waterfall 9단계: 이름(줄바꿈 \n), 값, 종류(base·sub=합계 단계, delta=더하거나 빼는 항목). */
export function waterfallSteps(
  w: ProfitWaterfall,
): { label: string; value: Amount; kind: StepKind }[] {
  const minus = (value: Amount) => (value == null ? null : -value);
  return [
    { label: "매출", value: w.revenue, kind: "base" },
    { label: "매출원가", value: minus(w.costOfSales), kind: "delta" },
    { label: "매출\n총이익", value: w.grossProfit, kind: "sub" },
    {
      label: "판관비",
      value: minus(w.sellingAdministrativeExpense),
      kind: "delta",
    },
    { label: "전월 조정", value: w.previousAdjustment, kind: "delta" },
    { label: "영업이익", value: w.operatingProfit, kind: "sub" },
    { label: "영업외\n손익", value: w.nonOperatingProfitLoss, kind: "delta" },
    {
      label: "법인세\n(월할)",
      value: minus(w.monthlyCorporateTax),
      kind: "delta",
    },
    { label: "당기\n순이익", value: w.netProfit, kind: "sub" },
  ];
}

/** 시안 waterfall(): 매출에서 당기순이익까지 흐름(높이 280, 값은 백만 원). 값이 없는 단계(이관)는 「–」. */
export function WaterfallChart({ waterfall }: { waterfall: ProfitWaterfall }) {
  const VW = 720,
    H = 280,
    pl = 56,
    pr = 12,
    pt = 26,
    pb = 44;
  let run = 0;
  const bars = waterfallSteps(waterfall).map((step) => {
    const value = step.value ?? 0;
    let a: number, b: number;
    if (step.kind === "delta") {
      a = run;
      b = run + value;
    } else {
      a = 0;
      b = value;
    }
    run = b;
    return { ...step, a, b };
  });
  const values = bars.flatMap((bar) => [bar.a, bar.b]);
  const scale = axisScale(values, 1.08);
  const { low, high } = scale;
  const y = (value: number) =>
    pt + (H - pt - pb) * (1 - (value - low) / (high - low));
  const gw = (VW - pl - pr) / bars.length,
    bw = Math.min(56, gw * 0.62);
  const ticks = <Grid ticks={scale.ticks} y={y} left={pl} right={VW - pr} />;
  const won = (value: Amount) =>
    value == null
      ? "–"
      : `${(Math.round(value) + 0).toLocaleString("ko-KR")}원`;
  return (
    <>
      <svg
        className="profit-chart"
        viewBox={`0 0 ${VW} ${H}`}
        role="img"
        data-testid="profit-waterfall"
        aria-label={`매출에서 당기순이익까지 흐름: ${bars
          .map((bar) => `${bar.label.replace("\n", " ")} ${won(bar.value)}`)
          .join(", ")}`}
      >
        {ticks}
        {bars.map((bar, index) => {
          const cx = pl + gw * index + gw / 2;
          const top = y(Math.max(bar.a, bar.b)),
            bottom = y(Math.min(bar.a, bar.b));
          const value = bar.value ?? 0;
          const color =
            bar.kind === "delta"
              ? value < 0
                ? chartColor.c2
                : chartColor.c3
              : value < 0
                ? chartColor.bad
                : chartColor.c1;
          const next = pl + gw * (index + 1) + gw / 2;
          return (
            <g key={bar.label} data-testid="profit-waterfall-step">
              <rect
                x={cx - bw / 2}
                y={top}
                width={bw}
                height={Math.max(1, bottom - top)}
                rx={2}
                style={{ fill: color }}
              >
                <title>{`${bar.label.replace("\n", " ")} ${won(bar.value)}`}</title>
              </rect>
              {index < bars.length - 1 && (
                <line
                  x1={cx + bw / 2}
                  x2={next - bw / 2}
                  y1={y(bar.b)}
                  y2={y(bar.b)}
                  style={{ stroke: chartColor.c6 }}
                  strokeDasharray="3 3"
                />
              )}
              <text
                className="ax profit-wf-value"
                x={cx}
                y={top - 6}
                textAnchor="middle"
              >
                {bar.value == null
                  ? "–"
                  : `${bar.kind === "delta" && bar.value > 0 ? "+" : ""}${million(bar.value)}`}
              </text>
              {bar.label.split("\n").map((line, j) => (
                <text
                  key={line}
                  className="ax"
                  x={cx}
                  y={H - pb + 16 + j * 13}
                  textAnchor="middle"
                >
                  {line}
                </text>
              ))}
            </g>
          );
        })}
      </svg>
      <ChartLegend
        items={[
          { name: "합계 단계", color: chartColor.c1 },
          { name: "더하는 항목", color: chartColor.c3 },
          { name: "빼는 항목", color: chartColor.c2 },
        ]}
      />
    </>
  );
}

/** 시안 mixRows(): 이름 · 금액(kor) (비중) · 이익률, 아래 가는 막대(가장 큰 값 = 100%). */
export function MixRows({
  rows,
  testId,
}: {
  rows: ProfitCompositionRow[];
  testId?: string;
}) {
  const max = Math.max(1, ...rows.map((row) => row.revenue ?? 0));
  return (
    <div className="profit-mix" data-testid={testId}>
      {rows.map((row, index) => (
        <div
          key={`${row.name}-${index}`}
          data-testid={testId ? `${testId}-row` : undefined}
        >
          <div className="profit-mix-line">
            <span>{row.name}</span>
            <span className="profit-tnum">
              {formatKoreanMoney(row.revenue)}{" "}
              <span className="ls-cap">({percentage(row.share)})</span> · 이익률{" "}
              <span
                className={
                  row.profitRate != null && row.profitRate < 0
                    ? "ls-neg"
                    : undefined
                }
              >
                {percentage(row.profitRate)}
              </span>
            </span>
          </div>
          <div className="profit-track">
            <div
              className="profit-fill"
              style={{
                width: `${(Math.max(0, row.revenue ?? 0) / max) * 100}%`,
              }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

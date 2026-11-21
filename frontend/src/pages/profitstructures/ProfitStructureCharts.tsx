import { Money, type MoneyUnit } from "../../shared/list";
import type {
  Amount,
  ProfitCompositionRow,
  ProfitStructureResult,
  ProfitTrend,
} from "./profitStructures";
import {
  percentage,
  profitLabels,
  waterfallKeys,
} from "./profitStructureFormulas";

export function ProfitCompositionTable({
  rows,
  unit,
  id,
}: {
  rows: ProfitCompositionRow[];
  unit: MoneyUnit;
  id: string;
}) {
  return (
    <table className="ls-t profit-composition-table" aria-label={id}>
      <thead>
        <tr>
          <th>이름</th>
          <th>매출</th>
          <th>비중</th>
          <th>이익률</th>
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr>
            <td colSpan={4}>조건에 맞는 결과가 없습니다.</td>
          </tr>
        ) : (
          rows.map((row, index) => (
            <tr key={`${row.name}-${index}`} data-testid={`${id}-row-${index}`}>
              <td>{row.name}</td>
              <td>
                <Money value={row.revenue} unit={unit} />
              </td>
              <td>{percentage(row.share)}</td>
              <td>{percentage(row.profitRate)}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}
function TrendLines({
  rows,
  keys,
  unit,
}: {
  rows: ProfitTrend[];
  keys: (keyof ProfitTrend)[];
  unit: MoneyUnit;
}) {
  const numbers = rows.flatMap((row) =>
    keys
      .map((key) => row[key])
      .filter((n): n is number => typeof n === "number"),
  );
  const low = Math.min(0, ...numbers),
    high = Math.max(0, ...numbers),
    span = high - low || 1;
  const x = (i: number) =>
    rows.length < 2 ? 320 : 35 + (i * 570) / (rows.length - 1);
  const y = (value: number) => 185 - ((value - low) / span) * 160;
  return (
    <>
      <svg
        viewBox="0 0 640 225"
        role="img"
        aria-label={keys
          .map((k) => profitLabels[k as keyof typeof profitLabels])
          .join(" · ")}
      >
        <line x1="35" x2="605" y1={y(0)} y2={y(0)} className="profit-axis" />
        {keys.map((key, series) => (
          <g key={key} className={`profit-series profit-series-${series}`}>
            {rows.map((row, i) => {
              const value = row[key];
              const next = rows[i + 1]?.[key];
              return typeof value !== "number" ? null : (
                <g key={row.period}>
                  {typeof next === "number" && (
                    <line
                      x1={x(i)}
                      y1={y(value)}
                      x2={x(i + 1)}
                      y2={y(next)}
                      strokeDasharray={
                        key === "referenceRevenue" ? "6 4" : undefined
                      }
                    />
                  )}
                  <circle cx={x(i)} cy={y(value)} r="3">
                    <title>
                      {row.period}{" "}
                      {profitLabels[key as keyof typeof profitLabels]} {value}
                    </title>
                  </circle>
                </g>
              );
            })}
          </g>
        ))}
        {rows.map((row, i) => (
          <text key={row.period} x={x(i)} y="214" textAnchor="middle">
            {row.period}
          </text>
        ))}
      </svg>
      <div className="profit-chart-values">
        {rows.map((row) => (
          <div
            key={row.period}
            data-testid={`profit-trend-${keys[0]}-${row.period}`}
          >
            <strong>{row.period}</strong>
            {keys.map((key) => (
              <span key={key}>
                {profitLabels[key as keyof typeof profitLabels]}{" "}
                <Money value={row[key] as Amount} unit={unit} />
              </span>
            ))}
          </div>
        ))}
      </div>
    </>
  );
}
export function ProfitStructureCharts({
  data,
  unit,
}: {
  data: ProfitStructureResult;
  unit: MoneyUnit;
}) {
  const range = {
    "data-period-start": data.periodStart,
    "data-period-end": data.periodEnd,
  };
  const deductions = new Set([
    "costOfSales",
    "sellingAdministrativeExpense",
    "monthlyCorporateTax",
  ]);
  const totals = new Set([
    "revenue",
    "grossProfit",
    "operatingProfit",
    "netProfit",
  ]);
  let balance: number | null = 0;
  const steps = waterfallKeys.map((key) => {
    const value = data.waterfall[key];
    const start = totals.has(key) ? 0 : balance;
    const signed = value == null ? null : deductions.has(key) ? -value : value;
    const end = totals.has(key)
      ? value
      : start == null || signed == null
        ? null
        : start + signed;
    balance = end;
    return { key, value, start, end };
  });
  const bounds = steps
    .flatMap((s) => [s.start, s.end])
    .filter((v): v is number => v != null);
  const low = Math.min(0, ...bounds),
    high = Math.max(0, ...bounds),
    span = high - low || 1;
  const y = (v: number) => 200 - ((v - low) / span) * 170;
  const maxCost = Math.max(
    1,
    ...data.trends.map(
      (t) =>
        (t.allocatedCost ?? 0) + (t.idleCost ?? 0) + (t.directExpense ?? 0),
    ),
  );
  return (
    <div className="profit-chart-grid" {...range}>
      <section
        className="profit-panel profit-waterfall"
        aria-label="폭포 차트"
        data-testid="profit-waterfall"
        {...range}
      >
        <h3>매출부터 당기순이익까지</h3>
        <svg
          viewBox="0 0 900 260"
          role="img"
          aria-label="매출부터 당기순이익까지 폭포 차트"
        >
          <line x1="10" x2="890" y1={y(0)} y2={y(0)} className="profit-axis" />
          {steps.map(({ key, value, start, end }, i) => (
            <g key={key} data-testid={`profit-waterfall-${key}`}>
              {start != null && end != null && (
                <rect
                  x={i * 98 + 14}
                  y={Math.min(y(start), y(end))}
                  width="70"
                  height={Math.max(1, Math.abs(y(start) - y(end)))}
                  className={
                    totals.has(key)
                      ? "profit-total"
                      : deductions.has(key)
                        ? "profit-deduction"
                        : "profit-change"
                  }
                />
              )}
              <text x={i * 98 + 49} y="226" textAnchor="middle">
                {profitLabels[key]}
              </text>
              <title>
                {profitLabels[key]} {value ?? "–"}
              </title>
            </g>
          ))}
        </svg>
        <dl className="profit-waterfall-values">
          {waterfallKeys.map((key) => (
            <div key={key}>
              <dt>{profitLabels[key]}</dt>
              <dd>
                <Money value={data.waterfall[key]} unit={unit} />
              </dd>
            </div>
          ))}
        </dl>
      </section>
      <section
        className="profit-panel"
        aria-label="매출·원가 추이"
        data-testid="profit-sales-trend"
        {...range}
      >
        <h3>매출·원가 추이</h3>
        <p>
          매출 · 매출원가
          {data.referenceRevenueVisible && " · 배분 기준 매출(점선 참고선)"}
        </p>
        <TrendLines
          rows={data.trends}
          keys={
            data.referenceRevenueVisible
              ? ["revenue", "costOfSales", "referenceRevenue"]
              : ["revenue", "costOfSales"]
          }
          unit={unit}
        />
      </section>
      <section
        className="profit-panel"
        aria-label="이익 추이"
        data-testid="profit-profit-trend"
        {...range}
      >
        <h3>이익 추이</h3>
        <p>매출총이익 · 영업이익</p>
        <TrendLines
          rows={data.trends}
          keys={["grossProfit", "operatingProfit"]}
          unit={unit}
        />
      </section>
      <section
        className="profit-panel"
        aria-label="원가 구성"
        data-testid="profit-cost-stack"
        {...range}
      >
        <h3>원가 구성</h3>
        <p>배분 원가 · 대기 원가 · 직접 경비</p>
        {data.trends.map((row) => (
          <div
            key={row.period}
            className="profit-cost-row"
            data-testid={`profit-cost-${row.period}`}
          >
            <strong>{row.period}</strong>
            <div
              className="profit-stack"
              role="img"
              aria-label={`${row.period} 원가 구성`}
            >
              {(["allocatedCost", "idleCost", "directExpense"] as const).map(
                (key, i) => (
                  <span
                    key={key}
                    className={`profit-stack-${i}`}
                    style={{
                      width: `${(Math.max(0, row[key] ?? 0) / maxCost) * 100}%`,
                    }}
                    title={`${profitLabels[key]} ${row[key] ?? "–"}`}
                  />
                ),
              )}
            </div>
            {(["allocatedCost", "idleCost", "directExpense"] as const).map(
              (key) => (
                <span key={key}>
                  {profitLabels[key]} <Money value={row[key]} unit={unit} />
                </span>
              ),
            )}
          </div>
        ))}
      </section>
      {data.compositionVisible && data.composition ? (
        <section
          className="profit-panel profit-compositions"
          aria-label="매출 구성"
          {...range}
        >
          {(["contractTypes", "topClients", "projectSalesParts"] as const).map(
            (key) => (
              <div key={key}>
                <h3>{profitLabels[key]} 매출 구성</h3>
                <ProfitCompositionTable
                  rows={data.composition![key]}
                  unit={unit}
                  id={`profit-${key}`}
                />
                {key === "projectSalesParts" && (
                  <p data-testid="profit-sales-part-notice">
                    {data.composition!.salesPartNotice}
                  </p>
                )}
              </div>
            ),
          )}
        </section>
      ) : (
        <p className="profit-panel" data-testid="profit-migration-notice">
          이관이 포함된 기간은 매출 구성과 배분 기준 매출 참고선을 표시하지
          않습니다.
        </p>
      )}
    </div>
  );
}

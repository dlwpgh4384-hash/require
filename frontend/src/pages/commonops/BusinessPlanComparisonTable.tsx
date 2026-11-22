import { useState } from "react";
import { formatMoney, type MoneyUnit } from "../../shared/list";
import type { BusinessPlanComparisonMonth } from "./businessPlanComparisons";

type Field = Exclude<keyof BusinessPlanComparisonMonth, "yearMonth">;
const rows: {
  field: Field;
  label: string;
  action?: string;
  help?: string;
  rate?: boolean;
}[] = [
  {
    field: "revenueTarget",
    label: "매출 목표",
    action: "M25-405",
    help: "목표 업로드 사업계획 엑셀",
  },
  {
    field: "revenueActual",
    label: "매출 실적",
    action: "M25-406",
    help: "영업이익표 매출액(투입 기준, 청구 금액 아님)",
  },
  { field: "achievementRate", label: "달성률", rate: true },
  {
    field: "operatingProfitTarget",
    label: "영업이익 목표",
    action: "M25-407",
    help: "목표 업로드 사업계획 엑셀",
  },
  {
    field: "operatingProfitActual",
    label: "영업이익 실적",
    action: "M25-408",
    help: "영업이익표 영업이익",
  },
  { field: "profitRateDifference", label: "차이(이익률)", rate: true },
];
export const comparisonPercent = (
  value: number | null | undefined,
  suffix = "%",
) => (value == null ? "–" : `${value.toFixed(1)}${suffix}`);

export function BusinessPlanComparisonTable({
  months,
  baseMonth,
  unit,
}: {
  months: BusinessPlanComparisonMonth[];
  baseMonth: string;
  unit: MoneyUnit;
}) {
  const [help, setHelp] = useState<string | null>(null);
  return (
    <div className="ls-twrap business-plan-table-scroll">
      <table
        className="ls-t business-plan-table"
        data-testid="business-plan-table"
      >
        <thead>
          <tr>
            <th scope="col">항목</th>
            {months.map((month) => (
              <th
                key={month.yearMonth}
                scope="col"
                className={
                  month.yearMonth === baseMonth
                    ? "business-plan-current"
                    : undefined
                }
              >
                {Number(month.yearMonth.slice(4))}월
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.field}
              data-testid={`business-plan-row-${row.field}`}
              className={row.rate ? "business-plan-rate" : undefined}
            >
              <th scope="row">
                {row.label}
                {row.help && (
                  <>
                    {" "}
                    <button
                      type="button"
                      className="business-plan-info"
                      data-testid={row.action}
                      aria-label={`${row.label} 출처`}
                      title={row.help}
                      aria-expanded={help === row.field}
                      onClick={() =>
                        setHelp(help === row.field ? null : row.field)
                      }
                    >
                      ⓘ
                    </button>
                    {help === row.field && (
                      <span role="tooltip">{row.help}</span>
                    )}
                  </>
                )}
              </th>
              {months.map((month) => {
                const value = month[row.field];
                const noTarget = row.rate && month.revenueTarget === 0;
                const under =
                  row.field === "achievementRate" &&
                  !noTarget &&
                  value != null &&
                  value < 100;
                return (
                  <td
                    key={month.yearMonth}
                    data-testid={`business-plan-${row.field}-${month.yearMonth}`}
                    title={noTarget ? "목표 없음" : undefined}
                    className={`ls-right${month.yearMonth === baseMonth ? " business-plan-current" : ""}${under ? " business-plan-under" : ""}`}
                  >
                    {row.rate
                      ? comparisonPercent(
                          value,
                          row.field === "profitRateDifference" ? "%p" : "%",
                        )
                      : formatMoney(value, unit)}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

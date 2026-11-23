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
    help: "[목표 업로드]로 올린 사업계획 엑셀의 그 달 매출 목표(연월·매출 목표·영업이익 목표, 1~12월 12줄). 빈칸은 0 = 목표 없음",
  },
  {
    field: "revenueActual",
    label: "매출 실적",
    action: "M25-406",
    help: "재무·자금 › 영업이익표의 그 달 매출액과 같음. 월단위·파견 = 월 단가 × 월 MM 합(식-02), 턴키 = 그 달 청구 공급가액(식-04). 입금 금액이 아님, 부가세 제외",
  },
  { field: "achievementRate", label: "달성률", rate: true },
  {
    field: "operatingProfitTarget",
    label: "영업이익 목표",
    action: "M25-407",
    help: "[목표 업로드]로 올린 사업계획 엑셀의 그 달 영업이익 목표. 빈칸은 0 = 목표 없음",
  },
  {
    field: "operatingProfitActual",
    label: "영업이익 실적",
    action: "M25-408",
    help: "재무·자금 › 영업이익표의 그 달 영업이익과 같음. 매출액 − 매출원가(배분 원가·대기 원가·직접 경비) − 판관비 + 전월 조정",
  },
  { field: "profitRateDifference", label: "차이(이익률)", rate: true },
];
/** 시안 pct: 음수는 「−」(U+2212), 없으면 「–」. */
export const comparisonPercent = (
  value: number | null | undefined,
  suffix = "%",
) =>
  value == null
    ? "–"
    : `${value < 0 ? "−" : ""}${Math.abs(value).toFixed(1)}${suffix}`;
/** 시안 pp: 늘 부호를 붙인다(+1.2%p, −0.8%p). */
const signedPoint = (value: number | null | undefined) =>
  value == null
    ? "–"
    : `${value < 0 ? "−" : "+"}${Math.abs(value).toFixed(1)}%p`;

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
                // 시안: 달성률 100% 미만, 차이(이익률)는 실적 이익률이 목표보다 낮으면 빨강
                const under =
                  !noTarget &&
                  value != null &&
                  ((row.field === "achievementRate" && value < 100) ||
                    (row.field === "profitRateDifference" && value < 0));
                return (
                  <td
                    key={month.yearMonth}
                    data-testid={`business-plan-${row.field}-${month.yearMonth}`}
                    title={noTarget ? "목표 없음" : undefined}
                    className={`ls-right${month.yearMonth === baseMonth ? " business-plan-current" : ""}${under ? " business-plan-under" : ""}`}
                  >
                    {row.field === "profitRateDifference"
                      ? signedPoint(value)
                      : row.rate
                        ? comparisonPercent(value)
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

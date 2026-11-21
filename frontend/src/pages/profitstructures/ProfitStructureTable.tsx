import type { ReactNode } from "react";
import { Badge, Money, type MoneyUnit } from "../../shared/list";
import type { ProfitNumericTable } from "./profitStructures";
import { percentage } from "./profitStructureFormulas";

/**
 * 시안 UI-07 「숫자로 보기」 표: 줄 = 항목, 열 = 기간(맨 오른쪽이 고른 기간, 파란 바탕).
 * 「매출 구성 · …」 머리 줄은 굵게 빈칸, └ 줄은 들여쓰기 회색. 마감 전 기간 머리에는 「잠정」.
 * 열 고르기·쪽 나누기가 없는 고정 보고 표다.
 */
export function ProfitStructureTable({
  table,
  unit,
}: {
  table: ProfitNumericTable;
  unit: MoneyUnit;
}) {
  const last = table.columns.length - 1;
  const cur = (index: number) => (index === last ? " profit-cur" : "");
  return (
    <div className="ls-twrap profit-twrap">
      <table className="ls-t profit-numeric-table" data-testid="profit-numeric-table">
        <thead>
          <tr>
            <th className="ls-sc">항목</th>
            {table.columns.map((column, index) => (
              <th
                key={`${column.label}-${index}`}
                className={`ls-right${cur(index)}`}
                data-testid="profit-numeric-column"
              >
                {column.label}
                {column.provisional && (
                  <>
                    {" "}
                    <Badge kind="warn">잠정</Badge>
                  </>
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {table.rows.map((row, rowIndex) => (
            <tr
              key={`${row.label}-${rowIndex}`}
              className={row.style === "bold" || row.style === "group" ? "profit-b" : undefined}
              data-testid="profit-numeric-row"
            >
              <td className={`ls-sc${row.style === "indent" ? " profit-ind" : ""}`}>
                {row.label}
              </td>
              {table.columns.map((column, index) => {
                if (row.style === "group")
                  return <td key={`${column.label}-${index}`} />;
                const value = row.values[index];
                let text: ReactNode;
                if (value == null) text = <span className="profit-sub">–</span>;
                else if (row.format === "percent") text = percentage(value);
                else text = <Money value={value} unit={unit} />;
                return (
                  <td key={`${column.label}-${index}`} className={`ls-right${cur(index)}`}>
                    {text}
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

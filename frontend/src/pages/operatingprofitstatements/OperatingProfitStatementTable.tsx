import { Badge, formatMoney, type MoneyUnit } from "../../shared/list";
import {
  percentage,
  percentPoint,
} from "../profitstructures/profitStructureFormulas";
import type { OperatingResult } from "./operatingProfitStatements";

export function OperatingProfitStatementTable({
  table,
  unit,
}: {
  table?: OperatingResult["numericTable"];
  unit: MoneyUnit;
}) {
  const columns = table?.columns.length
    ? table.columns
    : [
        ...Array.from({ length: 12 }, (_, i) => ({
          label: `${i + 1}월`,
          working: false,
          provisional: false,
        })),
        { label: "누계", working: false, provisional: false },
        { label: "전년 누계", working: false, provisional: false },
        { label: "전년 대비", working: false, provisional: false },
      ];
  return (
    <div
      className="operating-table-scroll"
      data-testid="operating-table-scroll"
    >
      <table
        className="ls-table operating-table"
        data-testid="operating-numeric-table"
      >
        <thead>
          <tr>
            <th scope="col">항목</th>
            {columns.map((column) => (
              <th scope="col" key={column.label}>
                {column.label}
                {column.working && (
                  <>
                    {" "}
                    <Badge kind="info">작업중</Badge>
                  </>
                )}
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
          {table?.rows.map((row, index) => (
            <tr
              key={row.label}
              className={`operating-${row.style}`}
              data-testid={`operating-row-${index}`}
            >
              <th scope="row">{row.label}</th>
              {row.values.map((cell, c) => {
                const comparison = c === columns.length - 1;
                const bad =
                  comparison &&
                  cell.value != null &&
                  (row.isCost ? cell.value > 0 : cell.value < 0);
                const signed =
                  row.label === "전월 조정" &&
                  cell.value != null &&
                  cell.value > 0;
                return (
                  <td
                    key={columns[c]?.label ?? c}
                    className={bad ? "operating-bad" : ""}
                  >
                    {cell.value == null ? (
                      "–"
                    ) : cell.format === "money" ? (
                      <>
                        {signed ? "+" : ""}
                        {formatMoney(cell.value, unit)}
                      </>
                    ) : cell.format === "percentage-point" ? (
                      percentPoint(cell.value)
                    ) : (
                      percentage(cell.value)
                    )}
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

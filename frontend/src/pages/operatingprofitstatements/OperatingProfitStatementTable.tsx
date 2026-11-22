import type { ReactNode } from "react";
import { Badge, Money, type MoneyUnit } from "../../shared/list";
import {
  percentage,
  percentPoint,
} from "../profitstructures/profitStructureFormulas";
import type { OperatingResult } from "./operatingProfitStatements";

type Table = OperatingResult["numericTable"];
const COMPARISON = "전년 대비";
const fallback: Table["columns"] = [
  ...Array.from({ length: 12 }, (_, i) => ({
    label: `${i + 1}월`,
    working: false,
    provisional: false,
  })),
  { label: "누계", working: false, provisional: false },
];
/** 시안 「전년 대비」: +5.2% · −3.1%. */
const signedPercent = (value: number) =>
  `${value < 0 ? "−" : "+"}${Math.abs(value).toFixed(1)}%`;
/** 시안 plTable 줄 모양: b = 굵게, grp = 회색 묶음, ind = 들여쓰기 회색 글. */
const rowClass: Record<string, string | undefined> = {
  bold: "operating-b",
  group: "operating-grp",
};

/**
 * 시안 UI-08 영업이익표: 줄 = 항목, 열 = 1~12월·누계(·전년 누계·전년 대비).
 * 누계 기준월 열은 파란 바탕, 음수는 빨강, 기준월 뒤 달은 「–」. 서버가 만든 표를 그대로 그린다.
 */
export function OperatingProfitStatementTable({
  table,
  unit,
  baseIndex,
}: {
  table?: Table;
  unit: MoneyUnit;
  /** 누계 기준월 열(0 = 1월). 시안 .cur */
  baseIndex?: number;
}) {
  const columns = table?.columns.length ? table.columns : fallback;
  const cur = (index: number) => (index === baseIndex ? " operating-cur" : "");
  return (
    <div
      className="ls-twrap operating-table-scroll"
      data-testid="operating-table-scroll"
    >
      <table
        className="ls-t operating-table"
        data-testid="operating-numeric-table"
      >
        <thead>
          <tr>
            <th className="ls-sc">항목</th>
            {columns.map((column, index) => (
              <th
                key={column.label}
                className={`ls-right${cur(index)}`}
                data-testid="operating-numeric-column"
              >
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
          {table?.rows.map((row, rowIndex) => (
            <tr
              key={row.label}
              className={rowClass[row.style]}
              data-testid={`operating-row-${rowIndex}`}
              data-style={row.style}
            >
              <td
                className={`ls-sc${row.style === "indent" ? " operating-ind" : ""}`}
              >
                {row.label}
              </td>
              {columns.map((column, index) => {
                const cell = row.values[index];
                const value = cell?.value;
                const comparison = column.label === COMPARISON;
                let text: ReactNode;
                if (value == null)
                  text = <span className="operating-sub">–</span>;
                else if (cell.format === "money")
                  text = <Money value={value} unit={unit} />;
                else {
                  const bad =
                    comparison && cell.format === "percent" && row.isCost
                      ? value > 0
                      : value < 0;
                  const shown =
                    cell.format === "percentage-point"
                      ? percentPoint(value)
                      : comparison
                        ? signedPercent(value)
                        : percentage(value);
                  text = (
                    <span className={bad ? "operating-bad" : undefined}>
                      {shown}
                    </span>
                  );
                }
                return (
                  <td key={column.label} className={`ls-right${cur(index)}`}>
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

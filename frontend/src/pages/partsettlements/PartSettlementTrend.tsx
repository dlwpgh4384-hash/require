import type { ReactNode } from "react";
import { Money, type MoneyUnit } from "../../shared/list";
import type { PartSettlementTrendMonth, PartSettlementTrends } from "./partSettlements";

type Metric = "monthlyMm" | "unitPrice" | "totalCost" | "profit" | "profitShare";
/** 시안 기간 추이 L: 사람마다 5줄. */
const metrics: [Metric, string][] = [
  ["monthlyMm", "M/M"],
  ["unitPrice", "단가"],
  ["totalCost", "비용"],
  ["profit", "이익"],
  ["profitShare", "이익금"],
];
const dash = <span className="part-settlement-sub">–</span>;
export const mmText = (value: number) => Number(value).toFixed(2);

/** 시안 비고(cap): 여러 개면 「 · 」로 잇고, 「⚠」로 시작하는 것은 빨간 글씨. 없으면 「–」. */
export function NoteText({ notes }: { notes: string[] | null | undefined }) {
  const list = (notes ?? []).filter(Boolean);
  if (!list.length) return <>–</>;
  return (
    <>
      {list.map((note, index) => (
        <span key={`${index}-${note}`}>
          {index > 0 && " · "}
          <span className={note.startsWith("⚠") ? "ls-neg" : undefined}>
            {note}
          </span>
        </span>
      ))}
    </>
  );
}

/** 시안 UI-19 「기간 추이」: 이름(5줄 묶음) | 구분 | 1월~기준월 | 합계 | 비고, 맨 아래 파트 이익금. */
export function PartSettlementTrend({
  trend,
  yearMonth,
  partName,
  unit,
  notes,
  loading,
  error,
  unitSelect,
  onDownload,
}: {
  trend: PartSettlementTrends | null;
  yearMonth: string;
  partName: string;
  unit: MoneyUnit;
  /** 사람별 비고(기준월 표의 값). */
  notes: Record<string, string[]>;
  loading: boolean;
  error: string;
  unitSelect: ReactNode;
  onDownload: (() => void) | undefined;
}) {
  const months = trend?.monthlyTotals.map((month) => month.yearMonth) ?? [];
  const people = trend?.items ?? [];
  const value = (key: Metric, amount: number): ReactNode =>
    key === "monthlyMm" ? mmText(amount) : <Money value={amount} unit={unit} />;
  const cell = (key: Metric, month: PartSettlementTrendMonth | undefined) =>
    month && month.monthlyMm > 0 ? value(key, month[key]) : dash;
  const total = (key: Metric, list: PartSettlementTrendMonth[]) =>
    list.reduce((sum, month) => sum + (month.monthlyMm > 0 ? month[key] : 0), 0);
  const cur = (month: string) => (month === yearMonth ? " part-settlement-cur" : "");
  return (
    <div className="ls-root" data-testid="part-settlement-trend-list">
      <div className="ls-tbar">
        <div className="ls-tl" data-testid="part-settlement-trend-toolbar">
          <span>
            {partName} · {yearMonth.slice(0, 4)}-01 ~ {yearMonth} ·{" "}
            <strong>{people.length}</strong>명{" "}
            <span className="ls-cap">(엑셀 「원가표」와 같은 모양)</span>
          </span>
        </div>
        <div className="ls-tr">
          {unitSelect}
          {onDownload && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="part-settlement-trend-excel"
              disabled={!trend || loading || !!error}
              onClick={onDownload}
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
              </svg>{" "}
              엑셀 다운로드
            </button>
          )}
        </div>
      </div>
      {error ? (
        <div className="ls-empty" role="alert" data-testid="part-settlement-trend-error">
          <p>{error}</p>
        </div>
      ) : (
        <div className="ls-twrap">
          <table className="ls-t part-settlement-trend-table">
            <thead>
              <tr>
                <th className="ls-left ls-sc">이름</th>
                <th className="ls-left">구분</th>
                {months.map((month) => (
                  <th key={month} className={`ls-right${cur(month)}`}>
                    {Number(month.slice(5))}월
                  </th>
                ))}
                <th className="ls-right">합계</th>
                <th className="ls-left">비고</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={months.length + 4} role="status">
                    목록을 불러오는 중입니다.
                  </td>
                </tr>
              )}
              {!loading &&
                people.flatMap((person) =>
                  metrics.map(([key, label], index) => (
                    <tr
                      key={`${person.developerId}-${key}`}
                      data-testid={`part-settlement-trend-${person.developerId}-${key}`}
                      className={key === "profitShare" ? "part-settlement-strong" : undefined}
                    >
                      {index === 0 && (
                        <td rowSpan={5} className="ls-left ls-sc part-settlement-top">
                          {person.developerName}
                        </td>
                      )}
                      <td className="ls-cap">{label}</td>
                      {months.map((month) => (
                        <td key={month} className={`ls-right${cur(month)}`}>
                          {cell(
                            key,
                            person.months.find((item) => item.yearMonth === month),
                          )}
                        </td>
                      ))}
                      <td className="ls-right">{value(key, total(key, person.months))}</td>
                      {index === 0 && (
                        <td rowSpan={5} className="ls-cap part-settlement-top">
                          <NoteText notes={notes[person.developerId]} />
                        </td>
                      )}
                    </tr>
                  )),
                )}
              {!loading && trend && !people.length && (
                <tr data-testid="part-settlement-trend-empty">
                  <td colSpan={months.length + 4} className="part-settlement-empty">
                    조건에 맞는 결과가 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
            <tfoot className="ls-foot">
              <tr data-testid="part-settlement-trend-total">
                <td className="ls-left ls-sc">
                  파트 이익금{" "}
                  <span className="ls-cap" title="인력별 이익금 합 − 그 달 법인카드 사용액">
                    ⓘ
                  </span>
                </td>
                <td />
                {(trend?.monthlyTotals ?? []).map((month) => (
                  <td key={month.yearMonth} className="ls-right">
                    <Money value={month.partProfitAmount} unit={unit} />
                  </td>
                ))}
                <td className="ls-right">
                  <Money
                    value={(trend?.monthlyTotals ?? []).reduce(
                      (sum, month) => sum + month.partProfitAmount,
                      0,
                    )}
                    unit={unit}
                  />
                </td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </div>
  );
}

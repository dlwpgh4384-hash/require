import type { PeriodBasis } from "../../shared/period/periodRules";
import { financeText } from "./financeItemFormulas";
import type { FinanceMonth } from "./financeItems";
import { Icon } from "./financeParts";

/**
 * 시안 finStrip(): 「입력 현황」 카드 — [‹] 연도 [›], 안내 글, 1~12월 칸(마감·작업중·건수).
 * 아직 오지 않은 달은 누를 수 없고 「–」로 보인다. 기간 값은 공통 useScreenPeriod가 가진다.
 */
export function FinanceMonthStrip({
  year,
  month,
  basis,
  months,
  disabled,
  onPick,
  onYear,
}: {
  year: number | null;
  month: string;
  basis: PeriodBasis | null;
  months: FinanceMonth["months"];
  disabled: boolean;
  onPick: (month: string) => void;
  onYear: (year: number) => void;
}) {
  const minYear = basis ? Number(basis.cashBaseYm.slice(0, 4)) : 0;
  const maxYear = basis ? Number(basis.latestYm.slice(0, 4)) : 0;
  const keys =
    year == null
      ? []
      : Array.from(
          { length: 12 },
          (_, index) => `${year}${String(index + 1).padStart(2, "0")}`,
        );
  return (
    <section className="fin-card fin-strip" data-testid="finance-month-strip">
      <div className="fin-strip-h">
        <div className="fin-strip-l">
          <div className="fin-ynav">
            <button
              type="button"
              className="ls-ibtn ls-ibtn-sm"
              aria-label="이전 해"
              data-testid="finance-year-prev"
              disabled={disabled || year == null || year <= minYear}
              onClick={() => year != null && onYear(year - 1)}
            >
              <Icon name="chevL" size={18} />
            </button>
            <strong>{year == null ? "–" : `${year}년`}</strong>
            <button
              type="button"
              className="ls-ibtn ls-ibtn-sm"
              aria-label="다음 해"
              data-testid="finance-year-next"
              disabled={disabled || year == null || year >= maxYear}
              onClick={() => year != null && onYear(year + 1)}
            >
              <Icon name="chevR" size={18} />
            </button>
          </div>
          <strong className="fin-strip-t">입력 현황</strong>
        </div>
        <span className="ls-cap">{financeText.stripGuide}</span>
      </div>
      <div className="fin-mstrip" role="tablist" aria-label="입력할 달">
        {keys.map((key) => {
          const info = months.find((value) => value.yearMonth === key);
          const future =
            info?.future ?? (basis ? key > basis.latestYm : false);
          const beforeStart = basis ? key < basis.cashBaseYm : false;
          const closed =
            !future &&
            (info?.closed ?? basis?.closedMonths.includes(key) ?? false);
          const on = key === month;
          const count = info ? ` · ${info.itemCount}건` : "";
          return (
            <button
              type="button"
              role="tab"
              key={key}
              aria-selected={on}
              data-testid={`finance-month-${key}`}
              className={`fin-mchip${on ? " on" : ""}${closed ? " cl" : future ? "" : " wk"}`}
              disabled={future || beforeStart || (disabled && !on)}
              title={future ? financeText.futureMonth : undefined}
              onClick={() => !on && onPick(key)}
            >
              <span>{Number(key.slice(4))}월</span>
              <span className="s">
                {future || beforeStart ? (
                  "–"
                ) : closed ? (
                  <>
                    <Icon name="lock" size={12} /> 마감{count}
                  </>
                ) : (
                  `작업중${count}`
                )}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

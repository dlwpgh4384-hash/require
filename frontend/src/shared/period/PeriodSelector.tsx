import { useId } from "react";
import {
  isAllowed,
  monthRange,
  periodStatus,
  withYear,
  yearRange,
  type PeriodBasis,
  type PeriodOptions,
  type PeriodValue,
} from "./periodRules";
import "./period.css";

export interface PeriodSelectorProps extends PeriodOptions {
  basis: PeriodBasis;
  value: PeriodValue;
  onChange: (value: PeriodValue) => void;
  /** "이 화면의 기준" 한 줄 설명. 각 업무 화면이 넘겨준다. */
  description?: string;
  /** 휴대폰 폭이면 드롭다운만 보여 준다. */
  mobile?: boolean;
  disabled?: boolean;
}

const range = (min: number, max: number) =>
  Array.from({ length: Math.max(0, max - min + 1) }, (_, index) => min + index);

/** FR-0105 공통 기간 선택 부품. 본문 맨 위에 두고, 업무 자료는 조회하지 않는다. */
export function PeriodSelector({
  basis,
  value,
  onChange,
  description,
  mobile = false,
  disabled = false,
  ...options
}: PeriodSelectorProps) {
  const id = useId();
  const years = yearRange(basis, options);
  const status = periodStatus(value, basis, options.kind);
  const change = (next: PeriodValue) => {
    if (!disabled && isAllowed(next, basis, options)) onChange(next);
  };
  const setYear = (year: number) => change(withYear(value, year, basis, options));
  const months =
    options.kind === "year" ? undefined : monthRange(basis, options, value.year);
  const monthLabel = options.kind === "yearCumulative" ? "누계 기준월" : "월";
  return (
    <div
      className={`period-selector${mobile ? " is-mobile" : ""}`}
      data-testid="period-selector"
      data-kind={options.kind}
      role="group"
      aria-labelledby={`${id}-label`}
    >
      <span id={`${id}-label`} className="period-label">
        기간
      </span>
      <div className="period-field">
        {!mobile && (
          <button
            type="button"
            className="period-arrow"
            data-testid="period-year-prev"
            aria-label="이전 연도"
            disabled={disabled || value.year <= years.min}
            onClick={() => setYear(value.year - 1)}
          >
            ‹
          </button>
        )}
        <label className="visually-hidden" htmlFor={`${id}-year`}>
          연도
        </label>
        <select
          id={`${id}-year`}
          data-testid="period-year"
          value={value.year}
          disabled={disabled}
          onChange={(event) => setYear(Number(event.target.value))}
        >
          {range(years.min, years.max).map((year) => (
            <option key={year} value={year}>
              {year}년
            </option>
          ))}
        </select>
        {!mobile && (
          <button
            type="button"
            className="period-arrow"
            data-testid="period-year-next"
            aria-label="다음 연도"
            disabled={disabled || value.year >= years.max}
            onClick={() => setYear(value.year + 1)}
          >
            ›
          </button>
        )}
      </div>
      {months && (
        <div className="period-field">
          {!mobile && (
            <button
              type="button"
              className="period-arrow"
              data-testid="period-month-prev"
              aria-label={`이전 ${monthLabel}`}
              disabled={disabled || (value.month ?? 1) <= months.min}
              onClick={() => change({ year: value.year, month: (value.month ?? 1) - 1 })}
            >
              ‹
            </button>
          )}
          <label className="visually-hidden" htmlFor={`${id}-month`}>
            {monthLabel}
          </label>
          <select
            id={`${id}-month`}
            data-testid="period-month"
            value={value.month}
            disabled={disabled}
            onChange={(event) =>
              change({ year: value.year, month: Number(event.target.value) })
            }
          >
            {range(months.min, months.max).map((month) => (
              <option key={month} value={month}>
                {month}월
              </option>
            ))}
          </select>
          {!mobile && (
            <button
              type="button"
              className="period-arrow"
              data-testid="period-month-next"
              aria-label={`다음 ${monthLabel}`}
              disabled={disabled || (value.month ?? 12) >= months.max}
              onClick={() => change({ year: value.year, month: (value.month ?? 12) + 1 })}
            >
              ›
            </button>
          )}
        </div>
      )}
      <span className="period-badges" aria-live="polite" data-testid="period-status">
        <span
          className={`period-badge${status.readOnly ? " is-locked" : ""}${status.yearBadge === "내년 · 계획" ? " is-plan" : ""}`}
          data-testid="period-year-badge"
        >
          {status.yearBadge}
        </span>
        {status.monthBadge && (
          <span
            className={`period-badge${status.monthBadge === "마감" ? " is-closed" : ""}`}
            data-testid="period-month-badge"
          >
            {status.monthBadge}
          </span>
        )}
      </span>
      {description && (
        <span className="period-description" data-testid="period-description">
          이 화면의 기준: {description}
        </span>
      )}
    </div>
  );
}

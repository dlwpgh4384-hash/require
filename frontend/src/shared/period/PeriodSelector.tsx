import { useId } from "react";
import { Badge } from "../list";
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
  /**
   * 보이는 모양. 주지 않으면 지금 모양(연도·월 상자, 연도 배지, 설명).
   * "monthBox"는 시안 periodBar의 연도+월 화면 모양: 「‹ [2026년 10월 ▾] › 마감/작업중」 한 줄(연도 배지·설명 없음).
   * 연도+월(kind "yearMonth")에서만 쓴다. 휴대폰에서는 드롭다운과 배지만 보인다.
   * "cumulativeBar"는 시안 periodBar의 연도+누계 기준월 모양:
   * 「‹ 2026년 › 누계 기준월 [11월 ▾] 1~11월 누계 · 진행 중/결산 완료」. 누계(kind "yearCumulative")에서만 쓴다.
   */
  variant?: "default" | "monthBox" | "cumulativeBar";
}

const range = (min: number, max: number) =>
  Array.from({ length: Math.max(0, max - min + 1) }, (_, index) => min + index);

const chevron = (d: string) => (
  <svg
    width="18"
    height="18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);

/** 시안 periodBar(연도+월): 고를 수 있는 달 전체를 한 상자에, 앞뒤 화살표와 마감/작업중 배지. */
function MonthBox({
  basis,
  value,
  onChange,
  mobile,
  disabled,
  options,
}: {
  basis: PeriodBasis;
  value: PeriodValue;
  onChange: (value: PeriodValue) => void;
  mobile: boolean;
  disabled: boolean;
  options: PeriodOptions;
}) {
  const years = yearRange(basis, options);
  const all: PeriodValue[] = [];
  for (const year of range(years.min, years.max)) {
    const months = monthRange(basis, options, year);
    for (const month of range(months.min, months.max)) all.push({ year, month });
  }
  const key = (item: PeriodValue) => `${item.year}-${item.month}`;
  const index = all.findIndex((item) => key(item) === key(value));
  const status = periodStatus(value, basis, options.kind);
  const go = (next: PeriodValue | undefined) => {
    if (next && !disabled && isAllowed(next, basis, options)) onChange(next);
  };
  return (
    <div
      className={`period-selector period-month-box${mobile ? " is-mobile" : ""}`}
      data-testid="period-selector"
      data-kind={options.kind}
      data-variant="monthBox"
      role="group"
      aria-label="조회 월"
    >
      {!mobile && (
        <button
          type="button"
          className="year-bar-arrow"
          data-testid="period-month-prev"
          aria-label="이전 달"
          disabled={disabled || index <= 0}
          onClick={() => go(all[index - 1])}
        >
          {chevron("M15 18l-6-6 6-6")}
        </button>
      )}
      <select
        className="period-month-select"
        data-testid="period-month"
        aria-label="조회 월"
        value={key(value)}
        disabled={disabled}
        onChange={(event) =>
          go(all.find((item) => key(item) === event.target.value))
        }
      >
        {all.map((item) => (
          <option key={key(item)} value={key(item)}>
            {item.year}년 {item.month}월
          </option>
        ))}
      </select>
      {!mobile && (
        <button
          type="button"
          className="year-bar-arrow"
          data-testid="period-month-next"
          aria-label="다음 달"
          disabled={disabled || index < 0 || index >= all.length - 1}
          onClick={() => go(all[index + 1])}
        >
          {chevron("M9 18l6-6-6-6")}
        </button>
      )}
      <span aria-live="polite" data-testid="period-month-badge">
        {status.monthBadge === "마감" ? (
          <Badge kind="lock">마감</Badge>
        ) : (
          <Badge kind="info">작업중</Badge>
        )}
      </span>
    </div>
  );
}

/**
 * 시안 periodBar(kind 'year', 누계 화면): ‹ 2026년 › · 누계 기준월 [n월] · 「1~n월 누계」 ·
 * 올해는 「진행 중」, 지난 해는 「결산 완료」. 시작 전(이관) 해는 「월별 손익만 옮겨 온 해 · 읽기 전용」을 덧붙인다.
 */
function CumulativeBar({
  basis,
  value,
  onChange,
  disabled,
  options,
}: {
  basis: PeriodBasis;
  value: PeriodValue;
  onChange: (value: PeriodValue) => void;
  disabled: boolean;
  options: PeriodOptions;
}) {
  const years = yearRange(basis, options);
  const months = monthRange(basis, options, value.year);
  const month = value.month ?? months.max;
  const latestYear = Number(basis.latestYm.slice(0, 4));
  const done = value.year < latestYear;
  const migrated = value.year < Number(basis.cashBaseYm.slice(0, 4));
  const change = (next: PeriodValue) => {
    if (!disabled && isAllowed(next, basis, options)) onChange(next);
  };
  const setYear = (year: number) => change(withYear(value, year, basis, options));
  return (
    <div
      className="period-cumulative-bar"
      data-testid="period-selector"
      data-kind={options.kind}
      data-variant="cumulativeBar"
      role="group"
      aria-label="조회 기간"
    >
      <div className="year-bar-nav">
        <button
          type="button"
          className="year-bar-arrow"
          data-testid="period-year-prev"
          aria-label="이전 해"
          disabled={disabled || value.year <= years.min}
          onClick={() => setYear(value.year - 1)}
        >
          {chevron("M15 18l-6-6 6-6")}
        </button>
        <strong data-testid="period-year">{value.year}년</strong>
        <button
          type="button"
          className="year-bar-arrow"
          data-testid="period-year-next"
          aria-label="다음 해"
          disabled={disabled || value.year >= years.max}
          onClick={() => setYear(value.year + 1)}
        >
          {chevron("M9 18l6-6-6-6")}
        </button>
      </div>
      <label className="period-cumulative-field">
        누계 기준월
        <select
          className="period-month-select"
          data-testid="period-month"
          aria-label="누계 기준월"
          value={month}
          disabled={disabled}
          onChange={(event) =>
            change({ year: value.year, month: Number(event.target.value) })
          }
        >
          {range(months.min, months.max).map((item) => (
            <option key={item} value={item}>
              {item}월
            </option>
          ))}
        </select>
      </label>
      <span className="year-bar-cap" data-testid="period-cumulative-cap">
        1~{month}월 누계
      </span>
      <span aria-live="polite" data-testid="period-status">
        {done ? (
          <Badge kind="lock">결산 완료</Badge>
        ) : (
          <Badge kind="info">진행 중</Badge>
        )}
      </span>
      {migrated && (
        <span className="year-bar-cap">월별 손익만 옮겨 온 해 · 읽기 전용</span>
      )}
    </div>
  );
}

/** FR-0105 공통 기간 선택 부품. 본문 맨 위에 두고, 업무 자료는 조회하지 않는다. */
export function PeriodSelector({
  basis,
  value,
  onChange,
  description,
  mobile = false,
  disabled = false,
  variant = "default",
  ...options
}: PeriodSelectorProps) {
  const id = useId();
  if (variant === "monthBox" && options.kind === "yearMonth")
    return (
      <MonthBox
        basis={basis}
        value={value}
        onChange={onChange}
        mobile={mobile}
        disabled={disabled}
        options={options}
      />
    );
  if (variant === "cumulativeBar" && options.kind === "yearCumulative")
    return (
      <CumulativeBar
        basis={basis}
        value={value}
        onChange={onChange}
        disabled={disabled}
        options={options}
      />
    );
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
            <option key={year} value={year} data-testid={`period-year-${year}`}>
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
              <option
                key={month}
                value={month}
                data-testid={`period-month-${month}`}
              >
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

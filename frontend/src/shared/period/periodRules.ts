// FR-0105 공통 기간 선택 규칙. 업무 자료를 조회하거나 계산하지 않는다.
// 자료가 있는 해·내년 허용 여부는 각 업무 화면이 넘겨준다.

export type PeriodKind = "year" | "yearMonth" | "yearCumulative";

/** 화면별 기간 유형(FR-0105). 목록에 없는 화면은 기간 없음. */
export const PERIOD_KIND_BY_SCREEN: Record<string, PeriodKind> = {
  "UI-01": "year",
  "UI-03": "year",
  "UI-05": "year",
  "UI-11": "year",
  "UI-12": "year",
  "UI-13": "year",
  "UI-17": "year",
  "UI-21": "year",
  "UI-28": "year",
  "UI-04": "yearMonth",
  "UI-10": "yearMonth",
  "UI-14": "yearMonth",
  "UI-16": "yearMonth",
  "UI-19": "yearMonth",
  "UI-20": "yearMonth",
  "UI-07": "yearCumulative",
  "UI-08": "yearCumulative",
  "UI-09": "yearCumulative",
  "UI-18": "yearCumulative",
};

/** 시스템을 쓰기 시작하기 전 해(CASH_BASE_YM 앞)를 고를 수 있는 화면. */
export const BEFORE_START_SCREENS = ["UI-07", "UI-08", "UI-09"];

export interface PeriodBasis {
  /** 가장 최근 달 YYYYMM: 마지막 마감월 다음 달, 마감이 없으면 CASH_BASE_YM */
  latestYm: string;
  cashBaseYm: string;
  closedMonths: string[];
}

export interface PeriodValue {
  year: number;
  month?: number;
}

export interface PeriodOptions {
  kind: PeriodKind;
  /** 업무 화면이 조회해 넘기는 자료가 있는 첫 해·마지막 해 */
  firstDataYear?: number;
  lastDataYear?: number;
  /** 계획 화면이거나 내년까지 걸친 자료가 있을 때 true */
  allowNextYear?: boolean;
  /** UI-07·08·09만 true: CASH_BASE_YM 앞의 해를 고를 수 있다 */
  allowBeforeStart?: boolean;
}

const ym = (value: string) => ({
  year: Number(value.slice(0, 4)),
  month: Number(value.slice(4, 6)),
});

export function latestPeriod(basis: PeriodBasis, kind: PeriodKind): PeriodValue {
  const latest = ym(basis.latestYm);
  return kind === "year" ? { year: latest.year } : latest;
}

export function yearRange(basis: PeriodBasis, options: PeriodOptions) {
  const latestYear = ym(basis.latestYm).year;
  const startYear = ym(basis.cashBaseYm).year;
  const max =
    options.kind === "year" && options.allowNextYear ? latestYear + 1 : latestYear;
  let min = startYear;
  if (options.allowBeforeStart) {
    min = Math.min(startYear, options.firstDataYear ?? startYear - 1);
  } else if (options.firstDataYear !== undefined) {
    min = Math.max(startYear, Math.min(options.firstDataYear, latestYear));
  }
  if (!options.allowBeforeStart && options.lastDataYear !== undefined) {
    // 자료가 있는 해까지. 올해(가장 최근 달의 해)는 언제나 고를 수 있다.
    return { min, max: Math.max(latestYear, Math.min(max, options.lastDataYear)) };
  }
  return { min, max };
}

export function monthRange(
  basis: PeriodBasis,
  options: PeriodOptions,
  year: number,
) {
  const latest = ym(basis.latestYm);
  const start = ym(basis.cashBaseYm);
  const min = !options.allowBeforeStart && year === start.year ? start.month : 1;
  const max = year === latest.year ? latest.month : 12;
  return { min, max };
}

export function isAllowed(
  value: PeriodValue,
  basis: PeriodBasis,
  options: PeriodOptions,
) {
  const years = yearRange(basis, options);
  if (!Number.isInteger(value.year) || value.year < years.min || value.year > years.max)
    return false;
  if (options.kind === "year") return value.month === undefined;
  if (value.month === undefined || !Number.isInteger(value.month)) return false;
  const months = monthRange(basis, options, value.year);
  return value.month >= months.min && value.month <= months.max;
}

/** 연도를 바꿀 때 월을 맞춘다. 누계 화면의 지난 해는 12월(결산 완료). */
export function withYear(
  current: PeriodValue,
  year: number,
  basis: PeriodBasis,
  options: PeriodOptions,
): PeriodValue {
  if (options.kind === "year") return { year };
  const months = monthRange(basis, options, year);
  const latestYear = ym(basis.latestYm).year;
  if (options.kind === "yearCumulative")
    return { year, month: year < latestYear ? 12 : months.max };
  const month = Math.min(Math.max(current.month ?? months.max, months.min), months.max);
  return { year, month };
}

export interface PeriodStatus {
  yearBadge: "올해" | "지난 해 · 읽기 전용" | "내년 · 계획" | "결산 완료";
  monthBadge?: "마감" | "작업중";
  /** 지난 해: 입력·업로드 버튼을 빼는 일은 업무 화면이 한다 */
  readOnly: boolean;
}

export function periodStatus(
  value: PeriodValue,
  basis: PeriodBasis,
  kind: PeriodKind,
): PeriodStatus {
  const latestYear = ym(basis.latestYm).year;
  const readOnly = value.year < latestYear;
  const yearBadge =
    value.year > latestYear
      ? "내년 · 계획"
      : readOnly
        ? kind === "yearCumulative"
          ? "결산 완료"
          : "지난 해 · 읽기 전용"
        : "올해";
  if (kind === "year" || value.month === undefined) return { yearBadge, readOnly };
  const key = `${value.year}${String(value.month).padStart(2, "0")}`;
  return {
    yearBadge,
    readOnly,
    monthBadge: basis.closedMonths.includes(key) ? "마감" : "작업중",
  };
}

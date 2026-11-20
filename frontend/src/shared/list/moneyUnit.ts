/** 시안 unitSel·money(): 금액 표시 단위. 처음 값은 「천 원」. */
export const MONEY_UNITS = ["원", "천 원", "백만 원"] as const;
export type MoneyUnit = (typeof MONEY_UNITS)[number];
export const DEFAULT_MONEY_UNIT: MoneyUnit = "천 원";

const divisor = (unit: MoneyUnit) =>
  unit === "천 원" ? 1e3 : unit === "백만 원" ? 1e6 : 1;

/**
 * 시안 money(v, unit): 단위로 나눠 반올림(Math.round), 천 단위 쉼표, 음수는 「−」.
 * 값이 없으면 「–」.
 */
export function formatMoney(
  value: number | null | undefined,
  unit: MoneyUnit,
): string {
  if (value == null || !Number.isFinite(value)) return "–";
  const n = Math.round(value / divisor(unit));
  return (n < 0 ? "−" : "") + Math.abs(n).toLocaleString("ko-KR");
}

/** 단위로 나눈 값이 음수인지(시안: 음수 금액은 빨간 글씨). */
export function isNegativeMoney(
  value: number | null | undefined,
  unit: MoneyUnit,
): boolean {
  return (
    value != null &&
    Number.isFinite(value) &&
    Math.round(value / divisor(unit)) < 0
  );
}

/** 시안 kor(): 1억 이상은 「x.x억 원」, 아니면 「N만 원」. 음수는 「−」. */
export function formatKoreanMoney(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return "–";
  const abs = Math.abs(value);
  const sign = value < 0 ? "−" : "";
  return abs >= 1e8
    ? `${sign}${(abs / 1e8).toFixed(1)}억 원`
    : `${sign}${Math.round(abs / 1e4).toLocaleString("ko-KR")}만 원`;
}

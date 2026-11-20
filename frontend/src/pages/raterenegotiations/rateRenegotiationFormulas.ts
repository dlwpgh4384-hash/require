import type { FormulaRow } from "../../shared/list";

/** 월 원가 설명에 덧붙이는 말(시안에 없음, 2026-11-21 사용자 결정: 급여 업로드 전 풀 급여 계산을 알림). */
const PRE_UPLOAD = "급여 업로드 전이면 개발자 풀 월 급여로 계산";

const rateText = (target: number | null | undefined) =>
  target == null ? "–" : String(Number(target));

/** 시안 열 머리글 ⓘ(calc) 문구. 목표 이익률은 운영 기준값(TGT_MRGN_RT). */
export function rateRenegotiationColumnHelp(
  target: number | null | undefined,
): Record<string, string> {
  return {
    renewalDate:
      "투입 배정의 종료일. 이 날짜에 계약을 연장하면서 단가를 다시 정함",
    monthlyCost: `그 투입에 배분된 원가(배분 원가). 개인 원가·수익표와 같은 계산. ${PRE_UPLOAD}`,
    profitRate: "(단가 × MM − 월 원가) ÷ (단가 × MM) × 100",
    targetUnitPrice: `월 원가 ÷ MM ÷ (1 − ${rateText(target)}%). 만 원 단위 올림`,
    requiredIncreaseRate: "(목표 단가 − 현재 단가) ÷ 현재 단가 × 100",
    annualProfitIncrease:
      "현재 단가 × 인상률 × MM × 12. 1년 동안 계속 투입한다고 가정",
  };
}

/** 시안 fxBox('UI-32') 표. 문구는 시안 그대로. */
export function rateRenegotiationFormulas(
  target: number | null | undefined,
): FormulaRow[] {
  return [
    [
      "갱신일",
      "투입 배정의 종료일",
      "이 날짜에 계약을 연장하면서 단가를 다시 정합니다",
    ],
    [
      "월 원가",
      "그 투입에 배분된 원가(배분 원가)",
      `개인 원가·수익표와 같은 계산 · ${PRE_UPLOAD}`,
    ],
    ["이익률", "(단가 × MM − 월 원가) ÷ (단가 × MM) × 100", ""],
    [
      "목표 단가",
      `월 원가 ÷ MM ÷ (1 − ${rateText(target)}%)`,
      "만 원 단위 올림",
    ],
    ["필요 인상률", "(목표 단가 − 현재 단가) ÷ 현재 단가 × 100", ""],
    [
      "연 이익 증가",
      "현재 단가 × 인상률 × MM × 12",
      "1년 동안 계속 투입한다고 가정",
    ],
  ];
}

export const rateRenegotiationFormulaFoot =
  "턴키는 계약금액 단위로 협상하므로 빠집니다. 줄을 누르면 그 사람의 상세가 열립니다.";

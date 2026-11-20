import type { FormulaRow } from "../../shared/list";

const won = (value: number) => value.toLocaleString("ko-KR");

/** 시안 fxBox('UI-19') 표. 문구는 시안 그대로, 배분율·공통관리비는 그 달 파트 기준값. */
export function partSettlementFormulas(
  rate: number | null | undefined,
  fee: number | null | undefined,
): FormulaRow[] {
  const rateText = rate == null ? "–" : String(rate);
  const feeText = fee == null ? "–" : won(fee);
  return [
    [
      "MM",
      "프로젝트별 MM을 모두 더함",
      "투입을 시작·끝낸 달은 날짜만큼(예: 15일 시작 → 17/31) · 내부 프로젝트 제외",
    ],
    [
      "단가",
      "프로젝트별 (월 단가 × MM)을 모두 더함",
      "턴키는 배분표의 그 사람 그 달 금액, 배분표가 없으면 그 달 원가 × (계약금액 ÷ 예산) · 무상지원 0",
    ],
    [
      "급여",
      "그 달 실제 지급액",
      "MM과 관계없이 그 달 받은 급여 전부. 투입을 시작한 달은 날짜만큼: 월 급여 × (그 달 일수 − 시작일 + 1) ÷ 그 달 일수 (엑셀 「원가표」 방식)",
    ],
    [
      "공통관리비",
      `파트 기준값(${feeText}원)`,
      "외주(업체 인력)만 제외 — 정규·계약직·프리랜서에 붙음",
    ],
    ["퇴직금", "급여 ÷ 12", "정규직에만"],
    ["비용", "급여 + 공통관리비 + 퇴직금", ""],
    ["이익", "단가 − 비용", "적자면 음수"],
    [
      "이익금",
      `이익 × 이익 배분율(${rateText}%)`,
      "원 단위 버림(음수는 0 쪽으로 자름: −933,333.2 → −933,333) · 배분율은 그 달에 적용되는 파트 기준 이력 값",
    ],
    [
      "파트 이익금",
      "인력별 이익금을 모두 더함(적자 포함) − 법인카드 사용액",
      "",
    ],
    [
      "기간 추이 합계",
      "투입한 달(MM > 0)의 값만 더함",
      "투입 안 한 달은 – · 맨 아래 「파트 이익금」 줄은 달마다 인력별 이익금 합 − 그 달 법인카드 사용액",
    ],
  ];
}

export const partSettlementFormulaFoot =
  "인력은 그 사람의 소속 영업 파트로 모읍니다.";

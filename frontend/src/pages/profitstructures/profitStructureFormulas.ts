import type { FormulaRow } from "../../shared/list";
import type { Amount } from "./profitStructures";

/** 시안 fxBox('UI-07', …) 표. 문구는 시안 그대로. */
export const profitFormulaRows: FormulaRow[] = [
  [
    "기간",
    "월: 그 달 · 분기: 그 분기 안의 달을 더함(진행 중인 분기는 기준월까지) · 연 누계: 1월부터 그 달까지 더함",
    "요약 카드와 흐름 차트는 맨 오른쪽(가장 최근) 기간 기준",
  ],
  [
    "매출총이익(률)",
    "매출 − 매출원가 · 매출총이익 ÷ 매출 × 100",
    "매출원가 = 배분 원가(내부 제외) + 대기 원가 + 직접 경비 (3.4)",
  ],
  [
    "영업이익(률)",
    "매출총이익 − 판관비 ± 전월 조정 · 영업이익 ÷ 매출 × 100",
    "판관비 = 재무 항목 입력 + 간접 인건비 + 연구개발비",
  ],
  [
    "당기순이익",
    "영업이익 + 영업외수익 − 영업외비용 − 법인세(월할)",
    "법인세(월할)는 재무 항목 화면의 연간 예상액에서 계산",
  ],
  [
    "대기 원가 비중",
    "대기 원가 ÷ 매출원가 × 100",
    "투입되지 않은 정규·계약직 인건비가 원가에서 차지하는 몫",
  ],
  [
    "배분 기준 매출(참고선)",
    "월단위·파견 매출 + 턴키 배분표 금액(배분표가 없으면 그 달 원가 × 계약금액 ÷ 예산)",
    "턴키는 청구한 달에만 매출이 잡혀 출렁이므로, 일한 만큼 나눈 금액으로 바꿔 본 선 (3.3)",
  ],
  [
    "매출 구성",
    "계약 유형·고객사·영업 파트별로 투입 매출과 배분 원가를 더함 · 이익률 = (매출 − 배분 원가) ÷ 매출",
    "영업 파트는 프로젝트를 따온 파트 기준(파트 이익금은 사람의 소속 파트 기준, D-31) · 대기 원가·판관비는 나누지 않음",
  ],
];
export const profitFormulaFoot =
  "숫자는 영업이익표·순이익표와 같은 계산입니다.";

/** 시안 pct(): 소수 한 자리 %, 음수는 「−」, 값이 없으면 「–」. */
export const percentage = (value: Amount | undefined) =>
  value == null || !Number.isFinite(value)
    ? "–"
    : `${value < 0 ? "−" : ""}${Math.abs(value).toFixed(1)}%`;
/** 시안 pp(): 비중 차이 「+1.2%p」·「−0.4%p」. */
export const percentPoint = (value: number) =>
  `${value < 0 ? "−" : "+"}${Math.abs(value).toFixed(1)}%p`;
/** 시안 dl()·yy(): (지금 − 비교) ÷ |비교| × 100. 비교 값이 없거나 0이면 null. */
export function changeRate(
  current: Amount | undefined,
  previous: Amount | undefined,
) {
  if (current == null || previous == null || previous === 0) return null;
  return ((current - previous) / Math.abs(previous)) * 100;
}

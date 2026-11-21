import type { FormulaRow } from "../../shared/list";
import type {
  ProfitPeriodRow,
  Amount,
  ProfitWaterfall,
} from "./profitStructures";

export const profitFormulaRows: FormulaRow[] = [
  [
    "매출",
    "월단위·파견 월 매출 + 턴키 청구 공급가액",
    "부가세 제외, 취소 청구 제외. 턴키는 청구일의 달로 집계합니다.",
  ],
  [
    "매출원가",
    "배분 원가 + 대기 원가 + 직접 경비",
    "배분 원가는 내부를 제외한 인건비 몫이며, 직접 경비는 한 번만 더합니다.",
  ],
  [
    "매출총이익(률)",
    "매출 − 매출원가 / 매출총이익 ÷ 매출 × 100",
    "기간 합계로 이익률을 계산합니다. 월별 이익률의 평균이 아닙니다.",
  ],
  ["판관비", "재무 입력 판관비 + 간접 인건비 + 내부 프로젝트 연구개발비"],
  [
    "전월 조정",
    "이전 마감월의 정정분을 이번 달 영업이익에 반영합니다.",
    "저장된 조정금액은 이익 증감 부호입니다. 비용 증가를 다시 반전하지 않고 그대로 더합니다.",
  ],
  ["영업이익(률)", "매출총이익 − 판관비 + 전월 조정 / 영업이익 ÷ 매출 × 100"],
  ["영업외손익", "영업외수익 − 영업외비용"],
  [
    "법인세(월할)",
    "(연간 예상 법인세 − 마감 반영액) ÷ 남은 개월 수",
    "원 단위 버림, 끝자리는 마지막 미마감월에 모읍니다.",
  ],
  ["당기순이익", "영업이익 + 영업외손익 − 법인세(월할)"],
  ["대기 원가 비중", "대기 원가 ÷ 매출원가 × 100"],
  [
    "배분 기준 매출",
    "월단위·파견 매출 + 턴키 배분표 금액",
    "배분표가 없으면 턴키 배분 원가 × 계약금액 ÷ 예산입니다. 실제 매출과 다른 참고선입니다.",
  ],
  [
    "매출 구성",
    "비중 = 구성 매출 ÷ 전체 매출 × 100 / 이익률 = (매출 − 배분 원가) ÷ 매출 × 100",
    "대기 원가와 직접 경비는 차감하지 않습니다. 프로젝트를 따온 영업 파트 기준입니다.",
  ],
  [
    "직전·전년 비교",
    "같은 개월 수의 금액·비중을 비교합니다.",
    "부분 분기는 직전 분기 첫 동일 개월 수와 비교합니다. 비중 차이는 %p입니다.",
  ],
  [
    "마감·이관",
    "마감 고정값은 다시 계산하지 않습니다.",
    "이관 자료의 알 수 없는 값과 분모가 0인 비율은 –입니다. 이관이 섞인 기간의 매출 구성·참고선은 숨깁니다.",
  ],
];
export const profitLabels = {
  period: "기간",
  revenue: "매출",
  grossProfit: "매출총이익",
  grossProfitRate: "매출총이익률",
  operatingProfit: "영업이익",
  operatingProfitRate: "영업이익률",
  idleCostShare: "대기 원가 비중",
  costOfSales: "매출원가",
  sellingAdministrativeExpense: "판관비",
  previousAdjustment: "전월 조정",
  nonOperatingProfitLoss: "영업외손익",
  monthlyCorporateTax: "법인세(월할)",
  netProfit: "당기순이익",
  allocatedCost: "배분 원가",
  idleCost: "대기 원가",
  directExpense: "직접 경비",
  referenceRevenue: "배분 기준 매출",
  contractTypes: "계약 유형별",
  topClients: "고객사 상위 5곳(+그 밖)",
  projectSalesParts: "프로젝트 영업 파트별",
} as const;
export type ProfitColumn = keyof typeof profitLabels;
export const compositionKeys = [
  "contractTypes",
  "topClients",
  "projectSalesParts",
] as const;
export const waterfallKeys: (keyof ProfitWaterfall)[] = [
  "revenue",
  "costOfSales",
  "grossProfit",
  "sellingAdministrativeExpense",
  "previousAdjustment",
  "operatingProfit",
  "nonOperatingProfitLoss",
  "monthlyCorporateTax",
  "netProfit",
];
export const percentage = (value: Amount | undefined) =>
  value == null ? "–" : `${value.toFixed(1)}%`;
export function numericValue(
  row: ProfitPeriodRow,
  key: string,
): Amount | string {
  if (key === "period") return row.period + (row.provisional ? " 잠정" : "");
  if (key in row.summary) return row.summary[key as keyof typeof row.summary];
  if (key in row.waterfall)
    return row.waterfall[key as keyof typeof row.waterfall];
  if (key in row.trend) return row.trend[key as keyof typeof row.trend];
  return null;
}

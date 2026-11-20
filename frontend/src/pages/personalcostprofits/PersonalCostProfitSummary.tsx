import type { ReactNode } from "react";
import type { PersonalCostProfitResult } from "./personalCostProfitTypes";
import { FormulaBox, formatKoreanMoney, type FormulaRow } from "../../shared/list";

/** 시안 pct(): 소수 첫째 자리 %, 음수는 「−」. */
export const percentText = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value)
    ? "–"
    : `${value < 0 ? "−" : ""}${Math.abs(value).toFixed(1)}%`;

/** 시안 kpi(l, v, d): 이름 · 값 · 보조 문구. */
function Kpi({
  label,
  value,
  sub,
}: {
  label: ReactNode;
  value: string;
  sub: string;
}) {
  return (
    <section className="personal-cost-profit-kpi">
      <span className="personal-cost-profit-kpi-l">{label}</span>
      <strong className="personal-cost-profit-kpi-v">{value}</strong>
      <span className="personal-cost-profit-kpi-d">{sub}</span>
    </section>
  );
}

/** 시안 UI-03 요약 5칸. 금액은 kor()(억 원·만 원). */
export function PersonalCostProfitSummary({
  summary,
  shownPeople,
  monthlyAverage,
  turnkeyExcluded,
  baseMonth,
}: {
  summary?: PersonalCostProfitResult["summary"];
  /** 표에 나온 사람 수(휴직 제외). */
  shownPeople: number;
  monthlyAverage: boolean;
  turnkeyExcluded: boolean;
  baseMonth: number;
}) {
  const one = (value: number | undefined) =>
    value == null ? "–" : value.toFixed(1);
  return (
    <div
      className="personal-cost-profit-summary"
      data-testid="personal-cost-profit-summary"
    >
      <Kpi
        label="총 인원"
        value={summary ? `${summary.totalPeople}명` : "–"}
        sub={`휴직 ${summary?.leavePeople ?? "–"}명 포함 · 월평균 가동 ${one(summary?.averageOperatingPeople)} · 가득 ${one(summary?.averageEarningPeople)}`}
      />
      <Kpi
        label={monthlyAverage ? "평균 단가 (월)" : "인당 매출 (누계)"}
        value={summary ? formatKoreanMoney(summary.averageUnitPrice) : "–"}
        sub={`단가 칸이 있는 ${summary?.unitPricePeople ?? "–"}명(0 포함)${turnkeyExcluded ? " · 턴키 제외" : ""}`}
      />
      <Kpi
        label={monthlyAverage ? "평균 원가 (월)" : "인당 원가 (누계)"}
        value={summary ? formatKoreanMoney(summary.averageCost) : "–"}
        sub={`원가합계 ÷ ${shownPeople}명 (휴직 제외)`}
      />
      <Kpi
        label={monthlyAverage ? "평균 영업이익 (월)" : "인당 영업이익 (누계)"}
        value={
          summary ? formatKoreanMoney(summary.averageOperatingProfit) : "–"
        }
        sub={`영업이익 합 ÷ ${shownPeople}명 · 적자 ${summary?.lossPeople ?? "–"}명`}
      />
      <Kpi
        label={
          <>
            가동률 · 가득률{" "}
            <span
              className="personal-cost-profit-info"
              title="가동률 = 그 달 일하는(MM이 있는) 사람 비율 · 가득률 = 월단위·파견으로 청구되는 사람 비율"
            >
              ⓘ
            </span>
          </>
        }
        value={
          summary
            ? `${percentText(summary.operatingRate)} · ${percentText(summary.earningRate)}`
            : "–"
        }
        sub={`1~${baseMonth}월 월평균 · 월평균 재직 직접 인력 ${Math.max(1, summary?.averageDirectPeople ?? 0).toFixed(1)}명 기준(간접 제외, 퇴사자는 퇴사한 달까지)`}
      />
    </div>
  );
}

/** 시안 fxBox('UI-03') 표. 문구는 시안 그대로. */
export function personalCostProfitFormulas(
  year: number,
  baseMonth: number,
  monthlyAverage: boolean,
): FormulaRow[] {
  return [
    [
      "기간",
      monthlyAverage
        ? `1~${baseMonth}월 누계 ÷ 그 사람이 일한 달 수 (월 평균)`
        : `${year}년 1월~${baseMonth}월 매달 값을 더함 (연 누계)`,
      "급여·원가는 그 사람이 일한(급여를 받은) 달만 더함 · 프리랜서·외주는 투입한 달만 · 월 평균은 엑셀 「영업이익현황」(한 달 표)과 비교할 때 씀",
    ],
    [
      "개인별 원가",
      "월 급여 + 그룹장·팀장 수당 + 4대보험(회사) + 퇴직금 적립",
      "엑셀 Q열. 수당은 직위 기준값(팀장·그룹장 정액) · 4대보험(회사)은 급여 업로드 값(국민연금·건강·장기요양 회사분 + 고용 1.15% + 산재 1%), 프리랜서·외주는 없음 · 퇴직금 적립 = (월 급여 + 수당) × 퇴직금 적립률(공통코드, 기본 8.33%), 정규직만",
    ],
    [
      "원가합계",
      "개인별 원가 + 팀비 + 프로젝트경비",
      "엑셀 T열. 팀비·프로젝트경비는 자사 인력 1인당 월 정액 × MM(1.0MM이면 각 5만 원), 외주는 없음. 프로젝트 상세의 추가 직접 경비는 이 표에 넣지 않고 프로젝트 원가 분석·영업이익표에만 들어감",
    ],
    [
      "단가(매출)",
      "프로젝트별 (월 단가 × MM)을 모두 더함",
      "엑셀 I열. 턴키는 그 달 배분표 금액(배분표가 없으면 그 달 배분 원가 × 계약금액 ÷ 예산) · 내부·무상지원·대기는 0 · 턴키 단가 미포함 보기는 턴키 몫을 빼고, 턴키만 하는 사람은 빈칸",
    ],
    [
      "영업이익",
      "단가 − 원가합계",
      "엑셀 U열. 내부 프로젝트·대기 인력은 매출이 없어 영업이익이 −원가로 보입니다. 회사 전체 영업이익표에서는 내부 몫은 연구개발비(판관비), 대기 몫은 대기 원가로 따로 잡혀 이 표의 합계와 다를 수 있습니다.",
    ],
    ["이익률", "영업이익 ÷ 단가 × 100", "단가가 0이면 –"],
    [
      "프로젝트별 배분 원가",
      "개인별 원가 × 그 프로젝트 MM ÷ MM 합계(1.0보다 작으면 1.0) + (그 프로젝트 팀비 + 프로젝트경비) × 그 프로젝트 MM",
      "▸로 펼친 줄. 프리랜서·외주는 개인별 원가도 ÷ MM 합계(지급액 전부를 나눔)",
    ],
    [
      "대기 원가",
      "개인별 원가 × (1.0 − MM 합계)",
      "MM 합계가 1.0보다 작은 정규·계약직만. 투입이 전혀 없으면 원가합계 전부",
    ],
    [
      "평균 단가",
      "단가 합 ÷ 단가 칸이 있는 인원",
      "엑셀 「평균단가」(AVERAGE). 단가 0인 대기·내부 인원도 넣음, 턴키 단가 미포함 보기에서는 턴키만 하는 사람 제외",
    ],
    [
      "평균 원가 · 영업이익",
      "원가합계 합 ÷ 인원 · 영업이익 합 ÷ 인원",
      "엑셀 「평균급여(원가포함)」「영업이익」. 인원은 표에 나온 사람(휴직 제외)",
    ],
    [
      "가동률 · 가득률",
      "가동 인원 ÷ 그 달 재직 직접 인력 × 100 · 가득 인원 ÷ 그 달 재직 직접 인력 × 100 (간접 인력 제외, 식-37)",
      "가동 = 그 달 MM이 있는 사람(무상지원·내부 포함), 가득 = 월단위·파견 투입 인원, 총 인원은 대기·휴직 포함",
    ],
    [
      "인당 공헌이익",
      "직무·등급별 영업이익 합 ÷ 그 칸의 인원",
      "괄호 안 숫자가 인원",
    ],
    [
      "참고 열",
      "원천세 = (월 급여 + 수당) × 3.3%(계약직·프리랜서) · 4대보험(본인)·근로소득세는 급여 업로드 값 · 실지급액 = 월 급여 + 수당 − 4대보험(본인) − 원천세 − 근로소득세(식-17)",
      "원가에 들어가지 않음. 엑셀은 실지급액에서 본인 4대보험을 대부분 빼지 않음(7개 행만 뺌)",
    ],
  ];
}

export function PersonalCostProfitCalculationNote({
  year,
  baseMonth,
  monthlyAverage,
}: {
  year: number;
  baseMonth: number;
  monthlyAverage: boolean;
}) {
  return (
    <FormulaBox
      testId="personal-cost-profit-calculation"
      rows={personalCostProfitFormulas(year, baseMonth, monthlyAverage)}
      foot="열 순서는 기존 「영업이익현황」(B~W열)과 같습니다. 참고 열은 기본으로 숨겨 두었고 [열 설정]에서 켭니다. 단가 합계는 영업이익표의 같은 달 매출과 같습니다. 비율·정액 값은 기준정보에서 바꿀 수 있습니다."
    />
  );
}

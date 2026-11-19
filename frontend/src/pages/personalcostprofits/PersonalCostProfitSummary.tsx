import type { PersonalCostProfitResult } from "./personalCostProfitTypes";
import { won } from "../projects/projects";

export function PersonalCostProfitSummary({
  summary,
}: {
  summary?: PersonalCostProfitResult["summary"];
}) {
  return (
    <div
      className="personal-cost-profit-summary"
      data-testid="personal-cost-profit-summary"
    >
      <section>
        <span>총 인원 (휴직 포함)</span>
        <strong>{summary?.totalPeople ?? "–"}명</strong>
        <small>
          월평균 가동 {summary?.averageOperatingPeople.toFixed(1) ?? "–"}명 ·
          가득 {summary?.averageEarningPeople.toFixed(1) ?? "–"}명
        </small>
      </section>
      <section>
        <span>평균 단가</span>
        <strong>{won(summary?.averageUnitPrice)} 원</strong>
      </section>
      <section>
        <span>평균 원가</span>
        <strong>{won(summary?.averageCost)} 원</strong>
      </section>
      <section>
        <span>평균 영업이익</span>
        <strong>{won(summary?.averageOperatingProfit)} 원</strong>
        <small>적자 {summary?.lossPeople ?? "–"}명</small>
      </section>
      <section>
        <span>가동률 · 가득률</span>
        <strong>
          {summary?.operatingRate.toFixed(1) ?? "–"}% ·{" "}
          {summary?.earningRate.toFixed(1) ?? "–"}%
        </strong>
        <small title="가동률은 어느 프로젝트든 일하는 직접 인력 비율, 가득률은 월단위·파견으로 청구되는 직접 인력 비율입니다. 월평균 인원을 소수 첫째 자리로 반올림한 뒤 계산합니다.">
          뜻 도움말 ⓘ
        </small>
      </section>
    </div>
  );
}
export function PersonalCostProfitCalculationNote() {
  return (
    <details className="personal-cost-profit-note">
      <summary data-testid="personal-cost-profit-calculation">
        계산 방법
      </summary>
      <p>
        내부 프로젝트·대기 인력은 매출이 없어 영업이익이 −원가로 보입니다. 회사
        전체 영업이익표에서는 내부 몫은 연구개발비(판관비), 대기 몫은 대기
        원가로 따로 잡혀 이 표의 합계와 다를 수 있습니다.
      </p>
    </details>
  );
}

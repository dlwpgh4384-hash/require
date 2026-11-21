import type { ReactNode } from "react";
import {
  Badge,
  KpiCard,
  KpiCards,
  formatKoreanMoney,
} from "../../shared/list";
import type {
  Amount,
  ProfitStructureResult,
  ProfitSummary,
} from "./profitStructures";
import {
  changeRate,
  percentage,
  percentPoint,
} from "./profitStructureFormulas";

/** 시안 dl(): ▲/▼ x.x% (오르면 초록, 내리면 빨강). goodUp=false면 반대. */
function Change({ rate, goodUp = true }: { rate: number; goodUp?: boolean }) {
  const good = goodUp ? rate >= 0 : rate <= 0;
  return (
    <span className={good ? "profit-ok" : "profit-bad"}>
      {rate >= 0 ? "▲" : "▼"} {Math.abs(rate).toFixed(1)}%
    </span>
  );
}
function Point({ value }: { value: number }) {
  return (
    <span className={value <= 0 ? "profit-ok" : "profit-bad"}>
      {percentPoint(value)}
    </span>
  );
}

/**
 * 시안 UI-07 요약 4칸: 매출 · 매출총이익 · 영업이익(영업이익표로 이동) · 대기 원가 비중.
 * 설명 글 = 기간 이름 또는 이익률 + 「· ▲ x.x% 전월 대비」 + 줄 바꿔 「전년 같은 기간 대비 ▲ x.x%」.
 */
export function ProfitStructureSummary({
  data,
  previousAvailable,
  idleCost,
  onOpenOperatingProfit,
}: {
  data: ProfitStructureResult | undefined;
  previousAvailable: boolean;
  /** 고른 기간의 대기 원가(원). 맨 오른쪽 추이 값. */
  idleCost: Amount;
  onOpenOperatingProfit: () => void;
}) {
  const summary = data?.summary;
  const label = data?.comparison.previousPeriodLabel ?? null;
  const previous =
    previousAvailable && label ? (data?.comparison.previousPeriod ?? null) : null;
  const lastYear =
    data?.comparison.previousYearVisible && data.comparison.previousYear
      ? data.comparison.previousYear
      : null;
  const provisional = data?.provisional ? (
    <>
      {" "}
      <Badge kind="warn">잠정</Badge>
    </>
  ) : null;
  const versus = (key: keyof ProfitSummary): ReactNode => {
    const prev = previous ? changeRate(summary?.[key], previous[key]) : null;
    const year = lastYear ? changeRate(summary?.[key], lastYear[key]) : null;
    return (
      <>
        {prev != null && (
          <>
            {" · "}
            <Change rate={prev} /> {label} 대비
          </>
        )}
        {year != null && (
          <>
            <br />
            전년 같은 기간 대비 <Change rate={year} />
          </>
        )}
      </>
    );
  };
  const share = summary?.idleCostShare;
  const sharePrev =
    previous && share != null && previous.idleCostShare != null
      ? share - previous.idleCostShare
      : null;
  const shareYear =
    lastYear && share != null && lastYear.idleCostShare != null
      ? share - lastYear.idleCostShare
      : null;
  const money = (value: Amount | undefined) =>
    summary ? formatKoreanMoney(value) : "–";
  return (
    <KpiCards aria-label="요약" testId="profit-summary-cards">
      <KpiCard
        label={<>매출{provisional}</>}
        value={money(summary?.revenue)}
        description={
          summary && (
            <>
              {data?.periodName}
              {versus("revenue")}
            </>
          )
        }
        testId="profit-kpi-revenue"
      />
      <KpiCard
        label={<>매출총이익{provisional}</>}
        value={money(summary?.grossProfit)}
        description={
          summary && (
            <>
              매출총이익률 {percentage(summary.grossProfitRate)}
              {versus("grossProfit")}
            </>
          )
        }
        testId="profit-kpi-gross"
      />
      <KpiCard
        label={<>영업이익{provisional}</>}
        value={money(summary?.operatingProfit)}
        description={
          summary && (
            <>
              영업이익률 {percentage(summary.operatingProfitRate)}
              {versus("operatingProfit")}
            </>
          )
        }
        onClick={onOpenOperatingProfit}
        testId="profit-kpi-operating"
      />
      <KpiCard
        label={<>대기 원가 비중{provisional}</>}
        value={summary ? percentage(share) : "–"}
        description={
          summary && (
            <>
              대기 원가 {formatKoreanMoney(idleCost)} ÷ 매출원가
              {sharePrev != null && (
                <>
                  {" · "}
                  <Point value={sharePrev} /> {label} 대비
                </>
              )}
              {shareYear != null && lastYear && (
                <>
                  <br />
                  전년 같은 기간 {percentage(lastYear.idleCostShare)} (
                  <Point value={shareYear} />)
                </>
              )}
            </>
          )
        }
        testId="profit-kpi-idle"
      />
    </KpiCards>
  );
}

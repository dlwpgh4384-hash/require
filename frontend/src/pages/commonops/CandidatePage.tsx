import { useEffect, useState, type ReactNode } from "react";
import { KpiCard, KpiCards } from "../../shared/list";
import {
  candidateOverviewMessages as messages,
  getCommonCandidateShortages,
  getCommonCandidateSummary,
  type CandidateShortage,
  type CandidateSummary,
} from "./candidateOverview";
import "./candidateOverview.css";

export function CandidatePage({
  onShowCandidates,
  revision = 0,
  children,
}: {
  onShowCandidates: (jobCode: string) => void;
  revision?: number;
  children?: ReactNode;
}) {
  const [summary, setSummary] = useState<CandidateSummary | null>(null);
  const [shortages, setShortages] = useState<CandidateShortage[] | null>(null);
  const [summaryError, setSummaryError] = useState(false);
  const [shortageError, setShortageError] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setSummary(null);
    setShortages(null);
    setSummaryError(false);
    setShortageError(false);
    getCommonCandidateSummary(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setSummary(value);
      })
      .catch(() => {
        if (!controller.signal.aborted) setSummaryError(true);
      });
    getCommonCandidateShortages(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setShortages(value.items);
      })
      .catch(() => {
        if (!controller.signal.aborted) setShortageError(true);
      });
    return () => controller.abort();
  }, [revision, retry]);
  const failure = (testId: string) => (
    <div role="alert" data-testid={testId}>
      {messages.failed}{" "}
      <button
        type="button"
        className="ls-btn"
        data-testid={`${testId}-retry`}
        onClick={() => setRetry((value) => value + 1)}
      >
        다시 시도
      </button>
    </div>
  );
  return (
    <section
      className="screen-body candidate-overview"
      data-testid="candidate-overview-page"
    >
      <section
        aria-label="외부 후보 요약"
        data-testid="candidate-summary-region"
      >
        {summaryError ? (
          failure("candidate-summary-error")
        ) : summary ? (
          <KpiCards aria-label="외부 후보 요약" testId="candidate-summary">
            <KpiCard label="진행 중" value={summary.activeCount} />
            <KpiCard
              label="면접 예정"
              value={summary.interviewScheduledCount}
            />
            <KpiCard
              label="다음 달 시작까지 가능"
              value={summary.availableByNextMonthCount}
            />
            <KpiCard
              label="30일 초과 미연락"
              value={summary.overdueContactCount}
            />
          </KpiCards>
        ) : (
          <p role="status">{messages.loading}</p>
        )}
      </section>
      <section
        aria-label="부족 직무별 후보"
        className="candidate-overview-shortages"
        data-testid="candidate-shortage-region"
      >
        <h2>부족 직무별 후보</h2>
        <p>영업 기회 기준(미충원 제외)</p>
        {shortageError ? (
          failure("candidate-shortage-error")
        ) : shortages === null ? (
          <p role="status">{messages.loading}</p>
        ) : shortages.length === 0 ? (
          <p>{messages.empty}</p>
        ) : (
          <div className="ls-twrap">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>직무</th>
                  <th>부족 시작월</th>
                  <th>최대 부족</th>
                  <th>가능 후보 수(전체 수)</th>
                  <th>후보 보기</th>
                </tr>
              </thead>
              <tbody>
                {shortages.map((row) => (
                  <tr
                    key={row.jobCode}
                    data-testid={`candidate-shortage-${row.jobCode}`}
                  >
                    <td>{row.jobName}</td>
                    <td>{row.shortageStartMonth}</td>
                    <td>{row.maxShortage}</td>
                    <td>
                      {row.availableCandidateCount} ({row.totalCandidateCount})
                    </td>
                    <td>
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm"
                        data-testid={`candidate-shortage-show-${row.jobCode}`}
                        onClick={() => onShowCandidates(row.jobCode)}
                      >
                        후보 보기
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {children}
    </section>
  );
}

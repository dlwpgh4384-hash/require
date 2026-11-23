import { useEffect, useState } from "react";
import { formatDateTime } from "../../shared/time";
import { ProjectRequestError } from "../projects/projects";
import {
  getCommonMonthCloseHistory,
  type MonthCloseHistory,
} from "./monthCloseHistory";
import "../../shared/list/list.css";
import "./monthCloseHistory.css";

/** UI-14 / SCR-UI-14 이력. 부모의 마감·해제 성공 시 refreshKey를 갱신한다. */
export function MonthCloseHistoryRegion({
  yearMonth,
  refreshKey = 0,
}: {
  yearMonth?: string;
  refreshKey?: string | number;
}) {
  const [result, setResult] = useState<MonthCloseHistory | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setResult(null);
    setError("");
    void getCommonMonthCloseHistory(yearMonth, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setResult(data);
      },
      (cause: unknown) => {
        if (controller.signal.aborted) return;
        setError(
          cause instanceof ProjectRequestError
            ? [
                cause.message,
                ...cause.errors.map((field) => field.message),
              ].join(" ")
            : "마감 이력을 불러오지 못했습니다. 다시 시도해 주세요.",
        );
      },
    );
    return () => controller.abort();
  }, [yearMonth, refreshKey, retry]);

  return (
    <section
      className="ls-root month-close-history"
      aria-label="마감 이력"
      aria-busy={!result && !error}
      data-screen-id="UI-14"
      data-testid="month-close-history-region"
    >
      <h3>마감 이력</h3>
      {error ? (
        <div role="alert" data-testid="month-close-history-error">
          {error}{" "}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="month-close-history-retry"
            onClick={() => setRetry((value) => value + 1)}
          >
            다시 시도
          </button>
        </div>
      ) : !result ? (
        <p role="status" data-testid="month-close-history-loading">
          마감 이력을 불러오는 중입니다.
        </p>
      ) : (
        <div className="ls-twrap">
          <table className="ls-t" aria-label="월 마감 이력">
            <thead>
              <tr>
                <th scope="col">연월</th>
                <th scope="col">작업</th>
                <th scope="col">처리자</th>
                <th scope="col">일시</th>
                <th scope="col">사유</th>
                <th scope="col">남은 점검</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((row, index) => (
                <tr
                  key={`${row.yearMonth}-${row.occurredAt}-${row.action}-${index}`}
                  data-testid={`month-close-history-row-${index}`}
                >
                  <td>{row.yearMonth}</td>
                  <td>{row.action}</td>
                  <td>{row.processor}</td>
                  <td>
                    <time dateTime={row.occurredAt}>
                      {formatDateTime(row.occurredAt)}
                    </time>
                  </td>
                  <td>{row.reason || "–"}</td>
                  <td>
                    {row.action === "마감" && row.remainingChecks.length
                      ? row.remainingChecks.join(", ")
                      : "–"}
                  </td>
                </tr>
              ))}
              {!result.items.length && (
                <tr>
                  <td colSpan={6} data-testid="month-close-history-empty">
                    마감 이력이 없습니다.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

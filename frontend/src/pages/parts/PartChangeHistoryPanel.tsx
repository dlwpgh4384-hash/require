import { useEffect, useState } from "react";
import { formatDateTime } from "../../shared/time";
import * as api from "./parts";

export function PartChangeHistoryPanel({
  partId,
  revision = 0,
}: {
  partId: string;
  revision?: number;
}) {
  const [rows, setRows] = useState<api.PartChange[] | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setRows(null);
    setError("");
    api.getCommonPartChangeHistory(partId, controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setRows(result.items);
      },
      (failure) => {
        if (!controller.signal.aborted) setError(api.partErrorMessage(failure));
      },
    );
    return () => controller.abort();
  }, [partId, revision, retry]);
  return (
    <section data-testid="part-change-history-panel">
      <h3>변경 기록{rows ? ` (${rows.length})` : ""}</h3>
      {error ? (
        <div role="alert">
          {error}
          <button
            type="button"
            data-testid="part-history-retry"
            onClick={() => setRetry((value) => value + 1)}
          >
            다시 시도
          </button>
        </div>
      ) : rows === null ? (
        <p role="status">변경 기록을 불러오는 중입니다.</p>
      ) : !rows.length ? (
        <p>변경 기록이 없습니다.</p>
      ) : (
        <div className="part-table-wrap">
          <table>
            <thead>
              <tr>
                <th>일시</th>
                <th>항목</th>
                <th>이전</th>
                <th>이후</th>
                <th>처리자</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr
                  key={`${row.changedAt}-${row.fieldName}-${index}`}
                  data-testid={`part-change-${index}`}
                >
                  <td>{formatDateTime(row.changedAt)}</td>
                  <td>{row.fieldName}</td>
                  <td>{row.beforeValue ?? "–"}</td>
                  <td>{row.afterValue ?? "–"}</td>
                  <td>{row.changedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

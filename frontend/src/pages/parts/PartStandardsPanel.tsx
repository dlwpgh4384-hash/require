import { useEffect, useState } from "react";
import * as api from "./parts";

/** UI-15 기준 이력은 보기 전용이다. */
export function PartStandardsPanel({
  partId,
  revision = 0,
}: {
  partId: string;
  revision?: number;
}) {
  const [rows, setRows] = useState<api.PartStandard[] | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setRows(null);
    setError("");
    api.getCommonPartStandards(partId, controller.signal).then(
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
    <section data-testid="part-standards-panel">
      {error ? (
        <div role="alert">
          {error}
          <button
            type="button"
            data-testid="part-standards-retry"
            onClick={() => setRetry((value) => value + 1)}
          >
            다시 시도
          </button>
        </div>
      ) : rows === null ? (
        <p role="status">기준 이력을 불러오는 중입니다.</p>
      ) : !rows.length ? (
        <p>기준 이력이 없습니다.</p>
      ) : (
        <div className="ls-twrap part-standards-wrap">
          <table className="ls-t">
            <thead>
              <tr>
                <th>적용 기간</th>
                <th>파트장</th>
                <th className="ls-right">배분율</th>
                <th className="ls-right">공통관리비</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr
                  key={row.effectiveMonth}
                  data-testid={`part-standard-${row.effectiveMonth}`}
                >
                  <td>
                    {row.effectiveMonth} ~ {row.endMonth ?? "현재"}
                  </td>
                  <td>{row.leaderName}</td>
                  <td className="ls-right">{row.distributionRate}%</td>
                  <td className="ls-right">{api.partMoney(row.salesExpense)}</td>
                  <td>
                    <span
                      className={`ls-b ls-b-${row.state === "예정" ? "info" : row.state === "지난" ? "lock" : "ok"}`}
                    >
                      {row.state === "지난" ? "지난 기준" : row.state}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

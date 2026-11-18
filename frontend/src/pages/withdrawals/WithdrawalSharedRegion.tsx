import { useEffect, useState } from "react";
import {
  getCommonWithdrawalAssignments,
  withdrawalRefreshEvent,
  type WithdrawalSharedRow,
} from "./withdrawals";
import { projectFailure } from "../projects/projects";
import "./withdrawals.css";

export function WithdrawalSharedRegion({
  screenId,
  developerId,
  opportunityId,
}: {
  screenId: "UI-28" | "UI-17" | "UI-29";
  developerId?: string;
  opportunityId?: string;
}) {
  const [rows, setRows] = useState<WithdrawalSharedRow[] | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const active = () => {
      if (document.visibilityState !== "hidden") setRevision((n) => n + 1);
    };
    window.addEventListener("focus", active);
    window.addEventListener(withdrawalRefreshEvent, active);
    document.addEventListener("visibilitychange", active);
    return () => {
      window.removeEventListener("focus", active);
      window.removeEventListener(withdrawalRefreshEvent, active);
      document.removeEventListener("visibilitychange", active);
    };
  }, []);
  useEffect(() => {
    const abort = new AbortController();
    setRows(null);
    setError("");
    getCommonWithdrawalAssignments({ developerId, opportunityId }, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted)
          setRows(
            screenId === "UI-29"
              ? result.items.filter((r) => r.tentativeAssignment)
              : result.items,
          );
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [screenId, developerId, opportunityId, revision]);
  return (
    <div data-testid="withdrawal-shared-region">
      {error ? (
        <div role="alert">
          {error}
          <button
            type="button"
            data-testid="withdrawal-shared-retry"
            onClick={() => setRevision((n) => n + 1)}
          >
            다시 시도
          </button>
        </div>
      ) : rows === null ? (
        <p role="status">목록을 불러오는 중입니다.</p>
      ) : rows.length === 0 ? (
        <p>해당 원천 자료 없음</p>
      ) : (
        rows.map((row) => (
          <article
            className="withdrawal-shared-row"
            key={row.assignmentId}
            data-testid={`withdrawal-shared-${row.assignmentId}`}
          >
            <strong>{row.name}</strong>
            {screenId !== "UI-29" && (
              <span>{row.extensionPossibilityName}</span>
            )}
            {screenId !== "UI-17" && row.tentativeAssignment && (
              <span
                className={
                  screenId === "UI-28" ? "withdrawal-tentative" : undefined
                }
              >
                가배정 · {row.tentativeAssignment.opportunityName} ·{" "}
                {row.tentativeAssignment.startMonth}
              </span>
            )}
          </article>
        ))
      )}
    </div>
  );
}

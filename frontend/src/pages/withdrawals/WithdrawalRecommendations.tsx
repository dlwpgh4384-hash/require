import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { projectFailure } from "../projects/projects";
import {
  getCommonWithdrawalRecommendations,
  notifyWithdrawalChanged,
  putCommonWithdrawalTentativeAssignment,
  type WithdrawalRecommendationsData,
} from "./withdrawals";

export function WithdrawalRecommendations({
  assignmentId,
  replaceCurrent,
  onClose,
  onSaved,
}: {
  assignmentId: string;
  replaceCurrent: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [data, setData] = useState<WithdrawalRecommendationsData | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    setData(null);
    getCommonWithdrawalRecommendations(
      assignmentId,
      replaceCurrent,
      abort.signal,
    )
      .then((result) => {
        if (!abort.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [assignmentId, replaceCurrent, revision]);
  const save = async (needSn: number) => {
    if (saving.current) return;
    saving.current = true;
    setBusy(true);
    setError("");
    try {
      await putCommonWithdrawalTentativeAssignment(assignmentId, needSn);
      notifyWithdrawalChanged();
      onSaved();
    } catch (reason) {
      setError(projectFailure(reason));
    } finally {
      saving.current = false;
      setBusy(false);
    }
  };
  return (
    <Dialog
      title="다음 자리 추천"
      testId="withdrawal-recommendations"
      closable
      onClose={() => !saving.current && onClose()}
    >
      <div className="project-modal">
        {!data && !error && <p role="status">목록을 불러오는 중입니다.</p>}
        {error && (
          <div role="alert" data-testid="withdrawal-recommendation-error">
            {error}
            <button
              type="button"
              data-testid="withdrawal-recommendation-retry"
              disabled={busy}
              onClick={() => setRevision((n) => n + 1)}
            >
              다시 시도
            </button>
          </div>
        )}
        {data?.items.length === 0 && <p>맞는 자리 없음</p>}
        {data?.items.slice(0, 5).map((item) => (
          <article
            className="withdrawal-recommendation"
            key={`${item.opportunityId}-${item.needSn}`}
            data-testid={`withdrawal-card-${item.opportunityId}-${item.needSn}`}
          >
            <strong>{item.opportunityName}</strong>
            <p>
              {item.jobName} · {item.gradeName} · {item.startMonth} ·{" "}
              {item.score}/100
            </p>
            <p>{item.reasons.join(" · ")}</p>
            <p>{item.waitingLabel}</p>
            <button
              type="button"
              className="ls-btn ls-btn-p"
              disabled={busy}
              data-testid={`withdrawal-reserve-${item.opportunityId}-${item.needSn}`}
              onClick={() => void save(item.needSn)}
            >
              가배정
            </button>
          </article>
        ))}
        {data?.shortages.map((row) => (
          <p
            key={`${row.month}-${row.jobName}`}
            data-testid={`withdrawal-shortage-${row.month}`}
          >
            이 직무는 {Number(row.month.slice(5))}월 {row.shortageCount}명 부족
            예상
          </p>
        ))}
        <div className="app-actions">
          <button
            type="button"
            data-testid="withdrawal-recommendation-cancel"
            disabled={busy}
            onClick={onClose}
          >
            취소
          </button>
        </div>
      </div>
    </Dialog>
  );
}

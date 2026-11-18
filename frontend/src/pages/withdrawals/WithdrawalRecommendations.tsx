import { useEffect, useRef, useState } from "react";
import { Banner, DetailPanel, KeyValues } from "../../shared/list";
import { projectFailure } from "../projects/projects";
import {
  getCommonWithdrawalRecommendations,
  notifyWithdrawalChanged,
  putCommonWithdrawalTentativeAssignment,
  type WithdrawalRecommendation,
  type WithdrawalRecommendationsData,
  type WithdrawalRow,
  extensionText,
} from "./withdrawals";

const infoIcon = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
  </svg>
);
/** 시안 점수 설명(글자 그대로). */
const SCORE_NOTE =
  "점수 = 직무(일치 40 · 관련 20) + 등급(일치 20 · 한 단계 차이 10) + 공백(없음 25 · 1개월 15 · 2개월 8 · 3개월 3) + 같은 고객사 10 + 수주 확률 60% 이상 5 (100점 만점, 같으면 자리 시작이 빠른 순). 철수한 다음 달부터 3개월 안에 시작하는 자리만 봅니다. 점수 계산은 지금은 로컬 가중합이지만, 실제로는 AI 추천 Open API에서 받아옵니다.";
const EMPTY =
  "지금 진행 중인 영업 기회 가운데 맞는 자리가 없습니다. 영업 파이프라인과 인력 운영 보드를 함께 확인하세요.";
const oneDecimal = (value: number) => (Math.round(value * 10) / 10).toFixed(1);
const dayAfter = (date: string) => {
  const next = new Date(`${date}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  return next.toISOString().slice(0, 10);
};

/** 시안 openNextRec: 오른쪽 상세 창(공통 DetailPanel)에서 다음 자리를 추천받아 가배정한다. */
export function WithdrawalRecommendations({
  row,
  replaceCurrent,
  onClose,
  onSaved,
}: {
  row: WithdrawalRow;
  replaceCurrent: boolean;
  onClose: () => void;
  /** 가배정 저장 뒤 알림 글자(창은 닫지 않고 새로 그린다). */
  onSaved: (message: string) => void;
}) {
  const [data, setData] = useState<WithdrawalRecommendationsData | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [revision, setRevision] = useState(0);
  // 가배정한 뒤에는 지금 가배정을 포함해 다시 읽는다(시안: 그 카드가 [가배정됨]).
  const [replace, setReplace] = useState(replaceCurrent);
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    getCommonWithdrawalRecommendations(row.assignmentId, replace, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [row.assignmentId, replace, revision]);
  const save = async (item: WithdrawalRecommendation) => {
    if (saving.current) return;
    saving.current = true;
    setBusy(true);
    setError("");
    try {
      await putCommonWithdrawalTentativeAssignment(
        row.assignmentId,
        item.opportunityId,
        item.needSn,
      );
      notifyWithdrawalChanged();
      onSaved(
        `${row.name}님을 「${item.opportunityName}」에 가배정했습니다. 인력 운영 보드에 점선으로 보입니다.`,
      );
      setReplace(true);
      setRevision((n) => n + 1);
    } catch (reason) {
      setError(projectFailure(reason));
    } finally {
      saving.current = false;
      setBusy(false);
    }
  };
  const target = data?.target;
  const items = data?.items.slice(0, 5) ?? [];
  const shortages = data?.shortages ?? [];
  const sub = target
    ? `${target.jobName} · ${target.gradeName ?? "–"} · ${target.projectName} ${target.endDate} 철수`
    : `${row.jobName} · ${row.projectName} ${row.endDate} 철수`;
  return (
    <DetailPanel
      title={`${target?.name ?? row.name} — 다음 자리 추천`}
      sub={sub}
      testId="withdrawal-recommendations"
      onClose={() => !saving.current && onClose()}
    >
      {error && (
        <div role="alert" className="withdrawal-alert" data-testid="withdrawal-recommendation-error">
          {error}{" "}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="withdrawal-recommendation-retry"
            disabled={busy}
            onClick={() => setRevision((n) => n + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      {!data && !error && <p role="status">목록을 불러오는 중입니다.</p>}
      {data && (
        <>
          {target?.tentativeOpportunityName && (
            <Banner kind="info">
              {infoIcon}
              <span data-testid="withdrawal-current-tentative">
                지금 가배정: {target.tentativeOpportunityName} ({target.tentativeStartMonth}부터)
              </span>
            </Banner>
          )}
          <KeyValues
            items={[
              [
                "기술",
                (target?.skills ?? []).map((skill) => (
                  <span key={skill} className="ls-chip">
                    {skill}
                  </span>
                )),
              ],
              ["연장 가능성", extensionText(target?.extensionPossibilityName ?? row.extensionPossibilityName) || "미정"],
              [
                "대기 시작",
                `${target?.waitingStartDate ?? dayAfter(row.endDate)}부터 (다음 자리가 없으면)`,
              ],
            ]}
          />
          <h2 className="withdrawal-h2" data-testid="withdrawal-recommendation-count">
            맞는 자리 {items.length}곳
          </h2>
          {items.length ? (
            items.map((item, index) => {
              const on = Boolean(item.tentative);
              return (
                <div
                  className="withdrawal-card"
                  key={`${item.opportunityId}-${item.needSn}`}
                  data-testid={`withdrawal-card-${item.opportunityId}-${item.needSn}`}
                >
                  <div className="withdrawal-card-h">
                    <strong>
                      {index + 1}. {item.opportunityName}
                    </strong>
                    <span className="withdrawal-score">
                      <strong>{item.score}</strong>
                      <span className="ls-cap">/100</span>
                    </span>
                  </div>
                  <div className="ls-cap">
                    {item.jobName} {item.gradeName} · {item.startMonth} 시작 · 남은 자리 {item.remainingCount ?? 0}명 ·{" "}
                    {item.customerName ?? ""} · {item.stageName ?? ""}
                  </div>
                  <div className="withdrawal-bar" aria-hidden="true">
                    <div className="withdrawal-track">
                      <div className="withdrawal-fill" style={{ width: `${item.score}%` }} />
                    </div>
                  </div>
                  <div className="withdrawal-chips">
                    {item.reasons.map((reason) => (
                      <span key={reason} className="ls-chip">
                        {reason}
                      </span>
                    ))}
                  </div>
                  <div className="withdrawal-card-f">
                    <span className="ls-cap">{item.waitingLabel}</span>
                    <button
                      type="button"
                      className={`ls-btn ls-btn-sm${on ? "" : " ls-btn-p"}`}
                      disabled={on || busy}
                      data-testid={`withdrawal-reserve-${item.opportunityId}-${item.needSn}`}
                      onClick={() => void save(item)}
                    >
                      {on ? "가배정됨" : "가배정"}
                    </button>
                  </div>
                </div>
              );
            })
          ) : (
            <p className="project-sub" data-testid="withdrawal-recommendation-empty">
              {EMPTY}
            </p>
          )}
          {shortages.length > 0 && (
            <p className="ls-note" data-testid="withdrawal-shortage">
              {shortages[0].jobName} 직무는{" "}
              {shortages
                .map((s) => `${Number(s.month.slice(5))}월 ${oneDecimal(s.shortageCount)}명`)
                .join(", ")}{" "}
              부족이 예상됩니다.
            </p>
          )}
          <p className="ls-note" data-testid="withdrawal-score-note">
            {SCORE_NOTE}
          </p>
        </>
      )}
    </DetailPanel>
  );
}

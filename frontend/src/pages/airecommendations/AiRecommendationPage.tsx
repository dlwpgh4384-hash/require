import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { AssignmentForm } from "../assignments/AssignmentForm";
import {
  AssignmentRequestError,
  type Assignment,
} from "../assignments/assignments";
import { getCommonProject, type Project } from "../projects/projects";
import { getCommonSalesOpportunity } from "../salespipeline/salesOpportunities";
import {
  notifyWithdrawalChanged,
  withdrawalRefreshEvent,
} from "../withdrawals/withdrawals";
import {
  newAiRequestId,
  subscribeAiRecommendation,
  type AiRecommendationEvent,
} from "./aiRecommendationRequest";
import {
  getCommonAiRecommendationOptions,
  postCommonAiRecommendation,
  postCommonAiRecommendationAssignment,
  postCommonAiRecommendationExclusion,
  putCommonAiRecommendationTentativeAssignment,
  type AiCard,
  type AiCondition,
  type AiInput,
  type AiOptions,
  type AiResult,
  type AiTarget,
} from "./aiRecommendations";
import styles from "./AiRecommendation.module.css";

const targetKey = (target: Pick<AiTarget, "targetType" | "targetId">) =>
  `${target.targetType}:${target.targetId}`;
const failure = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "요청을 처리하지 못했습니다. 다시 시도해 주세요.";
const conditions = (
  target: AiTarget,
  options: AiOptions,
  request?: Partial<AiCondition>,
): AiCondition => {
  const jobCode =
    request?.jobCode ||
    target.needs[0]?.jobCode ||
    options.jobs.find((j) => j.code === "BE")?.code ||
    options.jobs[0]?.code ||
    "";
  const startMonth =
    request?.startMonth && request.startMonth > target.startMonth
      ? request.startMonth
      : target.startMonth;
  return {
    targetType: target.targetType,
    targetId: target.targetId,
    jobCode,
    startMonth,
    gradeCode:
      request?.gradeCode ||
      options.grades.find((g) => g.name === "고급")?.code ||
      options.grades[0]?.code ||
      "",
    ...(target.targetType === "opportunity"
      ? {
          needSn: (
            target.needs.find((n) => n.jobCode === jobCode) ?? target.needs[0]
          )?.needSn,
        }
      : {}),
  };
};

export function AiRecommendationPage() {
  const [options, setOptions] = useState<AiOptions | null>(null);
  const [draft, setDraft] = useState<AiCondition | null>(null);
  const [result, setResult] = useState<AiResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState("");
  const [exclude, setExclude] = useState<AiCard | null>(null);
  const [reasonCode, setReasonCode] = useState("");
  const [assign, setAssign] = useState<{
    card: AiCard;
    project: Project;
    snapshot: AiResult;
  } | null>(null);
  const [recordRetry, setRecordRetry] = useState<{
    id: string;
    developerId: string;
    assignmentId: string;
  } | null>(null);
  const current = useRef({ options, draft, result });
  current.current = { options, draft, result };
  const loaded = useRef<Promise<AiOptions> | null>(null);
  const started = useRef(false);
  const incoming = useRef(false);
  const mounted = useRef(false);
  const serial = useRef<Promise<void>>(Promise.resolve());
  const retryInput = useRef<AiInput | null>(null);
  const root = useRef<HTMLElement | null>(null);
  const working = useRef(false);
  working.current = busy;
  const report = (reason: unknown) => {
    setError(failure(reason));
    setFields(
      reason instanceof AssignmentRequestError
        ? Object.fromEntries(reason.errors.map((e) => [e.field, e.message]))
        : {},
    );
  };
  const run = async (
    condition: AiCondition,
    requestId: string = newAiRequestId(),
    message = "",
  ) => {
    setBusy(true);
    setError("");
    setFields({});
    retryInput.current = { ...condition, requestId };
    try {
      const next = await postCommonAiRecommendation(retryInput.current);
      if (!mounted.current) return;
      setResult(next);
      setDraft(next.condition);
      setNotice(message);
      retryInput.current = null;
    } catch (reason) {
      if (mounted.current) report(reason);
    } finally {
      if (mounted.current) setBusy(false);
    }
  };
  const receive = async (event?: AiRecommendationEvent) => {
    const values = await loaded.current!;
    if (!mounted.current) return;
    let target = event?.targetId
      ? values.targets.find(
          (t) =>
            t.targetId === event.targetId && t.targetType === event.targetType,
        )
      : values.targets[0];
    if (event?.targetId && !target) {
      setError("진행 중인 대상을 골라 주세요.");
      return;
    }
    if (!target) {
      setDraft(null);
      return;
    }
    const condition = conditions(target, values, event);
    setDraft(condition);
    await run(
      condition,
      event?.requestId,
      event ? "대상을 바꿔 다시 추천했습니다." : "",
    );
  };
  useEffect(() => {
    mounted.current = true;
    loaded.current ??= getCommonAiRecommendationOptions().then((values) => {
      setOptions(values);
      return values;
    });
    const off = subscribeAiRecommendation((event) => {
      incoming.current = true;
      serial.current = serial.current.then(() => receive(event)).catch(report);
    });
    if (!started.current) {
      started.current = true;
      serial.current = serial.current
        .then(async () => {
          await loaded.current;
          if (!incoming.current) await receive();
        })
        .catch(report);
    }
    const refresh = () => {
      void refreshTargets();
    };
    window.addEventListener("focus", refresh);
    window.addEventListener(withdrawalRefreshEvent, refresh);
    document.addEventListener("visibilitychange", refresh);
    const panel = root.current?.closest(".screen-tab-panel");
    const observer = panel
      ? new MutationObserver(() => {
          if (!panel.hasAttribute("hidden")) refresh();
        })
      : null;
    if (panel)
      observer?.observe(panel, {
        attributes: true,
        attributeFilter: ["hidden"],
      });
    return () => {
      mounted.current = false;
      off();
      observer?.disconnect();
      window.removeEventListener("focus", refresh);
      window.removeEventListener(withdrawalRefreshEvent, refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, []);
  const refreshTargets = async () => {
    const selected = current.current.draft;
    if (!selected || working.current) return;
    try {
      const fresh = await getCommonAiRecommendationOptions();
      setOptions(fresh);
      loaded.current = Promise.resolve(fresh);
      if (fresh.targets.some((t) => targetKey(t) === targetKey(selected)))
        return;
      let replacement: AiTarget | undefined;
      if (selected.targetType === "opportunity") {
        try {
          const old = await getCommonSalesOpportunity(selected.targetId);
          replacement = fresh.targets.find(
            (t) => t.targetType === "project" && t.targetId === old.projectId,
          );
        } catch {
          replacement = fresh.targets[0];
        }
      }
      replacement ??= fresh.targets[0];
      if (!replacement) {
        setDraft(null);
        setResult(null);
        return;
      }
      const next = conditions(replacement, fresh, selected);
      setDraft(next);
      await run(next, undefined, "대상을 바꿔 다시 추천했습니다.");
    } catch (reason) {
      report(reason);
    }
  };
  const target = options?.targets.find(
    (t) => draft && targetKey(t) === targetKey(draft),
  );
  const afterEnd = !!(
    draft &&
    target?.targetType === "project" &&
    target.endMonth &&
    draft.startMonth > target.endMonth
  );
  const refill = async () => {
    if (current.current.draft) await run(current.current.draft);
  };
  const saveExclusion = async () => {
    if (!exclude || !result || !reasonCode) {
      setFields({ reasonCode: "제외 사유를 골라 주세요." });
      return;
    }
    setBusy(true);
    setError("");
    try {
      await postCommonAiRecommendationExclusion(result.recommendationId, {
        developerId: exclude.developerId,
        reasonCode,
      });
      setExclude(null);
      await refill();
      setNotice("추천 후보에서 제외했습니다.");
    } catch (reason) {
      report(reason);
    } finally {
      setBusy(false);
    }
  };
  const tentative = async (card: AiCard) => {
    if (!result || !draft?.needSn) return;
    setBusy(true);
    setError("");
    try {
      const saved = await putCommonAiRecommendationTentativeAssignment(
        result.recommendationId,
        { developerId: card.developerId, needSn: draft.needSn },
      );
      await refill();
      setNotice(saved.message);
      notifyWithdrawalChanged();
    } catch (reason) {
      report(reason);
    } finally {
      setBusy(false);
    }
  };
  const openAssignment = async (card: AiCard) => {
    if (!result) return;
    try {
      setAssign({
        card,
        project: await getCommonProject(result.condition.targetId),
        snapshot: result,
      });
    } catch (reason) {
      report(reason);
    }
  };
  const record = async (value: {
    id: string;
    developerId: string;
    assignmentId: string;
  }) => {
    try {
      await postCommonAiRecommendationAssignment(value.id, {
        developerId: value.developerId,
        assignmentId: value.assignmentId,
      });
      setRecordRetry(null);
      await refill();
      setNotice("투입 배정과 추천 선택을 기록했습니다.");
    } catch (reason) {
      setRecordRetry(value);
      report(reason);
    }
  };
  const savedAssignment = (row: Assignment) => {
    if (!assign) return;
    const selected = assign;
    setAssign(null);
    if (
      row.developerId === selected.card.developerId &&
      row.projectId === selected.snapshot.condition.targetId
    )
      void record({
        id: selected.snapshot.recommendationId,
        developerId: row.developerId,
        assignmentId: row.assignmentId,
      });
  };
  return (
    <section
      ref={root}
      className={styles.page}
      data-screen-id="UI-30"
      data-testid="ai-recommendation-page"
    >
      <div>
        <h1>AI 인력 추천</h1>
        <p>조건에 맞는 인력을 비교하고 배정할 수 있습니다.</p>
      </div>
      {notice && (
        <p role="status" data-testid="ai-notice">
          {notice}
        </p>
      )}
      {error && (
        <div role="alert" data-testid="ai-error">
          {error}
          {retryInput.current && (
            <button
              className="ls-btn"
              data-testid="ai-retry"
              onClick={() => {
                const input = retryInput.current;
                if (input) void run(input, input.requestId);
              }}
            >
              다시 시도
            </button>
          )}
          {!options && (
            <button
              className="ls-btn"
              data-testid="ai-options-retry"
              onClick={() => {
                loaded.current = getCommonAiRecommendationOptions().then(
                  (v) => {
                    setOptions(v);
                    return v;
                  },
                );
                void receive().catch(report);
              }}
            >
              다시 불러오기
            </button>
          )}
        </div>
      )}
      {!options && !error && (
        <p role="status">추천 조건을 불러오는 중입니다.</p>
      )}
      {options && !draft && <p>진행 중인 대상이 없습니다.</p>}
      {draft && options && (
        <div className={styles.conditions}>
          <div className={styles.row}>
            <label>
              대상
              <select
                className="ls-in"
                data-testid="ai-target"
                value={targetKey(draft)}
                disabled={busy}
                onChange={(e) => {
                  const next = options.targets.find(
                    (t) => targetKey(t) === e.target.value,
                  );
                  if (!next) return;
                  const condition = conditions(next, options, draft);
                  setDraft(condition);
                  void run(
                    condition,
                    undefined,
                    "대상을 바꿔 다시 추천했습니다.",
                  );
                }}
              >
                {options.targets.map((t) => (
                  <option key={targetKey(t)} value={targetKey(t)}>
                    {t.targetName}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className={styles.row}>
            <label>
              직무
              <select
                className="ls-in"
                data-testid="ai-job"
                value={draft.jobCode}
                onChange={(e) => {
                  const jobCode = e.target.value;
                  setDraft({
                    ...draft,
                    jobCode,
                    needSn: (
                      target?.needs.find((n) => n.jobCode === jobCode) ??
                      target?.needs[0]
                    )?.needSn,
                  });
                }}
              >
                {options.jobs.map((j) => (
                  <option key={j.code} value={j.code}>
                    {j.name}
                  </option>
                ))}
              </select>
            </label>
            {target?.targetType === "opportunity" && (
              <label>
                가배정 자리{" "}
                <span title="현재 선택한 필요 인력 줄에 가배정합니다. 자리를 바꿔도 추천 점수는 다시 계산하지 않습니다.">
                  ⓘ
                </span>
                {target.needs.length ? (
                  <select
                    className="ls-in"
                    data-testid="ai-need"
                    value={draft.needSn ?? ""}
                    onChange={(e) =>
                      setDraft({ ...draft, needSn: Number(e.target.value) })
                    }
                  >
                    {target.needs.map((n) => (
                      <option key={n.needSn} value={n.needSn}>
                        {n.needSn}번 · {n.jobName} · {n.requiredCount}명 ·{" "}
                        {n.startMonth}
                      </option>
                    ))}
                  </select>
                ) : (
                  <span>필요 인력을 먼저 등록해 주세요</span>
                )}
              </label>
            )}
          </div>
          <div className={styles.row}>
            <label>
              시작월
              <input
                className="ls-in"
                data-testid="ai-month"
                type="month"
                value={draft.startMonth}
                onChange={(e) =>
                  setDraft({ ...draft, startMonth: e.target.value })
                }
                onBlur={() => {
                  if (
                    target?.targetType === "project" &&
                    draft.startMonth < target.startMonth
                  )
                    setDraft({ ...draft, startMonth: target.startMonth });
                }}
              />
            </label>
            <label>
              등급
              <select
                className="ls-in"
                data-testid="ai-grade"
                value={draft.gradeCode}
                onChange={(e) =>
                  setDraft({ ...draft, gradeCode: e.target.value })
                }
              >
                {options.grades.map((g) => (
                  <option key={g.code} value={g.code}>
                    {g.name}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="ls-btn ls-btn-primary"
              data-testid="ai-recommend"
              disabled={
                busy ||
                afterEnd ||
                !draft.startMonth ||
                !draft.jobCode ||
                !draft.gradeCode
              }
              onClick={() => void run(draft)}
            >
              추천 받기
            </button>
          </div>
          {afterEnd && <p role="alert">시작월이 프로젝트 종료월 뒤입니다.</p>}
          {Object.entries(fields)
            .filter(([field]) => field !== "reasonCode")
            .map(([field, message]) => (
              <p key={field} role="alert">
                {message}
              </p>
            ))}
        </div>
      )}
      {busy && (
        <p role="status" data-testid="ai-loading">
          처리 중입니다.
        </p>
      )}
      {result && (
        <section data-testid="ai-results" aria-busy={busy}>
          <h2>추천 {result.items.length}명</h2>
          <p data-testid="ai-applied">
            적용 조건: {result.condition.targetId} · {result.condition.jobCode}{" "}
            · {result.condition.startMonth} · {result.condition.gradeCode}
          </p>
          {!result.items.length && <p>조건에 맞는 사람이 없습니다.</p>}
          <div className={styles.cards}>
            {result.items.map((card, index) => (
              <article
                className={styles.card}
                key={card.developerId}
                data-testid={`ai-card-${card.developerId}`}
              >
                <header>
                  <strong>
                    {index + 1}. {card.developerName}
                  </strong>
                  <b>{card.totalScore}점</b>
                </header>
                <p>
                  {card.managementNumber} · {card.jobName} · {card.gradeName} ·{" "}
                  {card.employeeTypeName}
                </p>
                {card.assignmentCandidate && (
                  <p className="ls-b">투입 배정 후보</p>
                )}
                {card.otherTentativeOpportunityName && (
                  <p>가배정 있음: {card.otherTentativeOpportunityName}</p>
                )}
                <dl>
                  {Object.entries(card.scores).map(([key, score]) => (
                    <div className={styles.score} key={key}>
                      <dt>{score.name}</dt>
                      <dd>
                        <meter
                          min="0"
                          max={score.maximumScore || 1}
                          value={score.score}
                          aria-label={score.name}
                        />{" "}
                        {score.score}/{score.maximumScore}
                      </dd>
                    </div>
                  ))}
                </dl>
                <p>{card.reason}</p>
                <footer>
                  <button
                    className="ls-btn ls-btn-primary"
                    data-testid={`ai-assign-${card.developerId}`}
                    disabled={
                      busy ||
                      (result.condition.targetType === "opportunity" &&
                        (!draft?.needSn ||
                          draft.targetId !== result.condition.targetId ||
                          card.assignmentCandidate))
                    }
                    onClick={() =>
                      result.condition.targetType === "project"
                        ? void openAssignment(card)
                        : void tentative(card)
                    }
                  >
                    {result.condition.targetType === "project"
                      ? "배정"
                      : "가배정"}
                  </button>
                  <button
                    className="ls-btn"
                    data-testid={`ai-exclude-${card.developerId}`}
                    disabled={busy}
                    onClick={() => {
                      setExclude(card);
                      setReasonCode("");
                      setFields({});
                    }}
                  >
                    제외
                  </button>
                </footer>
              </article>
            ))}
          </div>
          <p>추천은 참고용입니다. 최종 배정은 담당자가 결정합니다.</p>
        </section>
      )}
      {recordRetry && (
        <button
          className="ls-btn"
          data-testid="ai-record-retry"
          disabled={busy}
          onClick={() => void record(recordRetry)}
        >
          추천 배정 기록만 다시 저장
        </button>
      )}
      {exclude && (
        <Dialog
          title="추천 후보 제외"
          testId="ai-exclusion-dialog"
          onClose={() => {
            if (!busy) setExclude(null);
          }}
        >
          <p>
            {exclude.developerName}님을 이 대상·직무의 추천에서
            제외하시겠습니까?
          </p>
          <label>
            제외 사유
            <select
              className="ls-in"
              data-testid="ai-exclusion-reason"
              value={reasonCode}
              onChange={(e) => setReasonCode(e.target.value)}
            >
              <option value="">사유 선택</option>
              {options?.exclusionReasons.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.name}
                </option>
              ))}
            </select>
          </label>
          {fields.reasonCode && <p role="alert">{fields.reasonCode}</p>}
          <button
            className="ls-btn"
            data-testid="ai-exclusion-cancel"
            disabled={busy}
            onClick={() => setExclude(null)}
          >
            취소
          </button>
          <button
            className="ls-btn ls-btn-primary"
            data-testid="ai-exclusion-confirm"
            disabled={busy || !reasonCode}
            onClick={() => void saveExclusion()}
          >
            제외
          </button>
        </Dialog>
      )}
      {assign && (
        <>
          <p role="status">
            {assign.card.otherTentativeOpportunityName
              ? `다른 기회 「${assign.card.otherTentativeOpportunityName}」 가배정은 유지됩니다.`
              : "추천 인력을 투입 배정합니다."}
          </p>
          <AssignmentForm
            projects={[assign.project]}
            roles={[]}
            initialValues={{
              projectId: assign.project.projectId,
              developerId: assign.card.developerId,
              jobCode: assign.snapshot.condition.jobCode,
              startDate:
                `${assign.snapshot.condition.startMonth}-01` >
                assign.project.startDate
                  ? `${assign.snapshot.condition.startMonth}-01`
                  : assign.project.startDate,
              endDate: assign.project.endDate,
            }}
            onClose={() => setAssign(null)}
            onSaved={savedAssignment}
          />
        </>
      )}
    </section>
  );
}

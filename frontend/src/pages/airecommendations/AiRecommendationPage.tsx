import { useEffect, useRef, useState } from "react";
import { Dialog, Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { Badge } from "../../shared/list";
import { koreanToday } from "../../shared/time";
import { AssignmentForm } from "../assignments/AssignmentForm";
import {
  AssignmentRequestError,
  assignmentDevelopmentRoles,
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
  AI_SCORE_LABELS,
  aiReasonText,
  getCommonAiRecommendationOptions,
  orderedNeeds,
  orderedTargets,
  postCommonAiRecommendation,
  postCommonAiRecommendationAssignment,
  postCommonAiRecommendationExclusion,
  putCommonAiRecommendationTentativeAssignment,
  targetLabel,
  type AiCard,
  type AiCondition,
  type AiInput,
  type AiOptions,
  type AiResult,
  type AiTarget,
  type AiTargetType,
} from "./aiRecommendations";
import styles from "./AiRecommendation.module.css";

type TargetRef = { targetType: AiTargetType; targetId: string };
const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;
const targetKey = (target: TargetRef) =>
  `${target.targetType}:${target.targetId}`;
const same = (a: TargetRef, b: TargetRef) => targetKey(a) === targetKey(b);
const firstTarget = (options: AiOptions) => orderedTargets(options.targets)[0];
const failure = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "요청을 처리하지 못했습니다. 다시 시도해 주세요.";
type Clamp = "project" | "past" | null;
/** 시안 알림: 프로젝트 시작월로 올렸을 때는 시안 글, 이번 달로 올렸을 때는 「지난 달이라」. */
const clampText = (kind: Exclude<Clamp, null>, month: string) =>
  kind === "project"
    ? `시작월이 프로젝트 시작월보다 앞이라 ${month}로 맞춰 추천했습니다.`
    : `시작월이 지난 달이라 ${month}로 맞춰 추천했습니다.`;
/** 이번 달(한국 달력) YYYY-MM. */
const thisMonth = () => koreanToday().slice(0, 7);
/**
 * 프로젝트 자리의 가장 이른 시작월 = max(이번 달, 프로젝트 시작월)(점검 목록 L-13). 서버도 같게 맞춘다.
 * 지난 달(마감 달)은 확정 투입 월 MM이 없어 방금 배정한 사람이 다시 추천되므로 쓰지 않는다. 기회는 그대로.
 */
const projectFloor = (target: AiTarget) => {
  const now = thisMonth();
  return target.startMonth >= now ? target.startMonth : now;
};
const clampMonth = (
  target: AiTarget | undefined,
  month: string,
): { month: string; clamped: Clamp } => {
  if (target?.targetType !== "project") return { month, clamped: null };
  const floor = projectFloor(target);
  if (month >= floor) return { month, clamped: null };
  return {
    month: floor,
    clamped: floor === target.startMonth ? "project" : "past",
  };
};
const CHANGED = "대상을 바꿔 다시 추천했습니다.";
/** 가배정 자리: 직무가 같은 첫 줄, 없으면 첫 줄(NEED_SN 순서). */
const needFor = (target: AiTarget | undefined, jobCode: string) => {
  const needs = orderedNeeds(target?.needs ?? []);
  return (needs.find((n) => n.jobCode === jobCode) ?? needs[0])?.needSn;
};
/** 추천 조건 만들기. 시작월은 대상이 프로젝트일 때만 max(이번 달, 프로젝트 시작월)보다 앞이면 그 달로 맞춘다. */
const conditions = (
  target: AiTarget,
  options: AiOptions,
  request?: Partial<AiCondition>,
): { condition: AiCondition; clamped: Clamp } => {
  const jobCode =
    request?.jobCode ||
    orderedNeeds(target.needs)[0]?.jobCode ||
    options.jobs.find((j) => j.code === "BE")?.code ||
    options.jobs[0]?.code ||
    "";
  const asked =
    request?.startMonth && MONTH.test(request.startMonth)
      ? request.startMonth
      : "";
  // 요청 월이 없으면(메뉴로 열기) 프로젝트는 max(이번 달, 프로젝트 시작월), 기회는 기회 시작월 — 알림 없음.
  const fixed = asked
    ? clampMonth(target, asked)
    : { month: clampMonth(target, target.startMonth).month, clamped: null };
  return {
    clamped: fixed.clamped,
    condition: {
      targetType: target.targetType,
      targetId: target.targetId,
      jobCode,
      startMonth: fixed.month,
      gradeCode:
        request?.gradeCode ||
        options.grades.find((g) => g.name === "고급")?.code ||
        options.grades[0]?.code ||
        "",
      ...(target.targetType === "opportunity"
        ? { needSn: needFor(target, jobCode) }
        : {}),
    },
  };
};
const svg = (d: string, size: number) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);
// 시안 아이콘 ic('sparkle')·ic('warn')
const sparkle = (size: number) =>
  svg(
    "M12 3l1.8 4.6L18 9l-4.2 1.4L12 15l-1.8-4.6L6 9l4.2-1.4zM19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z",
    size,
  );
const warnIcon = svg(
  "M12 3l10 18H2zM12 10v4M12 17h.01",
  16,
);
const plain = (value: number) => String(Number(value));

function Skeleton({ testId }: { testId: string }) {
  return (
    <div className={styles.recs} role="status" data-testid={testId}>
      <span className="ls-sr">추천을 불러오는 중입니다.</span>
      {[0, 1, 2].map((i) => (
        <div className={styles.rec} key={i} aria-hidden="true">
          <div className={`${styles.skel} ${styles.skelShort}`} />
          {[0, 1, 2, 3].map((j) => (
            <div className={styles.skel} key={j} />
          ))}
        </div>
      ))}
    </div>
  );
}

export function AiRecommendationPage() {
  const [options, setOptions] = useState<AiOptions | null>(null);
  const [draft, setDraft] = useState<AiCondition | null>(null);
  const [result, setResult] = useState<AiResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [computing, setComputing] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<{
    message: string;
    kind: ToastKind;
    id: number;
  } | null>(null);
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
  const muted = useRef(false);
  /** 받는 중인 요청 수(다른 화면에서 온 요청이 줄에 서 있거나 계산 중). 0이 아니면 대상 새로 읽기를 하지 않는다. */
  const receiving = useRef(0);
  /** 한 번이라도 본 대상 이름(결과 머리에 쓰기: 대상이 목록에서 빠져도 비지 않게). */
  const names = useRef(new Map<string, string>());
  const targetSelect = useRef<HTMLSelectElement | null>(null);
  /** 대상을 바꿔 다시 계산이 끝나면 대상 칸에 포커스(시안 rc-t.focus()). */
  const refocusTarget = useRef(false);
  const locked = busy || computing;
  working.current = locked;
  useEffect(() => {
    if (locked || !refocusTarget.current) return;
    refocusTarget.current = false;
    targetSelect.current?.focus();
  }, [locked]);
  const show = (message: string, kind: ToastKind = "ok") =>
    setToast({ message, kind, id: Date.now() });
  const remember = (values: AiOptions) => {
    values.targets.forEach((t) => names.current.set(targetKey(t), t.targetName));
    return values;
  };
  const publish = (values: AiOptions) => {
    remember(values);
    setOptions(values);
    loaded.current = Promise.resolve(values);
    return values;
  };
  // 이 화면이 보낸 새로 고침 신호는 자기 자신이 다시 받지 않는다(다른 탭: 투입 배정·영업 파이프라인·인력 운영 보드·철수 리스트).
  const announce = () => {
    muted.current = true;
    try {
      notifyWithdrawalChanged();
    } finally {
      muted.current = false;
    }
  };
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
    {
      requestId = newAiRequestId(),
      message = "",
      keepDraft = false,
    }: { requestId?: string; message?: string; keepDraft?: boolean } = {},
  ) => {
    setComputing(true);
    setError("");
    setFields({});
    retryInput.current = { ...condition, requestId };
    try {
      const next = await postCommonAiRecommendation(retryInput.current);
      if (!mounted.current) return;
      setResult(next);
      if (!keepDraft) setDraft(next.condition);
      if (message) show(message);
      retryInput.current = null;
    } catch (reason) {
      if (mounted.current) report(reason);
    } finally {
      if (mounted.current) setComputing(false);
    }
  };
  /** 요청한 대상을 새로 읽어 목록에 넣는다(열린 탭이 모르던 새 기회·바뀐 필요 인력). 진행 중이 아니면(400) 그대로 둔다. */
  const withTarget = async (values: AiOptions, ref: TargetRef) => {
    try {
      const one = await getCommonAiRecommendationOptions(ref);
      const fresh = one.targets.find((t) => same(t, ref));
      if (!fresh) return values;
      return publish({
        ...values,
        targets: values.targets.some((t) => same(t, ref))
          ? values.targets.map((t) => (same(t, ref) ? fresh : t))
          : [...values.targets, fresh],
      });
    } catch {
      return values;
    }
  };
  /** 수주된 기회는 그 프로젝트로 바꾼다(시안 recFix). */
  const wonProject = async (values: AiOptions, opportunityId: string) => {
    try {
      const old = await getCommonSalesOpportunity(opportunityId);
      if (!old.projectId) return { values, target: undefined };
      const ref: TargetRef = { targetType: "project", targetId: old.projectId };
      let next = values;
      if (!next.targets.some((t) => same(t, ref)))
        next = publish(await getCommonAiRecommendationOptions());
      return { values: next, target: next.targets.find((t) => same(t, ref)) };
    } catch {
      return { values, target: undefined };
    }
  };
  const receive = async (event?: AiRecommendationEvent) => {
    let values = await loaded.current!;
    if (!mounted.current) return;
    const open = !!current.current.draft;
    // 시안 asgrec(A5): 열린 탭에 값 없는 요청(투입 배정 창의 내부·수주예정 프로젝트 등)이 오면 지금 대상을 그대로 두고
    // 다시 추천하지도, 알리지도 않는다.
    if (event && open && !event.targetType && !event.targetId) return;
    let target: AiTarget | undefined;
    let request: Partial<AiCondition> | undefined;
    // 틀린 요청(모르는 대상 종류, 2026-13 같은 달)은 받지 않고 첫 진행 대상으로 연다.
    const valid =
      !!event?.targetId &&
      (event.targetType === "project" || event.targetType === "opportunity") &&
      (!event.startMonth || MONTH.test(event.startMonth));
    if (event && valid) {
      const ref: TargetRef = {
        targetType: event.targetType!,
        targetId: event.targetId!,
      };
      values = await withTarget(values, ref);
      target = values.targets.find((t) => same(t, ref));
      if (!target && ref.targetType === "opportunity") {
        const won = await wonProject(values, ref.targetId);
        values = won.values;
        target = won.target;
      }
      if (target) request = event;
    }
    if (!mounted.current) return;
    target ??= firstTarget(values);
    if (!target) {
      setDraft(null);
      setResult(null);
      return;
    }
    const { condition, clamped } = conditions(target, values, request);
    setDraft(condition);
    await run(condition, {
      requestId: event?.requestId,
      message: clamped
        ? clampText(clamped, condition.startMonth)
        : event && open
          ? CHANGED
          : "",
    });
  };
  const load = () => {
    loaded.current = getCommonAiRecommendationOptions().then((values) => {
      setOptions(remember(values));
      return values;
    });
  };
  useEffect(() => {
    mounted.current = true;
    if (!loaded.current) load();
    const off = subscribeAiRecommendation((event) => {
      incoming.current = true;
      receiving.current += 1;
      serial.current = serial.current
        .then(() => receive(event))
        .catch(report)
        .finally(() => {
          receiving.current -= 1;
        });
    });
    if (!started.current) {
      started.current = true;
      receiving.current += 1;
      serial.current = serial.current
        .then(async () => {
          await loaded.current;
          if (!incoming.current) await receive();
        })
        .catch(report)
        .finally(() => {
          receiving.current -= 1;
        });
    }
    const refresh = () => {
      if (!muted.current) void refreshTargets();
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
    // 처음 한 번만 건다(안의 함수는 ref로 최신 값을 읽는다).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  /**
   * 대상·자리 목록 다시 읽기. 받는 중인 요청이 있거나 계산 중이면 하지 않는다(그 요청이 끝난 뒤 대상을 덮지 않게).
   * force: [가배정]·[제외]가 400·409로 실패했을 때(그 작업이 아직 busy로 보이지만 끝난 상태).
   */
  const refreshTargets = async (force = false) => {
    const selected = current.current.draft;
    if (!selected || receiving.current > 0 || (!force && working.current))
      return;
    try {
      const fresh = publish(await getCommonAiRecommendationOptions());
      // 그사이 다른 요청을 받기 시작했으면 그 요청에 맡긴다.
      if (receiving.current > 0) return;
      const kept = fresh.targets.find((t) => same(t, selected));
      if (kept) {
        // 지워진 자리 줄을 고르고 있었다면 같은 직무 줄(없으면 첫 줄)로 바꾼다.
        const draftNow = current.current.draft;
        if (
          draftNow &&
          same(draftNow, kept) &&
          kept.targetType === "opportunity" &&
          draftNow.needSn !== undefined &&
          !kept.needs.some((n) => n.needSn === draftNow.needSn)
        )
          setDraft({ ...draftNow, needSn: needFor(kept, draftNow.jobCode) });
        return;
      }
      let values = fresh;
      let replacement: AiTarget | undefined;
      if (selected.targetType === "opportunity") {
        const won = await wonProject(fresh, selected.targetId);
        values = won.values;
        replacement = won.target;
      }
      replacement ??= firstTarget(values);
      if (!replacement) {
        setDraft(null);
        setResult(null);
        return;
      }
      const next = conditions(replacement, values, selected).condition;
      setDraft(next);
      await run(next, { message: CHANGED });
    } catch (reason) {
      report(reason);
    }
  };
  const target = options?.targets.find((t) => draft && same(t, draft));
  const afterEnd = !!(
    draft &&
    target?.targetType === "project" &&
    target.endMonth &&
    draft.startMonth > target.endMonth
  );
  const jobName = (code: string) =>
    options?.jobs.find((j) => j.code === code)?.name ?? code;
  const gradeName = (code: string) =>
    options?.grades.find((g) => g.code === code)?.name ?? code;
  /** [가배정]·[제외]·[배정] 뒤 마지막으로 실행한 조건(고치는 중인 칸 말고)으로 카드를 다시 채운다. */
  const refill = async () => {
    const executed = current.current.result?.condition;
    if (executed) await run(executed, { keepDraft: true });
  };
  const submit = () => {
    if (!draft || locked) return;
    const { month, clamped } = clampMonth(target, draft.startMonth);
    const condition = clamped ? { ...draft, startMonth: month } : draft;
    if (clamped) setDraft(condition);
    void run(condition, {
      message: clamped
        ? clampText(clamped, condition.startMonth)
        : "추천을 다시 계산했습니다.",
    });
  };
  /** F18: 누르기 전에 자리 줄이 지워졌거나 기회가 닫혀 400·409가 오면 대상·자리 목록을 다시 읽는다. */
  const reloadAfterRejected = (reason: unknown) => {
    if (
      reason instanceof AssignmentRequestError &&
      (reason.status === 400 || reason.status === 409)
    )
      // 대상이 바뀌어 다시 추천하면 오류 글이 지워지므로, 다 읽은 뒤 서버 글을 다시 보인다.
      void refreshTargets(true).then(() => {
        if (mounted.current) report(reason);
      });
  };
  const saveExclusion = async () => {
    if (!exclude || !result) return;
    if (!reasonCode) {
      setFields({ reasonCode: "제외 사유를 골라 주세요." });
      return;
    }
    const name = exclude.developerName;
    const job = jobName(result.condition.jobCode);
    setBusy(true);
    setError("");
    try {
      await postCommonAiRecommendationExclusion(result.recommendationId, {
        developerId: exclude.developerId,
        reasonCode,
      });
      setExclude(null);
      await refill();
      show(`${name}님을 이 자리(${job}) 추천에서 뺐습니다.`);
    } catch (reason) {
      report(reason);
      reloadAfterRejected(reason);
    } finally {
      setBusy(false);
    }
  };
  const tentative = async (card: AiCard) => {
    const executed = current.current.result;
    const needSn = current.current.draft?.needSn;
    if (!executed || !needSn) return;
    setBusy(true);
    setError("");
    try {
      // 투입 배정으로 넘긴 후보(CNVT)도 막지 않고 보낸다: 서버가 409와 안내 글을 준다.
      const saved = await putCommonAiRecommendationTentativeAssignment(
        executed.recommendationId,
        { developerId: card.developerId, needSn },
      );
      announce();
      await refill();
      show(saved.message);
    } catch (reason) {
      report(reason);
      reloadAfterRejected(reason);
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
    setBusy(true);
    try {
      await postCommonAiRecommendationAssignment(value.id, {
        developerId: value.developerId,
        assignmentId: value.assignmentId,
      });
      setRecordRetry(null);
      await refill();
    } catch (reason) {
      setRecordRetry(value);
      report(reason);
    } finally {
      setBusy(false);
    }
  };
  const savedAssignment = (row: Assignment, message: string) => {
    if (!assign) return;
    const selected = assign;
    setAssign(null);
    // 투입 배정(UI-12)과 같은 저장 알림(MM 1.0 초과 같은 서버 경고 포함)
    show(message);
    announce();
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
  const opportunity = target?.targetType === "opportunity" ? target : undefined;
  const needs = orderedNeeds(opportunity?.needs ?? []);
  const executed = result?.condition;
  const executedTarget = executed
    ? options?.targets.find((t) => same(t, executed))
    : undefined;
  const fieldErrors = Object.entries(fields).filter(
    ([field]) => field !== "reasonCode",
  );
  return (
    <section
      ref={root}
      className={`screen-body ${styles.page}`}
      data-screen-id="UI-30"
      data-testid="ai-recommendation-page"
    >
      {error && (
        <div
          className="ls-banner ls-banner-bad"
          role="alert"
          data-testid="ai-error"
        >
          {warnIcon}
          <span>{error}</span>
          {retryInput.current && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="ai-retry"
              onClick={() => {
                const input = retryInput.current;
                if (input) void run(input, { requestId: input.requestId });
              }}
            >
              다시 시도
            </button>
          )}
          {!options && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="ai-options-retry"
              onClick={() => {
                setError("");
                load();
                serial.current = serial.current
                  .then(() => receive())
                  .catch(report);
              }}
            >
              다시 불러오기
            </button>
          )}
          {recordRetry && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="ai-record-retry"
              disabled={locked}
              onClick={() => void record(recordRetry)}
            >
              다시 저장
            </button>
          )}
        </div>
      )}
      {!options && !error && <Skeleton testId="ai-options-loading" />}
      {options && !options.targets.length && (
        <section className={styles.card}>
          <p className={styles.empty}>진행 중인 대상이 없습니다.</p>
        </section>
      )}
      {draft && options && (
        <section className={styles.card} aria-labelledby="ai-condition-title">
          <h2 id="ai-condition-title">어떤 자리에 추천할까요?</h2>
          <form
            className={styles.form}
            noValidate
            onSubmit={(e) => {
              e.preventDefault();
              submit();
            }}
          >
            <div className={styles.fld}>
              <label className={`${styles.fl} ${styles.req}`} htmlFor="ai-target">
                프로젝트·영업 기회
              </label>
              <div className={styles.fc}>
                <select
                  id="ai-target"
                  ref={targetSelect}
                  className="ls-in"
                  data-testid="ai-target"
                  value={targetKey(draft)}
                  disabled={locked}
                  onChange={(e) => {
                    const next = options.targets.find(
                      (t) => targetKey(t) === e.target.value,
                    );
                    if (!next) return;
                    const condition = conditions(next, options, draft).condition;
                    setDraft(condition);
                    // 계산이 끝나 칸이 다시 켜지면 대상 칸에 포커스(시안 rc-t.focus()).
                    refocusTarget.current = true;
                    void run(condition, { message: CHANGED });
                  }}
                >
                  {orderedTargets(options.targets).map((t) => (
                    <option key={targetKey(t)} value={targetKey(t)}>
                      {targetLabel(t)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className={styles.fld}>
              <label className={`${styles.fl} ${styles.req}`} htmlFor="ai-job">
                직무
              </label>
              <div className={styles.fc}>
                <div className={styles.jobRow}>
                  <select
                    id="ai-job"
                    className={`ls-in ${styles.job}`}
                    data-testid="ai-job"
                    value={draft.jobCode}
                    onChange={(e) => {
                      const jobCode = e.target.value;
                      setDraft({
                        ...draft,
                        jobCode,
                        ...(opportunity
                          ? { needSn: needFor(opportunity, jobCode) }
                          : {}),
                      });
                    }}
                  >
                    {options.jobs.map((j) => (
                      <option key={j.code} value={j.code}>
                        {j.name}
                      </option>
                    ))}
                  </select>
                  {opportunity && (
                    <>
                      <label
                        className={`${styles.fl} ${styles.req} ${styles.slotLabel}`}
                        htmlFor="ai-need"
                      >
                        가배정 자리{" "}
                        <span
                          className={styles.sub}
                          title="[가배정]한 사람이 들어갈 필요 인력 줄입니다. 직무를 고르면 같은 직무 줄로 맞춰지고, 없으면 첫 줄입니다."
                        >
                          ⓘ
                        </span>
                      </label>
                      {needs.length ? (
                        <select
                          id="ai-need"
                          className={`ls-in ${styles.slot}`}
                          data-testid="ai-need"
                          value={draft.needSn ?? ""}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              needSn: Number(e.target.value),
                            })
                          }
                        >
                          {needs.map((n, i) => (
                            <option key={n.needSn} value={n.needSn}>
                              {i + 1}. {n.jobName} {n.requiredCount}명 ·{" "}
                              {n.startMonth}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span className={styles.help}>
                          필요 인력을 먼저 등록해 주세요
                        </span>
                      )}
                    </>
                  )}
                </div>
              </div>
            </div>
            <div className={styles.fld}>
              <label className={`${styles.fl} ${styles.req}`} htmlFor="ai-month">
                시작월
              </label>
              <div className={styles.fc}>
                <input
                  id="ai-month"
                  className={`ls-in ${styles.month}`}
                  data-testid="ai-month"
                  type="month"
                  value={draft.startMonth}
                  aria-invalid={afterEnd || undefined}
                  onChange={(e) =>
                    setDraft({ ...draft, startMonth: e.target.value })
                  }
                />
                {afterEnd && (
                  <span className={styles.bad} role="alert">
                    시작월이 프로젝트 종료월 뒤입니다.
                  </span>
                )}
              </div>
            </div>
            <div className={styles.fld}>
              <label className={`${styles.fl} ${styles.req}`} htmlFor="ai-grade">
                등급
              </label>
              <div className={styles.fc}>
                <select
                  id="ai-grade"
                  className={`ls-in ${styles.grade}`}
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
              </div>
            </div>
            <div className={`${styles.fld} ${styles.full}`}>
              <span />
              <div className={styles.fc}>
                <div className={styles.row}>
                  <button
                    type="submit"
                    className="ls-btn ls-btn-p"
                    data-testid="ai-recommend"
                    disabled={
                      locked ||
                      afterEnd ||
                      !MONTH.test(draft.startMonth) ||
                      !draft.jobCode ||
                      !draft.gradeCode
                    }
                  >
                    {sparkle(16)} 추천 받기
                  </button>
                  <span className={styles.help}>
                    휴직·퇴사 예정자, 그 기간 MM이 이미 1.0인 사람은 뺍니다.
                    나이·성별·학력은 쓰지 않습니다.
                  </span>
                </div>
                {fieldErrors.map(([field, message]) => (
                  <span key={field} className={styles.bad} role="alert">
                    {message}
                  </span>
                ))}
              </div>
            </div>
          </form>
        </section>
      )}
      {!result && computing && <Skeleton testId="ai-loading" />}
      {result && executed && (
        <section
          className={styles.results}
          data-testid="ai-results"
          aria-busy={computing}
        >
          <div className={styles.cardH}>
            <h2 data-testid="ai-applied">
              {executedTarget?.targetName ??
                names.current.get(targetKey(executed)) ??
                ""}{" "}
              · {jobName(executed.jobCode)} ·{" "}
              {executed.startMonth}부터 · {gradeName(executed.gradeCode)}
            </h2>
            <Badge kind="info">
              <span className={styles.badge}>
                {sparkle(12)}추천 {result.items.length}명
              </span>
            </Badge>
          </div>
          {computing ? (
            <Skeleton testId="ai-loading" />
          ) : result.items.length ? (
            <div className={styles.recs}>
              {result.items.map((card, index) => {
                const max =
                  Object.values(card.scores).reduce(
                    (s, x) => s + (Number(x.maximumScore) || 0),
                    0,
                  ) || 100;
                return (
                  <article
                    className={styles.rec}
                    key={card.developerId}
                    data-testid={`ai-card-${card.developerId}`}
                  >
                    <div className={styles.recH}>
                      <span className={styles.pname}>
                        <strong>
                          {index + 1}. {card.developerName}
                        </strong>
                        <small>
                          {card.managementNumber} · {card.jobName} ·{" "}
                          {card.gradeName} · {card.employeeTypeName}
                        </small>
                        {card.otherTentativeOpportunityName && (
                          <small>
                            <Badge kind="warn">
                              가배정 있음: {card.otherTentativeOpportunityName}
                            </Badge>
                          </small>
                        )}
                        {card.assignmentCandidate && (
                          <small>
                            <Badge kind="info">투입 배정 후보</Badge>
                          </small>
                        )}
                      </span>
                      <div className={styles.score}>
                        {plain(card.totalScore)}
                        <small>/{plain(max)}점</small>
                      </div>
                    </div>
                    <div>
                      {AI_SCORE_LABELS.filter(([key]) => card.scores[key]).map(
                        ([key, label]) => {
                          const s = card.scores[key];
                          const width = s.maximumScore
                            ? Math.max(
                                0,
                                Math.min(100, (s.score / s.maximumScore) * 100),
                              )
                            : 0;
                          return (
                            <div
                              className={styles.sbar}
                              key={key}
                              data-testid={`ai-score-${card.developerId}-${key}`}
                            >
                              <span>{label}</span>
                              <div className={styles.track} aria-hidden="true">
                                <div
                                  className={styles.fill}
                                  style={{ width: `${width}%` }}
                                />
                              </div>
                              <span>
                                {plain(s.score)}/{plain(s.maximumScore)}
                              </span>
                            </div>
                          );
                        },
                      )}
                    </div>
                    <p className={styles.why} data-testid={`ai-why-${card.developerId}`}>
                      {aiReasonText(card)}
                    </p>
                    <div className={styles.recF}>
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm ls-btn-p"
                        data-testid={`ai-assign-${card.developerId}`}
                        disabled={
                          locked ||
                          (executed.targetType === "opportunity" &&
                            (!draft?.needSn ||
                              draft.targetId !== executed.targetId))
                        }
                        onClick={() =>
                          executed.targetType === "project"
                            ? void openAssignment(card)
                            : void tentative(card)
                        }
                      >
                        {executed.targetType === "project" ? "배정" : "가배정"}
                      </button>
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm ls-btn-dt"
                        data-testid={`ai-exclude-${card.developerId}`}
                        disabled={locked}
                        onClick={() => {
                          setExclude(card);
                          setReasonCode(options?.exclusionReasons[0]?.code ?? "");
                          setFields({});
                        }}
                      >
                        제외
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <p className={styles.empty}>조건에 맞는 사람이 없습니다.</p>
          )}
          <p className={styles.note}>
            추천은 참고용입니다. 배정은 담당자가 정합니다. 프로젝트 자리는
            [배정](투입 배정 창이 채워져 열리고 저장은 그 창에서), 영업 기회
            자리는 [가배정](기회의 필요 인력·인력 운영 보드 점선에 표시)을
            씁니다. 점수가 같으면 가용일 점수가 높은 사람, 그다음 관리번호
            순입니다. 성과 점수는 투입 이력(연장 비율, 인력 사정 조기 철수)으로
            매깁니다.
          </p>
        </section>
      )}
      {exclude && (
        <Dialog
          title={`${exclude.developerName}님을 추천에서 뺄까요?`}
          testId="ai-exclusion-dialog"
          closable
          onClose={() => {
            if (!busy) setExclude(null);
          }}
        >
          <div className={styles.dfc}>
            <label htmlFor="ai-exclusion-reason">
              제외 사유<span className="field-required">*</span>
            </label>
            <select
              id="ai-exclusion-reason"
              data-testid="ai-exclusion-reason"
              value={reasonCode}
              aria-invalid={!!fields.reasonCode || undefined}
              onChange={(e) => {
                setReasonCode(e.target.value);
                setFields({});
              }}
            >
              {!reasonCode && <option value="">선택</option>}
              {options?.exclusionReasons.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.name}
                </option>
              ))}
            </select>
            <small className={styles.help}>
              이 자리·이 직무의 추천에서만 빠집니다. 직무를 바꾸거나 다른 자리를
              고르면 다시 나옵니다.
            </small>
            {fields.reasonCode && (
              <small role="alert" className="field-error">
                {fields.reasonCode}
              </small>
            )}
          </div>
          <div className="app-actions">
            <button
              type="button"
              className="ls-btn"
              data-testid="ai-exclusion-cancel"
              disabled={busy}
              onClick={() => setExclude(null)}
            >
              취소
            </button>
            <button
              type="button"
              className="ls-btn app-danger"
              data-testid="ai-exclusion-confirm"
              disabled={busy || !reasonCode}
              onClick={() => void saveExclusion()}
            >
              제외
            </button>
          </div>
        </Dialog>
      )}
      {assign && (
        <AssignmentForm
          projects={[assign.project]}
          roles={assignmentDevelopmentRoles}
          subtitle="AI 인력 추천에서 채움"
          screenId="UI-30"
          extraWarning={
            assign.card.otherTentativeOpportunityName
              ? `${assign.card.developerName}님의 다른 기회 「${assign.card.otherTentativeOpportunityName}」 가배정은 그대로 둡니다.`
              : undefined
          }
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
      )}
      {toast && (
        <Toast
          key={toast.id}
          message={toast.message}
          kind={toast.kind}
          testId="ai-toast"
          onDismiss={() => setToast(null)}
        />
      )}
    </section>
  );
}

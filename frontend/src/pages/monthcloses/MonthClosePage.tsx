import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useLocation, useNavigate } from "react-router-dom";
import { Banner } from "../../shared/list/DetailPanel";
import {
  ConfirmDialog,
  Dialog,
  Toast,
  type ToastKind,
} from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import type { PeriodBasis } from "../../shared/period/periodRules";
import { useViewport } from "../../shared/layout/useViewport";
import { formatDateTime, koreanThisMonth } from "../../shared/time";
import { requestMenuBadgeRefresh } from "../../app/menuBadges";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import {
  createPreferenceSaver,
  getCommonUserPreference,
} from "../userpreferences/userPreferences";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { ProjectField } from "../projects/ProjectField";
import {
  canReopenMonthClose,
  getCommonMonthClose,
  getCommonMonthCloseHistory,
  getCommonMonthCloseReopenImpact,
  isMonthCloseConflict,
  putCommonMonthCloseConfirm,
  putCommonMonthCloseReopen,
  MonthCloseRequestError,
  type MonthClose,
  type MonthCloseHistoryItem,
} from "./monthCloses";
import { useMonthCloseTitleHost } from "./MonthCloseWorkspace";
import "./monthCloses.css";

type Load<T> = { value: T | null; loading: boolean; error: string };
type ImpactLoad =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; value: number };
const message = (failure: unknown) =>
  failure instanceof Error ? failure.message : "다시 시도해 주세요.";
const isConflict = (failure: unknown) =>
  isMonthCloseConflict(failure) ||
  (failure instanceof Error && failure.message === feedbackMessages.conflict);
const developmentRoles = ["ADMIN", "FIN", "SALES_LDR"];
const WARN = "확인 필요";
/** 시안 ic(): 띠·점검 줄·배지에 쓰는 선 아이콘. */
const iconPaths = {
  warn: "M12 3l10 18H2zM12 10v5M12 18h.01",
  info: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01",
  okc: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 12l3 3 5-6",
  lock: "M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4",
};
function Icon({ name, size }: { name: keyof typeof iconPaths; size: number }) {
  return (
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
      <path d={iconPaths[name]} />
    </svg>
  );
}
/** 「2026-10」의 다음 달 숫자(10 → 11, 12 → 1). */
const nextMonthNumber = (yearMonth: string) =>
  (Number(yearMonth.slice(5)) % 12) + 1;
/** 시안: 「2026-10-07 18:20 홍길동이(가) 마감했습니다.」 — 일시·처리자가 없으면 그 부분만 뺀다. */
function closedBy(row: MonthClose) {
  const at = row.closedAt ? formatDateTime(row.closedAt) : "";
  if (row.closedBy) return ` ${at ? `${at} ` : ""}${row.closedBy}이(가) 마감했습니다.`;
  return at ? ` ${at}에 마감했습니다.` : " 마감했습니다.";
}

/** UI-14 월 마감(시안 SCR['UI-14']). 로그인 연결 전에는 서버와 같은 개발 실행 문맥을 사용한다. */
export function MonthClosePage({
  roles = developmentRoles,
}: { roles?: readonly string[] } = {}) {
  const root = useRef<HTMLDivElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const writing = useRef(false);
  const visiting = useRef(false);
  const announce = useRef(false);
  const navigate = useNavigate();
  const location = useLocation();
  const host = useMonthCloseTitleHost();
  const { mobile } = useViewport();
  const { state: period, setValue } = useScreenPeriod("UI-14", {
    kind: "yearMonth",
  });
  const [basis, setBasis] = useState<PeriodBasis | null>(null);
  const [detail, setDetail] = useState<Load<MonthClose>>({
    value: null,
    loading: true,
    error: "",
  });
  const [history, setHistory] = useState<Load<MonthCloseHistoryItem[]>>({
    value: null,
    loading: true,
    error: "",
  });
  const [impact, setImpact] = useState<ImpactLoad>({ status: "loading" });
  const [revision, refresh] = useState(0);
  const [historyRevision, refreshHistory] = useState(0);
  const [impactRevision, refreshImpact] = useState(0);
  const [dialog, setDialog] = useState<"confirm" | "reopen" | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [error, setError] = useState("");
  const [dialogError, setDialogError] = useState("");
  const [toast, setToast] = useState<{ text: string; kind: ToastKind } | null>(
    null,
  );
  const [busy, setBusy] = useState(false);
  const month =
    period.status === "ready"
      ? `${period.value.year}-${String(period.value.month).padStart(2, "0")}`
      : "";
  // 다른 화면으로 갔다가 이 화면으로 돌아오면 [확인하기]를 다시 누를 수 있다.
  useEffect(() => {
    if (location.pathname.endsWith("/UI-14")) visiting.current = false;
  }, [location.pathname]);
  // 다시 읽는 동안에도 앞서 받은 점검 결과를 그대로 보인다(같은 달일 때).
  useEffect(() => {
    if (!month || mobile) return;
    const controller = new AbortController();
    setDetail((previous) => ({ ...previous, loading: true, error: "" }));
    getCommonMonthClose(month, controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        setDetail({ value, loading: false, error: "" });
        if (announce.current) {
          announce.current = false;
          const left = value.checks.filter((item) => item.status === WARN).length;
          setToast({
            text: left
              ? `점검을 다시 했습니다. 확인할 항목 ${left}개가 남아 있습니다.`
              : "점검을 다시 했습니다. 확인할 항목이 없습니다.",
            kind: "info",
          });
        }
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        announce.current = false;
        setDetail((previous) => ({
          ...previous,
          loading: false,
          error: message(failure),
        }));
      });
    return () => controller.abort();
  }, [month, revision, mobile]);
  useEffect(() => {
    if (!month || mobile) return;
    const controller = new AbortController();
    setHistory((previous) => ({ ...previous, loading: true, error: "" }));
    getCommonMonthCloseHistory(undefined, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted)
          setHistory({ value: value.items, loading: false, error: "" });
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setHistory((previous) => ({
            ...previous,
            loading: false,
            error: message(failure),
          }));
      });
    return () => controller.abort();
  }, [month, historyRevision, mobile]);
  useEffect(() => {
    if (dialog !== "reopen") return;
    const controller = new AbortController();
    setImpact({ status: "loading" });
    getCommonMonthCloseReopenImpact(month, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted)
          setImpact({ status: "ready", value: value.previousAdjustmentCount });
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setImpact({ status: "error", message: message(failure) });
      });
    return () => controller.abort();
  }, [month, dialog, impactRevision]);
  const row =
    detail.value && detail.value.yearMonth === month ? detail.value : null;
  const close = () => {
    if (!writing.current) {
      setDialog(null);
      setDialogError("");
    }
  };
  const recheck = () => {
    setError("");
    announce.current = true;
    refresh((value) => value + 1);
  };
  async function submit() {
    if (writing.current || !dialog || !row || period.status !== "ready") return;
    if (dialog === "reopen") {
      if (impact.status !== "ready") return;
      if (!reason.trim()) {
        setReasonError("해제 사유를 입력해 주세요.");
        reasonRef.current?.focus();
        return;
      }
    }
    writing.current = true;
    setBusy(true);
    setError("");
    setDialogError("");
    setReasonError("");
    try {
      const result =
        dialog === "confirm"
          ? await putCommonMonthCloseConfirm(month, row.modifiedAt)
          : await putCommonMonthCloseReopen(month, row.modifiedAt, reason);
      // 저장 결과를 그대로 보인다(점검을 다시 읽지 않음). 이력만 새로 읽는다.
      setDetail({ value: result, loading: false, error: "" });
      setDialog(null);
      setToast({
        text: result.isClosed
          ? `${month}를 마감했습니다.`
          : `${month} 마감을 해제했습니다. 영업 파트 정산 확정은 그대로입니다.`,
        kind: "ok",
      });
      // 조회 월 옆 「마감」 표시도 바로 바꾼다(기간 기준을 다시 읽지 않음).
      const key = month.replace("-", "");
      setBasis((previous) => {
        const source = previous ?? period.basis;
        const others = source.closedMonths.filter((item) => item !== key);
        return {
          ...source,
          closedMonths: result.isClosed ? [...others, key].sort() : others,
        };
      });
      refreshHistory((value) => value + 1);
      requestMenuBadgeRefresh();
    } catch (failure) {
      if (isConflict(failure)) {
        // 다른 사람이 먼저 바꿨다: 창을 닫고 지금 상태를 다시 읽는다.
        setDialog(null);
        setError(feedbackMessages.conflict);
        refresh((value) => value + 1);
        refreshHistory((value) => value + 1);
        return;
      }
      const field =
        failure instanceof MonthCloseRequestError
          ? failure.fields.find((item) => item.field === "reason")
          : undefined;
      if (dialog === "reopen" && field) {
        setReasonError(field.message);
        reasonRef.current?.focus();
      } else if (dialog === "reopen") {
        setDialogError(message(failure));
      } else {
        setDialog(null);
        setError(message(failure));
      }
    } finally {
      writing.current = false;
      setBusy(false);
    }
  }
  async function visit(screen: string) {
    if (
      visiting.current ||
      busy ||
      period.status !== "ready" ||
      !/^UI-\d{2}$/.test(screen)
    )
      return;
    visiting.current = true;
    setError("");
    try {
      // 점검하던 달로 대상 화면이 열리도록 그 화면의 기간을 먼저 저장한다.
      const previous = await getCommonUserPreference("PERIOD", screen);
      const saver = createPreferenceSaver("PERIOD", screen);
      saver.setToken(previous.data.modDttm);
      const targetPeriod =
        screen === "UI-12" || screen === "UI-13"
          ? { year: period.value.year }
          : period.value;
      await saver.save(JSON.stringify(targetPeriod));
      navigate(`/screens/${screen}`);
    } catch (failure) {
      visiting.current = false;
      setError(message(failure));
    }
  }
  usePreferenceBusinessShortcuts(
    { scope: root, onSearch: () => refresh((value) => value + 1) },
    !mobile && !dialog && !busy,
  );
  if (mobile)
    return (
      <div data-testid="month-close-page" className="month-close-page">
        <p>PC에서 이용할 수 있는 화면입니다.</p>
      </div>
    );
  if (period.status !== "ready")
    return (
      <div data-testid="month-close-page" className="month-close-page">
        <p role={period.status === "error" ? "alert" : "status"}>
          {period.status === "error"
            ? period.message
            : "기간을 불러오는 중입니다."}
        </p>
      </div>
    );
  const sourceBasis = basis ?? period.basis;
  const currentBasis = {
    ...sourceBasis,
    latestYm:
      sourceBasis.latestYm > koreanThisMonth()
        ? sourceBasis.latestYm
        : koreanThisMonth(),
  };
  const locked = busy || detail.loading;
  const remaining = row
    ? row.checks.filter((item) => item.status === WARN).map((item) => item.name)
    : [];
  // 시안 actions: 마감 전 [다시 점검]·[마감하기], 마감한 달은 가장 최근 달이고 관리자일 때만 [마감 해제].
  const actions = row ? (
    !row.isClosed ? (
      <>
        <button
          type="button"
          className="ls-btn"
          data-testid="month-close-recheck"
          disabled={locked}
          onClick={recheck}
        >
          다시 점검
        </button>
        <button
          type="button"
          className="ls-btn ls-btn-p"
          data-testid="month-close-confirm"
          disabled={locked}
          onClick={() => {
            setError("");
            setDialog("confirm");
          }}
        >
          마감하기
        </button>
      </>
    ) : row.reopenable === true && canReopenMonthClose(roles) ? (
      <button
        type="button"
        className="ls-btn ls-btn-dt"
        data-testid="month-close-reopen"
        disabled={locked}
        onClick={() => {
          setError("");
          setDialogError("");
          setReason("");
          setReasonError("");
          setImpact({ status: "loading" });
          setDialog("reopen");
        }}
      >
        <Icon name="lock" size={16} /> 마감 해제
      </button>
    ) : null
  ) : null;
  const titleActions =
    actions &&
    (host ? (
      createPortal(actions, host)
    ) : (
      <div className="month-close-actions">{actions}</div>
    ));
  return (
    <div
      ref={root}
      data-testid="month-close-page"
      data-screen-id="UI-14"
      className="screen-body month-close-page"
    >
      {titleActions}
      <PeriodSelector
        kind="yearMonth"
        variant="monthBox"
        basis={currentBasis}
        value={period.value}
        disabled={busy || dialog !== null}
        onChange={(value) => {
          setDialog(null);
          setError("");
          setValue(value);
        }}
      />
      {row &&
        (row.isClosed ? (
          <Banner kind="lock" icon={<Icon name="lock" size={18} />}>
            <strong>{row.yearMonth}</strong>는{closedBy(row)}
            {row.reopenable
              ? " 마감 해제는 시스템 관리자만 할 수 있습니다."
              : " 가장 최근에 마감한 달만 해제할 수 있습니다."}
          </Banner>
        ) : (
          <Banner
            kind={row.attentionCount ? "warn" : "info"}
            icon={
              <Icon name={row.attentionCount ? "warn" : "info"} size={18} />
            }
          >
            {row.yearMonth} 마감 전 점검 {row.totalCount}개 중{" "}
            <strong>{row.attentionCount}개</strong>를 확인해야 합니다.
          </Banner>
        ))}
      {!dialog && error && (
        <p role="alert" className="ls-neg" data-testid="month-close-error">
          {error}
        </p>
      )}
      {detail.error && (
        <div role="alert">
          <p>{detail.error}</p>
          {!row && (
            <button
              type="button"
              className="ls-btn"
              data-testid="month-close-retry"
              onClick={() => refresh((value) => value + 1)}
            >
              다시 점검
            </button>
          )}
        </div>
      )}
      {!row && detail.loading && (
        <p role="status">점검을 불러오는 중입니다.</p>
      )}
      {row && (
        <section className="month-close-card" aria-label="마감 점검">
          <h2>마감 점검</h2>
          <div className="month-close-checks">
            {row.checks.map((original, index) => {
              // 시안 closeItems(closed): 마감한 달은 모두 정상·「확인 완료」(급여 업로드만 건수), [확인하기] 없음
              const check = row.isClosed
                ? {
                    ...original,
                    status: "정상",
                    description:
                      original.name === "급여 업로드"
                        ? original.description
                        : "확인 완료",
                  }
                : original;
              const warn = check.status === WARN;
              return (
                <div
                  key={check.name}
                  className="month-close-chk"
                  data-testid={`month-close-check-${index}`}
                >
                  <span
                    className={warn ? "month-close-warn" : "month-close-ok"}
                  >
                    <Icon name={warn ? "warn" : "okc"} size={20} />
                    <span className="ls-sr">{check.status}</span>
                  </span>
                  <div className="month-close-chk-text">
                    <strong>{check.name}</strong>
                    <div className="ls-cap">{check.description}</div>
                  </div>
                  {warn && check.targetScreenId ? (
                    <button
                      type="button"
                      className="ls-btn ls-btn-sm"
                      data-testid={`month-close-visit-${index}`}
                      disabled={busy}
                      onClick={() => visit(check.targetScreenId!)}
                    >
                      확인하기
                    </button>
                  ) : (
                    <span />
                  )}
                </div>
              );
            })}
          </div>
        </section>
      )}
      <section
        className="month-close-card"
        aria-label="마감 이력"
        data-testid="month-close-history"
      >
        <h2>마감 이력</h2>
        {!history.value && history.loading && (
          <p role="status">이력을 불러오는 중입니다.</p>
        )}
        {history.error && (
          <div role="alert">
            <p>{history.error}</p>
            <button
              type="button"
              className="ls-btn"
              data-testid="month-close-history-retry"
              onClick={() => refreshHistory((value) => value + 1)}
            >
              다시 시도
            </button>
          </div>
        )}
        {history.value &&
          (history.value.length === 0 ? (
            <p className="ls-cap">마감 이력 없음</p>
          ) : (
            <div className="ls-twrap month-close-twrap">
              <table className="ls-t">
                <thead>
                  <tr>
                    <th>연월</th>
                    <th>작업</th>
                    <th>처리자</th>
                    <th>일시</th>
                    <th>사유</th>
                    <th>남은 점검</th>
                  </tr>
                </thead>
                <tbody>
                  {history.value.map((item, index) => (
                    <tr
                      key={`${item.yearMonth}-${item.occurredAt}-${index}`}
                      data-testid={`month-close-history-${index}`}
                    >
                      <td>{item.yearMonth}</td>
                      <td>
                        {item.action === "마감" ? (
                          <span className="ls-b ls-b-lock">
                            <Icon name="lock" size={12} />
                            {item.action}
                          </span>
                        ) : (
                          <span className="ls-b ls-b-warn">{item.action}</span>
                        )}
                      </td>
                      <td>{item.processor}</td>
                      <td>{formatDateTime(item.occurredAt)}</td>
                      <td className="month-close-wrap">{item.reason || "–"}</td>
                      <td className="month-close-wrap">
                        {item.remainingChecks.join(", ") || "–"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
      </section>
      {dialog === "confirm" && row && (
        <ConfirmDialog
          title={`${row.yearMonth}를 마감할까요?`}
          testId="month-close-confirm-dialog"
          proceedLabel="마감하기"
          description={`마감하면 이 달 자료는 고칠 수 없습니다. ${
            remaining.length
              ? `확인할 항목 ${remaining.length}개(${remaining.join(", ")})가 남아 있습니다. 남은 항목은 마감 이력에 함께 남습니다.`
              : "점검 항목이 모두 정상입니다."
          } 확정하지 않은 영업 파트 정산은 마감과 함께 자동 확정됩니다(마감 뒤 틀린 금액은 다음 달에 반영).`}
          onCancel={close}
          onProceed={() => void submit()}
        />
      )}
      {dialog === "reopen" && row && (
        <Dialog
          title={`${row.yearMonth} 마감을 해제할까요?`}
          testId="month-close-reopen-dialog"
          role="alertdialog"
          closable
          onClose={close}
        >
          <p>
            해제하면 이 달 자료를 다시 고칠 수 있습니다. 다시 마감할 때 이 달
            숫자를 새로 고정하고, 법인세 월할은 이 달부터 마감 안 된 달끼리 다시
            나눕니다. 사유는 기록에 남습니다.
            {impact.status === "ready" && impact.value > 0 && (
              <>
                {" "}
                {nextMonthNumber(row.yearMonth)}월에 넣은 전월 조정이{" "}
                <strong>{impact.value}건</strong> 있습니다. 이 달을 직접 고치면
                같은 정정이 두 번 들어가지 않게 그 줄은 지워 주세요.
              </>
            )}
          </p>
          {impact.status === "loading" && (
            <p role="status" className="ls-cap">
              다음 달 전월 조정 건수를 불러오는 중입니다.
            </p>
          )}
          {impact.status === "error" && (
            <div role="alert">
              <p>{impact.message}</p>
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="month-close-impact-retry"
                onClick={() => refreshImpact((value) => value + 1)}
              >
                조정 건수 다시 조회
              </button>
            </div>
          )}
          <ProjectField id="month-close-reason" label="해제 사유" required>
            <textarea
              ref={reasonRef}
              id="month-close-reason"
              className="ls-in month-close-reason"
              data-testid="month-close-reason"
              maxLength={500}
              placeholder="예: 9월 청구 누락 2건 반영"
              value={reason}
              disabled={busy}
              onChange={(event) => {
                setReason(event.target.value);
                // 공통 규칙: 고치기 시작하면 칸 아래 오류를 지운다
                if (event.target.value.trim()) setReasonError("");
              }}
              aria-invalid={!!reasonError}
              aria-describedby={
                reasonError ? "month-close-reason-error" : undefined
              }
            />
            {reasonError && (
              <span
                id="month-close-reason-error"
                className="field-error ls-neg"
                role="alert"
              >
                {reasonError}
              </span>
            )}
          </ProjectField>
          {dialogError && (
            <p role="alert" className="ls-neg">
              {dialogError}
            </p>
          )}
          <div className="app-actions">
            <button
              type="button"
              data-testid="month-close-reopen-cancel"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              type="button"
              className="app-danger"
              data-testid="month-close-reopen-submit"
              disabled={busy || impact.status !== "ready"}
              onClick={() => void submit()}
            >
              마감 해제
            </button>
          </div>
        </Dialog>
      )}
      {toast && (
        <Toast
          message={toast.text}
          kind={toast.kind}
          testId="month-close-toast"
          onDismiss={() => setToast(null)}
        />
      )}
    </div>
  );
}

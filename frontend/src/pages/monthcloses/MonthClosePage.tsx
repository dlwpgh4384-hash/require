import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Banner } from "../../shared/list/DetailPanel";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
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
import {
  canReopenMonthClose,
  getCommonMonthClose,
  getCommonMonthCloseHistory,
  getCommonMonthCloseReopenImpact,
  putCommonMonthCloseConfirm,
  putCommonMonthCloseReopen,
  MonthCloseRequestError,
  type MonthClose,
  type MonthCloseHistoryItem,
} from "./monthCloses";
import "./monthCloses.css";

type Load<T> =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; value: T };
const message = (failure: unknown) =>
  failure instanceof Error ? failure.message : "다시 시도해 주세요.";
const developmentRoles = ["ADMIN", "FIN", "SALES_LDR"];

/** UI-14 월 마감. 로그인 연결 전에는 서버와 같은 개발 실행 문맥을 사용한다. */
export function MonthClosePage({
  roles = developmentRoles,
}: { roles?: readonly string[] } = {}) {
  const root = useRef<HTMLDivElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const writing = useRef(false);
  const navigate = useNavigate();
  const { mobile } = useViewport();
  const { state: period, setValue } = useScreenPeriod("UI-14", {
    kind: "yearMonth",
  });
  const [basis, setBasis] = useState<PeriodBasis | null>(null);
  const [detail, setDetail] = useState<Load<MonthClose>>({ status: "loading" });
  const [history, setHistory] = useState<Load<MonthCloseHistoryItem[]>>({
    status: "loading",
  });
  const [impact, setImpact] = useState<Load<number>>({ status: "loading" });
  const [revision, refresh] = useState(0);
  const [historyRevision, refreshHistory] = useState(0);
  const [impactRevision, refreshImpact] = useState(0);
  const [dialog, setDialog] = useState<"confirm" | "reopen" | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const month =
    period.status === "ready"
      ? `${period.value.year}-${String(period.value.month).padStart(2, "0")}`
      : "";
  useEffect(() => {
    if (!month || mobile) return;
    const controller = new AbortController();
    setDetail({ status: "loading" });
    getCommonMonthClose(month, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setDetail({ status: "ready", value });
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setDetail({ status: "error", message: message(failure) });
      });
    return () => controller.abort();
  }, [month, revision, mobile]);
  useEffect(() => {
    if (!month || mobile) return;
    const controller = new AbortController();
    setHistory({ status: "loading" });
    getCommonMonthCloseHistory(month, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted)
          setHistory({ status: "ready", value: value.items });
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setHistory({ status: "error", message: message(failure) });
      });
    return () => controller.abort();
  }, [month, revision, historyRevision, mobile]);
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
  const close = () => {
    if (!writing.current) {
      setDialog(null);
      setError("");
    }
  };
  async function submit() {
    if (
      writing.current ||
      !dialog ||
      detail.status !== "ready" ||
      detail.value.yearMonth !== month
    )
      return;
    if (dialog === "reopen") {
      if (impact.status !== "ready") return;
      if (!reason.trim() || [...reason].length > 500) {
        setReasonError("해제 사유를 1~500자로 입력해 주세요.");
        reasonRef.current?.focus();
        return;
      }
    }
    writing.current = true;
    setBusy(true);
    setError("");
    setReasonError("");
    try {
      const result =
        dialog === "confirm"
          ? await putCommonMonthCloseConfirm(month, detail.value.modifiedAt)
          : await putCommonMonthCloseReopen(
              month,
              detail.value.modifiedAt,
              reason,
            );
      setDetail({ status: "ready", value: result });
      setDialog(null);
      setNotice(result.isClosed ? "마감했습니다." : "마감을 해제했습니다.");
      refresh((value) => value + 1);
      requestMenuBadgeRefresh();
      try {
        const preference = await getCommonUserPreference("PERIOD", "UI-14");
        if (preference.meta.periodBasis) setBasis(preference.meta.periodBasis);
      } catch {
        /* 저장 결과를 유지하고 기간 기준은 다음 조회에서 다시 읽는다. */
      }
    } catch (failure) {
      setError(message(failure));
      if (failure instanceof MonthCloseRequestError) {
        const field = failure.fields.find((item) => item.field === "reason");
        if (field) {
          setReasonError(field.message);
          reasonRef.current?.focus();
        }
      }
    } finally {
      writing.current = false;
      setBusy(false);
    }
  }
  async function visit(screen: string) {
    if (period.status !== "ready" || busy || !/^UI-\d{2}$/.test(screen)) return;
    setError("");
    try {
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
  const row =
    detail.status === "ready" && detail.value.yearMonth === month
      ? detail.value
      : null;
  return (
    <div
      ref={root}
      data-testid="month-close-page"
      data-screen-id="UI-14"
      className="month-close-page"
    >
      <PeriodSelector
        kind="yearMonth"
        variant="monthBox"
        basis={currentBasis}
        value={period.value}
        disabled={busy || dialog !== null}
        onChange={(value) => {
          setDialog(null);
          setError("");
          setNotice("");
          setValue(value);
        }}
      />
      {notice && (
        <p role="status" data-testid="month-close-success">
          {notice}
        </p>
      )}
      {row && (
        <>
          <Banner
            kind={row.isClosed ? "lock" : "warn"}
            icon={<span aria-hidden="true">{row.isClosed ? "✓" : "!"}</span>}
          >
            {row.isClosed
              ? `${row.closedAt ? formatDateTime(row.closedAt) : ""} · ${row.closedBy ?? ""} 마감`
              : `점검 ${row.totalCount}개 중 ${row.attentionCount}개 확인 필요`}
          </Banner>
          <div className="month-close-table">
            <table>
              <thead>
                <tr>
                  <th>점검 항목</th>
                  <th>상태</th>
                  <th>한 줄 설명</th>
                  <th>이동</th>
                </tr>
              </thead>
              <tbody>
                {row.checks.map((check, index) => (
                  <tr
                    key={check.name}
                    data-testid={`month-close-check-${index}`}
                  >
                    <th scope="row">{check.name}</th>
                    <td>{check.status}</td>
                    <td>{check.description}</td>
                    <td>
                      {check.targetScreenId && (
                        <button
                          type="button"
                          data-testid={`month-close-visit-${index}`}
                          disabled={busy}
                          onClick={() => void visit(check.targetScreenId!)}
                        >
                          확인하기
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>
            마감 뒤 정정은 다음달 재무 항목의 전월 조정으로 반영합니다. 해제해도
            파트 정산 확정은 유지됩니다.
          </p>
          <div className="app-actions">
            {!row.isClosed ? (
              <>
                <button
                  type="button"
                  data-testid="month-close-recheck"
                  disabled={busy}
                  onClick={() => {
                    setError("");
                    refresh((value) => value + 1);
                  }}
                >
                  다시 점검
                </button>
                <button
                  type="button"
                  data-testid="month-close-confirm"
                  disabled={busy}
                  onClick={() => {
                    setError("");
                    setDialog("confirm");
                  }}
                >
                  마감하기
                </button>
              </>
            ) : (
              canReopenMonthClose(roles) && (
                <button
                  type="button"
                  data-testid="month-close-reopen"
                  disabled={busy}
                  onClick={() => {
                    setError("");
                    setReason("");
                    setReasonError("");
                    setImpact({ status: "loading" });
                    setDialog("reopen");
                  }}
                >
                  마감 해제
                </button>
              )
            )}
          </div>
        </>
      )}
      {detail.status === "loading" && (
        <p role="status">점검을 불러오는 중입니다.</p>
      )}
      {detail.status === "error" && (
        <div role="alert">
          <p>{detail.message}</p>
          <button
            type="button"
            data-testid="month-close-retry"
            onClick={() => refresh((value) => value + 1)}
          >
            다시 점검
          </button>
        </div>
      )}
      {!dialog && error && <p role="alert">{error}</p>}
      <section aria-label="마감 이력" data-testid="month-close-history">
        <h2>마감 이력</h2>
        {history.status === "loading" && (
          <p role="status">이력을 불러오는 중입니다.</p>
        )}
        {history.status === "error" && (
          <div role="alert">
            <p>{history.message}</p>
            <button
              type="button"
              data-testid="month-close-history-retry"
              onClick={() => refreshHistory((value) => value + 1)}
            >
              다시 시도
            </button>
          </div>
        )}
        {history.status === "ready" &&
          (history.value.length === 0 ? (
            <p>마감 이력 없음</p>
          ) : (
            <div className="month-close-table">
              <table>
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
                      <td>{item.action}</td>
                      <td>{item.processor}</td>
                      <td>{formatDateTime(item.occurredAt)}</td>
                      <td>{item.reason ?? "–"}</td>
                      <td>{item.remainingChecks.join(", ") || "–"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
      </section>
      {dialog === "confirm" && row && (
        <ConfirmDialog
          title="마감 확인"
          testId="month-close-confirm-dialog"
          proceedLabel={busy ? "처리 중" : "마감"}
          description={`남은 점검: ${row.attentionCount}개. 확인 필요가 남아도 마감할 수 있습니다.${error ? ` ${error}` : ""}`}
          onCancel={close}
          onProceed={() => void submit()}
        />
      )}
      {dialog === "reopen" && (
        <Dialog
          title="마감 해제"
          testId="month-close-reopen-dialog"
          closable
          onClose={close}
        >
          {impact.status === "loading" && (
            <p role="status">다음달 전월 조정 건수를 불러오는 중입니다.</p>
          )}
          {impact.status === "error" && (
            <div role="alert">
              <p>{impact.message}</p>
              <button
                type="button"
                data-testid="month-close-impact-retry"
                onClick={() => refreshImpact((value) => value + 1)}
              >
                조정 건수 다시 조회
              </button>
            </div>
          )}
          {impact.status === "ready" && (
            <p>
              다음달 전월 조정: {impact.value}건. 중복 정정되지 않도록 확인해
              주세요.
            </p>
          )}
          <label htmlFor="month-close-reason">해제 사유 (필수)</label>
          <textarea
            ref={reasonRef}
            id="month-close-reason"
            data-testid="month-close-reason"
            value={reason}
            disabled={busy}
            onChange={(event) => setReason(event.target.value)}
            aria-invalid={!!reasonError}
            aria-describedby="month-close-reason-error"
          />
          <p
            id="month-close-reason-error"
            className="field-error"
            role={reasonError ? "alert" : undefined}
          >
            {reasonError}
          </p>
          {error && <p role="alert">{error}</p>}
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
              data-testid="month-close-reopen-submit"
              disabled={busy || impact.status !== "ready"}
              onClick={() => void submit()}
            >
              해제
            </button>
          </div>
        </Dialog>
      )}
    </div>
  );
}

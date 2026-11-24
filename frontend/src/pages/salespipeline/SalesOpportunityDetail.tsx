import { useEffect, useRef, useState, type ReactNode } from "react";
import { ConfirmDialog, Dialog, Toast } from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  DetailPanel,
  KeyValues,
  type DetailTab,
} from "../../shared/list";
import { digitsOnly, focusFirstError } from "../../shared/feedback/messages";
import { formatKoreanMoney, formatMoney } from "../../shared/list/moneyUnit";
import { formatDate, koreanToday } from "../../shared/time";
import { ProjectLink } from "../projects/ProjectLink";
import "../../shared/list/list.css";
import "./salesOpportunities.css";
import { quoteNeeds, salesIcon, SalesQuoteView, won } from "./SalesOpportunityForm";
import {
  formatMm,
  formatPercent,
  getCommonSalesOpportunity,
  plainNumber,
  salesOpportunityAction,
  salesWinDefaults,
  SalesOpportunityError,
  SALES_CONTRACT_TYPES,
  type SalesNotice,
  type SalesOpportunity,
  type SalesOpportunityAction,
  type SalesOpportunityOptions,
  type SalesQuote,
  writtenOpportunity,
} from "./salesOpportunities";

type DialogKind = "reject" | "win" | "lose" | "stop" | "resume";
const latestHistory = (row: SalesOpportunity, code: string) =>
  [...row.approvalHistory]
    .filter((h) => h.actionCode === code)
    .sort((a, b) => b.actedAt.localeCompare(a.actedAt))[0];

/** 영업 기회 상세(UI-29 서랍): 머리(이름·고객사 · 단계 · 확률·[수정]·[⋯]) + 기본 정보 / 필요 인력 / 견적 점검 탭. */
export function SalesOpportunityDetail({
  opportunityId,
  options,
  onEdit,
  onChanged,
  onClose,
  drawer = false,
  onNotice,
  testId = "sales-opportunity-detail",
}: {
  opportunityId: string;
  options: SalesOpportunityOptions;
  onEdit: (row: SalesOpportunity) => void;
  onChanged: (row: SalesOpportunity) => void;
  onClose: () => void;
  /** true면 공통 DetailPanel 서랍을 직접 그린다(바깥에서 서랍으로 감싸지 않을 때). */
  drawer?: boolean;
  /** 서랍을 닫는 처리(수주·실주·삭제·다시 사용) 뒤 알림을 화면이 띄운다. 주면 처리 뒤 서랍을 닫는다. */
  onNotice?: (notice: SalesNotice) => void;
  testId?: string;
}) {
  const [row, setRow] = useState<SalesOpportunity | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [dialog, setDialog] = useState<DialogKind | null>(null);
  const [values, setValues] = useState<Record<string, string | number>>({});
  const [fields, setFields] = useState<Record<string, string>>({});
  const [dialogError, setDialogError] = useState("");
  const [tab, setTab] = useState("basic");
  const [menu, setMenu] = useState(false);
  const [toast, setToast] = useState<SalesNotice | null>(null);
  const [quote, setQuote] = useState<SalesQuote | null>(null);
  const [quoteError, setQuoteError] = useState("");
  const menuRef = useRef<HTMLDivElement>(null);
  // 작은 창(수주·실주·반려) 오류 뒤 첫 오류 칸으로(공통 focusFirstError). 오류 칸과 busy 해제가 그려진 뒤에 옮긴다.
  const dialogBody = useRef<HTMLFieldSetElement>(null);
  const [focusRequest, setFocusRequest] = useState(0);
  useEffect(() => {
    if (focusRequest) focusFirstError(dialogBody.current);
  }, [focusRequest]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    getCommonSalesOpportunity(opportunityId)
      .then((value) => {
        if (active) {
          setRow(value);
          setError("");
        }
      })
      .catch((reason) => {
        if (active)
          setError(
            reason instanceof Error
              ? reason.message
              : "상세를 불러오지 못했습니다.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [opportunityId]);
  // 견적 점검 탭을 열 때만 계산한다. 승인 띠는 저장 때 판정한 이익률(상세 응답)을 보인다.
  const needQuote = !!row && tab === "quote";
  useEffect(() => {
    if (!row || !needQuote) return;
    let active = true;
    setQuoteError("");
    quoteNeeds(row.needs)
      .then((value) => {
        if (active) setQuote(value);
      })
      .catch((reason) => {
        if (active)
          setQuoteError(
            reason instanceof Error
              ? reason.message
              : "견적을 계산하지 못했습니다.",
          );
      });
    return () => {
      active = false;
    };
  }, [row, needQuote]);
  useEffect(() => {
    if (!menu) return;
    const down = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenu(false);
    };
    document.addEventListener("mousedown", down);
    return () => document.removeEventListener("mousedown", down);
  }, [menu]);
  const name = (group: string, code: string | null | undefined) =>
    options.codes[group]?.find((c) => c.code === code)?.codeName ||
    code ||
    "–";
  const developer = (id: string) =>
    options.developers.find((d) => d.developerId === id);
  const tentatives = row
    ? row.needs.flatMap((need, index) =>
        need.tentativeDeveloperIds.map((id) => ({ id, need, index })),
      )
    : [];
  function notify(notice: SalesNotice, close: boolean) {
    if (close && onNotice) {
      onNotice(notice);
      onClose();
    } else setToast(notice);
  }
  function open(next: DialogKind) {
    if (!row) return;
    setMenu(false);
    setDialog(next);
    setFields({});
    setDialogError("");
    setValues(
      next === "win"
        ? salesWinDefaults(row)
        : next === "reject"
          ? { reason: "" }
          : next === "lose"
            ? { lossReasonCode: "", memo: "" }
            : {},
    );
  }
  async function perform(action: SalesOpportunityAction) {
    if (!row || busy) return;
    setFields({});
    setDialogError("");
    if (action === "reject" && [...String(values.reason || "")].length > 300) {
      setFields({ reason: "반려 사유는 300자 이내로 넣어 주세요." });
      setFocusRequest((n) => n + 1);
      return;
    }
    if (action === "lose" && !values.lossReasonCode) {
      setFields({ lossReasonCode: "실주 사유를 골라 주세요." });
      setFocusRequest((n) => n + 1);
      return;
    }
    if (action === "win") {
      const problems: Record<string, string> = {};
      if (!(Number(values.contractAmount) > 0))
        problems.contractAmount = "계약금액을 입력해 주세요.";
      const s = String(values.contractStartDate || "");
      const e = String(values.contractEndDate || "");
      if (!s || !e || e <= s)
        problems.contractEndDate =
          "계약 기간을 확인해 주세요. 종료일이 시작일보다 앞입니다.";
      if (Object.keys(problems).length) {
        setFields(problems);
        setFocusRequest((n) => n + 1);
        return;
      }
    }
    const body =
      action === "reject"
        ? { reason: String(values.reason || "").trim() }
        : action === "lose"
          ? {
              lossReasonCode: values.lossReasonCode,
              memo: String(values.memo || "").trim(),
            }
          : values;
    setBusy(true);
    try {
      const written = await salesOpportunityAction(
        row,
        action,
        action === "approve" ? {} : body,
      );
      // 처리 응답이 최신 기회다. 응답에 없을 때만 다시 읽고, 그 읽기가 실패해도 처리는 끝났으므로
      // 실패로 알리지 않고 서랍을 닫아 목록을 다시 읽게 한다(옛 수정 시각으로 다시 누르지 않게).
      const fresh = await writtenOpportunity(written, row.opportunityId);
      const latest = fresh ?? row;
      if (fresh) setRow(fresh);
      setDialog(null);
      onChanged(latest);
      // 승인·반려는 서랍을 열어 둔다(최신 기회를 못 받았을 때만 닫는다).
      const close = !fresh;
      const count = tentatives.length;
      if (action === "approve")
        notify({ kind: "ok", message: "견적을 승인했습니다." }, close);
      else if (action === "reject")
        notify(
          {
            kind: "ok",
            message: "견적을 반려했습니다. 영업 담당자에게 알렸습니다.",
          },
          close,
        );
      else if (action === "win")
        notify(
          {
            kind: "ok",
            message: `수주 처리했습니다. 프로젝트 ${latest.projectId ?? ""}를 만들었습니다.${count ? ` 가배정한 ${count}명은 투입 배정 화면의 「배정 후보」로 넘어갔습니다.` : ""}`,
          },
          true,
        );
      else if (action === "lose")
        notify({ kind: "ok", message: "실주 처리했습니다." }, true);
      else if (action === "stop")
        notify(
          {
            kind: "ok",
            message:
              "삭제했습니다. 종료 탭의 [사용 중지 포함]에서 다시 쓸 수 있습니다.",
          },
          true,
        );
      else if (action === "resume")
        notify(
          { kind: "ok", message: `「${latest.opportunityName}」을 다시 사용합니다.` },
          true,
        );
    } catch (reason) {
      const message =
        reason instanceof Error
          ? reason.message
          : "처리하지 못했습니다. 다시 시도해 주세요.";
      if (reason instanceof SalesOpportunityError && reason.status === 409) {
        // 작은 창은 닫고 최신 상세를 다시 읽는다(FR-0106).
        setDialog(null);
        setToast({ kind: "warn", message });
        try {
          const latest = await getCommonSalesOpportunity(row.opportunityId);
          setRow(latest);
          onChanged(latest);
        } catch {
          setRow(null);
          setError(message);
        }
      } else {
        if (reason instanceof SalesOpportunityError)
          setFields(
            Object.fromEntries(reason.errors.map((e) => [e.field, e.message])),
          );
        if (dialog === "win" || dialog === "lose" || dialog === "reject") {
          setDialogError(message);
          setFocusRequest((n) => n + 1);
        }
        else {
          // 확인 창(삭제·다시 사용)은 한 번만 눌리므로 닫고 알린다.
          setDialog(null);
          setToast({ kind: "warn", message });
        }
      }
    } finally {
      setBusy(false);
    }
  }
  const set = (key: string, value: string | number) => {
    setValues((v) => ({ ...v, [key]: value }));
    setFields((f) =>
      Object.fromEntries(Object.entries(f).filter(([k]) => k !== key)),
    );
  };
  const fieldError = (key: string) =>
    fields[key] ? (
      <span className="so-errmsg" role="alert">
        {fields[key]}
      </span>
    ) : null;
  const fc = (
    id: string,
    label: string,
    control: ReactNode,
    help?: string,
    required = true,
  ) => (
    <div className="so-mfc">
      <label className={`so-fl so-mfl${required ? " so-req" : ""}`} htmlFor={id}>
        {label}
      </label>
      {control}
      {help && <span className="so-help">{help}</span>}
      {fieldError(id.replace(/^so-act-/, ""))}
    </div>
  );
  const actionButtons = (confirmLabel: string, danger: boolean, action: SalesOpportunityAction) => (
    <div className="app-actions">
      <button
        type="button"
        data-testid="sales-action-cancel"
        onClick={() => setDialog(null)}
      >
        취소
      </button>
      <button
        type="button"
        data-testid="sales-action-confirm"
        className={danger ? "app-danger" : "primary-button"}
        disabled={busy}
        onClick={() => perform(action)}
      >
        {confirmLabel}
      </button>
    </div>
  );
  const today = koreanToday();
  const ended = !!row && row.opportunityStatusCode !== "ING";
  const statusName = row ? name("OPP_STAT", row.opportunityStatusCode) : "";
  const sub = row
    ? `${row.customerName} · ${ended ? statusName : `${name("SALES_STEP", row.stageCode)} · 확률 ${plainNumber(row.winningProbability)}%`}`
    : undefined;
  const headerActions = row ? (
    row.opportunityStatusCode === "ING" ? (
      <>
        <button
          type="button"
          className="ls-btn ls-btn-sm"
          data-testid="sales-opportunity-edit"
          onClick={() => onEdit(row)}
        >
          수정
        </button>
        <div className="so-more" ref={menuRef}>
          <button
            type="button"
            className="ls-ibtn ls-ibtn-sm"
            aria-label="더보기"
            aria-expanded={menu}
            data-testid="sales-opportunity-more"
            onClick={() => setMenu((v) => !v)}
          >
            {salesIcon("more", 18)}
          </button>
          {menu && (
            <div className="so-more-menu" role="menu">
              {(
                [
                  ["win", "수주 처리"],
                  ["lose", "실주 처리"],
                  ["stop", "삭제"],
                ] as const
              ).map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  role="menuitem"
                  data-testid={`sales-opportunity-${key}`}
                  onClick={() => open(key)}
                >
                  {label}
                </button>
              ))}
            </div>
          )}
        </div>
      </>
    ) : row.opportunityStatusCode === "STOP" ? (
      <button
        type="button"
        className="ls-btn ls-btn-sm ls-btn-p"
        data-testid="sales-opportunity-resume"
        onClick={() => open("resume")}
      >
        다시 사용
      </button>
    ) : null
  ) : null;
  const approvalBanner = () => {
    if (!row || row.opportunityStatusCode !== "ING") return null;
    // 시안 A.m: 승인 요청·승인 때 판정한 이익률. 지금 다시 계산한 값(견적 점검 탭)과 섞지 않는다.
    if (row.approvalStatusCode === "WAIT") {
      const requested = latestHistory(row, "REQ");
      return (
        <Banner
          kind="warn"
          icon={salesIcon("warn")}
          action={
            <span className="so-banner-actions">
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="sales-opportunity-reject"
                disabled={busy}
                onClick={() => open("reject")}
              >
                반려
              </button>
              <button
                type="button"
                className="ls-btn ls-btn-sm ls-btn-p"
                data-testid="sales-opportunity-approve"
                disabled={busy}
                onClick={() => perform("approve")}
              >
                승인
              </button>
            </span>
          }
        >
          견적 승인 대기 — 예상 이익률 {formatPercent(row.expectedProfitRate)}
          {row.approvalRate != null &&
            ` (기준 ${plainNumber(row.approvalRate)}%)`}
          {requested && ` · ${formatDate(requested.actedAt)} 요청`}
        </Banner>
      );
    }
    if (row.approvalStatusCode === "OK") {
      const approved = latestHistory(row, "OK");
      return (
        <Banner kind="info" icon={salesIcon("okc")}>
          견적 승인됨 (경영진
          {approved ? `, ${formatDate(approved.actedAt)}` : ""}) — 예상 이익률{" "}
          {formatPercent(row.approvedProfitRate ?? row.expectedProfitRate)}
        </Banner>
      );
    }
    if (row.approvalStatusCode === "REJ") {
      const rejected = latestHistory(row, "REJ");
      return (
        <Banner kind="lock" icon={salesIcon("lock")}>
          견적 반려 ({formatDate(rejected?.actedAt)})
          {rejected?.reason ? ` · 사유: ${rejected.reason}` : ""} — 단가나
          인원을 고쳐 다시 저장하면 다시 승인을 요청합니다.
        </Banner>
      );
    }
    return null;
  };
  const endedBanner = () => {
    if (!row || !ended) return null;
    const win = row.opportunityStatusCode === "WIN";
    const reason =
      row.opportunityStatusCode === "LOSE" && row.lossReasonCode
        ? `${name("LOSE_RSN", row.lossReasonCode)}${row.lossMemo ? ` — ${row.lossMemo}` : ""}`
        : "";
    return (
      <Banner
        kind={win ? "info" : "lock"}
        icon={salesIcon(win ? "okc" : "lock")}
      >
        {row.resultDate || "–"} {statusName}
        {win && row.projectId ? (
          <>
            {" "}
            — 프로젝트{" "}
            <ProjectLink
              projectId={row.projectId}
              name={row.projectId}
              testId="sales-opportunity-project"
            />
            로 넘어갔습니다.
          </>
        ) : reason ? (
          ` — 사유: ${reason}`
        ) : (
          ""
        )}
      </Banner>
    );
  };
  const tabs: DetailTab[] | undefined = row
    ? [
        {
          key: "basic",
          label: "기본 정보",
          content: (
            <>
              {endedBanner()}
              {approvalBanner()}
              {row.stale && !ended && (
                <Banner kind="warn" icon={salesIcon("warn")}>
                  마지막 수정 {formatDate(row.modifiedAt)} — 2주 넘게 갱신이
                  없습니다.
                </Banner>
              )}
              <KeyValues
                items={[
                  ["고객사", row.customerName],
                  [
                    "영업 파트",
                    options.parts.find((p) => p.partId === row.salesPartId)
                      ?.partName || "–",
                  ],
                  [
                    "계약유형",
                    row.contractTypeCode ? (
                      <Badge>{name("CTRT_TYPE", row.contractTypeCode)}</Badge>
                    ) : (
                      "–"
                    ),
                  ],
                  ["단계", ended ? "–" : name("SALES_STEP", row.stageCode)],
                  ["수주 확률", `${plainNumber(row.winningProbability)}%`],
                  [
                    "예상 금액",
                    <span className="so-tnum">
                      {won(row.expectedAmount)} 원{" "}
                      <span className="so-cap">
                        확률 반영{" "}
                        {formatKoreanMoney(
                          (row.expectedAmount * row.winningProbability) / 100,
                        )}
                      </span>
                    </span>,
                  ],
                  [
                    "시작 예정월",
                    `${row.startMonth} · ${row.expectedMonths || 6}개월`,
                  ],
                  [
                    "다음 할 일",
                    row.nextAction ? (
                      <>
                        {row.nextAction}
                        {row.nextActionDueDate && (
                          <>
                            {" "}
                            <span
                              className={
                                row.nextActionDueDate < today
                                  ? "so-bad"
                                  : "so-sub"
                              }
                            >
                              ({row.nextActionDueDate}
                              {row.nextActionDueDate < today
                                ? ", 기한 지남"
                                : ""}
                              )
                            </span>
                          </>
                        )}
                      </>
                    ) : (
                      "–"
                    ),
                  ],
                  ["근무지", row.workplace || "–"],
                  ["비고", row.memo || "–"],
                  ["마지막 수정", formatDate(row.modifiedAt)],
                ]}
              />
            </>
          ),
        },
        {
          key: "needs",
          label: `필요 인력 (${row.needs.reduce((s, n) => s + n.requiredCount, 0)}명)`,
          content: (
            <>
              <div className="ls-twrap so-twrap">
                <table className="ls-t">
                  <thead>
                    <tr>
                      <th>직무</th>
                      <th>등급</th>
                      <th className="ls-right">인원</th>
                      <th>기간</th>
                      <th className="ls-right">월 MM</th>
                      <th className="ls-right">예상 단가</th>
                    </tr>
                  </thead>
                  <tbody>
                    {row.needs.map((n, i) => (
                      <tr key={n.needSn ?? i} data-testid={`sales-detail-need-${i}`}>
                        <td>{name("JOB", n.jobCode)}</td>
                        <td>{n.gradeCode ? name("GRADE", n.gradeCode) : "–"}</td>
                        <td className="ls-right">{n.requiredCount}</td>
                        <td>
                          {n.startMonth} · {n.months}개월
                        </td>
                        <td className="ls-right">{formatMm(n.monthlyMm)}</td>
                        <td className="ls-right">{won(n.estimatedUnitPrice)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <h3 className="so-h">가배정 인력 ({tentatives.length})</h3>
              {tentatives.length ? (
                <div className="ls-twrap so-twrap">
                  <table className="ls-t">
                    <thead>
                      <tr>
                        <th>이름</th>
                        <th>직무</th>
                        <th>자리</th>
                        <th>투입 가능</th>
                      </tr>
                    </thead>
                    <tbody>
                      {tentatives.map(({ id, need, index }) => {
                        const d = developer(id);
                        return (
                          <tr key={`${id}-${index}`} data-testid={`sales-detail-tentative-${id}`}>
                            <td>{d?.name || "–"}</td>
                            <td>
                              {d?.primaryJobName ||
                                (d?.primaryJobCode
                                  ? name("JOB", d.primaryJobCode)
                                  : "–")}
                            </td>
                            <td>
                              {index + 1}. {name("JOB", need.jobCode)}
                            </td>
                            <td>{need.startMonth}부터</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="so-sub">
                  가배정한 사람이 없습니다. [수정]에서 행마다 고르거나 [인력
                  추천]을 쓰세요.
                </p>
              )}
            </>
          ),
        },
        {
          key: "quote",
          label: "견적 점검",
          content: (
            <SalesQuoteView
              quote={quote}
              needs={row.needs}
              options={options}
              error={quoteError}
            />
          ),
        },
      ]
    : undefined;
  const status = (
    <>
      {loading && <p role="status">불러오는 중…</p>}
      {error && !loading && (
        <p className="so-errmsg" role="alert">
          {error}
        </p>
      )}
    </>
  );
  const showTabs = row && !loading ? tabs : undefined;
  const current = showTabs?.find((t) => t.key === tab) ?? showTabs?.[0];
  const dialogs = row && (
    <>
      {dialog === "win" && (
        <Dialog
          title="수주 처리"
          testId="sales-opportunity-action-dialog"
          closable
          onClose={() => {
            if (!busy) setDialog(null);
          }}
        >
          <fieldset className="so-mb" disabled={busy} ref={dialogBody}>
            <p>
              「{row.opportunityName}」를 수주로 바꾸고{" "}
              <strong>
                프로젝트 1건과 인원 계획{" "}
                {new Set(row.needs.map((n) => n.jobCode)).size}줄
              </strong>
              (직무별)을 만듭니다. 필요 인력은 직무마다 인원 × 월 MM × 개월을
              더해 그 프로젝트의 인원 계획이 되고, 가배정한 {tentatives.length}
              명은 투입 배정 후보로 넘어갑니다.
            </p>
            {fc(
              "so-act-contractAmount",
              "계약금액",
              <div className="so-row">
                <input
                  id="so-act-contractAmount"
                  className={`so-in so-num${fields.contractAmount ? " so-err" : ""}`}
                  style={{ width: 180 }}
                  inputMode="numeric"
                  maxLength={19}
                  data-testid="sales-action-contractamount"
                  aria-invalid={!!fields.contractAmount}
                  value={
                    Number(values.contractAmount)
                      ? formatMoney(Number(values.contractAmount), "원")
                      : ""
                  }
                  onChange={(e) =>
                    set("contractAmount", Number(digitsOnly(e.target.value)) || 0)
                  }
                />
                <span className="so-sub">원</span>
              </div>,
              "부가세 제외",
            )}
            {fc(
              "so-act-contractTypeCode",
              "계약유형",
              <select
                id="so-act-contractTypeCode"
                className={`so-in${fields.contractTypeCode ? " so-err" : ""}`}
                data-testid="sales-action-contracttypecode"
                aria-invalid={!!fields.contractTypeCode}
                value={String(values.contractTypeCode ?? "")}
                onChange={(e) => set("contractTypeCode", e.target.value)}
              >
                {(options.codes.CTRT_TYPE || [])
                  .filter((c) => SALES_CONTRACT_TYPES.includes(c.code))
                  .map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.codeName}
                    </option>
                  ))}
              </select>,
            )}
            <div className="so-row so-dates">
              {fc(
                "so-act-contractStartDate",
                "계약 시작일",
                <input
                  id="so-act-contractStartDate"
                  className={`so-in${fields.contractStartDate ? " so-err" : ""}`}
                  type="date"
                  data-testid="sales-action-contractstartdate"
                  aria-invalid={!!fields.contractStartDate}
                  value={String(values.contractStartDate ?? "")}
                  onChange={(e) => set("contractStartDate", e.target.value)}
                />,
              )}
              {fc(
                "so-act-contractEndDate",
                "계약 종료일",
                <input
                  id="so-act-contractEndDate"
                  className={`so-in${fields.contractEndDate ? " so-err" : ""}`}
                  type="date"
                  data-testid="sales-action-contractenddate"
                  aria-invalid={!!fields.contractEndDate}
                  value={String(values.contractEndDate ?? "")}
                  onChange={(e) => set("contractEndDate", e.target.value)}
                />,
              )}
            </div>
            {fc(
              "so-act-projectStatusCode",
              "상태",
              <select
                id="so-act-projectStatusCode"
                className="so-in"
                data-testid="sales-action-projectstatuscode"
                value={String(values.projectStatusCode ?? "")}
                onChange={(e) => set("projectStatusCode", e.target.value)}
              >
                {(options.codes.PROJ_STAT || [])
                  .filter((c) => ["ING", "PRE"].includes(c.code))
                  .map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.codeName}
                    </option>
                  ))}
              </select>,
              "계약서 날인 전이면 「수주예정」. 종료는 계약 종료일이 지나면 자동입니다.",
            )}
            {dialogError && (
              <span className="so-errmsg" role="alert">
                {dialogError}
              </span>
            )}
          </fieldset>
          {actionButtons("수주 처리", false, "win")}
        </Dialog>
      )}
      {dialog === "lose" && (
        <Dialog
          title="실주 처리"
          testId="sales-opportunity-action-dialog"
          closable
          onClose={() => {
            if (!busy) setDialog(null);
          }}
        >
          <fieldset className="so-mb" disabled={busy} ref={dialogBody}>
            <p>
              「{row.opportunityName}」를 실주로 바꿉니다. 가배정한{" "}
              {tentatives.length}명은 풀립니다.
            </p>
            {fc(
              "so-act-lossReasonCode",
              "실주 사유",
              <select
                id="so-act-lossReasonCode"
                className={`so-in${fields.lossReasonCode ? " so-err" : ""}`}
                data-testid="sales-action-lossreasoncode"
                aria-invalid={!!fields.lossReasonCode}
                value={String(values.lossReasonCode ?? "")}
                onChange={(e) => set("lossReasonCode", e.target.value)}
              >
                <option value="">선택</option>
                {(options.codes.LOSE_RSN || []).map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
              </select>,
            )}
            {fc(
              "so-act-memo",
              "메모",
              <textarea
                id="so-act-memo"
                className={`so-in so-textarea${fields.memo ? " so-err" : ""}`}
                maxLength={500}
                placeholder="다음 영업에 참고할 내용"
                data-testid="sales-action-memo"
                aria-invalid={!!fields.memo}
                value={String(values.memo ?? "")}
                onChange={(e) => set("memo", e.target.value)}
              />,
              undefined,
              false,
            )}
            {dialogError && (
              <span className="so-errmsg" role="alert">
                {dialogError}
              </span>
            )}
          </fieldset>
          {actionButtons("실주 처리", true, "lose")}
        </Dialog>
      )}
      {dialog === "reject" && (
        <Dialog
          title="견적을 반려할까요?"
          testId="sales-opportunity-action-dialog"
          role="alertdialog"
          closable
          onClose={() => {
            if (!busy) setDialog(null);
          }}
        >
          <fieldset className="so-mb" disabled={busy} ref={dialogBody}>
            <p>영업 담당자에게 단가나 인원을 고쳐 다시 견적하라는 알림이 갑니다.</p>
            <label className="so-fl so-mfl" htmlFor="so-act-reason">
              반려 사유 (선택)
            </label>
            <input
              id="so-act-reason"
              className={`so-in so-wide${fields.reason ? " so-err" : ""}`}
              maxLength={300}
              placeholder="예: 이익률이 낮음. 고급 1명을 중급으로"
              data-testid="sales-action-reason"
              aria-invalid={!!fields.reason}
              value={String(values.reason ?? "")}
              onChange={(e) => set("reason", e.target.value)}
            />
            <span className="so-help">
              300자까지. 기회 상세 띠와 담당자 알림에 함께 보입니다.
            </span>
            {fieldError("reason")}
            {dialogError && (
              <span className="so-errmsg" role="alert">
                {dialogError}
              </span>
            )}
          </fieldset>
          {actionButtons("반려", true, "reject")}
        </Dialog>
      )}
      {dialog === "stop" && (
        <ConfirmDialog
          title={`「${row.opportunityName}」을 삭제할까요?`}
          description="가배정한 인력도 함께 풀립니다. 목록에서는 사라지고 종료 탭의 [사용 중지 포함]에서 다시 쓸 수 있습니다(가배정은 돌아오지 않음)."
          proceedLabel="삭제"
          destructive
          testId="sales-opportunity-action-dialog"
          onCancel={() => setDialog(null)}
          onProceed={() => perform("stop")}
        />
      )}
      {dialog === "resume" && (
        <ConfirmDialog
          title={`「${row.opportunityName}」을 다시 사용할까요?`}
          description="진행 중 목록으로 돌아갑니다. 지울 때 풀린 가배정은 돌아오지 않으니 필요하면 다시 가배정해 주세요."
          proceedLabel="다시 사용"
          testId="sales-opportunity-action-dialog"
          onCancel={() => setDialog(null)}
          onProceed={() => perform("resume")}
        />
      )}
    </>
  );
  const toastView = toast && (
    <Toast
      kind={toast.kind}
      message={toast.message}
      testId="sales-opportunity-detail-toast"
      onDismiss={() => setToast(null)}
    />
  );
  if (drawer)
    return (
      <>
        <DetailPanel
          title={row && !loading ? row.opportunityName : "영업 기회 상세"}
          sub={row && !loading ? sub : undefined}
          actions={!loading ? headerActions : undefined}
          tabs={showTabs}
          activeTab={tab}
          onTab={setTab}
          onClose={onClose}
          testId={testId}
        >
          {status}
        </DetailPanel>
        {dialogs}
        {toastView}
      </>
    );
  return (
    <section
      data-screen-id="UI-29"
      data-testid={testId}
      className="sales-opportunity-detail"
    >
      {status}
      {row && !loading && (
        <>
          <div className="so-dh">
            <div className="so-dh-actions ls-da">{headerActions}</div>
          </div>
          <div className="ls-tabs so-tabs" role="tablist">
            {showTabs?.map((t) => (
              <button
                key={t.key}
                type="button"
                role="tab"
                aria-selected={t.key === current?.key}
                className={`ls-tb${t.key === current?.key ? " ls-on" : ""}`}
                data-testid={`${testId}-tab-${t.key}`}
                onClick={() => setTab(t.key)}
              >
                {t.label}
              </button>
            ))}
          </div>
          <div className="so-db">{current?.content}</div>
        </>
      )}
      {dialogs}
      {toastView}
    </section>
  );
}

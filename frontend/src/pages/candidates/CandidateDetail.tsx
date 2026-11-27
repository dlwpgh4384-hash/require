import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Dialog,
  InlineConfirmation,
  type ToastKind,
} from "../../shared/feedback/Dialog";
import { Badge, DetailPanel, KeyValues } from "../../shared/list";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { formatDate } from "../../shared/time";
import { useSensitiveView } from "../../shared/permission";
import { developerFailure, won } from "../developers/developers";
import { CandidateForm } from "./CandidateForm";
import {
  CandidateRequestError,
  candidateMessages as messages,
  consentNote,
  contactStatusKind,
  getCommonCandidate,
  getCommonCandidateContacts,
  getCommonCandidateMatches,
  postCommonCandidateContact,
  type Candidate,
  type CandidateContact,
  type CandidateMatches,
  type CandidateOptions,
} from "./candidates";

const pct = (value: number) =>
  !Number.isFinite(value)
    ? "–"
    : `${value < 0 ? "−" : ""}${Math.abs(value).toFixed(1)}%`;

/** 후보 상세(시안 openCand): 오른쪽 상세 칸, [기본 정보]·[연락 기록 (n)] 탭. */
export function CandidateDetail({
  candidateId,
  revision = 0,
  options,
  showAmounts: showAmountsProp,
  canManage = true,
  onClose,
  onChanged,
  onNotice,
}: {
  candidateId: string;
  revision?: number;
  options: CandidateOptions;
  showAmounts?: boolean;
  canManage?: boolean;
  onClose: () => void;
  onChanged: () => void;
  onNotice: (message: string, kind?: ToastKind) => void;
}) {
  // 희망 단가·예상 이익률은 서버 meta.sensitive(민감 체크)일 때만(⑨-1). 아니면 서버가 값을 보내지 않는다.
  const sensitive = useSensitiveView();
  const showAmounts = sensitive && (showAmountsProp ?? true);
  const [candidate, setCandidate] = useState<Candidate | null>(null);
  const [contacts, setContacts] = useState<CandidateContact[]>([]);
  const [matches, setMatches] = useState<CandidateMatches>({ items: [] });
  const [tab, setTab] = useState("basic");
  const [busy, setBusy] = useState(true);
  const [contactsBusy, setContactsBusy] = useState(true);
  const [matchesBusy, setMatchesBusy] = useState(true);
  const [error, setError] = useState("");
  const [contactsError, setContactsError] = useState("");
  const [matchesError, setMatchesError] = useState("");
  const [retry, setRetry] = useState(0);
  const [editing, setEditing] = useState(false);
  const [contacting, setContacting] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    setBusy(true);
    setContactsBusy(true);
    setMatchesBusy(true);
    setError("");
    setContactsError("");
    setMatchesError("");
    void getCommonCandidate(candidateId, signal)
      .then((value) => {
        if (!signal.aborted) setCandidate(value);
      })
      .catch((reason) => {
        if (!signal.aborted) setError(developerFailure(reason));
      })
      .finally(() => {
        if (!signal.aborted) setBusy(false);
      });
    void getCommonCandidateContacts(candidateId, signal)
      .then((value) => {
        if (!signal.aborted) setContacts(value.items);
      })
      .catch((reason) => {
        if (!signal.aborted) setContactsError(developerFailure(reason));
      })
      .finally(() => {
        if (!signal.aborted) setContactsBusy(false);
      });
    void getCommonCandidateMatches(candidateId, signal)
      .then((value) => {
        if (!signal.aborted) setMatches(value);
      })
      .catch((reason) => {
        if (!signal.aborted) setMatchesError(developerFailure(reason));
      })
      .finally(() => {
        if (!signal.aborted) setMatchesBusy(false);
      });
    return () => controller.abort();
  }, [candidateId, revision, retry]);
  const refresh = () => {
    setRetry((value) => value + 1);
    onChanged();
  };
  const conflict = () => {
    setEditing(false);
    setContacting(false);
    onNotice(messages.conflict, "warn");
    refresh();
  };
  const retryButton = (id: string) => (
    <button
      type="button"
      className="ls-btn ls-btn-sm"
      data-testid={id}
      onClick={() => setRetry((value) => value + 1)}
    >
      다시 시도
    </button>
  );
  const c = candidate;
  const threshold = (value: number | null | undefined) =>
    value != null && Number.isFinite(Number(value)) ? Number(value) : null;
  // 기준(운영 기준값)은 자리마다 또는 응답에 한 번 온다. 없으면 서버의 lowMargin 표시를 쓴다.
  const lowRate =
    threshold(matches.lowMarginRate) ??
    threshold(matches.items.find((item) => item.lowMarginRate != null)?.lowMarginRate);
  const low = (rate: number, flagged: boolean, own?: number | null) => {
    const limit = threshold(own) ?? lowRate;
    return limit != null ? rate < limit : flagged;
  };
  const sub = c
    ? [
        [c.employeeTypeName, c.companyName].filter(Boolean).join(" · "),
        `${c.primaryJobName}${c.secondaryJobName ? ` (보조 ${c.secondaryJobName})` : ""}`,
        c.gradeName,
      ].join(" · ")
    : undefined;
  const consent = c ? consentNote(c) : "";
  const basic = (
    <div className="candidate-detail-body" data-testid="candidate-basic-panel">
      {busy && <p role="status">불러오는 중…</p>}
      {error && (
        <p role="alert" className="candidate-error">
          {error} {retryButton("candidate-detail-retry")}
        </p>
      )}
      {c && (
        <KeyValues
          items={[
            [
              "연락 상태",
              <Badge kind={contactStatusKind(c.contactStatusName)}>
                {c.contactStatusName}
              </Badge>,
            ],
            ...(showAmounts
              ? ([
                  [
                    "희망 단가",
                    <span className="candidate-num">
                      {won(c.desiredMonthlyRate)} 원{" "}
                      <span className="ls-cap" data-testid="candidate-rate-note">
                        {c.employeeTypeCode === "OS"
                          ? "업체에 주는 금액(부가세 별도)"
                          : "본인에게 주는 금액"}
                      </span>
                    </span>,
                  ],
                ] as [string, ReactNode][])
              : []),
            ["투입 가능일", c.availableDate],
            ["전화번호", c.phone || "–"],
            [
              "이메일",
              <>
                {c.email || "–"}
                {consent && (
                  <>
                    {" "}
                    <span className="ls-cap" data-testid="candidate-consent">
                      {consent}
                    </span>
                  </>
                )}
              </>,
            ],
            ["보조 직무", c.secondaryJobName || "–"],
            [
              "기술",
              c.skills.map((skill) => (
                <span className="ls-chip" key={skill}>
                  {skill}
                </span>
              )),
            ],
            ["거주지", c.homeAddress || "–"],
            ["출처", c.sourceName],
            ["메모", c.memo || "–"],
          ]}
        />
      )}
      <h2>맞는 자리</h2>
      {matchesBusy ? (
        <p role="status">불러오는 중…</p>
      ) : matchesError ? (
        <p role="alert" className="candidate-error">
          {matchesError} {retryButton("candidate-matches-retry")}
        </p>
      ) : !matches.items.length ? (
        <p className="candidate-sub" data-testid="candidate-matches-empty">
          지금 진행 중인 영업 기회 가운데 맞는 자리가 없습니다.
        </p>
      ) : (
        <>
          <div className="ls-twrap">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>영업 기회</th>
                  <th>직무 · 등급</th>
                  <th>시작</th>
                  <th className="ls-right">자리 단가</th>
                  {showAmounts && <th className="ls-right">예상 이익률</th>}
                </tr>
              </thead>
              <tbody>
                {matches.items.map((match, index) => (
                  <tr
                    key={`${match.opportunityId}-${index}`}
                    data-testid={`candidate-match-${index}`}
                  >
                    <td>
                      {match.opportunityName}{" "}
                      <span className="ls-cap">{match.winningProbability}%</span>
                    </td>
                    <td>
                      {match.jobName} · {match.gradeName}
                    </td>
                    <td>{match.startMonth}</td>
                    <td className="ls-right">{won(match.slotMonthlyRate)}</td>
                    {showAmounts && (
                      <td
                        className={`ls-right${low(match.expectedProfitRate, match.lowMargin, match.lowMarginRate) ? " candidate-bad" : ""}`}
                        data-testid={`candidate-match-rate-${index}`}
                      >
                        {pct(match.expectedProfitRate)}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {showAmounts && (
            <p className="ls-note" data-testid="candidate-match-note">
              예상 이익률 = (자리 단가 − 희망 단가 − 팀비·경비) ÷ 자리 단가.{" "}
              {lowRate != null
                ? `기준 ${lowRate}%보다 낮으면 단가를 다시 협의하세요.`
                : "기준보다 낮으면 단가를 다시 협의하세요."}
            </p>
          )}
        </>
      )}
    </div>
  );
  const history = (
    <div className="candidate-detail-body" data-testid="candidate-contacts-panel">
      {contactsBusy ? (
        <p role="status">불러오는 중…</p>
      ) : contactsError ? (
        <p role="alert" className="candidate-error">
          {contactsError} {retryButton("candidate-contacts-retry")}
        </p>
      ) : (
        <div className="ls-twrap">
          <table className="ls-t">
            <thead>
              <tr>
                <th>날짜</th>
                <th>상태</th>
                <th>메모</th>
              </tr>
            </thead>
            <tbody>
              {contacts.map((contact, index) => (
                <tr
                  key={`${contact.recordedAt}-${index}`}
                  data-testid={`candidate-history-${index}`}
                >
                  <td>{formatDate(contact.recordedAt)}</td>
                  <td>
                    <Badge kind={contactStatusKind(contact.contactStatusName)}>
                      {contact.contactStatusName}
                    </Badge>
                  </td>
                  <td className="candidate-wrap">{contact.memo}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
  return (
    <>
      <DetailPanel
        title={c?.name || "후보 상세"}
        sub={sub}
        testId="candidate-detail"
        onClose={() => {
          if (!editing && !contacting) onClose();
        }}
        actions={
          canManage ? (
            <>
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="candidate-edit"
                disabled={busy || !c}
                onClick={() => setEditing(true)}
              >
                수정
              </button>
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="candidate-contact"
                disabled={busy || !c}
                onClick={() => setContacting(true)}
              >
                연락 기록
              </button>
            </>
          ) : undefined
        }
        activeTab={tab}
        onTab={setTab}
        tabs={[
          { key: "basic", label: "기본 정보", content: basic },
          {
            key: "contacts",
            label: `연락 기록 (${contacts.length})`,
            content: history,
          },
        ]}
      />
      {editing && c && (
        <CandidateForm
          candidate={c}
          options={options}
          onCancel={() => setEditing(false)}
          onSaved={(saved) => {
            setEditing(false);
            onNotice(messages.updated(saved.name || c.name));
            refresh();
          }}
          onConflict={conflict}
        />
      )}
      {contacting && c && (
        <CandidateContactForm
          candidate={c}
          options={options}
          onCancel={() => setContacting(false)}
          onSaved={() => {
            setContacting(false);
            onNotice(messages.contacted);
            refresh();
          }}
          onConflict={conflict}
        />
      )}
    </>
  );
}

/** 연락 기록 창(UI-33-M3, 시안 candLog). */
export function CandidateContactForm({
  candidate,
  options,
  onCancel,
  onSaved,
  onConflict,
}: {
  candidate: Candidate;
  options: CandidateOptions;
  onCancel: () => void;
  onSaved: () => void;
  onConflict: () => void;
}) {
  const statuses = options.codes.CNTC_STAT || [];
  const [status, setStatus] = useState(
    candidate.contactStatusCode || statuses[0]?.code || "",
  );
  const [memo, setMemo] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const dirty = !!memo.trim() || status !== (candidate.contactStatusCode || "");
  const setDirty = useScreenDirty();
  useEffect(() => {
    setDirty("UI-33", dirty);
    return () => setDirty("UI-33", false);
  }, [dirty, setDirty]);
  const close = () => {
    if (pending.current) return;
    if (dirty) setLeaving(true);
    else onCancel();
  };
  async function save() {
    if (pending.current) return;
    const invalid: Record<string, string> = {};
    if (!status) invalid.contactStatusCode = messages.required;
    if (!memo.trim()) invalid.memo = messages.memo;
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      await postCommonCandidateContact(candidate.candidateId, {
        modifiedAt: candidate.modifiedAt,
        contactStatusCode: status,
        memo: memo.trim(),
      });
      setDirty("UI-33", false);
      onSaved();
    } catch (reason) {
      if (reason instanceof CandidateRequestError && reason.status === 409) {
        setDirty("UI-33", false);
        onConflict();
        return;
      }
      setError(developerFailure(reason));
      if (reason instanceof CandidateRequestError)
        setErrors(
          Object.fromEntries(
            reason.errors.map((item) => [item.field, item.message]),
          ),
        );
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return (
    <>
      <Dialog
        title={`${candidate.name} 연락 기록`}
        onClose={close}
        testId="candidate-contact-dialog"
        closable
      >
        <form
          className="candidate-contact-form"
          data-testid="candidate-contact-form"
          data-screen-id="UI-33-M3"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <div className="candidate-field">
            <label htmlFor="candidate-contact-status">
              연락 상태<span className="field-required">*</span>
            </label>
            <select
              id="candidate-contact-status"
              className="ls-in"
              data-testid="candidate-contact-status"
              aria-invalid={!!errors.contactStatusCode}
              disabled={busy}
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setErrors((old) => ({ ...old, contactStatusCode: "" }));
              }}
            >
              {statuses.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.codeName}
                </option>
              ))}
            </select>
            {errors.contactStatusCode && (
              <small className="field-error" role="alert">
                {errors.contactStatusCode}
              </small>
            )}
          </div>
          <div className="candidate-field">
            <label htmlFor="candidate-contact-memo">
              메모<span className="field-required">*</span>
            </label>
            <textarea
              id="candidate-contact-memo"
              className="ls-in"
              data-testid="candidate-contact-memo"
              aria-invalid={!!errors.memo}
              disabled={busy}
              value={memo}
              maxLength={200}
              rows={3}
              onChange={(event) => {
                setMemo(event.target.value);
                setErrors((old) => ({ ...old, memo: "" }));
              }}
            />
            <span className="candidate-help">예: 단가 협의, 면접 일정</span>
            {errors.memo && (
              <small
                className="field-error"
                role="alert"
                data-testid="candidate-contact-memo-error"
              >
                {errors.memo}
              </small>
            )}
          </div>
          {error && (
            <p role="alert" className="candidate-error">
              {error}
            </p>
          )}
          <div className="app-actions">
            <button
              className="ls-btn"
              type="button"
              data-testid="candidate-contact-cancel"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              type="submit"
              className="ls-btn ls-btn-p"
              data-testid="candidate-contact-save"
              disabled={busy}
            >
              저장
            </button>
          </div>
        </form>
      </Dialog>
      {leaving && (
        <InlineConfirmation
          title="저장하지 않고 나갈까요?"
          description="바꾼 내용이 사라집니다."
          onCancel={() => setLeaving(false)}
          onProceed={onCancel}
          testId="candidate-contact-discard"
        />
      )}
    </>
  );
}

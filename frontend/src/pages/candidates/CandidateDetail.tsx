import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { formatDateTime } from "../../shared/time";
import { developerFailure, won } from "../developers/developers";
import { CandidateForm } from "./CandidateForm";
import {
  CandidateRequestError,
  getCommonCandidate,
  getCommonCandidateContacts,
  getCommonCandidateMatches,
  postCommonCandidateContact,
  type Candidate,
  type CandidateContact,
  type CandidateMatch,
  type CandidateOptions,
} from "./candidates";

export function CandidateDetail({
  candidateId,
  revision = 0,
  options,
  showAmounts = true,
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
  onNotice: (message: string) => void;
}) {
  const [candidate, setCandidate] = useState<Candidate | null>(null);
  const [contacts, setContacts] = useState<CandidateContact[]>([]);
  const [matches, setMatches] = useState<CandidateMatch[]>([]);
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
    let active = true;
    setBusy(true);
    setContactsBusy(true);
    setMatchesBusy(true);
    setError("");
    setContactsError("");
    setMatchesError("");
    setCandidate(null);
    setContacts([]);
    setMatches([]);
    void getCommonCandidate(candidateId)
      .then((value) => {
        if (active) setCandidate(value);
      })
      .catch((reason) => {
        if (active) setError(developerFailure(reason));
      })
      .finally(() => {
        if (active) setBusy(false);
      });
    void getCommonCandidateContacts(candidateId)
      .then((value) => {
        if (active) setContacts(value.items);
      })
      .catch((reason) => {
        if (active) setContactsError(developerFailure(reason));
      })
      .finally(() => {
        if (active) setContactsBusy(false);
      });
    void getCommonCandidateMatches(candidateId)
      .then((value) => {
        if (active) setMatches(value.items);
      })
      .catch((reason) => {
        if (active) setMatchesError(developerFailure(reason));
      })
      .finally(() => {
        if (active) setMatchesBusy(false);
      });
    return () => {
      active = false;
    };
  }, [candidateId, revision, retry]);
  const refresh = () => {
    setRetry((value) => value + 1);
    onChanged();
  };
  const conflict = () => {
    setEditing(false);
    setContacting(false);
    onNotice("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
    refresh();
  };
  const saved = () => {
    setEditing(false);
    setContacting(false);
    onNotice("저장했습니다.");
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
  return (
    <section className="candidate-detail" data-testid="candidate-detail">
      <header>
        <h2>{candidate?.name || "선택 후보"}</h2>
        <div className="candidate-actions">
          {canManage && (
            <>
              <button
                className="ls-btn"
                data-testid="candidate-edit"
                disabled={busy || !candidate}
                onClick={() => setEditing(true)}
              >
                수정
              </button>
              <button
                className="ls-btn ls-btn-p"
                data-testid="candidate-contact"
                disabled={busy || !candidate}
                onClick={() => setContacting(true)}
              >
                연락 기록 남기기
              </button>
            </>
          )}
          <button
            className="ls-btn"
            data-testid="candidate-detail-close"
            onClick={onClose}
          >
            닫기
          </button>
        </div>
      </header>
      {busy && <p role="status">불러오는 중…</p>}
      {error && (
        <p role="alert">
          {error}
          {retryButton("candidate-detail-retry")}
        </p>
      )}
      <div role="tablist" aria-label="후보 상세">
        <button
          role="tab"
          className="ls-btn"
          data-testid="candidate-basic-tab"
          aria-selected={tab === "basic"}
          onClick={() => setTab("basic")}
        >
          기본 정보
        </button>
        <button
          role="tab"
          className="ls-btn"
          data-testid="candidate-contacts-tab"
          aria-selected={tab === "contacts"}
          onClick={() => setTab("contacts")}
        >
          연락 기록
        </button>
      </div>
      {tab === "basic" ? (
        <div role="tabpanel" data-testid="candidate-basic-panel">
          {candidate && (
            <dl className="candidate-info">
              <dt>이름</dt>
              <dd>{candidate.name}</dd>
              <dt>구분/업체</dt>
              <dd>
                {candidate.employeeTypeName} {candidate.companyName}
              </dd>
              <dt>전화번호</dt>
              <dd>{candidate.phone || "–"}</dd>
              <dt>이메일</dt>
              <dd>
                {candidate.email || "–"}{" "}
                <small>
                  동의 확인일: {candidate.privacyAgreementDate || "–"} · 담당자:{" "}
                  {candidate.privacyAgreementUserName ||
                    candidate.privacyAgreementUserId ||
                    "–"}
                </small>
              </dd>
              <dt>주 직무</dt>
              <dd>{candidate.primaryJobName}</dd>
              <dt>보조 직무</dt>
              <dd>{candidate.secondaryJobName || "–"}</dd>
              <dt>기술/등급</dt>
              <dd>
                {candidate.skills.join(", ")} · {candidate.gradeName}
              </dd>
              <dt>거주지</dt>
              <dd>{candidate.homeAddress || "–"}</dd>
              {showAmounts && (
                <>
                  <dt>희망 단가</dt>
                  <dd>{won(candidate.desiredMonthlyRate)}원/월</dd>
                </>
              )}
              <dt>투입 가능일</dt>
              <dd>{candidate.availableDate}</dd>
              <dt>연락 상태</dt>
              <dd>{candidate.contactStatusName}</dd>
              <dt>최근 연락</dt>
              <dd>
                {candidate.lastContactDate}
                {candidate.overdueContact && " · 30일 초과 미연락"}
              </dd>
              <dt>출처</dt>
              <dd>{candidate.sourceName}</dd>
              <dt>메모</dt>
              <dd>{candidate.memo || "–"}</dd>
            </dl>
          )}
          <h3>맞는 자리</h3>
          {matchesBusy ? (
            <p role="status">불러오는 중…</p>
          ) : matchesError ? (
            <p role="alert">
              {matchesError}
              {retryButton("candidate-matches-retry")}
            </p>
          ) : (
            <div className="ls-twrap">
              <table className="ls-t">
                <thead>
                  <tr>
                    <th>기회(확률)</th>
                    <th>직무/등급</th>
                    <th>시작</th>
                    {showAmounts && (
                      <>
                        <th>단가</th>
                        <th>예상 이익률</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {matches.map((match, index) => (
                    <tr
                      key={`${match.opportunityId}-${index}`}
                      data-testid={`candidate-match-${index}`}
                    >
                      <td>
                        {match.opportunityName} ({match.winningProbability}%)
                      </td>
                      <td>
                        {match.jobName}/{match.gradeName}
                      </td>
                      <td>{match.startMonth}</td>
                      {showAmounts && (
                        <>
                          <td>{won(match.slotMonthlyRate)}</td>
                          <td
                            className={
                              match.lowMargin
                                ? "candidate-warning field-error"
                                : ""
                            }
                          >
                            {match.expectedProfitRate.toLocaleString("ko-KR", {
                              maximumFractionDigits: 2,
                            })}
                            %{match.lowMargin && " · 단가를 다시 협의"}
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                  {!matches.length && (
                    <tr>
                      <td colSpan={showAmounts ? 5 : 3}>
                        조회된 자료가 없습니다.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        <div role="tabpanel" data-testid="candidate-contacts-panel">
          {contactsBusy ? (
            <p role="status">불러오는 중…</p>
          ) : contactsError ? (
            <p role="alert">
              {contactsError}
              {retryButton("candidate-contacts-retry")}
            </p>
          ) : (
            <ol className="candidate-contact-list">
              {contacts.map((contact, index) => (
                <li
                  key={`${contact.recordedAt}-${index}`}
                  data-testid={`candidate-history-${index}`}
                >
                  <strong>{contact.contactStatusName}</strong> · {contact.memo}
                  <small>
                    {formatDateTime(contact.recordedAt)} · {contact.recordedBy}
                    {contact.systemRecord && " · 시스템"}
                  </small>
                </li>
              ))}
              {!contacts.length && <li>조회된 자료가 없습니다.</li>}
            </ol>
          )}
        </div>
      )}
      {editing && candidate && (
        <CandidateForm
          candidate={candidate}
          options={options}
          onCancel={() => setEditing(false)}
          onSaved={saved}
          onConflict={conflict}
        />
      )}
      {contacting && candidate && (
        <CandidateContactForm
          candidate={candidate}
          options={options}
          onCancel={() => setContacting(false)}
          onSaved={saved}
          onConflict={conflict}
        />
      )}
    </section>
  );
}
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
  const [status, setStatus] = useState(candidate.contactStatusCode);
  const [memo, setMemo] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  async function save() {
    if (pending.current) return;
    const invalid: Record<string, string> = {};
    if (!status) invalid.contactStatusCode = "입력 값을 확인해 주세요.";
    if (!memo.trim() || [...memo.trim()].length > 200)
      invalid.memo = "입력 값을 확인해 주세요.";
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
      onSaved();
    } catch (reason) {
      if (reason instanceof CandidateRequestError && reason.status === 409) {
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
    <Dialog
      title="연락 기록"
      onClose={() => {
        if (!pending.current) onCancel();
      }}
      testId="candidate-contact-dialog"
      closable
    >
      <form
        className="candidate-contact-form"
        data-testid="candidate-contact-form"
        data-screen-id="UI-33-M3"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <label htmlFor="candidate-contact-status">연락 상태*</label>
        <select
          id="candidate-contact-status"
          data-testid="candidate-contact-status"
          disabled={busy}
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setErrors((old) => ({ ...old, contactStatusCode: "" }));
          }}
        >
          <option value="">선택</option>
          {options.codes.CNTC_STAT?.map((item) => (
            <option key={item.code} value={item.code}>
              {item.codeName}
            </option>
          ))}
        </select>
        {errors.contactStatusCode && (
          <small role="alert">{errors.contactStatusCode}</small>
        )}
        <label htmlFor="candidate-contact-memo">메모*</label>
        <textarea
          id="candidate-contact-memo"
          data-testid="candidate-contact-memo"
          disabled={busy}
          value={memo}
          maxLength={200}
          onChange={(event) => {
            setMemo(event.target.value);
            setErrors((old) => ({ ...old, memo: "" }));
          }}
        />
        {errors.memo && <small role="alert">{errors.memo}</small>}
        {error && <p role="alert">{error}</p>}
        <footer>
          <button
            className="ls-btn"
            type="button"
            data-testid="candidate-contact-cancel"
            disabled={busy}
            onClick={onCancel}
          >
            취소
          </button>
          <button
            className="ls-btn ls-btn-p"
            data-testid="candidate-contact-save"
            disabled={busy}
          >
            저장
          </button>
        </footer>
      </form>
    </Dialog>
  );
}

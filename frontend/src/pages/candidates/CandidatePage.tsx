import { useEffect, useRef, useState, type ReactNode } from "react";
import { Toast } from "../../shared/feedback/Dialog";
import { developerFailure, won } from "../developers/developers";
import { CandidateForm } from "./CandidateForm";
import { CandidateDetail } from "./CandidateDetail";
import {
  candidatePolicy,
  getCandidateOptions,
  getCommonCandidates,
  getCommonCandidateSummary,
  getCommonCandidateShortages,
  type Candidate,
  type CandidateOptions,
  type CandidateFilter,
  type CandidateSummary,
  type CandidateShortage,
} from "./candidates";
import "./candidates.css";

export function CandidatePage({
  renderActions,
  futureRoles,
}: {
  renderActions?: (actions: ReactNode) => ReactNode;
  futureRoles?: string[];
}) {
  const policy = futureRoles
    ? candidatePolicy(futureRoles)
    : { canManage: true, showAmounts: true };
  const [options, setOptions] = useState<CandidateOptions | null>(null);
  const [optionsError, setOptionsError] = useState("");
  const [optionsRevision, setOptionsRevision] = useState(0);
  const [filter, setFilter] = useState<CandidateFilter>({});
  const [query, setQuery] = useState<CandidateFilter>({});
  const [revision, setRevision] = useState(0);
  const [rows, setRows] = useState<Candidate[]>([]);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [summary, setSummary] = useState<CandidateSummary | null>(null);
  const [summaryError, setSummaryError] = useState("");
  const [shortages, setShortages] = useState<CandidateShortage[] | null>(null);
  const [shortagesError, setShortagesError] = useState("");
  const [registering, setRegistering] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const pinned = useRef<Candidate | null>(null);
  useEffect(() => {
    let active = true;
    setOptionsError("");
    void getCandidateOptions()
      .then((value) => {
        if (active) setOptions(value);
      })
      .catch((reason) => {
        if (active) setOptionsError(developerFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [optionsRevision]);
  useEffect(() => {
    const controller = new AbortController();
    setBusy(true);
    setError("");
    void getCommonCandidates(query, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) {
          const first = pinned.current;
          setRows(
            first
              ? [
                  value.items.find(
                    (item) => item.candidateId === first.candidateId,
                  ) || first,
                  ...value.items.filter(
                    (item) => item.candidateId !== first.candidateId,
                  ),
                ]
              : value.items,
          );
        }
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(developerFailure(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    return () => controller.abort();
  }, [query, revision]);
  useEffect(() => {
    let active = true;
    setSummary(null);
    setShortages(null);
    setSummaryError("");
    setShortagesError("");
    void getCommonCandidateSummary()
      .then((value) => {
        if (active) setSummary(value);
      })
      .catch((reason) => {
        if (active) setSummaryError(developerFailure(reason));
      });
    void getCommonCandidateShortages()
      .then((value) => {
        if (active) setShortages(value.items);
      })
      .catch((reason) => {
        if (active) setShortagesError(developerFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [revision]);
  const refresh = () => setRevision((value) => value + 1);
  const submitQuery = (next: CandidateFilter) => {
    pinned.current = null;
    setQuery({ ...next });
  };
  const actions = policy.canManage ? (
    <button
      className="ls-btn ls-btn-p"
      data-testid="candidate-register"
      disabled={!options}
      onClick={() => setRegistering(true)}
    >
      후보 등록
    </button>
  ) : null;
  function filterSelect(
    key: keyof CandidateFilter,
    group: string,
    label: string,
  ) {
    return (
      <label key={key}>
        {label}
        <select
          aria-label={label}
          data-testid={`candidate-filter-${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`}
          value={filter[key] || ""}
          onChange={(event) =>
            setFilter((old) => ({ ...old, [key]: event.target.value }))
          }
        >
          <option value="">전체</option>
          {options?.codes[group]
            ?.filter(
              (item) =>
                group !== "EMP_TYPE" || ["FL", "OS"].includes(item.code),
            )
            .map((item) => (
              <option key={item.code} value={item.code}>
                {item.codeName}
              </option>
            ))}
        </select>
      </label>
    );
  }
  return (
    <div
      className="screen-body candidate-page"
      data-testid="candidate-page"
      data-screen-id="UI-33"
    >
      <div className="candidate-heading">
        <h2>외부 인력 후보</h2>
        {renderActions ? renderActions(actions) : actions}
      </div>
      {optionsError && (
        <p role="alert">
          {optionsError}
          <button
            data-testid="candidate-options-retry"
            onClick={() => setOptionsRevision((value) => value + 1)}
          >
            다시 시도
          </button>
        </p>
      )}
      <section className="candidate-summary" aria-label="후보 요약">
        {summary ? (
          <dl>
            <div>
              <dt>진행 중</dt>
              <dd>{summary.activeCount}</dd>
            </div>
            <div>
              <dt>면접 예정</dt>
              <dd>{summary.interviewScheduledCount}</dd>
            </div>
            <div>
              <dt>다음 달 시작까지 가능</dt>
              <dd>{summary.availableByNextMonthCount}</dd>
            </div>
            <div>
              <dt>30일 초과 미연락</dt>
              <dd>{summary.overdueContactCount}</dd>
            </div>
          </dl>
        ) : summaryError ? (
          <p role="alert">
            {summaryError}
            <button data-testid="candidate-summary-retry" onClick={refresh}>
              다시 시도
            </button>
          </p>
        ) : (
          <p role="status">불러오는 중…</p>
        )}
      </section>
      <section className="candidate-shortages">
        <h3>영업 기회 기준(미충원 제외)</h3>
        {shortagesError ? (
          <p role="alert">
            {shortagesError}
            <button data-testid="candidate-shortages-retry" onClick={refresh}>
              다시 시도
            </button>
          </p>
        ) : shortages === null ? (
          <p role="status">불러오는 중…</p>
        ) : (
          <div className="ls-twrap">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>직무</th>
                  <th>부족 시작월</th>
                  <th>최대 부족</th>
                  <th>가능 후보 수(전체 수)</th>
                  <th>후보</th>
                </tr>
              </thead>
              <tbody>
                {shortages.map((shortage, index) => (
                  <tr
                    key={`${shortage.shortageStartMonth}-${shortage.jobCode}`}
                    data-testid={`candidate-shortage-${index}`}
                  >
                    <td>{shortage.jobName}</td>
                    <td>{shortage.shortageStartMonth}</td>
                    <td>{shortage.maxShortage}</td>
                    <td>
                      {shortage.availableCandidateCount}(
                      {shortage.totalCandidateCount})
                    </td>
                    <td>
                      <button
                        className="ls-btn ls-btn-sm"
                        data-testid={`candidate-shortage-view-${index}`}
                        onClick={() => {
                          const next = { ...filter, jobCode: shortage.jobCode };
                          setFilter(next);
                          submitQuery(next);
                        }}
                      >
                        후보 보기
                      </button>
                    </td>
                  </tr>
                ))}
                {!shortages.length && (
                  <tr>
                    <td colSpan={5}>조회된 자료가 없습니다.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <section className="candidate-list">
        <form
          className="candidate-filters"
          onSubmit={(event) => {
            event.preventDefault();
            submitQuery(filter);
          }}
        >
          {filterSelect("jobCode", "JOB", "직무")}
          {filterSelect("gradeCode", "GRADE", "등급")}
          {filterSelect("employeeTypeCode", "EMP_TYPE", "구분")}
          {filterSelect("contactStatusCode", "CNTC_STAT", "연락 상태")}
          <label>
            투입 가능 시점
            <select
              aria-label="투입 가능 시점"
              data-testid="candidate-filter-availability"
              value={filter.availabilityWindow || ""}
              onChange={(event) =>
                setFilter((old) => ({
                  ...old,
                  availabilityWindow: event.target.value,
                }))
              }
            >
              <option value="">전체</option>
              <option value="next-month">다음 달까지</option>
              <option value="within-three-months">3개월 이내</option>
            </select>
          </label>
          <button
            className="ls-btn ls-btn-p"
            data-testid="candidate-search"
            disabled={busy}
          >
            조회
          </button>
        </form>
        {busy ? (
          <p role="status">불러오는 중…</p>
        ) : error ? (
          <p role="alert">
            {error}
            <button data-testid="candidate-list-retry" onClick={refresh}>
              다시 시도
            </button>
          </p>
        ) : (
          <div className="ls-twrap">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>이름</th>
                  <th>구분/업체</th>
                  <th>직무/등급</th>
                  <th>기술</th>
                  <th>거주지</th>
                  {policy.showAmounts && <th>희망 단가</th>}
                  <th>투입 가능일</th>
                  <th>연락 상태</th>
                  <th>최근 연락</th>
                  <th>출처</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    key={row.candidateId}
                    data-testid={`candidate-row-${row.candidateId}`}
                    aria-selected={selected === row.candidateId}
                  >
                    <td>
                      <button
                        className="candidate-name"
                        data-testid={`candidate-open-${row.candidateId}`}
                        onClick={() => setSelected(row.candidateId)}
                      >
                        {row.name}
                      </button>
                    </td>
                    <td>
                      {row.employeeTypeName} {row.companyName}
                    </td>
                    <td>
                      {row.primaryJobName}/{row.gradeName}
                    </td>
                    <td>
                      {row.skills.map((skill) => (
                        <span className="candidate-chip" key={skill}>
                          {skill}
                        </span>
                      ))}
                    </td>
                    <td>{row.homeAddress || "–"}</td>
                    {policy.showAmounts && (
                      <td>{won(row.desiredMonthlyRate)}</td>
                    )}
                    <td>{row.availableDate}</td>
                    <td>{row.contactStatusName}</td>
                    <td
                      className={row.overdueContact ? "candidate-warning" : ""}
                    >
                      {row.lastContactDate}
                      {row.overdueContact && ` · ${row.daysSinceLastContact}일`}
                    </td>
                    <td>{row.sourceName}</td>
                  </tr>
                ))}
                {!rows.length && (
                  <tr>
                    <td colSpan={policy.showAmounts ? 10 : 9}>
                      조회된 자료가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {selected && options && (
        <CandidateDetail
          key={selected}
          candidateId={selected}
          options={options}
          showAmounts={policy.showAmounts}
          canManage={policy.canManage}
          onClose={() => setSelected(null)}
          onChanged={refresh}
          onNotice={setNotice}
        />
      )}
      {registering && options && (
        <CandidateForm
          options={options}
          onCancel={() => setRegistering(false)}
          onConflict={() => {
            setRegistering(false);
            refresh();
          }}
          onSaved={(candidate) => {
            pinned.current = candidate;
            setRegistering(false);
            setSelected(candidate.candidateId);
            setNotice("저장했습니다.");
            refresh();
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          onDismiss={() => setNotice("")}
          testId="candidate-toast"
        />
      )}
    </div>
  );
}

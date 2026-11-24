import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  KpiCard,
  KpiCards,
  ListFilter,
  ListFilters,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { koreanToday } from "../../shared/time";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { developerFailure, won } from "../developers/developers";
import { CandidateForm } from "./CandidateForm";
import { CandidateDetail } from "./CandidateDetail";
import {
  candidateEmployeeTypes,
  candidateMessages as messages,
  candidatePolicy,
  closedCandidate,
  contactStatusKind,
  getCandidateOptions,
  getCommonCandidates,
  getCommonCandidatesExcel,
  getCommonCandidateSummary,
  getCommonCandidateShortages,
  type Candidate,
  type CandidateOptions,
  type CandidateFilter,
  type CandidateSummary,
  type CandidateShortage,
} from "./candidates";
import "./candidates.css";

/** 다음 달 1일(한국 날짜) yyyy-MM-01. */
function nextMonthFirst(today = koreanToday()) {
  const [year, month] = today.split("-").map(Number);
  return month === 12
    ? `${year + 1}-01-01`
    : `${year}-${String(month + 1).padStart(2, "0")}-01`;
}
const overdue = (row: Candidate) => row.overdueContact && !closedCandidate(row);
const sortValue = (row: Candidate, key: string): string | number => {
  const value = (row as unknown as Record<string, unknown>)[key];
  return typeof value === "number" ? value : String(value ?? "");
};

/** 인력 계획 › 외부 인력 후보(UI-33). 시안 SCR['UI-33']. */
export function CandidatePage({
  renderActions,
  futureRoles,
  testId = "candidate-page",
}: {
  renderActions?: (actions: ReactNode) => ReactNode;
  futureRoles?: string[];
  testId?: string;
}) {
  const policy = futureRoles
    ? candidatePolicy(futureRoles)
    : { canManage: true, showAmounts: true };
  const root = useRef<HTMLDivElement>(null);
  const [options, setOptions] = useState<CandidateOptions | null>(null);
  const [optionsError, setOptionsError] = useState("");
  const [optionsRevision, setOptionsRevision] = useState(0);
  const [filter, setFilter] = useState<CandidateFilter>({});
  const [applied, setApplied] = useState<CandidateFilter>({});
  const [revision, setRevision] = useState(0);
  const [rows, setRows] = useState<Candidate[] | null>(null);
  const [error, setError] = useState("");
  const [summary, setSummary] = useState<CandidateSummary | null>(null);
  const [summaryError, setSummaryError] = useState("");
  const [shortages, setShortages] = useState<CandidateShortage[] | null>(null);
  const [shortagesError, setShortagesError] = useState("");
  const [registering, setRegistering] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [notice, setNotice] = useState<{
    message: string;
    kind: ToastKind;
  } | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const presentation = useListPresentation();
  /** 방금 등록한 후보(시안: 목록 맨 위). 조건으로 다시 조회하면 지운다. */
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
    setRows(null);
    setError("");
    void getCommonCandidates(applied, controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
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
      })
      .catch((reason) => {
        if (controller.signal.aborted) return;
        setRows([]);
        setError(developerFailure(reason));
      });
    return () => controller.abort();
  }, [applied, revision]);
  useEffect(() => {
    const controller = new AbortController();
    const { signal } = controller;
    setSummary(null);
    setShortages(null);
    setSummaryError("");
    setShortagesError("");
    void getCommonCandidateSummary(signal)
      .then((value) => {
        if (!signal.aborted) setSummary(value);
      })
      .catch((reason) => {
        if (!signal.aborted) setSummaryError(developerFailure(reason));
      });
    void getCommonCandidateShortages(signal)
      .then((value) => {
        if (!signal.aborted) setShortages(value.items);
      })
      .catch((reason) => {
        if (!signal.aborted) setShortagesError(developerFailure(reason));
      });
    return () => controller.abort();
  }, [revision]);
  const refresh = () => setRevision((value) => value + 1);
  const search = (next: CandidateFilter = filter) => {
    pinned.current = null;
    setApplied({ ...next });
    setPage(1);
  };
  usePreferenceBusinessShortcuts({ scope: root, onSearch: () => search() });
  const columns = useMemo<ListColumn<Candidate>[]>(
    () => [
      { key: "name", label: "이름", required: true },
      {
        key: "employeeTypeName",
        label: "구분",
        render: (row) => (
          <>
            <Badge>{row.employeeTypeName}</Badge>
            {row.companyName && (
              <>
                {" "}
                <span className="ls-cap">{row.companyName}</span>
              </>
            )}
          </>
        ),
      },
      { key: "primaryJobName", label: "직무" },
      { key: "gradeName", label: "등급" },
      {
        key: "skills",
        label: "기술",
        sortable: false,
        render: (row) => (
          <span className="candidate-chips">
            {row.skills.map((skill) => (
              <span className="ls-chip" key={skill}>
                {skill}
              </span>
            ))}
          </span>
        ),
      },
      {
        key: "homeAddress",
        label: "거주지",
        render: (row) => row.homeAddress || "–",
      },
      ...(policy.showAmounts
        ? [
            {
              key: "desiredMonthlyRate",
              label: "희망 단가",
              align: "right" as const,
              render: (row: Candidate) => won(row.desiredMonthlyRate),
            },
          ]
        : []),
      { key: "availableDate", label: "투입 가능일" },
      {
        key: "contactStatusName",
        label: "연락 상태",
        render: (row) => (
          <Badge kind={contactStatusKind(row.contactStatusName)}>
            {row.contactStatusName}
          </Badge>
        ),
      },
      {
        key: "lastContactDate",
        label: "최근 연락",
        render: (row) => (
          <>
            {row.lastContactDate}
            {overdue(row) && (
              <>
                {" "}
                <Badge kind="warn">{row.daysSinceLastContact}일 전</Badge>
              </>
            )}
          </>
        ),
      },
      { key: "sourceName", label: "출처" },
    ],
    [policy.showAmounts],
  );
  const cols = useListColumns("UI-33", columns);
  const sorted = useMemo(() => {
    if (!rows || !sort) return rows;
    return [...rows].sort((a, b) => {
      const x = sortValue(a, sort.key);
      const y = sortValue(b, sort.key);
      const order =
        typeof x === "number" && typeof y === "number"
          ? x - y
          : String(x).localeCompare(String(y), "ko");
      return sort.dir === "desc" ? -order : order;
    });
  }, [rows, sort]);
  const actions = policy.canManage ? (
    <button
      type="button"
      className="ls-btn ls-btn-p"
      data-testid="candidate-register"
      disabled={!options}
      onClick={() => setRegistering(true)}
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        aria-hidden="true"
      >
        <path d="M12 5v14M5 12h14" />
      </svg>{" "}
      후보 등록
    </button>
  ) : null;
  function filterSelect(
    key: keyof CandidateFilter,
    label: string,
    items: { code: string; codeName: string }[],
  ) {
    const id = `candidate-filter-${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`;
    return (
      <ListFilter key={key} label={label} htmlFor={id}>
        <select
          id={id}
          className="ls-in"
          data-testid={key === "availabilityWindow" ? "candidate-filter-availability" : id}
          value={filter[key] || ""}
          onChange={(event) =>
            setFilter((old) => ({ ...old, [key]: event.target.value }))
          }
        >
          <option value="">전체</option>
          {items.map((item) => (
            <option key={item.code} value={item.code}>
              {item.codeName}
            </option>
          ))}
        </select>
      </ListFilter>
    );
  }
  const retry = (id: string, onClick: () => void) => (
    <button
      type="button"
      className="ls-btn ls-btn-sm"
      data-testid={id}
      onClick={onClick}
    >
      다시 시도
    </button>
  );
  const notify = (message: string, kind: ToastKind = "ok") =>
    setNotice({ message, kind });
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonCandidatesExcel(applied);
      notify(`엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`);
    } catch (reason) {
      notify(developerFailure(reason), "warn");
    } finally {
      setDownloading(false);
    }
  };
  const nextMonth = nextMonthFirst();
  return (
    <div
      ref={root}
      className="screen-body candidate-page"
      data-testid={testId}
      data-screen-id="UI-33"
    >
      {renderActions ? renderActions(actions) : actions}
      {optionsError && (
        <p role="alert" className="candidate-error">
          {optionsError}{" "}
          {retry("candidate-options-retry", () =>
            setOptionsRevision((value) => value + 1),
          )}
        </p>
      )}
      {summaryError ? (
        <p role="alert" className="candidate-error">
          {summaryError} {retry("candidate-summary-retry", refresh)}
        </p>
      ) : summary ? (
        <KpiCards aria-label="후보 요약" testId="candidate-summary">
          <KpiCard
            label="후보"
            value={summary.totalCount != null ? `${summary.totalCount}명` : "–"}
            description={`진행 중 ${summary.activeCount}명`}
            testId="candidate-kpi-total"
          />
          <KpiCard
            label="면접 예정"
            value={`${summary.interviewScheduledCount}명`}
            testId="candidate-kpi-interview"
          />
          <KpiCard
            label="다음 달 시작까지 투입 가능"
            value={`${summary.availableByNextMonthCount}명`}
            description={`투입 가능일 ${nextMonth}까지`}
            testId="candidate-kpi-available"
          />
          <KpiCard
            label="30일 넘게 연락 안 함"
            value={`${summary.overdueContactCount}명`}
            testId="candidate-kpi-overdue"
          />
        </KpiCards>
      ) : (
        <p role="status">불러오는 중…</p>
      )}
      <section className="candidate-card" data-testid="candidate-shortages">
        <div className="candidate-card-h">
          <h2>부족 직무와 맞는 후보</h2>
          <span className="ls-cap">
            다음 달부터 6개월 · 영업 기회 기준(미충원 제외)
          </span>
        </div>
        {shortagesError ? (
          <p role="alert" className="candidate-error">
            {shortagesError} {retry("candidate-shortages-retry", refresh)}
          </p>
        ) : shortages === null ? (
          <p role="status">불러오는 중…</p>
        ) : !shortages.length ? (
          <p className="candidate-sub" data-testid="candidate-shortages-empty">
            부족이 예상되는 직무가 없습니다.
          </p>
        ) : (
          <div className="ls-twrap">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>직무</th>
                  <th>부족 시작</th>
                  <th className="ls-right">가장 많이 부족</th>
                  <th className="ls-right">그때까지 투입 가능한 후보</th>
                  <th aria-label="후보 보기" />
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
                    <td className="ls-right candidate-bad">
                      {Number(shortage.maxShortage).toFixed(1)}명
                    </td>
                    <td
                      className={`ls-right${shortage.availableCandidateCount < Number(shortage.maxShortage) ? " candidate-warn" : ""}`}
                      data-testid={`candidate-shortage-count-${index}`}
                    >
                      {shortage.availableCandidateCount}명{" "}
                      <span className="ls-cap">
                        (전체 {shortage.totalCandidateCount}명)
                      </span>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm"
                        data-testid={`candidate-shortage-view-${index}`}
                        onClick={() => {
                          const next = { jobCode: shortage.jobCode };
                          setFilter(next);
                          search(next);
                        }}
                      >
                        후보 보기
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <ListFilters
        testId="candidate-filters"
        activeCount={Object.values(applied).filter(Boolean).length}
        onSearch={() => search()}
        onReset={() => {
          setFilter({});
          search({});
        }}
      >
        {filterSelect("jobCode", "직무", options?.codes.JOB || [])}
        {filterSelect("gradeCode", "등급", options?.codes.GRADE || [])}
        {filterSelect(
          "employeeTypeCode",
          "구분",
          options ? candidateEmployeeTypes(options) : [],
        )}
        {filterSelect("contactStatusCode", "연락 상태", options?.codes.CNTC_STAT || [])}
        {filterSelect("availabilityWindow", "투입 가능", [
          { code: "next-month", codeName: "다음 달까지" },
          { code: "within-three-months", codeName: "3개월 안" },
        ])}
      </ListFilters>
      <ListView
        screenId="UI-33"
        testId="candidate-list"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        onDownload={() => void download()}
        downloading={downloading}
        rows={
          sorted?.slice(
            (page - 1) * presentation.pageSize,
            page * presentation.pageSize,
          ) ?? null
        }
        rowKey={(row) => row.candidateId}
        total={rows?.length || 0}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={(size) => {
          presentation.setPageSize(size);
          setPage(1);
        }}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={sort}
        onSort={(next) => {
          setSort(next);
          setPage(1);
        }}
        onRowClick={(row) => setSelected(row.candidateId)}
        highlightKey={selected}
        error={error || undefined}
        onRetry={refresh}
        mobileRows={sorted}
        renderCard={(row) => (
          <>
            <div className="ls-r1">
              <strong>{row.name}</strong>
              <Badge kind={contactStatusKind(row.contactStatusName)}>
                {row.contactStatusName}
              </Badge>
            </div>
            <div className="ls-r2">
              <span>
                {row.primaryJobName} · {row.gradeName} · {row.employeeTypeName}
              </span>
              {policy.showAmounts && <b>{won(row.desiredMonthlyRate)}</b>}
            </div>
            <div className="ls-r2">
              <span>{row.availableDate}부터</span>
              <span>{row.sourceName}</span>
            </div>
          </>
        )}
      />
      {selected && options && (
        <CandidateDetail
          key={selected}
          candidateId={selected}
          options={options}
          showAmounts={policy.showAmounts}
          canManage={policy.canManage}
          onClose={() => setSelected(null)}
          onChanged={refresh}
          onNotice={notify}
        />
      )}
      {registering && options && (
        <CandidateForm
          options={options}
          onCancel={() => setRegistering(false)}
          onConflict={() => {
            setRegistering(false);
            notify(messages.conflict, "warn");
            refresh();
          }}
          onSaved={(candidate) => {
            // 시안: 새 후보는 목록 맨 위(1쪽, 정렬 없음)에 보인다.
            pinned.current = candidate;
            setRegistering(false);
            setSort(null);
            setPage(1);
            notify(messages.created(candidate.name));
            refresh();
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice.message}
          kind={notice.kind}
          onDismiss={() => setNotice(null)}
          testId="candidate-toast"
        />
      )}
    </div>
  );
}

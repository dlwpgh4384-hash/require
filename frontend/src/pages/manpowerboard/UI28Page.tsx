import { useEffect, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import {
  Badge,
  KpiCard,
  KpiCards,
  ListFilter,
  ListFilters,
  ListPager,
  useListPresentation,
} from "../../shared/list";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { koreanToday } from "../../shared/time";
import { getCommonCodes } from "../codegroups/codeGroups";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { projectFailure, ProjectRequestError } from "../projects/projects";
import {
  getCommonSalesPipelineForecast,
  type SalesForecast,
} from "../salespipeline/salesPipeline";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import {
  getCommonManpowerBoard,
  getCommonManpowerBoardExcel,
  type ManpowerBoardCell,
  type ManpowerBoardFilter,
  type ManpowerBoardResult,
} from "./manpowerBoard";
import "./manpowerBoard.css";

const periodOptions = {
  kind: "year",
  firstDataYear: 2026,
  lastDataYear: 2027,
  allowNextYear: true,
} as const;
const mm = (value: number | null | undefined) =>
  value == null ? "–" : value.toFixed(2);
const legends = [
  "투입 중",
  "일부 투입",
  "동시 투입",
  "철수 예정",
  "비어 있음",
  "가배정",
  "휴직",
  "퇴사",
  "미계약 선투입",
];
const cellClass = (cell: ManpowerBoardCell) => {
  if (cell.statusName === "퇴사" || cell.statusName === "휴직")
    return "board-cell board-ineligible";
  if (cell.totalMm > 1) return "board-cell board-excess";
  if (cell.tentative) return "board-cell board-tentative";
  if (cell.statusName === "비어 있음") return "board-cell board-idle";
  if (cell.statusName === "미계약 선투입" || cell.statusName === "철수 예정")
    return "board-cell board-warning";
  return "board-cell";
};
export function manpowerBoardTooltip(cell: ManpowerBoardCell) {
  return [
    cell.month,
    cell.statusName,
    ...cell.projects.map((p) => `${p.projectName} · ${mm(p.monthlyMm)} MM`),
    cell.extensionPossibilityName
      ? `연장 가능성 ${cell.extensionPossibilityName}`
      : "",
    cell.winningProbability == null
      ? ""
      : `수주 확률 ${cell.winningProbability}%`,
    cell.totalMm > 1 ? `합계 ${mm(cell.totalMm)} MM · 1.0 초과` : "",
  ]
    .filter(Boolean)
    .join("\n");
}

// SCR-UI-28: 업무자료는 조회 전용이며 상세 역시 readOnly 모드만 사용한다.
export function UI28Page({
  renderActions,
}: { renderActions?: (actions: ReactNode) => ReactNode } = {}) {
  const navigate = useNavigate();
  const period = useScreenPeriod("UI-28", periodOptions);
  const currentYear = Number(koreanToday().slice(0, 4));
  const [draft, setDraft] = useState<ManpowerBoardFilter>({
    year: Math.max(2026, Math.min(2027, currentYear)),
  });
  const [applied, setApplied] = useState(draft);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<ManpowerBoardResult | null>(null);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [codes, setCodes] = useState<
    Record<string, { code: string; codeName: string }[]>
  >({});
  const [optionError, setOptionError] = useState("");
  const [optionRevision, setOptionRevision] = useState(0);
  const [tab, setTab] = useState<"calendar" | "forecast">("calendar");
  const [forecast, setForecast] = useState<SalesForecast | null>(null);
  const [forecastError, setForecastError] = useState("");
  const [forecastRevision, setForecastRevision] = useState(0);
  const [detail, setDetail] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const presentation = useListPresentation();
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: ToastKind;
  } | null>(null);
  const selectedPeriodYear =
    period.state.status === "ready" ? period.state.value.year : null;

  useEffect(() => {
    if (selectedPeriodYear == null) return;
    const year = Math.max(2026, Math.min(2027, selectedPeriodYear));
    setDraft((previous) => ({ ...previous, year }));
    setApplied((previous) => ({ ...previous, year }));
    setPage(1);
  }, [selectedPeriodYear]);
  useEffect(() => {
    const controller = new AbortController();
    setOptionError("");
    Promise.all(
      ["JOB", "GRADE", "EMP_TYPE"].map(
        async (group) =>
          [
            group,
            (await getCommonCodes(group, controller.signal)).items.filter(
              (c) => c.useYn === "Y",
            ),
          ] as const,
      ),
    )
      .then((entries) => {
        if (!controller.signal.aborted) setCodes(Object.fromEntries(entries));
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setOptionError(projectFailure(reason));
      });
    return () => controller.abort();
  }, [optionRevision]);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError("");
    setFields([]);
    getCommonManpowerBoard(applied, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (controller.signal.aborted) return;
        setError(projectFailure(reason));
        if (reason instanceof ProjectRequestError) setFields(reason.errors);
      });
    return () => controller.abort();
  }, [applied, revision]);
  useEffect(() => {
    if (tab !== "forecast") return;
    let active = true;
    setForecast(null);
    setForecastError("");
    // 달력의 연도/코드 조건을 기존 예측 API에 절대 전달하지 않는다.
    getCommonSalesPipelineForecast()
      .then((result) => {
        if (active) setForecast(result);
      })
      .catch((reason) => {
        if (active) setForecastError(projectFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [tab, forecastRevision]);

  const search = () => {
    setApplied({ ...draft });
    setPage(1);
    setRevision((n) => n + 1);
  };
  const reset = () => {
    const next = { year: draft.year };
    setDraft(next);
    setApplied(next);
    setPage(1);
    setRevision((n) => n + 1);
  };
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonManpowerBoardExcel(applied);
      setNotice({
        kind: "ok",
        message: `엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`,
      });
    } catch (reason) {
      setNotice({ kind: "warn", message: projectFailure(reason) });
      if (reason instanceof ProjectRequestError) setFields(reason.errors);
    } finally {
      setDownloading(false);
    }
  };
  const actions = (
    <button
      type="button"
      className="ls-btn"
      data-testid="manpower-board-excel"
      disabled={!data || downloading}
      onClick={() => void download()}
    >
      {downloading ? "만드는 중…" : "엑셀 다운로드"}
    </button>
  );
  const pages = Math.max(
    1,
    Math.ceil((data?.items.length ?? 0) / presentation.pageSize),
  );
  const currentPage = Math.min(page, pages);
  const months = Array.from(
    { length: 12 },
    (_, i) => `${applied.year}-${String(i + 1).padStart(2, "0")}`,
  );
  const summary = data?.summary;
  const fieldError = (field: string) =>
    fields
      .filter((f) => f.field === field)
      .map((f) => (
        <span
          key={f.field}
          className="field-error"
          role="alert"
          data-testid={`manpower-board-${field}-error`}
        >
          {f.message}
        </span>
      ));

  return (
    <section
      className="screen-body manpower-board-page"
      data-testid="manpower-board-page"
      data-screen-id="SCR-UI-28"
    >
      {renderActions ? (
        renderActions(actions)
      ) : (
        <div className="project-title-actions">{actions}</div>
      )}
      {period.state.status === "ready" ? (
        <PeriodSelector
          {...periodOptions}
          basis={period.state.basis}
          value={{ year: draft.year }}
          onChange={(value) => period.setValue(value)}
          description="사람별 1~12월 인력 운영 현황"
        />
      ) : (
        <p role={period.state.status === "error" ? "alert" : "status"}>
          {period.state.status === "error"
            ? period.state.message
            : "기간을 불러오는 중입니다."}
        </p>
      )}
      {fieldError("year")}
      <KpiCards aria-label="인력 운영 요약" testId="manpower-board-summary">
        <KpiCard
          label="지금 비어 있는 인원"
          value={summary?.idleCount ?? "–"}
          description={`남는 MM ${mm(summary?.availableMm)}`}
        />
        <KpiCard
          label="3개월 안 철수 예정"
          value={summary?.withdrawalCount ?? "–"}
          description={`연장 가능성 높음 ${summary?.highExtensionCount ?? "–"}명`}
          testId="manpower-board-withdrawal"
          onClick={() => navigate("/screens/UI-05")}
        />
        <KpiCard
          label="동시 투입"
          value={summary?.concurrentCount ?? "–"}
          description={`이번 달 2건 이상 / 1.0 초과 ${summary?.overAllocatedCount ?? "–"}명`}
          testId="manpower-board-concurrent"
          onClick={() => navigate("/screens/UI-12")}
        />
        <KpiCard
          label={
            <>
              3개월 뒤 남는 MM <Badge kind="info">예측</Badge>
            </>
          }
          value={mm(summary?.futureAvailableMm)}
        />
      </KpiCards>
      <div
        role="tablist"
        aria-label="인력 운영 보기"
        className="manpower-board-tabs"
      >
        <button
          type="button"
          role="tab"
          id="manpower-board-calendar-tab"
          aria-controls="manpower-board-calendar"
          aria-selected={tab === "calendar"}
          data-testid="manpower-board-calendar-tab"
          onClick={() => setTab("calendar")}
        >
          달력
        </button>
        <button
          type="button"
          role="tab"
          id="manpower-board-forecast-tab"
          aria-controls="manpower-board-forecast"
          aria-selected={tab === "forecast"}
          data-testid="manpower-board-forecast-tab"
          onClick={() => setTab("forecast")}
        >
          수요·공급
        </button>
      </div>
      {tab === "calendar" ? (
        <div
          role="tabpanel"
          id="manpower-board-calendar"
          aria-labelledby="manpower-board-calendar-tab"
          data-testid="manpower-board-calendar"
        >
          <ListFilters
            onSearch={search}
            onReset={reset}
            testId="manpower-board-filters"
            activeCount={
              Number(!!applied.jobCode) +
              Number(!!applied.gradeCode) +
              Number(!!applied.employeeTypeCode) +
              Number(!!applied.onlyAvailable)
            }
          >
            {(
              [
                ["jobCode", "JOB", "직무"],
                ["gradeCode", "GRADE", "등급"],
                ["employeeTypeCode", "EMP_TYPE", "직원구분"],
              ] as const
            ).map(([field, group, label]) => (
              <ListFilter
                key={field}
                label={label}
                htmlFor={`manpower-board-${field}`}
              >
                <select
                  id={`manpower-board-${field}`}
                  className="ls-in"
                  value={draft[field] || ""}
                  data-testid={`manpower-board-${field.toLowerCase()}`}
                  onChange={(event) =>
                    setDraft({
                      ...draft,
                      [field]: event.target.value || undefined,
                    })
                  }
                >
                  <option value="">전체</option>
                  {(codes[group] || []).map((code) => (
                    <option key={code.code} value={code.code}>
                      {code.codeName}
                    </option>
                  ))}
                </select>
                {fieldError(field)}
              </ListFilter>
            ))}
            <ListFilter label="보기" htmlFor="manpower-board-view">
              <select
                id="manpower-board-view"
                className="ls-in"
                data-testid="manpower-board-view"
                value={draft.onlyAvailable ? "available" : "all"}
                onChange={(event) =>
                  setDraft({
                    ...draft,
                    onlyAvailable: event.target.value === "available",
                  })
                }
              >
                <option value="all">전체 인력</option>
                <option value="available">남는 MM이 있는 사람만</option>
              </select>
            </ListFilter>
          </ListFilters>
          {optionError && (
            <div role="alert">
              조건을 불러오지 못했습니다. {optionError}{" "}
              <button
                type="button"
                data-testid="manpower-board-options-retry"
                onClick={() => setOptionRevision((n) => n + 1)}
              >
                다시 시도
              </button>
            </div>
          )}
          {error ? (
            <div role="alert" data-testid="manpower-board-error">
              {error}{" "}
              <button
                type="button"
                data-testid="manpower-board-retry"
                onClick={() => setRevision((n) => n + 1)}
              >
                다시 시도
              </button>
            </div>
          ) : (
            !data && (
              <p role="status" data-testid="manpower-board-loading">
                인력 운영 달력을 불러오는 중입니다.
              </p>
            )
          )}
          <p>총 {data?.totalCount ?? "–"}명</p>
          <div className="manpower-board-legend">
            {legends.map((label) => (
              <Badge
                key={label}
                kind={
                  label === "철수 예정" || label === "미계약 선투입"
                    ? "warn"
                    : "info"
                }
              >
                {label}
              </Badge>
            ))}
          </div>
          <div
            className="manpower-board-scroll"
            tabIndex={0}
            role="region"
            aria-label="연간 인력 운영 달력"
            data-testid="manpower-board-scroll"
          >
            <table className="manpower-board-table">
              <thead>
                <tr>
                  <th scope="col">이름</th>
                  <th scope="col">직무</th>
                  {months.map((month, i) => (
                    <th
                      scope="col"
                      key={month}
                      className={
                        month === koreanToday().slice(0, 7)
                          ? "board-current"
                          : undefined
                      }
                    >
                      {i + 1}월
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data?.items
                  .slice(
                    (currentPage - 1) * presentation.pageSize,
                    currentPage * presentation.pageSize,
                  )
                  .map((person) => (
                    <tr
                      key={person.developerId}
                      data-testid={`manpower-board-person-${person.developerId}`}
                      onClick={() => setDetail(person.developerId)}
                    >
                      <th scope="row">
                        <button
                          type="button"
                          className="link-button"
                          data-testid={`manpower-board-detail-${person.developerId}`}
                          onClick={() => setDetail(person.developerId)}
                        >
                          {person.name}
                        </button>
                      </th>
                      <td>{person.jobName}</td>
                      {person.cells.map((cell) => (
                        <td
                          key={cell.month}
                          className={cellClass(cell)}
                          title={manpowerBoardTooltip(cell)}
                        >
                          <span>{cell.statusName}</span>
                          {cell.extensionPossibilityName && (
                            <small>{cell.extensionPossibilityName}</small>
                          )}
                          {cell.tentative &&
                            cell.winningProbability != null && (
                              <small>{cell.winningProbability}%</small>
                            )}
                          {cell.totalMm > 1 && (
                            <Badge kind="bad">합계 {mm(cell.totalMm)} MM</Badge>
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}
                {data?.items.length === 0 && (
                  <tr>
                    <td colSpan={14} data-testid="manpower-board-empty">
                      조회 결과가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan={2} scope="row">
                    비는 인원
                  </th>
                  {months.map((month, i) => (
                    <td key={month}>
                      {data?.monthTotals[i]?.idleCount ?? "–"}
                    </td>
                  ))}
                </tr>
                <tr>
                  <th colSpan={2} scope="row">
                    남는 MM (일부 투입 포함)
                  </th>
                  {months.map((month, i) => (
                    <td key={month}>{mm(data?.monthTotals[i]?.availableMm)}</td>
                  ))}
                </tr>
              </tfoot>
            </table>
          </div>
          <ListPager
            page={currentPage}
            pageSize={presentation.pageSize}
            total={data?.totalCount ?? 0}
            onPage={setPage}
            onPageSize={(size) => {
              presentation.setPageSize(size);
              setPage(1);
            }}
          />
        </div>
      ) : (
        <div
          role="tabpanel"
          id="manpower-board-forecast"
          aria-labelledby="manpower-board-forecast-tab"
          data-testid="manpower-board-forecast"
        >
          <p>
            가용: 기존 예측의 남는 인력 / 필요: 영업 확률 + 진행 미충원 (MM)
          </p>
          {forecastError ? (
            <div role="alert">
              {forecastError}
              <button
                type="button"
                data-testid="manpower-board-forecast-retry"
                onClick={() => setForecastRevision((n) => n + 1)}
              >
                다시 시도
              </button>
            </div>
          ) : !forecast ? (
            <p role="status">수요·공급을 불러오는 중입니다.</p>
          ) : (
            <div className="manpower-board-scroll">
              <table className="manpower-board-forecast-table">
                <thead>
                  <tr>
                    <th scope="col">구분 (MM)</th>
                    {forecast.months.map((month) => (
                      <th scope="col" key={month.month}>
                        {month.month}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <th scope="row">필요 인력</th>
                    {forecast.months.map((month) => (
                      <td key={month.month}>{mm(month.demand)}</td>
                    ))}
                  </tr>
                  <tr>
                    <th scope="row">가용 인력</th>
                    {forecast.months.map((month) => (
                      <td key={month.month}>{mm(month.supply)}</td>
                    ))}
                  </tr>
                  <tr>
                    <th scope="row">차이</th>
                    {forecast.months.map((month) => (
                      <td
                        key={month.month}
                        className={
                          month.supply < month.demand
                            ? "board-shortage"
                            : month.supply > month.demand
                              ? "board-surplus"
                              : undefined
                        }
                      >
                        {month.supply === month.demand
                          ? "맞음"
                          : `${mm(Math.abs(month.supply - month.demand))} MM ${month.supply < month.demand ? "부족" : "남음"}`}
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
              {forecast.months.length === 0 && <p>예측 자료가 없습니다.</p>}
            </div>
          )}
        </div>
      )}
      {detail && (
        <DeveloperDetail
          readOnly
          developerId={detail}
          onClose={() => setDetail(null)}
        />
      )}
      {notice && (
        <Toast
          {...notice}
          testId="manpower-board-toast"
          onDismiss={() => setNotice(null)}
        />
      )}
    </section>
  );
}

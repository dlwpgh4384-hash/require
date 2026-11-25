import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import {
  Badge,
  Banner,
  KpiCard,
  KpiCards,
  ListFilter,
  ListFilters,
  ListPager,
  useListPresentation,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { koreanToday } from "../../shared/time";
import { getCommonCodes } from "../codegroups/codeGroups";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { useOpenDeveloperAction } from "../developers/developerRequest";
import { projectFailure, ProjectRequestError } from "../projects/projects";
import {
  getCommonSalesPipelineForecast,
  type SalesForecast,
} from "../salespipeline/salesPipeline";
import {
  forecastDifference,
  forecastDifferenceText,
} from "../manpowerforecasts/manpowerForecasts";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import {
  getCommonManpowerBoard,
  getCommonManpowerBoardExcel,
  type ManpowerBoardCell,
  type ManpowerBoardFilter,
  type ManpowerBoardResult,
} from "./manpowerBoard";
import { withdrawalRefreshEvent } from "../withdrawals/withdrawals";
import "./manpowerBoard.css";

const periodOptions = {
  kind: "year",
  firstDataYear: 2026,
  lastDataYear: 2027,
  allowNextYear: true,
} as const;
type BoardConditions = Omit<ManpowerBoardFilter, "year">;
/** 시안 mmf: 소수 둘째 자리. */
const mmf = (value: number) => value.toFixed(2);
/** 소수 첫째 자리, 값이 없으면 「–」. */
const one = (value: number | null | undefined) =>
  value == null ? "–" : (Math.round(value * 10 + 1e-9) / 10).toFixed(1); // 서버·엑셀(HALF_UP)과 같게
// 시안 ic('info'): 안내 띠 아이콘
const infoIcon = (
  <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
  </svg>
);
/** 시안 lg: 칸 견본과 이름. */
const legends: { cls: string; text?: string; label: string; part?: boolean }[] = [
  { cls: "c-on", label: "투입 중" },
  { cls: "c-on c-part", label: "일부 투입(1.0 미만)", part: true },
  { cls: "c-on", text: "2건", label: "동시 투입" },
  { cls: "c-end", label: "철수 예정" },
  { cls: "c-idle", label: "비어 있음" },
  { cls: "c-tent", label: "가배정" },
  { cls: "c-leave", label: "휴직" },
  { cls: "c-ret", label: "퇴사" },
  { cls: "c-pre", text: "⚠", label: "미계약 선투입" },
];
/** 시안 cellHtml: 일부 투입(1.0 미만)은 투입·철수 예정·미계약 선투입 칸에만. */
const isPart = (cell: ManpowerBoardCell) =>
  ["on", "end", "pre"].includes(cell.statusCode) && cell.totalMm < 1;
/** 시안 cellHtml 칸 글자: 2건 이상 → 「N건」, 일부 투입 → MM(소수 첫째), 철수 예정 → 연장 가능성, 가배정 → 확률, 미계약 → ⚠. */
export function manpowerBoardCellText(cell: ManpowerBoardCell) {
  if (cell.projects.length > 1) return `${cell.projects.length}건`;
  if (isPart(cell)) return cell.totalMm.toFixed(1);
  if (cell.statusCode === "end" && cell.extensionPossibilityName)
    return cell.extensionPossibilityName;
  if (cell.statusCode === "tent")
    return cell.winningProbability == null ? "" : `${cell.winningProbability}%`;
  if (cell.statusCode === "pre") return "⚠";
  return "";
}
/** 시안 cellHtml 풍선 도움말 한 줄: 「달 · 상태 · 프로젝트 0.50MM, … (합계 1.30, 1.0 초과)」. */
export function manpowerBoardTooltip(cell: ManpowerBoardCell) {
  const list = cell.projects.length
    ? ` · ${cell.projects.map((p) => `${p.projectName} ${mmf(p.monthlyMm)}MM`).join(", ")}${cell.totalMm > 1 ? ` (합계 ${mmf(cell.totalMm)}, 1.0 초과)` : ""}`
    : "";
  return `${cell.month} · ${cell.statusName}${list}`;
}
function BoardCell({ cell }: { cell: ManpowerBoardCell }) {
  const part = isPart(cell);
  return (
    <td className="mc">
      <span
        className={`cell c-${cell.statusCode}${part ? " c-part" : ""}${cell.totalMm > 1 ? " c-over" : ""}`}
        style={
          part
            ? ({ "--p": `${Math.round(cell.totalMm * 100)}%` } as CSSProperties)
            : undefined
        }
        title={manpowerBoardTooltip(cell)}
      >
        {manpowerBoardCellText(cell)}
      </span>
    </td>
  );
}

// SCR-UI-28: 업무자료는 조회 전용이다. 사람 상세는 시안 openPerson대로 개발자 풀과 같은 상세([수정]·[사용 중지])를 쓰고,
// [수정]·[사용 중지]는 상세를 닫고 개발자 풀(UI-02)의 수정 폼·확인 창으로 넘긴다(시안 editform, 개인 원가·수익표와 같은 통로).
export function UI28Page({
  renderActions,
}: { renderActions?: (actions: ReactNode) => ReactNode } = {}) {
  const navigate = useNavigate();
  const openDeveloperAction = useOpenDeveloperAction();
  const period = useScreenPeriod("UI-28", periodOptions);
  // 오늘(한국 날짜)은 한 번만 구한다.
  const today = useMemo(() => koreanToday(), []);
  const currentYear = Number(today.slice(0, 4));
  const thisMonth = today.slice(0, 7);
  // 기간을 읽은 뒤에만 조회한다(처음 요청 한 번).
  const year =
    period.state.status === "ready" ? period.state.value.year : null;
  const [draft, setDraft] = useState<BoardConditions>({});
  const [applied, setApplied] = useState<BoardConditions>({});
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
  const forecastRequested = useRef<number | null>(null);
  const alive = useRef(true);
  const [detail, setDetail] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const presentation = useListPresentation();
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: ToastKind;
  } | null>(null);

  useEffect(() => {
    alive.current = true;
    // AI 인력 추천 [배정]·[가배정], 철수 리스트 가배정이 바뀌면 열린 탭의 달력(점선 포함)도 다시 읽는다.
    const reload = () => setRevision((n) => n + 1);
    window.addEventListener(withdrawalRefreshEvent, reload);
    return () => {
      alive.current = false;
      window.removeEventListener(withdrawalRefreshEvent, reload);
    };
  }, []);
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
    if (year == null) return;
    // 연도를 빨리 바꾸면 앞 요청은 끊고, 늦게 온 앞 응답은 버린다.
    const controller = new AbortController();
    setData(null);
    setError("");
    setFields([]);
    getCommonManpowerBoard({ year, ...applied }, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (controller.signal.aborted) return;
        setError(projectFailure(reason));
        if (reason instanceof ProjectRequestError) setFields(reason.errors);
      });
    return () => controller.abort();
  }, [year, applied, revision]);
  useEffect(() => {
    // 처음 연 결과를 탭을 바꿔도 그대로 두고, 다시 시도할 때만 새로 읽는다.
    if (tab !== "forecast" || forecastRequested.current === forecastRevision)
      return;
    const request = forecastRevision;
    forecastRequested.current = request;
    setForecast(null);
    setForecastError("");
    // 달력의 연도/코드 조건은 보내지 않고, 이번 달부터 예측한다.
    // 탭을 연 채 달이 바뀌어도 400이 나지 않게, 보낼 때 오늘 달을 다시 구한다.
    getCommonSalesPipelineForecast(koreanToday().slice(0, 7))
      .then((result) => {
        if (alive.current && forecastRequested.current === request)
          setForecast(result);
      })
      .catch((reason) => {
        if (alive.current && forecastRequested.current === request)
          setForecastError(projectFailure(reason));
      });
  }, [tab, forecastRevision, thisMonth]);

  const search = () => {
    setApplied({ ...draft });
    setPage(1);
    setRevision((n) => n + 1);
  };
  const reset = () => {
    setDraft({});
    setApplied({});
    setPage(1);
    setRevision((n) => n + 1);
  };
  const download = async () => {
    if (downloading || year == null) return;
    setDownloading(true);
    try {
      const count = await getCommonManpowerBoardExcel({ year, ...applied });
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
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
      </svg>{" "}
      {downloading ? "만드는 중…" : "엑셀 다운로드"}
    </button>
  );
  const pages = Math.max(
    1,
    Math.ceil((data?.items.length ?? 0) / presentation.pageSize),
  );
  const currentPage = Math.min(page, pages);
  const shownYear = year ?? currentYear;
  const months = Array.from(
    { length: 12 },
    (_, i) => `${shownYear}-${String(i + 1).padStart(2, "0")}`,
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
  const tabs = [
    ["calendar", "달력"],
    ["forecast", "수요·공급"],
  ] as const;

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
        <YearBar
          year={period.state.value.year}
          currentYear={currentYear}
          min={periodOptions.firstDataYear}
          max={periodOptions.lastDataYear}
          caption="그 해 1~12월 달력"
          testId="manpower-board-year"
          onChange={(value) => {
            period.setValue({ year: value });
            setPage(1);
          }}
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
          value={summary ? `${summary.idleCount}명` : "–"}
          description={`남는 MM ${one(summary?.availableMm)} (일부 투입 포함)`}
        />
        <KpiCard
          label="3개월 안 철수 예정"
          value={summary ? `${summary.withdrawalCount}명` : "–"}
          description={`연장 가능성 높음 ${summary?.highExtensionCount ?? "–"}명`}
          testId="manpower-board-withdrawal"
          onClick={() => navigate("/screens/UI-05")}
        />
        <KpiCard
          label="동시 투입"
          value={summary ? `${summary.concurrentCount}명` : "–"}
          description={`이번 달 2건 이상 · 1.0 초과 ${summary?.overAllocatedCount ?? "–"}명`}
          testId="manpower-board-concurrent"
          onClick={() => navigate("/screens/UI-12")}
        />
        <KpiCard
          label="3개월 뒤 남는 MM"
          value={one(summary?.futureAvailableMm)}
          description={<Badge kind="info">예측</Badge>}
        />
      </KpiCards>
      <div className="ls-tabs" role="tablist" aria-label="인력 운영 보기">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            id={`manpower-board-${key}-tab`}
            className={`ls-tb${tab === key ? " ls-on" : ""}`}
            aria-selected={tab === key}
            aria-controls={tab === key ? `manpower-board-${key}` : undefined}
            data-testid={`manpower-board-${key}-tab`}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
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
          <div className="ls-tbar">
            <div className="ls-tl" data-testid="manpower-board-total">
              총 <strong>{data?.totalCount ?? "–"}</strong>명
            </div>
            <div className="ls-tr">
              <div className="board-legend" data-testid="manpower-board-legend">
                {legends.map((item) => (
                  <span key={item.label}>
                    <span
                      className={`cell ${item.cls}`}
                      style={
                        item.part
                          ? ({ "--p": "50%" } as CSSProperties)
                          : undefined
                      }
                      aria-hidden="true"
                    >
                      {item.text}
                    </span>
                    {item.label}
                  </span>
                ))}
              </div>
            </div>
          </div>
          <div
            className="ls-twrap"
            tabIndex={0}
            role="region"
            aria-label="연간 인력 운영 달력"
            data-testid="manpower-board-scroll"
          >
            <table className="ls-t board-table">
              <thead>
                <tr>
                  <th scope="col" className="ls-sc">
                    이름
                  </th>
                  <th scope="col">직무</th>
                  {months.map((month) => (
                    <th
                      scope="col"
                      key={month}
                      className={month === thisMonth ? "mc cur" : "mc"}
                    >
                      {month.slice(2)}
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
                      tabIndex={0}
                      data-testid={`manpower-board-person-${person.developerId}`}
                      onClick={() => setDetail(person.developerId)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") setDetail(person.developerId);
                      }}
                    >
                      <td className="ls-sc">
                        <span className="board-pname">
                          {person.name}
                          <small>{person.managementNumber || person.developerId}</small>
                        </span>
                      </td>
                      <td>{person.jobName}</td>
                      {person.cells.map((cell) => (
                        <BoardCell key={cell.month} cell={cell} />
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
              <tfoot className="ls-foot">
                <tr data-testid="manpower-board-idle-row">
                  <td className="ls-sc">비는 인원</td>
                  <td />
                  {months.map((month, i) => {
                    const count = data?.monthTotals[i]?.idleCount;
                    return (
                      <td key={month} className="mc">
                        <span className={count ? "board-bad" : undefined}>
                          {count == null ? "–" : `${count}명`}
                        </span>
                      </td>
                    );
                  })}
                </tr>
                <tr data-testid="manpower-board-free-row">
                  <td className="ls-sc">남는 MM</td>
                  <td className="ls-cap">일부 투입 포함</td>
                  {months.map((month, i) => (
                    <td key={month} className="mc">
                      {one(data?.monthTotals[i]?.availableMm)}
                    </td>
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
          <p className="ls-note">
            칸에 마우스를 올리면 그 달의 프로젝트별 MM이 보입니다. 1.0 미만으로
            투입된 달은 투입된 만큼만 칠합니다.
          </p>
        </div>
      ) : (
        <div
          role="tabpanel"
          id="manpower-board-forecast"
          aria-labelledby="manpower-board-forecast-tab"
          data-testid="manpower-board-forecast"
        >
          <Banner kind="info" icon={infoIcon}>
            가용 = 사람마다 (1 − 그 달 투입 MM)의 합. 철수한 투입은 연장
            가능성(높음 80%·보통 50%·낮음 0%)만큼 남는다고 봅니다. 필요 = 영업
            기회의 필요 인력 × 단계 확률 + 진행 프로젝트 미충원.
          </Banner>
          {forecastError ? (
            <div role="alert">
              {forecastError}{" "}
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
            <div className="ls-twrap board-free">
              <table className="ls-t">
                <thead>
                  <tr>
                    <th scope="col" className="ls-sc">
                      구분
                    </th>
                    {forecast.months.map((month) => (
                      <th scope="col" key={month.month} className="ls-right">
                        {month.month}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td className="ls-sc">필요 인력</td>
                    {forecast.months.map((month) => (
                      <td key={month.month} className="ls-right">
                        {one(month.demand)}명
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="ls-sc">가용 인력</td>
                    {forecast.months.map((month) => (
                      <td key={month.month} className="ls-right">
                        {one(month.supply)}명
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="ls-sc">
                      <strong>차이</strong>
                    </td>
                    {forecast.months.map((month) => {
                      // 인력 수요 예측(UI-17) 표와 같은 글: 화면에 보이는 두 값(소수 첫째 자리)의 차
                      const d = forecastDifference(month);
                      return (
                        <td key={month.month} className="ls-right">
                          <span
                            className={
                              d < -0.4
                                ? "board-bad"
                                : d > 1
                                  ? "board-warn"
                                  : undefined
                            }
                          >
                            {forecastDifferenceText(d)}
                          </span>
                        </td>
                      );
                    })}
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
          key={detail}
          developerId={detail}
          onClose={() => setDetail(null)}
          onEdit={(developer) => {
            setDetail(null);
            openDeveloperAction({ developer, action: "edit" });
          }}
          onStop={(developer) => {
            setDetail(null);
            openDeveloperAction({ developer, action: "stop" });
          }}
          onNotice={(message) => setNotice({ kind: "warn", message })}
          onChanged={(message) => {
            // 상세 안에서 근무 상태를 바꾸면 달력을 다시 읽는다(개발자 풀과 같은 알림 종류).
            setRevision((n) => n + 1);
            if (message)
              setNotice({
                kind: message.includes("투입 배정이") ? "info" : "ok",
                message,
              });
          }}
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

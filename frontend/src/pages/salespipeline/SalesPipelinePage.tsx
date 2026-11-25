import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import { requestAiRecommendation } from "../airecommendations/aiRecommendationRequest";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  DEFAULT_MONEY_UNIT,
  FormulaBox,
  KpiCard,
  KpiCards,
  ListFilter,
  ListFilters,
  ListView,
  MoneyUnitSelect,
  formatKoreanMoney,
  formatMoney,
  listMessages,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
  type MoneyUnit,
} from "../../shared/list";
import { koreanToday } from "../../shared/time";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { SalesOpportunityForm } from "./SalesOpportunityForm";
import { SalesOpportunityDetail } from "./SalesOpportunityDetail";
import type {
  SalesNotice,
  SalesOpportunity,
  SalesOpportunityOptions,
} from "./salesOpportunities";
import { ProjectLink } from "../projects/ProjectLink";
import {
  ChartLegend,
  StackChart,
  axisScale,
} from "../profitstructures/ProfitStructureCharts";
import {
  DeveloperRequestError,
  approvalBadge,
  developerFailure,
  getCommonSalesPipeline,
  getCommonSalesPipelineClosed,
  getCommonSalesPipelineForecast,
  getCommonSalesPipelineForecastExcel,
  getSalesPipelineOptions,
  cardJobs,
  putCommonSalesPipelineStage,
  salesPipelineDraft,
  type PipelineCard,
  type SalesPipeline,
  type SalesClosed,
  type SalesForecast,
} from "./salesPipeline";
import "../profitstructures/profitStructures.css";
import "./salesPipeline.css";

type Tab = "ongoing" | "closed" | "forecast";
const icon = (d: string, size = 16) => (
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
/** 시안 ic() 경로. */
const paths = {
  plus: "M12 5v14M5 12h14",
  down: "M12 3v12M7 10l5 5 5-5M5 21h14",
  warn: "M12 3l10 18H2zM12 10v5M12 18h.01",
  sparkle:
    "M12 3l1.8 4.6L18 9l-4.2 1.4L12 15l-1.8-4.6L6 9l4.2-1.4zM19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z",
};
const kor = formatKoreanMoney;
const percent = (value: number | null | undefined) =>
  value == null ? "–" : `${Number(value)}%`;
const monthLabel = (month: string) => `${Number(month.slice(5))}월`;
const sum = (values: number[]) => values.reduce((a, v) => a + v, 0);
const chart = { c1: "var(--pf-c1)", c3: "var(--pf-c3)" };

/** 시안 barChart(): 인력 수요·공급 묶음 막대. 눈금은 명(소수 없이). */
function PeopleChart({
  labels,
  series,
}: {
  labels: string[];
  series: { name: string; color: string; values: number[] }[];
}) {
  const W = 640,
    H = 220,
    PL = 52,
    PR = 16,
    PT = 12,
    PB = 28;
  const { high, ticks } = axisScale(series.flatMap((s) => s.values));
  const y = (value: number) => PT + (H - PT - PB) * (1 - value / high);
  const gw = (W - PL - PR) / Math.max(1, labels.length);
  const bw = Math.min(22, (gw - 12) / Math.max(1, series.length));
  return (
    <>
      <svg
        className="profit-chart"
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`${series.map((s) => s.name).join(", ")} 비교`}
        data-testid="sales-manpower-chart"
      >
        {ticks.map((value) => (
          <g key={value}>
            <line
              className={value === 0 ? "zl" : "gl"}
              x1={PL}
              x2={W - PR}
              y1={y(value)}
              y2={y(value)}
            />
            <text className="ax" x={PL - 8} y={y(value) + 4} textAnchor="end">
              {value.toFixed(0)}
            </text>
          </g>
        ))}
        {labels.map((label, index) => {
          const cx = PL + gw * index + gw / 2;
          return (
            <g key={`${label}-${index}`}>
              <text className="ax" x={cx} y={H - 8} textAnchor="middle">
                {label}
              </text>
              {series.map((s, j) => {
                const value = Math.max(0, s.values[index] || 0);
                const bx = cx - (bw * series.length) / 2 + bw * j;
                return (
                  <rect
                    key={s.name}
                    x={bx + 1}
                    y={y(value)}
                    width={Math.max(0, bw - 2)}
                    height={Math.max(0, y(0) - y(value))}
                    rx={2}
                    style={{ fill: s.color }}
                  >
                    <title>{`${label} ${s.name} ${value.toFixed(1)}명`}</title>
                  </rect>
                );
              })}
            </g>
          );
        })}
      </svg>
      <ChartLegend items={series.map(({ name, color }) => ({ name, color }))} />
    </>
  );
}

/** 인력계획 › 영업 파이프라인 UI-29. 시안 SCR['UI-29']·pipeForecast(). */
export function SalesPipelinePage({
  renderActions,
  testId = "sales-pipeline-page",
}: {
  renderActions?: (actions: ReactNode) => ReactNode;
  testId?: string;
}) {
  const root = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>("ongoing");
  const [revision, setRevision] = useState(0);
  const refresh = () => setRevision((v) => v + 1);
  const [pipeline, setPipeline] = useState<SalesPipeline | null>(null);
  const [pipelineError, setPipelineError] = useState("");
  const [closed, setClosed] = useState<SalesClosed[] | null>(null);
  const [closedError, setClosedError] = useState("");
  const [forecast, setForecast] = useState<SalesForecast | null>(null);
  const [forecastError, setForecastError] = useState("");
  const [options, setOptions] = useState<SalesOpportunityOptions | null>(null);
  const [optionError, setOptionError] = useState("");
  const [resultDraft, setResultDraft] = useState("");
  const [result, setResult] = useState("");
  const [includeStopped, setIncludeStopped] = useState(false);
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  /** 상세 서랍에 연 기회 ID(머리·탭은 SalesOpportunityDetail이 그린다). */
  const [selected, setSelected] = useState<string | null>(null);
  const [editing, setEditing] = useState<SalesOpportunity | "new" | null>(null);
  const [highlight, setHighlight] = useState<{
    opportunityId: string;
    milliseconds: number;
  } | null>(null);
  // 시안 toast()는 알림을 쌓는다(저장 알림 아래에 승인 대기 안내 한 장 더).
  const [notices, setNotices] = useState<(SalesNotice & { id: number })[]>([]);
  const [downloading, setDownloading] = useState(false);
  const [moving, setMoving] = useState<string | null>(null);
  const [dropStage, setDropStage] = useState<string | null>(null);
  const dragged = useRef<string | null>(null);
  const show = (list: SalesNotice[]) => {
    const at = Date.now();
    setNotices(list.map((notice, index) => ({ ...notice, id: at + index })));
  };
  const notify = (message: string, kind: ToastKind = "ok") =>
    show([{ message, kind }]);

  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(
      () => setHighlight(null),
      highlight.milliseconds,
    );
    return () => window.clearTimeout(timer);
  }, [highlight]);
  // 고르는 값(공통코드·고객사·파트·인력)은 화면을 열 때 한 번만 읽는다. 저장·처리 뒤 새로 고침(refresh)에는
  // 다시 읽지 않고, 읽기에 실패했을 때만 [다시 시도]로 다시 읽는다.
  const [optionAttempt, setOptionAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    getSalesPipelineOptions()
      .then((value) => {
        if (active) {
          setOptions(value);
          setOptionError("");
        }
      })
      .catch((reason) => {
        if (active) setOptionError(developerFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [optionAttempt]);
  // 요약 4칸·탭 건수는 세 탭 모두 위에 보인다(시안 head). 종료 탭 건수도 파이프라인 요약(closedCount)에서 읽는다.
  useEffect(() => {
    let active = true;
    setPipelineError("");
    getCommonSalesPipeline()
      .then((value) => {
        if (active) setPipeline(value);
      })
      .catch((reason) => {
        if (active) setPipelineError(developerFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [revision]);
  useEffect(() => {
    if (tab !== "closed") return;
    let active = true;
    setClosed(null);
    setClosedError("");
    getCommonSalesPipelineClosed(result, includeStopped)
      .then((value) => {
        if (active) setClosed(value.items);
      })
      .catch((reason) => {
        if (active) {
          setClosed([]);
          setClosedError(developerFailure(reason));
        }
      });
    return () => {
      active = false;
    };
  }, [tab, result, includeStopped, revision]);
  useEffect(() => {
    if (tab !== "forecast") return;
    let active = true;
    setForecast(null);
    setForecastError("");
    getCommonSalesPipelineForecast()
      .then((value) => {
        if (active) setForecast(value);
      })
      .catch((reason) => {
        if (active) setForecastError(developerFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [tab, revision]);

  const searchClosed = (value = resultDraft) => {
    setResult(value);
  };
  usePreferenceBusinessShortcuts({
    scope: root,
    onSearch: () => {
      if (tab === "closed") searchClosed();
    },
  });

  const codeName = (group: string, code: string | null | undefined) =>
    (code && options?.codes[group]?.find((c) => c.code === code)?.codeName) ||
    "";
  const jobName = (code: string, name?: string | null) =>
    name || codeName("JOB", code) || code;
  const stages = pipeline?.stages || [];
  const stageName = (code: string) =>
    stages.find((s) => s.stageCode === code)?.stageName ||
    codeName("SALES_STEP", code) ||
    code;
  const stageChoices = stages.length
    ? stages.map((s) => ({ code: s.stageCode, name: stageName(s.stageCode) }))
    : (options?.codes.SALES_STEP || []).map((c) => ({
        code: c.code,
        name: c.codeName,
      }));
  // 기회 폼의 단계별 기본 확률: 이미 읽은 파이프라인 값을 넘겨 폼이 다시 읽지 않게 한다.
  const stageProbabilities = useMemo(() => {
    const known = (pipeline?.stages || []).filter(
      (s) => s.defaultProbability != null,
    );
    return known.length
      ? Object.fromEntries(
          known.map((s) => [s.stageCode, Number(s.defaultProbability)]),
        )
      : undefined;
  }, [pipeline]);
  const resultName = (code: string) =>
    code === "WIN"
      ? "수주"
      : code === "LOSE"
        ? "실주"
        : codeName("OPP_STAT", code) || "–";

  async function move(card: PipelineCard, stageCode: string) {
    if (!stageCode || card.stageCode === stageCode || moving) return;
    setMoving(card.opportunityId);
    try {
      const saved = await putCommonSalesPipelineStage(card, stageCode);
      const changed =
        saved?.winningProbability != null &&
        Number(saved.winningProbability) !== Number(card.winningProbability);
      notify(
        `「${card.opportunityName}」을 ${stageName(stageCode)} 단계로 옮겼습니다.${
          changed ? ` 수주 확률 ${Number(saved.winningProbability)}%` : ""
        }`,
      );
      refresh();
    } catch (reason) {
      notify(developerFailure(reason), "warn");
      if (reason instanceof DeveloperRequestError && reason.status === 409)
        refresh();
    } finally {
      setMoving(null);
    }
  }
  const openCard = (card: PipelineCard) => setSelected(card.opportunityId);
  const dropOn = (event: DragEvent<HTMLElement>, stageCode: string) => {
    event.preventDefault();
    setDropStage(null);
    const id = event.dataTransfer?.getData("text/plain") || dragged.current;
    dragged.current = null;
    const card = stages
      .flatMap((s) => s.items)
      .find((item) => item.opportunityId === id);
    if (card) void move(card, stageCode);
  };

  async function download() {
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonSalesPipelineForecastExcel();
      notify(`엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`);
    } catch (reason) {
      notify(developerFailure(reason), "warn");
    } finally {
      setDownloading(false);
    }
  }

  const createButton = (
    <button
      type="button"
      className="ls-btn ls-btn-p"
      data-testid="sales-pipeline-create"
      disabled={!options}
      onClick={() => setEditing("new")}
    >
      {icon(paths.plus)} 기회 등록
    </button>
  );
  const actions =
    tab === "forecast" ? (
      <button
        type="button"
        className="ls-btn"
        data-testid="sales-forecast-excel"
        disabled={downloading}
        onClick={() => void download()}
      >
        {icon(paths.down)} {downloading ? "만드는 중…" : "엑셀 다운로드"}
      </button>
    ) : tab === "closed" ? (
      <>
        <label className="sales-stop-check">
          <input
            data-testid="sales-include-stopped"
            type="checkbox"
            checked={includeStopped}
            onChange={(e) => {
              setIncludeStopped(e.target.checked);
            }}
          />{" "}
          사용 중지 포함
        </label>
        {createButton}
      </>
    ) : (
      createButton
    );
  const retry = (id: string, onRetry: () => void = refresh) => (
    <button
      type="button"
      className="ls-btn ls-btn-sm"
      data-testid={id}
      onClick={onRetry}
    >
      다시 시도
    </button>
  );

  const summary = pipeline?.summary;
  const tabs: [Tab, string][] = [
    ["ongoing", `진행 중 (${summary ? summary.ongoingCount : "–"})`],
    ["closed", `종료 · 수주·실주 (${summary?.closedCount ?? "–"})`],
    ["forecast", "매출·인력 예측"],
  ];
  const today = koreanToday();

  if (editing && options)
    return (
      <div
        ref={root}
        className="screen-body sales-pipeline"
        data-screen-id="UI-29"
        data-testid={testId}
      >
        {/* 시안 oppFormView: 제목줄 단추는 비우고, 제목줄 [‹ 목록] + 폼 제목은 기회 폼이 바꾼다(제목은 한 번만). */}
        {renderActions?.(null)}
        <div data-testid="sales-pipeline-form">
          <SalesOpportunityForm
            initial={editing === "new" ? salesPipelineDraft() : editing}
            opportunity={editing === "new" ? undefined : editing}
            options={options}
            stageProbabilities={stageProbabilities}
            onCancel={() => setEditing(null)}
            onSaved={(row, highlightMilliseconds, saved) => {
              const created = editing === "new";
              setEditing(null);
              setTab("ongoing");
              setHighlight({
                opportunityId: row.opportunityId,
                milliseconds: highlightMilliseconds,
              });
              refresh();
              show(
                saved?.length
                  ? saved
                  : [
                      {
                        kind: "ok",
                        message: created
                          ? `「${row.opportunityName}」 기회를 등록했습니다.`
                          : "저장했습니다.",
                      },
                    ],
              );
            }}
            onDeleted={(_row, deleted) => {
              setEditing(null);
              refresh();
              show(deleted);
            }}
          />
        </div>
      </div>
    );

  const months = forecast?.months || [];
  const projectRevenue = months.map((m) => Number(m.projectRevenue) || 0);
  const weightedRevenue = months.map(
    (m) => Number(m.weightedOpportunityRevenue) || 0,
  );
  const allWonRevenue = months.map((m) => Number(m.allWonRevenue) || 0);
  const fullOpportunity = allWonRevenue.map((v, i) => v - projectRevenue[i]);
  const demand = months.map((m) => Number(m.demand) || 0);
  const supply = months.map((m) => Number(m.supply) || 0);
  const labels = months.map((m) => monthLabel(m.month));
  const opportunityRows = forecast?.opportunityRevenues || [];
  const hiring = forecast?.hiringReviews || [];
  const hiringName = (row: SalesForecast["hiringReviews"][number]) =>
    jobName(row.jobCode, row.jobName);

  return (
    <div
      ref={root}
      className="screen-body sales-pipeline"
      data-screen-id="UI-29"
      data-testid={testId}
    >
      {renderActions ? (
        renderActions(actions)
      ) : (
        <div className="sales-actions">{actions}</div>
      )}
      {optionError && (
        <p role="alert" className="sales-error">
          {optionError}{" "}
          {retry("sales-options-retry", () => setOptionAttempt((n) => n + 1))}
        </p>
      )}
      {pipelineError ? (
        <p role="alert" className="sales-error">
          {pipelineError} {retry("sales-retry")}
        </p>
      ) : summary ? (
        <KpiCards aria-label="파이프라인 요약" testId="sales-summary">
          <KpiCard
            label="진행 중인 기회"
            value={`${summary.ongoingCount}건`}
            description={`2주 갱신 없음 ${summary.staleCount}건`}
          />
          <KpiCard
            label="예상 금액 합계"
            value={kor(Number(summary.expectedAmount))}
          />
          <KpiCard
            label="확률 반영 금액"
            value={kor(Number(summary.weightedAmount))}
            description="수주 확률 × 예상 금액"
          />
          <KpiCard
            label="확률 반영 필요 인력"
            value={`${Number(summary.weightedManpower).toFixed(1)}명`}
            description="필요 인원 × 월 MM × 수주 확률"
            testId="sales-kpi-manpower"
            onClick={() => navigate("/screens/UI-17")}
          />
        </KpiCards>
      ) : (
        <p role="status">{listMessages.loading}</p>
      )}
      <div
        className="ls-tabs sales-tabs"
        role="tablist"
        aria-label="영업 파이프라인 탭"
      >
        {tabs.map(([key, text]) => (
          <button
            key={key}
            type="button"
            role="tab"
            className={`ls-tb${tab === key ? " ls-on" : ""}`}
            data-testid={`sales-tab-${key}`}
            aria-selected={tab === key}
            onClick={() => {
              setTab(key);
            }}
          >
            {text}
          </button>
        ))}
      </div>
      {tab === "ongoing" && pipeline && (
        <section data-testid="sales-ongoing-panel" className="sales-panel">
          <p className="ls-note">
            카드를 누르면 상세가 열립니다. 카드를 끌어 다른 단계로 옮길 수 있고,
            휴대폰에서는 카드 아래 &apos;단계&apos;에서 고릅니다.
          </p>
          {!stages.length ? (
            <p className="ls-cap">{listMessages.empty}</p>
          ) : (
            <div className="sales-kanban">
              {stages.map((stage) => (
                <section
                  key={stage.stageCode}
                  className={`sales-kcol${dropStage === stage.stageCode ? " is-drop" : ""}`}
                  aria-label={stageName(stage.stageCode)}
                  data-testid={`sales-stage-${stage.stageCode.toLowerCase()}`}
                  onDragOver={(event) => {
                    event.preventDefault();
                    setDropStage(stage.stageCode);
                  }}
                  onDragLeave={() => setDropStage(null)}
                  onDrop={(event) => dropOn(event, stage.stageCode)}
                >
                  <div className="sales-kh">
                    <h2>
                      {stageName(stage.stageCode)}{" "}
                      {stage.defaultProbability != null && (
                        <span className="sales-kp">
                          기본 {Number(stage.defaultProbability)}%
                        </span>
                      )}
                    </h2>
                    <span className="sales-ksum">
                      {stage.count}건 · {kor(Number(stage.amount))}
                    </span>
                  </div>
                  {stage.items.length ? (
                    stage.items.map((card) => {
                      const due =
                        !!card.nextActionDueDate &&
                        card.nextActionDueDate < today;
                      const approval = approvalBadge(card.approvalStatusCode);
                      const jobs = cardJobs(card);
                      return (
                        <article
                          key={card.opportunityId}
                          data-testid={`sales-card-${card.opportunityId}`}
                          className={`sales-opp${highlight?.opportunityId === card.opportunityId ? " is-hl" : ""}`}
                          draggable
                          tabIndex={0}
                          aria-busy={moving === card.opportunityId}
                          onDragStart={(event) => {
                            dragged.current = card.opportunityId;
                            event.dataTransfer?.setData(
                              "text/plain",
                              card.opportunityId,
                            );
                          }}
                          onClick={() => openCard(card)}
                          onKeyDown={(event) => {
                            if (
                              event.target === event.currentTarget &&
                              (event.key === "Enter" || event.key === " ")
                            ) {
                              event.preventDefault();
                              openCard(card);
                            }
                          }}
                        >
                          <div className="sales-nm">{card.opportunityName}</div>
                          <div className="ls-cap sales-line">
                            <span>
                              {card.customerName} · {card.startMonth} 시작
                            </span>
                            <b>{kor(Number(card.expectedAmount))}</b>
                          </div>
                          <div className="sales-chips">
                            <span className="ls-chip">
                              확률 {percent(card.winningProbability)}
                            </span>
                            {jobs.map((job, index) => (
                              <span
                                className="ls-chip"
                                key={`${job.jobCode}-${index}`}
                              >
                                {jobName(job.jobCode, job.jobName)}
                                {job.count != null && ` ${job.count}`}
                              </span>
                            ))}
                          </div>
                          {card.nextAction && (
                            <div
                              className={`ls-cap${due ? " sales-overdue" : ""}`}
                              data-testid={`sales-next-${card.opportunityId}`}
                            >
                              다음: {card.nextAction}
                              {card.nextActionDueDate &&
                                ` · ${card.nextActionDueDate.slice(5)}`}
                              {due && " (기한 지남)"}
                            </div>
                          )}
                          {(card.stale || approval) && (
                            <div className="sales-badges">
                              {card.stale && (
                                <Badge kind="warn">
                                  {icon(paths.warn, 12)}2주 갱신 없음
                                </Badge>
                              )}
                              {approval && (
                                <Badge kind={approval.kind}>
                                  {approval.icon && icon(paths.warn, 12)}
                                  {approval.text}
                                </Badge>
                              )}
                            </div>
                          )}
                          <div className="sales-card-foot">
                            <button
                              type="button"
                              className="ls-btn ls-btn-sm"
                              data-testid={`sales-recommend-${card.opportunityId}`}
                              onClick={(event) => {
                                event.stopPropagation();
                                // 대상 = 이 기회, 직무 = 첫 필요 인력 줄, 시작월 = 기회 시작월(SFR-129-02).
                                requestAiRecommendation({
                                  targetType: "opportunity",
                                  targetId: card.opportunityId,
                                  jobCode: jobs[0]?.jobCode || "BE",
                                  startMonth: card.startMonth,
                                });
                                navigate("/screens/UI-30");
                              }}
                            >
                              {icon(paths.sparkle, 14)} 인력 추천
                            </button>
                            <select
                              className="ls-in ls-in-sm"
                              aria-label="단계"
                              data-testid={`sales-stage-select-${card.opportunityId}`}
                              value={card.stageCode}
                              disabled={moving === card.opportunityId}
                              onClick={(event) => event.stopPropagation()}
                              onKeyDown={(event) => event.stopPropagation()}
                              onChange={(event) =>
                                void move(card, event.target.value)
                              }
                            >
                              {stageChoices.map((choice) => (
                                <option key={choice.code} value={choice.code}>
                                  {choice.name}
                                </option>
                              ))}
                            </select>
                          </div>
                        </article>
                      );
                    })
                  ) : (
                    <p className="ls-cap sales-kempty">
                      이 단계의 기회가 없습니다.
                    </p>
                  )}
                </section>
              ))}
            </div>
          )}
        </section>
      )}
      {tab === "closed" && (
        <section data-testid="sales-closed-panel" className="sales-panel">
          <ListFilters
            testId="sales-closed-filters"
            activeCount={result ? 1 : 0}
            onSearch={() => searchClosed()}
            onReset={() => {
              setResultDraft("");
              searchClosed("");
            }}
          >
            <ListFilter label="결과" htmlFor="sales-result-filter">
              <select
                id="sales-result-filter"
                className="ls-in"
                data-testid="sales-result-filter"
                value={resultDraft}
                onChange={(e) => setResultDraft(e.target.value)}
              >
                <option value="">전체</option>
                <option value="WIN">수주</option>
                <option value="LOSE">실주</option>
              </select>
            </ListFilter>
          </ListFilters>
          <ClosedList
            rows={closed}
            error={closedError}
            onRetry={refresh}
            resultName={resultName}
            onOpen={(row) => setSelected(row.opportunityId)}
          />
        </section>
      )}
      {tab === "forecast" &&
        (forecastError ? (
          <p role="alert" className="sales-error">
            {forecastError} {retry("sales-forecast-retry")}
          </p>
        ) : !forecast ? (
          <p role="status">{listMessages.loading}</p>
        ) : (
          <section data-testid="sales-forecast-panel" className="sales-panel">
            <KpiCards aria-label="매출·인력 예측 요약">
              <KpiCard
                label="앞으로 6개월 예상 매출"
                value={kor(sum(projectRevenue) + sum(weightedRevenue))}
                description="확률 반영"
              />
              <KpiCard
                label="그중 영업 기회"
                value={kor(sum(weightedRevenue))}
                description={`모두 따면 ${kor(sum(fullOpportunity))}`}
              />
              <KpiCard
                label="확률 반영 인력 수요"
                value={`${(sum(demand) / 6).toFixed(1)}명/월`}
                description={`공급 ${(sum(supply) / 6).toFixed(1)}명/월`}
              />
              <KpiCard
                label="채용 검토 직무"
                value={`${hiring.length}개`}
                description={
                  hiring
                    .slice(0, 3)
                    .map((h) => hiringName(h).split("·")[0])
                    .join(", ") || "없음"
                }
              />
            </KpiCards>
            <div className="sales-g2">
              <section className="sales-card">
                <div className="sales-card-h">
                  <h2>앞으로 6개월 예상 매출</h2>
                  <span className="ls-cap">단위: 백만 원</span>
                </div>
                <StackChart
                  testId="sales-revenue-chart"
                  labels={labels}
                  height={220}
                  series={[
                    {
                      name: "진행 중 프로젝트",
                      color: chart.c1,
                      values: projectRevenue,
                    },
                    {
                      name: "영업 기회(확률 반영)",
                      color: chart.c3,
                      values: weightedRevenue,
                    },
                  ]}
                />
              </section>
              <section className="sales-card">
                <div className="sales-card-h">
                  <h2>인력 수요와 공급</h2>
                  <span className="ls-cap">단위: 명 · 확률 반영</span>
                </div>
                <PeopleChart
                  labels={labels}
                  series={[
                    {
                      name: "공급(투입 가능)",
                      color: chart.c1,
                      values: supply,
                    },
                    {
                      name: "수요(확률 반영)",
                      color: chart.c3,
                      values: demand,
                    },
                  ]}
                />
              </section>
            </div>
            <section className="sales-card" data-testid="sales-revenue-table">
              <div className="sales-card-h">
                <h2>월별 예상 매출</h2>
                <MoneyUnitSelect
                  value={unit}
                  onChange={setUnit}
                  id="sales-forecast-unit"
                />
              </div>
              <div className="ls-twrap">
                <table className="ls-t sales-month-table">
                  <thead>
                    <tr>
                      <th>구분</th>
                      {labels.map((label, i) => (
                        <th key={months[i].month} className="ls-right">
                          {label}
                        </th>
                      ))}
                      <th className="ls-right">6개월 합계</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>진행 중 프로젝트</td>
                      {projectRevenue.map((v, i) => (
                        <td key={months[i].month} className="ls-right">
                          {formatMoney(v, unit)}
                        </td>
                      ))}
                      <td className="ls-right">
                        {formatMoney(sum(projectRevenue), unit)}
                      </td>
                    </tr>
                    {/* 시안 pipeForecast: 기회마다 「└ 기회명 n%」 한 줄(식-92 확률 반영, 줄 합 = 영업 기회 합계). */}
                    {opportunityRows.map((row) => {
                      // 예상 기간 밖의 달은 서버가 주지 않는다(시안: 「–」).
                      const cells = months.map((m) =>
                        row.months.find((x) => x.month === m.month),
                      );
                      const values = cells.map(
                        (cell) => Number(cell?.weightedRevenue) || 0,
                      );
                      return (
                        <tr
                          key={row.opportunityId}
                          data-testid={`sales-revenue-row-${row.opportunityId}`}
                        >
                          <td>
                            <span className="sales-sub">└</span>{" "}
                            {row.opportunityName}{" "}
                            <span className="ls-cap">
                              {percent(row.winningProbability)}
                            </span>
                          </td>
                          {values.map((v, i) => (
                            <td
                              key={months[i].month}
                              className="ls-right"
                              data-testid={`sales-revenue-${row.opportunityId}-${months[i].month}`}
                            >
                              {cells[i] ? (
                                formatMoney(v, unit)
                              ) : (
                                <span className="sales-sub">–</span>
                              )}
                            </td>
                          ))}
                          <td className="ls-right">
                            {formatMoney(sum(values), unit)}
                          </td>
                        </tr>
                      );
                    })}
                    <tr className="sales-total">
                      <td>
                        <strong>예상 매출 (확률 반영)</strong>
                      </td>
                      {projectRevenue.map((v, i) => (
                        <td key={months[i].month} className="ls-right">
                          <strong>
                            {formatMoney(v + weightedRevenue[i], unit)}
                          </strong>
                        </td>
                      ))}
                      <td className="ls-right">
                        <strong>
                          {formatMoney(
                            sum(projectRevenue) + sum(weightedRevenue),
                            unit,
                          )}
                        </strong>
                      </td>
                    </tr>
                    <tr>
                      <td className="ls-cap">참고: 기회를 모두 따면</td>
                      {allWonRevenue.map((v, i) => (
                        <td key={months[i].month} className="ls-right ls-cap">
                          {formatMoney(v, unit)}
                        </td>
                      ))}
                      <td className="ls-right ls-cap">
                        {formatMoney(sum(allWonRevenue), unit)}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
            <section className="sales-card" data-testid="sales-hiring">
              <div className="sales-card-h">
                <h2>채용·외부 인력 검토</h2>
                <span className="ls-cap">
                  확률 반영 수요가 공급보다 0.4명 넘게 많은 직무
                </span>
              </div>
              {hiring.length ? (
                <>
                  <div className="ls-twrap">
                    <table className="ls-t">
                      <thead>
                        <tr>
                          <th>직무</th>
                          <th>부족 시작</th>
                          <th className="ls-right">가장 많이 부족</th>
                          <th>채용 시작 권장</th>
                          <th>외부 인력 후보</th>
                        </tr>
                      </thead>
                      <tbody>
                        {hiring.map((row) => (
                          <tr
                            key={row.jobCode}
                            data-testid={`sales-hiring-${row.jobCode.toLowerCase()}`}
                          >
                            <td>{hiringName(row)}</td>
                            <td>{row.shortageStartMonth}</td>
                            <td className="ls-right sales-bad">
                              {Number(row.maximumShortage).toFixed(1)}명
                            </td>
                            <td>
                              {row.recommendedHiringMonth === "지금" ? (
                                <Badge kind="bad">지금</Badge>
                              ) : (
                                row.recommendedHiringMonth
                              )}
                            </td>
                            <td>
                              {row.candidateCount ? (
                                <button
                                  type="button"
                                  className="ls-btn ls-btn-sm"
                                  data-testid={`sales-candidates-${row.jobCode.toLowerCase()}`}
                                  onClick={() =>
                                    navigate(
                                      `/screens/UI-33?jobCode=${encodeURIComponent(row.jobCode)}`,
                                      { state: { jobCode: row.jobCode } },
                                    )
                                  }
                                >
                                  {row.candidateCount}명 보기
                                </button>
                              ) : (
                                <span className="sales-sub">없음</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p className="ls-note">
                    채용 시작 권장 = 부족 시작월 − 2개월(채용·계약에 걸리는
                    기간). 이미 지났으면 &apos;지금&apos;입니다.
                  </p>
                </>
              ) : (
                <p className="sales-sub" data-testid="sales-hiring-empty">
                  앞으로 6개월 동안 부족한 직무가 없습니다.
                </p>
              )}
            </section>
            <FormulaBox
              testId="sales-formula"
              rows={[
                [
                  "진행 중 프로젝트 매출",
                  "월단위·파견: 그 달 투입 인력의 (단가 × MM) 합 · 턴키: (계약금액 − 이미 청구한 공급가) ÷ 다음 달부터 계약 종료까지 남은 개월",
                  "철수일이 지난 사람은 빠짐(연장은 반영하지 않음)",
                ],
                [
                  "영업 기회 매출",
                  "예상 금액 ÷ 예상 기간 × 수주 확률",
                  "시작 예정월부터 예상 기간 동안 나눔",
                ],
                [
                  "인력 수요",
                  "필요 인력 × 월 MM × 수주 확률 + 진행 프로젝트 미충원",
                  "인력 수요 예측과 같은 계산(미충원 = 인원 계획의 계획 MM − 배정 MM을 다음 달부터 종료 달까지 나눈 값, 확률 100%)",
                ],
                [
                  "인력 공급",
                  "투입이 끝나는 사람 + 대기 인원",
                  "연장 가능성: 높음 80% · 보통 50% · 낮음 0% 남는다고 봄",
                ],
              ]}
              foot="수주 확률은 영업 기회의 단계별 기본값이며 직접 고칠 수 있습니다."
            />
          </section>
        ))}
      {selected && options && (
        // 서랍(공통 DetailPanel)·머리(고객사 · 단계 · 확률)·[수정]·[⋯]·탭 3개는 상세 부품이 그린다.
        <SalesOpportunityDetail
          drawer
          testId="sales-pipeline-detail"
          opportunityId={selected}
          options={options}
          onClose={() => setSelected(null)}
          onEdit={(row) => {
            setSelected(null);
            setEditing(row);
          }}
          onChanged={() => refresh()}
          onNotice={(notice) => show([notice])}
        />
      )}
      {notices.map((notice, index) => (
        <Toast
          key={notice.id}
          kind={notice.kind}
          message={notice.message}
          testId={index ? "sales-pipeline-toast-next" : undefined}
          onDismiss={() =>
            setNotices((list) => list.filter((n) => n.id !== notice.id))
          }
        />
      ))}
    </div>
  );
}

/** 시안 listView('UI-29c'): 종료 탭 목록. 열 설정·쪽·정렬은 이 탭을 열 때만 읽는다. */
function ClosedList({
  rows: closed,
  error,
  onRetry,
  resultName,
  onOpen,
}: {
  rows: SalesClosed[] | null;
  error: string;
  onRetry: () => void;
  resultName: (code: string) => string;
  onOpen: (row: SalesClosed) => void;
}) {
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const presentation = useListPresentation();
  // 조건을 바꿔 다시 읽으면 첫 쪽부터 보인다.
  useEffect(() => setPage(1), [closed]);
  const columns = useMemo<ListColumn<SalesClosed>[]>(
    () => [
      { key: "opportunityName", label: "기회명", required: true },
      { key: "customerName", label: "고객사" },
      {
        key: "result",
        label: "결과",
        render: (row) => (
          <Badge kind={row.result === "WIN" ? "ok" : "lock"}>
            {resultName(row.result)}
          </Badge>
        ),
      },
      {
        key: "expectedAmount",
        label: "예상 금액",
        align: "right",
        render: (row) => formatMoney(row.expectedAmount, "원"),
      },
      {
        key: "resultDate",
        label: "종료일",
        render: (row) => row.resultDate || "–",
      },
      {
        key: "projectOrReason",
        label: "프로젝트·실주 사유",
        sortable: false,
        render: (row) =>
          row.result === "WIN" ? (
            row.projectId ? (
              <ProjectLink
                projectId={row.projectId}
                name={row.opportunityName}
                testId={`sales-project-${row.opportunityId}`}
              />
            ) : (
              "–"
            )
          ) : (
            row.lossReason || "–"
          ),
      },
    ],
    [resultName],
  );
  const cols = useListColumns("UI-29", columns);
  const sortedClosed = useMemo(
    () =>
      !closed
        ? null
        : !sort
          ? closed
          : [...closed].sort((a, b) => {
              const x = a[sort.key as keyof SalesClosed],
                y = b[sort.key as keyof SalesClosed];
              const order =
                typeof x === "number" && typeof y === "number"
                  ? x - y
                  : String(x || "").localeCompare(String(y || ""), "ko");
              return sort.dir === "asc" ? order : -order;
            }),
    [closed, sort],
  );
  return (
    <ListView
      screenId="UI-29"
      testId="sales-closed-list"
      columns={columns}
      visibleColumns={cols.visible}
      hiddenColumns={cols.hidden}
      onToggleColumn={cols.toggle}
      onResetColumns={cols.reset}
      rows={
        sortedClosed?.slice(
          (page - 1) * presentation.pageSize,
          page * presentation.pageSize,
        ) ?? null
      }
      mobileRows={sortedClosed}
      rowKey={(row) => row.opportunityId}
      total={closed?.length || 0}
      page={page}
      pageSize={presentation.pageSize}
      onPage={setPage}
      onPageSize={(value) => {
        presentation.setPageSize(value);
        setPage(1);
      }}
      dense={presentation.dense}
      onDense={presentation.setDense}
      sort={sort}
      onSort={setSort}
      onRowClick={onOpen}
      renderCard={(row) => (
        <>
          <div className="ls-r1">
            <strong>{row.opportunityName}</strong>
            <Badge kind={row.result === "WIN" ? "ok" : "lock"}>
              {resultName(row.result)}
            </Badge>
          </div>
          <div className="ls-r2">
            <span>
              {row.customerName} · {row.resultDate || ""}
            </span>
            <b>{kor(Number(row.expectedAmount))}</b>
          </div>
        </>
      )}
      error={error}
      onRetry={onRetry}
    />
  );
}

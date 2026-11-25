import { useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import {
  Banner as SharedBanner,
  Badge,
  KpiCard,
  KpiCards,
  formatKoreanMoney,
  formatMoney,
  formatDecimal,
} from "../../shared/list";
import { Toast } from "../../shared/feedback/Dialog";
import { ShellIcon } from "../../shared/layout/ShellIcon";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import {
  isAllowed,
  latestPeriod,
  type PeriodValue,
} from "../../shared/period/periodRules";
import { koreanToday } from "../../shared/time";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { projectFailure, ProjectRequestError } from "../projects/projects";
import {
  BarChart,
  LineChart,
  MixRows,
  chartColor,
} from "../profitstructures/ProfitStructureCharts";
import { ProjectCostAnalysisDetail } from "../projectcostanalyses/ProjectCostAnalysisDetail";
import {
  getCommonProjectCostAnalyses,
  type CommonProjectCostAnalysisResult,
  type CommonProjectCostAnalysisRow,
} from "../projectcostanalyses/projectCostAnalyses";
import {
  getCommonDashboard,
  getCommonDashboardExcel,
  type Amount,
  type DashboardResult,
} from "./dashboard";
import "../profitstructures/profitStructures.css";
import "./dashboard.css";

const options = { kind: "yearMonth" as const };
const ok = (value: Amount | undefined): value is number =>
  value != null && Number.isFinite(value);
/** 시안 pct(): 소수 한 자리 %(공통 반올림), 음수 「−」, 값 없으면 「–」. */
const pct = (value: Amount | undefined) =>
  ok(value) ? `${formatDecimal(value)}%` : "–";
/** 시안 pp(): 「+1.2%p」·「−0.4%p」. */
const pp = (value: number) =>
  `${value < 0 ? "−" : "+"}${formatDecimal(Math.abs(value))}%p`;
const monthNumber = (month: string) => Number(month.slice(5, 7));
const ym = (value: PeriodValue) =>
  `${value.year}-${String(value.month).padStart(2, "0")}`;

function Icon({ path, size }: { path: string; size: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={path} />
    </svg>
  );
}
/** 시안 ic('warn') · ic('down'). */
const WARN = "M12 3l10 18H2zM12 10v5M12 18h.01";
const DOWN = "M12 3v12M7 10l5 5 5-5M5 21h14";

/** 시안 delta(): 「▲ 3.2% 전월 대비」(좋으면 초록, 나쁘면 빨강). 비교 값이 없으면 null. */
function Delta({ value }: { value: Amount | undefined }) {
  if (!ok(value)) return null;
  return (
    <>
      <span className={value >= 0 ? "dashboard-ok" : "dashboard-bad"}>
        {value >= 0 ? "▲" : "▼"} {formatDecimal(Math.abs(value))}%
      </span>{" "}
      전월 대비
    </>
  );
}
function DetailButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="ls-btn ls-btn-sm ls-btn-t" onClick={onClick}>
      자세히
    </button>
  );
}

export function UI16Page({
  renderActions,
}: { renderActions?: (actions: ReactNode) => ReactNode } = {}) {
  const navigate = useNavigate();
  const period = useScreenPeriod("UI-16", options);
  const [data, setData] = useState<DashboardResult | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [fieldError, setFieldError] = useState("");
  const [retry, setRetry] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const [toast, setToast] = useState<{
    kind: "success" | "error";
    message: string;
  } | null>(null);
  const [detail, setDetail] = useState<{
    row: CommonProjectCostAnalysisRow;
    result: CommonProjectCostAnalysisResult;
    year: number;
    baseMonth: number;
  } | null>(null);
  const [detailBusy, setDetailBusy] = useState(false);
  const detailRequest = useRef<AbortController | null>(null);
  const revision = useRef(0);
  const today = koreanToday();
  const current = {
    year: Number(today.slice(0, 4)),
    month: Number(today.slice(5, 7)),
  };
  const basis = period.state.status === "ready" ? period.state.basis : null;
  const fallback = basis && !isAllowed(current, basis, options);
  // 다른 월 화면처럼 고른 달은 개인 설정(PERIOD)에 남기고 다음에 열 때 그 달로 연다.
  const value =
    period.state.status === "ready"
      ? isAllowed(period.state.value, period.state.basis, options)
        ? period.state.value
        : latestPeriod(period.state.basis, "yearMonth")
      : null;
  const month = value ? ym(value) : "";
  useEffect(() => {
    if (!month) return;
    const controller = new AbortController();
    const sequence = ++revision.current;
    setBusy(true);
    setError("");
    setFieldError("");
    setData(null);
    setDetail(null);
    setDetailBusy(false);
    detailRequest.current?.abort();
    getCommonDashboard(month, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted && sequence === revision.current)
          setData(response);
      })
      .catch((failure) => {
        if (controller.signal.aborted || sequence !== revision.current) return;
        if (failure instanceof ProjectRequestError)
          setFieldError(
            failure.errors.find((e) => e.field === "yearMonth")?.message ?? "",
          );
        setError(projectFailure(failure));
      })
      .finally(() => {
        if (!controller.signal.aborted && sequence === revision.current)
          setBusy(false);
      });
    return () => controller.abort();
  }, [month, retry]);
  useEffect(
    () => () => {
      detailRequest.current?.abort();
    },
    [],
  );
  const go = (screen: string) => navigate(`/screens/${screen}`);
  async function openProject(projectId: string) {
    if (!data) return;
    detailRequest.current?.abort();
    const controller = new AbortController();
    detailRequest.current = controller;
    // 원가 분석은 첫 미마감 달까지만 받으니, 경보를 읽은 달(서버가 준 달)로 연다.
    const year = Number(data.projectAlertMonth.slice(0, 4)),
      baseMonth = Number(data.projectAlertMonth.slice(5, 7));
    setDetailBusy(true);
    try {
      const response = await getCommonProjectCostAnalyses(
        { year, baseMonth, projectId },
        controller.signal,
      );
      if (controller.signal.aborted) return;
      const row = response.items.find((r) => r.projectId === projectId);
      if (!row) throw new Error("프로젝트 원가 상세를 찾지 못했습니다.");
      setDetail({ row, result: response, year, baseMonth });
    } catch (failure) {
      if (!controller.signal.aborted)
        setToast({ kind: "error", message: projectFailure(failure) });
    } finally {
      if (!controller.signal.aborted) setDetailBusy(false);
    }
  }
  async function download() {
    if (!data || busy || downloading) return;
    const targetMonth = data.selectedMonth;
    setDownloading(true);
    try {
      const count = await getCommonDashboardExcel(targetMonth);
      setToast({
        kind: "success",
        message: `엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`,
      });
    } catch (failure) {
      setToast({ kind: "error", message: projectFailure(failure) });
    } finally {
      setDownloading(false);
    }
  }
  const actions = (
    <button
      type="button"
      className="ls-btn"
      data-testid="dashboard-excel"
      disabled={busy || !data || downloading}
      onClick={() => void download()}
    >
      <Icon path={DOWN} size={16} />{" "}
      {downloading ? "만드는 중…" : "엑셀 다운로드"}
    </button>
  );
  return (
    <div
      role="region"
      aria-label="UI-16 본문"
      className="screen-body profit-structures-page dashboard-page"
      data-testid="dashboard-page"
    >
      {renderActions ? (
        renderActions(actions)
      ) : (
        <div className="dashboard-actions">{actions}</div>
      )}
      {basis && value && (
        <PeriodSelector
          kind="yearMonth"
          variant="monthBox"
          basis={basis}
          value={value}
          onChange={(next) => period.setValue(next)}
        />
      )}
      {fallback && (
        <SharedBanner kind="info">
          이번 달은 조회 가능 범위 밖이므로 최근 허용월을 표시합니다.
        </SharedBanner>
      )}
      {fieldError && (
        <p className="profit-field-error" data-testid="dashboard-month-error">
          {fieldError}
        </p>
      )}
      {period.state.status === "error" && (
        <SharedBanner kind="bad">{period.state.message}</SharedBanner>
      )}
      {period.state.status !== "error" && busy && (
        <SharedBanner kind="info">대시보드를 불러오는 중입니다.</SharedBanner>
      )}
      {error && (
        <SharedBanner
          kind="bad"
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="dashboard-retry"
              onClick={() => setRetry((n) => n + 1)}
            >
              다시 시도
            </button>
          }
        >
          {error}
        </SharedBanner>
      )}
      {data && (
        <DashboardBody
          data={data}
          today={today}
          go={go}
          detailBusy={detailBusy}
          openProject={(id) => void openProject(id)}
        />
      )}
      {detail && (
        <div data-testid="dashboard-detail">
          <ProjectCostAnalysisDetail
            row={detail.row}
            period={{ year: detail.year, baseMonth: detail.baseMonth }}
            targetMarginRate={detail.result.targetMarginRate}
            monthlyMarginNotice={detail.result.monthlyMarginNotice}
            onClose={() => setDetail(null)}
          />
        </div>
      )}
      {toast && (
        <Toast
          kind={toast.kind === "success" ? "ok" : "warn"}
          message={toast.message}
          onDismiss={() => setToast(null)}
        />
      )}
    </div>
  );
}

function DashboardBody({
  data,
  today,
  go,
  detailBusy,
  openProject,
}: {
  data: DashboardResult;
  today: string;
  go: (screen: string) => void;
  detailBusy: boolean;
  openProject: (projectId: string) => void;
}) {
  const s = data.summary;
  // 가동률 목표(시안 「목표 90% 대비」). 서버 값이 없으면 시안 기본 90%.
  const target = ok(s.utilizationTargetRate) ? s.utilizationTargetRate : 90;
  const selected = data.selectedMonth;
  const selectedNo = monthNumber(selected);
  const year = selected.slice(0, 4);
  const cur = selected === today.slice(0, 7);
  const ML = cur ? "이번 달" : `${selectedNo}월`;
  // 서버가 시안 규칙(0건 숨김, 철수 줄은 늘 보임)으로 정한 visible 줄만 그린다. 급함 = 빨강(bad) 줄 수.
  const todos = data.todos.filter((t) => t.visible);
  const urgent = todos.filter((t) => t.tone === "bad").length;
  const remaining = data.closing.remainingItems;
  const done = data.closing.completedCount;
  const total = data.closing.totalCount;
  const alerts = data.projectAlerts;
  // 매출·원가 추이: 1~12월, 매출·원가는 고른 달까지, 목표 매출은 12달 모두(시안).
  const months = Array.from(
    { length: 12 },
    (_, k) => `${year}-${String(k + 1).padStart(2, "0")}`,
  );
  const trendBy = new Map(data.trend.map((t) => [t.month, t]));
  const upTo = (m: string) => m <= selected;
  const manpower = data.manpower;
  const gaps = manpower.filter((m) => m.shortage);
  const parts = [...data.salesParts].sort(
    (a, b) => (b.revenue ?? 0) - (a.revenue ?? 0),
  );
  const partTotal = parts.reduce((sum, p) => sum + (p.revenue ?? 0), 0) || 1;
  const pipeline = data.pipeline;
  return (
    <>
      <div className="dashboard-asof" data-testid="dashboard-basis">
        <span className="ls-cap">{data.asOfText}</span>
        {data.provisional && data.provisionalNotice && (
          <span className="ls-cap">
            · <Badge kind="warn">잠정</Badge> {data.provisionalNotice}
          </span>
        )}
        {data.mixedBasisNotice && (
          <span className="ls-cap">· {data.mixedBasisNotice}</span>
        )}
      </div>
      <div className="dashboard-kpis">
        <KpiCards aria-label="대시보드 요약">
          <KpiCard
            label={`${ML} 매출`}
            value={formatKoreanMoney(s.revenue)}
            description={
              <>
                <Delta value={s.revenuePreviousComparison} />
                {ok(s.revenuePreviousComparison) ? " · " : ""}목표{" "}
                {pct(s.revenueTargetRate)}
              </>
            }
            testId="dashboard-revenue"
            onClick={() => go("UI-08")}
          />
          <KpiCard
            label={`${ML} 영업이익`}
            value={formatKoreanMoney(s.operatingProfit)}
            description={
              <>
                이익률 {pct(s.operatingProfitRate)}
                {ok(s.operatingProfitPreviousComparison) ? " · " : ""}
                <Delta value={s.operatingProfitPreviousComparison} />
              </>
            }
            testId="dashboard-profit"
            onClick={() => go("UI-08")}
          />
          <KpiCard
            label="누계 영업이익"
            value={formatKoreanMoney(s.cumulativeOperatingProfit)}
            description={`1~${selectedNo}월 · 목표 달성 ${pct(s.cumulativeAchievementRate)}`}
            testId="dashboard-cumulative"
            onClick={() => go("UI-18")}
          />
          <KpiCard
            label="가동률"
            value={pct(s.utilizationRate)}
            description={`목표 ${Number.isInteger(target) ? target : formatDecimal(target)}% 대비 ${
              ok(s.utilizationRate) ? pp(s.utilizationRate - target) : "–"
            } · 대기 ${ok(s.idlePeople) ? Math.round(s.idlePeople) : "–"}명`}
            testId="dashboard-utilization"
            onClick={() => go("UI-28")}
          />
          <KpiCard
            label={cur ? "미수금" : "미수금 (오늘)"}
            value={formatKoreanMoney(s.unpaidAmount)}
            description={
              s.overdueCount > 0 ? (
                <span className="dashboard-bad">
                  연체 {s.overdueCount}건 {formatKoreanMoney(s.overdueAmount)}
                </span>
              ) : (
                "연체 없음"
              )
            }
            testId="dashboard-unpaid"
            onClick={() => go("UI-13")}
          />
        </KpiCards>
      </div>
      <div className="dashboard-dgrid">
        <section className="profit-card dashboard-todos" data-testid="dashboard-todos">
          <div className="profit-card-h">
            <h2>
              확인할 일{" "}
              <span className="ls-cap dashboard-count" data-testid="dashboard-todo-count">
                {todos.length}건{urgent ? ` · 급함 ${urgent}건` : ""}
              </span>
            </h2>
          </div>
          {todos.map((t, i) => {
            const screen = t.targetScreenId;
            const name = t.targetScreenName;
            return (
              <button
                type="button"
                className="dashboard-witem"
                data-testid={`dashboard-todo-${i}`}
                key={t.key}
                disabled={!screen}
                onClick={() => screen && go(screen)}
              >
                <span className={`dashboard-${t.tone}`} aria-hidden="true">
                  <Icon path={WARN} size={16} />
                </span>
                <span className="dashboard-wt">
                  <strong>{t.title}</strong>
                  <span className="ls-cap">{t.description}</span>
                </span>
                {name && (
                  <span className="ls-cap dashboard-wgo">
                    {name} <ShellIcon name="chevronRight" size={14} />
                  </span>
                )}
              </button>
            );
          })}
        </section>
        <div className="dashboard-side">
          <section className="profit-card dashboard-close" data-testid="dashboard-close">
            <div className="profit-card-h">
              <h2>{selectedNo}월 마감 진행</h2>
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="dashboard-close-go"
                onClick={() => go("UI-14")}
              >
                월 마감
              </button>
            </div>
            <div className="dashboard-bar">
              <div
                className="dashboard-track"
                role="progressbar"
                aria-label="마감 진행"
                aria-valuemin={0}
                aria-valuemax={total}
                aria-valuenow={done}
              >
                <div
                  className="dashboard-fill"
                  style={{ width: `${total ? (done / total) * 100 : 0}%` }}
                />
              </div>
              <span className="profit-tnum" data-testid="dashboard-close-count">
                {done}/{total}
              </span>
            </div>
            {remaining.length ? (
              remaining.map((item, i) => (
                <div
                  className="dashboard-close-item"
                  key={`${item.name}-${i}`}
                  data-testid={`dashboard-close-${i}`}
                >
                  <span className="dashboard-warn">●</span>
                  <span>
                    <strong>{item.name}</strong>{" "}
                    {item.description && (
                      <span className="ls-cap">{item.description}</span>
                    )}
                  </span>
                </div>
              ))
            ) : (
              <p className="dashboard-sub">남은 점검이 없습니다.</p>
            )}
          </section>
          <section className="profit-card dashboard-alerts" data-testid="dashboard-alerts">
            <div className="profit-card-h">
              <h2>
                프로젝트 경보{" "}
                <span className="ls-cap dashboard-count">{alerts.length}건</span>
              </h2>
              <DetailButton onClick={() => go("UI-04")} />
            </div>
            {detailBusy && <p className="dashboard-sub">원가 상세를 불러오는 중입니다.</p>}
            {alerts.length ? (
              alerts.map((a) => {
                return (
                  <button
                    type="button"
                    className="dashboard-witem dashboard-alert"
                    key={a.projectId}
                    data-testid={`dashboard-alert-${a.projectId}`}
                    onClick={() => openProject(a.projectId)}
                  >
                    <span className="dashboard-wt">
                      <strong>{a.projectName}</strong>
                      <span className="ls-cap">
                        {a.marginLabel} {pct(a.marginRate)} · 예산 소진{" "}
                        {pct(a.budgetBurnRate)}
                      </span>
                      {a.mainCause && (
                        <span className="ls-cap">
                          주원인: {a.mainCause}
                          {ok(a.mainCauseAmount)
                            ? ` (${formatKoreanMoney(a.mainCauseAmount)})`
                            : ""}
                        </span>
                      )}
                    </span>
                    <span className="dashboard-badges">
                      {a.alertBadges.map((b) => (
                        <Badge key={b.name} kind={b.tone}>
                          {b.name}
                        </Badge>
                      ))}
                    </span>
                  </button>
                );
              })
            ) : (
              <p className="dashboard-sub">경보가 없습니다.</p>
            )}
          </section>
        </div>
      </div>
      <div className="dashboard-g2">
        <section className="profit-card" data-testid="dashboard-trend">
          <div className="profit-card-h">
            <h2>매출·원가 추이</h2>
            <span className="ls-cap">단위: 백만 원 · {year}년</span>
          </div>
          <LineChart
            labels={months.map((m) => `${monthNumber(m)}월`)}
            series={[
              {
                name: "매출",
                color: chartColor.c1,
                values: months.map((m) =>
                  upTo(m) ? (trendBy.get(m)?.revenue ?? null) : null,
                ),
              },
              {
                name: "원가",
                color: chartColor.c2,
                values: months.map((m) =>
                  upTo(m) ? (trendBy.get(m)?.cost ?? null) : null,
                ),
              },
              {
                name: "목표 매출",
                color: chartColor.c6,
                dash: true,
                values: months.map((m) => trendBy.get(m)?.targetRevenue ?? null),
              },
            ]}
            testId="dashboard-trend-chart"
          />
          <details className="dashboard-tbl">
            <summary>표로 보기</summary>
            <div className="ls-twrap dashboard-twrap">
              <table className="ls-t">
                <thead>
                  <tr>
                    <th>월</th>
                    <th className="ls-right">매출</th>
                    <th className="ls-right">원가</th>
                    <th className="ls-right">목표 매출</th>
                  </tr>
                </thead>
                <tbody>
                  {months.filter(upTo).map((m) => {
                    const t = trendBy.get(m);
                    return (
                      <tr key={m} data-testid={`dashboard-trend-${m}`}>
                        <td>{m}</td>
                        <td className="ls-right">{formatMoney(t?.revenue, "원")}</td>
                        <td className="ls-right">{formatMoney(t?.cost, "원")}</td>
                        <td className="ls-right">
                          {formatMoney(t?.targetRevenue, "원")}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </details>
        </section>
        <section className="profit-card" data-testid="dashboard-supply">
          <div className="profit-card-h">
            <h2>
              앞으로 6개월 인력 수급 <Badge kind="info">예측</Badge>
            </h2>
            <span className="ls-cap">단위: 명(MM)</span>
          </div>
          <BarChart
            labels={manpower.map((m) => `${monthNumber(m.month)}월`)}
            series={[
              {
                name: "가용 인력",
                color: chartColor.c1,
                values: manpower.map((m) => m.supply),
              },
              {
                name: "필요 인력(확률 반영)",
                color: chartColor.c2,
                values: manpower.map((m) => m.demand),
              },
            ]}
            format={{
              tick: (v) => v.toFixed(0),
              value: (v) => `${formatDecimal(v)}명`,
            }}
            testId="dashboard-supply-chart"
          />
          {gaps.length > 0 && (
            <p className="ls-note" data-testid="dashboard-supply-gap">
              <span className="dashboard-bad">부족 예상</span>:{" "}
              {gaps
                .map(
                  (m) =>
                    `${monthNumber(m.month)}월 ${formatDecimal(Math.abs(m.difference))}명`,
                )
                .join(", ")}
            </p>
          )}
          <details className="dashboard-tbl">
            <summary>표로 보기</summary>
            <div className="ls-twrap dashboard-twrap">
              <table className="ls-t">
                <thead>
                  <tr>
                    <th>월</th>
                    <th className="ls-right">가용</th>
                    <th className="ls-right">필요</th>
                    <th className="ls-right">차이</th>
                  </tr>
                </thead>
                <tbody>
                  {manpower.map((m) => (
                    <tr key={m.month} data-testid={`dashboard-manpower-${m.month}`}>
                      <td>{m.month}</td>
                      <td className="ls-right">{formatDecimal(m.supply)}</td>
                      <td className="ls-right">{formatDecimal(m.demand)}</td>
                      <td className="ls-right">{formatDecimal(m.difference)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
          <p className="ls-note">
            가정: 연장 가능성 높음 80%·보통 50%·낮음 0%, 영업 단계 확률
            10/30/60/90%
          </p>
        </section>
      </div>
      <div className="dashboard-grid3">
        <section className="profit-card" data-testid="dashboard-parts">
          <div className="profit-card-h">
            <h2>영업 파트별 {ML}</h2>
            <DetailButton onClick={() => go("UI-07")} />
          </div>
          <MixRows
            rows={parts.map((p) => ({
              name: p.partName,
              revenue: p.revenue,
              share: ((p.revenue ?? 0) / partTotal) * 100,
              profitRate: p.profitRate,
            }))}
            testId="dashboard-part-rows"
          />
        </section>
        <section className="profit-card" data-testid="dashboard-pipeline">
          <div className="profit-card-h">
            <h2>영업 파이프라인</h2>
            <DetailButton onClick={() => go("UI-29")} />
          </div>
          <div className="dashboard-kv2">
            <div>
              <span className="ls-cap">진행 중 기회</span>
              <strong>{pipeline.activeCount}건</strong>
            </div>
            <div>
              <span className="ls-cap">확률 반영 예상 매출</span>
              <strong>{formatKoreanMoney(pipeline.weightedExpectedRevenue)}</strong>
            </div>
          </div>
          {pipeline.upcoming.map((p, i) => (
            <div
              className="dashboard-opp"
              key={p.opportunityId}
              data-testid={`dashboard-upcoming-${i}`}
            >
              <span className="dashboard-opp-name">
                {p.opportunityName}{" "}
                <span className="ls-cap">
                  {p.startMonth.slice(2, 7).replace("-", ".")}월 시작
                </span>
              </span>
              <span className="dashboard-opp-stage">
                {p.stageName && (
                  <Badge kind={ok(p.probability) && p.probability >= 60 ? "ok" : "info"}>
                    {p.stageName}
                  </Badge>
                )}
                {ok(p.probability) ? ` ${Math.round(p.probability)}%` : ""}
              </span>
            </div>
          ))}
        </section>
        <section className="profit-card" data-testid="dashboard-cash">
          <div className="profit-card-h">
            <h2>자금 (앞으로 3개월)</h2>
            <DetailButton onClick={() => go("UI-21")} />
          </div>
          {data.cash.map((c) => (
            <div
              key={c.month}
              className="dashboard-cash-row"
              data-testid={`dashboard-cash-${c.month}`}
            >
              <span>
                {monthNumber(c.month)}월 말 잔액 <span className="ls-cap">예정</span>
              </span>
              <span className={`profit-tnum${c.shortage ? " dashboard-bad" : ""}`}>
                {formatKoreanMoney(c.monthEndBalance)}
                {c.shortage && (
                  <>
                    {" "}
                    <Icon path={WARN} size={14} />
                  </>
                )}
              </span>
            </div>
          ))}
          <p className="ls-note">
            최소 운영자금 {formatKoreanMoney(data.minimumCash)}. 빨간색은 그보다
            적을 것으로 예상되는 달.
          </p>
        </section>
      </div>
    </>
  );
}

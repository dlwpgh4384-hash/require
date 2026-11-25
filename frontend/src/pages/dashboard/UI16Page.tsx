import { useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import {
  Banner as SharedBanner,
  Badge as SharedBadge,
  KpiCard,
  KpiCards,
  formatKoreanMoney,
  formatDecimal,
} from "../../shared/list";
import { Toast } from "../../shared/feedback/Dialog";
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
  LineChart,
  ChartLegend,
  chartColor,
} from "../profitstructures/ProfitStructureCharts";
import { ManpowerForecastBars } from "../manpowerforecasts/ManpowerForecastBars";
import { ProjectCostAnalysisDetail } from "../projectcostanalyses/ProjectCostAnalysisDetail";
import {
  getCommonProjectCostAnalyses,
  type CommonProjectCostAnalysisResult,
  type CommonProjectCostAnalysisRow,
} from "../projectcostanalyses/projectCostAnalyses";
import {
  getCommonDashboard,
  getCommonDashboardExcel,
  type DashboardResult,
} from "./dashboard";
import "../profitstructures/profitStructures.css";
import "./dashboard.css";

const options = { kind: "yearMonth" as const };
function Banner({
  tone,
  children,
}: {
  tone: "danger" | "info";
  children: ReactNode;
}) {
  return (
    <SharedBanner kind={tone === "danger" ? "bad" : "info"}>
      {children}
    </SharedBanner>
  );
}
function Badge({
  tone,
  children,
}: {
  tone: "warn" | "ok" | "bad" | "neutral";
  children: ReactNode;
}) {
  return <SharedBadge kind={tone}>{children}</SharedBadge>;
}
const percent = (value: number | null) =>
  value == null ? "–" : `${formatDecimal(value)}%`;
const ym = (value: PeriodValue) =>
  `${value.year}-${String(value.month).padStart(2, "0")}`;
export function UI16Page({
  renderActions,
}: { renderActions?: (actions: ReactNode) => ReactNode } = {}) {
  const navigate = useNavigate();
  const period = useScreenPeriod("UI-16", options);
  const [selection, setSelection] = useState<PeriodValue | null>(null);
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
  const value = basis
    ? selection && isAllowed(selection, basis, options)
      ? selection
      : isAllowed(current, basis, options)
        ? current
        : latestPeriod(basis, "yearMonth")
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
    const year = Number(data.today.slice(0, 4)),
      baseMonth = Number(data.today.slice(5, 7));
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
      className="ls-btn ls-btn-sm"
      data-testid="dashboard-excel"
      disabled={busy || !data || downloading}
      onClick={() => void download()}
    >
      {downloading ? "엑셀 만드는 중…" : "엑셀 다운로드"}
    </button>
  );
  const s = data?.summary;
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
          onChange={setSelection}
        />
      )}
      {fallback && (
        <Banner tone="info">
          이번 달은 조회 가능 범위 밖이므로 최근 허용월을 표시합니다.
        </Banner>
      )}
      {fieldError && (
        <p className="profit-field-error" data-testid="dashboard-month-error">
          {fieldError}
        </p>
      )}
      {period.state.status === "error" && (
        <Banner tone="danger">{period.state.message}</Banner>
      )}
      {period.state.status !== "error" && busy && (
        <Banner tone="info">대시보드를 불러오는 중입니다.</Banner>
      )}
      {error && (
        <Banner tone="danger">
          {error}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="dashboard-retry"
            onClick={() => setRetry((n) => n + 1)}
          >
            다시 시도
          </button>
        </Banner>
      )}
      {data && s && (
        <>
          <div className="dashboard-basis" data-testid="dashboard-basis">
            기준 {data.selectedMonth} · 오늘 {data.today}{" "}
            <Badge tone={data.provisional ? "warn" : "ok"}>
              {data.closingState}
            </Badge>{" "}
            · 최종 마감 {data.lastClosedMonth ?? "없음"}
            {data.provisional && " · 잠정"}
          </div>
          {data.mixedBasisNotice && (
            <Banner tone="info">{data.mixedBasisNotice}</Banner>
          )}
          <KpiCards aria-label="대시보드 요약" className="dashboard-kpis">
            <KpiCard
              label="이번 달 매출"
              value={formatKoreanMoney(s.revenue)}
              description={`전월 대비 ${percent(s.revenuePreviousComparison)} · 목표 ${percent(s.revenueTargetRate)}`}
              testId="dashboard-revenue"
              onClick={() => go("UI-08")}
            />
            <KpiCard
              label="영업이익"
              value={formatKoreanMoney(s.operatingProfit)}
              description={`이익률 ${percent(s.operatingProfitRate)} · 전월 대비 ${percent(s.operatingProfitPreviousComparison)}`}
              testId="dashboard-profit"
              onClick={() => go("UI-08")}
            />
            <KpiCard
              label="누계 영업이익"
              value={formatKoreanMoney(s.cumulativeOperatingProfit)}
              description={`달성률 ${percent(s.cumulativeAchievementRate)}`}
              testId="dashboard-cumulative"
              onClick={() => go("UI-18")}
            />
            <KpiCard
              label="가동률"
              value={percent(s.utilizationRate)}
              description={`목표 ${percent(s.utilizationTargetRate)} · 대기 ${formatDecimal(s.idlePeople)}명`}
              testId="dashboard-utilization"
              onClick={() => go("UI-03")}
            />
            <KpiCard
              label="미수금"
              value={formatKoreanMoney(s.unpaidAmount)}
              description={`연체 ${s.overdueCount}건 · ${formatKoreanMoney(s.overdueAmount)}`}
              testId="dashboard-unpaid"
              onClick={() => go("UI-13")}
            />
          </KpiCards>
          <div className="dashboard-grid">
            <section className="profit-card">
              <h2>확인할 일</h2>
              <div className="dashboard-todos">
                {data.todos.map((t, i) => (
                  <button
                    type="button"
                    className="dashboard-todo"
                    data-testid={`dashboard-todo-${i}`}
                    key={t.title}
                    disabled={!t.targetScreenId}
                    onClick={() => t.targetScreenId && go(t.targetScreenId)}
                  >
                    <span>
                      {t.title}{" "}
                      <Badge tone={t.urgent ? "bad" : "neutral"}>
                        {t.count}건{t.urgent ? " · 급함" : ""}
                      </Badge>
                    </span>
                    <small>{t.description}</small>
                  </button>
                ))}
              </div>
            </section>
            <section className="profit-card">
              <h2>마감 진행</h2>
              <p data-testid="dashboard-close-count">
                {data.closing.completedCount} / {data.closing.totalCount} 완료
              </p>
              <progress
                max={data.closing.totalCount || 1}
                value={data.closing.completedCount}
                aria-label="마감 진행"
              />
              {data.closing.remainingItems.length ? (
                <ul>
                  {data.closing.remainingItems.map((item, i) => (
                    <li key={item} data-testid={`dashboard-close-${i}`}>
                      {item}
                    </li>
                  ))}
                </ul>
              ) : (
                <p>남은 점검 항목이 없습니다.</p>
              )}
            </section>
            <section className="profit-card dashboard-wide">
              <h2>프로젝트 경보</h2>
              {detailBusy && <p>원가 상세를 불러오는 중입니다.</p>}
              {data.projectAlerts.length ? (
                data.projectAlerts.map((a) => (
                  <button
                    type="button"
                    className="dashboard-todo"
                    key={a.projectId}
                    data-testid={`dashboard-alert-${a.projectId}`}
                    onClick={() => void openProject(a.projectId)}
                  >
                    <span>
                      {a.projectName} <Badge tone="bad">{a.alertName}</Badge>
                    </span>
                    <small>주원인: {a.mainCause ?? "–"}</small>
                  </button>
                ))
              ) : (
                <p>프로젝트 경보가 없습니다.</p>
              )}
            </section>
            <section className="profit-card">
              <h2>매출·원가 추이</h2>
              <div className="dashboard-scroll">
                <LineChart
                  labels={data.trend.map((t) => t.month)}
                  series={[
                    {
                      name: "매출",
                      color: chartColor.c1,
                      values: data.trend.map((t) => t.revenue),
                    },
                    {
                      name: "원가",
                      color: chartColor.c2,
                      values: data.trend.map((t) => t.cost),
                    },
                    {
                      name: "목표 매출",
                      color: chartColor.c6,
                      dash: true,
                      values: data.trend.map((t) => t.targetRevenue),
                    },
                  ]}
                  testId="dashboard-trend-chart"
                />
              </div>
              <ChartLegend
                items={[
                  { name: "매출", color: chartColor.c1 },
                  { name: "원가", color: chartColor.c2 },
                  { name: "목표 매출", color: chartColor.c6, dash: true },
                ]}
              />
              <div className="dashboard-scroll">
                <table className="ls-table">
                  <caption>매출·원가 숫자표</caption>
                  <thead>
                    <tr>
                      <th>월</th>
                      <th>매출</th>
                      <th>원가</th>
                      <th>목표 매출</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.trend.map((t) => (
                      <tr
                        key={t.month}
                        data-testid={`dashboard-trend-${t.month}`}
                      >
                        <th>{t.month}</th>
                        <td>{formatKoreanMoney(t.revenue)}</td>
                        <td>{formatKoreanMoney(t.cost)}</td>
                        <td>{formatKoreanMoney(t.targetRevenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
            <section className="profit-card">
              <h2>앞으로 6개월 인력 수급</h2>
              <div className="dashboard-scroll">
                <ManpowerForecastBars months={data.manpower} />
              </div>
              <div className="dashboard-scroll">
                <table className="ls-table">
                  <caption>회사 전체 인력 수급 숫자표</caption>
                  <thead>
                    <tr>
                      <th>월</th>
                      <th>가용</th>
                      <th>필요</th>
                      <th>차이</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.manpower.map((m) => (
                      <tr
                        key={m.month}
                        data-testid={`dashboard-manpower-${m.month}`}
                        className={m.shortage ? "dashboard-shortage" : ""}
                      >
                        <th>{m.month}</th>
                        <td>{formatDecimal(m.supply)}</td>
                        <td>{formatDecimal(m.demand)}</td>
                        <td>
                          {formatDecimal(m.difference)}
                          {m.shortage && " 부족"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
          <div className="dashboard-bottom">
            <section className="profit-card">
              <h2>영업 파트별 이번 달 실적</h2>
              {data.salesParts.length ? (
                <div className="dashboard-scroll">
                  <table className="ls-table">
                    <thead>
                      <tr>
                        <th>영업 파트</th>
                        <th>매출</th>
                        <th>이익률</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.salesParts.map((p) => (
                        <tr
                          key={p.partId}
                          data-testid={`dashboard-part-${p.partId}`}
                        >
                          <th>{p.partName}</th>
                          <td>{formatKoreanMoney(p.revenue)}</td>
                          <td>{percent(p.profitRate)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p>조회할 영업 파트가 없습니다.</p>
              )}
            </section>
            <section className="profit-card">
              <h2>영업 파이프라인</h2>
              <p>진행 기회 {data.pipeline.activeCount}건</p>
              <p>
                확률 반영 예상 매출{" "}
                {formatKoreanMoney(data.pipeline.weightedExpectedRevenue)}
              </p>
              <h3>곧 시작할 기회</h3>
              {data.pipeline.upcoming.length ? (
                <ul>
                  {data.pipeline.upcoming.map((p, i) => (
                    <li
                      key={`${p.opportunityName}-${p.startMonth}-${i}`}
                      data-testid={`dashboard-upcoming-${i}`}
                    >
                      {p.opportunityName} · {p.startMonth}
                    </li>
                  ))}
                </ul>
              ) : (
                <p>곧 시작할 기회가 없습니다.</p>
              )}
            </section>
            <section className="profit-card">
              <h2>앞으로 3개월 자금 전망</h2>
              {data.cash.map((c) => (
                <div
                  key={c.month}
                  className={c.shortage ? "dashboard-shortage" : ""}
                  data-testid={`dashboard-cash-${c.month}`}
                >
                  <span>
                    {c.month} · {formatKoreanMoney(c.monthEndBalance)}
                  </span>
                  {c.warning && (
                    <p>
                      <Badge tone="bad">자금 부족 예상</Badge> {c.warning}
                    </p>
                  )}
                </div>
              ))}
            </section>
          </div>
        </>
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

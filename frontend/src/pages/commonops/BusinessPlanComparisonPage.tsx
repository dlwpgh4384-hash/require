import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import {
  Banner,
  KpiCard,
  KpiCards,
  MoneyUnitSelect,
  DEFAULT_MONEY_UNIT,
  formatKoreanMoney,
  type MoneyUnit,
} from "../../shared/list";
import { Toast } from "../../shared/feedback/Dialog";
import { useViewport } from "../../shared/layout/useViewport";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { isAllowed } from "../../shared/period/periodRules";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { UploadDialog } from "../uploads/UploadDialog";
import { UploadResultDialog } from "../uploads/UploadResultDialog";
import type { UploadResult } from "../uploads/uploads";
import {
  BarChart,
  chartColor,
} from "../profitstructures/ProfitStructureCharts";
import "../profitstructures/profitStructures.css";
import { ProjectRequestError } from "../projects/projects";
import {
  getCommonBusinessPlanComparisons,
  downloadBusinessPlanTemplate,
  type BusinessPlanComparisonResult,
} from "./businessPlanComparisons";
import {
  BusinessPlanComparisonTable,
  comparisonPercent,
} from "./BusinessPlanComparisonTable";
import { useBusinessPlanComparisonTitleHost } from "./BusinessPlanComparisonWorkspace";
import "./businessPlanComparisons.css";

export const businessPlanPeriodOptions = {
  kind: "yearCumulative",
  firstDataYear: 2026,
  allowAnnualPlanMonths: true,
} as const;

/** UI-18 / SCR-UI-18: M022/G007 본문. 목표·실적은 조회 응답만 표시한다. */
export function BusinessPlanComparisonPage() {
  const period = useScreenPeriod("UI-18", businessPlanPeriodOptions);
  const host = useBusinessPlanComparisonTitleHost();
  const navigate = useNavigate();
  const { mobile } = useViewport();
  const [data, setData] = useState<BusinessPlanComparisonResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [revision, setRevision] = useState(0);
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [upload, setUpload] = useState(false);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: "ok" | "warn" | "info";
  } | null>(null);
  const root = useRef<HTMLElement>(null);
  const state = period.state;
  const filter = useMemo(
    () =>
      state.status === "ready"
        ? {
            year: state.value.year,
            baseMonth: `${state.value.year}${String(state.value.month ?? 12).padStart(2, "0")}`,
          }
        : null,
    [state],
  );
  useEffect(() => {
    if (!filter) return;
    const controller = new AbortController();
    setData(null);
    setLoading(true);
    setError("");
    setFields([]);
    getCommonBusinessPlanComparisons(filter, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) setData(response.data);
      })
      .catch((reason) => {
        if (controller.signal.aborted) return;
        setError(
          reason instanceof Error
            ? reason.message
            : "사업계획 대비 실적을 불러오지 못했습니다.",
        );
        setFields(reason instanceof ProjectRequestError ? reason.errors : []);
        if (
          reason instanceof ProjectRequestError &&
          reason.errors.some((f) => ["year", "baseMonth"].includes(f.field))
        )
          root.current
            ?.querySelector<HTMLSelectElement>('[data-testid="period-month"]')
            ?.focus();
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [filter, revision]);
  const template = async () => {
    if (!filter || downloading) return;
    setDownloading(true);
    try {
      await downloadBusinessPlanTemplate(filter.year);
    } catch (reason) {
      setNotice({
        message:
          reason instanceof Error
            ? reason.message
            : "양식을 내려받지 못했습니다.",
        kind: "warn",
      });
    } finally {
      setDownloading(false);
    }
  };
  const adjust = () => {
    if (!data || state.status !== "ready") return;
    const destination = state.basis.latestYm;
    const value = {
      year: Number(destination.slice(0, 4)),
      month: Number(destination.slice(4)),
    };
    if (
      !isAllowed(value, state.basis, { kind: "yearMonth" }) ||
      state.basis.closedMonths.includes(destination) ||
      destination <= data.baseMonth
    ) {
      setNotice({
        message: "전월 조정을 입력할 수 있는 미마감월이 없습니다.",
        kind: "info",
      });
      return;
    }
    navigate("/screens/UI-10", {
      state: { financeAdjustmentSourceMonth: data.baseMonth },
    });
  };
  const actions = !mobile && (
    <div className="business-plan-actions">
      <button
        type="button"
        className="upload-title-button"
        data-testid="M25-399"
        disabled={!filter || downloading}
        onClick={() => void template()}
      >
        양식 내려받기
      </button>
      <button
        type="button"
        className="upload-title-button"
        data-testid="M25-400"
        disabled={!filter}
        onClick={() => setUpload(true)}
      >
        목표 업로드
      </button>
    </div>
  );
  const summary = data?.summary;
  const monthLabel = data
    ? `${data.baseMonth.slice(0, 4)}-${data.baseMonth.slice(4)}`
    : "";
  return (
    <section
      ref={root}
      className="screen-body business-plan-comparison"
      data-testid="business-plan-comparison-page"
      data-screen-id="UI-18"
      data-action-id="M25-395"
    >
      {host ? createPortal(actions, host) : actions}
      {state.status === "ready" ? (
        <div data-action-id="M25-396">
          <PeriodSelector
            {...businessPlanPeriodOptions}
            variant="cumulativeBar"
            basis={state.basis}
            value={state.value}
            onChange={period.setValue}
          />
        </div>
      ) : (
        <p role={state.status === "error" ? "alert" : "status"}>
          {state.status === "error"
            ? state.message
            : "기간을 불러오는 중입니다."}
        </p>
      )}
      {fields.map((field) => (
        <p role="alert" key={field.field}>
          {field.message}
        </p>
      ))}
      {error && (
        <Banner
          kind="warn"
          icon={null}
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="M25-414"
              onClick={() => setRevision((v) => v + 1)}
            >
              다시 시도
            </button>
          }
        >
          {error}
        </Banner>
      )}
      {loading && (
        <p role="status" data-testid="business-plan-loading">
          자료를 불러오는 중입니다.
        </p>
      )}
      {data && (
        <>
          {data.baseMonthClosed && (
            <Banner
              kind="lock"
              action={
                <button
                  type="button"
                  className="ls-btn ls-btn-sm"
                  data-testid="M25-398"
                  onClick={adjust}
                >
                  전월 조정 입력
                </button>
              }
            >
              {monthLabel}는 마감되었습니다. 고칠 내용은 다음 달 전월 조정으로
              입력하세요.
            </Banner>
          )}
          {!data.hasTargets && (
            <Banner kind="info" icon={null}>
              {data.year}년 사업계획 목표가 아직 없습니다. [목표 업로드]로 올려
              주세요.
            </Banner>
          )}
          <div
            data-testid="business-plan-summary"
            data-base-month={data.baseMonth}
          >
            <KpiCards
              aria-label="요약"
              className="business-plan-kpis"
              testId="business-plan-kpis"
            >
              <KpiCard
                label="누계 매출 달성률"
                value={comparisonPercent(
                  summary?.cumulativeRevenueAchievementRate,
                )}
                testId="business-plan-kpi-revenue"
                description={`실적 ${formatKoreanMoney(summary?.cumulativeRevenueActual)} / 목표 ${formatKoreanMoney(summary?.cumulativeRevenueTarget)}`}
              />
              <KpiCard
                label="누계 영업이익 달성률"
                value={comparisonPercent(
                  summary?.cumulativeOperatingProfitAchievementRate,
                )}
                testId="business-plan-kpi-profit"
                description={`실적 ${formatKoreanMoney(summary?.cumulativeOperatingProfitActual)} / 목표 ${formatKoreanMoney(summary?.cumulativeOperatingProfitTarget)}`}
              />
              <KpiCard
                label={`${monthLabel} 매출 달성률`}
                value={comparisonPercent(
                  summary?.baseMonthRevenueAchievementRate,
                )}
                testId="business-plan-kpi-base"
                description={`목표 ${formatKoreanMoney(summary?.baseMonthRevenueTarget)}`}
              />
            </KpiCards>
          </div>
          <section
            className="business-plan-chart-card"
            aria-label="월별 매출 실적과 목표"
            data-testid="business-plan-chart-card"
          >
            <div className="ls-tbar">
              <strong>월별 매출 실적과 목표</strong>
              <span>단위: 백만 원</span>
            </div>
            <BarChart
              labels={data.months.map(
                (m) => `${Number(m.yearMonth.slice(4))}월`,
              )}
              series={[
                {
                  name: "매출 실적",
                  color: chartColor.c1,
                  values: data.months.map((m) => m.revenueActual),
                },
              ]}
              guide={{
                name: "매출 목표",
                values: data.months.map((m) => m.revenueTarget),
              }}
              testId="business-plan-chart"
            />
          </section>
          <section data-testid="business-plan-months" aria-label="월별 달성률">
            <div className="ls-tbar">
              <strong>월별 달성률</strong>
              <span data-action-id="M25-397">
                <MoneyUnitSelect
                  value={unit}
                  onChange={setUnit}
                  id="business-plan-money-unit"
                />
              </span>
            </div>
            <BusinessPlanComparisonTable
              months={data.months}
              baseMonth={data.baseMonth}
              unit={unit}
            />
          </section>
          {data.months.every(
            (m) => m.revenueActual == null && m.operatingProfitActual == null,
          ) && (
            <p role="status" data-testid="business-plan-empty">
              조회 기간의 실적 자료가 없습니다.
            </p>
          )}
        </>
      )}
      {upload && (
        <UploadDialog
          formTypeCode="B10"
          title="목표 업로드"
          checkLabel="검사"
          intro={
            <p>
              연월 / 매출 목표 / 영업이익 목표. 한 해 1~12월을 올려 주세요. 목표
              빈칸은 0으로 반영합니다.
            </p>
          }
          onClose={() => setUpload(false)}
          onChecked={(value) => {
            setUpload(false);
            setResult(value);
          }}
        />
      )}
      {result && (
        <UploadResultDialog
          uploadId={result.uploadId}
          initialResult={result}
          targetName="목표"
          onClose={() => {
            setResult(null);
            setRevision((v) => v + 1);
          }}
          onApplied={() => {
            setRevision((v) => v + 1);
            setNotice({ message: "사업계획 목표를 반영했습니다.", kind: "ok" });
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice.message}
          kind={notice.kind}
          testId="business-plan-toast"
          onDismiss={() => setNotice(null)}
        />
      )}
    </section>
  );
}

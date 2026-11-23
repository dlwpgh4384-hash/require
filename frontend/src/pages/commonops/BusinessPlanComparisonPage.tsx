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
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import { useViewport } from "../../shared/layout/useViewport";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { isAllowed } from "../../shared/period/periodRules";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { UploadDialog } from "../uploads/UploadDialog";
import { UploadResultDialog } from "../uploads/UploadResultDialog";
import type { ClosedMonthPolicy, UploadResult } from "../uploads/uploads";
import {
  BarChart,
  chartColor,
} from "../profitstructures/ProfitStructureCharts";
import "../profitstructures/profitStructures.css";
import { ProjectRequestError } from "../projects/projects";
import {
  getCommonBusinessPlanComparisons,
  downloadBusinessPlanTemplate,
  CLOSED_CHANGED_REASON,
  EMPTY_TARGET_REASON,
  monthSpan,
  planUploadMessage,
  type BusinessPlanComparisonResult,
} from "./businessPlanComparisons";

/** 시안 ic('info',16)·ic('warn',16) */
const infoIcon = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
  </svg>
);
const warnIcon = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01" />
  </svg>
);
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
  const [confirm, setConfirm] = useState<{
    year: string;
    resolve: (
      value: { closedMonthPolicy?: ClosedMonthPolicy } | false,
    ) => void;
  } | null>(null);
  const [done, setDone] = useState<{ message: string; empty: string[] } | null>(
    null,
  );
  const policy = useRef<ClosedMonthPolicy | undefined>(undefined);
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
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M12 4v11M7 10l5 5 5-5M5 20h14" />
        </svg>
        양식 내려받기
      </button>
      <button
        type="button"
        className="upload-title-button"
        data-testid="M25-400"
        disabled={!filter}
        onClick={() => setUpload(true)}
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M12 20V9M7 14l5-5 5 5M5 4h14" />
        </svg>
        목표 업로드
      </button>
    </div>
  );
  /** 그해 마감한 달(YYYYMM): 월 마감(TB_CL_CLOSE)과 같은 기간 기준. */
  const closedOf = (year: string) =>
    state.status === "ready"
      ? state.basis.closedMonths.filter((m) => m.startsWith(year)).sort()
      : [];
  const beforeApply = (
    checked: UploadResult,
  ): Promise<{ closedMonthPolicy?: ClosedMonthPolicy } | false> => {
    policy.current = undefined;
    const changed = checked.issues.find(
      (i) => i.reason === CLOSED_CHANGED_REASON,
    );
    if (!changed) return Promise.resolve({});
    return new Promise((resolve) =>
      setConfirm({ year: changed.inputValue.slice(0, 4), resolve }),
    );
  };
  const choose = (value: ClosedMonthPolicy | false) => {
    if (!confirm) return;
    policy.current = value || undefined;
    confirm.resolve(value ? { closedMonthPolicy: value } : false);
    setConfirm(null);
  };
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
              <strong>{monthLabel}</strong>는 마감되었습니다. 고칠 내용은 다음
              달 전월 조정으로 입력하세요.
            </Banner>
          )}
          {!data.hasTargets && (
            <Banner kind="warn" icon={infoIcon}>
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
                description={`${formatKoreanMoney(summary?.cumulativeRevenueActual)} / 목표 ${formatKoreanMoney(summary?.cumulativeRevenueTarget)}`}
              />
              <KpiCard
                label="누계 영업이익 달성률"
                value={comparisonPercent(
                  summary?.cumulativeOperatingProfitAchievementRate,
                )}
                testId="business-plan-kpi-profit"
                description={`${formatKoreanMoney(summary?.cumulativeOperatingProfitActual)} / 목표 ${formatKoreanMoney(summary?.cumulativeOperatingProfitTarget)}`}
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
                name: "목표",
                // 목표 0 = 목표 없음: 그 달은 점선을 그리지 않는다.
                values: data.months.map((m) =>
                  m.revenueTarget ? m.revenueTarget : null,
                ),
              }}
              testId="business-plan-chart"
            />
          </section>
          <section
            className="business-plan-months"
            data-testid="business-plan-months"
            aria-label="월별 달성률"
          >
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
          beforeApply={beforeApply}
          onApplied={(applied) => {
            const year = (
              applied.issues.find((i) => i.reason === CLOSED_CHANGED_REASON)
                ?.inputValue ?? ""
            ).slice(0, 4);
            setResult(null);
            setDone({
              message: planUploadMessage(
                policy.current,
                closedOf(year || String(filter?.year ?? "")),
              ),
              empty: applied.issues
                .filter((i) => i.reason === EMPTY_TARGET_REASON)
                .map((i) => i.inputValue),
            });
            setRevision((v) => v + 1);
          }}
        />
      )}
      {confirm && (
        <Dialog
          title="목표 업로드 · 마감한 달 목표가 바뀝니다"
          testId="business-plan-closed-confirm"
          closable
          onClose={() => choose(false)}
        >
          <div className="upload-mb">
            <p>
              올린 파일에서{" "}
              <strong>마감한 달({monthSpan(closedOf(confirm.year))})</strong>의
              목표가 지금 목표와 다릅니다.
            </p>
            <p className="upload-sub">
              마감한 달 목표를 바꾸면 지난 달성률이 달라집니다. 실적은 바뀌지
              않고, 업로드 기록에 남습니다. 마감한 달 목표가 그대로인 파일은 이
              창 없이 바로 올라갑니다.
            </p>
          </div>
          <footer className="upload-mf">
            <span />
            <div className="upload-mf-r">
              <button
                type="button"
                className="ls-btn"
                data-testid="business-plan-closed-cancel"
                onClick={() => choose(false)}
              >
                취소
              </button>
              <button
                type="button"
                className="ls-btn"
                data-testid="business-plan-closed-keep"
                onClick={() => choose("keep")}
              >
                마감한 달은 두기
              </button>
              <button
                type="button"
                className="ls-btn ls-btn-p"
                data-testid="business-plan-closed-replace"
                onClick={() => choose("replace")}
              >
                전체 변경
              </button>
            </div>
          </footer>
        </Dialog>
      )}
      {done && (
        <Dialog
          title="목표 업로드 결과"
          testId="business-plan-upload-done"
          closable
          onClose={() => setDone(null)}
        >
          <div className="upload-mb">
            <p>{done.message}</p>
            {done.empty.length > 0 && (
              <Banner kind="warn" icon={warnIcon}>
                목표가 빈 달:{" "}
                <strong>
                  {done.empty.map((m) => `${Number(m.slice(5))}월`).join(", ")}
                </strong>{" "}
                — 오류 없이 0(목표 없음)으로 올렸습니다. 이 달의 달성률은
                「–」로 보입니다.
              </Banner>
            )}
          </div>
          <footer className="upload-mf">
            <span />
            <div className="upload-mf-r">
              <button
                type="button"
                className="ls-btn ls-btn-p"
                data-testid="business-plan-upload-done-close"
                onClick={() => setDone(null)}
              >
                닫기
              </button>
            </div>
          </footer>
        </Dialog>
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

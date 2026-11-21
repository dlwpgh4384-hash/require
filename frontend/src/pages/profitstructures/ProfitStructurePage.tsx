import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useInRouterContext, useNavigate } from "react-router-dom";
import {
  Banner,
  FormulaBox,
  MoneyUnitSelect,
  DEFAULT_MONEY_UNIT,
  listMessages,
  type MoneyUnit,
} from "../../shared/list";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { ProjectRequestError } from "../projects/projects";
import {
  getCommonProfitStructures,
  getCommonProfitStructuresExcel,
  ProfitStructureRequestError,
  type PeriodUnit,
  type ProfitStructureFilter,
  type ProfitStructureResult,
} from "./profitStructures";
import { profitFormulaFoot, profitFormulaRows } from "./profitStructureFormulas";
import {
  BarChart,
  LineChart,
  MixRows,
  StackChart,
  WaterfallChart,
  chartColor,
} from "./ProfitStructureCharts";
import { ProfitStructureSummary } from "./ProfitStructureSummary";
import { ProfitStructureTable } from "./ProfitStructureTable";
import { useProfitStructureTitleHost } from "./ProfitStructureWorkspace";
import "./profitStructures.css";

const SCREEN = "UI-07";
const options = { kind: "yearCumulative", allowBeforeStart: true } as const;
/** 시안 seg: 월 / 분기 / 연 누계 */
const units: [PeriodUnit, string][] = [
  ["month", "월"],
  ["quarter", "분기"],
  ["year-to-date", "연 누계"],
];
const tableCaption: Record<PeriodUnit, string> = {
  month: "월별",
  quarter: "분기별",
  "year-to-date": "월별 누계",
};
const downloadIcon = (
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
    <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
  </svg>
);
const infoIcon = (
  <svg
    className="ls-banner-icon"
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
    <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
  </svg>
);

/** 다른 화면(영업이익표·순이익표)으로 가는 통로. 라우터 밖(단위 시험)에서는 아무것도 하지 않는다. */
function useOpenScreen() {
  const inRouter = useInRouterContext();
  // 한 화면 안에서 라우터 유무는 바뀌지 않는다.
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const navigate = inRouter ? useNavigate() : null;
  return useCallback(
    (id: string) => navigate?.(`/screens/${encodeURIComponent(id)}`),
    [navigate],
  );
}
function ScreenLink({
  id,
  children,
  open,
}: {
  id: string;
  children: ReactNode;
  open: (id: string) => void;
}) {
  return (
    <a
      href={`/screens/${id}`}
      className="profit-link"
      data-testid={`profit-link-${id}`}
      onClick={(event) => {
        event.preventDefault();
        open(id);
      }}
    >
      {children}
    </a>
  );
}
function Card({
  title,
  cap,
  right,
  children,
  testId,
}: {
  title: string;
  cap?: ReactNode;
  right?: ReactNode;
  children?: ReactNode;
  testId?: string;
}) {
  return (
    <section className="profit-card" aria-label={title} data-testid={testId}>
      <div className="profit-card-h">
        <h2>{title}</h2>
        {cap && <span className="ls-cap">{cap}</span>}
        {right && <div className="profit-card-right">{right}</div>}
      </div>
      {children}
    </section>
  );
}

/** 재무·자금 › 전체 수익 구조(UI-07, SFR-107). 조회만 하는 화면이다. */
export function ProfitStructurePage() {
  const period = useScreenPeriod(SCREEN, options);
  const host = useProfitStructureTitleHost();
  const openScreen = useOpenScreen();
  const [periodUnit, setPeriodUnit] = useState<PeriodUnit>("month");
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [response, setResponse] = useState<{
    data: ProfitStructureResult;
    previousPeriodAvailable: boolean;
  } | null>(null);
  const [loading, setLoading] = useState(false),
    [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>([]);
  const [revision, setRevision] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: ToastKind;
    id: number;
  } | null>(null);
  const root = useRef<HTMLElement>(null),
    generation = useRef(0);
  const state = period.state;
  const filter = useMemo<ProfitStructureFilter | null>(
    () =>
      state.status === "ready"
        ? {
            year: state.value.year,
            periodUnit,
            baseMonth: `${state.value.year}${String(state.value.month).padStart(2, "0")}`,
          }
        : null,
    [state, periodUnit],
  );
  const notify = (message: string, kind: ToastKind = "ok") =>
    setNotice((old) => ({ message, kind, id: (old?.id ?? 0) + 1 }));
  useEffect(() => {
    if (!filter) return;
    const controller = new AbortController();
    setResponse(null);
    setLoading(true);
    setError("");
    setFields([]);
    generation.current++;
    getCommonProfitStructures(filter, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted)
          setResponse({
            data: result.data,
            previousPeriodAvailable: result.meta?.previousPeriodAvailable !== false,
          });
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        setError(
          failure instanceof ProfitStructureRequestError && failure.status === 403
            ? "이 화면을 조회할 권한이 없습니다."
            : failure instanceof ProjectRequestError
              ? failure.message
              : "전체 수익 구조를 불러오지 못했습니다. 다시 시도해 주세요.",
        );
        const errors = failure instanceof ProjectRequestError ? failure.errors : [];
        setFields(errors);
        const field = errors[0]?.field;
        if (field === "baseMonth" || field === "year")
          root.current
            ?.querySelector<HTMLSelectElement>('[data-testid="period-month"]')
            ?.focus();
        if (field === "periodUnit")
          root.current
            ?.querySelector<HTMLButtonElement>('[role="tab"][aria-selected="true"]')
            ?.focus();
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [filter, revision]);
  const data = response?.data;
  const trends = data?.trends ?? [];
  const labels = trends.map((trend) => trend.period);
  const series = (key: keyof (typeof trends)[number]) =>
    trends.map((trend) => trend[key] as number | null);
  const table = data?.numericTable ?? { columns: [], rows: [] };
  const periodName = data?.periodName ?? "";
  const month = state.status === "ready" ? (state.value.month ?? 12) : 12;
  const quarterMonths = ((month - 1) % 3) + 1;
  const partialQuarter = periodUnit === "quarter" && quarterMonths < 3 && labels.length > 0;
  const year = state.status === "ready" ? state.value.year : 0;

  const download = async () => {
    if (!filter || !data || loading || downloading || error) return;
    const current = generation.current;
    setDownloading(true);
    try {
      await getCommonProfitStructuresExcel(filter);
      if (current === generation.current) notify("엑셀 파일을 내려받았습니다.");
    } catch (failure) {
      if (current !== generation.current) return;
      notify(
        failure instanceof ProjectRequestError
          ? failure.message
          : listMessages.downloadError,
        "warn",
      );
    } finally {
      setDownloading(false);
    }
  };
  const titleActions = (
    <button
      type="button"
      className="ls-btn upload-title-button"
      data-testid="profit-excel"
      disabled={downloading || loading || !data || !!error}
      onClick={() => void download()}
    >
      {downloadIcon} 엑셀 다운로드
    </button>
  );
  const range = {
    "data-period-start": data?.periodStart,
    "data-period-end": data?.periodEnd,
  };
  return (
    <section
      className="screen-body profit-structures-page"
      data-testid="profit-structures-page"
      data-screen-id={SCREEN}
      ref={root}
    >
      {host ? createPortal(titleActions, host) : titleActions}
      {state.status === "ready" ? (
        <PeriodSelector
          {...options}
          variant="cumulativeBar"
          basis={state.basis}
          value={state.value}
          onChange={period.setValue}
        />
      ) : (
        <p role={state.status === "error" ? "alert" : "status"} className="ls-note">
          {state.status === "error" ? state.message : "기간을 불러오는 중입니다."}
        </p>
      )}
      {fields.map((field) => (
        <p
          className="profit-field-error"
          role="alert"
          key={field.field}
          data-testid={`profit-error-${field.field}`}
        >
          {field.message}
        </p>
      ))}
      <div className="ls-tabs profit-tabs" role="tablist" aria-label="기간 단위">
        {units.map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={`ls-tb${value === periodUnit ? " ls-on" : ""}`}
            role="tab"
            aria-selected={value === periodUnit}
            data-testid={`profit-unit-${value}`}
            onClick={() => setPeriodUnit(value)}
          >
            {label}
          </button>
        ))}
      </div>
      {error && (
        <Banner
          kind="warn"
          icon={infoIcon}
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="profit-retry"
              onClick={() => setRevision((value) => value + 1)}
            >
              다시 시도
            </button>
          }
        >
          {error}
        </Banner>
      )}
      {data?.provisionalNotice && (
        <Banner kind="warn" icon={infoIcon}>
          {data.provisionalNotice}
        </Banner>
      )}
      <div data-testid="profit-summary" {...range}>
        <ProfitStructureSummary
          data={data}
          previousAvailable={response?.previousPeriodAvailable ?? false}
          idleCost={trends.length ? trends[trends.length - 1].idleCost : null}
          onOpenOperatingProfit={() => openScreen("UI-08")}
        />
      </div>
      {data && (
        <>
          <Card
            title="수익 구조 — 매출에서 순이익까지"
            cap={`${periodName} · 단위: 백만 원`}
            testId="profit-waterfall-card"
          >
            <WaterfallChart waterfall={data.waterfall} />
          </Card>
          <div className="profit-grid2">
            <Card title="매출·원가 추이" cap="단위: 백만 원" testId="profit-trend-card">
              {periodUnit === "quarter" ? (
                <BarChart
                  testId="profit-trend-chart"
                  labels={labels}
                  series={[
                    { name: "매출", color: chartColor.c1, values: series("revenue") },
                    { name: "매출원가", color: chartColor.c2, values: series("costOfSales") },
                  ]}
                  guide={
                    data.referenceRevenueVisible
                      ? { name: "배분 기준 매출(참고)", values: series("referenceRevenue") }
                      : null
                  }
                />
              ) : (
                <LineChart
                  testId="profit-trend-chart"
                  labels={labels}
                  series={[
                    { name: "매출", color: chartColor.c1, values: series("revenue") },
                    { name: "매출원가", color: chartColor.c2, values: series("costOfSales") },
                    ...(data.referenceRevenueVisible
                      ? [
                          {
                            name: "배분 기준 매출(참고)",
                            color: chartColor.c6,
                            dash: true,
                            values: series("referenceRevenue"),
                          },
                        ]
                      : []),
                  ]}
                />
              )}
              {partialQuarter && (
                <p className="ls-note" data-testid="profit-partial-quarter">
                  {labels[labels.length - 1]}는 진행 중이라 {quarterMonths}개월만 더한 값입니다.
                </p>
              )}
            </Card>
            <Card title="이익 추이" cap="단위: 백만 원" testId="profit-profit-card">
              <BarChart
                testId="profit-profit-chart"
                labels={labels}
                series={[
                  { name: "매출총이익", color: chartColor.c3, values: series("grossProfit") },
                  { name: "영업이익", color: chartColor.c1, values: series("operatingProfit") },
                ]}
              />
            </Card>
          </div>
          <Card
            title="원가 구성"
            cap="배분 원가 · 대기 원가 · 직접 경비 · 단위: 백만 원"
            testId="profit-cost-card"
          >
            <StackChart
              testId="profit-cost-chart"
              labels={labels}
              series={[
                { name: "배분 원가", color: chartColor.c1, values: series("allocatedCost") },
                { name: "대기 원가", color: chartColor.c4, values: series("idleCost") },
                { name: "직접 경비", color: chartColor.c2, values: series("directExpense") },
              ]}
            />
          </Card>
          {data.compositionVisible && data.composition ? (
            <div className="profit-grid3" data-testid="profit-compositions">
              <Card title="계약 유형별" cap={periodName}>
                <MixRows rows={data.composition.contractTypes ?? []} testId="profit-mix-contract" />
              </Card>
              <Card title="고객사 상위 5곳" cap={periodName}>
                <MixRows rows={data.composition.topClients ?? []} testId="profit-mix-client" />
              </Card>
              <Card title="영업 파트별" cap="프로젝트를 따온 파트 기준">
                <MixRows rows={data.composition.projectSalesParts ?? []} testId="profit-mix-part" />
                <p className="ls-note">
                  영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다.
                </p>
              </Card>
            </div>
          ) : (
            <Card title="매출 구성" testId="profit-composition-hidden">
              <p className="profit-sub">
                {year}년은 월별 손익만 옮겨 와서 계약 유형·고객사·영업 파트별 구성은 볼 수 없습니다.
              </p>
            </Card>
          )}
        </>
      )}
      <Card
        title="숫자로 보기"
        cap={`${tableCaption[periodUnit]} · 단위 ${unit}`}
        right={<MoneyUnitSelect value={unit} onChange={setUnit} id="profit-money-unit" />}
        testId="profit-numeric-panel"
      >
        {data && table.columns.length > 0 ? (
          <ProfitStructureTable table={table} unit={unit} />
        ) : (
          !error && (
            <div
              className="ls-empty"
              role="status"
              data-testid={loading || !data ? "profit-table-loading" : "profit-table-empty"}
            >
              <p>{loading || !data ? listMessages.loading : listMessages.empty}</p>
            </div>
          )
        )}
        <p className="ls-note" data-testid="profit-numeric-note">
          매출 구성(계약 유형·고객사·영업 파트)은 각 기간의 실제 매출입니다. 엑셀 다운로드는 이 표와
          같습니다. 자세한 항목은{" "}
          <ScreenLink id="UI-08" open={openScreen}>
            영업이익표
          </ScreenLink>
          ·
          <ScreenLink id="UI-09" open={openScreen}>
            순이익표
          </ScreenLink>
          에서 봅니다. 같은 계산이라 숫자가 같습니다.
        </p>
      </Card>
      <FormulaBox rows={profitFormulaRows} foot={profitFormulaFoot} testId="profit-formulas" />
      {notice && (
        <Toast
          key={notice.id}
          message={notice.message}
          kind={notice.kind}
          onDismiss={() => setNotice(null)}
          testId="profit-toast"
        />
      )}
    </section>
  );
}

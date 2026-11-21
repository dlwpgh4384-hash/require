import { useEffect, useMemo, useRef, useState } from "react";
import {
  Banner,
  Badge,
  FormulaBox,
  KpiCard,
  KpiCards,
  ListView,
  Money,
  MoneyUnitSelect,
  DEFAULT_MONEY_UNIT,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type MoneyUnit,
} from "../../shared/list";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { YearBar } from "../../shared/period/YearBar";
import { useViewport } from "../../shared/layout/useViewport";
import { withYear, yearRange } from "../../shared/period/periodRules";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { ProjectRequestError } from "../projects/projects";
import {
  getCommonProfitStructures,
  getCommonProfitStructuresExcel,
  ProfitStructureRequestError,
  type Amount,
  type PeriodUnit,
  type ProfitPeriodRow,
  type ProfitStructureFilter,
  type ProfitStructureResult,
  type ProfitSummary,
} from "./profitStructures";
import {
  compositionKeys,
  numericValue,
  percentage,
  profitFormulaRows,
  profitLabels,
  type ProfitColumn,
} from "./profitStructureFormulas";
import {
  ProfitCompositionTable,
  ProfitStructureCharts,
} from "./ProfitStructureCharts";
import "./profitStructures.css";

const options = { kind: "yearCumulative", allowBeforeStart: true } as const;
const units: [PeriodUnit, string][] = [
  ["month", "월"],
  ["quarter", "분기"],
  ["year-to-date", "연 누계"],
];
const percentKeys = new Set([
  "grossProfitRate",
  "operatingProfitRate",
  "idleCostShare",
]);
export function ProfitStructurePage() {
  const { mobile } = useViewport();
  const period = useScreenPeriod("UI-07", options);
  const [periodUnit, setPeriodUnit] = useState<PeriodUnit>("month");
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [response, setResponse] = useState<{
    data: ProfitStructureResult;
    previousPeriodAvailable: boolean;
  } | null>(null);
  const [loading, setLoading] = useState(false),
    [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [revision, setRevision] = useState(0),
    [page, setPage] = useState(1);
  const [downloading, setDownloading] = useState(false),
    [downloadMessage, setDownloadMessage] = useState("");
  const [downloadError, setDownloadError] = useState(false);
  const root = useRef<HTMLDivElement>(null),
    downloadGeneration = useRef(0);
  const presentation = useListPresentation();
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
  useEffect(() => {
    if (!filter) return;
    const controller = new AbortController();
    setResponse(null);
    setLoading(true);
    setError("");
    setFields([]);
    setPage(1);
    setDownloadMessage("");
    downloadGeneration.current++;
    getCommonProfitStructures(filter, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted)
          setResponse({
            data: result.data,
            previousPeriodAvailable:
              result.meta.previousPeriodAvailable === true,
          });
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        setError(
          failure instanceof ProfitStructureRequestError &&
            failure.status === 403
            ? "이 화면을 조회할 권한이 없습니다."
            : failure instanceof ProjectRequestError
              ? failure.message
              : "전체 수익 구조를 불러오지 못했습니다. 다시 시도해 주세요.",
        );
        const errors =
          failure instanceof ProjectRequestError ? failure.errors : [];
        setFields(errors);
        const field = errors[0]?.field;
        if (field === "baseMonth" || field === "year")
          root.current?.querySelector<HTMLSelectElement>("select")?.focus();
        if (field === "periodUnit")
          root.current
            ?.querySelector<HTMLButtonElement>(
              '[data-testid="profit-unit-month"]',
            )
            ?.focus();
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [filter, revision]);
  const data = response?.data;
  const cell = (row: ProfitPeriodRow, key: ProfitColumn) => {
    if (compositionKeys.includes(key as (typeof compositionKeys)[number])) {
      const values = row.composition?.[key as (typeof compositionKeys)[number]];
      return values ? (
        <ProfitCompositionTable
          rows={values}
          unit={unit}
          id={`profit-numeric-${key}-${row.period}`}
        />
      ) : (
        "–"
      );
    }
    const value = numericValue(row, key);
    return key === "period" ? (
      value
    ) : percentKeys.has(key) ? (
      percentage(value as Amount)
    ) : (
      <Money value={value as Amount} unit={unit} />
    );
  };
  const columns: ListColumn<ProfitPeriodRow>[] = (
    Object.keys(profitLabels) as ProfitColumn[]
  )
    .filter(
      (key) =>
        !(
          data &&
          !data.compositionVisible &&
          compositionKeys.includes(key as (typeof compositionKeys)[number])
        ) &&
        !(data && !data.referenceRevenueVisible && key === "referenceRevenue"),
    )
    .map((key) => ({
      key,
      label: profitLabels[key],
      required: key === "period",
      sortable: false,
      align: key === "period" ? "left" : "right",
      render: (row) => cell(row, key),
    }));
  const cols = useListColumns("UI-07", columns);
  const rows = data?.numericRows ?? [];
  const download = async () => {
    if (!filter || !data || loading || downloading || error) return;
    const generation = downloadGeneration.current;
    setDownloading(true);
    setDownloadMessage("");
    setDownloadError(false);
    try {
      await getCommonProfitStructuresExcel(
        filter,
        cols.visible.map((column) => column.key),
      );
      if (generation === downloadGeneration.current)
        setDownloadMessage("엑셀 파일을 내려받았습니다.");
    } catch (failure) {
      if (generation !== downloadGeneration.current) return;
      setDownloadError(true);
      setDownloadMessage(
        failure instanceof ProjectRequestError
          ? failure.message
          : "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.",
      );
      if (failure instanceof ProjectRequestError) setFields(failure.errors);
    } finally {
      setDownloading(false);
    }
  };
  const comparison = (key: keyof ProfitSummary, rate = false) => {
    if (!data || !response) return null;
    const current = data.summary[key];
    const delta = (previous: ProfitSummary) => {
      const value = previous[key];
      if (current == null || value == null) return "–";
      const difference = current - value;
      return rate ? (
        `${difference > 0 ? "+" : ""}${difference.toFixed(1)}%p`
      ) : (
        <>
          {difference > 0 ? "+" : ""}
          <Money value={difference} unit={unit} />
        </>
      );
    };
    return (
      <>
        {response.previousPeriodAvailable && (
          <span>직전 기간 {delta(data.comparison.previousPeriod)}</span>
        )}
        {data.comparison.previousYearVisible &&
          data.comparison.previousYear && (
            <span>전년 같은 기간 {delta(data.comparison.previousYear)}</span>
          )}
      </>
    );
  };
  const range = {
    "data-period-start": data?.periodStart,
    "data-period-end": data?.periodEnd,
  };
  return (
    <div
      className="profit-structures-page"
      data-testid="profit-structures-page"
      data-screen-id="UI-07"
      ref={root}
    >
      {state.status === "ready" ? (
        <>
          <YearBar
            year={state.value.year}
            currentYear={Number(state.basis.latestYm.slice(0, 4))}
            {...yearRange(state.basis, options)}
            caption="전체 수익 구조 · 조회 전용"
            onChange={(year) =>
              period.setValue(withYear(state.value, year, state.basis, options))
            }
          />
          <div
            className="profit-period-controls"
            data-testid="profit-period-controls"
          >
            <PeriodSelector
              {...options}
              basis={state.basis}
              value={state.value}
              onChange={period.setValue}
              description="선택한 기준월까지 같은 기간으로 계산합니다."
            />
            <div className="ls-tabs" role="tablist" aria-label="집계 기간">
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
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="profit-search"
              disabled={loading}
              onClick={() => setRevision((value) => value + 1)}
            >
              조회
            </button>
          </div>
        </>
      ) : (
        <Banner kind={state.status === "error" ? "warn" : "info"}>
          {state.status === "error"
            ? state.message
            : "기간을 불러오는 중입니다."}
        </Banner>
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
      {loading && (
        <p role="status" data-testid="profit-loading">
          전체 수익 구조를 불러오는 중입니다.
        </p>
      )}
      {error && <Banner kind="warn">{error}</Banner>}
      {data?.provisionalNotice && (
        <Banner kind="warn">{data.provisionalNotice}</Banner>
      )}
      <section
        aria-label="수익 구조 요약"
        data-testid="profit-summary"
        {...range}
      >
        {data?.provisional && <Badge kind="warn">잠정</Badge>}
        <KpiCards aria-label="요약 네 칸">
          <KpiCard
            label="매출"
            value={<Money value={data?.summary.revenue} unit={unit} />}
            description={comparison("revenue")}
          />
          <KpiCard
            label="매출총이익(률)"
            value={
              <>
                <Money value={data?.summary.grossProfit} unit={unit} /> (
                {percentage(data?.summary.grossProfitRate)})
              </>
            }
            description={
              <>
                {comparison("grossProfit")}
                {comparison("grossProfitRate", true)}
              </>
            }
          />
          <KpiCard
            label="영업이익(률)"
            value={
              <>
                <Money value={data?.summary.operatingProfit} unit={unit} /> (
                {percentage(data?.summary.operatingProfitRate)})
              </>
            }
            description={
              <>
                {comparison("operatingProfit")}
                {comparison("operatingProfitRate", true)}
              </>
            }
          />
          <KpiCard
            label="대기 원가 비중"
            value={percentage(data?.summary.idleCostShare)}
            description={comparison("idleCostShare", true)}
          />
        </KpiCards>
      </section>
      {data ? (
        <ProfitStructureCharts data={data} unit={unit} />
      ) : (
        <div className="profit-chart-grid" aria-label="차트 골격">
          {[
            "매출부터 당기순이익까지",
            "매출·원가 추이",
            "이익 추이",
            "원가 구성",
            "매출 구성",
          ].map((label) => (
            <section className="profit-panel" key={label}>
              <h3>{label}</h3>
              <p>
                {error
                  ? "조회하지 못했습니다."
                  : loading
                    ? "불러오는 중입니다."
                    : "자료가 없습니다."}
              </p>
            </section>
          ))}
        </div>
      )}
      <section
        aria-label="숫자로 보기"
        data-testid="profit-numeric-panel"
        {...range}
      >
        <h3>
          숫자로 보기 {data?.provisional && <Badge kind="warn">잠정</Badge>}
        </h3>
        {mobile && (
          <div
            className="profit-mobile-toolbar"
            data-testid="profit-mobile-toolbar"
          >
            <MoneyUnitSelect
              value={unit}
              onChange={setUnit}
              id="profit-mobile-money-unit"
            />
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="profit-mobile-download"
              disabled={downloading || loading || !data || !!error}
              onClick={download}
            >
              엑셀 다운로드
            </button>
          </div>
        )}
        <ListView
          screenId="UI-07"
          columns={columns}
          visibleColumns={cols.visible}
          hiddenColumns={cols.hidden}
          onToggleColumn={cols.toggle}
          onResetColumns={cols.reset}
          rows={
            loading
              ? null
              : rows.slice(
                  (page - 1) * presentation.pageSize,
                  page * presentation.pageSize,
                )
          }
          mobileRows={loading ? null : rows}
          rowKey={(row) => row.period}
          total={rows.length}
          page={page}
          pageSize={presentation.pageSize}
          onPage={setPage}
          onPageSize={(size) => {
            presentation.setPageSize(size);
            setPage(1);
          }}
          dense={presentation.dense}
          onDense={presentation.setDense}
          sort={null}
          onSort={() => undefined}
          selectable={false}
          onDownload={download}
          downloading={downloading || loading || !data || !!error}
          error={error || undefined}
          onRetry={() => setRevision((v) => v + 1)}
          toolbarRight={
            <MoneyUnitSelect
              value={unit}
              onChange={setUnit}
              id="profit-money-unit"
            />
          }
          renderCard={(row) => (
            <dl>
              {cols.visible.map((column) => (
                <div key={column.key}>
                  <dt>{column.label}</dt>
                  <dd>{cell(row, column.key as ProfitColumn)}</dd>
                </div>
              ))}
            </dl>
          )}
        />
        {error && (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="profit-retry"
            onClick={() => setRevision((v) => v + 1)}
          >
            다시 시도
          </button>
        )}
        {downloadMessage && (
          <p
            role={downloadError ? "alert" : "status"}
            data-testid="profit-download-message"
          >
            {downloadMessage}
          </p>
        )}
      </section>
      <FormulaBox
        rows={profitFormulaRows}
        foot="조회는 업무 손익을 저장하지 않습니다. 모든 금액·비율은 서버 계산 결과입니다."
      />
    </div>
  );
}

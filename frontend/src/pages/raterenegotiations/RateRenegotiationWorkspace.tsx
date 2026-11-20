import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Badge,
  Banner,
  FormulaBox,
  KpiCard,
  KpiCards,
  ListFilter,
  ListFilters,
  ListView,
  Money,
  MoneyUnitSelect,
  DEFAULT_MONEY_UNIT,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type MoneyUnit,
} from "../../shared/list";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { ProjectRequestError } from "../projects/projects";
import { Toast } from "../../shared/feedback/Dialog";
import {
  getCommonRateRenegotiations,
  getCommonRateRenegotiationsExcel,
  getRateRenegotiationOptions,
  rateRenegotiationDefaults,
  rateRenegotiationColumns,
  type RateRenegotiationColumn,
  type RateRenegotiationFilter,
  type RateRenegotiationResult,
  type RateRenegotiationRow,
} from "./rateRenegotiations";
import "./rateRenegotiations.css";

type HelpKey = keyof RateRenegotiationResult["formulaHelp"];
const helpKeys = new Set<string>([
  "renewalDate",
  "monthlyCost",
  "profitRate",
  "targetUnitPrice",
  "requiredIncreaseRate",
  "annualProfitIncrease",
]);
const moneyKeys = new Set<string>([
  "currentUnitPrice",
  "monthlyCost",
  "targetUnitPrice",
  "annualProfitIncrease",
]);
const kebab = (key: string) =>
  key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
const percent = (value: number | null | undefined) =>
  value == null ? "–" : `${value.toFixed(1)}%`;
const message = (reason: unknown) =>
  reason instanceof ProjectRequestError
    ? reason.message
    : "목록을 불러오지 못했습니다. 다시 시도해 주세요.";

// UI-32 / M017 / G006: no period selector, business save or negotiation state.
export function RateRenegotiationWorkspace() {
  const [draft, setDraft] = useState(rateRenegotiationDefaults);
  const [applied, setApplied] = useState(rateRenegotiationDefaults);
  const [data, setData] = useState<RateRenegotiationResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [revision, setRevision] = useState(0);
  const [options, setOptions] = useState<Awaited<
    ReturnType<typeof getRateRenegotiationOptions>
  > | null>(null);
  const [optionError, setOptionError] = useState(false);
  const [optionRevision, setOptionRevision] = useState(0);
  const [detail, setDetail] = useState<string | null>(null);
  const [help, setHelp] = useState<HelpKey | null>(null);
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [page, setPage] = useState(1);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState("");
  const root = useRef<HTMLDivElement>(null);
  const presentation = useListPresentation();

  const showFields = (reason: unknown) => {
    const entries = reason instanceof ProjectRequestError ? reason.errors : [];
    setFields(
      Object.fromEntries(
        entries.map((e) => [
          e.field,
          /[.]$/.test(e.message) ? e.message : `${e.message}.`,
        ]),
      ),
    );
    if (entries[0])
      Array.from(root.current?.querySelectorAll<HTMLElement>("[name]") ?? [])
        .find((element) => element.getAttribute("name") === entries[0].field)
        ?.focus();
  };
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError("");
    setFields({});
    getCommonRateRenegotiations(applied, controller.signal)
      .then((result) => {
        if (!active) return;
        setData(result);
        setPage(1);
      })
      .catch((reason) => {
        if (!active) return;
        setError(message(reason));
        showFields(reason);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [applied, revision]);
  useEffect(() => {
    const controller = new AbortController();
    setOptionError(false);
    getRateRenegotiationOptions(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setOptions(value);
      })
      .catch(() => {
        if (!controller.signal.aborted) setOptionError(true);
      });
    return () => controller.abort();
  }, [optionRevision]);
  const fieldError = (name: string) =>
    fields[name] ? (
      <span className="rate-field-error" id={`rate-${name}-error`}>
        {fields[name]}
      </span>
    ) : null;
  const attributes = (name: keyof RateRenegotiationFilter) => ({
    id: `rate-${name}`,
    name,
    "data-testid": `rate-${kebab(name)}`,
    "aria-invalid": !!fields[name],
    "aria-describedby": fields[name] ? `rate-${name}-error` : undefined,
  });
  const change = (key: keyof RateRenegotiationFilter, value: string | number) =>
    setDraft((previous) => ({ ...previous, [key]: value }));
  const search = () => {
    setApplied({ ...draft });
    setRevision((v) => v + 1);
  };
  const reset = () => {
    const next = rateRenegotiationDefaults();
    setDraft(next);
    setApplied(next);
    setRevision((v) => v + 1);
  };
  const assumption = (text: string) => {
    const value = Number(text);
    if (!Number.isFinite(value)) return;
    const increaseRate = Math.max(0, Math.min(50, value));
    setDraft((previous) => ({ ...previous, increaseRate }));
    setApplied((previous) => ({ ...previous, increaseRate }));
  };
  const cell = (
    row: RateRenegotiationRow,
    key: RateRenegotiationColumn,
  ): ReactNode => {
    if (key === "renewalDate")
      return (
        <>
          {row.renewalDate}{" "}
          <Badge
            kind={
              row.daysUntilRenewal <= 30
                ? "bad"
                : row.daysUntilRenewal <= 92
                  ? "warn"
                  : "neutral"
            }
          >
            D-{row.daysUntilRenewal}
          </Badge>
        </>
      );
    if (moneyKeys.has(key))
      return <Money value={row[key] as number} unit={unit} />;
    if (key === "profitRate" || key === "requiredIncreaseRate")
      return percent(row[key]);
    return row[key];
  };
  const columns: ListColumn<RateRenegotiationRow>[] =
    rateRenegotiationColumns.map(([key, label]) => ({
      key,
      label:
        key === "annualProfitIncrease"
          ? `${data?.appliedIncreaseRate ?? applied.increaseRate}% 올리면 연 이익`
          : label,
      required: key === "developerName",
      sortable: false,
      align: typeof data?.items[0]?.[key] === "number" ? "right" : "left",
      calc: helpKeys.has(key) ? data?.formulaHelp[key as HelpKey] : undefined,
      render: (row) => cell(row, key),
    }));
  const selected = useListColumns("UI-32", columns);
  const rows = data?.items ?? [];
  const summary = data?.summary;
  const download = async () => {
    if (loading || error || !data) return;
    setDownloading(true);
    setNotice("");
    try {
      const name = await getCommonRateRenegotiationsExcel(
        { ...applied, increaseRate: data.appliedIncreaseRate },
        selected.visible.map((c) => c.key),
      );
      setNotice(`${name} 파일을 받았습니다.`);
    } catch (reason) {
      setNotice(
        reason instanceof Error
          ? reason.message
          : "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.",
      );
      showFields(reason);
    } finally {
      setDownloading(false);
    }
  };
  return (
    <div className="rate-workspace" data-testid="rate-workspace" ref={root}>
      <Banner kind="info">
        {data
          ? `기준월 ${data.baseYearMonth} · 1년 계속 투입 가정`
          : "기준월을 불러오는 중입니다."}
      </Banner>
      {data && (data.salaryEstimatedDeveloperIds ?? []).length > 0 && (
        <div data-testid="rate-salary-estimated">
          <Banner kind="warn">
            {`${data.baseYearMonth} 급여 업로드 전: ${(data.salaryEstimatedDeveloperIds ?? []).length}명은 개발자 풀 월 급여로 계산한 값입니다.`}
          </Banner>
        </div>
      )}
      <ListFilters onSearch={search} onReset={reset}>
        <ListFilter label="갱신 시점" htmlFor="rate-renewalMonths">
          <select
            className="ls-in"
            {...attributes("renewalMonths")}
            value={draft.renewalMonths}
            onChange={(e) => change("renewalMonths", Number(e.target.value))}
          >
            {[3, 6, 12].map((value) => (
              <option key={value} value={value}>
                {value}개월 안
              </option>
            ))}
          </select>
          {fieldError("renewalMonths")}
        </ListFilter>
        <ListFilter label="이익률 미만" htmlFor="rate-profitRateThreshold">
          <select
            className="ls-in"
            {...attributes("profitRateThreshold")}
            value={draft.profitRateThreshold}
            onChange={(e) =>
              change("profitRateThreshold", Number(e.target.value))
            }
          >
            {[10, 15, 20].map((value) => (
              <option key={value} value={value}>
                {value}% 미만
              </option>
            ))}
          </select>
          {fieldError("profitRateThreshold")}
        </ListFilter>
        <ListFilter label="직원구분" htmlFor="rate-employeeTypeCode">
          <select
            className="ls-in"
            {...attributes("employeeTypeCode")}
            value={draft.employeeTypeCode ?? ""}
            onChange={(e) => change("employeeTypeCode", e.target.value)}
          >
            <option value="">전체</option>
            {(options?.employees ?? []).map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          {fieldError("employeeTypeCode")}
        </ListFilter>
        <ListFilter label="프로젝트" htmlFor="rate-projectId">
          <select
            className="ls-in"
            {...attributes("projectId")}
            value={draft.projectId ?? ""}
            onChange={(e) => change("projectId", e.target.value)}
          >
            <option value="">전체</option>
            {(options?.projects ?? []).map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          {fieldError("projectId")}
        </ListFilter>
      </ListFilters>
      {optionError && (
        <Banner
          kind="warn"
          action={
            <button
              type="button"
              className="ls-btn"
              data-testid="rate-options-retry"
              onClick={() => setOptionRevision((v) => v + 1)}
            >
              다시 시도
            </button>
          }
        >
          선택지를 불러오지 못했습니다.
        </Banner>
      )}
      <KpiCards
        aria-label="재협상 대상 요약"
        className="rate-kpis"
        testId="rate-summary"
      >
        <KpiCard
          label="대상 인원"
          value={summary ? `${summary.targetCount}명` : "–"}
          testId="rate-count"
        />
        <KpiCard
          label="대상 평균 이익률"
          value={percent(summary?.averageProfitRate)}
          description={`목표 이익률 ${percent(summary?.targetProfitRate)}`}
          testId="rate-average"
        />
        <KpiCard
          label="인상률 가정 연 이익 증가"
          value={
            <Money value={summary?.assumedAnnualProfitIncrease} unit={unit} />
          }
          testId="rate-assumed"
        />
        <KpiCard
          label="목표 단가 연 이익 증가"
          value={
            <Money value={summary?.targetAnnualProfitIncrease} unit={unit} />
          }
          testId="rate-target"
        />
      </KpiCards>
      <div className="rate-assumption">
        <label htmlFor="rate-increaseRate">인상률</label>
        <input
          className="ls-in"
          type="number"
          step="any"
          {...attributes("increaseRate")}
          value={draft.increaseRate}
          onChange={(e) => assumption(e.target.value)}
        />
        <span>% (0~50%) · 1년 계속 투입 가정</span>
        {fieldError("increaseRate")}
      </div>
      {error && (
        <div data-testid="rate-error">
          <Banner
            kind="bad"
            action={
              <button
                type="button"
                className="ls-btn"
                data-testid="rate-retry"
                onClick={() => setRevision((v) => v + 1)}
              >
                다시 시도
              </button>
            }
          >
            {error}
          </Banner>
        </div>
      )}
      <ListView
        screenId="UI-32"
        columns={columns}
        visibleColumns={selected.visible}
        hiddenColumns={selected.hidden}
        onToggleColumn={selected.toggle}
        onResetColumns={selected.reset}
        rows={
          loading || error
            ? null
            : rows.slice(
                (page - 1) * presentation.pageSize,
                page * presentation.pageSize,
              )
        }
        rowKey={(row) => row.assignmentId}
        total={data?.items.length ?? 0}
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
        onRowClick={(row) => setDetail(row.developerId)}
        stickyFirstColumn
        onDownload={() => void download()}
        downloading={downloading || loading || !!error || !data}
        toolbarRight={
          <MoneyUnitSelect
            value={unit}
            onChange={setUnit}
            id="rate-money-unit"
          />
        }
        footer={{
          annualProfitIncrease:
            data && !loading && !error ? (
              <Money value={data.totalAnnualProfitIncrease} unit={unit} />
            ) : (
              "–"
            ),
        }}
        testId="rate-list"
      />
      {fields.columns && <p className="rate-field-error">{fields.columns}</p>}
      <div className="rate-mobile-cards" data-testid="rate-mobile-cards">
        {!loading &&
          !error &&
          rows
            .slice(
              (page - 1) * presentation.pageSize,
              page * presentation.pageSize,
            )
            .map((row) => (
              <button
                type="button"
                className="ls-mcard"
                key={row.assignmentId}
                data-testid={`rate-card-${row.assignmentId}`}
                onClick={() => setDetail(row.developerId)}
              >
                {selected.visible.map((column) => (
                  <span key={column.key}>
                    <span className="ls-cap">{column.label}</span>{" "}
                    {cell(row, column.key as RateRenegotiationColumn)}
                  </span>
                ))}
              </button>
            ))}
      </div>
      <div className="rate-help-actions" aria-label="머리글 계산 도움말">
        {rateRenegotiationColumns
          .filter(([key]) => helpKeys.has(key))
          .map(([key, label]) => (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              key={key}
              data-testid={`rate-help-${kebab(key)}`}
              aria-expanded={help === key}
              disabled={!data}
              onClick={() =>
                setHelp((previous) =>
                  previous === key ? null : (key as HelpKey),
                )
              }
            >
              {label} ⓘ
            </button>
          ))}
      </div>
      {help && data && (
        <div className="rate-help-content" data-testid="rate-help-content">
          {data.formulaHelp[help]}
        </div>
      )}
      {data && (
        <FormulaBox
          rows={rateRenegotiationColumns
            .filter(([key]) => helpKeys.has(key))
            .map(([key, label]) => [label, data.formulaHelp[key as HelpKey]])}
          foot={`기준월 ${data.baseYearMonth} · 1년 계속 투입 가정`}
          testId="rate-formulas"
        />
      )}
      {detail && (
        <DeveloperDetail
          developerId={detail}
          readOnly={true}
          onClose={() => setDetail(null)}
        />
      )}
      <Toast message={notice} onDismiss={() => setNotice("")} />
    </div>
  );
}

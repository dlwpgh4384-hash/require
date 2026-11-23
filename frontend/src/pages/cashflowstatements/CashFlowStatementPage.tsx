import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useInRouterContext, useNavigate } from "react-router-dom";
import {
  Banner,
  MoneyUnitSelect,
  DEFAULT_MONEY_UNIT,
  formatMoney,
  formatKoreanMoney,
  type MoneyUnit,
} from "../../shared/list";
import { Toast } from "../../shared/feedback/Dialog";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { ProjectRequestError } from "../projects/projects";
import {
  LineChart,
  chartColor,
  type ChartSeries,
} from "../profitstructures/ProfitStructureCharts";
import {
  getCommonCashFlowStatements,
  getCommonCashFlowStatementsExcel,
  type CashFlowStatementResult,
} from "./cashFlowStatements";
import { useCashFlowStatementTitleHost } from "./CashFlowStatementWorkspace";
import {
  cashFlowPeriodOptions,
  useCashFlowStatementPeriod,
} from "./useCashFlowStatementPeriod";
import "../profitstructures/profitStructures.css";
import "./cashFlowStatements.css";

export function cashFlowBalanceSeries(
  data: CashFlowStatementResult,
): ChartSeries[] {
  const lastClosed = data.months.reduce(
    (last, month, index) => (month.closed ? index : last),
    -1,
  );
  return [
    {
      name: "실적",
      color: chartColor.c1,
      values: data.months.map((m) => (m.closed ? m.closingBalance : null)),
    },
    {
      name: "예정",
      color: chartColor.c1,
      dash: true,
      values: data.months.map((m, index) =>
        !m.closed || index === lastClosed ? m.closingBalance : null,
      ),
    },
  ];
}
function useOpenReceivables() {
  const inRouter = useInRouterContext();
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const navigate = inRouter ? useNavigate() : null;
  return () => navigate?.("/screens/UI-13");
}

// UI-21: 원천 조회 전용, 기초잔액 설정은 UI-27에서 관리한다.
export function CashFlowStatementPage() {
  const period = useCashFlowStatementPeriod();
  const host = useCashFlowStatementTitleHost();
  const openReceivables = useOpenReceivables();
  const state = period.state;
  const year =
    state.status === "ready" ? state.value.year : period.fallback?.year;
  const latest =
    state.status === "ready"
      ? state.basis.latestYm
      : period.fallback
        ? `${period.fallback.currentYear}${String(period.fallback.currentMonth).padStart(2, "0")}`
        : null;
  const [data, setData] = useState<CashFlowStatementResult | null>(null);
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [revision, setRevision] = useState(0);
  const selected =
    latest && Number(latest.slice(0, 4)) === year
      ? Number(latest.slice(4)) - 1
      : null;
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: "ok" | "warn";
  } | null>(null);
  const generation = useRef(0);
  const root = useRef<HTMLElement>(null);
  useEffect(() => {
    generation.current++;
    setData(null);
    setNotice(null);
    if (year === undefined) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setFields([]);
    getCommonCashFlowStatements({ year }, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result.data);
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        setError(
          failure instanceof ProjectRequestError
            ? failure.message
            : "자금수지표를 불러오지 못했습니다. 다시 시도해 주세요.",
        );
        setFields(failure instanceof ProjectRequestError ? failure.errors : []);
        if (
          failure instanceof ProjectRequestError &&
          failure.errors.some((f) => f.field === "year")
        )
          root.current
            ?.querySelector<HTMLSelectElement>(
              '[data-testid="period-year"], [data-testid="cash-flow-year"]',
            )
            ?.focus();
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => {
      controller.abort();
      generation.current++;
    };
  }, [year, revision]);
  const download = async () => {
    if (year === undefined || !data || loading || downloading) return;
    const current = generation.current;
    setDownloading(true);
    try {
      await getCommonCashFlowStatementsExcel({ year }, unit);
      if (current === generation.current)
        setNotice({ message: "엑셀 파일을 내려받았습니다.", kind: "ok" });
    } catch (failure) {
      if (current === generation.current)
        setNotice({
          message:
            failure instanceof ProjectRequestError
              ? failure.message
              : "엑셀 파일을 받지 못했습니다.",
          kind: "warn",
        });
    } finally {
      setDownloading(false);
    }
  };
  const actions = (
    <button
      type="button"
      className="ls-btn ls-btn-sm"
      data-testid="cash-flow-excel"
      disabled={downloading || loading || !data || !!error}
      onClick={() => void download()}
    >
      <span aria-hidden="true">⇩ </span>엑셀 다운로드
    </button>
  );
  const columns = data?.numericTable.columns ?? [
    ...Array.from({ length: 12 }, (_, index) => ({
      label: `${index + 1}월`,
      actual: null,
      warning: false,
    })),
    ...["상반기", "하반기", "연간"].map((label) => ({
      label,
      actual: null,
      warning: false,
    })),
  ];
  return (
    <section
      ref={root}
      className="screen-body cash-flow-statements-page"
      data-testid="cash-flow-statements-page"
      data-screen-id="UI-21"
    >
      {host ? (
        createPortal(actions, host)
      ) : (
        <div className="cash-flow-actions">{actions}</div>
      )}
      <div data-testid="cash-flow-period">
        {state.status === "ready" ? (
          <PeriodSelector
            {...cashFlowPeriodOptions}
            basis={state.basis}
            value={state.value}
            onChange={period.setValue}
          />
        ) : period.fallback ? (
          <div className="cash-flow-year">
            <label htmlFor="cash-flow-year">연도</label>
            <select
              id="cash-flow-year"
              data-testid="cash-flow-year"
              value={period.fallback.year}
              onChange={(event) =>
                period.setFallbackYear(Number(event.target.value))
              }
            >
              {Array.from(
                { length: period.fallback.currentYear },
                (_, i) => i + 1,
              )
                .reverse()
                .map((value) => (
                  <option key={value} value={value}>
                    {value}년
                  </option>
                ))}
            </select>
          </div>
        ) : (
          <p role={state.status === "error" ? "alert" : "status"}>
            {state.status === "error"
              ? state.message
              : "기간을 불러오는 중입니다."}
          </p>
        )}
        {fields.map((field, index) => (
          <p className="cash-flow-field-error" key={`${field.field}-${index}`}>
            {field.field}: {field.message}
          </p>
        ))}
      </div>
      {loading && (
        <p role="status" data-testid="cash-flow-loading">
          자금수지표를 불러오는 중입니다.
        </p>
      )}
      {error && (
        <div role="alert" data-testid="cash-flow-error">
          <p>{error}</p>
          <button
            type="button"
            data-testid="cash-flow-retry"
            onClick={() => setRevision((n) => n + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      <>
        {data &&
          !data.months.some(
            (month) =>
              month.openingBalance !== null ||
              month.closingBalance !== null ||
              month.cashSales !== null ||
              month.incomeTotal !== null ||
              month.expenseTotal !== null,
          ) && (
            <Banner kind="info">
              <span data-testid="cash-flow-empty">
                조회할 자금 자료가 없습니다. 자료가 등록되면 표시됩니다.
              </span>
            </Banner>
          )}
        <div className="cash-flow-warnings">
          {data?.warnings.map((warning) => (
            <div
              key={warning.yearMonth}
              data-testid={`cash-flow-warning-${warning.yearMonth}`}
            >
              <Banner kind="bad">
                {warning.yearMonth.slice(0, 4)}-{warning.yearMonth.slice(4)}{" "}
                월말 잔액이 최소 운영자금(
                {formatKoreanMoney(warning.minimumCash)})보다 적을 것으로
                예상됩니다. 예상 잔액{" "}
                {formatKoreanMoney(warning.expectedBalance)}.{" "}
                <button
                  type="button"
                  className="cash-flow-link"
                  data-testid={`cash-flow-receivables-${warning.yearMonth}`}
                  onClick={openReceivables}
                >
                  미수금 보기
                </button>
              </Banner>
            </div>
          ))}
        </div>
        <article className="profit-card" data-testid="cash-flow-balance-card">
          <div className="profit-card-h">
            <h2>월말 잔액 흐름</h2>
            <span className="ls-cap">단위: 백만 원 · 부가세 포함</span>
          </div>
          <LineChart
            series={data ? cashFlowBalanceSeries(data) : []}
            labels={columns.slice(0, 12).map((column) => column.label)}
            guide={
              data
                ? {
                    name: "최소 운영자금",
                    values: data.months.map(() => data.minimumCash),
                  }
                : null
            }
            testId="cash-flow-balance-chart"
          />
        </article>
        <article className="profit-card" data-testid="cash-flow-table-card">
          <div className="profit-card-h">
            <h2>
              {year === undefined
                ? "자금수지 (부가세 포함)"
                : `${year}년 자금수지 (부가세 포함)`}
            </h2>
            <MoneyUnitSelect
              value={unit}
              onChange={setUnit}
              id="cash-flow-unit"
            />
          </div>
          <div className="ls-twrap">
            <table
              className="ls-t cash-flow-table"
              data-testid={data ? "cash-flow-table" : "cash-flow-table-pending"}
              aria-busy={loading}
            >
              <thead>
                <tr>
                  <th scope="col">항목</th>
                  {columns.map((column, index) => {
                    const warning = data?.warnings.find(
                      (w) => w.yearMonth === data?.months[index]?.yearMonth,
                    );
                    return (
                      <th
                        scope="col"
                        key={column.label}
                        className={
                          selected === index ? "is-selected" : undefined
                        }
                      >
                        {column.label}
                        {column.actual !== null && (
                          <small>{column.actual ? "실적" : "예정"}</small>
                        )}
                        {column.warning && warning && (
                          <span
                            className="cash-flow-warning-icon"
                            tabIndex={0}
                            role="img"
                            aria-label={`${column.label} 자금 부족 예상`}
                            aria-describedby={`cash-flow-warning-detail-${warning.yearMonth}`}
                          >
                            ⚠
                            <span
                              className="cash-flow-tooltip"
                              role="tooltip"
                              id={`cash-flow-warning-detail-${warning.yearMonth}`}
                            >
                              예상 잔액{" "}
                              {formatKoreanMoney(warning.expectedBalance)} ·
                              최소 운영자금{" "}
                              {formatKoreanMoney(warning.minimumCash)}
                            </span>
                          </span>
                        )}
                      </th>
                    );
                  })}
                </tr>
              </thead>
              <tbody>
                {!data && (
                  <tr>
                    <td colSpan={16}>–</td>
                  </tr>
                )}
                {data?.numericTable.rows.map((row, rowIndex) => (
                  <tr
                    key={row.label}
                    className={`cash-flow-row-${row.style}`}
                    data-testid={`cash-flow-row-${rowIndex}`}
                  >
                    <th scope="row">{row.label}</th>
                    {row.values.map((value, index) => (
                      <td
                        key={index}
                        className={[
                          columns[index]?.actual === false ? "is-forecast" : "",
                          selected === index ? "is-selected" : "",
                          value !== null && value < 0 ? "is-negative" : "",
                        ]
                          .filter(Boolean)
                          .join(" ")}
                      >
                        {formatMoney(value, unit)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </article>
      </>
      {notice && (
        <Toast
          {...notice}
          onDismiss={() => setNotice(null)}
          testId="cash-flow-toast"
        />
      )}
    </section>
  );
}

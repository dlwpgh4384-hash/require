import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  Banner,
  KpiCard,
  KpiCards,
  Money,
  MoneyUnitSelect,
  DEFAULT_MONEY_UNIT,
  formatKoreanMoney,
  type MoneyUnit,
} from "../../shared/list";
import { Toast } from "../../shared/feedback/Dialog";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { ProjectRequestError } from "../projects/projects";
import { percentage } from "../profitstructures/profitStructureFormulas";
import { OperatingProfitStatementTable } from "../operatingprofitstatements/OperatingProfitStatementTable";
import {
  getCommonNetProfitStatements,
  getCommonNetProfitStatementsExcel,
  NetProfitStatementRequestError,
  type NetProfitStatementResult,
} from "./netProfitStatements";
import { useNetProfitStatementTitleHost } from "./NetProfitStatementWorkspace";
import "../operatingprofitstatements/operatingProfitStatements.css";
import "./netProfitStatements.css";

const options = {
  kind: "yearCumulative",
  allowBeforeStart: true,
  firstDataYear: 2025,
} as const;

// UI-09 / SFR-109: readonly statement and identical workbook.
export function NetProfitStatementPage() {
  const period = useScreenPeriod("UI-09", options);
  const host = useNetProfitStatementTitleHost();
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [data, setData] = useState<NetProfitStatementResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [revision, setRevision] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: "ok" | "warn";
  } | null>(null);
  const root = useRef<HTMLElement>(null);
  const generation = useRef(0);
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
    generation.current++;
    setNotice(null);
    setData(null);
    if (!filter) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setFields([]);
    getCommonNetProfitStatements(filter, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result.data);
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        setError(
          failure instanceof NetProfitStatementRequestError &&
            failure.status === 403
            ? "이 화면을 조회할 권한이 없습니다."
            : failure instanceof ProjectRequestError
              ? failure.message
              : "순이익표를 불러오지 못했습니다. 다시 시도해 주세요.",
        );
        setFields(failure instanceof ProjectRequestError ? failure.errors : []);
        if (
          failure instanceof ProjectRequestError &&
          failure.errors.some((f) => ["year", "baseMonth"].includes(f.field))
        ) {
          root.current
            ?.querySelector<HTMLSelectElement>('[data-testid="period-month"]')
            ?.focus();
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => {
      controller.abort();
      generation.current++;
    };
  }, [filter, revision]);
  const download = async () => {
    if (!filter || !data || loading || downloading) return;
    const current = generation.current;
    setDownloading(true);
    try {
      await getCommonNetProfitStatementsExcel(filter, unit);
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
      data-testid="net-profit-excel"
      disabled={downloading || loading || !data || !!error}
      onClick={() => void download()}
    >
      엑셀 다운로드
    </button>
  );
  const end = Number(filter?.baseMonth.slice(4));
  const monthName = filter ? `${filter.year}-${filter.baseMonth.slice(4)}` : "";
  const compared =
    data?.numericTable.columns.some((c) => c.label === "전년 대비") ?? false;
  const summary = data?.summary;
  const change = summary?.previousMonthChangePercent;
  return (
    <section
      ref={root}
      className="screen-body operating-profit-statements-page net-profit-statements-page"
      data-testid="net-profit-statements-page"
      data-screen-id="UI-09"
    >
      {host ? (
        createPortal(actions, host)
      ) : (
        <div className="operating-actions">{actions}</div>
      )}
      {state.status === "ready" ? (
        <PeriodSelector
          {...options}
          variant="cumulativeBar"
          basis={state.basis}
          value={state.value}
          onChange={period.setValue}
        />
      ) : (
        <p role={state.status === "error" ? "alert" : "status"}>
          {state.status === "error"
            ? state.message
            : "기간을 불러오는 중입니다."}
        </p>
      )}
      {fields.map((f) => (
        <p
          key={f.field}
          role="alert"
          data-testid={`net-profit-error-${f.field}`}
        >
          {f.message}
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
              data-testid="net-profit-retry"
              onClick={() => setRevision((v) => v + 1)}
            >
              다시 시도
            </button>
          }
        >
          {error}
        </Banner>
      )}
      {data?.provisionalNotice && (
        <Banner kind="warn" icon={null}>
          {data.provisionalNotice}
        </Banner>
      )}
      <div data-testid="net-profit-summary" data-base-month={data?.baseMonth}>
        <KpiCards aria-label="요약" testId="net-profit-kpis">
          <KpiCard
            label="누계 당기순이익"
            value={formatKoreanMoney(summary?.cumulativeNetProfit)}
            testId="net-profit-kpi-cumulative"
            description={<>순이익률 {percentage(summary?.netProfitRate)}</>}
          />
          <KpiCard
            label={`${monthName} 순이익`.trim()}
            value={formatKoreanMoney(summary?.baseMonthNetProfit)}
            testId="net-profit-kpi-base"
            description={
              change == null ? (
                <>전월 대비 –</>
              ) : (
                <>
                  <span
                    className={change < 0 ? "operating-bad" : "operating-good"}
                  >
                    {change < 0 ? "▼" : "▲"} {Math.abs(change).toFixed(1)}%
                  </span>{" "}
                  전월 대비
                </>
              )
            }
          />
        </KpiCards>
      </div>
      <section
        className="operating-report-card"
        aria-label="순이익 보고서"
        data-testid="net-profit-report-card"
      >
        <div className="ls-tbar">
          <div className="ls-tl" data-testid="net-profit-table-caption">
            {filter
              ? `${filter.year}년 · ${monthName}까지 실적${compared ? ` · 전년 같은 기간(1~${end}월)과 비교` : ""}`
              : null}
          </div>
          <div className="ls-tr">
            <MoneyUnitSelect
              value={unit}
              onChange={setUnit}
              id="net-profit-money-unit"
            />
          </div>
        </div>
        <OperatingProfitStatementTable
          table={data?.numericTable}
          unit={unit}
          baseIndex={end ? end - 1 : undefined}
        />
        {loading && (
          <p role="status" data-testid="net-profit-loading">
            자료를 불러오는 중입니다.
          </p>
        )}
        {data &&
          !error &&
          (data.numericTable.rows.length === 0 ||
            data.numericTable.rows.every((r) =>
              r.values.every((c) => c.value == null),
            )) && (
            <p role="status" data-testid="net-profit-empty">
              자료가 없습니다.
            </p>
          )}
        <p className="ls-note" data-testid="net-profit-note">
          매출 인식: 턴키는 청구일, 월단위·파견은 귀속월 기준. 부가세 제외.
          {compared &&
            " 전년 대비는 같은 기간 누계끼리 비교하며, 비용 항목은 늘면 빨강으로 표시합니다."}
        </p>
        <p className="ls-note">내부 비용은 판관비(연구개발비)로 처리합니다.</p>
        <p className="ls-note">
          법인세는 월할 금액이며 실제 납부는 자금수지표(UI-21)에만 반영합니다.
        </p>
      </section>
      {data && (
        <section
          aria-label="계약 유형별 기여도"
          data-testid="net-profit-contributions"
        >
          <h3>계약 유형별 기여도 · 기준월 매출총이익</h3>
          {data.contributionVisible ? (
            <>
              <div className="net-profit-contribution-grid">
                {data.contributions.map((item) => (
                  <article
                    className="net-profit-contribution-card"
                    key={item.contractTypeCode}
                    data-testid={`net-profit-contribution-${item.contractTypeCode.toLowerCase()}`}
                  >
                    <h4>{item.contractTypeName}</h4>
                    <div
                      className={
                        item.amount != null && item.amount < 0
                          ? "operating-bad"
                          : undefined
                      }
                    >
                      {item.amount != null && item.amount < 0 && "적자 "}
                      <Money value={item.amount} unit={unit} />
                    </div>
                    {item.sharePercent != null && (
                      <p>{percentage(item.sharePercent)}</p>
                    )}
                    {item.sharePercent != null && item.sharePercent > 0 && (
                      <div
                        role="meter"
                        aria-label={`${item.contractTypeName} 기여도`}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={item.sharePercent}
                        className="net-profit-contribution-track"
                        data-testid={`net-profit-bar-${item.contractTypeCode.toLowerCase()}`}
                      >
                        <div style={{ width: `${item.sharePercent}%` }} />
                      </div>
                    )}
                  </article>
                ))}
              </div>
              <p className="ls-note">
                적자 유형은 기여도 비율에서 제외하고 금액만 표시합니다.
              </p>
            </>
          ) : (
            <p data-testid="net-profit-pre-start">
              운영 시작 전에는 월별 손익만 이관되어 계약 유형별 기여도를
              표시하지 않습니다.
            </p>
          )}
        </section>
      )}
      {notice && (
        <Toast
          message={notice.message}
          kind={notice.kind}
          onDismiss={() => setNotice(null)}
          testId="net-profit-toast"
        />
      )}
    </section>
  );
}

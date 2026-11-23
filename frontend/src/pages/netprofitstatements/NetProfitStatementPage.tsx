import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useInRouterContext, useNavigate } from "react-router-dom";
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

/** 시안 막대 색: 턴키 --c1, 월단위 --c3, 파견 --c2 */
const barColor: Record<string, string> = {
  TK: "var(--np-c1)",
  MO: "var(--np-c3)",
  DP: "var(--np-c2)",
};

/** 다른 화면(자금수지표)으로 가는 통로. 라우터 밖(단위 시험)에서는 아무것도 하지 않는다. */
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

// UI-09 / SFR-109: readonly statement and identical workbook.
/** 시안 ic('down',16)·ic('info',16) */
const downloadIcon = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
  </svg>
);
const infoIcon = (
  <svg className="ls-banner-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
  </svg>
);
export function NetProfitStatementPage() {
  const period = useScreenPeriod("UI-09", options);
  const host = useNetProfitStatementTitleHost();
  const openScreen = useOpenScreen();
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
      className="ls-btn"
      data-testid="net-profit-excel"
      disabled={downloading || loading || !data || !!error}
      onClick={() => void download()}
    >
      {downloadIcon} 엑셀 다운로드
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
          icon={infoIcon}
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
        <Banner kind="warn" icon={infoIcon}>
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
        <p className="ls-note" data-testid="net-profit-tax-note">
          법인세는 연간 예상액을 달마다 나눈 금액(월할)입니다. 실제로 낸
          법인세는{" "}
          <a
            href="/screens/UI-21"
            className="net-profit-link"
            data-testid="net-profit-link-UI-21"
            onClick={(event) => {
              event.preventDefault();
              openScreen("UI-21");
            }}
          >
            자금수지표
          </a>
          에만 잡힙니다. 내부(자체 개발) 비용은 판관비(연구개발비)로
          처리합니다.
        </p>
      </section>
      {data && (
        <section
          className="net-profit-card"
          aria-label="계약 유형별 기여도"
          data-testid="net-profit-contributions"
        >
          <div className="net-profit-card-h">
            <h2>계약 유형별 기여도</h2>
            {data.contributionVisible && (
              <span className="ls-cap">{monthName} 매출총이익 기준</span>
            )}
          </div>
          {data.contributionVisible ? (
            <>
              {data.contributions.map((item) => {
                const deficit = item.amount != null && item.amount < 0;
                const share = item.sharePercent ?? 0;
                return (
                  <div
                    className="net-profit-bar"
                    key={item.contractTypeCode}
                    data-testid={`net-profit-contribution-${item.contractTypeCode.toLowerCase()}`}
                  >
                    <span className="net-profit-bar-name">
                      {item.contractTypeName}
                    </span>
                    <div
                      role="meter"
                      aria-label={`${item.contractTypeName} 기여도`}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={deficit ? 0 : share}
                      className="net-profit-track"
                      data-testid={`net-profit-bar-${item.contractTypeCode.toLowerCase()}`}
                    >
                      <div
                        style={{
                          width: `${deficit ? 0 : share}%`,
                          background:
                            barColor[item.contractTypeCode] ?? "var(--np-c1)",
                        }}
                      />
                    </div>
                    <span
                      className={`net-profit-bar-value${deficit ? " operating-bad" : ""}`}
                    >
                      {deficit
                        ? `적자 · ${formatKoreanMoney(item.amount)}`
                        : `${percentage(share)} · ${formatKoreanMoney(item.amount)}`}
                    </span>
                  </div>
                );
              })}
              <p className="ls-note" data-testid="net-profit-contribution-note">
                {data.contributions.some((c) => c.amount != null && c.amount < 0)
                  ? "적자 유형은 비율에서 빼고 금액만 표시합니다. "
                  : ""}
                내부(자체 개발) 비용은 판관비(연구개발비)로 처리되어 여기에 넣지
                않습니다.
              </p>
            </>
          ) : (
            <p className="net-profit-sub" data-testid="net-profit-pre-start">
              {data.year}년은 월별 손익만 옮겨 와서 계약 유형별 기여도는 볼 수
              없습니다.
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

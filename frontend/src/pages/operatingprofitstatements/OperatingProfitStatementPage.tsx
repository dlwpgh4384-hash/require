import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
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
import { UploadActions } from "../uploads/UploadActions";
import { ProjectRequestError } from "../projects/projects";
import { percentage } from "../profitstructures/profitStructureFormulas";
import {
  getCommonOperatingProfitStatements,
  getCommonOperatingProfitStatementsExcel,
  OperatingProfitStatementRequestError,
  type OperatingResult,
  type OperatingMetric,
} from "./operatingProfitStatements";
import { OperatingProfitStatementTable } from "./OperatingProfitStatementTable";
import { useOperatingProfitStatementTitleHost } from "./OperatingProfitStatementWorkspace";
import "./operatingProfitStatements.css";

const options = {
  kind: "yearCumulative",
  allowBeforeStart: true,
  firstDataYear: 2025,
} as const;
/** 시안 yy()·delta(): 「▲ 5.2% 전년 같은 기간 대비」 — 화살표·숫자만 색, 줄면 빨강. */
function Comparison({
  value,
  label,
}: {
  value: number | null | undefined;
  label: string;
}) {
  return value == null ? null : (
    <>
      <span className={value < 0 ? "operating-bad" : "operating-good"}>
        {value < 0 ? "▼" : "▲"} {Math.abs(value).toFixed(1)}%
      </span>{" "}
      {label}
    </>
  );
}
const YEAR = "전년 같은 기간 대비";
/** 시안 UI-08 요약 4칸: 누계 3칸은 전년 같은 기간 대비, 기준월 칸은 전월 대비만. */
function MetricDescription({
  id,
  metric,
  rate,
}: {
  id: string;
  metric?: OperatingMetric;
  rate?: number | null;
}) {
  if (id === "base")
    return (
      <Comparison value={metric?.previousMonthChangePercent} label="전월 대비" />
    );
  const year = metric?.previousYearChangePercent;
  if (id !== "operating") return <Comparison value={year} label={YEAR} />;
  return (
    <>
      이익률 {percentage(rate)}
      {year != null && (
        <>
          {" · "}
          <Comparison value={year} label={YEAR} />
        </>
      )}
    </>
  );
}
// UI-08 / SFR-108. UploadActions owns UI-08-M1 and UI-08-M2 using the common upload APIs.
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
export function OperatingProfitStatementPage() {
  const period = useScreenPeriod("UI-08", options);
  const host = useOperatingProfitStatementTitleHost();
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [data, setData] = useState<OperatingResult | null>(null);
  const [loading, setLoading] = useState(false),
    [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [revision, setRevision] = useState(0),
    [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: "ok" | "warn";
  } | null>(null);
  const root = useRef<HTMLElement>(null),
    generation = useRef(0);
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
    generation.current++;
    setData(null);
    setLoading(true);
    setError("");
    setFields([]);
    getCommonOperatingProfitStatements(filter, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result.data);
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        setError(
          failure instanceof OperatingProfitStatementRequestError &&
            failure.status === 403
            ? "이 화면을 조회할 권한이 없습니다."
            : failure instanceof ProjectRequestError
              ? failure.message
              : "영업이익표를 불러오지 못했습니다. 다시 시도해 주세요.",
        );
        setFields(failure instanceof ProjectRequestError ? failure.errors : []);
        if (
          failure instanceof ProjectRequestError &&
          failure.errors.some((f) => ["year", "baseMonth"].includes(f.field))
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
  const download = async () => {
    if (!filter || !data || loading || downloading) return;
    const current = generation.current;
    setDownloading(true);
    try {
      await getCommonOperatingProfitStatementsExcel(filter, unit);
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
    <div className="operating-title-buttons">
      <UploadActions
        formTypeCode="B11"
        targetName="전년 손익"
        templateLabel="전년 손익 양식"
        uploadLabel="전년 손익 업로드"
        dialog={{
          title: "전년 손익 업로드",
          intro: (
            <p>
              시작한 달 전 달만 올릴 수 있습니다. 필수:
              연월·매출·매출원가·판관비 합계·영업이익. 세부 줄은 있으면
              적습니다.
            </p>
          ),
          checkLabel: "검사",
        }}
        notes={[
          // 시안 업로드 결과 창(전년 손익) 안내 문구
          "빈칸은 값 없음으로 두고 화면에 「–」로 보입니다. 엑셀 값은 다시 계산하지 않습니다. 같은 연월을 다시 올리면 그 달을 덮어씁니다.",
        ]}
        onApplied={() => {
          setRevision((v) => v + 1);
          setNotice({ message: "전년 손익을 반영했습니다.", kind: "ok" });
        }}
      />
      <button
        type="button"
        className="upload-title-button"
        data-testid="operating-excel"
        disabled={downloading || loading || !data || !!error}
        onClick={() => void download()}
      >
        {downloadIcon} 엑셀 다운로드
      </button>
    </div>
  );
  const summary = data?.summary;
  const year = filter?.year ?? data?.year;
  const end = Number((filter?.baseMonth ?? data?.baseMonth ?? "").slice(4));
  const monthName = year && end ? `${year}-${String(end).padStart(2, "0")}` : "";
  const cards: [string, OperatingMetric | undefined, string][] = [
    ["누계 매출", summary?.cumulativeRevenue, "revenue"],
    ["누계 매출총이익", summary?.cumulativeGrossProfit, "gross"],
    ["누계 영업이익", summary?.cumulativeOperatingProfit, "operating"],
    [`${monthName} 영업이익`.trim(), summary?.baseMonthOperatingProfit, "base"],
  ];
  // 첫 해(이관 해)는 서버가 전년 열을 주지 않는다. 시안 plTable의 PP(전년) 유무와 같다.
  const compared =
    data?.numericTable.columns.some((c) => c.label === "전년 대비") ?? false;
  return (
    <section
      ref={root}
      className="screen-body operating-profit-statements-page"
      data-testid="operating-profit-statements-page"
      data-screen-id="UI-08"
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
          data-testid={`operating-error-${f.field}`}
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
              data-testid="operating-retry"
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
      <div data-testid="operating-summary" data-base-month={data?.baseMonth}>
        <KpiCards aria-label="요약" testId="operating-kpis">
          {cards.map(([label, metric, id]) => (
            <KpiCard
              key={id}
              label={label}
              value={formatKoreanMoney(metric?.amount)}
              testId={`operating-kpi-${id}`}
              description={
                <MetricDescription
                  id={id}
                  metric={metric}
                  rate={summary?.operatingProfitRate}
                />
              }
            />
          ))}
        </KpiCards>
      </div>
      <section
        className="operating-report-card"
        aria-label="영업이익 보고서"
        data-testid="operating-report-card"
      >
        <div className="ls-tbar">
          <div className="ls-tl" data-testid="operating-table-caption">
            {year && monthName
              ? `${year}년 · ${monthName}까지 실적${compared ? ` · 전년 같은 기간(1~${end}월)과 비교` : ""}`
              : null}
          </div>
          <div className="ls-tr">
            <MoneyUnitSelect
              value={unit}
              onChange={setUnit}
              id="operating-money-unit"
            />
          </div>
        </div>
        <OperatingProfitStatementTable
          table={data?.numericTable}
          unit={unit}
          baseIndex={end ? end - 1 : undefined}
        />
        {loading && (
          <p role="status" data-testid="operating-loading">
            자료를 불러오는 중입니다.
          </p>
        )}
        {data &&
          !error &&
          (data.numericTable.rows.length === 0 ||
            data.numericTable.rows.every((r) =>
              r.values.every((c) => c.value == null),
            )) && (
            <p role="status" data-testid="operating-empty">
              자료가 없습니다.
            </p>
          )}
        <p className="ls-note" data-testid="operating-note">
          매출 인식: 턴키는 청구일, 월단위·파견은 귀속월 기준. 부가세 제외.
          {compared &&
            " 전년 대비는 같은 기간 누계끼리 비교하며, 비용 항목은 늘면 빨강으로 표시합니다."}
        </p>
      </section>
      {notice && (
        <Toast
          message={notice.message}
          kind={notice.kind}
          onDismiss={() => setNotice(null)}
          testId="operating-toast"
        />
      )}
    </section>
  );
}

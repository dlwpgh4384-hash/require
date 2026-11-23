import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Badge,
  Banner,
  FormulaBox,
  KpiCard,
  KpiCards,
  formatKoreanMoney,
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
import { ProjectLink } from "../projects/ProjectLink";
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
import {
  rateRenegotiationColumnHelp,
  rateRenegotiationFormulaFoot,
  rateRenegotiationFormulas,
} from "./rateRenegotiationFormulas";
import "./rateRenegotiations.css";

const moneyKeys = new Set<string>([
  "currentUnitPrice",
  "monthlyCost",
  "targetUnitPrice",
  "annualProfitIncrease",
]);
const kebab = (key: string) =>
  key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
/** 시안 pct(): 소수 한 자리 %, 음수는 「−」. */
const percent = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value)
    ? "–"
    : `${value < 0 ? "−" : ""}${Math.abs(value).toFixed(1)}%`;
const plain = (value: number | null | undefined) =>
  value == null ? "–" : String(Number(value));
/** 시안 갱신 시점 이름: 3·6개월 안, 12개월은 「1년 안」 */
const renewalLabel = (months: number) =>
  months === 12 ? "1년 안" : `${months}개월 안`;
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
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [page, setPage] = useState(1);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState("");
  // 인상률 칸 글자. 비우거나 쓰는 중에는 바로 0으로 바꾸지 않고, 숫자일 때만 잠깐 뒤 다시 계산한다.
  const [rateText, setRateText] = useState(
    String(rateRenegotiationDefaults().increaseRate),
  );
  const rateTimer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(rateTimer.current), []);
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
    window.clearTimeout(rateTimer.current);
    setRateText(String(next.increaseRate));
    setDraft(next);
    setApplied(next);
    setRevision((v) => v + 1);
  };
  const assumption = (text: string) => {
    window.clearTimeout(rateTimer.current);
    const value = Number(text);
    if (text.trim() === "" || !Number.isFinite(value)) {
      setRateText(text);
      return;
    }
    const increaseRate = Math.max(0, Math.min(50, value));
    setRateText(increaseRate === value ? text : String(increaseRate));
    setDraft((previous) => ({ ...previous, increaseRate }));
    rateTimer.current = window.setTimeout(
      () => setApplied((previous) => ({ ...previous, increaseRate })),
      300,
    );
  };
  const cell = (
    row: RateRenegotiationRow,
    key: RateRenegotiationColumn,
  ): ReactNode => {
    // 시안 pname: 이름 아래 관리번호(작게)
    if (key === "developerName")
      return (
        <span className="rate-pname">
          {row.developerName}
          {row.managementNumber && <small>{row.managementNumber}</small>}
        </span>
      );
    // 시안 badge(구분)
    if (key === "employeeTypeName")
      return <Badge kind="neutral">{row.employeeTypeName}</Badge>;
    // 시안 projLink: 프로젝트 이름을 누르면 프로젝트 상세(줄 누름과 따로)
    if (key === "projectName")
      return (
        <ProjectLink
          projectId={row.projectId}
          name={row.projectName}
          testId={`rate-project-${row.assignmentId}`}
        />
      );
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
    if (key === "annualProfitIncrease")
      return (
        <>
          +<Money value={row.annualProfitIncrease} unit={unit} />
        </>
      );
    if (moneyKeys.has(key))
      return <Money value={row[key] as number} unit={unit} />;
    // 시안: 이익률 10% 미만 빨강, 15% 미만 주황
    if (key === "profitRate")
      return (
        <span
          className={
            row.profitRate < 10
              ? "rate-bad"
              : row.profitRate < 15
                ? "rate-warn"
                : undefined
          }
        >
          {percent(row.profitRate)}
        </span>
      );
    // 시안: 필요 인상률이 0이면 「–」
    if (key === "requiredIncreaseRate")
      return row.requiredIncreaseRate > 0
        ? percent(row.requiredIncreaseRate)
        : "–";
    // 시안 mmf(): MM은 소수 둘째 자리
    if (key === "monthlyMm") return Number(row.monthlyMm).toFixed(2);
    return row[key];
  };
  const columns: ListColumn<RateRenegotiationRow>[] =
    rateRenegotiationColumns.map(([key, label]) => ({
      key,
      label:
        key === "annualProfitIncrease"
          ? `${data?.appliedIncreaseRate ?? applied.increaseRate}% 올리면 연 이익`
          : key === "monthlyCost"
            ? "월 원가(기준월)"
            : key === "targetUnitPrice"
              ? `목표 단가 (${plain(data?.summary.targetProfitRate)}%)`
              : label,
      required: key === "developerName",
      sortable: false,
      align: typeof data?.items[0]?.[key] === "number" ? "right" : "left",
      calc: rateRenegotiationColumnHelp(data?.summary.targetProfitRate)[key],
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
      {data && (data.salaryEstimatedDeveloperIds ?? []).length > 0 && (
        <div data-testid="rate-salary-estimated">
          <Banner kind="warn">
            {`${Number(data.baseYearMonth.slice(-2))}월 급여 업로드 전: ${(data.salaryEstimatedDeveloperIds ?? []).length}명은 개발자 풀 월 급여로 계산한 값입니다.`}
          </Banner>
        </div>
      )}
      <KpiCards
        aria-label="재협상 대상 요약"
        className="rate-kpis"
        testId="rate-summary"
      >
        <KpiCard
          label="재협상 대상"
          value={summary ? `${summary.targetCount}명` : "–"}
          description={`갱신 ${renewalLabel(applied.renewalMonths)} · 이익률 ${applied.profitRateThreshold}% 미만`}
          testId="rate-count"
        />
        <KpiCard
          label="대상 평균 이익률"
          value={percent(summary?.averageProfitRate)}
          description={`목표 ${plain(summary?.targetProfitRate)}%`}
          testId="rate-average"
        />
        <KpiCard
          label={`단가 ${plain(data?.appliedIncreaseRate ?? applied.increaseRate)}% 올리면`}
          value={
            summary
              ? `+${formatKoreanMoney(summary.assumedAnnualProfitIncrease)}`
              : "–"
          }
          description="연 이익 증가(12개월 기준)"
          testId="rate-assumed"
        />
        <KpiCard
          label="목표 단가까지 올리면"
          value={
            summary
              ? `+${formatKoreanMoney(summary.targetAnnualProfitIncrease)}`
              : "–"
          }
          description="연 이익 증가(12개월 기준)"
          testId="rate-target"
        />
      </KpiCards>
      <Banner kind="info">
        <span className="rate-banner">
          <span className="rate-banner-text">
            월단위·파견 프로젝트에서 계약 갱신이 다가오는 사람 가운데 이익률이
            낮은 사람입니다. 인상률을 바꾸면 이익 증가액을 다시 계산합니다.
          </span>
          <span className="rate-assumption">
            <label htmlFor="rate-increaseRate">인상률</label>
            <input
              className="ls-in"
              type="number"
              step="any"
              inputMode="decimal"
              {...attributes("increaseRate")}
              value={rateText}
              onChange={(e) => assumption(e.target.value)}
              onBlur={() => {
                if (rateText.trim() === "" || !Number.isFinite(Number(rateText)))
                  setRateText(String(draft.increaseRate));
              }}
            />
            <span>%</span>
          </span>
        </span>
        {fieldError("increaseRate")}
      </Banner>
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
                {renewalLabel(value)}
              </option>
            ))}
          </select>
          {fieldError("renewalMonths")}
        </ListFilter>
        <ListFilter label="이익률" htmlFor="rate-profitRateThreshold">
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
        <ListFilter label="직원 구분" htmlFor="rate-employeeTypeCode">
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
              <>
                +<Money value={data.totalAnnualProfitIncrease} unit={unit} />
              </>
            ) : (
              "–"
            ),
        }}
        mobileRows={data && !loading && !error ? rows : null}
        renderCard={(row) => (
          // 시안 휴대폰 카드: 이름·프로젝트 + D-일수 / 「단가 · 이익률」 + 연 이익
          <>
            <div className="ls-r1">
              <span className="rate-card-name">
                <strong>{row.developerName}</strong>
                <small>{row.projectName}</small>
              </span>
              <Badge kind={row.daysUntilRenewal <= 92 ? "warn" : "neutral"}>
                D-{row.daysUntilRenewal}
              </Badge>
            </div>
            <div className="ls-r2">
              <span>
                단가 {formatKoreanMoney(row.currentUnitPrice)} · 이익률{" "}
                {percent(row.profitRate)}
              </span>
              <b>+{formatKoreanMoney(row.annualProfitIncrease)}/년</b>
            </div>
          </>
        )}
        testId="rate-list"
      />
      {fields.columns && <p className="rate-field-error">{fields.columns}</p>}
      <FormulaBox
        rows={rateRenegotiationFormulas(data?.summary.targetProfitRate)}
        foot={rateRenegotiationFormulaFoot}
        testId="rate-formulas"
      />
      {detail && (
        <DeveloperDetail
          developerId={detail}
          readOnly={true}
          onClose={() => setDetail(null)}
        />
      )}
      {notice && <Toast message={notice} onDismiss={() => setNotice("")} />}
    </div>
  );
}

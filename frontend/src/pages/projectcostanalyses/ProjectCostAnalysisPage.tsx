import { useEffect, useRef, useState } from "react";
import { koreanToday } from "../../shared/time";
import { YearBar } from "../../shared/period/YearBar";
import { ListView, ListFilters, ListFilter } from "../../shared/list/ListView";
import { useListColumns } from "../../shared/list/useListPresentation";
import { KpiCards, KpiCard } from "../../shared/list/KpiCards";
import { FormulaBox } from "../../shared/list/FormulaBox";
import { MoneyUnitSelect } from "../../shared/list/MoneyUnitSelect";
import {
  DEFAULT_MONEY_UNIT,
  formatMoney,
  type MoneyUnit,
} from "../../shared/list/moneyUnit";
import type { ListColumn, ListSort } from "../../shared/list/listTypes";
import { Toast } from "../../shared/feedback/Dialog";
import { getCommonCodes } from "../codegroups/codeGroups";
import { ProjectRequestError } from "../projects/projects";
import {
  getCommonProjectCostAnalyses,
  getCommonProjectCostAnalysesExcel,
  type CommonProjectCostAnalysisResult,
  type CommonProjectCostAnalysisRow,
  type ProjectCostAnalysisFilter,
} from "./projectCostAnalyses";
import { useProjectCostAnalysisPeriod } from "./useProjectCostAnalysisPeriod";
import { ProjectCostAnalysisDetail } from "./ProjectCostAnalysisDetail";
import "./projectCostAnalyses.css";

export const projectCostAnalysisColumnLabels: [
  keyof CommonProjectCostAnalysisRow,
  string,
][] = [
  ["projectName", "프로젝트"],
  ["contractTypeName", "유형"],
  ["projectStatus", "상태"],
  ["originalContractAmount", "처음 계약금액"],
  ["contractAmount", "현재 계약금액"],
  ["plannedMm", "계획 MM"],
  ["actualMm", "실제 MM"],
  ["cumulativeRevenue", "누적 매출"],
  ["totalCost", "누적 원가 ⓘ"],
  ["cumulativeProfit", "누적 이익"],
  ["cumulativeMarginRate", "누적 마진율"],
  ["expectedFinalMarginRate", "예상 최종 마진율"],
  ["monthlyMarginRate", "월간 마진율"],
  ["targetDifference", "목표 대비"],
  ["budgetUsageRate", "예산 소진율"],
  ["unitPricePerMm", "MM당 단가"],
  ["costPerMm", "MM당 원가"],
  ["profitPerMm", "MM당 이익"],
  ["freeSupportMm", "무상지원 MM"],
  ["freeSupportCost", "무상지원 원가"],
  ["researchCost", "연구개발비 누계"],
  ["alerts", "경보"],
];
const moneyKeys = new Set([
  "originalContractAmount",
  "contractAmount",
  "cumulativeRevenue",
  "totalCost",
  "cumulativeProfit",
  "unitPricePerMm",
  "costPerMm",
  "profitPerMm",
  "freeSupportCost",
  "researchCost",
]);
export function ProjectCostAnalysisPage() {
  const today = koreanToday();
  const currentYear = Number(today.slice(0, 4));
  const period = useProjectCostAnalysisPeriod();
  const [draft, setDraft] = useState({
    projectId: "",
    contractTypeCode: "",
    projectStatus: "",
    alertOnly: false,
  });
  const [filter, setFilter] = useState(draft);
  const [data, setData] = useState<CommonProjectCostAnalysisResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [retry, setRetry] = useState(0);
  const [optionsRetry, setOptionsRetry] = useState(0);
  const [types, setTypes] = useState<{ code: string; codeName: string }[]>([]);
  const [statuses, setStatuses] = useState<
    { code: string; codeName: string }[]
  >([]);
  const [optionError, setOptionError] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [dense, setDense] = useState(false);
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [selected, setSelected] = useState<CommonProjectCostAnalysisRow>();
  const [downloading, setDownloading] = useState(false);
  const [toast, setToast] = useState("");
  const form = useRef<HTMLElement>(null);
  const request: ProjectCostAnalysisFilter = { ...period.period, ...filter };
  useEffect(() => {
    const controller = new AbortController();
    setOptionError("");
    Promise.all([
      getCommonCodes("CTRT_TYPE", controller.signal),
      getCommonCodes("PROJ_STAT", controller.signal),
    ])
      .then(([type, status]) => {
        if (controller.signal.aborted) return;
        setTypes((type.items ?? []).filter((item) => item.useYn === "Y"));
        setStatuses(
          (status.items ?? []).filter(
            (item) => item.useYn === "Y" && ["ING", "END"].includes(item.code),
          ),
        );
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setOptionError(
            failure instanceof Error
              ? failure.message
              : "조건을 불러오지 못했습니다.",
          );
      });
    return () => controller.abort();
  }, [optionsRetry]);
  useEffect(() => {
    if (!period.ready) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setFields({});
    setSelected(undefined);
    setPage(1);
    getCommonProjectCostAnalyses(
      { ...period.period, ...filter },
      controller.signal,
    )
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        setData(undefined);
        setError(
          failure instanceof Error
            ? failure.message
            : "분석을 불러오지 못했습니다.",
        );
        const next =
          failure instanceof ProjectRequestError
            ? Object.fromEntries(
                failure.errors.map((item) => [item.field, item.message]),
              )
            : {};
        setFields(next);
        form.current
          ?.querySelector<HTMLElement>(`[name="${Object.keys(next)[0]}"]`)
          ?.focus();
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [
    period.ready,
    period.period.year,
    period.period.baseMonth,
    filter,
    retry,
  ]);
  function cell(
    row: CommonProjectCostAnalysisRow,
    key: keyof CommonProjectCostAnalysisRow,
  ) {
    const value = row[key];
    if (key === "plannedMm" && value === null && row.contractTypeCode !== "IN")
      return "계획 없음";
    if (key === "expectedFinalMarginRate" && row.missingMonthlyCost)
      return <span title={row.missingMonthlyCostReason ?? ""}>단가 없음</span>;
    if (key === "alerts") return row.alerts.join(" · ");
    if (key === "totalCost")
      return (
        <span title={row.costComposition}>
          {value === null ? "" : formatMoney(Number(value), unit)}
        </span>
      );
    if (key === "budgetUsageRate" && typeof value === "number")
      return (
        <span
          className={`project-cost-budget ${row.budgetColor === "빨강" ? "is-red" : row.budgetColor === "주황" ? "is-orange" : ""}`}
        >
          {value.toFixed(1)}%<meter min={0} max={100} value={value} />
        </span>
      );
    if (typeof value === "number")
      return moneyKeys.has(key)
        ? formatMoney(value, unit)
        : key.endsWith("Rate") || key === "targetDifference"
          ? `${value.toFixed(1)}${key === "targetDifference" ? "%p" : "%"}`
          : value.toLocaleString("ko-KR");
    return value === null ? "" : typeof value === "string" ? value : "";
  }
  const columns: ListColumn<CommonProjectCostAnalysisRow>[] =
    projectCostAnalysisColumnLabels.map(([key, label]) => ({
      key,
      label,
      required: key === "projectName",
      render: (row) => cell(row, key),
    }));
  const columnState = useListColumns("UI-04", columns);
  const rows = data?.items ?? [];
  const maxMonth =
    period.period.year === currentYear ? Number(today.slice(5, 7)) : 12;
  async function download() {
    setDownloading(true);
    try {
      await getCommonProjectCostAnalysesExcel(request);
      setToast("엑셀 파일을 다운로드했습니다.");
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "엑셀을 받지 못했습니다.",
      );
    } finally {
      setDownloading(false);
    }
  }
  const summary = data?.summary;
  return (
    <section
      ref={form}
      className="project-cost-analysis-page"
      data-testid="project-cost-analysis-page"
      data-screen-id="UI-04"
      aria-label="프로젝트 원가 분석"
    >
      <YearBar
        year={period.period.year}
        currentYear={currentYear}
        min={period.earliest}
        max={currentYear}
        pastReadOnly={false}
        caption="기준월 말까지의 프로젝트 누적 원가를 조회합니다."
        testId="project-cost-year"
        onChange={(year) =>
          period.change({
            year,
            baseMonth: Math.min(
              period.period.baseMonth,
              year === currentYear ? Number(today.slice(5, 7)) : 12,
            ),
          })
        }
      />
      <label className="project-cost-month">
        기준월
        <select
          name="baseMonth"
          aria-label="기준월"
          data-testid="project-cost-month"
          value={period.period.baseMonth}
          disabled={!period.ready}
          onChange={(event) =>
            period.change({
              ...period.period,
              baseMonth: Number(event.target.value),
            })
          }
        >
          {Array.from({ length: maxMonth }, (_, index) => (
            <option key={index + 1} value={index + 1}>
              {index + 1}월
            </option>
          ))}
        </select>
        {fields.baseMonth && <span role="alert">{fields.baseMonth}</span>}
      </label>
      {period.error && (
        <p role="alert">
          {period.error}{" "}
          <button type="button" onClick={period.retry}>
            다시 시도
          </button>
        </p>
      )}
      <KpiCards
        aria-label="기준월 전체 프로젝트 요약"
        className="project-cost-summary"
        testId="project-cost-summary"
      >
        <KpiCard
          label="누적 매출"
          value={summary ? formatMoney(summary.cumulativeRevenue, unit) : "–"}
        />
        <KpiCard
          label="누적 이익(내부 제외)"
          value={summary ? formatMoney(summary.cumulativeProfit, unit) : "–"}
        />
        <KpiCard label="저마진 경보" value={summary?.lowMarginCount ?? "–"} />
        <KpiCard
          label="예산 초과"
          value={summary?.budgetExceededCount ?? "–"}
        />
        <KpiCard
          label="연구개발비 누계(내부)"
          value={summary ? formatMoney(summary.researchCost, unit) : "–"}
        />
      </KpiCards>
      <p className="ls-cap">
        요약은 목록 조건과 관계없이 기준월 전체 대상 프로젝트를 집계합니다.
      </p>
      <div>
        <ListFilters
          onSearch={() => {
            setFilter({ ...draft });
            setRetry((value) => value + 1);
          }}
          onReset={() => {
            const reset = {
              projectId: "",
              contractTypeCode: "",
              projectStatus: "",
              alertOnly: false,
            };
            setDraft(reset);
            setFilter(reset);
          }}
        >
          <ListFilter label="프로젝트명" htmlFor="project-cost-project">
            <select
              id="project-cost-project"
              name="projectId"
              value={draft.projectId}
              onChange={(event) =>
                setDraft({ ...draft, projectId: event.target.value })
              }
            >
              <option value="">전체</option>
              {data?.projectOptions.map((item) => (
                <option key={item.projectId} value={item.projectId}>
                  {item.projectName}
                </option>
              ))}
            </select>
            {fields.projectId && <span role="alert">{fields.projectId}</span>}
          </ListFilter>
          <ListFilter label="계약유형" htmlFor="project-cost-type">
            <select
              id="project-cost-type"
              name="contractTypeCode"
              value={draft.contractTypeCode}
              onChange={(event) =>
                setDraft({ ...draft, contractTypeCode: event.target.value })
              }
            >
              <option value="">전체</option>
              {types.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.codeName}
                </option>
              ))}
            </select>
            {fields.contractTypeCode && (
              <span role="alert">{fields.contractTypeCode}</span>
            )}
          </ListFilter>
          <ListFilter label="상태" htmlFor="project-cost-status">
            <select
              id="project-cost-status"
              name="projectStatus"
              value={draft.projectStatus}
              onChange={(event) =>
                setDraft({ ...draft, projectStatus: event.target.value })
              }
            >
              <option value="">전체</option>
              {statuses.map((item) => (
                <option
                  key={item.code}
                  value={item.code === "ING" ? "진행" : "종료"}
                >
                  {item.codeName}
                </option>
              ))}
            </select>
            {fields.projectStatus && (
              <span role="alert">{fields.projectStatus}</span>
            )}
          </ListFilter>
          <label>
            <input
              type="checkbox"
              checked={draft.alertOnly}
              onChange={(event) =>
                setDraft({ ...draft, alertOnly: event.target.checked })
              }
            />
            경보 있는 것만
          </label>
        </ListFilters>
      </div>
      {optionError && (
        <p role="alert">
          {optionError}
          <button
            type="button"
            onClick={() => setOptionsRetry((value) => value + 1)}
          >
            조건 다시 시도
          </button>
        </p>
      )}
      <ListView
        screenId="UI-04"
        columns={columns}
        visibleColumns={columnState.visible}
        hiddenColumns={columnState.hidden}
        onToggleColumn={columnState.toggle}
        onResetColumns={columnState.reset}
        rows={
          loading || !period.ready
            ? null
            : error && !data
              ? null
              : rows.slice((page - 1) * pageSize, page * pageSize)
        }
        rowKey={(row) => row.projectId}
        total={data?.totalCount ?? 0}
        page={page}
        pageSize={pageSize}
        onPage={setPage}
        onPageSize={(size) => {
          setPageSize(size);
          setPage(1);
        }}
        dense={dense}
        onDense={setDense}
        sort={null}
        onSort={(_sort: ListSort) => undefined}
        onRowClick={setSelected}
        onDownload={
          period.ready && !loading && !error ? () => void download() : undefined
        }
        downloading={downloading}
        error={error}
        onRetry={() => setRetry((value) => value + 1)}
        testId="project-cost-list"
        toolbarLeft={
          <span>
            {period.period.baseMonth}월 기준 · 총 {data?.totalCount ?? "–"}건
          </span>
        }
        toolbarRight={
          <MoneyUnitSelect
            value={unit}
            onChange={setUnit}
            id="project-cost-unit"
          />
        }
        mobileRows={loading || error ? null : rows}
        renderCard={(row) => (
          <article
            className="project-cost-mobile-card"
            data-testid={`project-cost-card-${row.projectId.toLowerCase()}`}
          >
            <button
              type="button"
              data-testid={`project-cost-card-open-${row.projectId.toLowerCase()}`}
              onClick={() => setSelected(row)}
            >
              {row.projectName}
            </button>
            <dl>
              {projectCostAnalysisColumnLabels
                .filter(([key]) => key !== "projectName")
                .map(([key, label]) => (
                  <div key={key}>
                    <dt>{label}</dt>
                    <dd>{cell(row, key)}</dd>
                  </div>
                ))}
            </dl>
          </article>
        )}
      />
      <FormulaBox
        testId="project-cost-formulas"
        rows={
          data?.items.map(
            (row) => [row.projectName, row.costComposition] as [string, string],
          ) ?? []
        }
        foot="업무 자료를 수정하지 않는 조회 화면입니다. 계산 방법은 DB 정의와 해당월 원천 자료를 따릅니다."
      />
      {selected && (
        <ProjectCostAnalysisDetail
          key={`${selected.projectId}-${period.period.year}-${period.period.baseMonth}`}
          row={selected}
          period={period.period}
          unit={unit}
          onClose={() => setSelected(undefined)}
        />
      )}
      {toast && <Toast message={toast} onDismiss={() => setToast("")} />}
    </section>
  );
}

import { useEffect, useMemo, useState } from "react";
import {
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { yearRange, type PeriodBasis } from "../../shared/period/periodRules";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { projectFailure, ProjectRequestError, won } from "../projects/projects";
import { Toast } from "../../shared/feedback/Dialog";
import { PersonalCostProfitFilters } from "./PersonalCostProfitFilters";
import {
  PersonalCostProfitSummary,
  PersonalCostProfitCalculationNote,
} from "./PersonalCostProfitSummary";
import {
  PersonalCostProfitAllocations,
  PersonalCostProfitContributions,
} from "./PersonalCostProfitContributions";
import {
  getCommonPersonalCostProfits,
  getCommonPersonalCostProfitsExcel,
  getPersonalCostProfitOptions,
} from "./personalCostProfits";
import type {
  PersonalCostProfitFilter,
  PersonalCostProfitOptions,
  PersonalCostProfitResult,
  PersonalCostProfitRow,
} from "./personalCostProfitTypes";
import "./personalCostProfits.css";

export const personalCostProfitColumnLabels = [
  ["developerName", "이름"],
  ["affiliationName", "소속"],
  ["positionName", "직위"],
  ["employeeTypeName", "직원구분"],
  ["joinDate", "입사일"],
  ["operatingRate", "가동률"],
  ["mm", "MM"],
  ["unitPrice", "단가"],
  ["monthlySalary", "월 급여"],
  ["allowance", "수당"],
  ["companyInsurance", "4대보험(회사)"],
  ["retirementReserve", "퇴직금 적립"],
  ["personalCost", "개인별 원가"],
  ["teamExpenses", "팀비"],
  ["projectExpenses", "프로젝트경비"],
  ["totalCost", "원가합계"],
  ["operatingProfit", "영업이익"],
  ["profitRate", "이익률"],
  ["personalInsurance", "4대보험(본인)"],
  ["withholdingTax", "3.3% 원천세"],
  ["incomeTax", "근로소득세"],
  ["netPay", "실지급액"],
] as const;
const referenceKeys = new Set<string>([
  "personalInsurance",
  "withholdingTax",
  "incomeTax",
  "netPay",
]);
const format = (key: string, value: unknown) =>
  value == null
    ? "–"
    : typeof value !== "number"
      ? String(value)
      : key === "mm"
        ? value.toFixed(2)
        : key.endsWith("Rate")
          ? `${value.toFixed(1)}%`
          : won(value);
const defaults = (
  year: number,
  basis: PeriodBasis,
): PersonalCostProfitFilter => ({
  year,
  baseMonth:
    year === Number(basis.latestYm.slice(0, 4))
      ? Number(basis.latestYm.slice(4))
      : 12,
  displayMode: "연누계",
  view: "전체",
  includeTurnkey: true,
});

// UI-03: existing M014/G006 screen body; no independent router or authentication gate.
export function PersonalCostProfitPage() {
  const period = useScreenPeriod("UI-03", { kind: "year" });
  return (
    <div
      className="personal-cost-profit-page"
      data-testid="personal-cost-profit-page"
      data-screen-id="UI-03"
    >
      {period.state.status === "loading" ? (
        <p>기간을 불러오는 중입니다.</p>
      ) : period.state.status === "error" ? (
        <p role="alert">{period.state.message}</p>
      ) : (
        <PersonalCostProfitContent
          basis={period.state.basis}
          year={period.state.value.year}
          onYear={(year) => period.setValue({ year })}
        />
      )}
    </div>
  );
}
function PersonalCostProfitContent({
  basis,
  year,
  onYear,
}: {
  basis: PeriodBasis;
  year: number;
  onYear: (year: number) => void;
}) {
  const [draft, setDraft] = useState(() => defaults(year, basis));
  const [applied, setApplied] = useState(draft);
  const [data, setData] = useState<PersonalCostProfitResult | null>(null);
  const [options, setOptions] = useState<PersonalCostProfitOptions | null>(
    null,
  );
  const [optionError, setOptionError] = useState("");
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [revision, setRevision] = useState(0);
  const [optionRevision, setOptionRevision] = useState(0);
  const [expanded, setExpanded] = useState<string[]>([]);
  const [detail, setDetail] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState("");
  const presentation = useListPresentation();
  const columns: ListColumn<PersonalCostProfitRow>[] =
    personalCostProfitColumnLabels
      .filter(
        ([key]) =>
          !data ||
          !["monthlySalary", "personalCost"].includes(key) ||
          data.totals[key as "monthlySalary" | "personalCost"] !== undefined,
      )
      .map(([key, label]) => ({
        key,
        label,
        required: key === "developerName",
        hiddenByDefault: referenceKeys.has(key),
        align: [
          "developerName",
          "affiliationName",
          "positionName",
          "employeeTypeName",
          "joinDate",
        ].includes(key)
          ? "left"
          : "right",
        render: (row) =>
          key === "developerName" ? (
            <span className="personal-cost-profit-name">
              <button
                type="button"
                className="link-button"
                data-testid={`personal-expand-${row.developerId}`}
                aria-label={`${row.developerName} 프로젝트 펼치기`}
                aria-expanded={expanded.includes(row.developerId)}
                onClick={(event) => {
                  event.stopPropagation();
                  setExpanded((previous) =>
                    previous.includes(row.developerId)
                      ? previous.filter((id) => id !== row.developerId)
                      : [...previous, row.developerId],
                  );
                }}
              >
                {expanded.includes(row.developerId) ? "▾" : "▸"}
              </button>
              {row.developerName}
            </span>
          ) : (
            <>
              {format(key, row[key])}
              {key === "mm" && row.mmOverOne && (
                <span className="personal-cost-profit-over">1.0 초과</span>
              )}
            </>
          ),
      }));
  const columnState = useListColumns("UI-03", columns);
  // Required JSON data stays available for local column toggles; optional uploaded reference fields are requested only when shown.
  const requestedColumns = personalCostProfitColumnLabels
    .filter(
      ([key]) => !referenceKeys.has(key) || !columnState.hidden.includes(key),
    )
    .map(([key]) => key)
    .join(",");
  useEffect(() => {
    const abort = new AbortController();
    getPersonalCostProfitOptions(abort.signal)
      .then((value) => {
        if (!abort.signal.aborted) {
          setOptions(value);
          setOptionError("");
        }
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setOptionError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [optionRevision]);
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    setFields({});
    setData(null);
    getCommonPersonalCostProfits(
      { ...applied, columns: requestedColumns.split(",") },
      abort.signal,
    )
      .then((value) => {
        if (!abort.signal.aborted) setData(value);
      })
      .catch((reason) => {
        if (abort.signal.aborted) return;
        setError(projectFailure(reason));
        if (reason instanceof ProjectRequestError) {
          setFields(
            Object.fromEntries(
              reason.errors.map((field) => [field.field, field.message]),
            ),
          );
          const first = reason.errors[0]?.field;
          if (first) document.getElementById(`personal-${first}`)?.focus();
        }
      });
    return () => abort.abort();
  }, [applied, revision, requestedColumns]);
  const apply = (next: PersonalCostProfitFilter) => {
    setDraft(next);
    setApplied(next);
    setPage(1);
    setRevision((v) => v + 1);
  };
  const changeYear = (next: number) => {
    onYear(next);
    apply({ ...draft, year: next, baseMonth: defaults(next, basis).baseMonth });
  };
  const sorted = useMemo(
    () =>
      [...(data?.items ?? [])].sort((a, b) => {
        if (!sort) return 0;
        const av = a[sort.key as keyof PersonalCostProfitRow];
        const bv = b[sort.key as keyof PersonalCostProfitRow];
        const result =
          typeof av === "number" && typeof bv === "number"
            ? av - bv
            : String(av ?? "").localeCompare(String(bv ?? ""), "ko");
        return (
          (sort.dir === "asc" ? result : -result) ||
          a.developerId.localeCompare(b.developerId)
        );
      }),
    [data, sort],
  );
  const years = yearRange(basis, { kind: "year" });
  const download = async () => {
    setDownloading(true);
    try {
      const count = await getCommonPersonalCostProfitsExcel({
        ...applied,
        columns: columnState.visible.map((column) => column.key),
      });
      setNotice(`엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`);
    } catch (reason) {
      setNotice(projectFailure(reason));
    } finally {
      setDownloading(false);
    }
  };
  return (
    <>
      <div className="personal-cost-profit-period">
        <YearBar
          year={draft.year}
          min={years.min}
          max={years.max}
          currentYear={Number(basis.latestYm.slice(0, 4))}
          onChange={changeYear}
          showBadge={false}
          caption="그 해 1월부터 지금까지 누계"
        />
        <span>기준월 {draft.baseMonth}월</span>
        <label>
          표시 방식
          <select
            aria-label="표시 방식"
            data-testid="personal-display-mode"
            value={draft.displayMode}
            onChange={(event) =>
              apply({
                ...applied,
                displayMode: event.target
                  .value as PersonalCostProfitFilter["displayMode"],
              })
            }
          >
            <option value="연누계">연 누계</option>
            <option value="월평균">월 평균</option>
          </select>
        </label>
        {fields.year && <span className="field-error">{fields.year}</span>}
        {fields.baseMonth && (
          <span className="field-error">{fields.baseMonth}</span>
        )}
        {fields.displayMode && (
          <span className="field-error">{fields.displayMode}</span>
        )}
      </div>
      <PersonalCostProfitSummary summary={data?.summary} />
      <PersonalCostProfitContributions items={data?.contributions ?? []} />
      <PersonalCostProfitFilters
        value={draft}
        options={options}
        fields={fields}
        onChange={setDraft}
        onSearch={() => apply(draft)}
        onReset={() => apply(defaults(draft.year, basis))}
      />
      {optionError && (
        <div role="alert">
          조건을 불러오지 못했습니다.{" "}
          <button
            className="ls-btn"
            data-testid="personal-options-retry"
            onClick={() => setOptionRevision((v) => v + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      <div>
        <button
          className="ls-btn"
          data-testid="personal-expand-all"
          onClick={() =>
            setExpanded(
              expanded.length ? [] : sorted.map((row) => row.developerId),
            )
          }
        >
          {expanded.length ? "프로젝트 모두 접기" : "프로젝트 모두 펼치기"}
        </button>
      </div>
      <ListView
        screenId="UI-03"
        columns={columns}
        visibleColumns={columnState.visible}
        hiddenColumns={columnState.hidden}
        onToggleColumn={columnState.toggle}
        onResetColumns={columnState.reset}
        rows={
          data
            ? sorted.slice(
                (page - 1) * presentation.pageSize,
                page * presentation.pageSize,
              )
            : null
        }
        rowKey={(row) => row.developerId}
        total={sorted.length}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={(size) => {
          presentation.setPageSize(size);
          setPage(1);
        }}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={sort}
        onSort={setSort}
        onRowClick={(row) => setDetail(row.developerId)}
        error={error}
        onRetry={() => setRevision((v) => v + 1)}
        onDownload={() => void download()}
        downloading={downloading}
        onResetFilters={() => apply(defaults(draft.year, basis))}
        footer={Object.fromEntries(
          columnState.visible.map((column) => [
            column.key,
            column.key === "developerName"
              ? "합계"
              : format(
                  column.key,
                  data?.totals[column.key as keyof PersonalCostProfitRow],
                ),
          ]),
        )}
        renderExpandedRow={(row) =>
          expanded.includes(row.developerId) ? (
            <PersonalCostProfitAllocations items={row.allocations} />
          ) : null
        }
        testId="personal-cost-profit-list"
      />
      <PersonalCostProfitCalculationNote />
      {detail && (
        <DeveloperDetail
          developerId={detail}
          readOnly
          onClose={() => setDetail(null)}
        />
      )}
      {notice && (
        <Toast message={notice} kind="ok" onDismiss={() => setNotice("")} />
      )}
    </>
  );
}

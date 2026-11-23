import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Badge,
  DEFAULT_MONEY_UNIT,
  ListView,
  Money,
  MoneyUnitSelect,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
  type MoneyUnit,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { yearRange, type PeriodBasis } from "../../shared/period/periodRules";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { useOpenDeveloperAction } from "../developers/developerRequest";
import { projectFailure, ProjectRequestError } from "../projects/projects";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { ShellIcon } from "../../shared/layout/ShellIcon";
import { PersonalCostProfitFilters } from "./PersonalCostProfitFilters";
import {
  PersonalCostProfitSummary,
  PersonalCostProfitCalculationNote,
  percentText,
} from "./PersonalCostProfitSummary";
import { PersonalCostProfitContributions } from "./PersonalCostProfitContributions";
import {
  getCommonPersonalCostProfits,
  getCommonPersonalCostProfitsExcel,
  getPersonalCostProfitOptions,
} from "./personalCostProfits";
import type {
  PersonalCostProfitAllocation,
  PersonalCostProfitFilter,
  PersonalCostProfitOptions,
  PersonalCostProfitResult,
  PersonalCostProfitRow,
} from "./personalCostProfitTypes";
import "./personalCostProfits.css";

type ColumnKey = keyof PersonalCostProfitRow & string;
/** 시안 C03: [키, 제목, 계산 방법(ⓘ)]. 키는 서버 열 키, 순서는 시안·서버와 같다. */
export const personalCostProfitColumnLabels: readonly (readonly [
  ColumnKey,
  string,
  string?,
])[] = [
  ["developerName", "이름"],
  ["affiliationName", "소속"],
  ["positionName", "직위"],
  ["employeeTypeName", "구분"],
  ["joinDate", "입사일"],
  [
    "operatingRate",
    "가동률",
    "투입된 달 ÷ 재직한 달. 조회 연도 1월(그 해 입사면 입사한 달)부터 이번 달(퇴사자는 퇴사한 달)까지",
  ],
  ["mm", "MM"],
  ["unitPrice", "단가"],
  [
    "monthlySalary",
    "월 급여",
    "회사가 직원에게 주는 급여. 외주는 급여가 없어 0(협력사 공급가는 개인별 원가에 들어감, 2026-11-20 결정)",
  ],
  ["allowance", "그룹장·팀장 수당"],
  ["companyInsurance", "4대보험(회사)"],
  ["personalInsurance", "4대보험(본인)"],
  ["withholdingTax", "3.3% 원천세"],
  ["incomeTax", "근로소득세"],
  ["retirementReserve", "퇴직금 적립"],
  ["netPay", "실지급액"],
  [
    "personalCost",
    "개인별 원가",
    "월 급여 + 수당 + 4대보험(회사) + 퇴직금 적립 (엑셀 Q열). 프리랜서는 그 달 지급액, 외주는 협력사 공급가(식-10)",
  ],
  ["teamExpenses", "팀비", "팀비(1인당 월) × 그 사람 MM. 외주 0(식-11)"],
  [
    "projectExpenses",
    "프로젝트경비",
    "프로젝트경비(1인당 월) × 그 사람 MM(식-11). 추가 직접 경비(출장비 등)는 사람에게 나누지 않고 프로젝트 원가에만 들어감",
  ],
  [
    "totalCost",
    "원가합계",
    "개인별 원가 + 팀비 + 프로젝트경비 (엑셀 T열)",
  ],
  ["operatingProfit", "영업이익", "단가 − 원가합계 (엑셀 U열)"],
  ["profitRate", "이익률"],
];
/** 참고 열: 제목 뒤 「 (참고)」, 처음에는 숨김. */
const referenceKeys = new Set<string>([
  "personalInsurance",
  "withholdingTax",
  "incomeTax",
  "netPay",
]);
/** 시안 SENS03: 민감 항목 권한이 있어야 보이는 열(월 급여·개인별 원가와 그 구성). 서버 정책과 같다. */
const sensitiveKeys = new Set<string>([
  "monthlySalary",
  "allowance",
  "companyInsurance",
  "personalInsurance",
  "withholdingTax",
  "incomeTax",
  "retirementReserve",
  "netPay",
  "personalCost",
]);
/** 시안 req:1 — 열 설정에서 끌 수 없는 열. */
const requiredKeys = new Set<string>([
  "developerName",
  "unitPrice",
  "totalCost",
  "operatingProfit",
]);
const textKeys = new Set<string>([
  "developerName",
  "affiliationName",
  "positionName",
  "employeeTypeName",
  "joinDate",
]);
/** 시안 hl(): 연 누계면 「단가」→「매출」, 「월 급여」→「급여」. */
export const personalCostProfitHeading = (
  key: string,
  label: string,
  monthlyAverage: boolean,
) =>
  monthlyAverage
    ? label
    : key === "unitPrice"
      ? "매출"
      : key === "monthlySalary"
        ? "급여"
        : label;
const dash = <span className="ls-cap">–</span>;
const isIdle = (item: PersonalCostProfitAllocation) =>
  item.assignmentId === "IDLE";
const projectsOf = (row: PersonalCostProfitRow) =>
  row.allocations.filter((item) => !isIdle(item));
const idleCostOf = (row: PersonalCostProfitRow) =>
  row.allocations
    .filter(isIdle)
    .reduce((sum, item) => sum + item.allocatedCost, 0);
/** ▸ 단추는 프로젝트 투입이나 대기 몫이 있는 사람만(시안 can). */
const canExpand = (row: PersonalCostProfitRow) =>
  projectsOf(row).length > 0 || idleCostOf(row) > 0;
const rateText = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value) ? (
    dash
  ) : (
    <span className={value < 0 ? "ls-neg" : undefined}>
      {percentText(value)}
    </span>
  );
const defaults = (
  year: number,
  basis: PeriodBasis,
): PersonalCostProfitFilter => ({
  year,
  // 기준월은 자동: 올해는 기간 기준의 최근 달, 지난 해는 12월
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
    // screen-body: 다른 업무 화면처럼 회색 바탕 위에 연도 줄·요약·표를 따로 놓는다(흰 카드로 감싸지 않음).
    <div
      className="screen-body personal-cost-profit-page"
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
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const presentation = useListPresentation();
  // 시안 openPerson: 개발자 풀과 같은 상세. [수정]·[사용 중지]는 개발자 풀(UI-02)로 넘긴다.
  const openDeveloperAction = useOpenDeveloperAction();
  const monthlyAverage = applied.displayMode === "월평균";
  const turnkeyExcluded = applied.includeTurnkey === false;
  // 턴키 단가 미포함 보기에서 턴키만 하는 사람은 단가 칸을 비운다(시안 shownRate)
  const turnkeyOnly = (row: PersonalCostProfitRow) =>
    turnkeyExcluded &&
    projectsOf(row).length > 0 &&
    projectsOf(row).every((item) => item.contractTypeCode === "TK");
  const toggle = (id: string) =>
    setExpanded((previous) =>
      previous.includes(id)
        ? previous.filter((value) => value !== id)
        : [...previous, id],
    );
  const cell = (key: ColumnKey, row: PersonalCostProfitRow): ReactNode => {
    switch (key) {
      case "developerName": {
        const open = expanded.includes(row.developerId);
        return (
          <span className="personal-cost-profit-name">
            {canExpand(row) ? (
              <button
                type="button"
                className="ls-ibtn ls-ibtn-sm"
                data-testid={`personal-expand-${row.developerId}`}
                aria-label={`${row.developerName} 프로젝트별 보기`}
                aria-expanded={open}
                onClick={(event) => {
                  event.stopPropagation();
                  toggle(row.developerId);
                }}
              >
                {/* 시안 ibtn sm + ic(open?'chevD':'chevR',16) */}
                <ShellIcon
                  name={open ? "chevronDown" : "chevronRight"}
                  size={16}
                />
              </button>
            ) : (
              <span
                className="personal-cost-profit-exp-space"
                aria-hidden="true"
              />
            )}
            <span className="personal-cost-profit-pname">
              {row.developerName}
              {row.managementNumber && <small>{row.managementNumber}</small>}
            </span>
          </span>
        );
      }
      case "affiliationName":
        return row.affiliationName || "자사";
      case "positionName":
        return row.positionName || "–";
      case "employeeTypeName":
        return row.employeeTypeName || "–";
      case "joinDate":
        return row.joinDate ?? dash;
      case "operatingRate":
        // 시안 wr(): 칸에 마우스를 올리면 「9달 ÷ 10달」(식-96), 재직한 달이 0이면 –
        return row.employedMonths === 0 ? (
          dash
        ) : (
          <span
            title={
              row.employedMonths == null
                ? undefined
                : `${row.operatingMonths ?? 0}달 ÷ ${row.employedMonths}달`
            }
          >
            {rateText(row.operatingRate)}
          </span>
        );
      case "mm":
        return projectsOf(row).length ? (
          <>
            {row.mm.toFixed(2)}
            {row.mmOverOne && (
              <>
                {" "}
                <Badge kind="warn">1.0 초과</Badge>
              </>
            )}
          </>
        ) : (
          dash
        );
      case "unitPrice":
        return turnkeyOnly(row) ? (
          dash
        ) : (
          <Money value={row.unitPrice} unit={unit} />
        );
      case "profitRate":
        return rateText(row.profitRate);
      default: {
        const value = row[key];
        return typeof value === "number" ? (
          <Money value={value} unit={unit} />
        ) : (
          dash
        );
      }
    }
  };
  // 민감 항목 권한이 없으면 서버가 월 급여·개인별 원가를 합계에서 뺀다. 그때는 구성 열(시안 SENS03)까지 모두 뺀다
  const sensitiveVisible =
    !data ||
    data.totals.monthlySalary !== undefined ||
    data.totals.personalCost !== undefined;
  const columns: ListColumn<PersonalCostProfitRow>[] =
    personalCostProfitColumnLabels
      .filter(([key]) => sensitiveVisible || !sensitiveKeys.has(key))
      .map(([key, label, calc]) => ({
        key,
        label: personalCostProfitHeading(key, label, monthlyAverage),
        calc,
        note: referenceKeys.has(key) ? "(참고)" : undefined,
        required: requiredKeys.has(key),
        hiddenByDefault: referenceKeys.has(key),
        align: textKeys.has(key) ? "left" : "right",
        render: (row) => cell(key, row),
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
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonPersonalCostProfitsExcel({
        ...applied,
        columns: columnState.visible.map((column) => column.key),
      });
      setNoticeKind("ok");
      setNotice(`엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`);
    } catch (reason) {
      // 실패는 다른 화면처럼 주의(warn) 알림으로 보인다
      setNoticeKind("warn");
      setNotice(projectFailure(reason));
    } finally {
      setDownloading(false);
    }
  };
  const shownPeople = data?.items.length ?? 0;
  const expandable = sorted.filter(canExpand).map((row) => row.developerId);
  // 시안 tr.subr: 펼친 줄은 같은 표의 칸에 맞춘다. 단가·원가합계·영업이익·이익률 칸만 채운다
  const subCell = (
    key: string,
    item: PersonalCostProfitAllocation,
    row: PersonalCostProfitRow,
  ): ReactNode => {
    const internal = item.contractTypeCode === "IN";
    switch (key) {
      case "developerName":
        return (
          <>
            └ {item.projectName} · {item.jobName ?? "–"}
            {item.free && (
              <>
                {" "}
                <Badge>무상지원</Badge>
              </>
            )}
          </>
        );
      case "mm":
        return item.mm.toFixed(2);
      case "unitPrice":
        return internal ||
          item.free ||
          (turnkeyExcluded && item.contractTypeCode === "TK") ? (
          dash
        ) : (
          <Money value={item.unitPrice} unit={unit} />
        );
      case "totalCost":
        return (
          <span
            title={`배분 원가 = 원가합계 × ${item.mm.toFixed(2)} ÷ ${Math.max(1, row.mm).toFixed(2)}`}
          >
            <Money value={item.allocatedCost} unit={unit} />
          </span>
        );
      case "operatingProfit":
        return (
          <>
            <Money value={item.operatingProfit} unit={unit} />
            {internal && <span className="ls-cap"> 연구개발</span>}
          </>
        );
      case "profitRate":
        return item.unitPrice
          ? rateText((item.operatingProfit / item.unitPrice) * 100)
          : dash;
      default:
        return "";
    }
  };
  const idleCell = (key: string, row: PersonalCostProfitRow): ReactNode => {
    const idle = idleCostOf(row);
    switch (key) {
      case "developerName":
        return "└ 남는 몫(대기 원가)";
      case "mm":
        // 월 평균이면 한 달 기준이라 1.0 − MM 합계를 보인다. 연 누계는 비운다
        return monthlyAverage && row.mm < 1 ? (1 - row.mm).toFixed(2) : "";
      case "totalCost":
        return <Money value={idle} unit={unit} />;
      case "operatingProfit":
        return <Money value={-idle} unit={unit} />;
      default:
        return "";
    }
  };
  const subRow = (
    testId: string,
    visible: ListColumn<PersonalCostProfitRow>[],
    render: (key: string) => ReactNode,
  ) => (
    <tr key={testId} className="ls-subr" data-testid={testId}>
      {visible.map((column) => (
        <td
          key={column.key}
          className={`ls-${column.align ?? "left"}${column.key === "developerName" ? " ls-ind" : ""}`}
        >
          {render(column.key)}
        </td>
      ))}
    </tr>
  );
  const unitLabel = (
    <MoneyUnitSelect value={unit} onChange={setUnit} id="personal-unit" />
  );
  return (
    <>
      <YearBar
        year={draft.year}
        min={years.min}
        max={years.max}
        currentYear={Number(basis.latestYm.slice(0, 4))}
        onChange={changeYear}
        caption="그 해 1월부터 지금까지 누계"
      />
      {(fields.year || fields.baseMonth || fields.displayMode) && (
        <div className="personal-cost-profit-period-error">
          {fields.year && <span className="field-error">{fields.year}</span>}
          {fields.baseMonth && (
            <span className="field-error">{fields.baseMonth}</span>
          )}
          {fields.displayMode && (
            <span className="field-error">{fields.displayMode}</span>
          )}
        </div>
      )}
      <PersonalCostProfitSummary
        summary={data?.summary}
        shownPeople={shownPeople}
        monthlyAverage={monthlyAverage}
        turnkeyExcluded={turnkeyExcluded}
        baseMonth={applied.baseMonth}
      />
      <PersonalCostProfitContributions
        items={data?.contributions ?? []}
        unit={unit}
      />
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
        toolbarLeft={
          <>
            <span className="ls-seg" role="group" aria-label="금액 보기">
              {(
                [
                  ["연누계", "연 누계"],
                  ["월평균", "월 평균"],
                ] as const
              ).map(([mode, label]) => (
                <button
                  key={mode}
                  type="button"
                  className={`ls-btn ls-btn-sm${applied.displayMode === mode ? " ls-btn-p" : ""}`}
                  data-testid={`personal-mode-${mode}`}
                  aria-pressed={applied.displayMode === mode}
                  onClick={() =>
                    apply({ ...applied, displayMode: mode })
                  }
                >
                  {label}
                </button>
              ))}
            </span>
            <span data-testid="personal-period-text">
              {applied.year}년 1~{applied.baseMonth}월{" "}
              {monthlyAverage ? "월 평균(÷ 사람마다 일한 달 수)" : "누계"} · 총{" "}
              <strong>{shownPeople}</strong>명
            </span>
            <button
              type="button"
              className="ls-btn ls-btn-t"
              data-testid="personal-expand-all"
              onClick={() => setExpanded(expanded.length ? [] : expandable)}
            >
              {expanded.length ? "모두 접기" : "프로젝트 모두 펼치기"}
            </button>
          </>
        }
        toolbarRight={unitLabel}
        footer={Object.fromEntries(
          columnState.visible.map((column) => {
            const value =
              data?.totals[column.key as keyof PersonalCostProfitRow];
            return [
              column.key,
              column.key === "mm" ? (
                typeof value === "number" ? value.toFixed(2) : ""
              ) : textKeys.has(column.key) ||
                column.key === "operatingRate" ||
                column.key === "profitRate" ||
                typeof value !== "number" ? (
                ""
              ) : (
                <Money value={value} unit={unit} />
              ),
            ];
          }),
        )}
        renderExpandedRow={(row, visible) =>
          expanded.includes(row.developerId) ? (
            <>
              {projectsOf(row).map((item) =>
                subRow(
                  `personal-allocation-${item.assignmentId}`,
                  visible,
                  (key) => subCell(key, item, row),
                ),
              )}
              {idleCostOf(row) > 0 &&
                subRow(`personal-idle-${row.developerId}`, visible, (key) =>
                  idleCell(key, row),
                )}
            </>
          ) : null
        }
        testId="personal-cost-profit-list"
      />
      <PersonalCostProfitCalculationNote
        year={applied.year}
        baseMonth={applied.baseMonth}
        monthlyAverage={monthlyAverage}
      />
      {detail && (
        <DeveloperDetail
          key={detail}
          developerId={detail}
          onClose={() => setDetail(null)}
          onEdit={(developer) => {
            setDetail(null);
            openDeveloperAction({ developer, action: "edit" });
          }}
          onStop={(developer) => {
            setDetail(null);
            openDeveloperAction({ developer, action: "stop" });
          }}
          onNotice={(message) => {
            setNoticeKind("warn");
            setNotice(message);
          }}
          onChanged={(message) => {
            // [상태 등록] 등은 상세 안에서 저장하고, 여기서는 표를 다시 읽고 알린다(개발자 풀과 같은 알림 종류).
            setRevision((v) => v + 1);
            if (message) {
              setNoticeKind(message.includes("투입 배정이") ? "info" : "ok");
              setNotice(message);
            }
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          onDismiss={() => {
            setNotice("");
            setNoticeKind("ok");
          }}
        />
      )}
    </>
  );
}

import { useEffect, useRef, useState } from "react";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { useViewport } from "../../shared/layout/useViewport";
import { ListView, ListFilters, ListFilter, Badge } from "../../shared/list/ListView";
import { Banner } from "../../shared/list/DetailPanel";
import { useListColumns } from "../../shared/list/useListPresentation";
import { KpiCards, KpiCard } from "../../shared/list/KpiCards";
import { FormulaBox, type FormulaRow } from "../../shared/list/FormulaBox";
import { MoneyUnitSelect } from "../../shared/list/MoneyUnitSelect";
import {
  DEFAULT_MONEY_UNIT,
  formatKoreanMoney,
  formatMoney,
  isNegativeMoney,
  type MoneyUnit,
} from "../../shared/list/moneyUnit";
import type { ListColumn, ListSort } from "../../shared/list/listTypes";
import { Toast } from "../../shared/feedback/Dialog";
import { getCommonCodes } from "../codegroups/codeGroups";
import { ProjectRequestError, typeLabel } from "../projects/projects";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import {
  formatMm,
  formatPercent,
  formatPoint,
  getCommonProjectCostAnalyses,
  getCommonProjectCostAnalysesExcel,
  type CommonProjectCostAnalysisResult,
  type CommonProjectCostAnalysisRow,
  type ProjectCostAnalysisPeriod,
} from "./projectCostAnalyses";
import { ProjectCostAnalysisDetail } from "./ProjectCostAnalysisDetail";
import "./projectCostAnalyses.css";

type Row = CommonProjectCostAnalysisRow;
const PERIOD_OPTIONS = { kind: "yearMonth" } as const;
const EMPTY_FILTER = {
  projectId: "",
  contractTypeCode: "",
  projectStatus: "",
  alertOnly: "",
};
const dash = <span className="ls-cap">–</span>;
const internal = (row: Row) => row.contractTypeCode === "IN";
const turnkey = (row: Row) => row.contractTypeCode === "TK";

/** 시안 pc(v): 마진율. 기준(기본 0)보다 낮으면 빨간 글씨. */
function rate(value: number | null, good = 0) {
  if (value == null) return dash;
  return <span className={value < good ? "ls-neg" : undefined}>{formatPercent(value)}</span>;
}

export function ProjectCostAnalysisPage() {
  const { mobile } = useViewport();
  const period = useScreenPeriod("UI-04", PERIOD_OPTIONS);
  const ready = period.state.status === "ready";
  const selectedPeriod: ProjectCostAnalysisPeriod | null =
    period.state.status === "ready"
      ? { year: period.state.value.year, baseMonth: period.state.value.month ?? 1 }
      : null;
  const [draft, setDraft] = useState(EMPTY_FILTER);
  const [filter, setFilter] = useState(EMPTY_FILTER);
  const [data, setData] = useState<CommonProjectCostAnalysisResult>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [retry, setRetry] = useState(0);
  const [optionsRetry, setOptionsRetry] = useState(0);
  const [types, setTypes] = useState<{ code: string; codeName: string }[]>([]);
  const [statuses, setStatuses] = useState<{ code: string; codeName: string }[]>([]);
  const [optionError, setOptionError] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [dense, setDense] = useState(false);
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [selected, setSelected] = useState<Row>();
  const [downloading, setDownloading] = useState(false);
  const [toast, setToast] = useState("");
  const form = useRef<HTMLElement>(null);
  const periodBox = useRef<HTMLDivElement>(null);
  const request = selectedPeriod && {
    ...selectedPeriod,
    projectId: filter.projectId,
    contractTypeCode: filter.contractTypeCode,
    projectStatus: filter.projectStatus,
    alertOnly: filter.alertOnly === "1" ? true : undefined,
  };
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
            failure instanceof Error ? failure.message : "조건을 불러오지 못했습니다.",
          );
      });
    return () => controller.abort();
  }, [optionsRetry]);
  const requestKey = request ? JSON.stringify(request) : "";
  useEffect(() => {
    if (!request) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setFields({});
    setSelected(undefined);
    setPage(1);
    getCommonProjectCostAnalyses(request, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((failure) => {
        if (controller.signal.aborted) return;
        setData(undefined);
        setError(failure instanceof Error ? failure.message : "분석을 불러오지 못했습니다.");
        const next =
          failure instanceof ProjectRequestError
            ? Object.fromEntries(failure.errors.map((item) => [item.field, item.message]))
            : {};
        setFields(next);
        const first = Object.keys(next)[0];
        // 기간 오류는 기간 상자로, 조건 오류는 그 조건 칸으로 옮겨 간다
        if (first === "year" || first === "baseMonth")
          periodBox.current?.querySelector<HTMLElement>("select")?.focus();
        else form.current?.querySelector<HTMLElement>(`[name="${first}"]`)?.focus();
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey, retry]);
  const money = (value: number | null) =>
    value == null ? (
      dash
    ) : (
      <span className={isNegativeMoney(value, unit) ? "ls-neg" : undefined}>
        {formatMoney(value, unit)}
      </span>
    );
  // 시안 19열(이름·순서 그대로). 시안 표는 정렬하지 않는다.
  const columnList: ListColumn<Row>[] = [
    { key: "projectName", label: "프로젝트", required: true, render: (r) => r.projectName },
    { key: "contractTypeName", label: "유형", render: (r) => <Badge>{typeLabel(r)}</Badge> },
    {
      key: "projectStatus",
      label: "상태",
      render: (r) => (
        <Badge kind={r.projectStatus === "진행" ? "info" : "lock"}>{r.projectStatus}</Badge>
      ),
    },
    {
      key: "originalContractAmount",
      label: "원 계약금액",
      align: "right",
      render: (r) => (internal(r) ? dash : money(r.originalContractAmount)),
    },
    {
      key: "contractAmount",
      label: "현재 계약금액",
      align: "right",
      render: (r) => (internal(r) ? dash : money(r.contractAmount)),
    },
    {
      key: "plannedMm",
      label: "계획 MM",
      align: "right",
      // 인원 계획이 없으면 「계획 없음」(명세 SFR-104-02)
      render: (r) => (internal(r) ? dash : r.plannedMm == null ? "계획 없음" : formatMm(r.plannedMm)),
    },
    { key: "actualMm", label: "실제 MM", align: "right", render: (r) => formatMm(r.actualMm) },
    {
      key: "cumulativeRevenue",
      label: "누적 매출",
      align: "right",
      calc: "턴키는 진행기준(원가÷예산), 월단위·파견은 귀속월 매출 누계",
      render: (r) => (internal(r) ? dash : money(r.cumulativeRevenue)),
    },
    {
      key: "totalCost",
      label: "누적 원가",
      align: "right",
      calc: "인건비 배분 원가 + 직접 경비(팀비·프로젝트경비·추가 직접 경비)",
      render: (r) => (
        <>
          {money(r.totalCost)}
          {internal(r) && <span className="ls-cap"> 연구개발비</span>}
        </>
      ),
    },
    {
      key: "cumulativeProfit",
      label: "이익",
      align: "right",
      render: (r) => (internal(r) ? dash : money(r.cumulativeProfit)),
    },
    {
      key: "cumulativeMarginRate",
      label: "누적 마진율",
      align: "right",
      render: (r) => rate(r.cumulativeMarginRate),
    },
    {
      key: "expectedFinalMarginRate",
      label: "예상 최종·월간 마진율",
      align: "right",
      calc: "턴키: (현재 계약금액 − 원가 누계 − 남은 기간 예상 원가) ÷ 현재 계약금액. 월단위·파견: 당월 마진율",
      render: (r) =>
        turnkey(r) ? (
          <span
            title={
              r.remainingCostBasis
                ? `남은 예상 원가 ${formatKoreanMoney(r.remainingCost)} · ${r.remainingCostBasis}`
                : undefined
            }
            data-testid={`project-cost-final-${r.projectId}`}
          >
            {rate(r.expectedFinalMarginRate)}
            {r.missingMonthlyCost && (
              <>
                {" "}
                <Badge kind="warn">단가 없음</Badge>
              </>
            )}
          </span>
        ) : r.monthlyMarginRate != null ? (
          <>
            {rate(r.monthlyMarginRate)} <span className="ls-cap">월간</span>
          </>
        ) : (
          dash
        ),
    },
    {
      key: "targetDifference",
      label: "목표 대비",
      align: "right",
      render: (r) =>
        r.targetDifference == null ? (
          dash
        ) : (
          <span className={r.targetDifference < -5 ? "ls-neg" : undefined}>
            {formatPoint(r.targetDifference)}
          </span>
        ),
    },
    {
      key: "budgetUsageRate",
      label: "예산 소진율",
      calc: "원가 누계 ÷ 예산(프로젝트별 입력값, 기본값 계약금액 × (1 − 목표 마진율))",
      render: (r) => {
        if (r.budgetUsageRate == null) return dash;
        const tone =
          r.budgetUsageRate >= 100 ? " is-bad" : r.budgetUsageRate >= 80 ? " is-warn" : "";
        return (
          <div className="project-cost-bar" data-testid={`project-cost-burn-${r.projectId}`}>
            <div className="project-cost-track">
              <div
                className={`project-cost-fill${tone}`}
                style={{ width: `${Math.min(100, r.budgetUsageRate)}%` }}
              />
            </div>
            <span className={`project-cost-burn${tone}`}>{formatPercent(r.budgetUsageRate)}</span>
          </div>
        );
      },
    },
    {
      key: "unitPricePerMm",
      label: "MM당 단가",
      align: "right",
      render: (r) => (internal(r) || r.unitPricePerMm == null ? dash : money(r.unitPricePerMm)),
    },
    {
      key: "costPerMm",
      label: "MM당 원가",
      align: "right",
      render: (r) => (internal(r) || r.costPerMm == null ? dash : money(r.costPerMm)),
    },
    {
      key: "profitPerMm",
      label: "MM당 이익",
      align: "right",
      render: (r) => (internal(r) || r.profitPerMm == null ? dash : money(r.profitPerMm)),
    },
    {
      key: "freeSupportMm",
      label: "무상지원 MM·원가",
      align: "right",
      render: (r) =>
        !internal(r) && r.freeSupportMm ? (
          <>
            {formatMm(r.freeSupportMm)} · {money(r.freeSupportCost)}
          </>
        ) : (
          dash
        ),
    },
    {
      key: "alerts",
      label: "경보",
      render: (r) =>
        r.alerts.length ? (
          <>
            {r.alerts.map((alert, index) => (
              <span key={alert}>
                {index > 0 && " "}
                <Badge kind={alert === "예산 초과" ? "bad" : "warn"}>{alert}</Badge>
              </span>
            ))}
            {r.primaryCauseName && r.primaryCauseAmount != null && (
              <>
                <br />
                <span className="ls-cap">
                  주원인: {r.primaryCauseName} ({formatKoreanMoney(r.primaryCauseAmount)})
                </span>
              </>
            )}
          </>
        ) : (
          dash
        ),
    },
  ];
  const columns = columnList.map((column) => ({ ...column, sortable: false }));
  const columnState = useListColumns("UI-04", columns);
  const rows = data?.items ?? [];
  // 시안 tfoot: 합계(내부 제외)
  const commercial = rows.filter((r) => !internal(r));
  const total = (pick: (r: Row) => number | null) =>
    commercial.reduce((sum, r) => sum + (pick(r) ?? 0), 0);
  const revenueTotal = total((r) => r.cumulativeRevenue);
  const profitTotal = total((r) => r.cumulativeProfit);
  const footer = {
    originalContractAmount: money(total((r) => r.originalContractAmount)),
    contractAmount: money(total((r) => r.contractAmount)),
    plannedMm: formatMm(total((r) => r.plannedMm)),
    actualMm: formatMm(total((r) => r.actualMm)),
    cumulativeRevenue: money(revenueTotal),
    totalCost: money(total((r) => r.totalCost)),
    cumulativeProfit: money(profitTotal),
    cumulativeMarginRate: formatPercent((profitTotal / Math.max(1, revenueTotal)) * 100),
  };
  async function download() {
    if (!request) return;
    setDownloading(true);
    try {
      await getCommonProjectCostAnalysesExcel(request);
      setToast("엑셀 파일을 다운로드했습니다.");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "엑셀을 받지 못했습니다.");
    } finally {
      setDownloading(false);
    }
  }
  const summary = data?.summary;
  const target = data?.targetMarginRate ?? 20;
  const monthLabel = selectedPeriod
    ? `${selectedPeriod.year}-${String(selectedPeriod.baseMonth).padStart(2, "0")}`
    : "";
  const periodError = fields.baseMonth ?? fields.year;
  const formulas: FormulaRow[] = [
    [
      "누적 매출",
      "턴키: 계약금액 × 진행률(진행률 = 누적 원가 ÷ 예산, 1 넘으면 1) · 월단위·파견: 매달 (월 단가 × MM)을 더한 누계",
      "종료 건은 100% · 수주예정은 0 · 내부 프로젝트는 매출 없음 · 원가 발생에 맞춰 매출을 인식하는 진행기준입니다(청구 시점과는 다를 수 있음)",
    ],
    [
      "누적 원가",
      "경과한 달마다 (배분 원가 + 직접 경비)를 더한 누계",
      "직접 경비 = 팀비·프로젝트경비·추가 직접 경비(출장비 등) · 배분 원가는 개인 원가·수익표와 같은 방식",
    ],
    ["이익 · 누적 마진율", "누적 매출 − 누적 원가 · 이익 ÷ 누적 매출 × 100", ""],
    [
      "예상 최종 마진율(턴키)",
      "(현재 계약금액 − 누적 원가 − 남은 예상 원가) ÷ 현재 계약금액 × 100",
      "남은 예상 원가: 투입 배정의 남은 달(다음 달 ~ 철수일, 프로젝트 종료일까지) MM × 그 사람 월 인건비. 월 인건비는 최근 월 인건비, 없으면 개발자 풀 월 급여, 없으면 배분표 금액 × 예산 ÷ 계약금액, 다 없으면 0(「단가 없음」 표시). 아직 배정하지 않은 사람·달은 들어가지 않습니다. 마진율 칸에 마우스를 올리면 내용이 보입니다",
    ],
    ["월간 마진율(월단위·파견)", "(이번 달 매출 − 배분 원가 − 직접 경비) ÷ 이번 달 매출 × 100", ""],
    ["목표 대비", `마진율 − 목표 마진율(${target}%)`, "단위 %p"],
    [
      "예산 · 예산 소진율",
      "예산(목표 원가) = 프로젝트 등록·수정 화면에서 입력한 값 · 소진율 = 누적 원가 ÷ 예산 × 100",
      "입력하지 않으면 기본값(계약금액 × (1 − 목표 마진율))을 씁니다 · 80% 이상 주황, 100% 이상 빨강",
    ],
    [
      "MM당 단가 · 원가 · 이익",
      "누적 매출 ÷ 실제 MM · 누적 원가 ÷ 실제 MM · 둘의 차",
      "실제 MM은 월별 MM 합. 엑셀 「턴키 계약목록」의 평균단가 − 평균급여를 대신함 — 엑셀은 평균급여에 월 급여만 넣어 이익이 이 값보다 크게 나옴",
    ],
    [
      "배분 기준 매출(턴키, 참고)",
      "그 달 배분표 합계",
      "배분표가 없으면 그 달 원가 × (현재 계약금액 ÷ 예산) — 위 누적 매출과 같은 기준(진행기준)을 월별로 나눈 것이라, 월별 금액을 다 더하면 누적 매출과 같아집니다. 상세 창 월별 원가 탭에 표시",
    ],
    ["경보", "저마진: 목표 대비 < −5%p · 예산 초과: 소진율 ≥ 100%", ""],
    [
      "시뮬레이션(가상)",
      "턴키: (계약금액 − 누적 원가 − 남은 원가 − 인원 × 1인 월 원가 × 개월) ÷ 계약금액 · 월단위·파견: (매출 + 인원 × 단가 − 원가 − 인원 × 1인 월 원가) ÷ (매출 + 인원 × 단가)",
      "결과는 저장되지 않음",
    ],
  ];
  return (
    <section
      ref={form}
      className="screen-body project-cost-analysis-page"
      data-testid="project-cost-analysis-page"
      data-screen-id="UI-04"
      aria-label="프로젝트 원가 분석"
    >
      <div ref={periodBox} data-testid="project-cost-period">
        {period.state.status === "ready" ? (
          <PeriodSelector
            basis={period.state.basis}
            value={period.state.value}
            onChange={period.setValue}
            mobile={mobile}
            variant="monthBox"
            {...PERIOD_OPTIONS}
          />
        ) : period.state.status === "error" ? (
          <p role="alert">{period.state.message}</p>
        ) : (
          <p role="status">기간을 불러오는 중입니다.</p>
        )}
        {periodError && (
          <p role="alert" className="project-cost-field-error">
            {periodError}
          </p>
        )}
      </div>
      <KpiCards aria-label="기준월 전체 프로젝트 요약" className="project-cost-summary" testId="project-cost-summary">
        <KpiCard
          label="누적 매출"
          value={summary ? formatKoreanMoney(summary.cumulativeRevenue) : "–"}
          description="진행·종료 프로젝트"
        />
        <KpiCard
          label="누적 이익"
          value={summary ? formatKoreanMoney(summary.cumulativeProfit) : "–"}
          description="내부 프로젝트 제외"
        />
        <KpiCard
          label="저마진 경보"
          value={summary ? `${summary.lowMarginCount}건` : "–"}
          description="목표 마진율보다 5%p 넘게 낮음"
        />
        <KpiCard
          label="예산 초과"
          value={summary ? `${summary.budgetExceededCount}건` : "–"}
          description="예산 소진율 100% 이상"
        />
        <KpiCard
          label="연구개발비 누계"
          value={summary ? formatKoreanMoney(summary.researchCost) : "–"}
          description="내부(자체 개발) 프로젝트"
        />
      </KpiCards>
      <ListFilters
        onSearch={() => {
          setFilter({ ...draft });
          setRetry((value) => value + 1);
        }}
        onReset={() => {
          setDraft(EMPTY_FILTER);
          setFilter(EMPTY_FILTER);
        }}
      >
        <ListFilter label="프로젝트명" htmlFor="project-cost-project">
          <select
            id="project-cost-project"
            className="ls-in"
            name="projectId"
            value={draft.projectId}
            onChange={(event) => setDraft({ ...draft, projectId: event.target.value })}
          >
            <option value="">전체</option>
            {(data?.projectOptions ?? []).map((item) => (
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
            className="ls-in"
            name="contractTypeCode"
            value={draft.contractTypeCode}
            onChange={(event) => setDraft({ ...draft, contractTypeCode: event.target.value })}
          >
            <option value="">전체</option>
            {types.map((item) => (
              <option key={item.code} value={item.code}>
                {item.codeName}
              </option>
            ))}
          </select>
          {fields.contractTypeCode && <span role="alert">{fields.contractTypeCode}</span>}
        </ListFilter>
        <ListFilter label="상태" htmlFor="project-cost-status">
          <select
            id="project-cost-status"
            className="ls-in"
            name="projectStatus"
            value={draft.projectStatus}
            onChange={(event) => setDraft({ ...draft, projectStatus: event.target.value })}
          >
            <option value="">전체</option>
            {statuses.map((item) => (
              <option key={item.code} value={item.code === "ING" ? "진행" : "종료"}>
                {item.codeName}
              </option>
            ))}
          </select>
          {fields.projectStatus && <span role="alert">{fields.projectStatus}</span>}
        </ListFilter>
        <ListFilter label="보기" htmlFor="project-cost-alert">
          <select
            id="project-cost-alert"
            className="ls-in"
            name="alertOnly"
            value={draft.alertOnly}
            onChange={(event) => setDraft({ ...draft, alertOnly: event.target.value })}
          >
            <option value="">전체</option>
            <option value="1">경보 있는 것만</option>
          </select>
        </ListFilter>
      </ListFilters>
      {optionError && (
        <p role="alert">
          {optionError}
          <button type="button" className="ls-btn ls-btn-sm" onClick={() => setOptionsRetry((value) => value + 1)}>
            조건 다시 시도
          </button>
        </p>
      )}
      {data?.monthlyMarginNotice && (
        <div data-testid="project-cost-monthly-notice">
          <Banner kind="info">{data.monthlyMarginNotice}</Banner>
        </div>
      )}
      <ListView
        screenId="UI-04"
        columns={columns}
        visibleColumns={columnState.visible}
        hiddenColumns={columnState.hidden}
        onToggleColumn={columnState.toggle}
        onResetColumns={columnState.reset}
        rows={loading || !ready ? null : error && !data ? null : rows.slice((page - 1) * pageSize, page * pageSize)}
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
        onDownload={ready && !loading && !error ? () => void download() : undefined}
        downloading={downloading}
        error={error}
        onRetry={() => setRetry((value) => value + 1)}
        testId="project-cost-list"
        stickyFirstColumn
        footer={footer}
        footerLabel="합계(내부 제외)"
        toolbarLeft={
          <span data-testid="project-cost-toolbar">
            {monthLabel} 기준 · 총 <strong>{data?.totalCount ?? 0}</strong>건 · 행을 누르면 월별 원가·시뮬레이션
          </span>
        }
        toolbarRight={<MoneyUnitSelect value={unit} onChange={setUnit} id="project-cost-unit" />}
      />
      <FormulaBox
        testId="project-cost-formulas"
        rows={formulas}
        foot={`목표 마진율 ${target}%는 기준정보 값입니다. 내부(자체 개발) 프로젝트는 매출이 없으므로 실제 MM과 연구개발비 누계만 봅니다.`}
      />
      {selected && selectedPeriod && (
        <ProjectCostAnalysisDetail
          key={`${selected.projectId}-${selectedPeriod.year}-${selectedPeriod.baseMonth}`}
          row={selected}
          period={selectedPeriod}
          targetMarginRate={target}
          monthlyMarginNotice={data?.monthlyMarginNotice ?? null}
          onClose={() => setSelected(undefined)}
        />
      )}
      {toast && <Toast message={toast} onDismiss={() => setToast("")} />}
    </section>
  );
}

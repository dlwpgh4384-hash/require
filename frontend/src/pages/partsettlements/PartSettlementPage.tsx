import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  ConfirmDialog,
  InlineConfirmation,
  Toast,
  type ToastKind,
} from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { ShellIcon } from "../../shared/layout/ShellIcon";
import {
  Banner,
  DEFAULT_MONEY_UNIT,
  FormulaBox,
  KpiCard,
  KpiCards,
  ListView,
  useListColumns,
  useListPresentation,
  Money,
  MoneyUnitSelect,
  formatKoreanMoney,
  type ListColumn,
  type ListSort,
  type MoneyUnit,
} from "../../shared/list";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { formatDateTime } from "../../shared/time";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { getCommonCodes } from "../codegroups/codeGroups";
import { ProjectRequestError, isProjectConflict } from "../projects/projects";
import * as api from "./partSettlements";
import { usePartSettlementTitleHost } from "./PartSettlementWorkspace";
import { PartSettlementTrend, NoteText, mmText } from "./PartSettlementTrend";
import { PartSettlementCancelDialog } from "./PartSettlementCancelDialog";
import {
  partSettlementFormulaFoot,
  partSettlementFormulas,
} from "./partSettlementFormulas";
import "./partSettlements.css";

const message = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "파트 이익금을 불러오지 못했습니다. 다시 시도해 주세요.";
/** 시안 빈 안내: 굵은 한 줄 + 흐린 설명(파트장 정하는 곳까지). */
const noPartsDetail =
  "본사(직접 영업)는 파트 이익금 정산 대상이 아닙니다. 영업 파트의 파트장은 기준정보 › 거래처·영업 파트의 영업 파트 탭에서 정합니다.";
const won = (value: number) => value.toLocaleString("ko-KR");
/** 법인카드 칸: 숫자만이면 천 단위 쉼표를 붙여 보인다(시안 toLocaleString). */
const withCommas = (value: string) =>
  /^\d+$/.test(value) ? value.replace(/\B(?=(\d{3})+(?!\d))/g, ",") : value;
const moneyKeys = [
  "unitPrice",
  "salary",
  "salesExpense",
  "retirementAmount",
  "totalCost",
  "profit",
  "profitShare",
] as const;
type MoneyKey = (typeof moneyKeys)[number];

/** UI-19 원가·수익 › 영업 파트 이익금(시안 SCR['UI-19']). */
export function PartSettlementPage() {
  const period = useScreenPeriod("UI-19", { kind: "yearMonth" });
  const yearMonth =
    period.state.status === "ready"
      ? `${period.state.value.year}-${String(period.state.value.month).padStart(2, "0")}`
      : "";
  const host = usePartSettlementTitleHost();
  const [tabs, setTabs] = useState<api.PartSettlementTabs | null>(null);
  const [partId, setPartId] = useState("");
  const [detail, setDetail] = useState<api.PartSettlement | null>(null);
  const [trend, setTrend] = useState<api.PartSettlementTrends | null>(null);
  const [view, setView] = useState<"monthly" | "trend">("monthly");
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [employeeTypes, setEmployeeTypes] = useState<Record<string, string>>(
    {},
  );
  const [card, setCard] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  /** 탭 목록을 받은 다시 읽기 번호: 다시 읽기 뒤 탭을 받았을 때만 상세를 한 번 읽는다. */
  const [tabsRevision, setTabsRevision] = useState(0);
  const [developerId, setDeveloperId] = useState<string | null>(null);
  const [pending, setPending] = useState<(() => void) | null>(null);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const cardRef = useRef<HTMLInputElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const saving = useRef(false);
  // 저장 뒤 다시 읽은 기간 추이가 늦게 와도, 그 사이 바꾼 파트·달·보기를 덮지 않게 지금 값을 둔다.
  const current = useRef("");
  current.current = `${partId}|${yearMonth}|${view}`;
  const setDirty = useScreenDirty();
  const cardChanged = !!detail && card !== String(detail.corpCardAmount);
  // 저장 중(칸을 벗어나 저장이 시작된 뒤)에는 나가기 확인을 묻지 않는다.
  const dirty = !busy && (cardChanged || (cancelOpen && reason.length > 0));
  useEffect(() => {
    setDirty("UI-19", dirty);
    return () => setDirty("UI-19", false);
  }, [dirty, setDirty]);
  const guard = (action: () => void) =>
    dirty ? setPending(() => action) : action();
  const retry = useCallback(() => setRevision((value) => value + 1), []);
  const toast = (text: string, kind: ToastKind = "ok") => {
    setNoticeKind(kind);
    setNotice(text);
  };
  // 구분 칸: 공통코드 EMP_TYPE 이름(받지 못하면 코드 그대로)
  useEffect(() => {
    const controller = new AbortController();
    getCommonCodes("EMP_TYPE", controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setEmployeeTypes(
          Object.fromEntries(
            (response.items ?? []).map((item) => [item.code, item.codeName]),
          ),
        );
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (!yearMonth) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setDetail(null);
    setTrend(null);
    setTabs(null);
    api
      .getCommonPartSettlements(yearMonth, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setTabs(response.data);
        setTabsRevision(revision);
        const items = response.data.items ?? [];
        setPartId((previous) =>
          items.some((item) => item.partId === previous)
            ? previous
            : (items[0]?.partId ?? ""),
        );
        if (!items.length) setLoading(false);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setError(message(failure));
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [yearMonth, revision]);
  // 저장 뒤 요약만 바꾼 탭(setTabs)으로 상세를 다시 읽지 않도록, 탭을 받았는지만 본다.
  const tabsReady = !!tabs;
  useEffect(() => {
    if (!yearMonth || !partId || !tabsReady) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setDetail(null);
    setTrend(null);
    setPage(1);
    setFieldErrors({});
    Promise.all([
      api.getCommonPartSettlement(partId, yearMonth, controller.signal),
      view === "trend"
        ? api.getCommonPartSettlementTrends(
            partId,
            yearMonth,
            controller.signal,
          )
        : Promise.resolve(null),
    ])
      .then(([response, trends]) => {
        if (controller.signal.aborted) return;
        setDetail(response.data);
        setCard(String(response.data.corpCardAmount));
        setTrend(trends?.data ?? null);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) setError(message(failure));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
    // 다시 읽기(409·재시도): revision이 아니라 탭을 받은 뒤의 tabsRevision으로 다시 읽는다.
    // (탭 응답이 빨라 tabsReady가 그대로여도 다시 읽고, 탭을 받기 전에 곧 취소될 상세 요청은 보내지 않는다.)
  }, [yearMonth, partId, tabsReady, view, tabsRevision]);
  const locked =
    !detail || detail.confirmed || detail.closed || busy || loading;
  const cardReadOnly = !detail || detail.confirmed || detail.closed;
  // 칸 오류는 칸 아래에, 그 밖(409 등)은 알림으로 보인다.
  // 409(FR-0106)는 알리고 다시 읽는다. 칸이 없는 오류(마감된 달 등)도 알림으로 보인다.
  const failure = (cause: unknown, focus: boolean) => {
    if (isProjectConflict(cause)) {
      toast(message(cause), "warn");
      setConfirmOpen(false);
      setCancelOpen(false);
      setReason("");
      retry();
      return;
    }
    const shown =
      cause instanceof ProjectRequestError
        ? cause.errors.filter(
            (item) =>
              item.field === "corpCardAmount" || item.field === "cancelReason",
          )
        : [];
    if (shown.length) {
      setFieldErrors(
        Object.fromEntries(shown.map((item) => [item.field, item.message])),
      );
      const first = shown[0]?.field;
      if (focus && first === "corpCardAmount") cardRef.current?.focus();
      if (first === "cancelReason") reasonRef.current?.focus();
      return;
    }
    toast(message(cause), "warn");
  };
  const command = async (
    action: "card" | "confirm" | "cancel",
    focus = true,
  ) => {
    if (!detail || saving.current) return;
    setFieldErrors({});
    if (action === "card" && (!/^\d+$/.test(card) || card.length > 15)) {
      setFieldErrors({
        corpCardAmount:
          "법인카드 사용액은 0~999,999,999,999,999 사이의 정수로 입력해 주세요.",
      });
      if (focus) cardRef.current?.focus();
      return;
    }
    if (action === "cancel" && !reason.trim()) {
      setFieldErrors({ cancelReason: "취소 사유를 입력해 주세요." });
      reasonRef.current?.focus();
      return;
    }
    saving.current = true;
    setBusy(true);
    try {
      const result =
        action === "card"
          ? await api.putCommonPartSettlementCard(
              partId,
              yearMonth,
              Number(card),
              detail.modifiedAt,
            )
          : action === "confirm"
            ? await api.putCommonPartSettlementConfirm(
                partId,
                yearMonth,
                detail.modifiedAt,
              )
            : await api.putCommonPartSettlementCancel(
                partId,
                yearMonth,
                reason,
                detail.modifiedAt,
              );
      setDetail(result.data);
      setCard(String(result.data.corpCardAmount));
      // 기간 추이를 보고 있으면 맨 아래 파트 이익금(법인카드 차감)도 새 값으로 다시 읽는다.
      if (view === "trend") {
        const asked = `${partId}|${yearMonth}|trend`;
        void api
          .getCommonPartSettlementTrends(partId, yearMonth)
          .then((response) => {
            if (current.current === asked) setTrend(response.data);
          })
          .catch(() => undefined);
      }
      setCancelOpen(false);
      setReason("");
      toast(
        action === "card"
          ? "법인카드 사용액을 저장했습니다."
          : action === "confirm"
            ? "정산을 확정했습니다."
            : "정산 확정을 취소했습니다.",
      );
      setTabs((previous) =>
        previous
          ? {
              ...previous,
              items: (previous.items ?? []).map((item) =>
                item.partId === partId
                  ? { ...item, summary: result.data.summary }
                  : item,
              ),
            }
          : previous,
      );
    } catch (cause) {
      failure(cause, focus);
    } finally {
      saving.current = false;
      setBusy(false);
    }
  };
  // 시안: 칸을 벗어나거나 Enter를 누르면 바뀐 값만 저장한다.
  const saveCard = (focus: boolean) => {
    if (cardChanged && !locked) void command("card", focus);
  };
  const turnkey = new Set(detail?.turnkeyDeveloperIds ?? []);
  const moneyColumn = (
    key: MoneyKey,
    label: string,
    calc?: string,
  ): ListColumn<api.PartSettlementRow> => ({
    key,
    label,
    align: "right",
    calc,
    render: (row) => <Money value={row[key]} unit={unit} />,
  });
  const columns: ListColumn<api.PartSettlementRow>[] = [
    {
      key: "developerName",
      label: "이름",
      required: true,
      render: (row) => (
        <button
          type="button"
          className="part-settlement-name"
          data-testid={`part-settlement-person-${row.developerId}`}
          onClick={(event) => {
            event.stopPropagation();
            guard(() => setDeveloperId(row.developerId));
          }}
        >
          {row.developerName}
        </button>
      ),
    },
    {
      key: "employeeTypeCode",
      label: "구분",
      render: (row) =>
        employeeTypes[row.employeeTypeCode] ?? (row.employeeTypeCode || "–"),
    },
    {
      key: "projectNames",
      label: "프로젝트",
      render: (row) => {
        const names = row.projectNames ?? [];
        return (
          <>
            <span title={names.join(", ")}>
              {names[0] ?? ""}
              {names.length > 1 && (
                <>
                  {" "}
                  <span className="part-settlement-sub">
                    외 {names.length - 1}건
                  </span>
                </>
              )}
            </span>
            {turnkey.has(row.developerId) && (
              <>
                {" "}
                <span className="ls-cap">턴키</span>
              </>
            )}
          </>
        );
      },
    },
    {
      key: "monthlyMm",
      label: "MM",
      align: "right",
      render: (row) => mmText(row.monthlyMm),
    },
    moneyColumn("unitPrice", "단가"),
    moneyColumn("salary", "급여"),
    moneyColumn("salesExpense", "공통관리비"),
    moneyColumn("retirementAmount", "퇴직금"),
    moneyColumn("totalCost", "비용 계", "급여 + 공통관리비 + 퇴직금"),
    moneyColumn("profit", "이익", "단가 − 비용 계 (적자면 음수)"),
    moneyColumn(
      "profitShare",
      `이익금(${detail?.summary.distributionRate ?? "–"}%)`,
    ),
    {
      key: "notes",
      label: "비고",
      sortable: false,
      render: (row) => (
        <span className="ls-cap">
          <NoteText notes={row.notes} />
        </span>
      ),
    },
  ];
  // 열 설정·쪽당 건수·좁게 보기는 공통 사용자 설정에 저장한다(다른 목록 화면과 같음).
  const columnState = useListColumns("UI-19", columns);
  const presentation = useListPresentation();
  const items = detail?.items ?? [];
  const rows = [...items];
  if (sort)
    rows.sort((a, b) => {
      const left = a[sort.key as keyof api.PartSettlementRow],
        right = b[sort.key as keyof api.PartSettlementRow];
      return (
        (typeof left === "number" && typeof right === "number"
          ? left - right
          : String(left).localeCompare(String(right), "ko")) *
        (sort.dir === "asc" ? 1 : -1)
      );
    });
  const sum = (key: "monthlyMm" | MoneyKey) =>
    items.reduce((total, row) => total + (Number(row[key]) || 0), 0);
  // 시안 합계 줄: 숫자 열을 모두 더한다(이익금도 열 합계 그대로, 법인카드는 빼지 않음)
  const footer = {
    monthlyMm: mmText(sum("monthlyMm")),
    ...Object.fromEntries(
      moneyKeys.map((key) => [
        key,
        <Money key={key} value={sum(key)} unit={unit} />,
      ]),
    ),
  };
  const notesById = Object.fromEntries(
    items.map((row) => [row.developerId, row.notes ?? []]),
  );
  const tab = tabs?.items?.find((item) => item.partId === partId);
  const summary = detail?.summary ?? tab?.summary;
  const partName = detail?.partName ?? tab?.partName ?? "";
  const closeCancel = () =>
    guard(() => {
      setCancelOpen(false);
      setReason("");
      setFieldErrors({});
    });
  const download =
    partId && !loading && !error
      ? () => {
          void api
            .getCommonPartSettlementsExcel(partId, yearMonth, view, columnState.visible.map((column) => column.key))
            .catch((cause) => toast(message(cause), "warn"));
        }
      : undefined;
  const unitSelect = (
    <MoneyUnitSelect
      value={unit}
      onChange={setUnit}
      id="part-settlement-money-unit"
    />
  );
  // 시안: 마감한 달은 확정된 것으로 보고 띠를 보인다(마감 시 자동 확정).
  const lockedMonth = !!detail && (detail.confirmed || detail.closed);
  // 시안 actions: 마감 전이면 [정산 확정] 또는 [확정 취소], 마감한 달은 단추 없음. 엑셀은 목록 도구줄에만.
  const titleActions =
    host && detail && !detail.closed
      ? createPortal(
          detail.confirmed ? (
            <button
              type="button"
              className="ls-btn ls-btn-dt"
              data-testid="part-settlement-cancel-open"
              disabled={busy || loading}
              onClick={() =>
                guard(() => {
                  setFieldErrors({});
                  setReason("");
                  setCancelOpen(true);
                })
              }
            >
              <ShellIcon name="lock" size={16} /> 확정 취소
            </button>
          ) : (
            <button
              type="button"
              className="ls-btn ls-btn-p"
              data-testid="part-settlement-confirm"
              disabled={busy || loading}
              onClick={() => guard(() => setConfirmOpen(true))}
            >
              정산 확정
            </button>
          ),
          host,
        )
      : null;
  return (
    <section
      className="screen-body part-settlement-page"
      data-testid="part-settlement-page"
      data-screen-id="UI-19"
      aria-label="영업 파트 이익금 작업 공간"
    >
      {titleActions}
      <div
        className="part-settlement-conditions"
        data-testid="part-settlement-conditions"
      >
        {period.state.status === "ready" && (
          <PeriodSelector
            kind="yearMonth"
            variant="monthBox"
            basis={period.state.basis}
            value={period.state.value}
            disabled={busy}
            onChange={(value) => guard(() => period.setValue(value))}
          />
        )}
        {period.state.status === "error" && (
          <p role="alert">{period.state.message}</p>
        )}
        {period.state.status === "loading" && (
          <p role="status">기간을 불러오는 중입니다.</p>
        )}
      </div>
      {lockedMonth && detail && (
        <Banner kind="lock" icon={<ShellIcon name="lock" size={18} />}>
          <span data-testid="part-settlement-banner">
            {yearMonth} {detail.partName} 정산은 <strong>확정</strong>되었습니다
            ({detail.confirmedByName ?? detail.confirmedBy ?? "–"} ·{" "}
            {detail.confirmationNote ?? formatDateTime(detail.confirmedAt)}).
            확정 후에는 고칠 수 없습니다.
            {detail.closed
              ? ""
              : " 고쳐야 하면 [확정 취소]를 누르고 사유를 남깁니다."}
          </span>
        </Banner>
      )}
      <div
        role="tablist"
        aria-label="영업 파트"
        className="ls-tabs part-settlement-tabs"
      >
        {(tabs?.items ?? []).map((item) => (
          <button
            type="button"
            role="tab"
            className={`ls-tb${partId === item.partId ? " ls-on" : ""}`}
            aria-selected={partId === item.partId}
            data-testid={`part-settlement-tab-${item.partId}`}
            key={item.partId}
            onClick={() => guard(() => setPartId(item.partId))}
            disabled={busy}
          >
            {item.partName}
          </button>
        ))}
      </div>
      {tabs && !(tabs.items ?? []).length && (
        <div className="ls-empty" data-testid="part-settlement-no-parts">
          <p>
            <strong>맡은 영업 파트가 없습니다.</strong>
          </p>
          <p className="part-settlement-sub">{noPartsDetail}</p>
        </div>
      )}
      {error && (
        <p role="alert" data-testid="part-settlement-error">
          {error}
        </p>
      )}
      <KpiCards
        aria-label="파트 이익금 요약"
        testId="part-settlement-summary"
      >
        <KpiCard
          label="파트장"
          value={summary?.leaderName ?? "–"}
          description={
            summary
              ? `이익 배분율 ${summary.distributionRate}% · 공통관리비 ${won(summary.salesExpense)}원`
              : "–"
          }
        />
        <KpiCard
          label="인원"
          value={summary ? `${summary.personCount}명` : "–"}
          description="이번 달 투입 인력"
        />
        <KpiCard
          label="이익 합계"
          value={summary ? formatKoreanMoney(summary.profitTotal) : "–"}
          description="단가 − (급여 + 공통관리비 + 퇴직금)"
        />
        <KpiCard
          label="파트 이익금"
          value={summary ? formatKoreanMoney(summary.partProfitAmount) : "–"}
          description={
            detail
              ? `인력별 이익금 ${formatKoreanMoney(sum("profitShare"))} − 법인카드 ${won(detail.corpCardAmount)}원`
              : "–"
          }
        />
      </KpiCards>
      <div
        className="part-settlement-card"
        data-testid="part-settlement-card-box"
      >
        <div className="part-settlement-fld">
          <label className="part-settlement-fl" htmlFor="part-settlement-card">
            법인카드 사용액
          </label>
          <div className="part-settlement-fc">
            <div className="part-settlement-row">
              <input
                ref={cardRef}
                id="part-settlement-card"
                className="ls-in part-settlement-money"
                data-testid="part-settlement-card"
                value={withCommas(card)}
                inputMode="numeric"
                readOnly={cardReadOnly}
                aria-invalid={!!fieldErrors.corpCardAmount}
                aria-describedby="part-settlement-card-error part-settlement-card-help"
                onChange={(event) =>
                  setCard(event.target.value.replace(/,/g, ""))
                }
                onBlur={() => saveCard(false)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    saveCard(true);
                  }
                }}
              />
              <span className="part-settlement-sub">원</span>
            </div>
            {fieldErrors.corpCardAmount && (
              <span
                id="part-settlement-card-error"
                className="field-error ls-neg"
              >
                {fieldErrors.corpCardAmount}
              </span>
            )}
            <span
              id="part-settlement-card-help"
              className="part-settlement-help"
            >
              파트 이익금에서 뺍니다(엑셀 「원가표」의 &apos;법카 사용 별도&apos;).
            </span>
          </div>
        </div>
      </div>
      <div
        role="tablist"
        aria-label="정산 보기"
        className="ls-tabs part-settlement-tabs"
      >
        <button
          type="button"
          role="tab"
          className={`ls-tb${view === "monthly" ? " ls-on" : ""}`}
          aria-selected={view === "monthly"}
          data-testid="part-settlement-monthly"
          onClick={() => guard(() => setView("monthly"))}
        >
          이번 달
        </button>
        <button
          type="button"
          role="tab"
          className={`ls-tb${view === "trend" ? " ls-on" : ""}`}
          aria-selected={view === "trend"}
          data-testid="part-settlement-trend"
          onClick={() => guard(() => setView("trend"))}
        >
          기간 추이 (1월~{yearMonth.slice(5)}월)
        </button>
      </div>
      {view === "monthly" ? (
        <div role="tabpanel" data-testid="part-settlement-monthly-panel">
          <ListView
            screenId="UI-19"
            columns={columns}
            visibleColumns={columnState.visible}
            hiddenColumns={columnState.hidden}
            onToggleColumn={columnState.toggle}
            onResetColumns={columnState.reset}
            rows={
              loading || error
                ? null
                : rows.slice(
                    (page - 1) * presentation.pageSize,
                    page * presentation.pageSize,
                  )
            }
            rowKey={(row) => row.developerId}
            total={rows.length}
            page={page}
            pageSize={presentation.pageSize}
            onPage={setPage}
            onPageSize={(value) => {
              presentation.setPageSize(value);
              setPage(1);
            }}
            dense={presentation.dense}
            onDense={presentation.setDense}
            sort={sort}
            onSort={setSort}
            error={error}
            onRetry={() => guard(retry)}
            onRowClick={(row) => guard(() => setDeveloperId(row.developerId))}
            toolbarLeft={
              <span data-testid="part-settlement-toolbar">
                {yearMonth} · {partName} · <strong>{items.length}</strong>명
              </span>
            }
            toolbarRight={unitSelect}
            downloading={!download}
            onDownload={() => download?.()}
            footer={footer}
            stickyFirstColumn
            testId="part-settlement-list"
          />
        </div>
      ) : (
        <div role="tabpanel" data-testid="part-settlement-trend-panel">
          <PartSettlementTrend
            trend={trend}
            yearMonth={yearMonth}
            partName={partName}
            unit={unit}
            notes={notesById}
            loading={loading}
            error={error}
            unitSelect={unitSelect}
            onDownload={() => download?.()}
          />
        </div>
      )}
      <FormulaBox
        testId="part-settlement-formulas"
        rows={partSettlementFormulas(
          summary?.distributionRate,
          summary?.salesExpense,
        )}
        foot={partSettlementFormulaFoot}
      />
      {confirmOpen && detail && (
        <ConfirmDialog
          title={`${detail.partName} ${yearMonth} 정산을 확정할까요?`}
          description="확정하면 이 달 파트 정산(법인카드 사용액 포함)이 잠깁니다."
          proceedLabel="확정하기"
          testId="part-settlement-confirm-dialog"
          onCancel={() => setConfirmOpen(false)}
          onProceed={() => {
            setConfirmOpen(false);
            void command("confirm");
          }}
        />
      )}
      {cancelOpen && (
        <PartSettlementCancelDialog
          reason={reason}
          error={fieldErrors.cancelReason}
          busy={busy}
          reasonRef={reasonRef}
          onReason={setReason}
          onClose={closeCancel}
          onSubmit={() => void command("cancel")}
        />
      )}
      {developerId && (
        <DeveloperDetail
          developerId={developerId}
          readOnly
          onClose={() => guard(() => setDeveloperId(null))}
        />
      )}
      {pending && (
        <InlineConfirmation
          title="저장하지 않은 내용이 있습니다"
          description="나가면 입력한 내용이 사라집니다."
          testId="part-settlement-dirty"
          onCancel={() => setPending(null)}
          onProceed={() => {
            const action = pending;
            setPending(null);
            setCard(String(detail?.corpCardAmount ?? ""));
            setReason("");
            setFieldErrors({});
            action();
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          testId="part-settlement-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

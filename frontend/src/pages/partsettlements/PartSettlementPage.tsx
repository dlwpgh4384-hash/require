import { useCallback, useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { FormulaBox } from "../../shared/list/FormulaBox";
import { KpiCard, KpiCards } from "../../shared/list/KpiCards";
import { ListView } from "../../shared/list/ListView";
import type { ListColumn, ListSort } from "../../shared/list/listTypes";
import { MoneyUnitSelect } from "../../shared/list/MoneyUnitSelect";
import {
  DEFAULT_MONEY_UNIT,
  formatMoney,
  type MoneyUnit,
} from "../../shared/list/moneyUnit";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { ProjectRequestError } from "../projects/projects";
import * as api from "./partSettlements";
import "./partSettlements.css";

const message = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "파트 이익금을 불러오지 못했습니다. 다시 시도해 주세요.";
const noParts =
  "맡은 영업 파트가 없습니다. 본사(직접 영업)는 파트 이익금 정산 대상이 아닙니다.";
type Metric =
  | "monthlyMm"
  | "unitPrice"
  | "totalCost"
  | "profit"
  | "profitShare";
const metrics: [Metric, string][] = [
  ["monthlyMm", "M/M"],
  ["unitPrice", "단가"],
  ["totalCost", "비용"],
  ["profit", "이익"],
  ["profitShare", "이익금"],
];

export function PartSettlementPage() {
  const period = useScreenPeriod("UI-19", { kind: "yearMonth" });
  const yearMonth =
    period.state.status === "ready"
      ? `${period.state.value.year}-${String(period.state.value.month).padStart(2, "0")}`
      : "";
  const [tabs, setTabs] = useState<api.PartSettlementTabs | null>(null);
  const [partId, setPartId] = useState("");
  const [detail, setDetail] = useState<api.PartSettlement | null>(null);
  const [trend, setTrend] = useState<api.PartSettlementTrends | null>(null);
  const [definitions, setDefinitions] = useState<
    api.PartSettlementDefinition[]
  >([]);
  const [view, setView] = useState<"monthly" | "trend">("monthly");
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [card, setCard] = useState("");
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const [developerId, setDeveloperId] = useState<string | null>(null);
  const [pending, setPending] = useState<(() => void) | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [dense, setDense] = useState(false);
  const [hidden, setHidden] = useState<string[]>([]);
  const cardRef = useRef<HTMLInputElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const setDirty = useScreenDirty();
  const dirty =
    (!!detail && card !== String(detail.corpCardAmount)) ||
    (cancelOpen && reason.length > 0);
  useEffect(() => {
    setDirty("UI-19", dirty);
    return () => setDirty("UI-19", false);
  }, [dirty, setDirty]);
  const guard = (action: () => void) =>
    dirty ? setPending(() => action) : action();
  const retry = useCallback(() => setRevision((value) => value + 1), []);
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
        setDefinitions(response.meta?.calculationRules ?? []);
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
  useEffect(() => {
    if (!yearMonth || !partId || !tabs) return;
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
        setDefinitions(response.meta?.calculationRules ?? []);
        setTrend(trends?.data ?? null);
      })
      .catch((failure) => {
        if (!controller.signal.aborted) setError(message(failure));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [yearMonth, partId, tabs, view]);
  const failure = (cause: unknown) => {
    setError(message(cause));
    if (cause instanceof ProjectRequestError) {
      setFieldErrors(
        Object.fromEntries(
          cause.errors.map((item) => [item.field, item.message]),
        ),
      );
      const first = cause.errors[0]?.field;
      if (first === "corpCardAmount") cardRef.current?.focus();
      if (first === "cancelReason") reasonRef.current?.focus();
    }
  };
  const command = async (action: "card" | "confirm" | "cancel") => {
    if (!detail || busy) return;
    setFieldErrors({});
    setError("");
    setNotice("");
    if (
      action === "card" &&
      (!/^\d+$/.test(card) || BigInt(card) > 999999999999999n)
    ) {
      setFieldErrors({
        corpCardAmount:
          "법인카드 금액은 0~999999999999999 사이의 정수로 입력해 주세요.",
      });
      cardRef.current?.focus();
      return;
    }
    if (action === "cancel" && (!reason.trim() || [...reason].length > 500)) {
      setFieldErrors({
        cancelReason: "확정 취소 사유를 1~500자로 입력해 주세요.",
      });
      reasonRef.current?.focus();
      return;
    }
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
      setCancelOpen(false);
      setReason("");
      setNotice(
        action === "card"
          ? "법인카드 금액을 저장했습니다."
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
      failure(cause);
    } finally {
      setBusy(false);
    }
  };
  const money = (value: number) => formatMoney(value, unit);
  const formula = (id: string) =>
    definitions.find((item) => item.calcId === id)?.formula ??
    "계산 정의를 불러올 수 없습니다.";
  const columns: ListColumn<api.PartSettlementRow>[] = [
    {
      key: "developerName",
      label: "이름",
      required: true,
      render: (row) => (
        <button
          type="button"
          className="ls-link"
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
    { key: "employeeTypeCode", label: "직원구분" },
    {
      key: "projectNames",
      label: "프로젝트",
      render: (row) => (
        <span title={(row.projectNames ?? []).join(", ")}>
          {row.projectNames?.[0] ?? "–"}
          {row.projectNames?.length > 1
            ? ` 외 ${row.projectNames.length - 1}건`
            : ""}
        </span>
      ),
    },
    { key: "monthlyMm", label: "MM", align: "right" },
    ...(
      [
        ["unitPrice", "단가"],
        ["salary", "급여"],
        ["salesExpense", "공통관리비"],
        ["retirementAmount", "퇴직금"],
        ["totalCost", "비용 계"],
        ["profit", "이익"],
        ["profitShare", "이익금"],
      ] as const
    ).map(([key, label]) => ({
      key,
      label,
      align: "right" as const,
      ...(key === "totalCost" || key === "profit"
        ? { calc: formula(key === "totalCost" ? "식-42" : "식-43") }
        : {}),
      render: (row: api.PartSettlementRow) => (
        <span className={row[key] < 0 ? "part-settlement-negative" : ""}>
          {money(row[key])}
        </span>
      ),
    })),
    {
      key: "notes",
      label: "비고",
      render: (row) => (
        <span
          className={row.missingMonthlySalary ? "part-settlement-negative" : ""}
        >
          {(row.notes ?? []).join(" · ")}
        </span>
      ),
    },
  ];
  let rows = [...(detail?.items ?? [])];
  if (sort)
    rows.sort((a, b) => {
      const left = a[sort.key as keyof api.PartSettlementRow],
        right = b[sort.key as keyof api.PartSettlementRow];
      return (
        (typeof left === "number" && typeof right === "number"
          ? left - right
          : String(left).localeCompare(String(right))) *
        (sort.dir === "asc" ? 1 : -1)
      );
    });
  const summary = detail?.summary;
  const locked =
    !detail || detail.confirmed || detail.closed || busy || loading;
  const closeCancel = () =>
    guard(() => {
      setCancelOpen(false);
      setReason("");
    });
  return (
    <section
      className="part-settlement-page"
      data-testid="part-settlement-page"
      data-screen-id="UI-19"
      aria-label="영업 파트 이익금 작업 공간"
    >
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
        <button
          type="button"
          className="ls-btn"
          data-testid="part-settlement-refresh"
          disabled={busy}
          onClick={() => guard(retry)}
        >
          조회
        </button>
        <button
          type="button"
          className="ls-btn ls-btn-p"
          data-testid="part-settlement-confirm"
          disabled={locked}
          onClick={() => guard(() => void command("confirm"))}
        >
          정산 확정
        </button>
        {detail?.confirmed && !detail.closed && (
          <button
            type="button"
            className="ls-btn"
            data-testid="part-settlement-cancel-open"
            disabled={busy}
            onClick={() => {
              setCancelOpen(true);
              setFieldErrors({});
            }}
          >
            확정 취소
          </button>
        )}
      </div>
      <div
        role="tablist"
        aria-label="영업 파트"
        className="part-settlement-tabs"
      >
        {(tabs?.items ?? []).map((item) => (
          <button
            type="button"
            role="tab"
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
      <p
        className="part-settlement-banner"
        data-testid="part-settlement-banner"
      >
        {detail?.closed
          ? "마감된 달입니다. 정정 금액은 다음 달에 반영해 주세요."
          : detail?.confirmed
            ? `${detail.confirmationNote ?? "확정된 정산입니다."} · ${detail.confirmedBy ?? ""} · ${detail.confirmedAt ?? ""}`
            : "미확정 정산입니다. 현재 원천으로 계산하며 확정 시 월말 소속으로 고정합니다."}
      </p>
      {tabs && !(tabs.items ?? []).length && (
        <p data-testid="part-settlement-no-parts">{tabs.notice ?? noParts}</p>
      )}
      {notice && (
        <p role="status" data-testid="part-settlement-success">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" data-testid="part-settlement-error">
          {error}
        </p>
      )}
      <KpiCards
        aria-label="파트 이익금 요약"
        className="part-settlement-summary"
      >
        <KpiCard
          label="파트장"
          value={summary?.leaderName ?? "–"}
          description={
            summary
              ? `배분율 ${summary.distributionRate}% · 공통관리비 ${money(summary.salesExpense)}`
              : "–"
          }
        />
        <KpiCard label="인원" value={summary?.personCount ?? "–"} />
        <KpiCard
          label="이익 합계"
          value={summary ? money(summary.profitTotal) : "–"}
        />
        <KpiCard
          label="파트 이익금"
          value={summary ? money(summary.partProfitAmount) : "–"}
        />
      </KpiCards>
      <div
        role="tablist"
        aria-label="정산 보기"
        className="part-settlement-tabs"
      >
        <button
          type="button"
          role="tab"
          aria-selected={view === "monthly"}
          data-testid="part-settlement-monthly"
          onClick={() => guard(() => setView("monthly"))}
        >
          이번 달
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === "trend"}
          data-testid="part-settlement-trend"
          onClick={() => guard(() => setView("trend"))}
        >
          기간 추이
        </button>
      </div>
      {view === "monthly" ? (
        <div role="tabpanel" data-testid="part-settlement-monthly-panel">
          <ListView
            screenId="UI-19"
            columns={columns}
            visibleColumns={columns.filter(
              (column) => !hidden.includes(column.key),
            )}
            hiddenColumns={hidden}
            onToggleColumn={(key) =>
              setHidden((previous) =>
                previous.includes(key)
                  ? previous.filter((value) => value !== key)
                  : [...previous, key],
              )
            }
            onResetColumns={() => setHidden([])}
            rows={
              loading || error
                ? null
                : rows.slice((page - 1) * pageSize, page * pageSize)
            }
            rowKey={(row) => row.developerId}
            total={rows.length}
            page={page}
            pageSize={pageSize}
            onPage={setPage}
            onPageSize={(value) => {
              setPageSize(value);
              setPage(1);
            }}
            dense={dense}
            onDense={setDense}
            sort={sort}
            onSort={setSort}
            error={error}
            onRetry={() => guard(retry)}
            onRowClick={(row) => guard(() => setDeveloperId(row.developerId))}
            toolbarRight={
              <MoneyUnitSelect
                value={unit}
                onChange={setUnit}
                id="part-settlement-money-unit"
              />
            }
            downloading={!partId || loading || !!error}
            onDownload={() => {
              if (partId && !loading && !error)
                void api
                  .getCommonPartSettlementsExcel(partId, yearMonth, view)
                  .catch(failure);
            }}
            footer={{
              profit: summary ? money(summary.profitTotal) : "–",
              profitShare: summary ? money(summary.partProfitAmount) : "–",
            }}
            testId="part-settlement-list"
          />
        </div>
      ) : (
        <div role="tabpanel" data-testid="part-settlement-trend-panel">
          <div className="part-settlement-conditions">
            <button
              className="ls-btn"
              type="button"
              data-testid="part-settlement-trend-excel"
              disabled={!trend || loading || !!error}
              onClick={() => {
                void api
                  .getCommonPartSettlementsExcel(partId, yearMonth, view)
                  .catch(failure);
              }}
            >
              엑셀 다운로드
            </button>
            <MoneyUnitSelect
              value={unit}
              onChange={setUnit}
              id="part-settlement-trend-money-unit"
            />
          </div>
          {loading && <p role="status">목록을 불러오는 중입니다.</p>}
          <div className="ls-twrap">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>이름</th>
                  <th>항목</th>
                  {trend?.monthlyTotals.map((month) => (
                    <th key={month.yearMonth}>{month.yearMonth}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {trend?.items.flatMap((person) =>
                  metrics.map(([key, label]) => (
                    <tr
                      key={`${person.developerId}-${key}`}
                      data-testid={`part-settlement-trend-${person.developerId}-${key}`}
                    >
                      <td>{person.developerName}</td>
                      <td>{label}</td>
                      {person.months.map((month) => (
                        <td
                          key={month.yearMonth}
                          className={
                            month[key] < 0 ? "part-settlement-negative" : ""
                          }
                        >
                          {key === "monthlyMm" ? month[key] : money(month[key])}
                        </td>
                      ))}
                    </tr>
                  )),
                )}
                {!loading && !error && !trend?.items.length && (
                  <tr data-testid="part-settlement-trend-empty">
                    <td colSpan={(trend?.monthlyTotals.length ?? 0) + 2}>
                      조건에 맞는 결과가 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan={2}>월별 파트 이익금 합계</th>
                  {trend?.monthlyTotals.map((month) => (
                    <td key={month.yearMonth}>
                      {money(month.partProfitAmount)}
                    </td>
                  ))}
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      )}
      <form
        className="part-settlement-card"
        onSubmit={(event) => {
          event.preventDefault();
          void command("card");
        }}
      >
        <label htmlFor="part-settlement-card">법인카드 (원)</label>
        <input
          ref={cardRef}
          id="part-settlement-card"
          data-testid="part-settlement-card"
          value={card}
          inputMode="numeric"
          readOnly={locked}
          aria-invalid={!!fieldErrors.corpCardAmount}
          aria-describedby="part-settlement-card-error"
          onChange={(event) => setCard(event.target.value)}
        />
        <button
          className="ls-btn ls-btn-p"
          type="submit"
          data-testid="part-settlement-card-save"
          disabled={locked}
        >
          저장
        </button>
        <button
          className="ls-btn"
          type="button"
          data-testid="part-settlement-card-discard"
          disabled={locked}
          onClick={() =>
            guard(() => {
              setCard(String(detail?.corpCardAmount ?? ""));
              setFieldErrors({});
            })
          }
        >
          취소
        </button>
        {fieldErrors.corpCardAmount && (
          <p
            id="part-settlement-card-error"
            className="part-settlement-negative"
          >
            {fieldErrors.corpCardAmount}
          </p>
        )}
      </form>
      {definitions.length ? (
        <FormulaBox
          rows={definitions.map((item) => [
            item.calcId,
            item.formula,
            item.details,
          ])}
          testId="part-settlement-formulas"
        />
      ) : (
        <p data-testid="part-settlement-formulas-unavailable">
          계산 정의를 불러올 수 없습니다.
        </p>
      )}
      {cancelOpen && (
        <Dialog
          title="확정 취소"
          testId="part-settlement-cancel-dialog"
          closable
          onClose={closeCancel}
        >
          <p>
            {detail?.partName} · {yearMonth}
          </p>
          <label htmlFor="part-settlement-reason">사유 *</label>
          <textarea
            ref={reasonRef}
            id="part-settlement-reason"
            data-testid="part-settlement-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            aria-invalid={!!fieldErrors.cancelReason}
            aria-describedby="part-settlement-reason-error"
          />
          {fieldErrors.cancelReason && (
            <p
              id="part-settlement-reason-error"
              className="part-settlement-negative"
            >
              {fieldErrors.cancelReason}
            </p>
          )}
          <div className="app-dialog-actions">
            <button
              type="button"
              data-testid="part-settlement-cancel-dismiss"
              disabled={busy}
              onClick={closeCancel}
            >
              닫기
            </button>
            <button
              type="button"
              className="app-danger"
              data-testid="part-settlement-cancel-submit"
              disabled={busy}
              onClick={() => void command("cancel")}
            >
              확정 취소
            </button>
          </div>
        </Dialog>
      )}
      {developerId && (
        <DeveloperDetail
          developerId={developerId}
          readOnly
          onClose={() => guard(() => setDeveloperId(null))}
        />
      )}
      {pending && (
        <ConfirmDialog
          title="저장하지 않은 내용을 버릴까요?"
          description="저장하지 않은 입력은 사라집니다."
          onCancel={() => setPending(null)}
          onProceed={() => {
            const action = pending;
            setPending(null);
            setCard(String(detail?.corpCardAmount ?? ""));
            setReason("");
            action();
          }}
        />
      )}
    </section>
  );
}

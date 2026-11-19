import { useCallback, useEffect, useRef, useState } from "react";
import type { ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  ListView,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { billShortMoney } from "../bills/bills";
import {
  getCommonBillChecks,
  type BillCheckResult,
  type BillCheckRow,
} from "./billChecks";
import { BillCheckForm, billCheckActionLabel } from "./BillCheckForm";
import "../projects/projects.css";
import "./billChecks.css";
import "../bills/bills.css";

const amount = (value: number) => value.toLocaleString("ko-KR");
const typeName = (code: string) =>
  code === "MO" ? "월단위" : code === "DP" ? "파견" : code;
const judgementKind = (value: string) =>
  value === "맞음" ? "ok" : value === "청구 없음" ? "bad" : "warn";

/** UI-13 청구 누락 점검 탭(시안 billCheckView, SFR-113-06). */
export function BillChecksPanel({
  onOpenBill,
  onChanged,
  onNotice,
}: {
  onOpenBill?: (billId: string) => void;
  onChanged?: () => void;
  onNotice?: (message: string, kind?: ToastKind) => void;
}) {
  const [result, setResult] = useState<BillCheckResult | null>(null);
  const [requestedMonth, setRequestedMonth] = useState<string>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<BillCheckRow | null>(null);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const presentation = useListPresentation();
  const request = useRef<AbortController>();
  const load = useCallback(async (month?: string) => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setLoading(true);
    setError("");
    try {
      const data = await getCommonBillChecks(month, controller.signal);
      if (!controller.signal.aborted) {
        setResult(data);
        setPage(1);
      }
    } catch (failure) {
      if (!controller.signal.aborted)
        setError(
          failure instanceof Error
            ? failure.message
            : "점검 결과를 불러오지 못했습니다.",
        );
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);
  useEffect(() => {
    void load(requestedMonth);
    return () => request.current?.abort();
  }, [load, requestedMonth, revision]);
  const act = (row: BillCheckRow) => {
    if (row.allowedAction === "DETAIL") {
      // 시안: 마감한 달에 많이 청구 → 고칠 수 없다는 안내 후 그 달 최근 청구 상세
      onNotice?.(
        `${Number((result?.checkMonth ?? "").slice(4))}월은 마감한 달이라 청구를 줄일 수 없습니다. 고객사와 정산한 뒤 다음 달 청구에서 빼 주세요.`,
        "info",
      );
      if (row.latestBillId) onOpenBill?.(row.latestBillId);
    } else setSelected(row);
  };
  const columns: ListColumn<BillCheckRow>[] = [
    {
      key: "judgement",
      label: "판정",
      required: true,
      render: (r) => <Badge kind={judgementKind(r.judgement)}>{r.judgement}</Badge>,
    },
    { key: "projectName", label: "프로젝트", required: true },
    { key: "projectType", label: "계약유형", render: (r) => typeName(r.projectType) },
    { key: "headcount", label: "투입 인원", align: "right" },
    {
      key: "monthlyMm",
      label: "투입 MM",
      align: "right",
      render: (r) => Number(r.monthlyMm).toFixed(2),
    },
    {
      key: "referenceAmount",
      label: "투입 기준 금액",
      align: "right",
      render: (r) => amount(r.referenceAmount),
    },
    {
      key: "billedSupplyAmount",
      label: "청구 공급가",
      align: "right",
      render: (r) => amount(r.billedSupplyAmount),
    },
    {
      key: "difference",
      label: "차이",
      align: "right",
      render: (r) => (
        <span className={r.difference > 0 ? "bill-overdue" : undefined}>
          {r.difference ? amount(r.difference) : "0"}
        </span>
      ),
    },
    {
      key: "explanation",
      label: "설명",
      render: (r) => <span className="bill-sub bill-wrap">{r.explanation || "–"}</span>,
    },
    {
      key: "action",
      label: "",
      sortable: false,
      required: true,
      render: (row) =>
        row.allowedAction !== "NONE" && billCheckActionLabel(row.allowedAction) ? (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid={`bill-check-action-${row.projectId}`}
            disabled={loading || !!error}
            onClick={() => act(row)}
          >
            {billCheckActionLabel(row.allowedAction)}
          </button>
        ) : null,
    },
  ];
  // 시안: 맞지 않는 줄을 먼저(서버 순서는 유지)
  const ordered = [...(result?.items ?? [])].sort(
    (a, b) => Number(a.judgement === "맞음") - Number(b.judgement === "맞음"),
  );
  if (sort)
    ordered.sort((a, b) => {
      const first = a[sort.key as keyof BillCheckRow];
      const second = b[sort.key as keyof BillCheckRow];
      const compared =
        typeof first === "number" && typeof second === "number"
          ? first - second
          : String(first ?? "").localeCompare(String(second ?? ""), "ko");
      return (sort.dir === "asc" ? 1 : -1) * compared;
    });
  const month = result?.checkMonth ?? requestedMonth;
  const items = result?.items ?? [];
  const bad = items.filter((x) => x.judgement !== "맞음");
  const missing = bad.reduce((s, x) => s + Math.max(0, x.difference), 0);
  const missingCount = items.filter((x) => x.judgement === "청구 없음").length;
  const lastClosed = result?.lastClosedMonth ?? null;
  const inProgress = (value: string) => !lastClosed || value > lastClosed;
  return (
    <section
      className="bill-check-panel"
      data-testid="bill-checks-panel"
      role="tabpanel"
      aria-label="청구 누락 점검"
    >
      <div className="bill-kpis" aria-label="점검 요약">
        <div className="bill-kpi" data-testid="bill-check-kpi-total">
          <span className="l">점검 프로젝트</span>
          <span className="v">{items.length}건</span>
          <span className="d">월단위·파견</span>
        </div>
        <div className="bill-kpi" data-testid="bill-check-kpi-ok">
          <span className="l">맞음</span>
          <span className="v">{items.length - bad.length}건</span>
          <span className="d" />
        </div>
        <div className="bill-kpi" data-testid="bill-check-kpi-missing">
          <span className="l">청구 없음</span>
          <span className={`v${missingCount ? " bill-bad" : ""}`}>{missingCount}건</span>
          <span className="d">투입은 있는데 청구가 없음</span>
        </div>
        <div className="bill-kpi" data-testid="bill-check-kpi-diff">
          <span className="l">금액 차이</span>
          <span className="v">{bad.length - missingCount}건</span>
          <span className="d">차이 0.5% 넘음</span>
        </div>
        <div className="bill-kpi" data-testid="bill-check-kpi-amount">
          <span className="l">빠진 것으로 보이는 금액</span>
          <span className={`v${missing ? " bill-bad" : ""}`}>{billShortMoney(missing)}</span>
          <span className="d">공급가 기준</span>
        </div>
      </div>
      {result && (
        <div className="bill-seg" role="group" aria-label="점검 월">
          {(result.months ?? []).map((value) => (
            <button
              key={value}
              type="button"
              className={`ls-btn ls-btn-sm${value === month ? " ls-btn-p" : ""}`}
              data-testid={`bill-check-month-${value}`}
              aria-pressed={value === month}
              onClick={() => setRequestedMonth(value)}
            >
              {Number(value.slice(4))}월{inProgress(value) ? " (진행 중)" : ""}
            </button>
          ))}
        </div>
      )}
      {error && (
        <div role="alert" className="field-error">
          {error}{" "}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="bill-check-retry"
            onClick={() => setRevision((v) => v + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      <section className="bill-card">
        <div className="bill-card-h">
          <h2>{month ? `${Number(month.slice(4))}월 투입 대비 청구` : "투입 대비 청구"}</h2>
          <span className="bill-sub">단위: 원 · 공급가</span>
        </div>
        <ListView
          screenId="UI-13-checks"
          columns={columns}
          visibleColumns={columns}
          hiddenColumns={[]}
          onToggleColumn={() => undefined}
          onResetColumns={() => undefined}
          rows={
            loading
              ? null
              : ordered.slice(
                  (page - 1) * presentation.pageSize,
                  page * presentation.pageSize,
                )
          }
          rowKey={(row) => row.projectId}
          total={ordered.length}
          page={page}
          onPage={setPage}
          pageSize={presentation.pageSize}
          onPageSize={(size) => {
            presentation.setPageSize(size);
            setPage(1);
          }}
          dense={presentation.dense}
          onDense={presentation.setDense}
          sort={sort}
          onSort={(value) => {
            setSort(value);
            setPage(1);
          }}
          columnSettings={false}
          testId="bill-check-list"
        />
        {month && inProgress(month) && (
          <p className="bill-sub bill-note" data-testid="bill-check-planned">
            이번 달은 아직 끝나지 않아 투입 예정 기준으로 봅니다.
          </p>
        )}
      </section>
      <details className="bill-check-calculation">
        <summary>계산 방법</summary>
        <p className="project-pre">{result?.calculationMethod ?? ""}</p>
        <p className="bill-sub">
          턴키는 단계별로 청구하므로 이 점검에서 뺍니다(프로젝트 현황판의 단계 완료와 함께 확인).
        </p>
      </details>
      {selected && result && (
        <BillCheckForm
          row={selected}
          checkMonth={result.checkMonth}
          onClose={() => setSelected(null)}
          onSaved={(_saved, message) => {
            setSelected(null);
            onNotice?.(message);
            void load(result.checkMonth);
            onChanged?.();
          }}
        />
      )}
    </section>
  );
}

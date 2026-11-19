import { useCallback, useEffect, useRef, useState } from "react";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import {
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import {
  getBillCheckDetail,
  getCommonBillChecks,
  type BillCheckDetail,
  type BillCheckResult,
  type BillCheckRow,
} from "./billChecks";
import { BillCheckForm, billCheckActionLabel } from "./BillCheckForm";
import "../projects/projects.css";
import "./billChecks.css";

const amount = (value: number) => value.toLocaleString("ko-KR");

export function BillChecksPanel({
  onOpenBill,
  onChanged,
}: {
  onOpenBill?: (billId: string) => void;
  onChanged?: (detail: BillCheckDetail) => void;
}) {
  const [result, setResult] = useState<BillCheckResult | null>(null);
  const [requestedMonth, setRequestedMonth] = useState<string>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<BillCheckRow | null>(null);
  const [detailId, setDetailId] = useState<string | null>(null);
  const [detail, setDetail] = useState<BillCheckDetail | null>(null);
  const [detailError, setDetailError] = useState("");
  const [notice, setNotice] = useState("");
  const [toast, setToast] = useState("");
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
  useEffect(() => {
    if (!detailId) return;
    const controller = new AbortController();
    setDetail(null);
    setDetailError("");
    getBillCheckDetail(detailId, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setDetail(data);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted)
          setDetailError(
            failure instanceof Error
              ? failure.message
              : "청구 상세를 불러오지 못했습니다.",
          );
      });
    return () => controller.abort();
  }, [detailId, revision]);
  const openDetail = (id: string) => {
    if (onOpenBill) onOpenBill(id);
    else setDetailId(id);
  };
  const act = (row: BillCheckRow) => {
    if (row.allowedAction === "DETAIL") {
      setNotice("마감한 달은 감액할 수 없습니다. 청구 상세를 확인해 주세요.");
      if (row.latestBillId) openDetail(row.latestBillId);
    } else setSelected(row);
  };
  const columns: ListColumn<BillCheckRow>[] = [
    { key: "judgement", label: "판정", required: true },
    { key: "projectName", label: "프로젝트", required: true },
    {
      key: "projectType",
      label: "유형",
      render: (r) =>
        r.projectType === "MO"
          ? "월단위"
          : r.projectType === "DP"
            ? "파견"
            : r.projectType,
    },
    { key: "headcount", label: "투입 인원", align: "right" },
    { key: "monthlyMm", label: "월 MM", align: "right" },
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
      render: (r) => amount(r.difference),
    },
    { key: "explanation", label: "설명" },
    {
      key: "action",
      label: "후속 행동",
      sortable: false,
      required: true,
      render: (row) =>
        row.allowedAction !== "NONE" &&
        billCheckActionLabel(row.allowedAction) ? (
          <button
            type="button"
            className="project-lnk"
            data-testid={`bill-check-action-${row.projectId}`}
            disabled={loading || !!error}
            onClick={() => act(row)}
          >
            {billCheckActionLabel(row.allowedAction)}
          </button>
        ) : (
          "–"
        ),
    },
  ];
  const columnState = useListColumns("UI-13-checks", columns);
  const ordered = [...(result?.items ?? [])];
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
  const month = requestedMonth ?? result?.checkMonth;
  const summary = result?.summary;
  return (
    <section
      className="project-page bill-check-panel"
      data-testid="bill-checks-panel"
      aria-label="청구 누락 점검"
    >
      <div className="project-actions">
        <h2>청구 누락 점검</h2>
        <label htmlFor="bill-check-month">점검 월</label>
        <input
          id="bill-check-month"
          type="month"
          data-testid="bill-check-month"
          value={month ? `${month.slice(0, 4)}-${month.slice(4)}` : ""}
          onChange={(e) => {
            if (e.target.value)
              setRequestedMonth(e.target.value.replace("-", ""));
          }}
        />
        <button
          type="button"
          className="ls-btn"
          data-testid="bill-check-search"
          onClick={() => setRevision((v) => v + 1)}
        >
          조회
        </button>
      </div>
      {month && (
        <div className="project-actions" role="group" aria-label="월 선택">
          {Array.from({ length: 12 }, (_, index) =>
            String(index + 1).padStart(2, "0"),
          ).map((value) => (
            <button
              key={value}
              type="button"
              className="ls-btn"
              data-testid={`bill-check-month-${value}`}
              aria-pressed={month.slice(4) === value}
              onClick={() => setRequestedMonth(`${month.slice(0, 4)}${value}`)}
            >
              {Number(value)}월
            </button>
          ))}
        </div>
      )}
      <div className="bill-check-summary" aria-label="점검 요약">
        {(
          [
            ["전체", summary?.totalCount],
            ["청구 없음", summary?.missingCount],
            ["적게 청구", summary?.underCount],
            ["많이 청구", summary?.overCount],
            ["맞음", summary?.matchedCount],
          ] as const
        ).map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{value == null ? "–" : amount(value)}</strong>
          </div>
        ))}
      </div>
      {loading && <p role="status">점검 결과를 불러오는 중입니다.</p>}
      {error && (
        <div role="alert">
          {error}{" "}
          <button
            type="button"
            data-testid="bill-check-retry"
            onClick={() => setRevision((v) => v + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      {!loading && !error && result?.items.length === 0 && (
        <p>조회 결과가 없습니다</p>
      )}
      {result?.plannedBasis && (
        <p className="project-cap">진행 중인 달은 예정 기준입니다.</p>
      )}
      {notice && (
        <p role="status" data-testid="bill-check-notice">
          {notice}
        </p>
      )}
      <ListView
        screenId="UI-13"
        columns={columns}
        visibleColumns={columnState.visible}
        hiddenColumns={columnState.hidden}
        onToggleColumn={columnState.toggle}
        onResetColumns={columnState.reset}
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
      <details className="bill-check-calculation">
        <summary>계산 방법</summary>
        <p className="project-pre">{result?.calculationMethod ?? ""}</p>
      </details>
      {selected && result && (
        <BillCheckForm
          row={selected}
          checkMonth={result.checkMonth}
          onClose={() => setSelected(null)}
          onSaved={(saved) => {
            setSelected(null);
            setToast("청구를 저장했습니다.");
            void load(result.checkMonth);
            onChanged?.(saved);
          }}
        />
      )}
      {detailId && (
        <Dialog
          title="청구 상세"
          testId="bill-check-detail"
          onClose={() => setDetailId(null)}
          closable
        >
          {notice && <p>{notice}</p>}
          {!detail && !detailError && (
            <p role="status">청구 상세를 불러오는 중입니다.</p>
          )}
          {detailError && (
            <div role="alert">
              {detailError}
              <button
                type="button"
                data-testid="bill-check-detail-retry"
                onClick={() => setRevision((v) => v + 1)}
              >
                다시 시도
              </button>
            </div>
          )}
          {detail && (
            <>
              <h3>청구 정보</h3>
              <dl className="bill-check-detail-grid">
                {Object.entries({
                  청구번호: detail.bill.billId,
                  프로젝트: detail.bill.projectName,
                  차수: detail.bill.billOrder,
                  청구일: detail.bill.billDate,
                  공급가: amount(detail.bill.supplyAmount),
                  부가세율: `${detail.bill.vatRate}%`,
                  부가세: amount(detail.bill.vatAmount),
                  합계: amount(detail.bill.totalAmount),
                  입금액: amount(detail.bill.receiptSumAmount),
                  미수금: amount(detail.bill.unpaidAmount),
                  "입금 예정일": detail.bill.payDueDate,
                  "세금계산서 번호": detail.bill.taxInvoiceNo || "번호 미입력",
                  상태: detail.bill.status,
                }).map(([label, value]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
              <h3>입금 내역 ({detail.receipts.length})</h3>
              {detail.receipts.length === 0 && <p>조회 결과가 없습니다</p>}
              {detail.receipts.map((receipt) => (
                <p
                  key={receipt.receiptId}
                  data-testid={`bill-check-receipt-${receipt.receiptId}`}
                >
                  {receipt.receiptDate} · {amount(receipt.amount)} ·{" "}
                  {receipt.cancelled
                    ? `취소됨 · ${receipt.cancelReason ?? ""}`
                    : "정상"}
                </p>
              ))}
            </>
          )}
        </Dialog>
      )}
      {toast && (
        <Toast
          message={toast}
          onDismiss={() => setToast("")}
          testId="bill-check-toast"
        />
      )}
    </section>
  );
}

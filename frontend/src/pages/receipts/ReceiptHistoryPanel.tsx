import { useEffect, useState } from "react";
import {
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
} from "../../shared/list";
import { BillingReceiptDialog } from "../commonops/BillingReceiptDialog";
import { ReceiptCancelDialog } from "./ReceiptCancelDialog";
import {
  getReceiptBillDetail,
  type Receipt,
  type ReceiptBillDetail,
} from "./receipts";
import "../commonops/billingLookup.css";

/** UI-13 / SCR-UI-13: receipt history in the selected bill detail, including prior-year bills. */
export function ReceiptHistoryPanel({
  billId,
  refreshKey = 0,
  onChanged,
}: {
  billId: string;
  refreshKey?: number;
  onChanged?: () => void;
}) {
  const [data, setData] = useState<ReceiptBillDetail | null>(null);
  const [failure, setFailure] = useState("");
  const [revision, setRevision] = useState(0);
  const [receiving, setReceiving] = useState(false);
  const [cancelling, setCancelling] = useState<Receipt | null>(null);
  const [page, setPage] = useState(1);
  const presentation = useListPresentation();
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setFailure("");
    setPage(1);
    setReceiving(false);
    setCancelling(null);
    void getReceiptBillDetail(billId, controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setData(result);
      },
      (error) => {
        if (!controller.signal.aborted)
          setFailure(
            error instanceof Error
              ? error.message
              : "입금 내역을 불러오지 못했습니다.",
          );
      },
    );
    return () => controller.abort();
  }, [billId, refreshKey, revision]);
  const changed = () => {
    setReceiving(false);
    setCancelling(null);
    setRevision((value) => value + 1);
    onChanged?.();
  };
  const columns: ListColumn<Receipt>[] = [
    { key: "receiptDate", label: "입금일", required: true, sortable: false },
    {
      key: "amount",
      label: "금액",
      sortable: false,
      align: "right",
      render: (row) => row.amount.toLocaleString("ko-KR"),
    },
    {
      key: "cancelled",
      label: "상태",
      sortable: false,
      render: (row) => (row.cancelled ? "취소됨" : "정상"),
    },
    {
      key: "cancelReason",
      label: "취소 사유",
      sortable: false,
      render: (row) => row.cancelReason ?? "–",
    },
    {
      key: "cancelledBy",
      label: "취소자",
      sortable: false,
      render: (row) => row.cancelledBy ?? "–",
    },
    {
      key: "cancelledAt",
      label: "취소 시각",
      sortable: false,
      render: (row) => row.cancelledAt ?? "–",
    },
    {
      key: "actions",
      label: "처리",
      sortable: false,
      render: (row) =>
        !row.cancelled && row.canCancel ? (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid={`receipt-cancel-${row.receiptId}`}
            onClick={() => setCancelling(row)}
          >
            취소
          </button>
        ) : null,
    },
  ];
  const settings = useListColumns("UI-13-receipts", columns);
  const rows = data?.receipts ?? [];
  return (
    <section
      className="billing-dunning-panel ls-root"
      data-testid="receipt-history-panel"
      data-screen-id="SCR-UI-13"
    >
      <h3>입금 내역 ({rows.length})</h3>
      {data && (
        <p data-testid="receipt-balance">
          정상 입금 합계 {data.bill.receiptSumAmount.toLocaleString("ko-KR")}원
          · 미수금 {data.bill.unpaidAmount.toLocaleString("ko-KR")}원 ·{" "}
          {data.bill.status}
        </p>
      )}
      <div>
        {data?.bill.canReceive && data.bill.unpaidAmount > 0 && (
          <button
            type="button"
            className="ls-btn ls-btn-p"
            data-testid="receipt-history-register"
            onClick={() => setReceiving(true)}
          >
            입금 등록
          </button>
        )}
      </div>
      {data && !rows.length && <p>조회 결과가 없습니다</p>}
      <ListView
        screenId="UI-13-receipts"
        columns={columns}
        visibleColumns={settings.visible}
        hiddenColumns={settings.hidden}
        onToggleColumn={settings.toggle}
        onResetColumns={settings.reset}
        rows={
          data
            ? rows.slice(
                (page - 1) * presentation.pageSize,
                page * presentation.pageSize,
              )
            : failure
              ? []
              : null
        }
        rowKey={(row) => row.receiptId}
        total={rows.length}
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
        error={failure}
        onRetry={() => setRevision((value) => value + 1)}
        testId="receipt-history-list"
      />
      {receiving && data && (
        <BillingReceiptDialog
          bills={[data.bill]}
          onClose={() => setReceiving(false)}
          onSaved={changed}
        />
      )}
      {cancelling && (
        <ReceiptCancelDialog
          receipt={cancelling}
          onClose={() => setCancelling(null)}
          onSaved={changed}
        />
      )}
    </section>
  );
}

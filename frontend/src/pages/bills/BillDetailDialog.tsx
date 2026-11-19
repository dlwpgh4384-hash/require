import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { formatDateTime } from "../../shared/time";
import { ReceiptHistoryPanel } from "../receipts/ReceiptHistoryPanel";
import { ProjectRequestError } from "../projects/projects";
import { BillForm } from "./BillForm";
import {
  billMoney,
  billStatus,
  getCommonBill,
  postCommonBillCancel,
  type BillDetail,
} from "./bills";

export function BillDetailDialog({
  billId,
  readOnly = false,
  onClose,
  onChanged,
}: {
  billId: string;
  readOnly?: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [detail, setDetail] = useState<BillDetail | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [editing, setEditing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    setDetail(null);
    setError("");
    void getCommonBill(billId, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setDetail(data);
      },
      (failure) => {
        if (!controller.signal.aborted)
          setError(
            failure instanceof Error
              ? failure.message
              : "청구 상세를 불러오지 못했습니다.",
          );
      },
    );
    return () => controller.abort();
  }, [billId, revision]);
  const changed = () => {
    setRevision((value) => value + 1);
    onChanged();
  };
  async function cancel() {
    if (pending.current) return;
    if (!reason.trim()) {
      setReasonError("취소 사유를 입력해 주세요.");
      return;
    }
    pending.current = true;
    setSaving(true);
    setReasonError("");
    try {
      await postCommonBillCancel(billId, reason.trim());
      setCancelling(false);
      changed();
    } catch (failure) {
      setReasonError(
        failure instanceof ProjectRequestError
          ? (failure.errors.find((item) => item.field === "reason")?.message ??
              failure.message)
          : failure instanceof Error
            ? failure.message
            : "청구 취소에 실패했습니다.",
      );
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  const bill = detail?.bill;
  return (
    <Dialog
      title="청구 상세"
      onClose={() => {
        if (!pending.current) onClose();
      }}
      closable
      testId="bill-detail"
    >
      <section data-screen-id="UI-13-M3" data-testid="bill-detail-content">
        {!detail && !error && (
          <p role="status">청구 상세를 불러오는 중입니다.</p>
        )}
        {error && (
          <p role="alert">
            {error}
            <button
              type="button"
              data-testid="bill-detail-retry"
              onClick={() => setRevision((value) => value + 1)}
            >
              다시 시도
            </button>
          </p>
        )}
        {detail && bill && (
          <>
            <h3>청구 정보</h3>
            <dl className="bill-detail-grid">
              {Object.entries({
                청구번호: bill.billId,
                프로젝트: bill.projectName,
                청구월: bill.billMonth,
                차수: bill.billOrder,
                청구일: bill.billDate,
                공급가: billMoney(bill.supplyAmount),
                부가세율: `${bill.vatRate}%`,
                부가세: billMoney(bill.vatAmount),
                합계: billMoney(bill.totalAmount),
                입금액: billMoney(bill.receiptSumAmount),
                미수금: billMoney(bill.unpaidAmount),
                "입금 예정일": bill.payDueDate,
                "세금계산서 번호": bill.taxInvoiceNo || "번호 미입력",
                상태: billStatus(bill.status),
              }).map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
            {detail.missingTaxInvoiceNumber && (
              <p role="status">번호 미입력: 세금계산서 번호를 확인해 주세요.</p>
            )}
            {bill.cancelled && (
              <p>
                취소 사유: {bill.cancelReason} · 취소자: {bill.cancelledBy} ·
                취소 시각: {formatDateTime(bill.cancelledAt)}
              </p>
            )}
            <div className="bill-actions">
              {!readOnly && bill.canEdit && (
                <button
                  type="button"
                  className="ls-btn"
                  data-testid="bill-detail-edit"
                  onClick={() => setEditing(true)}
                >
                  수정
                </button>
              )}
              {!readOnly && bill.canCancel && (
                <button
                  type="button"
                  className="ls-btn"
                  data-testid="bill-detail-cancel"
                  onClick={() => setCancelling(true)}
                >
                  청구 취소
                </button>
              )}
            </div>
            {!bill.canEdit && (
              <p>지난해·마감·취소 청구의 내용은 변경할 수 없습니다.</p>
            )}
            {!bill.canCancel &&
              !bill.cancelled &&
              bill.receiptSumAmount > 0 && (
                <p>청구를 취소하려면 먼저 정상 입금을 취소해 주세요.</p>
              )}
            {!readOnly ? (
              <ReceiptHistoryPanel
                billId={billId}
                refreshKey={revision}
                onChanged={changed}
              />
            ) : (
              <section data-testid="bill-readonly-receipts">
                <h3>입금 내역 ({detail.receipts.length})</h3>
                {!detail.receipts.length && <p>조회 결과가 없습니다</p>}
                {detail.receipts.map((receipt) => (
                  <p
                    key={receipt.receiptId}
                    data-testid={`bill-readonly-receipt-${receipt.receiptId}`}
                  >
                    {receipt.receiptDate} · {billMoney(receipt.amount)} ·{" "}
                    {receipt.cancelled ? "취소됨" : "정상"} ·{" "}
                    {receipt.cancelReason} · {receipt.cancelledBy} ·{" "}
                    {formatDateTime(receipt.cancelledAt)}
                  </p>
                ))}
              </section>
            )}
          </>
        )}
      </section>
      {editing && bill && (
        <BillForm
          bill={bill}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            changed();
          }}
        />
      )}
      {cancelling && (
        <Dialog
          title="청구 취소"
          testId="bill-cancel-dialog"
          onClose={() => {
            if (!pending.current) setCancelling(false);
          }}
        >
          <p>청구는 삭제되지 않으며 취소됨으로 남습니다.</p>
          <label>
            취소 사유
            <textarea
              aria-label="취소 사유"
              data-testid="bill-cancel-reason"
              value={reason}
              maxLength={500}
              disabled={saving}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          {reasonError && <p role="alert">{reasonError}</p>}
          <div className="bill-actions">
            <button
              type="button"
              data-testid="bill-cancel-back"
              disabled={saving}
              onClick={() => setCancelling(false)}
            >
              돌아가기
            </button>
            <button
              type="button"
              data-testid="bill-cancel-confirm"
              disabled={saving}
              onClick={() => void cancel()}
            >
              취소 확정
            </button>
          </div>
        </Dialog>
      )}
    </Dialog>
  );
}

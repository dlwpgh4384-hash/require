import { useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { failureMessage, isConflict } from "../bills/bills";
import { postCommonReceiptCancel, type Receipt } from "./receipts";
import "../commonops/billingLookup.css";

/** UI-13-M3 입금 취소(시안 paycancel): 사유 필수, 지우지 않고 「취소됨」으로 남긴다. */
export function ReceiptCancelDialog({
  receipt,
  onClose,
  onSaved,
  onConflict,
}: {
  receipt: Receipt;
  onClose: () => void;
  onSaved: (message: string) => void;
  onConflict?: (message: string) => void;
}) {
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  async function save() {
    if (pending.current) return;
    const value = reason.trim();
    if (!value) {
      setError("취소 사유를 적어 주세요.");
      return;
    }
    if ([...value].length > 500) {
      setError("취소 사유는 500자 이내로 입력해 주세요.");
      return;
    }
    pending.current = true;
    setSaving(true);
    setError("");
    try {
      await postCommonReceiptCancel(receipt.receiptId, value, receipt.modifiedAt);
      onSaved("입금을 취소했습니다. 기록은 남습니다.");
    } catch (failure) {
      if (isConflict(failure) && onConflict) onConflict(failureMessage(failure, ""));
      else setError(failureMessage(failure, "입금을 취소하지 못했습니다."));
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  return (
    <Dialog
      title="입금 취소"
      closable
      testId="receipt-cancel-dialog"
      onClose={() => {
        if (!pending.current) onClose();
      }}
    >
      <form
        className="billing-receipt-form"
        data-screen-id="UI-13-M3"
        data-testid="receipt-cancel-form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <p>
          {receipt.receiptDate} · {receipt.amount.toLocaleString("ko-KR")}원 입금을 취소합니다. 미수금이 그만큼 다시 늘어납니다.
        </p>
        <label htmlFor="receipt-cancel-reason">
          취소 사유 <span className="field-required">*</span>
        </label>
        <input
          id="receipt-cancel-reason"
          type="text"
          data-testid="receipt-cancel-reason"
          maxLength={500}
          placeholder="예: 금액 잘못 입력"
          value={reason}
          disabled={saving}
          aria-invalid={!!error}
          onChange={(event) => setReason(event.target.value)}
        />
        <p role="alert" className="field-error" data-testid="receipt-cancel-error">
          {error}
        </p>
        <div className="billing-dialog-actions">
          <button
            type="button"
            className="ls-btn"
            data-testid="receipt-cancel-close"
            disabled={saving}
            onClick={onClose}
          >
            닫기
          </button>
          <button
            type="submit"
            className="ls-btn app-danger"
            data-testid="receipt-cancel-confirm"
            disabled={saving}
          >
            입금 취소
          </button>
        </div>
      </form>
    </Dialog>
  );
}

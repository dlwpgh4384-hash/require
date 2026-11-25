import { useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
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
  // 사유를 적은 채 닫으면 저장하지 않은 내용 확인(00-1 FR-0106, 청구 취소 창과 같다)
  const [leaving, setLeaving] = useState(false);
  const pending = useRef(false);
  const close = () => {
    if (pending.current) return;
    if (reason.trim()) setLeaving(true);
    else onClose();
  };
  async function save() {
    if (pending.current) return;
    const value = reason.trim();
    const invalid = !value
      ? "취소 사유를 입력해 주세요."
      : [...value].length > 500
        ? "취소 사유는 500자까지 입력해 주세요."
        : "";
    if (invalid) {
      setError(invalid);
      // 오류 칸으로 간다(00-1 FR-0106)
      window.setTimeout(() => document.getElementById("receipt-cancel-reason")?.focus(), 0);
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
      onClose={close}
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
          onChange={(event) => {
            setReason(event.target.value);
            setError("");
          }}
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
            onClick={close}
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
      {leaving && (
        <InlineConfirmation
          title="저장하지 않은 내용이 있습니다"
          description="나가면 입력한 내용이 사라집니다."
          onCancel={() => setLeaving(false)}
          onProceed={onClose}
          testId="receipt-cancel-dirty"
        />
      )}
    </Dialog>
  );
}

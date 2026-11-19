import { useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { ProjectRequestError } from "../projects/projects";
import { postCommonReceiptCancel, type Receipt } from "./receipts";
import "../commonops/billingLookup.css";

/** UI-13-M3 / SCR-UI-13: receipt cancellation only; bill cancellation is a different command. */
export function ReceiptCancelDialog({
  receipt,
  onClose,
  onSaved,
}: {
  receipt: Receipt;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [reason, setReason] = useState("");
  const [fieldError, setFieldError] = useState("");
  const [failure, setFailure] = useState("");
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const eligible = !receipt.cancelled && receipt.canCancel;
  async function save() {
    if (pending.current || !eligible) return;
    const value = reason.trim();
    setFieldError("");
    setFailure("");
    if (!value) {
      setFieldError("취소 사유를 입력해 주세요.");
      return;
    }
    if ([...value].length > 500) {
      setFieldError("취소 사유는 500자 이내로 입력해 주세요.");
      return;
    }
    pending.current = true;
    setSaving(true);
    try {
      await postCommonReceiptCancel(receipt.receiptId, value);
      onSaved();
    } catch (error) {
      if (error instanceof ProjectRequestError) {
        const field = error.errors.find((item) => item.field === "reason");
        if (field) setFieldError(field.message);
      }
      setFailure(
        error instanceof Error ? error.message : "입금을 취소하지 못했습니다.",
      );
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
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <p>
          {receipt.receiptDate} · {receipt.amount.toLocaleString("ko-KR")}원
        </p>
        <p>
          삭제하지 않고 취소됨·사유·취소자·시각을 남깁니다. 금액 변경은 취소 후
          재등록해 주세요.
        </p>
        <label>
          사유
          <input
            type="text"
            aria-label="취소 사유"
            data-testid="receipt-cancel-reason"
            value={reason}
            disabled={saving || !eligible}
            aria-invalid={!!fieldError}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        {fieldError && (
          <p role="alert" className="field-error">
            {fieldError}
          </p>
        )}
        {!eligible && (
          <p role="alert">
            취소되었거나 입금월이 마감된 입금은 취소할 수 없습니다.
          </p>
        )}
        {failure && (
          <p role="alert" className="field-error">
            {failure}
          </p>
        )}
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
            className="ls-btn"
            data-testid="receipt-cancel-confirm"
            disabled={saving || !eligible}
          >
            {saving ? "취소 중…" : "입금 취소 확인"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

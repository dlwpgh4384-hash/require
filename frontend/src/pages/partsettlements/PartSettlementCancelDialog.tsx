import type { RefObject } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { ProjectField } from "../projects/ProjectField";

/** 시안 unconfirmpart: 「정산 확정을 취소할까요?」 확인 창 + 취소 사유(필수, 500자). */
export function PartSettlementCancelDialog({
  reason,
  error,
  busy,
  reasonRef,
  onReason,
  onClose,
  onSubmit,
}: {
  reason: string;
  error?: string;
  busy: boolean;
  reasonRef: RefObject<HTMLTextAreaElement>;
  onReason: (value: string) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
    <Dialog
      title="정산 확정을 취소할까요?"
      testId="part-settlement-cancel-dialog"
      role="alertdialog"
      closable
      onClose={onClose}
    >
      <p>취소하면 이 달 파트 정산을 다시 고칠 수 있습니다. 사유는 기록에 남습니다.</p>
      <ProjectField id="part-settlement-reason" label="취소 사유" required>
        <textarea
          ref={reasonRef}
          id="part-settlement-reason"
          className="ls-in part-settlement-reason"
          data-testid="part-settlement-reason"
          maxLength={500}
          value={reason}
          onChange={(event) => onReason(event.target.value)}
          aria-invalid={!!error}
          aria-describedby="part-settlement-reason-error"
        />
        {error && (
          <span id="part-settlement-reason-error" className="field-error ls-neg">
            {error}
          </span>
        )}
      </ProjectField>
      <div className="app-actions">
        <button
          type="button"
          data-testid="part-settlement-cancel-dismiss"
          disabled={busy}
          onClick={onClose}
        >
          취소
        </button>
        <button
          type="button"
          className="app-danger"
          data-testid="part-settlement-cancel-submit"
          disabled={busy}
          onClick={onSubmit}
        >
          확정 취소
        </button>
      </div>
    </Dialog>
  );
}

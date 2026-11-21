import { useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { paymentKind } from "./financeItemFormulas";
import type { FinancePayment } from "./financeItems";
import {
  FieldError,
  MoneyInput,
  Req,
  type Fields,
  type Notify,
} from "./financeParts";

/** 시안 ACT.taxpaid: 「법인세 납부 확인」 — 실제 납부일·금액을 넣으면 바로 저장한다. */
export function FinancePaymentDialog({
  row,
  amount,
  busy,
  fields,
  notify,
  onClose,
  onSubmit,
}: {
  row: FinancePayment;
  /** 처음 값: 지금 금액(저장 전 고친 값 포함). */
  amount: number;
  busy: boolean;
  fields: Fields;
  notify: Notify;
  onClose: () => void;
  onSubmit: (date: string, amount: number) => void;
}) {
  const [date, setDate] = useState(row.scheduledDate);
  const [value, setValue] = useState(String(amount));
  const kind = paymentKind(row.taxYear, row.paymentType);
  return (
    <Dialog
      title="법인세 납부 확인"
      testId="finance-payment-dialog"
      closable
      onClose={() => !busy && onClose()}
    >
      <p>
        「{kind}」를 실제로 낸 날과 금액을 넣습니다. 확인하면 자금수지표의 법인세
        납부가 이 값으로 바뀝니다.
      </p>
      <div className="fin-fsec">
        <div className="fin-fc">
          <label className="fin-fl fin-fl-top" htmlFor="finance-paid-date">
            실제 납부일
            <Req />
          </label>
          <input
            id="finance-paid-date"
            type="date"
            className="ls-in"
            style={{ width: 180 }}
            aria-invalid={!!fields.actualPaymentDate || undefined}
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
          <FieldError fields={fields} name="actualPaymentDate" />
        </div>
        <div className="fin-fc">
          <label className="fin-fl fin-fl-top" htmlFor="finance-paid-amount">
            실제 납부액
            <Req />
          </label>
          <div className="fin-row">
            <MoneyInput
              id="finance-paid-amount"
              width={180}
              value={value}
              invalid={!!fields.actualPaymentAmount}
              onChange={setValue}
            />
            <span className="fin-sub">원</span>
          </div>
          <FieldError fields={fields} name="actualPaymentAmount" />
        </div>
      </div>
      <div className="app-actions">
        <button
          type="button"
          className="ls-btn"
          data-testid="finance-payment-cancel"
          disabled={busy}
          onClick={onClose}
        >
          취소
        </button>
        <button
          type="button"
          className="ls-btn ls-btn-p"
          data-testid="finance-payment-submit"
          disabled={busy}
          onClick={() => {
            const paid = Number(value || 0);
            if (!date || !(paid > 0)) {
              notify("실제 납부일과 금액을 넣어 주세요.", "info");
              return;
            }
            onSubmit(date, paid);
          }}
        >
          납부 확인
        </button>
      </div>
    </Dialog>
  );
}

import { useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { koreanToday } from "../../shared/time";
import { ProjectRequestError } from "../projects/projects";
import {
  postBillingReceipts,
  type BillingReceivable,
  type BillingReceiptResult,
} from "./billingLookups";
import "./billingLookup.css";

/** UI-13-M2 / SCR-UI-13: one date, one row per selected outstanding bill. */
export function BillingReceiptDialog({
  bills,
  onClose,
  onSaved,
}: {
  bills: BillingReceivable[];
  onClose: () => void;
  onSaved: (result: BillingReceiptResult) => void;
}) {
  const candidates = bills.filter(
    (bill) => bill.canReceive && bill.unpaidAmount > 0,
  );
  const eligible = [
    ...new Map(candidates.map((bill) => [bill.billId, bill])).values(),
  ];
  const [date, setDate] = useState(koreanToday);
  const [amounts, setAmounts] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      eligible.map((bill) => [bill.billId, String(bill.unpaidAmount)]),
    ),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState("");
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [closing, setClosing] = useState(false);
  const pending = useRef(false);
  const close = () => {
    if (!pending.current) {
      if (dirty) setClosing(true);
      else onClose();
    }
  };
  async function save() {
    if (pending.current) return;
    const fields: Record<string, string> = {};
    if (!date) fields.receiptDate = "입금일을 입력해 주세요";
    const seen = new Set<string>();
    const duplicates = new Set(
      candidates
        .filter((bill) => {
          if (seen.has(bill.billId)) return true;
          seen.add(bill.billId);
          return false;
        })
        .map((bill) => bill.billId),
    );
    for (const bill of eligible) {
      const raw = amounts[bill.billId] ?? "";
      const amount = Number(raw);
      if (duplicates.has(bill.billId))
        fields[bill.billId] = "같은 청구는 한 번만 선택해 주세요";
      else if (!raw.trim() || amount === 0)
        fields[bill.billId] = "입금액을 입력해 주세요";
      else if (
        !/^\d+$/.test(raw) ||
        !Number.isSafeInteger(amount) ||
        amount < 0 ||
        raw.replace(/^0+/, "").length > 15
      )
        fields[bill.billId] =
          "입금액은 15자리 이내의 원 단위 양수로 입력해 주세요";
      else if (amount > bill.unpaidAmount)
        fields[bill.billId] = "입금액이 미수금보다 큽니다";
    }
    setErrors(fields);
    setFailure("");
    if (Object.keys(fields).length || !eligible.length) return;
    pending.current = true;
    setSaving(true);
    try {
      const result = await postBillingReceipts({
        receiptDate: date,
        items: eligible.map((bill) => ({
          billId: bill.billId,
          amount: Number(amounts[bill.billId]),
        })),
      });
      setDirty(false);
      onSaved(result);
    } catch (error) {
      setFailure(
        error instanceof Error ? error.message : "입금을 저장하지 못했습니다.",
      );
      if (error instanceof ProjectRequestError) {
        const next: Record<string, string> = {};
        for (const item of error.errors) {
          if (item.field === "receiptDate") next.receiptDate = item.message;
          else {
            const index = /^items(?:\[(\d+)\]|\.(\d+))\.amount$/.exec(
              item.field,
            );
            const bill = index
              ? eligible[Number(index[1] ?? index[2])]
              : undefined;
            if (bill) next[bill.billId] = item.message;
            else if (item.field === "items.amount" || item.field === "amount")
              eligible.forEach((row) => {
                next[row.billId] = item.message;
              });
          }
        }
        setErrors(next);
      }
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  return (
    <Dialog
      title="입금 등록"
      onClose={close}
      closable
      testId="billing-receipt-dialog"
    >
      <form
        className="billing-receipt-form"
        data-screen-id="UI-13-M2"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <label>
          입금일
          <input
            type="date"
            aria-label="입금일"
            data-testid="billing-receipt-date"
            value={date}
            disabled={saving}
            aria-invalid={!!errors.receiptDate}
            onChange={(event) => {
              setDate(event.target.value);
              setDirty(true);
            }}
          />
        </label>
        {errors.receiptDate && (
          <p className="field-error" role="alert">
            {errors.receiptDate}
          </p>
        )}
        <div className="billing-table-scroll">
          <table className="ls-t" aria-label="청구별 입금액">
            <thead>
              <tr>
                <th>청구번호</th>
                <th>프로젝트</th>
                <th>미수금</th>
                <th>입금액</th>
              </tr>
            </thead>
            <tbody>
              {eligible.map((bill, index) => (
                <tr
                  key={bill.billId}
                  data-testid={`billing-receipt-row-${index}`}
                >
                  <td>{bill.billId}</td>
                  <td>{bill.projectName}</td>
                  <td>{bill.unpaidAmount.toLocaleString("ko-KR")}</td>
                  <td>
                    <input
                      type="text"
                      inputMode="numeric"
                      aria-label={`${bill.billId} 입금액`}
                      data-testid={`billing-receipt-amount-${index}`}
                      disabled={saving}
                      value={amounts[bill.billId] ?? ""}
                      aria-invalid={!!errors[bill.billId]}
                      onChange={(event) => {
                        setAmounts((value) => ({
                          ...value,
                          [bill.billId]: event.target.value,
                        }));
                        setDirty(true);
                      }}
                    />
                    {errors[bill.billId] && (
                      <p className="field-error" role="alert">
                        {errors[bill.billId]}
                      </p>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!eligible.length && (
          <p>
            입금 가능한 청구가 없습니다. 현재 달 마감 여부와 잔액을 확인해
            주세요.
          </p>
        )}
        <p>
          미수금 전액이 기본값이며 분할 입금할 수 있습니다. 남는 금액은 다른
          청구에 자동 배분하지 않습니다.
        </p>
        {failure && (
          <p role="alert" className="field-error">
            {failure}
          </p>
        )}
        <div className="billing-dialog-actions">
          <button
            type="button"
            className="ls-btn"
            data-testid="billing-receipt-cancel"
            disabled={saving}
            onClick={close}
          >
            취소
          </button>
          <button
            type="submit"
            className="ls-btn ls-btn-p"
            data-testid="billing-receipt-save"
            disabled={saving || !eligible.length}
          >
            {saving ? "저장 중…" : "저장"}
          </button>
        </div>
      </form>
      {closing && (
        <InlineConfirmation
          title="저장하지 않고 닫을까요?"
          description="입력한 입금 정보는 저장되지 않습니다."
          onCancel={() => setClosing(false)}
          onProceed={onClose}
          testId="billing-receipt-dirty"
        />
      )}
    </Dialog>
  );
}

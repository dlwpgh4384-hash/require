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
import "../bills/bills.css";

const won = (value: number) => value.toLocaleString("ko-KR");
const digits = (value: string) => value.replace(/[^\d]/g, "");
/** 숫자 칸: 숫자가 아닌 글자는 바로 지우고 천 단위 쉼표를 붙인다(00-1 FR-0106). */
const money = (value: string) => {
  const raw = digits(value);
  return raw === "" ? "" : raw.length > 15 ? raw : won(Number(raw));
};
const without = (errors: Record<string, string>, key: string) => {
  if (!errors[key]) return errors;
  const next = { ...errors };
  delete next[key];
  return next;
};

/**
 * UI-13-M2 입금 등록. 청구 하나면 시안 payModal 모양(입금일·입금액 칸), 여러 청구를 골랐으면
 * 입금일 하나와 청구별 입금액 표. 남는 돈은 다른 청구에 자동으로 나누지 않는다.
 */
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
  const single = eligible.length === 1 ? eligible[0] : null;
  const [date, setDate] = useState(koreanToday);
  const [amounts, setAmounts] = useState<Record<string, string>>(() =>
    Object.fromEntries(
      eligible.map((bill) => [bill.billId, won(bill.unpaidAmount)]),
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
  const changeAmount = (billId: string, value: string) => {
    setAmounts((previous) => ({ ...previous, [billId]: money(value) }));
    setErrors((previous) => without(previous, billId));
    setDirty(true);
  };
  async function save() {
    if (pending.current) return;
    const fields: Record<string, string> = {};
    if (!date) fields.receiptDate = "입금일을 입력해 주세요.";
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
      const raw = digits(amounts[bill.billId] ?? "");
      const amount = Number(raw);
      if (duplicates.has(bill.billId))
        fields[bill.billId] = "같은 청구는 한 번만 선택해 주세요.";
      else if (!raw || amount === 0)
        fields[bill.billId] = "입금액을 입력해 주세요.";
      else if (
        raw.replace(/^0+/, "").length > 15 ||
        !Number.isSafeInteger(amount)
      )
        fields[bill.billId] =
          "입금액은 15자리 이내의 원 단위 양수로 입력해 주세요.";
      else if (amount > bill.unpaidAmount)
        fields[bill.billId] = "입금액이 미수금보다 큽니다.";
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
          amount: Number(digits(amounts[bill.billId] ?? "")),
        })),
      });
      setDirty(false);
      onSaved(result);
    } catch (error) {
      const next: Record<string, string> = {};
      if (error instanceof ProjectRequestError) {
        // 서버는 줄 번호를 붙여 돌려준다(items[2].amount): 그 줄에만 보인다.
        for (const item of error.errors) {
          if (item.field === "receiptDate") next.receiptDate = item.message;
          else {
            const index = /^items\[(\d+)\]\.(?:amount|billId)$/.exec(
              item.field,
            );
            const bill = index ? eligible[Number(index[1])] : undefined;
            if (bill && !next[bill.billId]) next[bill.billId] = item.message;
          }
        }
      }
      setErrors(next);
      // 칸에 붙인 오류가 없을 때만 아래 줄에 보인다.
      setFailure(
        Object.keys(next).length
          ? ""
          : error instanceof Error
            ? error.message
            : "입금을 저장하지 못했습니다.",
      );
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  const dateField = (
    <div className="bill-fc">
      <label htmlFor="billing-receipt-date">
        입금일 <span className="field-required">*</span>
      </label>
      <input
        id="billing-receipt-date"
        type="date"
        data-testid="billing-receipt-date"
        value={date}
        disabled={saving}
        aria-invalid={!!errors.receiptDate}
        onChange={(event) => {
          setDate(event.target.value);
          setErrors((previous) => without(previous, "receiptDate"));
          setDirty(true);
        }}
      />
      {errors.receiptDate && (
        <small
          className="field-error"
          role="alert"
          data-testid="billing-receipt-date-error"
        >
          {errors.receiptDate}
        </small>
      )}
    </div>
  );
  return (
    <Dialog
      title="입금 등록"
      onClose={close}
      closable
      testId="billing-receipt-dialog"
    >
      <form
        className="billing-receipt-form bill-form"
        data-screen-id="UI-13-M2"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        {single ? (
          <>
            {/* 시안 payModal: 「프로젝트명 · 청구번호」, 입금일*, 입금액*(원), 미수금 안내 */}
            <p data-testid="billing-receipt-subtitle">
              {single.projectName} · {single.billId}
            </p>
            {dateField}
            <div className="bill-fc">
              <label htmlFor="billing-receipt-amount">
                입금액 <span className="field-required">*</span>
              </label>
              <div className="bill-row">
                <input
                  id="billing-receipt-amount"
                  inputMode="numeric"
                  className="bill-money"
                  data-testid="billing-receipt-amount-0"
                  disabled={saving}
                  value={amounts[single.billId] ?? ""}
                  aria-invalid={!!errors[single.billId]}
                  onChange={(event) =>
                    changeAmount(single.billId, event.target.value)
                  }
                />
                <span className="bill-sub">원</span>
              </div>
              <small data-testid="billing-receipt-help">
                미수금 {won(single.unpaidAmount)}원. 나눠 받으면 받은 금액만
                적으세요. 더 많이 들어온 돈은 다른 청구를 골라 따로 등록합니다.
              </small>
              {errors[single.billId] && (
                <small
                  className="field-error"
                  role="alert"
                  data-testid="billing-receipt-error-0"
                >
                  {errors[single.billId]}
                </small>
              )}
            </div>
          </>
        ) : (
          <>
            {dateField}
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
                      <td>{won(bill.unpaidAmount)}</td>
                      <td>
                        <input
                          type="text"
                          inputMode="numeric"
                          className="bill-money"
                          aria-label={`${bill.billId} 입금액`}
                          data-testid={`billing-receipt-amount-${index}`}
                          disabled={saving}
                          value={amounts[bill.billId] ?? ""}
                          aria-invalid={!!errors[bill.billId]}
                          onChange={(event) =>
                            changeAmount(bill.billId, event.target.value)
                          }
                        />
                        {errors[bill.billId] && (
                          <p
                            className="field-error"
                            role="alert"
                            data-testid={`billing-receipt-error-${index}`}
                          >
                            {errors[bill.billId]}
                          </p>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!eligible.length ? (
              <p>
                입금 가능한 청구가 없습니다. 현재 달 마감 여부와 잔액을 확인해
                주세요.
              </p>
            ) : (
              <p>
                미수금 전액이 기본값이며 분할 입금할 수 있습니다. 남는 금액은 다른
                청구에 자동 배분하지 않습니다.
              </p>
            )}
          </>
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
          title="저장하지 않은 내용이 있습니다"
          description="나가면 입력한 내용이 사라집니다."
          onCancel={() => setClosing(false)}
          onProceed={onClose}
          testId="billing-receipt-dirty"
        />
      )}
    </Dialog>
  );
}

import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { koreanThisMonth, koreanToday } from "../../shared/time";
import {
  BillingOptionsFields,
  useBillingOptions,
} from "../commonops/BillingOptionsFields";
import { ProjectRequestError } from "../projects/projects";
import {
  postCommonBill,
  putCommonBill,
  type Bill,
  type BillDetail,
  type BillValues,
} from "./bills";

export function BillForm({
  bill,
  copy = false,
  onClose,
  onSaved,
}: {
  bill?: Bill;
  copy?: boolean;
  onClose: () => void;
  onSaved: (value: BillDetail) => void;
}) {
  const [projectId, setProject] = useState(bill?.projectId ?? "");
  const [month, setMonth] = useState(bill?.billMonth ?? koreanThisMonth());
  const [supply, setSupply] = useState(bill ? String(bill.supplyAmount) : "");
  const [rate, setRate] = useState(bill ? String(bill.vatRate) : "");
  const [date, setDate] = useState(bill?.billDate ?? koreanToday());
  const [due, setDue] = useState(bill?.payDueDate ?? "");
  const [invoice, setInvoice] = useState(bill?.taxInvoiceNo ?? "");
  const [dirty, setDirty] = useState(false);
  const [closing, setClosing] = useState(false);
  const [warning, setWarning] = useState("");
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const {
    data: options,
    error: optionError,
    loading,
    retry,
  } = useBillingOptions(projectId || undefined, month);
  useEffect(() => {
    if (!bill && options) {
      setSupply(
        options.estimatedSupplyAmount == null
          ? ""
          : String(options.estimatedSupplyAmount),
      );
      setRate(String(options.vatRate));
      setDate(options.defaultBillDate);
      setDue(options.defaultPayDueDate);
    }
  }, [options, bill]);
  const change = (action: () => void) => {
    action();
    setDirty(true);
    setWarning("");
    setFields({});
  };
  const close = () => {
    if (!pending.current) {
      if (dirty) setClosing(true);
      else onClose();
    }
  };
  async function save() {
    if (pending.current) return;
    const invalid: Record<string, string> = {};
    if (!projectId) invalid.projectId = "프로젝트를 선택해 주세요.";
    if (!/^\d{6}$/.test(month)) invalid.billMonth = "청구월을 입력해 주세요.";
    if (!/^\d{1,15}$/.test(supply) || Number(supply) <= 0)
      invalid.supplyAmount = "공급가는 15자리 이내 원 단위 양수입니다.";
    if (!/^\d+(\.\d{1,2})?$/.test(rate) || Number(rate) > 100)
      invalid.vatRate = "세율은 0~100, 소수 둘째 자리 이내입니다.";
    if (!date) invalid.billDate = "청구일을 입력해 주세요.";
    if (!due || due < date)
      invalid.payDueDate =
        "입금 예정일은 청구일 이후 또는 같은 날이어야 합니다.";
    if (
      bill &&
      !copy &&
      Number(supply) + Math.floor((Number(supply) * Number(rate)) / 100) <
        bill.receiptSumAmount
    )
      invalid.supplyAmount = "합계는 정상 입금 합계보다 작을 수 없습니다.";
    setFields(invalid);
    setError("");
    if (Object.keys(invalid).length) return;
    pending.current = true;
    setSaving(true);
    const values: BillValues = {
      supplyAmount: Number(supply),
      vatRate: Number(rate),
      billDate: date,
      payDueDate: due,
      taxInvoiceNo: invoice.trim() || null,
    };
    try {
      if (bill && !copy) onSaved(await putCommonBill(bill.billId, values));
      else {
        const result = await postCommonBill({
          ...values,
          projectId,
          billMonth: month,
          ...(copy && bill ? { sourceBillId: bill.billId } : {}),
          duplicateConfirmed: !!warning,
        });
        if (result.data) onSaved(result.data);
        else if (result.warning) setWarning(result.warning);
        else setError("저장 결과가 없습니다. 다시 조회해 주세요.");
      }
    } catch (failure) {
      setError(
        failure instanceof Error
          ? failure.message
          : "청구를 저장하지 못했습니다.",
      );
      if (failure instanceof ProjectRequestError)
        setFields(
          Object.fromEntries(
            failure.errors.map((item) => [item.field, item.message]),
          ),
        );
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  const labels = {
    supplyAmount: "공급가",
    vatRate: "부가세율",
    billDate: "청구일",
    payDueDate: "입금 예정일",
    taxInvoiceNo: "세금계산서 번호",
  };
  return (
    <Dialog
      title={bill ? (copy ? "고쳐서 청구" : "청구 수정") : "청구 등록"}
      onClose={close}
      closable
      testId="bill-form"
    >
      <form
        className="bill-form"
        data-screen-id="UI-13-M1"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        <BillingOptionsFields
          projectId={projectId}
          billMonth={month}
          fixed={!!bill}
          onProject={(id) => change(() => setProject(id))}
          onMonth={(value) => change(() => setMonth(value))}
        />
        {bill && (
          <p>
            프로젝트: {bill.projectName} · 청구월: {bill.billMonth} · 원청구
            차수: {bill.billOrder} (변경 불가)
          </p>
        )}
        {optionError && (
          <p role="alert">
            {optionError}
            <button
              type="button"
              data-testid="bill-form-options-retry"
              onClick={retry}
            >
              재시도
            </button>
          </p>
        )}
        {Object.entries(labels).map(([key, label]) => {
          const value = {
            supplyAmount: supply,
            vatRate: rate,
            billDate: date,
            payDueDate: due,
            taxInvoiceNo: invoice,
          }[key as keyof typeof labels];
          const setter = {
            supplyAmount: setSupply,
            vatRate: setRate,
            billDate: setDate,
            payDueDate: setDue,
            taxInvoiceNo: setInvoice,
          }[key as keyof typeof labels];
          return (
            <label key={key}>
              {label}
              <input
                data-testid={`bill-form-${key}`}
                aria-label={label}
                disabled={saving}
                type={
                  key === "billDate" || key === "payDueDate" ? "date" : "text"
                }
                maxLength={key === "taxInvoiceNo" ? 30 : undefined}
                value={value}
                aria-invalid={!!fields[key]}
                onChange={(event) => change(() => setter(event.target.value))}
              />
              {fields[key] && (
                <span className="field-error" role="alert">
                  {fields[key]}
                </span>
              )}
            </label>
          );
        })}
        {!invoice.trim() && (
          <p>번호 미입력: 세금계산서 번호 없이 저장할 수 있습니다.</p>
        )}
        {fields.projectId && <p role="alert">{fields.projectId}</p>}
        {fields.billMonth && <p role="alert">{fields.billMonth}</p>}
        {warning && (
          <p role="alert" data-testid="bill-duplicate-warning">
            {warning}
          </p>
        )}
        {error && <p role="alert">{error}</p>}
        <div className="bill-actions">
          <button
            type="button"
            className="ls-btn"
            data-testid="bill-form-cancel"
            disabled={saving}
            onClick={close}
          >
            취소
          </button>
          <button
            type="submit"
            className="ls-btn ls-btn-p"
            data-testid="bill-form-save"
            disabled={saving || (!bill && (loading || !!optionError))}
          >
            {saving ? "저장 중…" : warning ? "추가 등록" : "저장"}
          </button>
        </div>
      </form>
      {closing && (
        <InlineConfirmation
          title="저장하지 않고 닫을까요?"
          description="입력한 내용은 저장되지 않습니다."
          onCancel={() => setClosing(false)}
          onProceed={onClose}
          testId="bill-form-dirty"
        />
      )}
    </Dialog>
  );
}

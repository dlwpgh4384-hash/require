import { useEffect, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { ProjectRequestError } from "../projects/projects";
import {
  getBillCheckDetail,
  getBillCheckOptions,
  postCommonBillCheckAdditional,
  postCommonBillCheckCorrection,
  postCommonBillCheckMissing,
  type BillCheckDetail,
  type BillCheckRow,
} from "./billChecks";

const actionLabels: Record<string, string> = {
  MISSING: "청구 등록",
  ADDITIONAL: "추가 청구",
  CORRECTION: "청구 고치기",
  DETAIL: "상세 확인",
};
export const billCheckActionLabel = (action: string) =>
  actionLabels[action] ?? "";

export function BillCheckForm({
  row,
  checkMonth,
  onClose,
  onSaved,
}: {
  row: BillCheckRow;
  checkMonth: string;
  onClose: () => void;
  onSaved: (detail: BillCheckDetail) => void;
}) {
  const [initial, setInitial] = useState<{
    vatRate: string;
    billDate: string;
    payDueDate: string;
    taxInvoiceNo: string;
  } | null>(null);
  const [values, setValues] = useState({
    vatRate: "",
    billDate: "",
    payDueDate: "",
    taxInvoiceNo: "",
  });
  const [supply, setSupply] = useState<number | null>(null);
  const [customer, setCustomer] = useState("");
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [discard, setDiscard] = useState(false);
  const [revision, setRevision] = useState(0);
  const setDirty = useScreenDirty();
  const dirty =
    initial !== null && JSON.stringify(values) !== JSON.stringify(initial);
  useEffect(() => {
    setDirty("UI-13", dirty);
    return () => setDirty("UI-13", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setInitial(null);
    void (async () => {
      const options = await getBillCheckOptions(checkMonth, controller.signal);
      let selectedSupply =
        row.allowedAction === "ADDITIONAL"
          ? row.difference
          : row.referenceAmount;
      let taxInvoiceNo = "";
      let defaults = {
        vatRate: String(options.vatRate),
        billDate: options.defaultBillDate,
        payDueDate: options.defaultPayDueDate,
        taxInvoiceNo,
      };
      if (row.allowedAction === "CORRECTION" && row.latestBillId) {
        const detail = await getBillCheckDetail(
          row.latestBillId,
          controller.signal,
        );
        selectedSupply = detail.bill.supplyAmount + row.difference;
        defaults = {
          vatRate: String(detail.bill.vatRate),
          billDate: detail.bill.billDate,
          payDueDate: detail.bill.payDueDate,
          taxInvoiceNo: detail.bill.taxInvoiceNo ?? "",
        };
      }
      if (controller.signal.aborted) return;
      setSupply(selectedSupply);
      setCustomer(
        (options.projects ?? []).find((p) => p.projectId === row.projectId)
          ?.customerName ?? "",
      );
      setValues(defaults);
      setInitial(defaults);
    })().catch((failure: unknown) => {
      if (!controller.signal.aborted)
        setError(
          failure instanceof Error
            ? failure.message
            : "등록 정보를 불러오지 못했습니다.",
        );
    });
    return () => controller.abort();
  }, [row, checkMonth, revision]);
  const close = () => {
    if (!saving) {
      if (dirty) setDiscard(true);
      else onClose();
    }
  };
  const change = (field: keyof typeof values, value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
    setFields((previous) => ({ ...previous, [field]: "" }));
  };
  const save = async () => {
    if (!initial || saving) return;
    const next: Record<string, string> = {};
    const rate = Number(values.vatRate);
    if (
      values.vatRate.trim() === "" ||
      !Number.isFinite(rate) ||
      rate < 0 ||
      rate > 100 ||
      !/^\d+(\.\d{1,2})?$/.test(values.vatRate)
    )
      next.vatRate = "부가세율은 0~100, 소수 둘째 자리까지 입력해 주세요.";
    if (!values.billDate) next.billDate = "청구일을 입력해 주세요.";
    if (!values.payDueDate || values.payDueDate < values.billDate)
      next.payDueDate = "입금 예정일은 청구일 이후여야 합니다.";
    if (values.taxInvoiceNo.length > 30)
      next.taxInvoiceNo = "세금계산서 번호는 30자까지 입력해 주세요.";
    setFields(next);
    if (Object.keys(next).length) return;
    setSaving(true);
    setError("");
    try {
      const input = {
        projectId: row.projectId,
        checkMonth,
        vatRate: rate,
        billDate: values.billDate,
        payDueDate: values.payDueDate,
        taxInvoiceNo: values.taxInvoiceNo.trim() || null,
      };
      const submit =
        row.allowedAction === "MISSING"
          ? postCommonBillCheckMissing
          : row.allowedAction === "ADDITIONAL"
            ? postCommonBillCheckAdditional
            : postCommonBillCheckCorrection;
      const result = await submit(input);
      onSaved(result);
    } catch (failure) {
      if (failure instanceof ProjectRequestError)
        setFields(
          Object.fromEntries(failure.errors.map((f) => [f.field, f.message])),
        );
      setError(
        failure instanceof Error
          ? failure.message
          : "청구를 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog
      title={billCheckActionLabel(row.allowedAction)}
      onClose={close}
      testId="bill-check-form"
      closable
    >
      {discard ? (
        <InlineConfirmation
          title="저장하지 않고 닫을까요?"
          description="입력한 내용은 저장되지 않습니다."
          onCancel={() => setDiscard(false)}
          onProceed={onClose}
        />
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <div className="bill-check-fields">
            <label>
              프로젝트
              <input
                data-testid="bill-check-project"
                readOnly
                value={[row.projectName, customer].filter(Boolean).join(" · ")}
              />
            </label>
            <label>
              청구월
              <input
                data-testid="bill-check-month-fixed"
                readOnly
                value={`${checkMonth.slice(0, 4)}-${checkMonth.slice(4)}`}
              />
            </label>
            <label>
              공급가
              <input
                data-testid="bill-check-supply"
                readOnly
                value={supply ?? ""}
              />
            </label>
            <p className="project-cap">
              공급가는 저장 직전 서버에서 다시 계산합니다. 번호와 차수는 자동
              발급됩니다.
            </p>
            <label>
              부가세율
              <input
                data-testid="bill-check-vat-rate"
                type="number"
                min="0"
                max="100"
                step="0.01"
                value={values.vatRate}
                disabled={!initial || saving}
                onChange={(e) => change("vatRate", e.target.value)}
                aria-invalid={!!fields.vatRate}
              />
              {fields.vatRate && <span role="alert">{fields.vatRate}</span>}
            </label>
            <label>
              청구일
              <input
                data-testid="bill-check-bill-date"
                type="date"
                value={values.billDate}
                disabled={!initial || saving}
                onChange={(e) => change("billDate", e.target.value)}
                aria-invalid={!!fields.billDate}
              />
              {fields.billDate && <span role="alert">{fields.billDate}</span>}
            </label>
            <label>
              입금 예정일
              <input
                data-testid="bill-check-due-date"
                type="date"
                value={values.payDueDate}
                disabled={!initial || saving}
                onChange={(e) => change("payDueDate", e.target.value)}
                aria-invalid={!!fields.payDueDate}
              />
              {fields.payDueDate && (
                <span role="alert">{fields.payDueDate}</span>
              )}
            </label>
            <label>
              세금계산서 번호
              <input
                data-testid="bill-check-tax-number"
                maxLength={30}
                value={values.taxInvoiceNo}
                disabled={!initial || saving}
                onChange={(e) => change("taxInvoiceNo", e.target.value)}
                aria-invalid={!!fields.taxInvoiceNo}
              />
              {fields.taxInvoiceNo && (
                <span role="alert">{fields.taxInvoiceNo}</span>
              )}
            </label>
            {!values.taxInvoiceNo.trim() && (
              <p className="project-warn">
                번호 미입력: 저장할 수 있지만 세금계산서 번호를 확인해 주세요.
              </p>
            )}
          </div>
          {!initial && !error && (
            <p role="status">등록 정보를 불러오는 중입니다.</p>
          )}
          {error && <p role="alert">{error}</p>}
          {fields.checkMonth && <p role="alert">{fields.checkMonth}</p>}
          {fields.projectId && <p role="alert">{fields.projectId}</p>}
          {!initial && error && (
            <button
              type="button"
              data-testid="bill-check-options-retry"
              onClick={() => setRevision((v) => v + 1)}
            >
              다시 시도
            </button>
          )}
          <div className="app-actions">
            <button
              type="button"
              data-testid="bill-check-form-cancel"
              onClick={close}
              disabled={saving}
            >
              취소
            </button>
            <button
              type="submit"
              className="primary-button"
              data-testid="bill-check-save"
              disabled={!initial || saving}
            >
              {saving
                ? "저장 중"
                : row.allowedAction === "CORRECTION"
                  ? "저장"
                  : "등록"}
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

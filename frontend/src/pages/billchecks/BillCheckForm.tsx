import { useEffect, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { Banner } from "../../shared/list";
import { ProjectRequestError } from "../projects/projects";
import {
  billMonthLabel,
  defaultPayDue,
  failureMessage,
  isConflict,
} from "../bills/bills";
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
  ADDITIONAL: "청구 고치기",
  CORRECTION: "청구 고치기",
  DETAIL: "청구 고치기",
};
/** 시안 bchkfix 단추: 청구 없음 → [청구 등록], 금액 차이 → [청구 고치기]. */
export const billCheckActionLabel = (action: string) =>
  actionLabels[action] ?? "";

/** 칸 오류는 그 칸 밑에 보이고 그 칸으로 간다(00-1 FR-0106). 서버 errors[].field도 같은 이름. */
type FormField = "supplyAmount" | "vatRate" | "billDate" | "payDueDate" | "taxInvoiceNo";
const FIELD_INPUT: Record<FormField, string> = {
  supplyAmount: "bill-check-supply",
  vatRate: "bill-check-vat-rate",
  billDate: "bill-check-bill-date",
  payDueDate: "bill-check-due-date",
  taxInvoiceNo: "bill-check-tax-number",
};
const won = (value: number) => value.toLocaleString("ko-KR");
const digits = (value: string) => value.replace(/[^\d]/g, "");

/** UI-13 청구 누락 점검 [청구 등록]·[청구 고치기] 창(시안 bchkFix → billNew): 공급가를 채워 열고 고칠 수 있다. */
export function BillCheckForm({
  row,
  checkMonth,
  onClose,
  onSaved,
  onConflict,
}: {
  row: BillCheckRow;
  checkMonth: string;
  onClose: () => void;
  onSaved: (detail: BillCheckDetail, message: string) => void;
  /** 다른 사람이 먼저 바꿨을 때(409): 화면이 공통 알림을 띄우고 다시 불러온다. */
  onConflict?: (message: string) => void;
}) {
  const [initial, setInitial] = useState<{
    supply: string;
    vatRate: string;
    billDate: string;
    payDueDate: string;
    taxInvoiceNo: string;
  } | null>(null);
  const [values, setValues] = useState({
    supply: "",
    vatRate: "",
    billDate: "",
    payDueDate: "",
    taxInvoiceNo: "",
  });
  const [message, setMessage] = useState("");
  const [title, setTitle] = useState(billCheckActionLabel(row.allowedAction));
  const [customer, setCustomer] = useState("");
  const [error, setError] = useState("");
  // 칸 오류(공급가·세율·날짜·계산서 번호)는 그 칸 밑에 보이고 그 칸으로 간다(00-1 FR-0106)
  const [fieldErrors, setFieldErrors] = useState<
    Partial<Record<FormField, string>>
  >({});
  const [saving, setSaving] = useState(false);
  const [discard, setDiscard] = useState(false);
  const [revision, setRevision] = useState(0);
  // [청구 고치기]: 고칠 청구를 읽었을 때의 수정 시각(00-1 동시 수정)
  const [modifiedAt, setModifiedAt] = useState<string | null>(null);
  const setDirty = useScreenDirty();
  const dirty =
    initial !== null && JSON.stringify(values) !== JSON.stringify(initial);
  const M = `${Number(checkMonth.slice(4))}월`;
  useEffect(() => {
    setDirty("UI-13", dirty);
    return () => setDirty("UI-13", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setInitial(null);
    setModifiedAt(null);
    void (async () => {
      const options = await getBillCheckOptions(checkMonth, controller.signal);
      let supply =
        row.allowedAction === "ADDITIONAL" ? row.difference : row.referenceAmount;
      let defaults = {
        vatRate: String(options.vatRate),
        billDate: options.defaultBillDate,
        // 청구 화면 [청구 등록]과 같은 기본값: 지난 달이면 청구일 다음 달 25일까지 늦춘다
        payDueDate: defaultPayDue(checkMonth, options.defaultBillDate),
        taxInvoiceNo: "",
      };
      let text = `${M} 투입 기준 금액 ${won(row.referenceAmount)}원으로 채웠습니다. 고쳐서 등록할 수 있습니다.`;
      let heading = "청구 등록";
      let read: string | null = null;
      if (row.allowedAction === "ADDITIONAL") {
        heading = "추가 청구 (차액)";
        text = `${M}은 마감한 달이라 기존 청구는 고칠 수 없습니다. 모자란 ${won(row.difference)}원을 같은 달 추가 청구로 등록합니다.`;
      }
      if (row.allowedAction === "CORRECTION" && row.latestBillId) {
        const detail = await getBillCheckDetail(row.latestBillId, controller.signal);
        const last = detail.bill.supplyAmount;
        read = detail.bill.modifiedAt;
        supply = last + row.difference;
        heading = `청구 수정 · ${detail.bill.billId}`;
        text = `투입 기준에 맞추려면 공급가를 ${won(last)}원에서 ${won(supply)}원으로 고칩니다(차이 ${row.difference > 0 ? "+" : ""}${won(row.difference)}원).`;
        defaults = {
          vatRate: String(detail.bill.vatRate),
          billDate: detail.bill.billDate,
          payDueDate: detail.bill.payDueDate,
          taxInvoiceNo: detail.bill.taxInvoiceNo ?? "",
        };
      }
      if (controller.signal.aborted) return;
      setCustomer(
        (options.projects ?? []).find((p) => p.projectId === row.projectId)
          ?.customerName ?? "",
      );
      const start = { supply: supply > 0 ? won(supply) : "", ...defaults };
      setModifiedAt(read);
      setTitle(heading);
      setMessage(text);
      setValues(start);
      setInitial(start);
    })().catch((failure: unknown) => {
      if (!controller.signal.aborted)
        setError(
          failure instanceof Error
            ? failure.message
            : "등록 정보를 불러오지 못했습니다.",
        );
    });
    return () => controller.abort();
  }, [row, checkMonth, revision, M]);
  const close = () => {
    if (saving) return;
    if (dirty) setDiscard(true);
    else onClose();
  };
  const change = (field: keyof typeof values, value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
    setError("");
    const key: FormField = field === "supply" ? "supplyAmount" : field;
    setFieldErrors((previous) => {
      if (!previous[key]) return previous;
      const next = { ...previous };
      delete next[key];
      return next;
    });
  };
  const showFieldError = (field: FormField, text: string) => {
    setFieldErrors({ [field]: text });
    window.setTimeout(
      () => document.getElementById(FIELD_INPUT[field])?.focus(),
      0,
    );
  };
  const fieldError = (field: FormField) =>
    fieldErrors[field] ? (
      <small
        role="alert"
        className="field-error"
        data-testid={`bill-check-error-${field}`}
      >
        {fieldErrors[field]}
      </small>
    ) : null;
  const save = async () => {
    if (!initial || saving) return;
    const supply = digits(values.supply);
    const rate = values.vatRate.trim();
    if (!supply || Number(supply) <= 0)
      return showFieldError("supplyAmount", "공급가를 입력해 주세요.");
    if (rate === "" || !/^\d+(\.\d{1,2})?$/.test(rate) || Number(rate) > 100)
      return showFieldError("vatRate", "부가세율을 0~100 사이로 입력해 주세요.");
    if (!values.payDueDate)
      return showFieldError("payDueDate", "입금 예정일을 입력해 주세요.");
    if (values.payDueDate < values.billDate)
      return showFieldError("payDueDate", "입금 예정일이 청구일보다 빠릅니다.");
    setSaving(true);
    setError("");
    setFieldErrors({});
    try {
      const input = {
        projectId: row.projectId,
        checkMonth,
        vatRate: Number(rate),
        billDate: values.billDate,
        payDueDate: values.payDueDate,
        taxInvoiceNo: values.taxInvoiceNo.trim() || null,
        supplyAmount: Number(supply),
        ...(row.allowedAction === "CORRECTION" && modifiedAt ? { modifiedAt } : {}),
      };
      const submit =
        row.allowedAction === "MISSING"
          ? postCommonBillCheckMissing
          : row.allowedAction === "ADDITIONAL"
            ? postCommonBillCheckAdditional
            : postCommonBillCheckCorrection;
      const result = await submit(input);
      onSaved(
        result,
        row.allowedAction === "CORRECTION"
          ? "청구를 고쳤습니다."
          : `${row.projectName} ${billMonthLabel(checkMonth)} 청구를 등록했습니다.`,
      );
    } catch (failure) {
      // 서버 칸 오류(errors[].field)가 창의 칸이면 그 칸 밑에 붙인다. 나머지만 아래 줄에 보인다
      const placed =
        failure instanceof ProjectRequestError
          ? failure.errors.find((item) =>
              Object.prototype.hasOwnProperty.call(FIELD_INPUT, item.field),
            )
          : undefined;
      if (isConflict(failure) && onConflict) onConflict(failureMessage(failure, ""));
      else if (placed) showFieldError(placed.field as FormField, placed.message);
      else
        setError(
          failure instanceof ProjectRequestError || failure instanceof Error
            ? failure.message
            : "청구를 저장하지 못했습니다.",
        );
    } finally {
      setSaving(false);
    }
  };
  return (
    <Dialog title={title} onClose={close} testId="bill-check-form" closable>
      {discard ? (
        <InlineConfirmation
          title="저장하지 않은 내용이 있습니다"
          description="나가면 입력한 내용이 사라집니다."
          onCancel={() => setDiscard(false)}
          onProceed={onClose}
        />
      ) : (
        <form
          className="bill-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          {message && (
            <Banner kind="info">
              <span data-testid="bill-check-message">{message}</span>
            </Banner>
          )}
          <div className="bill-fc">
            <label>프로젝트</label>
            <span data-testid="bill-check-project">
              {[row.projectName, customer].filter(Boolean).join(" · ")}
            </span>
          </div>
          <div className="bill-fc">
            <label htmlFor="bill-check-month-fixed">청구월</label>
            <input
              id="bill-check-month-fixed"
              type="month"
              data-testid="bill-check-month-fixed"
              disabled
              value={billMonthLabel(checkMonth)}
            />
          </div>
          <div className="bill-fc">
            <label htmlFor="bill-check-supply">
              공급가 <span className="field-required">*</span>
            </label>
            <div className="bill-row">
              <input
                id="bill-check-supply"
                data-testid="bill-check-supply"
                inputMode="numeric"
                className="bill-money"
                value={values.supply}
                disabled={!initial || saving}
                aria-invalid={fieldErrors.supplyAmount ? true : undefined}
                onChange={(e) => {
                  const raw = digits(e.target.value);
                  change("supply", raw === "" ? "" : Number(raw).toLocaleString("ko-KR"));
                }}
              />
              <span className="bill-sub">원</span>
            </div>
            {fieldError("supplyAmount")}
            <small>
              월단위·파견은 그 달 투입 MM × 청구 단가 합계(식-02)로 채웁니다. 턴키는 직접 넣으세요.
            </small>
          </div>
          <div className="bill-fc">
            <label htmlFor="bill-check-vat-rate">
              부가세율 <span className="field-required">*</span>
            </label>
            <div className="bill-row">
              <input
                id="bill-check-vat-rate"
                data-testid="bill-check-vat-rate"
                inputMode="decimal"
                className="bill-rate"
                value={values.vatRate}
                disabled={!initial || saving}
                aria-invalid={fieldErrors.vatRate ? true : undefined}
                onChange={(e) => change("vatRate", e.target.value)}
              />
              <span className="bill-sub">%</span>
            </div>
            {fieldError("vatRate")}
            <small>
              기본 10%. 면세·영세율은 0. 부가세 금액은 저장할 때 공급가 × 세율(원 단위 버림)로 계산합니다.
            </small>
          </div>
          <div className="bill-fc">
            <label htmlFor="bill-check-bill-date">
              청구일 <span className="field-required">*</span>
            </label>
            <input
              id="bill-check-bill-date"
              data-testid="bill-check-bill-date"
              type="date"
              value={values.billDate}
              disabled={!initial || saving}
              aria-invalid={fieldErrors.billDate ? true : undefined}
              onChange={(e) => change("billDate", e.target.value)}
            />
            {fieldError("billDate")}
          </div>
          <div className="bill-fc">
            <label htmlFor="bill-check-due-date">
              입금 예정일 <span className="field-required">*</span>
            </label>
            <input
              id="bill-check-due-date"
              data-testid="bill-check-due-date"
              type="date"
              value={values.payDueDate}
              disabled={!initial || saving}
              aria-invalid={fieldErrors.payDueDate ? true : undefined}
              onChange={(e) => change("payDueDate", e.target.value)}
            />
            {fieldError("payDueDate")}
            <small>기본은 청구월 다음 달 25일. 연체와 미수 알림이 이 날짜부터 셉니다.</small>
          </div>
          <div className="bill-fc">
            <label htmlFor="bill-check-tax-number">세금계산서 번호</label>
            <input
              id="bill-check-tax-number"
              data-testid="bill-check-tax-number"
              maxLength={30}
              placeholder="예: 20261025-41000012-01"
              value={values.taxInvoiceNo}
              disabled={!initial || saving}
              aria-invalid={fieldErrors.taxInvoiceNo ? true : undefined}
              onChange={(e) => change("taxInvoiceNo", e.target.value)}
            />
            {fieldError("taxInvoiceNo")}
            <small>
              계산서를 아직 안 끊었으면 비워 두세요. 비어 있으면 “번호 미입력”으로 표시되고 월 마감 점검에 잡힙니다.
            </small>
          </div>
          {!initial && !error && (
            <p role="status">등록 정보를 불러오는 중입니다.</p>
          )}
          <p role="alert" className="field-error" data-testid="bill-check-error">
            {error}
          </p>
          {!initial && error && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="bill-check-options-retry"
              onClick={() => setRevision((v) => v + 1)}
            >
              다시 시도
            </button>
          )}
          <div className="app-actions">
            <button
              type="button"
              className="ls-btn"
              data-testid="bill-check-form-cancel"
              onClick={close}
              disabled={saving}
            >
              취소
            </button>
            <button
              type="submit"
              className="ls-btn ls-btn-p"
              data-testid="bill-check-save"
              disabled={!initial || saving}
            >
              {row.allowedAction === "CORRECTION" ? "저장" : "등록"}
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

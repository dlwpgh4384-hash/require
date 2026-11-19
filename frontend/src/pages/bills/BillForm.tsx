import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { focusFirstError } from "../../shared/feedback/messages";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { Banner } from "../../shared/list";
import { ProjectRequestError } from "../projects/projects";
import { koreanThisMonth, koreanToday } from "../../shared/time";
import {
  getCommonBillOptions,
  type BillingOptions,
} from "../commonops/billingLookups";
import {
  billMoney,
  billMonthLabel,
  defaultPayDue,
  failureMessage,
  isConflict,
  postCommonBill,
  putCommonBill,
  type Bill,
  type BillDetail,
  type BillValues,
} from "./bills";

/** 프로젝트·청구월을 고정해 여는 등록 창(시안 billNew의 pre): [고쳐서 청구]. */
export interface BillFormPreset {
  projectId: string;
  projectName: string;
  billMonth: string;
  supplyAmount: number | null;
  vatRate?: number;
  title?: string;
  message?: string;
  sourceBillId?: string;
}

const digits = (value: string) => value.replace(/[^\d]/g, "");
/** 칸마다 오류를 그 칸 밑에 보인다(00-1 FR-0106). 서버 errors[].field도 같은 이름으로 붙인다. */
const FIELDS = [
  "projectId",
  "billMonth",
  "supplyAmount",
  "vatRate",
  "billDate",
  "payDueDate",
  "taxInvoiceNo",
] as const;
type Field = (typeof FIELDS)[number];
type FieldErrors = Partial<Record<Field, string>>;
const money = (value: string) => {
  const raw = digits(value);
  return raw === "" ? "" : Number(raw).toLocaleString("ko-KR");
};

/** 창이 열려 있는 동안만 선택지를 읽는다(고정 창은 읽지 않는다). */
function useOptions(enabled: boolean, projectId: string, billMonth: string) {
  const [data, setData] = useState<BillingOptions | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    setError("");
    void getCommonBillOptions(
      { projectId: projectId || undefined, billMonth: billMonth || undefined },
      controller.signal,
    ).then(
      (value) => {
        if (!controller.signal.aborted) setData(value);
      },
      (failure) => {
        if (!controller.signal.aborted)
          setError(failureMessage(failure, "청구 선택지를 불러오지 못했습니다."));
      },
    );
    return () => controller.abort();
  }, [enabled, projectId, billMonth, revision]);
  return { data, error, retry: () => setRevision((v) => v + 1) };
}

/** UI-13-M1 청구 등록·수정 창(시안 billNew). */
export function BillForm({
  bill,
  preset,
  onClose,
  onSaved,
  onConflict,
}: {
  /** 있으면 [수정](프로젝트·청구월 고정). */
  bill?: Bill;
  preset?: BillFormPreset;
  onClose: () => void;
  onSaved: (value: BillDetail, message: string) => void;
  /** 다른 사람이 먼저 바꿨을 때(409). 화면이 다시 불러온다. */
  onConflict?: (message: string) => void;
}) {
  const fixed = !!bill || !!preset;
  const [projectId, setProject] = useState(
    bill?.projectId ?? preset?.projectId ?? "",
  );
  const [month, setMonth] = useState(
    bill?.billMonth ?? preset?.billMonth ?? koreanThisMonth(),
  );
  const [supply, setSupply] = useState(
    bill
      ? billMoney(bill.supplyAmount)
      : preset?.supplyAmount != null
        ? billMoney(preset.supplyAmount)
        : "",
  );
  const [rate, setRate] = useState(
    bill ? String(bill.vatRate) : preset?.vatRate != null ? String(preset.vatRate) : "",
  );
  const [date, setDate] = useState(bill?.billDate ?? koreanToday());
  const [due, setDue] = useState(
    bill?.payDueDate ?? defaultPayDue(preset?.billMonth ?? koreanThisMonth()),
  );
  const [invoice, setInvoice] = useState(bill?.taxInvoiceNo ?? "");
  const [dirty, setDirty] = useState(false);
  const [closing, setClosing] = useState(false);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [warned, setWarned] = useState("");
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  // 화면 탭 「●」와 탭 닫기 확인(00-1 FR-0103): 청구 누락 점검 창과 같은 방식
  const setScreenDirty = useScreenDirty();
  useEffect(() => {
    setScreenDirty("UI-13", dirty);
    return () => setScreenDirty("UI-13", false);
  }, [dirty, setScreenDirty]);
  // 새 청구: 프로젝트 목록(청구월 기준)과 고른 프로젝트의 추정 공급가·기본 세율
  const list = useOptions(!fixed, "", month);
  const picked = useOptions(!fixed && !!projectId, projectId, month);
  // 고정 창인데 세율을 받지 못했을 때만 기본 세율(운영 기준값 VAT_RT)을 따로 읽는다.
  const defaults = useOptions(!!preset && preset.vatRate == null, "", month);
  useEffect(() => {
    if (fixed || !picked.data) return;
    const estimate = picked.data.estimatedSupplyAmount;
    setSupply(estimate ? billMoney(estimate) : "");
  }, [picked.data, fixed]);
  useEffect(() => {
    const source = list.data ?? defaults.data;
    if (!bill && preset?.vatRate == null && source && rate === "")
      setRate(String(source.vatRate));
  }, [list.data, defaults.data, bill, preset, rate]);
  const change = (action: () => void, field?: Field) => {
    action();
    setDirty(true);
    setError("");
    if (field)
      setErrors((previous) => {
        if (!previous[field]) return previous;
        const next = { ...previous };
        delete next[field];
        return next;
      });
  };
  const showErrors = (next: FieldErrors) => {
    setErrors(next);
    window.setTimeout(() => focusFirstError(formRef.current), 0);
  };
  const invalid = (field: Field) =>
    errors[field] ? { "aria-invalid": true as const } : {};
  const fieldError = (field: Field) =>
    errors[field] ? (
      <small
        role="alert"
        className="field-error"
        data-testid={`bill-form-error-${field}`}
      >
        {errors[field]}
      </small>
    ) : null;
  const close = () => {
    if (pending.current) return;
    if (dirty) setClosing(true);
    else onClose();
  };
  const projectName =
    bill?.projectName ??
    preset?.projectName ??
    (list.data?.projects ?? []).find((p) => p.projectId === projectId)
      ?.projectName ??
    "";
  async function save() {
    if (pending.current) return;
    const value = digits(supply);
    const vr = rate.trim();
    // 저장할 때 전체를 검사하고, 틀린 칸 밑에 이유를 보인 뒤 첫 오류 칸으로 간다(00-1 FR-0106).
    const found: FieldErrors = {};
    if (!projectId) found.projectId = "프로젝트를 골라 주세요.";
    if (!/^\d{6}$/.test(month)) found.billMonth = "청구월을 입력해 주세요.";
    if (!value || Number(value) <= 0) found.supplyAmount = "공급가를 입력해 주세요.";
    if (vr === "" || !/^\d+(\.\d{1,2})?$/.test(vr) || Number(vr) > 100)
      found.vatRate = "부가세율을 0~100 사이로 입력해 주세요.";
    if (!date) found.billDate = "청구일을 입력해 주세요.";
    if (!due) found.payDueDate = "입금 예정일을 입력해 주세요.";
    else if (date && due < date) found.payDueDate = "입금 예정일이 청구일보다 빠릅니다.";
    const supplyAmount = Number(value);
    if (!found.supplyAmount && !found.vatRate && bill) {
      const total = supplyAmount + Math.floor((supplyAmount * Number(vr)) / 100);
      if (total < bill.receiptSumAmount)
        found.supplyAmount = `합계가 이미 받은 금액(${billMoney(bill.receiptSumAmount)}원)보다 작습니다.`;
    }
    setError("");
    if (Object.keys(found).length) return showErrors(found);
    setErrors({});
    pending.current = true;
    setSaving(true);
    const values: BillValues = {
      supplyAmount,
      vatRate: Number(vr),
      billDate: date,
      payDueDate: due,
      taxInvoiceNo: invoice.trim() || null,
    };
    try {
      if (bill) {
        const saved = await putCommonBill(bill.billId, {
          ...values,
          modifiedAt: bill.modifiedAt,
        });
        onSaved(saved, "청구를 고쳤습니다.");
        return;
      }
      const key = projectId + month;
      const result = await postCommonBill({
        ...values,
        projectId,
        billMonth: month,
        ...(preset?.sourceBillId ? { sourceBillId: preset.sourceBillId } : {}),
        duplicateConfirmed: warned === key,
      });
      if (result.data)
        onSaved(
          result.data,
          `${result.data.bill.projectName || projectName} ${billMonthLabel(month)} 청구를 등록했습니다.`,
        );
      else if (result.warning) {
        // 시안: 같은 달 청구가 있으면 경고만 하고, [등록]을 한 번 더 누르면 만든다.
        setWarned(key);
        setError(result.warning);
      } else setError("저장 결과가 없습니다. 다시 조회해 주세요.");
    } catch (failure) {
      if (isConflict(failure) && onConflict) onConflict(failureMessage(failure, ""));
      else {
        // 서버 칸 오류(errors[].field)는 그 칸 밑에 붙이고 첫 오류 칸으로 간다. 칸이 없는 오류만 아래 줄에 보인다.
        const fromServer: FieldErrors = {};
        if (failure instanceof ProjectRequestError)
          for (const item of failure.errors)
            if ((FIELDS as readonly string[]).includes(item.field) && !fromServer[item.field as Field])
              fromServer[item.field as Field] = item.message;
        if (Object.keys(fromServer).length) showErrors(fromServer);
        else setError(failureMessage(failure, "청구를 저장하지 못했습니다."));
      }
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  const title = bill
    ? `청구 수정 · ${bill.billId}`
    : (preset?.title ?? "청구 등록");
  const optionError = list.error || picked.error;
  return (
    <Dialog title={title} onClose={close} closable testId="bill-form">
      <form
        ref={formRef}
        className="bill-form"
        data-screen-id="UI-13-M1"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
      >
        {preset?.message && (
          <Banner kind="info">
            <span data-testid="bill-form-message">{preset.message}</span>
          </Banner>
        )}
        <div className="bill-fc">
          <label htmlFor="bill-form-project">
            프로젝트 <span className="field-required">*</span>
          </label>
          {fixed ? (
            <span data-testid="bill-form-project-fixed" {...invalid("projectId")}>
              {projectName}
            </span>
          ) : (
            <select
              id="bill-form-project"
              data-testid="bill-form-project"
              value={projectId}
              disabled={saving}
              {...invalid("projectId")}
              onChange={(event) =>
                change(() => setProject(event.target.value), "projectId")
              }
            >
              <option value="">프로젝트 선택</option>
              {(list.data?.projects ?? []).map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName} · {p.customerName}
                </option>
              ))}
            </select>
          )}
          {fieldError("projectId")}
        </div>
        <div className="bill-fc">
          <label htmlFor="bill-form-month">
            청구월 <span className="field-required">*</span>
          </label>
          <input
            id="bill-form-month"
            type="month"
            data-testid="bill-form-month"
            disabled={fixed || saving}
            value={billMonthLabel(month)}
            {...invalid("billMonth")}
            onChange={(event) =>
              change(() => {
                const next = event.target.value.replace("-", "");
                setMonth(next);
                if (/^\d{6}$/.test(next)) setDue(defaultPayDue(next));
              }, "billMonth")
            }
          />
          {fieldError("billMonth")}
        </div>
        <div className="bill-fc">
          <label htmlFor="bill-form-supply">
            공급가 <span className="field-required">*</span>
          </label>
          <div className="bill-row">
            <input
              id="bill-form-supply"
              data-testid="bill-form-supplyAmount"
              inputMode="numeric"
              className="bill-money"
              disabled={saving}
              value={supply}
              {...invalid("supplyAmount")}
              onChange={(event) =>
                change(() => setSupply(money(event.target.value)), "supplyAmount")
              }
            />
            <span className="bill-sub">원</span>
          </div>
          {fieldError("supplyAmount")}
          <small>
            월단위·파견은 그 달 투입 MM × 청구 단가 합계(식-02)로 채웁니다. 턴키는 직접 넣으세요.
          </small>
        </div>
        <div className="bill-fc">
          <label htmlFor="bill-form-rate">
            부가세율 <span className="field-required">*</span>
          </label>
          <div className="bill-row">
            <input
              id="bill-form-rate"
              data-testid="bill-form-vatRate"
              inputMode="decimal"
              className="bill-rate"
              disabled={saving}
              value={rate}
              {...invalid("vatRate")}
              onChange={(event) => change(() => setRate(event.target.value), "vatRate")}
            />
            <span className="bill-sub">%</span>
          </div>
          {fieldError("vatRate")}
          <small>
            기본 10%. 면세·영세율은 0. 부가세 금액은 저장할 때 공급가 × 세율(원 단위 버림)로 계산합니다.
          </small>
        </div>
        <div className="bill-fc">
          <label htmlFor="bill-form-date">
            청구일 <span className="field-required">*</span>
          </label>
          <input
            id="bill-form-date"
            type="date"
            data-testid="bill-form-billDate"
            disabled={saving}
            value={date}
            {...invalid("billDate")}
            onChange={(event) => change(() => setDate(event.target.value), "billDate")}
          />
          {fieldError("billDate")}
        </div>
        <div className="bill-fc">
          <label htmlFor="bill-form-due">
            입금 예정일 <span className="field-required">*</span>
          </label>
          <input
            id="bill-form-due"
            type="date"
            data-testid="bill-form-payDueDate"
            disabled={saving}
            value={due}
            {...invalid("payDueDate")}
            onChange={(event) => change(() => setDue(event.target.value), "payDueDate")}
          />
          {fieldError("payDueDate")}
          <small>기본은 청구월 다음 달 25일. 연체와 미수 알림이 이 날짜부터 셉니다.</small>
        </div>
        <div className="bill-fc">
          <label htmlFor="bill-form-tax">세금계산서 번호</label>
          <input
            id="bill-form-tax"
            data-testid="bill-form-taxInvoiceNo"
            maxLength={30}
            placeholder="예: 20261025-41000012-01"
            disabled={saving}
            value={invoice}
            {...invalid("taxInvoiceNo")}
            onChange={(event) => change(() => setInvoice(event.target.value), "taxInvoiceNo")}
          />
          {fieldError("taxInvoiceNo")}
          <small>
            계산서를 아직 안 끊었으면 비워 두세요. 비어 있으면 “번호 미입력”으로 표시되고 월 마감 점검에 잡힙니다.
          </small>
        </div>
        {optionError && (
          <p role="alert" className="field-error">
            {optionError}{" "}
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="bill-form-options-retry"
              onClick={() => {
                list.retry();
                picked.retry();
              }}
            >
              다시 시도
            </button>
          </p>
        )}
        <p role="alert" className="field-error" data-testid="bill-form-error">
          {error}
        </p>
        <div className="app-actions">
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
            disabled={saving}
          >
            {bill ? "저장" : "등록"}
          </button>
        </div>
      </form>
      {closing && (
        <InlineConfirmation
          title="저장하지 않은 내용이 있습니다"
          description="나가면 입력한 내용이 사라집니다."
          onCancel={() => setClosing(false)}
          onProceed={onClose}
          testId="bill-form-dirty"
        />
      )}
    </Dialog>
  );
}

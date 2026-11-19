import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { Banner } from "../../shared/list";
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
  const [warned, setWarned] = useState("");
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
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
  const change = (action: () => void) => {
    action();
    setDirty(true);
    setError("");
  };
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
    if (!projectId) return setError("프로젝트를 골라 주세요.");
    if (!/^\d{6}$/.test(month)) return setError("청구월을 입력해 주세요.");
    if (!value || Number(value) <= 0) return setError("공급가를 입력해 주세요.");
    if (vr === "" || !/^\d+(\.\d{1,2})?$/.test(vr) || Number(vr) > 100)
      return setError("부가세율을 0~100 사이로 입력해 주세요.");
    if (!due) return setError("입금 예정일을 입력해 주세요.");
    if (due < date) return setError("입금 예정일이 청구일보다 빠릅니다.");
    const supplyAmount = Number(value);
    const total = supplyAmount + Math.floor((supplyAmount * Number(vr)) / 100);
    if (bill && total < bill.receiptSumAmount)
      return setError(
        `합계가 이미 받은 금액(${billMoney(bill.receiptSumAmount)}원)보다 작습니다.`,
      );
    pending.current = true;
    setSaving(true);
    setError("");
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
      else setError(failureMessage(failure, "청구를 저장하지 못했습니다."));
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
            <span data-testid="bill-form-project-fixed">{projectName}</span>
          ) : (
            <select
              id="bill-form-project"
              data-testid="bill-form-project"
              value={projectId}
              disabled={saving}
              onChange={(event) =>
                change(() => setProject(event.target.value))
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
            onChange={(event) =>
              change(() => {
                const next = event.target.value.replace("-", "");
                setMonth(next);
                if (/^\d{6}$/.test(next)) setDue(defaultPayDue(next));
              })
            }
          />
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
              onChange={(event) =>
                change(() => setSupply(money(event.target.value)))
              }
            />
            <span className="bill-sub">원</span>
          </div>
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
              onChange={(event) => change(() => setRate(event.target.value))}
            />
            <span className="bill-sub">%</span>
          </div>
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
            onChange={(event) => change(() => setDate(event.target.value))}
          />
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
            onChange={(event) => change(() => setDue(event.target.value))}
          />
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
            onChange={(event) => change(() => setInvoice(event.target.value))}
          />
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

import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation, type ToastKind } from "../../shared/feedback/Dialog";
import { Badge, Banner, DetailPanel, KeyValues } from "../../shared/list";
import { formatDateTime } from "../../shared/time";
import { ReceiptCancelDialog } from "../receipts/ReceiptCancelDialog";
import { ReceiptHistoryPanel } from "../receipts/ReceiptHistoryPanel";
import type { Receipt } from "../receipts/receipts";
import {
  billMoney,
  billMonthLabel,
  billStatus,
  billStatusKind,
  failureMessage,
  getCommonBill,
  isConflict,
  postCommonBillCancel,
  type Bill,
  type BillDetail,
} from "./bills";

/**
 * UI-13-M3 청구 상세(시안 openBill): 오른쪽 상세 패널, 탭 [청구 정보] [입금 내역 (n)],
 * 머리 단추 [수정](마감 전·올해) [청구 취소](마감 전·정상 입금 없음) [입금 등록](미수·이번 달 마감 전).
 */
export function BillDetailDialog({
  billId,
  readOnly = false,
  initialTab = "info",
  onClose,
  onChanged,
  onEdit,
  onReceive,
}: {
  billId: string;
  readOnly?: boolean;
  initialTab?: "info" | "receipts";
  onClose: () => void;
  /** 저장·취소 뒤 목록을 다시 읽는다. message가 있으면 화면 알림으로 띄운다. */
  onChanged: (message?: string, kind?: ToastKind) => void;
  onEdit?: (bill: Bill) => void;
  onReceive?: (bill: Bill) => void;
}) {
  const [detail, setDetail] = useState<BillDetail | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [tab, setTab] = useState<string>(initialTab);
  const [cancelling, setCancelling] = useState(false);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState("");
  // 사유를 적은 채 닫으면 저장하지 않은 내용 확인(00-1 FR-0106)
  const [leaving, setLeaving] = useState(false);
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    void getCommonBill(billId, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setDetail(data);
      },
      (failure) => {
        if (!controller.signal.aborted)
          setError(failureMessage(failure, "청구 상세를 불러오지 못했습니다."));
      },
    );
    return () => controller.abort();
  }, [billId, revision]);
  const reload = () => setRevision((value) => value + 1);
  const closeCancel = () => {
    if (pending.current) return;
    if (reason.trim()) setLeaving(true);
    else setCancelling(false);
  };
  const conflict = (message: string) => {
    setCancelling(false);
    setLeaving(false);
    setReceipt(null);
    reload();
    onChanged(message, "warn");
  };
  async function cancel(bill: Bill) {
    if (pending.current) return;
    const value = reason.trim();
    if (!value) {
      setReasonError("취소 사유를 입력해 주세요.");
      // 오류 칸으로 간다(00-1 FR-0106)
      window.setTimeout(() => document.getElementById("bill-cancel-reason")?.focus(), 0);
      return;
    }
    pending.current = true;
    setSaving(true);
    setReasonError("");
    try {
      await postCommonBillCancel(bill.billId, value, bill.modifiedAt);
      setCancelling(false);
      onChanged(`${bill.billId} 청구를 취소했습니다. 기록은 남습니다.`);
      onClose();
    } catch (failure) {
      if (isConflict(failure)) conflict(failureMessage(failure, ""));
      else setReasonError(failureMessage(failure, "청구를 취소하지 못했습니다."));
    } finally {
      pending.current = false;
      setSaving(false);
    }
  }
  const bill = detail?.bill;
  const normal = detail?.receipts.filter((r) => !r.cancelled).length ?? 0;
  const actions =
    bill && !readOnly && !bill.cancelled ? (
      <>
        {bill.canEdit && onEdit && (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="bill-detail-edit"
            onClick={() => onEdit(bill)}
          >
            수정
          </button>
        )}
        {bill.canCancel && (
          <button
            type="button"
            className="ls-btn ls-btn-sm bill-danger-text"
            data-testid="bill-detail-cancel"
            onClick={() => {
              setReason("");
              setReasonError("");
              setLeaving(false);
              setCancelling(true);
            }}
          >
            청구 취소
          </button>
        )}
        {bill.canReceive && bill.unpaidAmount > 0 && onReceive && (
          <button
            type="button"
            className="ls-btn ls-btn-sm ls-btn-p"
            data-testid="bill-detail-receive"
            onClick={() => onReceive(bill)}
          >
            입금 등록
          </button>
        )}
      </>
    ) : null;
  const info = bill ? (
    <section data-testid="bill-detail-content" data-screen-id="UI-13-M3">
      {bill.cancelled ? (
        <Banner kind="info">
          취소된 청구입니다 · {formatDateTime(bill.cancelledAt)} {bill.cancelledBy} · 사유:{" "}
          {bill.cancelReason}. 매출·미수금·청구 누락 점검에서 빠지고 기록만 남습니다.
        </Banner>
      ) : (
        bill.canEdit &&
        normal > 0 && (
          <p className="bill-sub bill-note">
            입금이 있는 청구는 취소할 수 없습니다. 잘못 등록했다면 입금을 먼저 취소하세요.
          </p>
        )
      )}
      <KeyValues
        items={[
          ["프로젝트", bill.projectName],
          ["차수", `${bill.billOrder}차`],
          ["청구일", bill.billDate],
          ["공급가", <span className="tnum">{billMoney(bill.supplyAmount)} 원</span>],
          [
            "부가세",
            <span className="tnum">
              {billMoney(bill.vatAmount)} 원 <span className="bill-sub">({bill.vatRate}%)</span>
            </span>,
          ],
          [
            "합계",
            <strong className="tnum">{billMoney(bill.totalAmount)} 원</strong>,
          ],
          ["입금 예정일", bill.payDueDate],
          [
            "세금계산서",
            bill.taxInvoiceNo ? (
              bill.taxInvoiceNo
            ) : (
              <span className="bill-warn" data-testid="bill-detail-no-invoice">
                번호 미입력
              </span>
            ),
          ],
          [
            "상태",
            bill.cancelled ? (
              <span className="bill-sub">취소됨</span>
            ) : (
              <Badge kind={billStatusKind(bill.status)}>{billStatus(bill.status)}</Badge>
            ),
          ],
        ]}
      />
    </section>
  ) : null;
  return (
    <>
      <DetailPanel
        title={bill?.billId ?? billId}
        sub={bill?.projectName}
        actions={actions}
        testId="bill-detail"
        onClose={() => {
          if (!pending.current) onClose();
        }}
        activeTab={tab}
        onTab={setTab}
        tabs={
          detail && bill
            ? [
                { key: "info", label: "청구 정보", content: info },
                {
                  key: "receipts",
                  label: `입금 내역 (${normal})`,
                  content: (
                    <ReceiptHistoryPanel
                      receipts={detail.receipts}
                      readOnly={readOnly || bill.cancelled}
                      onCancel={setReceipt}
                    />
                  ),
                },
              ]
            : undefined
        }
      >
        {!detail && !error && <p role="status">청구 상세를 불러오는 중입니다.</p>}
        {error && (
          <p role="alert" className="field-error">
            {error}{" "}
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="bill-detail-retry"
              onClick={reload}
            >
              다시 시도
            </button>
          </p>
        )}
      </DetailPanel>
      {cancelling && bill && (
        <Dialog
          title="청구 취소"
          testId="bill-cancel-dialog"
          closable
          onClose={closeCancel}
        >
          <form
            className="billing-receipt-form"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void cancel(bill);
            }}
          >
            <p>
              {bill.projectName} · {bill.billId} ({bill.billOrder}차, {billMonthLabel(bill.billMonth)}, 합계{" "}
              {billMoney(bill.totalAmount)}원)을 취소합니다. 지우지 않고 「취소됨」으로 남으며, 매출·미수금·청구 누락 점검에서 빠집니다.
            </p>
            <label htmlFor="bill-cancel-reason">
              취소 사유 <span className="field-required">*</span>
            </label>
            <input
              id="bill-cancel-reason"
              data-testid="bill-cancel-reason"
              maxLength={500}
              placeholder="예: 같은 달 청구를 두 번 등록함"
              value={reason}
              disabled={saving}
              aria-invalid={!!reasonError}
              onChange={(event) => {
                setReason(event.target.value);
                setReasonError("");
              }}
            />
            <p role="alert" className="field-error" data-testid="bill-cancel-error">
              {reasonError}
            </p>
            <div className="billing-dialog-actions">
              <button
                type="button"
                className="ls-btn"
                data-testid="bill-cancel-back"
                disabled={saving}
                onClick={closeCancel}
              >
                닫기
              </button>
              <button
                type="submit"
                className="ls-btn app-danger"
                data-testid="bill-cancel-confirm"
                disabled={saving}
              >
                청구 취소
              </button>
            </div>
          </form>
          {leaving && (
            <InlineConfirmation
              title="저장하지 않은 내용이 있습니다"
              description="나가면 입력한 내용이 사라집니다."
              onCancel={() => setLeaving(false)}
              onProceed={() => {
                setLeaving(false);
                setCancelling(false);
              }}
              testId="bill-cancel-dirty"
            />
          )}
        </Dialog>
      )}
      {receipt && (
        <ReceiptCancelDialog
          receipt={receipt}
          onClose={() => setReceipt(null)}
          onConflict={conflict}
          onSaved={(message) => {
            setReceipt(null);
            setTab("receipts");
            reload();
            onChanged(message);
          }}
        />
      )}
    </>
  );
}

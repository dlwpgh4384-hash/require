import { formatDateTime } from "../../shared/time";
import type { Receipt } from "./receipts";
import "../commonops/billingLookup.css";

/**
 * UI-13 청구 상세 [입금 내역] 탭(시안 payRows): 입금 1번에 1줄(입금일·금액·상태), 줄마다 [취소].
 * 입금일이 속한 달이 마감된 입금은 [취소]를 숨긴다(SFR-113-11). 상세 한 번 읽은 내용을 그대로 쓴다.
 */
export function ReceiptHistoryPanel({
  receipts,
  readOnly = false,
  onCancel,
}: {
  receipts: Receipt[];
  readOnly?: boolean;
  onCancel: (receipt: Receipt) => void;
}) {
  if (!receipts.length)
    return (
      <p className="bill-sub" data-testid="receipt-history-empty">
        아직 입금이 없습니다.
      </p>
    );
  const locked = receipts.some((r) => !r.cancelled && !r.canCancel);
  return (
    <section data-testid="receipt-history-panel" data-screen-id="SCR-UI-13">
      <div className="ls-twrap">
        <table className="ls-t" aria-label="입금 내역">
          <thead>
            <tr>
              <th>입금일</th>
              <th className="num">금액</th>
              <th>상태</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {receipts.map((receipt) => (
              <tr
                key={receipt.receiptId}
                className={receipt.cancelled ? "receipt-cancelled" : undefined}
                data-testid={`receipt-row-${receipt.receiptId}`}
              >
                <td>{receipt.receiptDate}</td>
                <td className="num">
                  <span className={receipt.cancelled ? "receipt-strike" : undefined}>
                    {receipt.amount.toLocaleString("ko-KR")}
                  </span>
                </td>
                <td>
                  {receipt.cancelled ? (
                    <span
                      title={`${receipt.cancelledBy ?? ""} · ${formatDateTime(receipt.cancelledAt)}`}
                    >
                      취소됨 · {receipt.cancelReason}
                    </span>
                  ) : (
                    "정상"
                  )}
                </td>
                <td>
                  {!readOnly && !receipt.cancelled && receipt.canCancel && (
                    <button
                      type="button"
                      className="ls-btn ls-btn-sm"
                      data-testid={`receipt-cancel-${receipt.receiptId}`}
                      onClick={() => onCancel(receipt)}
                    >
                      취소
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="bill-sub bill-note">
        잘못 넣은 입금은 [취소] 후 다시 등록합니다. 지우지 않고 기록이 남습니다.
        {locked &&
          " 입금일이 마감한 달에 속한 입금은 취소할 수 없습니다(고칠 내용은 다음 달 전월 조정)."}
      </p>
    </section>
  );
}

import { useEffect, useMemo, useRef, useState } from "react";
import {
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { BillingReceiptDialog } from "./BillingReceiptDialog";
import {
  getCommonBillDunning,
  getBillingReceivable,
  postBillingDunningResend,
  type BillingDunningResult,
  type BillingDunningRow,
  type BillingReceivable,
} from "./billingLookups";
import "./billingLookup.css";

/** UI-13 / SCR-UI-13: mounted only on the current-year 미수 알림 tab by BillingPage. */
export function BillingDunningPanel({
  refreshKey = 0,
  onOpenBill,
  onChanged,
}: {
  refreshKey?: number;
  onOpenBill: (billId: string) => void;
  onChanged?: () => void;
}) {
  const [data, setData] = useState<BillingDunningResult | null>(null);
  const [failure, setFailure] = useState("");
  const [actionFailure, setActionFailure] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [pending, setPending] = useState<string | null>(null);
  const sending = useRef(false);
  const [receivable, setReceivable] = useState<BillingReceivable[]>([]);
  const [receipts, setReceipts] = useState<BillingReceivable[] | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const presentation = useListPresentation();
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setFailure("");
    setActionFailure("");
    setReceivable([]);
    setSelected([]);
    setPage(1);
    void getCommonBillDunning(controller.signal).then(
      async (result) => {
        if (controller.signal.aborted) return;
        setData(result);
        // Dunning's wire contract has no receipt permission flag: use the approved detail response.
        try {
          const details = await Promise.all(
            result.items.map((row) =>
              getBillingReceivable(row.billId, controller.signal),
            ),
          );
          if (!controller.signal.aborted)
            setReceivable(details.map((detail) => detail.bill));
        } catch (error) {
          if (!controller.signal.aborted)
            setActionFailure(
              error instanceof Error
                ? error.message
                : "입금 가능 여부를 불러오지 못했습니다.",
            );
        }
      },
      (error) => {
        if (!controller.signal.aborted)
          setFailure(
            error instanceof Error
              ? error.message
              : "미수 알림을 불러오지 못했습니다.",
          );
      },
    );
    return () => controller.abort();
  }, [refreshKey, revision]);
  async function resend(row: BillingDunningRow) {
    if (sending.current) return;
    sending.current = true;
    setPending(row.billId);
    setActionFailure("");
    setNotice("");
    try {
      await postBillingDunningResend(row.billId);
      setNotice(
        "다시 알림을 보냈습니다. 개발 환경의 메일은 로컬 보관함 배달입니다.",
      );
      setRevision((value) => value + 1);
      onChanged?.();
    } catch (error) {
      setActionFailure(
        error instanceof Error
          ? error.message
          : "다시 알림을 보내지 못했습니다.",
      );
    } finally {
      sending.current = false;
      setPending(null);
    }
  }
  const receive = (ids: string[]) => {
    const bills = receivable.filter(
      (bill) =>
        ids.includes(bill.billId) && bill.canReceive && bill.unpaidAmount > 0,
    );
    if (bills.length) setReceipts(bills);
  };
  const columns: ListColumn<BillingDunningRow>[] = [
    {
      key: "billId",
      label: "청구번호",
      required: true,
      render: (row) => (
        <button
          type="button"
          className="ls-link"
          data-testid={`billing-dunning-detail-${row.billId}`}
          onClick={() => onOpenBill(row.billId)}
        >
          {row.billId}
        </button>
      ),
    },
    { key: "projectName", label: "프로젝트" },
    {
      key: "unpaidAmount",
      label: "미수금",
      align: "right",
      render: (row) => row.unpaidAmount.toLocaleString("ko-KR"),
    },
    { key: "payDueDate", label: "입금 예정일" },
    {
      key: "daysOverdue",
      label: "지난 일수",
      render: (row) => `${row.daysOverdue}일`,
    },
    {
      key: "currentStep",
      label: "지금 단계",
      render: (row) =>
        row.currentStep == null ? "–" : `${row.currentStep}단계`,
    },
    {
      key: "history",
      label: "보낸 알림",
      sortable: false,
      render: (row) => (
        <div>
          {row.history.map((entry, index) => (
            <div key={`${entry.sentDate}-${index}`}>
              {entry.sentDate} {entry.step}단계{entry.resend ? "(다시)" : ""} →{" "}
              {entry.recipientDescription} · {entry.sendMethodCode}
            </div>
          ))}
        </div>
      ),
    },
    {
      key: "nextNotification",
      label: "다음 알림",
      render: (row) => row.nextNotification ?? "–",
    },
    {
      key: "actions",
      label: "처리",
      sortable: false,
      render: (row) => (
        <div className="billing-row-actions">
          {row.canResend ? (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid={`billing-dunning-resend-${row.billId}`}
              disabled={pending !== null}
              onClick={() => void resend(row)}
            >
              {pending === row.billId ? "전송 중…" : "다시 알림"}
            </button>
          ) : (
            row.resendMessage
          )}
          {receivable.some(
            (bill) =>
              bill.billId === row.billId &&
              bill.canReceive &&
              bill.unpaidAmount > 0,
          ) && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid={`billing-dunning-receive-${row.billId}`}
              onClick={() => receive([row.billId])}
            >
              입금 등록
            </button>
          )}
        </div>
      ),
    },
  ];
  const settings = useListColumns("UI-13-dunning", columns);
  const rows = useMemo(() => {
    const items = [...(data?.items ?? [])];
    if (sort)
      items.sort((a, b) => {
        const left = a[sort.key as keyof BillingDunningRow];
        const right = b[sort.key as keyof BillingDunningRow];
        const compared =
          typeof left === "number" && typeof right === "number"
            ? left - right
            : String(left ?? "").localeCompare(String(right ?? ""), "ko");
        return (
          (sort.dir === "asc" ? compared : -compared) ||
          a.billId.localeCompare(b.billId)
        );
      });
    return items;
  }, [data, sort]);
  return (
    <section
      className="billing-dunning-panel ls-root"
      role="tabpanel"
      aria-label="미수 알림"
      data-testid="billing-dunning-panel"
      data-screen-id="SCR-UI-13"
    >
      <div className="billing-dunning-cards">
        {(data?.cards ?? []).map((card) => (
          <article
            key={card.step}
            data-testid={`billing-dunning-card-${card.step}`}
          >
            <h3>{card.step}단계</h3>
            <p>
              {card.recipients.join(" + ") || "등록된 수신 역할이 없습니다"}
            </p>
          </article>
        ))}
      </div>
      <p>
        지난해 청구도 미수금이 있으면 표시합니다. 파트 미지정 시 경영진, 본사
        직접 영업은 본사 영업 책임자(없으면 경영진)에게 알립니다.
      </p>
      {notice && (
        <p role="status" data-testid="billing-dunning-success">
          {notice}
        </p>
      )}
      {failure && (
        <p role="alert" className="field-error">
          {failure}{" "}
          <button
            type="button"
            data-testid="billing-dunning-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            다시 시도
          </button>
        </p>
      )}
      {actionFailure && (
        <p role="alert" className="field-error">
          {actionFailure}{" "}
          <button
            type="button"
            data-testid="billing-dunning-action-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            다시 조회
          </button>
        </p>
      )}
      {!failure && data && !rows.length && <p>조회 결과가 없습니다</p>}
      <ListView
        screenId="UI-13-dunning"
        columns={columns}
        visibleColumns={settings.visible}
        hiddenColumns={settings.hidden}
        onToggleColumn={settings.toggle}
        onResetColumns={settings.reset}
        rows={
          data
            ? rows.slice(
                (page - 1) * presentation.pageSize,
                page * presentation.pageSize,
              )
            : failure
              ? []
              : null
        }
        rowKey={(row) => row.billId}
        total={rows.length}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={(size) => {
          presentation.setPageSize(size);
          setPage(1);
        }}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={sort}
        onSort={(value) => {
          setSort(value);
          setPage(1);
        }}
        selectable
        selected={selected}
        onSelect={setSelected}
        bulkActions={
          receivable.some(
            (bill) =>
              selected.includes(bill.billId) &&
              bill.canReceive &&
              bill.unpaidAmount > 0,
          )
            ? [
                {
                  key: "billing-receive",
                  label: "입금 등록",
                  onClick: () => receive(selected),
                },
              ]
            : []
        }
        testId="billing-dunning-list"
      />
      {receipts && (
        <BillingReceiptDialog
          bills={receipts}
          onClose={() => setReceipts(null)}
          onSaved={() => {
            setReceipts(null);
            setNotice("입금을 저장했습니다.");
            setRevision((value) => value + 1);
            onChanged?.();
          }}
        />
      )}
    </section>
  );
}

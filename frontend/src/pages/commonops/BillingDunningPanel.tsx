import { useEffect, useMemo, useRef, useState } from "react";
import type { ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  ListView,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { BillingReceiptDialog } from "./BillingReceiptDialog";
import {
  getCommonBillDunning,
  postBillingDunningResend,
  type BillingDunningResult,
  type BillingDunningRow,
  type BillingReceivable,
} from "./billingLookups";
import "./billingLookup.css";
import "../bills/bills.css";

/** 시안 DUN: 단계별 받는 사람(이름은 시안 글자 그대로). */
const RECEIVERS: Record<number, string> = {
  1: "영업 담당자(PM)",
  2: "영업 파트장",
  3: "경영진·재무",
};

/** 시안 dunBadge: 1단계 전(1~6일)은 「n일 지남」, 단계가 있으면 「n단계 · n일」. */
export function DunningBadge({
  step,
  days,
}: {
  step: number | null;
  days: number;
}) {
  if (days <= 0) return <span className="bill-sub">–</span>;
  if (step == null) return <Badge kind="neutral">{days}일 지남</Badge>;
  return (
    <Badge kind={step >= 2 ? "bad" : "warn"}>
      {step}단계 · {days}일
    </Badge>
  );
}

/** UI-13 미수 알림 탭(시안 dunView, SFR-113-07). 목록은 요청 한 번으로 읽는다. */
export function BillingDunningPanel({
  refreshKey = 0,
  soonCount = 0,
  onOpenBill,
  onChanged,
  onNotice,
}: {
  refreshKey?: number;
  /** 곧 예정일(7일 안) 건수: 화면이 청구 목록에서 센다. */
  soonCount?: number;
  onOpenBill: (billId: string) => void;
  onChanged?: () => void;
  onNotice?: (message: string, kind?: ToastKind) => void;
}) {
  const [data, setData] = useState<BillingDunningResult | null>(null);
  const [failure, setFailure] = useState("");
  const [revision, setRevision] = useState(0);
  const [pending, setPending] = useState<string | null>(null);
  const sending = useRef(false);
  const [receipts, setReceipts] = useState<BillingReceivable[] | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const presentation = useListPresentation();
  useEffect(() => {
    const controller = new AbortController();
    setFailure("");
    setSelected([]);
    void getCommonBillDunning(controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) {
          setData(result);
          setPage(1);
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
    try {
      const sent = await postBillingDunningResend(row.billId);
      onNotice?.(
        `${sent.history.recipientDescription}에게 미수 알림을 다시 보냈습니다. 보낸 알림에 「${sent.history.step}단계(다시)」로 남았습니다.`,
      );
      setRevision((value) => value + 1);
      onChanged?.();
    } catch (error) {
      onNotice?.(
        error instanceof Error ? error.message : "다시 알림을 보내지 못했습니다.",
        "warn",
      );
    } finally {
      sending.current = false;
      setPending(null);
    }
  }
  const receivable = (row: BillingDunningRow): BillingReceivable => ({
    billId: row.billId,
    projectName: row.projectName,
    unpaidAmount: row.unpaidAmount,
    canReceive: row.canReceive,
  });
  const receive = (ids: string[]) => {
    const bills = (data?.items ?? [])
      .filter((row) => ids.includes(row.billId) && row.canReceive && row.unpaidAmount > 0)
      .map(receivable);
    if (bills.length) setReceipts(bills);
  };
  const columns: ListColumn<BillingDunningRow>[] = [
    {
      key: "projectName",
      label: "프로젝트",
      required: true,
    },
    {
      key: "billId",
      label: "청구번호",
      required: true,
      render: (row) => (
        <button
          type="button"
          className="ls-link"
          data-testid={`billing-dunning-detail-${row.billId}`}
          onClick={(event) => {
            event.stopPropagation();
            onOpenBill(row.billId);
          }}
        >
          {row.billId}
        </button>
      ),
    },
    {
      key: "unpaidAmount",
      label: "미수금",
      align: "right",
      render: (row) => (
        <span className="bill-overdue">{row.unpaidAmount.toLocaleString("ko-KR")}</span>
      ),
    },
    { key: "payDueDate", label: "입금 예정일" },
    {
      key: "daysOverdue",
      label: "지난 일수",
      align: "right",
      render: (row) => `${row.daysOverdue}일`,
    },
    {
      key: "currentStep",
      label: "지금 단계",
      render: (row) => <DunningBadge step={row.currentStep} days={row.daysOverdue} />,
    },
    {
      key: "history",
      label: "보낸 알림",
      sortable: false,
      render: (row) =>
        row.history?.length ? (
          <div className="bill-sub bill-wrap">
            {(row.history ?? []).map((entry, index) => (
              <div key={`${entry.sentDate}-${index}`}>
                {entry.sentDate.slice(5)} {entry.step}단계{entry.resend ? "(다시)" : ""} →{" "}
                {entry.recipientDescription}
              </div>
            ))}
          </div>
        ) : (
          <span className="bill-sub">아직 없음</span>
        ),
    },
    {
      key: "nextNotification",
      label: "다음 알림",
      sortable: false,
      render: (row) => (
        <span className="bill-sub bill-wrap">{row.nextNotification ?? "마지막 단계"}</span>
      ),
    },
    {
      key: "actions",
      label: "",
      sortable: false,
      render: (row) => (
        <div className="billing-row-actions">
          {row.canResend ? (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid={`billing-dunning-resend-${row.billId}`}
              disabled={pending !== null}
              onClick={(event) => {
                event.stopPropagation();
                void resend(row);
              }}
            >
              다시 알림
            </button>
          ) : (
            row.resendMessage && <span className="bill-sub">{row.resendMessage}</span>
          )}
          {row.canReceive && row.unpaidAmount > 0 && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid={`billing-dunning-receive-${row.billId}`}
              onClick={(event) => {
                event.stopPropagation();
                receive([row.billId]);
              }}
            >
              입금 등록
            </button>
          )}
        </div>
      ),
    },
  ];
  const rows = useMemo(() => {
    const items = [...(data?.items ?? [])].sort((a, b) => b.daysOverdue - a.daysOverdue);
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
  const cards = [...(data?.cards ?? [])].sort((a, b) => a.step - b.step);
  return (
    <section
      className="billing-dunning-panel"
      role="tabpanel"
      aria-label="미수 알림"
      data-testid="billing-dunning-panel"
      data-screen-id="SCR-UI-13"
    >
      <div className="billing-dunning-cards">
        {cards.map((card) => {
          const count = rows.filter((row) => row.currentStep === card.step).length;
          return (
            <article
              key={card.step}
              className="bill-card"
              data-testid={`billing-dunning-card-${card.step}`}
            >
              <div className="bill-card-h">
                <h2>
                  {card.step}단계{" "}
                  <span className="bill-sub bill-normal">예정일 + {card.offsetDays}일</span>
                </h2>
                <Badge kind={count ? (card.step >= 2 ? "bad" : "warn") : "neutral"}>
                  {count}건
                </Badge>
              </div>
              <p className="bill-sub">
                받는 사람: <strong>{RECEIVERS[card.step] ?? card.recipients.join("·")}</strong>
                {card.step > 1 ? " (앞 단계 사람도 함께)" : ""}
              </p>
            </article>
          );
        })}
      </div>
      {failure && (
        <p role="alert" className="field-error">
          {failure}{" "}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="billing-dunning-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            다시 시도
          </button>
        </p>
      )}
      <section className="bill-card">
        <div className="bill-card-h">
          <h2>입금 예정일이 지난 청구 {rows.length}건</h2>
          <span className="bill-sub">곧 예정일(7일 안) {soonCount}건</span>
        </div>
        <ListView
          screenId="UI-13-dunning"
          columns={columns}
          visibleColumns={columns}
          hiddenColumns={[]}
          onToggleColumn={() => undefined}
          onResetColumns={() => undefined}
          columnSettings={false}
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
            rows.some(
              (row) => selected.includes(row.billId) && row.canReceive && row.unpaidAmount > 0,
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
        <p className="bill-sub bill-note">
          입금을 등록하면 알림이 멈춥니다. 알림은 메일과 시스템 알림으로 함께 보냅니다.
        </p>
      </section>
      {receipts && (
        <BillingReceiptDialog
          bills={receipts}
          onClose={() => setReceipts(null)}
          onSaved={() => {
            setReceipts(null);
            onNotice?.("입금을 등록했습니다.");
            setRevision((value) => value + 1);
            onChanged?.();
          }}
        />
      )}
    </section>
  );
}

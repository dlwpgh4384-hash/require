import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  ListFilter,
  ListFilters,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { daysBetween, koreanToday } from "../../shared/time";
import { BillChecksPanel } from "../billchecks/BillChecksPanel";
import { getCommonBillChecks } from "../billchecks/billChecks";
import {
  BillingDunningPanel,
  DunningBadge,
} from "../commonops/BillingDunningPanel";
import { BillingReceiptDialog } from "../commonops/BillingReceiptDialog";
import {
  getCommonBillDunning,
  getCommonBillOptions,
  type BillingOptions,
  type BillingReceivable,
} from "../commonops/billingLookups";
import { BillDetailDialog } from "./BillDetailDialog";
import { BillForm, type BillFormPreset } from "./BillForm";
import { useBillingTitleHost } from "./BillingWorkspace";
import {
  billMoney,
  billMonthLabel,
  billShortMoney,
  billStatus,
  billStatusKind,
  failureMessage,
  getCommonBills,
  getCommonBillsExcel,
  nextBillMonth,
  postCommonBillNextMonth,
  type Bill,
  type BillList,
} from "./bills";
import "../projects/projects.css";
import "./bills.css";

const svg = (d: string, size = 16) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);
const plusIcon = svg("M12 5v14M5 12h14");
const lockIcon = svg("M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4", 18);

type Filter = { status?: string; billMonth?: string; projectId?: string };
type Tab = "list" | "checks" | "dunning";

/** UI-13 매출 › 청구·입금(시안 SCR['UI-13']). */
export function BillingPage() {
  const today = koreanToday();
  const currentYear = Number(today.slice(0, 4));
  const currentMonth = today.slice(0, 7).replace("-", "");
  const host = useBillingTitleHost();
  const [year, setYear] = useState(currentYear);
  const [tab, setTab] = useState<Tab>("list");
  const [draft, setDraft] = useState<Filter>({});
  const [applied, setApplied] = useState<Filter>({});
  const [options, setOptions] = useState<BillingOptions | null>(null);
  const [data, setData] = useState<BillList | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [form, setForm] = useState<
    { kind: "new" } | { kind: "edit"; bill: Bill } | { kind: "copy"; preset: BillFormPreset } | null
  >(null);
  const [detail, setDetail] = useState<{ id: string; tab?: "info" | "receipts" } | null>(null);
  const [pending, setPending] = useState(false);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [receiving, setReceiving] = useState<BillingReceivable[] | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [counts, setCounts] = useState<{ checks: number | null; dunning: number | null }>({
    checks: null,
    dunning: null,
  });
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const busy = useRef(false);
  const presentation = useListPresentation();
  const current = year === currentYear;
  const readOnly = !current || !!data?.readOnly;
  const changed = () => setRevision((value) => value + 1);
  const toast = (message: string, kind: ToastKind = "ok") => {
    setNoticeKind(kind);
    setNotice(message);
  };
  // 그 해 청구 전체를 한 번 읽고 조건(상태·청구월·프로젝트)은 화면에서 거른다(청구월 선택지 = 청구가 있는 달, 시안).
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    void getCommonBills({ year }, controller.signal).then(
      (value) => {
        if (!controller.signal.aborted) setData(value);
      },
      (failure) => {
        if (!controller.signal.aborted) {
          setData(null);
          setError(failureMessage(failure, "청구 목록을 불러오지 못했습니다."));
        }
      },
    );
    return () => controller.abort();
  }, [year, revision]);
  useEffect(() => {
    const controller = new AbortController();
    void getCommonBillOptions({}, controller.signal).then(
      (value) => {
        if (!controller.signal.aborted) setOptions(value);
      },
      () => {
        if (!controller.signal.aborted) setOptions(null);
      },
    );
    return () => controller.abort();
  }, []);
  // 탭 이름의 건수(시안 billTabs): 청구 누락 점검(마감 기준 달의 맞지 않는 줄), 미수 알림(예정일 지난 청구)
  useEffect(() => {
    if (!current) return;
    const controller = new AbortController();
    void getCommonBillChecks(undefined, controller.signal).then(
      (value) => {
        if (!controller.signal.aborted)
          setCounts((c) => ({
            ...c,
            checks: (value?.items ?? []).filter((x) => x.judgement !== "맞음").length,
          }));
      },
      () => undefined,
    );
    void getCommonBillDunning(controller.signal).then(
      (value) => {
        if (!controller.signal.aborted)
          setCounts((c) => ({ ...c, dunning: (value?.items ?? []).length }));
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [current, revision]);
  const all = data?.items ?? [];
  /** 프로젝트마다 가장 최근 청구(취소 제외, 청구월·청구번호가 가장 늦은 것, 시안 qbLatest·서버와 같은 기준). */
  const latestIds = useMemo(() => {
    const latest = new Map<string, Bill>();
    for (const bill of all) {
      if (bill.cancelled) continue;
      const seen = latest.get(bill.projectId);
      if (
        !seen ||
        bill.billMonth > seen.billMonth ||
        (bill.billMonth === seen.billMonth && bill.billId > seen.billId)
      )
        latest.set(bill.projectId, bill);
    }
    return new Set([...latest.values()].map((bill) => bill.billId));
  }, [all]);
  const months = useMemo(
    () => [...new Set(all.filter((b) => !b.cancelled).map((b) => b.billMonth))].sort(),
    [all],
  );
  const filtered = useMemo(
    () =>
      all
        .filter((b) => !applied.status || b.status === applied.status)
        .filter((b) => !applied.billMonth || b.billMonth === applied.billMonth)
        .filter((b) => !applied.projectId || b.projectId === applied.projectId),
    [all, applied],
  );
  const rows = useMemo(() => {
    const result = [...filtered];
    if (sort)
      result.sort((a, b) => {
        const left = a[sort.key as keyof Bill],
          right = b[sort.key as keyof Bill];
        const compared =
          typeof left === "number" && typeof right === "number"
            ? left - right
            : String(left ?? "").localeCompare(String(right ?? ""), "ko");
        return (
          (sort.dir === "asc" ? compared : -compared) ||
          b.billId.localeCompare(a.billId)
        );
      });
    return result;
  }, [filtered, sort]);
  const active = rows.filter((b) => !b.cancelled);
  const sum = (key: "supplyAmount" | "vatAmount" | "totalAmount" | "receiptSumAmount" | "unpaidAmount") =>
    billMoney(active.reduce((s, b) => s + b[key], 0));
  const yearActive = all.filter((b) => !b.cancelled);
  const thisMonth = yearActive.filter((b) => b.billMonth === currentMonth);
  const soonCount = yearActive.filter((b) => {
    if (b.unpaidAmount <= 0) return false;
    const gap = daysBetween(today, b.payDueDate);
    return gap >= 0 && gap <= 7;
  }).length;
  async function next(row: Bill) {
    if (busy.current || !row.canNextMonth) return;
    busy.current = true;
    setPending(true);
    try {
      const saved = await postCommonBillNextMonth(row.billId);
      toast(
        `${row.projectName} ${billMonthLabel(saved.bill.billMonth)} 청구를 만들었습니다(공급가 ${billMoney(saved.bill.supplyAmount)}원). 세금계산서 번호는 나중에 넣으세요.`,
      );
      changed();
    } catch (failure) {
      toast(failureMessage(failure, "다음 달 청구를 만들지 못했습니다."), "warn");
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  // [고쳐서 청구](시안 billcopy): 다음 달 등록 창을 원청구 값으로 채워 연다. 다음 달이 마감이면 열지 않는다.
  async function copy(row: Bill) {
    if (busy.current || !row.canCopy) return;
    busy.current = true;
    setPending(true);
    const month = nextBillMonth(row.billMonth);
    try {
      const target = await getCommonBillOptions({ projectId: row.projectId, billMonth: month });
      if (target.monthClosed) {
        toast(`${billMonthLabel(month)}은 마감한 달이라 청구를 만들 수 없습니다.`, "warn");
        return;
      }
      const project = (target.projects ?? []).find((p) => p.projectId === row.projectId);
      setForm({
        kind: "copy",
        preset: {
          projectId: row.projectId,
          projectName: row.projectName,
          billMonth: month,
          // 턴키는 같은 금액, 월단위·파견은 다음 달 투입 기준(식-02)
          supplyAmount:
            project?.projectType === "TK" ? row.supplyAmount : target.estimatedSupplyAmount,
          vatRate: row.vatRate,
          sourceBillId: row.billId,
          title: "청구 등록 · 지난 청구 복사",
          message: `${row.billId}(${billMonthLabel(row.billMonth)}) 청구를 바탕으로 ${billMonthLabel(month)} 청구를 채웠습니다. 고칠 곳만 고쳐서 등록하세요.`,
        },
      });
    } catch (failure) {
      toast(failureMessage(failure, "청구 선택지를 불러오지 못했습니다."), "warn");
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  const download = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonBillsExcel({ year, ...applied });
      toast(`엑셀 파일을 만들었습니다${count ? ` (조회 결과 ${count}건)` : ""}`);
    } catch (failure) {
      toast(failureMessage(failure, "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요."), "warn");
    } finally {
      setDownloading(false);
    }
  };
  const quick = (row: Bill) => {
    // 시안 quickBillBtns: 지난 해이거나 프로젝트의 가장 최근 청구가 아니면 빈칸, 가장 최근인데 못 쓰면 「–」.
    if (readOnly || !latestIds.has(row.billId)) return null;
    if (!row.canCopy && !row.canNextMonth)
      return <span className="bill-sub">–</span>;
    return (
      <div className="bill-actions bill-nowrap">
        {row.canCopy && (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            title="같은 값으로 등록 창을 엽니다"
            data-testid={`bill-copy-${row.billId}`}
            disabled={pending}
            onClick={(event) => {
              event.stopPropagation();
              void copy(row);
            }}
          >
            고쳐서 청구
          </button>
        )}
        {row.canNextMonth && (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            title={`${billMonthLabel(nextBillMonth(row.billMonth))} 청구를 바로 만듭니다`}
            data-testid={`bill-next-${row.billId}`}
            disabled={pending}
            onClick={(event) => {
              event.stopPropagation();
              void next(row);
            }}
          >
            다음 달 청구
          </button>
        )}
      </div>
    );
  };
  const columns: ListColumn<Bill>[] = [
    { key: "billId", label: "청구번호" },
    { key: "projectName", label: "프로젝트", required: true },
    { key: "billOrder", label: "차수", align: "right", render: (b) => `${b.billOrder}차` },
    { key: "billDate", label: "청구일" },
    { key: "supplyAmount", label: "공급가", align: "right", render: (b) => billMoney(b.supplyAmount) },
    { key: "vatAmount", label: "부가세", align: "right", render: (b) => billMoney(b.vatAmount) },
    { key: "totalAmount", label: "합계", align: "right", render: (b) => billMoney(b.totalAmount) },
    {
      key: "receiptSumAmount",
      label: "입금액",
      align: "right",
      render: (b) => billMoney(b.receiptSumAmount),
    },
    {
      key: "unpaidAmount",
      label: "미수금",
      align: "right",
      render: (b) =>
        b.unpaidAmount && !b.cancelled ? (
          <span className={b.status === "LATE" ? "bill-overdue" : undefined}>
            {billMoney(b.unpaidAmount)}
          </span>
        ) : (
          "0"
        ),
    },
    { key: "payDueDate", label: "입금 예정일" },
    {
      key: "status",
      label: "상태",
      render: (b) =>
        b.cancelled ? (
          <span className="bill-sub" title={b.cancelReason ?? ""}>
            취소됨
          </span>
        ) : (
          <Badge kind={billStatusKind(b.status)}>{billStatus(b.status)}</Badge>
        ),
    },
    {
      key: "daysOverdue",
      label: "미수 알림",
      render: (b) =>
        b.cancelled || b.unpaidAmount <= 0 ? (
          <span className="bill-sub">–</span>
        ) : (
          <DunningBadge step={b.dunningStep} days={b.daysOverdue} />
        ),
    },
    { key: "actions", label: "빠른 청구", sortable: false, render: quick },
  ];
  const settings = useListColumns("UI-13-bills", columns);
  const titleActions = (content: ReactNode) => (host ? createPortal(content, host) : null);
  const select = (key: keyof Filter, label: string, items: [string, string][]) => (
    <ListFilter key={key} label={label} htmlFor={`bill-filter-${key}`}>
      <select
        className="ls-in"
        id={`bill-filter-${key}`}
        data-testid={`bill-filter-${key}`}
        value={draft[key] ?? ""}
        onChange={(event) => {
          const value = event.target.value;
          setDraft((f) => ({ ...f, [key]: value || undefined }));
        }}
      >
        <option value="">전체</option>
        {items.map(([value, name]) => (
          <option key={value} value={value}>
            {name}
          </option>
        ))}
      </select>
    </ListFilter>
  );
  const tabs: [Tab, ReactNode][] = [
    ["list", "청구 목록"],
    [
      "checks",
      <>
        청구 누락 점검
        {counts.checks ? (
          <>
            {" "}
            <span className="bill-nb" data-testid="bill-tab-checks-count">
              {counts.checks}
            </span>
          </>
        ) : null}
      </>,
    ],
    ["dunning", `미수 알림 (${counts.dunning ?? 0})`],
  ];
  return (
    <section
      className="screen-body bill-page"
      data-testid="billing-page"
      data-screen-id="UI-13"
      data-menu-id="M013"
      data-group-id="G005"
    >
      {!readOnly &&
        titleActions(
          <button
            type="button"
            className="ls-btn ls-btn-p"
            data-testid="bill-register"
            onClick={() => setForm({ kind: "new" })}
          >
            {plusIcon} 청구 등록
          </button>,
        )}
      <YearBar
        year={year}
        currentYear={currentYear}
        min={1}
        max={currentYear}
        caption="그 해에 청구한 건"
        testId="bill-year"
        onChange={(value) => {
          setYear(value);
          setTab("list");
          setDraft({});
          setApplied({});
          setPage(1);
          setSelected([]);
          setDetail(null);
        }}
      />
      {current && (
        <div className="bill-tabs" role="tablist" aria-label="청구 업무">
          {tabs.map(([key, label]) => (
            <button
              type="button"
              role="tab"
              key={key}
              className={`bill-tab${tab === key ? " on" : ""}`}
              aria-selected={tab === key}
              data-testid={`bill-tab-${key}`}
              onClick={() => setTab(key)}
            >
              {label}
            </button>
          ))}
        </div>
      )}
      {tab === "list" && (
        <section role="tabpanel" aria-label="청구 목록" className="bill-panel" data-testid="bill-list-panel">
          {year < currentYear && (
            <Banner kind="lock">
              {lockIcon}
              <span data-testid="bill-lock-banner">
                <strong>{year}년</strong>은 지난 해라 읽기만 할 수 있습니다.
              </span>
            </Banner>
          )}
          <div className="bill-kpis" aria-label="청구 요약">
            <div className="bill-kpi" data-testid="bill-kpi-year">
              <span className="l">{year}년 청구</span>
              <span className="v">{billShortMoney(data?.summary.yearBillTotal ?? 0)}</span>
              <span className="d">{data?.summary.yearBillCount ?? 0}건 · 부가세 포함</span>
            </div>
            <div className="bill-kpi" data-testid="bill-kpi-month">
              <span className="l">이번 달 청구</span>
              <span className="v">{billShortMoney(data?.summary.currentMonthBillTotal ?? 0)}</span>
              <span className="d">{current ? thisMonth.length : 0}건</span>
            </div>
            <div className="bill-kpi" data-testid="bill-kpi-unpaid">
              <span className="l">미수금</span>
              <span className="v">{billShortMoney(data?.summary.unpaidTotal ?? 0)}</span>
              <span className="d">{data?.summary.unpaidCount ?? 0}건</span>
            </div>
            <div className="bill-kpi" data-testid="bill-kpi-late">
              <span className="l">연체</span>
              <span className="v">{data?.summary.overdueCount ?? 0}건</span>
              <span className="d">입금 예정일 지남</span>
            </div>
          </div>
          <ListFilters
            testId="bill-filters"
            onSearch={() => {
              setApplied({ ...draft });
              setPage(1);
              setSelected([]);
            }}
            onReset={() => {
              setDraft({});
              setApplied({});
              setPage(1);
              setSelected([]);
            }}
            activeCount={Object.values(applied).filter(Boolean).length}
          >
            {select(
              "status",
              "상태",
              (options?.statusOptions ?? []).map((o) => [o.code, o.name]),
            )}
            {select(
              "billMonth",
              "청구월",
              months.map((m) => [m, billMonthLabel(m)]),
            )}
            {select(
              "projectId",
              "프로젝트",
              (options?.projects ?? []).map((p) => [p.projectId, p.projectName]),
            )}
          </ListFilters>
          <ListView
            screenId="UI-13"
            columns={columns}
            visibleColumns={settings.visible}
            hiddenColumns={settings.hidden}
            onToggleColumn={settings.toggle}
            onResetColumns={settings.reset}
            rows={
              data
                ? rows.slice((page - 1) * presentation.pageSize, page * presentation.pageSize)
                : error
                  ? []
                  : null
            }
            mobileRows={data ? rows : null}
            rowKey={(row) => row.billId}
            total={rows.length}
            page={page}
            onPage={setPage}
            pageSize={presentation.pageSize}
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
            error={error}
            onRetry={changed}
            onRowClick={(row) => setDetail({ id: row.billId })}
            // 엑셀 다운로드는 목록 도구줄 한 곳에만 둔다(제목줄에는 두지 않음).
            onDownload={() => void download()}
            downloading={downloading}
            selectable={!readOnly}
            selected={selected}
            onSelect={setSelected}
            bulkActions={
              !readOnly && rows.some((row) => selected.includes(row.billId) && row.canReceive)
                ? [
                    {
                      key: "bill-receive",
                      label: "입금 등록",
                      onClick: () =>
                        setReceiving(
                          rows.filter((row) => selected.includes(row.billId) && row.canReceive),
                        ),
                    },
                  ]
                : []
            }
            footer={{
              supplyAmount: sum("supplyAmount"),
              vatAmount: sum("vatAmount"),
              totalAmount: sum("totalAmount"),
              receiptSumAmount: sum("receiptSumAmount"),
              unpaidAmount: sum("unpaidAmount"),
            }}
            renderCard={(b) => (
              // 시안 휴대폰 카드: 프로젝트 + 상태 / 청구번호 · 청구일 + 합계 / 입금 예정 + 미수
              <>
                <div className="ls-r1">
                  <strong>{b.projectName}</strong>
                  {b.cancelled ? (
                    <span className="bill-sub">취소됨</span>
                  ) : (
                    <Badge kind={billStatusKind(b.status)}>{billStatus(b.status)}</Badge>
                  )}
                </div>
                <div className="ls-r2">
                  <span>
                    {b.billId} · {b.billDate}
                  </span>
                  <b>{billMoney(b.totalAmount)}</b>
                </div>
                <div className="ls-r2">
                  <span>입금 예정 {b.payDueDate}</span>
                  <span>미수 {billMoney(b.unpaidAmount)}</span>
                </div>
              </>
            )}
            testId="bill-list"
          />
        </section>
      )}
      {tab === "checks" && current && (
        <BillChecksPanel
          onOpenBill={(id) => setDetail({ id })}
          onChanged={changed}
          onNotice={toast}
        />
      )}
      {tab === "dunning" && current && (
        <BillingDunningPanel
          refreshKey={revision}
          soonCount={soonCount}
          onOpenBill={(id) => setDetail({ id })}
          onChanged={changed}
          onNotice={toast}
        />
      )}
      {form && (
        <BillForm
          bill={form.kind === "edit" ? form.bill : undefined}
          preset={form.kind === "copy" ? form.preset : undefined}
          onClose={() => setForm(null)}
          onConflict={(message) => {
            setForm(null);
            toast(message, "warn");
            changed();
          }}
          onSaved={(_saved, message) => {
            setForm(null);
            setPage(1);
            toast(message);
            changed();
          }}
        />
      )}
      {detail && (
        <BillDetailDialog
          key={`${detail.id}-${detail.tab ?? "info"}`}
          billId={detail.id}
          initialTab={detail.tab}
          readOnly={readOnly}
          onClose={() => setDetail(null)}
          onChanged={(message, kind) => {
            if (message) toast(message, kind);
            changed();
          }}
          onEdit={(bill) => {
            setDetail(null);
            setForm({ kind: "edit", bill });
          }}
          onReceive={(bill) => setReceiving([bill])}
        />
      )}
      {receiving && (
        <BillingReceiptDialog
          bills={receiving}
          onClose={() => setReceiving(null)}
          onSaved={() => {
            setReceiving(null);
            setDetail(null);
            setSelected([]);
            toast("입금을 등록했습니다.");
            changed();
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          testId="bill-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

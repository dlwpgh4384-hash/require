import { useEffect, useState } from "react";
import {
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { koreanToday } from "../../shared/time";
import { BillChecksPanel } from "../billchecks/BillChecksPanel";
import { BillingDunningPanel } from "../commonops/BillingDunningPanel";
import { BillingReceiptDialog } from "../commonops/BillingReceiptDialog";
import {
  getCommonBillOptions,
  type BillingOptions,
} from "../commonops/billingLookups";
import { BillDetailDialog } from "./BillDetailDialog";
import { BillForm } from "./BillForm";
import {
  billMoney,
  billStatus,
  getCommonBills,
  postCommonBillNextMonth,
  type Bill,
  type BillList,
} from "./bills";
import "../projects/projects.css";
import "./bills.css";

export function BillingPage() {
  const currentYear = Number(koreanToday().slice(0, 4));
  const [year, setYear] = useState(currentYear);
  const [tab, setTab] = useState("list");
  const [status, setStatus] = useState("");
  const [month, setMonth] = useState("");
  const [project, setProject] = useState("");
  const [options, setOptions] = useState<BillingOptions | null>(null);
  const [data, setData] = useState<BillList | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [form, setForm] = useState<Bill | "new" | null>(null);
  const [detailId, setDetail] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [page, setPage] = useState(1);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [receiving, setReceiving] = useState<Bill[] | null>(null);
  const presentation = useListPresentation();
  const readOnly = year !== currentYear || !!data?.readOnly;
  const changed = () => setRevision((value) => value + 1);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError("");
    setPage(1);
    setSelected([]);
    void getCommonBills(
      {
        year,
        status: status || undefined,
        billMonth: month.replace("-", "") || undefined,
        projectId: project || undefined,
      },
      controller.signal,
    ).then(
      (value) => {
        if (!controller.signal.aborted) setData(value);
      },
      (failure) => {
        if (!controller.signal.aborted)
          setError(
            failure instanceof Error
              ? failure.message
              : "청구 목록을 불러오지 못했습니다.",
          );
      },
    );
    return () => controller.abort();
  }, [year, status, month, project, revision]);
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
  }, [revision]);
  async function next(row: Bill) {
    if (pending) return;
    setPending(true);
    setNotice("");
    try {
      await postCommonBillNextMonth(row.billId);
      setNotice("다음 달 청구를 등록했습니다.");
      changed();
    } catch (failure) {
      setNotice(
        failure instanceof Error
          ? failure.message
          : "다음 달 청구를 등록하지 못했습니다.",
      );
    } finally {
      setPending(false);
    }
  }
  const columns: ListColumn<Bill>[] = [
    {
      key: "billId",
      label: "청구번호",
      required: true,
      render: (row) => (
        <button
          type="button"
          className="ls-link"
          data-testid={`bill-open-${row.billId}`}
          onClick={() => setDetail(row.billId)}
        >
          {row.billId}
        </button>
      ),
    },
    { key: "projectName", label: "프로젝트", required: true },
    { key: "billOrder", label: "차수" },
    { key: "billDate", label: "청구일" },
    ...(
      [
        ["supplyAmount", "공급가"],
        ["vatAmount", "부가세"],
        ["totalAmount", "합계"],
        ["receiptSumAmount", "입금액"],
        ["unpaidAmount", "미수금"],
      ] as const
    ).map(
      ([key, label]): ListColumn<Bill> => ({
        key,
        label,
        align: "right",
        render: (row) => (
          <span
            className={
              key === "unpaidAmount" && row.status === "LATE"
                ? "bill-overdue"
                : undefined
            }
          >
            {billMoney(row[key])}
          </span>
        ),
      }),
    ),
    { key: "payDueDate", label: "입금 예정일" },
    { key: "status", label: "상태", render: (row) => billStatus(row.status) },
    {
      key: "dunningStep",
      label: "미수 알림",
      render: (row) =>
        row.dunningStep == null
          ? "–"
          : `${row.dunningStep}단계 · ${row.daysOverdue}일`,
    },
    {
      key: "actions",
      label: "빠른 청구",
      sortable: false,
      render: (row) =>
        readOnly || (!row.canCopy && !row.canNextMonth) ? (
          "–"
        ) : (
          <div className="bill-actions">
            {row.canCopy && (
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid={`bill-copy-${row.billId}`}
                onClick={() => setForm(row)}
              >
                고쳐서 청구
              </button>
            )}
            {row.canNextMonth && (
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid={`bill-next-${row.billId}`}
                disabled={pending}
                onClick={() => void next(row)}
              >
                다음 달 청구
              </button>
            )}
          </div>
        ),
    },
  ];
  const settings = useListColumns("UI-13-bills", columns);
  const rows = [...(data?.items ?? [])];
  if (sort)
    rows.sort((a, b) => {
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
  return (
    <section
      className="project-page bill-page ls-root"
      data-testid="billing-page"
      data-screen-id="UI-13"
      data-menu-id="M013"
      data-group-id="G005"
    >
      <div className="project-actions">
        <h1>청구·입금</h1>
        <button
          type="button"
          className="ls-btn"
          data-testid="bill-year-prev"
          onClick={() => {
            setYear((value) => value - 1);
            setTab("list");
            setMonth("");
          }}
        >
          {year - 1}년
        </button>
        <label>
          조회 연도
          <input
            type="number"
            aria-label="조회 연도"
            data-testid="bill-year"
            min={1}
            max={9999}
            value={year}
            onChange={(event) => {
              const value = Number(event.target.value);
              if (value > 0 && value <= 9999) {
                setYear(value);
                setMonth("");
                setTab("list");
              }
            }}
          />
        </label>
        <button
          type="button"
          className="ls-btn"
          data-testid="bill-year-next"
          onClick={() => {
            setYear((value) => value + 1);
            setTab("list");
            setMonth("");
          }}
        >
          {year + 1}년
        </button>
        {!readOnly && (
          <button
            type="button"
            className="ls-btn ls-btn-p"
            data-testid="bill-register"
            onClick={() => setForm("new")}
          >
            청구 등록
          </button>
        )}
      </div>
      {readOnly && (
        <p>
          지난해 청구는 읽기 전용입니다. 현재 연도의 미수 알림에서 과거 미수
          입금을 처리할 수 있습니다.
        </p>
      )}
      <div className="bill-summary" aria-label="청구 요약">
        {[
          ["그 해 청구 건수", data?.summary.yearBillCount],
          ["그 해 청구", data?.summary.yearBillTotal],
          ["이번 달 청구", data?.summary.currentMonthBillTotal],
          ["미수 건수", data?.summary.unpaidCount],
          ["미수금", data?.summary.unpaidTotal],
          ["연체 건수", data?.summary.overdueCount],
        ].map(([label, value]) => (
          <article key={String(label)}>
            <span>{label}</span>
            <strong>
              {typeof value === "number" ? billMoney(value) : "–"}
            </strong>
          </article>
        ))}
      </div>
      <div role="tablist" aria-label="청구 업무" className="bill-actions">
        {[
          ["list", "청구 목록"],
          ...(!readOnly
            ? [
                ["checks", "청구 누락 점검"],
                ["dunning", "미수 알림"],
              ]
            : []),
        ].map(([key, label]) => (
          <button
            type="button"
            role="tab"
            className="ls-btn"
            aria-selected={tab === key}
            data-testid={`bill-tab-${key}`}
            key={key}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
      </div>
      {notice && (
        <p role="status" data-testid="bill-notice">
          {notice}
        </p>
      )}
      {tab === "list" && (
        <section
          role="tabpanel"
          aria-label="청구 목록"
          data-testid="bill-list-panel"
        >
          <div className="project-actions">
            <label>
              상태
              <select
                aria-label="상태"
                data-testid="bill-status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <option value="">전체</option>
                {(options?.statusOptions ?? []).map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              청구월
              <input
                type="month"
                aria-label="청구월"
                data-testid="bill-month"
                value={month}
                onChange={(event) => setMonth(event.target.value)}
              />
            </label>
            <label>
              프로젝트
              <select
                aria-label="프로젝트"
                data-testid="bill-project"
                value={project}
                onChange={(event) => setProject(event.target.value)}
              >
                <option value="">전체</option>
                {(options?.projects ?? []).map((item) => (
                  <option key={item.projectId} value={item.projectId}>
                    {item.projectName}
                  </option>
                ))}
              </select>
            </label>
            <button
              type="button"
              className="ls-btn"
              data-testid="bill-search"
              onClick={changed}
            >
              조회
            </button>
          </div>
          <ListView
            screenId="UI-13"
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
                : error
                  ? []
                  : null
            }
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
            selectable={!readOnly}
            selected={selected}
            onSelect={setSelected}
            bulkActions={
              !readOnly &&
              rows.some(
                (row) => selected.includes(row.billId) && row.canReceive,
              )
                ? [
                    {
                      key: "bill-receive",
                      label: "입금 등록",
                      onClick: () =>
                        setReceiving(
                          rows.filter(
                            (row) =>
                              selected.includes(row.billId) && row.canReceive,
                          ),
                        ),
                    },
                  ]
                : []
            }
            testId="bill-list"
          />
          {data && !rows.length && <p>조회 결과가 없습니다</p>}
          {data && (
            <p data-testid="bill-totals">
              합계: 공급가 {billMoney(data.totals.supplyAmount)} · 부가세{" "}
              {billMoney(data.totals.vatAmount)} · 합계{" "}
              {billMoney(data.totals.totalAmount)} · 입금액{" "}
              {billMoney(data.totals.receiptSumAmount)} · 미수금{" "}
              {billMoney(data.totals.unpaidAmount)}
            </p>
          )}
        </section>
      )}
      {tab === "checks" && !readOnly && (
        <BillChecksPanel onOpenBill={setDetail} onChanged={changed} />
      )}
      {tab === "dunning" && !readOnly && (
        <BillingDunningPanel
          refreshKey={revision}
          onOpenBill={setDetail}
          onChanged={changed}
        />
      )}
      {form && (
        <BillForm
          bill={form === "new" ? undefined : form}
          copy={form !== "new"}
          onClose={() => setForm(null)}
          onSaved={() => {
            setForm(null);
            setNotice("청구를 저장했습니다.");
            changed();
          }}
        />
      )}
      {detailId && (
        <BillDetailDialog
          billId={detailId}
          readOnly={readOnly}
          onClose={() => setDetail(null)}
          onChanged={changed}
        />
      )}
      {receiving && (
        <BillingReceiptDialog
          bills={receiving}
          onClose={() => setReceiving(null)}
          onSaved={() => {
            setReceiving(null);
            changed();
          }}
        />
      )}
    </section>
  );
}

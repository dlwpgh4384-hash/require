import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Dialog, ConfirmDialog, Toast } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useViewport } from "../../shared/layout/useViewport";
import { FormulaBox } from "../../shared/list/FormulaBox";
import { ListView } from "../../shared/list/ListView";
import {
  useListColumns,
  useListPresentation,
} from "../../shared/list/useListPresentation";
import type { ListColumn } from "../../shared/list/listTypes";
import {
  DEFAULT_MONEY_UNIT,
  MONEY_UNITS,
  formatMoney,
  type MoneyUnit,
} from "../../shared/list/moneyUnit";
import { PeriodSelector } from "../../shared/period/PeriodSelector";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { getCommonCodes } from "../codegroups/codeGroups";
import { ProjectRequestError } from "../projects/projects";
import { useFinanceItemTitleHost } from "./FinanceItemWorkspace";
import * as api from "./financeItems";
import type { UploadResult } from "../uploads/uploads";
import "./financeItems.css";

type Option = { code: string; codeName: string };
const headers = {
  classification: "구분",
  itemName: "세부 항목*",
  amount: "금액*",
  vatAmount: "부가세",
  cashDate: "입출금일",
  repeatYn: "매달 반복",
  memo: "비고",
};
const clone = <T,>(value: T): T => structuredClone(value);
const failure = (reason: unknown) =>
  reason instanceof Error
    ? reason.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

/** UI-10 / SCR-UI-10: 두 탭의 초안은 월 저장 한 번으로 확정한다. */
export function FinanceItemPage() {
  const period = useScreenPeriod("UI-10", { kind: "yearMonth" });
  const { mobile } = useViewport();
  const host = useFinanceItemTitleHost();
  const setDirty = useScreenDirty();
  const month =
    period.state.status === "ready"
      ? `${period.state.value.year}${String(period.state.value.month).padStart(2, "0")}`
      : "";
  const [draft, setDraft] = useState<api.FinanceMonth | null>(null);
  const [baseline, setBaseline] = useState<api.FinanceMonth | null>(null);
  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState("");
  const [codes, setCodes] = useState<Record<string, Option[]>>({});
  const [pending, setPending] = useState<{
    action: () => void;
    month: boolean;
  } | null>(null);
  const [previous, setPrevious] = useState<api.FinanceItem[] | null>(null);
  const [payment, setPayment] = useState<api.FinancePayment | null>(null);
  const [actualDate, setActualDate] = useState("");
  const [actualAmount, setActualAmount] = useState<api.Amount>("");
  const [upload, setUpload] = useState<"file" | "replace" | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [inspection, setInspection] = useState<UploadResult | null>(null);
  const columnState = useListColumns<number>(
    "UI-10",
    Object.entries(headers).map(([key, label]) => ({
      key,
      label,
      sortable: false,
    })),
  );
  const hidden = columnState.hidden;
  const presentation = useListPresentation();
  const [page, setPage] = useState(1);
  const [moneyUnit, setMoneyUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  const [revision, setRevision] = useState(0);
  const adjustmentTarget = useRef<string | null>(null);
  const inputs = useRef<
    Record<
      string,
      HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement | null
    >
  >({});
  const dirty =
    !!draft && !!baseline && JSON.stringify(draft) !== JSON.stringify(baseline);
  const firstDirty =
    !!draft &&
    !!baseline &&
    JSON.stringify([
      draft.items,
      draft.taxEstimate,
      draft.taxPayments,
      draft.adjustments,
    ]) !==
      JSON.stringify([
        baseline.items,
        baseline.taxEstimate,
        baseline.taxPayments,
        baseline.adjustments,
      ]);
  const secondDirty =
    !!draft &&
    !!baseline &&
    JSON.stringify([
      draft.cards,
      draft.cardPayment,
      draft.loans,
      draft.transfers,
    ]) !==
      JSON.stringify([
        baseline.cards,
        baseline.cardPayment,
        baseline.loans,
        baseline.transfers,
      ]);
  useEffect(() => {
    setDirty("UI-10", dirty);
    return () => setDirty("UI-10", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    const prevent = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  useEffect(() => {
    const controller = new AbortController();
    for (const group of ["FIN_CLS", "SGA_ITEM", "ADJ_ITEM", "IO", "XFER_CLS"])
      getCommonCodes(group, controller.signal)
        .then((response) => {
          if (!controller.signal.aborted)
            setCodes((old) => ({ ...old, [group]: response.items ?? [] }));
        })
        .catch(() => undefined);
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (!month || mobile) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setFields({});
    setPage(1);
    api
      .postCommonFinanceTaxYear(Number(month.slice(0, 4)), controller.signal)
      .then(() => api.getCommonFinanceItems(month, controller.signal))
      .then((value) => {
        if (controller.signal.aborted) return;
        const saved = clone(value);
        if (value.autoFillCount > 0) {
          saved.items = [];
          saved.autoFillCount = 0;
        }
        setBaseline(saved);
        if (adjustmentTarget.current) {
          value.adjustments.push({
            adjustmentId: null,
            modifiedAt: null,
            targetYearMonth: adjustmentTarget.current,
            adjustmentItem: "",
            amount: "",
            reason: "",
          });
          adjustmentTarget.current = null;
          setTab(0);
        }
        setDraft(value);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(failure(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [month, revision, mobile]);
  const change = (edit: (value: api.FinanceMonth) => void) =>
    setDraft((old) => {
      if (!old) return old;
      const next = clone(old);
      edit(next);
      return next;
    });
  const guard = (action: () => void, moving = false) =>
    dirty ? setPending({ action, month: moving }) : action();
  const go = (key: string) =>
    period.setValue({
      year: Number(key.slice(0, 4)),
      month: Number(key.slice(4)),
    });
  const report = (reason: unknown) => {
    setError(failure(reason));
    if (reason instanceof ProjectRequestError) {
      const errors = Object.fromEntries(
        reason.errors.map((item) => [item.field, item.message]),
      );
      setFields(errors);
      const first = reason.errors[0]?.field;
      if (
        first?.startsWith("cards") ||
        first?.startsWith("loans") ||
        first?.startsWith("transfers")
      )
        setTab(1);
      else if (first) setTab(0);
      if (first?.startsWith("items[")) {
        const index = Number(first.match(/^items\[(\d+)\]/)?.[1] ?? 0);
        setPage(Math.floor(index / presentation.pageSize) + 1);
        const key = first.split(".").pop();
        if (key && hidden.includes(key)) columnState.toggle(key);
      }
      if (first) requestAnimationFrame(() => inputs.current[first]?.focus());
    }
  };
  async function save() {
    if (!draft || busy) return;
    for (let n = 0; n < draft.items.length; n++) {
      const row = draft.items[n];
      const field = !row.itemName.trim()
        ? `items[${n}].itemName`
        : row.amount === ""
          ? `items[${n}].amount`
          : null;
      if (field) {
        setTab(0);
        setFields({
          [field]: field.endsWith("amount")
            ? "금액을 입력해 주세요"
            : "세부 항목을 입력해 주세요",
        });
        requestAnimationFrame(() => inputs.current[field]?.focus());
        return;
      }
    }
    setBusy(true);
    setError("");
    setFields({});
    try {
      const result = await api.putCommonFinanceItems(month, draft);
      setDraft(result);
      setBaseline(clone(result));
      setNotice("저장했습니다.");
    } catch (reason) {
      report(reason);
    } finally {
      setBusy(false);
    }
  }
  async function confirmPayment() {
    if (!payment || busy) return;
    setBusy(true);
    setError("");
    setFields({});
    try {
      const confirmed = await api.postCommonFinanceTaxPaymentConfirm(
        payment,
        actualDate,
        actualAmount,
      );
      const merge = (value: api.FinanceMonth | null) =>
        value && {
          ...value,
          taxPayments: value.taxPayments.map((row) =>
            row.paymentId === confirmed.paymentId ? confirmed : row,
          ),
        };
      setDraft(merge);
      setBaseline(merge);
      setPayment(null);
      setNotice("납부를 확인했습니다.");
    } catch (reason) {
      report(reason);
    } finally {
      setBusy(false);
    }
  }
  async function loadPrevious() {
    if (!draft) return;
    try {
      const result = await api.getCommonFinancePreviousItems(
        month,
        draft.items,
      );
      if (result.count) setPrevious(result.items);
      else setNotice("지난달에서 가져올 새 항목이 없습니다.");
    } catch (reason) {
      report(reason);
    }
  }
  async function applyUpload() {
    if (!file || !draft) return;
    setBusy(true);
    setError("");
    try {
      await api.postCommonFinanceItemsUpload(month, file, draft.modifiedAt);
      setUpload(null);
      setRevision((value) => value + 1);
      setNotice("엑셀 항목을 반영했습니다.");
    } catch (reason) {
      report(reason);
    } finally {
      setBusy(false);
    }
  }
  async function previewUpload() {
    if (!file || busy) return;
    setBusy(true);
    setError("");
    setFields({});
    try {
      const result = await api.checkCommonFinanceItemsUpload(month, file);
      setInspection(result);
      if (result.errorCount === 0) setUpload("replace");
    } catch (reason) {
      report(reason);
    } finally {
      setBusy(false);
    }
  }
  const locked = draft?.closed ?? true;
  const fieldError = (name: string) =>
    fields[name] && (
      <small role="alert" className="finance-error">
        {fields[name]}
      </small>
    );
  function input(
    name: string,
    label: string,
    value: api.Amount | null,
    update: (value: string) => void,
    type = "text",
    normalize = false,
    readonly = locked,
  ) {
    return (
      <label className="finance-field">
        <span className="sr-only">{label}</span>
        {readonly ? (
          <span>{value ?? "—"}</span>
        ) : (
          <input
            data-testid={`finance-${name.replace(/[^a-zA-Z0-9]+/g, "-").toLowerCase()}`}
            aria-label={label}
            aria-invalid={!!fields[name]}
            ref={(element) => {
              inputs.current[name] = element;
            }}
            type={type}
            value={value ?? ""}
            onChange={(event) => update(event.target.value)}
            onBlur={() => {
              if (normalize && value === "") update("0");
            }}
          />
        )}
        {fieldError(name)}
      </label>
    );
  }
  function select(
    name: string,
    label: string,
    value: string,
    options: Option[],
    update: (value: string) => void,
  ) {
    return (
      <label className="finance-field">
        <span className="sr-only">{label}</span>
        {locked ? (
          <span>
            {options.find((option) => option.code === value)?.codeName ?? value}
          </span>
        ) : (
          <select
            data-testid={`finance-${name.replace(/[^a-zA-Z0-9]+/g, "-").toLowerCase()}`}
            aria-label={label}
            aria-invalid={!!fields[name]}
            ref={(element) => {
              inputs.current[name] = element;
            }}
            value={value}
            onChange={(event) => update(event.target.value)}
          >
            <option value="">선택</option>
            {options.map((option) => (
              <option key={option.code} value={option.code}>
                {option.codeName}
              </option>
            ))}
          </select>
        )}
        {fieldError(name)}
      </label>
    );
  }
  const button = (
    id: string,
    text: string,
    action: () => void,
    disabled = false,
  ) => (
    <button
      type="button"
      className="ls-btn"
      data-testid={`finance-${id}`}
      disabled={busy || disabled}
      onClick={action}
    >
      {text}
    </button>
  );
  const titleActions = (
    <>
      {button("template", "양식 내려받기", () => {
        void api.getCommonFinanceItemsTemplate().catch(report);
      })}
      {!locked &&
        button("upload", "엑셀 업로드", () =>
          guard(() => {
            if (baseline) setDraft(clone(baseline));
            setUpload("file");
            setFile(null);
          }),
        )}
    </>
  );
  const totalCards =
    draft?.cards.reduce((sum, row) => sum + Number(row.amount || 0), 0) ?? 0;
  const sumItems = (code: string) =>
    draft?.items
      .filter((row) => row.classification === code)
      .reduce((sum, row) => sum + Number(row.amount || 0), 0) ?? 0;
  const closedTargets =
    period.state.status === "ready"
      ? [...period.state.basis.closedMonths]
          .sort()
          .reverse()
          .filter((key) => key < month)
          .slice(0, 12)
      : [];
  const itemColumns: ListColumn<number>[] = [
    ...Object.entries(headers).map(
      ([key, label]): ListColumn<number> => ({
        key,
        label,
        sortable: false,
        render: (index) => {
          const row = draft!.items[index];
          if (key === "classification")
            return select(
              `items[${index}].classification`,
              `구분 ${index + 1}`,
              row.classification,
              codes.FIN_CLS ?? [],
              (value) =>
                change((data) => {
                  data.items[index].classification = value;
                }),
            );
          if (key === "itemName")
            return (
              <label>
                <span className="sr-only">세부 항목 {index + 1}</span>
                {locked ? (
                  row.itemName
                ) : (
                  <input
                    aria-label={`세부 항목 ${index + 1}`}
                    data-testid={`finance-item-name-${index}`}
                    list="finance-sga-options"
                    value={row.itemName}
                    ref={(element) => {
                      inputs.current[`items[${index}].itemName`] = element;
                    }}
                    onChange={(event) =>
                      change((data) => {
                        data.items[index].itemName = event.target.value;
                        data.items[index].itemCode =
                          codes.SGA_ITEM?.find(
                            (option) => option.codeName === event.target.value,
                          )?.code ?? null;
                      })
                    }
                  />
                )}
                {fieldError(`items[${index}].itemName`)}
              </label>
            );
          if (key === "repeatYn")
            return select(
              `items[${index}].repeatYn`,
              `매달 반복 ${index + 1}`,
              row.repeatYn,
              [
                { code: "Y", codeName: "반복" },
                { code: "N", codeName: "안 함" },
              ],
              (value) =>
                change((data) => {
                  data.items[index].repeatYn = value;
                }),
            );
          if (
            key === "amount" ||
            key === "vatAmount" ||
            key === "cashDate" ||
            key === "memo"
          )
            return input(
              `items[${index}].${key}`,
              `${headers[key]} ${index + 1}`,
              row[key],
              (value) =>
                change((data) => {
                  if (key === "cashDate")
                    data.items[index].cashDate = value || null;
                  else data.items[index][key] = value;
                }),
              key === "cashDate" ? "date" : key === "memo" ? "text" : "number",
              key === "vatAmount",
            );
          return null;
        },
      }),
    ),
    {
      key: "actions",
      label: "출처 / 삭제",
      required: true,
      sortable: false,
      render: (index) => {
        const row = draft!.items[index];
        return (
          <>
            {row.autoFillYn === "Y" ? "자동" : row.uploadId ? "엑셀" : "직접"}
            {!row.itemId && " · 저장 전"}
            {!locked &&
              button(`item-delete-${index}`, "삭제", () =>
                change((data) => {
                  data.items.splice(index, 1);
                }),
              )}
          </>
        );
      },
    },
  ];
  if (mobile)
    return (
      <section className="screen-body" data-testid="finance-items-page">
        PC 전용 화면입니다. PC에서 이용해 주세요.
      </section>
    );
  return (
    <section
      className="screen-body finance-items"
      data-screen-id="UI-10"
      data-testid="finance-items-page"
    >
      {host ? (
        createPortal(titleActions, host)
      ) : (
        <div className="finance-actions">{titleActions}</div>
      )}
      {pending && (
        <ConfirmDialog
          title={
            pending.month
              ? "저장 안 한 내용이 있습니다"
              : "저장하지 않은 내용이 있습니다"
          }
          description="나가면 입력한 내용이 사라집니다."
          onCancel={() => setPending(null)}
          onProceed={() => {
            const action = pending.action;
            setPending(null);
            action();
          }}
          proceedLabel="나가기"
        />
      )}
      {period.state.status === "ready" && (
        <PeriodSelector
          kind="yearMonth"
          variant="monthBox"
          basis={period.state.basis}
          value={period.state.value}
          disabled={busy}
          onChange={(value) => guard(() => period.setValue(value), true)}
        />
      )}
      <nav aria-label="월별 입력 현황" className="finance-months">
        {draft?.months.map((value) => (
          <button
            type="button"
            data-testid={`finance-month-${value.yearMonth}`}
            key={value.yearMonth}
            disabled={value.future || busy}
            aria-current={value.yearMonth === month ? "date" : undefined}
            onClick={() => guard(() => go(value.yearMonth), true)}
          >
            {Number(value.yearMonth.slice(4))}월{" "}
            <small>
              {value.closed ? "마감" : "작업중"} · {value.itemCount}건
            </small>
          </button>
        ))}
      </nav>
      {draft?.closed && (
        <div className="finance-banner">
          마감된 달입니다. 금액은 변경할 수 없습니다.{" "}
          {button(
            "adjust-closed",
            "전월 조정 입력",
            () => {
              if (draft.firstOpenYearMonth) {
                adjustmentTarget.current = month;
                setTab(0);
                go(draft.firstOpenYearMonth);
              }
            },
            !draft.firstOpenYearMonth,
          )}
        </div>
      )}
      {!!draft?.autoFillCount && (
        <div className="finance-banner">
          지난달 반복 항목 {draft.autoFillCount}개를 자동으로 채웠습니다.
          저장해야 반영됩니다.
        </div>
      )}
      <div role="tablist" aria-label="재무 항목 탭" className="finance-tabs">
        {["판관비·법인세·조정", "카드·이체·대여금"].map((label, index) => (
          <button
            role="tab"
            type="button"
            data-testid={`finance-tab-${index}`}
            id={`finance-tab-${index}`}
            aria-controls={`finance-panel-${index}`}
            aria-selected={tab === index}
            key={label}
            onClick={() => setTab(index)}
          >
            {label}
            {(index === 0 ? firstDirty : secondDirty) && " · 저장 전"}
          </button>
        ))}
      </div>
      {loading && <p role="status">불러오는 중입니다.</p>}
      {(error || period.state.status === "error") && (
        <div role="alert">
          {error ||
            (period.state.status === "error" ? period.state.message : "")}
          {button("retry", "다시 시도", () =>
            setRevision((value) => value + 1),
          )}
        </div>
      )}
      <div
        role="tabpanel"
        id="finance-panel-0"
        data-testid="finance-panel-0"
        aria-labelledby="finance-tab-0"
        hidden={tab !== 0}
      >
        <p className="finance-banner">
          손익에 반영 · 판관비/영업외비용은 부가세를 포함해 자금 제경비에도
          반영합니다.
        </p>
        <label>
          합계 표시 단위{" "}
          <select
            aria-label="합계 표시 단위"
            data-testid="finance-money-unit"
            value={moneyUnit}
            onChange={(event) => setMoneyUnit(event.target.value as MoneyUnit)}
          >
            {MONEY_UNITS.map((unit) => (
              <option key={unit}>{unit}</option>
            ))}
          </select>
        </label>
        <p>입력 금액은 원 단위입니다.</p>
        <dl className="finance-totals">
          {[
            ["판관비", sumItems("SGA")],
            ["영업외수익", sumItems("NOPR_IN")],
            ["영업외비용", sumItems("NOPR_OUT")],
            ["법인세(월할)", draft?.taxEstimate.monthlyTaxAmount ?? "—"],
          ].map(([label, amount]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>
                {amount === "—"
                  ? amount
                  : formatMoney(Number(amount), moneyUnit)}
              </dd>
            </div>
          ))}
        </dl>
        <div className="finance-actions">
          {!locked && (
            <>
              {button("previous", "지난달 항목 불러오기", () => {
                void loadPrevious();
              })}
              {button("item-add", "항목 추가", () =>
                change((value) => {
                  value.items.push({
                    itemId: null,
                    modifiedAt: null,
                    classification: codes.FIN_CLS?.[0]?.code ?? "",
                    itemCode: null,
                    itemName: "",
                    amount: "",
                    vatAmount: 0,
                    cashDate: null,
                    repeatYn: "N",
                    autoFillYn: "N",
                    uploadId: null,
                    memo: "",
                  });
                }),
              )}
            </>
          )}
          {button(
            "excel",
            "엑셀 다운로드",
            () => {
              void api
                .getCommonFinanceItemsExcel(
                  month,
                  Object.keys(headers).filter((key) => !hidden.includes(key)),
                )
                .catch(report);
            },
            !draft,
          )}
          <details>
            <summary>열 설정</summary>
            {Object.entries(headers).map(([key, label]) => (
              <label key={key}>
                <input
                  type="checkbox"
                  data-testid={`finance-column-${key.toLowerCase()}`}
                  checked={!hidden.includes(key)}
                  onChange={() => columnState.toggle(key)}
                />
                {label}
              </label>
            ))}
          </details>
        </div>
        <ListView
          screenId="UI-10"
          testId="finance-items-list"
          columns={itemColumns}
          visibleColumns={itemColumns.filter(
            (column) => !hidden.includes(column.key),
          )}
          hiddenColumns={hidden}
          onToggleColumn={columnState.toggle}
          onResetColumns={columnState.reset}
          columnSettings={false}
          rows={
            draft
              ? draft.items
                  .map((_, index) => index)
                  .slice(
                    (page - 1) * presentation.pageSize,
                    page * presentation.pageSize,
                  )
              : []
          }
          rowKey={(index) => draft!.items[index].itemId ?? `new-${index}`}
          total={draft?.items.length ?? 0}
          page={page}
          pageSize={presentation.pageSize}
          onPage={setPage}
          onPageSize={(size) => {
            presentation.setPageSize(size);
            setPage(1);
          }}
          dense={presentation.dense}
          onDense={presentation.setDense}
          sort={null}
          onSort={() => {}}
          footer={{
            amount: button(
              "tax-focus",
              `법인세(월할) ${draft?.taxEstimate.monthlyTaxAmount.toLocaleString("ko-KR") ?? "—"}`,
              () => inputs.current["taxEstimate.estimatedTaxAmount"]?.focus(),
            ),
          }}
          footerLabel="법인세 월할"
        />
        <datalist id="finance-sga-options">
          {(codes.SGA_ITEM ?? []).map((option) => (
            <option key={option.code} value={option.codeName} />
          ))}
        </datalist>
        {!draft?.items.length && <p>입력한 항목이 없습니다.</p>}
        <h3>법인세</h3>
        {input(
          "taxEstimate.estimatedTaxAmount",
          "연간 예상 법인세",
          draft?.taxEstimate.estimatedTaxAmount ?? "",
          (value) =>
            change((data) => {
              data.taxEstimate.estimatedTaxAmount = value;
            }),
          "number",
        )}
        {draft?.taxEstimate.negativeTaxWarning && (
          <p className="finance-error">금액이 맞는지 확인하세요</p>
        )}
        <table className="ls-t">
          <thead>
            <tr>
              <th>납부일</th>
              <th>구분</th>
              <th>금액</th>
              <th>상태</th>
              <th>납부 확인</th>
            </tr>
          </thead>
          <tbody>
            {draft?.taxPayments.map((row, index) => (
              <tr key={row.paymentId} data-testid={`finance-payment-${index}`}>
                <td>
                  {input(
                    `taxPayments[${index}].scheduledDate`,
                    `납부예정일 ${row.paymentId}`,
                    row.paidYn === "Y"
                      ? row.actualPaymentDate
                      : row.scheduledDate,
                    (value) =>
                      change((data) => {
                        data.taxPayments[index].scheduledDate = value;
                      }),
                    "date",
                    false,
                    locked || row.paidYn === "Y",
                  )}
                </td>
                <td>
                  {row.taxYear} {row.paymentType}
                </td>
                <td>
                  {input(
                    `taxPayments[${index}].paymentAmount`,
                    `납부예정액 ${row.paymentId}`,
                    row.paymentAmount ?? row.displayAmount,
                    (value) =>
                      change((data) => {
                        data.taxPayments[index].paymentAmount = value;
                      }),
                    "number",
                    false,
                    locked || row.paidYn === "Y",
                  )}
                </td>
                <td
                  className={row.status === "확인 필요" ? "finance-error" : ""}
                >
                  {row.status}
                </td>
                <td>
                  {row.paidYn !== "Y" &&
                    button(`payment-confirm-${index}`, "납부 확인", () => {
                      setPayment(row);
                      setActualDate(row.scheduledDate);
                      setActualAmount(row.paymentAmount ?? row.displayAmount);
                      setFields({});
                    })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <h3>전월 조정</h3>
        {!locked &&
          button("adjustment-add", "전월 조정 추가", () =>
            change((data) => {
              data.adjustments.push({
                adjustmentId: null,
                modifiedAt: null,
                targetYearMonth: closedTargets[0] ?? "",
                adjustmentItem: "",
                amount: "",
                reason: "",
              });
            }),
          )}
        <table className="ls-t">
          <thead>
            <tr>
              <th>대상 달*</th>
              <th>조정 항목*</th>
              <th>금액* (이익 기준 ±)</th>
              <th>사유* (300자)</th>
              <th>삭제</th>
            </tr>
          </thead>
          <tbody>
            {draft?.adjustments.map((row, index) => (
              <tr
                key={row.adjustmentId ?? index}
                data-testid={`finance-adjustment-${index}`}
              >
                <td>
                  {select(
                    `adjustments[${index}].targetYearMonth`,
                    `대상 달 ${index + 1}`,
                    row.targetYearMonth,
                    closedTargets.map((value) => ({
                      code: value,
                      codeName: value,
                    })),
                    (value) =>
                      change((data) => {
                        data.adjustments[index].targetYearMonth = value;
                      }),
                  )}
                </td>
                <td>
                  {select(
                    `adjustments[${index}].adjustmentItem`,
                    `조정 항목 ${index + 1}`,
                    row.adjustmentItem,
                    codes.ADJ_ITEM ?? [],
                    (value) =>
                      change((data) => {
                        data.adjustments[index].adjustmentItem = value;
                      }),
                  )}
                </td>
                <td>
                  {input(
                    `adjustments[${index}].amount`,
                    `조정 금액 ${index + 1}`,
                    row.amount,
                    (value) =>
                      change((data) => {
                        data.adjustments[index].amount = value;
                      }),
                    "number",
                  )}
                </td>
                <td>
                  {input(
                    `adjustments[${index}].reason`,
                    `사유 ${index + 1}`,
                    row.reason,
                    (value) =>
                      change((data) => {
                        data.adjustments[index].reason = value;
                      }),
                  )}
                </td>
                <td>
                  {!locked &&
                    button(`adjustment-delete-${index}`, "삭제", () =>
                      change((data) => {
                        data.adjustments.splice(index, 1);
                      }),
                    )}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={5}>
                합계{" "}
                {draft?.adjustments
                  .reduce((sum, row) => sum + Number(row.amount || 0), 0)
                  .toLocaleString("ko-KR")}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
      <div
        role="tabpanel"
        id="finance-panel-1"
        data-testid="finance-panel-1"
        aria-labelledby="finance-tab-1"
        hidden={tab !== 1}
      >
        <p className="finance-banner">
          자금수지에만 반영합니다. 손익과 파트 정산카드에는 반영하지 않습니다.
        </p>
        <h3>
          회사 카드 ·{" "}
          {draft?.cards.filter((row) => row.useYn === "Y").length ?? 0}장 사용
          중
        </h3>
        <table className="ls-t">
          <thead>
            <tr>
              <th>카드 이름*</th>
              <th>끝 4자리*</th>
              <th>그 달 결제액</th>
              <th>상태</th>
              <th>삭제 / 되살리기</th>
            </tr>
          </thead>
          <tbody>
            {draft?.cards.map((row, index) => (
              <tr
                key={row.cardId ?? index}
                data-testid={`finance-card-${index}`}
              >
                <td>
                  {input(
                    `cards[${index}].cardName`,
                    `카드 이름 ${index + 1}`,
                    row.cardName,
                    (value) =>
                      change((data) => {
                        data.cards[index].cardName = value;
                      }),
                  )}
                </td>
                <td>
                  {input(
                    `cards[${index}].lastFourDigits`,
                    `끝 4자리 ${index + 1}`,
                    row.lastFourDigits,
                    (value) =>
                      change((data) => {
                        data.cards[index].lastFourDigits = value;
                      }),
                  )}
                </td>
                <td>
                  {input(
                    `cards[${index}].amount`,
                    `카드 결제액 ${index + 1}`,
                    row.amount,
                    (value) =>
                      change((data) => {
                        data.cards[index].amount = value;
                      }),
                    "number",
                    true,
                  )}
                </td>
                <td>
                  {row.useYn === "Y" ? "사용" : "삭제됨"}
                  {secondDirty && " · 저장 전"}
                </td>
                <td>
                  {!locked &&
                    button(
                      `card-toggle-${index}`,
                      row.useYn === "Y" ? "삭제" : "되살리기",
                      () =>
                        change((data) => {
                          data.cards[index].useYn =
                            row.useYn === "Y" ? "N" : "Y";
                        }),
                    )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!locked &&
          button("card-add", "카드 추가", () =>
            change((data) => {
              data.cards.push({
                cardId: null,
                modifiedAt: null,
                monthlyModifiedAt: null,
                cardName: "",
                lastFourDigits: "",
                useYn: "Y",
                amount: 0,
              });
            }),
          )}
        <p>카드별 합계 {totalCards.toLocaleString("ko-KR")}</p>
        {input(
          "cardPayment.paymentAmount",
          "자금수지 반영 합계",
          draft?.cardPayment.manualOverride
            ? draft.cardPayment.paymentAmount
            : totalCards,
          (value) =>
            change((data) => {
              data.cardPayment.paymentAmount = value;
              data.cardPayment.manualOverride = true;
            }),
          "number",
          true,
        )}
        {draft?.cardPayment.manualOverride && (
          <p>
            직접 고침 · 차액{" "}
            {(
              Number(draft.cardPayment.paymentAmount || 0) - totalCards
            ).toLocaleString("ko-KR")}
          </p>
        )}
        {!locked &&
          button("card-auto", "자동 합계로", () =>
            change((data) => {
              data.cardPayment.manualOverride = false;
              data.cardPayment.paymentAmount = totalCards;
            }),
          )}
        <h3>계좌이체 지출</h3>
        {!locked &&
          button("transfer-add", "계좌이체 추가", () =>
            change((data) => {
              data.transfers.push({
                transferId: null,
                modifiedAt: null,
                date: "",
                classification:
                  codes.XFER_CLS?.find((option) =>
                    option.codeName.includes("계좌이체"),
                  )?.code ??
                  codes.XFER_CLS?.[0]?.code ??
                  "",
                description: "",
                payee: "",
                amount: "",
                memo: "",
              });
            }),
          )}
        <table className="ls-t">
          <thead>
            <tr>
              <th>날짜*</th>
              <th>구분*</th>
              <th>내용*</th>
              <th>받는 곳</th>
              <th>금액*</th>
              <th>메모</th>
              <th>삭제</th>
            </tr>
          </thead>
          <tbody>
            {draft?.transfers.map((row, index) => (
              <tr
                key={row.transferId ?? index}
                data-testid={`finance-transfer-${index}`}
              >
                {(
                  [
                    "date",
                    "classification",
                    "description",
                    "payee",
                    "amount",
                    "memo",
                  ] as const
                ).map((key) => (
                  <td key={key}>
                    {key === "classification"
                      ? select(
                          `transfers[${index}].${key}`,
                          `이체 구분 ${index + 1}`,
                          row[key],
                          codes.XFER_CLS ?? [],
                          (value) =>
                            change((data) => {
                              data.transfers[index][key] = value;
                            }),
                        )
                      : input(
                          `transfers[${index}].${key}`,
                          `이체 ${key} ${index + 1}`,
                          row[key],
                          (value) =>
                            change((data) => {
                              data.transfers[index][key] = value;
                            }),
                          key === "date"
                            ? "date"
                            : key === "amount"
                              ? "number"
                              : "text",
                        )}
                  </td>
                ))}
                <td>
                  {!locked &&
                    button(`transfer-delete-${index}`, "삭제", () =>
                      change((data) => {
                        data.transfers.splice(index, 1);
                      }),
                    )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <h3>대여금 입출금</h3>
        {!locked &&
          button("loan-add", "대여금 추가", () =>
            change((data) => {
              data.loans.push({
                loanId: null,
                modifiedAt: null,
                date: "",
                direction: codes.IO?.[0]?.code ?? "",
                amount: "",
                counterparty: "",
                memo: "",
              });
            }),
          )}
        <table className="ls-t">
          <thead>
            <tr>
              <th>날짜*</th>
              <th>받음 / 갚음*</th>
              <th>금액*</th>
              <th>상대</th>
              <th>메모</th>
              <th>삭제</th>
            </tr>
          </thead>
          <tbody>
            {draft?.loans.map((row, index) => (
              <tr
                key={row.loanId ?? index}
                data-testid={`finance-loan-${index}`}
              >
                {(
                  [
                    "date",
                    "direction",
                    "amount",
                    "counterparty",
                    "memo",
                  ] as const
                ).map((key) => (
                  <td key={key}>
                    {key === "direction"
                      ? select(
                          `loans[${index}].${key}`,
                          `대여금 구분 ${index + 1}`,
                          row.direction,
                          codes.IO ?? [],
                          (value) =>
                            change((data) => {
                              data.loans[index].direction = value;
                            }),
                        )
                      : input(
                          `loans[${index}].${key}`,
                          `대여금 ${key} ${index + 1}`,
                          row[key],
                          (value) =>
                            change((data) => {
                              data.loans[index][key] = value;
                            }),
                          key === "date"
                            ? "date"
                            : key === "amount"
                              ? "number"
                              : "text",
                        )}
                  </td>
                ))}
                <td>
                  {!locked &&
                    button(`loan-delete-${index}`, "삭제", () =>
                      change((data) => {
                        data.loans.splice(index, 1);
                      }),
                    )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <footer className="finance-actions">
        {button(
          "cancel",
          "취소",
          () => {
            if (baseline) setDraft(clone(baseline));
            setFields({});
            setError("");
          },
          loading || locked,
        )}
        {button(
          "save",
          "저장",
          () => {
            void save();
          },
          loading || locked,
        )}
        {dirty && <span>저장 전 · 두 탭 함께 처리</span>}
      </footer>
      <FormulaBox
        rows={[
          ["판관비", "재무 항목 판관비 합계 + 간접 인건비 + 연구개발비"],
          [
            "법인세(월할)",
            "(연간 예상 법인세 − 마감월 반영액) ÷ 남은 개월 수, 끝자리는 마지막 미마감월",
          ],
          [
            "자금수지",
            "판관비/영업외비용은 부가세 포함, 카드·이체·대여금은 자금에만 반영",
          ],
          [
            "전월 조정",
            "선택월 이익에 부호 그대로 더합니다. 원 마감월은 바꾸지 않습니다.",
          ],
        ]}
      />
      {payment && (
        <Dialog
          title="납부 확인"
          testId="finance-payment-dialog"
          onClose={() => setPayment(null)}
        >
          <p>납부 확인은 바로 저장되며 취소로 되돌릴 수 없습니다.</p>
          {input(
            "actualPaymentDate",
            "실제 납부일*",
            actualDate,
            setActualDate,
            "date",
            false,
            false,
          )}
          {input(
            "actualPaymentAmount",
            "실제 납부액*",
            actualAmount,
            setActualAmount,
            "number",
            false,
            false,
          )}
          {button("payment-cancel", "취소", () => setPayment(null))}
          {button("payment-submit", "납부 확인", () => {
            void confirmPayment();
          })}
        </Dialog>
      )}
      {previous && (
        <Dialog
          title="지난달 항목을 불러올까요?"
          testId="finance-previous-dialog"
          onClose={() => setPrevious(null)}
        >
          <p>
            이번 달에 없는 항목 {previous.length}개:{" "}
            {previous.map((row) => row.itemName).join(", ")}
          </p>
          <p>
            금액은 그대로, 입출금일은 이번 달 같은 날짜로 바뀝니다. 저장해야
            반영됩니다.
          </p>
          {button("previous-cancel", "취소", () => setPrevious(null))}
          {button("previous-apply", "불러오기", () => {
            change((data) => {
              data.items.push(...previous);
            });
            setPrevious(null);
            setNotice("지난달 항목을 불러왔습니다. 저장해야 반영됩니다.");
          })}
        </Dialog>
      )}
      {upload && (
        <Dialog
          title={upload === "file" ? "엑셀 업로드 B.2" : "엑셀 교체 확인"}
          testId="finance-upload-dialog"
          onClose={() => setUpload(null)}
        >
          <p>선택 연월 {month}</p>
          {upload === "file" ? (
            <>
              <input
                type="file"
                aria-label="파일*"
                data-testid="finance-upload-file"
                accept=".xlsx"
                onChange={(event) => {
                  setFile(event.target.files?.[0] ?? null);
                  setInspection(null);
                }}
              />
              {fieldError("file")}
              {button(
                "upload-preview",
                "검사 및 교체 확인",
                () => {
                  void previewUpload();
                },
                !file,
              )}
              {inspection && (
                <div role="alert">
                  오류 {inspection.errorCount}건
                  {inspection.issues.map((issue, index) => (
                    <p key={index}>
                      {issue.row}행 {issue.column}: {issue.reason}
                    </p>
                  ))}
                </div>
              )}
            </>
          ) : (
            <>
              <p>
                기존 엑셀{" "}
                {baseline?.items.filter((row) => row.uploadId).length ?? 0}건을
                새 파일 {inspection?.successCount ?? 0}건으로 교체합니다.
                직접·자동·불러오기{" "}
                {baseline?.items.filter((row) => !row.uploadId).length ?? 0}건은
                그대로 둡니다.
              </p>
              {button("upload-apply", "반영", () => {
                void applyUpload();
              })}
            </>
          )}
          {error && <p role="alert">{error}</p>}
          {Object.entries(fields).map(([name, text]) => (
            <p key={name} className="finance-error">
              {name}: {text}
            </p>
          ))}
          {button("upload-cancel", "취소", () => setUpload(null))}
        </Dialog>
      )}
      {notice && <Toast message={notice} onDismiss={() => setNotice("")} />}
    </section>
  );
}

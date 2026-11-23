import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { isAllowed } from "../../shared/period/periodRules";
import {
  ConfirmDialog,
  Toast,
  type ToastKind,
} from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import { NavigationState } from "../../shared/layout/NavigationState";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useViewport } from "../../shared/layout/useViewport";
import { Banner } from "../../shared/list/DetailPanel";
import { Badge } from "../../shared/list/ListView";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { getCommonCodes } from "../codegroups/codeGroups";
import { UploadActions } from "../uploads/UploadActions";
import type { UploadCheckConfirm } from "../uploads/UploadDialog";
import { useFinanceItemTitleHost } from "./FinanceItemWorkspace";
import { FinanceMonthStrip } from "./FinanceMonthStrip";
import { FinanceItemsTable, isAutoRow } from "./FinanceItemsTable";
import { FinanceTaxCard } from "./FinanceTaxCard";
import { FinancePaymentDialog } from "./FinancePaymentDialog";
import { FinanceAdjustmentCard } from "./FinanceAdjustmentCard";
import { FinanceCardSection } from "./FinanceCardSection";
import { FinanceLoanSection, FinanceTransferSection } from "./FinanceCashRows";
import {
  ADJ_ITEMS,
  FIN_CLASSES,
  LOAN_DIRECTIONS,
  TRANSFER_CLASSES,
  financeText,
  monthlyTaxFormula,
  paymentKind,
} from "./financeItemFormulas";
import { Icon, dash, won, type Fields } from "./financeParts";
import * as api from "./financeItems";
import "./financeItems.css";

type Option = { code: string; codeName: string };
type Ask = {
  title: string;
  description: string;
  proceedLabel: string;
  destructive: boolean;
  action: () => void;
};
const SCREEN = "UI-10";
const clone = <T,>(value: T): T => structuredClone(value);
const failure = (reason: unknown) =>
  reason instanceof Error
    ? reason.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";
const isConflict = (reason: unknown) =>
  (reason instanceof api.FinanceRequestError && reason.status === 409) ||
  (reason instanceof Error && reason.message === feedbackMessages.conflict);
const addMonth = (month: string, delta: number) => {
  const total =
    Number(month.slice(0, 4)) * 12 + Number(month.slice(4)) - 1 + delta;
  return `${Math.floor(total / 12)}${String((total % 12) + 1).padStart(2, "0")}`;
};
// 금액 칸은 쓰는 동안 숫자 글자로 보관한다. 같은 금액이면 바뀐 것으로 보지 않는다.
const MONEY_KEYS = new Set([
  "amount",
  "vatAmount",
  "paymentAmount",
  "estimatedTaxAmount",
]);
const same = (left: unknown, right: unknown) =>
  JSON.stringify(left, (key, value) =>
    MONEY_KEYS.has(key) && typeof value === "string" && value !== ""
      ? Number(value)
      : value,
  ) ===
  JSON.stringify(right, (key, value) =>
    MONEY_KEYS.has(key) && typeof value === "string" && value !== ""
      ? Number(value)
      : value,
  );
const cardSum = (value: api.FinanceMonth) =>
  value.cards.reduce((total, card) => total + Number(card.amount || 0), 0);
const cardTotal = (value: api.FinanceMonth) => ({
  manual: value.cardPayment.manualOverride,
  amount: value.cardPayment.manualOverride
    ? value.cardPayment.paymentAmount
    : null,
});
/** 시안 finDirty(): 저장 안 한 묶음 이름(순서 그대로). */
function dirtySections(
  draft: api.FinanceMonth | null,
  base: api.FinanceMonth | null,
) {
  if (!draft || !base) return [];
  return (
    [
      ["판관비·영업외", same(draft.items, base.items)],
      [
        "법인세",
        same(
          [draft.taxEstimate.estimatedTaxAmount, draft.taxPayments],
          [base.taxEstimate.estimatedTaxAmount, base.taxPayments],
        ),
      ],
      ["대여금", same(draft.loans, base.loans)],
      ["전월 조정", same(draft.adjustments, base.adjustments)],
      [
        "법인카드",
        same([draft.cards, cardTotal(draft)], [base.cards, cardTotal(base)]),
      ],
      ["계좌이체 지출", same(draft.transfers, base.transfers)],
    ] as const
  )
    .filter(([, unchanged]) => !unchanged)
    .map(([name]) => name);
}
const FIRST_TAB = ["판관비·영업외", "법인세", "전월 조정"];
function emptyMonth(month: string, closed: boolean): api.FinanceMonth {
  return {
    yearMonth: month,
    closed,
    firstOpenYearMonth: null,
    modifiedAt: null,
    months: [],
    items: [],
    loans: [],
    transfers: [],
    adjustments: [],
    cards: [],
    taxPayments: [],
    taxEstimate: {
      taxYear: Number(month.slice(0, 4)),
      estimatedTaxAmount: "",
      monthlyTaxAmount: Number.NaN,
      negativeTaxWarning: false,
      modifiedAt: null,
    },
    cardPayment: { paymentAmount: 0, manualOverride: false, modifiedAt: null },
    summary: {
      sgaAmount: 0,
      nonOperatingIncomeAmount: 0,
      nonOperatingExpenseAmount: 0,
      monthlyTaxAmount: 0,
      adjustmentAmount: 0,
      cardTotalAmount: 0,
      cardPaymentDifference: 0,
    },
    activeCardCount: 0,
    autoFillCount: 0,
  };
}

/** UI-10 / SCR-UI-10 재무 항목: 두 탭의 초안은 하단 [저장] 한 번으로 함께 저장한다. */
export function FinanceItemPage() {
  const period = useScreenPeriod(SCREEN, { kind: "yearMonth" });
  const location = useLocation();
  const navigate = useNavigate();
  const consumedHandoff = useRef<string | null>(null);
  const { mobile } = useViewport();
  const host = useFinanceItemTitleHost();
  const setDirty = useScreenDirty();
  const ready = period.state.status === "ready" ? period.state : null;
  const basis = ready?.basis ?? null;
  const month = ready
    ? `${ready.value.year}${String(ready.value.month).padStart(2, "0")}`
    : "";
  const [draft, setDraft] = useState<api.FinanceMonth | null>(null);
  const [baseline, setBaseline] = useState<api.FinanceMonth | null>(null);
  const [monthsInfo, setMonthsInfo] = useState<api.FinanceMonth["months"]>([]);
  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [conflict, setConflict] = useState(false);
  const [fields, setFields] = useState<Fields>({});
  const [paymentFields, setPaymentFields] = useState<Fields>({});
  const [notice, setNotice] = useState<{
    message: string;
    kind: ToastKind;
    id: number;
  } | null>(null);
  const [codes, setCodes] = useState<Record<string, Option[]>>({});
  const [ask, setAsk] = useState<Ask | null>(null);
  const [payment, setPayment] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);
  const [adjPreset, setAdjPreset] = useState<string | null>(null);
  const adjFocus = useRef(false);
  const uploadMonth = useRef("");
  const root = useRef<HTMLElement>(null);
  const adjAmount = useRef<HTMLInputElement>(null);
  const inputs = useRef<Record<string, HTMLElement | null>>({});
  const register = useCallback(
    (name: string) => (element: HTMLElement | null) => {
      inputs.current[name] = element;
    },
    [],
  );
  const notify = useCallback(
    (message: string, kind: ToastKind = "ok") =>
      setNotice((old) => ({ message, kind, id: (old?.id ?? 0) + 1 })),
    [],
  );
  const closed = draft
    ? draft.closed
    : !!basis && !!month && basis.closedMonths.includes(month);
  const view = draft ?? (month ? emptyMonth(month, closed) : null);
  const sections = dirtySections(draft, baseline);
  const dirty = sections.length > 0;
  const firstDirty = sections.some((name) => FIRST_TAB.includes(name));
  const secondDirty = sections.some((name) => !FIRST_TAB.includes(name));
  const disabled = !draft || busy || loading;
  const option = (group: string, fallback: Option[]) =>
    codes[group]?.length ? codes[group] : fallback;
  const classes = option("FIN_CLS", FIN_CLASSES);

  useEffect(() => {
    setDirty(SCREEN, dirty);
  }, [dirty, setDirty]);
  useEffect(() => () => setDirty(SCREEN, false), [setDirty]);
  useEffect(() => {
    const controller = new AbortController();
    for (const group of ["FIN_CLS", "SGA_ITEM", "ADJ_ITEM", "IO", "XFER_CLS"])
      getCommonCodes(group, controller.signal)
        .then((response) => {
          if (!controller.signal.aborted)
            setCodes((old) => ({ ...old, [group]: response?.items ?? [] }));
        })
        .catch(() => undefined);
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (!month || mobile) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setConflict(false);
    setFields({});
    setDraft(null);
    setBaseline(null);
    api
      .postCommonFinanceTaxYear(Number(month.slice(0, 4)), controller.signal)
      .then(() => api.getCommonFinanceItems(month, controller.signal))
      .then((value) => {
        if (controller.signal.aborted) return;
        setBaseline(clone(value));
        setDraft(value);
        setMonthsInfo(value.months ?? []);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(failure(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [month, revision, mobile]);
  // [전월 조정 입력]으로 왔으면 그 달을 읽은 뒤 전월 조정 표로 옮겨 금액 칸에 커서를 둔다.
  useEffect(() => {
    if (!adjFocus.current || !draft || loading || draft.closed) return;
    adjFocus.current = false;
    requestAnimationFrame(() => {
      document
        .getElementById("finance-adjustment-card")
        ?.scrollIntoView?.({ block: "start" });
      adjAmount.current?.focus();
    });
  }, [draft, adjPreset, loading]);

  useEffect(() => {
    const source = location.state?.financeAdjustmentSourceMonth;
    if (
      typeof source !== "string" ||
      !/^[0-9]{4}(0[1-9]|1[0-2])$/.test(source) ||
      consumedHandoff.current === location.key ||
      !draft ||
      loading ||
      draft.yearMonth !== month ||
      !basis
    )
      return;
    if (
      source !== month &&
      isAllowed(
        { year: Number(source.slice(0, 4)), month: Number(source.slice(4)) },
        basis,
        { kind: "yearMonth" },
      )
    ) {
      goMonth(source);
      return;
    }
    consumedHandoff.current = location.key;
    goAdjustment(source, true);
    const { financeAdjustmentSourceMonth: _source, ...rest } = location.state;
    navigate(location.pathname, { replace: true, state: rest });
  }, [
    location.key,
    location.state,
    location.pathname,
    draft,
    loading,
    month,
    basis,
  ]);

  const change = (edit: (value: api.FinanceMonth) => void) =>
    setDraft((old) => {
      if (!old) return old;
      const next = clone(old);
      edit(next);
      return next;
    });
  const focusField = (name: string) =>
    requestAnimationFrame(() => {
      const element = inputs.current[name];
      element?.focus();
      element?.scrollIntoView?.({ block: "center" });
    });
  const goMonth = (target: string) => {
    setFields({});
    period.setValue({
      year: Number(target.slice(0, 4)),
      month: Number(target.slice(4)),
    });
  };
  /** 시안 setMonth(): 저장 안 한 내용이 있으면 묻고 다른 달로 간다. */
  const pickMonth = (target: string, after?: () => void) => {
    if (!target || target === month) return;
    const move = () => {
      goMonth(target);
      after?.();
    };
    if (!dirty) return move();
    setAsk({
      title: "저장 안 한 내용이 있습니다",
      description: `${sections.join(", ")}에서 바꾼 내용이 아직 저장되지 않았습니다. 저장하지 않고 다른 달로 갈까요?`,
      proceedLabel: "저장 안 하고 이동",
      destructive: true,
      action: move,
    });
  };
  const report = (reason: unknown, onField?: (fields: Fields) => void) => {
    if (isConflict(reason)) {
      // 입력은 그대로 두고, 다시 불러올지 사용자가 고른다.
      setConflict(true);
      return;
    }
    if (reason instanceof api.FinanceRequestError && reason.errors.length) {
      const unpaid = (draft?.taxPayments ?? [])
        .map((row, index) => (row.paidYn === "Y" ? -1 : index))
        .filter((index) => index >= 0);
      const mapped: Fields = {};
      for (const item of reason.errors) {
        // 납부 일정은 납부 확인 안 한 줄만 보내므로 서버 줄 번호를 화면 줄 번호로 바꾼다.
        const field = item.field.replace(
          /^taxPayments\[(\d+)\]/,
          (_, at) => `taxPayments[${unpaid[Number(at)] ?? at}]`,
        );
        mapped[field] = item.message;
      }
      if (onField) return onField(mapped);
      setFields(mapped);
      const first = Object.keys(mapped)[0];
      setTab(/^(cards|loans|transfers|cardPayment)/.test(first) ? 1 : 0);
      focusField(first);
      notify(mapped[first] ?? failure(reason), "warn");
      return;
    }
    notify(failure(reason), "warn");
  };
  async function save() {
    if (!draft || busy || closed) return;
    const missing: Fields = {};
    draft.items.forEach((row, index) => {
      if (!row.itemName.trim())
        missing[`items[${index}].itemName`] = "세부 항목을 입력해 주세요.";
    });
    draft.items.forEach((row, index) => {
      if (row.amount === "" || row.amount == null)
        missing[`items[${index}].amount`] = "금액을 입력해 주세요.";
    });
    const first = Object.keys(missing)[0];
    if (first) {
      setTab(0);
      setFields(missing);
      focusField(first);
      notify(missing[first], "info");
      return;
    }
    setBusy(true);
    setFields({});
    const saved = sections.filter((name) => name !== "판관비·영업외");
    try {
      const result = await api.putCommonFinanceItems(month, {
        ...draft,
        cardPayment: {
          ...draft.cardPayment,
          paymentAmount: draft.cardPayment.manualOverride
            ? draft.cardPayment.paymentAmount
            : cardSum(draft),
        },
      });
      setDraft(result);
      setBaseline(clone(result));
      if (result.months?.length) setMonthsInfo(result.months);
      setConflict(false);
      notify(
        saved.length
          ? `저장했습니다. ${saved.join("·")}도 함께 반영했습니다.`
          : "저장했습니다.",
      );
    } catch (reason) {
      report(reason);
    } finally {
      setBusy(false);
    }
  }
  const saveRef = useRef(save);
  saveRef.current = save;
  const closedRef = useRef(closed);
  closedRef.current = closed;
  const monthRef = useRef(month);
  monthRef.current = month;
  // 시안 keydown: Ctrl+S는 이 달이 마감 전이면 저장한다(창이 떠 있으면 창이 받는다).
  useEffect(() => {
    if (mobile) return;
    const keydown = (event: KeyboardEvent) => {
      if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "s")
        return;
      const element = root.current;
      if (!element || element.closest("[hidden]")) return;
      if (document.querySelector('[role="dialog"],[role="alertdialog"]'))
        return;
      event.preventDefault();
      if (closedRef.current)
        notify("이 화면에는 저장할 내용이 없습니다.", "info");
      else void saveRef.current();
    };
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [mobile, notify]);

  const discard = () => {
    if (baseline) setDraft(clone(baseline));
    setFields({});
  };
  const cancel = () => {
    discard();
    notify("변경을 취소했습니다.", "info");
  };
  async function loadPrevious() {
    if (!draft) return;
    const requested = month;
    const previousMonth = dash(addMonth(month, -1));
    try {
      const result = await api.getCommonFinancePreviousItems(
        month,
        draft.items,
      );
      // 응답 전에 다른 달로 옮겼으면 옛 달 결과를 버린다.
      if (monthRef.current !== requested) return;
      if (!result.count) {
        notify(`${previousMonth}에서 가져올 새 항목이 없습니다.`, "info");
        return;
      }
      setAsk({
        title: `${previousMonth} 항목을 불러올까요?`,
        description: `${previousMonth}에 있고 이번 달에 없는 항목 ${result.items.length}개(${result.items.map((row) => row.itemName).join(", ")})를 가져옵니다. 금액은 그대로, 입출금일은 이번 달 같은 날짜로 바뀝니다. 저장해야 반영됩니다.`,
        proceedLabel: "불러오기",
        destructive: false,
        action: () => {
          change((data) => {
            if (data.yearMonth !== requested) return;
            data.items.push(
              ...result.items.map((row) => ({
                ...row,
                itemId: null,
                modifiedAt: null,
                autoFillYn: "Y",
              })),
            );
          });
          notify(`${result.items.length}개 항목을 불러왔습니다.`);
        },
      });
    } catch (reason) {
      report(reason);
    }
  }
  async function confirmPayment(date: string, amount: number) {
    if (payment == null || !draft || busy) return;
    const row = draft.taxPayments[payment];
    setBusy(true);
    setPaymentFields({});
    try {
      const confirmed = await api.postCommonFinanceTaxPaymentConfirm(
        row,
        date,
        amount,
      );
      const merge = (value: api.FinanceMonth | null) =>
        value && {
          ...value,
          taxPayments: value.taxPayments.map((item) =>
            item.paymentId === confirmed.paymentId ? confirmed : item,
          ),
        };
      setDraft(merge);
      setBaseline(merge);
      setPayment(null);
      notify(
        `${paymentKind(row.taxYear, row.paymentType)} 납부를 확인했습니다. 자금수지표 ${Number(date.slice(5, 7))}월 법인세 납부에 ${won(amount)}원으로 반영했습니다.`,
      );
    } catch (reason) {
      if (isConflict(reason)) {
        setPayment(null);
        setConflict(true);
      } else report(reason, setPaymentFields);
    } finally {
      setBusy(false);
    }
  }
  /** 시안 ACT.adjgo: 마감 안 된 첫 달의 「전월 조정」 표로 가서 보던 마감월을 미리 고른다. */
  function goAdjustment(source = month, handoff = false) {
    if (!draft?.firstOpenYearMonth || !basis) {
      if (handoff)
        notify("전월 조정을 입력할 수 있는 미마감월이 없습니다.", "info");
      return;
    }
    if (
      handoff &&
      (!basis.closedMonths.includes(source) ||
        !isAllowed(
          {
            year: Number(draft.firstOpenYearMonth.slice(0, 4)),
            month: Number(draft.firstOpenYearMonth.slice(4)),
          },
          basis,
          { kind: "yearMonth" },
        ) ||
        basis.closedMonths.includes(draft.firstOpenYearMonth) ||
        draft.firstOpenYearMonth <= source ||
        source < addMonth([...basis.closedMonths].sort().at(-1) ?? source, -11))
    ) {
      notify("전월 조정을 입력할 수 있는 미마감월이 없습니다.", "info");
      return;
    }
    const target =
      !handoff && draft.firstOpenYearMonth > basis.latestYm
        ? basis.latestYm
        : draft.firstOpenYearMonth;
    setAdjPreset(source);
    setTab(0);
    adjFocus.current = true;
    goMonth(target);
    notify(
      `${Number(target.slice(4))}월 재무 항목의 「전월 조정」 표에 넣어 주세요.`,
      "info",
    );
  }

  if (mobile)
    return (
      <section className="screen-body" data-testid="finance-items-page">
        <NavigationState
          kind="pc-only"
          description="「재무 항목」은 큰 표를 다루는 화면이라 휴대폰에서는 열리지 않습니다."
        >
          <Link className="primary-button" to="/screens/UI-16">
            대시보드로
          </Link>
        </NavigationState>
      </section>
    );

  const year = ready?.value.year ?? null;
  const closedOfYear = basis
    ? basis.closedMonths.filter((key) => key.startsWith(String(year))).length
    : 0;
  const formula = monthlyTaxFormula(closedOfYear, 12 - closedOfYear);
  const closedTo = basis?.closedMonths.length
    ? [...basis.closedMonths].sort().at(-1)!
    : "";
  const targets: string[] = [];
  if (closedTo)
    for (
      let key = closedTo;
      key >= addMonth(closedTo, -11) && key < month;
      key = addMonth(key, -1)
    )
      targets.push(key);
  const openMonths: string[] = [];
  if (basis) {
    const start = closedTo ? addMonth(closedTo, 1) : basis.cashBaseYm;
    for (let key = start; key <= basis.latestYm; key = addMonth(key, 1))
      openMonths.push(dash(key));
  }
  const autoCount = view ? view.items.filter(isAutoRow).length : 0;
  const monthNo = month ? Number(month.slice(4)) : 0;
  const autoAmount = (row: api.FinancePayment) => {
    if (view && row.taxYear === view.taxEstimate.taxYear) {
      const mid = view.taxPayments.find(
        (item) => item.taxYear === row.taxYear && item.paymentType === "MID",
      );
      return (
        Number(view.taxEstimate.estimatedTaxAmount || 0) -
        Number(mid?.paymentAmount || 0)
      );
    }
    const base = baseline?.taxPayments.find(
      (item) => item.paymentId === row.paymentId,
    );
    return base && base.paymentAmount == null ? base.displayAmount : null;
  };
  const beforeCheck = async (
    chosen: string,
  ): Promise<UploadCheckConfirm | null> => {
    const key = chosen.replace("-", "");
    uploadMonth.current = key;
    const rows =
      key === month && baseline
        ? baseline.items
        : (await api.getCommonFinanceItems(key)).items;
    const saved = rows.filter((row) => row.itemId);
    const uploaded = saved.filter((row) => row.uploadId).length;
    if (!uploaded) return null;
    return {
      title: "같은 달을 다시 올립니다",
      description: `엑셀로 올린 ${Number(key.slice(4))}월 항목 ${uploaded}줄을 지우고 새 엑셀 내용으로 바꿉니다. 직접 입력한 ${saved.length - uploaded}줄은 그대로 둡니다. ${financeText.uploadAgainSub}`,
      proceedLabel: "바꾸기",
    };
  };
  const titleActions = !closed && (
    <div className="fin-title-actions">
      <button
        type="button"
        className="upload-title-button"
        data-testid="finance-previous"
        disabled={disabled}
        onClick={() => void loadPrevious()}
      >
        <Icon name="down" /> 지난달 항목 불러오기
      </button>
      <UploadActions
        formTypeCode="B2"
        targetName="재무 항목"
        disabled={disabled}
        dialog={{
          title: "엑셀 업로드 · 재무 항목",
          checkLabel: "올리기",
          month: {
            value: openMonths.includes(dash(month))
              ? dash(month)
              : (openMonths[0] ?? dash(month)),
            options: openMonths.length ? openMonths : [dash(month)],
            help: financeText.uploadHelp,
          },
          intro: <p className="upload-help">{financeText.uploadIntro}</p>,
          beforeCheck,
        }}
        onOpen={(open) => {
          if (!dirty) return open();
          setAsk({
            title: "저장 안 한 내용이 있습니다",
            description: `${sections.join(", ")}에서 바꾼 내용이 아직 저장되지 않았습니다. 엑셀을 올리면 저장하지 않은 내용이 사라집니다.`,
            proceedLabel: "저장 안 하고 올리기",
            destructive: true,
            action: () => {
              discard();
              open();
            },
          });
        }}
        onApplied={() => {
          const key = uploadMonth.current || month;
          if (key !== month) goMonth(key);
          else setRevision((value) => value + 1);
        }}
      />
    </div>
  );

  return (
    <section
      ref={root}
      className="screen-body finance-items"
      data-screen-id={SCREEN}
      data-testid="finance-items-page"
    >
      {host ? createPortal(titleActions, host) : titleActions}
      <FinanceMonthStrip
        year={year}
        month={month}
        basis={basis}
        months={monthsInfo}
        disabled={busy}
        onPick={(key) => {
          setAdjPreset(null);
          pickMonth(key);
        }}
        onYear={(target) => {
          if (!basis) return;
          const latestYear = Number(basis.latestYm.slice(0, 4));
          setAdjPreset(null);
          pickMonth(target < latestYear ? `${target}12` : basis.latestYm);
        }}
      />
      {period.state.status === "error" && (
        <Banner kind="bad" icon={<Icon name="info" size={18} />}>
          {period.state.message}
        </Banner>
      )}
      {closed && month && (
        <Banner
          kind="lock"
          icon={<Icon name="lock" size={18} />}
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="finance-adjust-closed"
              disabled={!draft?.firstOpenYearMonth || busy}
              onClick={() => goAdjustment()}
            >
              전월 조정 입력
            </button>
          }
        >
          <strong>{dash(month)}</strong>는 마감되었습니다. 고칠 내용은 다음 달
          전월 조정으로 입력하세요.
        </Banner>
      )}
      {error && (
        <Banner
          kind="bad"
          icon={<Icon name="info" size={18} />}
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="finance-retry"
              onClick={() => setRevision((value) => value + 1)}
            >
              다시 시도
            </button>
          }
        >
          {error}
        </Banner>
      )}
      {conflict && (
        <Banner
          kind="bad"
          icon={<Icon name="info" size={18} />}
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="finance-reload"
              disabled={busy}
              onClick={() => setRevision((value) => value + 1)}
            >
              다시 불러오기
            </button>
          }
        >
          {feedbackMessages.conflict}
        </Banner>
      )}
      {loading && (
        <p role="status" className="ls-sr">
          불러오는 중입니다.
        </p>
      )}
      <div
        role="tablist"
        aria-label="재무 항목 탭"
        className="ls-tabs fin-tabs"
      >
        {financeText.tabs.map(([label, title], index) => (
          <button
            role="tab"
            type="button"
            className={`ls-tb${tab === index ? " ls-on" : ""}`}
            data-testid={`finance-tab-${index}`}
            id={`finance-tab-${index}`}
            aria-controls={`finance-panel-${index}`}
            aria-selected={tab === index}
            title={title}
            key={label}
            onClick={() => setTab(index)}
          >
            {label}
            {(index === 0 ? firstDirty : secondDirty) && (
              <>
                {" "}
                <Badge kind="warn">저장 전</Badge>
              </>
            )}
          </button>
        ))}
      </div>
      <p className="ls-cap fin-tab-guide">{financeText.tabGuide[tab]}</p>
      {view && (
        <>
          <div
            role="tabpanel"
            id="finance-panel-0"
            data-testid="finance-panel-0"
            aria-labelledby="finance-tab-0"
            className="fin-panel"
            hidden={tab !== 0}
          >
            {autoCount > 0 && !closed && (
              <Banner kind="info" icon={<Icon name="info" size={18} />}>
                '매달 반복' 항목 <strong>{autoCount}개</strong>를 지난달(
                {dash(addMonth(month, -1))})에서 자동으로 채웠습니다. 금액이
                다르면 고친 뒤 저장해 주세요.
              </Banner>
            )}
            <FinanceItemsTable
              month={dash(month)}
              items={view.items}
              closed={closed}
              disabled={disabled}
              classes={classes}
              sgaItems={codes.SGA_ITEM ?? []}
              monthlyTax={view.taxEstimate.monthlyTaxAmount}
              taxFormula={formula}
              fields={fields}
              register={register}
              onChange={(index, patch) =>
                change((data) => {
                  Object.assign(data.items[index], patch);
                })
              }
              onDelete={(index) =>
                setAsk({
                  title: "이 행을 삭제할까요?",
                  description: `「${view.items[index]?.itemName || "새 항목"}」 행을 지웁니다.`,
                  proceedLabel: "삭제",
                  destructive: true,
                  action: () => {
                    setFields({});
                    change((data) => {
                      data.items.splice(index, 1);
                    });
                  },
                })
              }
              onAdd={() => {
                const at = view.items.length;
                change((data) => {
                  data.items.push({
                    itemId: null,
                    modifiedAt: null,
                    classification: classes[0]?.code ?? "",
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
                });
                focusField(`items[${at}].itemName`);
              }}
              onGoTax={() => {
                const card = document.getElementById("finance-tax-card");
                card?.scrollIntoView?.({ behavior: "smooth", block: "center" });
                const input = inputs.current["taxEstimate.estimatedTaxAmount"];
                if (input instanceof HTMLInputElement && !input.readOnly)
                  input.focus();
              }}
            />
            <FinanceTaxCard
              value={view}
              closed={closed}
              disabled={disabled}
              estimateDirty={
                !!draft &&
                !!baseline &&
                !same(
                  draft.taxEstimate.estimatedTaxAmount,
                  baseline.taxEstimate.estimatedTaxAmount,
                )
              }
              formula={formula}
              autoAmount={autoAmount}
              fields={fields}
              register={register}
              notify={notify}
              onEstimate={(amount) =>
                change((data) => {
                  data.taxEstimate.estimatedTaxAmount = amount;
                })
              }
              onPayment={(index, patch) =>
                change((data) => {
                  Object.assign(data.taxPayments[index], patch);
                })
              }
              onConfirm={(index) => {
                setPaymentFields({});
                setPayment(index);
              }}
            />
            <FinanceAdjustmentCard
              key={`${month}-${adjPreset ?? ""}`}
              monthNo={monthNo}
              rows={view.adjustments}
              closed={closed}
              disabled={disabled}
              targets={targets}
              preset={adjPreset}
              items={option("ADJ_ITEM", ADJ_ITEMS)}
              fields={fields}
              notify={notify}
              amountRef={adjAmount}
              onAdd={(row) => {
                setAdjPreset(null);
                change((data) => {
                  data.adjustments.push(row);
                });
              }}
              onDelete={(index) => {
                change((data) => {
                  data.adjustments.splice(index, 1);
                });
                notify(
                  "전월 조정 줄을 지웠습니다. [저장]을 누르면 반영됩니다.",
                  "info",
                );
              }}
            />
          </div>
          <div
            role="tabpanel"
            id="finance-panel-1"
            data-testid="finance-panel-1"
            aria-labelledby="finance-tab-1"
            className="fin-panel"
            hidden={tab !== 1}
            key={month}
          >
            <FinanceCardSection
              monthNo={monthNo}
              value={view}
              closed={closed}
              disabled={disabled}
              dirty={sections.includes("법인카드")}
              fields={fields}
              register={register}
              notify={notify}
              onCard={(index, patch) =>
                change((data) => {
                  Object.assign(data.cards[index], patch);
                })
              }
              onAddCard={(card) =>
                change((data) => {
                  data.cards.push(card);
                })
              }
              onRemoveCard={(index) =>
                change((data) => {
                  data.cards.splice(index, 1);
                })
              }
              onTotal={(amount, manual) =>
                change((data) => {
                  data.cardPayment.paymentAmount = amount;
                  data.cardPayment.manualOverride = manual;
                })
              }
              onAuto={() =>
                change((data) => {
                  data.cardPayment.manualOverride = false;
                  data.cardPayment.paymentAmount = cardSum(data);
                })
              }
            />
            <FinanceTransferSection
              month={month}
              rows={view.transfers}
              closed={closed}
              disabled={disabled}
              dirty={sections.includes("계좌이체 지출")}
              classes={option("XFER_CLS", TRANSFER_CLASSES)}
              fields={fields}
              notify={notify}
              onAdd={(row) =>
                change((data) => {
                  data.transfers.push(row);
                  data.transfers.sort((x, y) => x.date.localeCompare(y.date));
                })
              }
              onDelete={(index) =>
                change((data) => {
                  data.transfers.splice(index, 1);
                })
              }
            />
            <FinanceLoanSection
              month={month}
              rows={view.loans}
              closed={closed}
              disabled={disabled}
              directions={option("IO", LOAN_DIRECTIONS)}
              fields={fields}
              notify={notify}
              onAdd={(row) =>
                change((data) => {
                  data.loans.push(row);
                  data.loans.sort((x, y) => x.date.localeCompare(y.date));
                })
              }
              onDelete={(index) =>
                change((data) => {
                  data.loans.splice(index, 1);
                })
              }
            />
          </div>
        </>
      )}
      {!closed && (
        <div className="fin-fbar">
          <div className="r">
            <button
              type="button"
              className="ls-btn"
              data-testid="finance-cancel"
              disabled={disabled}
              onClick={cancel}
            >
              취소
            </button>
            <button
              type="button"
              className="ls-btn ls-btn-p"
              data-testid="finance-save"
              disabled={disabled}
              onClick={() => void save()}
            >
              저장
            </button>
          </div>
        </div>
      )}
      {payment != null && draft?.taxPayments[payment] && (
        <FinancePaymentDialog
          row={draft.taxPayments[payment]}
          amount={Number(
            draft.taxPayments[payment].paymentAmount ??
              (draft.taxPayments[payment].paymentType === "CNFM"
                ? autoAmount(draft.taxPayments[payment])
                : null) ??
              draft.taxPayments[payment].displayAmount,
          )}
          busy={busy}
          fields={paymentFields}
          notify={notify}
          onClose={() => setPayment(null)}
          onSubmit={(date, amount) => void confirmPayment(date, amount)}
        />
      )}
      {ask && (
        <ConfirmDialog
          title={ask.title}
          description={ask.description}
          proceedLabel={ask.proceedLabel}
          destructive={ask.destructive}
          onCancel={() => setAsk(null)}
          onProceed={() => {
            const action = ask.action;
            setAsk(null);
            action();
          }}
        />
      )}
      {notice && (
        <Toast
          key={notice.id}
          message={notice.message}
          kind={notice.kind}
          onDismiss={() => setNotice(null)}
        />
      )}
    </section>
  );
}

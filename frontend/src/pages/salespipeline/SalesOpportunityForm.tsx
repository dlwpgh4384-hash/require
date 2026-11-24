import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  ConfirmDialog,
  InlineConfirmation,
  Toast,
} from "../../shared/feedback/Dialog";
import { Badge, Banner, KpiCard, KpiCards } from "../../shared/list";
import { digitsOnly, focusFirstError } from "../../shared/feedback/messages";
import { formatKoreanMoney, formatMoney } from "../../shared/list/moneyUnit";
import { formatDate, koreanThisMonth } from "../../shared/time";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useScreenTitleOverride } from "../../shared/layout/ScreenTitleOverride";
import "../../shared/list/list.css";
import "./salesOpportunities.css";
import {
  addMonths,
  checkSalesTentativeWarnings,
  formatMm,
  formatPercent,
  getCommonSalesOpportunity,
  getSalesStageDefaults,
  plainNumber,
  postCommonSalesOpportunity,
  putCommonSalesOpportunity,
  quoteBandKind,
  quoteCommonSalesOpportunity,
  salesNeedBadFields,
  salesOpportunityAction,
  salesOpportunityErrors,
  salesOpportunityPayload,
  SalesOpportunityError,
  SALES_CONTRACT_TYPES,
  type SalesNeed,
  type SalesNotice,
  type SalesOpportunity,
  type SalesOpportunityInput,
  type SalesOpportunityOptions,
  type SalesQuote,
  type SalesTentativeWarning,
  writtenOpportunity,
} from "./salesOpportunities";

const ICONS = {
  warn: "M12 3l10 18H2zM12 10v5M12 18h.01",
  info: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01",
  lock: "M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4",
  okc: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 12l3 3 5-6",
  plus: "M12 5v14M5 12h14",
  x: "M18 6L6 18M6 6l12 12",
  more: "M5 12h.01M12 12h.01M19 12h.01",
};
/** 시안 ic(n, s) 아이콘. */
export function salesIcon(name: keyof typeof ICONS, size = 16) {
  return (
    <svg
      className="so-ic"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={name === "more" ? 3 : 1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={ICONS[name]} />
    </svg>
  );
}
/** 시안 won(): 원 단위, 쉼표, 없으면 「–」. */
export const won = (value: number | null | undefined) =>
  formatMoney(value, "원");
/** 화면 숫자(단가·금액)는 쉼표로, 입력은 숫자만 받는다(공통 digitsOnly). 비우면 null. */
const numberOrNull = (value: string) => {
  const only = digitsOnly(value);
  return only ? Number(only) : null;
};
const codeName = (
  options: SalesOpportunityOptions,
  group: string,
  code: string | null | undefined,
) =>
  options.codes[group]?.find((c) => c.code === code)?.codeName ||
  (code ? code : "–");
/** 견적 계산에 넣을 수 있는 줄(시안 quoteCheck: 직무·인원·개월이 있는 줄). */
export function quotableNeeds(needs: SalesNeed[]) {
  return needs
    .map((need, index) => ({ need, index }))
    .filter(({ need }) => salesNeedBadFields(need).length === 0);
}
/** 견적 계산 한 번: 넣은 줄만 보내고, 줄 번호(rowIndex)를 폼의 줄 번호로 되돌린다. */
export async function quoteNeeds(needs: SalesNeed[]) {
  const rows = quotableNeeds(needs);
  if (!rows.length) return null;
  const quote = await quoteCommonSalesOpportunity(rows.map((r) => r.need));
  return {
    ...quote,
    needQuotes: (quote.needQuotes || []).map((q) => ({
      ...q,
      rowIndex: rows[q.rowIndex]?.index ?? q.rowIndex,
    })),
  } as SalesQuote;
}

/** 시안 quoteHtml(F): 견적 이익 점검(SFR-129-04) 본문. 폼과 기회 상세 「견적 점검」 탭이 같이 쓴다. */
export function SalesQuoteView({
  quote,
  needs,
  options,
  error,
  loading,
}: {
  quote: SalesQuote | null;
  needs: SalesNeed[];
  options: SalesOpportunityOptions;
  error?: string;
  loading?: boolean;
}) {
  const rows = quotableNeeds(needs);
  if (error)
    return (
      <p className="so-errmsg" role="alert" data-testid="sales-quote-error">
        {error}
      </p>
    );
  if (!rows.length)
    return (
      <p className="so-sub" data-testid="sales-quote-empty">
        필요 인력을 넣으면 예상 이익률을 바로 계산합니다.
      </p>
    );
  if (!quote)
    return (
      <p className="so-sub" role="status">
        {loading === false ? "–" : "계산 중…"}
      </p>
    );
  const totalMm = rows.reduce(
    (s, { need }) => s + need.requiredCount * need.monthlyMm * need.months,
    0,
  );
  const approval = quote.approvalRate;
  const need = quote.profitBand === "손해 예상" || quote.profitBand === "승인 필요";
  const target = quote.targetRate;
  const jobPrice = (job: string) =>
    options.codes.JOB?.find((c) => c.code === job)?.estimateUnitPrice;
  return (
    <div className="so-quote" data-testid="sales-quote">
      <KpiCards aria-label="견적 이익 점검">
        <KpiCard
          label="예상 매출 (단가 × MM)"
          value={formatKoreanMoney(quote.expectedRevenue)}
          description={`${totalMm.toFixed(1)} MM`}
          testId="sales-quote-revenue"
        />
        <KpiCard
          label="예상 원가"
          value={formatKoreanMoney(quote.expectedCost)}
          description="등급별 평균 원가 기준"
          testId="sales-quote-cost"
        />
        <KpiCard
          label="예상 이익률"
          value={
            <span className={need ? "so-bad" : undefined}>
              {formatPercent(quote.profitRate)}
            </span>
          }
          description={
            quote.profitBand ? (
              <Badge kind={quoteBandKind(quote.profitBand)}>
                {quote.profitBand}
              </Badge>
            ) : undefined
          }
          testId="sales-quote-rate"
        />
        <KpiCard
          label="손해 안 보는 최소 금액"
          value={formatKoreanMoney(quote.breakEvenAmount ?? quote.expectedCost)}
          description={
            target != null
              ? `목표 이익률 ${plainNumber(target)}%면 ${formatKoreanMoney(quote.targetAmount)}`
              : `목표 이익률을 맞추는 금액 ${formatKoreanMoney(quote.targetAmount)}`
          }
          testId="sales-quote-break-even"
        />
      </KpiCards>
      {need && (
        <Banner kind="bad" icon={salesIcon("warn")}>
          예상 이익률이 승인 기준({plainNumber(approval)}%)보다 낮습니다.
          저장하면 <strong>견적 승인 대기</strong>가 되고, 경영진이 승인하기
          전에는 '협상' 단계로 옮길 수 없습니다.
        </Banner>
      )}
      <div className="ls-twrap so-twrap">
        <table className="ls-t">
          <thead>
            <tr>
              <th>직무 · 등급</th>
              <th className="ls-right">입력 단가</th>
              <th className="ls-right">최소 단가</th>
              <th className="ls-right">목표 단가</th>
              <th className="ls-right">이익률</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ need: n, index }) => {
              const q = quote.needQuotes?.find((x) => x.rowIndex === index);
              const price =
                n.estimatedUnitPrice ?? q?.unitPrice ?? jobPrice(n.jobCode);
              const rate =
                price && q ? ((price - q.minimumUnitPrice) / price) * 100 : null;
              return (
                <tr key={index} data-testid={`sales-quote-row-${index}`}>
                  <td>
                    {codeName(options, "JOB", n.jobCode)} ·{" "}
                    {codeName(options, "GRADE", n.gradeCode)}{" "}
                    <span className="so-cap">
                      {formatMm(n.requiredCount * n.monthlyMm * n.months)} MM
                    </span>
                  </td>
                  <td className="ls-right">{won(price)}</td>
                  <td className="ls-right">{won(q?.minimumUnitPrice)}</td>
                  <td
                    className={`ls-right${price != null && q && price < q.targetUnitPrice ? " so-warn" : ""}`}
                  >
                    {won(q?.targetUnitPrice)}
                  </td>
                  <td
                    className={`ls-right${rate != null && approval != null && rate < approval ? " so-bad" : ""}`}
                  >
                    {formatPercent(rate)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="ls-note">
        최소 단가 = 1인 월 원가(손해 안 보는 단가) · 목표 단가 = 이익률{" "}
        {target != null ? `${plainNumber(target)}%` : "목표"}가 되는 단가. 1인
        월 원가 = 같은 직무·등급 인력의 평균 원가(급여 + 수당 + 4대보험 +
        퇴직금 + 팀비·경비). 판관비는 넣지 않았으므로 목표 이익률{" "}
        {target != null ? `${plainNumber(target)}% ` : ""}안에서 감당합니다.
      </p>
    </div>
  );
}

export interface SalesOpportunityFormProps {
  initial: SalesOpportunityInput;
  opportunity?: SalesOpportunity;
  options: SalesOpportunityOptions;
  /** 저장 뒤: 최신 기회, 강조 시간(6초), 띄울 알림(시안 문구). */
  onSaved: (
    row: SalesOpportunity,
    highlightMilliseconds: number,
    notices?: SalesNotice[],
  ) => void;
  onCancel: () => void;
  /** 폼의 [삭제](사용 안 함) 뒤. 없으면 onCancel을 부른다. */
  onDeleted?: (row: SalesOpportunity, notices: SalesNotice[]) => void;
  /** 단계별 기본 확률. 없으면 파이프라인 조회에서 읽는다. */
  stageProbabilities?: Record<string, number>;
}
const blankNeed = (
  startMonth: string,
  months: number,
  gradeCode: string | null,
): SalesNeed => ({
  jobCode: "",
  gradeCode,
  requiredCount: 1,
  startMonth,
  months,
  monthlyMm: 1,
  estimatedUnitPrice: null,
  tentativeDeveloperIds: [],
});
const thisMonth = () => {
  const m = koreanThisMonth();
  return `${m.slice(0, 4)}-${m.slice(4)}`;
};
const numberValue = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value) ? "" : String(value);

export function SalesOpportunityForm({
  initial,
  opportunity,
  options,
  onSaved,
  onCancel,
  onDeleted,
  stageProbabilities,
}: SalesOpportunityFormProps) {
  const edit = !!opportunity;
  const middleGrade =
    options.codes.GRADE?.find((c) => c.codeName === "중급")?.code ?? null;
  const [draft, setDraft] = useState<SalesOpportunityInput>(() => {
    const value = structuredClone(initial);
    if (!opportunity) {
      // 시안 oppNew: 발굴 단계, 시작 예정월 = 두 달 뒤, 6개월, 빈 줄 하나
      if (!value.stageCode)
        value.stageCode = options.codes.SALES_STEP?.[0]?.code ?? "";
      if (!value.startMonth) value.startMonth = addMonths(thisMonth(), 2);
      if (value.expectedMonths == null) value.expectedMonths = 6;
      if (!value.needs.length)
        value.needs = [
          blankNeed(value.startMonth, value.expectedMonths || 6, middleGrade),
        ];
    }
    return value;
  });
  const [defaults, setDefaults] = useState<Record<string, number>>(
    stageProbabilities ?? {},
  );
  const [version, setVersion] = useState(opportunity?.modifiedAt);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<SalesNotice | null>(null);
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [quote, setQuote] = useState<SalesQuote | null>(null);
  const [quoteError, setQuoteError] = useState("");
  const [warnings, setWarnings] = useState<SalesTentativeWarning[]>([]);
  const form = useRef<HTMLFormElement>(null);
  // 저장 오류 뒤 첫 오류 칸으로(공통 focusFirstError). 오류 칸·busy 해제가 그려진 뒤에 옮긴다.
  const [focusRequest, setFocusRequest] = useState(0);
  useEffect(() => {
    if (!focusRequest || focusFirstError(form.current)) return;
    // 필요 인력이 한 줄도 없으면(needs) 칸이 없으니 [줄 추가]로 옮긴다.
    form.current
      ?.querySelector<HTMLElement>('[data-testid="sales-opportunity-add-need"]')
      ?.focus();
  }, [focusRequest]);
  const quoteSequence = useRef(0);
  // 사용자가 이 폼에서 확률을 직접 고쳤는지(고쳤으면 단계를 바꿔도 유지, SFR-129-07)
  const probabilityTouched = useRef(false);
  const readOnly = !!opportunity && opportunity.opportunityStatusCode !== "ING";
  // 시안 oppFormView: 제목줄이 [‹ 목록] + 「영업 기회 등록」·「영업 기회 수정」으로 바뀌고 작업 단추는 숨는다.
  // [‹ 목록]은 [취소]와 같다(고친 내용이 있으면 「저장하지 않은 내용」 확인, 시안 oppBack).
  const leave = () => (dirty && !readOnly ? setLeaving(true) : onCancel());
  useScreenTitleOverride("UI-29", edit ? "영업 기회 수정" : "영업 기회 등록", leave);
  const setScreenDirty = useScreenDirty();
  useEffect(() => {
    setScreenDirty("UI-29", dirty);
    return () => setScreenDirty("UI-29", false);
  }, [dirty, setScreenDirty]);
  useEffect(() => {
    if (stageProbabilities) {
      setDefaults(stageProbabilities);
      return;
    }
    let active = true;
    getSalesStageDefaults()
      .then((value) => {
        if (active) setDefaults(value);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [stageProbabilities]);
  // 새 기회는 0%로 시작하지 않는다: 기본 확률을 읽으면 그 단계 값으로 채운다.
  useEffect(() => {
    if (edit || probabilityTouched.current) return;
    const value = defaults[draft.stageCode];
    if (value != null && draft.winningProbability !== value)
      setDraft((d) => ({ ...d, winningProbability: value }));
  }, [defaults]);
  const clearError = (prefix: string) =>
    setErrors((e) =>
      Object.fromEntries(
        Object.entries(e).filter(
          ([field]) => field !== prefix && !field.startsWith(`${prefix}.`),
        ),
      ),
    );
  const change = (key: keyof SalesOpportunityInput, value: unknown) => {
    setDirty(true);
    setDraft((d) => {
      const next = { ...d, [key]: value };
      if (key === "stageCode" && !probabilityTouched.current) {
        const before = defaults[d.stageCode];
        const after = defaults[String(value)];
        const follows =
          !edit || before == null || d.winningProbability === before;
        if (follows && after != null) next.winningProbability = after;
      }
      return next;
    });
    clearError(key);
    if (key === "stageCode") clearError("winningProbability");
  };
  const needChange = (index: number, key: keyof SalesNeed, value: unknown) => {
    setDirty(true);
    setDraft((d) => ({
      ...d,
      needs: d.needs.map((n, i) => (i === index ? { ...n, [key]: value } : n)),
    }));
    clearError(`needs[${index}]`);
  };
  // 견적 이익 점검(SFR-129-04): 필요 인력 표가 바뀌면 바로 다시 계산
  useEffect(() => {
    const sequence = ++quoteSequence.current;
    setQuoteError("");
    if (readOnly || !quotableNeeds(draft.needs).length) {
      setQuote(null);
      return;
    }
    const timer = window.setTimeout(() => {
      quoteNeeds(draft.needs)
        .then((value) => {
          if (sequence === quoteSequence.current) setQuote(value);
        })
        .catch((error) => {
          if (sequence === quoteSequence.current) {
            setQuote(null);
            setQuoteError(
              error instanceof Error
                ? error.message
                : "견적을 계산하지 못했습니다.",
            );
          }
        });
    }, 200);
    return () => window.clearTimeout(timer);
  }, [draft.needs, readOnly]);
  // 가배정 경고(SFR-129-08): 새 기회·새 줄도 서버에 묻는다(다른 기회 가배정, 그 달 1.0 MM 이상).
  const tentativeLines = useMemo(
    () =>
      draft.needs.flatMap((need, rowIndex) =>
        need.tentativeDeveloperIds[0] && /^\d{4}-\d{2}$/.test(need.startMonth)
          ? [
              {
                rowIndex,
                needSn: need.needSn,
                developerId: need.tentativeDeveloperIds[0],
                startMonth: need.startMonth,
              },
            ]
          : [],
      ),
    [draft.needs],
  );
  const tentativeKey = JSON.stringify(tentativeLines);
  useEffect(() => {
    let active = true;
    if (readOnly || !tentativeLines.length) {
      setWarnings([]);
      return;
    }
    const timer = window.setTimeout(() => {
      checkSalesTentativeWarnings(opportunity?.opportunityId, tentativeLines)
        .then((value) => {
          if (active) setWarnings(value);
        })
        .catch(() => {
          // 경고 확인은 저장을 막지 않는다(실패하면 서버 경고만 비운다).
          if (active) setWarnings([]);
        });
    }, 200);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [tentativeKey, opportunity?.opportunityId, readOnly]);
  function report(error: unknown) {
    const message =
      error instanceof Error
        ? error.message
        : "처리하지 못했습니다. 다시 시도해 주세요.";
    setToast({ kind: "warn", message });
    if (error instanceof SalesOpportunityError) {
      const fields = Object.fromEntries(
        error.errors.map((e) => [e.field, e.message]),
      );
      setErrors(fields);
      // 409라도 줄 칸 오류(투입 배정으로 넘긴 후보)는 그 줄 아래에만 보이고, 「먼저 바꿨습니다」 띠는 수정 시각 충돌에만 띄운다.
      if (
        error.status === 409 &&
        !error.errors.some((e) => e.field?.startsWith("needs["))
      )
        setConflict(true);
      if (Object.keys(fields).length) setFocusRequest((n) => n + 1);
    }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy || readOnly) return;
    const validation = salesOpportunityErrors(draft);
    setErrors(validation);
    if (Object.keys(validation).length) {
      setFocusRequest((n) => n + 1);
      return;
    }
    setBusy(true);
    try {
      const input = salesOpportunityPayload(draft);
      const saved =
        opportunity && version
          ? await putCommonSalesOpportunity(
              opportunity.opportunityId,
              input,
              version,
            )
          : await postCommonSalesOpportunity(input);
      // 저장 응답이 최신 기회다. 따로 다시 읽지 않아, 다시 읽기가 실패해도 저장을 실패로 알리지 않는다.
      const id = saved?.opportunityId ?? opportunity?.opportunityId;
      const latest =
        (await writtenOpportunity(saved, id)) ??
        ({ ...opportunity, ...input, opportunityId: id ?? "" } as SalesOpportunity);
      const notices: SalesNotice[] = [
        {
          kind: "ok",
          message: edit
            ? "저장했습니다."
            : `「${input.opportunityName}」 기회를 등록했습니다.`,
        },
      ];
      if (
        latest.approvalStatusCode === "WAIT" &&
        opportunity?.approvalStatusCode !== "WAIT"
      )
        notices.push({
          kind: "info",
          message: "예상 이익률이 기준보다 낮아 견적 승인 대기로 올렸습니다.",
        });
      setDirty(false);
      onSaved(latest, 6000, notices);
    } catch (error) {
      report(error);
    } finally {
      setBusy(false);
    }
  }
  async function reload() {
    if (!opportunity) return;
    try {
      const latest = await getCommonSalesOpportunity(opportunity.opportunityId);
      if (latest.opportunityStatusCode !== "ING") {
        setToast({
          kind: "warn",
          message: "종료된 기회입니다. 상세에서 다시 확인해 주세요.",
        });
        return;
      }
      setVersion(latest.modifiedAt);
      setConflict(false);
      setToast({
        kind: "info",
        message: "최신 버전을 불러왔습니다. 입력을 확인한 뒤 저장해 주세요.",
      });
    } catch (error) {
      report(error);
    }
  }
  async function remove() {
    if (!opportunity || !version) return;
    setDeleting(false);
    setBusy(true);
    try {
      const stopped = await salesOpportunityAction(
        { ...opportunity, modifiedAt: version },
        "stop",
      );
      const latest =
        (await writtenOpportunity(stopped, opportunity.opportunityId)) ??
        opportunity;
      const notices: SalesNotice[] = [
        {
          kind: "ok",
          message:
            "삭제했습니다. 종료 탭의 [사용 중지 포함]에서 다시 쓸 수 있습니다.",
        },
      ];
      setDirty(false);
      if (onDeleted) onDeleted(latest, notices);
      else onCancel();
    } catch (error) {
      report(error);
    } finally {
      setBusy(false);
    }
  }
  const testKey = (key: string) =>
    key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
  const fld = (
    key: string,
    label: string,
    control: ReactNode,
    opt: { required?: boolean; help?: string; full?: boolean } = {},
  ) => (
    <div className={`so-fld${opt.full ? " so-full" : ""}`} key={key}>
      <label
        className={`so-fl${opt.required ? " so-req" : ""}`}
        htmlFor={`so-${key}`}
      >
        {label}
      </label>
      <div className="so-fc">
        {control}
        {opt.help && <span className="so-help">{opt.help}</span>}
        {errors[key] && (
          <span className="so-errmsg" role="alert">
            {errors[key]}
          </span>
        )}
      </div>
    </div>
  );
  const text = (
    key: "opportunityName" | "workplace" | "nextAction",
    maxLength: number,
    placeholder?: string,
  ) => (
    <input
      id={`so-${key}`}
      className={`so-in so-wide${errors[key] ? " so-err" : ""}`}
      data-testid={`sales-opportunity-${testKey(key)}`}
      name={key}
      maxLength={maxLength}
      placeholder={placeholder}
      value={draft[key] ?? ""}
      aria-invalid={!!errors[key]}
      onChange={(e) => change(key, e.target.value)}
    />
  );
  const select = (
    key: keyof SalesOpportunityInput,
    items: { value: string; label: string }[],
    width = 240,
  ) => (
    <select
      id={`so-${key}`}
      className={`so-in${errors[key] ? " so-err" : ""}`}
      style={{ width }}
      data-testid={`sales-opportunity-select-${testKey(key)}`}
      name={key}
      value={String(draft[key] ?? "")}
      aria-invalid={!!errors[key]}
      onChange={(e) => change(key, e.target.value)}
    >
      <option value="">선택</option>
      {items.map((i) => (
        <option key={i.value} value={i.value}>
          {i.label}
        </option>
      ))}
    </select>
  );
  const totals = useMemo(() => {
    const n = draft.needs.reduce((s, r) => s + (r.requiredCount || 0), 0);
    const mm = draft.needs.reduce(
      (s, r) => s + (r.requiredCount || 0) * (r.monthlyMm || 0) * (r.months || 0),
      0,
    );
    const lab = draft.needs.reduce(
      (s, r) =>
        s +
        (r.requiredCount || 0) *
          (r.monthlyMm || 0) *
          (r.months || 0) *
          (r.estimatedUnitPrice || 0),
      0,
    );
    return { n, mm, lab };
  }, [draft.needs]);
  const extras = draft.needs.reduce(
    (s, r) => s + Math.max(0, r.tentativeDeveloperIds.length - 1),
    0,
  );
  const prob = Number.isFinite(draft.winningProbability)
    ? draft.winningProbability
    : 0;
  const jobs = options.codes.JOB || [];
  const tentativeWarning = (need: SalesNeed, index: number) => {
    const id = need.tentativeDeveloperIds[0];
    if (!id) return "";
    if (
      draft.needs.some((n, i) => i !== index && n.tentativeDeveloperIds[0] === id)
    )
      return "같은 사람을 두 줄에 가배정했습니다";
    return (
      warnings.find((w) => w.rowIndex === index && w.developerId === id)
        ?.message || ""
    );
  };
  const candidates = (need: SalesNeed) =>
    options.developers
      .filter(
        (d) =>
          d.workStatusName !== "휴직" &&
          (!need.jobCode ||
            !d.primaryJobCode ||
            d.primaryJobCode === need.jobCode ||
            need.tentativeDeveloperIds.includes(d.developerId)),
      )
      .sort(
        (a, b) =>
          (a.workStatusName?.includes("대기") ? 0 : 1) -
            (b.workStatusName?.includes("대기") ? 0 : 1) ||
          (a.withdrawalDate || "").localeCompare(b.withdrawalDate || ""),
      );
  const stageItems = (options.codes.SALES_STEP || []).map((c) => ({
    value: c.code,
    label:
      defaults[c.code] != null
        ? `${c.codeName} (기본 ${plainNumber(defaults[c.code])}%)`
        : c.codeName,
  }));
  const approvalRate = quote?.approvalRate;
  return (
    <form
      ref={form}
      className="sales-opportunity-form"
      data-testid="sales-opportunity-form"
      data-screen-id="UI-29"
      noValidate
      onSubmit={save}
    >
      {edit && !readOnly && (
        <Banner kind="info" icon={salesIcon("info")}>
          마지막 수정 {formatDate(version)}. 단계를 바꾸면 수주 확률이 그 단계
          기본값으로 바뀝니다(직접 고칠 수 있음).
        </Banner>
      )}
      {conflict && (
        <Banner
          kind="warn"
          icon={salesIcon("warn")}
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="sales-opportunity-reload"
              onClick={reload}
            >
              최신 버전 불러오기 (입력 유지)
            </button>
          }
        >
          다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.
        </Banner>
      )}
      {readOnly && (
        <Banner kind="lock" icon={salesIcon("lock")}>
          종료된 기회는 수정할 수 없습니다.
        </Banner>
      )}
      <fieldset className="so-fieldset" disabled={busy || readOnly}>
        <section className="so-card">
          <h3>기본 정보</h3>
          <div className="so-fgrid">
            {fld("opportunityName", "기회명", text("opportunityName", 100), {
              required: true,
              full: true,
            })}
            {fld(
              "customerId",
              "고객사",
              select(
                "customerId",
                options.customers.map((c) => ({
                  value: c.clientId,
                  label: c.clientName,
                })),
              ),
              {
                required: true,
                help: "목록에 없으면 거래처·영업 파트 화면에서 먼저 등록합니다",
              },
            )}
            {fld(
              "salesPartId",
              "영업 파트",
              select(
                "salesPartId",
                options.parts.map((p) => ({
                  value: p.partId,
                  label: p.partName,
                })),
              ),
              { required: true },
            )}
            {fld("stageCode", "단계", select("stageCode", stageItems), {
              required: true,
            })}
            {fld(
              "winningProbability",
              "수주 확률",
              <div className="so-row">
                <input
                  id="so-winningProbability"
                  className={`so-in so-num${errors.winningProbability ? " so-err" : ""}`}
                  style={{ width: 90 }}
                  data-testid="sales-opportunity-winning-probability"
                  name="winningProbability"
                  inputMode="decimal"
                  value={numberValue(draft.winningProbability)}
                  aria-invalid={!!errors.winningProbability}
                  onChange={(e) => {
                    probabilityTouched.current = true;
                    const v = e.target.value.trim();
                    change("winningProbability", v === "" ? NaN : Number(v));
                  }}
                />
                <span className="so-sub">%</span>
              </div>,
              { required: true, help: "수요 예측과 확률 반영 금액에 쓰입니다" },
            )}
            {fld(
              "expectedAmount",
              "예상 금액",
              <div className="so-row">
                <input
                  id="so-expectedAmount"
                  className={`so-in so-num${errors.expectedAmount ? " so-err" : ""}`}
                  style={{ width: 180 }}
                  data-testid="sales-opportunity-expected-amount"
                  name="expectedAmount"
                  inputMode="numeric"
                  maxLength={19}
                  value={
                    draft.expectedAmount
                      ? formatMoney(draft.expectedAmount, "원")
                      : ""
                  }
                  aria-invalid={!!errors.expectedAmount}
                  onChange={(e) =>
                    change("expectedAmount", numberOrNull(e.target.value) ?? 0)
                  }
                />
                <span className="so-sub">원</span>
              </div>,
              { required: true, help: "부가세 제외" },
            )}
            {fld(
              "contractTypeCode",
              "계약유형",
              select(
                "contractTypeCode",
                (options.codes.CTRT_TYPE || [])
                  .filter((c) => SALES_CONTRACT_TYPES.includes(c.code))
                  .map((c) => ({ value: c.code, label: c.codeName })),
                160,
              ),
            )}
            {fld(
              "startMonth",
              "시작 예정월",
              <input
                id="so-startMonth"
                className={`so-in${errors.startMonth ? " so-err" : ""}`}
                style={{ width: 180 }}
                type="month"
                data-testid="sales-opportunity-start-month"
                name="startMonth"
                value={draft.startMonth}
                aria-invalid={!!errors.startMonth}
                onChange={(e) => change("startMonth", e.target.value)}
              />,
              { required: true },
            )}
            {fld(
              "expectedMonths",
              "예상 기간",
              <div className="so-row">
                <input
                  id="so-expectedMonths"
                  className={`so-in so-num${errors.expectedMonths ? " so-err" : ""}`}
                  style={{ width: 90 }}
                  data-testid="sales-opportunity-expected-months"
                  name="expectedMonths"
                  inputMode="numeric"
                  maxLength={3}
                  value={numberValue(draft.expectedMonths)}
                  aria-invalid={!!errors.expectedMonths}
                  onChange={(e) => change("expectedMonths", numberOrNull(e.target.value))}
                />
                <span className="so-sub">개월</span>
              </div>,
            )}
            {fld("workplace", "근무지", text("workplace", 200), {
              full: true,
              help: "근무지 주소 전체를 적습니다(예: 서울 중구 을지로 66). 맨 앞 시·도로 AI 인력 추천의 근무지 점수를 매기고, 수주 처리하면 프로젝트 근무지로 넘어갑니다.",
            })}
          </div>
        </section>
        <section className="so-card">
          <h3>다음 할 일</h3>
          <div className="so-fgrid">
            {fld(
              "nextAction",
              "할 일",
              text("nextAction", 200, "예: 제안서 제출, 고객 발표"),
            )}
            {fld(
              "nextActionDueDate",
              "기한",
              <input
                id="so-nextActionDueDate"
                className="so-in"
                style={{ width: 160 }}
                type="date"
                data-testid="sales-opportunity-next-action-due-date"
                name="nextActionDueDate"
                value={draft.nextActionDueDate ?? ""}
                aria-invalid={!!errors.nextActionDueDate}
                onChange={(e) =>
                  change("nextActionDueDate", e.target.value || null)
                }
              />,
              { help: "기한이 지나면 카드에 빨간 글자로 표시됩니다" },
            )}
          </div>
        </section>
        <section className="so-card">
          <div className="so-card-h">
            <h3>필요 인력</h3>
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="sales-opportunity-add-need"
              onClick={() => {
                setDirty(true);
                clearError("needs");
                setDraft((d) => ({
                  ...d,
                  needs: [
                    ...d.needs,
                    blankNeed(
                      d.startMonth || addMonths(thisMonth(), 2),
                      d.expectedMonths || 6,
                      middleGrade,
                    ),
                  ],
                }));
              }}
            >
              {salesIcon("plus")} 행 추가
            </button>
          </div>
          {errors.needs && (
            <span className="so-errmsg" role="alert">
              {errors.needs}
            </span>
          )}
          <div className="ls-twrap so-twrap">
            <table className="ls-t so-needs">
              <thead>
                <tr>
                  <th>
                    직무 <span className="so-bad">*</span>
                  </th>
                  <th>등급</th>
                  <th className="ls-right">
                    인원 <span className="so-bad">*</span>
                  </th>
                  <th>
                    시작월 <span className="so-bad">*</span>
                  </th>
                  <th className="ls-right">
                    개월 <span className="so-bad">*</span>
                  </th>
                  <th className="ls-right">
                    월 MM <span className="so-bad">*</span>
                  </th>
                  <th className="ls-right">예상 단가(원)</th>
                  <th>가배정</th>
                  <th className="ls-center">삭제</th>
                </tr>
              </thead>
              <tbody>
                {draft.needs.map((need, index) => {
                  const rowError = Object.entries(errors)
                    .filter(
                      ([k]) =>
                        k === `needs[${index}]` ||
                        k.startsWith(`needs[${index}].`),
                    )
                    .map(([, v]) => v);
                  const bad = rowError.length ? salesNeedBadFields(need) : [];
                  const invalid = (key: keyof SalesNeed) =>
                    bad.includes(key) || !!errors[`needs[${index}].${key}`];
                  const warning = tentativeWarning(need, index);
                  const numberInput = (
                    key: "requiredCount" | "months" | "monthlyMm",
                    width: number,
                    label: string,
                  ) => (
                    <input
                      className={`so-in so-in-sm so-num${invalid(key) ? " so-err" : ""}`}
                      style={{ width }}
                      type="number"
                      step={key === "monthlyMm" ? "0.01" : "1"}
                      data-testid={`sales-need-${index}-${key.toLowerCase()}`}
                      name={`needs[${index}].${key}`}
                      aria-label={`${index + 1}번째 줄 ${label}`}
                      aria-invalid={invalid(key)}
                      value={numberValue(need[key])}
                      onChange={(e) =>
                        needChange(
                          index,
                          key,
                          e.target.value === "" ? NaN : Number(e.target.value),
                        )
                      }
                    />
                  );
                  const jobPrice = jobs.find(
                    (c) => c.code === need.jobCode,
                  )?.estimateUnitPrice;
                  return [
                    <tr
                      key={need.needSn ?? `new-${index}`}
                      data-testid={`sales-opportunity-need-${index}`}
                    >
                      <td>
                        <select
                          className={`so-in so-in-sm${invalid("jobCode") ? " so-err" : ""}`}
                          data-testid={`sales-need-${index}-jobcode`}
                          name={`needs[${index}].jobCode`}
                          aria-label={`${index + 1}번째 줄 직무`}
                          aria-invalid={invalid("jobCode")}
                          value={need.jobCode || ""}
                          onChange={(e) =>
                            needChange(index, "jobCode", e.target.value)
                          }
                        >
                          <option value="">선택</option>
                          {jobs.map((c) => (
                            <option key={c.code} value={c.code}>
                              {c.codeName}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <select
                          className="so-in so-in-sm"
                          data-testid={`sales-need-${index}-gradecode`}
                          name={`needs[${index}].gradeCode`}
                          aria-label={`${index + 1}번째 줄 등급`}
                          value={need.gradeCode || ""}
                          onChange={(e) =>
                            needChange(index, "gradeCode", e.target.value || null)
                          }
                        >
                          {!need.gradeCode && <option value="">선택</option>}
                          {(options.codes.GRADE || []).map((c) => (
                            <option key={c.code} value={c.code}>
                              {c.codeName}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="ls-right">
                        {numberInput("requiredCount", 52, "인원")}
                      </td>
                      <td>
                        <input
                          className={`so-in so-in-sm${invalid("startMonth") ? " so-err" : ""}`}
                          type="month"
                          data-testid={`sales-need-${index}-startmonth`}
                          name={`needs[${index}].startMonth`}
                          aria-label={`${index + 1}번째 줄 시작월`}
                          aria-invalid={invalid("startMonth")}
                          value={need.startMonth || ""}
                          onChange={(e) =>
                            needChange(index, "startMonth", e.target.value)
                          }
                        />
                      </td>
                      <td className="ls-right">
                        {numberInput("months", 52, "개월")}
                      </td>
                      <td className="ls-right">
                        {numberInput("monthlyMm", 60, "월 MM")}
                      </td>
                      <td className="ls-right">
                        <input
                          className={`so-in so-in-sm so-num${invalid("estimatedUnitPrice") ? " so-err" : ""}`}
                          style={{ width: 110 }}
                          inputMode="numeric"
                          maxLength={19}
                          data-testid={`sales-need-${index}-estimatedunitprice`}
                          name={`needs[${index}].estimatedUnitPrice`}
                          aria-label={`${index + 1}번째 줄 예상 단가`}
                          aria-invalid={invalid("estimatedUnitPrice")}
                          placeholder={
                            jobPrice ? formatMoney(jobPrice, "원") : ""
                          }
                          value={
                            need.estimatedUnitPrice
                              ? formatMoney(need.estimatedUnitPrice, "원")
                              : ""
                          }
                          onChange={(e) =>
                            needChange(
                              index,
                              "estimatedUnitPrice",
                              numberOrNull(e.target.value),
                            )
                          }
                        />
                      </td>
                      <td>
                        <select
                          className={`so-in so-in-sm${invalid("tentativeDeveloperIds") ? " so-err" : ""}`}
                          style={{ width: 190 }}
                          data-testid={`sales-need-${index}-developers`}
                          name={`needs[${index}].tentativeDeveloperIds`}
                          aria-label={`${index + 1}번째 줄 가배정`}
                          aria-invalid={invalid("tentativeDeveloperIds")}
                          value={need.tentativeDeveloperIds[0] || ""}
                          onChange={(e) =>
                            needChange(
                              index,
                              "tentativeDeveloperIds",
                              [
                                e.target.value,
                                ...need.tentativeDeveloperIds.slice(1),
                              ].filter(Boolean),
                            )
                          }
                        >
                          <option value="">없음</option>
                          {candidates(need).map((d) => (
                            <option key={d.developerId} value={d.developerId}>
                              {d.name}
                              {need.jobCode &&
                              d.primaryJobCode &&
                              d.primaryJobCode !== need.jobCode
                                ? ` (${d.primaryJobName || codeName(options, "JOB", d.primaryJobCode)})`
                                : ""}
                              {d.workStatusName?.includes("대기")
                                ? " · 대기 중"
                                : d.withdrawalDate
                                  ? ` · ~${d.withdrawalDate.slice(0, 7)}`
                                  : ""}
                            </option>
                          ))}
                        </select>
                        {warning && (
                          <div
                            className="so-warn so-tent-warn"
                            role="status"
                            data-testid={`sales-need-${index}-warning`}
                          >
                            {salesIcon("warn", 12)} {warning}
                          </div>
                        )}
                      </td>
                      <td className="ls-center">
                        <button
                          type="button"
                          className="ls-ibtn ls-ibtn-sm"
                          data-testid={`sales-need-${index}-remove`}
                          aria-label={`${index + 1}번째 줄 삭제`}
                          disabled={draft.needs.length < 2}
                          onClick={() => {
                            setDirty(true);
                            setErrors({});
                            setDraft((d) => ({
                              ...d,
                              needs: d.needs.filter((_, i) => i !== index),
                            }));
                          }}
                        >
                          {salesIcon("x")}
                        </button>
                      </td>
                    </tr>,
                    rowError.length ? (
                      <tr key={`error-${index}`}>
                        <td colSpan={9}>
                          {rowError.map((message) => (
                            <span
                              key={message}
                              className="so-errmsg"
                              role="alert"
                            >
                              {message}
                            </span>
                          ))}
                        </td>
                      </tr>
                    ) : null,
                  ];
                })}
              </tbody>
              <tfoot className="ls-foot">
                <tr>
                  <td>합계</td>
                  <td />
                  <td className="ls-right" data-testid="sales-need-total-count">
                    {totals.n}명
                  </td>
                  <td />
                  <td />
                  <td className="ls-right">{formatMm(totals.mm)} MM</td>
                  <td className="ls-right">{won(totals.lab)}</td>
                  <td colSpan={2} />
                </tr>
              </tfoot>
            </table>
          </div>
          <p className="ls-note">
            예상 인건비 매출 {formatKoreanMoney(totals.lab)} · 수주 확률 반영{" "}
            {formatKoreanMoney((totals.lab * prob) / 100)}. 단가를 비우면 직무
            기본 단가를 씁니다. 가배정한 사람은 인력 운영 보드에 점선으로
            표시됩니다.
            {extras > 0 &&
              ` 이 표에 없는 가배정 ${extras}명은 상세 창 '가배정 인력'에 그대로 남습니다.`}
          </p>
        </section>
        <section className="so-card" data-testid="sales-quote-card">
          <div className="so-card-h">
            <h3>견적 이익 점검</h3>
            <span className="so-cap">
              {quote?.targetRate != null &&
                `목표 이익률 ${plainNumber(quote.targetRate)}% · `}
              {approvalRate != null &&
                `${plainNumber(approvalRate)}% 미만이면 승인 필요`}
            </span>
          </div>
          <SalesQuoteView
            quote={quote}
            needs={draft.needs}
            options={options}
            error={quoteError}
            loading={!readOnly}
          />
        </section>
        <section className="so-card">
          <h3>비고</h3>
          <textarea
            id="so-memo"
            className={`so-in so-textarea${errors.memo ? " so-err" : ""}`}
            data-testid="sales-opportunity-memo"
            name="memo"
            maxLength={500}
            aria-label="비고"
            aria-invalid={!!errors.memo}
            value={draft.memo ?? ""}
            onChange={(e) => change("memo", e.target.value)}
          />
          {errors.memo && (
            <span className="so-errmsg" role="alert">
              {errors.memo}
            </span>
          )}
        </section>
      </fieldset>
      <div className="so-fbar">
        {edit && !readOnly && (
          <button
            type="button"
            className="ls-btn ls-btn-dt"
            data-testid="sales-opportunity-delete"
            disabled={busy}
            onClick={() => setDeleting(true)}
          >
            삭제
          </button>
        )}
        <div className="so-fbar-r">
          <button
            type="button"
            className="ls-btn"
            data-testid="sales-opportunity-cancel"
            onClick={leave}
          >
            취소
          </button>
          <button
            type="submit"
            className="ls-btn ls-btn-p"
            data-testid="sales-opportunity-save"
            disabled={busy || readOnly}
          >
            저장
          </button>
        </div>
      </div>
      {leaving && (
        <InlineConfirmation
          title="저장하지 않은 내용이 있습니다"
          description="나가면 입력한 내용이 사라집니다."
          proceedLabel="나가기"
          testId="sales-opportunity-leave"
          onCancel={() => setLeaving(false)}
          onProceed={() => {
            setLeaving(false);
            onCancel();
          }}
        />
      )}
      {deleting && opportunity && (
        <ConfirmDialog
          title={`「${draft.opportunityName || opportunity.opportunityName}」을 삭제할까요?`}
          description="가배정한 인력도 함께 풀립니다. 목록에서는 사라지고 종료 탭의 [사용 중지 포함]에서 다시 쓸 수 있습니다(가배정은 돌아오지 않음)."
          proceedLabel="삭제"
          destructive
          testId="sales-opportunity-delete-confirm"
          onCancel={() => setDeleting(false)}
          onProceed={remove}
        />
      )}
      {toast && (
        <Toast
          kind={toast.kind}
          message={toast.message}
          testId="sales-opportunity-toast"
          onDismiss={() => setToast(null)}
        />
      )}
    </form>
  );
}

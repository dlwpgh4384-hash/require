import { useEffect, useRef, useState, type FormEvent } from "react";
import "./salesOpportunities.css";
import {
  getCommonSalesOpportunity,
  postCommonSalesOpportunity,
  putCommonSalesOpportunity,
  quoteCommonSalesOpportunity,
  salesOpportunityAction,
  salesOpportunityErrors,
  salesOpportunityPayload,
  SalesOpportunityError,
  type SalesOpportunity,
  type SalesOpportunityInput,
  type SalesOpportunityOptions,
  type SalesQuote,
  type SalesNeed,
} from "./salesOpportunities";

export interface SalesOpportunityFormProps {
  initial: SalesOpportunityInput;
  opportunity?: SalesOpportunity;
  options: SalesOpportunityOptions;
  onSaved: (row: SalesOpportunity, highlightMilliseconds: number) => void;
  onCancel: () => void;
}
export function SalesOpportunityForm({
  initial,
  opportunity,
  options,
  onSaved,
  onCancel,
}: SalesOpportunityFormProps) {
  const [draft, setDraft] = useState(() => structuredClone(initial));
  const [version, setVersion] = useState(opportunity?.modifiedAt);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState("");
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState(false);
  const [quote, setQuote] = useState<SalesQuote | null>(null);
  const [warnings, setWarnings] = useState<
    { needSn: number; developerId: string; message: string }[]
  >([]);
  const form = useRef<HTMLFormElement>(null);
  const quoteSequence = useRef(0);
  const readOnly = !!opportunity && opportunity.opportunityStatusCode !== "ING";
  const change = (key: keyof SalesOpportunityInput, value: unknown) => {
    setDraft((d) => ({ ...d, [key]: value }));
    setErrors((e) =>
      Object.fromEntries(Object.entries(e).filter(([field]) => field !== key)),
    );
    setQuote(null);
  };
  const needChange = (index: number, key: keyof SalesNeed, value: unknown) => {
    change(
      "needs",
      draft.needs.map((n, i) => (i === index ? { ...n, [key]: value } : n)),
    );
    setErrors((e) =>
      Object.fromEntries(
        Object.entries(e).filter(
          ([field]) => field !== `needs[${index}].${key}`,
        ),
      ),
    );
  };
  useEffect(() => {
    const sequence = ++quoteSequence.current;
    setQuote(null);
    const validation = salesOpportunityErrors(draft);
    if (
      readOnly ||
      Object.keys(validation).some((key) => key.startsWith("needs"))
    )
      return;
    quoteCommonSalesOpportunity(draft.needs)
      .then((value) => {
        if (sequence === quoteSequence.current) setQuote(value);
      })
      .catch((error) => {
        if (sequence === quoteSequence.current) report(error);
      });
    return () => {
      quoteSequence.current++;
    };
  }, [draft.needs]);
  useEffect(() => {
    let active = true;
    setWarnings([]);
    if (!opportunity || !version) return;
    const checks = draft.needs.flatMap((need) =>
      !need.needSn || !/^\d{4}-\d{2}$/.test(need.startMonth)
        ? []
        : need.tentativeDeveloperIds.map((developerId) =>
            salesOpportunityAction<{ warnings: typeof warnings }>(
              { ...opportunity, modifiedAt: version },
              "tentative-assignment-check",
              {
                developerId,
                needSn: need.needSn,
                startMonth: need.startMonth,
              },
            ),
          ),
    );
    Promise.all(checks)
      .then((results) => {
        if (active) setWarnings(results.flatMap((result) => result.warnings));
      })
      .catch((error) => {
        if (active) report(error);
      });
    return () => {
      active = false;
    };
  }, [draft.needs, opportunity, version]);
  async function calculate() {
    const validation = salesOpportunityErrors(draft);
    const needErrors = Object.fromEntries(
      Object.entries(validation).filter(([key]) => key.startsWith("needs")),
    );
    if (Object.keys(needErrors).length) {
      setErrors(needErrors);
      return;
    }
    const sequence = ++quoteSequence.current;
    try {
      const value = await quoteCommonSalesOpportunity(draft.needs);
      if (sequence === quoteSequence.current) {
        setQuote(value);
        setMessage("");
      }
    } catch (error) {
      if (sequence === quoteSequence.current) report(error);
    }
  }
  function report(error: unknown) {
    setMessage(
      error instanceof Error
        ? error.message
        : "처리하지 못했습니다. 다시 시도해 주세요.",
    );
    if (error instanceof SalesOpportunityError) {
      setErrors(
        Object.fromEntries(error.errors.map((e) => [e.field, e.message])),
      );
      if (error.status === 409) setConflict(true);
    }
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    const validation = salesOpportunityErrors(draft);
    setErrors(validation);
    if (Object.keys(validation).length) {
      const name = Object.keys(validation)[0];
      form.current?.querySelector<HTMLElement>(`[name="${name}"]`)?.focus();
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
      onSaved(await getCommonSalesOpportunity(saved.opportunityId), 6000);
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
        setMessage("종료된 기회입니다. 상세에서 다시 확인해 주세요.");
        return;
      }
      setVersion(latest.modifiedAt);
      setConflict(false);
      setMessage("최신 버전을 불러왔습니다. 입력을 확인한 뒤 저장해 주세요.");
    } catch (error) {
      report(error);
    }
  }
  const field = (
    key: keyof SalesOpportunityInput,
    label: string,
    type = "text",
    maxLength?: number,
  ) => (
    <label key={key}>
      {label}
      <input
        data-testid={`sales-opportunity-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
        name={key}
        type={type}
        maxLength={maxLength}
        value={String(draft[key] ?? "")}
        aria-invalid={!!errors[key]}
        onChange={(e) =>
          change(
            key,
            type === "number"
              ? e.target.value === ""
                ? null
                : Number(e.target.value)
              : e.target.value,
          )
        }
      />
      {errors[key] && <span role="alert">{errors[key]}</span>}
    </label>
  );
  const select = (
    key: keyof SalesOpportunityInput,
    label: string,
    items: { value: string; label: string }[],
  ) => (
    <label>
      {label}
      <select
        data-testid={`sales-opportunity-select-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
        name={key}
        value={String(draft[key] || "")}
        onChange={(e) => change(key, e.target.value)}
      >
        <option value="">선택</option>
        {items.map((i) => (
          <option key={i.value} value={i.value}>
            {i.label}
          </option>
        ))}
      </select>
      {errors[key] && <span role="alert">{errors[key]}</span>}
    </label>
  );
  return (
    <form
      ref={form}
      className="sales-opportunity-form"
      data-testid="sales-opportunity-form"
      data-screen-id="UI-29"
      noValidate
      onSubmit={save}
    >
      <h2>{opportunity ? "기회 수정" : "기회 등록"}</h2>
      {message && <p role="alert">{message}</p>}
      {conflict && (
        <button
          type="button"
          data-testid="sales-opportunity-reload"
          onClick={reload}
        >
          최신 버전 불러오기 (입력 유지)
        </button>
      )}
      <fieldset disabled={busy || readOnly}>
        {field("opportunityName", "기회명", "text", 100)}
        {select(
          "customerId",
          "고객",
          options.customers.map((c) => ({
            value: c.clientId,
            label: c.clientName,
          })),
        )}
        {select(
          "salesPartId",
          "영업 파트",
          options.parts.map((p) => ({ value: p.partId, label: p.partName })),
        )}
        {select(
          "stageCode",
          "단계",
          (options.codes.SALES_STEP || []).map((c) => ({
            value: c.code,
            label: c.codeName,
          })),
        )}
        {field("winningProbability", "확률 (%)", "number")}
        {field("expectedAmount", "예상 금액", "number")}
        {select(
          "contractTypeCode",
          "계약 유형",
          (options.codes.CTRT_TYPE || []).map((c) => ({
            value: c.code,
            label: c.codeName,
          })),
        )}
        {field("startMonth", "시작월", "month")}
        {field("expectedMonths", "기간 (빈칸은 6개월)", "number")}
        {field("workplace", "근무지", "text", 200)}
        {field("nextAction", "다음 할 일", "text", 200)}
        {field("nextActionDueDate", "다음 할 일 기한", "date")}
        {field("memo", "비고", "text", 500)}
        <h3>필요 인력</h3>
        {errors.needs && <p role="alert">{errors.needs}</p>}
        {draft.needs.map((need, index) => (
          <fieldset
            key={need.needSn ?? `new-${index}`}
            data-testid={`sales-opportunity-need-${index}`}
          >
            <legend>{index + 1}번째 줄</legend>
            {(
              [
                ["jobCode", "직무", "JOB"],
                ["gradeCode", "등급", "GRADE"],
              ] as const
            ).map(([key, label, group]) => (
              <label key={key}>
                {label}
                <select
                  data-testid={`sales-need-${index}-${key.toLowerCase()}`}
                  name={`needs[${index}].${key}`}
                  value={need[key] || ""}
                  onChange={(e) =>
                    needChange(index, key, e.target.value || null)
                  }
                >
                  <option value="">선택</option>
                  {(options.codes[group] || []).map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.codeName}
                    </option>
                  ))}
                </select>
              </label>
            ))}
            {(
              [
                ["requiredCount", "인원", "number"],
                ["startMonth", "시작월", "month"],
                ["months", "개월", "number"],
                ["monthlyMm", "월 MM", "number"],
                [
                  "estimatedUnitPrice",
                  "예상 단가 (빈칸은 직무 기준)",
                  "number",
                ],
              ] as const
            ).map(([key, label, type]) => (
              <label key={key}>
                {label}
                <input
                  data-testid={`sales-need-${index}-${key.toLowerCase()}`}
                  name={`needs[${index}].${key}`}
                  type={type}
                  step={key === "monthlyMm" ? "0.01" : undefined}
                  value={need[key] ?? ""}
                  onChange={(e) =>
                    needChange(
                      index,
                      key,
                      type === "number"
                        ? e.target.value === ""
                          ? null
                          : Number(e.target.value)
                        : e.target.value,
                    )
                  }
                />
              </label>
            ))}
            <label>
              가배정 인력
              <select
                multiple
                data-testid={`sales-need-${index}-developers`}
                value={need.tentativeDeveloperIds}
                onChange={(e) =>
                  needChange(
                    index,
                    "tentativeDeveloperIds",
                    Array.from(e.target.selectedOptions, (o) => o.value),
                  )
                }
              >
                {options.developers.map((d) => (
                  <option key={d.developerId} value={d.developerId}>
                    {d.name}
                  </option>
                ))}
              </select>
            </label>
            {need.tentativeDeveloperIds.some((id) =>
              draft.needs.some(
                (n, i) => i !== index && n.tentativeDeveloperIds.includes(id),
              ),
            ) && <p role="status">같은 사람이 두 줄에 있습니다.</p>}
            {warnings
              .filter((w) => w.needSn === need.needSn)
              .map((w, i) => (
                <p role="status" key={`${w.developerId}-${i}`}>
                  {w.message}
                </p>
              ))}
            {Object.entries(errors)
              .filter(([k]) => k.startsWith(`needs[${index}].`))
              .map(([k, value]) => (
                <p role="alert" key={k}>
                  {value}
                </p>
              ))}
            {quote?.needQuotes
              ?.filter((q) => q.rowIndex === index)
              .map((q) => (
                <p key={q.rowIndex}>
                  최소 단가 {q.minimumUnitPrice.toLocaleString()} · 목표 단가{" "}
                  {q.targetUnitPrice.toLocaleString()}
                </p>
              ))}
            <button
              data-testid={`sales-need-${index}-remove`}
              type="button"
              onClick={() =>
                change(
                  "needs",
                  draft.needs.filter((_, i) => i !== index),
                )
              }
            >
              줄 삭제
            </button>
          </fieldset>
        ))}
        <button
          data-testid="sales-opportunity-add-need"
          type="button"
          onClick={() =>
            change("needs", [
              ...draft.needs,
              {
                jobCode: "",
                gradeCode: null,
                requiredCount: 1,
                startMonth: draft.startMonth,
                months: draft.expectedMonths || 6,
                monthlyMm: 1,
                estimatedUnitPrice: null,
                tentativeDeveloperIds: [],
              },
            ])
          }
        >
          인력 추가
        </button>
        <button
          data-testid="sales-opportunity-quote"
          type="button"
          onClick={calculate}
        >
          견적 계산
        </button>
        {quote && (
          <output>
            예상 매출 {quote.expectedRevenue.toLocaleString()} · 예상 원가{" "}
            {quote.expectedCost.toLocaleString()} · 이익률 {quote.profitRate}%
            <p>
              최소 단가 {quote.minimumUnitPrice.toLocaleString()} · 목표 단가{" "}
              {quote.targetUnitPrice.toLocaleString()}
            </p>
            <p>
              손익분기 최소금액 {quote.breakEvenAmount?.toLocaleString()} ·
              목표금액 {quote.targetAmount?.toLocaleString()}
            </p>
            <p>
              {quote.profitBand}{" "}
              {quote.approvalRate != null &&
                `(승인 기준 ${quote.approvalRate}%)`}
            </p>
          </output>
        )}
        <button data-testid="sales-opportunity-save" type="submit">
          {busy ? "저장 중…" : "저장"}
        </button>
      </fieldset>
      {readOnly && <p>종료된 기회는 수정할 수 없습니다.</p>}
      <button
        data-testid="sales-opportunity-cancel"
        type="button"
        onClick={onCancel}
      >
        취소
      </button>
    </form>
  );
}

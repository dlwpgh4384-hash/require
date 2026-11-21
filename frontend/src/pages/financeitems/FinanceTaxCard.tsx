import { useRef } from "react";
import { Badge } from "../../shared/list/ListView";
import { financeText, paymentKind } from "./financeItemFormulas";
import type { FinanceMonth, FinancePayment } from "./financeItems";
import {
  Card,
  FieldError,
  MoneyInput,
  won,
  type Fields,
  type Notify,
  type Register,
} from "./financeParts";

/** 시안 taxCard: 연간 예상 법인세, 이번 달 법인세(월할), 납부 일정 표. */
export function FinanceTaxCard({
  value,
  closed,
  disabled,
  estimateDirty,
  formula,
  autoAmount,
  fields,
  register,
  notify,
  onEstimate,
  onPayment,
  onConfirm,
}: {
  value: FinanceMonth;
  closed: boolean;
  disabled: boolean;
  estimateDirty: boolean;
  formula: string;
  /** 확정 신고 줄의 자동 계산값(연간 예상 − 중간예납). 모르면 null. */
  autoAmount: (row: FinancePayment) => number | null;
  fields: Fields;
  register: Register;
  notify: Notify;
  onEstimate: (amount: string) => void;
  onPayment: (index: number, patch: Partial<FinancePayment>) => void;
  onConfirm: (index: number) => void;
}) {
  const tax = value.taxEstimate;
  const monthly = tax.monthlyTaxAmount;
  const before = useRef<string>("");
  return (
    <Card
      id="finance-tax-card"
      testId="finance-tax-card"
      title="법인세"
      sub={`${tax.taxYear}년 · 1년 예상액을 달마다 나눠 순이익표에 반영`}
    >
      <div className="fin-fsec">
        <div className="fin-fld">
          <label className="fin-fl" htmlFor="finance-tax-est">
            연간 예상 법인세
          </label>
          <div className="fin-fc">
            <div className="fin-row">
              <MoneyInput
                id="finance-tax-est"
                label="연간 예상 법인세"
                width={180}
                value={tax.estimatedTaxAmount}
                readOnly={closed}
                disabled={disabled}
                invalid={!!fields["taxEstimate.estimatedTaxAmount"]}
                inputRef={register("taxEstimate.estimatedTaxAmount")}
                blankToZero
                onFocus={() => {
                  before.current = String(tax.estimatedTaxAmount);
                }}
                onBlur={() => {
                  if (!closed && before.current !== String(tax.estimatedTaxAmount))
                    notify(
                      "연간 예상 법인세를 바꿨습니다. [저장]을 누르면 마감 안 된 달의 법인세를 다시 나눕니다.",
                      "info",
                    );
                }}
                onChange={onEstimate}
              />
              <span className="fin-sub">원</span>
            </div>
            <FieldError fields={fields} name="taxEstimate.estimatedTaxAmount" />
            <span className="fin-help">
              {estimateDirty && (
                <b className="fin-draft">{financeText.taxEstimateDraft} </b>
              )}
              {closed ? financeText.taxEstimateClosed : financeText.taxEstimateHelp}
            </span>
          </div>
        </div>
        <div className="fin-fld">
          <span className="fin-fl">
            이번 달 법인세{" "}
            <span className="fin-help-i" title={formula} aria-label={`계산식: ${formula}`}>
              ⓘ
            </span>
          </span>
          <div className="fin-fc">
            <div className="fin-row">
              <input
                className="ls-in ls-in-sm fin-money fin-ro"
                aria-label="이번 달 법인세"
                data-testid="finance-monthly-tax"
                value={won(monthly)}
                readOnly
                tabIndex={-1}
                style={{ width: 180 }}
              />
              <span className="fin-sub">원</span>
              {closed && <Badge kind="lock">마감 확정</Badge>}
            </div>
            <span className="fin-help">
              {formula}
              {!closed && tax.negativeTaxWarning && (
                <>
                  <br />
                  <b className="fin-bad">{financeText.taxNegative}</b>
                </>
              )}
              {closed && " — 마감할 때 정해진 금액"}
            </span>
          </div>
        </div>
      </div>
      <div className="ls-twrap fin-twrap fin-free">
        <table className="ls-t" data-testid="finance-payments">
          <thead>
            <tr>
              <th>납부일</th>
              <th>구분</th>
              <th className="ls-right">금액</th>
              <th>상태</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {value.taxPayments.map((row, index) => {
              const kind = paymentKind(row.taxYear, row.paymentType);
              const paid = row.paidYn === "Y";
              const editable = !paid && !closed;
              const auto = row.paymentType === "CNFM" ? autoAmount(row) : null;
              const name = (field: string) => `taxPayments[${index}].${field}`;
              return (
                <tr key={row.paymentId} data-testid={`finance-payment-${index}`}>
                  <td>
                    {editable ? (
                      <input
                        type="date"
                        className="ls-in ls-in-sm"
                        style={{ width: 140 }}
                        aria-label={`${kind} 납부예정일`}
                        ref={register(name("scheduledDate"))}
                        aria-invalid={!!fields[name("scheduledDate")] || undefined}
                        disabled={disabled}
                        value={row.scheduledDate}
                        onChange={(event) =>
                          onPayment(index, { scheduledDate: event.target.value })
                        }
                        onBlur={(event) => {
                          if (!event.target.value)
                            notify("납부예정일을 넣어 주세요.", "info");
                        }}
                      />
                    ) : (
                      row.scheduledDate
                    )}
                    <FieldError fields={fields} name={name("scheduledDate")} />
                  </td>
                  <td>{kind}</td>
                  <td className="ls-right">
                    {!editable ? (
                      <>
                        {won(row.paymentAmount ?? row.displayAmount)}
                        {paid &&
                          row.actualPaymentDate &&
                          row.actualPaymentDate !== row.scheduledDate && (
                            <>
                              {" "}
                              <span className="ls-cap">
                                {row.actualPaymentDate} 납부
                              </span>
                            </>
                          )}
                      </>
                    ) : row.paymentType === "MID" ? (
                      <MoneyInput
                        label="중간예납 금액"
                        testId={`finance-payment-amount-${index}`}
                        width={130}
                        placeholder="세무사 안내 금액"
                        value={row.paymentAmount}
                        disabled={disabled}
                        invalid={!!fields[name("paymentAmount")]}
                        inputRef={register(name("paymentAmount"))}
                        onChange={(digits) =>
                          onPayment(index, {
                            paymentAmount: digits === "" ? null : digits,
                          })
                        }
                      />
                    ) : (
                      <>
                        <MoneyInput
                          label="확정 신고 예상액"
                          testId={`finance-payment-amount-${index}`}
                          width={130}
                          value={row.paymentAmount ?? auto ?? row.displayAmount}
                          disabled={disabled}
                          invalid={!!fields[name("paymentAmount")]}
                          inputRef={register(name("paymentAmount"))}
                          onChange={(digits) =>
                            onPayment(index, {
                              paymentAmount:
                                digits !== "" && auto != null && Number(digits) === auto
                                  ? null
                                  : digits,
                            })
                          }
                          onBlur={() => {
                            if (row.paymentAmount === "")
                              onPayment(index, {
                                paymentAmount: auto === 0 ? null : "0",
                              });
                          }}
                        />{" "}
                        <span className="ls-cap">
                          {row.paymentAmount != null && row.paymentAmount !== ""
                            ? `고침${auto != null ? ` · 자동 ${won(auto)}` : ""}`
                            : "자동 계산"}
                        </span>
                      </>
                    )}
                    <FieldError fields={fields} name={name("paymentAmount")} />
                  </td>
                  <td>
                    <Badge
                      kind={
                        row.status === "납부"
                          ? "ok"
                          : row.status === "확인 필요"
                            ? "bad"
                            : "info"
                      }
                    >
                      {row.status}
                    </Badge>
                  </td>
                  <td>
                    {!paid && (
                      <button
                        type="button"
                        className={`ls-btn ls-btn-sm${row.status === "확인 필요" ? " ls-btn-p" : ""}`}
                        data-testid={`finance-payment-confirm-${index}`}
                        disabled={disabled}
                        onClick={() => onConfirm(index)}
                      >
                        납부 확인
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <p className="ls-note fin-note">{financeText.taxNote}</p>
    </Card>
  );
}

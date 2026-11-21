import { useEffect, useState, type RefObject } from "react";
import { Badge } from "../../shared/list/ListView";
import { financeText } from "./financeItemFormulas";
import type { FinanceAdjustment } from "./financeItems";
import {
  Card,
  EmptyRow,
  FieldError,
  Icon,
  Req,
  dash,
  signed,
  type Fields,
  type Notify,
} from "./financeParts";

type Option = { code: string; codeName: string };

/** 시안 adjCard: 고른 달의 전월 조정 줄(읽기만) + 맨 아래 입력 줄 [추가]. */
export function FinanceAdjustmentCard({
  monthNo,
  rows,
  closed,
  disabled,
  targets,
  preset,
  items,
  fields,
  notify,
  amountRef,
  onAdd,
  onDelete,
}: {
  monthNo: number;
  rows: FinanceAdjustment[];
  closed: boolean;
  disabled: boolean;
  /** 고를 수 있는 대상 달(YYYYMM, 마감월부터 거꾸로 12개월). */
  targets: string[];
  /** [전월 조정 입력]으로 왔을 때 미리 고를 달. */
  preset: string | null;
  items: Option[];
  fields: Fields;
  notify: Notify;
  amountRef: RefObject<HTMLInputElement>;
  onAdd: (row: FinanceAdjustment) => void;
  onDelete: (index: number) => void;
}) {
  const first = preset && targets.includes(preset) ? preset : (targets[0] ?? "");
  const [target, setTarget] = useState(first);
  const [item, setItem] = useState(items[0]?.code ?? "");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [reasonEl, setReasonEl] = useState<HTMLInputElement | null>(null);
  useEffect(() => setTarget(first), [first]);
  useEffect(() => {
    if (!item && items[0]) setItem(items[0].code);
  }, [item, items]);
  const nameOf = (code: string) =>
    items.find((option) => option.code === code)?.codeName ?? code;
  const sum = rows.reduce((total, row) => total + Number(row.amount || 0), 0);
  const add = () => {
    const raw = amount.replace(/−/g, "-").replace(/[^0-9-]/g, "");
    const value = Number(raw);
    if (!raw || !value || Number.isNaN(value)) {
      notify("금액을 넣어 주세요. 이익이 줄면 − 를 붙입니다.", "warn");
      amountRef.current?.focus();
      return;
    }
    if (!reason.trim()) {
      notify("사유를 넣어 주세요.", "warn");
      reasonEl?.focus();
      return;
    }
    onAdd({
      adjustmentId: null,
      modifiedAt: null,
      targetYearMonth: target,
      adjustmentItem: item,
      amount: value,
      reason: reason.trim(),
    });
    setAmount("");
    setReason("");
    notify(
      `${dash(target)} ${nameOf(item)} ${signed(value)} 줄을 넣었습니다. [저장]을 누르면 ${monthNo}월 영업이익에 반영됩니다.`,
      "info",
    );
  };
  return (
    <Card
      id="finance-adjustment-card"
      testId="finance-adjustment-card"
      title="전월 조정"
      sub={`${monthNo}월 · 마감한 달의 차액을 이 달 손익 「전월 조정」 줄에 반영`}
    >
      <div className="ls-twrap fin-twrap fin-free">
        <table className="ls-t" data-testid="finance-adjustments">
          <thead>
            <tr>
              <th>
                대상 달
                <Req />
              </th>
              <th>
                조정 항목
                <Req />
              </th>
              <th className="ls-right">
                금액(이익 기준 ±)
                <Req />
              </th>
              <th>
                사유
                <Req />
              </th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const value = Number(row.amount || 0);
              const name = (field: string) => `adjustments[${index}].${field}`;
              return (
                <tr
                  key={row.adjustmentId ?? `new-${index}`}
                  data-testid={`finance-adjustment-${index}`}
                >
                  <td>
                    {dash(row.targetYearMonth)}
                    <FieldError fields={fields} name={name("targetYearMonth")} />
                  </td>
                  <td>
                    {nameOf(row.adjustmentItem)}
                    <FieldError fields={fields} name={name("adjustmentItem")} />
                  </td>
                  <td className="ls-right">
                    <span className={value < 0 ? "ls-neg" : undefined}>
                      {signed(value)}
                    </span>
                    <FieldError fields={fields} name={name("amount")} />
                  </td>
                  <td>
                    {row.reason}
                    {!row.adjustmentId && (
                      <>
                        {" "}
                        <Badge kind="warn">저장 전</Badge>
                      </>
                    )}
                    <FieldError fields={fields} name={name("reason")} />
                  </td>
                  <td>
                    {!closed && (
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm"
                        data-testid={`finance-adjustment-delete-${index}`}
                        aria-label="줄 삭제"
                        disabled={disabled}
                        onClick={() => onDelete(index)}
                      >
                        삭제
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
            {!rows.length && (
              <EmptyRow span={5} text={`${monthNo}월 전월 조정이 없습니다.`} />
            )}
            {!closed && targets.length > 0 && (
              <tr data-testid="finance-adjustment-input">
                <td>
                  <select
                    className="ls-in ls-in-sm"
                    aria-label="대상 달"
                    data-testid="finance-adjustment-target"
                    disabled={disabled}
                    value={target}
                    onChange={(event) => setTarget(event.target.value)}
                  >
                    {targets.map((value) => (
                      <option key={value} value={value}>
                        {dash(value)}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <select
                    className="ls-in ls-in-sm"
                    aria-label="조정 항목"
                    disabled={disabled}
                    value={item}
                    onChange={(event) => setItem(event.target.value)}
                  >
                    {items.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.codeName}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="ls-right">
                  <input
                    ref={amountRef}
                    id="finance-adjustment-amount"
                    className="ls-in ls-in-sm fin-money"
                    inputMode="numeric"
                    aria-label="금액"
                    placeholder="예: -100,000"
                    style={{ width: 140 }}
                    disabled={disabled}
                    value={amount}
                    onChange={(event) => setAmount(event.target.value)}
                  />
                </td>
                <td>
                  <input
                    ref={setReasonEl}
                    className="ls-in ls-in-sm"
                    aria-label="사유"
                    maxLength={300}
                    placeholder="무엇을 왜 고치는지"
                    style={{ width: 220 }}
                    disabled={disabled}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm ls-btn-p fin-btn-icon"
                    data-testid="finance-adjustment-add"
                    disabled={disabled}
                    onClick={add}
                  >
                    <Icon name="plus" size={14} /> 추가
                  </button>
                </td>
              </tr>
            )}
          </tbody>
          {rows.length > 0 && (
            <tfoot className="ls-foot">
              <tr>
                <td colSpan={2}>
                  <strong>합계</strong>
                </td>
                <td className="ls-right">
                  <strong className={sum < 0 ? "ls-neg" : undefined}>
                    {signed(sum)}
                  </strong>
                </td>
                <td colSpan={2} className="ls-cap">
                  영업이익표·순이익표 {monthNo}월 「전월 조정」 줄
                </td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
      <p className="ls-note fin-note">{financeText.adjustmentNote}</p>
    </Card>
  );
}

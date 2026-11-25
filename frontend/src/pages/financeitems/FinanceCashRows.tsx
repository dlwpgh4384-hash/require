import { useRef, useState } from "react";
import { Badge } from "../../shared/list/ListView";
import { koreanToday } from "../../shared/time";
import { financeText } from "./financeItemFormulas";
import type { FinanceLoan, FinanceTransfer } from "./financeItems";
import {
  Card,
  EmptyRow,
  FieldError,
  Icon,
  MoneyInput,
  Req,
  won,
  type Fields,
  type Notify,
} from "./financeParts";

type Option = { code: string; codeName: string };

/** 입력 줄의 처음 날짜: 그 달의 오늘 날짜(달 길이를 넘으면 그 달 마지막 날). */
function defaultDate(month: string) {
  const year = Number(month.slice(0, 4));
  const mon = Number(month.slice(4));
  const last = new Date(year, mon, 0).getDate();
  const day = Math.min(Number(koreanToday().slice(8)), last);
  return `${month.slice(0, 4)}-${month.slice(4)}-${String(day).padStart(2, "0")}`;
}
const range = (month: string) => {
  const year = Number(month.slice(0, 4));
  const mon = Number(month.slice(4));
  const last = new Date(year, mon, 0).getDate();
  const head = `${month.slice(0, 4)}-${month.slice(4)}`;
  return { min: `${head}-01`, max: `${head}-${String(last).padStart(2, "0")}` };
};
const inMonth = (date: string, month: string) =>
  date.slice(0, 7) === `${month.slice(0, 4)}-${month.slice(4)}`;

/** 시안 cardCard 뒤쪽: 계좌이체 지출 — 기존 줄은 읽기만, 맨 아래 입력 줄 [추가]. */
export function FinanceTransferSection({
  month,
  rows,
  closed,
  disabled,
  dirty,
  classes,
  fields,
  notify,
  onAdd,
  onDelete,
}: {
  month: string;
  rows: FinanceTransfer[];
  closed: boolean;
  disabled: boolean;
  dirty: boolean;
  classes: Option[];
  fields: Fields;
  notify: Notify;
  onAdd: (row: FinanceTransfer) => void;
  onDelete: (index: number) => void;
}) {
  const monthNo = Number(month.slice(4));
  const [date, setDate] = useState(() => defaultDate(month));
  const [cls, setCls] = useState(classes[0]?.code ?? "EXP");
  const [item, setItem] = useState("");
  const [who, setWho] = useState("");
  const [amount, setAmount] = useState("");
  const [memo, setMemo] = useState("");
  const dateEl = useRef<HTMLInputElement>(null);
  const itemEl = useRef<HTMLInputElement>(null);
  const amountEl = useRef<HTMLElement | null>(null);
  const nameOf = (code: string) =>
    classes.find((option) => option.code === code)?.codeName ?? code;
  const sum = rows.reduce((total, row) => total + Number(row.amount || 0), 0);
  const add = () => {
    if (!date || !inMonth(date, month)) {
      notify(`${monthNo}월 날짜를 넣어 주세요.`, "warn");
      dateEl.current?.focus();
      return;
    }
    if (!item.trim()) {
      notify("내용을 넣어 주세요.", "warn");
      itemEl.current?.focus();
      return;
    }
    const value = Number(amount || 0);
    if (!value) {
      notify("금액을 넣어 주세요.", "warn");
      amountEl.current?.focus();
      return;
    }
    onAdd({
      transferId: null,
      modifiedAt: null,
      date,
      classification: cls,
      description: item.trim(),
      payee: who.trim(),
      amount: value,
      memo: memo.trim(),
    });
    setItem("");
    setWho("");
    setAmount("");
    setMemo("");
    notify(
      `${item.trim()} ${won(value)} 줄을 넣었습니다. [저장]을 누르면 자금수지표 ${cls === "SPCL" ? "미지급 인건비" : "제경비"}에 반영됩니다.`,
      "info",
    );
  };
  return (
    <Card
      testId="finance-transfer-card"
      title="계좌이체 지출"
      sub={`${monthNo}월 · 손익에는 넣지 않고 자금수지표에만 반영(계좌이체 → 제경비, 특별비 → 미지급 인건비)`}
      extra={
        dirty && (
          <>
            {" "}
            <Badge kind="warn">저장 전</Badge>
          </>
        )
      }
    >
      <div className="ls-twrap fin-twrap fin-free">
        <table className="ls-t" data-testid="finance-transfers">
          <thead>
            <tr>
              <th>
                날짜
                <Req />
              </th>
              <th>
                구분
                <Req />
              </th>
              <th>
                내용
                <Req />
              </th>
              <th>받는 곳</th>
              <th className="ls-right">
                금액
                <Req />
              </th>
              <th>메모</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const name = (key: string) => `transfers[${index}].${key}`;
              return (
                <tr
                  key={row.transferId ?? `new-${index}`}
                  data-testid={`finance-transfer-${index}`}
                >
                  <td>
                    {row.date}
                    <FieldError fields={fields} name={name("date")} />
                  </td>
                  <td>
                    {nameOf(row.classification)}
                    <FieldError fields={fields} name={name("classification")} />
                  </td>
                  <td>
                    {row.description}
                    <FieldError fields={fields} name={name("description")} />
                  </td>
                  <td>{row.payee}</td>
                  <td className="ls-right">
                    {won(row.amount)}
                    <FieldError fields={fields} name={name("amount")} />
                  </td>
                  <td>
                    {row.memo}
                    {!row.transferId && (
                      <>
                        {" "}
                        <Badge kind="warn">저장 전</Badge>
                      </>
                    )}
                  </td>
                  <td>
                    {!closed && (
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm"
                        data-testid={`finance-transfer-delete-${index}`}
                        aria-label="줄 삭제"
                        disabled={disabled}
                        onClick={() => {
                          onDelete(index);
                          notify(
                            `${row.description} ${won(row.amount)} 줄을 지웠습니다. [저장]을 누르면 반영됩니다.`,
                            "info",
                          );
                        }}
                      >
                        삭제
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
            {!rows.length && (
              <EmptyRow span={7} text={`${monthNo}월 계좌이체 지출이 없습니다.`} />
            )}
            {!closed && (
              <tr data-testid="finance-transfer-input">
                <td>
                  <input
                    ref={dateEl}
                    type="date"
                    className="ls-in ls-in-sm"
                    aria-label="날짜"
                    style={{ width: 140 }}
                    {...range(month)}
                    disabled={disabled}
                    value={date}
                    onChange={(event) => setDate(event.target.value)}
                  />
                </td>
                <td>
                  <select
                    className="ls-in ls-in-sm"
                    aria-label="구분"
                    disabled={disabled}
                    value={cls}
                    onChange={(event) => setCls(event.target.value)}
                  >
                    {classes.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.codeName}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <input
                    ref={itemEl}
                    className="ls-in ls-in-sm"
                    aria-label="내용"
                    placeholder="예: 장비 임대"
                    maxLength={100}
                    style={{ width: 130 }}
                    disabled={disabled}
                    value={item}
                    onChange={(event) => setItem(event.target.value)}
                  />
                </td>
                <td>
                  <input
                    className="ls-in ls-in-sm"
                    aria-label="받는 곳"
                    placeholder="업체 이름"
                    maxLength={100}
                    style={{ width: 120 }}
                    disabled={disabled}
                    value={who}
                    onChange={(event) => setWho(event.target.value)}
                  />
                </td>
                <td className="ls-right">
                  <MoneyInput
                    label="금액"
                    testId="finance-transfer-amount"
                    placeholder="부가세 포함"
                    width={130}
                    disabled={disabled}
                    value={amount}
                    inputRef={(element) => {
                      amountEl.current = element;
                    }}
                    onChange={setAmount}
                  />
                </td>
                <td>
                  <input
                    className="ls-in ls-in-sm"
                    aria-label="메모"
                    maxLength={200}
                    style={{ width: 150 }}
                    disabled={disabled}
                    value={memo}
                    onChange={(event) => setMemo(event.target.value)}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm ls-btn-p fin-btn-icon"
                    data-testid="finance-transfer-add"
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
                <td>합계</td>
                <td />
                <td className="ls-cap">{rows.length}건</td>
                <td />
                <td className="ls-right">{won(sum)}</td>
                <td />
                <td />
              </tr>
            </tfoot>
          )}
        </table>
      </div>
      <p className="ls-note fin-note">{financeText.transferNote}</p>
    </Card>
  );
}

/** 시안 loanSec: 대여금 입출금 — 받음 +, 갚음 −. 기존 줄은 읽기만, 맨 아래 입력 줄 [추가]. */
export function FinanceLoanSection({
  month,
  rows,
  closed,
  disabled,
  directions,
  fields,
  notify,
  onAdd,
  onDelete,
}: {
  month: string;
  rows: FinanceLoan[];
  closed: boolean;
  disabled: boolean;
  directions: Option[];
  fields: Fields;
  notify: Notify;
  onAdd: (row: FinanceLoan) => void;
  onDelete: (index: number) => void;
}) {
  const monthNo = Number(month.slice(4));
  const [date, setDate] = useState(() => defaultDate(month));
  const [io, setIo] = useState(directions[0]?.code ?? "IN");
  const [amount, setAmount] = useState("");
  const [who, setWho] = useState("");
  const [memo, setMemo] = useState("");
  const dateEl = useRef<HTMLInputElement>(null);
  const amountEl = useRef<HTMLElement | null>(null);
  const nameOf = (code: string) =>
    directions.find((option) => option.code === code)?.codeName ?? code;
  const out = (code: string) => code === "OUT" || nameOf(code) === "갚음";
  const add = () => {
    if (!date) {
      notify("날짜를 넣어 주세요.", "warn");
      dateEl.current?.focus();
      return;
    }
    if (!inMonth(date, month)) {
      notify(
        `${monthNo}월 날짜만 넣을 수 있습니다. 다른 달은 그 달을 골라 넣어 주세요.`,
        "warn",
      );
      dateEl.current?.focus();
      return;
    }
    const value = Number(amount || 0);
    if (!value) {
      notify("금액을 넣어 주세요.", "warn");
      amountEl.current?.focus();
      return;
    }
    onAdd({
      loanId: null,
      modifiedAt: null,
      date,
      direction: io,
      amount: value,
      counterparty: who.trim(),
      memo: memo.trim(),
    });
    setAmount("");
    setWho("");
    setMemo("");
    notify(
      `대여금 ${nameOf(io)} ${won(value)} 줄을 넣었습니다. [저장]을 누르면 자금수지표에 ${out(io) ? "−" : "+"}로 반영됩니다.`,
      "info",
    );
  };
  return (
    <Card
      testId="finance-loan-card"
      title="대여금 입출금"
      sub={`${monthNo}월 · 손익에는 넣지 않고 자금수지표 대여금 줄에만 반영`}
    >
      <div className="ls-twrap fin-twrap fin-free">
        <table className="ls-t" data-testid="finance-loans">
          <thead>
            <tr>
              <th>
                날짜
                <Req />
              </th>
              <th>
                구분
                <Req />
              </th>
              <th className="ls-right">
                금액
                <Req />
              </th>
              <th>상대</th>
              <th>메모</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => {
              const name = (key: string) => `loans[${index}].${key}`;
              return (
                <tr
                  key={row.loanId ?? `new-${index}`}
                  data-testid={`finance-loan-${index}`}
                >
                  <td>
                    {row.date}
                    <FieldError fields={fields} name={name("date")} />
                  </td>
                  <td>
                    <Badge kind={out(row.direction) ? "warn" : "info"}>
                      {nameOf(row.direction)}
                    </Badge>
                    <FieldError fields={fields} name={name("direction")} />
                  </td>
                  <td className="ls-right">
                    {out(row.direction) ? "−" : ""}
                    {won(row.amount)}
                    <FieldError fields={fields} name={name("amount")} />
                  </td>
                  <td>{row.counterparty}</td>
                  <td>
                    {row.memo}
                    {!row.loanId && (
                      <>
                        {" "}
                        <Badge kind="warn">저장 전</Badge>
                      </>
                    )}
                  </td>
                  <td>
                    {!closed && (
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm"
                        data-testid={`finance-loan-delete-${index}`}
                        aria-label="줄 삭제"
                        disabled={disabled}
                        onClick={() => {
                          onDelete(index);
                          notify(
                            `대여금 ${nameOf(row.direction)} ${won(row.amount)} 줄을 지웠습니다. [저장]을 누르면 반영됩니다.`,
                            "info",
                          );
                        }}
                      >
                        삭제
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
            {!rows.length && (
              <EmptyRow span={6} text={`${monthNo}월 대여금 입출금이 없습니다.`} />
            )}
            {!closed && (
              <tr data-testid="finance-loan-input">
                <td>
                  <input
                    ref={dateEl}
                    type="date"
                    className="ls-in ls-in-sm"
                    aria-label="날짜"
                    style={{ width: 140 }}
                    {...range(month)}
                    disabled={disabled}
                    value={date}
                    onChange={(event) => setDate(event.target.value)}
                  />
                </td>
                <td>
                  <select
                    className="ls-in ls-in-sm"
                    aria-label="구분"
                    disabled={disabled}
                    value={io}
                    onChange={(event) => setIo(event.target.value)}
                  >
                    {directions.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.codeName}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="ls-right">
                  <MoneyInput
                    label="금액"
                    testId="finance-loan-amount"
                    placeholder="금액(양수)"
                    width={140}
                    disabled={disabled}
                    value={amount}
                    inputRef={(element) => {
                      amountEl.current = element;
                    }}
                    onChange={setAmount}
                  />
                </td>
                <td>
                  <input
                    className="ls-in ls-in-sm"
                    aria-label="상대"
                    placeholder="대표이사·은행 등"
                    maxLength={100}
                    style={{ width: 130 }}
                    disabled={disabled}
                    value={who}
                    onChange={(event) => setWho(event.target.value)}
                  />
                </td>
                <td>
                  <input
                    className="ls-in ls-in-sm"
                    aria-label="메모"
                    maxLength={200}
                    style={{ width: 140 }}
                    disabled={disabled}
                    value={memo}
                    onChange={(event) => setMemo(event.target.value)}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm ls-btn-p fin-btn-icon"
                    data-testid="finance-loan-add"
                    disabled={disabled}
                    onClick={add}
                  >
                    <Icon name="plus" size={14} /> 추가
                  </button>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <p className="ls-note fin-note">{financeText.loanNote}</p>
    </Card>
  );
}

import { useRef, useState } from "react";
import { Badge } from "../../shared/list/ListView";
import { financeText } from "./financeItemFormulas";
import type { FinanceCard, FinanceMonth } from "./financeItems";
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
  type Register,
} from "./financeParts";

/** 시안 cardCard 앞쪽: 법인카드 대금 — 카드별 결제액, 카드별 합계·자금수지 반영 합계. */
export function FinanceCardSection({
  monthNo,
  value,
  closed,
  disabled,
  dirty,
  fields,
  register,
  notify,
  onCard,
  onAddCard,
  onRemoveCard,
  onTotal,
  onAuto,
}: {
  monthNo: number;
  value: FinanceMonth;
  closed: boolean;
  disabled: boolean;
  dirty: boolean;
  fields: Fields;
  register: Register;
  notify: Notify;
  onCard: (index: number, patch: Partial<FinanceCard>) => void;
  onAddCard: (card: FinanceCard) => void;
  onRemoveCard: (index: number) => void;
  onTotal: (amount: string, manual: boolean) => void;
  onAuto: () => void;
}) {
  const [name, setName] = useState("");
  const [digits, setDigits] = useState("");
  const nameEl = useRef<HTMLInputElement>(null);
  const noEl = useRef<HTMLInputElement>(null);
  const totalBefore = useRef("");
  // 시안: 사용 중이거나 이 달 금액이 있는 카드만 보인다(삭제해도 금액이 있는 달에는 남음).
  const list = value.cards
    .map((card, index) => ({ card, index }))
    .filter(({ card }) => card.useYn === "Y" || Number(card.amount || 0) > 0);
  const sum = list.reduce((total, { card }) => total + Number(card.amount || 0), 0);
  const manual = value.cardPayment.manualOverride;
  const total = manual ? value.cardPayment.paymentAmount : sum;
  const diff = Number(total || 0) - sum;
  const active = value.cards.filter((card) => card.useYn === "Y").length;
  const add = () => {
    const cardName = name.trim();
    const no = digits.replace(/[^\d]/g, "");
    if (!cardName) {
      notify("카드 이름을 넣어 주세요.", "warn");
      nameEl.current?.focus();
      return;
    }
    if (no.length !== 4) {
      notify("카드 번호 끝 4자리를 넣어 주세요.", "warn");
      noEl.current?.focus();
      return;
    }
    if (
      value.cards.some(
        (card) => card.cardName === cardName && card.lastFourDigits === no,
      )
    ) {
      notify("같은 카드가 이미 있습니다.", "warn");
      return;
    }
    onAddCard({
      cardId: null,
      modifiedAt: null,
      monthlyModifiedAt: null,
      cardName,
      lastFourDigits: no,
      useYn: "Y",
      amount: 0,
    });
    setName("");
    setDigits("");
    notify(`${cardName}를 추가했습니다. 금액을 넣고 [저장]을 눌러 주세요.`, "info");
  };
  return (
    <Card
      id="finance-card-card"
      testId="finance-card-card"
      title="법인카드 대금"
      sub={`${monthNo}월 · 손익에는 넣지 않고 자금수지표 제경비에만 반영`}
      extra={
        dirty && (
          <>
            {" "}
            <Badge kind="warn">저장 전</Badge>
          </>
        )
      }
      right={<span className="ls-cap">카드 {active}장 사용 중</span>}
    >
      <div className="ls-twrap fin-twrap fin-free">
        <table className="ls-t" data-testid="finance-cards">
          <thead>
            <tr>
              <th>
                카드 이름
                <Req />
              </th>
              <th>
                끝 4자리
                <Req />
              </th>
              <th className="ls-right">{monthNo}월 결제액</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {list.map(({ card, index }) => {
              const field = (key: string) => `cards[${index}].${key}`;
              return (
                <tr
                  key={card.cardId ?? `new-${index}`}
                  data-testid={`finance-card-${index}`}
                >
                  <td>
                    {card.cardName}
                    {!card.cardId && (
                      <>
                        {" "}
                        <Badge kind="info">새 카드</Badge>
                      </>
                    )}
                    <FieldError fields={fields} name={field("cardName")} />
                  </td>
                  <td className="fin-tnum">
                    {card.lastFourDigits}
                    <FieldError fields={fields} name={field("lastFourDigits")} />
                  </td>
                  <td className="ls-right">
                    {closed ? (
                      won(card.amount || 0)
                    ) : (
                      <MoneyInput
                        label={`${card.cardName} 결제액`}
                        testId={`finance-card-amount-${index}`}
                        width={140}
                        disabled={disabled}
                        value={card.amount === 0 ? "" : card.amount}
                        invalid={!!fields[field("amount")]}
                        inputRef={register(field("amount"))}
                        onChange={(amount) => onCard(index, { amount })}
                        onBlur={() => {
                          // 빈칸은 칸을 벗어날 때 0으로 둔다(쓰는 동안은 그대로).
                          if (card.amount === "") onCard(index, { amount: 0 });
                        }}
                      />
                    )}
                    <FieldError fields={fields} name={field("amount")} />
                  </td>
                  <td>
                    <Badge kind={card.useYn === "Y" ? "ok" : "neutral"}>
                      {card.useYn === "Y" ? "사용" : "삭제됨"}
                    </Badge>
                    {!closed && (
                      <>
                        {" "}
                        <button
                          type="button"
                          className="ls-btn ls-btn-sm"
                          data-testid={`finance-card-toggle-${index}`}
                          disabled={disabled}
                          title={
                            card.useYn === "Y"
                              ? financeText.cardDeleteTitle
                              : financeText.cardRestoreTitle
                          }
                          onClick={() => {
                            if (!card.cardId) {
                              onRemoveCard(index);
                              return;
                            }
                            const use = card.useYn === "Y" ? "N" : "Y";
                            onCard(index, { useYn: use });
                            notify(
                              `${card.cardName}${use === "Y" ? "를 되살렸습니다." : "를 삭제했습니다. 금액이 있는 지난달에는 그대로 보이고, 금액이 없는 달부터 목록에서 빠집니다."} [저장]을 눌러야 반영됩니다.`,
                              "info",
                            );
                          }}
                        >
                          {card.useYn === "Y" ? "삭제" : "되살리기"}
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              );
            })}
            {!list.length && <EmptyRow span={4} text="등록한 카드가 없습니다." />}
            {!closed && (
              <tr data-testid="finance-card-input">
                <td>
                  <input
                    ref={nameEl}
                    className="ls-in ls-in-sm"
                    aria-label="카드 이름"
                    placeholder="예: 영업 4팀 카드"
                    maxLength={100}
                    style={{ width: 170 }}
                    disabled={disabled}
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                  />
                </td>
                <td>
                  <input
                    ref={noEl}
                    className="ls-in ls-in-sm"
                    aria-label="끝 4자리"
                    placeholder="1234"
                    maxLength={4}
                    inputMode="numeric"
                    style={{ width: 70 }}
                    disabled={disabled}
                    value={digits}
                    onChange={(event) =>
                      setDigits(event.target.value.replace(/[^\d]/g, ""))
                    }
                  />
                </td>
                <td />
                <td>
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm ls-btn-p fin-btn-icon"
                    data-testid="finance-card-add"
                    disabled={disabled}
                    onClick={add}
                  >
                    <Icon name="plus" size={14} /> 카드 추가
                  </button>
                </td>
              </tr>
            )}
          </tbody>
          <tfoot className="ls-foot">
            <tr>
              <td>카드별 합계</td>
              <td className="ls-cap">{list.length}장</td>
              <td className="ls-right" data-testid="finance-card-sum">
                {won(sum)}
              </td>
              <td />
            </tr>
            <tr>
              <td>
                <strong>자금수지 반영 합계</strong>
              </td>
              <td className="ls-cap">{manual ? "직접 고침" : "자동"}</td>
              <td className="ls-right">
                {closed ? (
                  <strong>{won(total)}</strong>
                ) : (
                  <MoneyInput
                    label="자금수지 반영 합계"
                    testId="finance-card-total"
                    width={140}
                    strong
                    disabled={disabled}
                    value={total}
                    invalid={!!fields["cardPayment.paymentAmount"]}
                    inputRef={register("cardPayment.paymentAmount")}
                    onFocus={() => {
                      totalBefore.current = String(total);
                    }}
                    onChange={(amount) => onTotal(amount, true)}
                    onBlur={() => {
                      const typed =
                        total === "" || total == null ? 0 : Number(total);
                      if (typed === sum) {
                        onTotal(String(sum), false);
                        if (totalBefore.current !== String(total) || manual)
                          notify("카드별 합계와 같아 자동 합계로 둡니다.", "info");
                      } else {
                        if (total === "") onTotal("0", true);
                        if (totalBefore.current !== String(total))
                          notify(
                            "자금수지에 넣을 합계를 고쳤습니다. [저장]을 눌러야 반영됩니다.",
                            "info",
                          );
                      }
                    }}
                  />
                )}
                <FieldError fields={fields} name="cardPayment.paymentAmount" />
              </td>
              <td>
                {manual && diff !== 0 && (
                  <>
                    <span className="ls-cap fin-warn-text">
                      카드별 합계와 {diff > 0 ? "+" : "−"}
                      {won(Math.abs(diff))} 차이
                    </span>
                    {!closed && (
                      <>
                        {" "}
                        <button
                          type="button"
                          className="ls-btn ls-btn-sm"
                          data-testid="finance-card-auto"
                          disabled={disabled}
                          onClick={() => {
                            onAuto();
                            notify(
                              "카드별 합계로 되돌렸습니다. [저장]을 눌러야 반영됩니다.",
                              "info",
                            );
                          }}
                        >
                          자동 합계로
                        </button>
                      </>
                    )}
                  </>
                )}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
      <p className="ls-note fin-note">{financeText.cardNote}</p>
    </Card>
  );
}

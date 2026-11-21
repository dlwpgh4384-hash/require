import { Badge } from "../../shared/list/ListView";
import { financeText } from "./financeItemFormulas";
import type { FinanceItem } from "./financeItems";
import {
  FieldError,
  Icon,
  MoneyInput,
  Req,
  won,
  type Fields,
  type Register,
} from "./financeParts";

type Option = { code: string; codeName: string };

/** 자동 채움·지난달 불러오기로 들어와 아직 저장하지 않은 줄(시안 「자동」). */
export const isAutoRow = (row: FinanceItem) =>
  row.autoFillYn === "Y" && !row.itemId;

/** 시안 UI-10 판관비·영업외 항목 표: 법인세(월할) 계산 줄, 구분별 합계 줄, [행 추가]. */
export function FinanceItemsTable({
  month,
  items,
  closed,
  disabled,
  classes,
  sgaItems,
  monthlyTax,
  taxFormula,
  fields,
  register,
  onChange,
  onDelete,
  onAdd,
  onGoTax,
}: {
  month: string;
  items: FinanceItem[];
  closed: boolean;
  disabled: boolean;
  classes: Option[];
  sgaItems: Option[];
  monthlyTax: number;
  taxFormula: string;
  fields: Fields;
  register: Register;
  onChange: (index: number, patch: Partial<FinanceItem>) => void;
  onDelete: (index: number) => void;
  onAdd: () => void;
  onGoTax: () => void;
}) {
  const nameOf = (code: string) =>
    classes.find((option) => option.code === code)?.codeName ?? code;
  const sum = (rows: FinanceItem[], key: "amount" | "vatAmount") =>
    rows.reduce((total, row) => total + Number(row[key] || 0), 0);
  const blank = closed ? null : <td />;
  return (
    <>
      <div className="ls-tbar fin-tbar">
        <div className="ls-tl">
          {month} · <strong>{items.length}</strong>건{" "}
          <span className="ls-cap">+ 법인세(월할) 계산 1줄</span>
        </div>
      </div>
      <div className="ls-twrap fin-twrap">
        <table className="ls-t fin-items" data-testid="finance-items-list">
          <thead>
            <tr>
              <th>구분</th>
              <th>
                세부 항목
                <Req />
              </th>
              <th className="ls-right">
                금액
                <Req />
              </th>
              <th className="ls-right">부가세</th>
              <th>입출금일</th>
              <th className="ls-center">매달 반복</th>
              <th>비고</th>
              {!closed && <th className="ls-center">삭제</th>}
            </tr>
          </thead>
          <tbody>
            {items.map((row, index) => {
              const key = (field: string) => `items[${index}].${field}`;
              return (
                <tr
                  key={row.itemId ?? `new-${index}`}
                  data-testid={`finance-item-${index}`}
                >
                  <td>
                    {closed ? (
                      nameOf(row.classification)
                    ) : (
                      <select
                        className="ls-in ls-in-sm"
                        aria-label="구분"
                        ref={register(key("classification"))}
                        aria-invalid={!!fields[key("classification")] || undefined}
                        disabled={disabled}
                        value={row.classification}
                        onChange={(event) =>
                          onChange(index, { classification: event.target.value })
                        }
                      >
                        {classes.map((option) => (
                          <option key={option.code} value={option.code}>
                            {option.codeName}
                          </option>
                        ))}
                      </select>
                    )}
                    <FieldError fields={fields} name={key("classification")} />
                  </td>
                  <td>
                    {closed ? (
                      row.itemName
                    ) : (
                      <>
                        <input
                          className="ls-in ls-in-sm"
                          aria-label="세부 항목"
                          data-testid={`finance-item-name-${index}`}
                          list="finance-sga-options"
                          style={{ width: 140 }}
                          ref={register(key("itemName"))}
                          aria-invalid={!!fields[key("itemName")] || undefined}
                          disabled={disabled}
                          value={row.itemName}
                          onChange={(event) =>
                            onChange(index, {
                              itemName: event.target.value,
                              itemCode:
                                sgaItems.find(
                                  (option) =>
                                    option.codeName === event.target.value,
                                )?.code ?? null,
                            })
                          }
                        />
                        {isAutoRow(row) && (
                          <>
                            {" "}
                            <Badge kind="info">자동</Badge>
                          </>
                        )}
                      </>
                    )}
                    <FieldError fields={fields} name={key("itemName")} />
                  </td>
                  <td className="ls-right">
                    {closed ? (
                      won(row.amount)
                    ) : (
                      <MoneyInput
                        label="금액"
                        testId={`finance-item-amount-${index}`}
                        width={130}
                        value={row.amount}
                        disabled={disabled}
                        invalid={!!fields[key("amount")]}
                        inputRef={register(key("amount"))}
                        onChange={(amount) => onChange(index, { amount })}
                      />
                    )}
                    <FieldError fields={fields} name={key("amount")} />
                  </td>
                  <td className="ls-right">
                    {closed ? (
                      won(row.vatAmount || 0)
                    ) : (
                      <MoneyInput
                        label="부가세"
                        testId={`finance-item-vat-${index}`}
                        width={110}
                        blankToZero
                        value={row.vatAmount}
                        disabled={disabled}
                        invalid={!!fields[key("vatAmount")]}
                        inputRef={register(key("vatAmount"))}
                        onChange={(vatAmount) => onChange(index, { vatAmount })}
                      />
                    )}
                    <FieldError fields={fields} name={key("vatAmount")} />
                  </td>
                  <td>
                    {closed ? (
                      row.cashDate || "–"
                    ) : (
                      <input
                        type="date"
                        className="ls-in ls-in-sm"
                        aria-label="입출금일"
                        ref={register(key("cashDate"))}
                        aria-invalid={!!fields[key("cashDate")] || undefined}
                        disabled={disabled}
                        value={row.cashDate ?? ""}
                        onChange={(event) =>
                          onChange(index, {
                            cashDate: event.target.value || null,
                          })
                        }
                      />
                    )}
                    <FieldError fields={fields} name={key("cashDate")} />
                  </td>
                  <td className="ls-center">
                    {closed ? (
                      row.repeatYn === "Y" ? (
                        "✓"
                      ) : (
                        "–"
                      )
                    ) : (
                      <input
                        type="checkbox"
                        aria-label={`${row.itemName} 매달 반복`}
                        disabled={disabled}
                        checked={row.repeatYn === "Y"}
                        onChange={(event) =>
                          onChange(index, {
                            repeatYn: event.target.checked ? "Y" : "N",
                          })
                        }
                      />
                    )}
                  </td>
                  <td>
                    {closed ? (
                      row.memo || "–"
                    ) : (
                      <input
                        className="ls-in ls-in-sm"
                        aria-label="비고"
                        maxLength={500}
                        style={{ width: 180 }}
                        ref={register(key("memo"))}
                        aria-invalid={!!fields[key("memo")] || undefined}
                        disabled={disabled}
                        value={row.memo ?? ""}
                        onChange={(event) =>
                          onChange(index, { memo: event.target.value })
                        }
                      />
                    )}
                    <FieldError fields={fields} name={key("memo")} />
                  </td>
                  {!closed && (
                    <td className="ls-center">
                      <button
                        type="button"
                        className="ls-ibtn ls-ibtn-sm"
                        data-testid={`finance-item-delete-${index}`}
                        aria-label={`${row.itemName} 행 삭제`}
                        disabled={disabled}
                        onClick={() => onDelete(index)}
                      >
                        <Icon name="x" />
                      </button>
                    </td>
                  )}
                </tr>
              );
            })}
            <tr className="fin-taxrow" data-testid="finance-tax-row">
              <td>법인세</td>
              <td>
                법인세(월할){" "}
                <span
                  className="ls-cap fin-help"
                  title={taxFormula}
                  aria-label={`계산식: ${taxFormula}`}
                >
                  ⓘ
                </span>{" "}
                <Badge>계산</Badge>
              </td>
              <td className="ls-right">
                <button
                  type="button"
                  className="ls-in ls-in-sm fin-taxbtn"
                  data-testid="finance-tax-focus"
                  title={financeText.taxRowButton}
                  onClick={onGoTax}
                >
                  {won(monthlyTax)}
                </button>
              </td>
              <td className="ls-right">
                <span className="fin-sub">–</span>
              </td>
              <td>
                <span className="ls-cap">{financeText.taxRowPay}</span>
              </td>
              <td className="ls-center">
                <span className="fin-sub">–</span>
              </td>
              <td>
                <span className="ls-cap">{financeText.taxRowNote}</span>
              </td>
              {blank}
            </tr>
          </tbody>
          <tfoot className="ls-foot">
            {classes.map((option) => {
              const rows = items.filter(
                (row) => row.classification === option.code,
              );
              return (
                <tr key={option.code}>
                  <td>{option.codeName} 합계</td>
                  <td className="ls-cap">{rows.length}건</td>
                  <td className="ls-right">{won(sum(rows, "amount"))}</td>
                  <td className="ls-right">{won(sum(rows, "vatAmount"))}</td>
                  <td />
                  <td />
                  <td />
                  {blank}
                </tr>
              );
            })}
            <tr>
              <td>법인세(월할)</td>
              <td className="ls-cap">계산값</td>
              <td className="ls-right">{won(monthlyTax)}</td>
              <td />
              <td />
              <td />
              <td />
              {blank}
            </tr>
          </tfoot>
        </table>
      </div>
      <datalist id="finance-sga-options">
        {sgaItems.map((option) => (
          <option key={option.code} value={option.codeName} />
        ))}
      </datalist>
      {!closed && (
        <>
          <div>
            <button
              type="button"
              className="ls-btn ls-btn-sm fin-btn-icon"
              data-testid="finance-item-add"
              disabled={disabled}
              onClick={onAdd}
            >
              <Icon name="plus" /> 행 추가
            </button>
          </div>
          <p className="ls-note fin-note">{financeText.itemsNote}</p>
        </>
      )}
    </>
  );
}

import {
  MONEY_UNITS,
  formatMoney,
  isNegativeMoney,
  type MoneyUnit,
} from "./moneyUnit";

/** 시안 unitSel(): 도구 줄 오른쪽 「단위 [원|천 원|백만 원]」. */
export function MoneyUnitSelect({
  value,
  onChange,
  id = "money-unit",
}: {
  value: MoneyUnit;
  onChange: (unit: MoneyUnit) => void;
  id?: string;
}) {
  return (
    <span className="ls-unitsel">
      <label htmlFor={id}>단위</label>
      <select
        id={id}
        className="ls-in ls-in-sm"
        data-testid="money-unit"
        value={value}
        onChange={(event) => onChange(event.target.value as MoneyUnit)}
      >
        {MONEY_UNITS.map((unit) => (
          <option key={unit} value={unit}>
            {unit}
          </option>
        ))}
      </select>
    </span>
  );
}

/** 단위에 맞춘 금액 한 칸. 음수는 빨간 글씨(시안 .neg). */
export function Money({
  value,
  unit,
}: {
  value: number | null | undefined;
  unit: MoneyUnit;
}) {
  const text = formatMoney(value, unit);
  return isNegativeMoney(value, unit) ? (
    <span className="ls-neg">{text}</span>
  ) : (
    <>{text}</>
  );
}

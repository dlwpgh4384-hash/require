import { useCallback, useEffect, useRef } from "react";
import {
  getCommonUserPreference,
  putCommonUserPreference,
  UserPreferenceRequestError,
} from "../../pages/userpreferences/userPreferences";
import {
  DEFAULT_MONEY_UNIT,
  MONEY_UNITS,
  formatMoney,
  isMoneyUnit,
  isNegativeMoney,
  type MoneyUnit,
} from "./moneyUnit";

/**
 * 시안 unitSel(): 도구 줄 오른쪽 「단위 [원|천 원|백만 원]」.
 * 금액 단위는 계정에 한 값(TB_SY_USER_PREF, PREF_TYPE=AMT_UNIT, 키=GLOBAL)이라 처음에 읽고(없으면 「천 원」),
 * 바꾸면 저장한다. 남이 먼저 바꿨으면(409) 다시 읽는다. remember={false}면 화면 안에서만 바꾼다(시험용).
 */
export function MoneyUnitSelect({
  value,
  onChange,
  id = "money-unit",
  remember = true,
}: {
  value: MoneyUnit;
  onChange: (unit: MoneyUnit) => void;
  id?: string;
  remember?: boolean;
}) {
  const token = useRef<string | null>(null);
  const changeRef = useRef(onChange);
  useEffect(() => {
    changeRef.current = onChange;
  });
  // 처음 읽기가 끝나기 전에 사용자가 고르면 그 값을 덮지 않는다
  const touched = useRef(false);
  const load = useCallback((signal?: AbortSignal, force = false) => {
    getCommonUserPreference("AMT_UNIT", "GLOBAL", signal).then(
      (result) => {
        if (signal?.aborted) return;
        token.current = result.data.modDttm;
        if (touched.current && !force) return;
        const saved = result.data.found ? result.data.prefVal : null;
        changeRef.current(isMoneyUnit(saved) ? saved : DEFAULT_MONEY_UNIT);
      },
      () => undefined,
    );
  }, []);
  useEffect(() => {
    if (!remember) return;
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [remember, load]);
  const change = (unit: MoneyUnit) => {
    touched.current = true;
    onChange(unit);
    if (!remember) return;
    putCommonUserPreference("AMT_UNIT", "GLOBAL", unit, token.current).then(
      (result) => {
        token.current = result.data.modDttm;
      },
      (error) => {
        if (error instanceof UserPreferenceRequestError && error.status === 409)
          load(undefined, true);
      },
    );
  };
  return (
    <span className="ls-unitsel">
      <label htmlFor={id}>단위</label>
      <select
        id={id}
        className="ls-in ls-in-sm"
        data-testid="money-unit"
        value={value}
        onChange={(event) => change(event.target.value as MoneyUnit)}
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

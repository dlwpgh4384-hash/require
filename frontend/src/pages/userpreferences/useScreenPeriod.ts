import { useCallback, useEffect, useRef, useState } from "react";
import {
  isAllowed,
  latestPeriod,
  type PeriodBasis,
  type PeriodOptions,
  type PeriodValue,
} from "../../shared/period/periodRules";
import { getCommonUserPreference, putCommonUserPreference } from "./userPreferences";

type State =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; basis: PeriodBasis; value: PeriodValue };

function parseSaved(value: string | null): PeriodValue | null {
  if (value === null) return null;
  try {
    const parsed = JSON.parse(value);
    if (parsed && typeof parsed.year === "number")
      return parsed.month === undefined
        ? { year: parsed.year }
        : { year: parsed.year, month: parsed.month };
  } catch {
    /* 형식이 틀린 저장값은 가장 최근 달로 바꾼다. */
  }
  return null;
}

/**
 * 화면별 기간을 TB_SY_USER_PREF(PERIOD)에서 읽고 저장한다.
 * 저장된 기간을 먼저 쓰고, 없으면 가장 최근 달. 저장된 기간이 지금 허용 범위 밖이면
 * 가장 최근 달로 바꿔 보여 주고 다시 저장한다(안내 창 없음).
 */
export function useScreenPeriod(screenId: string, options: PeriodOptions) {
  const [state, setState] = useState<State>({ status: "loading" });
  const token = useRef<string | null>(null);
  const optionsKey = JSON.stringify(options);
  const save = useCallback(
    async (value: PeriodValue) => {
      const result = await putCommonUserPreference(
        "PERIOD",
        screenId,
        JSON.stringify(value),
        token.current,
      );
      token.current = result.data.modDttm;
    },
    [screenId],
  );
  useEffect(() => {
    const controller = new AbortController();
    const current: PeriodOptions = JSON.parse(optionsKey);
    setState({ status: "loading" });
    getCommonUserPreference("PERIOD", screenId, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        const basis = response.meta.periodBasis;
        if (!basis) {
          setState({
            status: "error",
            message:
              response.meta.periodBasisError ??
              "기간 기준을 확인할 수 없습니다. 운영 기준값을 확인해 주세요.",
          });
          return;
        }
        token.current = response.data.modDttm;
        const saved = parseSaved(response.data.prefVal);
        const value =
          saved && isAllowed(saved, basis, current) ? saved : latestPeriod(basis, current.kind);
        setState({ status: "ready", basis, value });
        if (saved && !isAllowed(saved, basis, current))
          void save(value).catch(() => undefined);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setState({ status: "error", message: "기간을 불러오지 못했습니다. 다시 시도해 주세요." });
      });
    return () => controller.abort();
  }, [screenId, optionsKey, save]);
  const setValue = useCallback(
    (value: PeriodValue) => {
      setState((previous) =>
        previous.status === "ready" ? { ...previous, value } : previous,
      );
      void save(value).catch(() => undefined);
    },
    [save],
  );
  return { state, setValue };
}

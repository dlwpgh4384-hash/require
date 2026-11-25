import { useCallback, useEffect, useMemo, useState } from "react";
import {
  isAllowed,
  latestPeriod,
  type PeriodBasis,
  type PeriodOptions,
  type PeriodValue,
} from "../../shared/period/periodRules";
import {
  createPreferenceSaver,
  getCommonUserPreference,
  PERIOD_BASIS_CHANGED_EVENT,
  PERIOD_CHANGED_EVENT,
  type PeriodChangedDetail,
} from "./userPreferences";

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
export function useScreenPeriod(
  screenId: string,
  options: PeriodOptions,
  latestYm?: string,
) {
  const [state, setState] = useState<State>({ status: "loading" });
  // 화면마다 따로 차례로 저장한다(빨리 두 번 바꿔도 두 번째가 남고, 다른 화면의 수정 시각과 섞이지 않는다).
  const saver = useMemo(
    () => createPreferenceSaver("PERIOD", screenId),
    [screenId],
  );
  const optionsKey = JSON.stringify(options);
  const save = useCallback(
    async (value: PeriodValue) => {
      await saver.save(JSON.stringify(value));
    },
    [saver],
  );
  useEffect(() => {
    const controller = new AbortController();
    const current: PeriodOptions = JSON.parse(optionsKey);
    setState({ status: "loading" });
    getCommonUserPreference("PERIOD", screenId, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        const sourceBasis = response.meta.periodBasis;
        const basis =
          sourceBasis && latestYm ? { ...sourceBasis, latestYm } : sourceBasis;
        if (!basis) {
          setState({
            status: "error",
            message:
              response.meta.periodBasisError ??
              "기간 기준을 확인할 수 없습니다. 운영 기준값을 확인해 주세요.",
          });
          return;
        }
        saver.setToken(response.data.modDttm);
        const saved = parseSaved(response.data.prefVal);
        const value =
          saved && isAllowed(saved, basis, current)
            ? saved
            : latestPeriod(basis, current.kind);
        setState({ status: "ready", basis, value });
        if (saved && !isAllowed(saved, basis, current))
          void save(value).catch(() => undefined);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setState({
            status: "error",
            message: "기간을 불러오지 못했습니다. 다시 시도해 주세요.",
          });
      });
    return () => controller.abort();
  }, [screenId, optionsKey, save, latestYm]);
  const setValue = useCallback(
    (value: PeriodValue) => {
      setState((previous) =>
        previous.status === "ready" ? { ...previous, value } : previous,
      );
      void save(value).catch(() => undefined);
    },
    [save],
  );
  // 다른 화면(월 마감 [확인하기])이 이 화면 기간을 바꿔 저장하면, 이미 열린 탭도 그 기간으로 바꾼다(다시 저장하지 않음).
  useEffect(() => {
    const current: PeriodOptions = JSON.parse(optionsKey);
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<PeriodChangedDetail>).detail;
      if (!detail || detail.screenId !== screenId) return;
      const next = parseSaved(detail.prefVal);
      if (!next) return;
      saver.setToken(detail.modDttm);
      setState((previous) =>
        previous.status === "ready" && isAllowed(next, previous.basis, current)
          ? { ...previous, value: next }
          : previous,
      );
    };
    window.addEventListener(PERIOD_CHANGED_EVENT, listener);
    return () => window.removeEventListener(PERIOD_CHANGED_EVENT, listener);
  }, [screenId, optionsKey, saver]);
  // 월 마감·해제 뒤에는 기간 기준을 다시 읽는다. 고른 기간은 그대로 두고, 새 기준에서 고를 수 없게 되면 가장 최근 달로 바꾼다.
  useEffect(() => {
    const current: PeriodOptions = JSON.parse(optionsKey);
    let controller: AbortController | null = null;
    const listener = () => {
      controller?.abort();
      const active = new AbortController();
      controller = active;
      getCommonUserPreference("PERIOD", screenId, active.signal)
        .then((response) => {
          const basis = response.meta.periodBasis;
          if (active.signal.aborted || !basis) return;
          setState((previous) => {
            if (previous.status !== "ready") return previous;
            const value = isAllowed(previous.value, basis, current)
              ? previous.value
              : latestPeriod(basis, current.kind);
            return { status: "ready", basis, value };
          });
        })
        .catch(() => undefined);
    };
    window.addEventListener(PERIOD_BASIS_CHANGED_EVENT, listener);
    return () => {
      controller?.abort();
      window.removeEventListener(PERIOD_BASIS_CHANGED_EVENT, listener);
    };
  }, [screenId, optionsKey]);
  return { state, setValue };
}

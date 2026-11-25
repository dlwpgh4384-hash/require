import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  createPreferenceSaver,
  getCommonUserPreference,
  PERIOD_CHANGED_EVENT,
  type PeriodChangedDetail,
} from "./userPreferences";

/**
 * 연도만 고르는 화면(UI-01·05·11·12)의 고른 연도를 TB_SY_USER_PREF(PERIOD)에 저장하고 다시 열면 읽는다
 * (FR-0105, 월을 고르는 화면과 같은 저장 칸 {"year":2026}, 2026-11-24 사용자 결정).
 * 저장된 연도가 없거나 [min, max] 밖이거나 읽지 못하면 올해를 쓴다(안내 없음).
 * onLoaded는 저장된 연도를 처음 읽었을 때 한 번 부른다(화면이 쪽·선택을 맞출 때).
 */
export function useSavedYear(
  screenId: string,
  initial: number,
  min: number,
  max: number,
  onLoaded?: (year: number) => void,
) {
  const [year, setYearState] = useState(initial);
  const saver = useMemo(() => createPreferenceSaver("PERIOD", screenId), [screenId]);
  const touched = useRef(false);
  const loaded = useRef(onLoaded);
  loaded.current = onLoaded;
  useEffect(() => {
    const controller = new AbortController();
    getCommonUserPreference("PERIOD", screenId, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        saver.setToken(response.data.modDttm);
        // 읽는 사이 사람이 연도를 바꿨으면 그 값을 둔다.
        if (touched.current) return;
        let saved: number | null = null;
        try {
          const parsed = response.data.prefVal === null ? null : JSON.parse(response.data.prefVal);
          if (parsed && Number.isInteger(parsed.year)) saved = parsed.year;
        } catch {
          saved = null;
        }
        if (saved !== null && saved >= min && saved <= max && saved !== initial) {
          setYearState(saved);
          loaded.current?.(saved);
        }
      })
      .catch(() => undefined);
    return () => controller.abort();
    // 화면을 처음 열 때 한 번만 읽는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screenId, saver]);
  // 다른 화면(월 마감 [확인하기])이 이 화면 연도를 바꿔 저장하면, 이미 열린 탭도 그 해로 바꾼다(다시 저장하지 않음).
  useEffect(() => {
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<PeriodChangedDetail>).detail;
      if (!detail || detail.screenId !== screenId) return;
      let next: number | null = null;
      try {
        const parsed = JSON.parse(detail.prefVal);
        if (parsed && Number.isInteger(parsed.year)) next = parsed.year;
      } catch {
        next = null;
      }
      if (next === null || next < min || next > max) return;
      saver.setToken(detail.modDttm);
      touched.current = true;
      setYearState(next);
      loaded.current?.(next);
    };
    window.addEventListener(PERIOD_CHANGED_EVENT, listener);
    return () => window.removeEventListener(PERIOD_CHANGED_EVENT, listener);
  }, [screenId, saver, min, max]);
  const setYear = useCallback(
    (next: number) => {
      touched.current = true;
      setYearState(next);
      void saver.save(JSON.stringify({ year: next })).catch(() => undefined);
    },
    [saver],
  );
  return [year, setYear] as const;
}

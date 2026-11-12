import { useCallback, useEffect, useState } from "react";
import { useOptionalUserPreferences } from "../../pages/userpreferences/UserPreferencesProvider";
import {
  getCommonUserPreference,
  putCommonUserPreference,
} from "../../pages/userpreferences/userPreferences";
import { DEFAULT_PAGE_SIZE, type ListColumn } from "./listTypes";

/**
 * SFR-010-04·05: 한 화면 건수와 좁게 보기는 계정에 한 값(사용자 메뉴 › 화면 설정과 같은 값)이라
 * 목록에서 바꿔도 모든 목록에 똑같이 적용한다. 설정 저장소가 없으면(부품 단독 시험) 화면 안에서만 기억한다.
 */
export function useListPresentation() {
  const preferences = useOptionalUserPreferences();
  const [localSize, setLocalSize] = useState(DEFAULT_PAGE_SIZE);
  const [localDense, setLocalDense] = useState(false);
  const states = preferences?.states;
  const pageSize =
    states?.PAGE_SIZE.data?.found && states.PAGE_SIZE.data.prefVal
      ? Number(states.PAGE_SIZE.data.prefVal)
      : preferences
        ? DEFAULT_PAGE_SIZE
        : localSize;
  const dense = states?.DENSE.data?.found
    ? states.DENSE.data.prefVal === "좁게"
    : preferences
      ? false
      : localDense;
  const setPageSize = useCallback(
    (value: number) => {
      if (!preferences) return setLocalSize(value);
      void preferences
        .save(
          "PAGE_SIZE",
          String(value),
          preferences.states.PAGE_SIZE.data?.modDttm ?? null,
        )
        .catch(() => undefined);
    },
    [preferences],
  );
  const setDense = useCallback(
    (value: boolean) => {
      if (!preferences) return setLocalDense(value);
      void preferences
        .save(
          "DENSE",
          value ? "좁게" : "넓게",
          preferences.states.DENSE.data?.modDttm ?? null,
        )
        .catch(() => undefined);
    },
    [preferences],
  );
  return { pageSize, dense, setPageSize, setDense };
}

/** SFR-010-05: 열 설정은 화면마다 기억한다(TB_SY_USER_PREF, PREF_TYPE=COLS, 키=화면 번호, 값=숨긴 열). */
export function useListColumns<T>(screenId: string, columns: ListColumn<T>[]) {
  const defaults = columns
    .filter((column) => column.hiddenByDefault && !column.required)
    .map((column) => column.key);
  const [hidden, setHidden] = useState<string[]>(defaults);
  const [token, setToken] = useState<string | null>(null);
  const preferences = useOptionalUserPreferences();
  useEffect(() => {
    if (!preferences) return;
    const controller = new AbortController();
    getCommonUserPreference("COLS", screenId, controller.signal).then(
      (result) => {
        if (controller.signal.aborted) return;
        setToken(result.data.modDttm);
        if (result.data.found && result.data.prefVal) {
          try {
            const value: unknown = JSON.parse(result.data.prefVal);
            if (Array.isArray(value))
              setHidden(value.filter((item) => typeof item === "string"));
          } catch {
            /* 형식이 틀린 값은 무시하고 기본값을 쓴다. */
          }
        }
      },
      () => undefined,
    );
    return () => controller.abort();
  }, [preferences, screenId]);
  const persist = (next: string[]) => {
    setHidden(next);
    if (!preferences) return;
    void putCommonUserPreference("COLS", screenId, JSON.stringify(next), token)
      .then((result) => setToken(result.data.modDttm))
      .catch(() => undefined);
  };
  const required = new Set(
    columns.filter((column) => column.required).map((column) => column.key),
  );
  return {
    hidden: hidden.filter((key) => !required.has(key)),
    visible: columns.filter(
      (column) => column.required || !hidden.includes(column.key),
    ),
    toggle: (key: string) =>
      required.has(key)
        ? undefined
        : persist(
            hidden.includes(key)
              ? hidden.filter((item) => item !== key)
              : [...hidden, key],
          ),
    reset: () => persist(defaults),
  };
}

export type PreferenceType =
  | "FAV"
  | "RECENT"
  | "MENU_FOLD"
  | "PAGE_SIZE"
  | "DENSE"
  | "PERIOD"
  | "COLS"
  /** 금액 단위(키 GLOBAL, 값 원·천 원·백만 원). 단위 고르기(MoneyUnitSelect)가 직접 읽고 저장한다. */
  | "AMT_UNIT";
export interface UserPreferenceResult {
  prefTypeCd: PreferenceType;
  prefKey: string;
  found: boolean;
  prefVal: string | null;
  modDttm: string | null;
}
export interface PeriodBasis {
  latestYm: string;
  cashBaseYm: string;
  closedMonths: string[];
}
export interface UserPreferenceResponse {
  success: true;
  meta: {
    periodBasis?: PeriodBasis | null;
    periodBasisError?: string;
    currentYearMonth?: string;
  };
  data: UserPreferenceResult;
}
export const preferenceConflict =
  "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.";
export const preferenceSaveError =
  "설정을 저장하지 못했습니다. 다시 시도해 주세요.";
export class UserPreferenceRequestError extends Error {
  constructor(
    public status: number,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(status === 409 ? preferenceConflict : preferenceSaveError);
  }
}

async function preferenceRequest(
  type: PreferenceType,
  key: string,
  init?: RequestInit,
): Promise<UserPreferenceResponse> {
  const response = await fetch(
    `/api/common/user-preferences/${encodeURIComponent(type)}/${encodeURIComponent(key)}`,
    init,
  );
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    // Do not display untrusted internal server messages, including SQL errors.
    const errors =
      response.status === 400 && Array.isArray(body.errors)
        ? body.errors
            .filter((error: { field?: string }) => error.field === "prefVal")
            .map(() => ({
              field: "prefVal",
              message:
                type === "PAGE_SIZE"
                  ? "목록 건수는 20, 50, 100 중에서 입력해 주세요."
                  : "설정 값을 확인해 주세요.",
            }))
        : [];
    throw new UserPreferenceRequestError(response.status, errors);
  }
  const data = body.data;
  if (
    !data ||
    data.prefTypeCd !== type ||
    data.prefKey !== key ||
    typeof data.found !== "boolean" ||
    !(data.prefVal === null || typeof data.prefVal === "string") ||
    !(data.modDttm === null || typeof data.modDttm === "string")
  ) {
    throw new UserPreferenceRequestError(500);
  }
  return body as UserPreferenceResponse;
}

export function getCommonUserPreference(
  type: PreferenceType,
  key = "GLOBAL",
  signal?: AbortSignal,
) {
  return preferenceRequest(type, key, { signal });
}
export function putCommonUserPreference(
  type: PreferenceType,
  key: string,
  prefVal: string,
  modDttm: string | null,
) {
  return preferenceRequest(type, key, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ prefVal, modDttm }),
  });
}

/**
 * 같은 설정(type·key)을 하나씩 차례로 저장한다(00-1 코드 리뷰 2026-11-23).
 * 앞 저장이 받은 수정 시각을 다음 저장에 쓰고, 409이면 한 번 다시 읽어 그 시각으로 다시 저장한다.
 * 빨리 연달아 바꿔도 마지막 값이 저장되고, 409를 조용히 버려 옛 값이 남는 일이 없다.
 */
/**
 * 다른 화면이 어떤 화면의 기간(PERIOD)을 바꿔 저장했음을 알린다(월 마감 [확인하기]). 이미 탭으로 열린 그 화면이
 * 저장된 기간으로 바로 다시 읽게 한다(2026-11-24 사용자 결정).
 */
export const PERIOD_CHANGED_EVENT = "period-preference-changed";
export interface PeriodChangedDetail {
  screenId: string;
  prefVal: string;
  modDttm: string | null;
}
export function announcePeriodChanged(detail: PeriodChangedDetail) {
  window.dispatchEvent(new CustomEvent<PeriodChangedDetail>(PERIOD_CHANGED_EVENT, { detail }));
}
export function createPreferenceSaver(type: PreferenceType, key: string) {
  let token: string | null = null;
  let chain: Promise<unknown> = Promise.resolve();
  const put = async (prefVal: string) => {
    try {
      const result = await putCommonUserPreference(type, key, prefVal, token);
      token = result.data.modDttm;
      return result;
    } catch (failure) {
      if (!(failure instanceof UserPreferenceRequestError) || failure.status !== 409) throw failure;
      token = (await getCommonUserPreference(type, key)).data.modDttm;
      const result = await putCommonUserPreference(type, key, prefVal, token);
      token = result.data.modDttm;
      return result;
    }
  };
  return {
    /** 읽어 온 수정 시각을 알려 준다(저장 중이면 저장 결과가 더 새것이라 무시). */
    setToken(value: string | null) {
      token = value;
    },
    save(prefVal: string): Promise<UserPreferenceResponse> {
      const next = chain.then(() => put(prefVal));
      chain = next.catch(() => undefined);
      return next;
    },
  };
}

export function preferenceScreenIds(value: string | null): string[] {
  if (value === null) return [];
  const parsed: unknown = JSON.parse(value);
  if (!Array.isArray(parsed) || parsed.some((item) => typeof item !== "string"))
    throw new Error("화면 목록을 다시 불러와 주세요.");
  return parsed;
}
export function recentScreenIds(ids: string[], screenId: string) {
  return [screenId, ...ids.filter((id) => id !== screenId)].slice(0, 5);
}
export function visiblePreferenceScreens<
  T extends { scrnId: string | null; pcOnlyYn: "Y" | "N" },
>(
  ids: string[],
  menus: T[],
  mobile: boolean,
  available: (screenId: string) => boolean,
): T[] {
  return ids.flatMap((id) => {
    const item = menus.find((entry) => entry.scrnId === id);
    return item && available(id) && (!mobile || item.pcOnlyYn !== "Y")
      ? [item]
      : [];
  });
}

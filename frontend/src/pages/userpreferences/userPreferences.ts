export type PreferenceType =
  | "FAV"
  | "RECENT"
  | "MENU_FOLD"
  | "PAGE_SIZE"
  | "DENSE"
  | "PERIOD";
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
  meta: { periodBasis?: PeriodBasis | null; periodBasisError?: string };
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

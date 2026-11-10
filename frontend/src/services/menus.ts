export interface MenuItem {
  menuId: string;
  upperMenuId: string | null;
  menuNm: string;
  scrnId: string | null;
  menuLvl: number;
  sortOrd: number;
  pcOnlyYn: "Y" | "N";
  iconNm: string | null;
}
export interface ScreenState {
  available: boolean;
  pcOnlyYn: "Y" | "N";
  menuId: string | null;
  menuNm: string | null;
}
export interface MenuResponse {
  success: true;
  meta: { screenStates: Record<string, ScreenState>; parentMenuIds: string[] };
  data: { success: true; meta: Record<string, unknown>; data: MenuItem[] };
}
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
const isNullableString = (value: unknown) =>
  value === null || typeof value === "string";
const isYesNo = (value: unknown) => value === "Y" || value === "N";

function isMenuItem(value: unknown): value is MenuItem {
  return (
    isRecord(value) &&
    typeof value.menuId === "string" &&
    isNullableString(value.upperMenuId) &&
    typeof value.menuNm === "string" &&
    isNullableString(value.scrnId) &&
    Number.isInteger(value.menuLvl) &&
    Number.isInteger(value.sortOrd) &&
    isYesNo(value.pcOnlyYn) &&
    isNullableString(value.iconNm)
  );
}

function isScreenState(value: unknown): value is ScreenState {
  return (
    isRecord(value) &&
    typeof value.available === "boolean" &&
    isYesNo(value.pcOnlyYn) &&
    isNullableString(value.menuId) &&
    isNullableString(value.menuNm)
  );
}

export async function loadMenus(signal: AbortSignal): Promise<MenuResponse> {
  const response = await fetch("/api/common/menus", {
    signal,
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error("메뉴 조회 오류");
  const value: unknown = await response.json();
  if (
    !isRecord(value) ||
    value.success !== true ||
    !isRecord(value.data) ||
    value.data.success !== true ||
    !isRecord(value.data.meta) ||
    !Array.isArray(value.data.data) ||
    !value.data.data.every(isMenuItem) ||
    !isRecord(value.meta) ||
    !isRecord(value.meta.screenStates) ||
    !Object.values(value.meta.screenStates).every(isScreenState) ||
    !Array.isArray(value.meta.parentMenuIds) ||
    !value.meta.parentMenuIds.every((id) => typeof id === "string")
  ) {
    throw new Error("메뉴 응답 형식 오류");
  }
  return value as unknown as MenuResponse;
}

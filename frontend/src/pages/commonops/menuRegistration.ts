export interface MenuRegistrationItem {
  menuId: string;
  upperMenuId: string | null;
  menuNm: string;
  scrnId: string | null;
  menuLvl: 1 | 2;
  sortOrd: number;
  useYn: "Y" | "N";
  pcOnlyYn: "Y" | "N";
  lockYn: "Y" | "N";
  iconNm: string | null;
}
export type MenuCreateInput =
  | { menuLvl: 1; menuNm: string }
  | {
      menuLvl: 2;
      menuNm: string;
      upperMenuId: string;
      scrnId: string;
      pcOnlyYn: "Y" | "N";
    };
export interface MenuRegistrationOptions {
  upperMenus: { menuId: string; menuNm: string }[];
  screens: { scrnId: string; menuNm: string }[];
}
export class MenuRegistrationRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function registrationRequest<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    throw new MenuRegistrationRequestError(
      response.status,
      response.status === 400 && typeof body.message === "string"
        ? body.message
        : "메뉴를 처리하지 못했습니다. 다시 시도해 주세요.",
      response.status === 400 && Array.isArray(body.errors) ? body.errors : [],
    );
  }
  if (!body.data || typeof body.data !== "object" || Array.isArray(body.data))
    throw new Error("응답을 다시 확인해 주세요.");
  return body.data as T;
}
export async function getCommonMenuOptions(
  menuLvl: 1 | 2,
  signal?: AbortSignal,
) {
  const result = await registrationRequest<MenuRegistrationOptions>(
    `/api/common/menu-options?${new URLSearchParams({ menuLvl: String(menuLvl) })}`,
    { signal },
  );
  if (!Array.isArray(result.upperMenus) || !Array.isArray(result.screens))
    throw new Error("선택 목록 응답을 다시 확인해 주세요.");
  return result;
}
export const postCommonMenu = (input: MenuCreateInput) =>
  registrationRequest<MenuRegistrationItem>("/api/common/menus", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });

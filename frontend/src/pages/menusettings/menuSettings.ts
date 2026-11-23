import type { MenuRegistrationItem } from "../commonops/menuRegistrationApi";
import { MenuRegistrationRequestError } from "../commonops/menuRegistrationApi";

export type MenuSettingItem = MenuRegistrationItem;
export interface MenuUpdateItem {
  menuId: string;
  menuNm: string;
  upperMenuId: string | null;
  sortOrd: number;
  useYn?: "Y" | "N";
  pcOnlyYn?: "Y" | "N";
}
export interface MenuSettingsInput {
  items: MenuUpdateItem[];
  movedMenuIds: string[];
  /** 조회 때 받은 메뉴 표 수정 시각. 그 뒤 다른 저장이 있으면 409(FR-0106). */
  modifiedAt?: string | null;
}
export interface MenuSettingsResult {
  items: MenuSettingItem[];
  modifiedAt?: string | null;
}

async function settingsRequest(
  init?: RequestInit,
): Promise<MenuSettingsResult> {
  const response = await fetch("/api/common/menu-settings", init);
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new MenuRegistrationRequestError(
      response.status,
      (response.status === 400 || response.status === 409) && typeof body.message === "string"
        ? body.message
        : "메뉴를 처리하지 못했습니다. 다시 시도해 주세요.",
      response.status === 400 && Array.isArray(body.errors) ? body.errors : [],
    );
  if (!Array.isArray(body.data?.items))
    throw new Error("메뉴 응답을 확인해 주세요.");
  return body.data;
}
export const getCommonMenuSettings = (signal?: AbortSignal) =>
  settingsRequest({ signal });
export const putCommonMenuSettings = (input: MenuSettingsInput) =>
  settingsRequest({
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });

export function updateItem(row: MenuSettingItem): MenuUpdateItem {
  const common = {
    menuId: row.menuId,
    menuNm: row.menuNm,
    upperMenuId: row.upperMenuId,
    sortOrd: row.sortOrd,
  };
  return row.menuLvl === 1
    ? common
    : { ...common, useYn: row.useYn, pcOnlyYn: row.pcOnlyYn };
}
export const menuOrder = (a: MenuSettingItem, b: MenuSettingItem) =>
  a.sortOrd - b.sortOrd || a.menuId.localeCompare(b.menuId);
export function menuTree(rows: MenuSettingItem[]) {
  const groups = rows.filter((row) => row.menuLvl === 1).sort(menuOrder);
  const ordered = groups.flatMap((group) => [
    group,
    ...rows.filter((row) => row.upperMenuId === group.menuId).sort(menuOrder),
  ]);
  const ids = new Set(ordered.map((row) => row.menuId));
  return [
    ...ordered,
    ...rows.filter((row) => !ids.has(row.menuId)).sort(menuOrder),
  ];
}

/**
 * 시안 menuDrop: 대메뉴 위에 서브메뉴를 놓으면 그 묶음 끝, 메뉴 위에 놓으면 그 메뉴 자리.
 * 같은 묶음에서 위로 끌면 놓은 줄 앞, 아래로 끌면 놓은 줄 뒤. 묶음을 메뉴 줄에 놓으면 그 메뉴의 묶음 자리.
 */
export function moveMenu(
  rows: MenuSettingItem[],
  sourceId: string,
  targetId: string,
): MenuSettingItem[] {
  const source = rows.find((row) => row.menuId === sourceId);
  let target = rows.find((row) => row.menuId === targetId);
  if (source?.menuLvl === 1 && target && target.menuLvl !== 1)
    target = rows.find(
      (row) => row.menuLvl === 1 && row.menuId === target?.upperMenuId,
    );
  if (!source || !target || sourceId === target.menuId) return rows;
  const targetKey = target.menuId;
  const parent =
    source.menuLvl === 1
      ? null
      : target.menuLvl === 1
        ? target.menuId
        : target.upperMenuId;
  if (source.lockYn === "Y" && parent !== source.upperMenuId)
    throw new Error(
      `「${source.menuNm}」은 시스템 묶음 메뉴라 다른 묶음으로 옮길 수 없습니다.`,
    );
  const siblings = rows
    .filter((row) => row.menuId !== sourceId && row.upperMenuId === parent)
    .sort(menuOrder);
  let index = siblings.findIndex((row) => row.menuId === targetKey);
  if (index >= 0 && parent === source.upperMenuId) {
    const before = rows
      .filter((row) => row.upperMenuId === parent)
      .sort(menuOrder)
      .map((row) => row.menuId);
    if (before.indexOf(sourceId) < before.indexOf(targetKey)) index += 1;
  }
  siblings.splice(index < 0 ? siblings.length : index, 0, {
    ...source,
    upperMenuId: parent,
  });
  const changed = new Map(
    siblings.map((row, i) => [row.menuId, { ...row, sortOrd: i + 1 }]),
  );
  if (parent !== source.upperMenuId)
    rows
      .filter(
        (row) =>
          row.menuId !== sourceId && row.upperMenuId === source.upperMenuId,
      )
      .sort(menuOrder)
      .forEach((row, i) => changed.set(row.menuId, { ...row, sortOrd: i + 1 }));
  return rows.map((row) => changed.get(row.menuId) ?? row);
}

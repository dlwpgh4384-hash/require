import { useCallback, useMemo } from "react";
import { useOptionalAccountSession } from "../../pages/commonops/AccountSessionBoundary";
import type { AccountPermission } from "../../pages/commonops/accountAccessApi";

/**
 * 메뉴 × 동작 권한(SFR-008-02, 로그인(UI-22) ⑦-5). 서버가 내 정보(meta.permissions)로 준 표만 본다.
 * 역할 이름으로 판단하지 않는다. 역할이 여러 개면 서버가 넓은 쪽으로 합쳐 보낸다(하나라도 Y면 Y).
 */
export type PermissionAction = "REG" | "MOD" | "DEL" | "UPLOAD" | "EXCEL" | "CNFM";
export const PERMISSION_ACTIONS: readonly PermissionAction[] = [
  "REG",
  "MOD",
  "DEL",
  "UPLOAD",
  "EXCEL",
  "CNFM",
];
const FIELDS: Record<PermissionAction, keyof AccountPermission> = {
  REG: "createYn",
  MOD: "updateYn",
  DEL: "deleteYn",
  UPLOAD: "uploadYn",
  EXCEL: "downloadYn",
  CNFM: "cnfmYn",
};

export interface ScreenPermission {
  /** 조회 권한 */
  view: boolean;
  can: (action: PermissionAction) => boolean;
}
const FULL: ScreenPermission = { view: true, can: () => true };

/** 권한 표에서 한 화면의 권한. 표가 없으면(로그인 틀 밖, 화면 단위 시험) 모두 허용. */
export function screenPermission(
  permissions: readonly AccountPermission[] | undefined,
  screenId: string,
): ScreenPermission {
  if (!permissions) return FULL;
  const rows = permissions.filter(
    (row) => row.screenId === screenId && row.readYn === "Y",
  );
  return {
    view: rows.length > 0,
    can: (action) => rows.some((row) => row[FIELDS[action]] === "Y"),
  };
}

function useAccountPermissions() {
  const account = useOptionalAccountSession();
  return account ? (account.meta.permissions ?? []) : undefined;
}

/** 한 화면의 동작 권한: `usePermission("UI-01").can("REG")`. */
export function usePermission(screenId: string): ScreenPermission {
  const permissions = useAccountPermissions();
  return useMemo(
    () => screenPermission(permissions, screenId),
    [permissions, screenId],
  );
}

/** 다른 화면으로 가는 단추·링크를 그릴지: `canView("UI-04")`(조회 권한). */
export function useCanViewScreen(): (screenId: string | null | undefined) => boolean {
  const permissions = useAccountPermissions();
  return useCallback(
    (screenId) =>
      !!screenId && screenPermission(permissions, screenId).view,
    [permissions],
  );
}

/**
 * 민감 칸(급여·단가·원가·계좌)을 볼 수 있는지. 서버 meta.sensitive(역할의 민감 체크) 하나만 본다(⑨-1).
 * 로그인 틀 밖(화면 단위 시험)에서는 보이는 것으로 본다.
 */
export function useSensitiveView(): boolean {
  const account = useOptionalAccountSession();
  return account ? account.meta.sensitive === true : true;
}

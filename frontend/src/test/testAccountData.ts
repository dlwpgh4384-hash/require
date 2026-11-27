import type {
  AccountInfo,
  AccountMeta,
  AccountPermission,
} from "../pages/commonops/accountAccessApi";

/**
 * 시험용 로그인 계정. 권한은 sql/DML_BaseData.sql의 처음 권한표(TB_SY_ROLE_MENU_AUTH)와 같다.
 * 로그인(UI-22) ⑦-8: 경영진 영업 파이프라인(M011) 확정 Y, PM M011 7개 N.
 * 민감 체크(SNSTV_VIEW_YN)는 관리자·경영진·재무 Y, 파트장·PM N.
 */
export type TestRole = "ADMIN" | "EXEC" | "FIN" | "SALES_LDR" | "PM";

/** [메뉴 ID, 화면 ID, PC 전용] — DML TB_SY_MENU 서브메뉴 30개. */
export const TEST_MENUS: readonly [string, string, "Y" | "N"][] = [
  ["M001", "UI-16", "N"],
  ["M002", "UI-15", "N"],
  ["M003", "UI-02", "N"],
  ["M004", "UI-01", "N"],
  ["M005", "UI-31", "N"],
  ["M006", "UI-12", "N"],
  ["M007", "UI-05", "N"],
  ["M008", "UI-11", "N"],
  ["M009", "UI-28", "N"],
  ["M010", "UI-17", "N"],
  ["M011", "UI-29", "N"],
  ["M012", "UI-33", "N"],
  ["M013", "UI-13", "N"],
  ["M014", "UI-03", "N"],
  ["M015", "UI-04", "N"],
  ["M016", "UI-19", "N"],
  ["M017", "UI-32", "N"],
  ["M018", "UI-10", "Y"],
  ["M019", "UI-07", "N"],
  ["M020", "UI-08", "N"],
  ["M021", "UI-09", "N"],
  ["M022", "UI-18", "N"],
  ["M023", "UI-21", "N"],
  ["M024", "UI-14", "Y"],
  ["M025", "UI-26", "N"],
  ["M026", "UI-30", "N"],
  ["M027", "UI-23", "Y"],
  ["M028", "UI-24", "Y"],
  ["M029", "UI-25", "Y"],
  ["M030", "UI-27", "Y"],
];
const ALL = "YYYYYYY";
const NONE = "NNNNNNN";
/** 조회·등록·수정·삭제·업로드·다운로드·확정 */
function flags(role: TestRole, menuId: string): string {
  const n = Number(menuId.slice(1));
  switch (role) {
    case "ADMIN":
    case "FIN":
      return ALL;
    case "EXEC":
      if (n >= 27) return NONE;
      return n === 11 ? "YNNNNYY" : "YNNNNYN";
    case "SALES_LDR":
      if ([14, 15, 18, 19, 20, 21, 22, 23, 24].includes(n) || n >= 27)
        return NONE;
      return "YYYNNYN";
    case "PM":
      if (n === 11 || (n >= 14 && n <= 24) || n >= 27) return NONE;
      return "YYYNNNN";
  }
}
const SENSITIVE: Record<TestRole, boolean> = {
  ADMIN: true,
  EXEC: true,
  FIN: true,
  SALES_LDR: false,
  PM: false,
};

/** 역할 여럿이면 서버처럼 메뉴마다 넓은 쪽(하나라도 Y). 볼 수 없는 메뉴 줄도 서버처럼 N으로 보낸다. */
export function testPermissions(roles: readonly TestRole[]): AccountPermission[] {
  return TEST_MENUS.map(([menuId, screenId, pcOnlyYn]) => {
    const merged = [0, 1, 2, 3, 4, 5, 6].map((i) =>
      roles.some((role) => flags(role, menuId)[i] === "Y") ? "Y" : "N",
    );
    return {
      menuId,
      screenId,
      pcOnlyYn,
      readYn: merged[0],
      createYn: merged[1],
      updateYn: merged[2],
      deleteYn: merged[3],
      uploadYn: merged[4],
      downloadYn: merged[5],
      cnfmYn: merged[6],
    };
  });
}

export interface TestAccount {
  info: AccountInfo;
  meta: AccountMeta;
}
export function testAccount(
  roles: readonly TestRole[],
  overrides: {
    permissions?: AccountPermission[];
    sensitive?: boolean;
    /** 시험이 만든 화면(예: 메뉴 시험의 ADDED)에 7개 Y 권한 줄을 더한다. */
    extraScreens?: readonly string[];
  } = {},
): TestAccount {
  return {
    info: {
      userId: roles.length ? roles[0].toLowerCase() : "nobody",
      name: roles[0] === "ADMIN" ? "관리자" : "시험 사용자",
      roles: [...roles],
      parts: [],
    },
    meta: {
      permissions: [
        ...(overrides.permissions ?? testPermissions(roles)),
        ...(overrides.extraScreens ?? []).map((screenId) => ({
          menuId: `TEST-${screenId}`,
          screenId,
          pcOnlyYn: "N",
          readYn: "Y",
          createYn: "Y",
          updateYn: "Y",
          deleteYn: "Y",
          uploadYn: "Y",
          downloadYn: "Y",
          cnfmYn: "Y",
        })),
      ],
      passwordChangeRequired: false,
      sensitive:
        overrides.sensitive ?? roles.some((role) => SENSITIVE[role]),
    },
  };
}

/** 기본 계정: 시스템 관리자(처음 권한표 그대로, 30개 메뉴 모두 7개 Y, 민감 Y). */
export const ADMIN_ACCOUNT = testAccount(["ADMIN"]);
// 화면 틀(내 정보 가짜 응답, setup.ts)이 읽는 지금 계정. 모듈을 서로 부르지 않게 전역 한 자리에 둔다.
const KEY = "__requireTestAccount";
type Holder = { [KEY]?: TestAccount };
/** 지금 시험 계정. 각 시험이 끝나면 관리자로 돌아간다(setup.ts). */
export function currentTestAccount(): TestAccount {
  return (globalThis as Holder)[KEY] ?? ADMIN_ACCOUNT;
}
export function setTestAccount(account: TestAccount) {
  (globalThis as Holder)[KEY] = account;
}
export function resetTestAccount() {
  delete (globalThis as Holder)[KEY];
}


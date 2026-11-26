import { configure } from "@testing-library/react";
import { expect, vi } from "vitest";

// 바쁜 PC(품질 검사)에서 1초 기본값 때문에 깜빡 실패하지 않게, findBy·waitFor 기본 기다림을 3초로 둔다.
// 확인하는 내용은 그대로이고 기다리는 시간만 늘린다.
configure({ asyncUtilTimeout: 3000 });

// jsdom에는 URL.revokeObjectURL이 없다. 엑셀 내려받기는 1초 뒤에 정리하는데, 그때는 시험이 바꿔 둔 URL이
// 이미 원래대로 돌아와 「not a function」 오류가 시험 밖에서 난다. 시험 환경에만 빈 함수를 둔다.
if (typeof URL.revokeObjectURL !== "function") URL.revokeObjectURL = () => {};

// 로그인·세션·알림을 직접 시험하는 파일(그러면 실제 API 그대로).
const OWN_LOGIN_TESTS = [
  "/app/AccountNavigation.test.",
  "/app/accountRoutes.test.",
  "/pages/commonops/Account",
  "/pages/commonops/accountNetwork.test.",
  "/pages/notifications/",
  "/pages/session/",
  "/pages/mfa/",
];
function ownLoginTest() {
  const path = (expect.getState().testPath ?? "").split("\\").join("/");
  return OWN_LOGIN_TESTS.some((part) => path.includes(part));
}

// 로그인(UI-22) 작업 뒤 앱은 처음에 내 정보(/api/common/my-info)로 로그인 상태를 확인한다.
// 로그인 자체를 시험하지 않는 기존 화면 시험은 로그인 전 개발 사용자(admin, 모든 화면 권한)로 로그인된 상태로 본다.
// 시험 파일이 내 정보·로그인 경계를 직접 다루면(로그인·세션·2차 인증 시험) 실제 그대로 둔다.
vi.mock("../pages/commonops/accountAccessApi", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../pages/commonops/accountAccessApi")
    >();
  const screens = Array.from(
    { length: 60 },
    (_, index) => `UI-${String(index + 1).padStart(2, "0")}`,
  );
  const permissions = screens.map((screenId) => ({
    menuId: screenId,
    screenId,
    readYn: "Y",
    pcOnlyYn: "N",
    createYn: "Y",
    updateYn: "Y",
    deleteYn: "Y",
    uploadYn: "Y",
    downloadYn: "Y",
    cnfmYn: "Y",
  }));
  return {
    ...actual,
    createAccountAccessApi: (
      ...args: Parameters<typeof actual.createAccountAccessApi>
    ) => {
      const api = actual.createAccountAccessApi(...args);
      if (ownLoginTest()) return api;
      return {
        ...api,
        myInfo: async () => ({
          success: true as const,
          data: {
            userId: "admin",
            name: "관리자",
            roles: ["ADMIN", "FIN", "SALES_LDR"],
            parts: [],
          },
          // 시험이 만든 화면(예: 추가한 메뉴)까지 모두 볼 수 있는 개발 사용자로 본다.
          meta: {
            permissions: Object.assign([...permissions], { some: () => true }),
            passwordChangeRequired: false,
            sensitive: true,
          },
        }),
      };
    },
  };
});

// 같은 기존 화면 시험에서 세션 남은 시간도 로그인된 30분으로 본다(세션 확인 실패로 화면이 막히지 않게).
vi.mock("../pages/session/sessionApi", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("../pages/session/sessionApi")>();
  return {
    ...actual,
    createSessionApi: (...args: Parameters<typeof actual.createSessionApi>) => {
      const api = actual.createSessionApi(...args);
      if (ownLoginTest()) return api;
      const timing = () => ({
        remainingSeconds: 1800,
        sessionMinutes: 30,
        serverRespondedAt: new Date().toISOString(),
      });
      return {
        ...api,
        remaining: async () => timing(),
        extend: async () => timing(),
      };
    },
  };
});

// 같은 기존 화면 시험에서 머리말 알림 종은 빈 목록으로 본다(화면 시험이 세는 요청 수가 그대로가 되게).
vi.mock("../pages/notifications/notificationApi", async (importOriginal) => {
  const actual =
    await importOriginal<
      typeof import("../pages/notifications/notificationApi")
    >();
  return {
    ...actual,
    createNotificationApi: (
      ...args: Parameters<typeof actual.createNotificationApi>
    ) => {
      const api = actual.createNotificationApi(...args);
      if (ownLoginTest()) return api;
      return { ...api, list: async () => ({ items: [], unreadCount: 0 }) };
    },
    // 알림 종이 기본으로 쓰는 공용 API도 같다.
    notificationApi: {
      ...actual.notificationApi,
      list: () =>
        ownLoginTest()
          ? actual.notificationApi.list()
          : Promise.resolve({ items: [], unreadCount: 0 }),
    },
  };
});

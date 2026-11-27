import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider } from "react-router-dom";
import type { ReactNode } from "react";
import { createAppRouter } from "./router";
import type { ShellMenuResponse } from "./NavigationRoot";
import type {
  AccountAccessApi,
  AccountInfo,
  AccountPermission,
  AccountResponse,
} from "../pages/commonops/accountAccessApi";

const dependencies = vi.hoisted(() => ({
  api: undefined as AccountAccessApi | undefined,
}));
vi.mock("../pages/commonops/accountAccessApi", async (importOriginal) => ({
  ...(await importOriginal<
    typeof import("../pages/commonops/accountAccessApi")
  >()),
  createAccountAccessApi: () => dependencies.api,
}));
vi.mock("../pages/userpreferences", () => ({
  UserPreferencesProvider: ({ children }: { children: ReactNode }) => children,
}));
vi.mock("../pages/notifications/NotificationBell", () => ({
  NotificationBell: () => null,
}));
vi.mock("../pages/common/CommonNavigationPage", async () => {
  const { NavigationRoot } = await import("./NavigationRoot");
  const { useScreenDirty } = await import("../shared/layout/ScreenTabs");
  const { UserPreferenceTools } = await import(
    "../pages/userpreferences/UserPreferenceTools"
  );
  function Draft({ id }: { id: string }) {
    const dirty = useScreenDirty();
    return (
      <input
        aria-label={`${id} 입력`}
        defaultValue=""
        onChange={() => dirty(id, true)}
      />
    );
  }
  return {
    // 실제 화면 틀처럼 헤더 오른쪽에 사용자 메뉴(내 정보·비밀번호 변경·로그아웃·자동 로그아웃 시계)를 둔다.
    CommonNavigationPage: () => (
      <NavigationRoot
        tabs
        renderScreen={(id) => <Draft id={id} />}
        extensions={(context) => ({
          actions: (
            <UserPreferenceTools
              menus={[]}
              mobile={false}
              available={context.available}
              onNavigate={context.open}
              dirty={context.dirty}
            />
          ),
        })}
      />
    ),
  };
});

let router: ReturnType<typeof createAppRouter> | undefined;
function permission(screenId: string): AccountPermission {
  return {
    menuId: screenId,
    screenId,
    readYn: "Y",
    pcOnlyYn: "N",
    createYn: "N",
    updateYn: "N",
    deleteYn: "N",
    uploadYn: "N",
    downloadYn: "N",
    cnfmYn: "N",
  };
}
function identity(
  permissions = [permission("UI-06"), permission("UI-12")],
  roles = ["FIN"],
  roleNames = ["재무 담당"],
): AccountResponse<AccountInfo> {
  return {
    success: true,
    data: {
      userId: "finance-reader",
      name: "재무 사용자",
      roles,
      roleNames,
      parts: [],
    },
    meta: {
      permissions,
      remainingSeconds: 1800,
      sessionMinutes: 30,
      serverRespondedAt: new Date().toISOString(),
    },
  };
}
function menus(): ShellMenuResponse {
  return {
    success: true,
    data: ["UI-06", "UI-12"].map((id, index) => ({
      menuId: id,
      upperMenuId: null,
      menuNm: id,
      scrnId: id,
      menuLvl: 1,
      sortOrd: index,
      pcOnlyYn: "N",
      iconNm: null,
    })),
    meta: {
      screenAccess: ["UI-06", "UI-12"].map((scrnId) => ({
        scrnId,
        available: true,
        pcOnlyYn: "N",
      })),
    },
  };
}
function mount(path = "/screens/UI-06") {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  return render(<RouterProvider router={router} />);
}
beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  dependencies.api = {
    myInfo: vi.fn().mockResolvedValue(identity()),
    login: vi.fn(),
    logout: vi.fn(),
    find: vi.fn(),
    change: vi.fn(),
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response(JSON.stringify(menus()), {
          headers: { "Content-Type": "application/json" },
        }),
    ),
  );
});
afterEach(() => {
  cleanup();
  router?.dispose();
  router = undefined;
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("account route composition", () => {
  it.each(["ADMIN", "EXEC", "FIN", "SALES_LDR", "PM"])(
    "renders assigned menus, account buttons and editable drafts for %s",
    async (role) => {
      vi.mocked(dependencies.api!.myInfo).mockResolvedValue(
        identity(undefined, [role], [role]),
      );
      mount();
      const input = await screen.findByLabelText("UI-06 입력");
      expect(screen.getByRole("link", { name: "UI-06" })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "UI-12" })).toBeInTheDocument();
      // 임시 도구 줄 두 개는 없고, 헤더 하나에 사용자 단추만 있다.
      expect(screen.queryByTestId("account-tools")).not.toBeInTheDocument();
      expect(
        screen.queryByTestId("account-session-tools"),
      ).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole("button", { name: "사용자 메뉴" }));
      // 시안 userPop 순서: 내 정보 / 비밀번호 변경 / 화면 설정 / 단축키 보기 / 로그아웃, 맨 아래 시계
      expect(
        screen.getAllByRole("menuitem").map((item) => item.textContent),
      ).toEqual(["내 정보", "비밀번호 변경", "화면 설정", "단축키 보기", "로그아웃"]);
      expect(screen.getByTestId("user-menu-panel")).toHaveTextContent(
        /자동 로그아웃까지 \d{2}:\d{2}연장$/,
      );
      fireEvent.change(input, { target: { value: "역할별 입력 확인" } });
      expect(input).toHaveValue("역할별 입력 확인");
      expect(screen.getByTestId("screen-tab-ui-06")).toHaveTextContent("●");
      // 저장 안 한 탭이 있으면 로그아웃 전에 확인한다(명세 ③-4). 팝업은 아직 열려 있다.
      fireEvent.click(screen.getByRole("menuitem", { name: "로그아웃" }));
      expect(
        screen.getByRole("alertdialog", { name: "저장하지 않은 내용이 있습니다." }),
      ).toBeInTheDocument();
      expect(dependencies.api!.logout).not.toHaveBeenCalled();
    },
  );

  it("header user button and popup header follow 「첫 역할 외 n」", async () => {
    vi.mocked(dependencies.api!.myInfo).mockResolvedValue(
      identity(undefined, ["FIN", "SALES_LDR"], ["재무 담당", "영업 파트장"]),
    );
    mount();
    await screen.findByLabelText("UI-06 입력");
    expect(screen.getByRole("button", { name: "사용자 메뉴" })).toHaveTextContent(
      "재무 사용자 (재무 외 1)",
    );
    fireEvent.click(screen.getByRole("button", { name: "사용자 메뉴" }));
    const role = screen.getByTitle("재무 담당, 영업 파트장");
    expect(role).toHaveTextContent("재무 담당 외 1");
  });

  it("my info and password change open as dialogs from the user menu", async () => {
    mount();
    await screen.findByLabelText("UI-06 입력");
    fireEvent.click(screen.getByRole("button", { name: "사용자 메뉴" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "내 정보" }));
    expect(
      await screen.findByRole("dialog", { name: "내 정보" }),
    ).toHaveAttribute("data-screen-id", "UI-41");
    fireEvent.click(screen.getByTestId("account-info-close"));
    fireEvent.click(screen.getByRole("button", { name: "사용자 메뉴" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "비밀번호 변경" }));
    expect(
      screen.getByRole("dialog", { name: "비밀번호 변경" }),
    ).toBeInTheDocument();
    expect(router!.state.location.pathname).toBe("/screens/UI-06");
    expect(screen.getByLabelText("UI-06 입력")).toBeInTheDocument();
  });

  it("session extension shows the mockup toast", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).includes("/api/common/session/extend")
          ? new Response(
              JSON.stringify({
                success: true,
                data: {
                  remainingSeconds: 1800,
                  sessionMinutes: 30,
                  serverRespondedAt: new Date(Date.now() + 5000).toISOString(),
                },
                meta: {},
              }),
              { headers: { "Content-Type": "application/json" } },
            )
          : new Response(JSON.stringify(menus()), {
              headers: { "Content-Type": "application/json" },
            }),
      ),
    );
    mount();
    await screen.findByLabelText("UI-06 입력");
    fireEvent.click(screen.getByRole("button", { name: "사용자 메뉴" }));
    fireEvent.click(screen.getByTestId("session-extend"));
    expect(await screen.findByTestId("account-header-message")).toHaveTextContent(
      "자동 로그아웃 시간을 30분으로 다시 늘렸습니다",
    );
  });

  it("keeps bootstrap fetch unchanged and accepts its timing event", async () => {
    const request = window.fetch;
    mount();
    await screen.findByLabelText("UI-06 입력");
    expect(window.fetch).toBe(request);
    fireEvent.click(screen.getByRole("button", { name: "사용자 메뉴" }));
    fireEvent(
      window,
      new CustomEvent("account-session-timing", {
        detail: {
          remainingSeconds: 90,
          sessionMinutes: 30,
          serverRespondedAt: new Date(Date.now() + 1000).toISOString(),
        },
      }),
    );
    expect(screen.getByTestId("session-left")).toHaveTextContent("01:30");
  });

  it.each([false, true])(
    "removes revoked tabs while preserving the allowed dirty draft (revoked active: %s)",
    async (active) => {
      mount();
      const retained = await screen.findByLabelText("UI-06 입력");
      fireEvent.change(retained, { target: { value: "보존할 입력" } });
      await act(async () => {
        await router!.navigate("/screens/UI-12");
      });
      const revoked = await screen.findByLabelText("UI-12 입력");
      fireEvent.change(revoked, { target: { value: "제거할 입력" } });
      if (!active)
        await act(async () => {
          await router!.navigate("/screens/UI-06");
        });
      vi.mocked(dependencies.api!.myInfo).mockResolvedValue(
        identity([permission("UI-06")]),
      );
      fireEvent(window, new Event("account-permissions-changed"));
      await waitFor(() =>
        expect(
          screen.queryByTestId("screen-tab-ui-12"),
        ).not.toBeInTheDocument(),
      );
      expect(screen.queryByLabelText("UI-12 입력")).not.toBeInTheDocument();
      expect(screen.getByLabelText("UI-06 입력")).toBe(retained);
      expect(retained).toHaveValue("보존할 입력");
      expect(screen.getByTestId("screen-tab-ui-06")).toHaveTextContent("●");
      expect(router!.state.location.pathname).toBe("/screens/UI-06");
      expect(screen.queryByTestId("unsaved-dialog")).not.toBeInTheDocument();
    },
  );

  it("expiration discards protected drafts and renders the registered login route", async () => {
    mount();
    fireEvent.change(await screen.findByLabelText("UI-06 입력"), {
      target: { value: "이전 세션" },
    });
    fireEvent(window, new Event("account-session-expired"));
    expect(await screen.findByLabelText("아이디")).toHaveValue("");
    expect(screen.queryByLabelText("UI-06 입력")).not.toBeInTheDocument();
    expect(router!.state.location.pathname).toBe("/screens/UI-22");
  });

  it("zero readable permissions uses the account empty shell rather than stale tabs", async () => {
    mount();
    await screen.findByLabelText("UI-06 입력");
    vi.mocked(dependencies.api!.myInfo).mockResolvedValue(identity([]));
    fireEvent(window, new Event("account-permissions-changed"));
    expect(await screen.findByTestId("account-empty-shell")).toHaveTextContent(
      "볼 수 있는 화면이 없습니다관리자에게 권한을 요청해 주세요.",
    );
    expect(screen.queryByTestId("screen-tab-list")).not.toBeInTheDocument();
  });

  it("the registered password route opens the change dialog, not the business shell", async () => {
    mount("/screens/UI-40");
    expect(await screen.findByLabelText("현재 비밀번호")).toBeInTheDocument();
    expect(
      screen.getByRole("dialog", { name: "비밀번호 변경" }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("screen-tab-list")).not.toBeInTheDocument();
  });

  it("the registered MFA route stays outside the authenticated business shell", async () => {
    mount("/screens/UI-39");
    expect(await screen.findByTestId("mfa-page")).toHaveAttribute(
      "data-screen-id",
      "UI-39",
    );
    expect(
      screen.queryByTestId("account-session-tools"),
    ).not.toBeInTheDocument();
    expect(router!.state.location.pathname).toBe("/screens/UI-39");
    expect(dependencies.api!.myInfo).not.toHaveBeenCalled();
  });

  it("the registered identity route renders the account owner's dialog", async () => {
    mount("/screens/UI-41");
    expect(
      await screen.findByRole("dialog", { name: "내 정보" }),
    ).toHaveAttribute("data-screen-id", "UI-41");
    expect(await screen.findByText("finance-reader")).toBeInTheDocument();
    expect(screen.queryByTestId("screen-tab-list")).not.toBeInTheDocument();
  });
});

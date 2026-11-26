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
    CommonNavigationPage: () => (
      <NavigationRoot tabs renderScreen={(id) => <Draft id={id} />} />
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
): AccountResponse<AccountInfo> {
  return {
    success: true,
    data: {
      userId: "finance-reader",
      name: "재무 사용자",
      roles,
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
        identity(undefined, [role]),
      );
      mount();
      const input = await screen.findByLabelText("UI-06 입력");
      expect(screen.getByRole("link", { name: "UI-06" })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "UI-12" })).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "로그아웃" }),
      ).toBeInTheDocument();
      fireEvent.change(input, { target: { value: "역할별 입력 확인" } });
      expect(input).toHaveValue("역할별 입력 확인");
      expect(screen.getByTestId("screen-tab-ui-06")).toHaveTextContent("●");
    },
  );

  it("keeps bootstrap fetch unchanged and accepts its timing event", async () => {
    const request = window.fetch;
    mount();
    await screen.findByLabelText("UI-06 입력");
    expect(window.fetch).toBe(request);
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
      "볼 수 있는 메뉴가 없습니다.",
    );
    expect(screen.queryByTestId("screen-tab-list")).not.toBeInTheDocument();
  });

  it("the registered password route uses the existing account page, not the business shell", async () => {
    mount("/screens/UI-40");
    expect(await screen.findByLabelText("현재 비밀번호")).toBeInTheDocument();
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

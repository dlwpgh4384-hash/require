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
import { createAppRouter } from "./router";
import { wrapNetworkErrors } from "../shared/network";
import type {
  AccountMeta,
  AccountPermission,
} from "../pages/commonops/accountAccessApi";

vi.mock("../pages/common/CommonNavigationPage", async () => {
  const { useNavigate } = await import("react-router-dom");
  const { UserPreferenceTools } = await import(
    "../pages/userpreferences/UserPreferenceTools"
  );
  // 실제 화면 틀처럼 헤더 오른쪽에 종·사용자 메뉴(자동 로그아웃 시계 포함)를 둔다.
  function Header() {
    const navigate = useNavigate();
    return (
      <header>
        <UserPreferenceTools
          menus={[]}
          mobile={false}
          available={() => true}
          onNavigate={(id) => navigate(`/screens/${id}`)}
        />
      </header>
    );
  }
  return {
    CommonNavigationPage: () => (
      <>
        <Header />
        <main data-testid="protected-business-page">
          <input
            data-testid="business-draft"
            aria-label="업무 입력"
            defaultValue=""
          />
          <button
            data-testid="business-request"
            onClick={() => void fetch("/api/common/menus")}
          >
            조회
          </button>
        </main>
      </>
    ),
  };
});
const openUserMenu = async () =>
  fireEvent.click(await screen.findByTestId("user-menu-open"));

let router: ReturnType<typeof createAppRouter> | undefined;
const permission: AccountPermission = {
  menuId: "M004",
  screenId: "UI-06",
  readYn: "Y",
  pcOnlyYn: "N",
  createYn: "N",
  updateYn: "N",
  deleteYn: "N",
  uploadYn: "N",
  downloadYn: "N",
  cnfmYn: "N",
};
const timing = {
  remainingSeconds: 420,
  sessionMinutes: 7,
  serverRespondedAt: "2026-01-01T00:00:00Z",
};
const json = (data: unknown, meta: AccountMeta = {}, status = 200) =>
  new Response(JSON.stringify({ success: status < 400, data, meta }), {
    status,
    headers: { "Content-Type": "application/json" },
  });

beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  document.cookie = "XSRF-TOKEN=; Max-Age=0; Path=/";
});
afterEach(() => {
  cleanup();
  router?.dispose();
  router = undefined;
  window.history.replaceState(null, "", "/");
  document.cookie = "XSRF-TOKEN=; Max-Age=0; Path=/";
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function setup(
  options: {
    permissions?: AccountPermission[];
    meta?: AccountMeta;
    anonymous?: boolean;
  } = {},
) {
  const meta: AccountMeta = {
    ...timing,
    permissions: options.permissions ?? [permission],
    ...options.meta,
  };
  const identity = {
    userId: "route-reader",
    name: "시험 사용자",
    roles: ["FIN"],
    roleNames: ["재무 담당"],
    parts: [],
  };
  const request = vi.fn(
    async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const path = String(input);
      if (path === "/api/health") {
        document.cookie = "XSRF-TOKEN=route-csrf; Path=/";
        return json({ status: "UP" });
      }
      if (path === "/api/common/my-info")
        return json(identity, meta, options.anonymous ? 401 : 200);
      if (path === "/api/common/login")
        return json({ nextScreenId: "UI-06", userName: "시험 사용자" }, meta);
      if (path === "/api/common/logout")
        return new Response(null, { status: 204 });
      if (
        path === "/api/common/session/remaining" ||
        path === "/api/common/session/extend"
      )
        return json(timing);
      if (path === "/api/common/notifications")
        return json({ items: [], unreadCount: 0 });
      if (path.startsWith("/api/common/user-preferences/"))
        return json({
          prefTypeCd: path.split("/").at(-2),
          prefKey: "GLOBAL",
          found: false,
          prefVal: null,
          modDttm: null,
        });
      if (path === "/api/common/menus") return json([]);
      throw new Error(`예상하지 않은 요청: ${path} ${init?.method ?? "GET"}`);
    },
  );
  const transport = { fetch: request as typeof fetch };
  wrapNetworkErrors(transport);
  vi.stubGlobal("fetch", transport.fetch);
  return { request, meta, identity };
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
const callsTo = (request: ReturnType<typeof setup>["request"], path: string) =>
  request.mock.calls.filter(([input]) => String(input) === path);

function submitLogin() {
  fireEvent.change(screen.getByTestId("account-login-user-id"), {
    target: { value: "route-reader" },
  });
  fireEvent.change(screen.getByTestId("account-login-password"), {
    target: { value: "historical" },
  });
  fireEvent.click(screen.getByTestId("account-login-submit"));
}

describe("account route composition", () => {
  it("UI-22 is standalone and makes no protected or preference requests", async () => {
    const { request } = setup();
    mount("/screens/UI-22");
    expect(await screen.findByTestId("account-ui-22")).toBeVisible();
    expect(
      screen.queryByTestId("protected-business-page"),
    ).not.toBeInTheDocument();
    expect(request).not.toHaveBeenCalled();
  });

  it("login bootstraps CSRF and enters the authorized first leaf without restoring a prelogin tab", async () => {
    const { request } = setup();
    mount("/screens/UI-22");
    submitLogin();
    expect(await screen.findByTestId("protected-business-page")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-06");
    expect(callsTo(request, "/api/health")).toHaveLength(1);
    const login = callsTo(request, "/api/common/login")[0][1]!;
    expect(login.credentials).toBe("same-origin");
    expect(new Headers(login.headers).get("X-XSRF-TOKEN")).toBe("route-csrf");
    expect(screen.getByTestId("account-login-message")).toHaveTextContent(
      "시험 사용자(재무)으로 로그인했습니다.",
    );
    await openUserMenu();
    expect(screen.getByTestId("session-left")).toHaveTextContent("07:00");
    expect(callsTo(request, "/api/common/session/remaining")).toHaveLength(0);
  });

  it("anonymous direct business entry replaces the URL with standalone login", async () => {
    const { request } = setup({ anonymous: true });
    mount("/screens/UI-06");
    expect(await screen.findByTestId("account-ui-22")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-22");
    expect(
      screen.queryByTestId("protected-business-page"),
    ).not.toBeInTheDocument();
    expect(callsTo(request, "/api/common/session/remaining")).toHaveLength(0);
  });

  it("the authenticated shell exposes the existing API-backed notification bell", async () => {
    const { request } = setup();
    mount("/screens/UI-06");
    const bell = await screen.findByRole("button", { name: "알림 0건" });
    fireEvent.click(bell);
    expect(await screen.findByTestId("notifications-empty")).toBeVisible();
    expect(
      callsTo(request, "/api/common/notifications").length,
    ).toBeGreaterThan(0);
    fireEvent.click(bell);
    expect(screen.queryByTestId("notifications-empty")).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/screens/UI-06");
  });

  it("pending administrator MFA stays outside business and preference providers", async () => {
    const { request, meta } = setup();
    const original = request.getMockImplementation()!;
    request.mockImplementation((input, init) =>
      String(input) === "/api/common/login"
        ? Promise.resolve(
            json(
              { nextScreenId: "UI-39", userName: "시험 사용자" },
              {
                ...meta,
                mfaDelivery: {
                  maskedEmail: "a***@example.test",
                  expiresAt: new Date(Date.now() + 300000).toISOString(),
                  resendAvailableAt: new Date(Date.now() + 60000).toISOString(),
                },
              },
            ),
          )
        : original(input, init),
    );
    mount("/screens/UI-22");
    submitLogin();
    expect(await screen.findByTestId("mfa-page")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-39");
    expect(callsTo(request, "/api/common/my-info")).toHaveLength(0);
    expect(
      screen.queryByTestId("protected-business-page"),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("mfa-restart-button"));
    expect(await screen.findByTestId("account-login-user-id")).toHaveValue("");
  });

  it("forced password change consumes completion metadata without protected my-info", async () => {
    const { request, meta } = setup();
    const original = request.getMockImplementation()!;
    request.mockImplementation((input, init) =>
      String(input) === "/api/common/login"
        ? Promise.resolve(
            json(
              { nextScreenId: "UI-40", userName: "시험 사용자" },
              {
                ...meta,
                passwordChangeRequired: true,
                modifiedAt: "2026-01-01T00:00:00Z",
              },
            ),
          )
        : original(input, init),
    );
    mount("/screens/UI-22");
    submitLogin();
    expect(await screen.findByTestId("account-ui-40")).toBeVisible();
    expect(screen.getByTestId("account-password-submit")).toBeEnabled();
    expect(callsTo(request, "/api/common/my-info")).toHaveLength(0);
    expect(callsTo(request, "/api/common/session/remaining")).toHaveLength(0);
    expect(
      screen.queryByTestId("protected-business-page"),
    ).not.toBeInTheDocument();
  });

  it("UI-41 opens the existing read-only account dialog, not a menu page", async () => {
    setup();
    mount("/screens/UI-41");
    expect(
      await screen.findByRole("dialog", { name: "내 정보" }),
    ).toBeVisible();
    expect(
      screen.queryByTestId("protected-business-page"),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("account-info-close"));
    expect(await screen.findByTestId("protected-business-page")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-06");
  });

  it("UI-42 uses the existing popup with the injected API and closes to an authorized leaf", async () => {
    const { request } = setup();
    mount("/screens/UI-42");
    expect(await screen.findByTestId("notifications-empty")).toBeVisible();
    expect(screen.getByTestId("account-authenticated-shell")).toBeVisible();
    expect(callsTo(request, "/api/common/notifications")).toHaveLength(1);
    expect(callsTo(request, "/api/common/menus")).toHaveLength(0);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(await screen.findByTestId("protected-business-page")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-06");
  });

  it("zero authorized leaves renders the exact no-menu guidance and logout", async () => {
    const { request } = setup({ permissions: [] });
    mount("/screens/UI-34");
    expect(
      await screen.findByRole("heading", { name: "볼 수 있는 화면이 없습니다" }),
    ).toBeVisible();
    expect(screen.getByText("관리자에게 권한을 요청해 주세요.")).toBeVisible();
    expect(screen.getByTestId("account-logout")).toBeVisible();
    expect(
      screen.queryByTestId("protected-business-page"),
    ).not.toBeInTheDocument();
    expect(callsTo(request, "/api/common/session/remaining")).toHaveLength(0);
  });

  it("missing initial timing reads remaining once and provides the server-backed timer", async () => {
    const { request } = setup({
      meta: {
        remainingSeconds: undefined,
        sessionMinutes: undefined,
        serverRespondedAt: undefined,
      },
    });
    mount("/screens/UI-06");
    await openUserMenu();
    expect(await screen.findByTestId("session-left")).toHaveTextContent(
      "07:00",
    );
    expect(callsTo(request, "/api/common/session/remaining")).toHaveLength(1);
  });

  it("session extension uses the shared CSRF transport without posting a body", async () => {
    const { request } = setup();
    mount("/screens/UI-06");
    await openUserMenu();
    fireEvent.click(await screen.findByTestId("session-extend"));
    await waitFor(() =>
      expect(callsTo(request, "/api/common/session/extend")).toHaveLength(1),
    );
    const init = callsTo(request, "/api/common/session/extend")[0][1]!;
    expect(init.method).toBe("POST");
    expect(init.body).toBeUndefined();
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("route-csrf");
    expect(callsTo(request, "/api/health")).toHaveLength(1);
  });

  it("mobile PC-only exclusion does not mount session or business data", async () => {
    const { request } = setup({
      permissions: [{ ...permission, pcOnlyYn: "Y" }],
    });
    vi.stubGlobal(
      "matchMedia",
      vi.fn(() => ({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    mount("/screens/UI-34");
    expect(
      await screen.findByText(
        "휴대폰에서 볼 수 있는 화면이 없습니다. PC에서 이용해 주세요.",
      ),
    ).toBeVisible();
    expect(
      screen.queryByTestId("protected-business-page"),
    ).not.toBeInTheDocument();
    expect(callsTo(request, "/api/common/session/remaining")).toHaveLength(0);
  });

  it("notification failure retries the same registered popup client", async () => {
    const { request } = setup();
    const original = request.getMockImplementation()!;
    let failed = false;
    request.mockImplementation((input, init) => {
      if (String(input) === "/api/common/notifications" && !failed) {
        failed = true;
        return Promise.resolve(
          new Response(
            JSON.stringify({
              success: false,
              meta: {},
              message: "알림 조회 실패",
            }),
            { status: 500, headers: { "Content-Type": "application/json" } },
          ),
        );
      }
      return original(input, init);
    });
    mount("/screens/UI-42");
    fireEvent.click(await screen.findByTestId("notifications-retry"));
    expect(await screen.findByTestId("notifications-empty")).toBeVisible();
    expect(callsTo(request, "/api/common/notifications")).toHaveLength(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-42");
  });

  it("out-of-scope notification guidance survives popup destination navigation", async () => {
    const { request } = setup();
    const original = request.getMockImplementation()!;
    request.mockImplementation((input, init) => {
      if (String(input) === "/api/common/notifications")
        return Promise.resolve(
          json({
            items: [
              {
                notificationId: 7,
                content: "확인할 알림",
                readYn: "N",
                notifiedAt: timing.serverRespondedAt,
              },
            ],
            unreadCount: 1,
          }),
        );
      if (String(input) === "/api/common/notifications/7/read")
        return Promise.resolve(
          new Response(
            JSON.stringify({
              success: true,
              data: {
                notificationId: 7,
                readYn: "Y",
                readAt: timing.serverRespondedAt,
              },
              meta: {
                navigation: {
                  status: "OUT_OF_SCOPE",
                  screenId: "UI-06",
                  pcOnly: false,
                },
              },
            }),
            { headers: { "Content-Type": "application/json" } },
          ),
        );
      return original(input, init);
    });
    mount("/screens/UI-42");
    fireEvent.click(await screen.findByTestId("notification-read-7"));
    expect(await screen.findByTestId("protected-business-page")).toBeVisible();
    expect(
      screen.getByTestId("account-notification-message"),
    ).toHaveTextContent("이 건은 지금 볼 수 있는 범위 밖입니다.");
    expect(router?.state.location.pathname).toBe("/screens/UI-06");
    expect(
      callsTo(request, "/api/common/notifications/7/read")[0][1]?.method,
    ).toBe("PUT");
  });

  it("protected 401 discards mounted drafts and replaces the route with empty login", async () => {
    const { request } = setup();
    mount("/screens/UI-06");
    fireEvent.change(await screen.findByLabelText("업무 입력"), {
      target: { value: "폐기할 내용" },
    });
    const original = request.getMockImplementation()!;
    request.mockImplementation((input, init) =>
      String(input) === "/api/common/menus"
        ? Promise.resolve(json(undefined, {}, 401))
        : original(input, init),
    );
    await act(async () => {
      fireEvent.click(screen.getByTestId("business-request"));
    });
    expect(await screen.findByTestId("account-ui-22")).toBeVisible();
    expect(screen.queryByLabelText("업무 입력")).not.toBeInTheDocument();
    expect(screen.getByTestId("account-login-user-id")).toHaveValue("");
    await waitFor(() =>
      expect(router?.state.location.pathname).toBe("/screens/UI-22"),
    );
  });
});

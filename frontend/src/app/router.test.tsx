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
import type { ShellMenuResponse } from "./NavigationRoot";
import { createAppRouter } from "./router";
import type { RoleSettings } from "../pages/roles/roles";

let router: ReturnType<typeof createAppRouter> | undefined;

beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
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

function response(): ShellMenuResponse {
  return {
    success: true,
    data: [
      {
        menuId: "route-leaf",
        upperMenuId: null,
        menuNm: "등록 화면",
        scrnId: "ADDED",
        menuLvl: 1,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [{ scrnId: "ADDED", available: true, pcOnlyYn: "N" }],
    },
  };
}
function respond(body = response()) {
  const request = vi.fn(async (url: string, _init?: RequestInit) => ({
    ok: true,
    json: async () =>
      url === "/api/common/menus" ? body : preferenceResponse(url),
  }));
  vi.stubGlobal("fetch", request);
  return request;
}
function preferenceResponse(url: string) {
  return {
    success: true,
    meta: {},
    data: {
      prefTypeCd: url.split("/").at(-2),
      prefKey: "GLOBAL",
      found: false,
      prefVal: null,
      modDttm: null,
    },
  };
}
function menuCalls(request: ReturnType<typeof vi.fn>) {
  return request.mock.calls.filter(([url]) => url === "/api/common/menus");
}
function respondWithUsers(failList = false) {
  const menus = response();
  menus.data.push({
    ...menus.data[0],
    menuId: "users-menu",
    menuNm: "사용자",
    scrnId: "UI-23",
    sortOrd: 2,
    pcOnlyYn: "Y",
  });
  menus.meta.screenAccess.push({
    scrnId: "UI-23",
    available: true,
    pcOnlyYn: "Y",
  });
  const list = vi.fn(async () =>
    failList
      ? {
          ok: false,
          status: 500,
          json: async () => ({ success: false, message: "private SQL error" }),
        }
      : {
          ok: true,
          status: 200,
          json: async () => ({
            success: true,
            meta: {},
            data: { items: [], page: 1, pageSize: 20, total: 0 },
          }),
        },
  );
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url.startsWith("/api/common/users?")) return list();
    return {
      ok: true,
      status: 200,
      json: async () =>
        url === "/api/common/menus"
          ? menus
          : url === "/api/common/user-options"
            ? {
                success: true,
                meta: {},
                data: { roles: [], projects: [], userStates: [] },
              }
            : preferenceResponse(url),
    };
  });
  vi.stubGlobal("fetch", request);
  return { request, list };
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  return render(<RouterProvider router={router} />);
}
function respondWithRoles() {
  const menus = response();
  menus.data.push({
    ...menus.data[0],
    menuId: "M028",
    menuNm: "권한",
    scrnId: "UI-24",
    sortOrd: 2,
    pcOnlyYn: "Y",
  });
  menus.meta.screenAccess.push({
    scrnId: "UI-24",
    available: true,
    pcOnlyYn: "Y",
  });
  const settings: RoleSettings = {
    role: {
      roleId: "ADMIN",
      roleName: "시스템 관리자",
      dataScopeCd: "ALL",
      snstvViewYn: "Y",
      useYn: "Y",
      basicRole: true,
    },
    menuAuths: [
      {
        menuId: "M028",
        menuName: "권한",
        screenId: "UI-24",
        groupName: "시스템",
        readYn: "Y",
        createYn: "Y",
        updateYn: "Y",
        deleteYn: "Y",
        uploadYn: "Y",
        downloadYn: "Y",
        cnfmYn: "Y",
      },
    ],
    dataScopes: [{ code: "ALL", name: "전체" }],
  };
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const list = vi.fn(
    async (): Promise<{
      ok: boolean;
      status: number;
      json: () => Promise<unknown>;
    }> => success({ items: [settings.role] }),
  );
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url === "/api/common/roles") return list();
    if (url === `/api/common/roles/${settings.role.roleId}`) {
      if (init?.method === "PUT") {
        const input = JSON.parse(String(init.body));
        settings.role = { ...settings.role, snstvViewYn: input.snstvViewYn };
      }
      return success(settings);
    }
    return { ok: true, status: 200, json: async () => preferenceResponse(url) };
  });
  vi.stubGlobal("fetch", request);
  return { request, list, settings };
}
function expectSingleShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("complementary")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
}

describe("application route registration", () => {
  it("UI-24 direct entry renders the role client inside the existing shell and saves anonymously", async () => {
    const { request } = respondWithRoles();
    mount("/screens/UI-24");
    await screen.findByTestId("role-settings");
    expectSingleShell();
    expect(screen.getAllByTestId("role-page")).toHaveLength(1);
    expect(screen.getByTestId("role-page")).toHaveClass("screen-body");
    expect(screen.getByTestId("screen-tab-ui-24")).toBeVisible();
    expect(request).toHaveBeenCalledWith("/api/common/roles", {
      signal: expect.any(AbortSignal),
    });
    expect(request).toHaveBeenCalledWith("/api/common/roles/ADMIN", {
      signal: expect.any(AbortSignal),
    });
    fireEvent.click(screen.getByTestId("role-sensitive"));
    fireEvent.click(screen.getByTestId("role-save"));
    await screen.findByTestId("role-toast");
    const saves = request.mock.calls.filter(
      ([url, init]) =>
        url === "/api/common/roles/ADMIN" && init?.method === "PUT",
    );
    expect(saves).toHaveLength(1);
    expect(JSON.parse(String(saves[0][1]?.body))).toEqual({
      dataScopeCd: "ALL",
      snstvViewYn: "N",
      confirmedAffectedUserIds: [],
      menuAuths: [
        {
          menuId: "M028",
          readYn: "Y",
          createYn: "Y",
          updateYn: "Y",
          deleteYn: "Y",
          uploadYn: "Y",
          downloadYn: "Y",
          cnfmYn: "Y",
        },
      ],
    });
    expect(saves[0][1]?.headers).toEqual({
      "Content-Type": "application/json",
    });
    await waitFor(() =>
      expect(
        request.mock.calls.filter(
          ([url, init]) => url === "/api/common/roles/ADMIN" && !init?.method,
        ).length,
      ).toBeGreaterThan(1),
    );
    expect(screen.getByTestId("role-sensitive")).not.toBeChecked();
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(router?.state.location.pathname).toBe("/screens/UI-24");
  });

  it("UI-24 navigation preserves preferences and its draft, and tab close uses the shared dirty guard", async () => {
    respondWithRoles();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    await act(async () => {
      await router!.navigate("/screens/UI-24");
    });
    await screen.findByTestId("role-settings");
    fireEvent.click(screen.getByTestId("role-sensitive"));
    await waitFor(() =>
      expect(screen.getByTestId("screen-tab-ui-24")).toHaveTextContent("●"),
    );
    await act(async () => {
      await router!.navigate("/screens/ADDED");
    });
    await act(async () => {
      await router!.navigate("/screens/UI-24");
    });
    expect(screen.getByTestId("role-sensitive")).not.toBeChecked();
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    fireEvent.click(screen.getByTestId("screen-tab-close-ui-24"));
    await screen.findByTestId("unsaved-dialog");
    fireEvent.click(screen.getByTestId("confirmation-cancel"));
    expect(screen.getByTestId("role-settings")).toBeVisible();
    expect(screen.getByTestId("role-sensitive")).not.toBeChecked();
    expectSingleShell();
  });

  it("UI-24 copy is a page dialog rather than another route", async () => {
    const { request } = respondWithRoles();
    mount("/screens/UI-24");
    await screen.findByTestId("role-settings");
    fireEvent.click(screen.getByTestId("role-copy-open"));
    expect(await screen.findByTestId("role-copy-dialog")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-24");
    fireEvent.click(screen.getByTestId("role-copy-cancel"));
    expect(screen.queryByTestId("role-copy-dialog")).not.toBeInTheDocument();
    expect(
      request.mock.calls.filter(
        ([url, init]) => url.startsWith("/api/common/roles") && !!init?.method,
      ),
    ).toHaveLength(0);
    expectSingleShell();
  });

  it("UI-24 keeps a successful empty role response empty inside the shell", async () => {
    const { list, request } = respondWithRoles();
    list.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ success: true, meta: {}, data: { items: [] } }),
    });
    mount("/screens/UI-24");
    expect(await screen.findByText("활성 역할이 없습니다.")).toBeVisible();
    expect(screen.queryByTestId("role-settings")).not.toBeInTheDocument();
    expect(screen.getByTestId("role-copy-open")).toBeDisabled();
    expect(
      request.mock.calls.filter(([url]) =>
        url.startsWith("/api/common/roles/"),
      ),
    ).toHaveLength(0);
    expectSingleShell();
  });

  it("UI-24 list retry uses the real feature client without redirect or server-detail leakage", async () => {
    const { list } = respondWithRoles();
    list.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({
        success: false,
        meta: {},
        message: "private SQL error",
      }),
    });
    mount("/screens/UI-24");
    const retry = await screen.findByTestId("role-list-retry");
    expect(screen.queryByText("private SQL error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("role-settings")).not.toBeInTheDocument();
    fireEvent.click(retry);
    expect(await screen.findByTestId("role-settings")).toBeVisible();
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-24");
    expectSingleShell();
  });

  it("UI-24 mobile direct entry keeps the existing PC-only guard and never loads role data", async () => {
    const { request } = respondWithRoles();
    vi.stubGlobal(
      "matchMedia",
      vi.fn((query: string) => ({
        matches: query === "(max-width: 767px)",
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    mount("/screens/UI-24");
    expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
    expect(screen.queryByTestId("role-page")).not.toBeInTheDocument();
    expect(
      request.mock.calls.filter(([url]) => url.startsWith("/api/common/roles")),
    ).toHaveLength(0);
    expect(router?.state.location.pathname).toBe("/screens/UI-24");
    expectSingleShell();
  });

  it("UI-23 direct entry uses the existing shell, feature clients and title registration action", async () => {
    const { request } = respondWithUsers();
    mount("/screens/UI-23");
    expect(
      await screen.findByText("조건에 맞는 결과가 없습니다."),
    ).toBeVisible();
    expectSingleShell();
    expect(router?.state.location.pathname).toBe("/screens/UI-23");
    expect(screen.getAllByTestId("user-page")).toHaveLength(1);
    expect(request).toHaveBeenCalledWith("/api/common/user-options", {
      signal: expect.any(AbortSignal),
    });
    expect(request).toHaveBeenCalledWith(
      "/api/common/users?includeStopped=false&page=1&pageSize=50",
      {
        signal: expect.any(AbortSignal),
      },
    );
    const register = screen.getByTestId("user-register-open");
    expect(register.closest(".screen-actions")).not.toBeNull();
    fireEvent.click(register);
    expect(await screen.findByTestId("user-dialog")).toBeVisible();
    expect(screen.getByTestId("user-userId-input")).not.toHaveAttribute(
      "readonly",
    );
    expect(
      request.mock.calls.some(([url]) => /\/api\/(auth|.*period)/i.test(url)),
    ).toBe(false);
    expect(
      request.mock.calls.filter(
        ([url, init]) =>
          url.startsWith("/api/common/users") &&
          ["POST", "PUT"].includes(String(init?.method)),
      ),
    ).toHaveLength(0);
  });

  it("navigation to UI-23 preserves the preferences scope and leaves other screen actions empty", async () => {
    respondWithUsers();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    expect(screen.queryByTestId("user-register-open")).not.toBeInTheDocument();
    await act(async () => {
      await router!.navigate("/screens/UI-23");
    });
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expect(screen.getByTestId("user-register-open")).toBeVisible();
    await act(async () => {
      await router!.navigate("/screens/ADDED");
    });
    expect(
      screen.getByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expect(screen.queryByTestId("user-register-open")).not.toBeInTheDocument();
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expectSingleShell();
  });

  it("UI-23 list failures retry the feature client without redirecting or leaking server details", async () => {
    const { list } = respondWithUsers(true);
    mount("/screens/UI-23");
    const retry = await screen.findByTestId("list-retry");
    expect(screen.queryByText("private SQL error")).not.toBeInTheDocument();
    expect(
      screen.queryByText("조건에 맞는 결과가 없습니다."),
    ).not.toBeInTheDocument();
    list.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        meta: {},
        data: { items: [], page: 1, pageSize: 20, total: 0 },
      }),
    });
    fireEvent.click(retry);
    expect(
      await screen.findByText("조건에 맞는 결과가 없습니다."),
    ).toBeVisible();
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-23");
    expectSingleShell();
  });

  it("root selects the database screen through the existing anonymous relative API call", async () => {
    const request = respond();
    mount("/");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    await waitFor(() =>
      expect(router?.state.location.pathname).toBe("/screens/ADDED"),
    );
    expectSingleShell();
    expect(request).toHaveBeenCalledWith("/api/common/menus", {
      signal: expect.any(AbortSignal),
    });
    expect(menuCalls(request)).toHaveLength(1);
  });

  it("dynamic direct entry and recreation render the registered placeholder, not a second shell", async () => {
    respond();
    const first = mount("/screens/ADDED");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expectSingleShell();
    first.unmount();
    router?.dispose();
    mount("/screens/ADDED");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expectSingleShell();
    expect(router?.state.location.pathname).toBe("/screens/ADDED");
  });

  it.each(["/screens/UNKNOWN", "/unregistered/path"])(
    "keeps %s in the common unknown state and returns to the first database screen",
    async (path) => {
      respond();
      mount(path);
      expect(await screen.findByText("없는 화면입니다")).toBeVisible();
      expectSingleShell();
      expect(router?.state.location.pathname).toBe(path);
      fireEvent.click(screen.getByRole("link", { name: "첫 화면으로 가기" }));
      expect(
        await screen.findByRole("region", { name: "ADDED 본문" }),
      ).toHaveTextContent("준비 중");
      expect(router?.state.location.pathname).toBe("/screens/ADDED");
    },
  );

  it("direct entry errors retry the existing client without redirecting or exposing stale content", async () => {
    const menuRequest = vi
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({ ok: true, json: async () => response() });
    const request = vi.fn(async (url: string) =>
      url === "/api/common/menus"
        ? menuRequest()
        : { ok: true, json: async () => preferenceResponse(url) },
    );
    vi.stubGlobal("fetch", request);
    mount("/screens/ADDED");
    const retries = await screen.findAllByRole("button", { name: "다시 시도" });
    expect(retries).toHaveLength(2);
    expect(
      screen.queryByRole("region", { name: "ADDED 본문" }),
    ).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/screens/ADDED");
    fireEvent.click(retries[0]);
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expect(router?.state.location.pathname).toBe("/screens/ADDED");
    expectSingleShell();
    expect(menuCalls(request)).toHaveLength(2);
  });

  it("root preserves a successful empty menu without a redirect or retry error", async () => {
    respond({ success: true, data: [], meta: { screenAccess: [] } });
    mount("/");
    expect(await screen.findAllByText("등록된 메뉴 없음")).toHaveLength(2);
    expect(
      screen.queryByRole("button", { name: "다시 시도" }),
    ).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/");
    expectSingleShell();
  });

  it.each(["/", "/unregistered/path"])(
    "provides one shared preferences scope at %s without creating settings or excluded API calls",
    async (path) => {
      const request = respond();
      mount(path);
      await waitFor(() =>
        expect(
          request.mock.calls.filter(([url]) => url !== "/api/common/menus"),
        ).toHaveLength(5),
      );
      await act(async () => {});
      expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
        "data-page-size",
      );
      expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
        "data-density",
      );
      expect(screen.getAllByTestId("user-preferences")).toHaveLength(1);
      expectSingleShell();
      const preferenceCalls = request.mock.calls.filter(
        ([url]) => url !== "/api/common/menus",
      );
      expect(preferenceCalls.map(([url]) => url).sort()).toEqual([
        "/api/common/user-preferences/DENSE/GLOBAL",
        "/api/common/user-preferences/FAV/GLOBAL",
        "/api/common/user-preferences/MENU_FOLD/GLOBAL",
        "/api/common/user-preferences/PAGE_SIZE/GLOBAL",
        "/api/common/user-preferences/RECENT/GLOBAL",
      ]);
      expect(preferenceCalls.every(([, init]) => !init?.method)).toBe(true);
    },
  );

  it("direct entry to a screen records only that screen in recent screens", async () => {
    const request = respond();
    mount("/screens/ADDED");
    await waitFor(() =>
      expect(
        request.mock.calls.filter(([, init]) => init?.method === "PUT"),
      ).toHaveLength(1),
    );
    const [url, init] = request.mock.calls.find(
      ([, i]) => i?.method === "PUT",
    )!;
    expect(url).toBe("/api/common/user-preferences/RECENT/GLOBAL");
    expect(JSON.parse(String(init?.body))).toEqual({
      prefVal: '["ADDED"]',
      modDttm: null,
    });
  });

  it("keeps the same preferences scope across child route navigation", async () => {
    const request = respond();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    // Direct entry also saves RECENT and reads it back. Wait for that work
    // instead of observing the transient five-request initialization count.
    await waitFor(() =>
      expect(
        request.mock.calls.filter(
          ([url, init]) =>
            url === "/api/common/user-preferences/RECENT/GLOBAL" &&
            !init?.method,
        ),
      ).toHaveLength(2),
    );
    await act(async () => {});
    const provider = screen.getByTestId("user-preferences");
    const reads = request.mock.calls.filter(
      ([url]) => url !== "/api/common/menus",
    ).length;
    await act(async () => {
      await router!.navigate("/unregistered/path");
    });
    expect(await screen.findByText("없는 화면입니다")).toBeVisible();
    expect(screen.getByTestId("user-preferences")).toBe(provider);
    expect(
      request.mock.calls.filter(([url]) => url !== "/api/common/menus"),
    ).toHaveLength(reads);
    expectSingleShell();
  });

  it("keeps the shell available without claiming default settings when preference reads fail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (url !== "/api/common/menus")
          throw new Error("private connection error");
        return { ok: true, json: async () => response() };
      }),
    );
    mount("/screens/ADDED");
    expect(
      await screen.findByRole("region", { name: "ADDED 본문" }),
    ).toHaveTextContent("준비 중");
    expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
      "data-page-size",
    );
    expect(
      screen.queryByText("private connection error"),
    ).not.toBeInTheDocument();
    expectSingleShell();
  });
});

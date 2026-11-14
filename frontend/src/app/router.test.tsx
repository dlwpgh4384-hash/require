import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider } from "react-router-dom";
import type { ShellMenuResponse } from "./NavigationRoot";
import { createAppRouter } from "./router";
import type { RoleSettings } from "../pages/roles/roles";
import type {
  MenuSettingItem,
  MenuSettingsInput,
} from "../pages/menusettings/menuSettings";
import type { CodeItem } from "../pages/codegroups/codeGroups";
import type { CodeMapItem } from "../pages/codemaps/codeMaps";
import type { OperatingStandard } from "../pages/operatingstandards/operatingStandards";
import type { CodeHistoryItem } from "../pages/commonops/codeHistory";

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

function respondWithMenuSettings() {
  let items: MenuSettingItem[] = [
    {
      menuId: "G010",
      upperMenuId: null,
      menuNm: "시스템",
      scrnId: null,
      menuLvl: 1,
      sortOrd: 1,
      useYn: "Y",
      pcOnlyYn: "N",
      lockYn: "Y",
      iconNm: "gear",
    },
    {
      menuId: "M029",
      upperMenuId: "G010",
      menuNm: "메뉴",
      scrnId: "UI-25",
      menuLvl: 2,
      sortOrd: 1,
      useYn: "Y",
      pcOnlyYn: "Y",
      lockYn: "Y",
      iconNm: null,
    },
  ];
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      meta: {},
      data: structuredClone(data),
    }),
  });
  const list = vi.fn(async () => success({ items }));
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/common/menu-settings") {
      if (init?.method === "PUT") {
        const input: MenuSettingsInput = JSON.parse(String(init.body));
        items = items.map((row) => ({
          ...row,
          ...input.items.find((item) => item.menuId === row.menuId),
        }));
        return success({ items });
      }
      return list();
    }
    if (url.startsWith("/api/common/menu-options?"))
      return success({
        upperMenus: items
          .filter((row) => row.menuLvl === 1)
          .map(({ menuId, menuNm }) => ({ menuId, menuNm })),
        screens: [{ scrnId: "UI-25", menuNm: "메뉴" }],
      });
    if (url === "/api/common/menus") {
      if (init?.method === "POST") {
        const input = JSON.parse(String(init.body));
        const item: MenuSettingItem = {
          menuId: "G011",
          upperMenuId: null,
          scrnId: null,
          menuLvl: 1,
          menuNm: input.menuNm,
          sortOrd: 2,
          useYn: "Y",
          pcOnlyYn: "N",
          lockYn: "N",
          iconNm: "folder",
        };
        items = [...items, item];
        return success(item);
      }
      const body = response();
      body.data.push(
        ...items.map(({ useYn: _useYn, lockYn: _lockYn, ...row }) => row),
      );
      body.meta.screenAccess.push({
        scrnId: "UI-25",
        available: true,
        pcOnlyYn: "Y",
      });
      return { ok: true, status: 200, json: async () => body };
    }
    return { ok: true, status: 200, json: async () => preferenceResponse(url) };
  });
  vi.stubGlobal("fetch", request);
  return { request, list };
}

function respondWithCommonCodes() {
  const menus = response();
  menus.data.push({
    ...menus.data[0],
    menuId: "common-codes-menu",
    menuNm: "공통코드",
    scrnId: "UI-27",
    sortOrd: 2,
    pcOnlyYn: "Y",
  });
  menus.meta.screenAccess.push({
    scrnId: "UI-27",
    available: true,
    pcOnlyYn: "Y",
  });
  let items: CodeItem[] = [
    { code: "ONE", codeName: "처음 이름", sortOrd: 1, useYn: "Y" },
  ];
  const mappings: CodeMapItem[] = [];
  let values: OperatingStandard[] = [
    {
      standardCode: "CASH_BASE_AMT",
      standardName: "기초 잔액",
      value: "1000",
      unit: "원",
      effectiveMonth: null,
      closed: false,
      immediate: true,
    },
  ];
  const history: CodeHistoryItem[] = [];
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      meta: {},
      data: structuredClone(data),
    }),
  });
  const groups = vi.fn(async () =>
    success({
      items: [
        {
          groupId: "GENERAL",
          groupName: "업무 그룹",
          sysCodeYn: "N",
          valCodeYn: "N",
          codeCount: items.length,
        },
      ],
    }),
  );
  const codes = vi.fn(async () => success({ items }));
  const maps = vi.fn(async () => success({ items: mappings }));
  const standards = vi.fn(async () => success({ items: values }));
  const histories = vi.fn(async () => success({ items: history }));
  const record = (kind: string, item: string, before: string, after: string) =>
    history.unshift({
      changedAt: "2026-01-02T03:04:05Z",
      kind,
      item,
      before,
      after,
      processor: "admin",
    });
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url === "/api/common/code-groups") return groups();
    if (url === "/api/common/code-groups/GENERAL/codes") {
      if (init?.method === "PUT") {
        const input: { items: CodeItem[] } = JSON.parse(String(init.body));
        record("코드", "이름 변경", items[0].codeName, input.items[0].codeName);
        items = items.map(
          (row) => input.items.find((next) => next.code === row.code) ?? row,
        );
        return success({ items });
      }
      return codes();
    }
    if (url === "/api/common/code-maps") {
      if (init?.method === "POST") {
        const item: CodeMapItem = JSON.parse(String(init.body));
        mappings.push(item);
        record("표기 대응", "표기 추가", "", item.excelText);
        return success(item);
      }
      return maps();
    }
    if (url === "/api/common/operating-standards") {
      if (init?.method === "PUT") {
        const input = JSON.parse(String(init.body));
        record(
          "운영 기준값",
          "기초 잔액 변경",
          values[0].value,
          input.items[0].value,
        );
        values = values.map((row) => ({ ...row, value: input.items[0].value }));
        return success({ items: values });
      }
      return standards();
    }
    if (url === "/api/common/code-change-history") return histories();
    return { ok: true, status: 200, json: async () => preferenceResponse(url) };
  });
  vi.stubGlobal("fetch", request);
  return { request, groups, codes, maps, standards, histories };
}

function respondWithClientParts() {
  const menus = response();
  menus.data.push({
    ...menus.data[0],
    menuId: "clients-menu",
    menuNm: "거래처·영업 파트",
    scrnId: "UI-15",
    sortOrd: 2,
    pcOnlyYn: "Y",
  });
  menus.meta.screenAccess.push({
    scrnId: "UI-15",
    available: true,
    pcOnlyYn: "Y",
  });
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const clients = vi.fn(async () => success({ items: [] }));
  const parts = vi.fn(async () => success({ items: [] }));
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/"))
      return {
        ok: true,
        status: 200,
        json: async () => preferenceResponse(url),
      };
    if (url.startsWith("/api/common/clients?")) return clients();
    if (url === "/api/common/parts") return parts();
    if (url === "/api/common/parts/options")
      return success({
        leaders: [],
        distributionRate: 37,
        salesExpense: 1200000,
        closedMonth: null,
        earliestMonth: "2026-01",
      });
    if (/^\/api\/common\/code-groups\/[^/]+\/codes$/.test(url))
      return success({ items: [] });
    if (
      (url === "/api/common/uploads" && init?.method === "POST") ||
      url === "/api/common/uploads/checked-upload"
    )
      return success({
        uploadId: "checked-upload",
        modifiedAt: null,
        statusName: "검사완료",
        successCount: 0,
        warningCount: 0,
        errorCount: 1,
        issues: [
          {
            row: 4,
            column: "상호",
            inputValue: "",
            reason: "상호를 입력해 주세요.",
            severity: "오류",
          },
        ],
      });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, clients, parts, success };
}

describe("client and part route composition", () => {
  it.each(["/screens/UI-15", "/#UI-15"])(
    "%s mounts real tabs with tab-specific title actions",
    async (path) => {
      const { request, parts } = respondWithClientParts();
      mount(path);
      await screen.findByText("조건에 맞는 결과가 없습니다.");
      expectSingleShell();
      expect(screen.getAllByTestId("client-part-workspace")).toHaveLength(1);
      expect(
        screen.getByTestId("client-create").closest(".screen-actions"),
      ).not.toBeNull();
      expect(
        screen.getByTestId("upload-open").closest(".screen-actions"),
      ).not.toBeNull();
      expect(screen.getAllByTestId("upload-actions")).toHaveLength(1);
      expect(parts).not.toHaveBeenCalled();
      fireEvent.click(screen.getByTestId("client-workspace-parts"));
      await screen.findByText("조건에 맞는 결과가 없습니다.");
      expect(screen.getByTestId("part-page")).toBeVisible();
      expect(screen.queryByTestId("part-add-button")).not.toBeInTheDocument();
      expect(screen.queryByTestId("client-create")).not.toBeInTheDocument();
      expect(screen.queryByTestId("upload-open")).not.toBeInTheDocument();
      expect(
        screen.getByTestId("part-title-add").closest(".screen-actions"),
      ).not.toBeNull();
      fireEvent.click(screen.getByTestId("part-title-add"));
      await screen.findByRole("dialog", { name: "파트 추가" });
      await waitFor(() =>
        expect(screen.getByTestId("part-rate-input")).toHaveValue("37"),
      );
      expect(screen.getByTestId("part-expense-input")).toHaveValue("1,200,000");
      expect(request).toHaveBeenCalledWith("/api/common/parts/options", {
        signal: expect.any(AbortSignal),
      });
      expect(router?.state.location.pathname).toBe("/screens/UI-15");
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      fireEvent.click(screen.getByTestId("part-cancel"));
      expect(
        screen.queryByTestId("part-create-dialog"),
      ).not.toBeInTheDocument();
      fireEvent.click(screen.getByTestId("client-workspace-clients"));
      await screen.findByText("조건에 맞는 결과가 없습니다.");
      expect(screen.queryByTestId("part-title-add")).not.toBeInTheDocument();
      expect(
        screen.getByTestId("upload-open").closest(".screen-actions"),
      ).not.toBeNull();
      expect(screen.getAllByTestId("client-create")).toHaveLength(1);
    },
  );

  it("client title registration opens the existing full-screen form without another URL", async () => {
    const { request } = respondWithClientParts();
    mount("/screens/UI-15");
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    fireEvent.click(screen.getByTestId("client-create"));
    expect(await screen.findByText(/구분을 선택하면/)).toBeVisible();
    expect(screen.queryByTestId("client-page")).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/screens/UI-15");
    expect(
      request.mock.calls.filter(
        ([url, init]) => !url.includes("user-preferences") && init?.method,
      ),
    ).toHaveLength(0);
  });

  it("the title upload uses the multipart client and closing its result never applies clients", async () => {
    const { request } = respondWithClientParts();
    mount("/screens/UI-15");
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    fireEvent.click(screen.getByTestId("upload-open"));
    await screen.findByTestId("upload-dialog");
    fireEvent.change(screen.getByTestId("upload-file"), {
      target: {
        files: [new File(["workbook"], "clients.xlsx")],
      },
    });
    fireEvent.click(screen.getByTestId("upload-check"));
    await screen.findByTestId("upload-result-dialog");
    await waitFor(() =>
      expect(screen.queryByText("검사 결과 조회 중…")).not.toBeInTheDocument(),
    );
    expect(screen.getByTestId("upload-apply")).toBeDisabled();
    const upload = request.mock.calls.find(
      ([url, init]) => url === "/api/common/uploads" && init?.method === "POST",
    )!;
    const form = upload[1]?.body as FormData;
    expect(form.get("formTypeCode")).toBe("B7");
    expect(form.get("file")).toBeInstanceOf(File);
    expect(router?.state.location.pathname).toBe("/screens/UI-15");
    fireEvent.click(screen.getByTestId("upload-result-close"));
    expect(
      screen.queryByTestId("upload-result-dialog"),
    ).not.toBeInTheDocument();
    expect(request.mock.calls.some(([url]) => url.endsWith("/apply"))).toBe(
      false,
    );
  });

  it("client loading disables title actions and empty completion enables them", async () => {
    const { clients, success } = respondWithClientParts();
    let complete!: (value: ReturnType<typeof success>) => void;
    clients.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    );
    mount("/screens/UI-15");
    await screen.findByText("목록을 불러오는 중입니다.");
    expect(screen.getByTestId("client-create")).toBeDisabled();
    expect(screen.getByTestId("upload-open")).toBeDisabled();
    await act(async () => {
      complete(success({ items: [] }));
    });
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    // 데이터를 다 읽은 뒤에 켜진다: 켜질 때까지 기다린다(느린 환경에서 깜빡 실패 방지).
    await waitFor(() =>
      expect(screen.getByTestId("client-create")).toBeEnabled(),
    );
  });

  it("client request failure retries the same API without replacing the shell", async () => {
    const { clients } = respondWithClientParts();
    clients.mockRejectedValueOnce(new Error("거래처를 불러오지 못했습니다."));
    mount("/screens/UI-15");
    const retry = await screen.findByTestId("list-retry");
    expect(screen.getByTestId("client-create")).toBeDisabled();
    fireEvent.click(retry);
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(clients).toHaveBeenCalledTimes(2);
    expectSingleShell();
  });

  it("part failure retries its direct client in the current tab", async () => {
    const { parts } = respondWithClientParts();
    parts.mockRejectedValueOnce(new Error("파트를 불러오지 못했습니다."));
    mount("/screens/UI-15");
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    fireEvent.click(screen.getByTestId("client-workspace-parts"));
    await screen.findByText("파트를 불러오지 못했습니다.");
    fireEvent.click(
      within(screen.getByTestId("part-page")).getByRole("button", {
        name: "다시 시도",
      }),
    );
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(parts).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-15");
    expectSingleShell();
  });

  it("UI-15 mobile entry retains the existing PC-only guard before feature requests", async () => {
    const { clients, parts, request } = respondWithClientParts();
    vi.stubGlobal(
      "matchMedia",
      vi.fn((query: string) => ({
        matches: query === "(max-width: 767px)",
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    mount("/screens/UI-15");
    await screen.findByText("PC에서 이용해 주세요");
    expect(clients).not.toHaveBeenCalled();
    expect(parts).not.toHaveBeenCalled();
    expect(screen.queryByTestId("client-page")).not.toBeInTheDocument();
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/common/uploads")),
    ).toBe(false);
    expectSingleShell();
  });
});

describe("common code route composition", () => {
  it.each(["/screens/UI-27", "/#UI-27"])(
    "%s opens four real tabs in the existing preference shell",
    async (path) => {
      const { request, maps, standards, histories } = respondWithCommonCodes();
      mount(path);
      await screen.findByLabelText("ONE 이름");
      expectSingleShell();
      expect(
        within(
          screen.getByRole("tablist", { name: "공통코드 작업" }),
        ).getAllByRole("tab"),
      ).toHaveLength(4);
      // 데이터를 다 읽은 뒤에 켜진다: 켜질 때까지 기다린다(느린 환경에서 깜빡 실패 방지).
      await waitFor(() =>
        expect(screen.getByTestId("code-group-title-add")).toBeEnabled(),
      );
      expect(maps).not.toHaveBeenCalled();
      expect(standards).not.toHaveBeenCalled();
      expect(histories).not.toHaveBeenCalled();
      fireEvent.click(screen.getByTestId("code-tab-maps"));
      expect(await screen.findByText("등록된 표기가 없습니다.")).toBeVisible();
      expect(screen.getByTestId("code-maps-panel")).toBeVisible();
      fireEvent.click(screen.getByTestId("code-tab-standards"));
      expect(await screen.findByLabelText("기초 잔액")).toHaveValue("1000");
      fireEvent.click(screen.getByTestId("code-tab-history"));
      expect(await screen.findByTestId("code-history-empty")).toBeVisible();
      expect(
        within(screen.getByTestId("code-history-panel")).queryByRole("button", {
          name: /저장|취소|다운로드/,
        }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTestId("code-group-title-add"),
      ).not.toBeInTheDocument();
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expect(router?.state.location.pathname).toBe("/screens/UI-27");
      expectSingleShell();
    },
  );

  it("code save refreshes history and the real map option client reads the changed name", async () => {
    const { request, codes, histories } = respondWithCommonCodes();
    mount("/screens/UI-27");
    fireEvent.change(await screen.findByLabelText("ONE 이름"), {
      target: { value: "바뀐 이름" },
    });
    fireEvent.click(screen.getByTestId("code-save"));
    await screen.findByText(
      "공통코드 1건을 저장했습니다. 선택 목록에 바로 반영됩니다.",
    );
    expect(codes).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByTestId("code-tab-history"));
    expect(await screen.findByTestId("code-history-row-0")).toHaveTextContent(
      "바뀐 이름",
    );
    expect(histories).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByTestId("code-tab-maps"));
    await screen.findByText("등록된 표기가 없습니다.");
    fireEvent.click(screen.getByTestId("code-map-title-add"));
    await screen.findByRole("option", { name: "업무 그룹" });
    fireEvent.change(screen.getByTestId("code-map-group"), {
      target: { value: "GENERAL" },
    });
    expect(
      await screen.findByRole("option", { name: "ONE · 바뀐 이름" }),
    ).toBeInTheDocument();
    expect(request).toHaveBeenCalledWith(
      "/api/common/code-groups/GENERAL/codes",
      { signal: expect.any(AbortSignal) },
    );
  });

  it("map creation uses its direct client and rereads group, selected codes and history", async () => {
    const { request, groups, codes, maps } = respondWithCommonCodes();
    mount("/screens/UI-27");
    await screen.findByLabelText("ONE 이름");
    fireEvent.click(screen.getByTestId("code-tab-maps"));
    await screen.findByText("등록된 표기가 없습니다.");
    fireEvent.click(screen.getByTestId("code-map-title-add"));
    fireEvent.change(screen.getByTestId("code-map-text"), {
      target: { value: "엑셀 별칭" },
    });
    await screen.findByRole("option", { name: "업무 그룹" });
    fireEvent.change(screen.getByTestId("code-map-group"), {
      target: { value: "GENERAL" },
    });
    await screen.findByRole("option", { name: "ONE · 처음 이름" });
    fireEvent.change(screen.getByTestId("code-map-code"), {
      target: { value: "ONE" },
    });
    fireEvent.click(screen.getByTestId("code-map-submit"));
    await waitFor(() =>
      expect(
        screen.queryByTestId("code-map-add-dialog"),
      ).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(groups).toHaveBeenCalledTimes(3));
    expect(codes).toHaveBeenCalledTimes(3);
    expect(maps).toHaveBeenCalledTimes(2);
    expect(request).toHaveBeenCalledWith("/api/common/code-maps", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        excelText: "엑셀 별칭",
        groupId: "GENERAL",
        code: "ONE",
      }),
    });
    fireEvent.click(screen.getByTestId("code-tab-history"));
    expect(await screen.findByTestId("code-history-row-0")).toHaveTextContent(
      "엑셀 별칭",
    );
    expect(router?.state.location.pathname).toBe("/screens/UI-27");
  });

  it("operating save retains the immediate null identity and refreshes code consumers and history", async () => {
    const { request, groups, codes, standards } = respondWithCommonCodes();
    mount("/screens/UI-27");
    await screen.findByLabelText("ONE 이름");
    fireEvent.click(screen.getByTestId("code-tab-standards"));
    fireEvent.change(await screen.findByLabelText("기초 잔액"), {
      target: { value: "2000" },
    });
    fireEvent.click(screen.getByTestId("operating-save"));
    expect(
      screen.queryByTestId("operating-effective-month"),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("operating-save-confirm"));
    await waitFor(() => expect(standards).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(groups).toHaveBeenCalledTimes(2));
    expect(codes).toHaveBeenCalledTimes(2);
    expect(request).toHaveBeenCalledWith("/api/common/operating-standards", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        items: [
          {
            standardCode: "CASH_BASE_AMT",
            value: "2000",
            effectiveMonth: null,
            action: "update",
          },
        ],
      }),
    });
    fireEvent.click(screen.getByTestId("code-tab-history"));
    expect(await screen.findByTestId("code-history-row-0")).toHaveTextContent(
      "2000",
    );
    expect(router?.state.location.pathname).toBe("/screens/UI-27");
  });

  it("operating dirty state guards local tabs and shell close; cancellation preserves and discard reloads", async () => {
    const { request } = respondWithCommonCodes();
    mount("/screens/UI-27");
    await screen.findByLabelText("ONE 이름");
    fireEvent.click(screen.getByTestId("code-tab-standards"));
    fireEvent.change(await screen.findByLabelText("기초 잔액"), {
      target: { value: "2999" },
    });
    await waitFor(() =>
      expect(screen.getByTestId("screen-tab-ui-27")).toHaveTextContent("●"),
    );
    fireEvent.click(screen.getByTestId("screen-tab-close-ui-27"));
    await screen.findByTestId("unsaved-dialog");
    fireEvent.click(screen.getByTestId("confirmation-cancel"));
    fireEvent.click(screen.getByTestId("code-tab-history"));
    fireEvent.click(screen.getByTestId("code-discard-cancel"));
    expect(screen.getByLabelText("기초 잔액")).toHaveValue("2999");
    fireEvent.click(screen.getByTestId("code-tab-history"));
    fireEvent.click(screen.getByTestId("code-discard-proceed"));
    await screen.findByTestId("code-history-empty");
    fireEvent.click(screen.getByTestId("code-tab-standards"));
    expect(await screen.findByLabelText("기초 잔액")).toHaveValue("1000");
    expect(
      request.mock.calls.filter(
        ([url, init]) =>
          url === "/api/common/operating-standards" && init?.method === "PUT",
      ),
    ).toHaveLength(0);
  });

  it("mapping dialog dirty state reaches the shell and local tab guard without losing cancelled input", async () => {
    respondWithCommonCodes();
    mount("/screens/UI-27");
    await screen.findByLabelText("ONE 이름");
    fireEvent.click(screen.getByTestId("code-tab-maps"));
    await screen.findByText("등록된 표기가 없습니다.");
    fireEvent.click(screen.getByTestId("code-map-title-add"));
    fireEvent.change(screen.getByTestId("code-map-text"), {
      target: { value: "입력 유지" },
    });
    await waitFor(() =>
      expect(screen.getByTestId("screen-tab-ui-27")).toHaveTextContent("●"),
    );
    fireEvent.click(screen.getByTestId("code-tab-history"));
    fireEvent.click(screen.getByTestId("code-discard-cancel"));
    expect(screen.getByTestId("code-map-text")).toHaveValue("입력 유지");
    fireEvent.click(screen.getByTestId("code-tab-history"));
    fireEvent.click(screen.getByTestId("code-discard-proceed"));
    await screen.findByTestId("code-history-empty");
    expect(screen.queryByTestId("code-map-add-dialog")).not.toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("screen-tab-ui-27")).not.toHaveTextContent("●"),
    );
  });

  it.each([
    ["maps", "code-map-retry", "등록된 표기가 없습니다."],
    ["standards", "operating-retry", "기초 잔액"],
    ["history", "code-history-retry", "기록이 없습니다."],
  ])(
    "%s failures stay in the routed panel and retry the real client",
    async (tab, retry, recovered) => {
      const fixture = respondWithCommonCodes();
      const reader =
        tab === "maps"
          ? fixture.maps
          : tab === "standards"
            ? fixture.standards
            : fixture.histories;
      reader.mockRejectedValueOnce(new Error("조회 연결 실패"));
      mount("/screens/UI-27");
      await screen.findByLabelText("ONE 이름");
      fireEvent.click(screen.getByTestId(`code-tab-${tab}`));
      fireEvent.click(await screen.findByTestId(retry));
      if (tab === "standards")
        expect(await screen.findByLabelText(recovered)).toBeVisible();
      else expect(await screen.findByText(recovered)).toBeVisible();
      expect(reader).toHaveBeenCalledTimes(2);
      expect(router?.state.location.pathname).toBe("/screens/UI-27");
      expectSingleShell();
    },
  );

  it("UI-27 mobile direct entry keeps the existing PC-only gate ahead of every feature client", async () => {
    const { groups, codes, maps, standards, histories } =
      respondWithCommonCodes();
    vi.stubGlobal(
      "matchMedia",
      vi.fn((query: string) => ({
        matches: query === "(max-width: 767px)",
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    mount("/screens/UI-27");
    expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
    for (const reader of [groups, codes, maps, standards, histories])
      expect(reader).not.toHaveBeenCalled();
    expect(screen.queryByTestId("code-page")).not.toBeInTheDocument();
    expectSingleShell();
  });
});

describe("application route registration", () => {
  it("UI-25 direct entry dispatches MenuPage and saves through its real client, refreshing the single shell", async () => {
    const { request, list } = respondWithMenuSettings();
    mount("/screens/UI-25");
    const input = await screen.findByLabelText("M029 메뉴 이름");
    expect(screen.getAllByTestId("menu-settings-page")).toHaveLength(1);
    expectSingleShell();
    expect(request).toHaveBeenCalledWith("/api/common/menu-settings", {
      signal: expect.any(AbortSignal),
    });
    fireEvent.change(input, { target: { value: "메뉴 설정" } });
    fireEvent.click(screen.getByTestId("menu-settings-save"));
    await screen.findByTestId("menu-settings-toast");
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(menuCalls(request)).toHaveLength(2));
    const saves = request.mock.calls.filter(
      ([url, init]) =>
        url === "/api/common/menu-settings" && init?.method === "PUT",
    );
    expect(saves).toHaveLength(1);
    expect(JSON.parse(String(saves[0][1]?.body))).toEqual({
      items: [
        {
          menuId: "M029",
          menuNm: "메뉴 설정",
          upperMenuId: "G010",
          sortOrd: 1,
          useYn: "Y",
          pcOnlyYn: "Y",
        },
      ],
      movedMenuIds: [],
    });
    await waitFor(() =>
      expect(screen.getByTestId("screen-tab-ui-25")).toHaveTextContent(
        "메뉴 설정",
      ),
    );
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(router?.state.location.pathname).toBe("/screens/UI-25");
    expectSingleShell();
  });

  it("UI-25 registration remains a dialog and uses the existing options and POST clients", async () => {
    const { request, list } = respondWithMenuSettings();
    mount("/screens/UI-25");
    await screen.findByLabelText("M029 메뉴 이름");
    fireEvent.click(screen.getByTestId("menu-registration-open"));
    await screen.findByTestId("menu-registration-no-screens");
    expect(request).toHaveBeenCalledWith("/api/common/menu-options?menuLvl=2", {
      signal: expect.any(AbortSignal),
    });
    fireEvent.click(screen.getByTestId("menu-registration-level-1"));
    fireEvent.change(screen.getByTestId("menu-registration-name"), {
      target: { value: "새 묶음" },
    });
    await waitFor(() =>
      expect(screen.getByTestId("menu-registration-submit")).toBeEnabled(),
    );
    fireEvent.click(screen.getByTestId("menu-registration-submit"));
    await screen.findByLabelText("G011 메뉴 이름");
    expect(
      screen.queryByTestId("menu-registration-dialog"),
    ).not.toBeInTheDocument();
    expect(request).toHaveBeenCalledWith("/api/common/menus", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ menuLvl: 1, menuNm: "새 묶음" }),
    });
    await waitFor(() =>
      expect(
        request.mock.calls.filter(
          ([url, init]) => url === "/api/common/menus" && !init?.method,
        ),
      ).toHaveLength(2),
    );
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-25");
    expectSingleShell();
  });

  it("UI-25 keeps its draft across child routes and uses the shared tab-close guard", async () => {
    respondWithMenuSettings();
    mount("/screens/UI-25");
    fireEvent.change(await screen.findByLabelText("M029 메뉴 이름"), {
      target: { value: "미저장 메뉴" },
    });
    await waitFor(() =>
      expect(screen.getByTestId("screen-tab-ui-25")).toHaveTextContent("●"),
    );
    const preferences = screen.getByTestId("user-preferences");
    await act(async () => {
      await router!.navigate("/screens/ADDED");
    });
    await screen.findByRole("region", { name: "ADDED 본문" });
    await act(async () => {
      await router!.navigate("/screens/UI-25");
    });
    expect(screen.getByLabelText("M029 메뉴 이름")).toHaveValue("미저장 메뉴");
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    fireEvent.click(screen.getByTestId("screen-tab-close-ui-25"));
    await screen.findByTestId("unsaved-dialog");
    fireEvent.click(screen.getByTestId("confirmation-cancel"));
    expect(screen.getByLabelText("M029 메뉴 이름")).toHaveValue("미저장 메뉴");
    expectSingleShell();
  });

  it("UI-25 mobile direct entry uses the existing PC-only gate without loading management data", async () => {
    const { request, list } = respondWithMenuSettings();
    vi.stubGlobal(
      "matchMedia",
      vi.fn((query: string) => ({
        matches: query === "(max-width: 767px)",
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    mount("/screens/UI-25");
    expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
    expect(screen.queryByTestId("menu-settings-page")).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
    expect(
      request.mock.calls.some(([url]) =>
        url.startsWith("/api/common/menu-options"),
      ),
    ).toBe(false);
    expectSingleShell();
  });

  it("UI-25 keeps an empty management result empty without replacing the shell or disabling registration", async () => {
    const { list } = respondWithMenuSettings();
    list.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ success: true, meta: {}, data: { items: [] } }),
    });
    mount("/screens/UI-25");
    expect(await screen.findByText("등록된 메뉴가 없습니다.")).toBeVisible();
    // 데이터를 다 읽은 뒤에 켜진다: 켜질 때까지 기다린다(느린 환경에서 깜빡 실패 방지).
    await waitFor(() =>
      expect(screen.getByTestId("menu-registration-open")).toBeEnabled(),
    );
    expect(screen.queryByTestId("menu-settings-retry")).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/screens/UI-25");
    expectSingleShell();
  });

  it("UI-25 management failure retries inside the same route without leaking server details", async () => {
    const { list } = respondWithMenuSettings();
    list.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({
        success: false,
        meta: {},
        message: "private SQL error",
        data: { items: [] },
      }),
    });
    mount("/screens/UI-25");
    const retry = await screen.findByTestId("menu-settings-retry");
    expect(screen.queryByText("private SQL error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("menu-settings-save")).not.toBeInTheDocument();
    fireEvent.click(retry);
    expect(await screen.findByLabelText("M029 메뉴 이름")).toBeVisible();
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-25");
    expectSingleShell();
  });

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
    // 저장 후 재조회와 상태 반영까지 끝나도 같은 방문을 다시 저장하지 않는다.
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
    expect(
      request.mock.calls.filter(([, init]) => init?.method === "PUT"),
    ).toHaveLength(1);
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

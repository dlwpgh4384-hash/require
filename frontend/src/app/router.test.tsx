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
import type { RateRenegotiationResult } from "../pages/raterenegotiations/rateRenegotiations";
import type { BusinessPlanComparisonResult } from "../pages/commonops/businessPlanComparisons";
import type { CashFlowStatementResult } from "../pages/cashflowstatements/cashFlowStatements";
import type { MonthClose } from "../pages/monthcloses/monthCloses";

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
      prefKey: url.split("/").at(-1),
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

function respondWithCashFlowStatements(available = true) {
  const menus = response();
  menus.data.push(
    {
      ...menus.data[0],
      menuId: "G007",
      menuNm: "재무·자금",
      scrnId: null,
      sortOrd: 2,
    },
    {
      ...menus.data[0],
      menuId: "M023",
      upperMenuId: "G007",
      menuNm: "자금수지표",
      scrnId: "UI-21",
      menuLvl: 2,
    },
  );
  menus.meta.screenAccess.push({ scrnId: "UI-21", available, pcOnlyYn: "N" });
  const result: CashFlowStatementResult = {
    year: 2026,
    minimumCash: 1000000000,
    months: Array.from({ length: 12 }, (_, index) => ({
      yearMonth: `2026${String(index + 1).padStart(2, "0")}`,
      closed: index < 10,
      openingBalance: null,
      closingBalance: null,
      cashSales: null,
      miscIncome: null,
      loan: null,
      incomeTotal: null,
      regularPayroll: null,
      contractFreelancePayroll: null,
      outsourcing: null,
      unpaidPayroll: null,
      overhead: null,
      corporateTaxPayment: null,
      expenseTotal: null,
    })),
    warnings: [],
    numericTable: {
      columns: [
        ...Array.from({ length: 12 }, (_, index) => ({
          label: `${index + 1}월`,
          actual: index < 10,
          warning: false,
        })),
        ...["상반기", "하반기", "연간"].map((label) => ({
          label,
          actual: null,
          warning: false,
        })),
      ],
      rows: [
        "전월이월금",
        "수입",
        "현금매출",
        "잡수익",
        "대여금",
        "수입 합계",
        "지출",
        "인건비(정규직)",
        "인건비(계약직·프리랜서)",
        "용역비(외주)",
        "미지급 인건비",
        "제경비",
        "법인세 납부",
        "지출 합계",
        "이월금",
      ].map((label, index) => ({
        label,
        style: [1, 6].includes(index)
          ? "group"
          : [0, 5, 13, 14].includes(index)
            ? "bold"
            : "indent",
        values: Array.from({ length: 15 }, () => null),
      })),
    },
  };
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const list = vi.fn(async () => success(result));
  const download = vi.fn(async () => ({
    ok: true,
    status: 200,
    headers: new Headers({
      "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent("자금수지표.xlsx")}`,
    }),
    blob: async () => new Blob(),
    json: async () => ({
      success: false,
      message: "다운로드 실패",
      errors: [],
    }),
  }));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/cash-flow-statements/excel?"))
      return download();
    if (url.startsWith("/api/common/cash-flow-statements?")) return list();
    if (url.startsWith("/api/common/user-preferences/"))
      return {
        ok: true,
        status: 200,
        json: async () => ({
          ...preferenceResponse(url),
          meta: {
            periodBasis: {
              latestYm: "202611",
              cashBaseYm: "202501",
              closedMonths: ["202610"],
            },
          },
        }),
      };
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, download };
}

function respondWithMonthCloses(available = true) {
  const menus = response();
  menus.data.push(
    {
      ...menus.data[0],
      menuId: "G008",
      menuNm: "마감",
      scrnId: null,
      sortOrd: 2,
    },
    {
      ...menus.data[0],
      menuId: "M024",
      upperMenuId: "G008",
      menuNm: "월 마감",
      scrnId: "UI-14",
      menuLvl: 2,
      pcOnlyYn: "Y",
    },
  );
  menus.meta.screenAccess.push({ scrnId: "UI-14", available, pcOnlyYn: "Y" });
  const result: MonthClose = {
    yearMonth: "2026-10",
    isClosed: false,
    modifiedAt: null,
    closedAt: null,
    closedBy: null,
    totalCount: 6,
    attentionCount: 0,
    checks: [
      ["급여 업로드", "UI-02"],
      ["MM 1.0 초과자", "UI-12"],
      ["세금계산서 번호 미입력", "UI-13"],
      ["재무 항목", "UI-10"],
      ["영업 파트 정산", "UI-19"],
      ["청구 누락 점검", "UI-13"],
    ].map(([name, targetScreenId]) => ({
      name,
      targetScreenId,
      status: "정상",
      description: "점검 완료",
    })),
  };
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      meta: {},
      data: structuredClone(data),
    }),
  });
  const list = vi.fn(async () => success(result));
  const history = vi.fn(async () => success({ items: [] }));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url === "/api/common/month-closes/2026-10") return list();
    if (url === "/api/common/month-close-history")
      return history();
    if (url === "/api/common/month-close-badge")
      return success({ needsAttention: true });
    if (url.startsWith("/api/common/user-preferences/")) {
      const preference = preferenceResponse(url);
      return {
        ok: true,
        status: 200,
        json: async () => ({
          ...preference,
          data: url.endsWith("/PERIOD/UI-14")
            ? {
                ...preference.data,
                found: true,
                prefVal: JSON.stringify({ year: 2026, month: 10 }),
              }
            : preference.data,
          meta: {
            periodBasis: {
              latestYm: "202611",
              cashBaseYm: "202601",
              closedMonths: [],
            },
          },
        }),
      };
    }
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, history };
}

describe("month close route composition", () => {
  it.each(["/screens/UI-14", "/#UI-14"])(
    "%s resolves the registered page and its public clients in one shell",
    async (path) => {
      const { request } = respondWithMonthCloses();
      mount(path);
      await screen.findByTestId("month-close-check-5");
      expect(await screen.findByText("마감 이력 없음")).toBeVisible();
      expect(screen.getAllByTestId("month-close-page")).toHaveLength(1);
      expect(screen.getAllByTestId(/^month-close-check-\d+$/)).toHaveLength(6);
      expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
        "월 마감",
      );
      expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveValue(
        "2026-10",
      );
      expect(request).toHaveBeenCalledWith("/api/common/month-closes/2026-10", {
        credentials: "same-origin",
        signal: expect.any(AbortSignal),
      });
      expect(request).toHaveBeenCalledWith(
        "/api/common/month-close-history",
        {
          credentials: "same-origin",
          signal: expect.any(AbortSignal),
        },
      );
      expect(await screen.findByTestId("menu-badge-UI-14")).toHaveTextContent(
        "!",
      );
      expect(request).toHaveBeenCalledWith("/api/common/month-close-badge", {
        signal: expect.any(AbortSignal),
      });
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expect(
        request.mock.calls.some(
          ([url, init]) => url.includes("/month-closes/") && init?.method,
        ),
      ).toBe(false);
      expect(router?.state.location.pathname).toBe("/screens/UI-14");
      expectSingleShell();
    },
  );

  it("M024 under G008 opens UI-14 without replacing the preference shell", async () => {
    respondWithMonthCloses();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    fireEvent.click(screen.getByTestId("menu-toggle-g008"));
    fireEvent.click(screen.getByRole("link", { name: "월 마감" }));
    await screen.findByTestId("month-close-check-5");
    expect(router?.state.location.pathname).toBe("/screens/UI-14");
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expectSingleShell();
  });

  it("the existing unavailable-screen guard prevents month detail and history reads", async () => {
    const { list, history } = respondWithMonthCloses(false);
    mount("/screens/UI-14");
    expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
    expect(screen.queryByTestId("month-close-page")).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
    expect(history).not.toHaveBeenCalled();
    expectSingleShell();
  });

  it("mobile direct entry retains the existing PC-only gate before feature reads", async () => {
    const { list, history } = respondWithMonthCloses();
    vi.stubGlobal(
      "matchMedia",
      vi.fn((query: string) => ({
        matches: query === "(max-width: 767px)",
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    mount("/screens/UI-14");
    expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
    expect(screen.queryByTestId("month-close-page")).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
    expect(history).not.toHaveBeenCalled();
    expectSingleShell();
  });

  it("a routed read failure retries the same selected month without replacing the shell", async () => {
    const { list } = respondWithMonthCloses();
    list.mockRejectedValueOnce(new Error("점검 조회 실패"));
    mount("/screens/UI-14");
    fireEvent.click(await screen.findByTestId("month-close-retry"));
    await screen.findByTestId("month-close-check-5");
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-14");
    expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveValue(
      "2026-10",
    );
    expectSingleShell();
  });
});

describe("cash flow statement route composition", () => {
  it.each(["/screens/UI-21", "/#UI-21"])(
    "%s resolves UI-21 through the public year-only client in one shell",
    async (path) => {
      const { request } = respondWithCashFlowStatements();
      mount(path);
      expect(await screen.findByTestId("cash-flow-empty")).toBeVisible();
      expect(screen.getAllByTestId("cash-flow-statements-page")).toHaveLength(
        1,
      );
      expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
        "자금수지표",
      );
      expect(screen.getByTestId("period-selector")).toHaveAttribute(
        "data-kind",
        "year",
      );
      expect(screen.queryByTestId("period-month")).not.toBeInTheDocument();
      expect(screen.getByTestId("cash-flow-balance-chart")).toBeVisible();
      expect(
        within(screen.getByTestId("cash-flow-table")).getAllByRole("row"),
      ).toHaveLength(16);
      for (const header of within(
        screen.getByTestId("cash-flow-table"),
      ).getAllByRole("columnheader")) {
        expect(within(header).queryByRole("button")).not.toBeInTheDocument();
      }
      const actions = within(screen.getByTestId("screen-actions"));
      expect(actions.getAllByTestId("cash-flow-excel")).toHaveLength(1);
      await waitFor(() =>
        expect(actions.getByTestId("cash-flow-excel")).toBeEnabled(),
      );
      expect(request).toHaveBeenCalledWith(
        "/api/common/cash-flow-statements?year=2026",
        {
          signal: expect.any(AbortSignal),
        },
      );
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expect(router?.state.location.pathname).toBe("/screens/UI-21");
      expectSingleShell();
    },
  );

  it("a pending public read keeps the routed report shell and disables title export", async () => {
    const { request, list, download } = respondWithCashFlowStatements();
    const result = await list.getMockImplementation()!();
    let complete!: (value: typeof result) => void;
    list.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    );
    mount("/screens/UI-21");
    await waitFor(() => expect(list).toHaveBeenCalledTimes(1));
    expect(await screen.findByTestId("cash-flow-loading")).toBeVisible();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "자금수지표",
    );
    expect(screen.getByTestId("period-selector")).toHaveAttribute(
      "data-kind",
      "year",
    );
    expect(screen.getByTestId("cash-flow-balance-chart")).toBeVisible();
    const table = screen.getByTestId("cash-flow-table-pending");
    expect(table).toHaveAttribute("aria-busy", "true");
    expect(within(table).getAllByRole("columnheader")).toHaveLength(16);
    expect(screen.getByTestId("money-unit")).toBeVisible();
    expect(screen.queryByTestId("cash-flow-empty")).not.toBeInTheDocument();
    const button = within(screen.getByTestId("screen-actions")).getByTestId(
      "cash-flow-excel",
    );
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(download).not.toHaveBeenCalled();
    expect(request).toHaveBeenCalledWith(
      "/api/common/cash-flow-statements?year=2026",
      { signal: expect.any(AbortSignal) },
    );
    expectSingleShell();
    await act(async () => {
      complete(result);
    });
    expect(await screen.findByTestId("cash-flow-empty")).toBeVisible();
    await waitFor(() => expect(button).toBeEnabled());
    expect(router?.state.location.pathname).toBe("/screens/UI-21");
    expectSingleShell();
  });

  it("M023 under G007 opens UI-21 without replacing preferences", async () => {
    const { list } = respondWithCashFlowStatements();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    fireEvent.click(screen.getByTestId("menu-toggle-g007"));
    fireEvent.click(screen.getByRole("link", { name: "자금수지표" }));
    expect(await screen.findByTestId("cash-flow-empty")).toBeVisible();
    expect(list).toHaveBeenCalledTimes(1);
    expect(router?.state.location.pathname).toBe("/screens/UI-21");
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expectSingleShell();
  });

  it("an unavailable UI-21 stays behind the existing screen guard", async () => {
    const { list, download } = respondWithCashFlowStatements(false);
    mount("/screens/UI-21");
    expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
    expect(
      screen.queryByTestId("cash-flow-statements-page"),
    ).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
    expect(download).not.toHaveBeenCalled();
    expectSingleShell();
  });

  it("a failed routed read disables export and retries the same public client", async () => {
    const { list } = respondWithCashFlowStatements();
    list.mockRejectedValueOnce(new Error("조회 실패"));
    mount("/screens/UI-21");
    await screen.findByTestId("cash-flow-error");
    expect(screen.queryByTestId("cash-flow-empty")).not.toBeInTheDocument();
    expect(screen.getByTestId("cash-flow-excel")).toBeDisabled();
    fireEvent.click(screen.getByTestId("cash-flow-retry"));
    expect(await screen.findByTestId("cash-flow-empty")).toBeVisible();
    await waitFor(() =>
      expect(screen.getByTestId("cash-flow-excel")).toBeEnabled(),
    );
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-21");
    expectSingleShell();
  });

  it.each([true, false])(
    "title export uses the local client and reports success=%s",
    async (succeeds) => {
      const { request, download } = respondWithCashFlowStatements();
      const created = vi.fn(() => "blob:cash-flow");
      const revoked = vi.fn();
      vi.stubGlobal(
        "URL",
        class extends URL {
          static createObjectURL = created;
          static revokeObjectURL = revoked;
        },
      );
      const click = vi
        .spyOn(HTMLAnchorElement.prototype, "click")
        .mockImplementation(() => undefined);
      if (!succeeds)
        download.mockResolvedValueOnce({
          ok: false,
          status: 500,
          headers: new Headers(),
          blob: async () => new Blob(),
          json: async () => ({
            success: false,
            message: "다운로드 실패",
            errors: [],
          }),
        });
      mount("/screens/UI-21");
      await screen.findByTestId("cash-flow-table");
      fireEvent.change(screen.getByTestId("money-unit"), {
        target: { value: "원" },
      });
      const button = within(screen.getByTestId("screen-actions")).getByTestId(
        "cash-flow-excel",
      );
      await waitFor(() => expect(button).toBeEnabled());
      // Complete delayed URL cleanup before afterEach restores the URL global.
      vi.useFakeTimers();
      try {
        await act(async () => {
          fireEvent.click(button);
        });
        expect(screen.getByTestId("cash-flow-toast")).toHaveTextContent(
          succeeds
            ? "엑셀 파일을 만들었습니다 (조회 결과"
            : "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.",
        );
        expect(request).toHaveBeenCalledWith(
          `/api/common/cash-flow-statements/excel?${new URLSearchParams({ year: "2026", amountUnit: "원" })}`,
        );
        expect(download).toHaveBeenCalledTimes(1);
        expect(click).toHaveBeenCalledTimes(succeeds ? 1 : 0);
        expect(created).toHaveBeenCalledTimes(succeeds ? 1 : 0);
        expect(revoked).not.toHaveBeenCalled();
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
        expect(revoked).toHaveBeenCalledTimes(succeeds ? 1 : 0);
        if (succeeds) expect(revoked).toHaveBeenCalledWith("blob:cash-flow");
        expect(router?.state.location.pathname).toBe("/screens/UI-21");
        expectSingleShell();
      } finally {
        vi.useRealTimers();
      }
    },
  );
});

function respondWithBusinessPlanComparisons(available = true) {
  const menus = response();
  menus.data.push(
    {
      ...menus.data[0],
      menuId: "G007",
      menuNm: "재무·자금",
      scrnId: null,
      sortOrd: 2,
    },
    {
      ...menus.data[0],
      menuId: "M022",
      upperMenuId: "G007",
      menuNm: "사업계획 대비 실적",
      scrnId: "UI-18",
      menuLvl: 2,
    },
  );
  menus.meta.screenAccess.push({ scrnId: "UI-18", available, pcOnlyYn: "N" });
  const result: BusinessPlanComparisonResult = {
    year: 2026,
    baseMonth: "202611",
    baseMonthClosed: false,
    hasTargets: false,
    summary: {
      cumulativeRevenueAchievementRate: null,
      cumulativeRevenueActual: null,
      cumulativeRevenueTarget: 0,
      cumulativeOperatingProfitAchievementRate: null,
      cumulativeOperatingProfitActual: null,
      cumulativeOperatingProfitTarget: 0,
      baseMonthRevenueAchievementRate: null,
      baseMonthRevenueTarget: 0,
    },
    months: Array.from({ length: 12 }, (_, index) => ({
      yearMonth: `2026${String(index + 1).padStart(2, "0")}`,
      revenueTarget: 0,
      revenueActual: null,
      operatingProfitTarget: 0,
      operatingProfitActual: null,
      achievementRate: null,
      profitRateDifference: null,
    })),
  };
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const list = vi.fn(async () => success(result));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/business-plan-comparisons?")) return list();
    if (url.startsWith("/api/common/user-preferences/"))
      return {
        ok: true,
        status: 200,
        json: async () => ({
          ...preferenceResponse(url),
          meta: {
            periodBasis: {
              latestYm: "202611",
              cashBaseYm: "202603",
              closedMonths: ["202610"],
            },
          },
        }),
      };
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list };
}

describe("business plan comparison route composition", () => {
  it.each(["/screens/UI-18", "/#UI-18"])(
    "%s renders the developed screen with public client and title actions",
    async (path) => {
      const { request } = respondWithBusinessPlanComparisons();
      mount(path);
      expect(await screen.findByTestId("business-plan-empty")).toBeVisible();
      expectSingleShell();
      expect(
        screen.getAllByTestId("business-plan-comparison-page"),
      ).toHaveLength(1);
      expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
        "사업계획 대비 실적",
      );
      const actions = within(screen.getByTestId("screen-actions"));
      expect(actions.getAllByTestId("M25-399")).toHaveLength(1);
      expect(actions.getAllByTestId("M25-400")).toHaveLength(1);
      expect(request).toHaveBeenCalledWith(
        "/api/common/business-plan-comparisons?year=2026&baseMonth=202611",
        {
          signal: expect.any(AbortSignal),
        },
      );
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      fireEvent.click(actions.getByRole("button", { name: "목표 업로드" }));
      expect(
        await screen.findByRole("dialog", { name: "목표 업로드" }),
      ).toBeVisible();
      expect(router?.state.location.pathname).toBe("/screens/UI-18");
      expect(
        request.mock.calls.some(([, init]) => init?.method === "POST"),
      ).toBe(false);
    },
  );

  it("M022 opens UI-18 in the existing preferences shell", async () => {
    respondWithBusinessPlanComparisons();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    fireEvent.click(screen.getByTestId("menu-toggle-g007"));
    fireEvent.click(screen.getByRole("link", { name: "사업계획 대비 실적" }));
    await screen.findByTestId("business-plan-empty");
    expect(router?.state.location.pathname).toBe("/screens/UI-18");
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expectSingleShell();
  });

  it("a failed comparison retries the direct client without losing title upload actions", async () => {
    const { list } = respondWithBusinessPlanComparisons();
    list.mockRejectedValueOnce(new Error("비교 조회 실패"));
    mount("/screens/UI-18");
    fireEvent.click(await screen.findByTestId("M25-414"));
    expect(await screen.findByTestId("business-plan-empty")).toBeVisible();
    expect(list).toHaveBeenCalledTimes(2);
    expect(
      within(screen.getByTestId("screen-actions")).getByTestId("M25-400"),
    ).toBeEnabled();
    expectSingleShell();
  });

  it("the existing unavailable-screen guard prevents comparison requests", async () => {
    const { list } = respondWithBusinessPlanComparisons(false);
    mount("/screens/UI-18");
    expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
    expect(
      screen.queryByTestId("business-plan-comparison-page"),
    ).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
    expectSingleShell();
  });
});

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
      await screen.findByRole("dialog", { name: "영업 파트 추가" });
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
    await waitFor(() =>
      expect(screen.getByTestId("client-create")).toBeEnabled(),
    );
    fireEvent.click(screen.getByTestId("client-create"));
    expect(await screen.findByText(/구분을 고르면 고객사는/)).toBeVisible();
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
    await waitFor(() =>
      expect(screen.getByTestId("upload-open")).toBeEnabled(),
    );
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
    await waitFor(() => expect(clients).toHaveBeenCalled());
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
      expect(await screen.findByLabelText("기초 잔액")).toHaveValue("1,000");
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
    // 등록 뒤 목록에 생긴 줄의 그룹·코드 이름을 보이려고(시안 UI-27 표기 대응 목록) 그룹 목록과 그 그룹 코드를 한 번씩 더 읽는다.
    await waitFor(() => expect(groups).toHaveBeenCalledTimes(4));
    expect(codes).toHaveBeenCalledTimes(4);
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
    expect(screen.getByLabelText("기초 잔액")).toHaveValue("2,999");
    fireEvent.click(screen.getByTestId("code-tab-history"));
    fireEvent.click(screen.getByTestId("code-discard-proceed"));
    await screen.findByTestId("code-history-empty");
    fireEvent.click(screen.getByTestId("code-tab-standards"));
    expect(await screen.findByLabelText("기초 잔액")).toHaveValue("1,000");
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

function rateRenegotiationResult(): RateRenegotiationResult {
  return {
    baseYearMonth: "2026-02",
    appliedIncreaseRate: 5,
    items: [],
    summary: {
      targetCount: 0,
      averageProfitRate: null,
      targetProfitRate: 20,
      assumedAnnualProfitIncrease: 0,
      targetAnnualProfitIncrease: 0,
    },
    totalAnnualProfitIncrease: 0,
    formulaHelp: {
      renewalDate: "갱신일 정의",
      monthlyCost: "월 원가 정의",
      profitRate: "이익률 정의",
      targetUnitPrice: "목표 단가 정의",
      requiredIncreaseRate: "필요 인상률 정의",
      annualProfitIncrease: "연 이익 정의",
    },
  };
}

function respondWithRateRenegotiations(available = true) {
  const menus = response();
  menus.data.push(
    {
      ...menus.data[0],
      menuId: "G006",
      menuNm: "원가·수익",
      scrnId: null,
      sortOrd: 2,
    },
    {
      ...menus.data[0],
      menuId: "M017",
      upperMenuId: "G006",
      menuNm: "단가 재협상 대상",
      scrnId: "UI-32",
      menuLvl: 2,
    },
  );
  menus.meta.screenAccess.push({ scrnId: "UI-32", available, pcOnlyYn: "N" });
  const result = rateRenegotiationResult();
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const list = vi.fn(async () => success(result));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/"))
      return {
        ok: true,
        status: 200,
        json: async () => preferenceResponse(url),
      };
    if (url.startsWith("/api/common/rate-renegotiations?")) return list();
    if (
      url === "/api/common/projects" ||
      url.startsWith("/api/common/code-groups/")
    )
      return success({ items: [] });
    const developer = result.items.find(
      (row) => url === `/api/common/developers/${row.developerId}`,
    );
    if (developer)
      return success({
        developerId: developer.developerId,
        name: developer.developerName,
        managementNumber: developer.developerId,
        employeeTypeName: developer.employeeTypeName,
        useYn: "Y",
        skillNames: [],
      });
    if (
      result.items.some((row) =>
        ["assignments", "statuses", "change-history"].some(
          (suffix) =>
            url === `/api/common/developers/${row.developerId}/${suffix}`,
        ),
      )
    )
      return success({ items: [], canManageStatuses: true });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { result, request, list, success };
}

describe("rate renegotiation route composition", () => {
  it.each(["/screens/UI-32", "/#UI-32"])(
    "%s renders UI-32 through the existing shell and public client",
    async (path) => {
      const { request } = respondWithRateRenegotiations();
      mount(path);
      await screen.findByTestId("list-empty");
      expectSingleShell();
      expect(screen.getAllByTestId("rate-renegotiation-page")).toHaveLength(1);
      expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
        "단가 재협상 대상",
      );
      expect(screen.getByTestId("rate-summary").children).toHaveLength(4);
      expect(
        within(screen.getByTestId("rate-list")).getAllByRole("columnheader"),
      ).toHaveLength(11);
      expect(
        within(screen.getByTestId("rate-formulas"))
          .getAllByRole("columnheader")
          .map((header) => header.textContent),
      ).toEqual(["항목", "계산식", "참고"]);
      expect(
        screen.getAllByRole("button", { name: /엑셀 다운로드/ }),
      ).toHaveLength(1);
      expect(screen.getByTestId("screen-actions")).toBeEmptyDOMElement();
      expect(request).toHaveBeenCalledWith(
        "/api/common/rate-renegotiations?renewalMonths=6&profitRateThreshold=15&increaseRate=5",
        { signal: expect.any(AbortSignal) },
      );
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expect(
        request.mock.calls
          .filter(([url]) => url.startsWith("/api/common/rate-renegotiations?"))
          .every(([, init]) => !init?.headers && !init?.body && !init?.method),
      ).toBe(true);
    },
  );

  it("M017 opens UI-32 under G006 without replacing the preferences provider", async () => {
    respondWithRateRenegotiations();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    fireEvent.click(screen.getByTestId("menu-toggle-g006"));
    fireEvent.click(screen.getByRole("link", { name: "단가 재협상 대상" }));
    await screen.findByTestId("list-empty");
    expect(router?.state.location.pathname).toBe("/screens/UI-32");
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expectSingleShell();
  });

  it.each(["developer-first", "developer-second"])(
    "opens clicked %s rather than assignmentId and retains list state on close",
    async (developerId) => {
      const { result, request, list } = respondWithRateRenegotiations();
      result.items = ["developer-first", "developer-second"].map(
        (id, index) => ({
          assignmentId: `assignment-${index}`,
          developerId: id,
          developerName: `개발자 ${index}`,
          employeeTypeName: "계약직",
          projectName: "재협상 프로젝트",
          renewalDate: "2026-03-03",
          daysUntilRenewal: 30,
          monthlyMm: 1,
          currentUnitPrice: 1000000,
          monthlyCost: 900000,
          profitRate: 10,
          targetUnitPrice: 1130000,
          requiredIncreaseRate: 13,
          annualProfitIncrease: 600000,
        }),
      );
      mount("/screens/UI-32");
      const row = result.items.find(
        (item) => item.developerId === developerId,
      )!;
      await screen.findByTestId(`list-row-${row.assignmentId}`);
      fireEvent.change(screen.getByLabelText("갱신 시점"), {
        target: { value: "3" },
      });
      fireEvent.click(screen.getByTestId("list-search"));
      await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
      await screen.findByTestId(`list-row-${row.assignmentId}`);
      fireEvent.change(screen.getByLabelText("인상률"), {
        target: { value: "8" },
      });
      await waitFor(() => expect(list).toHaveBeenCalledTimes(3));
      await screen.findByTestId(`list-row-${row.assignmentId}`);
      fireEvent.click(screen.getByTestId("list-columns"));
      fireEvent.click(screen.getByTestId("list-column-monthlyCost"));
      fireEvent.click(screen.getByTestId(`list-row-${row.assignmentId}`));
      expect(
        await screen.findByRole("dialog", { name: row.developerName }),
      ).toBeVisible();
      for (const suffix of ["", "/assignments", "/statuses", "/change-history"])
        expect(
          request.mock.calls.some(
            ([url]) => url === `/api/common/developers/${developerId}${suffix}`,
          ),
        ).toBe(true);
      expect(
        request.mock.calls.some(([url]) =>
          url.startsWith(`/api/common/developers/${row.assignmentId}`),
        ),
      ).toBe(false);
      expect(screen.queryByTestId("developer-edit")).not.toBeInTheDocument();
      expect(screen.queryByTestId("developer-stop")).not.toBeInTheDocument();
      fireEvent.click(screen.getByRole("button", { name: "닫기" }));
      expect(
        screen.queryByRole("dialog", { name: row.developerName }),
      ).not.toBeInTheDocument();
      expect(screen.getByLabelText("갱신 시점")).toHaveValue("3");
      expect(screen.getByLabelText("인상률")).toHaveValue(8);
      expect(
        screen.queryByTestId("list-head-monthlyCost"),
      ).not.toBeInTheDocument();
      expect(screen.queryByTestId("unsaved-dialog")).not.toBeInTheDocument();
      expect(list).toHaveBeenCalledTimes(3);
      expect(router?.state.location.pathname).toBe("/screens/UI-32");
    },
  );

  it("routed query failure retries without replacing the shell or showing empty success", async () => {
    const { list } = respondWithRateRenegotiations();
    list.mockRejectedValueOnce(new Error("network"));
    mount("/screens/UI-32");
    await screen.findByTestId("rate-error");
    expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("rate-retry"));
    await screen.findByTestId("list-empty");
    expect(list).toHaveBeenCalledTimes(2);
    expectSingleShell();
  });

  it("preserves the existing unavailable-screen guard ahead of the feature client", async () => {
    const { list } = respondWithRateRenegotiations(false);
    mount("/screens/UI-32");
    expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
    expect(
      screen.queryByTestId("rate-renegotiation-page"),
    ).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
  });
});

function respondWithProjects() {
  const menus = response();
  menus.data.push({
    ...menus.data[0],
    menuId: "projects-menu",
    menuNm: "프로젝트",
    scrnId: "UI-01",
    sortOrd: 2,
  });
  menus.meta.screenAccess.push({
    scrnId: "UI-01",
    available: true,
    pcOnlyYn: "N",
  });
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      meta: {},
      data: structuredClone(data),
    }),
  });
  const list = vi.fn(async () => success({ items: [], unassignedCount: 0 }));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/"))
      return {
        ok: true,
        status: 200,
        json: async () => preferenceResponse(url),
      };
    if (url === "/api/common/projects") return list();
    if (
      url.startsWith("/api/common/code-groups/") ||
      url.startsWith("/api/common/clients?") ||
      url === "/api/common/parts" ||
      url === "/api/common/operating-standards"
    )
      return success({ items: [] });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list };
}

function respondWithManpowerComparisons(
  available = true,
  projectId = "P-comparison",
) {
  const menus = response();
  menus.data.push(
    {
      ...menus.data[0],
      menuId: "G003",
      menuNm: "프로젝트·투입",
      scrnId: null,
      sortOrd: 2,
    },
    {
      ...menus.data[0],
      menuId: "M008",
      upperMenuId: "G003",
      menuNm: "인원 계획 대비 투입",
      scrnId: "UI-11",
      menuLvl: 2,
      pcOnlyYn: "Y",
    },
  );
  menus.meta.screenAccess.push({ scrnId: "UI-11", available, pcOnlyYn: "Y" });
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const project = {
    projectId,
    projectName: "비교 프로젝트",
    customerName: "(자체)",
    contractTypeCode: "IN",
    contractTypeName: "내부",
    statusCode: "ING",
    statusName: "진행",
    startDate: "2025-01-01",
    endDate: "2027-12-31",
    contractAmount: 0,
    originalContractAmount: 0,
    budgetAmount: 0,
    budgetManualYn: "N",
    teamExpenseAmount: 0,
    projectExpenseAmount: 0,
    assignedPeopleCount: 0,
    preContractPeopleCount: 0,
    distributionMismatchCount: 0,
    distributionTotalWarning: false,
    canEdit: true,
    canChangeBudget: true,
    canChangeSalesPart: true,
    canSaveManpowerPlan: true,
    canLose: true,
    canStart: true,
    assignments: [],
    issues: [],
    memos: [],
    progress: {
      plannedProgressRate: null,
      actualProgressRate: null,
      steps: [],
    },
  };
  const row = {
    projectId: project.projectId,
    projectName: project.projectName,
    jobCode: "BE",
    jobName: "개발",
    plannedMm: 3,
    actualMm: 1,
    differenceMm: -2,
    differenceStatus: "부족",
  };
  const list = vi.fn(async () =>
    success({
      items: [row],
      totals: { plannedMm: 3, actualMm: 1, differenceMm: -2 },
    }),
  );
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/"))
      return {
        ok: true,
        status: 200,
        json: async () => preferenceResponse(url),
      };
    if (url.startsWith("/api/common/manpower-comparisons?")) return list();
    if (url === "/api/common/projects")
      return success({ items: [project], unassignedCount: 0 });
    if (url === `/api/common/projects/${row.projectId}`)
      return success(project);
    if (url === `/api/common/projects/${row.projectId}/manpower-plan`)
      return success({ items: [row], canSave: true });
    if (url === "/api/common/projects/budget-rule")
      return success({ targetMarginRate: 20, budgetFactor: 0.8 });
    if (
      url.startsWith(`/api/common/projects/${row.projectId}/`) ||
      url.startsWith("/api/common/code-groups/") ||
      url === "/api/common/parts"
    )
      return success({ items: [] });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, success, row };
}

describe("manpower comparison route composition", () => {
  it.each(["/screens/UI-11", "/#UI-11"])(
    "%s uses the registered page and direct API client in one shell",
    async (path) => {
      const { request, list, row } = respondWithManpowerComparisons();
      mount(path);
      expect(
        await screen.findByTestId(`list-row-${row.projectId}-${row.jobCode}`),
      ).toBeVisible();
      expectSingleShell();
      expect(screen.getAllByTestId("manpower-comparison-page")).toHaveLength(1);
      expect(list).toHaveBeenCalledTimes(1);
      expect(request).toHaveBeenCalledWith(
        expect.stringMatching(
          /^\/api\/common\/manpower-comparisons\?year=202[567]$/,
        ),
        { signal: expect.any(AbortSignal) },
      );
      expect(request).toHaveBeenCalledWith("/api/common/projects", {
        signal: expect.any(AbortSignal),
      });
      expect(
        request.mock.calls.some(
          ([url]) =>
            url.startsWith("/api/common/projects?") ||
            url.startsWith("/api/auth/"),
        ),
      ).toBe(false);
      expect(screen.getByTestId("screen-actions")).toBeEmptyDOMElement();
      // 시안 합계 줄: 계획 MM·실제·예정 MM만(차이 합계 없음)
      expect(screen.getByTestId("list-footer")).toHaveTextContent(
        /^합계3\.001\.00$/,
      );
    },
  );

  it("the existing M008 menu opens UI-11 without replacing preferences or duplicating title actions on return", async () => {
    const { list } = respondWithManpowerComparisons();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    fireEvent.click(screen.getByTestId("menu-toggle-g003"));
    fireEvent.click(screen.getByRole("link", { name: "인원 계획 대비 투입" }));
    await screen.findByTestId("manpower-comparison-page");
    await waitFor(() => expect(list).toHaveBeenCalledTimes(1));
    expect(router?.state.location.pathname).toBe("/screens/UI-11");
    fireEvent.change(screen.getByTestId("manpower-comparison-difference"), {
      target: { value: "초과" },
    });
    await act(async () => {
      await router!.navigate("/screens/ADDED");
    });
    expect(screen.getByTestId("screen-panel-ui-11")).not.toBeVisible();
    fireEvent.click(screen.getByTestId("screen-tab-select-ui-11"));
    await waitFor(() =>
      expect(screen.getByTestId("screen-panel-ui-11")).toBeVisible(),
    );
    expect(screen.getByTestId("manpower-comparison-difference")).toHaveValue(
      "초과",
    );
    expect(list).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expect(screen.getByTestId("screen-actions")).toBeEmptyDOMElement();
    expect(
      within(screen.getByTestId("manpower-comparison-page")).getAllByRole(
        "button",
        { name: "조회" },
      ),
    ).toHaveLength(1);
    expectSingleShell();
  });

  it.each(["P-comparison", "P-selected-other"])(
    "FIX-COMPARE-011 opens response projectId %s in project detail on the [인원 계획] tab (editable, mockup openProject)",
    async (projectId) => {
      const { request, row } = respondWithManpowerComparisons(true, projectId);
      mount("/screens/UI-11");
      fireEvent.click(
        await screen.findByTestId(`list-row-${row.projectId}-${row.jobCode}`),
      );
      const detail = within(await screen.findByTestId("project-detail"));
      expect(
        await detail.findByRole("tab", { name: "인원 계획", selected: true }),
      ).toBeInTheDocument();
      expect(await detail.findByTestId("project-job-be")).toBeVisible();
      expect(screen.getByTestId("project-detail")).toHaveTextContent(
        row.projectId,
      );
      expect(request).toHaveBeenCalledWith(
        `/api/common/projects/${row.projectId}`,
        { signal: undefined },
      );
      expect(detail.getByTestId("project-job-mm-be")).toBeInTheDocument();
      // 바꾼 것이 없으면 Ctrl+S도 저장하지 않는다.
      fireEvent.keyDown(window, { key: "s", ctrlKey: true });
      expect(
        request.mock.calls.some(
          ([url, init]) =>
            url.startsWith("/api/common/projects") &&
            ["POST", "PUT", "PATCH", "DELETE"].includes(init?.method ?? "GET"),
        ),
      ).toBe(false);
      expect(router?.state.location.pathname).toBe("/screens/UI-11");
      fireEvent.click(detail.getByRole("button", { name: "닫기" }));
      expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument();
      expect(
        screen.getByTestId(`list-row-${row.projectId}-${row.jobCode}`),
      ).toBeVisible();
      expectSingleShell();
    },
  );

  it("a selected project detail failure stays local and retries the same identity", async () => {
    const { request, row } = respondWithManpowerComparisons();
    const original = request.getMockImplementation()!;
    const path = `/api/common/projects/${row.projectId}`;
    let fail = true;
    request.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url === path && fail) {
        fail = false;
        throw new Error("선택 프로젝트를 불러오지 못했습니다.");
      }
      return original(url, init);
    });
    mount("/screens/UI-11");
    fireEvent.click(
      await screen.findByTestId(
        `manpower-comparison-project-${row.projectId}-${row.jobCode}`,
      ),
    );
    expect(await screen.findByTestId("project-detail-error")).toHaveTextContent(
      "선택 프로젝트를 불러오지 못했습니다.",
    );
    expect(screen.getByTestId("manpower-comparison-page")).toBeVisible();
    expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("project-detail-retry"));
    const detail = within(screen.getByTestId("project-detail"));
    await detail.findByRole("tab", { name: "인원 계획" });
    expect(screen.getByTestId("project-detail")).toHaveTextContent(
      row.projectId,
    );
    expect(request.mock.calls.filter(([url]) => url === path)).toHaveLength(2);
    expect(
      request.mock.calls.filter(([url]) =>
        url.startsWith("/api/common/manpower-comparisons?"),
      ),
    ).toHaveLength(1);
    expect(router?.state.location.pathname).toBe("/screens/UI-11");
    expectSingleShell();
  });

  it("a routed empty comparison retains the controls, table headers and numeric zero totals", async () => {
    const { list, success } = respondWithManpowerComparisons();
    list.mockResolvedValueOnce(
      success({
        items: [],
        totals: { plannedMm: 0, actualMm: 0, differenceMm: 0 },
      }),
    );
    mount("/screens/UI-11");
    expect(await screen.findByTestId("list-empty")).toHaveTextContent(
      "조건에 맞는 결과가 없습니다.",
    );
    expect(screen.getByTestId("manpower-comparison-year")).toBeVisible();
    expect(screen.getByTestId("manpower-comparison-banner")).toBeVisible();
    expect(screen.getByTestId("manpower-comparison-filters")).toBeVisible();
    expect(screen.getByTestId("list-download")).toBeVisible();
    expect(screen.getAllByRole("columnheader")).toHaveLength(5);
    expect(screen.getByTestId("list-footer")).toHaveTextContent(
      /^합계0\.000\.00$/,
    );
    expectSingleShell();
  });

  it("a routed failure retries the last applied query instead of the edited draft", async () => {
    const { request, list } = respondWithManpowerComparisons();
    list.mockRejectedValueOnce(new Error("비교 조회 연결 실패"));
    mount("/screens/UI-11");
    const retry = await screen.findByTestId("list-retry");
    const first = request.mock.calls.find(([url]) =>
      url.startsWith("/api/common/manpower-comparisons?"),
    )![0];
    fireEvent.change(screen.getByTestId("manpower-comparison-difference"), {
      target: { value: "초과" },
    });
    fireEvent.click(retry);
    await screen.findByTestId("list-row-P-comparison-BE");
    expect(list).toHaveBeenCalledTimes(2);
    expect(
      request.mock.calls
        .filter(([url]) => url.startsWith("/api/common/manpower-comparisons?"))
        .map(([url]) => url),
    ).toEqual([first, first]);
    expect(screen.queryByTestId("list-error")).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/screens/UI-11");
    expectSingleShell();
  });

  it("an unavailable comparison stays behind the existing menu guard before querying", async () => {
    const { list } = respondWithManpowerComparisons(false);
    mount("/screens/UI-11");
    expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
    expect(
      screen.queryByTestId("manpower-comparison-page"),
    ).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
    expectSingleShell();
  });

  it("mobile direct entry preserves the PC-only guard before the comparison client", async () => {
    const { list } = respondWithManpowerComparisons();
    vi.stubGlobal(
      "matchMedia",
      vi.fn((query: string) => ({
        matches: query === "(max-width: 767px)",
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    mount("/screens/UI-11");
    expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
    expect(
      screen.queryByTestId("manpower-comparison-page"),
    ).not.toBeInTheDocument();
    expect(list).not.toHaveBeenCalled();
    expectSingleShell();
  });
});

describe("project route composition", () => {
  it.each(["/screens/UI-01", "/#UI-01"])(
    "%s renders the real project page with one shell and its existing client",
    async (path) => {
      const { request } = respondWithProjects();
      mount(path);
      await screen.findByTestId("list-empty");
      expect(screen.getAllByTestId("project-page")).toHaveLength(1);
      expect(
        (await screen.findByTestId("project-register")).closest(
          ".screen-actions",
        ),
      ).not.toBeNull();
      expect(request).toHaveBeenCalledWith("/api/common/projects", {
        signal: expect.any(AbortSignal),
      });
      expect(
        request.mock.calls.some(
          ([url]) =>
            url.startsWith("/api/common/projects?") ||
            url.startsWith("/api/auth/"),
        ),
      ).toBe(false);
      expect(router?.state.location.pathname).toBe("/screens/UI-01");
      expectSingleShell();
    },
  );

  it("the registered title action opens the shared project form without adding a route or sending a command", async () => {
    const { request } = respondWithProjects();
    mount("/screens/UI-01");
    await screen.findByTestId("list-empty");
    fireEvent.click(await screen.findByTestId("project-register"));
    expect(await screen.findByTestId("project-form")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-01");
    fireEvent.click(screen.getByTestId("project-cancel"));
    expect(screen.queryByTestId("project-form")).not.toBeInTheDocument();
    expect(
      request.mock.calls.filter(
        ([url, init]) =>
          url.startsWith("/api/common/projects") &&
          ["POST", "PUT"].includes(init?.method ?? ""),
      ),
    ).toHaveLength(0);
    expectSingleShell();
  });

  it("project list failure retries the same API inside the original route", async () => {
    const { list } = respondWithProjects();
    list.mockRejectedValueOnce(
      new Error("프로젝트 목록을 불러오지 못했습니다."),
    );
    mount("/screens/UI-01");
    fireEvent.click(await screen.findByTestId("list-retry"));
    expect(await screen.findByTestId("list-empty")).toBeVisible();
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-01");
    expectSingleShell();
  });
});

function respondWithAssignments() {
  const menus = response();
  menus.data.push(
    {
      ...menus.data[0],
      menuId: "G003",
      menuNm: "프로젝트·투입",
      scrnId: null,
      sortOrd: 2,
    },
    {
      ...menus.data[0],
      menuId: "M006",
      upperMenuId: "G003",
      menuNm: "투입 배정",
      scrnId: "UI-12",
      menuLvl: 2,
      sortOrd: 1,
    },
    {
      ...menus.data[0],
      menuId: "recommendations-menu",
      menuNm: "AI 추천",
      scrnId: "UI-30",
      sortOrd: 3,
    },
  );
  menus.meta.screenAccess.push(
    { scrnId: "UI-12", available: true, pcOnlyYn: "N" },
    { scrnId: "UI-30", available: true, pcOnlyYn: "N" },
  );
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const list = vi.fn(async () => success({ items: [] }));
  const upload = {
    uploadId: "assignment-upload",
    modifiedAt: null,
    statusName: "검사완료",
    successCount: 0,
    warningCount: 0,
    errorCount: 1,
    issues: [
      {
        row: 4,
        column: "프로젝트ID",
        inputValue: "",
        reason: "프로젝트를 선택해 주세요.",
        severity: "오류",
      },
    ],
  };
  const request = vi.fn(
    async (
      url: string,
      init?: RequestInit,
    ): Promise<Pick<Response, "ok" | "status" | "json">> => {
      if (url === "/api/common/menus")
        return { ok: true, status: 200, json: async () => menus };
      if (url.startsWith("/api/common/user-preferences/"))
        return {
          ok: true,
          status: 200,
          json: async () => preferenceResponse(url),
        };
      if (url.startsWith("/api/common/assignments?")) return list();
      if (url === "/api/common/assignments/candidates")
        return success({ items: [] });
      if (url === "/api/common/projects")
        return success({ items: [], unassignedCount: 0 });
      if (
        url.startsWith("/api/common/code-groups/") ||
        url.startsWith("/api/common/developers?")
      )
        return success({ items: [] });
      if (
        (url === "/api/common/uploads" && init?.method === "POST") ||
        url === "/api/common/uploads/assignment-upload"
      )
        return success(upload);
      throw new Error(`예상하지 않은 요청: ${url}`);
    },
  );
  vi.stubGlobal("fetch", request);
  return { request, list };
}

describe("assignment route composition", () => {
  it.each(["/screens/UI-12", "/#UI-12"])(
    "%s uses the real assignment client and a single title action host",
    async (path) => {
      const { request } = respondWithAssignments();
      mount(path);
      await screen.findByTestId("assignment-empty");
      expect(screen.getAllByTestId("assignment-page")).toHaveLength(1);
      expect(screen.getAllByTestId("assignment-create")).toHaveLength(1);
      expect(
        screen.getByTestId("assignment-create").closest(".screen-actions"),
      ).not.toBeNull();
      expect(
        screen.getByTestId("upload-open").closest(".screen-actions"),
      ).not.toBeNull();
      expect(screen.getAllByTestId("upload-actions")).toHaveLength(1);
      expect(request).toHaveBeenCalledWith(
        expect.stringMatching(/^\/api\/common\/assignments\?year=\d{4}$/),
        { signal: expect.any(AbortSignal) },
      );
      expect(request).toHaveBeenCalledWith(
        "/api/common/assignments/candidates",
        undefined,
      );
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expect(router?.state.location.pathname).toBe("/screens/UI-12");
      expectSingleShell();
    },
  );

  it("title registration opens the existing assignment form without another route or command", async () => {
    const { request } = respondWithAssignments();
    mount("/screens/UI-12");
    await screen.findByTestId("assignment-empty");
    fireEvent.click(screen.getByTestId("assignment-create"));
    expect(await screen.findByTestId("assignment-form")).toBeVisible();
    expect(router?.state.location.pathname).toBe("/screens/UI-12");
    expect(
      request.mock.calls.some(
        ([url, init]) =>
          url.startsWith("/api/common/assignments") && !!init?.method,
      ),
    ).toBe(false);
    expectSingleShell();
  });

  it("title upload sends B5 through the shared multipart client and keeps errors unapplied", async () => {
    const { request } = respondWithAssignments();
    mount("/screens/UI-12");
    await screen.findByTestId("assignment-empty");
    await waitFor(() =>
      expect(screen.getByTestId("upload-open")).toBeEnabled(),
    );
    fireEvent.click(screen.getByTestId("upload-open"));
    fireEvent.change(await screen.findByTestId("upload-file"), {
      target: { files: [new File(["workbook"], "assignments.xlsx")] },
    });
    fireEvent.click(screen.getByTestId("upload-check"));
    await screen.findByRole("dialog", { name: "엑셀 업로드 결과 · 투입 목록" });
    await waitFor(() =>
      expect(screen.queryByText("검사 결과 조회 중…")).not.toBeInTheDocument(),
    );
    expect(screen.getByTestId("upload-apply")).toBeDisabled();
    const sent = request.mock.calls.find(
      ([url, init]) => url === "/api/common/uploads" && init?.method === "POST",
    )!;
    const form = sent[1]?.body as FormData;
    expect(form.get("formTypeCode")).toBe("B5");
    expect(form.get("file")).toBeInstanceOf(File);
    fireEvent.click(screen.getByTestId("upload-result-close"));
    expect(request.mock.calls.some(([url]) => url.endsWith("/apply"))).toBe(
      false,
    );
    expect(router?.state.location.pathname).toBe("/screens/UI-12");
    expectSingleShell();
  });

  it.each([200, 409])(
    "B5 apply status %s uses the receipt version and refreshes assignments only on success",
    async (status) => {
      const { request, list } = respondWithAssignments();
      const original = request.getMockImplementation()!;
      const receipt = {
        uploadId: "assignment-upload",
        modifiedAt: "2026-11-17T00:00:00.123456Z",
        statusName: "검사완료",
        successCount: 0,
        warningCount: 1,
        errorCount: 0,
        issues: [
          {
            row: 2,
            column: "프로젝트ID",
            inputValue: "assignment-project",
            reason: "영업 파트가 다른 배정 1건",
            severity: "경고",
          },
        ],
      };
      const success = (data: unknown) => ({
        ok: true,
        status: 200,
        json: async () => ({ success: true, meta: {}, data }),
      });
      request.mockImplementation(async (url: string, init?: RequestInit) => {
        if (
          (url === "/api/common/uploads" && init?.method === "POST") ||
          url === `/api/common/uploads/${receipt.uploadId}`
        )
          return success(receipt);
        if (url === `/api/common/uploads/${receipt.uploadId}/apply`)
          return status === 200
            ? success({ ...receipt, statusName: "반영완료" })
            : {
                ok: false,
                status,
                json: async () => ({
                  success: false,
                  meta: {},
                  message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
                  errors: [
                    { field: "modifiedAt", message: "다시 불러와 주세요." },
                  ],
                }),
              };
        return original(url, init);
      });
      mount("/screens/UI-12");
      await screen.findByTestId("assignment-empty");
      const initialReads = list.mock.calls.length;
      await waitFor(() =>
        expect(screen.getByTestId("upload-open")).toBeEnabled(),
      );
      fireEvent.click(screen.getByTestId("upload-open"));
      fireEvent.change(await screen.findByTestId("upload-file"), {
        target: { files: [new File(["workbook"], "assignments.xlsx")] },
      });
      fireEvent.click(screen.getByTestId("upload-check"));
      await screen.findByRole("dialog", {
        name: "엑셀 업로드 결과 · 투입 목록",
      });
      await waitFor(() =>
        expect(screen.getByTestId("upload-apply")).toBeEnabled(),
      );
      expect(screen.getByTestId("upload-issue-0-0")).toHaveTextContent(
        receipt.issues[0].reason,
      );
      expect(list).toHaveBeenCalledTimes(initialReads);
      fireEvent.click(screen.getByTestId("upload-apply"));
      if (status === 200) {
        await screen.findByTestId("upload-applied");
        await waitFor(() =>
          expect(list).toHaveBeenCalledTimes(initialReads + 1),
        );
        expect(screen.getByTestId("upload-apply")).toBeDisabled();
        expect(screen.getByTestId("upload-issue-0-0")).toHaveTextContent(
          receipt.issues[0].reason,
        );
      } else {
        expect(
          await screen.findByTestId("upload-result-error"),
        ).toHaveTextContent("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
        expect(screen.queryByTestId("upload-applied")).not.toBeInTheDocument();
        expect(list).toHaveBeenCalledTimes(initialReads);
      }
      const applies = request.mock.calls.filter(([url]) =>
        url.endsWith("/apply"),
      );
      expect(applies).toHaveLength(1);
      expect(applies[0]).toEqual([
        `/api/common/uploads/${receipt.uploadId}/apply`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ modifiedAt: receipt.modifiedAt }),
        },
      ]);
      expect(router?.state.location.pathname).toBe("/screens/UI-12");
      expectSingleShell();
    },
  );

  it("last-year routing retains the list but removes mutating title actions", async () => {
    respondWithAssignments();
    mount("/screens/UI-12");
    await screen.findByTestId("assignment-empty");
    fireEvent.click(screen.getByTestId("assignment-year-prev"));
    expect(
      await screen.findByTestId("assignment-lock-banner"),
    ).toHaveTextContent(/^\d{4}년은 지난 해라 읽기만 할 수 있습니다\.$/);
    expect(screen.queryByTestId("assignment-create")).not.toBeInTheDocument();
    expect(screen.queryByTestId("upload-open")).not.toBeInTheDocument();
    expect(screen.queryByTestId("upload-template")).not.toBeInTheDocument();
    // 시안: 엑셀 다운로드는 지난 해에도 목록 도구 줄에 남는다(제목줄이 아님).
    expect(screen.getByTestId("list-download")).toHaveTextContent(
      "엑셀 다운로드",
    );
    expect(
      screen.getByTestId("list-download").closest(".screen-actions"),
    ).toBeNull();
    expectSingleShell();
  });

  it("list failure retries the real client in the same shell", async () => {
    const { list } = respondWithAssignments();
    list.mockRejectedValueOnce(new Error("배정 목록을 불러오지 못했습니다."));
    mount("/screens/UI-12");
    const retry = await screen.findByTestId("list-retry");
    expect(screen.getByTestId("list-error")).toHaveTextContent(
      "배정 목록을 불러오지 못했습니다.",
    );
    // 시안: 목록을 못 불러와도 제목줄 [배정]은 막지 않는다.
    expect(screen.getByTestId("assignment-create")).toBeEnabled();
    fireEvent.click(retry);
    expect(await screen.findByTestId("assignment-empty")).toBeVisible();
    expect(list).toHaveBeenCalledTimes(2);
    expect(router?.state.location.pathname).toBe("/screens/UI-12");
    expectSingleShell();
  });

  it("UI-30 remains navigation-only and preserves the preferences scope on return", async () => {
    respondWithAssignments();
    mount("/screens/UI-12");
    await screen.findByTestId("assignment-empty");
    const preferences = screen.getByTestId("user-preferences");
    await act(async () => {
      await router!.navigate("/screens/UI-30");
    });
    expect(
      await screen.findByRole("region", { name: "UI-30 본문" }),
    ).toHaveTextContent("준비 중");
    expect(screen.queryByTestId("assignment-create")).not.toBeInTheDocument();
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    await act(async () => {
      await router!.navigate("/screens/UI-12");
    });
    expect(await screen.findByTestId("assignment-empty")).toBeVisible();
    expect(screen.getAllByTestId("assignment-create")).toHaveLength(1);
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expectSingleShell();
  });
});

function respondWithProjectCostAnalyses() {
  const menus = response();
  menus.data.push(
    {
      ...menus.data[0],
      menuId: "G006",
      menuNm: "원가·수익",
      scrnId: null,
      sortOrd: 2,
    },
    {
      ...menus.data[0],
      menuId: "M015",
      upperMenuId: "G006",
      menuNm: "프로젝트 원가 분석",
      scrnId: "UI-04",
      menuLvl: 2,
    },
    {
      ...menus.data[0],
      menuId: "reference-menu",
      menuNm: "단가 검토",
      scrnId: "UI-32",
      sortOrd: 3,
    },
  );
  menus.meta.screenAccess.push(
    { scrnId: "UI-04", available: true, pcOnlyYn: "N" },
    { scrnId: "UI-32", available: true, pcOnlyYn: "N" },
  );
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const list = vi.fn(async () =>
    success({
      items: [],
      projectOptions: [],
      totalCount: 0,
      summary: {
        cumulativeRevenue: 0,
        cumulativeProfit: 0,
        lowMarginCount: 0,
        budgetExceededCount: 0,
        researchCost: 0,
      },
    }),
  );
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/project-cost-analyses?")) return list();
    if (url.startsWith("/api/common/rate-renegotiations?"))
      return success(rateRenegotiationResult());
    if (url === "/api/common/projects") return success({ items: [] });
    if (url.startsWith("/api/common/code-groups/"))
      return success({ items: [] });
    if (url.startsWith("/api/common/user-preferences/")) {
      const body = preferenceResponse(url);
      return {
        ok: true,
        status: 200,
        json: async () => ({
          ...body,
          meta: url.endsWith("/PERIOD/UI-04")
            ? {
                periodBasis: {
                  cashBaseYm: "202501",
                  latestYm: "202602",
                  closedMonths: [],
                },
              }
            : {},
        }),
      };
    }
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list };
}

describe("project cost analysis route composition", () => {
  it.each(["/screens/UI-04", "/#UI-04"])(
    "%s renders the registered page using its relative public client in one shell",
    async (path) => {
      const { request } = respondWithProjectCostAnalyses();
      mount(path);
      expect(
        await screen.findByText("조건에 맞는 결과가 없습니다."),
      ).toBeVisible();
      expect(screen.getAllByTestId("project-cost-analysis-page")).toHaveLength(
        1,
      );
      expect(screen.getByTestId("project-cost-summary")).toBeVisible();
      expect(request).toHaveBeenCalledWith(
        expect.stringMatching(
          // 「보기: 전체」면 alertOnly를 보내지 않는다(시안 보기 선택 상자)
          /^\/api\/common\/project-cost-analyses\?year=\d{4}&baseMonth=\d{1,2}$/,
        ),
        { signal: expect.any(AbortSignal) },
      );
      expect(
        request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
      ).toBe(false);
      expect(router?.state.location.pathname).toBe("/screens/UI-04");
      expectSingleShell();
    },
  );

  it("M015 under G006 opens UI-04 without replacing the preferences provider", async () => {
    respondWithProjectCostAnalyses();
    mount("/screens/ADDED");
    await screen.findByRole("region", { name: "ADDED 본문" });
    const preferences = screen.getByTestId("user-preferences");
    fireEvent.click(screen.getByTestId("menu-toggle-g006"));
    fireEvent.click(screen.getByRole("link", { name: "프로젝트 원가 분석" }));
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(router?.state.location.pathname).toBe("/screens/UI-04");
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    expect(screen.getByTestId("screen-tab-ui-04")).toBeVisible();
    expectSingleShell();
  });

  it("routed list failure retries the same client without a login redirect", async () => {
    const { request, list } = respondWithProjectCostAnalyses();
    list.mockRejectedValueOnce(new Error("분석 조회 연결 실패"));
    mount("/screens/UI-04");
    await screen.findByText("분석 조회 연결 실패");
    expect(
      screen.queryByText("조건에 맞는 결과가 없습니다."),
    ).not.toBeInTheDocument();
    const first = request.mock.calls.find(([url]) =>
      url.startsWith("/api/common/project-cost-analyses?"),
    )![0];
    fireEvent.click(
      within(screen.getByTestId("project-cost-analysis-page")).getByRole(
        "button",
        { name: "다시 시도" },
      ),
    );
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    expect(
      request.mock.calls
        .filter(([url]) => url.startsWith("/api/common/project-cost-analyses?"))
        .map(([url]) => url),
    ).toEqual([first, first]);
    expect(router?.state.location.pathname).toBe("/screens/UI-04");
    expectSingleShell();
  });

  it("UI-32 opens its registered workspace and returning to UI-04 preserves the shared scope", async () => {
    respondWithProjectCostAnalyses();
    mount("/screens/UI-04");
    await screen.findByText("조건에 맞는 결과가 없습니다.");
    const preferences = screen.getByTestId("user-preferences");
    await act(async () => {
      await router!.navigate("/screens/UI-32");
    });
    expect(await screen.findByTestId("rate-renegotiation-page")).toBeVisible();
    expect(
      await within(screen.getByTestId("rate-renegotiation-page")).findByTestId(
        "list-empty",
      ),
    ).toBeVisible();
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
    await act(async () => {
      await router!.navigate("/screens/UI-04");
    });
    await waitFor(() =>
      expect(screen.getByTestId("project-cost-analysis-page")).toBeVisible(),
    );
    expect(screen.getByTestId("user-preferences")).toBe(preferences);
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
      // 조회 응답에 수정 시각이 없으면 null을 그대로 보낸다(FR-0106 동시 수정 확인).
      modifiedAt: null,
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
    await waitFor(() =>
      expect(screen.getByTestId("menu-registration-open")).toBeEnabled(),
    );
    fireEvent.click(screen.getByTestId("menu-registration-open"));
    await screen.findByTestId(
      "menu-registration-no-screens",
      {},
      { timeout: 3000 },
    );
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
      // FR-0106: 읽은 시각을 함께 보낸다(목 응답에 시각이 없어 null)
      modifiedAt: null,
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
    await waitFor(() =>
      expect(screen.getByTestId("role-copy-open")).toBeEnabled(),
    );
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
          request.mock.calls.filter(
            ([url]) =>
              url !== "/api/common/menus" &&
              url !== "/api/common/bills/unpaid-count",
          ),
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
        ([url]) =>
          url !== "/api/common/menus" &&
          url !== "/api/common/bills/unpaid-count",
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
      ([url]) =>
        url !== "/api/common/menus" && url !== "/api/common/bills/unpaid-count",
    ).length;
    await act(async () => {
      await router!.navigate("/unregistered/path");
    });
    expect(await screen.findByText("없는 화면입니다")).toBeVisible();
    expect(screen.getByTestId("user-preferences")).toBe(provider);
    expect(
      request.mock.calls.filter(
        ([url]) =>
          url !== "/api/common/menus" &&
          url !== "/api/common/bills/unpaid-count",
      ),
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

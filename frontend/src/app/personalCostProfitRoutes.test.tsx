import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider } from "react-router-dom";
import { createAppRouter } from "./router";
import type { ShellMenuResponse } from "./NavigationRoot";
import type { PersonalCostProfitResult } from "../pages/personalcostprofits/personalCostProfitTypes";

let router: ReturnType<typeof createAppRouter> | undefined;
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data: structuredClone(data) }),
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
});
afterEach(() => {
  cleanup();
  router?.dispose();
  router = undefined;
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function respond(developerId = "DEV-selected", available = true) {
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      {
        menuId: "G006",
        upperMenuId: null,
        menuNm: "원가·수익",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 6,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "M014",
        upperMenuId: "G006",
        menuNm: "개인 원가·수익표",
        scrnId: "UI-03",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: { screenAccess: [{ scrnId: "UI-03", available, pcOnlyYn: "N" }] },
  };
  const result: PersonalCostProfitResult = {
    items: [
      {
        developerId,
        developerName: "선택 개발자",
        affiliationName: "개발 소속",
        positionName: "사원",
        employeeTypeName: "정규직",
        joinDate: null,
        operatingRate: 100,
        mm: 1,
        unitPrice: 200,
        allowance: 10,
        companyInsurance: 20,
        retirementReserve: 30,
        teamExpenses: 5,
        projectExpenses: 5,
        totalCost: 170,
        operatingProfit: 30,
        profitRate: 15,
        mmOverOne: false,
        allocations: [],
      },
    ],
    summary: {
      totalPeople: 1,
      averageOperatingPeople: 1,
      averageEarningPeople: 1,
      averageUnitPrice: 200,
      averageCost: 170,
      averageOperatingProfit: 30,
      lossPeople: 0,
      operatingRate: 100,
      earningRate: 100,
    },
    totals: {
      mm: 1,
      unitPrice: 200,
      allowance: 10,
      companyInsurance: 20,
      retirementReserve: 30,
      teamExpenses: 5,
      projectExpenses: 5,
      totalCost: 170,
      operatingProfit: 30,
    },
    contributions: [],
  };
  const list = vi.fn(async () => ok(result));
  const detail = vi.fn(async () =>
    ok({
      developerId,
      name: "선택 개발자",
      managementNumber: "선택 관리번호",
      organizationName: "개발 소속",
      employeeTypeName: "정규직",
      useYn: "Y",
      workStatusName: "가동",
      skillNames: [],
    }),
  );
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ...ok(null), json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/")) {
      const input = init?.body ? JSON.parse(String(init.body)) : null;
      return ok(
        {
          prefTypeCd: url.split("/").at(-2),
          prefKey: url.split("/").at(-1),
          found: !!input,
          prefVal: input?.prefVal ?? null,
          modDttm: null,
        },
        {
          periodBasis: {
            latestYm: "202602",
            cashBaseYm: "202501",
            closedMonths: ["202601"],
          },
        },
      );
    }
    if (url.startsWith("/api/common/personal-cost-profits?")) return list();
    if (url === `/api/common/developers/${developerId}`) return detail();
    if (
      ["assignments", "statuses", "change-history"].some(
        (tab) => url === `/api/common/developers/${developerId}/${tab}`,
      )
    )
      return ok({
        items: [],
        canManageStatuses: true,
        canCancelPastRetirement: true,
      });
    if (
      url.startsWith("/api/common/code-groups/") ||
      url.startsWith("/api/common/clients?") ||
      url === "/api/common/parts"
    )
      return ok({ items: [] });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, detail, result, developerId };
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
function expectSingleShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("complementary")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
}

it.each(["/screens/UI-03", "/#UI-03"])(
  "%s dispatches the existing registered page and relative API client without login",
  async (path) => {
    const { request, developerId } = respond();
    mount(path);
    await screen.findByTestId(`list-row-${developerId}`);
    expect(screen.getAllByTestId("personal-cost-profit-page")).toHaveLength(1);
    expect(screen.getByTestId("screen-actions")).toBeEmptyDOMElement();
    const url = request.mock.calls.find(([url]) =>
      url.startsWith("/api/common/personal-cost-profits?"),
    )![0];
    const params = new URLSearchParams(url.split("?")[1]);
    expect(params.get("year")).toBe("2026");
    expect(params.get("baseMonth")).toBe("2");
    expect(params.get("displayMode")).toBe("연누계");
    expect(params.get("view")).toBe("전체");
    expect(params.get("includeTurnkey")).toBe("true");
    expect(params.has("name")).toBe(false);
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(router?.state.location.pathname).toBe("/screens/UI-03");
    expectSingleShell();
  },
);

it("M014 under G006 opens UI-03 without replacing the preference provider", async () => {
  const { developerId } = respond();
  mount("/");
  const preferences = await screen.findByTestId("user-preferences");
  await screen.findByTestId(`list-row-${developerId}`);
  const group = screen.getByTestId("menu-toggle-g006");
  await waitFor(() => expect(group).toHaveAttribute("aria-expanded", "true"));
  fireEvent.click(group);
  expect(group).toHaveAttribute("aria-expanded", "false");
  fireEvent.click(group);
  expect(group).toHaveAttribute("aria-expanded", "true");
  fireEvent.click(screen.getByRole("link", { name: "개인 원가·수익표" }));
  await screen.findByTestId(`list-row-${developerId}`);
  expect(router?.state.location.pathname).toBe("/screens/UI-03");
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  expectSingleShell();
});

it.each(["DEV-selected", "DEV-another"])(
  "FX-PERSON-DETAIL opens the clicked developerId %s read-only and closing preserves the applied list and draft",
  async (developerId) => {
    const { request, list } = respond(developerId);
    mount("/screens/UI-03");
    await screen.findByTestId(`list-row-${developerId}`);
    fireEvent.change(screen.getByLabelText("이름"), {
      target: { value: "선택" },
    });
    fireEvent.click(screen.getByTestId("list-search"));
    await waitFor(() => {
      const url = request.mock.calls
        .filter(([url]) => url.startsWith("/api/common/personal-cost-profits?"))
        .at(-1)![0];
      expect(new URLSearchParams(url.split("?")[1]).get("name")).toBe("선택");
    });
    const row = await screen.findByTestId(`list-row-${developerId}`);
    const reads = list.mock.calls.length;
    fireEvent.change(screen.getByLabelText("이름"), {
      target: { value: "미조회 조건" },
    });
    fireEvent.click(row);
    const panel = within(await screen.findByTestId("developer-detail"));
    expect(
      await panel.findByText("선택 관리번호 · 개발 소속 · 정규직"),
    ).toBeVisible();
    for (const suffix of ["", "/assignments", "/statuses", "/change-history"])
      expect(request).toHaveBeenCalledWith(
        `/api/common/developers/${developerId}${suffix}`,
        undefined,
      );
    expect(
      panel.queryByRole("button", { name: "수정" }),
    ).not.toBeInTheDocument();
    expect(
      panel.queryByRole("button", { name: "사용 중지" }),
    ).not.toBeInTheDocument();
    fireEvent.click(panel.getByRole("tab", { name: /근무 상태/ }));
    expect(
      panel.queryByRole("button", { name: /등록|취소|변경/ }),
    ).not.toBeInTheDocument();
    fireEvent.click(panel.getByRole("button", { name: "닫기" }));
    expect(screen.queryByTestId("developer-detail")).not.toBeInTheDocument();
    expect(screen.getByLabelText("이름")).toHaveValue("미조회 조건");
    expect(screen.getByTestId(`list-row-${developerId}`)).toBe(row);
    expect(list).toHaveBeenCalledTimes(reads);
    expect(
      request.mock.calls
        .filter(([url]) => url.startsWith("/api/common/developers/"))
        .every(([, init]) => !init?.method || init.method === "GET"),
    ).toBe(true);
    expect(router?.state.location.pathname).toBe("/screens/UI-03");
    expectSingleShell();
  },
);

it("detail failure retries the selected identity without replacing or reloading the personal list", async () => {
  const { detail, list, developerId } = respond();
  detail.mockRejectedValueOnce(new Error("개발자 조회 연결 실패"));
  mount("/screens/UI-03");
  fireEvent.click(await screen.findByTestId(`list-row-${developerId}`));
  const panel = within(await screen.findByTestId("developer-detail"));
  await panel.findByText("개발자 조회 연결 실패");
  fireEvent.click(panel.getByRole("button", { name: "다시 시도" }));
  expect(
    await panel.findByText("선택 관리번호 · 개발 소속 · 정규직"),
  ).toBeVisible();
  expect(detail).toHaveBeenCalledTimes(2);
  expect(list).toHaveBeenCalledTimes(1);
  expectSingleShell();
});

it("an unavailable screen retains the existing navigation guard before any feature request", async () => {
  const { list, detail } = respond("DEV-selected", false);
  mount("/screens/UI-03");
  expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
  expect(
    screen.queryByTestId("personal-cost-profit-page"),
  ).not.toBeInTheDocument();
  expect(list).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
  expectSingleShell();
});

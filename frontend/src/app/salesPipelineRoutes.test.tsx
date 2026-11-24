import { afterEach, beforeEach, expect, it, vi } from "vitest";
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

function respond(available = true) {
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      {
        menuId: "G004",
        upperMenuId: null,
        menuNm: "인력 계획",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "M011",
        upperMenuId: "G004",
        menuNm: "영업 파이프라인",
        scrnId: "UI-29",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "M030",
        upperMenuId: "G004",
        menuNm: "인력 추천",
        scrnId: "UI-30",
        menuLvl: 2,
        sortOrd: 2,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [
        { scrnId: "UI-29", available, pcOnlyYn: "N" },
        { scrnId: "UI-30", available: true, pcOnlyYn: "N" },
      ],
    },
  };
  const ok = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const pipeline = {
    summary: {
      ongoingCount: 0,
      staleCount: 0,
      expectedAmount: 0,
      weightedAmount: 0,
      weightedManpower: 0,
    },
    stages: [],
  };
  const list = vi.fn(async () => ok(pipeline));
  const request = vi.fn(async (url: string, _init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (url.startsWith("/api/common/user-preferences/"))
      return ok({
        prefTypeCd: url.split("/").at(-2),
        prefKey: url.split("/").at(-1),
        found: false,
        prefVal: null,
        modDttm: null,
      });
    if (url === "/api/common/sales-pipeline") return list();
    if (url === "/api/common/sales-pipeline/forecast")
      return ok({ months: [], hiringReviews: [] });
    if (url.startsWith("/api/common/sales-pipeline/closed?"))
      return ok({ items: [] });
    if (
      url.startsWith("/api/common/code-groups/") ||
      url.startsWith("/api/common/clients?") ||
      url.startsWith("/api/common/developers?") ||
      url === "/api/common/parts"
    )
      return ok({ items: [] });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { list, request, ok, pipeline };
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
function expectShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("complementary")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.getAllByTestId("user-preferences")).toHaveLength(1);
}

it.each(["/screens/UI-29", "/#UI-29"])(
  "%s opens the canonical page using its public feature client",
  async (path) => {
    const { request } = respond();
    mount(path);
    expect(
      await screen.findByText("조건에 맞는 결과가 없습니다."),
    ).toBeVisible();
    const page = screen.getByTestId("sales-pipeline-page");
    expect(screen.getAllByTestId("sales-pipeline-page")).toHaveLength(1);
    expect(page).toHaveAttribute("data-screen-id", "UI-29");
    await waitFor(() =>
      expect(within(page).getByTestId("sales-pipeline-create")).toBeEnabled(),
    );
    expect(router?.state.location.pathname).toBe("/screens/UI-29");
    expect(router?.state.matches.at(-1)?.route.path).toBe("/screens/:scrnId");
    expect(request).toHaveBeenCalledWith(
      "/api/common/sales-pipeline",
      undefined,
    );
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expectShell();
  },
);

it("M011 under G004 opens UI-29 without replacing preferences and retains its selected tab", async () => {
  respond();
  mount("/screens/UI-30");
  expect(
    await screen.findByRole("region", { name: "UI-30 본문" }),
  ).toHaveTextContent("준비 중");
  const preferences = screen.getByTestId("user-preferences");
  const group = screen.getByTestId("menu-toggle-g004");
  if (group.getAttribute("aria-expanded") !== "true") fireEvent.click(group);
  fireEvent.click(screen.getByRole("link", { name: "영업 파이프라인" }));
  await screen.findByTestId("sales-ongoing-panel");
  fireEvent.click(screen.getByTestId("sales-tab-forecast"));
  await screen.findByTestId("sales-forecast-panel");
  await act(async () => {
    await router!.navigate("/screens/UI-30");
  });
  await act(async () => {
    await router!.navigate("/screens/UI-29");
  });
  expect(screen.getByTestId("sales-tab-forecast")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(screen.getByTestId("sales-forecast-panel")).toBeVisible();
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  expectShell();
});

it("a pending routed pipeline read shows loading rather than an empty success", async () => {
  const { list, ok, pipeline } = respond();
  let complete!: (value: ReturnType<typeof ok>) => void;
  list.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  mount("/screens/UI-29");
  expect(await screen.findByText("목록을 불러오는 중입니다.")).toBeVisible();
  expect(screen.queryByTestId("sales-ongoing-panel")).not.toBeInTheDocument();
  await waitFor(() => expect(list).toHaveBeenCalledTimes(1));
  await act(async () => {
    complete(ok(pipeline));
  });
  expect(await screen.findByTestId("sales-ongoing-panel")).toBeVisible();
  expectShell();
});

it("a failed routed read retries the same client in the same shell", async () => {
  const { list } = respond();
  list.mockRejectedValueOnce(new Error("조회 실패"));
  mount("/screens/UI-29");
  fireEvent.click(await screen.findByTestId("sales-retry"));
  expect(await screen.findByText("조건에 맞는 결과가 없습니다.")).toBeVisible();
  expect(list).toHaveBeenCalledTimes(2);
  expect(router?.state.location.pathname).toBe("/screens/UI-29");
  expectShell();
});

it("preserves the existing unavailable-screen boundary without reading feature data", async () => {
  const { list, request } = respond(false);
  mount("/screens/UI-29");
  expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
  expect(screen.queryByTestId("sales-pipeline-page")).not.toBeInTheDocument();
  expect(list).not.toHaveBeenCalled();
  expect(
    request.mock.calls.some(([url]) =>
      url.startsWith("/api/common/sales-pipeline"),
    ),
  ).toBe(false);
  expectShell();
});

it("the registered forecast and closed tabs use feature-local API exports", async () => {
  const { request } = respond();
  mount("/screens/UI-29");
  await screen.findByTestId("sales-ongoing-panel");
  fireEvent.click(screen.getByTestId("sales-tab-forecast"));
  expect(await screen.findByTestId("sales-forecast-panel")).toBeVisible();
  expect(request).toHaveBeenCalledWith(
    "/api/common/sales-pipeline/forecast",
    undefined,
  );
  fireEvent.click(screen.getByTestId("sales-tab-closed"));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/sales-pipeline/closed?includeStopped=false",
      undefined,
    ),
  );
  expect(router?.state.location.pathname).toBe("/screens/UI-29");
  expectShell();
});

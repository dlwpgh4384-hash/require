import { afterEach, beforeEach, expect, it, vi } from "vitest";
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
import type {
  AiInput,
  AiOptions,
} from "../pages/airecommendations/aiRecommendations";

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

function respond(available = true, noTargets = false) {
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      {
        menuId: "G009",
        upperMenuId: null,
        menuNm: "AI",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 9,
        pcOnlyYn: "N",
        iconNm: "sparkle",
      },
      {
        menuId: "M025",
        upperMenuId: "G009",
        menuNm: "AI 분석 도우미",
        scrnId: "UI-26",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "M026",
        upperMenuId: "G009",
        menuNm: "AI 인력 추천",
        scrnId: "UI-30",
        menuLvl: 2,
        sortOrd: 2,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [
        { scrnId: "UI-26", available: true, pcOnlyYn: "N" },
        { scrnId: "UI-30", available, pcOnlyYn: "N" },
      ],
    },
  };
  const options: AiOptions = {
    targets: noTargets
      ? []
      : [
          {
            targetType: "opportunity",
            targetId: "selected-opportunity",
            targetName: "진행 기회",
            startMonth: "2026-12",
            endMonth: null,
            needs: [],
          },
        ],
    jobs: [{ code: "BE", name: "백엔드" }],
    grades: [
      { code: "SR", name: "고급" },
      { code: "MD", name: "중급" },
    ],
    exclusionReasons: [],
  };
  const ok = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const reads = vi.fn(async () => ok(options));
  const runs = vi.fn(async (input: AiInput) => {
    const { requestId, ...condition } = input;
    return ok({ recommendationId: requestId, condition, items: [] });
  });
  const request = vi.fn(async (url: string, init?: RequestInit) => {
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
    if (url.startsWith("/api/common/ai-recommendation-options")) return reads();
    if (url === "/api/common/ai-recommendations" && init?.method === "POST")
      return runs(JSON.parse(String(init.body)) as AiInput);
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, reads, runs };
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

it.each(["/screens/UI-30", "/#UI-30"])(
  "%s resolves the real recommendation page through the shared route and local client",
  async (path) => {
    const { request, runs } = respond();
    mount(path);
    expect(
      await screen.findByText("조건에 맞는 사람이 없습니다."),
    ).toBeVisible();
    expect(screen.getAllByTestId("ai-recommendation-page")).toHaveLength(1);
    expect(screen.getByTestId("ai-recommendation-page")).toHaveAttribute(
      "data-screen-id",
      "UI-30",
    );
    expect(screen.getByText("필요 인력을 먼저 등록해 주세요")).toBeVisible();
    expect(runs).toHaveBeenCalledTimes(1);
    expect(runs).toHaveBeenCalledWith(
      expect.objectContaining({
        requestId: expect.any(String),
        targetType: "opportunity",
        targetId: "selected-opportunity",
        startMonth: "2026-12",
        jobCode: "BE",
        gradeCode: "SR",
      }),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/common/ai-recommendation-options",
      { method: "GET" },
    );
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(router?.state.location.pathname).toBe("/screens/UI-30");
    expect(router?.state.location.search).toBe("");
    expect(router?.state.matches.at(-1)?.route.path).toBe("/screens/:scrnId");
    expectShell();
  },
);

it("M026 opens UI-30 inside the existing preferences provider", async () => {
  respond();
  mount("/screens/UI-26");
  await screen.findByRole("region", { name: "UI-26 본문" });
  const preferences = screen.getByTestId("user-preferences");
  const group = screen.getByTestId("menu-toggle-g009");
  if (group.getAttribute("aria-expanded") !== "true") fireEvent.click(group);
  fireEvent.click(screen.getByRole("link", { name: "AI 인력 추천" }));
  await screen.findByText("조건에 맞는 사람이 없습니다.");
  expect(router?.state.location.pathname).toBe("/screens/UI-30");
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  expectShell();
});

it("tab activation retains the recommendation draft without another execution", async () => {
  const { runs } = respond();
  mount("/screens/UI-30");
  await screen.findByText("조건에 맞는 사람이 없습니다.");
  fireEvent.change(screen.getByTestId("ai-grade"), { target: { value: "MD" } });
  await act(async () => {
    await router!.navigate("/screens/UI-26");
  });
  await screen.findByRole("region", { name: "UI-26 본문" });
  await act(async () => {
    await router!.navigate("/screens/UI-30");
  });
  expect(screen.getByTestId("ai-grade")).toHaveValue("MD");
  expect(screen.getByTestId("ai-applied")).toHaveTextContent("SR");
  expect(runs).toHaveBeenCalledTimes(1);
  expectShell();
});

it("an unavailable screen does not mount the feature or request its data", async () => {
  const { reads, runs } = respond(false);
  mount("/screens/UI-30");
  expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
  expect(
    screen.queryByTestId("ai-recommendation-page"),
  ).not.toBeInTheDocument();
  expect(reads).not.toHaveBeenCalled();
  expect(runs).not.toHaveBeenCalled();
  expectShell();
});

it("no ongoing targets renders the specified empty state without creating a recommendation", async () => {
  const { runs } = respond(true, true);
  mount("/screens/UI-30");
  expect(await screen.findByText("진행 중인 대상이 없습니다.")).toBeVisible();
  expect(runs).not.toHaveBeenCalled();
  expect(screen.queryByTestId("ai-results")).not.toBeInTheDocument();
  expectShell();
});

it("an options failure retries the same local client within the existing route", async () => {
  const { reads } = respond(true, true);
  reads.mockRejectedValueOnce(new Error("조건 조회 실패"));
  mount("/screens/UI-30");
  expect(await screen.findByTestId("ai-error")).toHaveTextContent(
    "조건 조회 실패",
  );
  const preferences = screen.getByTestId("user-preferences");
  fireEvent.click(screen.getByTestId("ai-options-retry"));
  expect(await screen.findByText("진행 중인 대상이 없습니다.")).toBeVisible();
  await waitFor(() => expect(reads).toHaveBeenCalledTimes(2));
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  expect(router?.state.location.pathname).toBe("/screens/UI-30");
  expectShell();
});

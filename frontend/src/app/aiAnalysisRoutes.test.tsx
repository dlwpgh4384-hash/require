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
import type {
  AnalysisAnswer,
  AnalysisHistoryItem,
  AnalysisInput,
} from "../pages/aianalysis/aiAnalysis";
import {
  publishScreenPeriod,
  screenPeriodChannel,
} from "../shared/layout/screenPeriodChannel";

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
  publishScreenPeriod("ADDED", undefined);
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function respond() {
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
        menuId: "source-screen",
        upperMenuId: null,
        menuNm: "원래 화면",
        scrnId: "ADDED",
        menuLvl: 1,
        sortOrd: 10,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [
        { scrnId: "UI-26", available: true, pcOnlyYn: "N" },
        { scrnId: "ADDED", available: true, pcOnlyYn: "N" },
      ],
    },
  };
  const answer: AnalysisAnswer = {
    questionId: "101",
    answerStatus: "OK",
    conclusion: "원천 조회 결과",
    table: { columns: [{ key: "revenue", label: "매출" }], rows: [[123456]] },
    assumptions: [],
    usedData: "원래 화면의 마감 자료",
    baseMonth: "2026-11",
    provisional: false,
    actions: [
      {
        label: "원래 화면 열기",
        targetScreenId: "ADDED",
        period: { startMonth: "2026-01", endMonth: "2026-09" },
      },
    ],
    suggestedQuestions: [],
  };
  const history: AnalysisHistoryItem[] = [];
  const ok = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  });
  const reads = vi.fn(async () => ok({ items: [...history] }));
  const runs = vi.fn(async (input: AnalysisInput) => {
    history.push({
      questionId: answer.questionId,
      question: input.question,
      askedAt: "2026-11-25T00:00:00Z",
      answerStatus: answer.answerStatus,
    });
    return ok(answer);
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
    if (url === "/api/common/ai-analysis/questions") {
      return init?.method === "POST"
        ? runs(JSON.parse(String(init.body)) as AnalysisInput)
        : reads();
    }
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, reads, runs, answer };
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
function expectSharedRoute() {
  expect(router?.state.matches.at(-1)?.route.path).toBe("/screens/:scrnId");
  expect(router?.state.location.search).toBe("");
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.getAllByTestId("user-preferences")).toHaveLength(1);
}
async function ask() {
  fireEvent.change(screen.getByTestId("analysis-question"), {
    target: { value: "이번 달 매출" },
  });
  fireEvent.click(screen.getByTestId("analysis-send"));
  await screen.findByText("원천 조회 결과");
  await waitFor(() =>
    expect(screen.getByTestId("analysis-send")).toBeEnabled(),
  );
}

it.each(["/screens/UI-26", "/#UI-26"])(
  "%s resolves the real page and feature-local client without login",
  async (path) => {
    const { request, runs } = respond();
    mount(path);
    expect(await screen.findByText("저장된 질문이 없습니다.")).toBeVisible();
    expect(screen.getByTestId("ai-analysis-page")).toHaveAttribute(
      "data-screen-id",
      "UI-26",
    );
    await ask();
    expect(screen.getByText("123,456원")).toBeVisible();
    expect(runs).toHaveBeenCalledWith(
      expect.objectContaining({
        requestId: expect.any(String),
        question: "이번 달 매출",
        screenId: "UI-26",
      }),
    );
    expect(request).toHaveBeenCalledWith(
      "/api/common/ai-analysis/questions",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
      }),
    );
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(router?.state.location.pathname).toBe("/screens/UI-26");
    expectSharedRoute();
  },
);

it("M025 opens UI-26 through the existing AI menu without replacing preferences", async () => {
  respond();
  mount("/screens/ADDED");
  await screen.findByRole("region", { name: "ADDED 본문" });
  const preferences = screen.getByTestId("user-preferences");
  fireEvent.click(screen.getByTestId("menu-toggle-g009"));
  fireEvent.click(screen.getByRole("link", { name: "AI 분석 도우미" }));
  expect(await screen.findByTestId("ai-analysis-page")).toBeVisible();
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  expect(router?.state.location.pathname).toBe("/screens/UI-26");
  expectSharedRoute();
});

it("UI-38 shares the routed conversation across close and reopen without a new route or POST", async () => {
  const { runs } = respond();
  mount("/screens/UI-26");
  await screen.findByTestId("ai-analysis-page");
  await ask();
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  const dialog = within(screen.getByTestId("ai-analysis-dialog"));
  expect(dialog.getByText("원천 조회 결과")).toBeVisible();
  expect(dialog.getByTestId("analysis-modal-content")).toHaveAttribute(
    "data-screen-id",
    "UI-38",
  );
  fireEvent.click(dialog.getByTestId("analysis-cancel"));
  expect(screen.queryByTestId("ai-analysis-dialog")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  expect(
    within(screen.getByTestId("ai-analysis-dialog")).getByText(
      "원천 조회 결과",
    ),
  ).toBeVisible();
  expect(runs).toHaveBeenCalledTimes(1);
  expect(router?.state.location.pathname).toBe("/screens/UI-26");
  expectSharedRoute();
});

it("header sends the active source period and answer navigation closes the modal with the answer period", async () => {
  const { runs, answer } = respond();
  const sourcePeriod = { startMonth: "2026-03", endMonth: "2026-06" };
  publishScreenPeriod("ADDED", sourcePeriod);
  mount("/screens/ADDED");
  await screen.findByRole("region", { name: "ADDED 본문" });
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  const dialog = within(screen.getByTestId("ai-analysis-dialog"));
  fireEvent.change(dialog.getByLabelText("질문"), {
    target: { value: "매출" },
  });
  fireEvent.click(dialog.getByTestId("analysis-send"));
  await dialog.findByText("원천 조회 결과");
  await waitFor(() =>
    expect(dialog.getByTestId("analysis-send")).toBeEnabled(),
  );
  expect(runs).toHaveBeenCalledWith(
    expect.objectContaining({ screenId: "ADDED", period: sourcePeriod }),
  );
  const receive = vi.fn();
  const unsubscribe = screenPeriodChannel("ADDED").subscribe(receive);
  try {
    fireEvent.click(dialog.getByRole("button", { name: "원래 화면 열기" }));
    expect(receive).toHaveBeenCalledWith(answer.actions[0].period);
  } finally {
    unsubscribe();
  }
  expect(screen.queryByTestId("ai-analysis-dialog")).not.toBeInTheDocument();
  expect(router?.state.location.pathname).toBe("/screens/ADDED");
  expectSharedRoute();
});

it("closing the actual UI-26 tab clears shared dialogue while server history survives reopening", async () => {
  const { runs } = respond();
  mount("/screens/ADDED");
  await screen.findByRole("region", { name: "ADDED 본문" });
  await act(async () => {
    await router!.navigate("/screens/UI-26");
  });
  await screen.findByTestId("ai-analysis-page");
  await ask();
  expect(await screen.findByTestId("analysis-replay-101")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("screen-tab-close-ui-26"));
  await waitFor(() =>
    expect(screen.queryByTestId("ai-analysis-page")).not.toBeInTheDocument(),
  );
  await act(async () => {
    await router!.navigate("/screens/UI-26");
  });
  await screen.findByTestId("ai-analysis-page");
  expect(screen.queryByText("원천 조회 결과")).not.toBeInTheDocument();
  expect(screen.getByTestId("analysis-replay-101")).toBeInTheDocument();
  expect(runs).toHaveBeenCalledTimes(1);
  expectSharedRoute();
});

it("a history failure retries the same local client inside the routed page", async () => {
  const { reads } = respond();
  reads.mockRejectedValueOnce(new Error("offline"));
  mount("/screens/UI-26");
  fireEvent.click(await screen.findByTestId("analysis-history-retry"));
  expect(await screen.findByText("저장된 질문이 없습니다.")).toBeVisible();
  expect(reads).toHaveBeenCalledTimes(2);
  expect(router?.state.location.pathname).toBe("/screens/UI-26");
  expectSharedRoute();
});

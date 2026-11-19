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
import type { Project } from "../pages/projects/projects";
import type { BoardMemo } from "../pages/projects/projectBoardDetails";
import type { ProjectBoardResult } from "../pages/projectboard/projectBoard";

let router: ReturnType<typeof createAppRouter> | undefined;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
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

function respond(statusCode = "ING", projectId = "selected-board-project") {
  const project: Project = {
    projectId,
    projectName: `현황판 ${projectId}`,
    customerId: null,
    customerName: "(자체)",
    contractTypeCode: "IN",
    contractTypeName: "내부",
    startDate: "2025-01-01",
    endDate: "2099-12-31",
    salesPartId: null,
    salesPartName: null,
    workplace: null,
    statusCode,
    statusName: (
      { ING: "진행", PRE: "수주예정", END: "종료", LOSE: "실주" } as Record<
        string,
        string
      >
    )[statusCode],
    estimateAmount: null,
    contractAmount: 0,
    originalContractAmount: 0,
    budgetAmount: null,
    budgetManualYn: "N",
    teamExpenseAmount: 0,
    projectExpenseAmount: 0,
    feature: projectId,
    pmName: null,
    trafficLight: null,
    canEdit: statusCode !== "LOSE",
    canChangeBudget: false,
    canChangeSalesPart: false,
    canSaveManpowerPlan: false,
    canLose: statusCode === "PRE",
    canStart: statusCode === "PRE",
    assignedPeopleCount: 0,
    preContractPeopleCount: 0,
    distributionStepName: null,
    distributionMismatchCount: 0,
    distributionTotalWarning: false,
    loseDate: null,
    loseReasonName: null,
    loseMemo: null,
    assignments: [],
    issues: [],
    memos: [],
    progress: {
      plannedProgressRate: null,
      actualProgressRate: null,
      steps: [],
    },
  };
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      {
        menuId: "G003",
        upperMenuId: null,
        menuNm: "프로젝트·투입",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 3,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "M004",
        upperMenuId: "G003",
        menuNm: "프로젝트",
        scrnId: "UI-01",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "M005",
        upperMenuId: "G003",
        menuNm: "프로젝트 현황판",
        scrnId: "UI-31",
        menuLvl: 2,
        sortOrd: 2,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: ["UI-01", "UI-31"].map((scrnId) => ({
        scrnId,
        available: true,
        pcOnlyYn: "N" as const,
      })),
    },
  };
  const board: ProjectBoardResult = {
    summary: {
      progress: { total: 1, normal: 1, caution: 0, danger: 0 },
      riskCount: 0,
      openIssues: { total: 0, high: 0 },
      overdueIssueCount: 0,
      staleProjectCount: 0,
    },
    staleProjects: [],
    calculationMethod: "DB 계산 방법 · UI-04 상세 확인",
    items: [
      {
        projectId,
        projectName: project.projectName,
        contractTypeCode: "IN",
        signal: "정상",
        signalReasons: [],
        reasonSummary: "경보 없음",
        pmUserId: null,
        pmName: null,
        currentStepName: null,
        stepDelayed: false,
        plannedProgress: null,
        actualProgress: null,
        costUsageRate: null,
        costProgressGap: null,
        openIssueCount: 0,
        overdueIssueCount: 0,
        latestMemoDate: null,
        daysWithoutMemo: null,
        stale: false,
      },
    ],
  };
  const memos: BoardMemo[] = [];
  const list = vi.fn(async () => ok(board));
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    const path = url.split("?")[0];
    if (path === "/api/common/menus")
      return { ok: true, status: 200, json: async () => menus };
    if (path.startsWith("/api/common/user-preferences/"))
      return ok({
        prefTypeCd: path.split("/").at(-2),
        prefKey: "GLOBAL",
        found: false,
        prefVal: null,
        modDttm: null,
      });
    if (path === "/api/common/project-board") return list();
    if (path === "/api/common/projects")
      return ok({ items: [project], unassignedCount: 0 });
    if (path === `/api/common/projects/${projectId}`) return ok(project);
    if (path === `/api/common/projects/${projectId}/weekly-memos`) {
      if (init?.method === "POST") {
        const memo: BoardMemo = {
          ...JSON.parse(String(init.body)),
          memoId: "1",
          projectId,
          createdBy: "admin",
          createdAt: "2026-11-18T00:00:00Z",
          updatedAt: null,
          updatedBy: null,
        };
        memos.push(memo);
        return ok(memo);
      }
      return ok({ items: memos });
    }
    if (path === `/api/common/projects/${projectId}/manpower-plan`)
      return ok({ items: [], canSave: false });
    if (path === "/api/common/projects/budget-rule")
      return ok({ targetMarginRate: 20, budgetFactor: 0.8 });
    if (path === "/api/common/project-issues")
      return ok({ items: [], openCount: 7 });
    if (
      path.startsWith("/api/common/code-groups/") ||
      path === "/api/common/parts" ||
      path === "/api/common/clients" ||
      path.startsWith(`/api/common/projects/${projectId}/`)
    )
      return ok({ items: [] });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, list, board, project };
}
function mount(path = "/screens/UI-31") {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
function expectSingleShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("complementary")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
}

it.each(["/screens/UI-31", "/#UI-31"])(
  "%s dispatches UI-31 through the existing shell and direct board client",
  async (path) => {
    const { request } = respond();
    mount(path);
    await screen.findByTestId("list-row-selected-board-project");
    expect(screen.getAllByTestId("project-board-page")).toHaveLength(1);
    expectSingleShell();
    expect(request).toHaveBeenCalledWith("/api/common/project-board?", {
      signal: expect.any(AbortSignal),
    });
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(screen.getByTestId("screen-actions")).toBeEmptyDOMElement();
  },
);

it.each(["ING", "PRE", "END", "LOSE"])(
  "a selected project now in %s still opens progress, never silently falling back to basic",
  async (statusCode) => {
    // The detail may have ended/lost since the board response was captured.
    const { project, request } = respond(statusCode);
    mount();
    fireEvent.click(await screen.findByTestId(`list-row-${project.projectId}`));
    const detail = within(await screen.findByTestId("project-detail"));
    expect(
      await detail.findByRole("tab", { name: "진행 현황", selected: true }),
    ).toBeVisible();
    expect(detail.getByRole("tab", { name: "기본 정보" })).toHaveAttribute(
      "aria-selected",
      "false",
    );
    await detail.findByTestId("project-progress-panel");
    await waitFor(() =>
      expect(detail.queryByText("불러오는 중입니다.")).not.toBeInTheDocument(),
    );
    if (statusCode === "END" || statusCode === "LOSE") {
      expect(detail.queryByTestId("project-memo-add")).not.toBeInTheDocument();
      expect(
        detail.queryByTestId("project-stage-edit"),
      ).not.toBeInTheDocument();
    } else expect(detail.getByTestId("project-memo-add")).toBeEnabled();
    expect(request).toHaveBeenCalledWith(
      `/api/common/projects/${project.projectId}/weekly-memos`,
      { method: "GET" },
    );
    expect(
      request.mock.calls.filter(
        ([url, init]) =>
          url.startsWith("/api/common/projects") &&
          ["POST", "PUT", "DELETE"].includes(init?.method ?? ""),
      ),
    ).toEqual(
      statusCode === "END" || statusCode === "LOSE"
        ? []
        : [
            [
              `/api/common/projects/${project.projectId}/steps/ensure`,
              { method: "POST" },
            ],
          ],
    );
  },
);

it.each(["first-board-project", "other-board-project"])(
  "detail edit closes the drawer and opens UI-01's existing edit form for %s",
  async (projectId) => {
    const { project, request } = respond("ING", projectId);
    mount();
    fireEvent.click(await screen.findByTestId(`list-row-${projectId}`));
    const detail = within(await screen.findByTestId("project-detail"));
    fireEvent.click(await detail.findByTestId("project-edit"));
    const form = within(await screen.findByTestId("project-form"));
    expect(form.getByRole("textbox", { name: "프로젝트명" })).toHaveValue(
      project.projectName,
    );
    expect(form.getByTestId("project-feature")).toHaveValue(projectId);
    expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument();
    expect(router?.state.location.pathname).toBe("/screens/UI-01");
    expect(
      request.mock.calls.filter(
        ([url, init]) =>
          url.startsWith("/api/common/projects") &&
          !url.endsWith("/steps/ensure") &&
          ["POST", "PUT", "DELETE"].includes(init?.method ?? ""),
      ),
    ).toHaveLength(0);
    expectSingleShell();
  },
);

it("memo save from routed progress refreshes the owning board and displays onChanged feedback", async () => {
  const { project, request, list } = respond();
  mount();
  fireEvent.click(await screen.findByTestId(`list-row-${project.projectId}`));
  const detail = within(await screen.findByTestId("project-detail"));
  const add = await detail.findByTestId("project-memo-add");
  await waitFor(() => expect(add).toBeEnabled());
  fireEvent.click(add);
  fireEvent.change(await screen.findByTestId("project-memo-work-done"), {
    target: { value: "이번 주 진행 기록" },
  });
  fireEvent.click(screen.getByTestId("project-progress-save"));
  expect(await screen.findByTestId("project-board-toast")).toHaveTextContent(
    "저장했습니다.",
  );
  await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
  expect(request).toHaveBeenCalledWith(
    `/api/common/projects/${project.projectId}/weekly-memos`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        workDone: "이번 주 진행 기록",
        nextWork: null,
        requests: null,
        actualProgress: null,
      }),
    },
  );
  expect(
    request.mock.calls.filter(
      ([url, init]) => url.endsWith("/weekly-memos") && init?.method === "POST",
    ),
  ).toHaveLength(1);
  expect(screen.queryByTestId("project-memo-dialog")).not.toBeInTheDocument();
  expect(router?.state.location.pathname).toBe("/screens/UI-31");
});

it("the registered issue tab uses the merged issue client and reports its open count without Excel", async () => {
  const { request } = respond();
  mount();
  await screen.findByTestId("list-row-selected-board-project");
  fireEvent.click(screen.getByTestId("project-board-issues-tab"));
  expect(await screen.findByTestId("project-issues-panel")).toBeVisible();
  await waitFor(() =>
    expect(screen.getByTestId("project-board-issues-tab")).toHaveTextContent(
      "(7)",
    ),
  );
  expect(request).toHaveBeenCalledWith("/api/common/project-issues", {
    signal: expect.any(AbortSignal),
  });
  expect(screen.getByTestId("list-download")).not.toBeVisible();
  expectSingleShell();
});

it("an empty routed board retains filters and tabs", async () => {
  const { board } = respond();
  board.items = [];
  mount();
  expect(await screen.findByTestId("list-empty")).toHaveTextContent(
    "조건에 맞는 결과가 없습니다.",
  );
  expect(screen.getByTestId("project-board-filters")).toBeVisible();
  expect(screen.getByTestId("project-board-issues-tab")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "프로젝트" })).toBeVisible();
});

it("a failed routed board retries locally without authentication or a replacement route", async () => {
  const { list } = respond();
  list.mockRejectedValueOnce(new Error("현황판 조회 연결 실패"));
  mount();
  fireEvent.click(await screen.findByTestId("list-retry"));
  expect(
    await screen.findByTestId("list-row-selected-board-project"),
  ).toBeVisible();
  expect(list).toHaveBeenCalledTimes(2);
  expect(router?.state.location.pathname).toBe("/screens/UI-31");
  expectSingleShell();
});

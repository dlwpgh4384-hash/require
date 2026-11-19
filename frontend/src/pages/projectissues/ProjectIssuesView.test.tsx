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
import { ProjectIssuesView } from "./ProjectIssuesView";
import { ProjectDetail } from "../projects/ProjectDetail";
import { subscribeProjectEdit } from "../projects/projectEdit";
import type { Project } from "../projects/projects";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
let request: ReturnType<typeof vi.fn>;
let project: Project;
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  project = {
    projectId: "P-linked",
    projectName: "연결 프로젝트",
    contractTypeCode: "IN",
    contractTypeName: "내부",
    startDate: "2026-11-01",
    endDate: "2026-12-31",
    statusCode: "ING",
    statusName: "진행",
    customerId: null,
    customerName: "(자체)",
    salesPartId: null,
    salesPartName: null,
    workplace: null,
    estimateAmount: null,
    contractAmount: 0,
    originalContractAmount: 0,
    budgetAmount: null,
    budgetManualYn: "N",
    teamExpenseAmount: 0,
    projectExpenseAmount: 0,
    feature: null,
    pmName: null,
    trafficLight: null,
    canEdit: true,
    canChangeBudget: false,
    canChangeSalesPart: false,
    canSaveManpowerPlan: false,
    canLose: false,
    canStart: false,
    assignedPeopleCount: 0,
    preContractPeopleCount: 0,
    distributionStepName: null,
    distributionMismatchCount: 0,
    distributionTotalWarning: false,
    loseDate: null,
    loseReasonName: null,
    loseMemo: null,
    issues: [],
    assignments: [],
    memos: [],
    progress: {
      plannedProgressRate: null,
      actualProgressRate: null,
      steps: [],
    },
  };
  request = vi.fn(async (url: string) => {
    const path = url.split("?")[0];
    if (path === "/api/common/project-issues")
      return ok({
        openCount: 1,
        items: [
          {
            issueId: "24",
            projectId: "P-linked",
            projectName: "연결 프로젝트",
            title: "연결 이슈",
            dueDate: "2026-11-14",
            importanceCode: "HIGH",
            statusCode: "OPEN",
            actionText: null,
            assigneeId: "admin",
            assigneeName: "관리자",
            createdBy: "admin",
            createdAt: "2026-11-01T00:00:00Z",
            updatedBy: null,
            updatedAt: null,
            overdueDays: 1,
          },
        ],
      });
    if (path === "/api/common/projects")
      return ok({ items: [project], unassignedCount: 0 });
    if (path === "/api/common/projects/P-linked") return ok(project);
    if (path === "/api/common/projects/budget-rule")
      return ok({ targetMarginRate: 20, budgetFactor: 0.8 });
    if (path.endsWith("/IMPR/codes"))
      return ok({
        items: [{ code: "HIGH", codeName: "높음", useYn: "Y", sortOrd: 1 }],
      });
    if (path.endsWith("/ISSUE_STAT/codes"))
      return ok({
        items: [{ code: "OPEN", codeName: "열림", useYn: "Y", sortOrd: 1 }],
      });
    if (path.endsWith("/manpower-plan"))
      return ok({ items: [], canSave: false });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

it("project name opens the actual selected ProjectDetail issues tab", async () => {
  render(<ProjectIssuesView />);
  fireEvent.click(await screen.findByTestId("project-issue-project-24"));
  const detail = await screen.findByTestId("project-detail");
  await waitFor(() =>
    expect(within(detail).getByRole("tab", { name: /이슈/ })).toHaveAttribute(
      "aria-selected",
      "true",
    ),
  );
  expect(
    within(detail).getByTestId("project-issues-panel"),
  ).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith(
    "/api/common/projects/P-linked",
    expect.anything(),
  );
  expect(
    request.mock.calls.some(([url]) => url.includes("projectId=P-linked")),
  ).toBe(true);
});

it.each(["END", "LOSE"])(
  "%s project retains the issue tab and edit flow through explicit issue renderer",
  async (state) => {
    project.statusCode = state;
    render(<ProjectIssuesView />);
    fireEvent.click(await screen.findByTestId("project-issue-project-24"));
    const detail = await screen.findByTestId("project-detail");
    const row = await within(detail).findByTestId("list-row-24");
    await waitFor(() =>
      expect(
        within(detail)
          .getByTestId("project-issue-status-filter")
          .querySelectorAll("option").length,
      ).toBeGreaterThan(1),
    );
    expect(
      within(detail).queryByTestId("project-issue-create"),
    ).not.toBeInTheDocument();
    fireEvent.click(row);
    expect(
      await screen.findByTestId("project-issue-dialog"),
    ).toBeInTheDocument();
  },
);

it("default ProjectDetail caller still starts on basic information", async () => {
  render(<ProjectDetail projectId="P-linked" onClose={vi.fn()} />);
  const detail = await screen.findByTestId("project-detail");
  await waitFor(() =>
    expect(
      within(detail).getByRole("tab", { name: "기본 정보" }),
    ).toHaveAttribute("aria-selected", "true"),
  );
  expect(
    within(detail).queryByTestId("project-issues-panel"),
  ).not.toBeInTheDocument();
});

it("detail opened from an issue keeps the shared progress commands when changing tabs", async () => {
  render(<ProjectIssuesView />);
  fireEvent.click(await screen.findByTestId("project-issue-project-24"));
  const detail = within(await screen.findByTestId("project-detail"));
  fireEvent.click(await detail.findByTestId("project-detail-tab-progress"));
  await waitFor(() =>
    expect(detail.queryByText("불러오는 중입니다.")).not.toBeInTheDocument(),
  );
  expect(detail.getByTestId("project-progress-panel")).toBeVisible();
  fireEvent.click(detail.getByTestId("project-memo-add"));
  expect(await screen.findByTestId("project-memo-dialog")).toBeVisible();
});

it("detail edit closes detail and sends the same project to existing edit hook", async () => {
  const listener = vi.fn();
  const unsubscribe = subscribeProjectEdit(listener);
  try {
    render(<ProjectIssuesView />);
    fireEvent.click(await screen.findByTestId("project-issue-project-24"));
    fireEvent.click(await screen.findByTestId("project-edit"));
    expect(listener).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: "P-linked" }),
    );
    expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument();
  } finally {
    unsubscribe();
  }
});

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
import { ProjectPage } from "./ProjectPage";
import { ProjectDetail } from "./ProjectDetail";
import { ProjectForm } from "./ProjectForm";
import { ProjectDistribution } from "./ProjectDistribution";
import {
  ProjectWorkspaceProvider,
  ProjectTitleActions,
} from "./ProjectWorkspace";
import { filterProjects, type Project } from "./projects";
const viewport = vi.hoisted(() => ({ mobile: false, width: 1280 }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
let row: Project;
let rows: Project[];
let request: ReturnType<typeof vi.fn>;
let failed: boolean;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const writes = () =>
  request.mock.calls.filter((c) =>
    ["POST", "PUT"].includes(
      (c[1] as RequestInit | undefined)?.method ?? "GET",
    ),
  );
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  viewport.mobile = false;
  failed = false;
  row = {
    projectId: "P-0001",
    projectName: "기간연결 프로젝트",
    customerId: "customer",
    customerName: "고객사",
    contractTypeCode: "TK",
    contractTypeName: "턴키",
    startDate: "2025-12-01",
    endDate: "2026-12-31",
    salesPartId: null,
    salesPartName: null,
    workplace: "서울",
    statusCode: "PRE",
    statusName: "수주예정",
    estimateAmount: 900000,
    contractAmount: 1000000,
    originalContractAmount: 1000000,
    budgetAmount: 800000,
    budgetManualYn: "N",
    teamExpenseAmount: 50000,
    projectExpenseAmount: 50000,
    feature: "특징",
    pmName: "담당PM",
    trafficLight: null,
    canEdit: true,
    canChangeBudget: true,
    canChangeSalesPart: true,
    canSaveManpowerPlan: true,
    canLose: true,
    canStart: true,
    assignedPeopleCount: 1,
    preContractPeopleCount: 1,
    distributionStepName: null,
    distributionMismatchCount: 2,
    distributionTotalWarning: true,
    loseDate: null,
    loseReasonName: null,
    loseMemo: null,
    assignments: [],
    issues: [],
    progress: {
      plannedProgressRate: null,
      actualProgressRate: null,
      steps: [],
    },
  };
  rows = [row];
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const path = url.split("?")[0];
    if (failed)
      return {
        ok: false,
        status: 500,
        json: async () => ({
          success: false,
          message: "조회 실패",
          errors: [],
        }),
      };
    if (init?.method === "PUT" || init?.method === "POST") {
      if (path.endsWith("/start")) {
        row.statusCode = "ING";
        row.statusName = "진행";
        row.canLose = false;
        row.canStart = false;
      }
      if (path === "/api/common/projects") {
        const input = JSON.parse(init.body as string);
        row = { ...row, ...input, projectId: "P-0002" };
        rows = [...rows, row];
      }
      if (path === `/api/common/projects/${row.projectId}`)
        row = { ...row, ...JSON.parse(init.body as string) };
      return ok(row);
    }
    if (path === "/api/common/projects")
      return ok({ items: rows, unassignedCount: 1 });
    if (path.endsWith("/direct-expenses"))
      return ok({
        items: [
          {
            expenseId: "202611:1",
            yearMonth: "2026-11",
            itemCode: "ETC",
            itemName: "기타",
            amount: 15000,
            memo: "외주만 있는 달",
          },
        ],
      });
    if (path.endsWith("/sales-parts") || path.endsWith("/change-history"))
      return ok({ items: [] });
    if (path.endsWith("/manpower-plan"))
      return ok({
        items: [
          {
            jobCode: "BE",
            jobName: "개발",
            plannedMm: null,
            actualMm: 1,
            differenceMm: null,
          },
        ],
        canSave: row.canSaveManpowerPlan,
      });
    if (path.endsWith("/lose-impact"))
      return ok({ activePeopleCount: 1, futurePeopleCount: 2 });
    if (path.endsWith("/distribution"))
      return ok({
        project: row,
        items: [],
        uploads: [],
        mismatches: [],
        totalAmountWarning: true,
      });
    if (path.startsWith("/api/common/projects/")) return ok(row);
    if (path.includes("code-groups/") && path.endsWith("/codes")) {
      const group = path.split("/").at(-2);
      const codes: Record<string, unknown[]> = {
        CTRT_TYPE: [
          { code: "TK", codeName: "턴키" },
          { code: "IN", codeName: "내부" },
        ],
        PROJ_STAT: [
          { code: "ING", codeName: "진행" },
          { code: "PRE", codeName: "수주예정" },
        ],
        ALWNC: [
          { code: "TEAM_FEE", codeName: "팀비", codeVal: 50000 },
          { code: "PEXP_FEE", codeName: "프로젝트경비", codeVal: 50000 },
        ],
        JOB: [
          { code: "BE", codeName: "개발" },
          { code: "PM", codeName: "PM" },
        ],
        LOSE_RSN: [{ code: "ETC", codeName: "기타" }],
        DRCT_EXPS_ITEM: [{ code: "ETC", codeName: "기타" }],
      };
      return ok({ items: codes[group!] ?? [] });
    }
    if (path === "/api/common/clients")
      return ok({
        items: [
          {
            clientId: "customer",
            clientName: "고객사",
            clientTypeCode: "CUST",
            useYn: "Y",
          },
        ],
      });
    if (path === "/api/common/parts")
      return ok({ items: [{ partId: "part", partName: "영업1", useYn: "Y" }] });
    if (path === "/api/common/operating-standards")
      return ok({
        items: [
          {
            standardCode: "TGT_MRGN_RT",
            value: "20",
            effectiveMonth: "2026-01",
          },
        ],
      });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
it("the shell title action opens the workspace registration form", async () => {
  render(
    <ProjectWorkspaceProvider>
      <ProjectTitleActions />
      <ProjectPage />
    </ProjectWorkspaceProvider>,
  );
  await screen.findByTestId("project-page");
  fireEvent.click(screen.getByTestId("project-title-register"));
  expect(await screen.findByTestId("project-form")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("project-form-cancel"));
  expect(screen.queryByTestId("project-form")).not.toBeInTheDocument();
});
it("filters cross-year overlap in both years and excludes internal from unassigned count", () => {
  rows.push({ ...row, projectId: "P-0003", contractTypeCode: "IN" });
  for (const year of ["2025", "2026"]) {
    const result = filterProjects(rows, {
      year,
      keyword: "",
      statusCode: "",
      salesPartId: "",
      contractTypeCode: "",
      unassignedOnly: true,
    });
    expect(result.unassignedCount).toBe(1);
    expect(result.items.map((p) => p.projectId)).toEqual(["P-0001"]);
  }
});
it("fetches list without invented query parameters and narrows yellow banner", async () => {
  rows.push({
    ...row,
    projectId: "P-0003",
    projectName: "내부 프로젝트",
    contractTypeCode: "IN",
  });
  render(<ProjectPage />);
  await screen.findByText("기간연결 프로젝트");
  expect(request.mock.calls.some((c) => c[0] === "/api/common/projects")).toBe(
    true,
  );
  fireEvent.click(screen.getByTestId("project-unassigned-only"));
  expect(screen.queryByText("내부 프로젝트")).not.toBeInTheDocument();
});
it("past-year list disables create and every detail write for continued project", async () => {
  render(<ProjectPage />);
  await screen.findByText("기간연결 프로젝트");
  fireEvent.change(screen.getByTestId("project-year"), {
    target: { value: "2025" },
  });
  expect(screen.getByTestId("project-register")).toBeDisabled();
  fireEvent.click(screen.getByTestId("project-name-p-0001"));
  await screen.findByTestId("project-detail");
  await screen.findByText("담당PM");
  expect(screen.queryByTestId("project-edit")).not.toBeInTheDocument();
  expect(screen.queryByTestId("project-budget-change")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("mobile uses common cards, filter active count and more", async () => {
  viewport.mobile = true;
  rows = Array.from({ length: 55 }, (_, i) => ({
    ...row,
    projectId: `P-${i}`,
    projectName: `프로젝트 ${i}`,
  }));
  render(<ProjectPage />);
  await screen.findByTestId("project-card-p-0");
  fireEvent.click(screen.getByTestId("list-filter-toggle"));
  fireEvent.change(screen.getByTestId("project-keyword"), {
    target: { value: "프로젝트" },
  });
  expect(screen.getByTestId("list-filter-toggle")).toHaveTextContent(
    "조건 (1)",
  );
  expect(screen.getByText(/더 보기/)).toBeInTheDocument();
});
it("empty list renders common empty state", async () => {
  rows = [];
  render(<ProjectPage />);
  await screen.findByTestId("list-empty");
});
it("failed list has retry", async () => {
  failed = true;
  render(<ProjectPage />);
  await screen.findByTestId("list-error");
  expect(screen.getByText("다시 시도")).toBeInTheDocument();
});
it("lost detail hides edit, ellipsis, expense and budget controls", async () => {
  row.statusCode = "LOSE";
  row.statusName = "실주";
  row.loseDate = "2026-11-15";
  row.loseReasonName = "기타";
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText(/실주 처리일/);
  for (const id of [
    "project-edit",
    "project-more",
    "project-budget-change",
    "project-part-change",
    "project-expense-add",
  ])
    expect(screen.queryByTestId(id)).not.toBeInTheDocument();
});
it("internal detail hides additional expenses", async () => {
  row.contractTypeCode = "IN";
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText("담당PM");
  expect(screen.queryByTestId("project-expenses")).not.toBeInTheDocument();
});
it("pre-contract personnel banner is absent from running state", async () => {
  row.statusCode = "ING";
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText("담당PM");
  expect(screen.queryByText(/계약 전에/)).not.toBeInTheDocument();
});
it("start confirmation cancellation sends no command; save removes ellipsis", async () => {
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText("담당PM");
  fireEvent.click(screen.getByTestId("project-start"));
  fireEvent.click(screen.getByTestId("project-command-cancel"));
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("project-start"));
  fireEvent.click(screen.getByTestId("project-command-save"));
  await waitFor(() =>
    expect(screen.queryByTestId("project-more")).not.toBeInTheDocument(),
  );
  expect(writes()).toHaveLength(1);
});
it("expense deletion cancellation sends no write", async () => {
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText("외주만 있는 달");
  fireEvent.click(screen.getByTestId("project-expense-delete-202611-1"));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByText("취소"));
  expect(writes()).toHaveLength(0);
});
it("unauthorized manpower is readonly", async () => {
  row.canSaveManpowerPlan = false;
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText("담당PM");
  fireEvent.click(screen.getByRole("tab", { name: "인원계획" }));
  expect(screen.getByLabelText("개발 계획MM")).toBeDisabled();
  expect(screen.queryByTestId("project-plan-save")).not.toBeInTheDocument();
});
it("the manpower response can independently deny saving a plan", async () => {
  const original = request.getMockImplementation()!;
  request.mockImplementation((url: string, init?: RequestInit) =>
    url.endsWith("/manpower-plan")
      ? Promise.resolve(
          ok({
            items: [
              {
                jobCode: "BE",
                jobName: "개발",
                plannedMm: null,
                actualMm: 1,
                differenceMm: null,
              },
            ],
            canSave: false,
          }),
        )
      : original(url, init),
  );
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText("담당PM");
  fireEvent.click(screen.getByRole("tab", { name: "인원계획" }));
  expect(screen.getByLabelText("개발 계획MM")).toBeDisabled();
  expect(screen.queryByTestId("project-plan-save")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("switching the project ignores an older pending detail response", async () => {
  const original = request.getMockImplementation()!;
  let finish!: (response: ReturnType<typeof ok>) => void;
  const oldResponse = ok({ ...row, projectName: "이전 프로젝트" });
  request.mockImplementation((url: string, init?: RequestInit) =>
    url === "/api/common/projects/P-0001"
      ? new Promise<ReturnType<typeof ok>>((resolve) => {
          finish = resolve;
        })
      : original(url, init),
  );
  const view = render(<ProjectDetail projectId="P-0001" onClose={vi.fn()} />);
  row = { ...row, projectId: "P-0002", projectName: "새 프로젝트" };
  view.rerender(<ProjectDetail projectId="P-0002" onClose={vi.fn()} />);
  await screen.findByRole("heading", { name: "새 프로젝트" });
  await act(async () => {
    finish(oldResponse);
  });
  await waitFor(() =>
    expect(
      screen.queryByRole("heading", { name: "이전 프로젝트" }),
    ).not.toBeInTheDocument(),
  );
  expect(
    screen.getByRole("heading", { name: "새 프로젝트" }),
  ).toBeInTheDocument();
});
it("initial manpower absence is blank and added plan job is visible", async () => {
  render(<ProjectDetail projectId={row.projectId} onClose={vi.fn()} />);
  await screen.findByText("담당PM");
  fireEvent.click(screen.getByRole("tab", { name: "인원계획" }));
  expect(screen.getByLabelText("개발 계획MM")).toHaveValue(null);
  fireEvent.change(screen.getByTestId("project-job-select"), {
    target: { value: "PM" },
  });
  fireEvent.click(screen.getByTestId("project-job-add"));
  expect(screen.getByLabelText("PM 계획MM")).toBeInTheDocument();
});
it("edit form shows immutable fields and omits them and untouched budget from update", async () => {
  render(<ProjectForm project={row} onSaved={vi.fn()} onClose={vi.fn()} />);
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
  expect(screen.getByTestId("project-type-readonly")).toHaveAttribute(
    "readonly",
  );
  fireEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const body = JSON.parse(writes()[0][1].body);
  for (const key of [
    "projectId",
    "statusCode",
    "salesPartId",
    "contractTypeCode",
    "originalContractAmount",
    "budgetAmount",
  ])
    expect(body).not.toHaveProperty(key);
});
it("create uses API defaults for allowances and explicit manual budget only after edit", async () => {
  render(<ProjectForm onSaved={vi.fn()} onClose={vi.fn()} />);
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
  expect(screen.getByTestId("project-team-expense-amount")).toHaveValue(50000);
  fireEvent.change(screen.getByTestId("project-project-name"), {
    target: { value: "신규" },
  });
  fireEvent.change(screen.getByTestId("project-contract-type-code"), {
    target: { value: "IN" },
  });
  fireEvent.change(screen.getByTestId("project-status-code"), {
    target: { value: "ING" },
  });
  expect(screen.queryByTestId("project-customer-id")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(writes()[0][1].body)).not.toHaveProperty("budgetAmount");
});
it("an unchanged automatic budget displays the saved amount rather than a new margin preview", async () => {
  row.budgetAmount = 700000;
  render(<ProjectForm project={row} onSaved={vi.fn()} onClose={vi.fn()} />);
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
  expect(screen.getByTestId("project-budget-amount")).toHaveValue(700000);
  fireEvent.change(screen.getByTestId("project-contract-amount"), {
    target: { value: "2000000" },
  });
  expect(screen.getByTestId("project-budget-amount")).toHaveValue(1600000);
  fireEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(writes()[0][1].body)).not.toHaveProperty("budgetAmount");
});
it("an explicit budget equal to its automatic value is still submitted as manual intent", async () => {
  render(<ProjectForm project={row} onSaved={vi.fn()} onClose={vi.fn()} />);
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
  fireEvent.change(screen.getByTestId("project-budget-amount"), {
    target: { value: "800001" },
  });
  fireEvent.change(screen.getByTestId("project-budget-amount"), {
    target: { value: "800000" },
  });
  fireEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(writes()[0][1].body)).toHaveProperty(
    "budgetAmount",
    800000,
  );
});
it("invalid date blocks form submission", async () => {
  render(<ProjectForm project={row} onSaved={vi.fn()} onClose={vi.fn()} />);
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
  fireEvent.change(screen.getByTestId("project-end-date"), {
    target: { value: "2025-01-01" },
  });
  fireEvent.click(screen.getByTestId("project-form-save"));
  expect(writes()).toHaveLength(0);
  expect(
    screen.getByText("종료일은 시작일 이후여야 합니다."),
  ).toBeInTheDocument();
});
it("mobile distribution renders PC-only explanation and back", () => {
  viewport.mobile = true;
  const back = vi.fn();
  render(<ProjectDistribution projectId={row.projectId} onBack={back} />);
  expect(screen.getByText(/PC전용/)).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("project-distribution-back"));
  expect(back).toHaveBeenCalledOnce();
});
it("registration replaces the list area and clean cancel restores the same filters without writes", async () => {
  render(<ProjectPage />);
  await screen.findByTestId("project-name-p-0001");
  fireEvent.change(screen.getByTestId("project-keyword"), {
    target: { value: "기간연결" },
  });
  fireEvent.click(screen.getByTestId("project-register"));
  await screen.findByTestId("project-form-workspace");
  expect(screen.queryByTestId("project-page")).not.toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("project-form-cancel"));
  expect(screen.getByTestId("project-keyword")).toHaveValue("기간연결");
  expect(writes()).toHaveLength(0);
});
it("edit replaces list and detail rather than nesting a form dialog", async () => {
  render(<ProjectPage />);
  await screen.findByTestId("project-name-p-0001");
  fireEvent.click(screen.getByTestId("project-name-p-0001"));
  await screen.findByText("담당PM");
  fireEvent.click(screen.getByTestId("project-edit"));
  await screen.findByTestId("project-form-workspace");
  expect(screen.queryByTestId("project-page")).not.toBeInTheDocument();
  expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("project-form-cancel"));
  await screen.findByText("담당PM");
  expect(writes()).toHaveLength(0);
});
it("dirty form cancel keeps changes until the shared discard confirmation is accepted", async () => {
  render(<ProjectPage />);
  await screen.findByTestId("project-name-p-0001");
  fireEvent.click(screen.getByTestId("project-register"));
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
  fireEvent.change(screen.getByTestId("project-project-name"), {
    target: { value: "미저장" },
  });
  fireEvent.click(screen.getByTestId("project-form-cancel"));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByText("취소"));
  expect(screen.getByTestId("project-project-name")).toHaveValue("미저장");
  fireEvent.click(screen.getByTestId("project-form-cancel"));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByText("버리기"));
  await screen.findByTestId("project-page");
  expect(writes()).toHaveLength(0);
});
it("registration saves once without extra confirmation and returns to refreshed detail", async () => {
  render(<ProjectPage />);
  await screen.findByTestId("project-name-p-0001");
  fireEvent.click(screen.getByTestId("project-register"));
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
  fireEvent.change(screen.getByTestId("project-project-name"), {
    target: { value: "신규 내부" },
  });
  fireEvent.change(screen.getByTestId("project-contract-type-code"), {
    target: { value: "IN" },
  });
  fireEvent.click(screen.getByTestId("project-form-save"));
  await screen.findByTestId("project-detail");
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(1);
  expect(
    screen.queryByTestId("project-form-workspace"),
  ).not.toBeInTheDocument();
});

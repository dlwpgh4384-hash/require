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
import { MemoryRouter } from "react-router-dom";
import { AssignmentPage } from "./AssignmentPage";
import { AssignmentDetail } from "./AssignmentDetail";
import { AssignmentForm } from "./AssignmentForm";
import {
  assignmentInitialDates,
  assignmentPriceEditable,
  type Assignment,
  type AssignmentHistory,
} from "./assignments";
import {
  ProjectWorkspaceProvider,
  ProjectTitleActions,
} from "../projects/ProjectWorkspace";
import type { Project } from "../projects/projects";

const viewport = vi.hoisted(() => ({ mobile: false, width: 1280 }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
let request: ReturnType<typeof vi.fn>,
  row: Assignment,
  history: AssignmentHistory[],
  candidates: unknown[],
  failure: boolean,
  empty: boolean;
let project: Project;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const codes: Record<string, unknown[]> = {
  JOB: [{ code: "BE", codeName: "백엔드 개발" }],
  INPUT_TYPE: [
    { code: "NEW", codeName: "신규" },
    { code: "ADD", codeName: "추가투입" },
    { code: "SUB", codeName: "대체투입" },
    { code: "FREE", codeName: "무상지원" },
  ],
  EXT_PSBL: [
    { code: "H", codeName: "높음" },
    { code: "M", codeName: "보통" },
    { code: "L", codeName: "낮음" },
  ],
  ASGN_CHG_RSN: [{ code: "PERSON", codeName: "인력 사정" }],
  ASGN_CHG_TYPE: [
    { code: "INPUT", codeName: "투입" },
    { code: "EXT", codeName: "연장" },
    { code: "EARLY", codeName: "조기 철수" },
    { code: "UPRC", codeName: "단가 변경" },
    { code: "RATE", codeName: "월 MM 변경" },
    { code: "EXTP", codeName: "연장 가능성 변경" },
    { code: "KIND", codeName: "투입 구분 변경" },
    { code: "CRCT", codeName: "정정" },
    { code: "CNCL", codeName: "취소" },
  ],
};
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  failure = false;
  empty = false;
  candidates = [];
  row = {
    assignmentId: "A-0001",
    projectId: "P-0001",
    projectName: "개발 프로젝트",
    developerId: "developer",
    name: "김개발",
    managementNumber: "D-0001",
    jobCode: "BE",
    jobName: "백엔드 개발",
    startDate: "2026-11-01",
    endDate: "2026-12-31",
    billingPrice: 8000000,
    monthlyMm: 1,
    yearMm: 2,
    inputTypeCode: "NEW",
    inputTypeName: "신규",
    extensionPossibilityName: "미정",
    extensionCount: 0,
    warnings: [],
    historyCount: 1,
    modifiedAt: "2026-11-15T00:00:00Z",
  };
  project = {
    projectId: "P-0001",
    projectName: "개발 프로젝트",
    contractTypeCode: "MO",
    contractTypeName: "월단위",
    startDate: "2026-01-01",
    endDate: "2026-12-31",
    statusCode: "ING",
    statusName: "진행",
    salesPartId: "part",
    salesPartName: "영업파트",
    assignments: [],
    issues: [],
    progress: {
      steps: [],
      plannedProgressRate: null,
      actualProgressRate: null,
    },
  } as unknown as Project;
  history = [
    {
      historyId: "1",
      assignmentId: row.assignmentId,
      effectiveDate: "2026-11-01",
      changeTypeCode: "INPUT",
      changeTypeName: "투입",
      beforeValue: "",
      afterValue: "투입",
      reason: "",
      memo: "",
      changedBy: "admin",
      changedAt: row.modifiedAt,
      retroactiveYn: "N",
      correctedYn: "N",
      modifiedAt: row.modifiedAt,
    },
  ];
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const parsed = new URL(url, "http://application"),
      path = parsed.pathname;
    if (path.includes("code-groups/") && path.endsWith("/codes"))
      return ok({ items: codes[path.split("/").at(-2)!] ?? [] });
    if (path === "/api/common/projects")
      return ok({ items: [project], unassignedCount: 0 });
    if (path.startsWith("/api/common/projects/")) {
      if (path.endsWith("/distribution"))
        return ok({
          project,
          items: [],
          uploads: [],
          mismatches: [],
          totalAmountWarning: false,
        });
      if (path.endsWith("/manpower-plan"))
        return ok({ items: [], canSave: false });
      if (
        ["/direct-expenses", "/sales-parts", "/change-history"].some((suffix) =>
          path.endsWith(suffix),
        )
      )
        return ok({ items: [] });
      return ok(project);
    }
    if (path === "/api/common/developers")
      return ok({
        items: [
          {
            developerId: "developer",
            name: "김개발",
            managementNumber: "D-0001",
            workStatusName: "대기",
            employeeTypeCode: "FT",
            salesPartId: "part",
            salesPartName: "영업파트",
            withdrawalDate: null,
          },
        ],
      });
    if (path.startsWith("/api/common/developers/"))
      return ok({ monthlyRate: 8000000 });
    if (path === "/api/common/assignments/candidates")
      return ok({ items: candidates });
    if (path === "/api/common/assignments/contract-developers")
      return ok({ items: [] });
    if (path === "/api/common/assignments" && init?.method !== "POST")
      return failure
        ? {
            ok: false,
            status: 500,
            json: async () => ({
              success: false,
              message: "목록 조회 실패",
              errors: [],
            }),
          }
        : ok({ items: empty ? [] : [row] });
    if (path.endsWith("/history"))
      return ok({
        items: parsed.searchParams.get("changeTypeCode")
          ? history.filter(
              (h) =>
                h.changeTypeCode === parsed.searchParams.get("changeTypeCode"),
            )
          : history,
      });
    if (init?.method === "POST" || init?.method === "PUT") return ok(row);
    if (path === "/api/common/assignments/A-0001") return ok(row);
    return ok({ items: [], lastClosedMonth: null });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
function page(roles?: string[]) {
  return render(
    <MemoryRouter>
      <ProjectWorkspaceProvider>
        <header data-testid="assignment-title">
          <ProjectTitleActions />
        </header>
        <AssignmentPage roles={roles} />
      </ProjectWorkspaceProvider>
    </MemoryRouter>,
  );
}
const writes = () =>
  request.mock.calls.filter((c) =>
    ["POST", "PUT"].includes(
      (c[1] as RequestInit | undefined)?.method ?? "GET",
    ),
  );
it("renders the real API empty state", async () => {
  empty = true;
  page();
  await screen.findByTestId("assignment-empty");
  expect(screen.queryByTestId("assignment-row-A-0001")).not.toBeInTheDocument();
});
it("renders the eleven business columns and a single shared year caption", async () => {
  page();
  await screen.findByTestId("assignment-row-A-0001");
  expect(
    screen.getAllByText("그 해에 투입 기간이 걸친 배정 · MM은 그 해 합계"),
  ).toHaveLength(1);
  expect(
    within(screen.getByRole("table")).getAllByRole("columnheader"),
  ).toHaveLength(12);
  expect(
    screen.getByText("백엔드 개발", { selector: "td" }),
  ).toBeInTheDocument();
});
it("places create and upload only in the shared title host", async () => {
  page();
  await screen.findByTestId("assignment-create");
  expect(screen.getAllByTestId("assignment-create")).toHaveLength(1);
  expect(
    within(screen.getByTestId("assignment-title")).getByTestId(
      "assignment-create",
    ),
  ).toBeInTheDocument();
  expect(
    within(screen.getByTestId("assignment-page")).queryByTestId(
      "assignment-create",
    ),
  ).not.toBeInTheDocument();
});
it("last year remains readable and hides all editing controls", async () => {
  history[0] = {
    ...history[0],
    changeTypeCode: "EARLY",
    changeTypeName: "조기 철수",
  };
  page();
  await screen.findByTestId("assignment-row-A-0001");
  fireEvent.click(screen.getByTestId("assignment-year-prev"));
  await screen.findByText("지난해에는 읽기만 할 수 있습니다");
  expect(screen.getByTestId("assignment-year-year")).toHaveTextContent(
    "2025년",
  );
  expect(screen.queryByTestId("assignment-create")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("assignment-select-A-0001"),
  ).not.toBeInTheDocument();
  fireEvent.click(await screen.findByTestId("assignment-open-A-0001"));
  await screen.findByTestId("assignment-detail");
  expect(
    screen.queryByTestId("assignment-change-open"),
  ).not.toBeInTheDocument();
  const entry = await screen.findByTestId("assignment-history-1");
  expect(within(entry).getByText("조기 철수")).toBeInTheDocument();
  expect(
    within(entry).queryByRole("button", { name: "정정" }),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("shows empty and failed requests rather than a populated placeholder", async () => {
  failure = true;
  page();
  await screen.findByText("목록 조회 실패");
  expect(screen.queryByTestId("assignment-row-A-0001")).not.toBeInTheDocument();
  failure = false;
  fireEvent.click(screen.getByTestId("assignment-retry"));
  await screen.findByTestId("assignment-row-A-0001");
  expect(writes()).toHaveLength(0);
});
it("candidate confirmation only prefills a form and never writes", async () => {
  candidates = [
    {
      candidateId: "12",
      projectId: "P-0001",
      projectName: "개발 프로젝트",
      developerId: "developer",
      name: "김개발",
      jobCode: "BE",
      jobName: "백엔드 개발",
      startDate: "2026-12-01",
      endDate: "2026-12-31",
    },
  ];
  page();
  fireEvent.click(await screen.findByTestId("candidate-confirm-12"));
  await screen.findByTestId("assignment-form");
  await waitFor(() =>
    expect(screen.getByTestId("assignment-developer")).toHaveValue("developer"),
  );
  expect(screen.getByTestId("assignment-start-date")).toHaveValue("2026-12-01");
  expect(writes()).toHaveLength(0);
});
it("uses actual monthly overload values in the hover explanation", async () => {
  row.warnings = ["MM 1.0 초과"];
  row.monthlyMmTotal = 1.25;
  page();
  await screen.findByTestId("assignment-row-A-0001");
  expect(screen.getByTitle("MM 1.0 초과 월 MM 합계 1.25")).toHaveTextContent(
    "⚠",
  );
});
it("project link opens the existing project detail", async () => {
  page();
  fireEvent.click(await screen.findByTestId("assignment-project-A-0001"));
  await waitFor(() =>
    expect(
      request.mock.calls.some((c) => c[0] === "/api/common/projects/P-0001"),
    ).toBe(true),
  );
  const detail = await screen.findByRole("dialog", {
    name: project.projectName,
  });
  expect(within(detail).getByText(project.projectName)).toBeInTheDocument();
  expect(within(detail).getByText("계약 기간")).toBeInTheDocument();
  expect(
    within(detail).queryByText("프로젝트를 불러오는 중…"),
  ).not.toBeInTheDocument();
});
it("PM hides both list and detail monthly pricing", async () => {
  page(["PM"]);
  await screen.findByTestId("assignment-row-A-0001");
  expect(
    screen.queryByRole("columnheader", { name: "월 단가" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("assignment-open-A-0001"));
  await screen.findByTestId("assignment-detail");
  expect(
    within(screen.getByTestId("assignment-detail")).queryByText("월 단가"),
  ).not.toBeInTheDocument();
});
it("PM form hides price while finance may enter it and free support hides it again", async () => {
  const view = render(
    <MemoryRouter>
      <AssignmentForm
        projects={[project]}
        roles={["PM"]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </MemoryRouter>,
  );
  await waitFor(() =>
    expect(screen.getByTestId("assignment-form-save")).not.toBeDisabled(),
  );
  expect(
    screen.queryByTestId("assignment-billing-price"),
  ).not.toBeInTheDocument();
  view.unmount();
  render(
    <MemoryRouter>
      <AssignmentForm
        projects={[project]}
        roles={["FIN"]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </MemoryRouter>,
  );
  await waitFor(() =>
    expect(screen.getByTestId("assignment-billing-price")).toBeInTheDocument(),
  );
  fireEvent.change(screen.getByTestId("assignment-input-type"), {
    target: { value: "FREE" },
  });
  expect(
    screen.queryByTestId("assignment-billing-price"),
  ).not.toBeInTheDocument();
});
it("blank developer is rejected visibly without a POST", async () => {
  render(
    <MemoryRouter>
      <AssignmentForm
        projects={[project]}
        roles={["FIN"]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </MemoryRouter>,
  );
  await waitFor(() =>
    expect(screen.getByTestId("assignment-form-save")).not.toBeDisabled(),
  );
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  await screen.findByText("개발자를 골라 주세요.");
  expect(writes()).toHaveLength(0);
});
it("date initializers distinguish first open, project change and recommendation", () => {
  expect(assignmentInitialDates("2026-11-15", project)).toEqual({
    startDate: "2026-11-15",
    endDate: "2026-12-31",
  });
  expect(assignmentInitialDates("2026-11-15", project, true).startDate).toBe(
    "2026-01-01",
  );
  expect(
    assignmentInitialDates(
      "2026-11-15",
      { startDate: "2026-12-10", endDate: "2026-12-31" },
      false,
      "2026-12",
    ).startDate,
  ).toBe("2026-12-10");
  expect(assignmentPriceEditable(["PM"])).toBe(false);
  expect(assignmentPriceEditable(["SALES_LDR"])).toBe(true);
});
it("free support change dialog omits the rate-change command", async () => {
  row.inputTypeCode = "FREE";
  render(
    <MemoryRouter>
      <AssignmentDetail
        id={row.assignmentId}
        roles={["FIN"]}
        readOnly={false}
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />
    </MemoryRouter>,
  );
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  await waitFor(() =>
    expect(
      screen.queryByRole("option", { name: "단가 변경" }),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByTestId("assignment-change-dialog")).toBeInTheDocument();
});
it("readonly history hides correction and changing actions but still shows history", async () => {
  history[0] = {
    ...history[0],
    changeTypeCode: "EARLY",
    changeTypeName: "조기 철수",
  };
  render(
    <MemoryRouter>
      <AssignmentDetail
        id={row.assignmentId}
        roles={["FIN"]}
        readOnly
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />
    </MemoryRouter>,
  );
  await screen.findByText("변경 이력 (1)");
  expect(
    screen.queryByTestId("assignment-change-open"),
  ).not.toBeInTheDocument();
  const entry = await screen.findByTestId("assignment-history-1");
  expect(within(entry).getByText("조기 철수")).toBeInTheDocument();
  expect(
    within(entry).queryByRole("button", { name: "정정" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("assignment-history-filter-crct"));
  await screen.findByText("변경 이력이 없습니다.");
  expect(screen.getByTestId("assignment-history-filter-crct")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(
    screen.queryByTestId("assignment-correction-dialog"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("assignment-history-all"));
  expect(
    within(await screen.findByTestId("assignment-history-1")).queryByRole(
      "button",
      { name: "정정" },
    ),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("editable history opens correction from the history row rather than the filter", async () => {
  history[0] = {
    ...history[0],
    changeTypeCode: "EARLY",
    changeTypeName: "조기 철수",
  };
  render(
    <MemoryRouter>
      <AssignmentDetail
        id={row.assignmentId}
        roles={["FIN"]}
        readOnly={false}
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />
    </MemoryRouter>,
  );
  await screen.findByText("변경 이력 (1)");
  const entry = await screen.findByTestId("assignment-history-1");
  fireEvent.click(within(entry).getByRole("button", { name: "정정" }));
  expect(
    screen.getByTestId("assignment-correction-dialog"),
  ).toBeInTheDocument();
  expect(screen.getByTestId("assignment-correction-value")).toHaveValue(
    history[0].afterValue,
  );
  expect(writes()).toHaveLength(0);
});
it("shows the API instant through shared Korean time across the date boundary", async () => {
  history[0] = { ...history[0], changedAt: "2026-11-14T16:30:00.123456Z" };
  page();
  fireEvent.click(await screen.findByTestId("assignment-open-A-0001"));
  expect(await screen.findByTestId("assignment-history-1")).toHaveTextContent(
    "2026-11-15 01:30",
  );
  expect(writes()).toHaveLength(0);
});
it("saves anonymously with the unmodified detail version including fractional seconds", async () => {
  row.modifiedAt = "2026-11-14T16:30:00.123456Z";
  page();
  fireEvent.click(await screen.findByTestId("assignment-open-A-0001"));
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  await screen.findByRole("option", { name: "연장 가능성 변경" });
  fireEvent.change(screen.getByTestId("assignment-change-type"), {
    target: { value: "EXTP" },
  });
  fireEvent.change(screen.getByTestId("assignment-change-extension"), {
    target: { value: "H" },
  });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const [path, init] = writes()[0];
  expect(path).toBe(`/api/common/assignments/${row.assignmentId}/changes`);
  expect(JSON.parse(String(init?.body))).toMatchObject({
    modifiedAt: "2026-11-14T16:30:00.123456Z",
    changeTypeCode: "EXTP",
    newExtensionPossibilityCode: "H",
  });
  expect(screen.queryByText("로그인")).not.toBeInTheDocument();
});
it("prefills candidate pool price and sends explicit required creation defaults", async () => {
  render(
    <MemoryRouter>
      <AssignmentForm
        projects={[project]}
        candidate={{
          candidateId: "12",
          projectId: project.projectId,
          projectName: project.projectName,
          developerId: "developer",
          name: "김개발",
          jobCode: "BE",
          jobName: "백엔드 개발",
          startDate: "2026-11-15",
          endDate: "2026-12-31",
        }}
        roles={["FIN"]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </MemoryRouter>,
  );
  await waitFor(() =>
    expect(screen.getByTestId("assignment-billing-price")).toHaveValue(8000000),
  );
  expect(screen.getByTestId("assignment-monthly-mm")).toHaveValue(1);
  expect(screen.getByTestId("assignment-input-type")).toHaveValue("NEW");
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const payload = JSON.parse(String(writes()[0][1]?.body));
  expect(payload).toMatchObject({
    monthlyMm: 1,
    inputTypeCode: "NEW",
    billingPrice: 8000000,
  });
  expect(payload).not.toHaveProperty("candidateId");
  expect(payload).not.toHaveProperty("assignmentId");
});
it("project changes clear the contractual developer and its inherited price", async () => {
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.startsWith("/api/common/assignments/contract-developers"))
      return ok({
        items: [
          {
            developerId: "contract-person",
            name: "계약 인력",
            endDate: "2026-12-31",
            billingPrice: 9000000,
            overlappingAssignments: [],
          },
        ],
      });
    return original(url, init);
  });
  render(
    <MemoryRouter>
      <AssignmentForm
        projects={[
          project,
          { ...project, projectId: "P-0002", projectName: "다른 프로젝트" },
        ]}
        roles={["FIN"]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </MemoryRouter>,
  );
  await screen.findByRole("option", { name: "대체투입" });
  fireEvent.change(screen.getByTestId("assignment-developer"), {
    target: { value: "developer" },
  });
  await waitFor(() =>
    expect(screen.getByTestId("assignment-billing-price")).toHaveValue(8000000),
  );
  fireEvent.change(screen.getByTestId("assignment-input-type"), {
    target: { value: "SUB" },
  });
  await screen.findByRole("option", { name: "계약 인력 (철수 2026-12-31)" });
  fireEvent.change(screen.getByTestId("assignment-contract-developer"), {
    target: { value: "contract-person" },
  });
  expect(screen.getByTestId("assignment-billing-price")).toHaveValue(9000000);
  fireEvent.change(screen.getByTestId("assignment-project"), {
    target: { value: "P-0002" },
  });
  expect(screen.getByTestId("assignment-contract-developer")).toHaveValue("");
  expect(screen.getByTestId("assignment-billing-price")).toHaveValue(8000000);
  expect(screen.getByTestId("assignment-start-date")).toHaveValue(
    project.startDate,
  );
  expect(writes()).toHaveLength(0);
});

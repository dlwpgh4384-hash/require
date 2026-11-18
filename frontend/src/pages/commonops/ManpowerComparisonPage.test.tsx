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
import { MemoryRouter } from "react-router-dom";
import { ManpowerComparisonPage } from "./ManpowerComparisonPage";
import { ProjectLink } from "../projects/ProjectLink";
import type { Project } from "../projects/projects";
import type { ManpowerComparisonResult } from "./manpowerComparisons";

const viewport = vi.hoisted(() => ({ mobile: false, tablet: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
let request: ReturnType<typeof vi.fn>;
let data: ManpowerComparisonResult;
let failed: boolean;
let optionFailed: boolean;
let validationFields: { field: string; message: string }[];
let compareReply: (() => Promise<ReturnType<typeof ok>>) | undefined;
const project = (over: Partial<Project> = {}): Project => ({
  projectId: "P-selected",
  projectName: "선택 프로젝트",
  customerId: null,
  customerName: "(자체)",
  contractTypeCode: "IN",
  contractTypeName: "내부",
  startDate: "2026-01-01",
  endDate: "2026-12-31",
  salesPartId: null,
  salesPartName: null,
  workplace: null,
  statusCode: "ING",
  statusName: "진행",
  estimateAmount: null,
  contractAmount: 0,
  originalContractAmount: 0,
  budgetAmount: 0,
  budgetManualYn: "N",
  teamExpenseAmount: 0,
  projectExpenseAmount: 0,
  feature: null,
  pmName: null,
  trafficLight: null,
  canEdit: true,
  canChangeBudget: true,
  canChangeSalesPart: true,
  canSaveManpowerPlan: true,
  canLose: true,
  canStart: true,
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
  progress: { plannedProgressRate: null, actualProgressRate: null, steps: [] },
  ...over,
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
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  viewport.mobile = false;
  failed = false;
  optionFailed = false;
  validationFields = [
    { field: "differenceStatus", message: "차이를 확인해 주세요." },
  ];
  compareReply = undefined;
  data = {
    items: [
      {
        projectId: "P-selected",
        projectName: "선택 프로젝트",
        jobCode: "BE",
        jobName: "계획 직무",
        plannedMm: 3,
        actualMm: 0,
        differenceMm: -3,
        differenceStatus: "부족",
      },
      {
        projectId: "P-selected",
        projectName: "선택 프로젝트",
        jobCode: "FE",
        jobName: "투입 직무",
        plannedMm: 0,
        actualMm: 2,
        differenceMm: 2,
        differenceStatus: "초과",
      },
      {
        projectId: "P-selected",
        projectName: "선택 프로젝트",
        jobCode: "PM",
        jobName: "영점 직무",
        plannedMm: 0,
        actualMm: 0,
        differenceMm: 0,
        differenceStatus: "맞음",
      },
    ],
    totals: { plannedMm: 3, actualMm: 2, differenceMm: -1 },
  };
  request = vi.fn(async (url: string, _init?: RequestInit) => {
    const path = url.split("?")[0];
    if (path === "/api/common/manpower-comparisons") {
      if (compareReply) return compareReply();
      if (failed)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            meta: {},
            message: "조회하지 못했습니다.",
            errors: validationFields,
          }),
        };
      return ok(data);
    }
    if (path === "/api/common/projects") {
      if (optionFailed) throw new Error("조건 조회 실패");
      return ok({
        items: [
          project(),
          project({
            projectId: "P-across",
            projectName: "여러 해 프로젝트",
            startDate: "2025-01-01",
            endDate: "2027-12-31",
          }),
          project({
            projectId: "P-past",
            projectName: "지난 프로젝트",
            startDate: "2025-01-01",
            endDate: "2025-12-31",
          }),
        ],
        unassignedCount: 0,
      });
    }
    if (path === "/api/common/projects/P-selected") return ok(project());
    if (path.endsWith("/manpower-plan"))
      return ok({
        items: [
          {
            jobCode: "BE",
            jobName: "계획 직무",
            plannedMm: 3,
            actualMm: 0,
            differenceMm: -3,
          },
        ],
        canSave: true,
      });
    if (path.endsWith("/budget-rule"))
      return ok({ targetMarginRate: 20, budgetFactor: 0.8 });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});
const page = () =>
  render(
    <MemoryRouter>
      <ManpowerComparisonPage />
    </MemoryRouter>,
  );
const ready = () =>
  screen.findByTestId("manpower-comparison-row-P-selected-BE");
const calls = () =>
  request.mock.calls
    .map(([url]) => String(url))
    .filter((url) => url.startsWith("/api/common/manpower-comparisons?"));
const change = (id: string, value: string) =>
  fireEvent.change(screen.getByTestId(id), { target: { value } });
const expectStructure = () => {
  expect(screen.getByTestId("manpower-comparison-year")).toBeInTheDocument();
  expect(screen.getByTestId("manpower-comparison-filters")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "조회" })).toBeInTheDocument();
  expect(
    screen.getAllByRole("columnheader").map((header) => header.textContent),
  ).toEqual(["프로젝트", "직무", "계획 MM", "실제·예정 MM", "차이"]);
  expect(screen.getByTestId("manpower-comparison-totals")).toBeInTheDocument();
};

it("shows API rows, all three shared status colours and server totals without write actions", async () => {
  page();
  await ready();
  expectStructure();
  const shortage = within(
    screen.getByTestId("manpower-comparison-row-P-selected-BE"),
  );
  expect(shortage.getByText("부족 -3.00")).toHaveClass("ls-b-bad");
  expect(screen.getByText("초과 2.00")).toHaveClass("ls-b-warn");
  expect(screen.getByText("맞음 0.00")).toHaveClass("ls-b-ok");
  expect(screen.getByTestId("manpower-comparison-totals")).toHaveTextContent(
    "합계3.002.00-1.00",
  );
  expect(calls()).toHaveLength(1);
  expect(
    screen.queryByRole("button", { name: /저장|등록|다운로드/ }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText(/미충원/)).not.toBeInTheDocument();
});

it("keeps year and conditions as draft and applies all current values exactly once", async () => {
  page();
  await ready();
  await screen.findByRole("option", { name: "여러 해 프로젝트" });
  change("manpower-comparison-project", "P-across");
  change("manpower-comparison-difference", "부족");
  fireEvent.click(screen.getByRole("button", { name: "이전 해" }));
  expect(calls()).toHaveLength(1);
  expect(screen.getByTestId("manpower-comparison-project")).toHaveValue(
    "P-across",
  );
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() => expect(calls()).toHaveLength(2));
  const url = new URL(calls()[1], "http://test");
  expect(Object.fromEntries(url.searchParams)).toEqual({
    year: "2025",
    projectId: "P-across",
    differenceStatus: "부족",
  });
  expect(
    request.mock.calls.filter(([url]) =>
      String(url).startsWith("/api/common/projects?"),
    ),
  ).toHaveLength(0);
  expect(screen.getByText("지난 해 · 읽기 전용")).toBeInTheDocument();
});

it("filters project options locally and only clears selection when draft year no longer overlaps", async () => {
  page();
  await ready();
  await screen.findByRole("option", { name: "선택 프로젝트" });
  expect(
    screen.queryByRole("option", { name: "지난 프로젝트" }),
  ).not.toBeInTheDocument();
  change("manpower-comparison-project", "P-selected");
  fireEvent.click(screen.getByRole("button", { name: "이전 해" }));
  expect(screen.getByTestId("manpower-comparison-project")).toHaveValue("");
  expect(
    screen.getByRole("option", { name: "지난 프로젝트" }),
  ).toBeInTheDocument();
  expect(calls()).toHaveLength(1);
});

it("retains year, controls, headers and footer during loading", async () => {
  let finish!: (response: ReturnType<typeof ok>) => void;
  compareReply = () =>
    new Promise((resolve) => {
      finish = resolve;
    });
  page();
  expect(
    await screen.findByTestId("manpower-comparison-loading"),
  ).toBeVisible();
  expectStructure();
  await act(async () => finish(ok(data)));
  await ready();
});

it("retains empty structure and shows numeric zero totals, not an error", async () => {
  data = { items: [], totals: { plannedMm: 0, actualMm: 0, differenceMm: 0 } };
  page();
  expect(
    await screen.findByTestId("manpower-comparison-empty"),
  ).toHaveTextContent("조건에 맞는 결과가 없습니다.");
  expectStructure();
  expect(screen.getByTestId("manpower-comparison-totals")).toHaveTextContent(
    "합계0.000.000.00",
  );
  expect(
    screen.queryByTestId("manpower-comparison-error"),
  ).not.toBeInTheDocument();
});

it("shows field errors and never retains stale success after a failed query; retry uses applied not draft", async () => {
  page();
  await ready();
  failed = true;
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  expect(
    await screen.findByTestId("manpower-comparison-error"),
  ).toHaveTextContent("조회하지 못했습니다.");
  expect(
    screen.getByTestId("manpower-comparison-differenceStatus-error"),
  ).toHaveTextContent("차이를 확인해 주세요.");
  expectStructure();
  expect(
    screen.queryByTestId("manpower-comparison-row-P-selected-BE"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("manpower-comparison-empty"),
  ).not.toBeInTheDocument();
  change("manpower-comparison-difference", "초과");
  failed = false;
  fireEvent.click(screen.getByTestId("manpower-comparison-retry"));
  await ready();
  expect(calls().at(-1)).toBe(calls()[1]);
  expect(
    new URL(calls().at(-1)!, "http://test").searchParams.has(
      "differenceStatus",
    ),
  ).toBe(false);
});

it("ignores an aborted older response after a newer applied query", async () => {
  let finish!: (response: ReturnType<typeof ok>) => void;
  compareReply = () =>
    new Promise((resolve) => {
      finish = resolve;
    });
  page();
  await screen.findByTestId("manpower-comparison-loading");
  compareReply = undefined;
  change("manpower-comparison-difference", "부족");
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await ready();
  await act(async () =>
    finish(
      ok({ items: [], totals: { plannedMm: 0, actualMm: 0, differenceMm: 0 } }),
    ),
  );
  expect(
    screen.getByTestId("manpower-comparison-row-P-selected-BE"),
  ).toBeInTheDocument();
  expect(
    screen.queryByTestId("manpower-comparison-empty"),
  ).not.toBeInTheDocument();
});

it("surfaces project-option failure separately and retries without corrupting successful comparison", async () => {
  optionFailed = true;
  page();
  await ready();
  expect(
    await screen.findByTestId("manpower-comparison-options-error"),
  ).toBeInTheDocument();
  optionFailed = false;
  fireEvent.click(screen.getByTestId("manpower-comparison-options-retry"));
  await screen.findByRole("option", { name: "선택 프로젝트" });
  expect(calls()).toHaveLength(1);
});

it.each(["2024-12-31T16:00:00Z", "2028-01-01T00:00:00Z"])(
  "clamps initial Korean calendar year at supported boundaries: %s",
  async (time) => {
    vi.setSystemTime(new Date(time));
    page();
    await ready();
    const koreanYear = Number(
      new Intl.DateTimeFormat("en", {
        timeZone: "Asia/Seoul",
        year: "numeric",
      }).format(new Date(time)),
    );
    expect(new URL(calls()[0], "http://test").searchParams.get("year")).toBe(
      String(Math.min(2027, Math.max(2025, koreanYear))),
    );
  },
);

it("opens the exact selected project in real readonly detail even when its API grants editing", async () => {
  page();
  await ready();
  fireEvent.click(
    screen.getByTestId("manpower-comparison-project-P-selected-BE"),
  );
  const detail = within(await screen.findByTestId("project-detail"));
  await detail.findByRole("tab", { name: "인원 계획" });
  expect(
    request.mock.calls.some(
      ([url]) => url === "/api/common/projects/P-selected",
    ),
  ).toBe(true);
  expect(detail.queryByTestId("project-edit")).not.toBeInTheDocument();
  fireEvent.click(detail.getByRole("tab", { name: "인원 계획" }));
  await detail.findByTestId("project-job-be");
  expect(detail.queryByTestId("project-job-mm-be")).not.toBeInTheDocument();
  expect(detail.queryByTestId("project-job-add")).not.toBeInTheDocument();
  expect(
    detail.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(
    request.mock.calls.some(([, init]) =>
      ["POST", "PUT", "DELETE"].includes(init?.method ?? "GET"),
    ),
  ).toBe(false);
});

it("existing ProjectLink callers still default to editable project detail", async () => {
  render(
    <MemoryRouter>
      <ProjectLink
        projectId="P-selected"
        name="선택 프로젝트"
        testId="existing-project-link"
      />
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByTestId("existing-project-link"));
  const detail = within(await screen.findByTestId("project-detail"));
  fireEvent.click(await detail.findByRole("tab", { name: "인원 계획" }));
  expect(await detail.findByTestId("project-job-mm-be")).toBeInTheDocument();
  expect(detail.getByTestId("project-job-add")).toBeInTheDocument();
});

it.each(["year", "projectId", "differenceStatus"])(
  "places the API %s error below its condition without replacing the table skeleton",
  async (field) => {
    validationFields = [{ field, message: `${field} 조건을 확인해 주세요.` }];
    failed = true;
    page();
    expect(
      await screen.findByTestId(`manpower-comparison-${field}-error`),
    ).toHaveTextContent(`${field} 조건을 확인해 주세요.`);
    expectStructure();
    expect(
      screen.queryByTestId("manpower-comparison-empty"),
    ).not.toBeInTheDocument();
    expect(
      screen.getByTestId("manpower-comparison-totals"),
    ).not.toHaveTextContent("0.00");
  },
);

it("preserves tiny nonzero differences and the server classification rather than display-rounding to a match", async () => {
  data.items = [
    {
      ...data.items[0],
      plannedMm: 1,
      actualMm: 0.99999999,
      differenceMm: -0.00000001,
      differenceStatus: "부족",
    },
  ];
  data.totals = {
    plannedMm: 1,
    actualMm: 0.99999999,
    differenceMm: -0.00000001,
  };
  page();
  await ready();
  const row = within(
    screen.getByTestId("manpower-comparison-row-P-selected-BE"),
  );
  expect(row.getByText("부족 -0.00000001")).toHaveClass("ls-b-bad");
  expect(row.queryByText(/맞음/)).not.toBeInTheDocument();
  expect(screen.getByTestId("manpower-comparison-totals")).toHaveTextContent(
    "-0.00000001",
  );
});

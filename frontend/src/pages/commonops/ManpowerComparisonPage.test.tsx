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
import type {
  ManpowerComparisonResult,
  ManpowerComparisonRow,
} from "./manpowerComparisons";

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
const row = (over: Partial<ManpowerComparisonRow>): ManpowerComparisonRow => ({
  projectId: "P-selected",
  projectName: "선택 프로젝트",
  jobCode: "BE",
  jobName: "계획 직무",
  plannedMm: 0,
  actualMm: 0,
  differenceMm: 0,
  differenceStatus: "맞음",
  ...over,
});
const empty = (): ManpowerComparisonResult => ({
  items: [],
  totals: { plannedMm: 0, actualMm: 0, differenceMm: 0 },
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
  // 서버 순서와 다르게 준다: 화면 처음 정렬은 차이 오름차순(부족 큰 순)이다.
  data = {
    items: [
      row({
        jobCode: "FE",
        jobName: "투입 직무",
        plannedMm: 0,
        actualMm: 2,
        differenceMm: 2,
        differenceStatus: "초과",
      }),
      row({
        jobCode: "PM",
        jobName: "맞는 직무",
        plannedMm: 1,
        actualMm: 1,
        differenceMm: 0,
        differenceStatus: "맞음",
      }),
      row({
        jobCode: "BE",
        jobName: "계획 직무",
        plannedMm: 3,
        actualMm: 0,
        differenceMm: -3,
        differenceStatus: "부족",
      }),
    ],
    totals: { plannedMm: 4, actualMm: 3, differenceMm: -1 },
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
const ready = () => screen.findByTestId("list-row-P-selected-BE");
const calls = () =>
  request.mock.calls
    .map(([url]) => String(url))
    .filter((url) => url.startsWith("/api/common/manpower-comparisons?"));
const params = (url: string) =>
  Object.fromEntries(new URL(url, "http://test").searchParams);
const change = (id: string, value: string) =>
  fireEvent.change(screen.getByTestId(id), { target: { value } });
const bannerText =
  "계획 MM은 프로젝트 상세 [인원 계획] 탭에서 직무별로 입력한 값입니다. 2026년 몫 = 계획 MM × (2026년에 걸친 프로젝트 개월 수 ÷ 전체 개월 수). 줄을 누르면 그 프로젝트의 인원 계획이 열립니다.";
/** 자료와 상관없이 늘 보여야 하는 것: 연도 줄, 안내 띠, 조건, 도구 줄, 머리글, 합계 줄, 페이지. */
const expectStructure = () => {
  expect(screen.getByTestId("manpower-comparison-year")).toHaveTextContent(
    "그 해 1~12월 계획 MM과 실제(예정 포함) MM",
  );
  expect(screen.getByTestId("manpower-comparison-banner")).toHaveTextContent(
    bannerText,
  );
  expect(screen.getByTestId("manpower-comparison-filters")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "조회" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "초기화" })).toBeInTheDocument();
  expect(screen.getByTestId("list-count")).toBeInTheDocument();
  expect(screen.getByTestId("list-density")).toHaveTextContent("좁게 보기");
  expect(screen.getByTestId("list-download")).toHaveTextContent("엑셀 다운로드");
  expect(screen.queryByTestId("list-columns")).not.toBeInTheDocument();
  expect(
    screen.getAllByRole("columnheader").map((header) => header.textContent),
  ).toEqual(["프로젝트", "직무", "계획 MM", "실제·예정 MM", "차이"]);
  expect(screen.getByTestId("list-footer")).toHaveTextContent(/^합계/);
  expect(screen.getByTestId("list-pager")).toBeInTheDocument();
};
const order = () =>
  screen
    .getAllByTestId(/^list-row-/)
    .map((element) => element.getAttribute("data-testid"));

it("shows the mockup banner, toolbar, sorted rows, difference badges and plan/actual totals only", async () => {
  page();
  await ready();
  expectStructure();
  expect(screen.getByTestId("list-count")).toHaveTextContent("총 3건");
  // 처음 정렬: 차이 오름차순(부족 큰 순)
  expect(order()).toEqual([
    "list-row-P-selected-BE",
    "list-row-P-selected-PM",
    "list-row-P-selected-FE",
  ]);
  expect(screen.getByText("부족 3.00")).toHaveClass("ls-b-bad");
  expect(screen.getByText("초과 2.00")).toHaveClass("ls-b-warn");
  const matched = within(screen.getByTestId("list-cell-P-selected-PM-differenceMm"));
  expect(matched.getByText("맞음")).toHaveClass("ls-b-ok");
  expect(screen.getByTestId("list-cell-P-selected-PM-differenceMm")).toHaveTextContent(/^맞음$/);
  expect(screen.getByTestId("list-cell-P-selected-BE-plannedMm")).toHaveTextContent("3.00");
  expect(screen.getByTestId("list-footer-plannedMm")).toHaveTextContent("4.00");
  expect(screen.getByTestId("list-footer-actualMm")).toHaveTextContent("3.00");
  // 차이 합계는 시안에 없다
  expect(screen.getByTestId("list-footer-differenceMm")).toBeEmptyDOMElement();
  expect(calls()).toHaveLength(1);
  expect(screen.queryByRole("button", { name: /저장|등록/ })).not.toBeInTheDocument();
});

it("formats MM with two decimals and no thousands comma", async () => {
  data.items = [row({ plannedMm: 1234.5, actualMm: 0.4, differenceMm: -1234.1, differenceStatus: "부족" })];
  data.totals = { plannedMm: 1234.5, actualMm: 0.4, differenceMm: -1234.1 };
  page();
  await ready();
  expect(screen.getByTestId("list-cell-P-selected-BE-plannedMm")).toHaveTextContent(/^1234\.50$/);
  expect(screen.getByText("부족 1234.10")).toHaveClass("ls-b-bad");
  expect(screen.getByTestId("list-footer-plannedMm")).toHaveTextContent(/^1234\.50$/);
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
  expect(params(calls()[1])).toEqual({
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
  await waitFor(() =>
    expect(screen.getByTestId("manpower-comparison-banner")).toHaveTextContent(
      "2025년 몫 = 계획 MM × (2025년에 걸친",
    ),
  );
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

it("[초기화] clears the conditions and queries again right away (mockup freset)", async () => {
  page();
  await ready();
  change("manpower-comparison-difference", "부족");
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() => expect(calls()).toHaveLength(2));
  fireEvent.click(screen.getByRole("button", { name: "초기화" }));
  await waitFor(() => expect(calls()).toHaveLength(3));
  expect(params(calls()[2])).toEqual({ year: "2026" });
  expect(screen.getByTestId("manpower-comparison-difference")).toHaveValue("");
});

it("retains banner, controls, toolbar, headers and footer during loading", async () => {
  let finish!: (response: ReturnType<typeof ok>) => void;
  compareReply = () =>
    new Promise((resolve) => {
      finish = resolve;
    });
  page();
  expect(await screen.findByText("목록을 불러오는 중입니다.")).toBeVisible();
  expectStructure();
  expect(screen.getByTestId("list-footer-plannedMm")).toHaveTextContent("–");
  await act(async () => finish(ok(data)));
  await ready();
});

it("keeps every feature with zero rows and the empty state resets conditions with a new query", async () => {
  data = empty();
  page();
  expect(await screen.findByTestId("list-empty")).toHaveTextContent(
    "조건에 맞는 결과가 없습니다.",
  );
  expectStructure();
  expect(screen.getByTestId("list-count")).toHaveTextContent("총 0건");
  expect(screen.getByTestId("list-footer-plannedMm")).toHaveTextContent("0.00");
  expect(screen.getByTestId("list-footer-actualMm")).toHaveTextContent("0.00");
  expect(screen.queryByTestId("list-error")).not.toBeInTheDocument();
  change("manpower-comparison-difference", "초과");
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() => expect(calls()).toHaveLength(2));
  fireEvent.click(await screen.findByTestId("list-empty-reset"));
  await waitFor(() => expect(calls()).toHaveLength(3));
  expect(params(calls()[2])).toEqual({ year: "2026" });
});

it("shows field errors and never retains stale success after a failed query; retry uses applied not draft", async () => {
  page();
  await ready();
  failed = true;
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  expect(await screen.findByTestId("list-error")).toHaveTextContent(
    "조회하지 못했습니다.",
  );
  expect(
    screen.getByTestId("manpower-comparison-differenceStatus-error"),
  ).toHaveTextContent("차이를 확인해 주세요.");
  expect(screen.getByTestId("manpower-comparison-banner")).toBeInTheDocument();
  expect(screen.getByTestId("list-download")).toBeInTheDocument();
  expect(screen.queryByTestId("list-row-P-selected-BE")).not.toBeInTheDocument();
  expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
  change("manpower-comparison-difference", "초과");
  failed = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  await ready();
  expect(calls().at(-1)).toBe(calls()[1]);
  expect(params(calls().at(-1)!)).not.toHaveProperty("differenceStatus");
});

it("ignores an aborted older response after a newer applied query", async () => {
  let finish!: (response: ReturnType<typeof ok>) => void;
  compareReply = () =>
    new Promise((resolve) => {
      finish = resolve;
    });
  page();
  await screen.findByText("목록을 불러오는 중입니다.");
  compareReply = undefined;
  change("manpower-comparison-difference", "부족");
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await ready();
  await act(async () => finish(ok(empty())));
  expect(screen.getByTestId("list-row-P-selected-BE")).toBeInTheDocument();
  expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
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

it("header click sorts the whole result and the pager uses the page-size preference", async () => {
  data.items = Array.from({ length: 60 }, (_, index) =>
    row({
      jobCode: `J${String(index).padStart(2, "0")}`,
      jobName: `직무 ${index}`,
      plannedMm: index,
      actualMm: 0,
      differenceMm: -index,
      differenceStatus: index ? "부족" : "맞음",
    }),
  );
  page();
  await screen.findByTestId("list-row-P-selected-J59");
  expect(screen.getByTestId("list-range")).toHaveTextContent("1-50 / 60건");
  expect(order()[0]).toBe("list-row-P-selected-J59");
  fireEvent.click(screen.getByTestId("list-head-plannedMm"));
  expect(order()[0]).toBe("list-row-P-selected-J00");
  fireEvent.click(screen.getByTestId("list-page-2"));
  expect(screen.getByTestId("list-range")).toHaveTextContent("51-60 / 60건");
  expect(order()).toHaveLength(10);
  fireEvent.change(screen.getByTestId("list-page-size"), { target: { value: "20" } });
  expect(screen.getByTestId("list-range")).toHaveTextContent("1-20 / 60건");
});

it("row click opens the editable project detail on the [인원 계획] tab; the name link opens 기본 정보", async () => {
  page();
  await ready();
  fireEvent.click(screen.getByTestId("list-row-P-selected-BE"));
  const detail = within(await screen.findByTestId("project-detail"));
  expect(
    await detail.findByRole("tab", { name: "인원 계획", selected: true }),
  ).toBeInTheDocument();
  expect(await detail.findByTestId("project-job-mm-be")).toBeInTheDocument();
  expect(detail.getByTestId("project-plan-save")).toBeInTheDocument();
  expect(
    request.mock.calls.some(([url]) => url === "/api/common/projects/P-selected"),
  ).toBe(true);
  fireEvent.click(detail.getByRole("button", { name: "닫기" }));
  await waitFor(() =>
    expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument(),
  );
  fireEvent.click(screen.getByTestId("manpower-comparison-project-P-selected-BE"));
  const basic = within(await screen.findByTestId("project-detail"));
  expect(
    await basic.findByRole("tab", { name: "기본 정보", selected: true }),
  ).toBeInTheDocument();
});

it("saving the manpower plan or closing the detail reloads the comparison right away", async () => {
  page();
  await ready();
  fireEvent.click(screen.getByTestId("list-row-P-selected-BE"));
  const detail = within(await screen.findByTestId("project-detail"));
  const input = await detail.findByTestId("project-job-mm-be");
  fireEvent.change(input, { target: { value: "4" } });
  const before = calls().length;
  fireEvent.click(detail.getByTestId("project-plan-save"));
  await waitFor(() => expect(calls().length).toBeGreaterThan(before));
  expect(
    request.mock.calls.some(
      ([url, init]) =>
        String(url).endsWith("/manpower-plan") && init?.method === "PUT",
    ),
  ).toBe(true);
  expect(await screen.findByTestId("manpower-comparison-toast")).toHaveTextContent(
    "인원 계획을 저장했습니다.",
  );
  // 다시 읽는 동안에도 지금 줄은 그대로 둔다(깜빡임 없음)
  expect(screen.getByTestId("list-row-P-selected-BE")).toBeInTheDocument();
  const afterSave = calls().length;
  fireEvent.click(detail.getByRole("button", { name: "닫기" }));
  await waitFor(() => expect(calls().length).toBeGreaterThan(afterSave));
});

it("downloads the whole filtered result as Excel from the list toolbar", async () => {
  page();
  await ready();
  const original = request.getMockImplementation()!;
  const created = vi.fn(() => "blob:comparison");
  vi.stubGlobal(
    "URL",
    class extends URL {
      static createObjectURL = created;
      static revokeObjectURL = vi.fn();
    },
  );
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => {});
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    String(url).includes("/excel?")
      ? {
          ok: true,
          headers: new Headers({
            "Content-Type":
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "X-Total-Count": "3",
            "Content-Disposition": "attachment; filename*=UTF-8''%EC%9D%B8%EC%9B%90.xlsx",
          }),
          blob: async () => new Blob(["PK"]),
        }
      : original(url, init),
  );
  fireEvent.click(screen.getByTestId("list-download"));
  expect(await screen.findByTestId("manpower-comparison-toast")).toHaveTextContent(
    "엑셀 파일을 만들었습니다 (조회 결과 3건)",
  );
  const excel = request.mock.calls.map(([url]) => String(url)).find((url) => url.includes("/excel?"))!;
  expect(excel.startsWith("/api/common/manpower-comparisons/excel?")).toBe(true);
  expect(params(excel)).toEqual({ year: "2026" });
  expect(click).toHaveBeenCalledOnce();
  expect(created).toHaveBeenCalledOnce();
});

it("a failed Excel download shows the server message, not a completion toast", async () => {
  page();
  await ready();
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    String(url).includes("/excel?")
      ? {
          ok: false,
          status: 400,
          headers: new Headers({ "Content-Type": "application/json" }),
          json: async () => ({ success: false, message: "조건을 확인해 주세요." }),
        }
      : original(url, init),
  );
  fireEvent.click(screen.getByTestId("list-download"));
  expect(await screen.findByTestId("manpower-comparison-toast")).toHaveTextContent(
    "조건을 확인해 주세요.",
  );
  expect(screen.queryByText(/엑셀 파일을 만들었습니다/)).not.toBeInTheDocument();
});

it("mobile cards show project + difference badge and 「계획 x / 실제 y」", async () => {
  viewport.mobile = true;
  page();
  const card = within(await screen.findByTestId("list-card-P-selected-BE"));
  expect(card.getByText("선택 프로젝트")).toBeInTheDocument();
  expect(card.getByText("부족 3.00")).toHaveClass("ls-b-bad");
  expect(card.getByText("계획 직무")).toBeInTheDocument();
  expect(card.getByText("계획 3.00 / 실제 0.00")).toBeInTheDocument();
  expect(screen.getByTestId("list-count")).toHaveTextContent("총 3건");
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
  // 처음 탭은 예전처럼 기본 정보
  expect(
    await detail.findByRole("tab", { name: "기본 정보", selected: true }),
  ).toBeInTheDocument();
  fireEvent.click(await detail.findByRole("tab", { name: "인원 계획" }));
  expect(await detail.findByTestId("project-job-mm-be")).toBeInTheDocument();
  expect(detail.getByTestId("project-job-add")).toBeInTheDocument();
});

it.each(["year", "projectId", "differenceStatus"])(
  "places the API %s error below its condition and keeps the screen frame",
  async (field) => {
    validationFields = [{ field, message: `${field} 조건을 확인해 주세요.` }];
    failed = true;
    page();
    expect(
      await screen.findByTestId(`manpower-comparison-${field}-error`),
    ).toHaveTextContent(`${field} 조건을 확인해 주세요.`);
    expect(screen.getByTestId("manpower-comparison-banner")).toBeInTheDocument();
    expect(screen.getByTestId("manpower-comparison-filters")).toBeInTheDocument();
    expect(screen.getByTestId("list-count")).toBeInTheDocument();
    expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
  },
);

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
import { PersonalCostProfitPage } from "./PersonalCostProfitPage";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import type {
  PersonalCostProfitResult,
  PersonalCostProfitRow,
} from "./personalCostProfitTypes";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
let request: ReturnType<typeof vi.fn>;
let result: PersonalCostProfitResult;
let failed: boolean;
let validation: boolean;
const row = (): PersonalCostProfitRow => ({
  developerId: "D1",
  developerName: "김개발",
  affiliationName: "소속",
  positionName: "사원",
  employeeTypeName: "정규직",
  joinDate: "2026-01-01",
  operatingRate: 100,
  mm: 1.01,
  unitPrice: 0,
  monthlySalary: 1000,
  allowance: 20,
  companyInsurance: 10,
  retirementReserve: 80,
  personalCost: 1110,
  teamExpenses: 5,
  projectExpenses: 5,
  totalCost: 1120,
  operatingProfit: -1120,
  profitRate: null,
  mmOverOne: true,
  personalInsurance: 12,
  withholdingTax: 0,
  incomeTax: 24,
  netPay: 984,
  allocations: [
    {
      yearMonth: "202602",
      projectId: "P1",
      assignmentId: "PAID",
      projectName: "동일 프로젝트",
      jobName: "백엔드",
      mm: 0.5,
      unitPrice: 0,
      allocatedCost: 550,
      operatingProfit: -550,
    },
    {
      yearMonth: "202602",
      projectId: "P1",
      assignmentId: "FREE",
      projectName: "동일 프로젝트",
      jobName: "백엔드",
      mm: 0.51,
      unitPrice: 0,
      allocatedCost: 570,
      operatingProfit: -570,
    },
    {
      yearMonth: "202601",
      projectId: "IDLE",
      assignmentId: "IDLE",
      projectName: "남는 몫(대기 원가)",
      jobName: null,
      mm: 0,
      unitPrice: 0,
      allocatedCost: 0,
      operatingProfit: 0,
    },
  ],
});
beforeEach(() => {
  failed = false;
  validation = false;
  const person = row();
  result = {
    items: [person],
    totals: { totalCost: 1120, monthlySalary: 1000, personalCost: 1110 },
    summary: {
      totalPeople: 2,
      averageOperatingPeople: 0.5,
      averageEarningPeople: 0,
      averageUnitPrice: 0,
      averageCost: 1120,
      averageOperatingProfit: -1120,
      lossPeople: 1,
      operatingRate: 50,
      earningRate: 0,
    },
    contributions: [
      {
        jobCode: "BE",
        jobName: "백엔드",
        gradeCode: "MD",
        gradeName: "중급",
        people: 1,
        contributionPerPerson: -1120,
      },
    ],
  };
  request = vi.fn(async (path: string) => {
    if (path.startsWith("/api/common/user-preferences/"))
      return ok(
        {
          prefTypeCd: path.split("/").at(-2),
          prefKey: path.split("/").at(-1),
          found: false,
          prefVal: null,
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
    if (path.startsWith("/api/common/personal-cost-profits?")) {
      if (failed) throw new Error("network");
      if (validation)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            meta: {},
            message: "직무를 확인해 주세요.",
            errors: [
              { field: "jobCode", message: "사용할 수 없는 직무입니다." },
            ],
          }),
        };
      return ok(result);
    }
    if (path.includes("/code-groups/EMP_TYPE/"))
      return ok({ items: [{ code: "FT", codeName: "정규직", useYn: "Y" }] });
    if (path.includes("/code-groups/JOB/"))
      return ok({ items: [{ code: "BE", codeName: "백엔드", useYn: "Y" }] });
    if (path.startsWith("/api/common/clients?"))
      return ok({
        items: [{ clientId: "C1", clientName: "소속", useYn: "Y" }],
      });
    if (path === "/api/common/parts")
      return ok({ items: [{ partId: "PART", partName: "영업1", useYn: "Y" }] });
    if (path === "/api/common/developers/D1")
      return ok({
        developerId: "D1",
        name: "김개발",
        managementNumber: "M1",
        organizationName: "소속",
        useYn: "Y",
        employeeTypeName: "정규직",
        skillNames: [],
        workStatusName: "가동",
      });
    if (path.endsWith("/statuses"))
      return ok({
        items: [
          {
            statusId: "S1",
            statusName: "휴직",
            startDate: "2026-02-01",
            endDate: null,
          },
        ],
        canManageStatuses: true,
        canCancelPastRetirement: true,
      });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
const mount = () =>
  render(
    <MemoryRouter>
      <PersonalCostProfitPage />
    </MemoryRouter>,
  );
const ready = async () => {
  await screen.findByTestId("list-row-D1");
};
const query = () =>
  new URL(
    request.mock.calls
      .filter(([path]) =>
        String(path).startsWith("/api/common/personal-cost-profits?"),
      )
      .at(-1)![0],
    "https://example.invalid",
  ).searchParams;

it("renders the five summaries, exact year guidance, required table order and null profit rate without invented badges", async () => {
  mount();
  await ready();
  expect(screen.getByText("그 해 1월부터 지금까지 누계")).toBeVisible();
  expect(
    screen
      .getByTestId("personal-cost-profit-summary")
      .querySelectorAll("section"),
  ).toHaveLength(5);
  expect(screen.getByText(/월평균 가동 0.5명/)).toBeVisible();
  const headers = within(screen.getByTestId("personal-cost-profit-list"))
    .getAllByRole("columnheader")
    .map((header) => header.textContent?.replace(/ [▲▼]/g, ""));
  expect(headers).toEqual([
    "이름",
    "소속",
    "직위",
    "직원구분",
    "입사일",
    "가동률",
    "MM",
    "단가",
    "월 급여",
    "수당",
    "4대보험(회사)",
    "퇴직금 적립",
    "개인별 원가",
    "팀비",
    "프로젝트경비",
    "원가합계",
    "영업이익",
    "이익률",
  ]);
  expect(
    within(screen.getByTestId("list-row-D1")).getByText("1.0 초과"),
  ).toBeVisible();
  expect(
    within(screen.getByTestId("list-row-D1")).getByText("–"),
  ).toBeVisible();
  expect(
    screen.queryByText(/Infinity|NaN|잠정|작업중|마감/),
  ).not.toBeInTheDocument();
  expect(query().get("displayMode")).toBe("연누계");
  expect(query().get("baseMonth")).toBe("2");
});
it("keeps the year, conditions, list tools and headers for zero results", async () => {
  result.items = [];
  result.contributions = [];
  mount();
  await screen.findByTestId("list-empty");
  expect(screen.getByTestId("year-bar")).toBeVisible();
  expect(screen.getByTestId("list-search")).toBeVisible();
  expect(screen.getByTestId("list-download")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "이름" })).toBeVisible();
  expect(screen.getByLabelText("이름")).toBeVisible();
});
it("keeps contribution matrix folded initially with exactly four grades and missing cells shown as zero people", async () => {
  mount();
  await ready();
  const panel = screen.getByTestId("personal-cost-profit-contributions");
  expect(panel).not.toHaveAttribute("open");
  fireEvent.click(
    screen.getByTestId("personal-cost-profit-contributions-toggle"),
  );
  expect(
    within(panel)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["직무", "초급", "중급", "고급", "특급"]);
  expect(
    within(panel).getByRole("rowheader", { name: "백엔드" }),
  ).toBeVisible();
  expect(within(panel).getByText("-1,120 (1명)")).toBeVisible();
  expect(within(panel).getAllByText("– (0명)")).toHaveLength(3);
});
it("submits literal name and DB code filters, advanced OR-view and turnkey choice with no contract filter", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByLabelText("이름"), { target: { value: "김%" } });
  await waitFor(() =>
    expect(screen.getByLabelText("직무")).toHaveTextContent("백엔드"),
  );
  fireEvent.change(screen.getByLabelText("직무"), { target: { value: "BE" } });
  fireEvent.change(screen.getByLabelText("영업 파트"), {
    target: { value: "PART" },
  });
  fireEvent.click(screen.getByTestId("personal-advanced"));
  fireEvent.change(screen.getByLabelText("보기"), {
    target: { value: "동시 투입·1.0 초과만" },
  });
  fireEvent.change(screen.getByLabelText("턴키 단가"), {
    target: { value: "false" },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() => expect(query().get("name")).toBe("김%"));
  expect(query().get("jobCode")).toBe("BE");
  expect(query().get("salesPartId")).toBe("PART");
  expect(query().get("view")).toBe("동시 투입·1.0 초과만");
  expect(query().get("includeTurnkey")).toBe("false");
  expect(screen.queryByLabelText("계약유형")).not.toBeInTheDocument();
});
it("reset preserves period and columns while removing optional codes and restoring initial choices", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByLabelText("이전 해"));
  await waitFor(() => expect(query().get("year")).toBe("2025"));
  expect(query().get("baseMonth")).toBe("12");
  fireEvent.change(screen.getByLabelText("이름"), { target: { value: "김" } });
  fireEvent.click(screen.getByTestId("list-reset"));
  await waitFor(() => expect(screen.getByLabelText("이름")).toHaveValue(""));
  expect(query().get("year")).toBe("2025");
  expect(query().get("view")).toBe("전체");
});
it("display modes round trip as GET-only queries", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByLabelText("표시 방식"), {
    target: { value: "월평균" },
  });
  await waitFor(() => expect(query().get("displayMode")).toBe("월평균"));
  fireEvent.change(screen.getByLabelText("표시 방식"), {
    target: { value: "연누계" },
  });
  await waitFor(() => expect(query().get("displayMode")).toBe("연누계"));
  expect(
    request.mock.calls.every(
      ([, init]) => !init?.method || init.method === "GET",
    ),
  ).toBe(true);
});
it("individual and all-project expansion preserve paid/free identity and idle rows", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("personal-expand-D1"));
  expect(
    screen.getByTestId("personal-allocation-202602-P1-PAID"),
  ).toBeVisible();
  expect(
    screen.getByTestId("personal-allocation-202602-P1-FREE"),
  ).toBeVisible();
  expect(screen.getByText("남는 몫(대기 원가)")).toBeVisible();
  fireEvent.click(screen.getByTestId("personal-expand-all"));
  expect(
    screen.queryByTestId("personal-cost-profit-allocations"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("personal-expand-all"));
  expect(screen.getByTestId("personal-cost-profit-allocations")).toBeVisible();
});
it("does not offer sensitive column headings or column options when policy omits their wire fields", async () => {
  delete result.items[0].monthlySalary;
  delete result.items[0].personalCost;
  delete result.totals.monthlySalary;
  delete result.totals.personalCost;
  mount();
  await ready();
  expect(
    screen.queryByRole("columnheader", { name: "월 급여" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "개인별 원가" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("list-columns"));
  expect(
    screen.queryByRole("checkbox", { name: "월 급여" }),
  ).not.toBeInTheDocument();
});
it("shows upload reference columns only after column selection and passes their keys in requests", async () => {
  mount();
  await ready();
  expect(
    screen.queryByRole("columnheader", { name: "근로소득세" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("list-columns"));
  fireEvent.click(screen.getByRole("checkbox", { name: "근로소득세" }));
  await waitFor(() => expect(query().get("columns")).toContain("incomeTax"));
  await ready();
  expect(
    screen.getByRole("columnheader", { name: "근로소득세" }),
  ).toBeVisible();
});
it("failed requests keep the condition skeleton and recover through the list retry", async () => {
  failed = true;
  mount();
  await screen.findByTestId("list-error");
  expect(screen.getByLabelText("이름")).toBeVisible();
  failed = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  await ready();
});
it("shows field-local errors and focuses the offending DB option", async () => {
  validation = true;
  mount();
  expect(await screen.findByText("사용할 수 없는 직무입니다.")).toBeVisible();
  expect(screen.getByLabelText("직무")).toHaveFocus();
});
it("developer detail is opt-in read-only and keeps GET tabs without mutation controls", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("list-row-D1"));
  const detail = await screen.findByTestId("developer-detail");
  await waitFor(() =>
    expect(within(detail).getByText("M1 · 소속 · 정규직")).toBeVisible(),
  );
  expect(
    within(detail).queryByRole("button", { name: "수정" }),
  ).not.toBeInTheDocument();
  expect(
    within(detail).queryByRole("button", { name: "사용 중지" }),
  ).not.toBeInTheDocument();
  fireEvent.click(within(detail).getByRole("tab", { name: /근무 상태/ }));
  expect(
    within(detail).queryByRole("button", { name: /등록|취소|변경/ }),
  ).not.toBeInTheDocument();
});
it("default developer detail preserves the existing UI-02 edit and stop callbacks", async () => {
  const onEdit = vi.fn(),
    onStop = vi.fn();
  render(
    <DeveloperDetail
      developerId="D1"
      onClose={vi.fn()}
      onEdit={onEdit}
      onStop={onStop}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
    />,
  );
  const detail = await screen.findByTestId("developer-detail");
  await waitFor(() =>
    expect(within(detail).getByRole("button", { name: "수정" })).toBeEnabled(),
  );
  fireEvent.click(within(detail).getByRole("button", { name: "수정" }));
  expect(onEdit).toHaveBeenCalledWith(
    expect.objectContaining({ developerId: "D1" }),
  );
  fireEvent.click(within(detail).getByRole("button", { name: "사용 중지" }));
  expect(onStop).toHaveBeenCalledWith(
    expect.objectContaining({ developerId: "D1" }),
  );
});

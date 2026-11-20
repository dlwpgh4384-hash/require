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
import { RateRenegotiationPage } from "./RateRenegotiationPage";
import type { RateRenegotiationResult } from "./rateRenegotiations";
import { downloadFile } from "../../shared/list/download";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
vi.mock("../../shared/list/download", () => ({
  downloadFile: vi.fn().mockResolvedValue("대상.xlsx"),
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data, meta: {} }),
});
let request: ReturnType<typeof vi.fn>;
let result: RateRenegotiationResult;
let failure: "network" | "validation" | null;
let nullOptions: boolean;
beforeEach(() => {
  vi.mocked(downloadFile).mockClear();
  failure = null;
  nullOptions = false;
  result = {
    baseYearMonth: "2026-02",
    appliedIncreaseRate: 5,
    items: [
      {
        assignmentId: "assignment-a",
        developerId: "developer-a",
        developerName: "김개발",
        employeeTypeName: "계약직",
        projectName: "업무 시스템",
        renewalDate: "2026-03-03",
        daysUntilRenewal: 30,
        monthlyMm: 0.5,
        currentUnitPrice: 1000000,
        monthlyCost: 450000,
        profitRate: 10,
        targetUnitPrice: 1130000,
        requiredIncreaseRate: 13,
        annualProfitIncrease: 600000,
      },
    ],
    summary: {
      targetCount: 1,
      averageProfitRate: 10,
      targetProfitRate: 20,
      assumedAnnualProfitIncrease: 600000,
      targetAnnualProfitIncrease: 1560000,
    },
    totalAnnualProfitIncrease: 600000,
    formulaHelp: {
      renewalDate: "DB 갱신 정의",
      monthlyCost: "DB 월원가 정의",
      profitRate: "DB 이익률 정의",
      targetUnitPrice: "DB 목표단가 정의",
      requiredIncreaseRate: "DB 필요인상률 정의",
      annualProfitIncrease: "DB 연이익 정의",
    },
  };
  request = vi.fn(async (path: string) => {
    if (path.startsWith("/api/common/rate-renegotiations?")) {
      if (failure === "network") throw new Error("network");
      if (failure === "validation")
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            meta: {},
            message: "조회 조건을 확인해 주세요.",
            errors: [
              { field: "renewalMonths", message: "갱신 시점을 확인해 주세요." },
            ],
          }),
        };
      const rate = Number(
        new URL(path, "https://test.invalid").searchParams.get("increaseRate"),
      );
      return ok({ ...result, appliedIncreaseRate: rate });
    }
    if (path.includes("/user-preferences/"))
      return ok({ found: false, prefVal: null, modDttm: null });
    if (path.includes("/code-groups/"))
      return ok({
        items: nullOptions
          ? null
          : [{ code: "CT", codeName: "계약직", useYn: "Y" }],
      });
    if (path === "/api/common/projects")
      return ok({
        items: nullOptions
          ? null
          : [{ projectId: "project-a", projectName: "업무 시스템" }],
      });
    if (path === "/api/common/developers/developer-a")
      return ok({
        developerId: "developer-a",
        name: "김개발",
        managementNumber: "M-A",
        useYn: "Y",
        employeeTypeName: "계약직",
        skillNames: [],
      });
    return ok({ items: [], canManageStatuses: false });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
const mount = () =>
  render(
    <MemoryRouter>
      <RateRenegotiationPage />
    </MemoryRouter>,
  );
const ready = () => screen.findByTestId("list-row-assignment-a");
it("renders UI-32 without a second title, four server KPIs and eleven columns", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("rate-renegotiation-page")).toHaveAttribute(
    "data-screen-id",
    "UI-32",
  );
  expect(
    screen.queryByRole("heading", { name: "단가 재협상 대상" }),
  ).not.toBeInTheDocument();
  expect(screen.getByLabelText("갱신 시점")).toHaveValue("6");
  expect(screen.getByLabelText("이익률 미만")).toHaveValue("15");
  expect(screen.getByLabelText("인상률")).toHaveValue(5);
  expect(screen.getByTestId("rate-summary").children).toHaveLength(4);
  expect(screen.getByTestId("rate-average")).toHaveTextContent("10.0%");
  expect(screen.getByTestId("rate-average")).toHaveTextContent("20.0%");
  expect(
    within(screen.getByTestId("rate-list")).getAllByRole("columnheader"),
  ).toHaveLength(11);
  expect(
    screen.getByTestId("list-footer-currentUnitPrice"),
  ).toBeEmptyDOMElement();
  expect(screen.getByTestId("list-footer-profitRate")).toBeEmptyDOMElement();
  expect(
    screen.getByTestId("list-footer-annualProfitIncrease"),
  ).not.toBeEmptyDOMElement();
  expect(
    screen
      .getByTestId("list-cell-assignment-a-renewalDate")
      .querySelector(".ls-b-bad"),
  ).not.toBeNull();
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
});
it("keeps the skeleton, null average and export CTA for zero rows and null option lists", async () => {
  nullOptions = true;
  result.items = [];
  result.summary.targetCount = 0;
  result.summary.averageProfitRate = null;
  result.summary.assumedAnnualProfitIncrease = 0;
  result.summary.targetAnnualProfitIncrease = 0;
  result.totalAnnualProfitIncrease = 0;
  mount();
  await screen.findByTestId("list-empty");
  expect(screen.getByTestId("rate-average")).toHaveTextContent("–");
  expect(screen.getByTestId("list-head-monthlyCost")).toBeInTheDocument();
  expect(screen.getByTestId("list-search")).toBeEnabled();
  expect(screen.getAllByRole("button", { name: /엑셀 다운로드/ })).toHaveLength(
    1,
  );
});
it("shows a network error instead of empty data and retries the same conditions", async () => {
  failure = "network";
  mount();
  await screen.findByTestId("rate-error");
  expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
  failure = null;
  fireEvent.click(screen.getByTestId("rate-retry"));
  await ready();
});
it("puts server validation under its condition and focuses the first invalid control", async () => {
  mount();
  await ready();
  failure = "validation";
  fireEvent.click(screen.getByTestId("list-search"));
  expect(
    await screen.findByText("갱신 시점을 확인해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("갱신 시점")).toHaveFocus();
});
it.each([
  [-1, 0],
  [51, 50],
  [0, 0],
  [50, 50],
])(
  "clamps assumption %s to %s and calls the read-only query",
  async (input, expected) => {
    mount();
    await ready();
    fireEvent.change(screen.getByLabelText("인상률"), {
      target: { value: String(input) },
    });
    await waitFor(() =>
      expect(
        request.mock.calls.some(([path]) =>
          String(path).includes(`increaseRate=${expected}`),
        ),
      ).toBe(true),
    );
    expect(screen.getByLabelText("인상률")).toHaveValue(expected);
    expect(
      request.mock.calls.every(
        ([, init]) =>
          !init ||
          !(init as RequestInit).method ||
          (init as RequestInit).method === "GET",
      ),
    ).toBe(true);
  },
);
it("shows the mockup's short formula box with the target rate and no long DB text", async () => {
  mount();
  await ready();
  const box = screen.getByTestId("rate-formulas");
  const rows = within(box)
    .getAllByRole("row")
    .slice(1)
    .map((row) => row.textContent);
  expect(rows).toHaveLength(6);
  expect(rows[3]).toContain("월 원가 ÷ MM ÷ (1 − 20%)");
  expect(rows[1]).toContain("급여 업로드 전이면 개발자 풀 월 급여로 계산");
  expect(box).toHaveTextContent(
    "턴키는 계약금액 단위로 협상하므로 빠집니다. 줄을 누르면 그 사람의 상세가 열립니다.",
  );
  expect(box).not.toHaveTextContent("DB 갱신 정의");
  expect(screen.queryByTestId("rate-help-renewal-date")).not.toBeInTheDocument();
});
it("opens the clicked developer read-only and retains conditions, assumption and hidden columns on close", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByLabelText("갱신 시점"), {
    target: { value: "3" },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  await ready();
  fireEvent.change(screen.getByLabelText("인상률"), { target: { value: "8" } });
  await ready();
  fireEvent.click(screen.getByTestId("list-columns"));
  fireEvent.click(screen.getByTestId("list-column-monthlyCost"));
  fireEvent.click(screen.getByTestId("list-row-assignment-a"));
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([path]) => path === "/api/common/developers/developer-a",
      ),
    ).toBe(true),
  );
  for (const suffix of ["assignments", "statuses", "change-history"]) {
    expect(
      request.mock.calls.some(
        ([path]) => path === `/api/common/developers/developer-a/${suffix}`,
      ),
    ).toBe(true);
  }
  expect(
    screen.queryByRole("button", { name: "수정" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(screen.getByLabelText("갱신 시점")).toHaveValue("3");
  expect(screen.getByLabelText("인상률")).toHaveValue(8);
  expect(screen.queryByTestId("list-head-monthlyCost")).not.toBeInTheDocument();
});
it("exports only the visible columns with the applied rather than unsubmitted filters", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("list-columns"));
  fireEvent.click(screen.getByTestId("list-column-monthlyCost"));
  fireEvent.change(screen.getByLabelText("갱신 시점"), {
    target: { value: "3" },
  });
  fireEvent.click(screen.getByTestId("list-download"));
  await waitFor(() => expect(downloadFile).toHaveBeenCalled());
  const query = new URL(
    String(vi.mocked(downloadFile).mock.calls.at(-1)![0]),
    "https://test.invalid",
  ).searchParams;
  expect(query.get("renewalMonths")).toBe("6");
  expect(query.get("increaseRate")).toBe("5");
  expect(query.getAll("columns")).not.toContain("monthlyCost");
  expect(query.getAll("columns")).toHaveLength(10);
});
it.each([
  [0, "bad"],
  [30, "bad"],
  [31, "warn"],
  [92, "warn"],
  [93, "neutral"],
])("marks day %s with the correct renewal urgency", async (days, kind) => {
  result.items[0].daysUntilRenewal = Number(days);
  mount();
  await ready();
  expect(
    screen
      .getByTestId("list-cell-assignment-a-renewalDate")
      .querySelector(`.ls-b-${kind}`),
  ).not.toBeNull();
});
it("keeps controls and headers while loading and ignores an older assumption response", async () => {
  const original = request.getMockImplementation()!;
  let release!: (value: ReturnType<typeof ok>) => void;
  const pending = new Promise<ReturnType<typeof ok>>((resolve) => {
    release = resolve;
  });
  request.mockImplementation((path: string, init?: RequestInit) => {
    if (
      path.startsWith("/api/common/rate-renegotiations?") &&
      path.includes("increaseRate=5")
    )
      return pending;
    return original(path, init);
  });
  mount();
  expect(screen.getByTestId("list-head-monthlyCost")).toBeInTheDocument();
  expect(screen.getByTestId("list-search")).toBeEnabled();
  expect(screen.getByTestId("list-download")).toBeDisabled();
  expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("인상률"), { target: { value: "8" } });
  await ready();
  expect(
    screen.getByTestId("list-head-annualProfitIncrease"),
  ).toHaveTextContent("8%");
  await act(async () => {
    release(ok(result));
  });
  expect(
    screen.getByTestId("list-head-annualProfitIncrease"),
  ).toHaveTextContent("8%");
});
it("shows how many people were calculated from the developer pool salary before payroll upload", async () => {
  mount();
  await ready();
  expect(screen.queryByTestId("rate-salary-estimated")).not.toBeInTheDocument();
  cleanup();
  result = { ...result, salaryEstimatedDeveloperIds: ["developer-a"] };
  mount();
  await ready();
  expect(screen.getByTestId("rate-salary-estimated")).toHaveTextContent(
    "2026-02 급여 업로드 전: 1명은 개발자 풀 월 급여로 계산한 값입니다.",
  );
});

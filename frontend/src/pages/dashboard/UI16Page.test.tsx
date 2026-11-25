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
import { MemoryRouter, useLocation } from "react-router-dom";
import { UI16Page } from "./UI16Page";
import * as api from "./dashboard";
import { dashboardResult } from "./dashboardTestData";
import { ProjectRequestError } from "../projects/projects";

const period = vi.hoisted(() => ({ latest: "202611" }));
vi.mock("../../shared/time", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return { ...actual, koreanToday: () => "2026-11-25" };
});
vi.mock("../userpreferences/useScreenPeriod", () => ({
  useScreenPeriod: () => ({
    state: {
      status: "ready",
      basis: {
        latestYm: period.latest,
        cashBaseYm: "202601",
        closedMonths: ["202610"],
      },
    },
    value: { year: 2026, month: 11 },
    setValue: vi.fn(),
  }),
}));
vi.mock("./dashboard", () => ({
  getCommonDashboard: vi.fn(),
  getCommonDashboardExcel: vi.fn(),
}));
function Location() {
  return <output data-testid="location">{useLocation().pathname}</output>;
}
function mount() {
  return render(
    <MemoryRouter>
      <UI16Page />
      <Location />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  period.latest = "202611";
  vi.mocked(api.getCommonDashboard).mockImplementation(async (month) =>
    dashboardResult(month),
  );
  vi.mocked(api.getCommonDashboardExcel).mockResolvedValue(37);
});
afterEach(cleanup);
it("renders real API regions, unknown values, empty states and only a download action", async () => {
  mount();
  await screen.findByTestId("dashboard-revenue");
  expect(screen.getByTestId("dashboard-utilization")).toHaveTextContent("–");
  expect(screen.getByTestId("dashboard-close-count")).toHaveTextContent(
    "5 / 6",
  );
  expect(screen.getByTestId("dashboard-manpower-2027-04")).toBeVisible();
  expect(screen.getByTestId("dashboard-cash-2027-01")).toHaveTextContent("–");
  expect(screen.getByText("프로젝트 경보가 없습니다.")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: /저장|등록|업로드|삭제/ }),
  ).not.toBeInTheDocument();
  expect(api.getCommonDashboard).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByTestId("dashboard-todo-0"));
  expect(screen.getByTestId("location")).toHaveTextContent("/screens/UI-13");
});
it("reports exact response count and prevents duplicate exports while pending", async () => {
  let finish!: (value: number) => void;
  vi.mocked(api.getCommonDashboardExcel).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  mount();
  await screen.findByTestId("dashboard-revenue");
  const button = screen.getByTestId("dashboard-excel");
  fireEvent.click(button);
  fireEvent.click(button);
  expect(button).toBeDisabled();
  expect(api.getCommonDashboardExcel).toHaveBeenCalledTimes(1);
  await act(async () => finish(37));
  expect(
    await screen.findByText("엑셀 파일을 만들었습니다 (조회 결과 37건)"),
  ).toBeVisible();
  expect(api.getCommonDashboardExcel).toHaveBeenCalledWith("2026-11");
});
it("renders field errors and allows a clean retry without a false export success", async () => {
  vi.mocked(api.getCommonDashboard).mockRejectedValueOnce(
    new ProjectRequestError("기준월 오류", [
      { field: "yearMonth", message: "기간 확인" },
    ]),
  );
  mount();
  expect(await screen.findByTestId("dashboard-month-error")).toHaveTextContent(
    "기간 확인",
  );
  expect(screen.getByTestId("dashboard-excel")).toBeDisabled();
  fireEvent.click(screen.getByTestId("dashboard-retry"));
  await screen.findByTestId("dashboard-revenue");
  expect(screen.queryByTestId("dashboard-month-error")).not.toBeInTheDocument();
  vi.mocked(api.getCommonDashboardExcel).mockRejectedValueOnce(
    new Error("내려받기 실패"),
  );
  fireEvent.click(screen.getByTestId("dashboard-excel"));
  expect(await screen.findByText("내려받기 실패")).toBeVisible();
  expect(
    screen.queryByText(/엑셀 파일을 만들었습니다/),
  ).not.toBeInTheDocument();
});
it("honours the existing period lock with an explicit fallback notice", async () => {
  period.latest = "202610";
  mount();
  await screen.findByTestId("dashboard-revenue");
  expect(api.getCommonDashboard).toHaveBeenCalledWith(
    "2026-10",
    expect.any(AbortSignal),
  );
  expect(screen.getByText(/최근 허용월을 표시합니다/)).toBeVisible();
});
it("a delayed old selection cannot replace a later period", async () => {
  let finishOld!: (value: api.DashboardResult) => void;
  vi.mocked(api.getCommonDashboard).mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finishOld = resolve;
      }),
  );
  mount();
  await waitFor(() => expect(api.getCommonDashboard).toHaveBeenCalledTimes(1));
  const selector = within(screen.getByTestId("dashboard-page")).getByRole(
    "combobox",
  );
  const option = within(selector)
    .getAllByRole("option")
    .find((o) => o.textContent?.includes("10월"));
  expect(option).toBeDefined();
  fireEvent.change(selector, {
    target: { value: (option as HTMLOptionElement).value },
  });
  await screen.findByTestId("dashboard-revenue");
  expect(screen.getByTestId("dashboard-basis")).toHaveTextContent("2026-10");
  await act(async () => finishOld(dashboardResult("2026-11")));
  expect(screen.getByTestId("dashboard-basis")).toHaveTextContent("2026-10");
});

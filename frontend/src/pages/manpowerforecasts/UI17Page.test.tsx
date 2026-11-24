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
import { UI17Page } from "./UI17Page";
import * as api from "./manpowerForecasts";
import { ProjectRequestError } from "../projects/projects";
import {
  ManpowerForecastWorkspaceProvider,
  ManpowerForecastWorkspace,
  ManpowerForecastTitleActions,
} from "./ManpowerForecastWorkspace";

vi.mock("./manpowerForecasts", () => ({
  getCommonManpowerForecasts: vi.fn(),
  getCommonManpowerForecastsExcel: vi.fn(),
}));
vi.mock("../../shared/time", async () => ({
  ...(await vi.importActual<typeof import("../../shared/time")>(
    "../../shared/time",
  )),
  koreanToday: () => "2026-11-15",
}));
const periodControl = vi.hoisted(() => ({ ready: true }));
vi.mock("../userpreferences/useScreenPeriod", async () => {
  const { useState } = await import("react");
  return {
    useScreenPeriod: () => {
      const [value, setValue] = useState({ year: 2026 });
      return {
        state: periodControl.ready
          ? {
              status: "ready",
              value,
              basis: {
                latestYm: "202611",
                cashBaseYm: "202401",
                closedMonths: [],
              },
            }
          : { status: "loading" },
        setValue,
      };
    },
  };
});
const result = (year = 2026): api.ManpowerForecastResult => ({
  months: [
    {
      month: `${year}-11`,
      supply: 2,
      demand: 3.25,
      difference: -1.25,
      unfilledDemand: 0.25,
    },
    {
      month: `${year}-12`,
      supply: 4.25,
      demand: 1,
      difference: 3.25,
      unfilledDemand: 0,
    },
  ],
  summary: {
    lastMonthSupply: 4.25,
    lastMonthDemand: 1,
    mostShortage: { jobName: "백엔드", maximumShortage: 1.25 },
  },
  thresholds: { shortageThreshold: 0.4, surplusThreshold: 1 },
});
beforeEach(() => {
  vi.clearAllMocks();
  periodControl.ready = true;
  vi.mocked(api.getCommonManpowerForecasts).mockResolvedValue(result());
  vi.mocked(api.getCommonManpowerForecastsExcel).mockResolvedValue(3);
});
afterEach(cleanup);

it("renders the three API KPIs, banner, grouped chart and read-only three-row table", async () => {
  render(<UI17Page />);
  await screen.findByText("백엔드");
  expect(
    screen.getByText("마지막 달 가용 인력").parentElement,
  ).toHaveTextContent("4.3명");
  expect(
    screen.getByText("마지막 달 필요 인력").parentElement,
  ).toHaveTextContent("1.0명");
  expect(screen.getByText("최대 1.3명 부족")).toBeVisible();
  expect(screen.getByText(/UI-28과 같은 계산/)).toBeVisible();
  expect(
    screen.getByRole("img", { name: "월별 가용 인력, 필요 인력 비교" }),
  ).toBeVisible();
  const table = screen.getByRole("table", { name: "월별 전체 인력" });
  expect(within(table).getAllByRole("row")).toHaveLength(4);
  expect(within(table).getByText("1.3명 부족")).toBeVisible();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /저장|등록|삭제/ }),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
});
it("does not request forecast before period metadata is ready and disables the download while loading", () => {
  periodControl.ready = false;
  render(<UI17Page />);
  expect(screen.getByTestId("manpower-forecast-loading")).toBeVisible();
  expect(screen.getByTestId("manpower-forecast-excel")).toBeDisabled();
  expect(api.getCommonManpowerForecasts).not.toHaveBeenCalled();
});
it("download is public and uses the returned count unchanged in the completion Toast", async () => {
  render(<UI17Page />);
  const download = screen.getByTestId("manpower-forecast-excel");
  await waitFor(() => expect(download).toBeEnabled());
  fireEvent.click(download);
  expect(
    await screen.findByText("엑셀 파일을 만들었습니다 (조회 결과 3건)"),
  ).toBeVisible();
  expect(api.getCommonManpowerForecastsExcel).toHaveBeenCalledWith(2026);
});
it("renders no-shortage messages instead of a job code", async () => {
  const data = result();
  data.summary.mostShortage = null;
  vi.mocked(api.getCommonManpowerForecasts).mockResolvedValue(data);
  render(<UI17Page />);
  expect(await screen.findByText("부족 없음")).toBeVisible();
  expect(screen.getByText("모든 직무가 남거나 맞음")).toBeVisible();
});
it("keeps labelled rows for empty historical projections and allows the three-row export", async () => {
  vi.mocked(api.getCommonManpowerForecasts).mockResolvedValue({
    ...result(),
    months: [],
    summary: { lastMonthSupply: 0, lastMonthDemand: 0, mostShortage: null },
  });
  render(<UI17Page />);
  expect(await screen.findByTestId("manpower-forecast-empty")).toBeVisible();
  expect(screen.getByRole("table")).toHaveTextContent("필요 인력가용 인력차이");
  expect(
    screen.queryByTestId("manpower-forecast-chart"),
  ).not.toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByTestId("manpower-forecast-excel")).toBeEnabled(),
  );
});
it("shows failed request and field validation, retains the year bar and retries", async () => {
  vi.mocked(api.getCommonManpowerForecasts).mockRejectedValueOnce(
    new ProjectRequestError("연도를 확인해 주세요.", [
      { field: "year", message: "선택 범위 밖입니다." },
    ]),
  );
  render(<UI17Page />);
  expect(await screen.findByText("선택 범위 밖입니다.")).toBeVisible();
  expect(screen.getByTestId("manpower-forecast-excel")).toBeDisabled();
  expect(screen.getByRole("group", { name: "조회 연도" })).toBeVisible();
  fireEvent.click(screen.getByTestId("manpower-forecast-retry"));
  expect(await screen.findByText("백엔드")).toBeVisible();
});
it("download failures produce a warning rather than a success Toast", async () => {
  vi.mocked(api.getCommonManpowerForecastsExcel).mockRejectedValue(
    new ProjectRequestError("파일을 만들지 못했습니다.", []),
  );
  render(<UI17Page />);
  await waitFor(() =>
    expect(screen.getByTestId("manpower-forecast-excel")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("manpower-forecast-excel"));
  expect(await screen.findByText("파일을 만들지 못했습니다.")).toBeVisible();
  expect(
    screen.queryByText(/엑셀 파일을 만들었습니다/),
  ).not.toBeInTheDocument();
});
it("discards late previous-year responses and bounds selection to cash base through next year", async () => {
  let first!: (value: api.ManpowerForecastResult) => void;
  vi.mocked(api.getCommonManpowerForecasts)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          first = resolve;
        }),
    )
    .mockResolvedValueOnce(result(2027));
  render(<UI17Page />);
  await waitFor(() =>
    expect(api.getCommonManpowerForecasts).toHaveBeenCalledTimes(1),
  );
  fireEvent.click(screen.getByRole("button", { name: "다음 해" }));
  await screen.findByTestId("manpower-forecast-bars-2027-11");
  expect(screen.getByRole("button", { name: "다음 해" })).toBeDisabled();
  await act(async () => first(result(2026)));
  expect(
    screen.queryByTestId("manpower-forecast-bars-2026-11"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("manpower-forecast-bars-2027-11")).toBeVisible();
  expect(
    vi.mocked(api.getCommonManpowerForecasts).mock.calls[0][1]?.aborted,
  ).toBe(true);
  fireEvent.click(screen.getByTestId("manpower-forecast-current-year"));
  await waitFor(() =>
    expect(screen.getByTestId("year-bar-year")).toHaveTextContent("2026년"),
  );
  fireEvent.click(screen.getByRole("button", { name: "이전 해" }));
  fireEvent.click(screen.getByRole("button", { name: "이전 해" }));
  expect(screen.getByRole("button", { name: "이전 해" })).toBeDisabled();
});
it("places a single download action in the shell slot and removes it on unmount", async () => {
  const view = render(
    <ManpowerForecastWorkspaceProvider>
      <ManpowerForecastTitleActions />
      <ManpowerForecastWorkspace />
    </ManpowerForecastWorkspaceProvider>,
  );
  await waitFor(() =>
    expect(screen.getByTestId("manpower-forecast-excel")).toBeEnabled(),
  );
  expect(
    within(screen.getByTestId("manpower-forecast-title-actions")).getAllByRole(
      "button",
    ),
  ).toHaveLength(1);
  expect(
    within(screen.getByTestId("manpower-forecast-page")).queryByTestId(
      "manpower-forecast-excel",
    ),
  ).not.toBeInTheDocument();
  view.unmount();
  expect(
    screen.queryByTestId("manpower-forecast-excel"),
  ).not.toBeInTheDocument();
});

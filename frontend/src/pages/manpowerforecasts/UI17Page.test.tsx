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

vi.mock("./manpowerForecasts", async () => ({
  ...(await vi.importActual<typeof import("./manpowerForecasts")>(
    "./manpowerForecasts",
  )),
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
  const supply = screen.getByTestId("manpower-forecast-kpi-supply");
  expect(within(supply).getByText("12월 가용 인력")).toBeVisible();
  expect(supply).toHaveTextContent("4.3명");
  expect(supply).toHaveTextContent("2026-12 · 대기 + 연장 안 될 몫");
  const demand = screen.getByTestId("manpower-forecast-kpi-demand");
  expect(within(demand).getByText("12월 필요 인력")).toBeVisible();
  expect(demand).toHaveTextContent("1.0명");
  expect(demand).toHaveTextContent(
    "영업 기회 확률 반영 · 그중 미충원 0.0명",
  );
  expect(screen.getByText("최대 1.3명 부족")).toBeVisible();
  const banner = screen.getByTestId("banner-info");
  expect(banner).toHaveTextContent(
    "이 화면의 숫자는 예측입니다. 인력 운영 보드의 '수요·공급'과 같은 계산입니다.",
  );
  expect(within(banner).getByText("예측")).toBeVisible();
  expect(banner.querySelector("svg")).not.toBeNull();
  expect(screen.getByTestId("year-bar")).toHaveTextContent(
    "그 해 남은 달의 가용·필요 예측",
  );
  expect(
    screen.queryByTestId("manpower-forecast-current-year"),
  ).not.toBeInTheDocument();
  expect(
    screen.getByTestId("manpower-forecast-excel").querySelector("svg"),
  ).not.toBeNull();
  expect(
    screen.getByRole("img", { name: "월별 가용 인력, 필요 인력 비교" }),
  ).toBeVisible();
  const table = screen.getByRole("table", { name: "월별 전체 인력" });
  expect(within(table).getAllByRole("row")).toHaveLength(4);
  expect(within(table).getAllByRole("row")[0]).toHaveTextContent(
    "구분2026-112026-12",
  );
  expect(screen.getByTestId("manpower-forecast-row-demand")).toHaveTextContent(
    "필요 인력3.3명1.0명",
  );
  expect(screen.getByTestId("manpower-forecast-row-supply")).toHaveTextContent(
    "가용 인력2.0명4.3명",
  );
  expect(within(table).getByText("1.3명 부족")).toHaveClass("bad");
  expect(within(table).getByText("3.3명 남음")).toHaveClass("warn");
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
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
it("shows no empty table rows while the forecast is loading or after a failure", async () => {
  let resolve!: (value: api.ManpowerForecastResult) => void;
  vi.mocked(api.getCommonManpowerForecasts).mockImplementationOnce(
    () =>
      new Promise((r) => {
        resolve = r;
      }),
  );
  const view = render(<UI17Page />);
  await waitFor(() =>
    expect(api.getCommonManpowerForecasts).toHaveBeenCalledTimes(1),
  );
  expect(screen.getByTestId("manpower-forecast-loading")).toBeVisible();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("manpower-forecast-note"),
  ).not.toBeInTheDocument();
  await act(async () => resolve(result()));
  expect(screen.getByRole("table", { name: "월별 전체 인력" })).toBeVisible();
  view.unmount();
  vi.mocked(api.getCommonManpowerForecasts).mockRejectedValueOnce(
    new Error("예측 조회 오류"),
  );
  render(<UI17Page />);
  expect(await screen.findByText("예측 조회 오류")).toBeVisible();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
it("writes the mockup note under the table with each month's unfilled demand", async () => {
  const data = result();
  data.months[0].unfilledDemand = 0;
  data.months[1].unfilledDemand = 2.05;
  vi.mocked(api.getCommonManpowerForecasts).mockResolvedValue(data);
  render(<UI17Page />);
  const note = await screen.findByTestId("manpower-forecast-note");
  expect(note).toHaveClass("ls-note");
  expect(note.textContent).toBe(
    "표는 직무를 나누지 않은 전체 사람 수입니다(MM 합). 차이 = 가용 − 필요, 빨간색은 부족(사람을 구해야 함), 주황색은 남음(잉여 인력)입니다. 필요 인력 = 진행 중 영업 기회(수주 확률 반영) + 진행 프로젝트 미충원(인원 계획의 계획 MM − 배정 MM을 다음 달부터 종료 달까지 나눈 값). 달별 미충원: 11월 0.0 · 12월 2.1명.",
  );
});
it("labels each month's bars with 필요 and 그중 미충원 above the group", async () => {
  render(<UI17Page />);
  const label = await screen.findByTestId("manpower-forecast-label-2026-11");
  expect(
    Array.from(label.querySelectorAll("tspan")).map((t) => t.textContent),
  ).toEqual(["필요 3.3", "그중 미충원 0.3"]);
  expect(label.getAttribute("style")).toContain("font-size: 11px");
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
it("discards late previous-year responses and bounds selection to this year through next year", async () => {
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
  fireEvent.click(screen.getByRole("button", { name: "이전 해" }));
  await waitFor(() =>
    expect(screen.getByTestId("year-bar-year")).toHaveTextContent("2026년"),
  );
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

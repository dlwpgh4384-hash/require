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
import { MemoryRouter, useLocation } from "react-router-dom";
import { UI28Page } from "./UI28Page";
import * as board from "./manpowerBoard";
import * as pipeline from "../salespipeline/salesPipeline";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import { getCommonCodes } from "../codegroups/codeGroups";
import { ProjectRequestError } from "../projects/projects";

vi.mock("./manpowerBoard", async () => ({
  ...(await vi.importActual<typeof import("./manpowerBoard")>(
    "./manpowerBoard",
  )),
  getCommonManpowerBoard: vi.fn(),
  getCommonManpowerBoardExcel: vi.fn(),
}));
vi.mock("../salespipeline/salesPipeline", async () => ({
  ...(await vi.importActual<typeof import("../salespipeline/salesPipeline")>(
    "../salespipeline/salesPipeline",
  )),
  getCommonSalesPipelineForecast: vi.fn(),
}));
vi.mock("../codegroups/codeGroups", () => ({ getCommonCodes: vi.fn() }));
vi.mock("../developers/DeveloperDetail", () => ({
  DeveloperDetail: vi.fn(({ onClose }: { onClose: () => void }) => (
    <div data-testid="existing-developer-detail">
      <button data-testid="detail-close" onClick={onClose}>
        닫기
      </button>
    </div>
  )),
}));
vi.mock("../../shared/time", async () => ({
  ...(await vi.importActual<typeof import("../../shared/time")>(
    "../../shared/time",
  )),
  koreanToday: () => "2026-11-15",
}));
vi.mock("../userpreferences/useScreenPeriod", async () => {
  const { useState } = await import("react");
  return {
    useScreenPeriod: () => {
      const [value, setValue] = useState({ year: 2026 });
      return {
        state: {
          status: "ready",
          basis: { latestYm: "202611", cashBaseYm: "202601", closedMonths: [] },
          value,
        },
        setValue,
      };
    },
  };
});
const result = (count = 1): board.ManpowerBoardResult => ({
  year: 2026,
  summary: {
    idleCount: 3,
    availableMm: 3.6,
    withdrawalCount: 4,
    highExtensionCount: 2,
    concurrentCount: 2,
    overAllocatedCount: 1,
    futureAvailableMm: 5.2,
  },
  totalCount: count,
  items: Array.from({ length: count }, (_, i) => ({
    developerId: `person-${i}`,
    name: `인력 ${i}`,
    jobName: "백엔드",
    cells: Array.from({ length: 12 }, (_, month) => ({
      month: `2026-${String(month + 1).padStart(2, "0")}`,
      statusName: month < 10 ? "–" : "일부 투입",
      totalMm: month < 10 ? 0 : 0.4,
      projects: month < 10 ? [] : [{ projectName: "프로젝트", monthlyMm: 0.4 }],
      extensionPossibilityName: null,
      winningProbability: null,
      tentative: false,
    })),
  })),
  monthTotals: Array.from({ length: 12 }, (_, i) => ({
    month: `2026-${String(i + 1).padStart(2, "0")}`,
    idleCount: i < 10 ? null : 3,
    availableMm: i < 10 ? null : 3.6,
  })),
});
function Location() {
  const location = useLocation();
  return (
    <p data-testid="location">
      {location.pathname}
      {location.search}
    </p>
  );
}
function mount() {
  return render(
    <MemoryRouter initialEntries={["/screens/UI-28"]}>
      <UI28Page />
      <Location />
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(result());
  vi.mocked(board.getCommonManpowerBoardExcel).mockResolvedValue(37);
  vi.mocked(getCommonCodes).mockResolvedValue({
    items: [{ code: "BE", codeName: "백엔드", useYn: "Y" }],
  } as Awaited<ReturnType<typeof getCommonCodes>>);
  vi.mocked(pipeline.getCommonSalesPipelineForecast).mockResolvedValue({
    months: [
      {
        month: "2026-12",
        demand: 4.123,
        supply: 1.023,
        projectRevenue: 7,
        weightedOpportunityRevenue: 3,
        allWonRevenue: 10,
      },
      {
        month: "2027-01",
        demand: 1.222,
        supply: 2.322,
        projectRevenue: 8,
        weightedOpportunityRevenue: 4,
        allWonRevenue: 12,
      },
      {
        month: "2027-02",
        demand: 2,
        supply: 2,
        projectRevenue: 9,
        weightedOpportunityRevenue: 4,
        allWonRevenue: 13,
      },
      {
        month: "2027-03",
        demand: 3,
        supply: 3,
        projectRevenue: 9,
        weightedOpportunityRevenue: 5,
        allWonRevenue: 14,
      },
      {
        month: "2027-04",
        demand: 4,
        supply: 4,
        projectRevenue: 9,
        weightedOpportunityRevenue: 6,
        allWonRevenue: 15,
      },
      {
        month: "2027-05",
        demand: 5,
        supply: 5,
        projectRevenue: 9,
        weightedOpportunityRevenue: 7,
        allWonRevenue: 16,
      },
    ],
    hiringReviews: [],
    opportunityRevenues: [],
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("renders four API-backed cards and navigates only to existing destinations without query filters", async () => {
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  const summary = screen.getByTestId("manpower-board-summary");
  expect(summary.children).toHaveLength(4);
  expect(summary).toHaveTextContent("남는 MM 3.60");
  fireEvent.click(screen.getByTestId("manpower-board-withdrawal"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    /^\/screens\/UI-05$/,
  );
  fireEvent.click(screen.getByTestId("manpower-board-concurrent"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    /^\/screens\/UI-12$/,
  );
});

it("keeps condition drafts until search, changes year immediately, and resets conditions without resetting year", async () => {
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  await screen.findAllByRole("option", { name: "백엔드" });
  const before = vi.mocked(board.getCommonManpowerBoard).mock.calls.length;
  fireEvent.change(screen.getByTestId("manpower-board-jobcode"), {
    target: { value: "BE" },
  });
  expect(board.getCommonManpowerBoard).toHaveBeenCalledTimes(before);
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(board.getCommonManpowerBoard).toHaveBeenLastCalledWith(
      expect.objectContaining({ year: 2026, jobCode: "BE" }),
      expect.any(AbortSignal),
    ),
  );
  const year = screen.getByTestId("period-year");
  fireEvent.change(year, { target: { value: "2027" } });
  await waitFor(() =>
    expect(board.getCommonManpowerBoard).toHaveBeenLastCalledWith(
      expect.objectContaining({ year: 2027 }),
      expect.any(AbortSignal),
    ),
  );
  fireEvent.click(screen.getByRole("button", { name: "초기화" }));
  await waitFor(() =>
    expect(board.getCommonManpowerBoard).toHaveBeenLastCalledWith(
      { year: 2027 },
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByTestId("manpower-board-jobcode")).toHaveValue("");
});

it("uses local paging with full totals and no extra board fetch", async () => {
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(result(51));
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  const calls = vi.mocked(board.getCommonManpowerBoard).mock.calls.length;
  fireEvent.click(screen.getByRole("button", { name: "2" }));
  await waitFor(() =>
    expect(
      screen.queryByTestId("manpower-board-person-person-0"),
    ).not.toBeInTheDocument(),
  );
  expect(board.getCommonManpowerBoard).toHaveBeenCalledTimes(calls);
  expect(screen.getByText("총 51명")).toBeInTheDocument();
  expect(
    screen.getByText("남는 MM (일부 투입 포함)").closest("tr"),
  ).toHaveTextContent("3.60");
});

it("opens only the selected existing readonly detail and preserves calendar filters on close", async () => {
  mount();
  await screen.findByTestId("manpower-board-detail-person-0");
  expect(DeveloperDetail).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("manpower-board-detail-person-0"));
  await screen.findByTestId("existing-developer-detail");
  const props = vi.mocked(DeveloperDetail).mock.calls.at(-1)?.[0];
  expect(props).toEqual({
    developerId: "person-0",
    readOnly: true,
    onClose: expect.any(Function),
  });
  const calls = vi.mocked(board.getCommonManpowerBoard).mock.calls.length;
  fireEvent.click(screen.getByTestId("detail-close"));
  expect(
    screen.queryByTestId("existing-developer-detail"),
  ).not.toBeInTheDocument();
  expect(board.getCommonManpowerBoard).toHaveBeenCalledTimes(calls);
});

it("isolates forecast conditions and displays exact returned month order and supply-demand difference", async () => {
  mount();
  await screen.findByTestId("manpower-board-detail-person-0");
  fireEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  const panel = await screen.findByTestId("manpower-board-forecast");
  await within(panel).findByText("2026-12");
  expect(pipeline.getCommonSalesPipelineForecast).toHaveBeenCalledWith();
  expect(
    within(within(panel).getAllByRole("row")[0])
      .getAllByRole("columnheader")
      .slice(1)
      .map((cell) => cell.textContent),
  ).toEqual(["2026-12", "2027-01", "2027-02", "2027-03", "2027-04", "2027-05"]);
  expect(
    screen.queryByTestId("manpower-board-gradecode"),
  ).not.toBeInTheDocument();
  expect(within(panel).getByText("3.10 MM 부족")).toHaveClass("board-shortage");
  expect(within(panel).getByText("1.10 MM 남음")).toHaveClass("board-surplus");
  expect(within(panel).getByText("4.12")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "달력" }));
  expect(screen.getByTestId("manpower-board-gradecode")).toBeInTheDocument();
});

it("shows forecast failures in its own panel without replacing calendar data", async () => {
  vi.mocked(pipeline.getCommonSalesPipelineForecast).mockRejectedValue(
    new Error("예측 조회 오류"),
  );
  mount();
  await screen.findByTestId("manpower-board-detail-person-0");
  fireEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  await screen.findByText("예측 조회 오류");
  fireEvent.click(screen.getByRole("tab", { name: "달력" }));
  expect(
    screen.getByTestId("manpower-board-person-person-0"),
  ).toBeInTheDocument();
});

it("keeps loading separate from empty and disables download until a result exists", async () => {
  vi.mocked(board.getCommonManpowerBoard).mockReturnValue(
    new Promise(() => undefined),
  );
  mount();
  expect(screen.getByTestId("manpower-board-loading")).toBeInTheDocument();
  expect(screen.queryByTestId("manpower-board-empty")).not.toBeInTheDocument();
  expect(screen.getByTestId("manpower-board-excel")).toBeDisabled();
});

it("keeps headers, summary, footer and filters when the API returns an empty roster", async () => {
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(result(0));
  mount();
  await screen.findByTestId("manpower-board-empty");
  expect(
    screen.getByRole("columnheader", { name: "12월" }),
  ).toBeInTheDocument();
  expect(screen.getByTestId("manpower-board-filters")).toBeInTheDocument();
  expect(screen.getByText("남는 MM (일부 투입 포함)")).toBeInTheDocument();
});

it("shows shared errors[].field errors rather than an empty successful calendar", async () => {
  vi.mocked(board.getCommonManpowerBoard).mockRejectedValue(
    new ProjectRequestError("연도 오류", [
      { field: "year", message: "허용 연도를 선택해 주세요." },
    ]),
  );
  mount();
  await screen.findByTestId("manpower-board-error");
  expect(screen.getByTestId("manpower-board-year-error")).toHaveTextContent(
    "허용 연도",
  );
  expect(screen.queryByTestId("manpower-board-empty")).not.toBeInTheDocument();
  expect(screen.getByTestId("manpower-board-excel")).toBeDisabled();
});

it("shows success Toast only after the blob download resolves and uses X-Total-Count", async () => {
  let complete!: (count: number) => void;
  vi.mocked(board.getCommonManpowerBoardExcel).mockReturnValue(
    new Promise((resolve) => {
      complete = resolve;
    }),
  );
  mount();
  await screen.findByTestId("manpower-board-detail-person-0");
  fireEvent.click(screen.getByTestId("manpower-board-excel"));
  expect(screen.queryByTestId("manpower-board-toast")).not.toBeInTheDocument();
  expect(screen.getByTestId("manpower-board-excel")).toBeDisabled();
  complete(37);
  await waitFor(() =>
    expect(screen.getByTestId("manpower-board-toast")).toHaveTextContent(
      "엑셀 파일을 만들었습니다 (조회 결과 37건)",
    ),
  );
});

it("shows warn and never success on download failure", async () => {
  vi.mocked(board.getCommonManpowerBoardExcel).mockRejectedValue(
    new Error("파일 생성 실패"),
  );
  mount();
  await screen.findByTestId("manpower-board-detail-person-0");
  fireEvent.click(screen.getByTestId("manpower-board-excel"));
  await waitFor(() =>
    expect(screen.getByTestId("manpower-board-toast")).toHaveTextContent(
      "파일 생성 실패",
    ),
  );
  expect(screen.getByTestId("manpower-board-toast")).not.toHaveTextContent(
    "만들었습니다",
  );
});

it("preserves tentative, retirement and excess classes and builds tooltips from the selected month only", async () => {
  const data = result();
  data.items[0].cells[10] = {
    month: "2026-11",
    statusName: "동시 투입",
    totalMm: 1.3,
    projects: [
      { projectName: "첫 프로젝트", monthlyMm: 0.7 },
      { projectName: "둘째 프로젝트", monthlyMm: 0.6 },
    ],
    extensionPossibilityName: "높음 / 낮음",
    winningProbability: null,
    tentative: false,
  };
  data.items[0].cells[11] = {
    month: "2026-12",
    statusName: "가배정",
    totalMm: 0,
    projects: [{ projectName: "영업 기회", monthlyMm: 0 }],
    extensionPossibilityName: null,
    winningProbability: 30,
    tentative: true,
  };
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(data);
  mount();
  const row = await screen.findByTestId("manpower-board-person-person-0");
  const excess = within(row).getByText("동시 투입").closest("td")!;
  expect(excess).toHaveClass("board-excess");
  expect(excess.title).toContain("첫 프로젝트 · 0.70 MM");
  expect(excess.title).toContain("합계 1.30 MM");
  expect(excess.title).not.toContain("영업 기회");
  expect(within(row).getByText("가배정").closest("td")).toHaveClass(
    "board-tentative",
  );
});

it.each(["퇴사", "휴직"])(
  "gives %s precedence over excess color without hiding raw project totals",
  async (statusName) => {
    const data = result();
    data.items[0].cells[11] = {
      month: "2026-12",
      statusName,
      totalMm: 1.3,
      projects: [{ projectName: "계약 프로젝트", monthlyMm: 1.3 }],
      extensionPossibilityName: null,
      winningProbability: null,
      tentative: false,
    };
    vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(data);
    mount();
    const row = await screen.findByTestId("manpower-board-person-person-0");
    const cell = within(row).getByText(statusName).closest("td")!;
    expect(cell).toHaveClass("board-ineligible");
    expect(cell).not.toHaveClass("board-excess");
    expect(cell).toHaveTextContent("합계 1.30 MM");
    expect(
      screen.queryByRole("button", { name: "저장" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "삭제" }),
    ).not.toBeInTheDocument();
  },
);

it("does not color historical empty cells as idle capacity", async () => {
  mount();
  const row = await screen.findByTestId("manpower-board-person-person-0");
  for (const empty of within(row).getAllByText("–")) {
    expect(empty.closest("td")).not.toHaveClass("board-idle");
  }
});

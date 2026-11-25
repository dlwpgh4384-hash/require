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
import {
  manpowerBoardCellText,
  manpowerBoardTooltip,
  UI28Page,
} from "./UI28Page";
import * as board from "./manpowerBoard";
import * as pipeline from "../salespipeline/salesPipeline";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import {
  subscribeDeveloperAction,
  type DeveloperRequest,
} from "../developers/developerRequest";
import type { Developer } from "../developers/developers";
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
const periodControl: {
  ready: boolean;
  makeReady?: () => void;
} = { ready: true };
vi.mock("../userpreferences/useScreenPeriod", async () => {
  const { useState } = await import("react");
  return {
    useScreenPeriod: () => {
      const [value, setValue] = useState({ year: 2026 });
      const [ready, setReady] = useState(periodControl.ready);
      periodControl.makeReady = () => setReady(true);
      return {
        state: ready
          ? {
              status: "ready",
              basis: {
                latestYm: "202611",
                cashBaseYm: "202601",
                closedMonths: [],
              },
              value,
            }
          : { status: "loading" },
        setValue,
      };
    },
  };
});
type Cell = board.ManpowerBoardCell;
const cell = (month: string, patch: Partial<Cell> = {}): Cell => ({
  month,
  statusCode: "none",
  statusName: "–",
  totalMm: 0,
  projects: [],
  extensionPossibilityName: null,
  winningProbability: null,
  tentative: false,
  ...patch,
});
const result = (count = 1, year = 2026): board.ManpowerBoardResult => ({
  year,
  summary: {
    idleCount: 3,
    availableMm: 3.6,
    withdrawalCount: 4,
    highExtensionCount: 2,
    concurrentCount: 2,
    overAllocatedCount: 1,
    futureAvailableMm: 5.24,
  },
  totalCount: count,
  items: Array.from({ length: count }, (_, i) => ({
    developerId: `person-${i}`,
    name: `인력 ${year}-${i}`,
    jobName: "백엔드",
    cells: Array.from({ length: 12 }, (_, month) => {
      const ym = `${year}-${String(month + 1).padStart(2, "0")}`;
      return month < 10
        ? cell(ym)
        : cell(ym, {
            statusCode: "on",
            statusName: "투입 중",
            totalMm: 0.4,
            projects: [{ projectName: "프로젝트", monthlyMm: 0.4 }],
          });
    }),
  })),
  monthTotals: Array.from({ length: 12 }, (_, i) => ({
    month: `${year}-${String(i + 1).padStart(2, "0")}`,
    idleCount: i < 10 ? null : i === 10 ? 3 : 0,
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
const cellsOf = (row: HTMLElement) =>
  Array.from(row.querySelectorAll<HTMLElement>("td.mc .cell"));
beforeEach(() => {
  vi.clearAllMocks();
  periodControl.ready = true;
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(result());
  vi.mocked(board.getCommonManpowerBoardExcel).mockResolvedValue(37);
  vi.mocked(getCommonCodes).mockResolvedValue({
    items: [{ code: "BE", codeName: "백엔드", useYn: "Y" }],
  } as Awaited<ReturnType<typeof getCommonCodes>>);
  vi.mocked(pipeline.getCommonSalesPipelineForecast).mockResolvedValue({
    months: [
      ["2026-11", 4.123, 1.023],
      ["2026-12", 1.222, 2.322],
      ["2027-01", 2, 2.02],
      ["2027-02", 3, 2.7],
      ["2027-03", 4, 4],
      ["2027-04", 5, 5],
    ].map(([month, demand, supply]) => ({
      month: month as string,
      demand: demand as number,
      supply: supply as number,
      projectRevenue: 7,
      weightedOpportunityRevenue: 3,
      allWonRevenue: 10,
    })),
    hiringReviews: [],
    opportunityRevenues: [],
  });
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("renders the four mockup cards with 명 units and one-decimal MM and navigates to existing screens", async () => {
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  const summary = screen.getByTestId("manpower-board-summary");
  expect(summary.children).toHaveLength(4);
  const [idle, withdrawal, concurrent, future] = Array.from(summary.children);
  expect(idle).toHaveTextContent("3명");
  expect(idle).toHaveTextContent("남는 MM 3.6 (일부 투입 포함)");
  expect(withdrawal).toHaveTextContent("4명");
  expect(withdrawal).toHaveTextContent("연장 가능성 높음 2명");
  expect(concurrent).toHaveTextContent("2명");
  expect(concurrent).toHaveTextContent("이번 달 2건 이상 · 1.0 초과 1명");
  expect(future).toHaveTextContent("3개월 뒤 남는 MM");
  expect(future).toHaveTextContent("5.2");
  expect(within(future as HTMLElement).getByText("예측")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("manpower-board-withdrawal"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    /^\/screens\/UI-05$/,
  );
  fireEvent.click(screen.getByTestId("manpower-board-concurrent"));
  expect(screen.getByTestId("location")).toHaveTextContent(
    /^\/screens\/UI-12$/,
  );
});

it("waits for the period and sends exactly one first board request", async () => {
  periodControl.ready = false;
  mount();
  expect(board.getCommonManpowerBoard).not.toHaveBeenCalled();
  act(() => periodControl.makeReady!());
  await screen.findByTestId("manpower-board-person-person-0");
  expect(board.getCommonManpowerBoard).toHaveBeenCalledTimes(1);
  expect(board.getCommonManpowerBoard).toHaveBeenCalledWith(
    { year: 2026 },
    expect.any(AbortSignal),
  );
});

it("keeps condition drafts until search, changes year with the year bar, and resets conditions without resetting year", async () => {
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  await screen.findAllByRole("option", { name: "백엔드" });
  expect(screen.getByTestId("manpower-board-year")).toHaveTextContent(
    "그 해 1~12월 달력",
  );
  expect(screen.getByTestId("manpower-board-year-prev")).toBeDisabled();
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
  fireEvent.click(screen.getByTestId("manpower-board-year-next"));
  await waitFor(() =>
    expect(board.getCommonManpowerBoard).toHaveBeenLastCalledWith(
      expect.objectContaining({ year: 2027, jobCode: "BE" }),
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByTestId("manpower-board-year-next")).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "초기화" }));
  await waitFor(() =>
    expect(board.getCommonManpowerBoard).toHaveBeenLastCalledWith(
      { year: 2027 },
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByTestId("manpower-board-jobcode")).toHaveValue("");
});

it("ignores a late response from the previous year after a fast year change", async () => {
  let late!: (value: board.ManpowerBoardResult) => void;
  vi.mocked(board.getCommonManpowerBoard)
    .mockReturnValueOnce(
      new Promise((resolve) => {
        late = resolve;
      }),
    )
    .mockResolvedValueOnce(result(1, 2027));
  mount();
  await waitFor(() =>
    expect(board.getCommonManpowerBoard).toHaveBeenCalledTimes(1),
  );
  fireEvent.click(screen.getByTestId("manpower-board-year-next"));
  await screen.findByText("인력 2027-0");
  await act(async () => {
    late(result(1, 2026));
  });
  expect(screen.getByText("인력 2027-0")).toBeInTheDocument();
  expect(screen.queryByText("인력 2026-0")).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("manpower-board-scroll"))
      .getAllByRole("columnheader")
      .slice(2, 3)
      .map((h) => h.textContent),
  ).toEqual(["27-01"]);
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
  expect(screen.getByTestId("manpower-board-total")).toHaveTextContent(
    "총 51명",
  );
});

it("renders mockup footer rows: idle n명 in red, free MM with one decimal, past months as –", async () => {
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  const idle = screen.getByTestId("manpower-board-idle-row");
  const idleCells = Array.from(idle.querySelectorAll("td.mc"));
  expect(idle.querySelector("td")).toHaveTextContent("비는 인원");
  expect(idleCells.map((td) => td.textContent)).toEqual([
    ...Array(10).fill("–"),
    "3명",
    "0명",
  ]);
  expect(idleCells[10].querySelector("span")).toHaveClass("board-bad");
  expect(idleCells[11].querySelector("span")).not.toHaveClass("board-bad");
  const free = screen.getByTestId("manpower-board-free-row");
  const [first, second] = Array.from(free.querySelectorAll("td"));
  expect(first).toHaveTextContent(/^남는 MM$/);
  expect(second).toHaveTextContent("일부 투입 포함");
  expect(second).toHaveClass("ls-cap");
  expect(
    Array.from(free.querySelectorAll("td.mc")).map((td) => td.textContent),
  ).toEqual([...Array(10).fill("–"), "3.6", "3.6"]);
});

it("opens the developer pool detail (with [수정]·[사용 중지]) from the row click only and preserves calendar filters on close", async () => {
  mount();
  const row = await screen.findByTestId("manpower-board-person-person-0");
  expect(DeveloperDetail).not.toHaveBeenCalled();
  expect(within(row).queryByRole("button")).not.toBeInTheDocument();
  fireEvent.click(within(row).getByText("인력 2026-0"));
  await screen.findByTestId("existing-developer-detail");
  const props = vi.mocked(DeveloperDetail).mock.calls.at(-1)?.[0];
  // 시안 openPerson: 개발자 풀과 같은 상세(보기 전용 아님)
  expect(props).toEqual({
    developerId: "person-0",
    onClose: expect.any(Function),
    onEdit: expect.any(Function),
    onStop: expect.any(Function),
    onNotice: expect.any(Function),
    onChanged: expect.any(Function),
  });
  expect(props).not.toHaveProperty("readOnly");
  const calls = vi.mocked(board.getCommonManpowerBoard).mock.calls.length;
  fireEvent.click(screen.getByTestId("detail-close"));
  expect(
    screen.queryByTestId("existing-developer-detail"),
  ).not.toBeInTheDocument();
  expect(board.getCommonManpowerBoard).toHaveBeenCalledTimes(calls);
  fireEvent.keyDown(row, { key: "Enter" });
  expect(
    await screen.findByTestId("existing-developer-detail"),
  ).toBeInTheDocument();
});

it.each(["edit", "stop"] as const)(
  "detail [%s] closes the drawer and hands the developer to 개발자 풀 (시안 editform)",
  async (action) => {
    mount();
    fireEvent.click(
      await screen.findByTestId("manpower-board-person-person-0"),
    );
    await screen.findByTestId("existing-developer-detail");
    const props = vi.mocked(DeveloperDetail).mock.calls.at(-1)![0] as {
      onEdit: (developer: Developer) => void;
      onStop: (developer: Developer) => void;
    };
    const developer = { developerId: "person-0", name: "인력" } as Developer;
    act(() => (action === "edit" ? props.onEdit : props.onStop)(developer));
    expect(
      screen.queryByTestId("existing-developer-detail"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("location")).toHaveTextContent("/screens/UI-02");
    // 개발자 풀 화면이 열리면 남은 요청을 받아 수정 폼·사용 중지 확인 창을 연다.
    const received: DeveloperRequest[] = [];
    const stop = subscribeDeveloperAction((request) => received.push(request));
    stop();
    expect(received).toEqual([{ developer, action }]);
  },
);

it("detail status changes refresh the board and show the message", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("manpower-board-person-person-0"));
  await screen.findByTestId("existing-developer-detail");
  const props = vi.mocked(DeveloperDetail).mock.calls.at(-1)![0] as {
    onChanged: (message?: string) => void;
  };
  const calls = vi.mocked(board.getCommonManpowerBoard).mock.calls.length;
  act(() => props.onChanged("휴직을 등록했습니다."));
  await waitFor(() =>
    expect(board.getCommonManpowerBoard).toHaveBeenCalledTimes(calls + 1),
  );
  expect(screen.getByTestId("manpower-board-toast")).toHaveTextContent(
    "휴직을 등록했습니다.",
  );
});

it("shows the person number under the name, falling back to the developer id", async () => {
  const data = result(2);
  data.items[0].managementNumber = "E-1001";
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(data);
  mount();
  const first = await screen.findByTestId("manpower-board-person-person-0");
  expect(first.querySelector(".board-pname small")).toHaveTextContent(
    "E-1001",
  );
  expect(
    screen
      .getByTestId("manpower-board-person-person-1")
      .querySelector(".board-pname small"),
  ).toHaveTextContent("person-1");
});

it("loads the demand/supply table from this month in 명 and keeps it when switching tabs", async () => {
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  fireEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  const panel = await screen.findByTestId("manpower-board-forecast");
  await within(panel).findByText("2026-11");
  expect(pipeline.getCommonSalesPipelineForecast).toHaveBeenCalledWith(
    "2026-11",
  );
  expect(within(panel).getByTestId("banner-info")).toHaveTextContent(
    "가용 = 사람마다 (1 − 그 달 투입 MM)의 합. 철수한 투입은 연장 가능성(높음 80%·보통 50%·낮음 0%)만큼 남는다고 봅니다. 필요 = 영업 기회의 필요 인력 × 단계 확률 + 진행 프로젝트 미충원.",
  );
  const rows = within(panel).getAllByRole("row");
  expect(
    within(rows[0])
      .getAllByRole("columnheader")
      .map((c) => c.textContent),
  ).toEqual([
    "구분",
    "2026-11",
    "2026-12",
    "2027-01",
    "2027-02",
    "2027-03",
    "2027-04",
  ]);
  expect(rows[1]).toHaveTextContent("필요 인력4.1명1.2명2.0명3.0명4.0명5.0명");
  expect(rows[2]).toHaveTextContent("가용 인력1.0명2.3명2.0명2.7명4.0명5.0명");
  expect(within(panel).getByText("3.1명 부족")).toHaveClass("board-bad");
  expect(within(panel).getByText("1.1명 남음")).toHaveClass("board-warn");
  expect(within(panel).getByText("0.3명 부족")).not.toHaveClass("board-bad");
  expect(within(panel).getAllByText("맞음")).toHaveLength(3);
  expect(
    screen.queryByTestId("manpower-board-gradecode"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "달력" }));
  expect(screen.getByTestId("manpower-board-gradecode")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  expect(within(screen.getByTestId("manpower-board-forecast")).getByText("2026-11")).toBeInTheDocument();
  expect(pipeline.getCommonSalesPipelineForecast).toHaveBeenCalledTimes(1);
});

it("uses the common tab look with aria-controls only on the shown panel", async () => {
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  const calendar = screen.getByRole("tab", { name: "달력" });
  const forecast = screen.getByRole("tab", { name: "수요·공급" });
  expect(calendar.parentElement).toHaveClass("ls-tabs");
  expect(calendar).toHaveClass("ls-tb", "ls-on");
  expect(calendar).toHaveAttribute("aria-selected", "true");
  expect(calendar).toHaveAttribute("aria-controls", "manpower-board-calendar");
  expect(forecast).not.toHaveAttribute("aria-controls");
  expect(forecast).toHaveAttribute("aria-selected", "false");
});

it("shows forecast failures in its own panel and refetches only on retry", async () => {
  vi.mocked(pipeline.getCommonSalesPipelineForecast).mockRejectedValueOnce(
    new Error("예측 조회 오류"),
  );
  mount();
  await screen.findByTestId("manpower-board-person-person-0");
  fireEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  await screen.findByText("예측 조회 오류");
  fireEvent.click(screen.getByRole("tab", { name: "달력" }));
  expect(
    screen.getByTestId("manpower-board-person-person-0"),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "수요·공급" }));
  expect(pipeline.getCommonSalesPipelineForecast).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByTestId("manpower-board-forecast-retry"));
  await screen.findByText("2026-11");
  expect(pipeline.getCommonSalesPipelineForecast).toHaveBeenCalledTimes(2);
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

it("keeps short month headers, the current month mark, legend, note and footer for an empty roster", async () => {
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(result(0));
  mount();
  await screen.findByTestId("manpower-board-empty");
  const headers = within(screen.getByTestId("manpower-board-scroll"))
    .getAllByRole("columnheader")
    .map((h) => h.textContent);
  expect(headers).toEqual([
    "이름",
    "직무",
    ...Array.from({ length: 12 }, (_, i) => `26-${String(i + 1).padStart(2, "0")}`),
  ]);
  expect(screen.getByRole("columnheader", { name: "26-11" })).toHaveClass(
    "mc",
    "cur",
  );
  expect(screen.getByRole("columnheader", { name: "26-10" })).not.toHaveClass(
    "cur",
  );
  expect(screen.getByTestId("manpower-board-filters")).toBeInTheDocument();
  expect(screen.getByTestId("manpower-board-free-row")).toBeInTheDocument();
  const legend = screen.getByTestId("manpower-board-legend");
  expect(legend.closest(".ls-tr")).not.toBeNull();
  expect(
    Array.from(legend.children).map((item) => item.textContent),
  ).toEqual([
    "투입 중",
    "일부 투입(1.0 미만)",
    "2건동시 투입",
    "철수 예정",
    "비어 있음",
    "가배정",
    "휴직",
    "퇴사",
    "⚠미계약 선투입",
  ]);
  expect(legend.querySelectorAll(".cell")).toHaveLength(9);
  expect(
    screen.getByText(
      "칸에 마우스를 올리면 그 달의 프로젝트별 MM이 보입니다. 1.0 미만으로 투입된 달은 투입된 만큼만 칠합니다.",
    ),
  ).toBeInTheDocument();
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
  await screen.findByTestId("manpower-board-person-person-0");
  fireEvent.click(screen.getByTestId("manpower-board-excel"));
  expect(board.getCommonManpowerBoardExcel).toHaveBeenCalledWith({
    year: 2026,
  });
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
  await screen.findByTestId("manpower-board-person-person-0");
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

it("draws each calendar cell like the mockup: status class, partial fill, over outline, short text and a one-line tooltip", async () => {
  const data = result();
  const cells = data.items[0].cells;
  cells[0] = cell("2026-01", {
    statusCode: "on",
    statusName: "투입 중",
    totalMm: 1,
    projects: [{ projectName: "가 프로젝트", monthlyMm: 1 }],
  });
  cells[1] = cell("2026-02", { statusCode: "leave", statusName: "휴직" });
  cells[2] = cell("2026-03", { statusCode: "ret", statusName: "퇴사" });
  cells[3] = cell("2026-04", { statusCode: "pre", statusName: "미계약 선투입", totalMm: 1, projects: [{ projectName: "수주 예정", monthlyMm: 1 }] });
  cells[9] = cell("2026-10", {
    statusCode: "idle",
    statusName: "비어 있음",
  });
  cells[10] = cell("2026-11", {
    statusCode: "on",
    statusName: "투입 중",
    totalMm: 1.3,
    projects: [
      { projectName: "첫 프로젝트", monthlyMm: 0.7 },
      { projectName: "둘째 프로젝트", monthlyMm: 0.6 },
    ],
  });
  cells[11] = cell("2026-12", {
    statusCode: "tent",
    statusName: "가배정",
    winningProbability: 30,
    tentative: true,
  });
  data.items.push({
    ...data.items[0],
    developerId: "person-1",
    name: "인력 끝",
    cells: data.items[0].cells.map((c, i) =>
      i === 11
        ? cell("2026-12", {
            statusCode: "end",
            statusName: "철수 예정",
            totalMm: 1,
            projects: [{ projectName: "끝 프로젝트", monthlyMm: 1 }],
            extensionPossibilityName: "높음",
          })
        : i === 10
          ? cell("2026-11", {
              statusCode: "end",
              statusName: "철수 예정",
              totalMm: 0.5,
              projects: [{ projectName: "끝 프로젝트", monthlyMm: 0.5 }],
              extensionPossibilityName: "보통",
            })
          : c,
    ),
  });
  vi.mocked(board.getCommonManpowerBoard).mockResolvedValue(data);
  mount();
  const row = await screen.findByTestId("manpower-board-person-person-0");
  const spans = cellsOf(row);
  expect(spans).toHaveLength(12);
  expect(row.querySelectorAll("td.mc .cell")[0].parentElement).toHaveClass(
    "mc",
  );
  expect(spans[0]).toHaveAttribute("class", "cell c-on");
  expect(spans[0]).toHaveTextContent(/^$/);
  expect(spans[0].title).toBe("2026-01 · 투입 중 · 가 프로젝트 1.00MM");
  expect(spans[1]).toHaveAttribute("class", "cell c-leave");
  expect(spans[1].title).toBe("2026-02 · 휴직");
  expect(spans[2]).toHaveAttribute("class", "cell c-ret");
  expect(spans[3]).toHaveAttribute("class", "cell c-pre");
  expect(spans[3]).toHaveTextContent("⚠");
  expect(spans[4]).toHaveAttribute("class", "cell c-none");
  expect(spans[4].title).toBe("2026-05 · –");
  expect(spans[9]).toHaveAttribute("class", "cell c-idle");
  expect(spans[10]).toHaveAttribute("class", "cell c-on c-over");
  expect(spans[10]).toHaveTextContent("2건");
  expect(spans[10].title).toBe(
    "2026-11 · 투입 중 · 첫 프로젝트 0.70MM, 둘째 프로젝트 0.60MM (합계 1.30, 1.0 초과)",
  );
  expect(spans[11]).toHaveAttribute("class", "cell c-tent");
  expect(spans[11]).toHaveTextContent("30%");
  expect(row.querySelector("td.mc .ls-b, td.mc small")).toBeNull();

  const end = cellsOf(screen.getByTestId("manpower-board-person-person-1"));
  expect(end[11]).toHaveAttribute("class", "cell c-end");
  expect(end[11]).toHaveTextContent("높음");
  expect(end[10]).toHaveAttribute("class", "cell c-end c-part");
  expect(end[10]).toHaveTextContent("0.5");
  expect(end[10].style.getPropertyValue("--p")).toBe("50%");
});

it("fills partial months by MM and keeps the text rules as pure helpers", async () => {
  mount();
  const row = await screen.findByTestId("manpower-board-person-person-0");
  const partial = cellsOf(row)[10];
  expect(partial).toHaveAttribute("class", "cell c-on c-part");
  expect(partial).toHaveTextContent("0.4");
  expect(partial.style.getPropertyValue("--p")).toBe("40%");
  expect(partial.title).toBe("2026-11 · 투입 중 · 프로젝트 0.40MM");
  const base = cell("2026-11");
  expect(manpowerBoardCellText({ ...base, statusCode: "tent" })).toBe("");
  expect(
    manpowerBoardCellText({
      ...base,
      statusCode: "end",
      totalMm: 1,
      extensionPossibilityName: null,
    }),
  ).toBe("");
  expect(
    manpowerBoardTooltip({
      ...base,
      statusCode: "on",
      statusName: "투입 중",
      totalMm: 1,
      projects: [{ projectName: "A", monthlyMm: 1 }],
    }),
  ).toBe("2026-11 · 투입 중 · A 1.00MM");
});

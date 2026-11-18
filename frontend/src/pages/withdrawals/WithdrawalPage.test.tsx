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
import { WithdrawalPage } from "./WithdrawalPage";
import { WithdrawalRecommendations } from "./WithdrawalRecommendations";
import { StaffingBoardPage } from "./StaffingBoardPage";
import { WorkforceForecastPage } from "./WorkforceForecastPage";
import { OpportunityPage } from "./OpportunityPage";
import {
  notifyWithdrawalChanged,
  type WithdrawalList,
  type WithdrawalRow,
} from "./withdrawals";

const viewport = vi.hoisted(() => ({ mobile: false, width: 1280 }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
vi.mock("../assignments/AssignmentDetail", () => ({
  AssignmentDetail: ({ id, onClose }: { id: string; onClose: () => void }) => (
    <section data-testid="withdrawal-assignment-detail">
      {id}
      <button data-testid="withdrawal-detail-close" onClick={onClose}>
        닫기
      </button>
    </section>
  ),
}));
let request: ReturnType<typeof vi.fn>;
let data: WithdrawalList;
let failure: boolean;
let mutationFailure: boolean;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const row = (id: string, days: number): WithdrawalRow => ({
  assignmentId: id,
  developerId: `person-${id}`,
  name: `사람 ${id}`,
  projectName: "프로젝트",
  jobName: "백엔드",
  endDate: "2026-11-30",
  remainingDays: days,
  withdrawalCompleted: days < 0,
  extensionCount: 2,
  earlyWithdrawalHistory: ["2026-01-01"],
  extensionPossibilityCode: "M",
  extensionPossibilityName: "보통",
  nextAssignment: {
    opportunityName: null,
    recommendationCount: 1,
    hasTentativeAssignment: false,
  },
  modifiedAt: null,
});
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  viewport.mobile = false;
  viewport.width = 1280;
  failure = false;
  mutationFailure = false;
  data = {
    items: [row("a", 30), row("b", 60), row("c", 61), row("d", -1)],
    totalCount: 4,
    banner: { unassignedCount: 3, matchedCount: 2 },
  };
  request = vi.fn(async (input: string, init?: RequestInit) => {
    const url = new URL(String(input), "http://test");
    if (url.pathname.includes("/codes"))
      return ok({
        items: url.pathname.includes("EXT_PSBL")
          ? [
              { code: "H", codeName: "높음" },
              { code: "M", codeName: "보통" },
              { code: "L", codeName: "낮음" },
            ]
          : [{ code: "BE", codeName: "백엔드" }],
      });
    if (url.pathname.endsWith("/extension-possibility")) {
      if (mutationFailure)
        return {
          ok: false,
          status: 409,
          json: async () => ({
            success: false,
            message: "다시 불러와 주세요.",
            errors: [{ field: "modifiedAt", message: "다시 불러와 주세요." }],
          }),
        };
      const body = JSON.parse(String(init?.body));
      data.items[0].extensionPossibilityCode = body.extensionPossibilityCode;
      return ok({
        assignmentId: "a",
        extensionPossibilityCode: body.extensionPossibilityCode,
        modifiedAt: "2026-11-15T00:00:00Z",
      });
    }
    if (url.pathname.endsWith("/tentative-assignments"))
      return ok({
        items: [
          {
            assignmentId: "a",
            developerId: "person-a",
            name: "사람 a",
            extensionPossibilityCode: data.items[0].extensionPossibilityCode,
            extensionPossibilityName: "보통",
            tentativeAssignment: {
              assignmentId: "a",
              developerId: "person-a",
              opportunityId: "opp",
              opportunityName: "다음 기회",
              needSn: 1,
              startMonth: "2026-12",
            },
          },
        ],
      });
    if (url.pathname.endsWith("/recommendations"))
      return ok({
        items: [
          {
            needSn: 1,
            opportunityId: "opp",
            opportunityName: "다음 기회",
            jobName: "백엔드",
            gradeName: "중급",
            startMonth: "2026-12",
            score: 100,
            reasons: ["직무 일치"],
            waitingDays: 0,
            waitingLabel: "바로 이어서 투입",
          },
        ],
        shortages: [
          { month: "2026-12", jobName: "백엔드", shortageCount: 0.6 },
        ],
      });
    if (url.pathname.endsWith("/tentative-assignment"))
      return ok({ assignment: null });
    if (failure) throw new Error("조회 실패");
    return ok({
      ...data,
      banner: url.searchParams.get("year") === "2026" ? data.banner : null,
    });
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
      <WithdrawalPage />
    </MemoryRouter>,
  );
const ready = () => screen.findByTestId("withdrawal-extension-a");

it("uses contract day classes including both exact boundaries and completed copy", async () => {
  page();
  await ready();
  expect(screen.getByText("D-30")).toHaveClass("withdrawal-red");
  expect(screen.getByText("D-60")).toHaveClass("withdrawal-orange");
  expect(screen.getByText("D-61")).toHaveClass("withdrawal-gray");
  expect(screen.getByText("철수 완료")).toHaveClass("withdrawal-complete");
  expect(screen.getAllByText("연장 2회").length).toBeGreaterThan(0);
});
it("inline change is anonymous, sends required null token and does not open detail", async () => {
  page();
  const control = await ready();
  fireEvent.change(control, { target: { value: "H" } });
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/withdrawals/a/extension-possibility",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          extensionPossibilityCode: "H",
          modifiedAt: null,
        }),
      }),
    ),
  );
  expect(
    screen.queryByTestId("withdrawal-assignment-detail"),
  ).not.toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByTestId("withdrawal-extension-a")).toHaveValue("H"),
  );
});
it("미정 is an empty string rather than an invented fourth code", async () => {
  page();
  fireEvent.change(await ready(), { target: { value: "" } });
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      expect.stringContaining("/extension-possibility"),
      expect.objectContaining({
        body: JSON.stringify({
          extensionPossibilityCode: "",
          modifiedAt: null,
        }),
      }),
    ),
  );
});
it("conflict shows field feedback and leaves original possibility intact", async () => {
  mutationFailure = true;
  page();
  fireEvent.change(await ready(), { target: { value: "H" } });
  await waitFor(() =>
    expect(screen.getByTestId("withdrawal-error-a")).toHaveTextContent(
      "다시 불러와 주세요.",
    ),
  );
  expect(data.items[0].extensionPossibilityCode).toBe("M");
});
it("local detail close retains year and list filters", async () => {
  page();
  await ready();
  fireEvent.change(screen.getByTestId("withdrawal-filter-jobCode"), {
    target: { value: "BE" },
  });
  await ready();
  fireEvent.click(screen.getByText("사람 a"));
  expect(
    await screen.findByTestId("withdrawal-assignment-detail"),
  ).toHaveTextContent("a");
  fireEvent.click(screen.getByTestId("withdrawal-detail-close"));
  expect(screen.getByTestId("withdrawal-filter-jobCode")).toHaveValue("BE");
  expect(screen.getByTestId("withdrawal-year-year")).toHaveTextContent(
    "2026년",
  );
});
it("past-year list has no current-year banner but extension remains editable", async () => {
  page();
  await ready();
  fireEvent.click(screen.getByTestId("withdrawal-year-prev"));
  await ready();
  expect(
    screen.queryByTestId("withdrawal-banner-recommend"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("withdrawal-extension-a")).toBeEnabled();
  expect(screen.queryByText("지난 해 · 읽기 전용")).not.toBeInTheDocument();
});
it("next assignment distinguishes tentative, recommendation, none and completed", async () => {
  data.items[0].nextAssignment = {
    opportunityName: "기존 기회",
    recommendationCount: 0,
    hasTentativeAssignment: true,
  };
  data.items[2].nextAssignment.recommendationCount = 0;
  page();
  await ready();
  expect(screen.getByText(/가배정 · 기존 기회/)).toBeInTheDocument();
  expect(screen.getByTestId("withdrawal-replace-a")).toHaveTextContent(
    "바꾸기",
  );
  expect(screen.getByTestId("withdrawal-recommend-b")).toHaveTextContent(
    "추천 1곳",
  );
  expect(screen.getByText("없음 · 맞는 자리 없음")).toBeInTheDocument();
  expect(
    screen.queryByTestId("withdrawal-recommend-d"),
  ).not.toBeInTheDocument();
});
it("empty list renders its shared empty state", async () => {
  data.items = [];
  data.totalCount = 0;
  page();
  await waitFor(() =>
    expect(
      screen.queryByTestId("withdrawal-extension-a"),
    ).not.toBeInTheDocument(),
  );
  await waitFor(() =>
    expect(screen.getByTestId("withdrawal-list")).toHaveTextContent("0"),
  );
});
it("failed request offers retry that restores data", async () => {
  failure = true;
  page();
  await screen.findByText("조회 실패");
  failure = false;
  fireEvent.click(screen.getByRole("button", { name: /다시 시도/ }));
  await ready();
});
it("download uses the complete filtered binary and shows its count only after triggering download", async () => {
  page();
  await ready();
  const original = request.getMockImplementation()!;
  const created = vi.fn(() => "blob:withdrawal");
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
            "X-Total-Count": "35",
            "Content-Disposition": "attachment; filename=withdrawals.xlsx",
          }),
          blob: async () => new Blob(["PK"]),
        }
      : original(url, init),
  );
  fireEvent.click(screen.getByTestId("withdrawal-download"));
  expect(await screen.findByTestId("withdrawal-toast")).toHaveTextContent(
    "엑셀 파일을 만들었습니다 (조회 결과 35건)",
  );
  expect(click).toHaveBeenCalledOnce();
  expect(created).toHaveBeenCalledOnce();
});
it("failed download never displays a completion toast", async () => {
  page();
  await ready();
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    String(url).includes("/excel?")
      ? {
          ok: false,
          headers: new Headers(),
          json: async () => ({ message: "다운로드 실패" }),
        }
      : original(url, init),
  );
  fireEvent.click(screen.getByTestId("withdrawal-download"));
  expect(await screen.findByTestId("withdrawal-toast")).toHaveTextContent(
    "다운로드 실패",
  );
  expect(
    screen.queryByText(/엑셀 파일을 만들었습니다/),
  ).not.toBeInTheDocument();
});
it("recommendation shows score reason waiting and fractional shortage, saving only needSn", async () => {
  const saved = vi.fn();
  render(
    <WithdrawalRecommendations
      assignmentId="a"
      replaceCurrent={false}
      onClose={vi.fn()}
      onSaved={saved}
    />,
  );
  await screen.findByText("다음 기회");
  expect(screen.getByText(/100\/100/)).toBeInTheDocument();
  expect(screen.getByText("직무 일치")).toBeInTheDocument();
  expect(screen.getByText("바로 이어서 투입")).toBeInTheDocument();
  expect(screen.getByText(/12월 0.6명 부족 예상/)).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("withdrawal-reserve-opp-1"));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(request).toHaveBeenCalledWith(
    "/api/common/withdrawals/a/tentative-assignment",
    expect.objectContaining({ body: '{"needSn":1}' }),
  );
});
it("duplicate save is disabled until its promise resolves", async () => {
  let resolve: ((value: unknown) => void) | undefined;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    init?.method === "PUT"
      ? new Promise((done) => {
          resolve = done;
        })
      : ok({
          items: [
            {
              needSn: 1,
              opportunityId: "opp",
              opportunityName: "자리",
              jobName: "직무",
              gradeName: "중급",
              startMonth: "2026-12",
              score: 80,
              reasons: [],
              waitingDays: 0,
              waitingLabel: "바로 이어서 투입",
            },
          ],
          shortages: [],
        }),
  );
  render(
    <WithdrawalRecommendations
      assignmentId="a"
      replaceCurrent
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  const save = await screen.findByTestId("withdrawal-reserve-opp-1");
  fireEvent.click(save);
  fireEvent.click(save);
  expect(save).toBeDisabled();
  expect(
    request.mock.calls.filter(([, init]) => init?.method === "PUT"),
  ).toHaveLength(1);
  resolve?.(ok({ assignment: null }));
  await waitFor(() => expect(save).toBeEnabled());
});
it("recommendation empty and failure states provide close and retry", async () => {
  request.mockResolvedValue(ok({ items: [], shortages: [] }));
  const result = render(
    <WithdrawalRecommendations
      assignmentId="a"
      replaceCurrent={false}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await screen.findByText("맞는 자리 없음");
  result.unmount();
  request.mockRejectedValue(new Error("추천 조회 실패"));
  render(
    <WithdrawalRecommendations
      assignmentId="a"
      replaceCurrent={false}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  expect(
    await screen.findByTestId("withdrawal-recommendation-error"),
  ).toHaveTextContent("추천 조회 실패");
  expect(screen.getByTestId("withdrawal-recommendation-retry")).toBeEnabled();
});
it.each(["staffing", "forecast", "opportunity"])(
  "shared %s source is read-only and refreshes on focus and local mutation",
  async (kind) => {
    const result = render(
      kind === "staffing" ? (
        <StaffingBoardPage developerId="person-a" />
      ) : kind === "forecast" ? (
        <WorkforceForecastPage developerId="person-a" />
      ) : (
        <OpportunityPage opportunityId="opp" />
      ),
    );
    const region = await screen.findByTestId("withdrawal-shared-a");
    expect(within(region).queryByRole("button")).not.toBeInTheDocument();
    if (kind === "staffing")
      expect(region.querySelector(".withdrawal-tentative")).not.toBeNull();
    if (kind === "forecast") expect(region).not.toHaveTextContent("가배정");
    const before = request.mock.calls.length;
    fireEvent.focus(window);
    await waitFor(() =>
      expect(request.mock.calls.length).toBeGreaterThan(before),
    );
    const focused = request.mock.calls.length;
    notifyWithdrawalChanged();
    await waitFor(() =>
      expect(request.mock.calls.length).toBeGreaterThan(focused),
    );
    result.unmount();
  },
);
it("mobile cards keep inline possibility control and recommendation action", async () => {
  viewport.mobile = true;
  viewport.width = 375;
  page();
  expect(
    await screen.findByTestId("withdrawal-extension-a-mobile"),
  ).toBeEnabled();
  expect(screen.getByTestId("withdrawal-recommend-a")).toBeInTheDocument();
});

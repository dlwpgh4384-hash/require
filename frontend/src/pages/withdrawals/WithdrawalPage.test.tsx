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
import {
  notifyWithdrawalChanged,
  type WithdrawalList,
  type WithdrawalRecommendationsData,
  type WithdrawalRow,
} from "./withdrawals";

const viewport = vi.hoisted(() => ({ mobile: false, width: 1280 }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
vi.mock("../assignments/AssignmentDetail", () => ({
  AssignmentDetail: ({
    id,
    onClose,
    onChanged,
  }: {
    id: string;
    onClose: () => void;
    onChanged: (message?: string, kind?: "ok" | "info" | "warn") => void;
  }) => (
    <section data-testid="withdrawal-assignment-detail">
      {id}
      <button data-testid="withdrawal-detail-close" onClick={onClose}>
        닫기
      </button>
      <button
        data-testid="withdrawal-detail-change"
        onClick={() => onChanged("연장했습니다.", "info")}
      >
        바꿈
      </button>
    </section>
  ),
}));
let request: ReturnType<typeof vi.fn>;
let data: WithdrawalList;
let recommendations: WithdrawalRecommendationsData;
let reserved: boolean;
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
  earlyWithdrawalHistory: ["2026-01-01 · 2026-01-31"],
  extensionPossibilityCode: "M",
  extensionPossibilityName: "보통",
  nextAssignment: {
    opportunityName: null,
    recommendationCount: days < 0 ? 0 : 1,
    hasTentativeAssignment: false,
    recommendationTarget: days >= 0,
    note: null,
  },
  modifiedAt: null,
  projectId: "P-1",
  managementNumber: `D-${id}`,
});
const card = (tentative = false) => ({
  needSn: 1,
  opportunityId: "opp",
  opportunityName: "다음 기회",
  jobName: "백엔드",
  gradeName: "중급",
  startMonth: "2026-12",
  score: 100,
  reasons: ["직무 일치", "등급 일치", "공백 없음", "수주 확률 60%"],
  waitingDays: 0,
  waitingLabel: "바로 이어서 투입",
  remainingCount: 1,
  customerName: "고객사",
  stageName: "제안",
  tentative,
});
const target = (tentative = false) => ({
  assignmentId: "a",
  name: "사람 a",
  jobName: "백엔드",
  gradeName: "중급",
  projectName: "프로젝트",
  endDate: "2026-11-30",
  skills: ["Java", "Spring"],
  extensionPossibilityName: "보통",
  waitingStartDate: "2026-12-01",
  tentativeOpportunityId: tentative ? "opp" : null,
  tentativeOpportunityName: tentative ? "다음 기회" : null,
  tentativeStartMonth: tentative ? "2026-12" : null,
});
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  viewport.mobile = false;
  viewport.width = 1280;
  failure = false;
  mutationFailure = false;
  reserved = false;
  data = {
    items: [row("a", 30), row("b", 60), row("c", 61), row("d", -1)],
    totalCount: 4,
    banner: { soonCount: 4, unassignedCount: 3, matchedCount: 2 },
  };
  recommendations = {
    items: [card()],
    shortages: [{ month: "2026-12", jobName: "백엔드", shortageCount: 0.6 }],
    target: target(),
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
    if (url.pathname.endsWith("/recommendations"))
      return ok(
        reserved
          ? { ...recommendations, items: [card(true)], target: target(true) }
          : recommendations,
      );
    if (url.pathname.endsWith("/tentative-assignment")) {
      if (init?.method === "PUT") reserved = true;
      return ok({ assignment: null });
    }
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
const listCalls = () =>
  request.mock.calls
    .map(([url]) => String(url))
    .filter((url) => url.startsWith("/api/common/withdrawals?"));
const drawer = (replaceCurrent = false, saved = vi.fn()) =>
  render(
    <WithdrawalRecommendations
      row={row("a", 30)}
      replaceCurrent={replaceCurrent}
      onClose={vi.fn()}
      onSaved={saved}
    />,
  );

it("remaining days use shared badges with mockup boundaries and a lock for completed", async () => {
  page();
  await ready();
  expect(screen.getByText("D-30")).toHaveClass("ls-b", "ls-b-bad");
  expect(screen.getByText("D-60")).toHaveClass("ls-b-warn");
  expect(screen.getByText("D-61")).toHaveClass("ls-b-neutral");
  const done = screen.getByText("철수 완료");
  expect(done).toHaveClass("ls-b-lock");
  expect(done.querySelector("svg")).not.toBeNull();
  expect(screen.getAllByText("연장 2회").length).toBe(4);
  expect(screen.getAllByText("조기 철수 이력")[0]).toHaveClass("ls-b-warn");
});
it("zero extensions show a dash and no early badge without history", async () => {
  data.items[0].extensionCount = 0;
  data.items[0].earlyWithdrawalHistory = [];
  page();
  await ready();
  const cells = screen.getByText("D-a").closest("tr")!;
  expect(within(cells).queryByText(/연장 \d회/)).not.toBeInTheDocument();
  expect(within(cells).getAllByText("–").length).toBeGreaterThan(0);
  expect(within(cells).queryByText("조기 철수 이력")).not.toBeInTheDocument();
});
it("inline change is anonymous, sends required null token, toasts and does not open detail", async () => {
  page();
  const control = await ready();
  expect(control).toHaveClass("ls-in", "ls-in-sm");
  await waitFor(() =>
    expect(
      Array.from((control as HTMLSelectElement).options).map((o) => o.text),
    ).toEqual(["미정", "높음", "보통", "낮음"]),
  );
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
  expect(await screen.findByTestId("withdrawal-toast")).toHaveTextContent(
    "사람 a님의 연장 가능성을 '높음'으로 바꿨습니다.",
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
  expect(await screen.findByTestId("withdrawal-toast")).toHaveTextContent(
    "사람 a님의 연장 가능성을 '미정'으로 바꿨습니다.",
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
it("filters apply only when [조회] is pressed", async () => {
  page();
  await ready();
  const before = listCalls().length;
  fireEvent.change(screen.getByTestId("withdrawal-filter-jobCode"), {
    target: { value: "BE" },
  });
  fireEvent.change(screen.getByTestId("withdrawal-filter-period"), {
    target: { value: "3개월 안" },
  });
  expect(listCalls()).toHaveLength(before);
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(listCalls().at(-1)).toContain("jobCode=BE"),
  );
  expect(listCalls().at(-1)).toContain(
    `period=${encodeURIComponent("3개월 안").replace(/%20/g, "+")}`,
  );
});
it("local detail close retains year and list filters, and detail changes reach a toast", async () => {
  page();
  await ready();
  fireEvent.change(screen.getByTestId("withdrawal-filter-jobCode"), {
    target: { value: "BE" },
  });
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await ready();
  fireEvent.click(screen.getByText("D-a"));
  expect(
    await screen.findByTestId("withdrawal-assignment-detail"),
  ).toHaveTextContent("a");
  const before = listCalls().length;
  fireEvent.click(screen.getByTestId("withdrawal-detail-change"));
  expect(await screen.findByTestId("withdrawal-toast")).toHaveTextContent(
    "연장했습니다.",
  );
  await waitFor(() => expect(listCalls().length).toBeGreaterThan(before));
  fireEvent.click(screen.getByTestId("withdrawal-detail-close"));
  expect(screen.getByTestId("withdrawal-filter-jobCode")).toHaveValue("BE");
  expect(screen.getByTestId("withdrawal-year-year")).toHaveTextContent(
    "2026년",
  );
});
it("current-year banners use the exact mockup text and warn when someone has no next seat", async () => {
  page();
  await ready();
  const next = screen.getByTestId("withdrawal-next-banner");
  expect(next).toHaveTextContent(
    "3개월 안 철수 4명 중 연장 가능성이 높지 않고 다음 배정도 없는 사람 3명 — 이 중 2명은 맞는 자리가 있습니다. [다음 배정] 칸의 추천을 눌러 가배정하세요.",
  );
  expect(screen.getAllByTestId("banner-warn")).toHaveLength(1);
  expect(screen.getByTestId("withdrawal-info-banner")).toHaveTextContent(
    "2026년에 철수하는(했던) 인력입니다. [기간] 조건으로 '3개월 안'만 볼 수 있습니다. 연장 가능성을 바꾸면 인력 운영 보드와 수요 예측에 바로 반영됩니다.",
  );
  expect(
    screen.queryByTestId("withdrawal-banner-recommend"),
  ).not.toBeInTheDocument();
});
it("banner is info with zero counts when nobody lacks a next seat or nothing is returned", async () => {
  data.banner = { soonCount: 0, unassignedCount: 0, matchedCount: 0 };
  data.items = [];
  data.totalCount = 0;
  page();
  expect(await screen.findByTestId("withdrawal-next-banner")).toHaveTextContent(
    "3개월 안 철수 0명 중 연장 가능성이 높지 않고 다음 배정도 없는 사람 0명 — 이 중 0명은 맞는 자리가 있습니다.",
  );
  expect(screen.queryByTestId("banner-warn")).not.toBeInTheDocument();
  expect(screen.getAllByTestId("banner-info")).toHaveLength(2);
  expect(await screen.findByTestId("list-empty")).toBeVisible();
  expect(screen.getByTestId("withdrawal-filters")).toBeInTheDocument();
  expect(screen.getByTestId("withdrawal-download")).toBeEnabled();
});
it("past-year list has no next-seat banner but keeps the info banner and editable possibility", async () => {
  page();
  await ready();
  fireEvent.click(screen.getByTestId("withdrawal-year-prev"));
  await ready();
  expect(
    screen.queryByTestId("withdrawal-next-banner"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("withdrawal-info-banner")).toHaveTextContent(
    "2025년에 철수하는(했던) 인력입니다.",
  );
  expect(screen.getByTestId("withdrawal-extension-a")).toBeEnabled();
  expect(screen.queryByText("지난 해 · 읽기 전용")).not.toBeInTheDocument();
});
it("next assignment follows the mockup for tentative, target, non-target and completed rows", async () => {
  data.items[0].nextAssignment = {
    opportunityName: "기존 기회",
    recommendationCount: 0,
    hasTentativeAssignment: true,
    recommendationTarget: false,
    note: null,
  };
  data.items[1].nextAssignment = {
    opportunityName: null,
    recommendationCount: 0,
    hasTentativeAssignment: false,
    recommendationTarget: false,
    note: "연장 가능성 높음",
  };
  data.items[2].nextAssignment = {
    opportunityName: null,
    recommendationCount: 0,
    hasTentativeAssignment: false,
    recommendationTarget: false,
    note: "3개월 뒤 철수",
  };
  data.items[3].nextAssignment = {
    opportunityName: "끝난 뒤 기회",
    recommendationCount: 0,
    hasTentativeAssignment: true,
    recommendationTarget: false,
    note: null,
  };
  data.items.push({
    ...row("e", 20),
    nextAssignment: {
      opportunityName: null,
      recommendationCount: 0,
      hasTentativeAssignment: false,
      recommendationTarget: true,
      note: "맞는 자리 없음",
    },
  });
  data.items.push(row("f", 25));
  page();
  await ready();
  expect(screen.getByText("가배정 · 기존 기회")).toHaveClass("ls-b-info");
  expect(screen.getByTestId("withdrawal-replace-a")).toHaveTextContent("바꾸기");
  expect(screen.getByTestId("withdrawal-next-b")).toHaveTextContent(
    "– 연장 가능성 높음",
  );
  expect(screen.getByTestId("withdrawal-next-c")).toHaveTextContent(
    "– 3개월 뒤 철수",
  );
  expect(screen.queryByText("없음 · 맞는 자리 없음")).not.toBeInTheDocument();
  // 철수 완료라도 가배정이 있으면 배지는 보이고 [바꾸기]만 숨긴다.
  expect(screen.getByText("가배정 · 끝난 뒤 기회")).toHaveClass("ls-b-info");
  expect(screen.queryByTestId("withdrawal-replace-d")).not.toBeInTheDocument();
  const none = screen.getByTestId("withdrawal-next-e");
  expect(within(none).getByText("없음")).toHaveClass("project-bad");
  expect(within(none).getByText("맞는 자리 없음")).toHaveClass("ls-cap");
  const recommend = screen.getByTestId("withdrawal-recommend-f");
  expect(recommend).toHaveTextContent("추천 1곳");
  expect(recommend).toHaveClass("ls-btn", "ls-btn-sm");
  expect(recommend.querySelector("svg")).not.toBeNull();
});
it("the list has no column settings like the mockup", async () => {
  page();
  await ready();
  expect(screen.queryByTestId("list-columns")).not.toBeInTheDocument();
});
it("empty list renders its shared empty state with banners and filters", async () => {
  data.items = [];
  data.totalCount = 0;
  page();
  expect(await screen.findByTestId("list-empty")).toBeVisible();
  expect(screen.getByTestId("withdrawal-list")).toHaveTextContent("0");
  expect(screen.getByTestId("withdrawal-next-banner")).toBeVisible();
  expect(screen.getByTestId("withdrawal-info-banner")).toBeVisible();
});
it("failed request offers retry that restores data", async () => {
  failure = true;
  page();
  await screen.findByText("조회 실패");
  failure = false;
  fireEvent.click(screen.getByRole("button", { name: /다시 시도/ }));
  await ready();
});
it("focus and change notices reload without blanking the current rows", async () => {
  page();
  await ready();
  const before = listCalls().length;
  fireEvent.focus(window);
  expect(screen.getByTestId("withdrawal-extension-a")).toBeInTheDocument();
  expect(screen.queryByText("목록을 불러오는 중입니다.")).not.toBeInTheDocument();
  await waitFor(() => expect(listCalls().length).toBeGreaterThan(before));
  notifyWithdrawalChanged();
  expect(screen.getByTestId("withdrawal-extension-a")).toBeInTheDocument();
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
it("recommendation opens the shared right drawer with mockup header, cards and notes", async () => {
  page();
  await ready();
  fireEvent.click(screen.getByTestId("withdrawal-recommend-a"));
  const panel = await screen.findByTestId("withdrawal-recommendations");
  expect(panel).toHaveClass("ls-drawer");
  expect(panel).toHaveTextContent("사람 a — 다음 자리 추천");
  await within(panel).findByText("1. 다음 기회");
  expect(panel).toHaveTextContent("백엔드 · 중급 · 프로젝트 2026-11-30 철수");
  expect(within(panel).getByTestId("kv-row-기술")).toHaveTextContent("JavaSpring");
  expect(within(panel).getByTestId("kv-row-연장 가능성")).toHaveTextContent("보통");
  expect(within(panel).getByTestId("kv-row-대기 시작")).toHaveTextContent(
    "2026-12-01부터 (다음 자리가 없으면)",
  );
  expect(within(panel).getByTestId("withdrawal-recommendation-count")).toHaveTextContent("맞는 자리 1곳");
  const item = within(panel).getByTestId("withdrawal-card-opp-1");
  expect(item).toHaveTextContent("100/100");
  expect(item).toHaveTextContent(
    "백엔드 중급 · 2026-12 시작 · 남은 자리 1명 · 고객사 · 제안",
  );
  expect(item.querySelector(".withdrawal-fill")).toHaveStyle({ width: "100%" });
  for (const reason of ["직무 일치", "등급 일치", "공백 없음", "수주 확률 60%"])
    expect(within(item).getByText(reason)).toHaveClass("ls-chip");
  expect(item).toHaveTextContent("바로 이어서 투입");
  expect(within(panel).getByTestId("withdrawal-shortage")).toHaveTextContent(
    "백엔드 직무는 12월 0.6명 부족이 예상됩니다.",
  );
  expect(within(panel).getByTestId("withdrawal-score-note")).toHaveTextContent(
    "점수 = 직무(일치 40 · 관련 20) + 등급(일치 20 · 한 단계 차이 10)",
  );
  expect(within(panel).queryByRole("button", { name: "취소" })).not.toBeInTheDocument();
  expect(within(panel).queryByTestId("withdrawal-current-tentative")).not.toBeInTheDocument();
});
it("tentative save sends the opportunity and need line, toasts, and keeps the drawer open with 가배정됨", async () => {
  page();
  await ready();
  fireEvent.click(screen.getByTestId("withdrawal-recommend-a"));
  const panel = await screen.findByTestId("withdrawal-recommendations");
  fireEvent.click(await within(panel).findByTestId("withdrawal-reserve-opp-1"));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/withdrawals/a/tentative-assignment",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ opportunityId: "opp", needSn: 1 }),
      }),
    ),
  );
  expect(await screen.findByTestId("withdrawal-toast")).toHaveTextContent(
    "사람 a님을 「다음 기회」에 가배정했습니다. 인력 운영 보드에 점선으로 보입니다.",
  );
  await waitFor(() =>
    expect(within(panel).getByTestId("withdrawal-reserve-opp-1")).toHaveTextContent("가배정됨"),
  );
  expect(within(panel).getByTestId("withdrawal-reserve-opp-1")).toBeDisabled();
  expect(within(panel).getByTestId("withdrawal-reserve-opp-1")).not.toHaveClass("ls-btn-p");
  expect(within(panel).getByTestId("withdrawal-current-tentative")).toHaveTextContent(
    "지금 가배정: 다음 기회 (2026-12부터)",
  );
  expect(screen.getByTestId("withdrawal-recommendations")).toBeInTheDocument();
  expect(
    request.mock.calls.some(([url]) =>
      String(url).includes("/recommendations?replaceCurrent=true"),
    ),
  ).toBe(true);
});
it("duplicate save is disabled until its promise resolves", async () => {
  let resolve: ((value: unknown) => void) | undefined;
  request.mockImplementation(async (_url: string, init?: RequestInit) =>
    init?.method === "PUT"
      ? new Promise((done) => {
          resolve = done;
        })
      : ok({ ...recommendations, items: [{ ...card(), score: 80 }] }),
  );
  drawer(true);
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
it("no matching seat keeps the drawer frame, count 0, empty mockup text and score note", async () => {
  request.mockResolvedValue(
    ok({ items: [], shortages: [], target: { ...target(), skills: [] } }),
  );
  drawer();
  expect(await screen.findByTestId("withdrawal-recommendation-count")).toHaveTextContent(
    "맞는 자리 0곳",
  );
  expect(screen.getByTestId("withdrawal-recommendation-empty")).toHaveTextContent(
    "지금 진행 중인 영업 기회 가운데 맞는 자리가 없습니다. 영업 파이프라인과 인력 운영 보드를 함께 확인하세요.",
  );
  expect(screen.getByTestId("withdrawal-score-note")).toBeVisible();
  expect(screen.getByTestId("kv-row-기술")).toBeInTheDocument();
  expect(screen.queryByTestId("withdrawal-shortage")).not.toBeInTheDocument();
  expect(screen.queryByTestId("withdrawal-current-tentative")).not.toBeInTheDocument();
});
it("several shortage months share one line with one decimal", async () => {
  request.mockResolvedValue(
    ok({
      ...recommendations,
      shortages: [
        { month: "2026-12", jobName: "백엔드", shortageCount: 0.6 },
        { month: "2027-01", jobName: "백엔드", shortageCount: 1.25 },
      ],
    }),
  );
  drawer();
  expect(await screen.findByTestId("withdrawal-shortage")).toHaveTextContent(
    "백엔드 직무는 12월 0.6명, 1월 1.3명 부족이 예상됩니다.",
  );
});
it("recommendation failure provides retry", async () => {
  request.mockRejectedValue(new Error("추천 조회 실패"));
  drawer();
  expect(
    await screen.findByTestId("withdrawal-recommendation-error"),
  ).toHaveTextContent("추천 조회 실패");
  expect(screen.getByTestId("withdrawal-recommendation-retry")).toBeEnabled();
});
it("mobile cards follow the mockup and hold no controls", async () => {
  viewport.mobile = true;
  viewport.width = 375;
  data.items[1].nextAssignment.hasTentativeAssignment = true;
  page();
  const first = await screen.findByTestId("list-card-a");
  expect(first).toHaveTextContent("사람 a");
  expect(first).toHaveTextContent("백엔드");
  expect(within(first).getByText("D-30")).toHaveClass("ls-b-bad");
  expect(first).toHaveTextContent("프로젝트");
  expect(first).toHaveTextContent("2026-11-30");
  expect(first).toHaveTextContent("연장 보통");
  expect(first).toHaveTextContent("다음 배정 없음");
  expect(screen.getByTestId("list-card-b")).toHaveTextContent("가배정 있음");
  expect(within(screen.getByTestId("list-card-c")).getByText("D-61")).toHaveClass("ls-b-warn");
  expect(first.querySelector("select, button")).toBeNull();
  expect(screen.queryByTestId("withdrawal-extension-a")).not.toBeInTheDocument();
  fireEvent.click(first);
  expect(await screen.findByTestId("withdrawal-assignment-detail")).toHaveTextContent("a");
});

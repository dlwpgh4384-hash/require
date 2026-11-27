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
import * as cost from "../projectcostanalyses/projectCostAnalyses";
import { dashboardResult } from "./dashboardTestData";
import { ProjectRequestError } from "../projects/projects";

const period = vi.hoisted(() => ({
  latest: "202611",
  saved: { year: 2026, month: 11 } as { year: number; month?: number },
  setValue: null as unknown as ReturnType<typeof vi.fn>,
}));
vi.mock("../../shared/time", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return { ...actual, koreanToday: () => "2026-11-25" };
});
// 다른 월 화면처럼 개인 설정(PERIOD)에 남긴 달을 읽고, 바꾸면 setValue로 저장한다.
vi.mock("../userpreferences/useScreenPeriod", async () => {
  const { useState } = await import("react");
  return {
    useScreenPeriod: () => {
      const [value, setLocal] = useState(period.saved);
      return {
        state: {
          status: "ready",
          basis: {
            latestYm: period.latest,
            cashBaseYm: "202601",
            closedMonths: ["202610"],
          },
          value,
        },
        setValue: (next: { year: number; month?: number }) => {
          period.setValue(next);
          setLocal(next);
        },
      };
    },
  };
});
vi.mock("./dashboard", () => ({
  getCommonDashboard: vi.fn(),
  getCommonDashboardExcel: vi.fn(),
}));
vi.mock("../projectcostanalyses/projectCostAnalyses", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  getCommonProjectCostAnalyses: vi.fn(),
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
const location = () => screen.getByTestId("location");
beforeEach(() => {
  vi.clearAllMocks();
  period.latest = "202611";
  period.saved = { year: 2026, month: 11 };
  period.setValue = vi.fn();
  vi.mocked(api.getCommonDashboard).mockImplementation(async (month) =>
    dashboardResult(month),
  );
  vi.mocked(api.getCommonDashboardExcel).mockResolvedValue(37);
});
afterEach(cleanup);

it("요약 5칸: 시안 kp 글·설명과 누르면 가는 화면", async () => {
  mount();
  const revenue = await screen.findByTestId("dashboard-revenue");
  expect(revenue).toHaveTextContent("이번 달 매출");
  expect(revenue).toHaveTextContent("5.2억 원");
  expect(revenue).toHaveTextContent("▲ 3.2% 전월 대비 · 목표 75.0%");
  expect(within(revenue).getByText("▲ 3.2%")).toHaveClass("dashboard-ok");
  const profit = screen.getByTestId("dashboard-profit");
  expect(profit).toHaveTextContent("이번 달 영업이익");
  expect(profit).toHaveTextContent("이익률 −0.8% · ▼ 5.0% 전월 대비");
  expect(within(profit).getByText("▼ 5.0%")).toHaveClass("dashboard-bad");
  expect(screen.getByTestId("dashboard-cumulative")).toHaveTextContent(
    "누계 영업이익4,000만 원1~11월 · 목표 달성 –",
  );
  const utilization = screen.getByTestId("dashboard-utilization");
  expect(utilization).toHaveTextContent("가동률92.5%목표 90% 대비 +2.5%p · 대기 3명");
  const unpaid = screen.getByTestId("dashboard-unpaid");
  expect(unpaid).toHaveTextContent("미수금1.2억 원연체 1건 1,200만 원");
  expect(within(unpaid).getByText("연체 1건 1,200만 원")).toHaveClass(
    "dashboard-bad",
  );
  fireEvent.click(utilization);
  expect(location()).toHaveTextContent("/screens/UI-28");
  fireEvent.click(screen.getByTestId("dashboard-cumulative"));
  expect(location()).toHaveTextContent("/screens/UI-18");
  fireEvent.click(unpaid);
  expect(location()).toHaveTextContent("/screens/UI-13");
});

it("기준 줄은 시안 asof 글이고 작업중 배지를 다시 그리지 않는다", async () => {
  mount();
  const basis = await screen.findByTestId("dashboard-basis");
  expect(basis).toHaveTextContent(
    "오늘 2026-11-25 · 10월까지 마감· 잠정 11월은 마감 전이라 숫자는 잠정입니다",
  );
  expect(basis).not.toHaveTextContent("작업중");
  expect(basis).not.toHaveTextContent("오늘 기준");
  expect(within(basis).getByText("잠정")).toHaveClass("ls-b-warn");
});

it("확인할 일: 시안 한 줄 목록, 0건 줄은 숨기고 철수 줄은 남긴다", async () => {
  mount();
  const list = await screen.findByTestId("dashboard-todos");
  expect(screen.getByTestId("dashboard-todo-count")).toHaveTextContent(
    "2건 · 급함 1건",
  );
  expect(within(list).getByText("미수 연체 1건")).toBeVisible();
  expect(within(list).getByText("1,200만 원 · 알파")).toBeVisible();
  expect(within(list).getByText("3개월 안 철수 예정 0명")).toBeVisible();
  expect(within(list).queryByText("MM 1.0 초과 0명")).not.toBeInTheDocument();
  expect(
    within(list).queryByText("영업 파트 미지정 프로젝트 0건"),
  ).not.toBeInTheDocument();
  const first = screen.getByTestId("dashboard-todo-0");
  expect(first).toHaveTextContent("청구·입금");
  expect(first.querySelector(".dashboard-bad")).not.toBeNull();
  expect(screen.getByTestId("dashboard-todo-1")).toHaveTextContent(
    "철수 리스트",
  );
  fireEvent.click(first);
  expect(location()).toHaveTextContent("/screens/UI-13");
});

it("마감 진행과 프로젝트 경보: 시안 글·막대·배지와 이동", async () => {
  mount();
  const close = await screen.findByTestId("dashboard-close");
  expect(within(close).getByRole("heading")).toHaveTextContent("11월 마감 진행");
  expect(screen.getByTestId("dashboard-close-count")).toHaveTextContent("5/6");
  expect(within(close).getByRole("progressbar")).toHaveAttribute(
    "aria-valuenow",
    "5",
  );
  expect(screen.getByTestId("dashboard-close-0")).toHaveTextContent(
    "●청구 누락 점검 2건 — 투입 대비 청구 확인",
  );
  fireEvent.click(within(close).getByRole("button", { name: "월 마감" }));
  expect(location()).toHaveTextContent("/screens/UI-14");
  const alerts = screen.getByTestId("dashboard-alerts");
  expect(within(alerts).getByRole("heading")).toHaveTextContent(
    "프로젝트 경보 1건",
  );
  const row = screen.getByTestId("dashboard-alert-P-1");
  expect(row).toHaveTextContent("알파 차세대");
  expect(row).toHaveTextContent("예상 최종 마진율 5.2% · 예산 소진 104.3%");
  expect(row).toHaveTextContent("주원인: 외주비 (300만 원)");
  expect(within(row).getByText("예산 초과")).toHaveClass("ls-b-bad");
  fireEvent.click(within(alerts).getByRole("button", { name: "자세히" }));
  expect(location()).toHaveTextContent("/screens/UI-04");
});

it("경보를 누르면 서버가 준 경보 달(첫 미마감 달)로 원가 상세를 묻는다", async () => {
  // 9월까지 마감, 오늘 2026-11-25: 원가 분석은 10월까지만 받으니 10월로 묻는다.
  vi.mocked(api.getCommonDashboard).mockImplementation(async (month) =>
    dashboardResult(month, {
      lastClosedMonth: "2026-09",
      asOfText: "오늘 2026-11-25 · 9월까지 마감",
      projectAlertMonth: "2026-10",
    }),
  );
  vi.mocked(cost.getCommonProjectCostAnalyses).mockRejectedValue(
    new Error("stop"),
  );
  mount();
  fireEvent.click(await screen.findByTestId("dashboard-alert-P-1"));
  await waitFor(() =>
    expect(cost.getCommonProjectCostAnalyses).toHaveBeenCalledTimes(1),
  );
  expect(vi.mocked(cost.getCommonProjectCostAnalyses).mock.calls[0][0]).toEqual(
    { year: 2026, baseMonth: 10, projectId: "P-1" },
  );
});

it("자금 부족 확인할 일 제목은 시안대로 yyyy-MM 달을 쓴다", async () => {
  const base = dashboardResult("2026-11");
  vi.mocked(api.getCommonDashboard).mockImplementation(async (month) =>
    dashboardResult(month, {
      todos: [
        ...base.todos,
        {
          key: "cashShortage",
          title: "2026-12 자금 부족 예상",
          count: 1,
          amount: 800_000_000,
          description: "월말 잔액 8.0억 원 (최소 운영자금 10.0억 원)",
          targetScreenId: "UI-21",
          targetScreenName: "자금수지표",
          tone: "bad",
          urgent: false,
          alwaysVisible: false,
          visible: true,
        },
      ],
    }),
  );
  mount();
  const list = await screen.findByTestId("dashboard-todos");
  expect(within(list).getByText("2026-12 자금 부족 예상")).toBeVisible();
});

it("남은 점검·경보가 없으면 시안 빈 글", async () => {
  vi.mocked(api.getCommonDashboard).mockImplementation(async (month) =>
    dashboardResult(month, {
      closing: { completedCount: 6, totalCount: 6, remainingItems: [] },
      projectAlerts: [],
    }),
  );
  mount();
  expect(await screen.findByText("남은 점검이 없습니다.")).toBeVisible();
  expect(screen.getByText("경보가 없습니다.")).toBeVisible();
  expect(screen.getByTestId("dashboard-close-count")).toHaveTextContent("6/6");
});

it("추이·수급: 1~12월 축, 고른 달까지 숫자 표, 부족 예상·가정 글", async () => {
  mount();
  const trend = await screen.findByTestId("dashboard-trend");
  expect(trend).toHaveTextContent("단위: 백만 원 · 2026년");
  const chart = screen.getByTestId("dashboard-trend-chart");
  expect(within(chart as unknown as HTMLElement).getByText("1월")).toBeTruthy();
  expect(within(chart as unknown as HTMLElement).getByText("12월")).toBeTruthy();
  expect(trend.querySelectorAll(".profit-legend")).toHaveLength(1);
  expect(trend.querySelector("caption")).toBeNull();
  expect(screen.getByTestId("dashboard-trend-2026-11")).toHaveTextContent(
    "2026-11500,000,000400,000,000600,000,000",
  );
  expect(screen.queryByTestId("dashboard-trend-2026-12")).not.toBeInTheDocument();
  const supply = screen.getByTestId("dashboard-supply");
  expect(supply).toHaveTextContent("앞으로 6개월 인력 수급 예측");
  expect(supply).toHaveTextContent("단위: 명(MM)");
  expect(screen.getByTestId("dashboard-supply-gap")).toHaveTextContent(
    "부족 예상: 1월 1.5명",
  );
  expect(supply).toHaveTextContent(
    "가정: 연장 가능성 높음 80%·보통 50%·낮음 0%, 영업 단계 확률 10/30/60/90%",
  );
  expect(supply).toHaveTextContent("필요 인력(확률 반영)");
  expect(screen.getByTestId("dashboard-manpower-2027-01")).toHaveTextContent(
    "2027-0120.021.5−1.5",
  );
});

it("하단 3칸: 파트별 막대, 파이프라인 두 숫자·기회 줄, 자금 3개월", async () => {
  mount();
  const parts = await screen.findByTestId("dashboard-parts");
  expect(within(parts).getByRole("heading")).toHaveTextContent(
    "영업 파트별 이번 달",
  );
  expect(screen.getAllByTestId("dashboard-part-rows-row")[0]).toHaveTextContent(
    "1파트3.0억 원 (75.0%) · 이익률 12.5%",
  );
  const pipeline = screen.getByTestId("dashboard-pipeline");
  expect(pipeline).toHaveTextContent("진행 중 기회3건");
  expect(pipeline).toHaveTextContent("확률 반영 예상 매출1.5억 원");
  expect(pipeline.querySelector("h3")).toBeNull();
  expect(screen.getByTestId("dashboard-upcoming-0")).toHaveTextContent(
    "베타 차세대 26.12월 시작제안 60%",
  );
  const cash = screen.getByTestId("dashboard-cash");
  expect(within(cash).getByRole("heading")).toHaveTextContent(
    "자금 (앞으로 3개월)",
  );
  const short = screen.getByTestId("dashboard-cash-2027-01");
  expect(short).toHaveTextContent("1월 말 잔액 예정8.0억 원");
  expect(within(short).getByText("8.0억 원")).toHaveClass("dashboard-bad");
  expect(
    within(screen.getByTestId("dashboard-cash-2026-12")).getByText("15.0억 원"),
  ).not.toHaveClass("dashboard-bad");
  expect(cash).toHaveTextContent(
    "최소 운영자금 10.0억 원. 빨간색은 그보다 적을 것으로 예상되는 달.",
  );
  fireEvent.click(within(parts).getByRole("button", { name: "자세히" }));
  expect(location()).toHaveTextContent("/screens/UI-07");
  fireEvent.click(within(pipeline).getByRole("button", { name: "자세히" }));
  expect(location()).toHaveTextContent("/screens/UI-29");
  fireEvent.click(within(cash).getByRole("button", { name: "자세히" }));
  expect(location()).toHaveTextContent("/screens/UI-21");
});

it("지난달을 고르면 n월 글·오늘 기준 안내, 고른 달은 개인 설정에 남긴다", async () => {
  mount();
  await screen.findByTestId("dashboard-revenue");
  const selector = within(screen.getByTestId("dashboard-page")).getByRole(
    "combobox",
  );
  const option = within(selector)
    .getAllByRole("option")
    .find((o) => o.textContent?.includes("10월"));
  fireEvent.change(selector, {
    target: { value: (option as HTMLOptionElement).value },
  });
  expect(period.setValue).toHaveBeenCalledWith({ year: 2026, month: 10 });
  await waitFor(() =>
    expect(api.getCommonDashboard).toHaveBeenLastCalledWith(
      "2026-10",
      expect.any(AbortSignal),
    ),
  );
  expect(await screen.findByText("10월 매출")).toBeVisible();
  expect(screen.getByText("미수금 (오늘)")).toBeVisible();
  expect(screen.getByTestId("dashboard-basis")).toHaveTextContent(
    "미수금·확인할 일·프로젝트 경보·인력 수급·파이프라인·자금은 오늘 기준(청구 누락 의심은 고른 달)",
  );
});

it("저장된 달로 연다(다른 월 화면처럼)", async () => {
  period.saved = { year: 2026, month: 9 };
  mount();
  await screen.findByTestId("dashboard-revenue");
  expect(api.getCommonDashboard).toHaveBeenCalledWith(
    "2026-09",
    expect.any(AbortSignal),
  );
  expect(screen.getByText("9월 매출")).toBeVisible();
});

it("엑셀 다운로드 단추가 없다(55번 3-5 사용자 결정)", async () => {
  mount();
  await screen.findByTestId("dashboard-revenue");
  expect(screen.queryByTestId("dashboard-excel")).not.toBeInTheDocument();
  expect(screen.queryByText("엑셀 다운로드")).not.toBeInTheDocument();
  expect(api.getCommonDashboardExcel).not.toHaveBeenCalled();
  expect(
    screen.queryByRole("button", { name: /저장|등록|업로드|삭제/ }),
  ).not.toBeInTheDocument();
});

it("칸 오류를 보이고 다시 시도하면 깨끗이 다시 읽는다", async () => {
  vi.mocked(api.getCommonDashboard).mockRejectedValueOnce(
    new ProjectRequestError("기준월 오류", [
      { field: "yearMonth", message: "기간 확인" },
    ]),
  );
  mount();
  expect(await screen.findByTestId("dashboard-month-error")).toHaveTextContent(
    "기간 확인",
  );
  fireEvent.click(screen.getByTestId("dashboard-retry"));
  await screen.findByTestId("dashboard-revenue");
  expect(screen.queryByTestId("dashboard-month-error")).not.toBeInTheDocument();
});
it("이번 달이 허용 범위 밖이면 최근 허용월과 안내", async () => {
  period.latest = "202610";
  mount();
  await screen.findByTestId("dashboard-revenue");
  expect(api.getCommonDashboard).toHaveBeenCalledWith(
    "2026-10",
    expect.any(AbortSignal),
  );
  expect(screen.getByText(/최근 허용월을 표시합니다/)).toBeVisible();
});

it("늦게 온 앞 달 응답은 나중에 고른 달을 덮지 않는다", async () => {
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
  expect(await screen.findByText("10월 매출")).toBeVisible();
  await act(async () => finishOld(dashboardResult("2026-11")));
  expect(screen.getByText("10월 매출")).toBeVisible();
  expect(screen.queryByText("이번 달 매출")).not.toBeInTheDocument();
});

it.each(["part", "pm"] as const)(
  "%s 모양(명세 ⑧-4): 서버가 비운 요약·마감·추이·파트별·파이프라인·자금은 그리지 않는다",
  async (mode) => {
    vi.mocked(api.getCommonDashboard).mockImplementation(async (month) =>
      dashboardResult(month, {
        dashMode: mode,
        summary: null,
        closing: null,
        pipeline: null,
        minimumCash: null,
        cash: [],
        trend: [],
        salesParts: [],
      }),
    );
    mount();
    expect(await screen.findByTestId("dashboard-mode-notice")).toHaveTextContent(
      mode === "part" ? "담당 파트 기준으로 봅니다" : "맡은 프로젝트 기준으로 봅니다",
    );
    expect(screen.getByTestId("dashboard-todos")).toBeInTheDocument();
    expect(screen.getByTestId("dashboard-alerts")).toBeInTheDocument();
    expect(screen.getByTestId("dashboard-supply")).toHaveTextContent(
      "앞으로 6개월 인력 수급 (회사 전체)",
    );
    for (const id of [
      "dashboard-revenue",
      "dashboard-close",
      "dashboard-trend",
      "dashboard-parts",
      "dashboard-pipeline",
      "dashboard-cash",
    ])
      expect(screen.queryByTestId(id)).not.toBeInTheDocument();
    expect(screen.getByTestId("dashboard-page")).not.toHaveTextContent("NaN");
  },
);

it("전체 모양은 안내 띠 없이 그대로", async () => {
  vi.mocked(api.getCommonDashboard).mockImplementation(async (month) =>
    dashboardResult(month, { dashMode: "all" }),
  );
  mount();
  expect(await screen.findByTestId("dashboard-revenue")).toBeInTheDocument();
  expect(screen.queryByTestId("dashboard-mode-notice")).not.toBeInTheDocument();
  expect(screen.getByTestId("dashboard-supply")).not.toHaveTextContent("회사 전체");
});

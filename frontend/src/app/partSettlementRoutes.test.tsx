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
import { RouterProvider } from "react-router-dom";
import { createAppRouter } from "./router";
import type { ShellMenuResponse } from "./NavigationRoot";
import type {
  PartSettlement,
  PartSettlementSummary,
} from "../pages/partsettlements/partSettlements";
import type { Developer } from "../pages/developers/developers";

let router: ReturnType<typeof createAppRouter> | undefined;
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data: structuredClone(data) }),
});
beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
});
afterEach(() => {
  cleanup();
  router?.dispose();
  router = undefined;
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function respond(developerId = "DEV-selected", available = true) {
  const menus: ShellMenuResponse = {
    success: true,
    data: [
      {
        menuId: "G006",
        upperMenuId: null,
        menuNm: "원가·수익",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 6,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "M016",
        upperMenuId: "G006",
        menuNm: "영업 파트 이익금",
        scrnId: "UI-19",
        menuLvl: 2,
        sortOrd: 3,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "G008",
        upperMenuId: null,
        menuNm: "마감",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 8,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "M024",
        upperMenuId: "G008",
        menuNm: "AI 분석 도우미",
        scrnId: "UI-26",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [
        { scrnId: "UI-19", available, pcOnlyYn: "N" },
        { scrnId: "UI-26", available: true, pcOnlyYn: "N" },
      ],
    },
  };
  const summary: PartSettlementSummary = {
    leaderName: "가파트장",
    distributionRate: 40,
    salesExpense: 1500000,
    personCount: 1,
    profitTotal: 2000000,
    partProfitAmount: 800000,
  };
  const parts = [
    { partId: "PART-first", partName: "영업 가파트", summary },
    {
      partId: "PART-selected",
      partName: "영업 나파트",
      summary: { ...summary, leaderName: "나파트장" },
    },
  ];
  const developer: Developer = {
    developerId,
    managementNumber: "선택 관리번호",
    name: "선택 개발자",
    employeeTypeCode: "FT",
    employeeTypeName: "정규직",
    organizationId: "",
    organizationName: "자사",
    primaryJobCode: "DEV",
    primaryJobName: "개발",
    directClassCode: "DRCT",
    directClassName: "직접",
    salesPartId: parts[1].partId,
    salesPartName: parts[1].partName,
    workStatusName: "재직",
    retirementScheduled: false,
    retirementDate: null,
    useYn: "Y",
    skillNames: [],
  };
  const tabs = vi.fn(async (yearMonth: string) =>
    ok({ yearMonth, items: parts, notice: null }),
  );
  const settlement = vi.fn(async (partId: string, yearMonth: string) => {
    const part = parts.find((item) => item.partId === partId)!;
    const data: PartSettlement = {
      ...part,
      yearMonth,
      corpCardAmount: 0,
      confirmed: false,
      closed: false,
      confirmedBy: null,
      confirmedAt: null,
      confirmationNote: null,
      modifiedAt: null,
      items: [
        {
          developerId,
          developerName: developer.name,
          employeeTypeCode: "FT",
          projectNames: ["선택 프로젝트"],
          monthlyMm: 1,
          unitPrice: 8000000,
          salary: 4000000,
          salesExpense: 1500000,
          retirementAmount: 333333,
          totalCost: 5833333,
          profit: 2166667,
          profitShare: 866666,
          notes: [],
          missingMonthlySalary: false,
        },
      ],
    };
    return ok(data);
  });
  const detail = vi.fn(async () => ok(developer));
  const request = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/api/common/menus")
      return { ...ok(null), json: async () => menus };
    if (url === "/api/common/ai-analysis/questions") return ok({ items: [] });
    if (url.startsWith("/api/common/user-preferences/")) {
      const input = init?.body ? JSON.parse(String(init.body)) : null;
      return ok(
        {
          prefTypeCd: url.split("/").at(-2),
          prefKey: url.split("/").at(-1),
          found: !!input,
          prefVal: input?.prefVal ?? null,
          modDttm: null,
        },
        {
          periodBasis: {
            latestYm: "202602",
            cashBaseYm: "202501",
            closedMonths: [],
          },
        },
      );
    }
    if (url === "/api/common/code-groups/EMP_TYPE/codes")
      return ok({
        items: [{ code: "FT", codeName: "정규직", sortOrd: 1, useYn: "Y" }],
      });
    if (url.startsWith("/api/common/part-settlements?"))
      return tabs(new URLSearchParams(url.split("?")[1]).get("yearMonth")!);
    const confirm =
      /^\/api\/common\/part-settlements\/([^/]+)\/(\d{4}-\d{2})\/confirm$/.exec(
        url,
      );
    if (confirm && init?.method === "PUT") {
      const response = await settlement(
        decodeURIComponent(confirm[1]),
        confirm[2],
      );
      const data = (await response.json()).data as PartSettlement;
      return ok({
        ...data,
        confirmed: true,
        confirmedBy: "admin",
        confirmedByName: "관리자",
        confirmedAt: "2026-02-28T01:30:00Z",
        modifiedAt: "2026-02-28T01:30:00Z",
      });
    }
    const match =
      /^\/api\/common\/part-settlements\/([^/]+)\/(\d{4}-\d{2})$/.exec(url);
    if (match) return settlement(decodeURIComponent(match[1]), match[2]);
    if (url === `/api/common/developers/${encodeURIComponent(developerId)}`)
      return detail();
    if (
      ["assignments", "statuses", "change-history"].some(
        (suffix) =>
          url ===
          `/api/common/developers/${encodeURIComponent(developerId)}/${suffix}`,
      )
    )
      return ok({
        items: [],
        canManageStatuses: true,
        canCancelPastRetirement: true,
      });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  return { request, tabs, settlement, detail, developer, parts };
}
function mount(path: string) {
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
function expectSingleShell() {
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  // 시안 UI-26 질문 기록(aside「질문 기록」)은 화면 안 칸이라 공통 틀의 왼쪽 메뉴 수에서 뺀다.
  expect(
    screen.getAllByRole("complementary", {
      name: (name) => name !== "질문 기록",
    }),
  ).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
}

it.each(["/screens/UI-19", "/#UI-19"])(
  "%s resolves UI-19 through the existing router and direct public client",
  async (path) => {
    const { request, tabs, settlement, developer } = respond();
    mount(path);
    await screen.findByTestId(`list-row-${developer.developerId}`);
    expect(screen.getAllByTestId("part-settlement-page")).toHaveLength(1);
    expect(screen.getByTestId("part-settlement-page")).toHaveAttribute(
      "data-screen-id",
      "UI-19",
    );
    expect(tabs).toHaveBeenCalledWith("2026-02");
    expect(settlement).toHaveBeenCalledWith("PART-first", "2026-02");
    expect(request).toHaveBeenCalledWith(
      "/api/common/part-settlements?yearMonth=2026-02",
      {
        credentials: "same-origin",
        signal: expect.any(AbortSignal),
      },
    );
    expect(
      request.mock.calls.some(([url]) => url.startsWith("/api/auth/")),
    ).toBe(false);
    expect(router?.state.location.pathname).toBe("/screens/UI-19");
    expectSingleShell();
  },
);

it("M016 opens UI-19 from the analysis page in the existing preference provider", async () => {
  const { request, developer } = respond();
  mount("/screens/UI-26");
  expect(await screen.findByTestId("ai-analysis-page")).toBeVisible();
  const preferences = screen.getByTestId("user-preferences");
  expect(
    request.mock.calls.some(([url]) => url.includes("part-settlement")),
  ).toBe(false);
  fireEvent.click(screen.getByTestId("menu-toggle-g006"));
  fireEvent.click(screen.getByRole("link", { name: "영업 파트 이익금" }));
  await screen.findByTestId(`list-row-${developer.developerId}`);
  expect(router?.state.location.pathname).toBe("/screens/UI-19");
  expect(screen.getByTestId("user-preferences")).toBe(preferences);
  await act(async () => {
    await router!.navigate("/screens/UI-26");
  });
  expect(screen.getByTestId("ai-analysis-page")).toBeVisible();
  expect(
    request.mock.calls.some(([url]) =>
      url.startsWith("/api/common/part-settlement-close-checks"),
    ),
  ).toBe(false);
  expectSingleShell();
});

it.each([
  { developerId: "DEV-selected", interaction: "click" },
  { developerId: "DEV-another", interaction: "enter" },
])(
  "$interaction on the row opens $developerId in the real DetailPanel and preserves the selected part and month",
  async ({ developerId, interaction }) => {
    const { request, settlement, parts } = respond(developerId);
    mount("/screens/UI-19");
    await screen.findByTestId(`list-row-${developerId}`);
    fireEvent.click(screen.getByRole("tab", { name: parts[1].partName }));
    await screen.findByText("나파트장");
    fireEvent.change(screen.getByRole("combobox", { name: "조회 월" }), {
      target: { value: "2026-1" },
    });
    await waitFor(() =>
      expect(settlement).toHaveBeenLastCalledWith(parts[1].partId, "2026-01"),
    );
    const row = await screen.findByTestId(`list-row-${developerId}`);
    const reads = settlement.mock.calls.length;
    if (interaction === "enter") fireEvent.keyDown(row, { key: "Enter" });
    else fireEvent.click(within(row).getByText("선택 프로젝트"));
    const panel = within(await screen.findByTestId("developer-detail"));
    expect(
      await panel.findByText("선택 관리번호 · 자사 · 정규직"),
    ).toBeVisible();
    for (const suffix of ["", "/assignments", "/statuses", "/change-history"])
      expect(request).toHaveBeenCalledWith(
        `/api/common/developers/${developerId}${suffix}`,
        undefined,
      );
    expect(
      panel.queryByRole("button", { name: "수정" }),
    ).not.toBeInTheDocument();
    expect(
      panel.queryByRole("button", { name: "사용 중지" }),
    ).not.toBeInTheDocument();
    fireEvent.click(panel.getByRole("tab", { name: "근무 상태 이력" }));
    expect(
      panel.queryByRole("button", { name: "상태 등록" }),
    ).not.toBeInTheDocument();
    fireEvent.click(panel.getByRole("button", { name: "닫기" }));
    expect(screen.queryByTestId("developer-detail")).not.toBeInTheDocument();
    expect(
      screen.getByRole("tab", { name: parts[1].partName }),
    ).toHaveAttribute("aria-selected", "true");
    expect(
      screen.getByRole("combobox", { name: "조회 월" }),
    ).toHaveDisplayValue("2026년 1월");
    expect(screen.getByTestId(`list-row-${developerId}`)).toBe(row);
    expect(settlement).toHaveBeenCalledTimes(reads);
    expect(
      request.mock.calls
        .filter(([url]) => url.startsWith("/api/common/developers/"))
        .every(([, init]) => !init?.method || init.method === "GET"),
    ).toBe(true);
    expect(router?.state.location.pathname).toBe("/screens/UI-19");
    expectSingleShell();
  },
);

it("the name button reuses the selected developer client and Escape closes only the detail", async () => {
  const { developer, detail, settlement } = respond();
  mount("/screens/UI-19");
  fireEvent.click(
    await screen.findByTestId(
      `part-settlement-person-${developer.developerId}`,
    ),
  );
  await within(await screen.findByTestId("developer-detail")).findByText(
    "선택 관리번호 · 자사 · 정규직",
  );
  expect(detail).toHaveBeenCalledTimes(1);
  const reads = settlement.mock.calls.length;
  fireEvent.keyDown(window, { key: "Escape" });
  expect(screen.queryByTestId("developer-detail")).not.toBeInTheDocument();
  expect(screen.getByTestId(`list-row-${developer.developerId}`)).toBeVisible();
  expect(settlement).toHaveBeenCalledTimes(reads);
});

it("developer read failure stays local and retries the same identity without refreshing settlement", async () => {
  const { detail, settlement, developer } = respond();
  detail.mockRejectedValueOnce(new Error("개발자 조회 연결 실패"));
  mount("/screens/UI-19");
  fireEvent.click(
    await screen.findByTestId(
      `part-settlement-person-${developer.developerId}`,
    ),
  );
  const panel = within(await screen.findByTestId("developer-detail"));
  expect(await panel.findByText("개발자 조회 연결 실패")).toBeVisible();
  expect(screen.getByTestId(`list-row-${developer.developerId}`)).toBeVisible();
  expect(screen.queryByTestId("list-empty")).not.toBeInTheDocument();
  const reads = settlement.mock.calls.length;
  fireEvent.click(panel.getByRole("button", { name: "다시 시도" }));
  expect(await panel.findByText("선택 관리번호 · 자사 · 정규직")).toBeVisible();
  expect(detail).toHaveBeenCalledTimes(2);
  expect(settlement).toHaveBeenCalledTimes(reads);
});

it("dirty row navigation uses the existing discard guard and cancel retains the card draft", async () => {
  const { developer, detail } = respond();
  mount("/screens/UI-19");
  const row = await screen.findByTestId(`list-row-${developer.developerId}`);
  fireEvent.change(screen.getByTestId("part-settlement-card"), {
    target: { value: "123" },
  });
  await waitFor(() =>
    expect(screen.getByTestId("screen-tab-ui-19")).toHaveTextContent("●"),
  );
  fireEvent.click(row);
  expect(screen.getByRole("alertdialog")).toBeVisible();
  expect(detail).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("confirmation-cancel"));
  expect(screen.queryByTestId("developer-detail")).not.toBeInTheDocument();
  expect(screen.getByTestId("part-settlement-card")).toHaveValue("123");
});

it("unavailable UI-19 remains behind the shared navigation guard before feature reads", async () => {
  const { tabs, settlement, detail } = respond("DEV-selected", false);
  mount("/screens/UI-19");
  expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
  expect(screen.queryByTestId("part-settlement-page")).not.toBeInTheDocument();
  expect(tabs).not.toHaveBeenCalled();
  expect(settlement).not.toHaveBeenCalled();
  expect(detail).not.toHaveBeenCalled();
  expectSingleShell();
});

it("in the real shell the title bar holds [정산 확정]; confirming asks first and then shows the lock banner and [확정 취소]", async () => {
  const { request, developer } = respond();
  mount("/screens/UI-19");
  await screen.findByTestId(`list-row-${developer.developerId}`);
  expect(
    await within(
      screen.getByTestId(`list-row-${developer.developerId}`),
    ).findByText("정규직"),
  ).toBeVisible();
  expect(
    screen.queryByTestId("part-settlement-banner"),
  ).not.toBeInTheDocument();
  const bar = within(screen.getByTestId("part-settlement-title-actions"));
  fireEvent.click(bar.getByRole("button", { name: "정산 확정" }));
  const dialog = screen.getByRole("alertdialog", {
    name: "영업 가파트 2026-02 정산을 확정할까요?",
  });
  expect(
    request.mock.calls.some(
      ([url, init]) =>
        String(url).includes("part-settlements") && init?.method === "PUT",
    ),
  ).toBe(false);
  fireEvent.click(within(dialog).getByRole("button", { name: "확정하기" }));
  expect(await screen.findByText("정산을 확정했습니다.")).toBeVisible();
  expect(screen.getByTestId("part-settlement-banner")).toHaveTextContent(
    "2026-02 영업 가파트 정산은 확정되었습니다 (관리자 · 2026-02-28 10:30).",
  );
  expect(bar.getByRole("button", { name: "확정 취소" })).toBeVisible();
  expect(
    bar.queryByRole("button", { name: "엑셀 다운로드" }),
  ).not.toBeInTheDocument();
  expectSingleShell();
});

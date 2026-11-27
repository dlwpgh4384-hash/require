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
import { PartSettlementPage } from "./PartSettlementPage";
import {
  PartSettlementTitleActions,
  PartSettlementWorkspaceProvider,
} from "./PartSettlementWorkspace";
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import type {
  PartSettlement,
  PartSettlementRow,
  PartSettlementTabs,
} from "./partSettlements";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
vi.mock("../developers/DeveloperDetail", () => ({
  DeveloperDetail: ({
    developerId,
    onClose,
  }: {
    developerId: string;
    onClose: () => void;
  }) => (
    <aside data-testid="part-settlement-person-detail">
      {developerId}
      <button onClick={onClose}>상세 닫기</button>
    </aside>
  ),
}));
let fetcher: ReturnType<typeof vi.fn>;
let tabs: PartSettlementTabs;
let detail: PartSettlement;
let failed: boolean;
let validation: { field: string; message: string } | null;
let putStatus: number;
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
const dirty = vi.fn();
const firstRow: PartSettlementRow = {
  developerId: "D",
  developerName: "개발자 가",
  employeeTypeCode: "FT",
  projectNames: ["가 프로젝트", "나 프로젝트"],
  monthlyMm: 1,
  unitPrice: 3500000,
  salary: 4000000,
  salesExpense: 1500000,
  retirementAmount: 333333,
  totalCost: 5833333,
  profit: -2333333,
  profitShare: -933333,
  notes: ["시작 달 16/31일", "⚠ 월 급여 없음(시작 달 급여 0)"],
  missingMonthlySalary: true,
};
const secondRow: PartSettlementRow = {
  developerId: "E",
  developerName: "외주 나",
  employeeTypeCode: "OS",
  projectNames: ["턴키 프로젝트"],
  monthlyMm: 0.5,
  unitPrice: 5000000,
  salary: 4000000,
  salesExpense: 0,
  retirementAmount: 0,
  totalCost: 4000000,
  profit: 1000000,
  profitShare: 400000,
  notes: [],
  missingMonthlySalary: false,
};
beforeEach(() => {
  failed = false;
  validation = null;
  putStatus = 400;
  dirty.mockClear();
  detail = {
    partId: "A",
    partName: "영업 가파트",
    yearMonth: "2026-02",
    summary: {
      leaderName: "가파트장",
      distributionRate: 40,
      salesExpense: 1500000,
      personCount: 1,
      profitTotal: -2333333,
      partProfitAmount: -933333,
    },
    items: [firstRow],
    corpCardAmount: 0,
    confirmed: false,
    closed: false,
    confirmedBy: null,
    confirmedAt: null,
    confirmationNote: null,
    modifiedAt: null,
  };
  tabs = {
    yearMonth: "2026-02",
    items: [
      { partId: "A", partName: "영업 가파트", summary: detail.summary },
      {
        partId: "B",
        partName: "영업 나파트",
        summary: { ...detail.summary, distributionRate: 50 },
      },
    ],
    notice: null,
  };
  fetcher = vi.fn(async (input: string, init?: RequestInit) => {
    const url = new URL(input, "http://application.test");
    if (url.pathname.startsWith("/api/common/user-preferences/")) {
      const [, , , , prefTypeCd, prefKey] = url.pathname.split("/");
      return ok(
        { prefTypeCd, prefKey, found: false, prefVal: null, modDttm: null },
        {
          periodBasis: {
            latestYm: "202602",
            cashBaseYm: "202501",
            closedMonths: [],
          },
        },
      );
    }
    if (url.pathname === "/api/common/code-groups/EMP_TYPE/codes")
      return ok({
        items: [
          { code: "FT", codeName: "정규직", sortOrd: 1, useYn: "Y" },
          { code: "CT", codeName: "계약직", sortOrd: 2, useYn: "Y" },
        ],
      });
    if (init?.method === "PUT") {
      if (validation)
        return {
          ok: false,
          status: putStatus,
          json: async () => ({
            success: false,
            message: validation?.message,
            errors: [validation],
          }),
        };
      const body = JSON.parse(init.body as string);
      if (url.pathname.endsWith("/card"))
        detail = {
          ...detail,
          corpCardAmount: body.corpCardAmount,
          modifiedAt: "2026-02-01T00:00:00Z",
        };
      if (url.pathname.endsWith("/confirm"))
        detail = {
          ...detail,
          confirmed: true,
          confirmedBy: "admin",
          confirmedByName: "관리자",
          confirmedAt: "2026-02-01T00:00:00Z",
          modifiedAt: "2026-02-01T00:00:00Z",
        };
      if (url.pathname.endsWith("/cancel"))
        detail = {
          ...detail,
          confirmed: false,
          confirmedBy: null,
          confirmedByName: null,
          confirmedAt: null,
          modifiedAt: "2026-02-02T00:00:00Z",
        };
      return ok(detail);
    }
    if (failed) throw new Error("조회 실패");
    if (url.pathname === "/api/common/part-settlements") return ok(tabs);
    if (url.pathname.endsWith("/trends"))
      return ok({
        partId: "A",
        yearMonth: "2026-02",
        items: [
          {
            developerId: "D",
            developerName: "개발자 가",
            months: [
              {
                yearMonth: "2026-01",
                monthlyMm: 1,
                unitPrice: 3500000,
                totalCost: 5833333,
                profit: -2333333,
                profitShare: -933333,
              },
              {
                yearMonth: "2026-02",
                monthlyMm: 0.5,
                unitPrice: 2000000,
                totalCost: 1750000,
                profit: 250000,
                profitShare: 100000,
              },
            ],
          },
          {
            developerId: "E",
            developerName: "외주 나",
            months: [
              {
                yearMonth: "2026-01",
                monthlyMm: 0,
                unitPrice: 0,
                totalCost: 0,
                profit: 0,
                profitShare: 0,
              },
              {
                yearMonth: "2026-02",
                monthlyMm: 1,
                unitPrice: 5000000,
                totalCost: 4000000,
                profit: 1000000,
                profitShare: 400000,
              },
            ],
          },
        ],
        monthlyTotals: [
          { yearMonth: "2026-01", partProfitAmount: -1033333 },
          { yearMonth: "2026-02", partProfitAmount: 400000 },
        ],
      });
    if (url.pathname.includes("/B/"))
      return ok({
        ...detail,
        partId: "B",
        partName: "영업 나파트",
        summary: {
          ...detail.summary,
          leaderName: "나파트장",
          distributionRate: 50,
          salesExpense: 2000000,
        },
      });
    return ok(detail);
  });
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
function mount() {
  return render(
    <MemoryRouter>
      <ScreenDirtyContext.Provider value={dirty}>
        <PartSettlementWorkspaceProvider>
          <PartSettlementTitleActions />
          <PartSettlementPage />
        </PartSettlementWorkspaceProvider>
      </ScreenDirtyContext.Provider>
    </MemoryRouter>,
  );
}
async function ready() {
  await screen.findByRole("button", { name: "개발자 가" });
}
const calls = (action: string) =>
  fetcher.mock.calls.filter(
    ([url, init]) => String(url).endsWith(action) && init?.method === "PUT",
  );
const titleBar = () =>
  within(screen.getByTestId("part-settlement-title-actions"));

it("renders the mockup order: summary with descriptions, card, one excel CTA, mockup formulas", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("part-settlement-page")).toHaveAttribute(
    "data-screen-id",
    "UI-19",
  );
  expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveDisplayValue(
    "2026년 2월",
  );
  expect(
    fetcher.mock.calls.some(
      ([url]) =>
        String(url) === "/api/common/part-settlements?yearMonth=2026-02",
    ),
  ).toBe(true);
  // 확정 전에는 잠금 띠가 없고, 조건 줄에 [조회]도 없다.
  expect(screen.queryByTestId("part-settlement-banner")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "조회" })).not.toBeInTheDocument();
  expect(titleBar().getByRole("button", { name: "정산 확정" })).toBeVisible();
  expect(screen.getAllByRole("button", { name: /엑셀 다운로드/ })).toHaveLength(
    1,
  );
  const summary = screen.getByRole("group", { name: "파트 이익금 요약" });
  expect(summary.children).toHaveLength(4);
  expect(summary).toHaveTextContent("이익 배분율 40% · 공통관리비 1,500,000원");
  expect(summary).toHaveTextContent("1명이번 달 투입 인력");
  expect(summary).toHaveTextContent("−233만 원");
  expect(summary).toHaveTextContent("단가 − (급여 + 공통관리비 + 퇴직금)");
  expect(summary).toHaveTextContent("인력별 이익금 −93만 원 − 법인카드 0원");
  expect(screen.getByLabelText("법인카드 사용액")).toHaveValue("0");
  expect(
    screen.getByText(/파트 이익금에서 뺍니다\(엑셀 「원가표」의 '법카 사용 별도'\)/),
  ).toBeVisible();
  // 순서: 요약 → 법인카드 → 보기 탭 → 표
  const order = [
    screen.getByRole("tablist", { name: "영업 파트" }),
    summary,
    screen.getByTestId("part-settlement-card-box"),
    screen.getByRole("tablist", { name: "정산 보기" }),
    screen.getByTestId("part-settlement-list"),
  ];
  for (let index = 1; index < order.length; index++)
    expect(
      order[index - 1].compareDocumentPosition(order[index]) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  const formulas = screen.getByTestId("part-settlement-formulas");
  expect(within(formulas).getAllByRole("row")).toHaveLength(11);
  expect(formulas).toHaveTextContent("파트 기준값(1,500,000원)");
  expect(formulas).toHaveTextContent("이익 × 이익 배분율(40%)");
  expect(formulas).toHaveTextContent(
    "인력은 그 사람의 소속 영업 파트로 모읍니다.",
  );
  expect(formulas).not.toHaveTextContent("부록");
  expect(screen.getByRole("tab", { name: "기간 추이 (1월~02월)" })).toBeVisible();
});

it("monthly table shows mockup columns, code names, notes and plain column sums", async () => {
  detail.items = [firstRow, { ...secondRow, employeeTypeCode: "XX" }];
  detail.corpCardAmount = 100000;
  detail.turnkeyDeveloperIds = ["E"];
  mount();
  await ready();
  expect(screen.getByTestId("part-settlement-toolbar")).toHaveTextContent(
    "2026-02 · 영업 가파트 · 2명",
  );
  for (const name of [
    "이름",
    "구분",
    "프로젝트",
    "MM",
    "단가",
    "급여",
    "공통관리비",
    "퇴직금",
    "비고",
  ])
    expect(screen.getByRole("columnheader", { name })).toBeVisible();
  expect(screen.getByTestId("list-head-totalCost")).toHaveTextContent("비용 계 ⓘ");
  expect(
    within(screen.getByTestId("list-head-totalCost")).getByTitle(
      "급여 + 공통관리비 + 퇴직금",
    ),
  ).toBeVisible();
  expect(
    within(screen.getByTestId("list-head-profit")).getByTitle(
      "단가 − 비용 계 (적자면 음수)",
    ),
  ).toBeVisible();
  expect(screen.getByTestId("list-head-profitShare")).toHaveTextContent(
    "이익금(40%)",
  );
  expect(screen.getByTestId("list-cell-D-employeeTypeCode")).toHaveTextContent(
    "정규직",
  );
  expect(screen.getByTestId("list-cell-E-employeeTypeCode")).toHaveTextContent(
    "XX",
  );
  expect(screen.getByTitle("가 프로젝트, 나 프로젝트")).toHaveTextContent(
    "가 프로젝트 외 1건",
  );
  expect(screen.getByTestId("list-cell-E-projectNames")).toHaveTextContent(
    "턴키 프로젝트 턴키",
  );
  expect(screen.getByTestId("list-cell-D-projectNames")).not.toHaveTextContent(
    "턴키",
  );
  expect(screen.getByText("⚠ 월 급여 없음(시작 달 급여 0)")).toHaveClass(
    "ls-neg",
  );
  expect(screen.getByText("시작 달 16/31일")).not.toHaveClass("ls-neg");
  expect(screen.getByTestId("list-cell-E-notes")).toHaveTextContent("–");
  expect(screen.getByTestId("list-cell-E-monthlyMm")).toHaveTextContent("0.50");
  // 합계 줄: 열마다 더한 값(이익금도 법인카드를 빼지 않음). 단위 원.
  const foot = (key: string) => screen.getByTestId(`list-footer-${key}`);
  expect(foot("developerName")).toHaveTextContent("합계");
  expect(foot("monthlyMm")).toHaveTextContent("1.50");
  expect(foot("unitPrice")).toHaveTextContent("8,500,000");
  expect(foot("salary")).toHaveTextContent("8,000,000");
  expect(foot("salesExpense")).toHaveTextContent("1,500,000");
  expect(foot("retirementAmount")).toHaveTextContent("333,333");
  expect(foot("totalCost")).toHaveTextContent("9,833,333");
  expect(foot("profit")).toHaveTextContent("−1,333,333");
  expect(foot("profitShare")).toHaveTextContent("−533,333");
  expect(screen.getByTestId("list-head-developerName")).toHaveClass("ls-sc");
});

it("null options keep conditions, toolbar and table headers", async () => {
  tabs.items = null;
  mount();
  const empty = await screen.findByTestId("part-settlement-no-parts");
  expect(within(empty).getByText("맡은 영업 파트가 없습니다.")).toBeVisible();
  expect(empty).toHaveTextContent(
    "본사(직접 영업)는 파트 이익금 정산 대상이 아닙니다. 영업 파트의 파트장은 기준정보 › 거래처·영업 파트의 영업 파트 탭에서 정합니다.",
  );
  expect(screen.getByTestId("part-settlement-conditions")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "이름" })).toBeVisible();
  expect(
    within(screen.getByRole("tablist", { name: "영업 파트" })).queryAllByRole(
      "tab",
    ),
  ).toHaveLength(0);
});

it("empty workforce retains headers and an editable card", async () => {
  detail.items = [];
  mount();
  await screen.findByText("조건에 맞는 결과가 없습니다.");
  expect(screen.getByRole("columnheader", { name: "구분" })).toBeVisible();
  await waitFor(() =>
    expect(screen.getByTestId("part-settlement-card")).not.toHaveAttribute(
      "readonly",
    ),
  );
});

it("failed read remains an error instead of an empty success", async () => {
  failed = true;
  mount();
  expect(await screen.findByTestId("part-settlement-error")).toHaveTextContent(
    "조회 실패",
  );
  expect(
    screen.queryByTestId("part-settlement-no-parts"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("part-settlement-card")).toHaveAttribute(
    "readonly",
  );
});

it("switching part loads its own month criteria", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("tab", { name: "영업 나파트" }));
  await screen.findByText("나파트장");
  expect(
    screen.getByRole("group", { name: "파트 이익금 요약" }),
  ).toHaveTextContent("이익 배분율 50% · 공통관리비 2,000,000원");
});

it("card validates on Enter, shows separators and saves on blur with explicit nullable version", async () => {
  mount();
  await ready();
  const input = screen.getByTestId("part-settlement-card");
  fireEvent.change(input, { target: { value: "1.2" } });
  fireEvent.keyDown(input, { key: "Enter" });
  expect(input).toHaveFocus();
  expect(screen.getByText(/법인카드 사용액은 0~/)).toBeVisible();
  expect(calls("/card")).toHaveLength(0);
  fireEvent.change(input, { target: { value: "50000" } });
  expect(input).toHaveValue("50,000");
  fireEvent.blur(input);
  expect(
    await screen.findByText("법인카드 사용액을 저장했습니다."),
  ).toBeVisible();
  expect(JSON.parse(calls("/card")[0][1].body as string)).toEqual({
    corpCardAmount: 50000,
    modifiedAt: null,
  });
  // 바뀌지 않았으면 다시 저장하지 않는다.
  fireEvent.blur(input);
  expect(calls("/card")).toHaveLength(1);
});

it("server field validation stays under the card and focuses it", async () => {
  validation = { field: "corpCardAmount", message: "금액 확인" };
  mount();
  await ready();
  const input = screen.getByTestId("part-settlement-card");
  fireEvent.change(input, { target: { value: "50000" } });
  fireEvent.keyDown(input, { key: "Enter" });
  expect(await screen.findByText("금액 확인")).toHaveAttribute(
    "id",
    "part-settlement-card-error",
  );
  await waitFor(() => expect(input).toHaveFocus());
  expect(input).toHaveValue("50,000");
});

it("409 on confirm warns with the common wording and reloads the settlement", async () => {
  putStatus = 409;
  validation = {
    field: "modifiedAt",
    message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
  };
  mount();
  await ready();
  const reads = () =>
    fetcher.mock.calls.filter(([url, init]) =>
      String(url).endsWith("/A/2026-02") && !init?.method,
    ).length;
  const before = reads();
  fireEvent.click(titleBar().getByRole("button", { name: "정산 확정" }));
  fireEvent.click(
    within(screen.getByTestId("part-settlement-confirm-dialog")).getByRole(
      "button",
      { name: "확정하기" },
    ),
  );
  expect(await screen.findByTestId("part-settlement-toast")).toHaveTextContent(
    "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
  );
  await waitFor(() => expect(reads()).toBeGreaterThan(before));
});

it("a server error without a screen field (closed month) is shown as a notice", async () => {
  validation = {
    field: "yearMonth",
    message: "마감된 달은 변경할 수 없습니다. 정정 금액은 다음 달에 반영해 주세요.",
  };
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("part-settlement-card"), {
    target: { value: "50000" },
  });
  fireEvent.keyDown(screen.getByTestId("part-settlement-card"), {
    key: "Enter",
  });
  expect(await screen.findByTestId("part-settlement-toast")).toHaveTextContent(
    "마감된 달은 변경할 수 없습니다.",
  );
});

it("confirm asks first, then locks the card and shows the lock banner", async () => {
  mount();
  await ready();
  fireEvent.click(titleBar().getByRole("button", { name: "정산 확정" }));
  const dialog = screen.getByRole("alertdialog", {
    name: "영업 가파트 2026-02 정산을 확정할까요?",
  });
  expect(dialog).toHaveTextContent(
    "확정하면 이 달 파트 정산(법인카드 사용액 포함)이 잠깁니다.",
  );
  expect(calls("/confirm")).toHaveLength(0);
  fireEvent.click(within(dialog).getByRole("button", { name: "확정하기" }));
  expect(await screen.findByText("정산을 확정했습니다.")).toBeVisible();
  expect(calls("/confirm")).toHaveLength(1);
  expect(screen.getByTestId("part-settlement-banner")).toHaveTextContent(
    "2026-02 영업 가파트 정산은 확정되었습니다 (관리자 · 2026-02-01 09:00). 확정 후에는 고칠 수 없습니다. 고쳐야 하면 [확정 취소]를 누르고 사유를 남깁니다.",
  );
  expect(screen.getByTestId("banner-lock")).toBeVisible();
  expect(screen.getByTestId("part-settlement-card")).toHaveAttribute(
    "readonly",
  );
  expect(titleBar().getByRole("button", { name: "확정 취소" })).toBeVisible();
  expect(
    titleBar().queryByRole("button", { name: "정산 확정" }),
  ).not.toBeInTheDocument();
});

it("closed month shows no title buttons and the banner without the cancel hint", async () => {
  detail.confirmed = true;
  detail.closed = true;
  detail.confirmedBy = "admin";
  detail.confirmationNote = "마감 시 자동 확정";
  mount();
  await ready();
  expect(titleBar().queryAllByRole("button")).toHaveLength(0);
  const banner = screen.getByTestId("part-settlement-banner");
  expect(banner).toHaveTextContent("(admin · 마감 시 자동 확정)");
  expect(banner).not.toHaveTextContent("고쳐야 하면");
  expect(screen.getByTestId("part-settlement-card")).toHaveAttribute(
    "readonly",
  );
});

it("cancellation requires a reason, then unlocks the card", async () => {
  detail.confirmed = true;
  detail.confirmedBy = "admin";
  detail.confirmedAt = "2026-02-01T00:00:00Z";
  mount();
  await ready();
  fireEvent.click(titleBar().getByRole("button", { name: "확정 취소" }));
  const dialog = screen.getByRole("alertdialog", {
    name: "정산 확정을 취소할까요?",
  });
  expect(dialog).toHaveTextContent(
    "취소하면 이 달 파트 정산을 다시 고칠 수 있습니다. 사유는 기록에 남습니다.",
  );
  expect(within(dialog).getByLabelText(/취소 사유/)).toBe(
    screen.getByTestId("part-settlement-reason"),
  );
  expect(screen.getByTestId("part-settlement-reason")).toHaveAttribute(
    "maxLength",
    "500",
  );
  fireEvent.click(within(dialog).getByRole("button", { name: "확정 취소" }));
  expect(screen.getByTestId("part-settlement-reason")).toHaveFocus();
  expect(within(dialog).getByText("취소 사유를 입력해 주세요.")).toBeVisible();
  expect(calls("/cancel")).toHaveLength(0);
  fireEvent.change(screen.getByTestId("part-settlement-reason"), {
    target: { value: "정정 필요" },
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "확정 취소" }));
  expect(await screen.findByText("정산 확정을 취소했습니다.")).toBeVisible();
  expect(
    screen.queryByTestId("part-settlement-cancel-dialog"),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("part-settlement-banner")).not.toBeInTheDocument();
  expect(screen.getByTestId("part-settlement-card")).not.toHaveAttribute(
    "readonly",
  );
});

it("dirty part change uses the common leave wording and reports shell dirty", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("part-settlement-card"), {
    target: { value: "123" },
  });
  await waitFor(() => expect(dirty).toHaveBeenCalledWith("UI-19", true));
  fireEvent.click(screen.getByRole("tab", { name: "영업 나파트" }));
  const dialog = screen.getByRole("alertdialog", {
    name: "저장하지 않은 내용이 있습니다",
  });
  expect(dialog).toHaveTextContent("나가면 입력한 내용이 사라집니다.");
  fireEvent.click(screen.getByTestId("confirmation-cancel"));
  expect(screen.getByRole("tab", { name: "영업 가파트" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(screen.getByTestId("part-settlement-card")).toHaveValue("123");
});

it("trend shows the Excel 원가표 layout: five lines per person, sums over invested months, part total", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("part-settlement-trend"));
  await screen.findByTestId("part-settlement-trend-D-profitShare");
  const panel = within(screen.getByTestId("part-settlement-trend-panel"));
  expect(screen.getByTestId("part-settlement-trend-toolbar")).toHaveTextContent(
    "영업 가파트 · 2026-01 ~ 2026-02 · 2명 (엑셀 「원가표」와 같은 모양)",
  );
  const heads = panel
    .getAllByRole("columnheader")
    .map((cell) => cell.textContent);
  expect(heads).toEqual(["이름", "구분", "1월", "2월", "합계", "비고"]);
  expect(panel.getByRole("columnheader", { name: "2월" })).toHaveClass(
    "part-settlement-cur",
  );
  expect(panel.getAllByText("M/M")).toHaveLength(2);
  const line = (id: string, key: string) =>
    within(screen.getByTestId(`part-settlement-trend-${id}-${key}`))
      .getAllByRole("cell")
      .map((cell) => cell.textContent);
  // 이름·비고(5줄 묶음)는 첫 줄에만 있다.
  expect(line("D", "monthlyMm")).toEqual([
    "개발자 가",
    "M/M",
    "1.00",
    "0.50",
    "1.50",
    "시작 달 16/31일 · ⚠ 월 급여 없음(시작 달 급여 0)",
  ]);
  expect(line("D", "profitShare")).toEqual(["이익금", "−933,333", "100,000", "−833,333"]);
  expect(screen.getByTestId("part-settlement-trend-D-profitShare")).toHaveClass(
    "part-settlement-strong",
  );
  // 투입 안 한 달은 「–」, 합계는 투입한 달만.
  expect(line("E", "monthlyMm")).toEqual([
    "외주 나",
    "M/M",
    "–",
    "1.00",
    "1.00",
    "–",
  ]);
  expect(line("E", "unitPrice")).toEqual(["단가", "–", "5,000,000", "5,000,000"]);
  const total = within(screen.getByTestId("part-settlement-trend-total"))
    .getAllByRole("cell")
    .map((cell) => cell.textContent);
  expect(total).toEqual(["파트 이익금 ⓘ", "", "−1,033,333", "400,000", "−633,333", ""]);
  expect(
    panel.getByTitle("인력별 이익금 합 − 그 달 법인카드 사용액"),
  ).toBeVisible();
  expect(screen.getAllByRole("button", { name: /엑셀 다운로드/ })).toHaveLength(
    1,
  );
});

it("read-only developer detail closes without losing selected part or month", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("button", { name: "개발자 가" }));
  expect(screen.getByTestId("part-settlement-person-detail")).toHaveTextContent(
    "D",
  );
  fireEvent.click(screen.getByRole("button", { name: "상세 닫기" }));
  expect(screen.getByRole("tab", { name: "영업 가파트" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(
    fetcher.mock.calls.some(([url]) => String(url).includes("/A/2026-02")),
  ).toBe(true);
});

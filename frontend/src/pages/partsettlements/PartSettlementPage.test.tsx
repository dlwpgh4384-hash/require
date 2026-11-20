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
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import type { PartSettlement, PartSettlementTabs } from "./partSettlements";

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
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
const definitions = [
  { calcId: "식-42", formula: "DB 비용 설명", details: "DB 상세 설명" },
  { calcId: "식-43", formula: "DB 이익 설명", details: "DB 상세" },
];
const dirty = vi.fn();
beforeEach(() => {
  failed = false;
  validation = null;
  dirty.mockClear();
  detail = {
    partId: "A",
    partName: "영업 가파트",
    yearMonth: "2026-01",
    summary: {
      leaderName: "가파트장",
      distributionRate: 40,
      salesExpense: 1500000,
      personCount: 1,
      profitTotal: -2333333,
      partProfitAmount: -933333,
    },
    items: [
      {
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
        notes: ["⚠ 월 급여 없음(시작 달 급여 0)"],
        missingMonthlySalary: true,
      },
    ],
    corpCardAmount: 0,
    confirmed: false,
    closed: false,
    confirmedBy: null,
    confirmedAt: null,
    confirmationNote: null,
    modifiedAt: null,
  };
  tabs = {
    yearMonth: "2026-01",
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
            latestYm: "202601",
            cashBaseYm: "202501",
            closedMonths: [],
          },
        },
      );
    }
    if (init?.method === "PUT") {
      if (validation)
        return {
          ok: false,
          status: 400,
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
          confirmedAt: "2026-02-01T00:00:00Z",
          modifiedAt: "2026-02-01T00:00:00Z",
        };
      if (url.pathname.endsWith("/cancel"))
        detail = {
          ...detail,
          confirmed: false,
          confirmedBy: null,
          confirmedAt: null,
          modifiedAt: "2026-02-02T00:00:00Z",
        };
      return ok(detail);
    }
    if (failed) throw new Error("조회 실패");
    if (url.pathname === "/api/common/part-settlements")
      return ok(tabs, { calculationRules: definitions });
    if (url.pathname.endsWith("/trends"))
      return ok({
        partId: "A",
        yearMonth: "2026-01",
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
            ],
          },
        ],
        monthlyTotals: [{ yearMonth: "2026-01", partProfitAmount: -933333 }],
      });
    if (url.pathname.endsWith("/B/2026-01"))
      return ok(
        {
          ...detail,
          partId: "B",
          partName: "영업 나파트",
          summary: {
            ...detail.summary,
            leaderName: "나파트장",
            distributionRate: 50,
            salesExpense: 2000000,
          },
        },
        { calculationRules: definitions },
      );
    return ok(detail, { calculationRules: definitions });
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
        <PartSettlementPage />
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
it("renders real workspace, four summary cells, DB definitions and one excel CTA", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("part-settlement-page")).toHaveAttribute(
    "data-screen-id",
    "UI-19",
  );
  expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveDisplayValue(
    "2026년 1월",
  );
  expect(
    fetcher.mock.calls.some(
      ([url]) =>
        String(url) === "/api/common/part-settlements?yearMonth=2026-01",
    ),
  ).toBe(true);
  expect(screen.getAllByRole("button", { name: /엑셀 다운로드/ })).toHaveLength(
    1,
  );
  expect(
    screen.getByRole("group", { name: "파트 이익금 요약" }).children,
  ).toHaveLength(4);
  expect(screen.getByTestId("part-settlement-formulas")).toHaveTextContent(
    "DB 비용 설명",
  );
  expect(screen.getByTitle("가 프로젝트, 나 프로젝트")).toHaveTextContent(
    "가 프로젝트 외 1건",
  );
  expect(screen.getByText("⚠ 월 급여 없음(시작 달 급여 0)")).toHaveClass(
    "part-settlement-negative",
  );
});
it("null options keep conditions, toolbar and table headers", async () => {
  tabs.items = null;
  mount();
  await screen.findByText(
    "맡은 영업 파트가 없습니다. 본사(직접 영업)는 파트 이익금 정산 대상이 아닙니다.",
  );
  expect(screen.getByTestId("part-settlement-conditions")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "이름" })).toBeVisible();
  expect(
    within(screen.getByRole("tablist", { name: "영업 파트" })).queryAllByRole(
      "tab",
    ),
  ).toHaveLength(0);
});
it("empty workforce retains headers and card actions", async () => {
  detail.items = [];
  mount();
  await screen.findByText("조건에 맞는 결과가 없습니다.");
  await waitFor(() =>
    expect(screen.getByTestId("part-settlement-card-save")).toBeEnabled(),
  );
  expect(screen.getByRole("columnheader", { name: "직원구분" })).toBeVisible();
  expect(screen.getByTestId("part-settlement-card-save")).toBeEnabled();
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
  expect(screen.getByTestId("part-settlement-card-save")).toBeDisabled();
});
it("switching part loads its own month criteria", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("tab", { name: "영업 나파트" }));
  await screen.findByText("나파트장");
  expect(
    screen.getByRole("group", { name: "파트 이익금 요약" }),
  ).toHaveTextContent("배분율 50%");
});
it("card validates first field and sends integer with explicit nullable version", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("part-settlement-card"), {
    target: { value: "1.2" },
  });
  fireEvent.click(screen.getByTestId("part-settlement-card-save"));
  expect(screen.getByTestId("part-settlement-card")).toHaveFocus();
  expect(calls("/card")).toHaveLength(0);
  fireEvent.change(screen.getByTestId("part-settlement-card"), {
    target: { value: "50000" },
  });
  fireEvent.click(screen.getByTestId("part-settlement-card-save"));
  await screen.findByText("법인카드 금액을 저장했습니다.");
  expect(JSON.parse(calls("/card")[0][1].body as string)).toEqual({
    corpCardAmount: 50000,
    modifiedAt: null,
  });
});
it("server field validation retains input and focuses it", async () => {
  validation = { field: "corpCardAmount", message: "금액 확인" };
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("part-settlement-card"), {
    target: { value: "50000" },
  });
  fireEvent.click(screen.getByTestId("part-settlement-card-save"));
  await waitFor(() =>
    expect(screen.getByTestId("part-settlement-card")).toHaveFocus(),
  );
  expect(screen.getByTestId("part-settlement-card")).toHaveValue("50000");
});
it("direct confirm has no invented confirmation dialog and locks card", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("part-settlement-confirm"));
  await screen.findByText("정산을 확정했습니다.");
  expect(calls("/confirm")).toHaveLength(1);
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  expect(screen.getByTestId("part-settlement-card")).toHaveAttribute(
    "readonly",
  );
  expect(screen.getByTestId("part-settlement-cancel-open")).toBeVisible();
});
it("closed confirmed month hides cancel", async () => {
  detail.confirmed = true;
  detail.closed = true;
  mount();
  await ready();
  expect(
    screen.queryByTestId("part-settlement-cancel-open"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("part-settlement-confirm")).toBeDisabled();
  expect(screen.getByTestId("part-settlement-card")).toHaveAttribute(
    "readonly",
  );
});
it("cancellation requires reason then makes card editable without a second confirm", async () => {
  detail.confirmed = true;
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("part-settlement-cancel-open"));
  fireEvent.click(screen.getByTestId("part-settlement-cancel-submit"));
  expect(screen.getByTestId("part-settlement-reason")).toHaveFocus();
  expect(calls("/cancel")).toHaveLength(0);
  fireEvent.change(screen.getByTestId("part-settlement-reason"), {
    target: { value: "정정 필요" },
  });
  fireEvent.click(screen.getByTestId("part-settlement-cancel-submit"));
  await screen.findByText("정산 확정을 취소했습니다.");
  expect(
    screen.queryByTestId("part-settlement-cancel-dialog"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("part-settlement-card-save")).toBeEnabled();
});
it("dirty part change uses shared discard confirmation and reports shell dirty", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("part-settlement-card"), {
    target: { value: "123" },
  });
  await waitFor(() => expect(dirty).toHaveBeenCalledWith("UI-19", true));
  fireEvent.click(screen.getByRole("tab", { name: "영업 나파트" }));
  expect(screen.getByRole("alertdialog")).toBeVisible();
  fireEvent.click(screen.getByTestId("confirmation-cancel"));
  expect(screen.getByRole("tab", { name: "영업 가파트" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(screen.getByTestId("part-settlement-card")).toHaveValue("123");
});
it("trends show five rows per person and monthly part total", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("part-settlement-trend"));
  await screen.findByTestId("part-settlement-trend-D-profitShare");
  const panel = screen.getByTestId("part-settlement-trend-panel");
  expect(within(panel).getByText("M/M")).toBeVisible();
  expect(within(panel).getByText("월별 파트 이익금 합계")).toBeVisible();
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
    fetcher.mock.calls.some(([url]) => String(url).includes("/A/2026-01")),
  ).toBe(true);
});

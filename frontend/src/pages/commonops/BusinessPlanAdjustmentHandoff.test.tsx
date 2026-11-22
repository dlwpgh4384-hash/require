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
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import { FinanceItemPage } from "../financeitems/FinanceItemPage";
import type { FinanceMonth } from "../financeitems/financeItems";
import * as api from "../financeitems/financeItems";

const settings = vi.hoisted(() => ({
  firstOpen: "202611" as string | null,
  startMonth: 10,
}));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
vi.mock("../userpreferences/useScreenPeriod", async () => {
  const { useState } = await import("react");
  return {
    useScreenPeriod: () => {
      const [value, setValue] = useState({
        year: 2026,
        month: settings.startMonth,
      });
      return {
        state: {
          status: "ready",
          basis: {
            latestYm: "202611",
            cashBaseYm: "202601",
            closedMonths: ["202610"],
          },
          value,
        },
        setValue,
      };
    },
  };
});
vi.mock("../codegroups/codeGroups", () => ({
  getCommonCodes: vi.fn(async () => ({ items: [] })),
}));
vi.mock("../financeitems/financeItems", async (original) => ({
  ...(await original<typeof import("../financeitems/financeItems")>()),
  getCommonFinanceItems: vi.fn(),
  postCommonFinanceTaxYear: vi.fn(),
  putCommonFinanceItems: vi.fn(),
  postCommonFinanceTaxPaymentConfirm: vi.fn(),
  getCommonFinancePreviousItems: vi.fn(),
}));
function finance(month: string): FinanceMonth {
  return {
    yearMonth: month,
    closed: month === "202610",
    firstOpenYearMonth: settings.firstOpen,
    modifiedAt: null,
    months: [
      { yearMonth: "202610", closed: true, itemCount: 0, future: false },
      { yearMonth: "202611", closed: false, itemCount: 0, future: false },
    ],
    items: [],
    loans: [],
    transfers: [],
    adjustments: [],
    cards: [],
    taxPayments: [],
    taxEstimate: {
      taxYear: 2026,
      estimatedTaxAmount: 0,
      monthlyTaxAmount: 0,
      negativeTaxWarning: false,
      modifiedAt: null,
    },
    cardPayment: { paymentAmount: 0, manualOverride: false, modifiedAt: null },
    summary: {
      sgaAmount: 0,
      nonOperatingIncomeAmount: 0,
      nonOperatingExpenseAmount: 0,
      monthlyTaxAmount: 0,
      adjustmentAmount: 0,
      cardTotalAmount: 0,
      cardPaymentDifference: 0,
    },
    activeCardCount: 0,
    autoFillCount: 0,
  };
}
beforeEach(() => {
  vi.clearAllMocks();
  settings.firstOpen = "202611";
  settings.startMonth = 10;
  vi.mocked(api.postCommonFinanceTaxYear).mockResolvedValue({});
  vi.mocked(api.getCommonFinanceItems).mockImplementation(async (month) =>
    finance(month),
  );
});
afterEach(cleanup);
function Location() {
  return (
    <output data-testid="finance-location-state">
      {JSON.stringify(useLocation().state)}
    </output>
  );
}
function mount(state: unknown = { financeAdjustmentSourceMonth: "202610" }) {
  render(
    <MemoryRouter initialEntries={[{ pathname: "/screens/UI-10", state }]}>
      <ScreenDirtyContext.Provider value={() => undefined}>
        <FinanceItemPage />
        <Location />
      </ScreenDirtyContext.Provider>
    </MemoryRouter>,
  );
}
function adjustmentAmount() {
  return within(screen.getByTestId("finance-adjustment-card")).getByLabelText(
    "금액",
  );
}
it("consumes source once after its own finance load, opens first unclosed month and focuses a blank amount without saving", async () => {
  mount();
  await waitFor(() =>
    expect(screen.getByTestId("finance-adjustment-target")).toHaveValue(
      "202610",
    ),
  );
  await waitFor(() => expect(adjustmentAmount()).toHaveFocus());
  expect(adjustmentAmount()).toHaveValue("");
  expect(
    await screen.findByText("11월 재무 항목의 「전월 조정」 표에 넣어 주세요."),
  ).toBeVisible();
  expect(screen.getByTestId("finance-location-state")).not.toHaveTextContent(
    "financeAdjustmentSourceMonth",
  );
  expect(api.getCommonFinanceItems).toHaveBeenCalledWith(
    "202611",
    expect.any(AbortSignal),
  );
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  expect(api.postCommonFinanceTaxPaymentConfirm).not.toHaveBeenCalled();
  fireEvent.change(adjustmentAmount(), { target: { value: "123" } });
  expect(adjustmentAmount()).toHaveValue("123");
});
it("waits for the delayed finance response before performing a handoff", async () => {
  let resolve!: (value: FinanceMonth) => void;
  vi.mocked(api.getCommonFinanceItems).mockImplementationOnce(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  mount();
  await waitFor(() =>
    expect(api.getCommonFinanceItems).toHaveBeenCalledWith(
      "202610",
      expect.any(AbortSignal),
    ),
  );
  expect(screen.queryByTestId("finance-adjustment-target")).toBeNull();
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  await act(async () => resolve(finance("202610")));
  await waitFor(() => expect(adjustmentAmount()).toHaveFocus());
});
it("resolves the first-open month from the closed source rather than an unrelated saved UI-10 period", async () => {
  settings.startMonth = 11;
  mount();
  await waitFor(() => expect(adjustmentAmount()).toHaveFocus());
  expect(api.getCommonFinanceItems).toHaveBeenCalledWith(
    "202610",
    expect.any(AbortSignal),
  );
  expect(screen.getByTestId("finance-adjustment-target")).toHaveValue("202610");
  expect(adjustmentAmount()).toHaveValue("");
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
});
it("reports no first-open month without inventing a future month", async () => {
  settings.firstOpen = null;
  mount();
  expect(
    await screen.findByText("전월 조정을 입력할 수 있는 미마감월이 없습니다."),
  ).toBeVisible();
  expect(api.getCommonFinanceItems).toHaveBeenCalledTimes(1);
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
  expect(screen.queryByTestId("finance-adjustment-input")).toBeNull();
});
it("rejects an out-of-range first-open destination rather than clamping it to a closed month", async () => {
  settings.firstOpen = "202612";
  mount();
  expect(
    await screen.findByText("전월 조정을 입력할 수 있는 미마감월이 없습니다."),
  ).toBeVisible();
  expect(api.getCommonFinanceItems).toHaveBeenCalledTimes(1);
});
it("leaves UI-10 initialization and ordinary open-month behavior unchanged without state", async () => {
  settings.startMonth = 11;
  mount(null);
  await waitFor(() => expect(screen.getByTestId("finance-save")).toBeEnabled());
  expect(api.postCommonFinanceTaxYear).toHaveBeenCalledWith(
    2026,
    expect.any(AbortSignal),
  );
  expect(api.getCommonFinanceItems).toHaveBeenCalledTimes(1);
  expect(
    screen.queryByText("11월 재무 항목의 「전월 조정」 표에 넣어 주세요."),
  ).toBeNull();
  expect(api.putCommonFinanceItems).not.toHaveBeenCalled();
});

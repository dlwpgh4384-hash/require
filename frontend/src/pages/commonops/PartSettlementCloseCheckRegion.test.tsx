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
import { PartSettlementCloseCheckRegion } from "./PartSettlementCloseCheckRegion";
import type { PartSettlementCloseCheck } from "./partSettlementCloseChecks";

const initial: PartSettlementCloseCheck = {
  yearMonth: "2026-10",
  totalCount: 2,
  unconfirmedCount: 1,
  items: [
    { partId: "fixture-a", confirmed: false, modifiedAt: null },
    {
      partId: "fixture-b",
      confirmed: true,
      modifiedAt: "2026-10-31T15:00:00Z",
    },
  ],
};
const success = (data: PartSettlementCloseCheck) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  request = vi.fn(async () => success(initial));
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("shows loading without inventing zero counts and disables refresh while pending", async () => {
  let finish!: (value: ReturnType<typeof success>) => void;
  request.mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  render(<PartSettlementCloseCheckRegion yearMonth="2026-10" />);
  expect(
    screen.getByTestId("part-settlement-close-check-loading"),
  ).toBeInTheDocument();
  expect(
    screen.queryByTestId("part-settlement-close-check-summary"),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "다시 조회" })).toBeDisabled();
  await act(async () => {
    finish(success(initial));
  });
  expect(
    await screen.findByTestId("part-settlement-close-check-summary"),
  ).toHaveTextContent("2개 중 1개 미확정");
});

it("renders all public target states and nullable versions without mutation controls", async () => {
  render(<PartSettlementCloseCheckRegion yearMonth="2026-10" />);
  await screen.findByTestId("part-settlement-close-check-summary");
  expect(
    within(
      screen.getByTestId("part-settlement-close-check-row-fixture-a"),
    ).getByText("미확정"),
  ).toBeInTheDocument();
  expect(
    within(
      screen.getByTestId("part-settlement-close-check-row-fixture-a"),
    ).getByText("—"),
  ).toBeInTheDocument();
  expect(
    within(
      screen.getByTestId("part-settlement-close-check-row-fixture-b"),
    ).getByText("확정"),
  ).toBeInTheDocument();
  expect(screen.getByText("2026-10-31T15:00:00Z")).toHaveAttribute(
    "datetime",
    "2026-10-31T15:00:00Z",
  );
  expect(screen.getAllByRole("button")).toHaveLength(1);
  expect(
    screen.queryByRole("button", { name: /마감|확정|저장/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByTestId("part-settlement-close-check-region"),
  ).toHaveAttribute("data-screen-id", "UI-14");
  expect(request.mock.calls[0][0]).toBe(
    "/api/common/part-settlement-close-checks?yearMonth=2026-10",
  );
  expect(request.mock.calls[0][1].headers).toBeUndefined();
  expect(request.mock.calls[0][1].method).toBeUndefined();
});

it("retains table headers and refresh for an empty target set", async () => {
  request.mockResolvedValue(
    success({
      yearMonth: "2026-10",
      totalCount: 0,
      unconfirmedCount: 0,
      items: [],
    }),
  );
  render(<PartSettlementCloseCheckRegion yearMonth="2026-10" />);
  expect(
    await screen.findByText("마감 점검 대상 파트가 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.getByTestId("part-settlement-close-check-summary"),
  ).toHaveTextContent("0개 중 0개 미확정");
  expect(screen.getAllByRole("columnheader")).toHaveLength(3);
  expect(screen.getByRole("button", { name: "다시 조회" })).toBeEnabled();
});

it("shows a failed request instead of a successful empty and allows retry", async () => {
  request.mockRejectedValueOnce(new Error("network unavailable"));
  render(<PartSettlementCloseCheckRegion yearMonth="2026-10" />);
  expect(
    await screen.findByTestId("part-settlement-close-check-error"),
  ).toHaveTextContent("불러오지 못했습니다");
  expect(
    screen.queryByTestId("part-settlement-close-check-summary"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByText("마감 점검 대상 파트가 없습니다."),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(
    await screen.findByTestId("part-settlement-close-check-summary"),
  ).toHaveTextContent("2개 중 1개 미확정");
  expect(request).toHaveBeenCalledTimes(2);
});

it("surfaces the server yearMonth field validation without normalizing an invalid month", async () => {
  request.mockResolvedValue({
    ok: false,
    status: 400,
    json: async () => ({
      success: false,
      meta: {},
      message: "입력을 확인해 주세요.",
      errors: [
        {
          field: "yearMonth",
          message: "년월은 YYYY-MM 형식으로 입력해 주세요.",
        },
      ],
    }),
  });
  render(<PartSettlementCloseCheckRegion yearMonth="2026-13" />);
  expect(
    await screen.findByTestId("part-settlement-close-check-error"),
  ).toHaveTextContent("YYYY-MM");
  expect(request.mock.calls[0][0]).toContain("yearMonth=2026-13");
  expect(
    screen.queryByTestId("part-settlement-close-check-summary"),
  ).not.toBeInTheDocument();
});

it("discards stale success after a period change", async () => {
  let finishOld!: (value: ReturnType<typeof success>) => void;
  request.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finishOld = resolve;
      }),
  );
  const view = render(<PartSettlementCloseCheckRegion yearMonth="2026-09" />);
  view.rerender(<PartSettlementCloseCheckRegion yearMonth="2026-10" />);
  expect(
    await screen.findByTestId("part-settlement-close-check-summary"),
  ).toHaveTextContent("2개 중 1개 미확정");
  await act(async () => {
    finishOld(
      success({
        yearMonth: "2026-09",
        totalCount: 0,
        unconfirmedCount: 0,
        items: [],
      }),
    );
  });
  expect(
    screen.getByTestId("part-settlement-close-check-summary"),
  ).toHaveTextContent("2개 중 1개 미확정");
  expect(request.mock.calls[0][1].signal.aborted).toBe(true);
});

it("refreshes the same month after its owner's automatic confirmation receipt", async () => {
  const view = render(
    <PartSettlementCloseCheckRegion yearMonth="2026-10" refreshKey={0} />,
  );
  await screen.findByTestId("part-settlement-close-check-summary");
  request.mockResolvedValue(
    success({
      ...initial,
      unconfirmedCount: 0,
      items: initial.items.map((item) => ({ ...item, confirmed: true })),
    }),
  );
  view.rerender(
    <PartSettlementCloseCheckRegion yearMonth="2026-10" refreshKey={1} />,
  );
  await waitFor(() =>
    expect(
      screen.getByTestId("part-settlement-close-check-summary"),
    ).toHaveTextContent("2개 중 0개 미확정"),
  );
  expect(request.mock.calls.map((call) => call[0])).toEqual([
    "/api/common/part-settlement-close-checks?yearMonth=2026-10",
    "/api/common/part-settlement-close-checks?yearMonth=2026-10",
  ]);
});

it("removes prior successful counts when refresh fails instead of leaving a stale success", async () => {
  render(<PartSettlementCloseCheckRegion yearMonth="2026-10" />);
  await screen.findByTestId("part-settlement-close-check-summary");
  request.mockRejectedValueOnce(new Error("network unavailable"));
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("part-settlement-close-check-error");
  expect(
    screen.queryByTestId("part-settlement-close-check-summary"),
  ).not.toBeInTheDocument();
});

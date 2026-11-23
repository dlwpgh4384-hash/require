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
import { MonthCloseHistoryRegion } from "./MonthCloseHistoryRegion";
import {
  getCommonMonthCloseHistory,
  type MonthCloseHistoryItem,
} from "./monthCloseHistory";

let rows: MonthCloseHistoryItem[];
let request: ReturnType<typeof vi.fn>;
const success = (items: MonthCloseHistoryItem[]) => ({
  ok: true,
  status: 200,
  json: async () => ({
    success: true,
    meta: {},
    data: { items: structuredClone(items) },
  }),
});
beforeEach(() => {
  rows = [
    {
      yearMonth: "2026-10",
      action: "해제",
      processor: "저장 처리자",
      occurredAt: "2026-11-02T01:02:00Z",
      reason: "정정 검토",
      remainingChecks: [],
    },
    {
      yearMonth: "2026-10",
      action: "마감",
      processor: "월 마감 처리자",
      occurredAt: "2026-11-01T01:02:00Z",
      reason: null,
      remainingChecks: ["MM 1.0 초과자", "영업 파트 정산"],
    },
    {
      yearMonth: "2026-09",
      action: "마감",
      processor: "월 마감 처리자",
      occurredAt: "2026-10-01T01:02:00Z",
      reason: null,
      remainingChecks: [],
    },
  ];
  request = vi.fn(async () => success(rows));
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("waits for history without showing an invented empty result", async () => {
  let finish!: (value: ReturnType<typeof success>) => void;
  request.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  render(<MonthCloseHistoryRegion />);
  expect(screen.getByTestId("month-close-history-loading")).toBeInTheDocument();
  expect(
    screen.queryByTestId("month-close-history-empty"),
  ).not.toBeInTheDocument();
  await act(async () => finish(success(rows)));
  expect(await screen.findByRole("table")).toBeInTheDocument();
});

it("shows all six columns, Korean time and immutable warning names in API order", async () => {
  render(<MonthCloseHistoryRegion />);
  const table = await screen.findByRole("table", { name: "월 마감 이력" });
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["연월", "작업", "처리자", "일시", "사유", "남은 점검"]);
  expect(screen.getByTestId("month-close-history-row-0")).toHaveTextContent(
    "해제저장 처리자2026-11-02 10:02정정 검토–",
  );
  expect(screen.getByTestId("month-close-history-row-1")).toHaveTextContent(
    "MM 1.0 초과자, 영업 파트 정산",
  );
  const closed = within(
    screen.getByTestId("month-close-history-row-2"),
  ).getAllByRole("cell");
  expect(closed[4]).toHaveTextContent("–");
  expect(closed[5]).toHaveTextContent("–");
  expect(request).toHaveBeenCalledWith("/api/common/month-close-history", {
    signal: expect.any(AbortSignal),
  });
  expect(
    screen.queryByRole("button", { name: /저장|삭제|마감하기|해제/ }),
  ).not.toBeInTheDocument();
});

it("never shows remaining checks on a reopen row", async () => {
  rows = [{ ...rows[0], remainingChecks: ["지난 점검"] }];
  render(<MonthCloseHistoryRegion />);
  await screen.findByRole("table");
  expect(screen.queryByText("지난 점검")).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("month-close-history-row-0")).getAllByRole(
      "cell",
    )[5],
  ).toHaveTextContent("–");
});

it("retains six table headings on the real empty result", async () => {
  rows = [];
  render(<MonthCloseHistoryRegion />);
  expect(
    await screen.findByTestId("month-close-history-empty"),
  ).toHaveTextContent("마감 이력이 없습니다.");
  expect(screen.getAllByRole("columnheader")).toHaveLength(6);
});

it("retries a failed read with the same month and no mutation", async () => {
  request.mockRejectedValueOnce(new Error("network unavailable"));
  render(<MonthCloseHistoryRegion yearMonth="2026-10" />);
  await screen.findByTestId("month-close-history-error");
  expect(
    screen.queryByTestId("month-close-history-empty"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByRole("table");
  expect(request).toHaveBeenCalledTimes(2);
  expect(request.mock.calls.map((call) => call[0])).toEqual([
    "/api/common/month-close-history?yearMonth=2026-10",
    "/api/common/month-close-history?yearMonth=2026-10",
  ]);
  expect(
    request.mock.calls.every((call) => !call[1].method && !call[1].body),
  ).toBe(true);
});

it("shows server month field errors without repairing the supplied month", async () => {
  request.mockResolvedValue({
    ok: false,
    status: 400,
    json: async () => ({
      success: false,
      message: "입력을 확인해 주세요.",
      errors: [
        {
          field: "yearMonth",
          message: "년월은 YYYY-MM 형식으로 입력해 주세요.",
        },
      ],
    }),
  });
  render(<MonthCloseHistoryRegion yearMonth="2026-13" />);
  expect(
    await screen.findByTestId("month-close-history-error"),
  ).toHaveTextContent("YYYY-MM");
  expect(request.mock.calls[0][0]).toBe(
    "/api/common/month-close-history?yearMonth=2026-13",
  );
});

it("refreshes history for the same selected month after its owner's successful command", async () => {
  const view = render(
    <MonthCloseHistoryRegion yearMonth="2026-10" refreshKey={0} />,
  );
  await screen.findByText("정정 검토");
  rows = [{ ...rows[0], reason: "새 해제 사유" }];
  view.rerender(<MonthCloseHistoryRegion yearMonth="2026-10" refreshKey={1} />);
  expect(await screen.findByText("새 해제 사유")).toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(2);
  expect(request.mock.calls[1][0]).toBe(
    "/api/common/month-close-history?yearMonth=2026-10",
  );
});

it("ignores an old-month response after a period change", async () => {
  let finish!: (value: ReturnType<typeof success>) => void;
  request.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const view = render(<MonthCloseHistoryRegion yearMonth="2026-09" />);
  const oldSignal = request.mock.calls[0][1].signal;
  view.rerender(<MonthCloseHistoryRegion yearMonth="2026-10" />);
  await screen.findByText("정정 검토");
  await act(async () => finish(success([{ ...rows[0], reason: "지난 응답" }])));
  expect(oldSignal.aborted).toBe(true);
  expect(screen.queryByText("지난 응답")).not.toBeInTheDocument();
});

it("clears stale history on refresh failure", async () => {
  const view = render(<MonthCloseHistoryRegion refreshKey={0} />);
  await screen.findByRole("table");
  request.mockRejectedValueOnce(new Error("failed"));
  view.rerender(<MonthCloseHistoryRegion refreshKey={1} />);
  await screen.findByTestId("month-close-history-error");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});

it("renders stored reason as text and aborts on unmount", async () => {
  rows[0].reason = "<img src=x onerror=alert(1)>";
  const view = render(<MonthCloseHistoryRegion />);
  await screen.findByText("<img src=x onerror=alert(1)>");
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  const signal = request.mock.calls[0][1].signal;
  view.unmount();
  await waitFor(() => expect(signal.aborted).toBe(true));
});

it("rejects a malformed successful response rather than inventing empty rows", async () => {
  request.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ success: true, data: {} }),
  });
  await expect(getCommonMonthCloseHistory()).rejects.toThrow("마감 이력 응답");
});

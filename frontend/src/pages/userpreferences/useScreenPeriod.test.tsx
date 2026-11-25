import { afterEach, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useScreenPeriod } from "./useScreenPeriod";
import { announcePeriodBasisChanged } from "./userPreferences";

// 2026-11-24 사용자 지적: 1~4월 마감 뒤 열어 둔 탭은 10월까지 더 마감해도 4월까지만 고를 수 있었다.
const closed = (last: number) =>
  Array.from({ length: last }, (_, i) => `2026${String(i + 1).padStart(2, "0")}`);
let closedTo = 4;
const fetchMock = vi.fn(async () => ({
  ok: true,
  json: async () => ({
    success: true,
    meta: {
      periodBasis: {
        latestYm: "202611",
        cashBaseYm: "202601",
        closedMonths: closed(closedTo),
      },
    },
    data: { prefTypeCd: "PERIOD", prefKey: "UI-31", found: false, prefVal: null, modDttm: null },
  }),
}));
vi.stubGlobal("fetch", fetchMock);
afterEach(() => {
  closedTo = 4;
  fetchMock.mockClear();
});

it("rereads the period basis of an open screen after a month close and keeps the chosen period", async () => {
  const { result } = renderHook(() => useScreenPeriod("UI-31", { kind: "yearMonth" }));
  await waitFor(() => expect(result.current.state.status).toBe("ready"));
  const first = result.current.state;
  if (first.status !== "ready") throw new Error("not ready");
  expect(first.basis.closedMonths).toHaveLength(4);
  const chosen = first.value;
  closedTo = 10;
  act(() => announcePeriodBasisChanged());
  await waitFor(() => {
    const state = result.current.state;
    expect(state.status === "ready" && state.basis.closedMonths).toHaveLength(10);
  });
  const after = result.current.state;
  expect(after.status === "ready" && after.value).toEqual(chosen);
  // 기간 기준만 다시 읽고 고른 기간을 새로 저장하지 않는다.
  expect(fetchMock.mock.calls.every((call) => (call as unknown[])[1] === undefined || ((call as unknown[])[1] as RequestInit).method === undefined)).toBe(true);
});

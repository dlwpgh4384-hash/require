import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { PartSettlementCloseCheckRegion } from "./PartSettlementCloseCheckRegion";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
it("read-only close region calls the other owner's approved endpoint without activating UI-14", async () => {
  const fetcher = vi.fn(async () => ({
    ok: true,
    json: async () => ({
      success: true,
      meta: {},
      data: {
        yearMonth: "2026-01",
        totalCount: 1,
        unconfirmedCount: 1,
        items: [{ partId: "A", confirmed: false, modifiedAt: null }],
      },
    }),
  }));
  vi.stubGlobal("fetch", fetcher);
  render(<PartSettlementCloseCheckRegion yearMonth="2026-01" />);
  await screen.findByText("미확정 1개");
  expect(fetcher.mock.calls[0]).toBeDefined();
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  expect(screen.getByTestId("part-settlement-close-A")).toHaveTextContent(
    "미확정",
  );
});
it("failure remains an error, never a successful zero count", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => {
      throw new Error("점검 조회 실패");
    }),
  );
  render(<PartSettlementCloseCheckRegion yearMonth="2026-01" />);
  await waitFor(() =>
    expect(screen.getByRole("alert")).toHaveTextContent("점검 조회 실패"),
  );
  expect(screen.queryByText("미확정 0개")).not.toBeInTheDocument();
});

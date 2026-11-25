import { cleanup, render, screen, waitFor, act } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { afterEach, expect, it, vi } from "vitest";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import {
  getCommonUserPreference,
  type UserPreferenceResponse,
} from "../userpreferences/userPreferences";
import {
  currentScreenPeriod,
  screenPeriodChannel,
} from "../../shared/layout/screenPeriodChannel";
vi.mock("../userpreferences/userPreferences", async (original) => ({
  ...(await original<typeof import("../userpreferences/userPreferences")>()),
  getCommonUserPreference: vi.fn(),
}));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});
const options = { kind: "yearMonth", firstDataYear: 2026 } as const;
const response: UserPreferenceResponse = {
  success: true,
  meta: {
    periodBasis: { latestYm: "202611", cashBaseYm: "202601", closedMonths: [] },
  },
  data: {
    prefTypeCd: "PERIOD",
    prefKey: "UI-08",
    found: true,
    prefVal: '{"year":2026,"month":9}',
    modDttm: "2026-11-25T00:00:00Z",
  },
};
function Source() {
  const period = useScreenPeriod("UI-08", options);
  return (
    <p>
      {period.state.status === "ready"
        ? `${period.state.value.year}-${period.state.value.month}`
        : period.state.status}
    </p>
  );
}
it("publishes the actual active source filter, not a fresh default month", async () => {
  vi.mocked(getCommonUserPreference).mockResolvedValue(response);
  render(<Source />);
  await screen.findByText("2026-9");
  expect(currentScreenPeriod("UI-08")).toEqual({
    startMonth: "2026-09",
    endMonth: "2026-09",
  });
  act(() =>
    screenPeriodChannel("UI-08").request({
      startMonth: "2026-06",
      endMonth: "2026-06",
    }),
  );
  await screen.findByText("2026-6");
  expect(currentScreenPeriod("UI-08")).toEqual({
    startMonth: "2026-06",
    endMonth: "2026-06",
  });
});
it("does not lose an answer action that arrives before preferences finish loading", async () => {
  let finish!: (value: UserPreferenceResponse) => void;
  vi.mocked(getCommonUserPreference).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  screenPeriodChannel("UI-08").request({
    startMonth: "2026-07",
    endMonth: "2026-07",
  });
  render(<Source />);
  await act(async () => {
    finish(response);
  });
  await screen.findByText("2026-7");
  await waitFor(() =>
    expect(currentScreenPeriod("UI-08")).toEqual({
      startMonth: "2026-07",
      endMonth: "2026-07",
    }),
  );
});

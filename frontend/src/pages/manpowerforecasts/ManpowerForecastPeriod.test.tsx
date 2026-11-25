import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { getCommonUserPreference } from "../userpreferences/userPreferences";
import { yearRange } from "../../shared/period/periodRules";
vi.mock("../userpreferences/userPreferences", async () => ({
  ...(await vi.importActual<
    typeof import("../userpreferences/userPreferences")
  >("../userpreferences/userPreferences")),
  getCommonUserPreference: vi.fn(),
}));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});
const options = {
  kind: "year",
  allowNextYear: true,
  allowBeforeStart: false,
} as const;
function Period({ current }: { current?: string }) {
  const period = useScreenPeriod("UI-17", options, current);
  if (period.state.status !== "ready") return <p>{period.state.status}</p>;
  const range = yearRange(period.state.basis, options);
  return (
    <p data-testid="forecast-period">
      {period.state.value.year} / {range.min} / {range.max}
    </p>
  );
}
function metadata(saved: number | null = null) {
  vi.mocked(getCommonUserPreference).mockResolvedValue({
    success: true,
    meta: {
      periodBasis: {
        latestYm: "202402",
        cashBaseYm: "202401",
        closedMonths: ["202401"],
      },
    },
    data: {
      prefTypeCd: "PERIOD",
      prefKey: "UI-17",
      found: saved !== null,
      prefVal: saved === null ? null : JSON.stringify({ year: saved }),
      modDttm: null,
    },
  });
}
it("UI-17 uses the Korean current month, not last-open metadata, and keeps cash-base bounds", async () => {
  metadata();
  render(<Period current="202611" />);
  expect(await screen.findByTestId("forecast-period")).toHaveTextContent(
    "2026 / 2024 / 2027",
  );
});
it("an allowed saved past year remains selectable without historical forecast fabrication", async () => {
  metadata(2024);
  render(<Period current="202611" />);
  expect(await screen.findByTestId("forecast-period")).toHaveTextContent(
    "2024 / 2024 / 2027",
  );
});
it("omitting the additive override preserves existing consumers' last-open behavior", async () => {
  metadata();
  render(<Period />);
  expect(await screen.findByTestId("forecast-period")).toHaveTextContent(
    "2024 / 2024 / 2025",
  );
});

import { afterEach, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  ManpowerForecastTable,
  manpowerForecastDifference,
} from "./ManpowerForecastTable";
const thresholds = { shortageThreshold: 0.4, surplusThreshold: 1 };
afterEach(cleanup);
async function readCss(path: string) {
  const fs: { readFileSync(file: URL, encoding: "utf8"): string } =
    await import("node:" + "fs");
  return fs.readFileSync(new URL(path, import.meta.url), "utf8");
}
it("uses the shared warning color for surplus instead of the green chart series", async () => {
  const css = await readCss("./manpowerForecast.css");
  expect(css).toMatch(
    /\.manpower-forecast-table \.surplus\s*\{\s*color:\s*var\(--ls-warn\);\s*\}/,
  );
});
it.each([
  [-0.4001, "shortage"],
  [-0.4, "balanced"],
  [-0.3999, "balanced"],
  [0.9999, "balanced"],
  [1, "balanced"],
  [1.0001, "surplus"],
] as const)(
  "classifies raw difference %s using strict DB thresholds",
  (value, expected) => {
    expect(manpowerForecastDifference(value, thresholds)).toBe(expected);
  },
);
it.each([
  [-1.25, "1.3명 부족"],
  [1.25, "1.3명 남음"],
  [-0.049, "맞음"],
] as const)("formats signed HALF_UP difference %s", (difference, text) => {
  render(
    <ManpowerForecastTable
      months={[
        {
          month: "2026-12",
          supply: 1,
          demand: 1,
          unfilledDemand: 0,
          difference,
        },
      ]}
      thresholds={thresholds}
    />,
  );
  expect(screen.getByText(text)).toBeVisible();
});
it("keeps the aggregate row labels without inventing historical cells", () => {
  render(<ManpowerForecastTable months={[]} thresholds={thresholds} />);
  expect(screen.getAllByRole("row")).toHaveLength(4);
  expect(screen.getByText("필요 인력")).toBeVisible();
  expect(screen.getByText("가용 인력")).toBeVisible();
  expect(screen.getByText("차이")).toBeVisible();
});

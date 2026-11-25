import { afterEach, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  ManpowerForecastTable,
  manpowerForecastDifference,
} from "./ManpowerForecastTable";
const thresholds = { shortageThreshold: 0.4, surplusThreshold: 1 };
afterEach(cleanup);
import "./manpowerForecast.css?render-test";
it("uses the shared bad and warning colors for shortage and surplus", () => {
  render(
    <div className="manpower-forecast-page">
      <ManpowerForecastTable
        months={[
          {
            month: "2026-12",
            supply: 0,
            demand: 2,
            unfilledDemand: 0,
            difference: -2,
          },
          {
            month: "2027-01",
            supply: 3,
            demand: 1,
            unfilledDemand: 0,
            difference: 2,
          },
        ]}
        thresholds={thresholds}
      />
    </div>,
  );
  const shortage = screen.getByText("2.0명 부족");
  const surplus = screen.getByText("2.0명 남음");
  expect(shortage).toHaveClass("bad");
  expect(surplus).toHaveClass("warn");
  expect(getComputedStyle(shortage).color).toBe("var(--ls-bad)");
  expect(getComputedStyle(surplus).color).toBe("var(--ls-warn)");
});
it.each([
  [-0.4001, "bad"],
  [-0.4, undefined],
  [-0.3999, undefined],
  [0.9999, undefined],
  [1, undefined],
  [1.0001, "warn"],
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
it.each([
  [1.25, 0.24, "1.1명 남음", "warn"],
  [2, 3.25, "1.3명 부족", "bad"],
  [1.04, 1.05, "0.1명 부족", undefined],
  [1.15, 1.15, "맞음", undefined],
] as const)(
  "without a server difference, subtracts the shown values (supply %s, demand %s)",
  (supply, demand, text, cls) => {
    render(
      <ManpowerForecastTable
        months={[{ month: "2026-12", supply, demand, unfilledDemand: 0 }]}
        thresholds={thresholds}
      />,
    );
    const cell = screen.getByText(text);
    if (cls) expect(cell).toHaveClass(cls);
    else expect(cell.className).toBe("");
  },
);
it("keeps the aggregate row labels without inventing historical cells", () => {
  render(<ManpowerForecastTable months={[]} thresholds={thresholds} />);
  expect(screen.getAllByRole("row")).toHaveLength(4);
  expect(screen.getByText("필요 인력")).toBeVisible();
  expect(screen.getByText("가용 인력")).toBeVisible();
  expect(screen.getByText("차이")).toBeVisible();
});

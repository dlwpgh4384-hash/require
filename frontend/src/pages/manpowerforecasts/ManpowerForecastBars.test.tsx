import { afterEach, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { ManpowerForecastBars } from "./ManpowerForecastBars";
afterEach(cleanup);
it("renders two bars per month with shared theme colors and the included unfilled contribution", () => {
  const view = render(
    <ManpowerForecastBars
      months={[
        {
          month: "2026-12",
          supply: 2.25,
          demand: 3.25,
          unfilledDemand: 0.25,
          difference: -1,
        },
      ]}
    />,
  );
  expect(
    screen.getByRole("heading", { name: "월별 가용 vs 필요" }),
  ).toBeVisible();
  expect(screen.getByText("단위: 명")).toBeVisible();
  const bars = view.container.querySelectorAll("rect");
  expect(bars).toHaveLength(2);
  expect(bars[0].getAttribute("style")).toContain("var(--pf-c1)");
  expect(bars[1].getAttribute("style")).toContain("var(--pf-c3)");
  expect(bars[1].textContent).toContain("필요 인력 3.3명 · 그중 미충원 0.3명");
});
it("all-zero values retain a non-degenerate axis and finite bar geometry", () => {
  const view = render(
    <ManpowerForecastBars
      months={[
        {
          month: "2026-12",
          supply: 0,
          demand: 0,
          unfilledDemand: 0,
          difference: 0,
        },
      ]}
    />,
  );
  for (const bar of view.container.querySelectorAll("rect")) {
    expect(Number.isFinite(Number(bar.getAttribute("y")))).toBe(true);
    expect(bar.getAttribute("height")).toBe("0");
  }
});

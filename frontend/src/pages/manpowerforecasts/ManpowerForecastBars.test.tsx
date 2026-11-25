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
  expect(screen.getByText("단위: 명(MM)")).toBeVisible();
  const bars = view.container.querySelectorAll("rect");
  expect(bars).toHaveLength(2);
  expect(bars[0].getAttribute("style")).toContain("var(--pf-c1)");
  expect(bars[1].getAttribute("style")).toContain("var(--pf-c2)");
  expect(bars[1].textContent).toContain("필요 인력 3.3명");
  expect(
    screen.queryByTestId("manpower-forecast-unfilled-2026-12"),
  ).not.toBeInTheDocument();
  const label = screen.getByTestId("manpower-forecast-label-2026-12");
  expect(label).toHaveClass("blab");
  expect(label.textContent).toBe("필요 3.3그중 미충원 0.3");
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
it("shortens the second label line and the font when more than six months are shown", () => {
  const months = Array.from({ length: 7 }, (_, i) => ({
    month: `2027-${String(i + 1).padStart(2, "0")}`,
    supply: 3.5,
    demand: 2.25,
    unfilledDemand: 1.25,
  }));
  const view = render(<ManpowerForecastBars months={months} />);
  const label = screen.getByTestId("manpower-forecast-label-2027-01");
  expect(label.textContent).toBe("필요 2.3미충원 1.3");
  expect(label.getAttribute("style")).toContain("font-size: 9px");
  const ticks = Array.from(
    view.container.querySelectorAll('text.ax[text-anchor="end"]'),
  );
  expect(ticks.length).toBeGreaterThan(1);
  for (const tick of ticks) expect(tick.textContent).toMatch(/^\d+$/);
});

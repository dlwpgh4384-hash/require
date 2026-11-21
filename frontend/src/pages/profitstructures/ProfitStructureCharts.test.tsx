import { expect, it } from "vitest";
import { render } from "@testing-library/react";
import { axisScale, BarChart } from "./ProfitStructureCharts";

it("keeps the mockup axis (0 to niceMax, 4 steps) when nothing is negative", () => {
  expect(axisScale([30e6, 41e6])).toEqual({ low: 0, high: 50e6, ticks: [0, 12.5e6, 25e6, 37.5e6, 50e6] });
});

it("puts 0 on a tick with even steps when a loss is negative", () => {
  const scale = axisScale([40e6, -7e6]);
  expect(scale.ticks).toContain(0);
  expect(scale.low).toBeLessThanOrEqual(-7e6);
  expect(scale.high).toBeGreaterThanOrEqual(40e6);
  const steps = scale.ticks.slice(1).map((value, index) => value - scale.ticks[index]);
  expect(new Set(steps).size).toBe(1);
  // 마이너스 칸은 꼭 필요한 한 칸만(작은 손실이 큰 아래 공간을 만들지 않음), 전체는 6칸 이하
  expect(scale.low).toBe(-steps[0]);
  expect(scale.ticks.length - 1).toBeLessThanOrEqual(6);
  expect(axisScale([40e6, -2e6]).low).toBe(-10e6);
});

it("draws a loss bar below the bold 0 line", () => {
  const { container } = render(
    <BarChart
      labels={["1월", "2월"]}
      series={[{ name: "영업이익", color: "blue", values: [20e6, -5e6] }]}
    />,
  );
  const zero = container.querySelector("line.zl")!;
  const zeroY = Number(zero.getAttribute("y1"));
  const [gain, loss] = [...container.querySelectorAll("rect")];
  expect(Number(gain.getAttribute("y")) + Number(gain.getAttribute("height"))).toBeCloseTo(zeroY);
  expect(Number(loss.getAttribute("y"))).toBeCloseTo(zeroY);
  expect(Number(loss.getAttribute("height"))).toBeGreaterThan(0);
});

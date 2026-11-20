import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { KpiCard, KpiCards } from ".";

afterEach(cleanup);

it("KpiCards groups the cards under its label and renders the mockup kpi(l, v, d) parts", () => {
  render(
    <KpiCards aria-label="청구 요약" testId="kpis" className="extra">
      <KpiCard testId="a" label="미수금" value="220만 원" description="2건" />
      <KpiCard testId="b" label="맞음" value="3건" />
    </KpiCards>,
  );
  const group = screen.getByRole("group", { name: "청구 요약" });
  expect(group).toBe(screen.getByTestId("kpis"));
  expect(group).toHaveClass("ls-kpis", "extra");
  const card = screen.getByTestId("a");
  expect(card.tagName).toBe("DIV");
  expect(card).toHaveClass("ls-kpi");
  expect(
    [...card.children].map((part) => [part.className, part.textContent]),
  ).toEqual([
    ["l", "미수금"],
    ["v", "220만 원"],
    ["d", "2건"],
  ]);
  // 보조 문구가 없어도 칸 높이를 맞추도록 빈 .d를 둔다
  expect(screen.getByTestId("b").querySelector(".d")).toHaveTextContent("");
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
});

it("KpiCard with onClick is a button like the mockup button.kpi", () => {
  const onClick = vi.fn();
  render(
    <KpiCards aria-label="요약">
      <KpiCard testId="go" label="위험" value="1건" onClick={onClick} />
    </KpiCards>,
  );
  const button = screen.getByRole("button", { name: /위험/ });
  expect(button).toBe(screen.getByTestId("go"));
  expect(button).toHaveAttribute("type", "button");
  expect(button).toHaveClass("ls-kpi", "ls-kpi-go");
  fireEvent.click(button);
  expect(onClick).toHaveBeenCalledTimes(1);
});

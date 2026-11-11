import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { useState } from "react";
import { PeriodSelector } from "./PeriodSelector";
import {
  isAllowed,
  latestPeriod,
  periodStatus,
  yearRange,
  type PeriodBasis,
  type PeriodOptions,
  type PeriodValue,
} from "./periodRules";

afterEach(cleanup);

// 시험용 설정값(업무 자료가 아님): 가장 최근 달 2026-10, 시작 달 2026-01, 9월까지 마감.
const basis: PeriodBasis = {
  latestYm: "202610",
  cashBaseYm: "202601",
  closedMonths: ["202601", "202602", "202603", "202604", "202605", "202606", "202607", "202608", "202609"],
};

function Harness(props: PeriodOptions & { initial?: PeriodValue; mobile?: boolean; description?: string }) {
  const { initial, ...options } = props;
  const [value, setValue] = useState<PeriodValue>(initial ?? latestPeriod(basis, options.kind));
  return <PeriodSelector basis={basis} value={value} onChange={setValue} {...options} />;
}

describe("period rules (FR-0105)", () => {
  it("starts at the latest month and never goes past it on month screens", () => {
    expect(latestPeriod(basis, "yearMonth")).toEqual({ year: 2026, month: 10 });
    expect(latestPeriod(basis, "year")).toEqual({ year: 2026 });
    const month: PeriodOptions = { kind: "yearMonth" };
    expect(isAllowed({ year: 2026, month: 10 }, basis, month)).toBe(true);
    expect(isAllowed({ year: 2026, month: 11 }, basis, month)).toBe(false);
    expect(isAllowed({ year: 2027, month: 1 }, basis, month)).toBe(false);
  });

  it("allows next year only on year screens that pass allowNextYear", () => {
    expect(yearRange(basis, { kind: "year" })).toEqual({ min: 2026, max: 2026 });
    expect(yearRange(basis, { kind: "year", allowNextYear: true })).toEqual({ min: 2026, max: 2027 });
    expect(yearRange(basis, { kind: "yearMonth", allowNextYear: true }).max).toBe(2026);
  });

  it("opens the year before the start only for UI-07·08·09 style screens", () => {
    expect(yearRange(basis, { kind: "yearCumulative" }).min).toBe(2026);
    expect(yearRange(basis, { kind: "yearCumulative", allowBeforeStart: true }).min).toBe(2025);
  });

  it("badges: this year, past read-only, next-year plan, settled cumulative, closed/working month", () => {
    expect(periodStatus({ year: 2026 }, basis, "year").yearBadge).toBe("올해");
    expect(periodStatus({ year: 2025 }, basis, "year")).toMatchObject({ yearBadge: "지난 해 · 읽기 전용", readOnly: true });
    expect(periodStatus({ year: 2027 }, basis, "year").yearBadge).toBe("내년 · 계획");
    expect(periodStatus({ year: 2025, month: 12 }, basis, "yearCumulative").yearBadge).toBe("결산 완료");
    expect(periodStatus({ year: 2026, month: 9 }, basis, "yearMonth").monthBadge).toBe("마감");
    expect(periodStatus({ year: 2026, month: 10 }, basis, "yearMonth").monthBadge).toBe("작업중");
  });
});

describe("PeriodSelector reused with different settings (AC-0105)", () => {
  it("year+month: next arrow is dimmed at the latest month and badges follow the month", () => {
    render(<Harness kind="yearMonth" description="그 달에 투입이 있는 사람" />);
    expect(screen.getByTestId("period-selector")).toHaveAttribute("data-kind", "yearMonth");
    expect(screen.getByTestId("period-month-next")).toBeDisabled();
    expect(screen.getByTestId("period-year-next")).toBeDisabled();
    expect(screen.getByTestId("period-month-badge")).toHaveTextContent("작업중");
    fireEvent.click(screen.getByTestId("period-month-prev"));
    expect(screen.getByTestId("period-month")).toHaveValue("9");
    expect(screen.getByTestId("period-month-badge")).toHaveTextContent("마감");
    expect(screen.getByTestId("period-description")).toHaveTextContent("이 화면의 기준: 그 달에 투입이 있는 사람");
  });

  it("year only with next year allowed: shows 내년 · 계획 and has no month field", () => {
    render(<Harness kind="year" allowNextYear />);
    expect(screen.queryByTestId("period-month")).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("period-year-next"));
    expect(screen.getByTestId("period-year")).toHaveValue("2027");
    expect(screen.getByTestId("period-year-badge")).toHaveTextContent("내년 · 계획");
    expect(screen.getByTestId("period-year-next")).toBeDisabled();
  });

  it("cumulative before-start screen: past year jumps to December and shows 결산 완료", () => {
    render(<Harness kind="yearCumulative" allowBeforeStart />);
    fireEvent.click(screen.getByTestId("period-year-prev"));
    expect(screen.getByTestId("period-year")).toHaveValue("2025");
    expect(screen.getByTestId("period-month")).toHaveValue("12");
    expect(screen.getByTestId("period-year-badge")).toHaveTextContent("결산 완료");
    expect(screen.getByTestId("period-year-prev")).toBeDisabled();
  });

  it("mobile shows dropdowns only and keeps labels for screen readers", () => {
    render(<Harness kind="yearMonth" mobile />);
    expect(screen.queryByTestId("period-year-prev")).not.toBeInTheDocument();
    expect(screen.getByLabelText("연도")).toBeInTheDocument();
    expect(screen.getByLabelText("월")).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "기간" })).toBeInTheDocument();
  });

  it("never reports a value outside the allowed range", () => {
    const onChange = vi.fn();
    render(
      <PeriodSelector basis={basis} kind="yearMonth" value={{ year: 2026, month: 10 }} onChange={onChange} />,
    );
    fireEvent.change(screen.getByTestId("period-month"), { target: { value: "11" } });
    expect(onChange).not.toHaveBeenCalled();
  });
});

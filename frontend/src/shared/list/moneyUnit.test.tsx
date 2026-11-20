import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  DEFAULT_MONEY_UNIT,
  FormulaBox,
  Money,
  MoneyUnitSelect,
  formatKoreanMoney,
  formatMoney,
} from ".";

afterEach(cleanup);

it("formats money like the mockup money(): divide, round, comma and − for negatives", () => {
  expect(DEFAULT_MONEY_UNIT).toBe("천 원");
  expect(formatMoney(1234567, "원")).toBe("1,234,567");
  expect(formatMoney(1234567, "천 원")).toBe("1,235");
  expect(formatMoney(1234567, "백만 원")).toBe("1");
  expect(formatMoney(-1600, "천 원")).toBe("−2");
  expect(formatMoney(-400, "천 원")).toBe("0");
  expect(formatMoney(null, "원")).toBe("–");
});

it("formats summary money like the mockup kor(): 억 원 from 1억, otherwise 만 원", () => {
  expect(formatKoreanMoney(177_000_000)).toBe("1.8억 원");
  expect(formatKoreanMoney(-250_000_000)).toBe("−2.5억 원");
  expect(formatKoreanMoney(12_345_678)).toBe("1,235만 원");
  expect(formatKoreanMoney(-55_000)).toBe("−6만 원");
});

it("unit select offers 원·천 원·백만 원 and Money marks negatives", () => {
  const onChange = vi.fn();
  render(
    <>
      <MoneyUnitSelect value="천 원" onChange={onChange} />
      <span data-testid="m">
        <Money value={-2000} unit="천 원" />
      </span>
    </>,
  );
  const select = screen.getByLabelText("단위") as HTMLSelectElement;
  expect([...select.options].map((option) => option.text)).toEqual([
    "원",
    "천 원",
    "백만 원",
  ]);
  fireEvent.change(select, { target: { value: "원" } });
  expect(onChange).toHaveBeenCalledWith("원");
  expect(screen.getByTestId("m").querySelector(".ls-neg")).toHaveTextContent(
    "−2",
  );
});

it("FormulaBox renders the mockup 항목/계산식/참고 table and the foot note", () => {
  render(<FormulaBox rows={[["기간", "식", "참고 글"]]} foot="안내" />);
  expect(screen.getByText(/계산 방법/, { selector: "summary" })).toBeInTheDocument();
  expect(screen.getByText("이 화면의 숫자를 이렇게 계산합니다")).toBeVisible();
  expect(
    screen.getAllByRole("columnheader").map((cell) => cell.textContent),
  ).toEqual(["항목", "계산식", "참고"]);
  expect(screen.getByText("참고 글")).toBeVisible();
  expect(screen.getByText("안내")).toBeVisible();
});

import { afterEach, expect, it, vi } from "vitest";
import { useState } from "react";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  DEFAULT_MONEY_UNIT,
  FormulaBox,
  Money,
  MoneyUnitSelect,
  formatKoreanMoney,
  formatMoney,
  type MoneyUnit,
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
      <MoneyUnitSelect value="천 원" onChange={onChange} remember={false} />
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

// 금액 단위 저장(AMT_UNIT/GLOBAL): 처음에 읽고, 없으면 천 원, 바꾸면 읽은 시각으로 저장, 409면 다시 읽기
const preference = (prefVal: string | null, modDttm: string | null) => ({
  ok: true,
  status: 200,
  json: async () => ({
    success: true,
    meta: {},
    data: {
      prefTypeCd: "AMT_UNIT",
      prefKey: "GLOBAL",
      found: prefVal !== null,
      prefVal,
      modDttm,
    },
  }),
});
function UnitHarness({ onChange }: { onChange: (unit: string) => void }) {
  const [unit, setUnit] = useState<MoneyUnit>(DEFAULT_MONEY_UNIT);
  return (
    <MoneyUnitSelect
      value={unit}
      onChange={(next) => {
        onChange(next);
        setUnit(next);
      }}
    />
  );
}
it("unit select loads the saved account unit and saves a change with the read time", async () => {
  const fetchMock = vi.fn(async (_path: string, init?: RequestInit) =>
    init?.method === "PUT"
      ? preference("원", "2026-11-20T01:00:01Z")
      : preference("백만 원", "2026-11-20T01:00:00Z"),
  );
  vi.stubGlobal("fetch", fetchMock);
  const onChange = vi.fn();
  render(<UnitHarness onChange={onChange} />);
  await waitFor(() =>
    expect(screen.getByLabelText("단위")).toHaveValue("백만 원"),
  );
  expect(fetchMock.mock.calls[0][0]).toBe(
    "/api/common/user-preferences/AMT_UNIT/GLOBAL",
  );
  fireEvent.change(screen.getByLabelText("단위"), { target: { value: "원" } });
  expect(screen.getByLabelText("단위")).toHaveValue("원");
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  const [, init] = fetchMock.mock.calls[1];
  expect(init?.method).toBe("PUT");
  expect(JSON.parse(String(init?.body))).toEqual({
    prefVal: "원",
    modDttm: "2026-11-20T01:00:00Z",
  });
  vi.unstubAllGlobals();
});
it("unit select uses 천 원 when nothing is saved and reloads after a 409", async () => {
  let saved: string | null = null;
  const fetchMock = vi.fn(async (_path: string, init?: RequestInit) => {
    if (init?.method === "PUT") {
      saved = "백만 원";
      return {
        ok: false,
        status: 409,
        json: async () => ({ success: false, message: "conflict" }),
      };
    }
    return preference(saved, saved ? "2026-11-20T02:00:00Z" : null);
  });
  vi.stubGlobal("fetch", fetchMock);
  render(<UnitHarness onChange={() => undefined} />);
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
  expect(screen.getByLabelText("단위")).toHaveValue("천 원");
  fireEvent.change(screen.getByLabelText("단위"), { target: { value: "원" } });
  await waitFor(() =>
    expect(screen.getByLabelText("단위")).toHaveValue("백만 원"),
  );
  expect(fetchMock).toHaveBeenCalledTimes(3);
  vi.unstubAllGlobals();
});
it("unit select with remember={false} never reads or saves", () => {
  const fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
  const onChange = vi.fn();
  render(<MoneyUnitSelect value="천 원" onChange={onChange} remember={false} />);
  fireEvent.change(screen.getByLabelText("단위"), { target: { value: "원" } });
  expect(onChange).toHaveBeenCalledWith("원");
  expect(fetchMock).not.toHaveBeenCalled();
  vi.unstubAllGlobals();
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

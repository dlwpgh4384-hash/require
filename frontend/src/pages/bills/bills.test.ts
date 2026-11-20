import { expect, it } from "vitest";
import { defaultPayDue } from "./bills";

it("입금 예정일 기본값은 청구월 다음 달 25일", () => {
  expect(defaultPayDue("202611")).toBe("2026-12-25");
  expect(defaultPayDue("202612")).toBe("2027-01-25");
  expect(defaultPayDue("202611", "2026-11-20")).toBe("2026-12-25");
  // 같은 날은 그대로
  expect(defaultPayDue("202610", "2026-11-25")).toBe("2026-11-25");
});

it("지난 달 청구로 그 날이 청구일보다 빠르면 청구일 다음 달 25일", () => {
  expect(defaultPayDue("202609", "2026-11-20")).toBe("2026-12-25");
  expect(defaultPayDue("202610", "2026-11-26")).toBe("2026-12-25");
  expect(defaultPayDue("202611", "2026-12-31")).toBe("2027-01-25");
});

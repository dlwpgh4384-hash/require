import { expect, it } from "vitest";
import {
  formatDateTime,
  koreanInputToUtc,
  koreanThisMonth,
  koreanToday,
} from "./time";

it("서버 UTC 시각을 한국 시간으로 보여 준다", () => {
  expect(formatDateTime("2026-10-12T00:02:00Z")).toBe("2026-10-12 09:02");
  expect(formatDateTime("2026-10-31T15:30:00.123456Z")).toBe("2026-11-01 00:30");
  expect(formatDateTime(null)).toBe("–");
});

it("한국 시간 입력을 UTC ISO로 바꿔 보낸다", () => {
  expect(koreanInputToUtc("2026-11-01 08:00")).toBe("2026-10-31T23:00:00.000Z");
  expect(koreanInputToUtc("2026-11-01T09:30")).toBe("2026-11-01T00:30:00.000Z");
  expect(koreanInputToUtc("11/01 08:00")).toBeNull();
});

it("오늘·이번 달은 한국 달력으로 계산한다(매달 1일 오전 9시 전도 이번 달)", () => {
  const firstMorning = new Date("2026-10-31T23:00:00Z"); // 한국 11-01 08:00
  expect(koreanToday(firstMorning)).toBe("2026-11-01");
  expect(koreanThisMonth(firstMorning)).toBe("202611");
});

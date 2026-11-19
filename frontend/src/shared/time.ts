/**
 * 시각 공통(2026-11-12 결정): API는 ISO 8601 UTC(…Z), 화면은 한국 시간(Asia/Seoul)으로 보여 주고 입력받는다.
 * 업무 날짜·월(YYYY-MM-DD, YYYYMM)은 시각이 아니라 한국 달력 값이라 바꾸지 않는다.
 */
export const KOREA_TIME_ZONE = "Asia/Seoul";

function parts(value: Date) {
  const found = new Intl.DateTimeFormat("en-CA", {
    timeZone: KOREA_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(value);
  const get = (type: string) =>
    found.find((part) => part.type === type)?.value ?? "";
  return {
    date: `${get("year")}-${get("month")}-${get("day")}`,
    time: `${get("hour")}:${get("minute")}`,
  };
}

/** 서버 시각(UTC ISO)을 화면 시각 「yyyy-MM-dd HH:mm」(한국 시간)으로. 없으면 「–」. */
export function formatDateTime(value: string | null | undefined) {
  if (!value) return "–";
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) return value;
  const { date, time: clock } = parts(time);
  return `${date} ${clock}`;
}

/** 서버 시각(UTC ISO)의 한국 날짜 「yyyy-MM-dd」. 없으면 「–」. */
export function formatDate(value: string | null | undefined) {
  if (!value) return "–";
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) return value;
  return parts(time).date;
}

/** 서버 시각(UTC ISO)의 한국 날짜 「MM-dd」(시안 「10-15 작성」). 없으면 「–」. */
export function formatMonthDay(value: string | null | undefined) {
  const date = formatDate(value);
  return /^\d{4}-\d{2}-\d{2}$/.test(date) ? date.slice(5) : date;
}

/** 한국 날짜 두 개(yyyy-MM-dd) 사이의 날 수(to − from). */
export function daysBetween(from: string, to: string) {
  return Math.round(
    (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000,
  );
}

/** 한국 날짜(yyyy-MM-dd)에 날 수를 더한 날짜. */
export function addDays(date: string, days: number) {
  const day = new Date(`${date}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + days);
  return day.toISOString().slice(0, 10);
}

/** 화면에서 입력받은 한국 시각 「yyyy-MM-dd HH:mm」(또는 datetime-local 값)을 서버로 보낼 UTC ISO로. */
export function koreanInputToUtc(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?$/.exec(
    value.trim(),
  );
  if (!match) return null;
  const [, y, m, d, hh, mm, ss] = match;
  // 한국은 서머타임이 없어 UTC+9 고정.
  const utc = Date.UTC(+y, +m - 1, +d, +hh - 9, +mm, ss ? +ss : 0);
  return new Date(utc).toISOString();
}

/** 「오늘」(한국 날짜) yyyy-MM-dd. */
export function koreanToday(now = new Date()) {
  return parts(now).date;
}

/** 「이번 달」(한국 달력) YYYYMM. */
export function koreanThisMonth(now = new Date()) {
  return koreanToday(now).slice(0, 7).replace("-", "");
}

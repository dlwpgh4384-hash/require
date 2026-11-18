import { afterEach, expect, it, vi } from "vitest";
import { getCommonManpowerComparisons } from "./manpowerComparisons";
import { ProjectRequestError } from "../projects/projects";

afterEach(() => vi.unstubAllGlobals());

it("uses the anonymous relative GET with all applied filters and abort signal", async () => {
  const data = {
    items: [],
    totals: { plannedMm: 0, actualMm: 0, differenceMm: 0 },
  };
  const fetcher = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ success: true, meta: {}, data }),
  });
  vi.stubGlobal("fetch", fetcher);
  const signal = new AbortController().signal;
  expect(
    await getCommonManpowerComparisons(
      { year: 2026, projectId: "P A", differenceStatus: "부족" },
      signal,
    ),
  ).toEqual(data);
  const [url, init] = fetcher.mock.calls[0];
  expect(String(url)).toMatch(/^\/api\/common\/manpower-comparisons\?/);
  expect(
    Object.fromEntries(new URL(String(url), "http://test").searchParams),
  ).toEqual({ year: "2026", projectId: "P A", differenceStatus: "부족" });
  expect(init.signal).toBe(signal);
  expect(init.method ?? "GET").toBe("GET");
});

it("omits absent optional parameters and preserves all numeric zero totals", async () => {
  const data = {
    items: [],
    totals: { plannedMm: 0, actualMm: 0, differenceMm: 0 },
  };
  const fetcher = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ success: true, meta: {}, data }),
  });
  vi.stubGlobal("fetch", fetcher);
  expect(await getCommonManpowerComparisons({ year: 2025 })).toEqual(data);
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/common/manpower-comparisons?year=2025",
  );
});

it("preserves field errors instead of returning a successful empty list", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        success: false,
        meta: {},
        message: "조건을 확인해 주세요.",
        errors: [{ field: "year", message: "연도를 확인해 주세요." }],
      }),
    }),
  );
  const promise = getCommonManpowerComparisons({ year: 2028 });
  await expect(promise).rejects.toBeInstanceOf(ProjectRequestError);
  await expect(promise).rejects.toMatchObject({
    errors: [{ field: "year", message: "연도를 확인해 주세요." }],
  });
});

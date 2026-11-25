import { afterEach, expect, it, vi } from "vitest";
import {
  getCommonManpowerForecasts,
  getCommonManpowerForecastsExcel,
} from "./manpowerForecasts";
import { downloadFile } from "../../shared/list/download";
vi.mock("../../shared/list/download", () => ({ downloadFile: vi.fn() }));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});
it("requests the selected year through the relative public query URL", async () => {
  const data = {
    months: [],
    summary: { lastMonthSupply: 0, lastMonthDemand: 0, mostShortage: null },
    thresholds: { shortageThreshold: 0.4, surplusThreshold: 1 },
  };
  const fetcher = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const controller = new AbortController();
  expect(await getCommonManpowerForecasts(2027, controller.signal)).toEqual(
    data,
  );
  expect(fetcher.mock.calls[0][0]).toBe(
    "/api/common/manpower-forecasts?year=2027",
  );
  expect(fetcher.mock.calls[0][1].signal).toBe(controller.signal);
});
it("reuses downloadFile and returns its X-Total-Count unchanged", async () => {
  vi.mocked(downloadFile).mockImplementation(
    async (url, fallback, _error, inspect) => {
      expect(url).toBe("/api/common/manpower-forecasts/excel?year=2027");
      inspect?.(new Response(null, { headers: { "X-Total-Count": "3" } }));
      return fallback;
    },
  );
  expect(await getCommonManpowerForecastsExcel(2027)).toBe(3);
});
it.each([null, "", "three", "-1"])(
  "missing or malformed count %s cannot produce a completion success",
  async (header) => {
    vi.mocked(downloadFile).mockImplementation(
      async (_url, fallback, _error, inspect) => {
        inspect?.(
          new Response(null, {
            headers: header === null ? {} : { "X-Total-Count": header },
          }),
        );
        return fallback;
      },
    );
    await expect(getCommonManpowerForecastsExcel(2026)).rejects.toThrow(
      "조회 결과 건수를 확인하지 못했습니다.",
    );
  },
);

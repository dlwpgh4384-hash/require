import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  getCommonSalesPipelineClosed,
  getCommonSalesPipelineForecastExcel,
} from "./salesPipeline";
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  request = vi.fn();
  vi.stubGlobal("fetch", request);
  vi.stubGlobal(
    "URL",
    class extends URL {
      static createObjectURL = vi.fn(() => "blob:forecast");
      static revokeObjectURL = vi.fn();
    },
  );
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it("closed filters are encoded without adding role or part filters", async () => {
  request.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ success: true, data: { items: [] } }),
  });
  await getCommonSalesPipelineClosed("WIN", true);
  expect(request.mock.calls[0][0]).toBe(
    "/api/common/sales-pipeline/closed?includeStopped=true&result=WIN",
  );
});
it("download uses binary response count and server filename and revokes its URL", async () => {
  const headers = new Headers({
    "Content-Type":
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "X-Total-Count": "6",
    "Content-Disposition": "attachment; filename=forecast.xlsx",
  });
  request.mockResolvedValue({
    ok: true,
    headers,
    blob: async () => new Blob([new Uint8Array([80, 75])]),
  });
  expect(await getCommonSalesPipelineForecastExcel()).toBe(6);
  expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledOnce();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:forecast");
  expect(
    document.querySelector("[data-testid='sales-pipeline-download-link']"),
  ).toBeNull();
});
it("HTML response cannot become successful workbook", async () => {
  request.mockResolvedValue({
    ok: true,
    headers: new Headers({ "Content-Type": "text/html", "X-Total-Count": "6" }),
  });
  await expect(getCommonSalesPipelineForecastExcel()).rejects.toThrow(
    "엑셀 응답",
  );
  expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
});
it("missing count is an error rather than a fabricated zero", async () => {
  request.mockResolvedValue({
    ok: true,
    headers: new Headers({
      "Content-Type":
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    }),
  });
  await expect(getCommonSalesPipelineForecastExcel()).rejects.toThrow(
    "엑셀 응답",
  );
});
it("server generation failure is propagated without creating download", async () => {
  request.mockResolvedValue({
    ok: false,
    status: 500,
    json: async () => ({ message: "생성 실패" }),
  });
  await expect(getCommonSalesPipelineForecastExcel()).rejects.toThrow(
    "생성 실패",
  );
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

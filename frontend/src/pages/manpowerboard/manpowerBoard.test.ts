import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  getCommonManpowerBoard,
  getCommonManpowerBoardExcel,
} from "./manpowerBoard";
import { getCommonSalesPipelineForecast } from "../salespipeline/salesPipeline";

let request: ReturnType<typeof vi.fn>;
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
beforeEach(() => {
  request = vi.fn();
  vi.stubGlobal("fetch", request);
  vi.stubGlobal(
    "URL",
    class extends URL {
      static createObjectURL = vi.fn(() => "blob:manpower-board");
      static revokeObjectURL = vi.fn();
    },
  );
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
});
afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it("sends only the approved conditions with no pagination, authorization or detail requests", async () => {
  request.mockResolvedValue({
    ok: true,
    json: async () => ({ success: true, data: { items: [] }, meta: {} }),
  });
  await getCommonManpowerBoard({
    year: 2027,
    jobCode: "BE",
    gradeCode: "MD",
    employeeTypeCode: "FT",
    onlyAvailable: true,
  });
  expect(request).toHaveBeenCalledTimes(1);
  expect(request.mock.calls[0][0]).toBe(
    "/api/common/manpower-board?year=2027&jobCode=BE&gradeCode=MD&employeeTypeCode=FT&onlyAvailable=true",
  );
});

it("asks the forecast from the given start month and keeps the plain call unchanged", async () => {
  request.mockResolvedValue({
    ok: true,
    json: async () => ({ success: true, data: { months: [] }, meta: {} }),
  });
  await getCommonSalesPipelineForecast("2026-11");
  await getCommonSalesPipelineForecast();
  expect(request.mock.calls.map(([url]) => url)).toEqual([
    "/api/common/sales-pipeline/forecast?startMonth=2026-11",
    "/api/common/sales-pipeline/forecast",
  ]);
});

it("downloads the annual workbook through the common helper using the server filename and full-result count", async () => {
  vi.useFakeTimers();
  request.mockResolvedValue({
    ok: true,
    headers: new Headers({
      "Content-Type": mime,
      "X-Total-Count": "51",
      "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent("인력 운영 보드_2026년.xlsx")}`,
    }),
    blob: async () => new Blob([new Uint8Array([80, 75])]),
  });
  let filename = "";
  vi.mocked(HTMLAnchorElement.prototype.click).mockImplementation(function (
    this: HTMLAnchorElement,
  ) {
    filename = this.download;
  });
  expect(await getCommonManpowerBoardExcel({ year: 2026, jobCode: "BE" })).toBe(
    51,
  );
  expect(filename).toBe("인력 운영 보드_2026년.xlsx");
  expect(request).toHaveBeenCalledWith(
    "/api/common/manpower-board/excel?year=2026&jobCode=BE",
  );
  expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledOnce();
  expect(document.querySelector("a[download]")).toBeNull();
  vi.runAllTimers();
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:manpower-board");
});

it("propagates the shared field error envelope", async () => {
  request.mockResolvedValue({
    ok: false,
    status: 400,
    headers: new Headers({ "Content-Type": "application/json" }),
    json: async () => ({
      success: false,
      meta: {},
      message: "연도 오류",
      errors: [{ field: "year", message: "허용 연도" }],
    }),
  });
  await expect(
    getCommonManpowerBoardExcel({ year: 2028 }),
  ).rejects.toMatchObject({
    message: "연도 오류",
    errors: [{ field: "year", message: "허용 연도" }],
  });
  expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
});

it("does not invent zero when the count header is missing", async () => {
  request.mockResolvedValue({
    ok: true,
    headers: new Headers({ "Content-Type": mime }),
    blob: async () => new Blob([new Uint8Array([80, 75])]),
  });
  await expect(getCommonManpowerBoardExcel({ year: 2026 })).rejects.toThrow(
    "건수를 확인하지 못했습니다",
  );
  expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
  expect(URL.createObjectURL).not.toHaveBeenCalled();
});

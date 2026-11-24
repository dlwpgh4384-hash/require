import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  getCommonSalesPipelineClosed,
  getCommonSalesPipelineForecastExcel,
  putCommonSalesPipelineStage,
  approvalBadge,
  cardJobs,
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
it("stage move sends PUT with stage code and read time to the opportunity stage endpoint", async () => {
  request.mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      data: { opportunityId: "O-1", winningProbability: 30 },
    }),
  });
  const saved = await putCommonSalesPipelineStage(
    { opportunityId: "O-1", modifiedAt: "2026-11-01T00:00:00Z" },
    "PROP",
  );
  expect(saved.winningProbability).toBe(30);
  expect(request.mock.calls[0][0]).toBe(
    "/api/common/sales-opportunities/O-1/stage",
  );
  expect(request.mock.calls[0][1].method).toBe("PUT");
  expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({
    stageCode: "PROP",
    modifiedAt: "2026-11-01T00:00:00Z",
  });
});
it("approval badge is hidden when no approval is needed and never shows raw codes", () => {
  expect(approvalBadge("NONE")).toBeNull();
  expect(approvalBadge(null)).toBeNull();
  expect(approvalBadge("WAIT")).toEqual({
    text: "견적 승인 대기",
    kind: "bad",
    icon: true,
  });
  expect(approvalBadge("OK")?.text).toBe("견적 승인");
  expect(approvalBadge("REJ")?.text).toBe("견적 반려");
});
it("card chips prefer per-job headcounts and fall back to the job code list", () => {
  const base = {
    opportunityId: "O",
    opportunityName: "기회",
    customerId: "c",
    customerName: "고객",
    stageCode: "FIND",
    winningProbability: 10,
    expectedAmount: 1,
    startMonth: "2027-01",
    nextAction: null,
    nextActionDueDate: null,
    requiredJobs: ["BE", "FE"],
    stale: false,
    approvalStatusCode: "NONE",
    modifiedAt: "2026-11-01T00:00:00Z",
  };
  expect(cardJobs(base).map((job) => job.count)).toEqual([null, null]);
  expect(
    cardJobs({
      ...base,
      requiredJobHeadcounts: [{ jobCode: "BE", jobName: "백엔드 개발", headcount: 3 }],
    }),
  ).toEqual([{ jobCode: "BE", jobName: "백엔드 개발", count: 3 }]);
});

import { afterEach, expect, it, vi } from "vitest";
import {
  getCommonRateRenegotiations,
  getCommonRateRenegotiationsExcel,
  getRateRenegotiationOptions,
  rateRenegotiationDefaults,
  rateRenegotiationQuery,
} from "./rateRenegotiations";
import { downloadFile } from "../../shared/list/download";
import { ProjectRequestError } from "../projects/projects";
vi.mock("../../shared/list/download", () => ({
  downloadFile: vi.fn().mockResolvedValue("대상.xlsx"),
}));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});
it("sends only approved query keys and repeated visible columns in caller order", () => {
  const query = rateRenegotiationQuery(
    {
      ...rateRenegotiationDefaults(),
      employeeTypeCode: "CT",
      projectId: "프로젝트 / A",
    },
    ["profitRate", "developerName"],
  );
  expect(query.getAll("columns")).toEqual(["profitRate", "developerName"]);
  expect([...query.keys()]).toEqual([
    "renewalMonths",
    "profitRateThreshold",
    "employeeTypeCode",
    "projectId",
    "increaseRate",
    "columns",
    "columns",
  ]);
  expect(query.get("projectId")).toBe("프로젝트 / A");
});
it("uses the existing relative API and propagates the cancellation signal", async () => {
  const request = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ success: true, data: { items: [] } }),
  });
  vi.stubGlobal("fetch", request);
  const controller = new AbortController();
  await getCommonRateRenegotiations(
    rateRenegotiationDefaults(),
    controller.signal,
  );
  expect(request).toHaveBeenCalledWith(
    "/api/common/rate-renegotiations?renewalMonths=6&profitRateThreshold=15&increaseRate=5",
    { signal: controller.signal },
  );
});
it("exports the applied assumption and visible columns without monthly cost when hidden", async () => {
  await getCommonRateRenegotiationsExcel(
    { ...rateRenegotiationDefaults(), increaseRate: 8 },
    ["developerName", "annualProfitIncrease"],
  );
  expect(downloadFile).toHaveBeenCalledWith(
    "/api/common/rate-renegotiations/excel?renewalMonths=6&profitRateThreshold=15&increaseRate=8&columns=developerName&columns=annualProfitIncrease",
    "단가 재협상 대상.xlsx",
    expect.any(Function),
  );
});
it("preserves export field errors through the shared downloader", async () => {
  const actual = await vi.importActual<
    typeof import("../../shared/list/download")
  >("../../shared/list/download");
  vi.mocked(downloadFile).mockImplementationOnce(actual.downloadFile);
  const errors = [
    { field: "columns", message: "보이는 열을 중복 없이 선택해 주세요." },
  ];
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        success: false,
        meta: {},
        message: errors[0].message,
        errors,
      }),
    }),
  );
  await expect(
    getCommonRateRenegotiationsExcel(rateRenegotiationDefaults(), ["unknown"]),
  ).rejects.toMatchObject({
    constructor: ProjectRequestError,
    message: errors[0].message,
    errors,
  });
});
it("keeps the shared downloader's default error behavior for existing callers", async () => {
  const actual = await vi.importActual<
    typeof import("../../shared/list/download")
  >("../../shared/list/download");
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => {
        throw new Error("not JSON");
      },
    }),
  );
  await expect(
    actual.downloadFile("/api/common/rate-renegotiations/excel", "대상.xlsx"),
  ).rejects.toEqual(
    new Error("엑셀 파일을 받지 못했습니다. 다시 시도해 주세요."),
  );
});
it("normalizes null option payloads safely", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ success: true, data: { items: null } }),
    }),
  );
  expect(await getRateRenegotiationOptions()).toEqual({
    employees: [],
    projects: [],
  });
});

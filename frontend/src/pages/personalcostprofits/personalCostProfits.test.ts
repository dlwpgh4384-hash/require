import { afterEach, expect, it, vi } from "vitest";
import {
  getCommonPersonalCostProfitsExcel,
  personalCostProfitQuery,
} from "./personalCostProfits";
import type { PersonalCostProfitFilter } from "./personalCostProfitTypes";
const filter: PersonalCostProfitFilter = {
  year: 2026,
  baseMonth: 2,
  displayMode: "연누계",
  includeTurnkey: false,
  columns: ["developerName", "unitPrice"],
};
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it("encodes literal filters without adding unsupported pagination, contract type or expansion parameters", () => {
  const query = personalCostProfitQuery({
    ...filter,
    name: "김%_",
    jobCode: undefined,
  });
  expect(query.get("name")).toBe("김%_");
  expect(query.has("jobCode")).toBe(false);
  expect(query.get("columns")).toBe("developerName,unitPrice");
  expect(query.has("page")).toBe(false);
});
it("downloads selected visible columns for the full filter and uses server count and filename", async () => {
  const request = vi.fn(async () => ({
    ok: true,
    headers: new Headers({
      "Content-Type":
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "Content-Disposition":
        "attachment; filename*=UTF-8''%EA%B0%9C%EC%9D%B8.xlsx",
      "X-Total-Count": "27",
    }),
    blob: async () => new Blob([new Uint8Array([80, 75])]),
  }));
  vi.stubGlobal("fetch", request);
  const create = vi.fn(() => "blob:personal-workbook");
  const revoke = vi.fn();
  vi.stubGlobal(
    "URL",
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke }),
  );
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => undefined);
  expect(await getCommonPersonalCostProfitsExcel(filter)).toBe(27);
  expect(request).toHaveBeenCalledWith(
    expect.stringContaining("includeTurnkey=false"),
  );
  expect(request).toHaveBeenCalledWith(
    expect.stringContaining("columns=developerName%2CunitPrice"),
  );
  expect(click).toHaveBeenCalledOnce();
  expect(revoke).toHaveBeenCalledWith("blob:personal-workbook");
});
it("does not download a JSON validation error disguised as a file", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: false,
      headers: new Headers({ "Content-Type": "application/json" }),
      json: async () => ({ message: "등록된 열을 선택해 주세요." }),
    })),
  );
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => undefined);
  await expect(getCommonPersonalCostProfitsExcel(filter)).rejects.toThrow(
    "등록된 열을 선택해 주세요.",
  );
  expect(click).not.toHaveBeenCalled();
});
it("does not report success on a missing full-result count", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: true,
      headers: new Headers({
        "Content-Type":
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      }),
    })),
  );
  await expect(getCommonPersonalCostProfitsExcel(filter)).rejects.toThrow(
    "조회 결과 건수",
  );
});

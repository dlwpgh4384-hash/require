import { afterEach, expect, it, vi } from "vitest";
import { getCommonCodeChangeHistory } from "./codeHistory";

afterEach(() => vi.unstubAllGlobals());

it("rejects a 500 with safe Korean feedback rather than internal server details", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: false,
      status: 500,
      json: async () => ({ success: false, message: "private SQL" }),
    })),
  );
  await expect(getCommonCodeChangeHistory()).rejects.toThrow(
    "변경 이력을 불러오지 못했습니다.",
  );
});

it("retains a contract validation message on 400", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: false,
      status: 400,
      json: async () => ({
        success: false,
        message: "입력 형식을 확인해 주세요.",
      }),
    })),
  );
  await expect(getCommonCodeChangeHistory()).rejects.toThrow(
    "입력 형식을 확인해 주세요.",
  );
});

it("rejects success envelopes without an items array", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ success: true, meta: {}, data: {} }),
    })),
  );
  await expect(getCommonCodeChangeHistory()).rejects.toThrow(
    "변경 이력 응답을 확인해 주세요.",
  );
});

it("rejects a non-JSON response instead of showing an empty list", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => {
        throw new SyntaxError("not JSON");
      },
    })),
  );
  await expect(getCommonCodeChangeHistory()).rejects.toThrow();
});

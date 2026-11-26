import { describe, expect, it } from "vitest";
import { NETWORK_MESSAGE, wrapNetworkErrors } from "./network";

describe("wrapNetworkErrors", () => {
  it("turns a browser network failure into the Korean message", async () => {
    const target = {
      fetch: (async () => {
        throw new TypeError("Failed to fetch");
      }) as typeof fetch,
    };
    wrapNetworkErrors(target);
    await expect(target.fetch("/api/health")).rejects.toThrow(NETWORK_MESSAGE);
  });
  it("keeps aborts and server responses as they are", async () => {
    const abort = new DOMException("aborted", "AbortError");
    const target = {
      fetch: (async () => {
        throw abort;
      }) as typeof fetch,
    };
    wrapNetworkErrors(target);
    await expect(target.fetch("/api/health")).rejects.toBe(abort);
    const response = new Response("{}", { status: 500 });
    const ok = { fetch: (async () => response) as typeof fetch };
    wrapNetworkErrors(ok);
    await expect(ok.fetch("/api/health")).resolves.toBe(response);
  });
});

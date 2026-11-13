import { afterEach, expect, it, vi } from "vitest";
import * as api from "./roles";

afterEach(() => vi.unstubAllGlobals());

function respond(data: unknown) {
  const request = vi.fn(async () => ({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  }));
  vi.stubGlobal("fetch", request);
  return request;
}

it("reads the items envelope and preserves an empty database result", async () => {
  const request = respond({ items: [] });
  const signal = new AbortController().signal;
  await expect(api.getCommonRoles(signal)).resolves.toEqual({ items: [] });
  expect(request).toHaveBeenCalledWith("/api/common/roles", { signal });
});

it.each([{}, { items: null }, { items: {} }])(
  "rejects a mismatched list envelope instead of synthesizing role rows: %j",
  async (data) => {
    respond(data);
    await expect(api.getCommonRoles()).rejects.toThrow("역할 목록 응답");
  },
);

it("uses the controller detail, impact and stop methods and encoded role path", async () => {
  const request = respond({});
  await api.getCommonRole("R/001");
  expect(request).toHaveBeenLastCalledWith("/api/common/roles/R%2F001", {
    signal: undefined,
  });
  await api.getCommonRoleChangeImpact("R-001", "PART");
  expect(request).toHaveBeenLastCalledWith(
    "/api/common/roles/R-001/change-impact?dataScopeCd=PART",
    undefined,
  );
  await api.postCommonRoleStop("R-001");
  expect(request).toHaveBeenLastCalledWith("/api/common/roles/R-001/stop", {
    method: "POST",
  });
});

it("sends copy and update DTOs without adding user identity or authorization", async () => {
  const request = respond({});
  const copy: api.RoleCopy = {
    sourceRoleId: "PM",
    roleName: "전송 계약 시험",
    dataScopeCd: "PROJ",
  };
  await api.postCommonRoleCopy(copy);
  expect(request).toHaveBeenLastCalledWith("/api/common/roles", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(copy),
  });
  const update: api.RoleUpdate = {
    dataScopeCd: "PART",
    snstvViewYn: "N",
    menuAuths: [],
    confirmedAffectedUserIds: [],
  };
  await api.putCommonRole("R-001", update);
  expect(request).toHaveBeenLastCalledWith("/api/common/roles/R-001", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(update),
  });
});

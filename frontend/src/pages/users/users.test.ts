import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  getCommonUsers,
  getCommonUser,
  getCommonUserAccessLogs,
  getCommonUserChangeImpact,
  putCommonUsers,
  putCommonUser,
  putCommonUserStop,
  putCommonUserPasswordReset,
  putCommonUserResume,
  putCommonUserUnlock,
  UserRequestError,
} from "./users";

const account = {
  userId: "test",
  name: "시험 사용자",
  email: "test@example.invalid",
  roleCds: ["PM"],
  headquarters: false,
  projectIds: [],
  userStatCd: "USE",
  modDttm: "2026-01-02T03:04:05.123456Z",
};
const version = { modDttm: account.modDttm };
let request: ReturnType<typeof vi.fn>;
function reply(data: unknown) {
  request.mockResolvedValue(
    new Response(JSON.stringify({ success: true, data }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
}
/** 2026-11-12 결정: 등록·처리 동작은 POST, 수정만 PUT. */
function expectWrite(path: string, body: unknown, method = "POST") {
  expect(request).toHaveBeenCalledTimes(1);
  expect(request).toHaveBeenCalledWith(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
beforeEach(() => {
  request = vi.fn();
  vi.stubGlobal("fetch", request);
});
afterEach(() => vi.unstubAllGlobals());

it("GET users sends encoded optional filters and preserves pagination", async () => {
  const page = { items: [account], page: 2, pageSize: 50, total: 51 };
  const signal = new AbortController().signal;
  reply(page);
  await expect(
    getCommonUsers(
      {
        page: 2,
        pageSize: 50,
        roleCd: "PM",
        userStatCd: "USE",
        keyword: "가 & 나",
      },
      signal,
    ),
  ).resolves.toEqual(page);
  expect(request).toHaveBeenCalledTimes(1);
  expect(request).toHaveBeenCalledWith(
    "/api/common/users?page=2&pageSize=50&roleCd=PM&userStatCd=USE&keyword=%EA%B0%80+%26+%EB%82%98",
    { signal },
  );
});
it("GET detail encodes a single path segment and forwards abort signal", async () => {
  const signal = new AbortController().signal;
  reply(account);
  await expect(getCommonUser("a/b", signal)).resolves.toEqual(account);
  expect(request).toHaveBeenCalledTimes(1);
  expect(request).toHaveBeenCalledWith("/api/common/users/a%2Fb", { signal });
});
it("GET access logs sends pagination to the literal backend operation", async () => {
  const page = { items: [], page: 2, pageSize: 50, total: 0 };
  reply(page);
  await expect(getCommonUserAccessLogs("test", 2, 50)).resolves.toEqual(page);
  expect(request).toHaveBeenCalledTimes(1);
  expect(request).toHaveBeenCalledWith(
    "/api/common/users/test/access-logs?page=2&pageSize=50",
    { signal: undefined },
  );
});
it("GET change impact preserves explicit role removal and user list", async () => {
  const impact = { projects: [], projectCount: 0, blockedUsers: [] };
  reply(impact);
  await expect(
    getCommonUserChangeImpact(["test", "other"], "ROLE_CHANGE", []),
  ).resolves.toEqual(impact);
  expect(request).toHaveBeenCalledTimes(1);
  expect(request).toHaveBeenCalledWith(
    "/api/common/users/change-impact?userIds=test%2Cother&action=ROLE_CHANGE&roleCds=",
  );
});
it("PUT registration sends JSON and returns saved account", async () => {
  const body = {
    userId: account.userId,
    name: account.name,
    email: account.email,
    roleCds: account.roleCds,
    headquarters: false,
    projectIds: [],
  };
  const saved = { account, transferredProjects: [] };
  reply(saved);
  await expect(putCommonUsers(body)).resolves.toEqual(saved);
  expectWrite("/api/common/users", body);
});
it("PUT detail preserves concurrency token and explicit confirmation", async () => {
  const body = {
    name: "시험 사용자",
    email: "test@example.invalid",
    roleCds: ["EXEC"],
    headquarters: false,
    projectIds: [],
    confirmedProjectIds: [],
    ...version,
  };
  const saved = {
    account: { ...account, roleCds: ["EXEC"] },
    transferredProjects: [],
  };
  reply(saved);
  await expect(putCommonUser("test", body)).resolves.toEqual(saved);
  expectWrite("/api/common/users/test", body, "PUT");
});
it("PUT stop returns partial failure reasons without discarding success", async () => {
  const users = [{ userId: "test", ...version }];
  const result = {
    processedUserIds: ["test"],
    unprocessedCount: 1,
    unprocessed: [{ userId: "admin", reason: "마지막 시스템 관리자" }],
  };
  reply(result);
  await expect(putCommonUserStop(users, [])).resolves.toEqual(result);
  expectWrite("/api/common/users/stop", { users, confirmedProjectIds: [] });
});
it("PUT password reset preserves nullable version property", async () => {
  const result = { userId: "test", tempPwdYn: "Y", modDttm: account.modDttm };
  reply(result);
  await expect(putCommonUserPasswordReset("test", null)).resolves.toEqual(
    result,
  );
  expectWrite("/api/common/users/test/password-reset", { modDttm: null });
});
it("PUT resume returns the account at the existing route", async () => {
  reply(account);
  await expect(putCommonUserResume("test", account.modDttm)).resolves.toEqual(
    account,
  );
  expectWrite("/api/common/users/test/resume", version);
});
it("PUT unlock returns state and cleared failure count", async () => {
  const result = {
    userId: "test",
    userStatCd: "USE",
    pwdErrCnt: 0,
    ...version,
  };
  reply(result);
  await expect(putCommonUserUnlock("test", account.modDttm)).resolves.toEqual(
    result,
  );
  expectWrite("/api/common/users/test/unlock", version);
});
it("validation response preserves field errors", async () => {
  const errors = [{ field: "modDttm", message: "버전을 보내 주세요." }];
  request.mockResolvedValue(
    new Response(
      JSON.stringify({ success: false, message: "입력 오류", errors }),
      { status: 400 },
    ),
  );
  await expect(putCommonUserResume("test", null)).rejects.toMatchObject({
    status: 400,
    errors,
    message: "입력 오류",
  });
});
it("conflict is not converted to a success response", async () => {
  request.mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        message: "다시 불러와 주세요.",
        errors: [],
      }),
      { status: 409 },
    ),
  );
  await expect(
    putCommonUserUnlock("test", account.modDttm),
  ).rejects.toMatchObject({ status: 409, message: "다시 불러와 주세요." });
});
it("server errors do not expose internal details", async () => {
  request.mockResolvedValue(
    new Response(
      JSON.stringify({ success: false, message: "internal database detail" }),
      { status: 500 },
    ),
  );
  await expect(getCommonUser("test")).rejects.toThrow(
    new UserRequestError(
      500,
      "사용자 정보를 처리하지 못했습니다. 다시 시도해 주세요.",
    ),
  );
});

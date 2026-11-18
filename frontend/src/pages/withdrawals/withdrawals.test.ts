import { afterEach, expect, it, vi } from "vitest";
import { AssignmentRequestError } from "../assignments/assignments";
import { putCommonWithdrawalTentativeAssignment } from "./withdrawals";

afterEach(() => vi.unstubAllGlobals());

it("가배정 PUT은 선택한 배정 경로와 기회·필요 인력 줄을 함께 보낸다", async () => {
  const assignment = {
    assignmentId: "A-selected",
    developerId: "D-selected",
    opportunityId: "O-selected",
    opportunityName: "선택한 기회",
    needSn: 7,
    startMonth: "2026-12",
  };
  const request = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data: { assignment } }),
  });
  vi.stubGlobal("fetch", request);

  await expect(
    putCommonWithdrawalTentativeAssignment(
      assignment.assignmentId,
      assignment.opportunityId,
      assignment.needSn,
    ),
  ).resolves.toEqual({ assignment });

  expect(request).toHaveBeenCalledTimes(1);
  const [path, init] = request.mock.calls[0] as [string, RequestInit];
  expect(path).toBe("/api/common/withdrawals/A-selected/tentative-assignment");
  expect(init.method).toBe("PUT");
  expect(init.headers).toEqual({ "Content-Type": "application/json" });
  expect(JSON.parse(String(init.body))).toEqual({
    opportunityId: "O-selected",
    needSn: 7,
  });
});

it("같은 필요 인력 줄 번호라도 기회를 바꾸면 새 기회 ID를 보낸다", async () => {
  const request = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data: { assignment: null } }),
  });
  vi.stubGlobal("fetch", request);

  await putCommonWithdrawalTentativeAssignment("A-selected", "O-first", 1);
  await putCommonWithdrawalTentativeAssignment("A-selected", "O-second", 1);

  expect(request).toHaveBeenCalledTimes(2);
  const bodies = request.mock.calls.map(([, init]) =>
    JSON.parse(String((init as RequestInit).body)),
  );
  expect(bodies).toEqual([
    { opportunityId: "O-first", needSn: 1 },
    { opportunityId: "O-second", needSn: 1 },
  ]);
});

it("가배정 충돌은 성공이나 신규 생성으로 바꾸지 않고 필드 오류를 전달한다", async () => {
  const errors = [{ field: "needSn", message: "남은 자리가 없습니다." }];
  const request = vi.fn().mockResolvedValue({
    ok: false,
    status: 409,
    json: async () => ({
      success: false,
      meta: {},
      message: errors[0].message,
      errors,
    }),
  });
  vi.stubGlobal("fetch", request);

  const result = putCommonWithdrawalTentativeAssignment(
    "A-selected",
    "O-selected",
    7,
  );
  await expect(result).rejects.toBeInstanceOf(AssignmentRequestError);
  await expect(result).rejects.toMatchObject({ status: 409, errors });
  expect(request).toHaveBeenCalledTimes(1);
});

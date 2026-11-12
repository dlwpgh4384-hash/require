import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { UserPage } from "./UserPage";
import { UserDialog } from "./UserDialog";
import { UserRegistrationButton, UserWorkspaceProvider } from "./UserWorkspace";
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import type { CommonUserOptionsResult } from "../commonops/userOptions";
import type { UserAccount } from "./users";

const options: CommonUserOptionsResult = {
  roles: [
    { roleCd: "PM", roleName: "프로젝트 관리자", assignedProjectScope: true },
    { roleCd: "EXEC", roleName: "경영진", assignedProjectScope: false },
  ],
  projects: [{ projectId: "project-a", projectName: "합성 프로젝트" }],
  userStates: [
    { code: "USE", name: "사용" },
    { code: "LOCK", name: "잠김" },
    { code: "STOP", name: "중지" },
  ],
};
const token = "2026-01-02T03:04:05.123456Z";
let account: UserAccount;
let request: ReturnType<typeof vi.fn>;
const response = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data }),
});
const writes = () =>
  request.mock.calls.filter(
    (call: unknown[]) => (call[1] as RequestInit | undefined)?.method === "PUT",
  );
beforeEach(() => {
  account = {
    userId: "test",
    name: "시험 사용자",
    email: "test@example.invalid",
    roleCds: ["PM"],
    headquarters: false,
    projectIds: ["project-a"],
    userStatCd: "USE",
    modDttm: token,
  };
  request = vi.fn(async (url: string, init?: RequestInit) => {
    if (init?.method === "PUT") {
      if (url.endsWith("/stop"))
        return response({
          processedUserIds: ["test"],
          unprocessedCount: 1,
          unprocessed: [{ userId: "admin", reason: "마지막 관리자" }],
        });
      return response({ account, transferredProjects: [] });
    }
    if (url.includes("user-options")) return response(options);
    if (url.includes("change-impact"))
      return response({
        projectCount: 1,
        projects: options.projects,
        blockedUsers: [],
      });
    if (url.includes("access-logs"))
      return response({ items: [], page: 1, pageSize: 20, total: 0 });
    if (url.includes("users?"))
      return response({
        items: [
          {
            ...account,
            roleNames: ["프로젝트 관리자"],
            assignedParts: [],
            lastAccessAt: null,
          },
        ],
        page: 1,
        pageSize: 20,
        total: 1,
      });
    return response(account);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
function page() {
  return render(
    <UserWorkspaceProvider>
      <UserRegistrationButton />
      <UserPage />
    </UserWorkspaceProvider>,
  );
}
function detail(onClose = vi.fn()) {
  return render(
    <UserDialog
      userId="test"
      options={options}
      onClose={onClose}
      onSaved={vi.fn()}
    />,
  );
}
it("제목줄 등록 버튼이 등록 창을 열고 소문자 아이디를 그대로 저장한다", async () => {
  page();
  await screen.findByTestId("user-row-test");
  fireEvent.click(screen.getByTestId("user-register-open"));
  const dialog = await screen.findByTestId("user-dialog");
  fireEvent.change(within(dialog).getByTestId("user-id-input"), {
    target: { value: "test" },
  });
  fireEvent.change(within(dialog).getByTestId("user-name-input"), {
    target: { value: "시험 사용자" },
  });
  fireEvent.change(within(dialog).getByTestId("user-email-input"), {
    target: { value: "test@example.invalid" },
  });
  fireEvent.click(
    within(dialog).getByRole("checkbox", { name: "프로젝트 관리자" }),
  );
  fireEvent.click(
    within(dialog).getByRole("checkbox", { name: "합성 프로젝트" }),
  );
  fireEvent.click(within(dialog).getByTestId("user-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(writes()[0][1].body)).toEqual({
    userId: "test",
    name: "시험 사용자",
    email: "test@example.invalid",
    roleCds: ["PM"],
    headquarters: false,
    projectIds: ["project-a"],
  });
});
it("등록 오류는 첫 필드로 이동하고 잘못된 입력은 전송하지 않는다", async () => {
  render(
    <UserDialog
      userId={null}
      options={options}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByTestId("user-form-save"));
  expect(screen.getByTestId("user-id-input")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(screen.getByTestId("user-id-input")).toHaveFocus();
  expect(writes()).toHaveLength(0);
});
it("상세 신원은 읽기 전용이고 LOCK에서만 해제를 표시한다", async () => {
  account.userStatCd = "LOCK";
  detail();
  await screen.findByTestId("user-unlock");
  expect(screen.getByTestId("user-id-input")).toHaveAttribute("readonly");
  expect(screen.getByTestId("user-name-input")).toHaveAttribute("readonly");
  expect(screen.getByTestId("user-email-input")).toHaveAttribute("readonly");
  expect(
    screen.getByText("비밀번호 5회 실패로 잠긴 계정입니다."),
  ).toBeVisible();
  expect(screen.queryByTestId("user-resume")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("user-unlock"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(writes()[0][1].body)).toEqual({ modDttm: token });
});
it("STOP 재개와 초기화는 원본 마이크로초 버전을 보내며 평문은 표시하지 않는다", async () => {
  account.userStatCd = "STOP";
  detail();
  fireEvent.click(await screen.findByTestId("user-resume"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  await waitFor(() =>
    expect(screen.getByTestId("user-password-reset")).not.toBeDisabled(),
  );
  fireEvent.click(screen.getByTestId("user-password-reset"));
  await waitFor(() => expect(writes()).toHaveLength(2));
  expect(writes().map((call) => JSON.parse(call[1].body))).toEqual([
    { modDttm: token },
    { modDttm: token },
  ]);
  expect(screen.queryByTestId("user-unlock")).not.toBeInTheDocument();
  expect(document.querySelector('input[type="password"]')).toBeNull();
});
it("역할 제거 영향 확인 취소는 PUT 없이 역할·프로젝트 baseline을 복원한다", async () => {
  detail();
  fireEvent.click(await screen.findByRole("checkbox", { name: "경영진" }));
  fireEvent.click(screen.getByRole("checkbox", { name: "프로젝트 관리자" }));
  fireEvent.click(screen.getByTestId("user-form-save"));
  await screen.findByText("담당 프로젝트를 해제할까요?");
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(
    screen.getByRole("checkbox", { name: "프로젝트 관리자" }),
  ).toBeChecked();
  expect(screen.getByRole("checkbox", { name: "합성 프로젝트" })).toBeChecked();
  expect(screen.getByRole("checkbox", { name: "경영진" })).not.toBeChecked();
  expect(writes()).toHaveLength(0);
});
it("영향 확인 계속은 확인 프로젝트와 수정 가능 필드만 전송한다", async () => {
  detail();
  fireEvent.click(await screen.findByRole("checkbox", { name: "경영진" }));
  fireEvent.click(screen.getByRole("checkbox", { name: "프로젝트 관리자" }));
  fireEvent.click(screen.getByTestId("user-form-save"));
  fireEvent.click(await screen.findByRole("button", { name: "계속" }));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(writes()[0][1].body)).toEqual({
    roleCds: ["EXEC"],
    headquarters: false,
    projectIds: [],
    modDttm: token,
    confirmedProjectIds: ["project-a"],
  });
});
it("접근 기록 탭을 왕복해도 편집 입력과 dirty 표시는 유지된다", async () => {
  const dirty = vi.fn();
  render(
    <ScreenDirtyContext.Provider value={dirty}>
      <UserDialog
        userId="test"
        options={options}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </ScreenDirtyContext.Provider>,
  );
  fireEvent.click(await screen.findByRole("checkbox", { name: "경영진" }));
  fireEvent.click(screen.getByTestId("user-logs-tab"));
  await screen.findByText("접근 기록이 없습니다.");
  fireEvent.click(screen.getByTestId("user-account-tab"));
  expect(screen.getByRole("checkbox", { name: "경영진" })).toBeChecked();
  expect(dirty).toHaveBeenCalledWith("UI-23", true);
});
it("다이얼로그 Ctrl+S는 저장만 수행하고 배경 조회를 실행하지 않는다", async () => {
  detail();
  const input = await screen.findByTestId("user-id-input");
  input.focus();
  fireEvent.keyDown(input, { key: "s", ctrlKey: true });
  await screen.findByText("담당 프로젝트를 해제할까요?");
  expect(
    request.mock.calls.filter((call) => call[0].includes("users?")),
  ).toHaveLength(0);
  expect(writes()).toHaveLength(0);
});
it("일괄 중지 취소는 PUT 없이 유지하고 계속은 부분 실패 이유를 표시한다", async () => {
  page();
  fireEvent.click(await screen.findByTestId("user-select-test"));
  fireEvent.click(screen.getByTestId("user-stop-open"));
  const dialog = await screen.findByTestId("user-stop-confirm");
  expect(dialog).toHaveTextContent("담당 프로젝트 1건");
  fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("user-select-test")).toBeChecked();
  fireEvent.click(screen.getByTestId("user-stop-open"));
  fireEvent.click(
    within(await screen.findByTestId("user-stop-confirm")).getByRole("button", {
      name: "계속",
    }),
  );
  await screen.findByText("admin: 마지막 관리자");
  expect(JSON.parse(writes()[0][1].body)).toEqual({
    users: [{ userId: "test", modDttm: token }],
    confirmedProjectIds: ["project-a"],
  });
});
it("빈 목록과 실패 목록은 구분하며 재시도를 제공한다", async () => {
  request.mockImplementation(async (url: string) =>
    url.includes("user-options")
      ? response(options)
      : response({ items: [], page: 1, pageSize: 20, total: 0 }),
  );
  const view = page();
  await screen.findByText("조회된 사용자가 없습니다.");
  view.unmount();
  request.mockImplementation(async (url: string) => {
    if (url.includes("user-options")) return response(options);
    throw new Error("목록 실패");
  });
  page();
  await screen.findByText("목록 실패");
  expect(screen.getByTestId("user-list-retry")).toBeVisible();
});
it("서버 필드 오류는 해당 입력에 표시하고 계정 조회 실패는 재시도한다", async () => {
  request.mockImplementation(async () => {
    throw new Error("상세 실패");
  });
  detail();
  await screen.findByText("상세 실패");
  expect(screen.getByTestId("user-detail-retry")).toBeVisible();
});

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
import type { CommonUserOptionsResult } from "../commonops/userOptions";
import { formatDateTime, type UserAccount } from "./users";

const options: CommonUserOptionsResult = {
  roles: [
    {
      roleCd: "PM",
      roleName: "일반 사용자(PM)",
      assignedProjectScope: true,
      assignedPartScope: false,
    },
    {
      roleCd: "SALES_LDR",
      roleName: "영업 파트장",
      assignedProjectScope: false,
      assignedPartScope: true,
    },
    {
      roleCd: "EXEC",
      roleName: "경영진",
      assignedProjectScope: false,
      assignedPartScope: false,
    },
  ],
  projects: [
    {
      projectId: "project-a",
      projectName: "합성 프로젝트",
      pmUserId: "oldpm",
      pmName: "김명희",
    },
  ],
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
  headers: new Headers(),
  json: async () => ({ success: true, data }),
});
const writes = () =>
  request.mock.calls.filter(
    (call: unknown[]) =>
      ["POST", "PUT"].includes(String((call[1] as RequestInit | undefined)?.method)),
  );
const gets = (part: string) =>
  request.mock.calls
    .map((call: unknown[]) => String(call[0]))
    .filter((url: string) => url.includes(part));
const listItem = (value: UserAccount) => ({
  userId: value.userId,
  name: value.name,
  roleCds: value.roleCds,
  roleNames: value.roleNames,
  assignedParts: value.assignedParts,
  userStatCd: value.userStatCd,
  lastAccessAt: value.lastAccessAt,
  modDttm: value.modDttm,
});
beforeEach(() => {
  account = {
    userId: "test",
    name: "시험 사용자",
    email: "test@example.invalid",
    roleCds: ["PM"],
    headquarters: false,
    projectIds: ["project-a"],
    userStatCd: "LOCK",
    modDttm: token,
    roleNames: ["일반 사용자(PM)"],
    assignedParts: [],
    projects: [{ projectId: "project-a", projectName: "합성 프로젝트" }],
    leaderParts: [],
    temporaryPassword: true,
    lastAccessAt: "2026-10-12T00:02:00Z",
  };
  request = vi.fn(async (url: string, init?: RequestInit) => {
    if (init?.method === "PUT" || init?.method === "POST") {
      if (url.endsWith("/stop"))
        return response({
          processedUserIds: ["test"],
          unprocessedCount: 0,
          unprocessed: [],
        });
      if (url.endsWith("/unlock"))
        return response({
          userId: "test",
          userStatCd: "USE",
          pwdErrCnt: 0,
          modDttm: token,
        });
      return response({ account, transferredProjects: [] });
    }
    if (url.includes("user-options")) return response(options);
    if (url.includes("user-preferences"))
      return response({
        prefTypeCd: "COLS",
        prefKey: "UI-23",
        found: false,
        prefVal: null,
        modDttm: null,
      });
    if (url.includes("change-impact"))
      return response({ projectCount: 0, projects: [], blockedUsers: [] });
    if (url.includes("access-logs"))
      return response({ items: [], page: 1, pageSize: 20, total: 0 });
    if (url.includes("change-history"))
      return response([
        {
          changedAt: token,
          item: "이름",
          before: "옛 이름",
          after: "시험 사용자",
          changedBy: "admin",
        },
      ]);
    if (url.includes("users?"))
      return response({
        items: [listItem(account)],
        page: 1,
        pageSize: 50,
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

it("시안처럼 제목줄에 [사용 중지 포함]과 [등록]을 두고, 목록은 공통 목록 틀로 그린다", async () => {
  page();
  expect(screen.getByTestId("user-include-stopped")).not.toBeChecked();
  expect(screen.getByTestId("user-register-open")).toHaveTextContent("등록");
  const row = await screen.findByTestId("list-row-test");
  expect(within(row).getByText("일반 사용자(PM)")).toBeInTheDocument();
  expect(within(row).getByText("잠김")).toBeInTheDocument();
  expect(within(row).getByText("2026-10-12 09:02")).toBeInTheDocument();
  expect(screen.getByTestId("list-count")).toHaveTextContent("총 1건");
  expect(screen.getByTestId("list-columns")).toBeInTheDocument();
  expect(screen.getByTestId("list-density")).toHaveTextContent("좁게 보기");
  expect(screen.getByTestId("list-download")).toBeInTheDocument();
  expect(screen.getByTestId("list-range")).toHaveTextContent("1-1 / 1건");
  expect(screen.getByPlaceholderText("이름 또는 아이디")).toBeInTheDocument();
  expect(gets("users?")[0]).toContain("includeStopped=false");
});

it("사용 중지 포함·정렬·조건 초기화는 서버 조회 조건으로 보낸다", async () => {
  page();
  await screen.findByTestId("list-row-test");
  fireEvent.click(screen.getByTestId("user-include-stopped"));
  await waitFor(() =>
    expect(gets("users?").at(-1)).toContain("includeStopped=true"),
  );
  fireEvent.click(screen.getByTestId("list-head-name"));
  await waitFor(() => expect(gets("users?").at(-1)).toContain("sort=name"));
  expect(gets("users?").at(-1)).toContain("dir=asc");
  fireEvent.click(screen.getByTestId("list-head-name"));
  await waitFor(() => expect(gets("users?").at(-1)).toContain("dir=desc"));
  fireEvent.change(screen.getByTestId("user-keyword-filter"), {
    target: { value: " 시험 " },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() =>
    expect(gets("users?").at(-1)).toContain(
      `keyword=${encodeURIComponent("시험")}`,
    ),
  );
  fireEvent.click(screen.getByTestId("list-reset"));
  await waitFor(() => expect(gets("users?").at(-1)).not.toContain("keyword"));
  expect(screen.getByTestId("user-keyword-filter")).toHaveValue("");
});

it("행을 누르면 오른쪽 상세 패널에 계정 정보·접근 기록·변경 이력(n) 탭을 보인다", async () => {
  page();
  fireEvent.click(await screen.findByTestId("list-row-test"));
  const panel = await screen.findByTestId("user-detail");
  expect(await within(panel).findByText("test@example.invalid")).toBeInTheDocument();
  expect(within(panel).getByText("임시 비밀번호")).toBeInTheDocument();
  expect(within(panel).getByText("비밀번호를 5번 틀려 잠겼습니다.")).toBeInTheDocument();
  expect(within(panel).getByText("이 프로젝트들의 PM으로 표시됩니다.")).toBeInTheDocument();
  expect(within(panel).getByTestId("user-detail-tab-history")).toHaveTextContent(
    "변경 이력 (1)",
  );
  fireEvent.click(within(panel).getByTestId("user-detail-tab-history"));
  expect(within(panel).getByText("옛 이름")).toBeInTheDocument();
  fireEvent.click(within(panel).getByTestId("user-detail-tab-logs"));
  expect(
    await within(panel).findByText("아직 접근 기록이 없습니다."),
  ).toBeInTheDocument();
  fireEvent.click(within(panel).getByTestId("user-detail-tab-account"));
  fireEvent.click(within(panel).getByTestId("user-unlock"));
  await waitFor(() =>
    expect(writes().map((call) => String(call[0]))).toContain(
      "/api/common/users/test/unlock",
    ),
  );
  expect(await screen.findByTestId("user-toast")).toHaveTextContent(
    "잠금을 풀었습니다. 틀린 횟수를 0으로 되돌렸습니다.",
  );
});

it("[등록]은 시안 「사용자 등록」 창을 열고, 빈 칸·형식 오류는 칸마다 보여 준다", async () => {
  page();
  fireEvent.click(screen.getByTestId("user-register-open"));
  const dialog = await screen.findByTestId("user-dialog");
  expect(within(dialog).getByText("사용자 등록")).toBeInTheDocument();
  expect(
    within(dialog).getByText(
      "저장하면 이 주소로 임시 비밀번호를 보냅니다. 첫 로그인 때 바꿔야 합니다.",
    ),
  ).toBeInTheDocument();
  expect(within(dialog).getByTestId("user-headquarters-select")).toBeInTheDocument();
  expect(within(dialog).getByTestId("user-project-list")).toBeInTheDocument();
  expect(within(dialog).getByText("(지금 PM: 김명희)")).toBeInTheDocument();
  fireEvent.click(within(dialog).getByTestId("user-save"));
  expect(await within(dialog).findByText("아이디를 입력해 주세요.")).toBeInTheDocument();
  expect(within(dialog).getByText("이름을 입력해 주세요.")).toBeInTheDocument();
  expect(within(dialog).getByText("역할을 1개 이상 골라 주세요.")).toBeInTheDocument();
  expect(within(dialog).getByTestId("user-userId-input")).toHaveFocus();
  fireEvent.change(within(dialog).getByTestId("user-userId-input"), {
    target: { value: "AB" },
  });
  fireEvent.click(within(dialog).getByTestId("user-save"));
  expect(
    await within(dialog).findByText("아이디는 영문 소문자·숫자 4~20자로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("등록이 끝나면 시안 문구로 알리고 새 줄을 강조한다", async () => {
  account = { ...account, userId: "newone", name: "새 사람", email: "new@example.invalid" };
  page();
  fireEvent.click(screen.getByTestId("user-register-open"));
  const dialog = await screen.findByTestId("user-dialog");
  fireEvent.change(within(dialog).getByTestId("user-userId-input"), {
    target: { value: "newone" },
  });
  fireEvent.change(within(dialog).getByTestId("user-name-input"), {
    target: { value: "새 사람" },
  });
  fireEvent.change(within(dialog).getByTestId("user-email-input"), {
    target: { value: "new@example.invalid" },
  });
  fireEvent.click(within(dialog).getByTestId("user-role-input-2"));
  fireEvent.click(within(dialog).getByTestId("user-save"));
  expect(await screen.findByTestId("user-toast")).toHaveTextContent(
    "새 사람님을 등록했습니다. new@example.invalid로 임시 비밀번호를 보냈습니다.",
  );
  const body = JSON.parse(String((writes()[0][1] as RequestInit).body));
  expect(body).toMatchObject({ userId: "newone", roleCds: ["EXEC"], headquarters: false });
});

it("[수정]은 「사용자 수정」 창에서 아이디를 잠그고 이름·이메일도 함께 보낸다", async () => {
  page();
  fireEvent.click(await screen.findByTestId("list-row-test"));
  const panel = await screen.findByTestId("user-detail");
  fireEvent.click(await within(panel).findByTestId("user-edit"));
  const dialog = await screen.findByTestId("user-dialog");
  expect(within(dialog).getByText("사용자 수정")).toBeInTheDocument();
  expect(within(dialog).getByTestId("user-userId-input")).toHaveAttribute("readonly");
  expect(within(dialog).getByText("아이디는 바꿀 수 없습니다.")).toBeInTheDocument();
  expect(within(dialog).queryByText("(지금 PM: 김명희)")).toBeInTheDocument();
  fireEvent.change(within(dialog).getByTestId("user-name-input"), {
    target: { value: "바꾼 이름" },
  });
  fireEvent.click(within(dialog).getByTestId("user-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const [url, init] = writes()[0];
  expect(url).toBe("/api/common/users/test");
  expect(JSON.parse(String((init as RequestInit).body))).toMatchObject({
    name: "바꾼 이름",
    email: "test@example.invalid",
    modDttm: token,
  });
  expect(await screen.findByTestId("user-toast")).toHaveTextContent(
    "사용자 정보를 저장했습니다. 변경 이력에 남겼습니다.",
  );
});

it("체크하면 「N건 선택」과 일괄 [사용 중지]가 나오고, 확인 창은 시안 문구를 쓴다", async () => {
  page();
  await screen.findByTestId("list-row-test");
  fireEvent.click(screen.getByTestId("list-select-test"));
  expect(screen.getByTestId("list-count")).toHaveTextContent("1건 선택");
  fireEvent.click(screen.getByTestId("list-bulk-stop"));
  const confirm = await screen.findByTestId("user-stop-confirm");
  expect(confirm).toHaveTextContent("시험 사용자님의 사용을 중지할까요?");
  expect(confirm).toHaveTextContent("삭제가 아니며 기록은 그대로 남습니다.");
  fireEvent.click(within(confirm).getByTestId("confirmation-proceed"));
  expect(await screen.findByTestId("user-toast")).toHaveTextContent(
    "1건의 사용을 중지했습니다.",
  );
});

it("서버가 막은 계정만 고르면 확인 창 없이 이유를 알린다", async () => {
  const base = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    url.includes("change-impact")
      ? response({
          projectCount: 0,
          projects: [],
          blockedUsers: [
            { userId: "test", reason: "영업1 파트장입니다. 영업 파트 탭에서 먼저 파트장을 바꾸세요." },
          ],
        })
      : base(url, init),
  );
  page();
  await screen.findByTestId("list-row-test");
  fireEvent.click(screen.getByTestId("list-select-test"));
  fireEvent.click(screen.getByTestId("list-bulk-stop"));
  expect(await screen.findByTestId("user-toast")).toHaveTextContent(
    "영업1 파트장입니다.",
  );
  expect(screen.queryByTestId("user-stop-confirm")).not.toBeInTheDocument();
});

it("시각은 한국 시간으로 보여 준다", () => {
  expect(formatDateTime("2026-10-12T00:02:00Z")).toBe("2026-10-12 09:02");
  expect(formatDateTime(null)).toBe("–");
});

it("수정 창의 미저장 입력은 닫기 전에 확인한다", async () => {
  render(
    <UserDialog
      account={account}
      options={options}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  const dialog = screen.getByTestId("user-dialog");
  fireEvent.change(within(dialog).getByTestId("user-name-input"), {
    target: { value: "다른 이름" },
  });
  fireEvent.click(within(dialog).getByTestId("user-cancel"));
  expect(within(dialog).getByText("나가시겠습니까?")).toBeInTheDocument();
});

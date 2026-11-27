import { afterEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  AccountLoginPage,
  AccountPasswordPage,
  AccountPasswordDialog,
  AccountMyInfo,
  AccountLogout,
  AccountIdentity,
} from "./AccountAccessPages";
import {
  AccountApiError,
  createAccountAccessApi,
  firstAccountScreen,
  NO_ACCOUNT_MENUS,
  NO_MOBILE_MENUS,
  type AccountPermission,
} from "./accountAccessApi";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
function api() {
  return {
    login: vi.fn(),
    logout: vi
      .fn()
      .mockResolvedValue({ success: true, data: undefined, meta: {} }),
    find: vi.fn(),
    myInfo: vi.fn(),
    change: vi.fn(),
  };
}
function fillLogin() {
  fireEvent.change(screen.getByLabelText("아이디"), {
    target: { value: "test-user" },
  });
  fireEvent.change(screen.getByLabelText("비밀번호", { exact: true }), {
    target: { value: "test-value" },
  });
}
describe("UI-22", () => {
  it("starts with empty credentials and validates without calling the API", () => {
    const client = api();
    render(<AccountLoginPage api={client} onCompleted={vi.fn()} />);
    expect(screen.getByLabelText("아이디")).toHaveValue("");
    expect(screen.getByLabelText("비밀번호", { exact: true })).toHaveAttribute(
      "autocomplete",
      "current-password",
    );
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    expect(screen.getByText("비밀번호를 입력해 주세요.")).toBeInTheDocument();
    expect(client.login).not.toHaveBeenCalled();
  });
  it("forwards successful MFA or forced-password stage metadata to the session owner", async () => {
    const client = api();
    const completed = vi.fn();
    const response = {
      success: true,
      data: { nextScreenId: "UI-40", userName: "홍길동" },
      meta: { modifiedAt: "2026-01-01T00:00:00Z" },
    };
    client.login.mockResolvedValue(response);
    render(<AccountLoginPage api={client} onCompleted={completed} />);
    fillLogin();
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    await waitFor(() =>
      expect(completed).toHaveBeenCalledWith(response, "test-value"),
    );
  });
  it.each([
    "아이디 또는 비밀번호가 맞지 않습니다.",
    "비밀번호를 5번 틀려 잠긴 계정입니다. 관리자에게 잠금 해제를 요청하세요.",
    "사용 중지된 계정입니다.",
  ])("shows the exact server error: %s", async (text) => {
    const client = api();
    client.login.mockRejectedValue(new AccountApiError(401, text));
    render(<AccountLoginPage api={client} onCompleted={vi.fn()} />);
    fillLogin();
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(text);
  });
  it("shows the mockup card: brand, server error under the password and one guidance line", async () => {
    const client = api();
    client.login.mockRejectedValue(
      new AccountApiError(401, "아이디 또는 비밀번호가 맞지 않습니다.", [
        { field: "password", message: "아이디 또는 비밀번호가 맞지 않습니다." },
      ]),
    );
    render(<AccountLoginPage api={client} onCompleted={vi.fn()} />);
    expect(screen.getByTestId("account-ui-22")).toHaveTextContent(
      "SI프로젝트·원가 관리로그인",
    );
    expect(
      screen.getByText("비밀번호를 5번 틀리면 계정이 잠깁니다.", {
        exact: false,
      }),
    ).toContainElement(screen.getByRole("button", { name: "비밀번호 찾기" }));
    fillLogin();
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    const alert = await screen.findByRole("alert");
    expect(screen.getAllByRole("alert")).toHaveLength(1);
    // 비밀번호 칸 바로 아래(같은 칸 묶음)
    expect(
      screen.getByLabelText("비밀번호", { exact: true }).parentElement,
    ).toContainElement(alert);
  });
  it("shows a restart message from the MFA step under the password", () => {
    render(
      <AccountLoginPage
        api={api()}
        onCompleted={vi.fn()}
        initialError="인증번호를 5번 틀렸습니다. 처음부터 다시 로그인해 주세요."
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "인증번호를 5번 틀렸습니다. 처음부터 다시 로그인해 주세요.",
    );
  });
  it("find dialog follows the mockup and closes with a toast after sending", async () => {
    const client = api();
    const text = "입력한 정보가 맞으면 이메일로 임시 비밀번호를 보냈습니다.";
    client.find.mockResolvedValue({ success: true, data: { message: text }, meta: {} });
    render(<AccountLoginPage api={client} onCompleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 찾기" }));
    const dialog = screen.getByRole("dialog", { name: "비밀번호 찾기" });
    expect(dialog).toHaveTextContent(
      "아이디와 이메일이 맞으면 그 이메일로 임시 비밀번호를 보냅니다. 로그인한 뒤 바로 바꿔야 합니다.",
    );
    expect(screen.getByRole("button", { name: "취소" })).toBeInTheDocument();
    fireEvent.change(screen.getByTestId("account-find-user-id"), {
      target: { value: "test-user" },
    });
    fireEvent.change(screen.getByTestId("account-find-email"), {
      target: { value: "test@example.invalid" },
    });
    fireEvent.click(screen.getByRole("button", { name: "임시 비밀번호 받기" }));
    expect(await screen.findByTestId("account-find-message")).toHaveTextContent(text);
    expect(
      screen.queryByRole("dialog", { name: "비밀번호 찾기" }),
    ).not.toBeInTheDocument();
  });
  it("opens route-less find dialog and displays non-identifying result", async () => {
    const client = api();
    const text = "입력한 정보가 맞으면 이메일로 임시 비밀번호를 보냈습니다.";
    client.find.mockResolvedValue({
      success: true,
      data: { message: text },
      meta: {},
    });
    render(<AccountLoginPage api={client} onCompleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 찾기" }));
    fireEvent.change(screen.getByTestId("account-find-user-id"), {
      target: { value: "test-user" },
    });
    fireEvent.change(screen.getByLabelText("등록 이메일"), {
      target: { value: "test@example.invalid" },
    });
    fireEvent.click(screen.getByRole("button", { name: "임시 비밀번호 받기" }));
    expect(await screen.findByRole("status")).toHaveTextContent(text);
  });
  it("does not submit malformed find email", () => {
    const client = api();
    render(<AccountLoginPage api={client} onCompleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 찾기" }));
    fireEvent.click(screen.getByRole("button", { name: "임시 비밀번호 받기" }));
    expect(screen.getByText("아이디를 넣어 주세요.")).toBeInTheDocument();
    fireEvent.change(screen.getByTestId("account-find-user-id"), {
      target: { value: "test-user" },
    });
    fireEvent.click(screen.getByRole("button", { name: "임시 비밀번호 받기" }));
    expect(
      screen.getByText("이메일 형식이 맞지 않습니다."),
    ).toBeInTheDocument();
    expect(client.find).not.toHaveBeenCalled();
  });
});
function fillPassword(next = "Changed8!", confirmation = next) {
  fireEvent.change(screen.getByLabelText("현재 비밀번호"), {
    target: { value: "Current8!" },
  });
  fireEvent.change(screen.getByLabelText("새 비밀번호", { exact: true }), {
    target: { value: next },
  });
  fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), {
    target: { value: confirmation },
  });
}
describe("UI-40", () => {
  const meta = {
    modifiedAt: "2026-01-01T00:00:00Z",
    passwordChangedAt: "2025-01-01T00:00:00Z",
    serverRespondedAt: "2026-01-01T00:00:00Z",
    passwordChangeDays: 90,
    passwordChangeRequired: true,
  };
  it("uses supplied login metadata without fetching protected my-info", () => {
    const client = api();
    render(
      <AccountPasswordPage
        api={client}
        meta={meta}
        onCompleted={vi.fn()}
        onBack={vi.fn()}
      />,
    );
    expect(
      screen.getByText(
        "임시 비밀번호로 로그인했습니다. 새 비밀번호로 바꿔야 시작할 수 있습니다.",
      ),
    ).toBeInTheDocument();
    // 90일 안내는 사용자 메뉴의 비밀번호 변경 창에만 둔다(명세 ④-3).
    expect(screen.queryByText(/90일/)).not.toBeInTheDocument();
    expect(
      screen.getByText("8자 이상, 영문·숫자·특수문자를 모두 넣습니다."),
    ).toBeInTheDocument();
    expect(client.myInfo).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "변경하고 시작" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "처음으로" })).toBeInTheDocument();
  });
  it("names the user when the session owner knows it", () => {
    render(
      <AccountPasswordPage
        api={api()}
        meta={meta}
        name="홍길동"
        currentPassword="Current8!"
        onCompleted={vi.fn()}
        onBack={vi.fn()}
      />,
    );
    expect(screen.getByText(/^홍길동님은 임시 비밀번호로/)).toBeInTheDocument();
  });
  it.each([
    ["Abcd1!x", "Abcd1!x", "8자 이상, 영문·숫자·특수문자를 모두 넣어 주세요."],
    ["", "", "새 비밀번호를 입력해 주세요."],
    ["Current8!", "Current8!", "임시 비밀번호와 다르게 정해 주세요."],
    ["Changed8!", "Different8!", "새 비밀번호가 서로 다릅니다."],
  ])(
    "rejects invalid, same or mismatching input",
    (next, confirmation, text) => {
      const client = api();
      render(
        <AccountPasswordPage
          api={client}
          meta={meta}
          onCompleted={vi.fn()}
          onBack={vi.fn()}
        />,
      );
      fillPassword(next, confirmation);
      fireEvent.click(screen.getByRole("button", { name: "변경하고 시작" }));
      expect(screen.getByText(text)).toBeInTheDocument();
      expect(client.change).not.toHaveBeenCalled();
    },
  );
  it("preserves all inputs after stale 409", async () => {
    const client = api();
    client.change.mockRejectedValue(
      new AccountApiError(409, "다시 조회해 주세요."),
    );
    render(
      <AccountPasswordPage
        api={client}
        meta={meta}
        onCompleted={vi.fn()}
        onBack={vi.fn()}
      />,
    );
    fillPassword();
    fireEvent.click(screen.getByRole("button", { name: "변경하고 시작" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "다시 조회해 주세요.",
    );
    expect(screen.getByLabelText("새 비밀번호", { exact: true })).toHaveValue(
      "Changed8!",
    );
    expect(screen.getByLabelText("새 비밀번호 확인")).toHaveValue("Changed8!");
  });
  it("submits only the contract fields and completes", async () => {
    const client = api();
    const completed = vi.fn();
    client.change.mockResolvedValue({
      success: true,
      data: {},
      meta: { passwordChangeRequired: false },
    });
    render(
      <AccountPasswordPage
        api={client}
        meta={meta}
        onCompleted={completed}
        onBack={vi.fn()}
      />,
    );
    fillPassword();
    fireEvent.click(screen.getByRole("button", { name: "변경하고 시작" }));
    await waitFor(() =>
      expect(completed).toHaveBeenCalledWith({ passwordChangeRequired: false }),
    );
    expect(client.change).toHaveBeenCalledWith(
      "Current8!",
      "Changed8!",
      meta.modifiedAt,
    );
  });
  it("reuses the transient verified password without asking again in forced-change mode", async () => {
    const client = api();
    const done = vi.fn();
    client.change.mockResolvedValue({
      success: true,
      data: {},
      meta: { passwordChangeRequired: false },
    });
    render(
      <AccountPasswordPage
        api={client}
        meta={meta}
        currentPassword="Current8!"
        onCompleted={done}
        onBack={vi.fn()}
      />,
    );
    expect(screen.queryByLabelText("현재 비밀번호")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("새 비밀번호", { exact: true }), {
      target: { value: "Changed8!" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), {
      target: { value: "Changed8!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "변경하고 시작" }));
    await waitFor(() => expect(done).toHaveBeenCalledOnce());
    expect(client.change).toHaveBeenCalledWith(
      "Current8!",
      "Changed8!",
      meta.modifiedAt,
    );
  });
  it("ordinary change dialog asks for the current password and shows days left", () => {
    render(
      <AccountPasswordDialog
        api={api()}
        meta={{
          ...meta,
          passwordChangeRequired: false,
          passwordChangedAt: "2025-12-02T00:00:00Z",
        }}
        onCompleted={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("dialog", { name: "비밀번호 변경" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("현재 비밀번호")).toBeInTheDocument();
    expect(screen.getByTestId("account-password-age")).toHaveTextContent(
      "마지막 변경 2025-12-02 · 60일 뒤 변경 안내",
    );
    expect(screen.getByRole("button", { name: "취소" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "변경" })).toBeInTheDocument();
  });
  it("ordinary change dialog recommends a change after 90 days and checks in mockup order", () => {
    const client = api();
    render(
      <AccountPasswordDialog
        api={client}
        meta={{ ...meta, passwordChangeRequired: false }}
        onCompleted={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByTestId("account-password-age")).toHaveTextContent(
      "마지막 변경 2025-01-01 · 90일이 지나 변경을 권합니다",
    );
    fireEvent.click(screen.getByRole("button", { name: "변경" }));
    expect(
      screen.getByText("현재 비밀번호를 입력해 주세요."),
    ).toBeInTheDocument();
    fillPassword("Current8!");
    fireEvent.click(screen.getByRole("button", { name: "변경" }));
    expect(
      screen.getByText("현재 비밀번호와 다르게 정해 주세요."),
    ).toBeInTheDocument();
    expect(client.change).not.toHaveBeenCalled();
  });
});
describe("UI-41 and UI-34", () => {
  it("renders all identity fields read-only, including no-role accounts", async () => {
    const client = api();
    client.myInfo.mockResolvedValue({
      data: { userId: "user-a", name: "이름", roles: [], parts: ["본사"] },
      meta: {},
    });
    render(<AccountMyInfo api={client} onClose={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent("불러오는 중…");
    expect(await screen.findByText("user-a")).toBeInTheDocument();
    expect(screen.getByText("본사")).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    // 시안: 빈 값은 「–」, 보기 전용 안내, [닫기]
    expect(screen.getByText("–")).toBeInTheDocument();
    expect(
      screen.getByText(/^보기만 할 수 있습니다\. 이름·역할은 관리자가 사용자 관리에서 바꿉니다\./),
    ).toBeInTheDocument();
    expect(screen.getByTestId("account-info-close")).toHaveTextContent("닫기");
  });
  it("shows server role names joined with commas", async () => {
    const client = api();
    client.myInfo.mockResolvedValue({
      data: {
        userId: "u",
        name: "이름",
        roles: ["FIN", "CUSTOM_ROLE"],
        roleNames: ["재무 담당", "합성 사용자 역할"],
        parts: [],
      },
      meta: {},
    });
    render(<AccountMyInfo api={client} onClose={vi.fn()} />);
    expect(
      await screen.findByText("재무 담당, 합성 사용자 역할"),
    ).toBeInTheDocument();
  });
  it("shows failed my-info request", async () => {
    const client = api();
    client.myInfo.mockRejectedValue(new Error("조회 실패"));
    render(<AccountMyInfo api={client} onClose={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  });
  it("dirty logout cancellation preserves input and sends no request", () => {
    const client = api();
    render(
      <>
        <input aria-label="미저장 입력" defaultValue="유지할 값" />
        <AccountLogout api={client} dirty onLoggedOut={vi.fn()} />
      </>,
    );
    fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));
    expect(
      screen.getByText("저장하지 않은 내용이 있습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("로그아웃하면 입력한 내용이 사라집니다."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.getByLabelText("미저장 입력")).toHaveValue("유지할 값");
    expect(client.logout).not.toHaveBeenCalled();
  });
  it("confirmed logout calls current-session operation once", async () => {
    const client = api();
    const done = vi.fn();
    render(<AccountLogout api={client} dirty onLoggedOut={done} />);
    fireEvent.click(screen.getByTestId("account-logout"));
    fireEvent.click(screen.getByTestId("account-logout-confirm"));
    await waitFor(() => expect(done).toHaveBeenCalledOnce());
    expect(client.logout).toHaveBeenCalledOnce();
  });
  it("header identity reacts to changed authenticated user", () => {
    const view = render(
      <AccountIdentity
        info={{
          userId: "a",
          name: "이름1",
          roles: ["FIN", "PM"],
          roleNames: ["재무 담당", "일반 사용자(PM)"],
          parts: [],
        }}
      />,
    );
    expect(screen.getByTestId("account-identity")).toHaveTextContent(
      "이름1 (재무 외 1)",
    );
    // 줄인 이름 표에 없는 역할은 서버 역할 이름, 그것도 없으면 역할 ID.
    view.rerender(
      <AccountIdentity
        info={{ userId: "c", name: "이름3", roles: ["CUSTOM", "OTHER"], roleNames: ["합성 역할"], parts: [] }}
      />,
    );
    expect(screen.getByTestId("account-identity")).toHaveTextContent(
      "이름3 (합성 역할 외 1)",
    );
    view.rerender(
      <AccountIdentity
        info={{ userId: "b", name: "이름2", roles: [], parts: [] }}
      />,
    );
    expect(screen.getByTestId("account-identity")).toHaveTextContent(
      "이름2 (역할 없음)",
    );
  });
  it("chooses permitted dashboard, first visible leaf, or exact empty messages", () => {
    const permission = (
      screenId: string,
      pcOnlyYn = "N",
    ): AccountPermission => ({
      menuId: screenId,
      screenId,
      pcOnlyYn,
      readYn: "Y",
      createYn: "N",
      updateYn: "N",
      deleteYn: "N",
      uploadYn: "N",
      downloadYn: "N",
      cnfmYn: "N",
    });
    expect(
      firstAccountScreen([permission("UI-01"), permission("UI-16")], false)
        .screenId,
    ).toBe("UI-16");
    expect(firstAccountScreen([permission("UI-01")], false).screenId).toBe(
      "UI-01",
    );
    expect(firstAccountScreen([], false).message).toBe(NO_ACCOUNT_MENUS);
    expect(firstAccountScreen([permission("UI-01", "Y")], true).message).toBe(
      NO_MOBILE_MENUS,
    );
  });
  it("API serializer never sends client confirmation", async () => {
    const request = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ success: true, data: {}, meta: {} })),
      );
    const client = createAccountAccessApi(request);
    await client.change("old", "new", "version");
    expect(JSON.parse(request.mock.calls[0][1].body)).toEqual({
      currentPassword: "old",
      newPassword: "new",
      modifiedAt: "version",
    });
  });
});

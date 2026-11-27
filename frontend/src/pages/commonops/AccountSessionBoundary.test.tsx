import { afterEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { AccountSessionBoundary } from "./AccountSessionBoundary";
import {
  type AccountAccessApi,
  type AccountPermission,
  type AccountResponse,
  type AccountInfo,
} from "./accountAccessApi";
const dependencies = vi.hoisted(() => ({
  api: undefined as AccountAccessApi | undefined,
}));
vi.mock("./accountAccessApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./accountAccessApi")>()),
  createAccountAccessApi: () => dependencies.api,
}));
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
const permission: AccountPermission = {
  menuId: "M004",
  screenId: "UI-06",
  readYn: "Y",
  pcOnlyYn: "N",
  createYn: "N",
  updateYn: "N",
  deleteYn: "N",
  uploadYn: "N",
  downloadYn: "N",
  cnfmYn: "N",
};
function setup(permissions: AccountPermission[] = [permission]) {
  const identity: AccountResponse<AccountInfo> = {
    success: true,
    meta: { permissions, modifiedAt: "2026-01-01T00:00:00Z" },
    data: { userId: "reader", name: "실제 사용자", roles: ["FIN"], roleNames: ["재무 담당"], parts: [] },
  };
  const api: AccountAccessApi = {
    myInfo: vi.fn().mockResolvedValue(identity),
    login: vi.fn(),
    logout: vi
      .fn()
      .mockResolvedValue({ success: true, data: undefined, meta: {} }),
    find: vi.fn(),
    change: vi.fn(),
  };
  dependencies.api = api;
  vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: false }));
  return { api, identity };
}
function Path() {
  return (
    <output data-testid="current-account-path">{useLocation().pathname}</output>
  );
}
function shell(path = "/screens/UI-06", onAllowedScreensChanged = vi.fn()) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Path />
      <AccountSessionBoundary onAllowedScreensChanged={onAllowedScreensChanged}>
        <input aria-label="업무 입력" defaultValue="미저장 내용" />
      </AccountSessionBoundary>
    </MemoryRouter>,
  );
}
describe("shared account session owner", () => {
  it("zero-role or zero-menu account sees exactly the no-menu message and logout, not business inputs", async () => {
    const { identity } = setup([]);
    identity.data.roles = [];
    shell("/screens/UI-34");
    expect(
      await screen.findByRole("heading", { name: "볼 수 있는 화면이 없습니다" }),
    ).toBeInTheDocument();
    expect(screen.getByText("관리자에게 권한을 요청해 주세요.")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "로그아웃" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "내 정보" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });
  it("mobile-only exclusion has its own message and does not reveal protected children", async () => {
    setup([{ ...permission, pcOnlyYn: "Y" }]);
    vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
    shell("/screens/UI-34");
    expect(
      await screen.findByText(
        "휴대폰에서 볼 수 있는 화면이 없습니다. PC에서 이용해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("업무 입력")).not.toBeInTheDocument();
  });
  it("direct unauthorized URL goes to the first readable leaf before any business input renders", async () => {
    // 서버는 볼 수 없는 메뉴도 조회 N 줄로 보낸다.
    setup([permission, { ...permission, menuId: "M027", screenId: "UI-23", readYn: "N" }]);
    shell("/screens/UI-23");
    expect(screen.queryByLabelText("업무 입력")).not.toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("current-account-path")).toHaveTextContent(
        "/screens/UI-06",
      ),
    );
    // 시안 go(): 첫 화면이 대시보드가 아니면 「대시보드로 갑니다」 없이 토스트.
    expect(await screen.findByTestId("account-forbidden-toast")).toHaveTextContent(
      "이 화면을 볼 권한이 없습니다.",
    );
    expect(screen.getByTestId("account-forbidden-toast")).not.toHaveTextContent("대시보드로 갑니다");
  });
  it("expiry unmounts dirty business inputs and returns to empty login", async () => {
    setup();
    shell();
    expect(await screen.findByLabelText("업무 입력")).toHaveValue(
      "미저장 내용",
    );
    fireEvent(window, new Event("account-session-expired"));
    expect(await screen.findByLabelText("아이디")).toHaveValue("");
    expect(screen.queryByLabelText("업무 입력")).not.toBeInTheDocument();
    expect(screen.getByTestId("account-login-message")).toHaveTextContent(
      "30분 동안 사용하지 않아 로그아웃되었습니다",
    );
  });
  it("fresh visit without a session shows the login card without the expiry toast", async () => {
    const { api } = setup();
    // 공통 fetch 틀처럼: 내 정보 401이면 만료 알림을 보낸 뒤 실패한다.
    vi.mocked(api.myInfo).mockImplementation(async () => {
      window.dispatchEvent(
        new CustomEvent("account-session-expired", {
          detail: "다시 로그인해 주세요.",
        }),
      );
      throw new Error("401");
    });
    shell("/screens/UI-06");
    expect(await screen.findByLabelText("아이디")).toHaveValue("");
    await waitFor(() =>
      expect(screen.getByTestId("current-account-path")).toHaveTextContent(
        "/screens/UI-22",
      ),
    );
    expect(screen.queryByTestId("account-login-message")).not.toBeInTheDocument();
    expect(
      screen.queryByText("30분 동안 사용하지 않아 로그아웃되었습니다"),
    ).not.toBeInTheDocument();
  });
  it("a stray 401 on the login screen before signing in shows no expiry toast", async () => {
    setup();
    shell("/screens/UI-22");
    await screen.findByLabelText("아이디");
    fireEvent(window, new Event("account-session-expired"));
    expect(screen.queryByTestId("account-login-message")).not.toBeInTheDocument();
  });
  it("permission refresh reports only retained tabs to the existing tab owner", async () => {
    const { api, identity } = setup();
    const changed = vi.fn();
    shell("/screens/UI-34", changed);
    await screen.findByLabelText("업무 입력");
    vi.mocked(api.myInfo).mockResolvedValue({
      ...identity,
      meta: { permissions: [] },
    });
    fireEvent(window, new Event("account-permissions-changed"));
    await waitFor(() => expect(changed).toHaveBeenCalledWith([]));
    expect(
      await screen.findByRole("heading", { name: "볼 수 있는 화면이 없습니다" }),
    ).toBeInTheDocument();
    expect(screen.getByText("관리자에게 권한을 요청해 주세요.")).toBeInTheDocument();
  });
  it("does not resurrect identity or retained tabs when a pending refresh finishes after expiry", async () => {
    const { api, identity } = setup();
    const changed = vi.fn();
    shell("/screens/UI-34", changed);
    await screen.findByLabelText("업무 입력");
    let deliver!: (value: AccountResponse<AccountInfo>) => void;
    vi.mocked(api.myInfo).mockReturnValueOnce(
      new Promise((resolve) => {
        deliver = resolve;
      }),
    );
    fireEvent(window, new Event("account-permissions-changed"));
    fireEvent(window, new Event("account-session-expired"));
    await screen.findByLabelText("아이디");
    await act(async () => {
      deliver(identity);
    });
    expect(screen.getByTestId("current-account-path")).toHaveTextContent(
      "/screens/UI-22",
    );
    expect(screen.queryByLabelText("업무 입력")).not.toBeInTheDocument();
    expect(changed).not.toHaveBeenCalled();
  });
  it("moves a temporary login directly to the two-new-password form", async () => {
    const { api } = setup();
    vi.mocked(api.login).mockResolvedValue({
      success: true,
      data: { nextScreenId: "UI-40", userName: "임시 사용자" },
      meta: {
        passwordChangeRequired: true,
        modifiedAt: "2026-01-01T00:00:00Z",
      },
    });
    shell("/screens/UI-22");
    fireEvent.change(screen.getByLabelText("아이디"), {
      target: { value: "reader" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호", { exact: true }), {
      target: { value: "Current8!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    await screen.findByRole("button", { name: "변경하고 시작" });
    // 시안 renderPwForce: 로그인 결과의 이름으로 「○○님은 임시 비밀번호로 로그인했습니다.」
    expect(screen.getByText(/^임시 사용자님은 임시 비밀번호로 로그인했습니다./)).toBeInTheDocument();
    expect(screen.queryByLabelText("현재 비밀번호")).not.toBeInTheDocument();
    expect(api.myInfo).not.toHaveBeenCalled();
    expect(screen.queryByLabelText("업무 입력")).not.toBeInTheDocument();
    // 바꾸면 시안 토스트 「비밀번호를 바꿨습니다. 이름(역할)으로 로그인했습니다.」
    vi.mocked(api.change).mockResolvedValue({
      success: true,
      data: { passwordChangedAt: "2026-01-02T00:00:00Z", modifiedAt: "2026-01-02T00:00:00Z" },
      meta: { passwordChangeRequired: false },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호", { exact: true }), {
      target: { value: "Changed8!" },
    });
    fireEvent.change(screen.getByLabelText("새 비밀번호 확인"), {
      target: { value: "Changed8!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "변경하고 시작" }));
    expect(await screen.findByTestId("account-login-message")).toHaveTextContent(
      "비밀번호를 바꿨습니다. 임시 사용자(재무)으로 로그인했습니다.",
    );
    expect(api.change).toHaveBeenCalledWith(
      "Current8!",
      "Changed8!",
      "2026-01-01T00:00:00Z",
    );
  });
  it("login success shows the mockup toast with the short role name", async () => {
    const { api } = setup();
    vi.mocked(api.login).mockResolvedValue({
      success: true,
      data: { nextScreenId: "UI-06", userName: "실제 사용자" },
      meta: {},
    });
    shell("/screens/UI-22");
    fireEvent.change(screen.getByLabelText("아이디"), {
      target: { value: "reader" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호", { exact: true }), {
      target: { value: "Current8!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    expect(await screen.findByTestId("account-login-message")).toHaveTextContent(
      "실제 사용자(재무)으로 로그인했습니다.",
    );
    expect(await screen.findByLabelText("업무 입력")).toBeInTheDocument();
    // 임시 도구 줄(이름·내 정보·비밀번호 변경·로그아웃)은 헤더 사용자 메뉴로 옮겨 없다.
    expect(screen.queryByTestId("account-tools")).not.toBeInTheDocument();
  });
  it("the legacy password route opens the change dialog and returns to the first screen", async () => {
    setup();
    shell("/screens/UI-40");
    expect(
      await screen.findByRole("dialog", { name: "비밀번호 변경" }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    await waitFor(() =>
      expect(screen.getByTestId("current-account-path")).toHaveTextContent(
        "/screens/UI-06",
      ),
    );
  });
});

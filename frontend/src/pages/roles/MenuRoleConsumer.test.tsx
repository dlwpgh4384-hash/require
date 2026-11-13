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
import { RolePage } from "./RolePage";
import { RoleTitleActions, RoleWorkspaceProvider } from "./RoleWorkspace";
import {
  roleActions,
  type Role,
  type RoleMenu,
  type RoleSettings,
  type RoleUpdate,
} from "./roles";

let request: ReturnType<typeof vi.fn>;
let settings: Map<string, RoleSettings>;
let roles: Role[];
const menu = (name: string): RoleMenu => ({
  menuId: "owned-new-menu",
  menuName: name,
  screenId: "UI-25",
  groupName: "합성 묶음",
  readYn: "N",
  createYn: "N",
  updateYn: "N",
  deleteYn: "N",
  uploadYn: "N",
  downloadYn: "N",
  cnfmYn: "N",
});
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const renderPage = () =>
  render(
    <RoleWorkspaceProvider>
      <RoleTitleActions />
      <RolePage />
    </RoleWorkspaceProvider>,
  );
const writes = () =>
  request.mock.calls.filter(
    (call: unknown[]) => (call[1] as RequestInit | undefined)?.method === "PUT",
  );

beforeEach(() => {
  roles = ["ADMIN", "FIN"].map((roleId) => ({
    roleId,
    roleName: roleId === "ADMIN" ? "시스템 관리자" : "재무 담당",
    dataScopeCd: "ALL",
    snstvViewYn: "Y",
    useYn: "Y",
    basicRole: true,
  }));
  settings = new Map(
    roles.map((role) => [
      role.roleId,
      {
        role: { ...role },
        menuAuths: [menu("새 메뉴")],
        dataScopes: [{ code: "ALL", name: "전체" }],
      },
    ]),
  );
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/roles") return success({ items: roles });
    const roleId = decodeURIComponent(path.split("/")[4]);
    if (path.endsWith("/change-history")) return success({ items: [] });
    const value = settings.get(roleId);
    if (!value) throw new Error(`예상하지 않은 시험 요청: ${path}`);
    if (init?.method === "PUT") {
      const input: RoleUpdate = JSON.parse(String(init.body));
      value.role = {
        ...value.role,
        dataScopeCd: input.dataScopeCd,
        snstvViewYn: input.snstvViewYn,
      };
      value.menuAuths = value.menuAuths.map((row) => ({
        ...row,
        ...input.menuAuths.find((next) => next.menuId === row.menuId),
      }));
    }
    return success(value);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("권한 N 행도 표시하고 UI-24 제목줄과 공통 입력 클래스를 유지한다", async () => {
  renderPage();
  const row = await screen.findByTestId("role-menu-owned-new-menu");
  expect(within(row).getByRole("rowheader")).toHaveTextContent("새 메뉴");
  for (const [, label] of roleActions) {
    expect(
      within(row).getByRole("checkbox", { name: `새 메뉴 ${label}` }),
    ).not.toBeChecked();
  }
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "역할 복사" })).toBeEnabled(),
  );
  expect(screen.getByRole("button", { name: "역할 추가" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "변경 이력 (0)" })).toBeEnabled();
  expect(screen.getByLabelText("데이터 범위")).toHaveClass("ls-in");
  expect(writes()).toHaveLength(0);
});

it("다른 역할을 읽은 뒤 돌아오면 새 메뉴 이름과 N 행을 다시 읽는다", async () => {
  renderPage();
  await screen.findByTestId("role-menu-owned-new-menu");
  settings.get("ADMIN")!.menuAuths = [menu("바뀐 메뉴")];
  fireEvent.click(screen.getByTestId("role-tab-fin"));
  await screen.findByRole("group", { name: "재무 담당 설정" });
  fireEvent.click(screen.getByTestId("role-tab-admin"));
  await screen.findByRole("checkbox", { name: "바뀐 메뉴 조회" });
  const row = screen.getByTestId("role-menu-owned-new-menu");
  expect(within(row).getAllByRole("checkbox")).toHaveLength(7);
  expect(
    within(row).getByRole("checkbox", { name: "바뀐 메뉴 조회" }),
  ).not.toBeChecked();
  expect(
    request.mock.calls.filter(
      (call: unknown[]) => call[0] === "/api/common/roles/ADMIN",
    ),
  ).toHaveLength(2);
  expect(writes()).toHaveLength(0);
});

it("새 메뉴를 편집해도 미저장 안내와 Ctrl+S 저장이 유지된다", async () => {
  renderPage();
  await screen.findByTestId("role-menu-owned-new-menu");
  fireEvent.click(screen.getByRole("checkbox", { name: "새 메뉴 조회" }));
  expect(screen.getByText(/저장하지 않은 변경이 있습니다/)).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(writes()).toHaveLength(1));
  const payload: RoleUpdate = JSON.parse(
    String((writes()[0][1] as RequestInit).body),
  );
  expect(payload.menuAuths).toEqual([
    {
      menuId: "owned-new-menu",
      readYn: "Y",
      createYn: "N",
      updateYn: "N",
      deleteYn: "N",
      uploadYn: "N",
      downloadYn: "N",
      cnfmYn: "N",
    },
  ]);
  await waitFor(() =>
    expect(
      screen.queryByText(/저장하지 않은 변경이 있습니다/),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByRole("checkbox", { name: "새 메뉴 조회" })).toBeChecked();
  expect(screen.getByRole("button", { name: "역할 복사" })).toBeEnabled();
});

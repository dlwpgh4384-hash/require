import { useState } from "react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter } from "react-router-dom";
import {
  NavigationRoot,
  type ShellMenuResponse,
} from "../../app/NavigationRoot";
import { requestMenuRefresh } from "../../app/menuRefresh";
import { RolePage } from "../roles/RolePage";
import { RoleWorkspaceProvider } from "../roles/RoleWorkspace";

let menus: ShellMenuResponse;
let mobile: boolean;
let listeners: Set<() => void>;
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  mobile = false;
  listeners = new Set();
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      get matches() {
        return mobile;
      },
      addEventListener: (_event: string, callback: () => void) =>
        listeners.add(callback),
      removeEventListener: (_event: string, callback: () => void) =>
        listeners.delete(callback),
    })),
  );
  menus = {
    success: true,
    meta: {
      screenAccess: [
        { scrnId: "UI-24", available: true, pcOnlyYn: "Y" },
        { scrnId: "UI-25", available: true, pcOnlyYn: "Y" },
      ],
    },
    data: [
      {
        menuId: "G007",
        upperMenuId: null,
        menuNm: "시스템",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "M028",
        upperMenuId: "G007",
        menuNm: "권한",
        scrnId: "UI-24",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "Y",
        iconNm: null,
      },
      {
        menuId: "M029",
        upperMenuId: "G007",
        menuNm: "메뉴",
        scrnId: "UI-25",
        menuLvl: 2,
        sortOrd: 2,
        pcOnlyYn: "Y",
        iconNm: null,
      },
    ],
  };
  const role = {
    roleId: "ADMIN",
    roleName: "시스템 관리자",
    dataScopeCd: "ALL",
    snstvViewYn: "Y",
    useYn: "Y",
    basicRole: true,
  };
  const success = (data: unknown) => ({
    ok: true,
    status: 200,
    json: async () => ({
      success: true,
      meta: {},
      data: structuredClone(data),
    }),
  });
  request = vi.fn(async (path: string) => {
    if (path === "/api/common/menus")
      return {
        ok: true,
        status: 200,
        json: async () => structuredClone(menus),
      };
    if (path === "/api/common/roles") return success({ items: [role] });
    if (path.endsWith("/change-history")) return success({ items: [] });
    if (path === "/api/common/roles/ADMIN")
      return success({
        role,
        dataScopes: [{ code: "ALL", name: "전체" }],
        menuAuths: [
          {
            menuId: "M031",
            menuName: "새 메뉴",
            screenId: "UI-25",
            groupName: "시스템",
            readYn: "N",
            createYn: "N",
            updateYn: "N",
            deleteYn: "N",
            uploadYn: "N",
            downloadYn: "N",
            cnfmYn: "N",
          },
        ],
      });
    throw new Error(`예상하지 않은 시험 요청: ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
function DraftScreen() {
  const [value, setValue] = useState("");
  return (
    <input
      aria-label="기존 탭 입력"
      value={value}
      onChange={(event) => setValue(event.target.value)}
    />
  );
}
function mount(path: string, role = false) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <RoleWorkspaceProvider>
        <NavigationRoot
          tabs
          renderScreen={(id) =>
            role && id === "UI-24" ? <RolePage /> : <DraftScreen />
          }
          extensions={(context) => ({
            actions: (
              <output data-testid="shell-availability">
                {`${context.available("UI-25")}:${context.menus.some((row) => row.scrnId === "UI-25")}`}
              </output>
            ),
          })}
        />
      </RoleWorkspaceProvider>
    </MemoryRouter>,
  );
}
it("메뉴 refresh는 기존 dirty UI-24 설정을 다시 읽거나 덮어쓰지 않고 Sidebar를 갱신", async () => {
  mount("/screens/UI-24", true);
  const checkbox = await screen.findByRole("checkbox", {
    name: "새 메뉴 조회",
  });
  fireEvent.click(checkbox);
  expect(checkbox).toBeChecked();
  expect(screen.getByText(/저장하지 않은 변경이 있습니다/)).toBeInTheDocument();
  menus.data.find((row) => row.scrnId === "UI-25")!.menuNm = "새 메뉴 이름";
  act(() => requestMenuRefresh());
  await screen.findByRole("link", { name: "새 메뉴 이름" });
  expect(screen.getByRole("checkbox", { name: "새 메뉴 조회" })).toBeChecked();
  expect(
    request.mock.calls.filter((call) => call[0] === "/api/common/roles/ADMIN"),
  ).toHaveLength(1);
  expect(
    request.mock.calls.filter((call) => call[0] === "/api/common/menus"),
  ).toHaveLength(2);
});
it("메뉴 refresh 동안 기존 탭 입력을 유지하고 FAV/RECENT가 받는 가시성과 주소 판정을 함께 바꾼다", async () => {
  mount("/screens/UI-24");
  const input = await screen.findByLabelText("기존 탭 입력");
  fireEvent.change(input, { target: { value: "미저장 값" } });
  expect(screen.getByTestId("shell-availability")).toHaveTextContent(
    "true:true",
  );
  menus.data = menus.data.filter((row) => row.scrnId !== "UI-25");
  menus.meta.screenAccess[1].available = false;
  act(() => requestMenuRefresh());
  await waitFor(() =>
    expect(screen.getByTestId("shell-availability")).toHaveTextContent(
      "false:false",
    ),
  );
  expect(screen.getByLabelText("기존 탭 입력")).toHaveValue("미저장 값");
  expect(screen.queryByRole("link", { name: "메뉴" })).not.toBeInTheDocument();
});
it("PC 전용 메뉴 갱신 뒤 휴대폰 모드에서는 숨기고 직접 주소에도 공통 안내", async () => {
  mount("/screens/UI-25");
  await screen.findByLabelText("기존 탭 입력");
  act(() => {
    mobile = true;
    listeners.forEach((listener) => listener());
  });
  await screen.findByTestId("pc-only-dashboard");
  expect(screen.queryByRole("link", { name: "메뉴" })).not.toBeInTheDocument();
  expect(screen.getByTestId("shell-availability")).toHaveTextContent(
    "false:false",
  );
  act(() => {
    mobile = false;
    listeners.forEach((listener) => listener());
  });
  await screen.findByLabelText("기존 탭 입력");
  expect(screen.getByTestId("shell-availability")).toHaveTextContent(
    "true:true",
  );
});
it("현재 주소가 갱신 후 사용 불가이면 기존 패널 대신 공통 제한 안내를 표시", async () => {
  mount("/screens/UI-25");
  const input = await screen.findByLabelText("기존 탭 입력");
  menus.data = menus.data.filter((row) => row.scrnId !== "UI-25");
  menus.meta.screenAccess[1].available = false;
  act(() => requestMenuRefresh());
  await waitFor(() => expect(input).not.toBeVisible());
  expect(screen.getByText("사용할 수 없는 화면입니다")).toBeVisible();
});

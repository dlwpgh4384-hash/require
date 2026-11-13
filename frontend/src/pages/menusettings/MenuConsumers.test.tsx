import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter } from "react-router-dom";
import {
  NavigationRoot,
  type ShellMenuResponse,
} from "../../app/NavigationRoot";
import { MenuPage } from "./MenuPage";
import type { MenuSettingItem, MenuSettingsInput } from "./menuSettings";
import { PlaceholderScreen } from "../common/PlaceholderScreen";
import { PersonalScreenLists } from "../userpreferences/PersonalScreenPreferences";
import { UserPreferencesProvider } from "../userpreferences/UserPreferencesProvider";

let rows: MenuSettingItem[];
let mobile: boolean;
let listeners: Set<() => void>;
let request: ReturnType<typeof vi.fn>;
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
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
  rows = [
    {
      menuId: "G001",
      upperMenuId: null,
      menuNm: "합성 묶음",
      scrnId: null,
      menuLvl: 1,
      sortOrd: 1,
      useYn: "Y",
      pcOnlyYn: "N",
      lockYn: "N",
      iconNm: "folder",
    },
    {
      menuId: "M001",
      upperMenuId: "G001",
      menuNm: "관리 화면",
      scrnId: "UI-25",
      menuLvl: 2,
      sortOrd: 1,
      useYn: "Y",
      pcOnlyYn: "Y",
      lockYn: "Y",
      iconNm: null,
    },
    {
      menuId: "M002",
      upperMenuId: "G001",
      menuNm: "대상 메뉴",
      scrnId: "UI-03",
      menuLvl: 2,
      sortOrd: 2,
      useYn: "Y",
      pcOnlyYn: "N",
      lockYn: "N",
      iconNm: null,
    },
  ];
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/menu-settings") {
      if (init?.method === "PUT") {
        const input: MenuSettingsInput = JSON.parse(String(init.body));
        rows = rows.map((row) => ({
          ...row,
          ...input.items.find((item) => item.menuId === row.menuId),
        }));
      }
      return success({ items: rows });
    }
    if (path === "/api/common/menus") {
      const result: ShellMenuResponse = {
        success: true,
        meta: {
          screenAccess: rows
            .filter((row) => row.scrnId)
            .map((row) => ({
              scrnId: row.scrnId!,
              available: row.useYn === "Y",
              pcOnlyYn: row.pcOnlyYn,
            })),
        },
        data: rows
          .filter((row) => row.useYn === "Y")
          .map(({ useYn: _use, lockYn: _lock, ...row }) => row),
      };
      return {
        ok: true,
        status: 200,
        json: async () => structuredClone(result),
      };
    }
    if (path.startsWith("/api/common/user-preferences/")) {
      const type = path.split("/").at(-2)!;
      return success({
        prefTypeCd: type,
        prefKey: "GLOBAL",
        found: true,
        prefVal: ["FAV", "RECENT"].includes(type)
          ? '["UI-03"]'
          : type === "PAGE_SIZE"
            ? "20"
            : type === "DENSE"
              ? "넓게"
              : "false",
        modDttm: "2026-01-02T03:04:05.123456Z",
      });
    }
    throw new Error(`예상하지 않은 시험 요청: ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
function mount() {
  render(
    <MemoryRouter initialEntries={["/screens/UI-25"]}>
      <UserPreferencesProvider>
        <NavigationRoot
          tabs
          renderScreen={(id) =>
            id === "UI-25" ? <MenuPage /> : <PlaceholderScreen scrnId={id} />
          }
          extensions={(context) => ({
            quickAccess: (
              <PersonalScreenLists
                menus={context.menus}
                available={context.available}
                mobile={context.mobile}
                collapsed={false}
                onNavigate={context.open}
              />
            ),
            actions: (
              <button type="button" onClick={() => context.open("UI-03")}>
                대상 주소 열기
              </button>
            ),
          })}
        />
      </UserPreferencesProvider>
    </MemoryRouter>,
  );
}
async function save() {
  fireEvent.click(screen.getByTestId("menu-settings-save"));
  await waitFor(() =>
    expect(
      request.mock.calls.filter((call) => call[0] === "/api/common/menus"),
    ).toHaveLength(2),
  );
  await waitFor(() =>
    expect(screen.getByTestId("menu-settings-save")).toBeEnabled(),
  );
}
function viewport(value: boolean) {
  act(() => {
    mobile = value;
    listeners.forEach((listener) => listener());
  });
}

it("PC UI-25는 실제 편집 본문, 휴대폰 같은 주소는 기존 PC 전용 안내", async () => {
  mount();
  expect(await screen.findByTestId("menu-settings-page")).toBeVisible();
  viewport(true);
  await screen.findByTestId("pc-only-dashboard");
  expect(screen.queryByTestId("menu-settings-page")).not.toBeInTheDocument();
  viewport(false);
  expect(await screen.findByTestId("menu-settings-page")).toBeVisible();
});
it("PC 전용 PUT 뒤 Sidebar·실제 FAV·RECENT 세 목록과 직접 주소가 함께 제한되고 PC는 정상", async () => {
  mount();
  await screen.findByLabelText("M002 PC 전용");
  await screen.findByTestId("fav-ui-03");
  await screen.findByTestId("recent-ui-03");
  fireEvent.click(screen.getByLabelText("M002 PC 전용"));
  await save();
  expect(screen.getByRole("link", { name: "대상 메뉴" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "대상 주소 열기" }));
  expect(await screen.findByTestId("ui-03")).toBeVisible();
  viewport(true);
  await screen.findByTestId("pc-only-dashboard");
  expect(
    screen.queryByRole("link", { name: "대상 메뉴" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("fav-ui-03")).not.toBeInTheDocument();
  expect(screen.queryByTestId("recent-ui-03")).not.toBeInTheDocument();
  viewport(false);
  expect(await screen.findByTestId("ui-03")).toBeVisible();
  expect(await screen.findByTestId("fav-ui-03")).toBeInTheDocument();
});
it("사용 해제 저장 뒤 세 목록과 직접 주소에서 제외하지만 편집 표 행은 유지", async () => {
  mount();
  await screen.findByLabelText("M002 사용");
  await screen.findByTestId("fav-ui-03");
  fireEvent.click(screen.getByLabelText("M002 사용"));
  await save();
  expect(screen.getByLabelText("M002 사용")).not.toBeChecked();
  expect(
    screen.queryByRole("link", { name: "대상 메뉴" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("fav-ui-03")).not.toBeInTheDocument();
  expect(screen.queryByTestId("recent-ui-03")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "대상 주소 열기" }));
  expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
});
it("이름 저장 후 Sidebar와 즐겨찾기 이름이 갱신되며 다른 탭을 닫지 않음", async () => {
  mount();
  await screen.findByLabelText("M002 메뉴 이름");
  await screen.findByTestId("fav-ui-03");
  fireEvent.change(screen.getByLabelText("M002 메뉴 이름"), {
    target: { value: "새 대상 이름" },
  });
  await save();
  expect(
    await screen.findByRole("link", { name: "새 대상 이름" }),
  ).toBeInTheDocument();
  expect(screen.getByTestId("fav-ui-03")).toHaveTextContent("새 대상 이름");
  expect(screen.getByTestId("menu-settings-page")).toBeVisible();
});

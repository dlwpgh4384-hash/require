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
import { MenuPage } from "./MenuPage";
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import { MENU_REFRESH_EVENT } from "../../app/menuRefresh";
import type { MenuSettingItem, MenuSettingsInput } from "./menuSettings";

let rows: MenuSettingItem[];
let request: ReturnType<typeof vi.fn>;
let failGet: boolean;
let failPut: boolean;
let failAfterSave: boolean;
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const writes = () =>
  request.mock.calls.filter(
    (call: unknown[]) => (call[1] as RequestInit | undefined)?.method === "PUT",
  );
beforeEach(() => {
  rows = [
    {
      menuId: "G001",
      upperMenuId: null,
      menuNm: "첫 묶음",
      scrnId: null,
      menuLvl: 1,
      sortOrd: 1,
      useYn: "Y",
      pcOnlyYn: "N",
      lockYn: "N",
      iconNm: "folder",
    },
    {
      menuId: "G002",
      upperMenuId: null,
      menuNm: "시스템 묶음",
      scrnId: null,
      menuLvl: 1,
      sortOrd: 2,
      useYn: "Y",
      pcOnlyYn: "N",
      lockYn: "Y",
      iconNm: "gear",
    },
    {
      menuId: "M001",
      upperMenuId: "G001",
      menuNm: "첫 메뉴",
      scrnId: "UI-01",
      menuLvl: 2,
      sortOrd: 1,
      useYn: "Y",
      pcOnlyYn: "N",
      lockYn: "N",
      iconNm: null,
    },
    {
      menuId: "M002",
      upperMenuId: "G001",
      menuNm: "중지 메뉴",
      scrnId: "UI-02",
      menuLvl: 2,
      sortOrd: 2,
      useYn: "N",
      pcOnlyYn: "N",
      lockYn: "N",
      iconNm: null,
    },
    {
      menuId: "M003",
      upperMenuId: "G001",
      menuNm: "직접 메뉴",
      scrnId: "UI-03",
      menuLvl: 2,
      sortOrd: 3,
      useYn: "Y",
      pcOnlyYn: "N",
      lockYn: "N",
      iconNm: null,
    },
    {
      menuId: "M004",
      upperMenuId: "G002",
      menuNm: "잠금 메뉴",
      scrnId: "UI-24",
      menuLvl: 2,
      sortOrd: 1,
      useYn: "Y",
      pcOnlyYn: "Y",
      lockYn: "Y",
      iconNm: null,
    },
  ];
  failGet = false;
  failPut = false;
  failAfterSave = false;
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/menu-settings") {
      if (init?.method === "PUT") {
        if (failPut)
          return {
            ok: false,
            status: 400,
            json: async () => ({
              success: false,
              meta: {},
              message: "시스템 메뉴는 사용 해제할 수 없습니다.",
              errors: [
                {
                  field: "useYn",
                  message: "시스템 메뉴는 사용 해제할 수 없습니다.",
                },
              ],
            }),
          };
        const input: MenuSettingsInput = JSON.parse(String(init.body));
        rows = rows.map((row) => ({
          ...row,
          ...input.items.find((item) => item.menuId === row.menuId),
        }));
        if (failAfterSave) failGet = true;
        return success({ items: rows });
      }
      if (failGet) throw new Error("조회 실패");
      return success({ items: rows });
    }
    if (path.startsWith("/api/common/menu-options?"))
      return success({
        upperMenus: rows
          .filter((row) => row.menuLvl === 1)
          .map(({ menuId, menuNm }) => ({ menuId, menuNm })),
        screens: [{ scrnId: "UI-25", menuNm: "메뉴" }],
      });
    if (path === "/api/common/menus" && init?.method === "POST") {
      const input = JSON.parse(String(init.body));
      const item: MenuSettingItem = {
        ...input,
        menuId: "G003",
        menuLvl: 1,
        upperMenuId: null,
        scrnId: null,
        sortOrd: 3,
        useYn: "Y",
        pcOnlyYn: "N",
        lockYn: "N",
        iconNm: "folder",
      };
      rows.push(item);
      return success(item);
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
async function mount() {
  render(<MenuPage />);
  await screen.findByLabelText("M003 메뉴 이름");
}
function drag(source: string, target: string) {
  fireEvent.dragStart(
    screen.getByTestId(`menu-settings-handle-${source.toLowerCase()}`),
    { dataTransfer: { setData: vi.fn() } },
  );
  fireEvent.drop(
    screen.getByTestId(`menu-settings-row-${target.toLowerCase()}`),
  );
}
async function save() {
  fireEvent.click(screen.getByTestId("menu-settings-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
}

it("관리 표는 중지 행도 보이며 묶음은 굵은 행·메뉴는 들여쓰기·대메뉴 체크 없음", async () => {
  await mount();
  expect(screen.getByTestId("menu-settings-row-g001")).toHaveClass(
    "menu-settings-group",
  );
  expect(screen.getByTestId("menu-settings-row-m002")).toHaveClass(
    "menu-settings-child",
  );
  expect(screen.getByLabelText("M002 사용")).not.toBeChecked();
  expect(
    within(screen.getByTestId("menu-settings-row-g001")).queryByRole(
      "checkbox",
    ),
  ).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("menu-settings-row-m003")).getByText("UI-03"),
  ).toBeInTheDocument();
});
it("잠금 이름 편집과 3→1 드래그 저장은 영향을 받은 형제까지 보내고 직접 ID만 추적", async () => {
  const refreshed = vi.fn();
  window.addEventListener(MENU_REFRESH_EVENT, refreshed);
  try {
    await mount();
    fireEvent.change(screen.getByLabelText("M004 메뉴 이름"), {
      target: { value: " 새 잠금 이름 " },
    });
    drag("M003", "M001");
    await save();
    const body: MenuSettingsInput = JSON.parse(String(writes()[0][1].body));
    expect(body.movedMenuIds).toEqual(["M003"]);
    expect(body.items.find((row) => row.menuId === "M003")?.sortOrd).toBe(1);
    expect(body.items.find((row) => row.menuId === "M001")?.sortOrd).toBe(2);
    expect(body.items.find((row) => row.menuId === "M002")?.sortOrd).toBe(3);
    expect(body.items.find((row) => row.menuId === "M004")?.menuNm).toBe(
      " 새 잠금 이름 ",
    );
    for (const row of body.items) {
      expect(row).not.toHaveProperty("menuLvl");
      expect(row).not.toHaveProperty("scrnId");
      expect(row).not.toHaveProperty("iconNm");
      expect(row).not.toHaveProperty("lockYn");
    }
    await waitFor(() => expect(refreshed).toHaveBeenCalledOnce());
    expect(
      request.mock.calls.filter(
        (call) =>
          call[0] === "/api/common/menu-settings" && call[1]?.method !== "PUT",
      ),
    ).toHaveLength(2);
  } finally {
    window.removeEventListener(MENU_REFRESH_EVENT, refreshed);
  }
});
it("이름 필수 검증은 첫 오류에 포커스·PUT 없음", async () => {
  await mount();
  fireEvent.change(screen.getByLabelText("M003 메뉴 이름"), {
    target: { value: " " },
  });
  fireEvent.click(screen.getByTestId("menu-settings-save"));
  expect(
    await screen.findByText("메뉴 이름을 1~50자로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("M003 메뉴 이름")).toHaveFocus();
  expect(writes()).toHaveLength(0);
});
it("51 Unicode code points는 저장하지 않는다", async () => {
  await mount();
  fireEvent.change(screen.getByLabelText("M003 메뉴 이름"), {
    target: { value: "😀".repeat(51) },
  });
  fireEvent.click(screen.getByTestId("menu-settings-save"));
  await screen.findByText("메뉴 이름을 1~50자로 입력해 주세요.");
  expect(writes()).toHaveLength(0);
});
it("50 Unicode code points는 정규화 없이 보낸다", async () => {
  await mount();
  fireEvent.change(screen.getByLabelText("M003 메뉴 이름"), {
    target: { value: "😀".repeat(50) },
  });
  await save();
  expect(JSON.parse(String(writes()[0][1].body)).items[0].menuNm).toBe(
    "😀".repeat(50),
  );
});
it("일반 메뉴 다른 묶음 이동은 두 묶음 형제 순서를 반영", async () => {
  await mount();
  drag("M003", "G002");
  await save();
  const body: MenuSettingsInput = JSON.parse(String(writes()[0][1].body));
  expect(body.items.find((row) => row.menuId === "M003")).toMatchObject({
    upperMenuId: "G002",
    sortOrd: 2,
  });
  expect(body.movedMenuIds).toEqual(["M003"]);
});
it("잠금 메뉴 다른 묶음 drop은 draft에 반영하지 않음", async () => {
  await mount();
  drag("M004", "G001");
  expect(screen.getByTestId("menu-settings-failure")).toHaveTextContent(
    "시스템 메뉴는 다른 묶음으로 옮길 수 없습니다.",
  );
  fireEvent.click(screen.getByTestId("menu-settings-save"));
  expect(writes()).toHaveLength(0);
});
it("잠금 메뉴 사용은 체크 가능하고 서버 거부 후 draft 보존", async () => {
  await mount();
  failPut = true;
  expect(screen.getByLabelText("M004 사용")).toBeEnabled();
  fireEvent.click(screen.getByLabelText("M004 사용"));
  await save();
  await screen.findByText("시스템 메뉴는 사용 해제할 수 없습니다.");
  expect(screen.getByLabelText("M004 사용")).not.toBeChecked();
  expect(rows.find((row) => row.menuId === "M004")?.useYn).toBe("Y");
});
it("대메뉴 payload에는 useYn과 pcOnlyYn을 넣지 않는다", async () => {
  await mount();
  fireEvent.change(screen.getByLabelText("G001 메뉴 이름"), {
    target: { value: "새 묶음" },
  });
  await save();
  const row = JSON.parse(String(writes()[0][1].body)).items[0];
  expect(row).not.toHaveProperty("useYn");
  expect(row).not.toHaveProperty("pcOnlyYn");
});
it("취소는 서버 스냅샷과 순서를 복원하며 추가 확인이나 PUT 없음", async () => {
  await mount();
  drag("M003", "M001");
  fireEvent.change(screen.getByLabelText("M003 메뉴 이름"), {
    target: { value: "미저장" },
  });
  fireEvent.click(screen.getByTestId("menu-settings-cancel"));
  expect(screen.getByLabelText("M003 메뉴 이름")).toHaveValue("직접 메뉴");
  expect(
    within(screen.getByTestId("menu-settings-row-m003")).getByText("3"),
  ).toBeInTheDocument();
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("dirty 등록 취소는 draft 유지·등록창 미개방·쓰기 없음", async () => {
  await mount();
  fireEvent.change(screen.getByLabelText("M003 메뉴 이름"), {
    target: { value: "미저장" },
  });
  fireEvent.click(screen.getByTestId("menu-registration-open"));
  expect(screen.getAllByRole("alertdialog")).toHaveLength(1);
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "취소",
    }),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByLabelText("M003 메뉴 이름")).toHaveValue("미저장");
  expect(writes()).toHaveLength(0);
});
it("dirty 등록 버리기는 스냅샷 복원 후 기존 등록창을 한 번 연다", async () => {
  await mount();
  fireEvent.change(screen.getByLabelText("M003 메뉴 이름"), {
    target: { value: "미저장" },
  });
  fireEvent.click(screen.getByTestId("menu-registration-open"));
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "버리기",
    }),
  );
  expect(
    await screen.findByRole("dialog", { name: "메뉴 등록" }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("M003 메뉴 이름")).toHaveValue("직접 메뉴");
  expect(writes()).toHaveLength(0);
});
it("등록 완료는 관리 GET과 shell refresh 경계를 재사용", async () => {
  const refreshed = vi.fn();
  window.addEventListener(MENU_REFRESH_EVENT, refreshed);
  try {
    await mount();
    fireEvent.click(screen.getByTestId("menu-registration-open"));
    await waitFor(() =>
      expect(screen.getByTestId("menu-registration-level-1")).toBeEnabled(),
    );
    fireEvent.click(screen.getByTestId("menu-registration-level-1"));
    fireEvent.change(screen.getByTestId("menu-registration-name"), {
      target: { value: "신규 묶음" },
    });
    await waitFor(() =>
      expect(screen.getByTestId("menu-registration-submit")).toBeEnabled(),
    );
    fireEvent.click(screen.getByTestId("menu-registration-submit"));
    await screen.findByLabelText("G003 메뉴 이름");
    expect(refreshed).toHaveBeenCalledOnce();
    expect(writes()).toHaveLength(0);
  } finally {
    window.removeEventListener(MENU_REFRESH_EVENT, refreshed);
  }
});
it("조회 실패는 재시도하며 읽기 전 저장 불가", async () => {
  failGet = true;
  render(<MenuPage />);
  await screen.findByTestId("menu-settings-failure");
  expect(screen.queryByTestId("menu-settings-save")).not.toBeInTheDocument();
  failGet = false;
  fireEvent.click(screen.getByTestId("menu-settings-retry"));
  await screen.findByLabelText("M003 메뉴 이름");
});
it("정상 빈 목록도 등록 진입은 유지", async () => {
  rows = [];
  render(<MenuPage />);
  await screen.findByText("등록된 메뉴가 없습니다.");
  expect(screen.getByTestId("menu-registration-open")).toBeEnabled();
});
it("Ctrl+S와 공통 dirty 연동", async () => {
  const mark = vi.fn();
  render(
    <ScreenDirtyContext.Provider value={mark}>
      <MenuPage />
    </ScreenDirtyContext.Provider>,
  );
  const input = await screen.findByLabelText("M003 메뉴 이름");
  fireEvent.change(input, { target: { value: "단축키 변경" } });
  await waitFor(() => expect(mark).toHaveBeenCalledWith("UI-25", true));
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(writes()).toHaveLength(1));
});
it("저장 후 GET 실패는 PUT을 반복하지 않고 재조회", async () => {
  await mount();
  failAfterSave = true;
  fireEvent.change(screen.getByLabelText("M003 메뉴 이름"), {
    target: { value: "저장된 이름" },
  });
  await save();
  await screen.findByRole("button", { name: "재조회" });
  expect(screen.getByTestId("menu-settings-save")).toBeDisabled();
  failGet = false;
  fireEvent.click(screen.getByTestId("menu-settings-retry"));
  await waitFor(() =>
    expect(screen.getByTestId("menu-settings-save")).toBeEnabled(),
  );
  expect(writes()).toHaveLength(1);
});

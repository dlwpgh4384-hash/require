import { useEffect, useState } from "react";
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
import { MenuRegistration } from "./MenuRegistration";
import type {
  MenuRegistrationItem,
  MenuRegistrationOptions,
} from "./menuRegistration";
import { MENU_REFRESH_EVENT } from "../../app/menuRefresh";

let request: ReturnType<typeof vi.fn>;
let rows: MenuRegistrationItem[];
let options: MenuRegistrationOptions;
let failOptions: boolean;
let failCreate: boolean;
let failRefresh: boolean;
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const writes = () =>
  request.mock.calls.filter((call: unknown[]) =>
    ["POST", "PUT"].includes(
      (call[1] as RequestInit | undefined)?.method ?? "",
    ),
  );

beforeEach(() => {
  rows = [
    {
      menuId: "G007",
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
      menuId: "M027",
      upperMenuId: "G007",
      menuNm: "기존 사용자",
      scrnId: "UI-23",
      menuLvl: 2,
      sortOrd: 3,
      useYn: "Y",
      pcOnlyYn: "Y",
      lockYn: "Y",
      iconNm: null,
    },
  ];
  options = {
    upperMenus: [{ menuId: "G007", menuNm: "합성 묶음" }],
    screens: [
      { scrnId: "UI-23", menuNm: "사용자" },
      { scrnId: "UI-25", menuNm: "메뉴" },
    ],
  };
  failOptions = false;
  failCreate = false;
  failRefresh = false;
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path.startsWith("/api/common/menu-options?")) {
      if (failOptions) throw new Error("network");
      return success(
        path.endsWith("menuLvl=1") ? { upperMenus: [], screens: [] } : options,
      );
    }
    if (path === "/api/common/menu-settings") {
      if (failRefresh) throw new Error("network");
      return success({ items: rows });
    }
    if (path === "/api/common/menus" && init?.method === "POST") {
      if (failCreate)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            meta: {},
            message: "이미 다른 메뉴에 등록된 화면입니다.",
            errors: [
              {
                field: "scrnId",
                message: "이미 다른 메뉴에 등록된 화면입니다.",
              },
            ],
          }),
        };
      const input = JSON.parse(String(init.body));
      const item: MenuRegistrationItem = {
        ...input,
        menuId: input.menuLvl === 1 ? "G008" : "M031",
        upperMenuId: input.upperMenuId ?? null,
        scrnId: input.scrnId ?? null,
        sortOrd: 4,
        useYn: "Y",
        pcOnlyYn: input.pcOnlyYn ?? "N",
        lockYn: "N",
        iconNm: input.menuLvl === 1 ? "folder" : null,
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

/** 다른 단계가 소유하는 관리 표의 공개 경계만 흉내 낸다. 수정 PUT 구현은 이 시험의 대상이 아니다. */
function ManagementHost() {
  const [snapshot, setSnapshot] = useState<MenuRegistrationItem[] | null>(null);
  const [draft, setDraft] = useState<MenuRegistrationItem[]>([]);
  const [dialogDirty, setDialogDirty] = useState(false);
  const read = async () => {
    const value = await (await fetch("/api/common/menu-settings")).json();
    setSnapshot(value.data.items);
    setDraft(value.data.items);
  };
  useEffect(() => {
    void read();
  }, []);
  return (
    <section className="screen-body" data-testid="management-host">
      <MenuRegistration
        dirty={!!snapshot && JSON.stringify(snapshot) !== JSON.stringify(draft)}
        disabled={!snapshot}
        registeredScreenIds={
          snapshot?.flatMap((row) => (row.scrnId ? [row.scrnId] : [])) ?? []
        }
        onDiscard={() => setDraft(snapshot!)}
        onRegistered={read}
        onDialogDirty={setDialogDirty}
      />
      <output data-testid="dialog-dirty">{String(dialogDirty)}</output>
      {draft.map((row) => (
        <div
          key={row.menuId}
          data-testid={`management-row-${row.menuId.toLowerCase()}`}
        >
          <input
            aria-label={`이름 ${row.menuId}`}
            value={row.menuNm}
            onChange={(event) =>
              setDraft(
                draft.map((item) =>
                  item.menuId === row.menuId
                    ? { ...item, menuNm: event.target.value }
                    : item,
                ),
              )
            }
          />
          <input
            aria-label={`순서 ${row.menuId}`}
            type="number"
            value={row.sortOrd}
            onChange={(event) =>
              setDraft(
                draft.map((item) =>
                  item.menuId === row.menuId
                    ? { ...item, sortOrd: Number(event.target.value) }
                    : item,
                ),
              )
            }
          />
        </div>
      ))}
    </section>
  );
}
async function mount() {
  render(<ManagementHost />);
  await waitFor(() =>
    expect(screen.getByTestId("menu-registration-open")).toBeEnabled(),
  );
}
async function open() {
  fireEvent.click(screen.getByTestId("menu-registration-open"));
  await waitFor(() =>
    expect(screen.getByTestId("menu-registration-parent")).toBeEnabled(),
  );
}
function validChild() {
  fireEvent.change(screen.getByLabelText("메뉴 이름 *"), {
    target: { value: "새 합성 메뉴" },
  });
  fireEvent.change(screen.getByLabelText("상위 대메뉴 *"), {
    target: { value: "G007" },
  });
  fireEvent.change(screen.getByLabelText("화면 번호 *"), {
    target: { value: "UI-25" },
  });
}
it("dirty 이름 취소는 draft 보존, 창 미개방, POST와 PUT 모두 0회", async () => {
  await mount();
  fireEvent.change(screen.getByLabelText("이름 M027"), {
    target: { value: "미저장 이름" },
  });
  fireEvent.click(screen.getByTestId("menu-registration-open"));
  expect(screen.getAllByRole("alertdialog")).toHaveLength(1);
  expect(screen.getByRole("alertdialog")).toHaveAccessibleName(
    "변경 내용을 버릴까요?",
  );
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "취소",
    }),
  );
  expect(screen.getByLabelText("이름 M027")).toHaveValue("미저장 이름");
  expect(
    screen.queryByTestId("menu-registration-dialog"),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("dirty 순서 버리기는 서버 snapshot 복원 후 새 메뉴만 POST하고 관리 GET과 shell refresh", async () => {
  const refresh = vi.fn();
  window.addEventListener(MENU_REFRESH_EVENT, refresh);
  try {
    await mount();
    fireEvent.change(screen.getByLabelText("순서 M027"), {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByTestId("menu-registration-open"));
    fireEvent.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "버리기",
      }),
    );
    expect(screen.getByLabelText("순서 M027")).toHaveValue(3);
    await waitFor(() =>
      expect(screen.getByTestId("menu-registration-parent")).toBeEnabled(),
    );
    validChild();
    fireEvent.click(screen.getByTestId("menu-registration-submit"));
    await screen.findByTestId("management-row-m031");
    expect(
      screen.queryByTestId("menu-registration-dialog"),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("순서 M027")).toHaveValue(3);
    expect(rows.find((row) => row.menuId === "M027")?.sortOrd).toBe(3);
    expect(writes()).toHaveLength(1);
    expect(JSON.parse(String(writes()[0][1].body))).toEqual({
      menuLvl: 2,
      menuNm: "새 합성 메뉴",
      upperMenuId: "G007",
      scrnId: "UI-25",
      pcOnlyYn: "N",
    });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(
      request.mock.calls.filter(
        (call) => call[0] === "/api/common/menu-settings",
      ),
    ).toHaveLength(2);
  } finally {
    window.removeEventListener(MENU_REFRESH_EVENT, refresh);
  }
});
it("기본 서브메뉴이며 레벨을 바꿔도 이름 유지, 대메뉴는 이름과 레벨만 전송", async () => {
  await mount();
  await open();
  expect(screen.getByLabelText("레벨 *")).toHaveValue("2");
  validChild();
  fireEvent.change(screen.getByLabelText("레벨 *"), { target: { value: "1" } });
  expect(screen.getByLabelText("메뉴 이름 *")).toHaveValue("새 합성 메뉴");
  expect(screen.queryByLabelText("화면 번호 *")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("PC 전용")).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/메뉴 ID|아이콘/)).not.toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByTestId("menu-registration-submit")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("menu-registration-submit"));
  await screen.findByTestId("management-row-g008");
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    menuLvl: 1,
    menuNm: "새 합성 메뉴",
  });
  expect(
    request.mock.calls.some(
      (call) => call[0] === "/api/common/menu-options?menuLvl=1",
    ),
  ).toBe(true);
});
it("등록된 화면은 선택지에서 제외하며 중지 행도 등록된 것으로 간주", async () => {
  rows[1].useYn = "N";
  await mount();
  await open();
  const select = screen.getByLabelText("화면 번호 *");
  expect(
    within(select).queryByRole("option", { name: /UI-23/ }),
  ).not.toBeInTheDocument();
  expect(
    within(select).getByRole("option", { name: /UI-25/ }),
  ).toBeInTheDocument();
});
it("미등록 개발 화면이 없으면 서브메뉴 저장 금지, 대메뉴는 가능", async () => {
  options.screens = [options.screens[0]];
  await mount();
  await open();
  expect(screen.getByTestId("menu-registration-no-screens")).toHaveTextContent(
    "개발된 미등록 화면이 없습니다",
  );
  expect(screen.getByTestId("menu-registration-submit")).toBeDisabled();
  fireEvent.change(screen.getByLabelText("레벨 *"), { target: { value: "1" } });
  await waitFor(() =>
    expect(screen.getByTestId("menu-registration-submit")).toBeEnabled(),
  );
  expect(writes()).toHaveLength(0);
});
it("부모 없음은 가짜 부모를 만들지 않고 서브메뉴 저장을 막는다", async () => {
  options.upperMenus = [];
  await mount();
  fireEvent.click(screen.getByTestId("menu-registration-open"));
  await waitFor(() =>
    expect(screen.getByTestId("menu-registration-parent")).toBeEnabled(),
  );
  expect(screen.getByTestId("menu-registration-submit")).toBeDisabled();
  expect(
    within(screen.getByLabelText("상위 대메뉴 *")).getAllByRole("option"),
  ).toHaveLength(1);
});
it("옵션 loading에서는 등록 불가", async () => {
  await mount();
  request.mockImplementation(() => new Promise(() => {}));
  fireEvent.click(screen.getByTestId("menu-registration-open"));
  expect(
    screen.getByText("선택 목록을 불러오는 중입니다."),
  ).toBeInTheDocument();
  expect(screen.getByTestId("menu-registration-submit")).toBeDisabled();
});
it("옵션 실패시 재시도하고 입력을 보존한다", async () => {
  failOptions = true;
  await mount();
  fireEvent.click(screen.getByTestId("menu-registration-open"));
  await screen.findByTestId("menu-registration-options-retry");
  fireEvent.change(screen.getByLabelText("메뉴 이름 *"), {
    target: { value: "보존할 이름" },
  });
  failOptions = false;
  fireEvent.click(screen.getByTestId("menu-registration-options-retry"));
  await waitFor(() =>
    expect(screen.getByTestId("menu-registration-parent")).toBeEnabled(),
  );
  expect(screen.getByLabelText("메뉴 이름 *")).toHaveValue("보존할 이름");
  expect(writes()).toHaveLength(0);
});
it("필수 누락은 필드 오류와 첫 오류 포커스, POST 0회", async () => {
  await mount();
  await open();
  fireEvent.click(screen.getByTestId("menu-registration-submit"));
  expect(
    screen.getByTestId("menu-registration-error-menunm"),
  ).toBeInTheDocument();
  expect(
    screen.getByTestId("menu-registration-error-uppermenuid"),
  ).toBeInTheDocument();
  expect(
    screen.getByTestId("menu-registration-error-scrnid"),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("메뉴 이름 *")).toHaveFocus();
  expect(writes()).toHaveLength(0);
});
it("이름 공백과 51 Unicode code point는 거부, 50 code point는 그대로 등록", async () => {
  await mount();
  await open();
  validChild();
  fireEvent.change(screen.getByLabelText("메뉴 이름 *"), {
    target: { value: "\u00a0" },
  });
  fireEvent.click(screen.getByTestId("menu-registration-submit"));
  expect(writes()).toHaveLength(0);
  fireEvent.change(screen.getByLabelText("메뉴 이름 *"), {
    target: { value: "😀".repeat(51) },
  });
  fireEvent.click(screen.getByTestId("menu-registration-submit"));
  expect(writes()).toHaveLength(0);
  fireEvent.change(screen.getByLabelText("메뉴 이름 *"), {
    target: { value: "😀".repeat(50) },
  });
  fireEvent.click(screen.getByTestId("menu-registration-submit"));
  await screen.findByTestId("management-row-m031");
  expect(JSON.parse(String(writes()[0][1].body)).menuNm).toBe("😀".repeat(50));
});
it("서버 중복 errors 배열을 표시하고 선택값과 창을 보존", async () => {
  failCreate = true;
  await mount();
  await open();
  validChild();
  fireEvent.click(screen.getByTestId("menu-registration-submit"));
  await screen.findByTestId("menu-registration-error-scrnid");
  expect(screen.getByTestId("menu-registration-failure")).toHaveTextContent(
    "이미 다른 메뉴에 등록된 화면입니다.",
  );
  expect(screen.getByLabelText("메뉴 이름 *")).toHaveValue("새 합성 메뉴");
  expect(screen.getByLabelText("화면 번호 *")).toHaveValue("UI-25");
  expect(screen.getByLabelText("화면 번호 *")).toHaveFocus();
  expect(rows).toHaveLength(2);
});
it("깨끗한 등록 창 취소는 POST 없이 닫힌다", async () => {
  await mount();
  await open();
  fireEvent.click(screen.getByTestId("menu-registration-cancel"));
  expect(
    screen.queryByTestId("menu-registration-dialog"),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("등록 후 재조회 실패는 이미 저장된 등록을 다시 POST하지 않는다", async () => {
  await mount();
  await open();
  validChild();
  failRefresh = true;
  fireEvent.click(screen.getByTestId("menu-registration-submit"));
  await screen.findByTestId("menu-registration-refresh-failure");
  expect(
    screen.queryByTestId("menu-registration-dialog"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("menu-registration-open")).toBeDisabled();
  failRefresh = false;
  fireEvent.click(screen.getByTestId("menu-registration-refresh-retry"));
  await screen.findByTestId("management-row-m031");
  expect(writes()).toHaveLength(1);
});

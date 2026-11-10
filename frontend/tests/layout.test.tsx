import { act, render, screen, within, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import App from "../src/App";
import type { MenuItem, MenuResponse } from "../src/services/menus";

let mediaListeners: Set<(event: MediaQueryListEvent) => void>;
let mobile = false;
const item = (
  menuId: string,
  parent: string | null,
  scrnId: string | null,
  pcOnlyYn: "Y" | "N" = "N",
): MenuItem => ({
  menuId,
  upperMenuId: parent,
  menuNm: `메뉴 ${menuId}`,
  scrnId,
  menuLvl: parent ? 2 : 1,
  sortOrd: 1,
  pcOnlyYn,
  iconNm: parent ? null : "grid",
});
function response(items: MenuItem[], parents: string[] = []): MenuResponse {
  return {
    success: true,
    meta: {
      parentMenuIds: parents,
      screenStates: Object.fromEntries(
        items
          .filter((row) => row.scrnId)
          .map((row) => [
            row.scrnId!,
            {
              available: !parents.includes(row.menuId),
              pcOnlyYn: row.pcOnlyYn,
              menuId: row.menuId,
              menuNm: row.menuNm,
            },
          ]),
      ),
    },
    data: { success: true, meta: {}, data: items },
  };
}
const initial = () =>
  response(
    [
      item("group", null, null),
      item("first", "group", "screen-first"),
      item("second", "group", "screen-second"),
      item("pc-group", null, null),
      item("pc", "pc-group", "screen-pc", "Y"),
    ],
    ["group", "pc-group"],
  );
function serve(body: MenuResponse) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: true, json: async () => body }),
  );
}
function show(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}
function resize(value: boolean) {
  mobile = value;
  act(() =>
    mediaListeners.forEach((listener) =>
      listener({ matches: value } as MediaQueryListEvent),
    ),
  );
}
beforeEach(() => {
  mobile = false;
  mediaListeners = new Set();
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      get matches() {
        return mobile;
      },
      addEventListener: (
        _: string,
        listener: (e: MediaQueryListEvent) => void,
      ) => mediaListeners.add(listener),
      removeEventListener: (
        _: string,
        listener: (e: MediaQueryListEvent) => void,
      ) => mediaListeners.delete(listener),
    })),
  );
  serve(initial());
});

it("selects the DB first leaf and keeps one shell across navigation, with six disabled future slots", async () => {
  show();
  expect(
    await screen.findByRole("heading", { name: "메뉴 first" }),
  ).toBeInTheDocument();
  // Route selection opens its ancestry in an effect after the body renders.
  expect(
    await screen.findByRole("button", { name: "메뉴 group", expanded: true }),
  ).toHaveAttribute("aria-expanded", "true");
  fireEvent.click(screen.getByRole("link", { name: "메뉴 second" }));
  expect(
    await screen.findByRole("heading", { name: "메뉴 second" }),
  ).toBeInTheDocument();
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("navigation")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
  for (const name of [
    "즐겨찾기",
    "최근 본 화면",
    "화면 탭",
    "상단 도구줄",
    "단축키",
    "알림",
  ]) {
    expect(
      screen.getByRole("button", { name: `${name}: 준비 중` }),
    ).toBeDisabled();
  }
  expect(vi.mocked(fetch)).toHaveBeenCalledWith(
    "/api/common/menus",
    expect.any(Object),
  );
});

it("preserves direct entry and hides PC-only leaves and empty groups while restoring the same body", async () => {
  show("/screens/screen-pc");
  expect(
    await screen.findByRole("heading", { name: "메뉴 pc" }),
  ).toBeInTheDocument();
  resize(true);
  expect(screen.getByText("PC 전용 화면입니다")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "메뉴 펼치기" }));
  expect(
    screen.queryByRole("button", { name: /메뉴 pc-group/ }),
  ).not.toBeInTheDocument();
  resize(false);
  expect(screen.getByRole("heading", { name: "메뉴 pc" })).toBeInTheDocument();
  expect(
    within(screen.getByRole("main")).getByText("준비 중"),
  ).toBeInTheDocument();
});

it("does not navigate a mixed node, including when its original children are hidden", async () => {
  const data = initial();
  data.data.data.push(
    item("mixed", "group", "mixed-screen"),
    item("child", "mixed", "child-screen"),
  );
  data.meta.parentMenuIds.push("mixed");
  serve(data);
  show("/screens/screen-first");
  await screen.findByRole("heading", { name: "메뉴 first" });
  const mixed = await screen.findByRole("button", { name: /메뉴 mixed/ });
  fireEvent.click(mixed);
  expect(screen.getByRole("link", { name: "메뉴 child" })).toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "메뉴 first" }),
  ).toBeInTheDocument();
  fireEvent.click(mixed);
  expect(
    screen.queryByRole("link", { name: "메뉴 child" }),
  ).not.toBeInTheDocument();
  resize(true);
  fireEvent.click(screen.getByRole("button", { name: "메뉴 펼치기" }));
  expect(
    screen.getByRole("heading", { name: "메뉴 first" }),
  ).toBeInTheDocument();
});

it("distinguishes unavailable from unregistered, and returns only on explicit first-screen click", async () => {
  const data = initial();
  data.meta.screenStates["disabled-screen"] = {
    available: false,
    pcOnlyYn: "N",
    menuId: null,
    menuNm: null,
  };
  serve(data);
  const view = show("/screens/disabled-screen");
  expect(
    await screen.findByText("사용할 수 없는 화면입니다"),
  ).toBeInTheDocument();
  expect(screen.queryByText("없는 화면입니다")).not.toBeInTheDocument();
  view.unmount();
  show("/screens/unknown-screen");
  expect(await screen.findByText("없는 화면입니다")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "첫 화면으로" }));
  expect(
    await screen.findByRole("heading", { name: "메뉴 first" }),
  ).toBeInTheDocument();
});

it("retries a real request after failure and distinguishes a successful empty list", async () => {
  const fetcher = vi
    .fn()
    .mockRejectedValueOnce(new Error("sensitive-internal-data"))
    .mockRejectedValueOnce(new Error("still-down"))
    .mockResolvedValue({ ok: true, json: async () => response([]) });
  vi.stubGlobal("fetch", fetcher);
  show("/screens/screen-first");
  fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));
  fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("등록된 메뉴 없음")).toBeInTheDocument();
  expect(fetcher).toHaveBeenCalledTimes(3);
  expect(screen.queryByText("sensitive-internal-data")).not.toBeInTheDocument();
});

it("does not call a loading or no-first-screen state a successful screen", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => new Promise(() => undefined)),
  );
  const view = show();
  expect(
    screen.getAllByText("메뉴를 불러오는 중입니다").length,
  ).toBeGreaterThan(0);
  expect(
    within(screen.getByRole("main")).queryByText("준비 중"),
  ).not.toBeInTheDocument();
  view.unmount();
  serve(response([]));
  show();
  expect(await screen.findByText("표시할 화면이 없습니다")).toBeInTheDocument();
});

it("opens the active ancestry on direct entry and supports keyboard group toggle and navigation", async () => {
  const user = userEvent.setup();
  show("/screens/screen-second");
  const selected = await screen.findByRole("link", { name: "메뉴 second" });
  expect(selected).toHaveAttribute("aria-current", "page");
  const group = screen.getByRole("button", { name: "메뉴 group" });
  expect(group).toHaveAttribute("aria-expanded", "true");
  group.focus();
  await user.keyboard("{Enter}");
  expect(group).toHaveAttribute("aria-expanded", "false");
  expect(
    screen.queryByRole("link", { name: "메뉴 second" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "메뉴 second" }),
  ).toBeInTheDocument();
  await user.keyboard(" ");
  const first = screen.getByRole("link", { name: "메뉴 first" });
  first.focus();
  await user.keyboard("{Enter}");
  expect(
    await screen.findByRole("heading", { name: "메뉴 first" }),
  ).toBeInTheDocument();
  expect(first).toHaveAttribute("aria-current", "page");
  expect(selected).not.toHaveAttribute("aria-current");
});

it("keeps nested group expansion when its ancestor is collapsed and reopened", async () => {
  const data = initial();
  data.data.data.push(
    item("nested", "group", null),
    item("nested-leaf", "nested", "nested-screen"),
  );
  data.meta.parentMenuIds.push("nested");
  serve(data);
  show("/screens/screen-first");
  const nested = await screen.findByRole("button", { name: "메뉴 nested" });
  fireEvent.click(nested);
  const group = screen.getByRole("button", { name: "메뉴 group" });
  fireEvent.click(group);
  fireEvent.click(group);
  expect(
    screen.getByRole("link", { name: "메뉴 nested-leaf" }),
  ).toBeInTheDocument();
  expect(nested).toHaveAttribute("aria-expanded", "true");
});

it("keeps a mixed parent from becoming a screen link after all children are filtered", async () => {
  const data = initial();
  data.data.data.push(
    item("mixed", "group", "mixed-screen"),
    item("pc-child", "mixed", "pc-child-screen", "Y"),
  );
  data.meta.parentMenuIds.push("mixed");
  serve(data);
  show("/screens/screen-first");
  await screen.findByRole("button", { name: "메뉴 mixed" });
  resize(true);
  fireEvent.click(screen.getByRole("button", { name: "메뉴 펼치기" }));
  expect(
    screen.queryByRole("button", { name: "메뉴 mixed" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "메뉴 mixed" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "메뉴 first" }),
  ).toBeInTheDocument();
});

it("recovers the same selected screen after retry and prevents duplicate requests while pending", async () => {
  let resolveRetry!: (value: unknown) => void;
  const fetcher = vi
    .fn()
    .mockRejectedValueOnce(new Error("unavailable"))
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRetry = resolve;
        }),
    );
  vi.stubGlobal("fetch", fetcher);
  show("/screens/screen-second");
  fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));
  expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
  expect(
    screen.queryByRole("button", { name: "다시 시도" }),
  ).not.toBeInTheDocument();
  expect(fetcher).toHaveBeenCalledTimes(2);
  await act(async () =>
    resolveRetry({ ok: true, json: async () => initial() }),
  );
  expect(
    await screen.findByRole("heading", { name: "메뉴 second" }),
  ).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "메뉴 second" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

it.each([
  ["null", null],
  ["single envelope", { success: true, meta: {}, data: [] }],
  [
    "invalid menu fields",
    {
      ...initial(),
      data: { success: true, meta: {}, data: [{ menuId: "bad" }] },
    },
  ],
  [
    "invalid screen metadata",
    { ...initial(), meta: { parentMenuIds: [], screenStates: { bad: null } } },
  ],
  ["error envelope", { success: false, meta: {} }],
])("shows a recoverable error instead of crashing for %s", async (_, body) => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: true, json: async () => body }),
  );
  show();
  expect(await screen.findByRole("alert")).toHaveTextContent("메뉴 조회 오류");
  expect(screen.getByRole("button", { name: "다시 시도" })).toBeEnabled();
  expect(
    within(screen.getByRole("main")).queryByText("준비 중"),
  ).not.toBeInTheDocument();
});

it.each([
  "/invalid-path",
  "/screens/%E0%A4%A",
  "/screens/__proto__",
  "/screens/toString",
])(
  "handles unknown or malformed address %s without confusing it with a disabled screen",
  async (path) => {
    show(path);
    expect(await screen.findByText("없는 화면입니다")).toBeInTheDocument();
    expect(
      screen.queryByText("사용할 수 없는 화면입니다"),
    ).not.toBeInTheDocument();
  },
);

it("offers no writable fields, login or role-based controls for the specified anonymous user", async () => {
  show();
  await screen.findByRole("heading", { name: "메뉴 first" });
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /저장|삭제|로그인/ }),
  ).not.toBeInTheDocument();
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

it("disables first-screen recovery when the API has no visible leaf", async () => {
  serve(response([]));
  show("/screens/unknown");
  expect(await screen.findByText("없는 화면입니다")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "첫 화면으로" })).toBeDisabled();
});

it("uses API sibling order and menuId ties for initial selection and explicit recovery, not menu names", async () => {
  const data = initial();
  data.data.data.find((row) => row.menuId === "second")!.sortOrd = 0;
  serve(data);
  const view = show();
  await screen.findByRole("heading", { name: "메뉴 second" });
  await screen.findByRole("button", { name: "메뉴 group", expanded: true });
  expect(
    within(screen.getByRole("navigation"))
      .getAllByRole("link")
      .map((link) => link.textContent),
  ).toEqual(["메뉴 second", "메뉴 first"]);
  view.unmount();

  show("/screens/unknown");
  await screen.findByText("없는 화면입니다");
  await userEvent.click(screen.getByRole("button", { name: "첫 화면으로" }));
  await screen.findByRole("heading", { name: "메뉴 second" });
});

it("re-evaluates the API first screen on a new visit rather than restoring a previous selection", async () => {
  const view = show("/screens/screen-second");
  await screen.findByRole("heading", { name: "메뉴 second" });
  view.unmount();
  const data = initial();
  data.data.data.reverse();
  serve(data);
  show();
  await screen.findByRole("heading", { name: "메뉴 first" });
  await screen.findByRole("button", { name: "메뉴 group", expanded: true });
  expect(
    within(screen.getByRole("navigation"))
      .getAllByRole("link")
      .map((link) => link.textContent),
  ).toEqual(["메뉴 first", "메뉴 second"]);
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

it("allows anonymous mobile menu navigation and collapse without new API requests or stored state", async () => {
  mobile = true;
  const user = userEvent.setup();
  show();
  await screen.findByRole("heading", { name: "메뉴 first" });
  expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "메뉴 펼치기" }));
  expect(screen.getByRole("navigation", { name: "업무 메뉴" })).toBeVisible();
  expect(
    screen.queryByRole("link", { name: "메뉴 pc" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "메뉴 pc-group" }),
  ).not.toBeInTheDocument();
  await user.click(screen.getByRole("link", { name: "메뉴 second" }));
  await screen.findByRole("heading", { name: "메뉴 second" });
  await user.click(screen.getByRole("button", { name: "메뉴 접기" }));
  expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "메뉴 펼치기" }));
  expect(screen.getByRole("link", { name: "메뉴 second" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

it("handles PC-only direct entry on mobile and restores the same target without fetching or redirecting", async () => {
  mobile = true;
  show("/screens/screen-pc");
  await screen.findByText("PC 전용 화면입니다");
  expect(
    screen.queryByRole("button", { name: "첫 화면으로" }),
  ).not.toBeInTheDocument();
  resize(false);
  await screen.findByRole("heading", { name: "메뉴 pc" });
  expect(screen.getByRole("link", { name: "메뉴 pc" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  expect(fetch).toHaveBeenCalledTimes(1);
});

it("keeps an unavailable PC-only address distinct from both unknown and width-only restrictions", async () => {
  const data = initial();
  data.meta.screenStates["disabled-screen"] = {
    available: false,
    pcOnlyYn: "Y",
    menuId: null,
    menuNm: null,
  };
  serve(data);
  mobile = true;
  show("/screens/disabled-screen");
  await screen.findByText("사용할 수 없는 화면입니다");
  expect(screen.queryByText("PC 전용 화면입니다")).not.toBeInTheDocument();
  expect(screen.queryByText("없는 화면입니다")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "첫 화면으로" }),
  ).not.toBeInTheDocument();
  resize(false);
  expect(screen.getByText("사용할 수 없는 화면입니다")).toBeInTheDocument();
});

it("highlights only the backend-resolved menu row when screen IDs are duplicated", async () => {
  const data = initial();
  const duplicate = item("duplicate", "group", "screen-first");
  duplicate.iconNm = "unknown-icon";
  data.data.data.push(duplicate);
  serve(data);
  show("/screens/screen-first");
  const chosen = await screen.findByRole("link", { name: "메뉴 first" });
  expect(chosen).toHaveAttribute("aria-current", "page");
  expect(
    screen.getByRole("link", { name: "메뉴 duplicate" }),
  ).not.toHaveAttribute("aria-current");
  expect(
    screen.getByRole("heading", { name: "메뉴 first" }),
  ).toBeInTheDocument();
});

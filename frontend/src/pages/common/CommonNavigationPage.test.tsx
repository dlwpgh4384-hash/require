import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import type {
  ShellMenuItem,
  ShellMenuResponse,
  ShellScreenAccess,
} from "../../app/NavigationRoot";
import { NavigationMenuIcon } from "../../app/NavigationMenuIcon";
import { CommonNavigationPage } from "./CommonNavigationPage";

const initialScreens = [
  "UI-16",
  "UI-15",
  "UI-02",
  "UI-01",
  "UI-31",
  "UI-12",
  "UI-05",
  "UI-11",
  "UI-28",
  "UI-17",
  "UI-29",
  "UI-33",
  "UI-13",
  "UI-03",
  "UI-04",
  "UI-19",
  "UI-32",
  "UI-10",
  "UI-07",
  "UI-08",
  "UI-09",
  "UI-18",
  "UI-21",
  "UI-14",
  "UI-26",
  "UI-30",
  "UI-23",
  "UI-24",
  "UI-25",
  "UI-27",
];
const pcScreens = ["UI-10", "UI-14", "UI-23", "UI-24", "UI-25", "UI-27"];
let mobile = false;
let mediaListeners: Set<() => void>;

beforeEach(() => {
  mobile = false;
  mediaListeners = new Set();
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      get matches() {
        return mobile;
      },
      addEventListener: (_event: string, callback: () => void) =>
        mediaListeners.add(callback),
      removeEventListener: (_event: string, callback: () => void) =>
        mediaListeners.delete(callback),
    })),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function width(isMobile: boolean) {
  act(() => {
    mobile = isMobile;
    mediaListeners.forEach((listener) => listener());
  });
}
function row(
  menuId: string,
  parent: string | null,
  scrnId: string | null,
  sortOrd: number,
  pcOnlyYn: "Y" | "N" = "N",
): ShellMenuItem {
  return {
    menuId,
    upperMenuId: parent,
    menuNm: `시험 메뉴 ${menuId}`,
    scrnId,
    menuLvl: 7,
    sortOrd,
    pcOnlyYn,
    iconNm: null,
  };
}
function response(
  data: ShellMenuItem[],
  access?: ShellScreenAccess[],
): ShellMenuResponse {
  return {
    success: true,
    data,
    meta: {
      screenAccess:
        access ??
        data
          .filter((item) => item.scrnId)
          .map((item) => ({
            scrnId: item.scrnId!,
            available: true,
            pcOnlyYn: item.pcOnlyYn,
          })),
    },
  };
}
function fixture(): ShellMenuResponse {
  return response([
    row("group", null, null, 1),
    ...initialScreens.map((id, index) =>
      row(
        `leaf-${String(index).padStart(2, "0")}`,
        "group",
        id,
        index + 1,
        pcScreens.includes(id) ? "Y" : "N",
      ),
    ),
  ]);
}
function respond(body: ShellMenuResponse) {
  const request = vi.fn(async (url: string, _init?: RequestInit) => ({
    ok: true,
    status: 200,
    json: async () =>
      url.startsWith("/api/common/users?")
        ? {
            success: true,
            meta: {},
            data: { items: [], page: 1, pageSize: 20, total: 0 },
          }
        : url === "/api/common/user-options"
          ? {
              success: true,
              meta: {},
              data: { roles: [], projects: [], userStates: [] },
            }
          : url === "/api/common/roles"
            ? { success: true, meta: {}, data: { items: [] } }
            : body,
  }));
  vi.stubGlobal("fetch", request);
  return request;
}
async function expectScreenContent(id: string) {
  if (id === "UI-23") {
    expect(
      await screen.findByText("조건에 맞는 결과가 없습니다."),
    ).toBeVisible();
    expect(screen.getByTestId("user-page")).toBeVisible();
    expect(screen.getByTestId("user-keyword-filter")).toBeVisible();
    expect(screen.getByTestId("list-search")).toBeVisible();
    expect(screen.getByTestId("user-register-open")).toBeVisible();
  } else if (id === "UI-24") {
    expect(await screen.findByText("활성 역할이 없습니다.")).toBeVisible();
    expect(screen.getByTestId("role-page")).toBeVisible();
  } else {
    expect(
      await screen.findByRole("region", { name: `${id} 본문` }),
    ).toHaveTextContent("준비 중");
  }
}
function Position() {
  return <div aria-label="주소">{useLocation().pathname}</div>;
}
function mount(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <CommonNavigationPage />
      <Position />
    </MemoryRouter>,
  );
}
function body() {
  return within(screen.getByRole("main"));
}
// 지금 화면이 속한 묶음은 자동으로 펼쳐진다(FR-0101). 닫혀 있을 때만 연다.
async function ensureOpen(name: string) {
  const group = await screen.findByRole("button", { name });
  if (group.getAttribute("aria-expanded") !== "true") fireEvent.click(group);
  return group;
}

describe("common placeholder screen contract", () => {
  it("each visible initial screen opens its placeholder or implemented user screen", async () => {
    respond(fixture());
    mount();
    await screen.findByRole("region", { name: "UI-16 본문" });
    await ensureOpen("시험 메뉴 group");
    for (const [index, id] of initialScreens.entries()) {
      fireEvent.click(
        screen.getByRole("link", {
          name: `시험 메뉴 leaf-${String(index).padStart(2, "0")}`,
        }),
      );
      // 탭이 8개면 가장 오래 안 본 탭을 닫고 연다(FR-0103).
      const limit = screen.queryByTestId("tab-limit-dialog");
      if (limit)
        fireEvent.click(
          within(limit).getByRole("button", { name: "닫고 열기" }),
        );
      await expectScreenContent(id);
      expect(screen.getByLabelText("주소")).toHaveTextContent(`/screens/${id}`);
    }
  });

  it("a PC-only duplicate representative cannot be replaced by another mobile link", async () => {
    mobile = true;
    respond(
      response(
        [row("first", null, "SAME", 1, "Y")],
        [{ scrnId: "SAME", available: true, pcOnlyYn: "Y" }],
      ),
    );
    mount("/screens/SAME");
    expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
    expect(screen.getByRole("link", { name: "대시보드로" })).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "시험 메뉴 first" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "시험 메뉴 later" }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/SAME");
  });

  it("a rejected envelope is an error, never a fabricated empty success", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ success: false, meta: {} }),
      }),
    );
    mount("/screens/UI-16");
    expect(
      await screen.findAllByRole("button", { name: "다시 시도" }),
    ).toHaveLength(2);
    expect(screen.queryByText("등록된 메뉴 없음")).not.toBeInTheDocument();
  });

  it.each(initialScreens)(
    "%s direct entry and recreation share exactly one shell and the registered screen",
    async (id) => {
      respond(fixture());
      const first = mount(`/screens/${id}`);
      await expectScreenContent(id);
      expect(screen.getAllByRole("banner")).toHaveLength(1);
      expect(screen.getAllByRole("complementary")).toHaveLength(1);
      expect(screen.getAllByRole("main")).toHaveLength(1);
      if (id !== "UI-23") {
        expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
        for (const name of ["조회", "저장", "다운로드", "업로드", "편집"])
          expect(
            screen.queryByRole("button", { name }),
          ).not.toBeInTheDocument();
      }
      first.unmount();
      mount(`/screens/${id}`);
      await expectScreenContent(id);
      expect(screen.getByLabelText("주소")).toHaveTextContent(`/screens/${id}`);
    },
  );

  it("accepts a database-added screen without a static registration list", async () => {
    respond(response([row("new", null, "DB-ADDED", 1)]));
    mount("/screens/DB-ADDED");
    expect(
      await screen.findByRole("region", { name: "DB-ADDED 본문" }),
    ).toHaveTextContent("준비 중");
  });

  it("opens the first visible leaf from the API rather than a pinned dashboard", async () => {
    mobile = true;
    respond(
      response([
        row("pc", null, "PC", 1, "Y"),
        row("b", null, "SECOND", 2),
        row("a", null, "FIRST", 2),
      ]),
    );
    mount();
    expect(
      await screen.findByRole("region", { name: "FIRST 본문" }),
    ).toHaveTextContent("준비 중");
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/FIRST");
  });

  it("initial menu fixture opens UI-16 without choosing a business dashboard", async () => {
    respond(fixture());
    mount();
    expect(
      await screen.findByRole("region", { name: "UI-16 본문" }),
    ).toHaveTextContent("준비 중");
  });

  it("chooses leaves in global sort order even when parent order differs", async () => {
    respond(
      response([
        row("first-group", null, null, 1),
        row("second-group", null, null, 2),
        row("late", "first-group", "LATE", 9),
        row("early", "second-group", "EARLY", 1),
      ]),
    );
    mount();
    expect(
      await screen.findByRole("region", { name: "EARLY 본문" }),
    ).toHaveTextContent("준비 중");
  });

  it("loading has two accessible statuses and never shows the target content prematurely", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
    mount("/screens/UI-16");
    expect(screen.getAllByRole("status")).toHaveLength(2);
    expect(
      body().queryByText("준비 중", { selector: "p" }),
    ).not.toBeInTheDocument();
  });

  it("HTTP500 shows two retries and retry re-evaluates the unchanged direct URL", async () => {
    const request = vi
      .fn()
      .mockResolvedValueOnce({ ok: false, status: 500 })
      .mockResolvedValueOnce({
        ok: true,
        json: async () =>
          response([], [{ scrnId: "UI-10", available: false, pcOnlyYn: "Y" }]),
      });
    vi.stubGlobal("fetch", request);
    mount("/screens/UI-10");
    expect(
      await screen.findAllByRole("button", { name: "다시 시도" }),
    ).toHaveLength(2);
    expect(screen.getAllByText("메뉴를 불러오지 못했습니다")).toHaveLength(2);
    expect(
      body().queryByText("준비 중", { selector: "p" }),
    ).not.toBeInTheDocument();
    fireEvent.click(body().getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-10");
    expect(request).toHaveBeenCalledTimes(2);
  });

  it("retry at the root chooses the first visible database screen after recovery", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockRejectedValueOnce(new Error("network"))
        .mockResolvedValueOnce({
          ok: true,
          json: async () => response([row("owned", null, "RECOVERED", 1)]),
        }),
    );
    mount();
    fireEvent.click(
      (await screen.findAllByRole("button", { name: "다시 시도" }))[0],
    );
    expect(
      await screen.findByRole("region", { name: "RECOVERED 본문" }),
    ).toHaveTextContent("준비 중");
    expect(screen.getByLabelText("주소")).toHaveTextContent(
      "/screens/RECOVERED",
    );
  });

  it("normal empty success is not a failed request", async () => {
    respond(response([]));
    mount();
    expect(await screen.findAllByText("등록된 메뉴 없음")).toHaveLength(2);
    expect(
      screen.queryByRole("button", { name: "다시 시도" }),
    ).not.toBeInTheDocument();
  });

  it("no visible screens after mobile filtering is an empty result", async () => {
    mobile = true;
    respond(
      response([row("group", null, null, 1), row("pc", "group", "PC", 1, "Y")]),
    );
    mount();
    // 메뉴는 있지만 휴대폰에서 열 수 있는 화면이 없다(FR-0002).
    expect(
      await screen.findAllByText("열 수 있는 화면이 없습니다"),
    ).toHaveLength(2);
    expect(
      screen.queryByRole("button", { name: "시험 메뉴 group" }),
    ).not.toBeInTheDocument();
  });

  it.each([
    {
      available: false,
      pc: "Y" as const,
      expected: "사용할 수 없는 화면입니다",
    },
    {
      available: false,
      pc: "N" as const,
      expected: "사용할 수 없는 화면입니다",
    },
    { available: true, pc: "Y" as const, expected: "PC에서 이용해 주세요" },
    { available: true, pc: "N" as const, expected: "준비 중" },
  ])(
    "direct route applies classification priority: $available / $pc",
    async ({ available, pc, expected }) => {
      mobile = true;
      respond(
        response(
          [row("other", null, "OTHER", 1)],
          [{ scrnId: "TARGET", available, pcOnlyYn: pc }],
        ),
      );
      mount("/screens/TARGET");
      expect(
        await body().findByText(expected, { selector: "p" }),
      ).toBeVisible();
      for (const text of [
        "없는 화면입니다",
        "사용할 수 없는 화면입니다",
        "PC에서 이용해 주세요",
        "준비 중",
      ]) {
        if (text !== expected)
          expect(
            body().queryByText(text, { selector: "p" }),
          ).not.toBeInTheDocument();
      }
      expect(screen.getByRole("complementary")).toBeInTheDocument();
      expect(screen.getByLabelText("주소")).toHaveTextContent(
        "/screens/TARGET",
      );
    },
  );

  it("unregistered route offers a first-screen link rather than treating filtered absence as hidden", async () => {
    respond(fixture());
    mount("/screens/UNKNOWN");
    expect(await screen.findByText("없는 화면입니다")).toBeVisible();
    fireEvent.click(screen.getByRole("link", { name: "첫 화면으로 가기" }));
    expect(
      await screen.findByRole("region", { name: "UI-16 본문" }),
    ).toHaveTextContent("준비 중");
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-16");
  });

  it.each(["empty", "mobile-only-hidden"])(
    "unregistered route keeps the return CTA with %s menus and shows empty state after clicking",
    async (condition) => {
      mobile = condition === "mobile-only-hidden";
      const request = respond(
        response(mobile ? [row("pc", null, "PC", 1, "Y")] : []),
      );
      mount("/screens/UNKNOWN");
      expect(await screen.findByText("없는 화면입니다")).toBeVisible();
      expect(screen.getByLabelText("주소")).toHaveTextContent(
        "/screens/UNKNOWN",
      );
      fireEvent.click(screen.getByRole("link", { name: "첫 화면으로 가기" }));
      await waitFor(() =>
        expect(screen.getByLabelText("주소").textContent).toBe("/"),
      );
      expect(
        body().getByText(
          mobile ? "열 수 있는 화면이 없습니다" : "등록된 메뉴 없음",
        ),
      ).toBeVisible();
      expect(body().queryByText("없는 화면입니다")).not.toBeInTheDocument();
      expect(
        body().queryByRole("button", { name: "다시 시도" }),
      ).not.toBeInTheDocument();
      expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
      expect(request).toHaveBeenCalledTimes(1);
    },
  );

  it("PC-only width roundtrip preserves URL, prunes the empty group and restores both", async () => {
    respond(
      response([
        row("group", null, null, 1),
        row("pc", "group", "UI-10", 2, "Y"),
      ]),
    );
    mount("/screens/UI-10");
    expect(
      await screen.findByRole("region", { name: "UI-10 본문" }),
    ).toHaveTextContent("준비 중");
    expect(
      screen.getByRole("button", { name: "시험 메뉴 group" }),
    ).toBeInTheDocument();
    width(true);
    expect(body().getByText("PC에서 이용해 주세요")).toBeVisible();
    expect(
      screen.queryByRole("button", { name: "시험 메뉴 group" }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-10");
    width(false);
    expect(
      screen.getByRole("region", { name: "UI-10 본문" }),
    ).toHaveTextContent("준비 중");
    expect(
      screen.getByRole("button", { name: "시험 메뉴 group" }),
    ).toBeInTheDocument();
  });

  it("all six PC-only seed screens disappear on mobile and return on desktop", async () => {
    respond(fixture());
    mount("/screens/UI-16");
    await screen.findByRole("region", { name: "UI-16 본문" });
    await ensureOpen("시험 메뉴 group");
    for (const id of pcScreens)
      expect(
        screen.getByRole("link", {
          name: `시험 메뉴 leaf-${String(initialScreens.indexOf(id)).padStart(2, "0")}`,
        }),
      ).toBeInTheDocument();
    width(true);
    fireEvent.click(screen.getByRole("button", { name: "메뉴 접기·펼치기" }));
    for (const id of pcScreens)
      expect(
        screen.queryByRole("link", {
          name: `시험 메뉴 leaf-${String(initialScreens.indexOf(id)).padStart(2, "0")}`,
        }),
      ).not.toBeInTheDocument();
    width(false);
    for (const id of pcScreens)
      expect(
        screen.getByRole("link", {
          name: `시험 메뉴 leaf-${String(initialScreens.indexOf(id)).padStart(2, "0")}`,
        }),
      ).toBeInTheDocument();
  });

  it("mixed menu toggles without navigating to its own screen", async () => {
    const data = [
      row("mixed", null, "MIXED", 1),
      row("child", "mixed", "CHILD", 2),
    ];
    respond(
      response(data, [
        { scrnId: "MIXED", available: false, pcOnlyYn: "N" },
        { scrnId: "CHILD", available: true, pcOnlyYn: "N" },
      ]),
    );
    mount("/screens/CHILD");
    await screen.findByRole("region", { name: "CHILD 본문" });
    const group = await ensureOpen("시험 메뉴 mixed");
    expect(screen.getByRole("link", { name: "시험 메뉴 child" })).toBeVisible();
    fireEvent.click(group);
    expect(
      screen.queryByRole("link", { name: "시험 메뉴 child" }),
    ).not.toBeInTheDocument();
    fireEvent.click(group);
    expect(screen.getByRole("link", { name: "시험 메뉴 child" })).toBeVisible();
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/CHILD");
  });

  it("mixed group cannot turn into a selectable leaf when its only child was removed server-side", async () => {
    respond(
      response(
        [row("mixed", null, "MIXED", 1)],
        [{ scrnId: "MIXED", available: false, pcOnlyYn: "N" }],
      ),
    );
    mount("/screens/MIXED");
    expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "시험 메뉴 mixed" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("열 수 있는 화면이 없습니다")).toBeInTheDocument();
  });

  it("without a preference store the four tool positions stay disabled and tabs replace the placeholder", async () => {
    respond(fixture());
    mount();
    await screen.findByRole("region", { name: "UI-16 본문" });
    expect(screen.getByTestId("screen-tab-list")).toBeInTheDocument();
    expect(screen.getByTestId("screen-actions")).toBeEmptyDOMElement();
    for (const name of ["즐겨찾기", "최근 본 화면", "단축키", "알림"]) {
      const control = screen.getByRole("button", { name: `${name} 준비 중` });
      expect(control).toBeDisabled();
      fireEvent.click(control);
      expect(control).toBeDisabled();
    }
    expect(
      screen.queryByRole("button", { name: /로그인|로그아웃|저장|등록/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("banner").querySelector("input")).toBeNull();
  });

  it("navigation and group state never write persistent storage or send write requests", async () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem");
    const requests = respond(fixture());
    mount();
    await screen.findByRole("region", { name: "UI-16 본문" });
    await ensureOpen("시험 메뉴 group");
    fireEvent.click(screen.getByRole("link", { name: "시험 메뉴 leaf-01" }));
    await waitFor(() =>
      expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-15"),
    );
    expect(setItem).not.toHaveBeenCalled();
    expect(requests).toHaveBeenCalledTimes(1);
    expect(requests.mock.calls[0][0]).toBe("/api/common/menus");
    expect(requests.mock.calls[0][1]).not.toHaveProperty("method");
    expect(requests.mock.calls[0][1]).not.toHaveProperty("body");
  });
});

describe("database icon mapping", () => {
  it("renders distinct inline known icons, neutral unknown icons and an empty null slot", () => {
    const { container, rerender } = render(<NavigationMenuIcon name="grid" />);
    const grid = container.querySelector("path")?.getAttribute("d");
    expect(container.querySelector("svg")).toBeInTheDocument();
    rerender(<NavigationMenuIcon name="db" />);
    expect(container.querySelector("path")?.getAttribute("d")).not.toBe(grid);
    rerender(<NavigationMenuIcon name="unknown-icon" />);
    expect(container.querySelector("svg")).toBeInTheDocument();
    rerender(<NavigationMenuIcon name={null} />);
    expect(container.querySelector("svg")).toBeNull();
    expect(container.querySelector(".menu-icon")).toBeInTheDocument();
  });
});

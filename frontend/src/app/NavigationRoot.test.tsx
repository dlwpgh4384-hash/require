import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { NavigationRoot, type ShellMenuResponse } from "./NavigationRoot";
import { CommonNavigationPage } from "../pages/common/CommonNavigationPage";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
});

function fixture(): ShellMenuResponse {
  return {
    success: true,
    data: [
      {
        menuId: "owned-group",
        upperMenuId: null,
        menuNm: "독립 그룹",
        scrnId: null,
        menuLvl: 7,
        sortOrd: 2,
        pcOnlyYn: "N",
        iconNm: "folder",
      },
      {
        menuId: "owned-screen",
        upperMenuId: "owned-group",
        menuNm: "독립 화면",
        scrnId: "ADDED",
        menuLvl: 9,
        sortOrd: 3,
        pcOnlyYn: "N",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [{ scrnId: "ADDED", available: true, pcOnlyYn: "N" }],
    },
  };
}
function Position() {
  return <div aria-label="주소">{useLocation().pathname}</div>;
}
function mount(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <NavigationRoot />
      <Position />
    </MemoryRouter>,
  );
}
function respond(body: ShellMenuResponse) {
  const request = vi
    .fn()
    .mockResolvedValue({ ok: true, json: async () => body });
  vi.stubGlobal("fetch", request);
  return request;
}

describe("shared navigation composition", () => {
  it("retains M019/G007 navigation for the implemented UI-07 screen", async () => {
    const body = fixture();
    body.data[0] = { ...body.data[0], menuId: "G007", menuNm: "손익" };
    body.data[1] = {
      ...body.data[1],
      menuId: "M019",
      upperMenuId: "G007",
      menuNm: "전체 수익 구조",
      scrnId: "UI-07",
    };
    body.meta.screenAccess = [
      { scrnId: "UI-07", available: true, pcOnlyYn: "N" },
    ];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        if (url === "/api/common/menus") {
          return { ok: true, json: async () => body };
        }
        const preference =
          /^\/api\/common\/user-preferences\/([^/]+)\/([^/]+)$/.exec(url);
        if (preference) {
          return {
            ok: true,
            json: async () => ({
              success: true,
              data: {
                prefTypeCd: preference[1],
                prefKey: preference[2],
                found: false,
                prefVal: null,
                modDttm: null,
              },
              meta: { periodBasis: null },
            }),
          };
        }
        throw new Error(`Unexpected request: ${url}`);
      }),
    );
    // 업무 화면 연결은 기본 틀이 아니라 실제 화면 조합이 담당한다.
    render(
      <MemoryRouter initialEntries={["/screens/UI-07"]}>
        <CommonNavigationPage />
        <Position />
      </MemoryRouter>,
    );
    expect(await screen.findByTestId("profit-structures-page")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "전체 수익 구조" }),
    ).toHaveAttribute("href", "/screens/UI-07");
    expect(
      screen.getByRole("navigation", { name: "현재 화면 경로" }),
    ).toHaveTextContent("손익");
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-07");
  });
  it("renders accessible loading in both menu and body", () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
    mount("/screens/ADDED");
    expect(screen.getAllByRole("status")).toHaveLength(2);
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/ADDED");
  });

  it("group toggles and navigation use only memory and read-only relative requests", async () => {
    const storage = vi.spyOn(Storage.prototype, "setItem");
    const requests = respond(fixture());
    mount();
    const group = await screen.findByRole("button", { name: "독립 그룹" });
    // 첫 주소(/)는 첫 화면으로 자동 이동하고 그 화면의 묶음을 자동으로 펼친다(FR-0101).
    // 자동 이동·펼침이 끝난 뒤에 눌러야 누르기 전 상태가 매번 같다(끝나기 전에 누르면 결과가 속도에 따라 달라짐).
    await waitFor(() =>
      expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/ADDED"),
    );
    await waitFor(() => expect(group).toHaveAttribute("aria-expanded", "true"));
    fireEvent.click(group);
    expect(group).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(group);
    expect(group).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(screen.getByRole("link", { name: "독립 화면" }));
    await waitFor(() =>
      expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/ADDED"),
    );
    expect(await screen.findByText("준비 중", { selector: "p" })).toBeVisible();
    fireEvent.click(group);
    expect(group).toHaveAttribute("aria-expanded", "false");
    expect(storage).not.toHaveBeenCalled();
    for (const call of requests.mock.calls) {
      // 메뉴와 메뉴 옆 미수 건수(SFR-001-04)만 읽는다.
      expect(["/api/common/menus", "/api/common/bills/unpaid-count"]).toContain(call[0]);
      expect(call[1]).not.toHaveProperty("body");
      expect(call[1]).not.toHaveProperty("method");
    }
  });

  it("recreation forgets manually closed groups and reopens only the current screen's group", async () => {
    respond(fixture());
    const first = mount("/screens/ADDED");
    const group = await screen.findByRole("button", { name: "독립 그룹" });
    await waitFor(() => expect(group).toHaveAttribute("aria-expanded", "true"));
    fireEvent.click(group);
    expect(group).toHaveAttribute("aria-expanded", "false");
    first.unmount();
    mount("/screens/ADDED");
    const again = await screen.findByRole("button", { name: "독립 그룹" });
    await waitFor(() => expect(again).toHaveAttribute("aria-expanded", "true"));
  });

  it("failed reads expose both retry controls and retain the original address", async () => {
    const request = vi
      .fn()
      .mockRejectedValueOnce(new Error("private-sql-detail"))
      .mockResolvedValueOnce({ ok: true, json: async () => fixture() });
    vi.stubGlobal("fetch", request);
    mount("/screens/ADDED");
    expect(
      await screen.findAllByRole("button", { name: "다시 시도" }),
    ).toHaveLength(2);
    expect(screen.queryByText("private-sql-detail")).not.toBeInTheDocument();
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/ADDED");
    fireEvent.click(screen.getAllByRole("button", { name: "다시 시도" })[0]);
    expect(await screen.findByText("준비 중", { selector: "p" })).toBeVisible();
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/ADDED");
  });

  it("normal empty menus are not presented as request failures", async () => {
    respond({ success: true, data: [], meta: { screenAccess: [] } });
    mount();
    expect(await screen.findAllByText("등록된 메뉴 없음")).toHaveLength(2);
    expect(
      screen.queryByRole("button", { name: "다시 시도" }),
    ).not.toBeInTheDocument();
  });

  it("shows the DB breadcrumb, a semantic page heading and active ancestor", async () => {
    respond(fixture());
    mount("/screens/ADDED");
    expect(
      await screen.findByRole("heading", { level: 1, name: "독립 화면" }),
    ).toBeVisible();
    expect(
      screen.getByRole("navigation", { name: "현재 화면 경로" }),
    ).toHaveTextContent("독립 그룹");
    expect(screen.getByRole("button", { name: "독립 그룹" })).toHaveClass(
      "has-active",
    );
    expect(screen.getByRole("banner")).not.toHaveTextContent("독립 화면");
  });

  it("closes the mobile overlay after selecting even the current route", async () => {
    vi.stubGlobal(
      "matchMedia",
      vi.fn(() => ({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    respond(fixture());
    mount("/screens/ADDED");
    const toggle = screen.getByRole("button", { name: "메뉴 접기·펼치기" });
    fireEvent.click(toggle);
    // 지금 화면이 속한 묶음은 자동으로 펼쳐져 있다(FR-0101).
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "독립 그룹" })).toHaveAttribute(
        "aria-expanded",
        "true",
      ),
    );
    fireEvent.click(screen.getByRole("link", { name: "독립 화면" }));
    await waitFor(() =>
      expect(toggle).toHaveAttribute("aria-expanded", "false"),
    );
    expect(
      screen.queryByRole("button", { name: "메뉴 닫기" }),
    ).not.toBeInTheDocument();
  });
});
describe("menu badge (SFR-001-04)", () => {
  function billing(count: number) {
    const body = fixture();
    body.data[1] = { ...body.data[1], menuNm: "청구·입금", scrnId: "UI-13" };
    body.meta.screenAccess = [{ scrnId: "UI-13", available: true, pcOnlyYn: "N" }];
    const request = vi.fn(async (url: string) =>
      url === "/api/common/bills/unpaid-count"
        ? { ok: true, json: async () => ({ success: true, meta: {}, data: { count } }) }
        : { ok: true, json: async () => body },
    );
    vi.stubGlobal("fetch", request);
    return request;
  }
  it("shows the unpaid bill count next to 청구·입금 after the menus load", async () => {
    const request = billing(10);
    mount("/screens/UI-13");
    const badge = await screen.findByTestId("menu-badge-UI-13");
    expect(badge).toHaveTextContent("10");
    expect(badge).toHaveAttribute("aria-label", "미수 10건");
    expect(screen.getByRole("link", { name: "청구·입금" })).toContainElement(badge);
    expect(request.mock.calls[0][0]).toBe("/api/common/menus");
  });
  it("hides the badge when nothing is unpaid", async () => {
    const request = billing(0);
    mount("/screens/UI-13");
    await screen.findByRole("link", { name: "청구·입금" });
    await waitFor(() =>
      expect(request.mock.calls.some(([url]) => url === "/api/common/bills/unpaid-count")).toBe(true),
    );
    expect(screen.queryByTestId("menu-badge-UI-13")).not.toBeInTheDocument();
  });
});

describe("55번 메뉴·화면 이동", () => {
  function twoGroups(): ShellMenuResponse {
    const item = (
      menuId: string,
      upperMenuId: string | null,
      menuNm: string,
      scrnId: string | null,
      sortOrd: number,
    ) => ({
      menuId,
      upperMenuId,
      menuNm,
      scrnId,
      menuLvl: upperMenuId ? 2 : 1,
      sortOrd,
      pcOnlyYn: "N" as const,
      iconNm: null,
    });
    return {
      success: true,
      data: [
        item("GA", null, "묶음 가", null, 1),
        item("MA", "GA", "가 화면", "UI-A", 1),
        item("GB", null, "묶음 나", null, 2),
        item("MB", "GB", "나 화면", "UI-B", 1),
      ],
      meta: {
        screenAccess: [
          { scrnId: "UI-A", available: true, pcOnlyYn: "N" },
          { scrnId: "UI-B", available: true, pcOnlyYn: "N" },
        ],
      },
    };
  }
  it("2-2: 대메뉴를 하나 열면 다른 대메뉴는 닫힌다(시안 openG 하나)", async () => {
    respond(twoGroups());
    mount("/screens/UI-A");
    const groupA = await screen.findByTestId("menu-toggle-ga");
    const groupB = screen.getByTestId("menu-toggle-gb");
    await waitFor(() => expect(groupA).toHaveAttribute("aria-expanded", "true"));
    expect(groupB).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(groupB);
    expect(groupB).toHaveAttribute("aria-expanded", "true");
    expect(groupA).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(groupB);
    expect(groupB).toHaveAttribute("aria-expanded", "false");
  });
  it("3-2: 화면을 옮기면 새 화면은 맨 위부터 열린다", async () => {
    respond(twoGroups());
    mount("/screens/UI-A");
    await screen.findByTestId("menu-toggle-ga");
    const main = screen.getByTestId("app-main");
    main.scrollTop = 400;
    fireEvent.click(screen.getByTestId("menu-toggle-gb"));
    fireEvent.click(screen.getByRole("link", { name: "나 화면" }));
    await waitFor(() =>
      expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-B"),
    );
    expect(main.scrollTop).toBe(0);
    expect(screen.getByTestId("menu-toggle-gb")).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(screen.getByTestId("menu-toggle-ga")).toHaveAttribute(
      "aria-expanded",
      "false",
    );
  });
});
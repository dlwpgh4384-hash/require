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
    fireEvent.click(group);
    expect(group).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(screen.getByRole("link", { name: "독립 화면" }));
    await waitFor(() =>
      expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/ADDED"),
    );
    expect(screen.getByText("준비 중", { selector: "p" })).toBeVisible();
    fireEvent.click(group);
    expect(group).toHaveAttribute("aria-expanded", "false");
    expect(storage).not.toHaveBeenCalled();
    for (const call of requests.mock.calls) {
      expect(call[0]).toBe("/api/common/menus");
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

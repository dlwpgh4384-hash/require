import { act, render } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { vi } from "vitest";
import App from "../src/App";
import type { MenuItem, MenuResponse } from "../src/services/menus";

// Explicit test fixtures only. Production menus continue to come from the API/DB.
export function menuResponse(): MenuResponse {
  const item = (
    menuId: string,
    scrnId: string | null,
    sortOrd: number,
    pcOnlyYn: "Y" | "N" = "N",
  ): MenuItem => ({
    menuId,
    scrnId,
    sortOrd,
    pcOnlyYn,
    upperMenuId: menuId === "group" ? null : "group",
    menuNm: `메뉴 ${menuId}`,
    menuLvl: menuId === "group" ? 1 : 2,
    iconNm: null,
  });
  const items = [
    item("group", null, 1),
    item("first", "screen-first", 1),
    item("second", "screen-second", 2),
    item("pc", "screen-pc", 3, "Y"),
  ];
  return {
    success: true,
    meta: {
      parentMenuIds: ["group"],
      screenStates: Object.fromEntries(
        items
          .filter((row) => row.scrnId)
          .map((row) => [
            row.scrnId!,
            {
              available: true,
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

export function serveMenus(body = menuResponse()) {
  const fetcher = vi
    .fn()
    .mockResolvedValue({ ok: true, json: async () => body });
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}

export function viewport(width = 1280) {
  let mobile = width < 768;
  const listeners = new Set<(event: MediaQueryListEvent) => void>();
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      get matches() {
        return mobile;
      },
      addEventListener: (
        _: string,
        listener: (event: MediaQueryListEvent) => void,
      ) => listeners.add(listener),
      removeEventListener: (
        _: string,
        listener: (event: MediaQueryListEvent) => void,
      ) => listeners.delete(listener),
    })),
  );
  return (nextWidth: number) => {
    mobile = nextWidth < 768;
    act(() =>
      listeners.forEach((listener) =>
        listener({ matches: mobile } as MediaQueryListEvent),
      ),
    );
  };
}

function CurrentRoute() {
  return <output aria-label="현재 주소">{useLocation().pathname}</output>;
}

export function showShell(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <CurrentRoute />
    </MemoryRouter>,
  );
}

import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter } from "react-router-dom";
import {
  NavigationRoot,
  type ShellMenuResponse,
} from "../../app/NavigationRoot";
import { requestMenuBadgeRefresh } from "../../app/menuBadges";

const badgePath = "/api/common/month-close-badge";
const unpaidPath = "/api/common/bills/unpaid-count";
let body: ShellMenuResponse;
let request: ReturnType<typeof vi.fn>;
let needsAttention: boolean;
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data }),
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
  needsAttention = true;
  body = {
    success: true,
    data: [
      {
        menuId: "G008",
        upperMenuId: null,
        menuNm: "마감",
        scrnId: null,
        menuLvl: 1,
        sortOrd: 1,
        pcOnlyYn: "N",
        iconNm: null,
      },
      {
        menuId: "M024",
        upperMenuId: "G008",
        menuNm: "월 마감",
        scrnId: "UI-14",
        menuLvl: 2,
        sortOrd: 1,
        pcOnlyYn: "Y",
        iconNm: null,
      },
    ],
    meta: {
      screenAccess: [{ scrnId: "UI-14", available: true, pcOnlyYn: "Y" }],
    },
  };
  request = vi.fn(async (url: string) => {
    if (url === "/api/common/menus")
      return { ok: true, status: 200, json: async () => structuredClone(body) };
    if (url === badgePath) return success({ needsAttention });
    if (url === unpaidPath) return success({ count: 7 });
    throw new Error(`Unexpected request: ${url}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
function mount() {
  return render(
    <MemoryRouter initialEntries={["/screens/UI-14"]}>
      <NavigationRoot />
    </MemoryRouter>,
  );
}
const badgeRequests = () =>
  request.mock.calls.filter((call) => call[0] === badgePath);

it("reads only after menu availability and shows the warning next to the existing month close menu", async () => {
  mount();
  const badge = await screen.findByTestId("menu-badge-UI-14");
  expect(badge).toHaveTextContent("!");
  expect(badge).toHaveClass("nb", "warn");
  expect(badge).toHaveAttribute("aria-label", "점검 필요");
  expect(screen.getByRole("link", { name: "월 마감" })).toContainElement(badge);
  expect(request.mock.calls[0][0]).toBe("/api/common/menus");
  expect(badgeRequests()).toHaveLength(1);
  expect(badgeRequests()[0][1]).not.toHaveProperty("body");
  expect(badgeRequests()[0][1]).not.toHaveProperty("method");
  expect(request.mock.calls.some((call) => call[0] === unpaidPath)).toBe(false);
});

it("makes no badge request while menus are pending", async () => {
  request.mockImplementation(() => new Promise(() => {}));
  mount();
  await act(async () => {});
  expect(badgeRequests()).toHaveLength(0);
});

it("makes no badge request if the month close menu is absent", async () => {
  body.data = [];
  mount();
  await screen.findAllByText("등록된 메뉴 없음");
  expect(badgeRequests()).toHaveLength(0);
});

it("makes no badge request if screen access marks the menu unavailable", async () => {
  body.meta.screenAccess[0].available = false;
  mount();
  await screen.findByTestId("navigation-state-unavailable");
  expect(badgeRequests()).toHaveLength(0);
});

it("makes no badge request for a child hidden behind an unreachable parent", async () => {
  body.data[1].upperMenuId = "not-returned";
  mount();
  await screen.findAllByText("열 수 있는 화면이 없습니다");
  expect(badgeRequests()).toHaveLength(0);
});

it("makes no badge request for PC-only UI-14 when the mobile menu excludes it", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  mount();
  await screen.findByTestId("pc-only-dashboard");
  expect(
    screen.queryByRole("link", { name: "월 마감" }),
  ).not.toBeInTheDocument();
  expect(badgeRequests()).toHaveLength(0);
});

it("hides the warning if the current month is closed", async () => {
  needsAttention = false;
  mount();
  await screen.findByRole("link", { name: "월 마감" });
  await waitFor(() => expect(badgeRequests()).toHaveLength(1));
  expect(screen.queryByTestId("menu-badge-UI-14")).not.toBeInTheDocument();
});

it("rereads after the owner's successful save signal and removes a resolved warning", async () => {
  mount();
  await screen.findByTestId("menu-badge-UI-14");
  needsAttention = false;
  act(() => requestMenuBadgeRefresh());
  await waitFor(() => expect(badgeRequests()).toHaveLength(2));
  await waitFor(() =>
    expect(screen.queryByTestId("menu-badge-UI-14")).not.toBeInTheDocument(),
  );
});

it("an independent failed warning read does not erase the existing unpaid count", async () => {
  body.data.push({
    ...body.data[1],
    menuId: "billing",
    scrnId: "UI-13",
    menuNm: "청구·입금",
    sortOrd: 2,
  });
  body.meta.screenAccess.push({
    scrnId: "UI-13",
    available: true,
    pcOnlyYn: "N",
  });
  const implementation = request.getMockImplementation()!;
  request.mockImplementation((url: string) =>
    url === badgePath
      ? Promise.reject(new Error("unavailable"))
      : implementation(url),
  );
  mount();
  expect(await screen.findByTestId("menu-badge-UI-13")).toHaveTextContent("7");
  expect(screen.queryByTestId("menu-badge-UI-14")).not.toBeInTheDocument();
});

it("ignores a stale warning response after a save-triggered refresh", async () => {
  let finish!: (value: ReturnType<typeof success>) => void;
  const implementation = request.getMockImplementation()!;
  let held = false;
  request.mockImplementation((url: string) => {
    if (url === badgePath && !held) {
      held = true;
      return new Promise((resolve) => {
        finish = resolve;
      });
    }
    return implementation(url);
  });
  mount();
  await waitFor(() => expect(badgeRequests()).toHaveLength(1));
  const oldSignal = badgeRequests()[0][1].signal;
  needsAttention = false;
  act(() => requestMenuBadgeRefresh());
  await waitFor(() => expect(badgeRequests()).toHaveLength(2));
  await act(async () => finish(success({ needsAttention: true })));
  expect(oldSignal.aborted).toBe(true);
  expect(screen.queryByTestId("menu-badge-UI-14")).not.toBeInTheDocument();
});

it("keeps the warning and unpaid count visible while a refresh is still pending", async () => {
  body.data.push({
    ...body.data[1],
    menuId: "billing",
    scrnId: "UI-13",
    menuNm: "청구·입금",
    sortOrd: 2,
  });
  body.meta.screenAccess.push({
    scrnId: "UI-13",
    available: true,
    pcOnlyYn: "N",
  });
  mount();
  await screen.findByTestId("menu-badge-UI-14");
  expect(await screen.findByTestId("menu-badge-UI-13")).toHaveTextContent("7");
  const implementation = request.getMockImplementation()!;
  request.mockImplementation((url: string) =>
    url === badgePath || url === unpaidPath
      ? new Promise(() => {})
      : implementation(url),
  );
  act(() => requestMenuBadgeRefresh());
  await waitFor(() => expect(badgeRequests()).toHaveLength(2));
  expect(screen.getByTestId("menu-badge-UI-14")).toBeInTheDocument();
  expect(screen.getByTestId("menu-badge-UI-13")).toHaveTextContent("7");
});

it("rejects a numeric badge or error envelope instead of displaying a made-up warning", async () => {
  const implementation = request.getMockImplementation()!;
  request.mockImplementation((url: string) =>
    url === badgePath
      ? Promise.resolve(success({ needsAttention: 1 }))
      : implementation(url),
  );
  mount();
  await screen.findByRole("link", { name: "월 마감" });
  await waitFor(() => expect(badgeRequests()).toHaveLength(1));
  expect(screen.queryByTestId("menu-badge-UI-14")).not.toBeInTheDocument();
});

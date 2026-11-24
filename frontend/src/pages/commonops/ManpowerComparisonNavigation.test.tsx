import { afterEach, expect, it, vi } from "vitest";
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
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { NavigationRoot } from "../../app/NavigationRoot";
import { findScreen } from "../common/screenRegistry";

const viewport = vi.hoisted(() => ({ mobile: false, tablet: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
let router: ReturnType<typeof createMemoryRouter> | undefined;
afterEach(() => {
  cleanup();
  router?.dispose();
  vi.unstubAllGlobals();
  viewport.mobile = false;
});

function mount(available = true) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: viewport.mobile,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  const menu = (
    menuId: string,
    scrnId: string,
    menuNm: string,
    sortOrd: number,
  ) => ({
    menuId,
    scrnId,
    menuNm,
    sortOrd,
    upperMenuId: null,
    menuLvl: 1,
    pcOnlyYn: "Y",
    iconNm: null,
  });
  const fetcher = vi.fn(async (url: string) => ({
    ok: true,
    status: 200,
    json: async () =>
      url === "/api/common/menus"
        ? {
            success: true,
            data: [
              menu("M008", "UI-11", "인원 계획 대비 투입", 1),
              menu("M-other", "OTHER", "다른 화면", 2),
            ],
            meta: {
              screenAccess: [
                { scrnId: "UI-11", available, pcOnlyYn: "Y" },
                { scrnId: "OTHER", available: true, pcOnlyYn: "Y" },
              ],
            },
          }
        : {
            success: true,
            meta: {},
            data:
              url === "/api/common/projects"
                ? { items: [] }
                : {
                    items: [],
                    totals: { plannedMm: 0, actualMm: 0, differenceMm: 0 },
                  },
          },
  }));
  vi.stubGlobal("fetch", fetcher);
  router = createMemoryRouter(
    [
      {
        path: "/screens/:scrnId",
        element: (
          <NavigationRoot
            tabs
            renderScreen={(id) => {
              const entry = findScreen(id);
              return entry ? (
                <entry.Page />
              ) : (
                <p data-testid="other-screen">다른 화면 본문</p>
              );
            }}
          />
        ),
      },
    ],
    { initialEntries: ["/screens/UI-11"] },
  );
  render(<RouterProvider router={router} />);
  return fetcher;
}

it("registers only the comparison body without borrowed title actions and isolates two kept-mounted tabs", async () => {
  mount();
  await screen.findByTestId("list-empty");
  expect(findScreen("UI-11")?.TitleActions).toBeUndefined();
  expect(findScreen("UI-17")).toBeUndefined();
  expect(findScreen("UI-28")?.rootTestId).toBe("manpower-board-page");
  expect(findScreen("UI-30")).toBeUndefined();
  await act(async () => {
    await router!.navigate("/screens/OTHER");
  });
  await screen.findByTestId("other-screen");
  expect(screen.getByTestId("screen-panel-ui-11")).not.toBeVisible();
  expect(screen.getByTestId("screen-tab-ui-11")).toBeInTheDocument();
  expect(screen.getByTestId("screen-tab-other")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("screen-tab-select-ui-11"));
  await waitFor(() =>
    expect(screen.getByTestId("screen-panel-ui-11")).toBeVisible(),
  );
  expect(
    within(screen.getByTestId("screen-panel-ui-11")).getAllByRole("button", {
      name: "조회",
    }),
  ).toHaveLength(1);
  expect(screen.queryByTestId("project-create")).not.toBeInTheDocument();
  expect(screen.queryByTestId("assignment-create")).not.toBeInTheDocument();
  expect(screen.queryByTestId("withdrawal-download")).not.toBeInTheDocument();
});

it("preserves unavailable-screen restriction instead of mounting comparison or querying it", async () => {
  const request = mount(false);
  await waitFor(() => expect(request).toHaveBeenCalled());
  expect(await screen.findByText("사용할 수 없는 화면입니다")).toBeVisible();
  expect(
    screen.queryByTestId("manpower-comparison-page"),
  ).not.toBeInTheDocument();
  expect(
    request.mock.calls.some(([url]) =>
      url.startsWith("/api/common/manpower-comparisons"),
    ),
  ).toBe(false);
});

it("preserves the existing PC-only restriction on mobile", async () => {
  viewport.mobile = true;
  const request = mount();
  expect(await screen.findByText("PC에서 이용해 주세요")).toBeVisible();
  expect(
    screen.queryByTestId("manpower-comparison-page"),
  ).not.toBeInTheDocument();
  expect(
    request.mock.calls.some(([url]) =>
      url.startsWith("/api/common/manpower-comparisons"),
    ),
  ).toBe(false);
});

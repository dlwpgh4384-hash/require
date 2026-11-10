import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { menuResponse, serveMenus, showShell, viewport } from "../shellHarness";

beforeEach(() => {
  viewport();
  serveMenus();
});
afterEach(() => vi.unstubAllGlobals());

test("anonymous navigation uses API order and keeps one shell without persisting selection", async () => {
  const body = menuResponse();
  body.data.data.find((row) => row.menuId === "second")!.sortOrd = 0;
  const fetcher = serveMenus(body);
  showShell();
  await screen.findByRole("heading", { name: "메뉴 second" });
  expect(screen.getByLabelText("현재 주소")).toHaveTextContent(
    "/screens/screen-second",
  );
  // The selected screen renders before its ancestry-opening effect completes.
  await userEvent.click(
    await screen.findByRole("link", { name: "메뉴 first" }),
  );
  await screen.findByRole("heading", { name: "메뉴 first" });
  expect(screen.getByRole("link", { name: "메뉴 first" })).toHaveAttribute(
    "aria-current",
    "page",
  );
  expect(screen.getAllByRole("banner")).toHaveLength(1);
  expect(screen.getAllByRole("navigation")).toHaveLength(1);
  expect(screen.getAllByRole("main")).toHaveLength(1);
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /로그인|저장|삭제/ }),
  ).not.toBeInTheDocument();
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(fetcher).toHaveBeenCalledWith(
    "/api/common/menus",
    expect.objectContaining({ signal: expect.any(AbortSignal) }),
  );
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

test("direct entry and a fresh mount preserve the requested screen", async () => {
  const view = showShell("/screens/screen-second");
  await screen.findByRole("heading", { name: "메뉴 second" });
  view.unmount();
  showShell("/screens/screen-second");
  await screen.findByRole("heading", { name: "메뉴 second" });
  expect(within(screen.getByRole("main")).getByText("준비 중")).toBeVisible();
  expect(screen.getByLabelText("현재 주소")).toHaveTextContent(
    "/screens/screen-second",
  );
});

test("unknown addresses stay put until explicit first-screen recovery", async () => {
  showShell("/screens/unregistered");
  await screen.findByText("없는 화면입니다");
  expect(screen.getByLabelText("현재 주소")).toHaveTextContent(
    "/screens/unregistered",
  );
  await userEvent.click(screen.getByRole("button", { name: "첫 화면으로" }));
  await screen.findByRole("heading", { name: "메뉴 first" });
  expect(screen.getByLabelText("현재 주소")).toHaveTextContent(
    "/screens/screen-first",
  );
});

test("PC-only selection is hidden on mobile and restored without redirect or refetch", async () => {
  const resize = viewport();
  showShell("/screens/screen-pc");
  await screen.findByRole("heading", { name: "메뉴 pc" });
  resize(390);
  expect(screen.getByText("PC 전용 화면입니다")).toBeVisible();
  await userEvent.click(screen.getByRole("button", { name: "메뉴 펼치기" }));
  expect(
    screen.queryByRole("link", { name: "메뉴 pc" }),
  ).not.toBeInTheDocument();
  expect(await screen.findByRole("link", { name: "메뉴 first" })).toBeVisible();
  resize(1280);
  await screen.findByRole("heading", { name: "메뉴 pc" });
  expect(screen.getByLabelText("현재 주소")).toHaveTextContent(
    "/screens/screen-pc",
  );
  expect(fetch).toHaveBeenCalledTimes(1);
});

import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { menuResponse, serveMenus, showShell, viewport } from "../shellHarness";

afterEach(() => vi.unstubAllGlobals());

for (const width of [320, 390, 768, 1280]) {
  for (const path of [
    "/",
    "/screens/screen-second",
    "/screens/unregistered-preview",
  ]) {
    test(`shell and retry recover from menu failure at ${width}px on ${path}`, async () => {
      viewport(width);
      const fetcher = vi
        .fn()
        .mockRejectedValueOnce(new Error("private-connection-details"))
        .mockResolvedValue({ ok: true, json: async () => menuResponse() });
      vi.stubGlobal("fetch", fetcher);
      showShell(path);
      await screen.findByRole("heading", {
        name: "메뉴를 확인한 뒤 화면을 표시합니다",
      });
      if (width < 768)
        await userEvent.click(
          screen.getByRole("button", { name: "메뉴 펼치기" }),
        );
      expect(screen.getByRole("alert")).toHaveTextContent("메뉴 조회 오류");
      expect(
        screen.queryByText("private-connection-details"),
      ).not.toBeInTheDocument();
      expect(screen.getByLabelText("현재 주소")).toHaveTextContent(path);
      for (const label of [
        "즐겨찾기",
        "최근 본 화면",
        "화면 탭",
        "상단 도구줄",
        "단축키",
        "알림",
      ]) {
        expect(
          screen.getByRole("button", { name: `${label}: 준비 중` }),
        ).toBeDisabled();
      }
      await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
      if (path === "/screens/unregistered-preview") {
        await screen.findByText("없는 화면입니다");
        expect(screen.getByLabelText("현재 주소")).toHaveTextContent(path);
      } else {
        await screen.findByRole("heading", {
          name: path === "/" ? "메뉴 first" : "메뉴 second",
        });
      }
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(fetcher).toHaveBeenCalledTimes(2);
      expect(screen.getAllByRole("banner")).toHaveLength(1);
      expect(screen.getAllByRole("main")).toHaveLength(1);
      expect(screen.getAllByRole("navigation")).toHaveLength(1);
    });
  }
}

test("loading is not mistaken for an unknown screen or successful content", () => {
  viewport();
  vi.stubGlobal(
    "fetch",
    vi.fn(() => new Promise(() => undefined)),
  );
  showShell("/screens/unregistered-preview");
  expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
  expect(
    within(screen.getByRole("main")).getByText("메뉴를 불러오는 중입니다"),
  ).toBeVisible();
  expect(screen.queryByText("없는 화면입니다")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "다시 시도" }),
  ).not.toBeInTheDocument();
});

test("successful empty menus are distinct from request failures", async () => {
  viewport();
  serveMenus({
    success: true,
    meta: { parentMenuIds: [], screenStates: {} },
    data: { success: true, meta: {}, data: [] },
  });
  showShell();
  await screen.findByText("등록된 메뉴 없음");
  expect(screen.getByText("표시할 화면이 없습니다")).toBeVisible();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

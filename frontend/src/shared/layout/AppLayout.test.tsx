import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { AppLayout } from "./AppLayout";

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

describe("AppLayout", () => {
  it("renders one shared shell with caller-owned title actions and body", () => {
    render(
      <AppLayout
        title="계약 화면"
        actions={<button>화면 명령 자리</button>}
        sidebar={<span>메뉴 영역</span>}
      >
        <p>본문 영역</p>
      </AppLayout>,
    );
    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("complementary")).toHaveLength(1);
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(
      screen.getByRole("button", { name: "화면 명령 자리" }),
    ).toBeVisible();
    expect(screen.getByText("본문 영역")).toBeVisible();
    expect(screen.getByText("메뉴 영역")).toBeVisible();
  });

  it("all six future tool positions are actually disabled", () => {
    render(<AppLayout />);
    for (const name of [
      "즐겨찾기",
      "최근 본 화면",
      "화면 탭",
      "상단 도구줄",
      "단축키",
      "알림",
    ]) {
      const tool = screen.getByRole("button", { name: `${name} 준비 중` });
      expect(tool).toBeDisabled();
      fireEvent.click(tool);
      expect(tool).toBeDisabled();
    }
  });

  it("desktop collapse and expansion do not write persistent preferences", () => {
    const store = vi.spyOn(Storage.prototype, "setItem");
    render(<AppLayout />);
    const toggle = screen.getByRole("button", { name: "메뉴 접기·펼치기" });
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByRole("complementary")).toHaveClass("is-collapsed");
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(store).not.toHaveBeenCalled();
  });

  it("mobile starts closed and supports toggle scrim and Escape without a browser engine", () => {
    vi.stubGlobal(
      "matchMedia",
      vi.fn(() => ({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    render(<AppLayout />);
    const toggle = screen.getByRole("button", { name: "메뉴 접기·펼치기" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    fireEvent.click(screen.getByRole("button", { name: "메뉴 닫기" }));
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(toggle);
    fireEvent.keyDown(window, { key: "Escape" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
  });

  it("new mounting discards the preceding expanded preference", () => {
    const first = render(<AppLayout />);
    fireEvent.click(screen.getByRole("button", { name: "메뉴 접기·펼치기" }));
    first.unmount();
    render(<AppLayout />);
    expect(
      screen.getByRole("button", { name: "메뉴 접기·펼치기" }),
    ).toHaveAttribute("aria-expanded", "true");
  });
});

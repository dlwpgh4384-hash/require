import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { useRef } from "react";
import { usePreferenceBusinessShortcuts } from "./UserPreferenceTools";

afterEach(() => cleanup());

// 00-1 코드 리뷰(2026-11-23): 공통 단축키 부품
function Screen({
  hidden,
  onSave,
  onSearch,
}: {
  hidden: boolean;
  onSave: () => void;
  onSearch?: () => void;
}) {
  const root = useRef<HTMLDivElement>(null);
  usePreferenceBusinessShortcuts({ scope: root, onSave, onSearch });
  return (
    <div hidden={hidden}>
      <div ref={root}>
        <input aria-label={hidden ? "숨은 칸" : "보이는 칸"} />
        <button type="button">{hidden ? "숨은 단추" : "보이는 단추"}</button>
      </div>
    </div>
  );
}

describe("common business shortcuts", () => {
  it("a hidden tab does not swallow Ctrl+S meant for the visible tab", () => {
    const hiddenSave = vi.fn();
    const visibleSave = vi.fn();
    render(
      <>
        <Screen hidden onSave={hiddenSave} />
        <Screen hidden={false} onSave={visibleSave} />
      </>,
    );
    fireEvent.keyDown(window, { key: "s", ctrlKey: true });
    expect(hiddenSave).not.toHaveBeenCalled();
    expect(visibleSave).toHaveBeenCalledTimes(1);
  });

  it("saves with Ctrl+S or Cmd+S even while typing in an input", () => {
    const save = vi.fn();
    render(<Screen hidden={false} onSave={save} />);
    fireEvent.keyDown(screen.getByLabelText("보이는 칸"), { key: "s", ctrlKey: true });
    fireEvent.keyDown(screen.getByLabelText("보이는 칸"), { key: "s", metaKey: true });
    expect(save).toHaveBeenCalledTimes(2);
  });

  it("Enter on a button is left to the button and does not run the search", () => {
    const search = vi.fn();
    render(<Screen hidden={false} onSave={vi.fn()} onSearch={search} />);
    fireEvent.keyDown(screen.getByRole("button", { name: "보이는 단추" }), { key: "Enter" });
    expect(search).not.toHaveBeenCalled();
    fireEvent.keyDown(window, { key: "Enter" });
    expect(search).toHaveBeenCalledTimes(1);
  });
});

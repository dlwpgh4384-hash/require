import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { AreaSelect, areaLevelLocked, useAreaPicker } from "./AreaPicker";

// 행정구역 목록(시·도 → 시·군·구 → 동). 세종(3600000000)은 시·군·구 없이 바로 동이다.
const areas: Record<string, { areaCode: string; areaName: string }[]> = {
  "": [
    { areaCode: "1100000000", areaName: "서울" },
    { areaCode: "3600000000", areaName: "세종" },
  ],
  "1100000000": [{ areaCode: "1114000000", areaName: "중구" }],
  "1114000000": [{ areaCode: "1114010000", areaName: "을지로동" }],
  "3600000000": [{ areaCode: "3611010100", areaName: "반곡동" }],
};
let failRoot = false;
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  failRoot = false;
  request = vi.fn(async (url: string) => {
    const [, query = ""] = url.split("?");
    const parent = new URLSearchParams(query).get("parentAreaCode") ?? "";
    if (failRoot && !parent)
      return { ok: false, status: 500, json: async () => ({ success: false, message: "목록 실패" }) };
    return { ok: true, status: 200, json: async () => ({ success: true, data: { items: areas[parent] ?? [] } }) };
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

let chosen: (string | null)[] = [];
let picker: ReturnType<typeof useAreaPicker> | null = null;
function Harness({ code, levels = 3 }: { code?: string | null; levels?: 2 | 3 }) {
  const area = useAreaPicker(code, levels);
  picker = area;
  return (
    <div>
      {Array.from({ length: levels }, (_, level) => (
        <AreaSelect
          key={level}
          picker={area}
          level={level}
          id={`area-${level}`}
          testId={`area-${level}`}
          disabled={area.busy || areaLevelLocked(area, level)}
          onPick={(l, value) => chosen.push(area.select(l, value))}
        />
      ))}
      <span data-testid="error">{area.error}</span>
    </div>
  );
}
beforeEach(() => {
  chosen = [];
  picker = null;
});

it("refills all three levels from a stored dong code", async () => {
  render(<Harness code="1114010000" />);
  await waitFor(() => expect(screen.getByTestId("area-2")).toHaveValue("1114010000"));
  expect(screen.getByTestId("area-0")).toHaveValue("1100000000");
  expect(screen.getByTestId("area-1")).toHaveValue("1114000000");
});
it("select returns the last chosen level and clears the levels below", async () => {
  render(<Harness />);
  await waitFor(() => expect(screen.getByTestId("area-0")).toBeEnabled());
  expect(screen.getByTestId("area-1")).toBeDisabled();
  fireEvent.change(screen.getByTestId("area-0"), { target: { value: "1100000000" } });
  await waitFor(() => expect(screen.getByTestId("area-1")).toBeEnabled());
  fireEvent.change(screen.getByTestId("area-1"), { target: { value: "1114000000" } });
  await waitFor(() => expect(screen.getByTestId("area-2")).toBeEnabled());
  fireEvent.change(screen.getByTestId("area-2"), { target: { value: "1114010000" } });
  fireEvent.change(screen.getByTestId("area-1"), { target: { value: "" } });
  expect(chosen).toEqual(["1100000000", "1114000000", "1114010000", "1100000000"]);
  expect(screen.getByTestId("area-2")).toHaveValue("");
  expect(screen.getByTestId("area-2")).toBeDisabled();
});
it("a 시·도 without 시·군·구 (세종) skips straight to the dong list", async () => {
  render(<Harness />);
  await waitFor(() => expect(screen.getByTestId("area-0")).toBeEnabled());
  fireEvent.change(screen.getByTestId("area-0"), { target: { value: "3600000000" } });
  await waitFor(() => expect(screen.getByTestId("area-2")).toBeEnabled());
  expect(screen.getByTestId("area-1")).toBeDisabled();
  expect(screen.getByTestId("area-2")).toHaveTextContent("반곡동");
});
it("two-level mode stops at 시·군·구 and show() refills without re-reading the 시·도 list", async () => {
  render(<Harness levels={2} />);
  await waitFor(() => expect(screen.getByTestId("area-0")).toBeEnabled());
  expect(screen.queryByTestId("area-2")).toBeNull();
  const rootCalls = () => request.mock.calls.filter((c) => !String(c[0]).includes("?")).length;
  expect(rootCalls()).toBe(1);
  act(() => picker!.show("1114000000"));
  await waitFor(() => expect(screen.getByTestId("area-1")).toHaveValue("1114000000"));
  expect(screen.getByTestId("area-0")).toHaveValue("1100000000");
  expect(rootCalls()).toBe(1);
});
it("a failed list shows the message and retry reads it again with the given code", async () => {
  failRoot = true;
  render(<Harness />);
  await waitFor(() => expect(screen.getByTestId("error")).toHaveTextContent("목록 실패"));
  failRoot = false;
  act(() => picker!.retry("1114000000"));
  await waitFor(() => expect(screen.getByTestId("area-1")).toHaveValue("1114000000"));
  expect(screen.getByTestId("error")).toHaveTextContent("");
});

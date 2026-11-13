import { afterEach, beforeEach, expect, it, vi } from "vitest";
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
import { CodeHistoryPanel } from "./CodeHistoryPanel";
import type { CodeHistoryItem } from "./codeHistory";

let rows: CodeHistoryItem[];
let request: ReturnType<typeof vi.fn>;
const success = (items: CodeHistoryItem[]) => ({
  ok: true,
  status: 200,
  json: async () => ({
    success: true,
    meta: {},
    data: { items: structuredClone(items) },
  }),
});
beforeEach(() => {
  rows = [
    {
      changedAt: "2026-11-13T00:03:00Z",
      kind: "표기 대응",
      item: "엑셀 표기 대응",
      before: "옛 표기",
      after: "새 표기",
      processor: "admin",
    },
    {
      changedAt: "2026-11-13T00:02:00Z",
      kind: "운영 기준값",
      item: "STD_VAL",
      before: "40.0000",
      after: "45.0000",
      processor: "admin",
    },
    {
      changedAt: "2026-11-13T00:01:00Z",
      kind: "코드",
      item: "CODE_NM",
      before: "옛 코드",
      after: "새 코드",
      processor: "admin",
    },
    {
      changedAt: "2026-11-13T00:00:00Z",
      kind: "그룹",
      item: "CODE_GRP_NM",
      before: "",
      after: "새 그룹",
      processor: "admin",
    },
  ];
  request = vi.fn(async () => success(rows));
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("shows loading until the API returns", async () => {
  let finish!: (value: ReturnType<typeof success>) => void;
  request.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  render(<CodeHistoryPanel />);
  expect(screen.getByTestId("code-history-loading")).toHaveTextContent(
    "불러오는 중",
  );
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  await act(async () => finish(success(rows)));
  expect(await screen.findByRole("table")).toBeInTheDocument();
});

it("renders all six fields in API order with Korean time and the stored processor", async () => {
  render(<CodeHistoryPanel />);
  const table = await screen.findByRole("table", {
    name: "공통코드 변경 이력",
  });
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["일시", "구분", "항목", "변경 전", "변경 후", "처리자"]);
  expect(screen.getByTestId("code-history-row-0")).toHaveTextContent(
    "2026-11-13 09:03표기 대응엑셀 표기 대응옛 표기새 표기admin",
  );
  expect(screen.getByTestId("code-history-row-1")).toHaveTextContent(
    "40.000045.0000",
  );
  expect(screen.getByTestId("code-history-row-3")).toHaveTextContent(
    "그룹CODE_GRP_NM–새 그룹admin",
  );
  expect(request).toHaveBeenCalledWith("/api/common/code-change-history", {
    signal: expect.any(AbortSignal),
  });
});

it.each(["코드", "그룹", "표기 대응", "운영 기준값"])(
  "filters %s locally without another API request",
  async (kind) => {
    render(<CodeHistoryPanel />);
    await screen.findByRole("table");
    fireEvent.click(screen.getByRole("button", { name: kind }));
    expect(screen.getAllByTestId(/^code-history-row-/)).toHaveLength(1);
    expect(screen.getByTestId("code-history-row-0")).toHaveTextContent(kind);
    expect(screen.getByRole("button", { name: kind })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    fireEvent.click(screen.getByRole("button", { name: "전체" }));
    expect(screen.getAllByTestId(/^code-history-row-/)).toHaveLength(4);
    expect(request).toHaveBeenCalledTimes(1);
  },
);

it("shows the empty state for an empty history", async () => {
  rows = [];
  render(<CodeHistoryPanel />);
  expect(await screen.findByTestId("code-history-empty")).toHaveTextContent(
    "기록이 없습니다.",
  );
  // 시안: 빈 이력도 표 머리 아래 한 줄로 보인다.
  expect(screen.getAllByRole("row")).toHaveLength(2);
});

it("shows an empty filtered list without discarding other history", async () => {
  rows = rows.filter((row) => row.kind === "그룹");
  render(<CodeHistoryPanel />);
  await screen.findByRole("table");
  fireEvent.click(screen.getByRole("button", { name: "코드" }));
  expect(screen.getByTestId("code-history-empty")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "전체" }));
  expect(screen.getAllByTestId(/^code-history-row-/)).toHaveLength(1);
});

it("shows request failure rather than empty success and retries the same endpoint", async () => {
  request.mockRejectedValueOnce(new Error("연결하지 못했습니다."));
  render(<CodeHistoryPanel />);
  expect(await screen.findByTestId("code-history-error")).toHaveTextContent(
    "연결하지 못했습니다.",
  );
  expect(screen.queryByTestId("code-history-empty")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "재시도" }));
  await screen.findByRole("table");
  expect(request).toHaveBeenCalledTimes(2);
});

it("is readonly under fixed admin and has no save, cancel, delete or export controls", async () => {
  render(<CodeHistoryPanel />);
  await screen.findByRole("table");
  expect(
    screen.queryByRole("button", { name: /저장|취소|삭제|다운로드/ }),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(screen.getAllByRole("button")).toHaveLength(5);
  expect(
    request.mock.calls.every((call) => !(call[1] as RequestInit).method),
  ).toBe(true);
});

it("refreshes after a workspace mutation and keeps the local filter", async () => {
  const view = render(<CodeHistoryPanel refreshKey={0} />);
  await screen.findByRole("table");
  fireEvent.click(screen.getByRole("button", { name: "코드" }));
  rows = [{ ...rows[2], after: "다시 바꾼 코드" }];
  view.rerender(<CodeHistoryPanel refreshKey={1} />);
  expect(await screen.findByText("다시 바꾼 코드")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "코드" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(request).toHaveBeenCalledTimes(2);
});

it("ignores an aborted stale response after refresh", async () => {
  let finish!: (value: ReturnType<typeof success>) => void;
  const stale = [{ ...rows[0], after: "지난 응답" }];
  request.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const view = render(<CodeHistoryPanel refreshKey={0} />);
  const oldSignal = request.mock.calls[0][1].signal as AbortSignal;
  view.rerender(<CodeHistoryPanel refreshKey={1} />);
  await screen.findByText("새 표기");
  expect(oldSignal.aborted).toBe(true);
  await act(async () => finish(success(stale)));
  expect(screen.queryByText("지난 응답")).not.toBeInTheDocument();
  expect(screen.getByText("새 표기")).toBeInTheDocument();
});

it("does not render historic text as HTML and aborts on unmount", async () => {
  rows[0].after = "<img src=x onerror=alert(1)>";
  const view = render(<CodeHistoryPanel />);
  await screen.findByText("<img src=x onerror=alert(1)>");
  expect(screen.queryByRole("img")).not.toBeInTheDocument();
  const signal = request.mock.calls[0][1].signal as AbortSignal;
  view.unmount();
  await waitFor(() => expect(signal.aborted).toBe(true));
});

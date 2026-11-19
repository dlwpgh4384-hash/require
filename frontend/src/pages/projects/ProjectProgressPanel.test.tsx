import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { ProjectProgressPanel } from "./ProjectProgressPanel";
import type { BoardMemo, BoardStep } from "./projectBoardDetails";

let request: ReturnType<typeof vi.fn>;
let memos: BoardMemo[];
let steps: BoardStep[];
const changed = vi.fn();
const props = {
  projectId: "P-0001",
  contractTypeCode: "TK",
  statusCode: "ING",
  endDate: "2027-12-31",
  onChanged: changed,
};
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data) }),
});
const writes = () =>
  request.mock.calls.filter((c) =>
    ["POST", "PUT"].includes((c[1] as RequestInit)?.method ?? "GET"),
  );
beforeEach(() => {
  changed.mockReset();
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
  memos = [
    {
      memoId: "1",
      projectId: "P-0001",
      workDone: "기존 작업",
      nextWork: null,
      requests: "기존 요청",
      actualProgress: 10,
      createdAt: "2026-10-01T00:00:00Z",
      createdBy: "other",
      updatedAt: null,
      updatedBy: null,
    },
  ];
  steps = [
    {
      stepId: "1",
      projectId: "P-0001",
      stepName: "착수",
      weight: 5,
      plannedStartDate: "2026-11-01",
      plannedEndDate: "2026-11-15",
      completedDate: null,
    },
    {
      stepId: "2",
      projectId: "P-0001",
      stepName: "분석",
      weight: 10,
      plannedStartDate: "2026-11-16",
      plannedEndDate: "2027-12-31",
      completedDate: null,
    },
  ];
  request = vi.fn(async (path: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    if (method === "GET")
      return ok({ items: path.endsWith("weekly-memos") ? memos : steps });
    const body = JSON.parse(String(init?.body ?? "{}"));
    if (path.endsWith("/complete"))
      steps[0] = { ...steps[0], completedDate: "2026-11-15" };
    else if (path.endsWith("/steps"))
      steps = steps.map((s) => ({
        ...s,
        ...body.items.find((i: BoardStep) => i.stepId === s.stepId),
      }));
    else if (path.includes("weekly-memos")) {
      if (method === "POST") memos.push({ ...memos[0], ...body, memoId: "2" });
      else memos[0] = { ...memos[0], ...body, updatedBy: "admin" };
    }
    return ok({});
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
async function ready(over = {}) {
  render(<ProjectProgressPanel {...props} {...over} />);
  await screen.findByText("기존 작업");
}

it("registers a memo once without confirmation and reloads before onChanged", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("project-memo-add"));
  fireEvent.change(screen.getByLabelText("요청 사항"), {
    target: { value: "새 요청" },
  });
  fireEvent.click(screen.getByTestId("project-progress-save"));
  await waitFor(() => expect(changed).toHaveBeenCalledWith("저장했습니다."));
  expect(writes()).toHaveLength(1);
  expect(writes()[0][0]).toBe("/api/common/projects/P-0001/weekly-memos");
  expect(JSON.parse(writes()[0][1].body)).toEqual({
    workDone: null,
    nextWork: null,
    requests: "새 요청",
    actualProgress: null,
  });
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByText("새 요청")).toBeInTheDocument();
});
it("edits the selected memo using PUT without identity in its body", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("project-memo-edit-1"));
  fireEvent.change(screen.getByLabelText("요청 사항"), {
    target: { value: "수정 요청" },
  });
  fireEvent.click(screen.getByTestId("project-progress-save"));
  await waitFor(() => expect(changed).toHaveBeenCalled());
  expect(writes()[0][0]).toBe("/api/common/projects/P-0001/weekly-memos/1");
  expect(writes()[0][1].method).toBe("PUT");
  expect(JSON.parse(writes()[0][1].body)).not.toHaveProperty("memoId");
});
it("operational memo hides progress and validates at least one text", async () => {
  await ready({ contractTypeCode: "MO" });
  fireEvent.click(screen.getByTestId("project-memo-add"));
  expect(screen.queryByLabelText("실적 진척률")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("project-progress-save"));
  expect(
    screen.getByText("한 일·할 일·요청 사항 중 하나를 적어 주세요."),
  ).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("validates optional TK integer progress", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("project-memo-add"));
  fireEvent.change(screen.getByLabelText("실적 진척률"), {
    target: { value: "1.5" },
  });
  fireEvent.click(screen.getByTestId("project-progress-save"));
  expect(screen.getByText("0~100 정수를 입력해 주세요.")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it.each([
  { statusCode: "END" },
  { statusCode: "LOSE" },
  { endDate: "2026-11-14" },
  { readOnly: true },
])("keeps history visible and hides locked commands %j", async (over) => {
  await ready(over);
  expect(screen.queryByTestId("project-memo-add")).not.toBeInTheDocument();
  expect(screen.queryByTestId("project-memo-edit-1")).not.toBeInTheDocument();
  expect(screen.queryByTestId("project-stage-edit")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("project-stage-complete-1"),
  ).not.toBeInTheDocument();
});
it("completed stage dates are disabled; payload contains every identity and dates only", async () => {
  steps[0].completedDate = "2026-11-14";
  await ready();
  fireEvent.click(screen.getByTestId("project-stage-edit"));
  expect(screen.getByTestId("project-date-0-start")).toBeDisabled();
  expect(screen.getByTestId("project-date-1-start")).toBeEnabled();
  fireEvent.click(screen.getByTestId("project-progress-save"));
  await waitFor(() => expect(changed).toHaveBeenCalled());
  expect(JSON.parse(writes()[0][1].body).items).toEqual(
    steps.map(({ stepId, plannedStartDate, plannedEndDate }) => ({
      stepId,
      plannedStartDate,
      plannedEndDate,
    })),
  );
});
it("completion is bodyless and future stages are disabled", async () => {
  await ready();
  expect(screen.getByTestId("project-stage-complete-2")).toBeDisabled();
  fireEvent.click(screen.getByTestId("project-stage-complete-1"));
  await waitFor(() => expect(changed).toHaveBeenCalled());
  expect(writes()[0][1].body).toBeUndefined();
  expect(
    screen.queryByTestId("project-stage-complete-1"),
  ).not.toBeInTheDocument();
});
it("empty read-only views retain headings without initializing stages", async () => {
  memos = [];
  steps = [];
  render(<ProjectProgressPanel {...props} readOnly />);
  await waitFor(() =>
    expect(screen.queryByText("불러오는 중입니다.")).not.toBeInTheDocument(),
  );
  expect(screen.getByText("주간 메모")).toBeInTheDocument();
  expect(screen.getAllByText("조회 결과가 없습니다.")).toHaveLength(2);
  expect(writes()).toHaveLength(0);
  expect(screen.queryByTestId("project-stage-ensure")).not.toBeInTheDocument();
});
it("initializes absent stages once and reads back the persisted stage identities", async () => {
  const initialized = structuredClone(steps);
  steps = [];
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path.endsWith("/steps/ensure")) steps = initialized;
    return original(path, init);
  });
  await ready();
  expect(screen.getByTestId("project-stage-complete-1")).toBeEnabled();
  expect(writes()).toHaveLength(1);
  expect(writes()[0]).toEqual([
    "/api/common/projects/P-0001/steps/ensure",
    { method: "POST" },
  ]);
  expect(
    request.mock.calls.filter(([path]) => path.endsWith("/steps")),
  ).toHaveLength(2);
  expect(changed).not.toHaveBeenCalled();
});
it.each([
  { statusCode: "END" },
  { statusCode: "LOSE" },
  { endDate: "2026-11-14" },
])(
  "does not initialize missing stages for a locked project %j",
  async (over) => {
    steps = [];
    await ready(over);
    expect(writes()).toHaveLength(0);
  },
);
it("failed reads remain local and retry", async () => {
  request.mockRejectedValueOnce(new Error("네트워크 오류"));
  render(<ProjectProgressPanel {...props} />);
  fireEvent.click(await screen.findByTestId("project-progress-retry"));
  await screen.findByText("기존 작업");
  expect(writes()).toHaveLength(0);
});
it("400 field errors render in modal without clearing input", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("project-memo-add"));
  fireEvent.change(screen.getByLabelText("요청 사항"), {
    target: { value: "요청" },
  });
  request.mockResolvedValueOnce({
    ok: false,
    status: 400,
    json: async () => ({
      success: false,
      message: "입력 오류",
      errors: [{ field: "requests", message: "요청 사항을 확인해 주세요." }],
    }),
  });
  fireEvent.click(screen.getByTestId("project-progress-save"));
  expect(
    await screen.findByText("요청 사항을 확인해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText(/요청 사항/)).toHaveValue("요청");
  expect(changed).not.toHaveBeenCalled();
});
it("clean cancel does not write", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("project-memo-add"));
  fireEvent.click(screen.getByTestId("project-progress-cancel"));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("dirty close uses existing discard dialog without a write", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("project-memo-add"));
  fireEvent.change(screen.getByLabelText("한 일"), {
    target: { value: "미저장" },
  });
  fireEvent.click(screen.getByTestId("project-progress-cancel"));
  const dialog = screen.getByRole("alertdialog");
  fireEvent.click(within(dialog).getByRole("button", { name: "확인" }));
  expect(writes()).toHaveLength(0);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

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
let writeStatus: number;
const changed = vi.fn();
const props = {
  projectId: "P-0001",
  projectName: "진행 프로젝트",
  contractTypeCode: "TK",
  statusCode: "ING",
  startDate: "2026-11-01",
  endDate: "2027-12-31",
  plannedProgress: 50.5,
  actualProgress: 42,
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
  writeStatus = 200;
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
      createdAt: "2026-10-15T00:00:00Z",
      createdBy: "other",
      updatedAt: "2026-10-15T16:00:00Z",
      updatedBy: "admin",
      writtenDate: "2026-10-15",
      modifiedAt: "2026-10-15T16:00:00.123456Z",
    },
  ];
  steps = [
    {
      stepId: "1",
      projectId: "P-0001",
      stepName: "착수",
      weight: 5,
      plannedStartDate: "2026-11-01",
      plannedEndDate: "2026-11-10",
      completedDate: "2026-11-10",
    },
    {
      stepId: "2",
      projectId: "P-0001",
      stepName: "분석",
      weight: 15,
      plannedStartDate: "2026-11-11",
      plannedEndDate: "2026-11-14",
      completedDate: null,
    },
    {
      stepId: "3",
      projectId: "P-0001",
      stepName: "설계",
      weight: 20,
      plannedStartDate: "2026-11-15",
      plannedEndDate: "2027-03-31",
      completedDate: null,
    },
    {
      stepId: "4",
      projectId: "P-0001",
      stepName: "개발",
      weight: 60,
      plannedStartDate: "2027-04-01",
      plannedEndDate: "2027-12-31",
      completedDate: null,
    },
  ];
  request = vi.fn(async (path: string, init?: RequestInit) => {
    const method = init?.method ?? "GET";
    if (method === "GET")
      return path.endsWith("weekly-memos")
        ? ok({ items: memos })
        : ok({ items: steps, modifiedAt: "2026-11-10T00:00:00.5Z" });
    if (writeStatus !== 200)
      return {
        ok: false,
        status: writeStatus,
        json: async () => ({
          success: false,
          message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
          errors: [{ field: "modifiedAt", message: "x" }],
        }),
      };
    return ok({});
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

it("stage table comes first with mockup columns, state badges and no 기본 단계 보완 button", async () => {
  render(<ProjectProgressPanel {...props} />);
  const table = await screen.findByTestId("project-stages");
  await within(table).findByText("착수");
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((h) => h.textContent),
  ).toEqual(["단계", "비중", "예정 기간", "완료일", "상태", ""]);
  const state = (id: string) =>
    within(screen.getByTestId(`project-stage-${id}`)).getAllByRole("cell")[4];
  expect(state("1")).toHaveTextContent("완료");
  expect(state("2")).toHaveTextContent("지연");
  expect(state("3")).toHaveTextContent("진행 중");
  expect(state("4")).toHaveTextContent("예정");
  expect(within(screen.getByTestId("project-stage-2")).getByText("지연")).toHaveClass(
    "ls-b-bad",
  );
  expect(screen.getByTestId("project-stage-1")).toHaveTextContent("26-11 ~ 26-11");
  expect(screen.getByTestId("project-stage-1")).toHaveTextContent("26-11-10");
  expect(screen.queryByTestId("project-stage-complete-4")).not.toBeInTheDocument();
  expect(screen.queryByText("기본 단계 보완")).not.toBeInTheDocument();
  expect(screen.getByTestId("project-stage-edit")).toHaveTextContent(
    "단계 계획 입력",
  );
  expect(screen.getByTestId("project-memo-add")).toHaveTextContent(
    "이번 주 상태 적기",
  );
  // 단계 표가 주간 메모보다 먼저
  const panel = screen.getByTestId("project-progress-panel");
  expect(
    panel.innerHTML.indexOf("project-stages") <
      panel.innerHTML.indexOf("project-memos"),
  ).toBe(true);
});

it("memo list shows Korean 「작성 · 고침」 dates and the request in warning color", async () => {
  render(<ProjectProgressPanel {...props} />);
  const memo = await screen.findByTestId("project-memo-1");
  expect(memo).toHaveTextContent("10-15 작성 · 10-16 고침");
  expect(memo).toHaveTextContent("실적 진척률 10%");
  expect(within(memo).getByText("기존 요청")).toHaveClass("project-warn-text");
});

it("new memo: mockup labels, plan guide and toast text; progress is required", async () => {
  render(<ProjectProgressPanel {...props} />);
  const add = await screen.findByTestId("project-memo-add");
  await waitFor(() => expect(add).toBeEnabled());
  fireEvent.click(add);
  const dialog = within(await screen.findByTestId("project-memo-dialog"));
  expect(dialog.getByText("이번 주 상태 적기", { selector: "h2" })).toBeVisible();
  expect(dialog.getByText("진행 프로젝트 · 2026-11-15")).toBeVisible();
  expect(dialog.getByText("계획 진척률은 50.5%입니다")).toBeVisible();
  for (const label of ["이번 주 한 일", "다음 주 할 일", "요청 사항"])
    expect(dialog.getByText(label)).toBeVisible();
  expect(dialog.getByTestId("project-memo-progress")).toHaveValue("42");
  fireEvent.change(dialog.getByTestId("project-memo-progress"), {
    target: { value: "1.5" },
  });
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  expect(await dialog.findByRole("alert")).toHaveTextContent(
    "실적 진척률은 0~100 사이 정수로 넣어 주세요.",
  );
  expect(writes()).toHaveLength(0);
  fireEvent.change(dialog.getByTestId("project-memo-progress"), {
    target: { value: "" },
  });
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  // 턴키·내부는 실적 진척률 필수(시안 「실적 진척률 *」, 2026-11-20 사용자 결정)
  expect(await dialog.findByRole("alert")).toHaveTextContent("실적 진척률을 입력해 주세요.");
  expect(writes()).toHaveLength(0);
  fireEvent.change(dialog.getByTestId("project-memo-progress"), {
    target: { value: "60" },
  });
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  await waitFor(() => expect(changed).toHaveBeenCalledWith("주간 상태를 저장했습니다."));
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    workDone: null,
    nextWork: null,
    requests: null,
    actualProgress: 60,
  });
});

it("operational memo hides progress, shows the auto note and needs one text", async () => {
  render(<ProjectProgressPanel {...props} contractTypeCode="MO" />);
  const add = await screen.findByTestId("project-memo-add");
  await waitFor(() => expect(add).toBeEnabled());
  fireEvent.click(add);
  const dialog = within(await screen.findByTestId("project-memo-dialog"));
  expect(dialog.queryByTestId("project-memo-progress")).not.toBeInTheDocument();
  expect(
    dialog.getByText(
      "운영형(월단위) 프로젝트는 진척률을 기간 기준으로 자동 계산합니다.",
    ),
  ).toBeVisible();
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  expect(await dialog.findByRole("alert")).toHaveTextContent(
    "한 일, 할 일, 요청 사항 중 하나는 적어 주세요.",
  );
  expect(writes()).toHaveLength(0);
});

it("memo edit sends modifiedAt and uses the edit toast", async () => {
  render(<ProjectProgressPanel {...props} />);
  fireEvent.click(await screen.findByTestId("project-memo-edit-1"));
  const dialog = within(await screen.findByTestId("project-memo-dialog"));
  expect(dialog.getByText("주간 메모 수정", { selector: "h2" })).toBeVisible();
  expect(dialog.getByText("진행 프로젝트 · 2026-10-15 작성")).toBeVisible();
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  await waitFor(() => expect(changed).toHaveBeenCalledWith("주간 메모를 고쳤습니다."));
  const [url, init] = writes().at(-1)!;
  expect(url).toBe("/api/common/projects/P-0001/weekly-memos/1");
  expect(JSON.parse(String(init.body))).toMatchObject({
    workDone: "기존 작업",
    requests: "기존 요청",
    actualProgress: 10,
    modifiedAt: "2026-10-15T16:00:00.123456Z",
  });
});

it("memo edit 409 closes the dialog, reloads and the next edit sends the new modifiedAt", async () => {
  render(<ProjectProgressPanel {...props} />);
  fireEvent.click(await screen.findByTestId("project-memo-edit-1"));
  const dialog = within(await screen.findByTestId("project-memo-dialog"));
  writeStatus = 409;
  // 그 사이 다른 사람이 고친 값(다시 읽으면 새 수정 시각이 온다)
  memos = [{ ...memos[0], workDone: "남이 고친 작업", modifiedAt: "2026-11-14T09:00:00Z" }];
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  await waitFor(() =>
    expect(screen.queryByTestId("project-memo-dialog")).not.toBeInTheDocument(),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
  );
  expect(await screen.findByText("남이 고친 작업")).toBeVisible();
  expect(changed).not.toHaveBeenCalled();
  writeStatus = 200;
  fireEvent.click(screen.getByTestId("project-memo-edit-1"));
  const again = within(await screen.findByTestId("project-memo-dialog"));
  fireEvent.click(again.getByTestId("project-progress-save"));
  await waitFor(() => expect(changed).toHaveBeenCalledWith("주간 메모를 고쳤습니다."));
  expect(JSON.parse(String(writes().at(-1)![1].body)).modifiedAt).toBe(
    "2026-11-14T09:00:00Z",
  );
});

it("a failed [완료] keeps the server message visible after reloading", async () => {
  render(<ProjectProgressPanel {...props} />);
  fireEvent.click(await screen.findByTestId("project-stage-complete-2"));
  const confirm = within(await screen.findByTestId("project-stage-complete-confirm"));
  writeStatus = 400;
  fireEvent.click(confirm.getByRole("button", { name: "완료 처리" }));
  await waitFor(() =>
    expect(screen.getByRole("alert")).toHaveTextContent(
      "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
    ),
  );
  // 다시 읽기가 끝난 뒤에도 문구가 남아 있다(예전에는 다시 읽기가 문구를 지웠다)
  await waitFor(() => expect(screen.getByTestId("project-stage-complete-2")).toBeEnabled());
  expect(screen.getByRole("alert")).toHaveTextContent("다른 사람이 먼저 바꿨습니다.");
  expect(changed).not.toHaveBeenCalled();
});

it("stage plan edits end dates only; starts follow the previous end and the first/last are fixed", async () => {
  render(<ProjectProgressPanel {...props} />);
  fireEvent.click(await screen.findByTestId("project-stage-edit"));
  const dialog = within(await screen.findByTestId("project-dates-dialog"));
  expect(dialog.getByText("진행 프로젝트 · 전체 2026-11-01 ~ 2027-12-31")).toBeVisible();
  // 완료 단계(1)·마지막 단계(4)는 날짜 칸이 없다
  expect(dialog.queryByTestId("project-date-0-end")).not.toBeInTheDocument();
  expect(dialog.queryByTestId("project-date-3-end")).not.toBeInTheDocument();
  expect(dialog.getAllByText("(앞 단계 종료일 다음날)")).toHaveLength(3);
  fireEvent.change(dialog.getByTestId("project-date-2-end"), {
    target: { value: "2026-11-14" },
  });
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  expect(await dialog.findByRole("alert")).toHaveTextContent(
    "「설계」 계획 종료일은 2026-11-14보다 늦어야 합니다.",
  );
  fireEvent.change(dialog.getByTestId("project-date-2-end"), {
    target: { value: "2027-05-31" },
  });
  fireEvent.click(dialog.getByTestId("project-progress-save"));
  await waitFor(() => expect(changed).toHaveBeenCalledWith("단계 계획을 저장했습니다."));
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.modifiedAt).toBe("2026-11-10T00:00:00.5Z");
  expect(body.items).toEqual([
    { stepId: "1", plannedStartDate: "2026-11-01", plannedEndDate: "2026-11-10" },
    { stepId: "2", plannedStartDate: "2026-11-11", plannedEndDate: "2026-11-14" },
    { stepId: "3", plannedStartDate: "2026-11-15", plannedEndDate: "2027-05-31" },
    { stepId: "4", plannedStartDate: "2027-06-01", plannedEndDate: "2027-12-31" },
  ]);
});

it("[완료] asks the mockup confirm, completes with no body and shows the stage toast", async () => {
  render(<ProjectProgressPanel {...props} />);
  fireEvent.click(await screen.findByTestId("project-stage-complete-2"));
  const confirm = within(await screen.findByTestId("project-stage-complete-confirm"));
  expect(confirm.getByText("「분석」 단계를 완료로 바꿀까요?")).toBeVisible();
  expect(confirm.getByText("완료일은 오늘로 기록됩니다.")).toBeVisible();
  expect(writes()).toHaveLength(0);
  fireEvent.click(confirm.getByRole("button", { name: "완료 처리" }));
  await waitFor(() => expect(changed).toHaveBeenCalledWith("분석 단계를 완료로 바꿨습니다."));
  expect(writes()[0]).toEqual([
    "/api/common/projects/P-0001/steps/2/complete",
    { method: "POST" },
  ]);
});

it("ended projects show tables read-only and never create stages", async () => {
  steps = [];
  memos = [];
  render(<ProjectProgressPanel {...props} statusCode="END" />);
  expect(await screen.findByText("등록된 단계가 없습니다.")).toBeVisible();
  expect(screen.getByText("적은 주간 메모가 없습니다.")).toBeVisible();
  expect(screen.queryByTestId("project-memo-add")).not.toBeInTheDocument();
  expect(screen.queryByTestId("project-stage-edit")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("dirty close asks before discarding, without a write", async () => {
  render(<ProjectProgressPanel {...props} />);
  const add = await screen.findByTestId("project-memo-add");
  await waitFor(() => expect(add).toBeEnabled());
  fireEvent.click(add);
  fireEvent.change(await screen.findByTestId("project-memo-work-done"), {
    target: { value: "적던 내용" },
  });
  fireEvent.click(screen.getByTestId("project-progress-cancel"));
  expect(await screen.findByText("입력한 내용을 버릴까요?")).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("메모·단계 계획 창은 시안 창 틀(.project-modal)이고 칸 이름이 입력과 이어지며, 진척률 오류 칸은 빨간 테두리", async () => {
  render(<ProjectProgressPanel {...props} />);
  const add = await screen.findByTestId("project-memo-add");
  await waitFor(() => expect(add).toBeEnabled());
  fireEvent.click(add);
  const memo = await screen.findByTestId("project-memo-dialog");
  expect(memo.querySelector(".project-modal")).not.toBeNull();
  // 저장·취소 줄도 창 틀 안(시안 .mf)
  expect(memo.querySelector(".project-modal > .app-actions")).not.toBeNull();
  // 필수 표시(*)가 붙어 이름표 글이 「실적 진척률 *」가 됨
  const progress = within(memo).getByLabelText(/^실적 진척률/);
  expect(progress).toBe(within(memo).getByTestId("project-memo-progress"));
  // 실적 진척률은 필수라 * 표시(2026-11-20 사용자 결정)
  expect(memo.querySelector(".project-fc .project-bad")).not.toBeNull();
  expect(within(memo).getByLabelText("이번 주 한 일")).toBe(
    within(memo).getByTestId("project-memo-work-done"),
  );
  fireEvent.change(progress, { target: { value: "101" } });
  fireEvent.click(within(memo).getByTestId("project-progress-save"));
  await within(memo).findByRole("alert");
  expect(progress).toHaveAttribute("aria-invalid", "true");
  expect(writes()).toHaveLength(0);
  cleanup();
  render(<ProjectProgressPanel {...props} />);
  fireEvent.click(await screen.findByTestId("project-stage-edit"));
  const dates = await screen.findByTestId("project-dates-dialog");
  expect(dates.querySelector(".project-modal > .app-actions")).not.toBeNull();
});

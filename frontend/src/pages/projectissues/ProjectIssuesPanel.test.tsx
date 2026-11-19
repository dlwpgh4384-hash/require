import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  ProjectIssueDialog,
  type ProjectIssueOption,
} from "./ProjectIssueDialog";
import { ProjectIssuesPanel } from "./ProjectIssuesPanel";
import type { ProjectIssue } from "./projectIssues";
import type { CodeItem } from "../codegroups/codeGroups";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
const code = (value: string, name: string): CodeItem => ({
  code: value,
  codeName: name,
  sortOrd: 1,
  useYn: "Y",
});
const importance = [
  code("HIGH", "높음"),
  code("MID", "중간"),
  code("LOW", "낮음"),
];
const statuses = [
  code("OPEN", "열림"),
  code("ACT", "조치 중"),
  code("CLOSE", "종료"),
];
const project: ProjectIssueOption = {
  projectId: "P-selected",
  projectName: "선택 프로젝트",
  statusCode: "ING",
  endDate: "2026-12-31",
};
const issue = (over: Partial<ProjectIssue> = {}): ProjectIssue => ({
  issueId: "12",
  projectId: project.projectId,
  projectName: project.projectName,
  title: "지난 이슈",
  dueDate: "2026-11-14",
  importanceCode: "HIGH",
  statusCode: "OPEN",
  actionText: "점검",
  assigneeId: "original",
  assigneeName: "최초 담당자",
  createdBy: "original",
  createdAt: "2026-11-01T00:00:00Z",
  updatedAt: null,
  updatedBy: null,
  overdueDays: 1,
  ...over,
});
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const bad = (status: number, field = "title") => ({
  ok: false,
  status,
  json: async () => ({
    success: false,
    message: "입력을 확인해 주세요.",
    errors: [{ field, message: "필드 오류" }],
  }),
});
let request: ReturnType<typeof vi.fn>;
let rows: ProjectIssue[];
let projects: ProjectIssueOption[];
let readFailed: boolean;
let optionFailed: boolean;
let writeStatus: number;
let writeReply: (() => Promise<ReturnType<typeof ok>>) | undefined;
let readReply: (() => Promise<ReturnType<typeof ok>>) | undefined;
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-14T16:00:00Z"));
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  rows = [issue()];
  projects = [
    project,
    {
      ...project,
      projectId: "P-ended",
      projectName: "종료 프로젝트",
      statusCode: "END",
    },
    {
      ...project,
      projectId: "P-lost",
      projectName: "실주 프로젝트",
      statusCode: "LOSE",
    },
  ];
  readFailed = false;
  optionFailed = false;
  writeStatus = 200;
  writeReply = undefined;
  readReply = undefined;
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const path = url.split("?")[0];
    if (path.startsWith("/api/common/project-issues")) {
      if (init?.method === "POST" || init?.method === "PUT") {
        if (writeReply) return writeReply();
        if (writeStatus !== 200) return bad(writeStatus);
        const input = JSON.parse(String(init.body));
        const saved = issue({
          ...input,
          issueId: init.method === "POST" ? "13" : path.split("/").at(-1),
        });
        rows = [saved];
        return ok(saved);
      }
      if (readReply) return readReply();
      if (readFailed) throw new Error("목록 연결 실패");
      const params = new URLSearchParams(url.split("?")[1]);
      const selected = rows.filter((r) =>
        params.get("statusCode")
          ? r.statusCode === params.get("statusCode")
          : r.statusCode !== "CLOSE",
      );
      return ok({
        items: selected,
        openCount: selected.filter((r) => r.statusCode !== "CLOSE").length,
      });
    }
    if (path === "/api/common/projects") {
      if (optionFailed) throw new Error("조건 연결 실패");
      return ok({ items: projects, unassignedCount: 0 });
    }
    if (path.endsWith("/IMPR/codes")) return ok({ items: importance });
    if (path.endsWith("/ISSUE_STAT/codes")) return ok({ items: statuses });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
const writes = () =>
  request.mock.calls.filter(
    ([, init]) => init?.method === "POST" || init?.method === "PUT",
  );
const dialog = (
  props: Partial<Parameters<typeof ProjectIssueDialog>[0]> = {},
) => {
  const onSaved = vi.fn();
  const onClose = vi.fn();
  render(
    <ProjectIssueDialog
      projects={projects}
      importance={importance}
      statuses={statuses}
      onSaved={onSaved}
      onClose={onClose}
      {...props}
    />,
  );
  return { onSaved, onClose };
};
const fill = () => {
  fireEvent.change(screen.getByTestId("project-issue-project"), {
    target: { value: project.projectId },
  });
  fireEvent.change(screen.getByTestId("project-issue-title"), {
    target: { value: "새 이슈" },
  });
};

it("create defaults use Korea today plus seven, MID, OPEN and no assignee selector", () => {
  dialog();
  expect(screen.getByTestId("project-issue-due-date")).toHaveValue(
    "2026-11-22",
  );
  expect(screen.getByTestId("project-issue-importance")).toHaveValue("MID");
  expect(screen.getByTestId("project-issue-status")).toHaveValue("OPEN");
  expect(screen.getByText(/등록 사용자 자동고정/)).toBeInTheDocument();
  expect(
    screen.queryByRole("combobox", { name: /담당자/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("option", { name: "종료 프로젝트" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("option", { name: "실주 프로젝트" }),
  ).not.toBeInTheDocument();
});

it("normal save submits once without confirmation and includes only contracted input fields", async () => {
  const { onSaved } = dialog();
  fill();
  fireEvent.click(screen.getByTestId("project-issue-save"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  const [url, init] = writes()[0];
  expect(url).toBe("/api/common/project-issues");
  expect(init.method).toBe("POST");
  expect(JSON.parse(init.body)).toEqual({
    projectId: "P-selected",
    title: "새 이슈",
    dueDate: "2026-11-22",
    importanceCode: "MID",
    statusCode: "OPEN",
    actionText: null,
  });
});

it("duplicate save and close are disabled throughout a pending submission", async () => {
  let release!: (value: ReturnType<typeof ok>) => void;
  writeReply = () =>
    new Promise((resolve) => {
      release = resolve;
    });
  dialog();
  fill();
  fireEvent.click(screen.getByTestId("project-issue-save"));
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(screen.getByTestId("project-issue-save")).toBeDisabled();
  expect(writes()).toHaveLength(1);
  await act(async () => {
    release(ok(issue()));
  });
});

it("required field errors prevent writes", async () => {
  dialog();
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(
    await screen.findByTestId("project-issue-title-error"),
  ).toHaveTextContent("필수");
  expect(
    screen.getByTestId("project-issue-project-id-error"),
  ).toHaveTextContent("필수");
  expect(writes()).toHaveLength(0);
});

it("length limits are enforced with visible field errors", () => {
  dialog();
  fill();
  fireEvent.change(screen.getByTestId("project-issue-title"), {
    target: { value: "가".repeat(101) },
  });
  fireEvent.change(screen.getByTestId("project-issue-action"), {
    target: { value: "나".repeat(501) },
  });
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(screen.getByTestId("project-issue-title-error")).toHaveTextContent(
    "100자",
  );
  expect(
    screen.getByTestId("project-issue-action-text-error"),
  ).toHaveTextContent("500자");
  expect(writes()).toHaveLength(0);
});

it("cancel with unchanged defaults sends no write", () => {
  const { onClose } = dialog();
  fireEvent.click(screen.getByTestId("project-issue-cancel"));
  expect(onClose).toHaveBeenCalledOnce();
  expect(writes()).toHaveLength(0);
});

it("dirty cancel uses existing discard protection without writing", () => {
  const { onClose } = dialog();
  fill();
  fireEvent.click(screen.getByTestId("project-issue-cancel"));
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.getByTestId("project-issue-discard")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(onClose).toHaveBeenCalledOnce();
  expect(writes()).toHaveLength(0);
});

it("edit echoes immutable project, displays original audit and allows closing on ended project", async () => {
  dialog({
    issue: issue({ projectId: "P-ended", projectName: "종료 프로젝트" }),
  });
  expect(screen.getByTestId("project-issue-project")).toBeDisabled();
  expect(screen.getByTestId("project-issue-audit")).toHaveTextContent(
    "original",
  );
  expect(screen.getByText(/최초 담당자/)).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("project-issue-status"), {
    target: { value: "CLOSE" },
  });
  fireEvent.click(screen.getByTestId("project-issue-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const [url, init] = writes()[0];
  expect(url).toBe("/api/common/project-issues/12");
  expect(init.method).toBe("PUT");
  expect(JSON.parse(init.body)).toMatchObject({
    projectId: "P-ended",
    statusCode: "CLOSE",
  });
  expect(Object.keys(JSON.parse(init.body))).not.toContain("assigneeId");
});

it("400 renders per-field validation and leaves the form open", async () => {
  writeStatus = 400;
  dialog();
  fill();
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(
    await screen.findByTestId("project-issue-title-error"),
  ).toHaveTextContent("필드 오류");
  expect(screen.getByTestId("project-issue-dialog")).toBeInTheDocument();
});

it("404 reports disappearance locally without redirect", async () => {
  writeStatus = 404;
  dialog({ issue: issue() });
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(
    await screen.findByTestId("project-issue-save-error"),
  ).toHaveTextContent("대상이 사라졌습니다");
});

it("network write errors are local and permit retry", async () => {
  writeReply = async () => {
    throw new Error("저장 연결 실패");
  };
  const { onSaved } = dialog();
  fill();
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(
    await screen.findByTestId("project-issue-save-error"),
  ).toHaveTextContent("저장 연결 실패");
  writeReply = undefined;
  fireEvent.click(screen.getByTestId("project-issue-save"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
});

it("list uses shared empty state and retains headers and filters without Excel action", async () => {
  rows = [];
  render(<ProjectIssuesPanel />);
  expect(
    await screen.findByText("조건에 맞는 결과가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getByTestId("project-issue-status-filter")).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: /제목/ }),
  ).toBeInTheDocument();
  expect(screen.queryByTestId("list-download")).not.toBeInTheDocument();
});

it("list loading retains filters and headers", async () => {
  let release!: (value: ReturnType<typeof ok>) => void;
  readReply = () =>
    new Promise((resolve) => {
      release = resolve;
    });
  render(<ProjectIssuesPanel />);
  expect(screen.getByText("목록을 불러오는 중입니다.")).toBeInTheDocument();
  expect(screen.getByTestId("project-issue-status-filter")).toBeInTheDocument();
  await act(async () => {
    release(ok({ items: [], openCount: 0 }));
  });
});

it("overdue days are red and project link dispatches selected domain identity, not row edit", async () => {
  const onOpenProject = vi.fn();
  render(<ProjectIssuesPanel onOpenProject={onOpenProject} />);
  expect(await screen.findByTestId("project-issue-due-12")).toHaveClass(
    "project-bad",
  );
  expect(screen.getByTestId("project-issue-due-12")).toHaveTextContent(
    "1일 초과",
  );
  fireEvent.click(screen.getByTestId("project-issue-project-12"));
  expect(onOpenProject).toHaveBeenCalledWith("P-selected");
  expect(screen.queryByTestId("project-issue-dialog")).not.toBeInTheDocument();
});

it("filter uses CLOSE as DB status and overdue as separate query parameter", async () => {
  render(<ProjectIssuesPanel />);
  await screen.findByTestId("project-issue-create");
  fireEvent.change(screen.getByTestId("project-issue-status-filter"), {
    target: { value: "CLOSE" },
  });
  fireEvent.change(screen.getByTestId("project-issue-overdue-filter"), {
    target: { value: "true" },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([url]) =>
          url.includes("statusCode=CLOSE") && url.includes("overdue=true"),
      ),
    ).toBe(true),
  );
});

it.each(["END", "LOSE"])(
  "%s detail hides new issue but keeps existing issue editable",
  async (statusCode) => {
    render(<ProjectIssuesPanel project={{ ...project, statusCode }} />);
    await screen.findByTestId("project-issue-due-12");
    await waitFor(() =>
      expect(
        screen
          .getByTestId("project-issue-status-filter")
          .querySelectorAll("option").length,
      ).toBeGreaterThan(1),
    );
    expect(
      screen.queryByTestId("project-issue-create"),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("list-row-12"));
    expect(
      await screen.findByTestId("project-issue-dialog"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("project-issue-project")).toBeDisabled();
  },
);

it("list errors stay local and retry reads the real API", async () => {
  readFailed = true;
  render(<ProjectIssuesPanel />);
  expect(await screen.findByText("목록 연결 실패")).toBeInTheDocument();
  readFailed = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByTestId("list-row-12")).toBeInTheDocument();
});

it("option errors retain list and can be retried", async () => {
  optionFailed = true;
  render(<ProjectIssuesPanel />);
  expect(
    await screen.findByTestId("project-issue-options-error"),
  ).toHaveTextContent("조건 연결 실패");
  expect(await screen.findByTestId("list-row-12")).toBeInTheDocument();
  optionFailed = false;
  fireEvent.click(screen.getByTestId("project-issue-options-retry"));
  expect(await screen.findByTestId("project-issue-create")).toBeInTheDocument();
});

it("successful save reloads owning GET, count and parent summary with conditions retained", async () => {
  const onChanged = vi.fn();
  const onOpenCount = vi.fn();
  render(
    <ProjectIssuesPanel onChanged={onChanged} onOpenCount={onOpenCount} />,
  );
  await screen.findByTestId("project-issue-create");
  fireEvent.click(await screen.findByTestId("list-row-12"));
  fireEvent.change(screen.getByTestId("project-issue-title"), {
    target: { value: "수정된 이슈" },
  });
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(await screen.findByText("수정된 이슈")).toBeInTheDocument();
  expect(onChanged).toHaveBeenCalledWith("이슈를 저장했습니다.");
  expect(onOpenCount).toHaveBeenLastCalledWith(1);
  expect(
    request.mock.calls.filter(
      ([url, init]) => url === "/api/common/project-issues" && !init?.method,
    ).length,
  ).toBeGreaterThan(1);
  expect(writes()).toHaveLength(1);
});

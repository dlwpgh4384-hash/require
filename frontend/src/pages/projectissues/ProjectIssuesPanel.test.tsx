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
import {
  ProjectIssueDialog,
  projectIssueAudit,
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
  dueDate: "2026-11-12",
  importanceCode: "HIGH",
  statusCode: "OPEN",
  actionText: "점검",
  assigneeId: "original",
  assigneeName: "김PM",
  createdBy: "original",
  createdByName: "김PM",
  createdAt: "2026-10-15T01:00:00Z",
  updatedAt: null,
  updatedBy: null,
  updatedByName: null,
  modifiedAt: "2026-10-15T01:00:00.123456Z",
  overdueDays: 3,
  ...over,
});
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const fail = (status: number, message: string) => ({
  ok: false,
  status,
  json: async () => ({
    success: false,
    message,
    errors: [{ field: "modifiedAt", message }],
  }),
});
let request: ReturnType<typeof vi.fn>;
let rows: ProjectIssue[];
let writeStatus: number;
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-14T16:00:00Z")); // 한국 2026-11-15
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  rows = [
    issue(),
    issue({
      issueId: "13",
      title: "끝난 이슈",
      importanceCode: "LOW",
      statusCode: "CLOSE",
      overdueDays: 0,
      actionText: null,
    }),
  ];
  writeStatus = 200;
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const path = url.split("?")[0];
    if (path === "/api/common/project-issues/excel")
      return {
        ok: true,
        status: 200,
        headers: new Headers({
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "Content-Disposition": "attachment; filename*=UTF-8''issues.xlsx",
        }),
        blob: async () => new Blob(["x"]),
      };
    if (path.startsWith("/api/common/project-issues")) {
      if (init?.method === "POST" || init?.method === "PUT") {
        if (writeStatus !== 200)
          return fail(
            writeStatus,
            "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
          );
        const input = JSON.parse(String(init.body));
        const saved = issue({
          ...input,
          issueId: init.method === "POST" ? "14" : path.split("/").at(-1),
        });
        return ok(saved);
      }
      const params = new URLSearchParams(url.split("?")[1]);
      const selected = rows.filter((r) =>
        params.get("all") === "true"
          ? true
          : params.get("statusCode")
            ? r.statusCode === params.get("statusCode")
            : r.statusCode !== "CLOSE",
      );
      return ok({
        items: selected,
        openCount: selected.filter((r) => r.statusCode !== "CLOSE").length,
      });
    }
    if (path === "/api/common/projects")
      return ok({
        items: [
          project,
          {
            ...project,
            projectId: "P-ended",
            projectName: "종료",
            statusCode: "END",
          },
        ],
        unassignedCount: 0,
      });
    if (path.endsWith("/IMPR/codes")) return ok({ items: importance });
    if (path.endsWith("/ISSUE_STAT/codes")) return ok({ items: statuses });
    throw new Error(`예상하지 않은 요청: ${url}`);
  });
  vi.stubGlobal("fetch", request);
  URL.createObjectURL = vi.fn(() => "blob:x");
  URL.revokeObjectURL = vi.fn();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

it("list: mockup columns, colored badges, overdue days and default 열림·조치 중 filter", async () => {
  render(<ProjectIssuesPanel onOpenProject={vi.fn()} />);
  const list = await screen.findByTestId("project-issue-list");
  await within(list).findByTestId("list-row-12");
  for (const name of [
    "중요도",
    "제목",
    "프로젝트",
    "담당자",
    "기한",
    "상태",
    "조치 내용",
    "등록일",
  ])
    expect(
      within(list).getByRole("columnheader", { name: new RegExp(name) }),
    ).toBeVisible();
  expect(screen.getByTestId("project-issue-due-12")).toHaveTextContent(
    "2026-11-12 (3일 지남)",
  );
  expect(screen.getByTestId("project-issue-due-12")).toHaveClass("project-bad");
  const row = within(screen.getByTestId("list-row-12"));
  await waitFor(() => expect(row.getByText("높음")).toHaveClass("ls-b-bad"));
  expect(row.getByText("열림")).toHaveClass("ls-b-info");
  expect(row.getByText("2026-10-15")).toBeVisible();
  expect(screen.queryByTestId("list-row-13")).not.toBeInTheDocument();
  const status = screen.getByTestId("project-issue-status-filter");
  expect(status).toHaveValue("open");
  expect(
    [...status.querySelectorAll("option")].map((o) => o.textContent),
  ).toEqual(["전체", "열림·조치 중", "기한 지남", "종료"]);
  expect(screen.getByRole("button", { name: /열 설정/ })).toBeVisible();
  expect(screen.getByTestId("list-download")).toBeVisible();
});

it("list: status choices map to the server filter (전체 = all, 기한 지남 = overdue, 종료 = CLOSE)", async () => {
  render(<ProjectIssuesPanel />);
  await screen.findByTestId("list-row-12");
  for (const [value, query] of [
    ["all", "all=true"],
    ["overdue", "overdue=true"],
    ["closed", "statusCode=CLOSE"],
  ]) {
    fireEvent.change(screen.getByTestId("project-issue-status-filter"), {
      target: { value },
    });
    fireEvent.click(screen.getByTestId("list-search"));
    await waitFor(() =>
      expect(
        request.mock.calls.some(([url]) => String(url).endsWith(`?${query}`)),
      ).toBe(true),
    );
  }
});

it("list: [이슈 등록] goes to the given title host, nothing when the host is null", async () => {
  const host = document.createElement("div");
  document.body.appendChild(host);
  const { rerender } = render(<ProjectIssuesPanel createHost={host} />);
  expect(
    await within(host).findByRole("button", { name: "이슈 등록" }),
  ).toBeVisible();
  rerender(<ProjectIssuesPanel createHost={null} />);
  await waitFor(() => expect(host).toBeEmptyDOMElement());
  expect(screen.queryByTestId("project-issue-create")).not.toBeInTheDocument();
  host.remove();
});

it("list: excel uses the same conditions and reports through onChanged", async () => {
  const onChanged = vi.fn();
  render(<ProjectIssuesPanel onChanged={onChanged} />);
  await screen.findByTestId("list-row-12");
  fireEvent.click(screen.getByTestId("list-download"));
  await waitFor(() =>
    expect(onChanged).toHaveBeenCalledWith(
      "엑셀 파일을 만들었습니다 (조회 결과 1건)",
    ),
  );
  expect(request).toHaveBeenCalledWith("/api/common/project-issues/excel");
});

it("list: new issue dialog defaults, mockup errors and registration toast text", async () => {
  const onChanged = vi.fn();
  render(<ProjectIssuesPanel onChanged={onChanged} />);
  fireEvent.click(await screen.findByTestId("project-issue-create"));
  const dialog = within(await screen.findByTestId("project-issue-dialog"));
  expect(dialog.getByText("이슈 등록", { selector: "h2" })).toBeVisible();
  expect(dialog.getByTestId("project-issue-due-date")).toHaveValue(
    "2026-11-22",
  );
  expect(dialog.getByTestId("project-issue-importance")).toHaveValue("MID");
  expect(dialog.getByTestId("project-issue-status")).toHaveValue("OPEN");
  expect(dialog.getByTestId("project-issue-owner")).toHaveTextContent(
    "(로그인 계정)",
  );
  // 종료 프로젝트는 고를 수 없다
  expect(
    [
      ...dialog
        .getByTestId("project-issue-project")
        .querySelectorAll("option"),
    ].map((o) => o.textContent),
  ).toEqual(["선택", "선택 프로젝트"]);
  fireEvent.click(dialog.getByTestId("project-issue-save"));
  expect(await dialog.findByRole("alert")).toHaveTextContent(
    "프로젝트를 골라 주세요.",
  );
  fireEvent.change(dialog.getByTestId("project-issue-project"), {
    target: { value: "P-selected" },
  });
  fireEvent.click(dialog.getByTestId("project-issue-save"));
  expect(await dialog.findByRole("alert")).toHaveTextContent(
    "제목을 넣어 주세요.",
  );
  fireEvent.change(dialog.getByTestId("project-issue-title"), {
    target: { value: "새 이슈" },
  });
  fireEvent.click(dialog.getByTestId("project-issue-save"));
  await waitFor(() =>
    expect(onChanged).toHaveBeenCalledWith("이슈를 등록했습니다."),
  );
  const post = request.mock.calls.find(([, init]) => init?.method === "POST");
  expect(JSON.parse(String(post?.[1]?.body))).toEqual({
    projectId: "P-selected",
    title: "새 이슈",
    dueDate: "2026-11-22",
    importanceCode: "MID",
    statusCode: "OPEN",
    actionText: null,
  });
});

it("detail tab: simple mockup table with closed issues last and no filters", async () => {
  render(<ProjectIssuesPanel project={project} />);
  const table = await screen.findByTestId("project-issue-table");
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((h) => h.textContent),
  ).toEqual(["중요도", "제목 · 조치 내용", "담당자", "기한", "상태"]);
  const ids = within(table)
    .getAllByRole("row")
    .slice(1)
    .map((r) => r.getAttribute("data-testid"));
  expect(ids).toEqual(["project-issue-row-12", "project-issue-row-13"]);
  expect(screen.getByTestId("project-issue-due-12")).toHaveTextContent(
    "11-123일 지남",
  );
  expect(
    screen.queryByTestId("project-issue-status-filter"),
  ).not.toBeInTheDocument();
  expect(
    screen.getByText(/기한이 지난 이슈는 빨간색으로 보입니다/),
  ).toBeVisible();
  expect(request).toHaveBeenCalledWith(
    "/api/common/project-issues?projectId=P-selected&all=true",
    expect.anything(),
  );
});

it("detail tab: empty state keeps the guide and the register button", async () => {
  rows = [];
  render(<ProjectIssuesPanel project={project} />);
  expect(await screen.findByTestId("project-issue-empty")).toHaveTextContent(
    "등록된 이슈가 없습니다.",
  );
  expect(await screen.findByTestId("project-issue-create")).toBeVisible();
});

it.each(["END", "LOSE"])(
  "detail tab of %s project hides the guide and new issue but keeps editing",
  async (statusCode) => {
    render(<ProjectIssuesPanel project={{ ...project, statusCode }} />);
    const row = await screen.findByTestId("project-issue-row-12");
    expect(
      screen.queryByTestId("project-issue-create"),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/기한이 지난 이슈는/)).not.toBeInTheDocument();
    await waitFor(() => {
      fireEvent.click(row);
      expect(screen.getByTestId("project-issue-dialog")).toBeVisible();
    });
  },
);

it("edit sends modifiedAt, shows the audit line with names and the mockup save toast", async () => {
  const onChanged = vi.fn();
  rows = [
    issue({
      updatedAt: "2026-10-19T16:00:00Z",
      updatedBy: "editor",
      updatedByName: "이관리",
      modifiedAt: "2026-10-19T16:00:00.654321Z",
    }),
  ];
  render(<ProjectIssuesPanel project={project} onChanged={onChanged} />);
  const row = await screen.findByTestId("project-issue-row-12");
  await waitFor(() => {
    fireEvent.click(row);
    expect(screen.getByTestId("project-issue-dialog")).toBeVisible();
  });
  const dialog = within(screen.getByTestId("project-issue-dialog"));
  expect(dialog.getByText("이슈 수정", { selector: "h2" })).toBeVisible();
  expect(dialog.getByTestId("project-issue-audit")).toHaveTextContent(
    "10-15 김PM 등록 · 10-20 이관리 고침",
  );
  expect(dialog.getByTestId("project-issue-owner")).toHaveTextContent(
    "김PM(로그인 계정)",
  );
  fireEvent.change(dialog.getByTestId("project-issue-status"), {
    target: { value: "CLOSE" },
  });
  fireEvent.click(dialog.getByTestId("project-issue-save"));
  await waitFor(() =>
    expect(onChanged).toHaveBeenCalledWith("이슈를 저장했습니다."),
  );
  const put = request.mock.calls.find(([, init]) => init?.method === "PUT");
  expect(put?.[0]).toBe("/api/common/project-issues/12");
  expect(JSON.parse(String(put?.[1]?.body))).toMatchObject({
    statusCode: "CLOSE",
    modifiedAt: "2026-10-19T16:00:00.654321Z",
  });
});

it("409 keeps the dialog open with the common concurrency message", async () => {
  writeStatus = 409;
  render(
    <ProjectIssueDialog
      issue={issue()}
      projects={[project]}
      importance={importance}
      statuses={statuses}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByTestId("project-issue-save"));
  expect(
    await screen.findByTestId("project-issue-save-error"),
  ).toHaveTextContent("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
  expect(screen.getByTestId("project-issue-dialog")).toBeVisible();
});

it("audit line uses Korean dates (UTC evening is the next Korean day)", () => {
  expect(
    projectIssueAudit(
      issue({ createdAt: "2026-10-14T15:30:00Z", updatedAt: null }),
    ),
  ).toBe("10-15 김PM 등록");
});
it("이슈 창: 시안 창 틀(.project-modal), 필수 칸(프로젝트·제목·중요도·기한·상태)에 *, 조치 내용은 선택, 틀린 칸은 빨간 테두리", async () => {
  render(<ProjectIssuesPanel />);
  fireEvent.click(await screen.findByTestId("project-issue-create"));
  const box = await screen.findByTestId("project-issue-dialog");
  const dialog = within(box);
  expect(box.querySelector("form.project-modal")).not.toBeNull();
  const required = [...box.querySelectorAll(".project-fc label")]
    .filter((label) => label.querySelector(".project-bad"))
    .map((label) => label.textContent?.replace(" *", ""));
  expect(required).toEqual(["프로젝트", "제목", "중요도", "기한", "상태"]);
  expect(dialog.getByLabelText("조치 내용")).toBe(
    dialog.getByTestId("project-issue-action"),
  );
  fireEvent.click(dialog.getByTestId("project-issue-save"));
  await dialog.findByRole("alert");
  expect(dialog.getByTestId("project-issue-project")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  fireEvent.change(dialog.getByTestId("project-issue-project"), {
    target: { value: "P-selected" },
  });
  fireEvent.click(dialog.getByTestId("project-issue-save"));
  await waitFor(() =>
    expect(dialog.getByTestId("project-issue-title")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(dialog.getByTestId("project-issue-project")).not.toHaveAttribute(
    "aria-invalid",
  );
  expect(
    request.mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(0);
});
it("list: 중요도·상태 이름을 받기 전에는 코드(HIGH·OPEN) 대신 「불러오는 중」", async () => {
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  const base = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.endsWith("/IMPR/codes")) await gate;
    return base(url, init);
  });
  render(<ProjectIssuesPanel />);
  await waitFor(() =>
    expect(
      request.mock.calls.some(([url]) =>
        String(url).startsWith("/api/common/project-issues"),
      ),
    ).toBe(true),
  );
  await new Promise((resolve) => setTimeout(resolve, 20));
  expect(screen.queryByText("HIGH")).not.toBeInTheDocument();
  expect(screen.queryByText("지난 이슈")).not.toBeInTheDocument();
  release();
  expect(await screen.findByText("지난 이슈")).toBeVisible();
  expect(screen.getAllByText("높음").length).toBeGreaterThan(0);
  expect(screen.queryByText("HIGH")).not.toBeInTheDocument();
});

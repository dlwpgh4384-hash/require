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
import { MemoryRouter } from "react-router-dom";
import { ProjectBoardPage } from "./ProjectBoardPage";
import type { ProjectBoardResult, ProjectBoardRow } from "./projectBoard";

const viewport = vi.hoisted(() => ({ mobile: false, tablet: false }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
vi.mock("../projects/ProjectDetail", () => ({
  ProjectDetail: ({
    projectId,
    initialTab,
    onChanged,
    onClose,
  }: {
    projectId: string;
    initialTab: string;
    onChanged: (message: string) => void;
    onClose: () => void;
  }) => (
    <div data-testid="board-detail-boundary">
      {projectId} · {initialTab}
      <button onClick={() => onChanged("저장했습니다.")}>상세 저장 알림</button>
      <button onClick={onClose}>상세 닫기</button>
    </div>
  ),
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
let request: ReturnType<typeof vi.fn>;
let result: ProjectBoardResult;
let failed: boolean;
let optionFailed: boolean;
let issueFailed: boolean;
let waiting: Promise<ReturnType<typeof ok>> | undefined;
const row = (over: Partial<ProjectBoardRow> = {}): ProjectBoardRow => ({
  projectId: "selected",
  projectName: "지연 프로젝트",
  contractTypeCode: "TK",
  signal: "위험",
  signalReasons: ["일정 10.0%p 지연", "주간 메모 15일간 업데이트 없음"],
  reasonSummary:
    "일정 10.0%p 지연 · 주간 메모 15일간 업데이트 없음 · 1차 경보: UI-04에서 상세 확인",
  pmUserId: "assigned-pm",
  pmName: "담당 PM",
  currentStepName: "수행",
  stepDelayed: true,
  plannedProgress: 60,
  actualProgress: 50,
  costUsageRate: 65,
  costProgressGap: 15,
  openIssueCount: 2,
  overdueIssueCount: 1,
  latestMemoDate: "2026-10-31",
  daysWithoutMemo: 15,
  stale: true,
  ...over,
});
const empty = (): ProjectBoardResult => ({
  summary: {
    progress: { total: 0, normal: 0, caution: 0, danger: 0 },
    riskCount: 0,
    openIssues: { total: 0, high: 0 },
    overdueIssueCount: 0,
    staleProjectCount: 0,
  },
  items: [],
  staleProjects: [],
  calculationMethod: "DB 계산 방법 · 1차 경보 · UI-04 상세 확인",
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
  viewport.mobile = false;
  failed = optionFailed = issueFailed = false;
  waiting = undefined;
  result = {
    summary: {
      progress: { total: 2, normal: 1, caution: 0, danger: 1 },
      riskCount: 1,
      openIssues: { total: 2, high: 1 },
      overdueIssueCount: 1,
      staleProjectCount: 1,
    },
    items: [
      row(),
      row({
        projectId: "monthly",
        projectName: "월단위 프로젝트",
        contractTypeCode: "MO",
        signal: "정상",
        signalReasons: [],
        reasonSummary: "해당하는 경보 사유 없음",
        pmUserId: null,
        pmName: null,
        currentStepName: null,
        stepDelayed: false,
        costUsageRate: null,
        costProgressGap: null,
        openIssueCount: 0,
        overdueIssueCount: 0,
        daysWithoutMemo: 14,
        stale: false,
      }),
    ],
    staleProjects: [
      {
        projectId: "selected",
        projectName: "지연 프로젝트",
        daysWithoutMemo: 15,
      },
    ],
    calculationMethod: "DB 계산 방법 · 1차 경보 · UI-04 상세 확인",
  };
  request = vi.fn(async (url: string) => {
    const path = url.split("?")[0];
    if (path === "/api/common/project-board") {
      if (waiting) return waiting;
      if (failed)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: "조건을 확인해 주세요.",
            errors: [{ field: "signal", message: "신호등을 확인해 주세요." }],
          }),
        };
      return ok(result);
    }
    if (path === "/api/common/project-board/excel")
      return {
        ok: true,
        status: 200,
        headers: new Headers({
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "Content-Disposition": "attachment; filename=board.xlsx",
        }),
        blob: async () => new Blob(["download-response"]),
      };
    if (path === "/api/common/project-issues") {
      if (issueFailed) throw new Error("이슈 조회 실패");
      return ok({
        items: [
          {
            issueId: "71",
            projectId: "selected",
            projectName: "지연 프로젝트",
            title: "납기 확인",
            importanceCode: "HIGH",
            statusCode: "OPEN",
            assigneeName: "등록 담당자",
            dueDate: "2026-11-14",
            overdueDays: 1,
            actionText: "확인 중",
            createdAt: "2026-11-10T00:00:00Z",
          },
        ],
        openCount: 7,
      });
    }
    if (path.endsWith("/CTRT_TYPE/codes")) {
      if (optionFailed) throw new Error("옵션 오류");
      return ok({
        items: [
          { code: "TK", codeName: "턴키" },
          { code: "MO", codeName: "월단위" },
        ],
      });
    }
    if (path.endsWith("/ISSUE_STAT/codes"))
      return ok({
        items: [
          { code: "OPEN", codeName: "열림" },
          { code: "CLOSE", codeName: "종료" },
        ],
      });
    if (path.endsWith("/IMPR/codes"))
      return ok({ items: [{ code: "HIGH", codeName: "높음" }] });
    if (path === "/api/common/parts")
      return ok({ items: [{ partId: "sales", partName: "영업 파트" }] });
    if (path === "/api/common/projects")
      return ok({
        items: [{ projectId: "selected", projectName: "지연 프로젝트" }],
      });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
const mount = () =>
  render(
    <MemoryRouter>
      <ProjectBoardPage />
    </MemoryRouter>,
  );

it("renders the five API-backed summaries and two tabs, retaining server order", async () => {
  mount();
  await screen.findByTestId("list-row-selected");
  expect(screen.getByRole("tab", { name: "현황판" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(screen.getByRole("tab", { name: /이슈 모아 보기/ })).toBeVisible();
  const summary = screen.getByLabelText("프로젝트 현황 요약");
  expect(summary.children).toHaveLength(5);
  expect(summary).toHaveTextContent("정상 1 · 주의 0 · 위험 1");
  expect(summary).toHaveTextContent("높음 1");
  const rows = screen.getAllByTestId(/^list-row-(selected|monthly)$/);
  expect(rows.map((element) => element.getAttribute("data-testid"))).toEqual([
    "list-row-selected",
    "list-row-monthly",
  ]);
  expect(within(rows[0]).getByText("담당 PM")).toHaveAttribute(
    "title",
    "assigned-pm",
  );
});

it("shows all reasons on focus and the UI-04 first-warning explanation", async () => {
  mount();
  const target = await screen.findByTestId("list-row-selected");
  const signal = within(target).getByLabelText(/위험: 일정/);
  expect(signal).toHaveAttribute("tabindex", "0");
  expect(signal).toHaveAttribute(
    "title",
    "일정 10.0%p 지연 · 주간 메모 15일간 업데이트 없음",
  );
  expect(target).toHaveTextContent("UI-04에서 상세 확인");
  expect(
    within(target).getByRole("progressbar", { name: "계획" }),
  ).toHaveAttribute("value", "60");
  expect(
    within(target).getByRole("progressbar", { name: "실적" }),
  ).toHaveAttribute("value", "50");
});

it("shows stale project name and days but no fourteen-day boundary warning", async () => {
  mount();
  const banner = await screen.findByTestId("project-board-stale-banner");
  expect(banner).toHaveTextContent("지연 프로젝트 · 15일");
  expect(banner).not.toHaveTextContent("월단위 프로젝트");
  expect(screen.getByTestId("list-row-selected")).toHaveTextContent(
    "15일 초과 경고",
  );
  expect(screen.getByTestId("list-row-monthly")).not.toHaveTextContent(
    "초과 경고",
  );
});

it("renders non-turnkey cost and missing PM as dashes without monetary columns", async () => {
  mount();
  const target = await screen.findByTestId("list-row-monthly");
  expect(within(target).getAllByText("–").length).toBeGreaterThan(1);
  expect(
    screen.queryByRole("columnheader", { name: "원가 금액" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "예산" }),
  ).not.toBeInTheDocument();
});

it("opens the selected project's shared progress tab and refreshes onChanged", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("list-row-selected"));
  expect(screen.getByTestId("board-detail-boundary")).toHaveTextContent(
    "selected · progress",
  );
  const before = request.mock.calls.filter(([url]) =>
    String(url).startsWith("/api/common/project-board?"),
  ).length;
  fireEvent.click(screen.getByRole("button", { name: "상세 저장 알림" }));
  await waitFor(() =>
    expect(
      request.mock.calls.filter(([url]) =>
        String(url).startsWith("/api/common/project-board?"),
      ).length,
    ).toBeGreaterThan(before),
  );
  expect(await screen.findByTestId("project-board-toast")).toHaveTextContent(
    "저장했습니다.",
  );
});

it("applies only contracted filters and downloads with the applied, not draft, conditions", async () => {
  mount();
  await screen.findByRole("option", { name: "턴키" });
  fireEvent.change(screen.getByTestId("project-board-signal"), {
    target: { value: "위험" },
  });
  fireEvent.change(screen.getByTestId("project-board-contract-type"), {
    target: { value: "TK" },
  });
  fireEvent.change(screen.getByTestId("project-board-sales-part"), {
    target: { value: "sales" },
  });
  fireEvent.submit(screen.getByTestId("project-board-filters"));
  const query = new URLSearchParams({
    signal: "위험",
    contractTypeCode: "TK",
    salesPartId: "sales",
  });
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      `/api/common/project-board?${query}`,
      expect.anything(),
    ),
  );
  fireEvent.change(screen.getByTestId("project-board-contract-type"), {
    target: { value: "MO" },
  });
  Object.defineProperty(URL, "createObjectURL", {
    configurable: true,
    value: vi.fn(() => "blob:board"),
  });
  Object.defineProperty(URL, "revokeObjectURL", {
    configurable: true,
    value: vi.fn(),
  });
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(
    () => undefined,
  );
  fireEvent.click(screen.getByTestId("list-download"));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      `/api/common/project-board/excel?${query}`,
    ),
  );
});

it("keeps filters and headers in the empty state", async () => {
  result = empty();
  mount();
  expect(await screen.findByText("조건에 맞는 결과가 없습니다.")).toBeVisible();
  expect(screen.getByTestId("project-board-filters")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "PM" })).toBeVisible();
  expect(screen.getByRole("tab", { name: /이슈 모아 보기/ })).toBeVisible();
});

it("shows loading without synthesized rows", async () => {
  waiting = new Promise(() => undefined);
  mount();
  expect(await screen.findByText("목록을 불러오는 중입니다.")).toBeVisible();
  expect(screen.queryByTestId("list-row-selected")).not.toBeInTheDocument();
  expect(screen.getByLabelText("프로젝트 현황 요약")).toHaveTextContent("–");
});

it("keeps read failure local and retries with field errors visible", async () => {
  failed = true;
  mount();
  expect(await screen.findByText("신호등을 확인해 주세요.")).toBeVisible();
  expect(screen.getByRole("tab", { name: "현황판" })).toBeVisible();
  failed = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByTestId("list-row-selected")).toBeVisible();
});

it("retries filter option failures without replacing board data", async () => {
  optionFailed = true;
  mount();
  expect(await screen.findByTestId("list-row-selected")).toBeVisible();
  await screen.findByTestId("project-board-options-retry");
  optionFailed = false;
  fireEvent.click(screen.getByTestId("project-board-options-retry"));
  expect(
    await screen.findByRole("option", { name: "턴키" }),
  ).toBeInTheDocument();
});

it("issue tab calls the other feature API, shows its openCount and hides Excel", async () => {
  mount();
  await screen.findByTestId("list-row-selected");
  fireEvent.click(screen.getByTestId("project-board-issues-tab"));
  const issue = await screen.findByTestId("list-row-71");
  expect(issue).toHaveTextContent("납기 확인");
  expect(issue).toHaveTextContent("1일 초과");
  expect(
    screen.getByRole("tab", { name: "이슈 모아 보기 (7)" }),
  ).toHaveAttribute("aria-selected", "true");
  expect(screen.getByTestId("list-download")).not.toBeVisible();
  fireEvent.click(screen.getByTestId("project-issue-project-71"));
  expect(screen.getByTestId("board-detail-boundary")).toHaveTextContent(
    "selected · issues",
  );
});

it("issue request failure has its own retry and does not remove the tabs", async () => {
  issueFailed = true;
  mount();
  await screen.findByTestId("list-row-selected");
  fireEvent.click(screen.getByTestId("project-board-issues-tab"));
  const panel = await screen.findByTestId("project-board-issues-panel");
  await within(panel).findByRole("button", { name: "다시 시도" });
  issueFailed = false;
  fireEvent.click(within(panel).getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByTestId("list-row-71")).toBeVisible();
  expect(screen.getByRole("tab", { name: "현황판" })).toBeVisible();
});

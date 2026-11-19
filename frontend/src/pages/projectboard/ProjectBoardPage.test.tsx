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
import {
  ProjectBoardTitleActions,
  ProjectBoardWorkspaceProvider,
} from "./ProjectBoardWorkspace";
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
      <button onClick={() => onChanged("주간 상태를 저장했습니다.")}>
        상세 저장 알림
      </button>
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
const row = (over: Partial<ProjectBoardRow> = {}): ProjectBoardRow => ({
  projectId: "selected",
  projectName: "지연 프로젝트",
  contractTypeCode: "TK",
  signal: "위험",
  signalReasons: [
    "일정이 계획보다 12.5%p 늦음",
    "원가 사용률이 진척률보다 15.5%p 높음 — 원가 초과 위험",
    "17일째 상태 업데이트 없음",
  ],
  signalReasonLevels: ["위험", "위험", "주의"],
  reasonSummary: "",
  pmUserId: "assigned-pm",
  pmName: "담당 PM",
  currentStepName: "개발",
  stepDelayed: true,
  plannedProgress: 50.5,
  actualProgress: 38,
  costUsageRate: 53.5,
  costProgressGap: 15.5,
  openIssueCount: 2,
  overdueIssueCount: 1,
  latestMemoDate: "2026-10-29",
  lastUpdateDate: "2026-10-29",
  daysWithoutMemo: 17,
  stale: true,
  salesPartId: "sales",
  ...over,
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
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
  viewport.mobile = false;
  failed = optionFailed = false;
  result = {
    summary: {
      progress: { total: 3, normal: 1, caution: 1, danger: 1 },
      riskCount: 1,
      openIssues: { total: 4, high: 1 },
      overdueIssueCount: 1,
      staleProjectCount: 2,
    },
    items: [
      row({
        projectId: "normal",
        projectName: "가나 프로젝트",
        contractTypeCode: "IN",
        signal: "정상",
        signalReasons: [],
        signalReasonLevels: [],
        currentStepName: "설계",
        stepDelayed: false,
        costUsageRate: null,
        costProgressGap: null,
        openIssueCount: 0,
        overdueIssueCount: 0,
        daysWithoutMemo: 3,
        lastUpdateDate: "2026-11-12",
        stale: false,
        salesPartId: "HQ",
      }),
      row(),
      row({
        projectId: "caution",
        projectName: "다라 프로젝트",
        contractTypeCode: "MO",
        signal: "주의",
        signalReasons: ["기한 지난 이슈 1건", "21일째 상태 업데이트 없음"],
        signalReasonLevels: ["주의", "주의"],
        costUsageRate: null,
        costProgressGap: null,
        daysWithoutMemo: 21,
        lastUpdateDate: "2026-10-25",
        latestMemoDate: null,
        salesPartId: null,
      }),
    ],
    staleProjects: [
      { projectId: "selected", projectName: "지연 프로젝트", daysWithoutMemo: 17 },
      { projectId: "caution", projectName: "다라 프로젝트", daysWithoutMemo: 21 },
    ],
    calculationMethod: "DB 계산 방법",
  };
  request = vi.fn(async (url: string) => {
    const path = url.split("?")[0];
    if (path === "/api/common/project-board") {
      if (failed)
        return {
          ok: false,
          status: 500,
          json: async () => ({ success: false, message: "현황판을 불러오지 못했습니다." }),
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
    if (path === "/api/common/project-issues")
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
    if (path.endsWith("/CTRT_TYPE/codes")) {
      if (optionFailed) throw new Error("옵션 오류");
      return ok({
        items: [
          { code: "TK", codeName: "턴키" },
          { code: "MO", codeName: "월단위" },
          { code: "IN", codeName: "내부(자체 개발)" },
        ],
      });
    }
    if (path.endsWith("/ISSUE_STAT/codes"))
      return ok({ items: [{ code: "OPEN", codeName: "열림" }] });
    if (path.endsWith("/IMPR/codes"))
      return ok({ items: [{ code: "HIGH", codeName: "높음" }] });
    if (path === "/api/common/parts")
      return ok({ items: [{ partId: "sales", partName: "영업 파트", useYn: "Y" }] });
    if (path === "/api/common/projects")
      return ok({
        items: [
          {
            projectId: "selected",
            projectName: "지연 프로젝트",
            statusCode: "ING",
            endDate: "2099-12-31",
          },
        ],
      });
    return ok({ items: [] });
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
const mount = () =>
  render(
    <MemoryRouter>
      <ProjectBoardWorkspaceProvider>
        <div data-testid="title-bar">
          <ProjectBoardTitleActions />
        </div>
        <ProjectBoardPage />
      </ProjectBoardWorkspaceProvider>
    </MemoryRouter>,
  );
const ids = () =>
  screen
    .getAllByTestId(/^list-row-/)
    .map((r) => r.getAttribute("data-testid")?.replace("list-row-", ""));

it("five summary cards use 「건」 and the mockup captions; risk and overdue are red", async () => {
  mount();
  await screen.findByTestId("list-row-selected");
  expect(screen.getByTestId("project-board-kpi-total")).toHaveTextContent(
    "진행 프로젝트3건정상 1 · 주의 1 · 위험 1",
  );
  expect(screen.getByTestId("project-board-kpi-risk")).toHaveTextContent(
    "위험1건일정·원가·중요 이슈 기준",
  );
  expect(within(screen.getByTestId("project-board-kpi-risk")).getByText("1건")).toHaveClass(
    "project-bad",
  );
  expect(screen.getByTestId("project-board-kpi-open")).toHaveTextContent(
    "열린 이슈4건높음 1건",
  );
  expect(screen.getByTestId("project-board-kpi-overdue")).toHaveTextContent(
    "기한 지난 이슈1건담당자 확인 필요",
  );
  expect(screen.getByTestId("project-board-kpi-stale")).toHaveTextContent(
    "업데이트 없음2건2주 넘게 상태를 적지 않음",
  );
  expect(request).toHaveBeenCalledWith("/api/common/project-board?", {
    signal: expect.any(AbortSignal),
  });
});

it("default order is light severity then name; headers, reasons and stage/update texts follow the mockup", async () => {
  mount();
  await screen.findByTestId("list-row-selected");
  expect(ids()).toEqual(["selected", "caution", "normal"]);
  expect(
    screen.getByRole("columnheader", { name: /진척률 \(계획 \/ 실적\)/ }),
  ).toBeVisible();
  const selected = within(screen.getByTestId("list-row-selected"));
  expect(selected.getByText("selected · 턴키")).toBeVisible();
  expect(screen.getByTestId("project-board-reasons-selected")).toHaveTextContent(
    "일정이 계획보다 12.5%p 늦음 · 원가 사용률이 진척률보다 15.5%p 높음 — 원가 초과 위험 · 17일째 상태 업데이트 없음",
  );
  expect(screen.getByTestId("project-board-reasons-selected")).toHaveClass("project-bad");
  expect(screen.getByTestId("project-board-reasons-caution")).toHaveClass(
    "project-warn-text",
  );
  expect(selected.getByText("지연")).toHaveClass("ls-b-bad");
  expect(selected.getByRole("cell", { name: "개발 지연" })).toBeVisible();
  expect(selected.getByText("50.5%")).toBeVisible();
  expect(selected.getByText("진척 대비 +15.5%p")).toBeVisible();
  expect(screen.getByTestId("project-board-update-selected")).toHaveTextContent(
    "2026-10-29 17일 전",
  );
  expect(within(screen.getByTestId("project-board-update-selected")).getByText("17일 전")).toHaveClass(
    "ls-b-warn",
  );
  expect(screen.getByTestId("project-board-signal-normal")).toHaveAttribute(
    "title",
    "계획대로 진행",
  );
  expect(screen.getByTestId("project-board-signal-selected")).toHaveAttribute(
    "title",
    "일정이 계획보다 12.5%p 늦음 / 원가 사용률이 진척률보다 15.5%p 높음 — 원가 초과 위험 / 17일째 상태 업데이트 없음",
  );
  expect(within(screen.getByTestId("list-row-normal")).getByText("normal · 내부")).toBeVisible();
  expect(screen.queryByText(/1차 경보: UI-04/)).not.toBeInTheDocument();
});

it("every column except progress sorts, and column settings are available", async () => {
  mount();
  await screen.findByTestId("list-row-selected");
  fireEvent.click(screen.getByTestId("list-head-projectName"));
  expect(ids()).toEqual(["normal", "caution", "selected"]);
  fireEvent.click(screen.getByTestId("list-head-openIssueCount"));
  expect(ids()).toEqual(["normal", "selected", "caution"]);
  for (const key of ["signal", "pmName", "currentStepName", "costUsageRate", "overdueIssueCount", "lastUpdate"])
    expect(screen.getByTestId(`list-head-${key}`)).toHaveAttribute("aria-sort");
  expect(screen.getByTestId("list-head-progress")).not.toHaveAttribute("aria-sort");
  expect(screen.getByTestId("list-columns")).toBeVisible();
});

it("stale banner is one warning line with clickable names opening project progress", async () => {
  mount();
  const banner = await screen.findByTestId("project-board-stale-banner");
  expect(banner).toHaveTextContent(
    "2주 넘게 상태를 적지 않은 프로젝트: 지연 프로젝트 (17일), 다라 프로젝트 (21일)",
  );
  expect(within(banner).getByTestId("banner-warn")).toBeVisible();
  fireEvent.click(within(banner).getByRole("button", { name: "다라 프로젝트" }));
  expect(await screen.findByTestId("board-detail-boundary")).toHaveTextContent(
    "caution · progress",
  );
});

it("filters are applied on the screen with 내부 and 본사(직접 영업) options; Excel sends the applied filter", async () => {
  mount();
  await screen.findByTestId("list-row-selected");
  await waitFor(() =>
    expect(
      [...screen.getByTestId("project-board-contract-type").querySelectorAll("option")].map(
        (o) => o.textContent,
      ),
    ).toEqual(["전체", "턴키", "월단위", "내부"]),
  );
  expect(
    [...screen.getByTestId("project-board-sales-part").querySelectorAll("option")].map(
      (o) => o.textContent,
    ),
  ).toEqual(["전체", "영업 파트", "본사(직접 영업)"]);
  fireEvent.change(screen.getByTestId("project-board-sales-part"), {
    target: { value: "HQ" },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  expect(ids()).toEqual(["normal"]);
  // 요약은 조건과 상관없이 전체
  expect(screen.getByTestId("project-board-kpi-total")).toHaveTextContent("3건");
  fireEvent.click(screen.getByTestId("list-download"));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/project-board/excel?salesPartId=HQ",
    ),
  );
  expect(await screen.findByTestId("project-board-toast")).toHaveTextContent(
    "엑셀 파일을 만들었습니다 (조회 결과 1건)",
  );
});

it("calculation method is the mockup table, not a raw formula dump", async () => {
  mount();
  const box = await screen.findByTestId("project-board-calculation");
  expect(box).toHaveTextContent("계산 방법 이 화면의 숫자를 이렇게 계산합니다");
  expect(
    within(box)
      .getAllByRole("row")
      .slice(1)
      .map((r) => within(r).getAllByRole("cell")[0].textContent),
  ).toEqual(["계획 진척률", "실적 진척률", "원가 사용률", "진척 대비 원가", "신호등", "업데이트 없음"]);
  expect(box).toHaveTextContent(
    "수주예정 프로젝트도 선투입이 있으면 함께 봅니다. 종료된 프로젝트는 빠집니다.",
  );
  expect(box).toHaveTextContent("오늘 − 마지막 주간 메모 날짜 > 14일");
  expect(screen.queryByText("DB 계산 방법")).not.toBeInTheDocument();
});

it("row click opens project progress; detail notices become one board toast and refresh", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("list-row-selected"));
  const detail = await screen.findByTestId("board-detail-boundary");
  expect(detail).toHaveTextContent("selected · progress");
  fireEvent.click(within(detail).getByText("상세 저장 알림"));
  expect(await screen.findByTestId("project-board-toast")).toHaveTextContent(
    "주간 상태를 저장했습니다.",
  );
  await waitFor(() =>
    expect(
      request.mock.calls.filter(([url]) => String(url).startsWith("/api/common/project-board?")),
    ).toHaveLength(2),
  );
});

it("a failed read keeps the summary, tabs, filters and toolbar and retries", async () => {
  failed = true;
  mount();
  expect(await screen.findByText("현황판을 불러오지 못했습니다.")).toBeVisible();
  expect(screen.getByTestId("project-board-kpi-total")).toHaveTextContent("–");
  expect(screen.getByTestId("project-board-filters")).toBeVisible();
  expect(screen.getByTestId("project-board-issues-tab")).toBeVisible();
  expect(screen.getByTestId("project-board-calculation")).toBeVisible();
  failed = false;
  fireEvent.click(screen.getByTestId("list-retry"));

  expect(await screen.findByTestId("list-row-selected")).toBeVisible();
});

it("empty board keeps filters, headers and the calculation box", async () => {
  result.items = [];
  result.staleProjects = [];
  mount();
  expect(await screen.findByTestId("list-empty")).toBeVisible();
  expect(screen.queryByTestId("project-board-stale-banner")).not.toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: /프로젝트/ })).toBeVisible();
  expect(screen.getByTestId("project-board-calculation")).toBeVisible();
});

it("option failure keeps the board usable and offers retry", async () => {
  optionFailed = true;
  mount();
  await screen.findByTestId("list-row-selected");
  expect(await screen.findByTestId("project-board-options-retry")).toBeVisible();
  optionFailed = false;
  fireEvent.click(screen.getByTestId("project-board-options-retry"));
  await waitFor(() =>
    expect(screen.queryByTestId("project-board-options-retry")).not.toBeInTheDocument(),
  );
});

it("issue tab shows its count, the issue list with Excel and [이슈 등록] in the title bar only there", async () => {
  mount();
  await screen.findByTestId("list-row-selected");
  const title = within(screen.getByTestId("title-bar"));
  expect(title.queryAllByRole("button")).toHaveLength(0);
  fireEvent.click(screen.getByTestId("project-board-issues-tab"));
  const issues = within(screen.getByTestId("project-board-issues-panel"));
  expect(await issues.findByTestId("list-row-71")).toBeVisible();
  await waitFor(() =>
    expect(screen.getByTestId("project-board-issues-tab")).toHaveTextContent(
      "이슈 모아 보기 (7)",
    ),
  );
  expect(issues.getByTestId("list-download")).toBeVisible();
  expect(await title.findByRole("button", { name: "이슈 등록" })).toBeVisible();
  fireEvent.click(screen.getByTestId("project-board-tab"));
  await waitFor(() => expect(title.queryAllByRole("button")).toHaveLength(0));
});

it("mobile card shows name + light, stage with plan/actual and issue counts", async () => {
  viewport.mobile = true;
  mount();
  const card = await screen.findByTestId("list-card-selected");
  expect(card).toHaveTextContent("지연 프로젝트위험");
  expect(card).toHaveTextContent("개발 · 계획 50.5% / 실적 38.0%");
  expect(card).toHaveTextContent("이슈 2 (지남 1)");
});

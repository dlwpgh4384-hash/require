import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ProjectLink } from "./ProjectLink";
import { subscribeProjectDistribution, subscribeProjectEdit } from "./projectEdit";
import type { Project } from "./projects";

// 다른 화면(철수 리스트 상세·개발자 풀·거래처)의 표 안에서 연 프로젝트 상세에서 인원 계획을 저장하면
// 완료 알림이 보이고, 다시 고치면 [저장]이 다시 살아나야 한다(2026-11-19 사용자 발견).
const ok = (data: unknown) => ({ ok: true, status: 200, json: async () => ({ success: true, meta: {}, data }) });
const project = {
  projectId: "P-1",
  projectName: "링크 프로젝트",
  clientName: "고객사",
  contractTypeCode: "SI",
  contractTypeName: "SI",
  statusCode: "PROG",
  statusName: "진행",
  startDate: "2026-01-01",
  endDate: "2026-12-31",
  canSaveManpowerPlan: true,
  canEdit: true,
};
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const path = new URL(String(url), "http://localhost").pathname;
    if (path.endsWith("/manpower-plan") && init?.method === "PUT") return ok({ items: [] });
    if (path.endsWith("/manpower-plan"))
      return ok({ items: [{ jobCode: "BE", jobName: "백엔드", plannedMm: 3, actualMm: 0, differenceMm: -3 }], canSave: true });
    if (path === "/api/common/projects/P-1") return ok(project);
    if (path.endsWith("/budget-rule")) return ok({ targetMarginRate: 20, budgetFactor: 0.8 });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("shows the save notice and re-enables save after editing again, when opened from another screen", async () => {
  render(<ProjectLink projectId="P-1" name="링크 프로젝트" testId="link-p1" />);
  fireEvent.click(screen.getByTestId("link-p1"));
  const detail = within(await screen.findByTestId("project-detail"));
  fireEvent.click(await detail.findByRole("tab", { name: "인원 계획" }));
  const input = await detail.findByTestId("project-job-mm-be");
  fireEvent.change(input, { target: { value: "4" } });
  const save = detail.getByTestId("project-plan-save");
  expect(save).toBeEnabled();
  fireEvent.click(save);
  expect(await screen.findByTestId("project-link-toast")).toHaveTextContent("인원 계획을 저장했습니다.");
  await waitFor(() =>
    expect(
      request.mock.calls.some(([url, init]) => String(url).endsWith("/manpower-plan") && init?.method === "PUT"),
    ).toBe(true),
  );
  // 저장 뒤에는 바뀐 것이 없어 잠기고, 다시 고치면 살아난다.
  await waitFor(() => expect(detail.getByTestId("project-plan-save")).toBeDisabled());
  fireEvent.change(await detail.findByTestId("project-job-mm-be"), { target: { value: "5" } });
  expect(detail.getByTestId("project-plan-save")).toBeEnabled();
});

// 시안 editform: 다른 화면에서 연 상세의 [수정]은 상세를 닫고 프로젝트(UI-01) 화면의 수정 폼을 연다(2026-11-19 사용자 결정).
it("[수정] in a detail opened from another screen closes it and asks the project screen to open its edit form", async () => {
  const received: Project[] = [];
  render(
    <MemoryRouter initialEntries={["/screens/UI-05"]}>
      <Routes>
        <Route path="/screens/UI-05" element={<ProjectLink projectId="P-1" name="링크 프로젝트" testId="link-p1" />} />
        <Route path="/screens/UI-01" element={<p data-testid="project-screen">프로젝트 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByTestId("link-p1"));
  const detail = within(await screen.findByTestId("project-detail"));
  fireEvent.click(await detail.findByTestId("project-edit"));
  expect(await screen.findByTestId("project-screen")).toBeInTheDocument();
  expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument();
  // 프로젝트 화면이 열리면서 받아 갈 요청이 남아 있다.
  const stop = subscribeProjectEdit((p) => received.push(p));
  stop();
  expect(received.map((p) => p.projectId)).toEqual(["P-1"]);
});

// 다른 화면에서 연 상세도 프로젝트 화면과 같은 기능: 진행 현황 [이번 주 상태 적기], 이슈 [이슈 등록](2026-11-19 사용자 결정).
it("a detail opened from another screen has the weekly memo and issue registration like the project screen", async () => {
  render(<ProjectLink projectId="P-1" name="링크 프로젝트" testId="link-p1" />);
  fireEvent.click(screen.getByTestId("link-p1"));
  const detail = within(await screen.findByTestId("project-detail"));
  fireEvent.click(await detail.findByRole("tab", { name: "진행 현황" }));
  expect(await detail.findByTestId("project-memo-add")).toHaveTextContent("이번 주 상태 적기");
  fireEvent.click(detail.getByRole("tab", { name: /^이슈/ }));
  expect(await detail.findByTestId("project-issues-panel")).toBeInTheDocument();
  expect(await detail.findByTestId("project-issue-create")).toHaveTextContent("이슈 등록");
});

it("a read-only detail opened from another screen shows no weekly memo or issue registration", async () => {
  render(<ProjectLink projectId="P-1" name="링크 프로젝트" testId="link-p1" readOnly />);
  fireEvent.click(screen.getByTestId("link-p1"));
  const detail = within(await screen.findByTestId("project-detail"));
  fireEvent.click(await detail.findByRole("tab", { name: "진행 현황" }));
  await waitFor(() => expect(detail.queryByText("주간 메모")).toBeInTheDocument());
  expect(detail.queryByTestId("project-memo-add")).not.toBeInTheDocument();
  fireEvent.click(detail.getByRole("tab", { name: /^이슈/ }));
  expect(detail.queryByTestId("project-issues-panel")).not.toBeInTheDocument();
  expect(detail.queryByTestId("project-issue-create")).not.toBeInTheDocument();
});

// 시안 distopen: 다른 화면(개발자 풀 등)에서 연 턴키 상세에도 [수정] 옆 [배분표]가 있고, 누르면 상세를 닫고 프로젝트 화면의 배분표로 간다.
it("턴키 상세의 [배분표]는 다른 화면에서 열어도 보이고, 프로젝트 화면 배분표로 요청을 넘긴다", async () => {
  request.mockImplementation(async (url: string) => {
    const path = new URL(String(url), "http://localhost").pathname;
    if (path === "/api/common/projects/P-1") return ok({ ...project, contractTypeCode: "TK", contractTypeName: "턴키" });
    return ok({ items: [] });
  });
  const received: string[] = [];
  render(
    <MemoryRouter initialEntries={["/screens/UI-02"]}>
      <Routes>
        <Route path="/screens/UI-02" element={<ProjectLink projectId="P-1" name="링크 프로젝트" testId="link-p1" />} />
        <Route path="/screens/UI-01" element={<p data-testid="project-screen">프로젝트 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByTestId("link-p1"));
  const detail = within(await screen.findByTestId("project-detail"));
  const open = await detail.findByTestId("project-distribution-open");
  expect(open).toHaveTextContent("배분표");
  fireEvent.click(open);
  expect(await screen.findByTestId("project-screen")).toBeInTheDocument();
  expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument();
  const stop = subscribeProjectDistribution((id) => received.push(id));
  stop();
  expect(received).toEqual(["P-1"]);
});

it("턴키가 아니면 [배분표]가 없다", async () => {
  render(<ProjectLink projectId="P-1" name="링크 프로젝트" testId="link-p1" />);
  fireEvent.click(screen.getByTestId("link-p1"));
  const detail = within(await screen.findByTestId("project-detail"));
  await detail.findByTestId("project-edit");
  expect(detail.queryByTestId("project-distribution-open")).not.toBeInTheDocument();
});

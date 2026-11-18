import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ProjectLink } from "./ProjectLink";

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

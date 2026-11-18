import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { ProjectPage } from "./ProjectPage";
import { ProjectDistribution } from "./ProjectDistribution";
import { ProjectWorkspaceProvider, ProjectTitleActions } from "./ProjectWorkspace";
import type { Project } from "./projects";
import { requestProjectEdit } from "./projectEdit";

const viewport = vi.hoisted(() => ({ mobile: false, width: 1280 }));
vi.mock("../../shared/layout/useViewport", () => ({ useViewport: () => viewport }));

let row: Project;
let rows: Project[];
let request: ReturnType<typeof vi.fn>;
let manpowerCanSave: boolean;
let history: { changedAt: string; itemName: string; beforeValue: string | null; afterValue: string | null; changedBy: string }[];
const ok = (data: unknown) => ({ ok: true, status: 200, json: async () => ({ success: true, meta: {}, data: structuredClone(data) }) });
const writes = () => request.mock.calls.filter((c) => ["POST", "PUT"].includes((c[1] as RequestInit | undefined)?.method ?? "GET"));
const body = (call: unknown[]) => JSON.parse(((call[1] as RequestInit).body as string) ?? "{}");
const base = (over: Partial<Project> = {}): Project => ({
  projectId: "P-0001",
  projectName: "가온은행 모바일 앱",
  customerId: "customer",
  customerName: "가온은행",
  contractTypeCode: "TK",
  contractTypeName: "턴키",
  startDate: "2026-10-01",
  endDate: "2027-03-31",
  salesPartId: "part",
  salesPartName: "영업1파트",
  workplace: "서울 중구 을지로 66",
  statusCode: "PRE",
  statusName: "수주예정",
  estimateAmount: 900000,
  contractAmount: 1000000,
  originalContractAmount: 1000000,
  budgetAmount: 800000,
  budgetManualYn: "N",
  teamExpenseAmount: 50000,
  projectExpenseAmount: 50000,
  feature: "특징",
  modifiedAt: "2026-11-14T01:00:00Z",
  pmName: "담당PM",
  trafficLight: null,
  canEdit: true,
  canChangeBudget: true,
  canChangeSalesPart: true,
  canSaveManpowerPlan: true,
  canLose: true,
  canStart: true,
  assignedPeopleCount: 2,
  preContractPeopleCount: 2,
  distributionStepName: null,
  distributionMismatchCount: 0,
  distributionTotalWarning: false,
  loseDate: null,
  loseReasonName: null,
  loseMemo: null,
  assignments: [{ name: "김개발", jobName: "백엔드 개발", startDate: "2026-11-01", endDate: "2027-01-31", mm: 3 }],
  issues: [],
  progress: { plannedProgressRate: null, actualProgressRate: null, steps: [] },
  ...over,
});

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  viewport.mobile = false;
  manpowerCanSave = true;
  history = [{ changedAt: "2026-11-14T16:30:00Z", itemName: "계약", beforeValue: null, afterValue: "1,000,000 원", changedBy: "시스템 관리자" }];
  row = base();
  rows = [
    row,
    base({ projectId: "P-0002", projectName: "사내 AI 플랫폼", customerId: null, customerName: "(자체)", contractTypeCode: "IN", contractTypeName: "내부(자체 개발)", salesPartId: null, salesPartName: null, statusCode: "ING", statusName: "진행", startDate: "2026-01-01", endDate: "2027-12-31", contractAmount: 0 }),
    base({ projectId: "P-0003", projectName: "파트 없는 운영", contractTypeCode: "MO", contractTypeName: "월단위", salesPartId: null, salesPartName: null, statusCode: "ING", statusName: "진행", startDate: "2026-02-01", endDate: "2026-12-31" }),
    base({ projectId: "P-0004", projectName: "지난해 구축", statusCode: "END", statusName: "종료", startDate: "2025-01-01", endDate: "2025-12-31", salesPartId: "HQ", salesPartName: "본사" }),
  ];
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const path = url.split("?")[0];
    if (init?.method === "PUT" || init?.method === "POST") {
      if (path === "/api/common/projects") {
        const input = JSON.parse(init.body as string);
        const created = base({ ...input, projectId: "P-0009", statusName: "진행" });
        rows = [...rows, created];
        return ok(created);
      }
      if (path.endsWith("/start")) Object.assign(row, { statusCode: "ING", statusName: "진행", canLose: false, canStart: false });
      if (path.endsWith("/lose")) Object.assign(row, { statusCode: "LOSE", statusName: "실주" });
      if (path.endsWith("/manpower-plan")) return ok({ items: [], canSave: true });
      return ok(row);
    }
    if (path === "/api/common/projects") return ok({ items: rows, unassignedCount: 1 });
    if (path === "/api/common/projects/budget-rule") return ok({ targetMarginRate: 20, budgetFactor: 0.8 });
    if (path.endsWith("/direct-expenses"))
      return ok({ items: [{ expenseId: "202611:1", yearMonth: "2026-11", itemCode: "TRIP", itemName: "출장비", amount: 15000, memo: "지방 점검" }] });
    if (path.endsWith("/sales-parts"))
      return ok({ items: [{ salesPartId: "part", salesPartName: "영업1파트", effectiveMonth: "2026-10", reason: null, changedBy: "관리자", scheduled: false }] });
    if (path.endsWith("/change-history")) return ok({ items: history });
    if (path.endsWith("/manpower-plan"))
      return ok({ items: [{ jobCode: "BE", jobName: "백엔드 개발", plannedMm: null, actualMm: 3, differenceMm: null }], canSave: manpowerCanSave });
    if (path.endsWith("/lose-impact")) return ok({ activePeopleCount: 1, futurePeopleCount: 2 });
    if (path.endsWith("/distribution"))
      return ok({ project: row, items: [], uploads: [], mismatches: [], totalAmountWarning: false });
    if (path.startsWith("/api/common/projects/")) {
      const id = decodeURIComponent(path.split("/")[4]);
      return ok(rows.find((r) => r.projectId === id) ?? row);
    }
    if (path.includes("code-groups/") && path.endsWith("/codes")) {
      const group = path.split("/").at(-2);
      const codes: Record<string, unknown[]> = {
        CTRT_TYPE: [{ code: "TK", codeName: "턴키" }, { code: "MO", codeName: "월단위" }, { code: "IN", codeName: "내부(자체 개발)" }],
        PROJ_STAT: [{ code: "ING", codeName: "진행" }, { code: "PRE", codeName: "수주예정" }, { code: "END", codeName: "종료" }, { code: "LOSE", codeName: "실주" }],
        ALWNC: [{ code: "TEAM_FEE", codeName: "팀비", codeVal: 50000 }, { code: "PEXP_FEE", codeName: "프로젝트경비", codeVal: 50000 }],
        JOB: [{ code: "BE", codeName: "백엔드 개발" }, { code: "PM", codeName: "PM·사업관리" }],
        LOSE_RSN: [{ code: "PRICE", codeName: "가격" }],
        DRCT_EXPS_ITEM: [{ code: "TRIP", codeName: "출장비" }],
      };
      return ok({ items: codes[group!] ?? [] });
    }
    if (path === "/api/common/clients") return ok({ items: [{ clientId: "customer", clientName: "가온은행", clientTypeCode: "CUST", useYn: "Y" }] });
    if (path === "/api/common/parts") return ok({ items: [{ partId: "part", partName: "영업1파트", useYn: "Y" }, { partId: "part2", partName: "영업2파트", useYn: "Y" }] });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
const shell = () =>
  render(
    <ProjectWorkspaceProvider>
      <ProjectTitleActions />
      <ProjectPage />
    </ProjectWorkspaceProvider>,
  );
const openDetail = async (id = "P-0001") => {
  fireEvent.click(await screen.findByTestId(`list-row-${id}`));
  return within(await screen.findByTestId("project-detail"));
};

it("list follows the mockup: title actions, year bar, badges, internal and HQ labels", async () => {
  shell();
  const title = await screen.findByTestId("project-title-actions");
  await waitFor(() => expect(within(title).getByTestId("project-register")).toBeVisible());
  expect(within(title).getByText("양식 내려받기")).toBeVisible();
  expect(within(title).getByText("엑셀 업로드")).toBeVisible();
  expect(screen.getByTestId("project-year-year")).toHaveTextContent("2026년");
  expect(screen.getByText("올해")).toBeVisible();
  expect(screen.getByText("그 해에 수행 기간이 걸친 프로젝트")).toBeVisible();
  const list = within(screen.getByTestId("project-list"));
  expect(list.getAllByRole("columnheader").map((h) => h.textContent?.replace(/[▲▼]/g, "").trim())).toEqual(
    expect.arrayContaining(["프로젝트ID", "프로젝트명", "고객사", "계약유형", "계약 기간", "계약금액", "투입 인원", "영업 파트", "상태", "배분표"]),
  );
  const internal = within(screen.getByTestId("list-row-P-0002"));
  expect(internal.getByText("내부")).toBeVisible();
  expect(internal.getByText("없음(내부)")).toBeVisible();
  expect(within(screen.getByTestId("list-row-P-0003")).getByText("미지정")).toBeVisible();
  expect(within(screen.getByTestId("list-row-P-0001")).getByText("올리기")).toBeVisible();
  // 지난해(2025)만 걸친 프로젝트는 올해 목록에 없다
  expect(screen.queryByTestId("list-row-P-0004")).not.toBeInTheDocument();
});

it("the unassigned banner counts non-internal projects and filters them with an info toast", async () => {
  shell();
  expect(await screen.findByTestId("project-unassigned-banner")).toHaveTextContent("영업 파트가 없는 프로젝트가 1건 있습니다.");
  fireEvent.click(screen.getByTestId("project-unassigned-only"));
  expect(await screen.findByTestId("project-toast")).toHaveTextContent("영업 파트가 없는 프로젝트만 보여 줍니다.");
  expect(screen.getByTestId("list-row-P-0003")).toBeVisible();
  expect(screen.queryByTestId("list-row-P-0001")).not.toBeInTheDocument();
  expect(screen.queryByTestId("list-row-P-0002")).not.toBeInTheDocument();
});

it("conditions apply only on 조회 and match name or customer", async () => {
  shell();
  await screen.findByTestId("list-row-P-0001");
  fireEvent.change(screen.getByTestId("project-keyword"), { target: { value: "사내" } });
  expect(screen.getByTestId("list-row-P-0001")).toBeVisible();
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() => expect(screen.queryByTestId("list-row-P-0001")).not.toBeInTheDocument());
  expect(screen.getByTestId("list-row-P-0002")).toBeVisible();
});

it("a past year is read only: no title actions and no detail writes", async () => {
  shell();
  await screen.findByTestId("list-row-P-0001");
  fireEvent.click(screen.getByTestId("project-year-prev"));
  expect(await screen.findByText("지난 해 · 읽기 전용")).toBeVisible();
  expect(screen.queryByTestId("project-register")).not.toBeInTheDocument();
  const detail = await openDetail("P-0004");
  expect(await detail.findByText("지난해 구축")).toBeVisible();
  expect(detail.queryByTestId("project-edit")).not.toBeInTheDocument();
  expect(detail.queryByTestId("project-budget-change")).not.toBeInTheDocument();
  expect(detail.getByText("본사")).toBeVisible();
  expect(detail.getByText("직접 영업")).toBeVisible();
});

it("detail panel shows the mockup header, tabs and pre-contract warning", async () => {
  shell();
  const detail = await openDetail();
  expect(await detail.findByText("P-0001 · 가온은행 · 턴키")).toBeVisible();
  expect(detail.getAllByRole("tab").map((t) => t.textContent)).toEqual([
    "기본 정보",
    "진행 현황",
    "이슈 (0)",
    "인원 계획",
    "투입 인력 (1)",
    "계약 변경 이력",
  ]);
  expect(detail.getByText("계약 전에 2명이 먼저 투입되었습니다(미계약 선투입).", { exact: false })).toBeVisible();
  expect(detail.getByText("1,000,000 원")).toBeVisible();
  expect(detail.getByText("50,000 원 · 50,000 원 (1인당)")).toBeVisible();
  expect(detail.getByText("출장비")).toBeVisible();
});

it("budget change validates, saves through PUT and shows the mockup toast", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-budget-change"));
  const dialog = within(await screen.findByTestId("project-command-dialog"));
  expect(dialog.getByText("기본값(계약금액 × (1 − 목표 마진율 20%))은 800,000원입니다.")).toBeVisible();
  fireEvent.change(dialog.getByTestId("project-budget-value"), { target: { value: "" } });
  fireEvent.click(dialog.getByTestId("project-command-save"));
  expect(dialog.getByTestId("project-command-error")).toHaveTextContent("예산은 0보다 커야 합니다.");
  expect(writes()).toHaveLength(0);
  fireEvent.change(dialog.getByTestId("project-budget-value"), { target: { value: "900000" } });
  expect(dialog.getByTestId("project-budget-value")).toHaveValue("900,000");
  fireEvent.click(dialog.getByTestId("project-command-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(writes()[0][0]).toBe("/api/common/projects/P-0001/budget");
  expect(body(writes()[0])).toEqual({ budgetAmount: 900000, modifiedAt: "2026-11-14T01:00:00Z" });
  expect(await screen.findByTestId("project-toast")).toHaveTextContent("예산(목표 원가)을 바꿨습니다.");
});

it("sales part change offers other parts and HQ and reports the month", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-part-change"));
  const dialog = within(await screen.findByTestId("project-command-dialog"));
  const options = [...(dialog.getByTestId("project-command-part") as HTMLSelectElement).options].map((o) => o.text);
  expect(options).toEqual(["영업2파트", "본사(직접 영업)"]);
  expect(dialog.getByText("마감 안 된 달(2026-11)부터 고를 수 있습니다.")).toBeVisible();
  fireEvent.click(dialog.getByTestId("project-command-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body(writes()[0])).toEqual({ salesPartId: "part2", effectiveMonth: "2026-11" });
  expect(await screen.findByTestId("project-toast")).toHaveTextContent("영업 파트를 영업2파트(으)로 바꿨습니다 · 2026-11부터 적용");
});

it("direct expense add needs an amount and delete asks first", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-expense-add"));
  const dialog = within(await screen.findByTestId("project-command-dialog"));
  fireEvent.click(dialog.getByTestId("project-command-save"));
  expect(dialog.getByTestId("project-command-error")).toHaveTextContent("금액을 넣어 주세요.");
  fireEvent.change(dialog.getByTestId("project-command-amount"), { target: { value: "30000" } });
  fireEvent.click(dialog.getByTestId("project-command-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body(writes()[0])).toEqual({ yearMonth: "2026-11", itemCode: "TRIP", amount: 30000 });
  expect(await screen.findByTestId("project-toast")).toHaveTextContent("직접 경비를 추가했습니다.");
  fireEvent.click(await detail.findByTestId("project-expense-delete-202611-1"));
  const confirm = within(await screen.findByTestId("project-expense-delete-confirm"));
  expect(confirm.getByText("2026-11 출장비 15,000원을 지울까요?")).toBeVisible();
  expect(writes()).toHaveLength(1);
});

it("the ⋯ menu: 진행으로 confirms first, 실주 처리 needs a reason and shows the impact", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-more"));
  fireEvent.click(detail.getByTestId("project-start"));
  const confirm = within(await screen.findByTestId("project-start-confirm"));
  expect(confirm.getByText("「가온은행 모바일 앱」를 진행으로 바꿀까요?")).toBeVisible();
  fireEvent.click(confirm.getByText("취소"));
  expect(writes()).toHaveLength(0);
  fireEvent.click(detail.getByTestId("project-more"));
  fireEvent.click(detail.getByTestId("project-lose"));
  const dialog = within(await screen.findByTestId("project-command-dialog"));
  expect(await dialog.findByText("투입 중인 사람 1명은 오늘 날짜로 철수, 시작 전 2명은 배정 취소됩니다.", { exact: false })).toBeVisible();
  fireEvent.click(dialog.getByTestId("project-command-save"));
  expect(dialog.getByTestId("project-command-error")).toHaveTextContent("실주 사유를 골라 주세요.");
  fireEvent.change(dialog.getByTestId("project-command-reasoncode"), { target: { value: "PRICE" } });
  fireEvent.click(dialog.getByTestId("project-command-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body(writes()[0])).toEqual({ reasonCode: "PRICE" });
  expect(await screen.findByTestId("project-toast")).toHaveTextContent("실주 처리했습니다. 1명 오늘 철수. 2명 배정 취소.");
});

it("a lost project is read only with the lost band", async () => {
  Object.assign(row, { statusCode: "LOSE", statusName: "실주", loseDate: "2026-11-10", loseReasonName: "가격", loseMemo: "단가 차이", canEdit: false });
  shell();
  const detail = await openDetail();
  expect(await detail.findByText("실주 2026-11-10 · 사유: 가격 — 단가 차이", { exact: false })).toBeVisible();
  expect(detail.queryByTestId("project-edit")).not.toBeInTheDocument();
  expect(detail.queryByTestId("project-more")).not.toBeInTheDocument();
  expect(detail.queryByTestId("project-budget-change")).not.toBeInTheDocument();
  expect(detail.queryByTestId("project-expense-add")).not.toBeInTheDocument();
});

it("manpower plan saves planned MM, and a denied plan is read only", async () => {
  shell();
  let detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-detail-tab-plan"));
  fireEvent.change(await detail.findByTestId("project-job-mm-be"), { target: { value: "2.5" } });
  expect(detail.getByText("초과 0.50")).toBeVisible();
  expect(detail.getByText("저장하지 않은 변경이 있습니다")).toBeVisible();
  fireEvent.click(detail.getByTestId("project-plan-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body(writes()[0])).toEqual({ jobs: [{ jobCode: "BE", plannedMm: 2.5 }] });
  cleanup();
  manpowerCanSave = false;
  shell();
  detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-detail-tab-plan"));
  expect(await detail.findByText("지금은 보기만 할 수 있습니다.")).toBeVisible();
  expect(detail.queryByTestId("project-job-mm-be")).not.toBeInTheDocument();
});

it("a blank plan with assignments shows 초과 and Ctrl+S saves the plan", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-detail-tab-plan"));
  expect(await detail.findByText("초과 3.00")).toBeVisible();
  fireEvent.change(detail.getByTestId("project-job-mm-be"), { target: { value: "3" } });
  expect(detail.getByText("맞음")).toBeVisible();
  await act(async () => {
    fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  });
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body(writes()[0])).toEqual({ jobs: [{ jobCode: "BE", plannedMm: 3 }] });
});

it("edit sends the read modifiedAt and shows the conflict message on 409", async () => {
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    init?.method === "PUT" && url === "/api/common/projects/P-0001"
      ? { ok: false, status: 409, json: async () => ({ success: false, message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.", errors: [] }) }
      : original(url, init),
  );
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-edit"));
  const form = within(await screen.findByTestId("project-form"));
  await waitFor(() => expect(form.getByTestId("project-save")).toBeEnabled());
  fireEvent.change(form.getByTestId("project-projectName"), { target: { value: "이름 바꿈" } });
  fireEvent.click(form.getByTestId("project-save"));
  expect(await form.findByTestId("project-form-error")).toHaveTextContent("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
  expect(body(writes()[0]).modifiedAt).toBe("2026-11-14T01:00:00Z");
});

it("change history shows the Korean date of a UTC time", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-detail-tab-history"));
  // 2026-11-14T16:30Z = 한국 2026-11-15 01:30
  expect(await detail.findByText("2026-11-15")).toBeVisible();
});

it("registration form: mockup cards, required messages, budget default from the server rule", async () => {
  shell();
  fireEvent.click(await screen.findByTestId("project-register"));
  const form = within(await screen.findByTestId("project-form"));
  expect(form.getByText("기본 정보")).toBeVisible();
  expect(form.getByText("금액")).toBeVisible();
  await waitFor(() => expect(form.getByTestId("project-teamExpenseAmount")).toHaveValue("50,000"));
  fireEvent.click(form.getByTestId("project-save"));
  expect(form.getByText("프로젝트명을 입력해 주세요.")).toBeVisible();
  expect(form.getByText("계약유형을 입력해 주세요.")).toBeVisible();
  expect(form.getByText("계약금액을 입력해 주세요.")).toBeVisible();
  fireEvent.change(form.getByTestId("project-contractAmount"), { target: { value: "1000000" } });
  expect(form.getByTestId("project-contractAmount")).toHaveValue("1,000,000");
  expect(form.getByTestId("project-budgetAmount")).toHaveValue("800,000");
  fireEvent.change(form.getByTestId("project-startDate"), { target: { value: "2026-12-01" } });
  fireEvent.change(form.getByTestId("project-endDate"), { target: { value: "2026-11-01" } });
  fireEvent.click(form.getByTestId("project-save"));
  expect(form.getByText("종료일이 시작일보다 앞입니다. 날짜를 다시 확인해 주세요.")).toBeVisible();
  expect(writes()).toHaveLength(0);
});

it("registration saves once, sends the automatic budget only as default and opens the new detail", async () => {
  shell();
  fireEvent.click(await screen.findByTestId("project-register"));
  const form = within(await screen.findByTestId("project-form"));
  await waitFor(() => expect(form.getByTestId("project-teamExpenseAmount")).toHaveValue("50,000"));
  fireEvent.change(form.getByTestId("project-projectName"), { target: { value: "새 프로젝트" } });
  fireEvent.change(form.getByTestId("project-customerId"), { target: { value: "customer" } });
  fireEvent.change(form.getByTestId("project-contractTypeCode"), { target: { value: "TK" } });
  fireEvent.change(form.getByTestId("project-startDate"), { target: { value: "2026-11-01" } });
  fireEvent.change(form.getByTestId("project-endDate"), { target: { value: "2027-04-30" } });
  fireEvent.change(form.getByTestId("project-salesPartId"), { target: { value: "HQ" } });
  fireEvent.change(form.getByTestId("project-contractAmount"), { target: { value: "2000000" } });
  await act(async () => {
    fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  });
  await waitFor(() => expect(writes()).toHaveLength(1));
  const sent = body(writes()[0]);
  expect(sent).toMatchObject({ projectName: "새 프로젝트", contractTypeCode: "TK", statusCode: "ING", salesPartId: "HQ", contractAmount: 2000000 });
  expect(sent).not.toHaveProperty("budgetAmount");
  expect(await screen.findByTestId("project-toast")).toHaveTextContent("저장했습니다.");
  expect(await screen.findByTestId("project-detail")).toBeVisible();
});

it("edit form locks type, status and part with the mockup notes", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-edit"));
  const form = within(await screen.findByTestId("project-form"));
  expect(form.getByTestId("project-contractTypeCode")).toHaveAttribute("readonly");
  expect(form.getByText("계약유형은 등록 후 바꿀 수 없습니다. 원가·매출 계산 방식이 달라지기 때문입니다.")).toBeVisible();
  expect(form.getByText("상태는 여기서 바꿀 수 없습니다. 수주예정이면 프로젝트 상세의 [⋯]에서 진행 또는 실주로 바꿉니다.")).toBeVisible();
  expect(form.getByTestId("project-salesPartId")).toHaveValue("영업1파트");
});

it("a dirty form asks before leaving", async () => {
  shell();
  fireEvent.click(await screen.findByTestId("project-register"));
  const form = within(await screen.findByTestId("project-form"));
  await waitFor(() => expect(form.getByTestId("project-teamExpenseAmount")).toHaveValue("50,000"));
  fireEvent.change(form.getByTestId("project-projectName"), { target: { value: "입력 중" } });
  fireEvent.click(form.getByTestId("project-cancel"));
  expect(await screen.findByTestId("project-leave-confirm")).toBeVisible();
  fireEvent.click(screen.getByTestId("project-leave-go"));
  expect(await screen.findByTestId("project-page")).toBeVisible();
});

it("distribution: empty state text, and the phone shows the PC-only notice", async () => {
  shell();
  fireEvent.click(await screen.findByTestId("project-distribution-p-0001"));
  expect(await screen.findByTestId("project-distribution-empty")).toHaveTextContent("아직 올린 배분표가 없습니다.");
  expect(screen.getByTestId("project-distribution-summary")).toHaveTextContent("P-0001 · 가온은행 · 계약금액 1,000,000원 · 2026-10-01 ~ 2027-03-31");
  cleanup();
  viewport.mobile = true;
  render(<ProjectDistribution projectId="P-0001" onBack={() => undefined} />);
  expect(screen.getByText("배분표는 PC 전용 화면입니다. PC에서 이용해 주세요.")).toBeVisible();
});

it("phone width uses the shared cards with the mockup rows", async () => {
  viewport.mobile = true;
  shell();
  const card = within(await screen.findByTestId("list-card-P-0001"));
  expect(card.getByText("가온은행 모바일 앱")).toBeVisible();
  expect(card.getByText("수주예정")).toBeVisible();
  expect(card.getByText("가온은행 · 턴키")).toBeVisible();
  expect(card.getByText("2명")).toBeVisible();
});

it("시안 UI-01: 등록 폼은 글자 칸을 떠날 때, 고르는 칸·날짜는 바꿀 때 그 칸 아래에 문구를 보인다", async () => {
  shell();
  fireEvent.click(await screen.findByTestId("project-register"));
  const form = within(await screen.findByTestId("project-form"));
  await waitFor(() => expect(form.getByTestId("project-teamExpenseAmount")).toHaveValue("50,000"));
  fireEvent.blur(form.getByTestId("project-projectName"));
  expect(form.getByText("프로젝트명을 입력해 주세요.")).toBeVisible();
  fireEvent.blur(form.getByTestId("project-contractAmount"));
  expect(form.getByText("계약금액을 입력해 주세요.")).toBeVisible();
  // 시안: 상태도 「선택」이 있고 처음 값은 진행
  const status = form.getByTestId("project-statusCode") as HTMLSelectElement;
  expect(status).toHaveValue("ING");
  expect([...status.options].map((o) => o.text)).toEqual(["선택", "진행", "수주예정"]);
  fireEvent.change(status, { target: { value: "" } });
  expect(form.getByText("상태를 입력해 주세요.")).toBeVisible();
  fireEvent.change(form.getByTestId("project-startDate"), { target: { value: "2026-12-01" } });
  fireEvent.change(form.getByTestId("project-endDate"), { target: { value: "2026-11-01" } });
  expect(form.getByText("종료일이 시작일보다 앞입니다. 날짜를 다시 확인해 주세요.")).toBeVisible();
  fireEvent.change(form.getByTestId("project-projectName"), { target: { value: "새 이름" } });
  fireEvent.blur(form.getByTestId("project-projectName"));
  expect(form.queryByText("프로젝트명을 입력해 주세요.")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("시안 UI-01: 실주 사유·경비 월·항목·금액에 필수 표시(*)", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-expense-add"));
  let dialog = within(await screen.findByTestId("project-command-dialog"));
  for (const label of ["경비 월", "항목", "금액"]) expect(dialog.getByText(label).closest("label")).toHaveTextContent(`${label} *`);
  expect(dialog.getByText("메모").closest("label")).not.toHaveTextContent("*");
  fireEvent.click(dialog.getByTestId("project-command-cancel"));
  fireEvent.click(detail.getByTestId("project-more"));
  fireEvent.click(detail.getByTestId("project-lose"));
  dialog = within(await screen.findByTestId("project-command-dialog"));
  expect(dialog.getByText("실주 사유").closest("label")).toHaveTextContent("실주 사유 *");
});

it("시안 UI-01: 영업 파트 적용 시작 월이 틀리면 창을 두고 info 알림(마감 다음 달 / 지금 파트 다음 달)", async () => {
  row.closedMonth = "2026-10";
  const original = request.getMockImplementation()!;
  let currentFrom = "2026-10";
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    (init?.method ?? "GET") === "GET" && url.endsWith("/sales-parts")
      ? ok({ items: [{ salesPartId: "part", salesPartName: "영업1파트", effectiveMonth: currentFrom, reason: null, changedBy: "관리자", scheduled: false }] })
      : original(url, init),
  );
  shell();
  let detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-part-change"));
  let dialog = within(await screen.findByTestId("project-command-dialog"));
  fireEvent.change(dialog.getByTestId("project-command-effectivemonth"), { target: { value: "2026-10" } });
  fireEvent.click(dialog.getByTestId("project-command-save"));
  expect(await screen.findByTestId("project-detail-toast")).toHaveTextContent("적용 시작 월은 2026-11 이후로 골라 주세요.");
  expect(screen.getByTestId("project-command-dialog")).toBeVisible();
  expect(writes()).toHaveLength(0);
  cleanup();
  currentFrom = "2026-11";
  shell();
  detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-part-change"));
  dialog = within(await screen.findByTestId("project-command-dialog"));
  // 지금 파트와 같은 파트를 고르면 「예약 취소」라 검사하지 않는다 → 다른 파트로 바꾸는 경우를 본다.
  fireEvent.change(dialog.getByTestId("project-command-part"), { target: { value: "part2" } });
  fireEvent.change(dialog.getByTestId("project-command-effectivemonth"), { target: { value: "2026-11" } });
  fireEvent.click(dialog.getByTestId("project-command-save"));
  expect(await screen.findByTestId("project-detail-toast")).toHaveTextContent("적용 시작 월은 2026-12 이후로 골라 주세요.");
  expect(writes()).toHaveLength(0);
});

it("시안 UI-01: 계획 MM이 소수 셋째 자리면 저장하지 않고 직무 이름으로 info 알림", async () => {
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-detail-tab-plan"));
  fireEvent.change(await detail.findByTestId("project-job-mm-be"), { target: { value: "1.234" } });
  expect(detail.getByTestId("project-job-mm-be")).toHaveValue("1.234");
  fireEvent.click(detail.getByTestId("project-plan-save"));
  expect(await screen.findByTestId("project-detail-toast")).toHaveTextContent(
    "백엔드 개발 계획 MM은 0 이상 숫자(소수 둘째 자리까지)로 넣어 주세요.",
  );
  expect(writes()).toHaveLength(0);
});

it("시안 UI-01: 진행 현황 마지막 업데이트가 14일을 넘으면 경고 색", async () => {
  row.memos = [{ writtenDate: "2026-10-01", thisWeek: null, nextWeek: null, request: null, actualProgressRate: null }];
  shell();
  const detail = await openDetail();
  fireEvent.click(await detail.findByTestId("project-detail-tab-progress"));
  const age = await detail.findByTestId("project-last-update-age");
  expect(age).toHaveTextContent("(45일 전)");
  expect(age).toHaveClass("project-warn-text");
  cleanup();
  row.memos = [{ writtenDate: "2026-11-10", thisWeek: null, nextWeek: null, request: null, actualProgressRate: null }];
  shell();
  const fresh = await openDetail();
  fireEvent.click(await fresh.findByTestId("project-detail-tab-progress"));
  expect(await fresh.findByTestId("project-last-update-age")).not.toHaveClass("project-warn-text");
});

it("시안 UI-01: 배분표 불일치 이유 문구", async () => {
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    (init?.method ?? "GET") === "GET" && url.endsWith("/distribution")
      ? ok({
          project: row,
          items: [],
          uploads: [{ uploadedAt: "2026-11-14T01:00:00Z", stepName: "견적", stepCode: "EST", fileName: "a.xlsx", uploadedBy: "관리자" }],
          mismatches: [
            { name: "김개발", kind: "배정 없음", months: ["2026-11"] },
            { name: "이개발", kind: "배분표 없음", months: ["2026-12"] },
          ],
          totalAmountWarning: false,
        })
      : original(url, init),
  );
  render(<ProjectDistribution projectId="P-0001" onBack={() => undefined} />);
  const table = within(await screen.findByTestId("project-distribution-mismatches"));
  expect(table.getByText("배분표에는 금액이 있는데 그 달 투입 배정이 없습니다.")).toBeVisible();
  expect(table.getByText("투입 배정은 있는데 배분표에 줄이 없어 「그 달 원가 × 계약금액 ÷ 예산」으로 계산합니다.")).toBeVisible();
});

// 시안 editform: 다른 화면에서 연 상세의 [수정] 요청을 받으면 이 화면이 수정 폼을 연다(열려 있을 때·나중에 열릴 때 모두).
it("opens the edit form when another screen asks to edit a project", async () => {
  shell();
  await screen.findByTestId("list-row-P-0001");
  act(() => requestProjectEdit(base()));
  const form = within(await screen.findByTestId("project-form"));
  expect(form.getByTestId("project-projectName")).toHaveValue("가온은행 모바일 앱");
});
it("opens the edit form for a request left before the project screen opened", async () => {
  requestProjectEdit(base({ projectId: "P-0003", projectName: "파트 없는 운영" }));
  shell();
  const form = within(await screen.findByTestId("project-form"));
  expect(form.getByTestId("project-projectName")).toHaveValue("파트 없는 운영");
});

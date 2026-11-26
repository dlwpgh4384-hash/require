import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { announcePeriodChanged } from "../userpreferences/userPreferences";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { AssignmentPage } from "./AssignmentPage";
import { AssignmentDetail } from "./AssignmentDetail";
import { AssignmentForm } from "./AssignmentForm";
import {
  addDays,
  assignmentInitialDates,
  assignmentPriceEditable,
  type Assignment,
  type AssignmentCandidate,
  type AssignmentHistory,
} from "./assignments";
import { AssignmentWorkspaceProvider, AssignmentTitleActions } from "./AssignmentWorkspace";
import type { Project } from "../projects/projects";
import {
  subscribeAiRecommendation,
  type AiRecommendationEvent,
} from "../airecommendations/aiRecommendationRequest";

const viewport = vi.hoisted(() => ({ mobile: false, width: 1280 }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
let request: ReturnType<typeof vi.fn>,
  row: Assignment,
  history: AssignmentHistory[],
  candidates: AssignmentCandidate[],
  failure: boolean,
  empty: boolean,
  developer: Record<string, unknown>,
  statuses: unknown[],
  booked: unknown[],
  contracts: unknown[],
  writeFailure: { status: number; message: string; errors?: { field: string; message: string }[] } | null;
let project: Project;
/** 개발자 목록에 더 넣을 사람(목록 응답은 directClassCode만 준다). */
let extraDevelopers: Record<string, unknown>[] = [];
/** 프로젝트 영업 파트 이력(/sales-parts). */
let salesParts: unknown[] = [];
/** true면 파트 이력 요청이 실패(500)한다. */
let salesPartsFail = false;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const codes: Record<string, unknown[]> = {
  JOB: [
    { code: "BE", codeName: "백엔드 개발" },
    { code: "PM", codeName: "PM·사업관리" },
  ],
  ASGN_CHG_RSN: [
    { code: "CUST", codeName: "고객 요청" },
    { code: "HR", codeName: "인력 사정" },
  ],
};
beforeEach(() => {
  extraDevelopers = [];
  salesParts = [];
  salesPartsFail = false;
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T00:00:00Z"));
  failure = false;
  empty = false;
  candidates = [];
  statuses = [];
  booked = [];
  contracts = [];
  writeFailure = null;
  row = {
    assignmentId: "A-0001",
    projectId: "P-0001",
    projectName: "개발 프로젝트",
    developerId: "developer",
    name: "김개발",
    managementNumber: "D-0001",
    jobCode: "BE",
    jobName: "백엔드 개발",
    startDate: "2026-11-01",
    endDate: "2026-12-31",
    billingPrice: 8000000,
    monthlyMm: 1,
    yearMm: 2,
    inputTypeCode: "NEW",
    inputTypeName: "신규",
    extensionPossibilityCode: null,
    extensionPossibilityName: "미정",
    extensionCount: 0,
    warnings: [],
    historyCount: 1,
    modifiedAt: "2026-11-15T00:00:00Z",
  };
  project = {
    projectId: "P-0001",
    projectName: "개발 프로젝트",
    contractTypeCode: "MO",
    contractTypeName: "월단위",
    startDate: "2026-01-01",
    endDate: "2026-12-31",
    statusCode: "ING",
    statusName: "진행",
    salesPartId: "part",
    salesPartName: "영업파트",
    closedMonth: "2026-09",
    assignments: [],
    issues: [],
    progress: { steps: [], plannedProgressRate: null, actualProgressRate: null },
  } as unknown as Project;
  developer = {
    developerId: "developer",
    name: "김개발",
    managementNumber: "D-0001",
    workStatusName: "대기",
    employeeTypeCode: "FT",
    employeeTypeName: "정규직",
    gradeName: "중급",
    primaryJobCode: "PM",
    primaryJobName: "PM·사업관리",
    directClassName: "직접",
    useYn: "Y",
    salesPartId: "part",
    salesPartName: "영업파트",
    organizationName: "개발본부",
    withdrawalDate: null,
    monthlyRate: 8000000,
    retirementDate: null,
  };
  history = [
    {
      historyId: "1",
      assignmentId: row.assignmentId,
      effectiveDate: "2026-11-01",
      changeTypeCode: "INPUT",
      changeTypeName: "투입",
      beforeValue: "",
      afterValue: "종료 2026-12-31 · 월 MM 1.00 · 신규",
      reason: "",
      memo: "",
      changedBy: "admin",
      changedAt: row.modifiedAt,
      retroactiveYn: "N",
      correctedYn: "N",
      modifiedAt: row.modifiedAt,
    },
  ];
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const parsed = new URL(url, "http://application"),
      path = parsed.pathname;
    if (writeFailure && (init?.method === "POST" || init?.method === "PUT")) {
      const f = writeFailure;
      writeFailure = null;
      return { ok: false, status: f.status, json: async () => ({ success: false, message: f.message, errors: f.errors ?? [] }) };
    }
    if (path.includes("code-groups/") && path.endsWith("/codes")) return ok({ items: codes[path.split("/").at(-2)!] ?? [] });
    if (path === "/api/common/projects") return ok({ items: [project], unassignedCount: 0 });
    if (path.startsWith("/api/common/projects/")) {
      if (path.endsWith("/distribution")) return ok({ project, items: [], uploads: [], mismatches: [], totalAmountWarning: false });
      if (path.endsWith("/manpower-plan")) return ok({ items: [], canSave: false });
      if (path.endsWith("/sales-parts"))
        return salesPartsFail
          ? { ok: false, status: 500, json: async () => ({ success: false, message: "파트 이력 조회 실패", errors: [] }) }
          : ok({ items: salesParts });
      if (["/direct-expenses", "/change-history"].some((s) => path.endsWith(s))) return ok({ items: [] });
      return ok(project);
    }
    // 실제 개발자 목록 API처럼 파트 이름(salesPartName)은 빼고 준다: 경고 글의 파트 이름은 상세에서 읽는다.
    if (path === "/api/common/developers")
      return ok({ items: [developer, ...extraDevelopers].map(({ salesPartName: _name, ...row }) => row) });
    if (path.endsWith("/statuses")) return ok({ items: statuses, canManageStatuses: false, canCancelPastRetirement: false });
    if (path.startsWith("/api/common/developers/") && path.endsWith("/assignments")) return ok({ items: booked });
    if (path.endsWith("/change-history")) return ok({ items: [] });
    if (path.startsWith("/api/common/developers/")) return ok(developer);
    if (path === "/api/common/assignments/candidates") return ok({ items: candidates });
    if (path === "/api/common/assignments/contract-developers") return ok({ items: contracts });
    if (path === "/api/common/assignments" && init?.method !== "POST")
      return failure
        ? { ok: false, status: 500, json: async () => ({ success: false, message: "목록 조회 실패", errors: [] }) }
        : ok({ items: empty ? [] : [row] });
    if (path.endsWith("/history")) return ok({ items: history });
    if (init?.method === "POST" || init?.method === "PUT") return ok(path.endsWith("/remove") ? { candidateId: "12" } : row);
    if (path === "/api/common/assignments/A-0001") return ok(row);
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
function page(roles?: string[]) {
  return render(
    <MemoryRouter>
      <AssignmentWorkspaceProvider>
        <header data-testid="assignment-title">
          <AssignmentTitleActions />
        </header>
        <AssignmentPage roles={roles} />
      </AssignmentWorkspaceProvider>
    </MemoryRouter>,
  );
}
const inRouter = (node: React.ReactNode) => render(<MemoryRouter>{node}</MemoryRouter>);
const form = (props: Partial<Parameters<typeof AssignmentForm>[0]> = {}) =>
  inRouter(<AssignmentForm projects={[project]} roles={["FIN"]} onClose={vi.fn()} onSaved={vi.fn()} {...props} />);
const detail = (readOnly = false, roles = ["FIN"]) =>
  inRouter(<AssignmentDetail id={row.assignmentId} roles={roles} readOnly={readOnly} onClose={vi.fn()} onChanged={vi.fn()} />);
// 고른 연도 저장(사용자 설정 PERIOD)은 업무 쓰기가 아니라서 뺀다.
const writes = () => request.mock.calls.filter((c) => ["POST", "PUT"].includes((c[1] as RequestInit | undefined)?.method ?? "GET") && !String(c[0]).startsWith("/api/common/user-preferences/"));
const body = (n = 0) => JSON.parse(String((writes()[n][1] as RequestInit).body));
const ready = () => waitFor(() => expect(screen.getByTestId("assignment-form-save")).not.toBeDisabled());
const pick = async () => {
  await ready();
  fireEvent.change(screen.getByTestId("assignment-developer"), { target: { value: "김개발 (D-0001)" } });
  await waitFor(() => expect(screen.getByTestId("assignment-form")).toHaveTextContent("중급 · PM·사업관리"));
};
const openHistory = async () => {
  fireEvent.click(await screen.findByTestId("assignment-detail-tab-history"));
};

it("shows the mockup empty state with a reset button", async () => {
  empty = true;
  page();
  const box = await screen.findByTestId("assignment-empty");
  expect(within(box).getByText("조건에 맞는 결과가 없습니다.")).toBeInTheDocument();
  expect(within(box).getByRole("button", { name: "조건 초기화" })).toBeInTheDocument();
  expect(screen.queryByTestId("assignment-candidates")).not.toBeInTheDocument();
});

it("renders the mockup columns, badges and formats with the year in the MM header", async () => {
  row.extensionPossibilityCode = "H";
  page();
  await screen.findByTestId("list-row-A-0001");
  const heads = within(screen.getByRole("table")).getAllByRole("columnheader").map((h) => h.textContent);
  expect(heads.slice(1)).toEqual(["경고", "프로젝트", "이름", "직무", "투입일", "철수일", "단가", "월 MM", "2026년 MM", "연장 가능성", "투입 구분"]);
  expect(screen.getByText("그 해에 투입 기간이 걸친 배정 · MM은 그 해 합계")).toBeInTheDocument();
  expect(screen.getByTestId("list-cell-A-0001-billingPrice")).toHaveTextContent("8,000,000");
  expect(screen.getByTestId("list-cell-A-0001-name")).toHaveTextContent("김개발D-0001");
  expect(screen.getByTestId("list-cell-A-0001-warnings")).toBeEmptyDOMElement();
  expect(within(screen.getByTestId("list-cell-A-0001-extensionPossibilityCode")).getByText("높음")).toHaveClass("ls-b-ok");
  expect(within(screen.getByTestId("list-cell-A-0001-inputTypeName")).getByText("신규")).toHaveClass("ls-b-neutral");
  expect(screen.getByTestId("list-cell-A-0001-yearMm")).toHaveTextContent("2.00");
  expect(screen.getByTestId("list-count")).toHaveTextContent("총 1건");
});

it("shows turnkey and free support price labels and a dash for unknown extension", async () => {
  project.contractTypeCode = "TK";
  page();
  expect(await screen.findByTestId("list-cell-A-0001-billingPrice")).toHaveTextContent("배분표");
  expect(screen.getByTestId("list-cell-A-0001-extensionPossibilityCode")).toHaveTextContent("–");
  cleanup();
  project.contractTypeCode = "MO";
  row.inputTypeCode = "FREE";
  page();
  expect(await screen.findByTestId("list-cell-A-0001-billingPrice")).toHaveTextContent("무상지원");
});

it("places template, upload and primary create in the title host and excel download in the list tools", async () => {
  page();
  const title = within(screen.getByTestId("assignment-title"));
  expect(await title.findByTestId("assignment-create")).toHaveTextContent("배정");
  expect(title.getByTestId("assignment-create")).toHaveClass("primary-button");
  expect(title.getByRole("button", { name: "양식 내려받기" })).toBeInTheDocument();
  expect(title.getByRole("button", { name: "엑셀 업로드" })).toBeInTheDocument();
  expect(title.queryByText("엑셀 다운로드")).not.toBeInTheDocument();
  const list = within(screen.getByTestId("assignment-list"));
  expect(list.getByTestId("list-download")).toHaveTextContent("엑셀 다운로드");
  expect(list.getByTestId("list-columns")).toHaveTextContent("열 설정");
  expect(list.getByTestId("list-density")).toHaveTextContent("좁게 보기");
});

it("reopening uses the saved year and changing the year saves it (FR-0105)", async () => {
  const base = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    String(url) === "/api/common/user-preferences/PERIOD/UI-12"
      ? init?.method === "PUT"
        ? ok({ prefTypeCd: "PERIOD", prefKey: "UI-12", found: true, prefVal: String(JSON.parse(String(init.body)).prefVal ?? ""), modDttm: "2026-11-24T00:00:00Z" })
        : ok({ prefTypeCd: "PERIOD", prefKey: "UI-12", found: true, prefVal: '{"year":2025}', modDttm: "2026-11-23T00:00:00Z" })
      : base(url, init),
  );
  page();
  expect(await screen.findByTestId("assignment-lock-banner")).toHaveTextContent("2025년은 지난 해라 읽기만 할 수 있습니다.");
  await waitFor(() =>
    expect(request.mock.calls.some(([u]) => String(u).startsWith("/api/common/assignments?") && String(u).includes("year=2025"))).toBe(true),
  );
  fireEvent.click(screen.getByTestId("assignment-year-next"));
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        ([u, i]) => String(u) === "/api/common/user-preferences/PERIOD/UI-12" && (i as RequestInit | undefined)?.method === "PUT"
          && String((i as RequestInit).body).includes('\\"year\\":2026'),
      ),
    ).toBe(true),
  );
});

it("an already open tab follows a period saved by month close [확인하기]", async () => {
  page();
  await screen.findByTestId("list-row-A-0001");
  expect(screen.queryByTestId("assignment-lock-banner")).not.toBeInTheDocument();
  act(() => announcePeriodChanged({ screenId: "UI-12", prefVal: '{"year":2025}', modDttm: null }));
  expect(await screen.findByTestId("assignment-lock-banner")).toHaveTextContent("2025년은 지난 해라 읽기만 할 수 있습니다.");
  // 다른 화면 신호는 무시한다.
  act(() => announcePeriodChanged({ screenId: "UI-01", prefVal: '{"year":2024}', modDttm: null }));
  expect(screen.getByTestId("assignment-lock-banner")).toHaveTextContent("2025년");
});

it("last year shows the lock banner and hides every editing control", async () => {
  history[0] = { ...history[0], historyId: "2", changeTypeCode: "EARLY", changeTypeName: "조기철수" };
  candidates = [{ candidateId: "12", projectId: "P-0001", projectName: "개발 프로젝트", developerId: "developer", name: "김개발", jobCode: "BE", jobName: "백엔드 개발", startDate: "2026-12-01", endDate: "2026-12-31" }];
  page();
  await screen.findByTestId("assignment-candidates");
  fireEvent.click(screen.getByTestId("assignment-year-prev"));
  expect(await screen.findByTestId("assignment-lock-banner")).toHaveTextContent("2025년은 지난 해라 읽기만 할 수 있습니다.");
  await screen.findByTestId("list-row-A-0001");
  expect(screen.getByRole("columnheader", { name: "2025년 MM" })).toBeInTheDocument();
  expect(screen.queryByTestId("assignment-create")).not.toBeInTheDocument();
  expect(screen.queryByTestId("upload-actions")).not.toBeInTheDocument();
  expect(screen.queryByTestId("assignment-candidates")).not.toBeInTheDocument();
  expect(screen.queryByTestId("list-select-A-0001")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("list-row-A-0001"));
  await openHistory();
  const entry = await screen.findByTestId("assignment-history-2");
  expect(within(entry).getByText("조기 철수")).toHaveClass("ls-b-bad");
  expect(within(entry).queryByRole("button", { name: "정정" })).not.toBeInTheDocument();
  expect(screen.queryByTestId("assignment-change-open")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("shows a failed list with retry", async () => {
  failure = true;
  page();
  expect(await screen.findByTestId("list-error")).toHaveTextContent("목록 조회 실패");
  failure = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  await screen.findByTestId("list-row-A-0001");
});

it("shows the warning icon and the person monthly MM sum on hover", async () => {
  row.warnings = ["MM 1.0 초과", "미계약 선투입"];
  row.monthlyMmTotal = 1.25;
  page();
  await screen.findByTestId("list-row-A-0001");
  expect(screen.getByTestId("assignment-warn-A-0001")).toHaveAttribute("title", "MM 1.0 초과, 미계약 선투입");
  expect(screen.getByTitle("이 사람의 월 MM 합계 1.25")).toHaveTextContent("1.00 ⚠");
});

it("filters apply only on 조회 or Enter and reset clears them", async () => {
  page();
  await screen.findByTestId("list-row-A-0001");
  const keyword = screen.getByLabelText("이름");
  expect(keyword).toHaveAttribute("placeholder", "이름 또는 관리번호");
  fireEvent.change(keyword, { target: { value: "D-0001" } });
  const listCalls = () => request.mock.calls.filter(([u]) => String(u).startsWith("/api/common/assignments?"));
  expect(listCalls()).toHaveLength(1);
  fireEvent.submit(screen.getByTestId("assignment-filters"));
  await waitFor(() => expect(listCalls().some(([u]) => new URLSearchParams(String(u).split("?")[1]).get("keyword") === "D-0001")).toBe(true));
  fireEvent.click(screen.getByTestId("list-reset"));
  expect(screen.getByLabelText("이름")).toHaveValue("");
  expect(within(screen.getByTestId("list-filter-assignment-filter-warning")).getByRole("option", { name: "MM 1.0 초과" })).toBeInTheDocument();
});

it("row click opens the detail with the mockup title, subtitle, tabs and formats", async () => {
  row.extensionPossibilityCode = "M";
  page();
  fireEvent.click(await screen.findByTestId("list-row-A-0001"));
  const panel = await screen.findByRole("dialog", { name: "김개발 · 개발 프로젝트" });
  expect(panel).toHaveTextContent("A-0001 · 백엔드 개발 · 2026-11-01 ~ 2026-12-31");
  expect(within(panel).getByRole("tab", { name: "투입 정보" })).toHaveAttribute("aria-selected", "true");
  expect(within(panel).getByRole("tab", { name: "변경 이력 (1)" })).toBeInTheDocument();
  expect(screen.getByTestId("kv-row-단가")).toHaveTextContent("8,000,000 원 / 월");
  expect(screen.getByTestId("kv-row-월 MM")).toHaveTextContent("1.00");
  expect(within(screen.getByTestId("kv-row-연장 가능성")).getByText("보통")).toHaveClass("ls-b-warn");
  expect(screen.getByTestId("kv-row-연장 횟수")).toHaveTextContent("0회");
  expect(screen.queryByTestId("banner-warn")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("assignment-detail-person"));
  expect(await screen.findByTestId("developer-detail")).toBeInTheDocument();
});

it("detail shows free support and turnkey prices like the mockup and hides price from PM", async () => {
  row.inputTypeCode = "FREE";
  detail();
  expect(await screen.findByTestId("kv-row-단가")).toHaveTextContent("없음 (무상지원)");
  cleanup();
  detail(false, ["PM"]);
  await screen.findByTestId("kv-row-월 MM");
  expect(screen.queryByTestId("kv-row-단가")).not.toBeInTheDocument();
});

it("PM list has no price column", async () => {
  page(["PM"]);
  await screen.findByTestId("list-row-A-0001");
  expect(screen.queryByRole("columnheader", { name: "단가" })).not.toBeInTheDocument();
});

it("history tab uses mockup chips, badges, Korean time and the empty text", async () => {
  history.push({
    ...history[0],
    historyId: "2",
    effectiveDate: "2026-11-10",
    changeTypeCode: "EXT",
    changeTypeName: "연장",
    beforeValue: "종료 2026-11-30",
    afterValue: "종료 2026-12-31",
    reason: "고객 요청",
    memo: "메모",
    retroactiveYn: "Y",
    correctedYn: "Y",
    changedAt: "2026-11-14T16:30:00.123456Z",
  });
  detail();
  await openHistory();
  const chips = within(screen.getByRole("group", { name: "변경 종류" })).getAllByRole("button").map((b) => b.textContent);
  expect(chips).toEqual(["전체", "투입", "연장", "조기 철수", "단가 변경", "월 MM 변경", "연장 가능성 변경", "투입 구분 변경", "정정", "취소"]);
  expect(screen.getByText("지나간 기록은 고치거나 지울 수 없습니다. 틀렸으면 [정정]으로 바로잡은 내용을 남깁니다.")).toBeInTheDocument();
  expect(screen.getAllByRole("columnheader").map((h) => h.textContent)).toEqual(["적용일 · 입력", "변경 종류", "변경 전 → 후", "사유", ""]);
  const ext = screen.getByTestId("assignment-history-2");
  expect(ext).toHaveTextContent("admin · 11-15 01:30");
  expect(ext).toHaveTextContent("종료 2026-11-30 → 종료 2026-12-31");
  expect(ext).toHaveTextContent("고객 요청 · 메모");
  expect(within(ext).getByText("연장")).toHaveClass("ls-b-ok");
  expect(within(ext).getByText("소급 입력")).toHaveClass("ls-b-warn");
  expect(within(ext).getByText("정정됨")).toHaveClass("ls-b-lock");
  expect(within(ext).queryByRole("button", { name: "정정" })).not.toBeInTheDocument();
  expect(within(screen.getByTestId("assignment-history-1")).queryByRole("button", { name: "정정" })).not.toBeInTheDocument();
  // 최근 적용일이 위
  expect(screen.getAllByTestId(/^assignment-history-\d$/).map((r) => r.dataset.testid)).toEqual(["assignment-history-2", "assignment-history-1"]);
  fireEvent.click(screen.getByTestId("assignment-history-filter-crct"));
  expect(screen.getByTestId("assignment-history-filter-crct")).toHaveAttribute("aria-pressed", "true");
  expect(screen.getByText("이 종류의 기록이 없습니다.")).toBeInTheDocument();
  expect(screen.getByTestId("assignment-change-open")).toHaveTextContent("변경 기록");
  expect(screen.getByTestId("assignment-change-open")).toHaveClass("ls-btn-p");
});

it("change modal defaults to extension and validates with mockup messages", async () => {
  detail();
  await openHistory();
  fireEvent.click(screen.getByTestId("assignment-change-open"));
  const dialog = await screen.findByRole("dialog", { name: "변경 기록" });
  expect(screen.getByTestId("assignment-change-type")).toHaveValue("EXT");
  expect(within(dialog).getAllByRole("option").map((o) => o.textContent)).toEqual(
    expect.arrayContaining(["연장", "조기 철수", "단가 변경", "월 MM 변경", "연장 가능성 변경", "투입 구분 변경"]),
  );
  expect(within(dialog).getByLabelText(/연장 시작일/)).toHaveValue("2027-01-01");
  expect(within(dialog).getByLabelText(/새 종료일/)).toHaveValue("");
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText("새 종료일을 넣어 주세요.")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-change-end"), { target: { value: "2027-03-31" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(
    await within(dialog).findByText("새 종료일이 프로젝트 계약 종료일(2026-12-31)보다 늦습니다. 「프로젝트·투입 › 프로젝트」에서 계약 기간을 먼저 연장해 주세요."),
  ).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "EARLY" } });
  expect(within(dialog).getByLabelText(/철수일/)).toHaveValue("2026-11-15");
  expect(screen.queryByTestId("assignment-change-end")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText("사유를 골라 주세요.")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "RATE" } });
  expect(within(dialog).getByLabelText(/적용 시작월/)).toHaveAttribute("type", "month");
  expect(dialog).toHaveTextContent("지금 1.00. 적용 시작월부터 월 MM·원가·매출을 다시 계산하고 앞선 달은 그대로 둡니다.");
  fireEvent.change(screen.getByTestId("assignment-change-date"), { target: { value: "2026-09" } });
  expect(screen.getByTestId("assignment-change-warning")).toHaveTextContent(
    "지난 날짜로 기록합니다. '소급 입력'으로 표시되고 사유가 필요합니다. 마감한 달(2026-09)에 걸칩니다. 마감한 달의 금액은 그대로 두고 마감하지 않은 달부터 반영합니다.",
  );
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "UPRC" } });
  expect(within(dialog).getByLabelText(/새 월 단가\(원\)/)).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "KIND" } });
  expect(within(dialog).getByLabelText(/투입 구분/)).toHaveValue("NEW");
  expect(dialog).toHaveTextContent("투입 구분은 배정 전체에 적용됩니다.");
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText("현재 투입 구분과 같습니다.")).toBeInTheDocument();
  expect(screen.getByTestId("assignment-change-save")).toHaveTextContent("기록 저장");
  expect(writes()).toHaveLength(0);
});

it("free support change dialog omits the rate-change type", async () => {
  row.inputTypeCode = "FREE";
  detail();
  await openHistory();
  fireEvent.click(screen.getByTestId("assignment-change-open"));
  await screen.findByTestId("assignment-change-dialog");
  expect(screen.queryByRole("option", { name: "단가 변경" })).not.toBeInTheDocument();
});

it("saving a change sends the read version, toasts the mockup text and opens the history tab", async () => {
  row.modifiedAt = "2026-11-14T16:30:00.123456Z";
  page();
  fireEvent.click(await screen.findByTestId("list-row-A-0001"));
  await openHistory();
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "EXTP" } });
  expect(screen.getByTestId("assignment-change-extension")).toHaveValue("");
  fireEvent.change(screen.getByTestId("assignment-change-extension"), { target: { value: "H" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(writes()[0][0]).toBe(`/api/common/assignments/${row.assignmentId}/changes`);
  expect(body()).toMatchObject({ modifiedAt: "2026-11-14T16:30:00.123456Z", changeTypeCode: "EXTP", newExtensionPossibilityCode: "H", effectiveDate: "2026-11-15" });
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("변경 이력에 '연장 가능성 변경' 기록을 남겼습니다.");
  expect(screen.queryByTestId("assignment-change-dialog")).not.toBeInTheDocument();
  expect(screen.getByTestId("assignment-detail-tab-history")).toHaveAttribute("aria-selected", "true");
});

it("correction modal starts empty, requires both fields and toasts after save", async () => {
  history[0] = { ...history[0], changeTypeCode: "EARLY", changeTypeName: "조기철수", beforeValue: "종료 2026-12-31", afterValue: "철수 2026-11-30" };
  const onChanged = vi.fn();
  inRouter(<AssignmentDetail id={row.assignmentId} roles={["FIN"]} readOnly={false} onClose={vi.fn()} onChanged={onChanged} />);
  await openHistory();
  fireEvent.click(within(await screen.findByTestId("assignment-history-1")).getByRole("button", { name: "정정" }));
  const dialog = screen.getByRole("dialog", { name: "기록 정정" });
  expect(dialog).toHaveTextContent("2026-11-01 · 조기 철수 · 종료 2026-12-31 → 철수 2026-11-30");
  expect(dialog).toHaveTextContent("원래 기록은 그대로 남고 '정정됨'으로 표시됩니다. 지금 값을 바꾸려면 [변경 기록]을 따로 넣으세요.");
  expect(screen.getByTestId("assignment-correction-value")).toHaveValue("");
  fireEvent.click(screen.getByTestId("assignment-correction-save"));
  expect(await within(dialog).findByText("바른 내용을 적어 주세요.")).toBeInTheDocument();
  expect(within(dialog).getByText("사유를 골라 주세요.")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-correction-value"), { target: { value: "철수 2026-11-29" } });
  fireEvent.change(screen.getByTestId("assignment-correction-reason"), { target: { value: "HR" } });
  fireEvent.click(screen.getByTestId("assignment-correction-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toEqual({ modifiedAt: row.modifiedAt, correctedValue: "철수 2026-11-29", reason: "인력 사정" });
  await waitFor(() => expect(onChanged).toHaveBeenCalledWith("정정 기록을 남겼습니다."));
});

it("candidate card lists candidates, confirms into a prefilled form and sends candidateId", async () => {
  candidates = [
    { candidateId: "12", projectId: "P-0001", projectName: "개발 프로젝트", developerId: "developer", name: "김개발", managementNumber: "D-0001", jobCode: "BE", jobName: "백엔드 개발", startDate: "2026-12-01", endDate: "2026-12-31" },
  ];
  page();
  const card = await screen.findByTestId("assignment-candidates");
  expect(card).toHaveTextContent("배정 후보 (1)");
  expect(card).toHaveTextContent("영업 파이프라인에서 수주 처리한 기회의 가배정 인력입니다. [확정]을 누르면 배정 창이 채워진 채로 열립니다.");
  expect(within(card).getAllByRole("columnheader").map((h) => h.textContent)).toEqual(["프로젝트", "이름", "직무", "예정 기간", ""]);
  expect(card).toHaveTextContent("김개발 D-0001");
  expect(card).toHaveTextContent("2026-12-01 ~ 2026-12-31");
  expect(screen.getByTestId("candidate-confirm-12")).toHaveClass("ls-btn-p");
  fireEvent.click(screen.getByTestId("candidate-confirm-12"));
  const panel = await screen.findByRole("dialog", { name: "투입 배정" });
  expect(panel).toHaveTextContent("배정 후보 확정");
  await waitFor(() => expect(screen.getByTestId("assignment-developer")).toHaveValue("김개발 (D-0001)"));
  expect(screen.getByTestId("assignment-start-date")).toHaveValue("2026-12-01");
  expect(screen.getByTestId("assignment-job")).toHaveValue("BE");
  await waitFor(() => expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000"));
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toMatchObject({ candidateId: "12", developerId: "developer", startDate: "2026-12-01", billingPrice: 8000000, monthlyMm: 1, inputTypeCode: "NEW" });
});

it("removing a candidate asks with the mockup text and toasts", async () => {
  candidates = [{ candidateId: "12", projectId: "P-0001", projectName: "개발 프로젝트", developerId: "developer", name: "김개발", jobCode: "BE", jobName: "백엔드 개발", startDate: "2026-12-01", endDate: "2026-12-31" }];
  page();
  fireEvent.click(await screen.findByTestId("candidate-remove-12"));
  const confirm = screen.getByRole("alertdialog", { name: "김개발님을 배정 후보에서 뺄까요?" });
  expect(confirm).toHaveTextContent("배정하지 않고 후보 목록에서만 뺍니다. 인원 계획의 계획 MM은 그대로라 그 몫은 미충원으로 남습니다.");
  fireEvent.click(within(confirm).getByRole("button", { name: "빼기" }));
  await waitFor(() => expect(writes()[0][0]).toBe("/api/common/assignments/candidates/12/remove"));
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("배정 후보에서 뺐습니다.");
});

it("bulk extension change uses the mockup dialog and skip count", async () => {
  page();
  fireEvent.click(await screen.findByTestId("list-select-A-0001"));
  expect(screen.getByTestId("list-count")).toHaveTextContent("1건 선택");
  expect(screen.getByTestId("list-select-none")).toHaveTextContent("선택 해제");
  fireEvent.click(screen.getByTestId("list-bulk-extension"));
  const dialog = screen.getByRole("dialog", { name: "연장 가능성 변경" });
  expect(dialog).toHaveTextContent("선택한 1건의 연장 가능성을 한 번에 바꿉니다. 건마다 변경 이력이 1줄씩 남습니다.");
  expect(within(dialog).getAllByRole("option").map((o) => o.textContent)).toEqual(["높음", "보통", "낮음", "미정"]);
  fireEvent.change(screen.getByTestId("assignment-bulk-value"), { target: { value: "M" } });
  fireEvent.click(within(dialog).getByRole("button", { name: "바꾸기" }));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toMatchObject({ extensionPossibilityCode: "M", assignments: [{ assignmentId: "A-0001", modifiedAt: row.modifiedAt }] });
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("1건의 연장 가능성을 '보통'으로 바꿨습니다.");
});

it("bulk extension skips rows that already have the value", async () => {
  row.extensionPossibilityCode = "H";
  page();
  fireEvent.click(await screen.findByTestId("list-select-page"));
  fireEvent.click(screen.getByTestId("list-bulk-extension"));
  fireEvent.click(screen.getByRole("button", { name: "바꾸기" }));
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("0건의 연장 가능성을 '높음'으로 바꿨습니다. (이미 같은 값 1건 제외)");
  expect(writes()).toHaveLength(0);
});

it("bulk cancel confirms with the mockup text and toasts", async () => {
  page();
  fireEvent.click(await screen.findByTestId("list-select-A-0001"));
  fireEvent.click(screen.getByTestId("list-bulk-cancel"));
  const confirm = screen.getByRole("alertdialog", { name: "선택한 1건의 배정을 취소할까요?" });
  expect(confirm).toHaveTextContent("지우지 않고 변경 이력에 「취소」로 남깁니다. 투입 목록과 MM·원가 계산에서는 빠집니다.");
  expect(confirm).not.toHaveTextContent("UI-10");
  fireEvent.click(within(confirm).getByRole("button", { name: "배정 취소" }));
  await waitFor(() => expect(writes()[0][0]).toBe("/api/common/assignments/cancel"));
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("1건의 배정을 취소했습니다. 변경 이력에 「취소」로 남겼습니다.");
});

it("indirect staff given only as a code in the developer list are not offered (SFR-102-10)", async () => {
  extraDevelopers = [
    { developerId: "indirect", name: "정관리", managementNumber: "D-0009", workStatusName: "대기", employeeTypeCode: "FT", primaryJobCode: "PM", directClassCode: "INDRCT", useYn: "Y", monthlyRate: null },
    { developerId: "direct2", name: "이직접", managementNumber: "D-0010", workStatusName: "대기", employeeTypeCode: "FT", primaryJobCode: "PM", directClassCode: "DRCT", useYn: "Y", monthlyRate: 7000000 },
  ];
  form();
  await screen.findByRole("dialog", { name: "투입 배정" });
  await ready();
  await waitFor(() => expect(document.querySelector('#assignment-developer-list option[value="이직접 (D-0010)"]')).not.toBeNull());
  expect(document.querySelector('#assignment-developer-list option[value="정관리 (D-0009)"]')).toBeNull();
});

it("assign form matches the mockup labels, options, hints and defaults", async () => {
  form();
  const panel = await screen.findByRole("dialog", { name: "투입 배정" });
  await ready();
  expect(screen.getByTestId("assignment-project")).toHaveDisplayValue("개발 프로젝트 (월단위)");
  expect(within(screen.getByTestId("assignment-project")).queryByRole("option", { name: "선택" })).not.toBeInTheDocument();
  expect(screen.getByTestId("assignment-developer")).toHaveAttribute("placeholder", "이름 또는 관리번호로 찾기");
  expect(document.querySelector('#assignment-developer-list option[value="김개발 (D-0001)"]')).toHaveAttribute("label", "대기");
  expect(screen.getByTestId("assignment-start-date")).toHaveValue("2026-11-15");
  expect(screen.getByTestId("assignment-end-date")).toHaveValue("2026-12-31");
  expect(screen.getByTestId("assignment-monthly-mm")).toHaveValue("1.00");
  expect(screen.getByTestId("assignment-input-type")).toHaveDisplayValue("신규");
  for (const label of ["프로젝트", "개발자", "직무", "투입일 ~ 철수일", "월 MM", "투입 구분", "청구 단가(월)"])
    expect(within(screen.getByText(label, { selector: "label" })).getByText("*")).toBeInTheDocument();
  expect(panel).toHaveTextContent("개발자를 고르면 주 직무로 채웁니다.");
  expect(panel).toHaveTextContent("기본 1.00(풀 출근).");
  expect(panel).toHaveTextContent("연장 가능성은 처음에 「미정」이고 철수 리스트(UI-05)에서 바꿉니다.");
  expect(screen.getByTestId("assignment-ai-recommend")).toHaveTextContent("이 자리에 맞는 사람 AI 추천 받기");
  await pick();
  expect(screen.getByTestId("assignment-job")).toHaveValue("PM");
  expect(panel).toHaveTextContent("정규직 · 중급 · PM·사업관리 · 월 단가 8,000,000원");
  await waitFor(() => expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000"));
  fireEvent.change(screen.getByTestId("assignment-billing-price"), { target: { value: "9500000" } });
  expect(screen.getByTestId("assignment-billing-price")).toHaveValue("9,500,000");
});

it("assign form validates with the mockup messages before any POST", async () => {
  form();
  await ready();
  fireEvent.change(screen.getByTestId("assignment-monthly-mm"), { target: { value: "1.5" } });
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await screen.findByText("개발자를 골라 주세요.")).toBeInTheDocument();
  expect(screen.getByText("월 MM은 0보다 크고 1.00 이하로 넣어 주세요.")).toBeInTheDocument();
  await pick();
  fireEvent.change(screen.getByTestId("assignment-end-date"), { target: { value: "2027-01-31" } });
  fireEvent.change(screen.getByTestId("assignment-monthly-mm"), { target: { value: "1.00" } });
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(
    await screen.findByText("철수일이 프로젝트 계약 종료일(2026-12-31)보다 늦습니다. 「프로젝트·투입 › 프로젝트」에서 계약 기간을 먼저 연장해 주세요."),
  ).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-end-date"), { target: { value: "2026-11-01" } });
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await screen.findByText("철수일이 투입일보다 앞입니다.")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("PM has no price field; the server checks the rate and its message shows under the developer", async () => {
  developer.monthlyRate = null;
  form({ roles: ["PM"] });
  await pick();
  expect(screen.queryByTestId("assignment-billing-price")).not.toBeInTheDocument();
  const message = "월 단가가 없어 배정할 수 없습니다. 파트장·재무에게 요청해 주세요.";
  writeFailure = { status: 400, message: "입력 내용을 확인해 주세요.", errors: [{ field: "billingPrice", message }] };
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  // PM 화면에는 금액이 오지 않으므로 화면이 미리 막지 않고 서버가 검사한다(2026-11-24 사용자 결정).
  expect(await within(screen.getByTestId("assignment-field-developer")).findByText(message)).toBeInTheDocument();
  expect(writes()).toHaveLength(1);
  expect(JSON.parse(String((writes()[0][1] as RequestInit).body))).not.toHaveProperty("billingPrice");
});

it("finance sees the no-rate hint, free support hides price and turnkey shows the notice", async () => {
  developer.monthlyRate = null;
  form();
  await pick();
  expect(screen.getByTestId("assignment-field-price")).toHaveTextContent("⚠ 개발자 풀에 월 단가가 없는 사람입니다.");
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await screen.findByText("청구 단가를 넣어 주세요.")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-input-type"), { target: { value: "FREE" } });
  expect(screen.queryByTestId("assignment-billing-price")).not.toBeInTheDocument();
  cleanup();
  project.contractTypeCode = "TK";
  project.contractTypeName = "턴키";
  form();
  await ready();
  expect(screen.getByTestId("assignment-no-price")).toHaveTextContent("턴키는 배분표 금액으로 매출을 계산하므로 청구 단가 칸이 없습니다.");
  cleanup();
  project.contractTypeCode = "IN";
  form();
  await ready();
  expect(screen.getByTestId("assignment-no-price")).toHaveTextContent("내부 프로젝트는 매출이 없어 청구 단가 칸이 없습니다.");
});

it("shows all mockup warnings together in one band", async () => {
  project.statusCode = "PRE";
  project.contractTypeCode = "DP";
  project.contractTypeName = "파견";
  project.salesPartId = "part2";
  project.salesPartName = "2파트";
  developer.employeeTypeCode = "FL";
  developer.employeeTypeName = "프리랜서";
  statuses = [{ statusName: "휴직", startDate: "2026-12-10", endDate: null, reason: null, registeredBy: null }];
  // 시안: 같은 기간에 걸친 다른 배정(지금 월 MM)의 합 + 입력 MM이 1을 넘으면 첫 줄에 MM 1.0 초과
  booked = [
    { projectId: "P-0009", projectName: "다른 프로젝트", jobName: "백엔드 개발", startDate: "2026-12-01", endDate: "2027-03-31", mm: 0.5 },
    { projectId: "P-0008", projectName: "지난 프로젝트", jobName: "백엔드 개발", startDate: "2026-01-01", endDate: "2026-10-31", mm: 1 },
  ];
  form();
  await ready();
  expect(screen.getByTestId("assignment-project")).toHaveDisplayValue("개발 프로젝트 (파견 · 수주예정)");
  await pick();
  await waitFor(() => expect(screen.getByTestId("assignment-form-warnings")).toHaveTextContent("MM 1.0 초과"));
  await waitFor(() => expect(screen.getByTestId("assignment-form-warnings")).toHaveTextContent("휴직"));
  expect(screen.getAllByTestId("banner-warn")).toHaveLength(1);
  const lines = () => [...screen.getByTestId("assignment-form-warnings").children].map((line) => line.textContent ?? "");
  expect(lines()[0]).toBe("MM 1.0 초과: 김개발님은 같은 기간에 이미 0.50MM 배정되어 있습니다(합계 1.50). 저장은 됩니다.");
  expect(lines().slice(1).map((line) => line.slice(0, 6))).toEqual(["김개발님은 ", "영업 파트가", "파견 프로젝", "수주예정 프"]);
  // 입력 MM을 바꾸면 바로 다시 센다(합계 1 이하면 경고 없음).
  fireEvent.change(screen.getByTestId("assignment-monthly-mm"), { target: { value: "0.5" } });
  expect(screen.getByTestId("assignment-form-warnings")).not.toHaveTextContent("MM 1.0 초과");
  fireEvent.change(screen.getByTestId("assignment-monthly-mm"), { target: { value: "0.6" } });
  expect(screen.getByTestId("assignment-form-warnings")).toHaveTextContent("이미 0.50MM 배정되어 있습니다(합계 1.10)");
  // 기간이 겹치지 않으면 세지 않는다.
  fireEvent.change(screen.getByTestId("assignment-end-date"), { target: { value: "2026-11-30" } });
  expect(screen.getByTestId("assignment-form-warnings")).not.toHaveTextContent("MM 1.0 초과");
  fireEvent.change(screen.getByTestId("assignment-end-date"), { target: { value: "2026-12-31" } });
  const band = screen.getByTestId("assignment-form-warnings");
  expect(band).toHaveTextContent("김개발님은 배정 기간에 휴직(2026-12-10 ~ 복직일 미정)이 있습니다. 저장은 됩니다.");
  expect(band).toHaveTextContent(
    "영업 파트가 다릅니다: 김개발님은 영업파트 소속, 프로젝트는 2파트입니다. 매출은 2파트로, 이 사람 이익금은 영업파트로 잡힙니다. 파트를 잘못 골랐다면 개발자 풀에서 고쳐 주세요. 저장은 됩니다.",
  );
  expect(band).toHaveTextContent("파견 프로젝트에 프리랜서 인력을 배정합니다. 막지는 않으니 계약 조건을 확인하세요.");
  expect(band).toHaveTextContent("수주예정 프로젝트입니다. 저장하면 「미계약 선투입」으로 표시됩니다.");
  fireEvent.change(screen.getByTestId("assignment-start-date"), { target: { value: "2026-09-01" } });
  expect(screen.getByTestId("assignment-start-date")).toHaveValue("2026-09-01");
  expect(screen.getByTestId("assignment-form-warnings")).toHaveTextContent("마감한 달(2026-09)에 걸칩니다. 마감한 달 금액은 그대로 두고 차액은 이번 달 전월 조정으로 넣습니다.");
});

const partCalls = () => request.mock.calls.filter((c) => String(c[0]).endsWith("/sales-parts")).length;
const laterStart = { projectId: "P-0001", developerId: "", jobCode: "BE", startDate: "2027-02-01", endDate: "2027-06-30" };
it("a project that starts later warns before save with the part effective at the start month", async () => {
  // 수주로 만든 아직 시작 전 프로젝트: 목록(이번 달 기준) 파트는 비고, 파트 이력은 시작 월부터 있다.
  project.startDate = "2027-02-01";
  project.endDate = "2027-06-30";
  project.salesPartId = null;
  project.salesPartName = null;
  salesParts = [
    { salesPartId: "part3", salesPartName: "3파트", effectiveMonth: "2027-04", reason: null, changedBy: "admin", scheduled: true },
    { salesPartId: "part2", salesPartName: "2파트", effectiveMonth: "2027-02", reason: null, changedBy: "admin", scheduled: true },
  ];
  form({ initialValues: laterStart });
  await pick();
  await waitFor(() =>
    expect(screen.getByTestId("assignment-form-warnings")).toHaveTextContent(
      "영업 파트가 다릅니다: 김개발님은 영업파트 소속, 프로젝트는 2파트입니다. 매출은 2파트로, 이 사람 이익금은 영업파트로 잡힙니다.",
    ),
  );
  expect(writes()).toHaveLength(0);
  // 투입일 달이 바뀌면 그 달 파트로 다시 본다(새로 읽지 않음).
  fireEvent.change(screen.getByTestId("assignment-start-date"), { target: { value: "2027-05-01" } });
  expect(screen.getByTestId("assignment-form-warnings")).toHaveTextContent("프로젝트는 3파트입니다.");
  expect(partCalls()).toBe(1);
  cleanup();
  // 같은 파트면 경고가 없다.
  salesParts = [{ salesPartId: "part", salesPartName: "영업파트", effectiveMonth: "2027-02", reason: null, changedBy: "admin", scheduled: true }];
  form({ initialValues: laterStart });
  await pick();
  await waitFor(() => expect(partCalls()).toBe(2));
  await act(async () => {});
  expect(screen.getByTestId("assignment-form")).not.toHaveTextContent("영업 파트가 다릅니다");
});

it.each([
  ["the part history is empty", false],
  ["the part history request fails", true],
])("falls back to the list part and still warns when %s", async (_case, fails) => {
  project.salesPartId = "part2";
  project.salesPartName = "2파트";
  salesParts = [];
  salesPartsFail = fails;
  form({ initialValues: laterStart });
  await pick();
  await waitFor(() => expect(partCalls()).toBe(1));
  await waitFor(() =>
    expect(screen.getByTestId("assignment-form-warnings")).toHaveTextContent(
      "영업 파트가 다릅니다: 김개발님은 영업파트 소속, 프로젝트는 2파트입니다.",
    ),
  );
  expect(writes()).toHaveLength(0);
});
it("a running project in this month uses the list part without reading the part history", async () => {
  form();
  await pick();
  expect(partCalls()).toBe(0);
});

it("after save the page closes the form, highlights the row and toasts without opening detail", async () => {
  page();
  await screen.findByTestId("list-row-A-0001");
  row.warnings = ["MM 1.0 초과"];
  fireEvent.click(await screen.findByTestId("assignment-create"));
  await pick();
  await waitFor(() => expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000"));
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("배정을 저장했습니다. 변경 이력에 「투입」 1줄을 남겼습니다. 경고: MM 1.0 초과");
  expect(screen.queryByTestId("assignment-form")).not.toBeInTheDocument();
  expect(screen.queryByTestId("assignment-detail")).not.toBeInTheDocument();
  await waitFor(() => expect(screen.getByTestId("list-row-A-0001")).toHaveClass("ls-hl"));
  expect(body()).not.toHaveProperty("candidateId");
});

it("replacement asks with the mockup dialog and requires a reason to withdraw together", async () => {
  const overlap = { ...row, assignmentId: "A-0009", name: "계약 인력", startDate: "2026-10-01", endDate: "2026-12-31", monthlyMm: 1, inputTypeName: "신규" };
  contracts = [{ developerId: "contract-person", name: "계약 인력", jobName: "백엔드 개발", endDate: "2026-12-31", billingPrice: 9000000, overlappingAssignments: [overlap] }];
  form();
  await pick();
  fireEvent.change(screen.getByTestId("assignment-input-type"), { target: { value: "SUB" } });
  await screen.findByRole("option", { name: "계약 인력 · 백엔드 개발 · 9,000,000원" });
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await screen.findByText("계약상 인력을 골라 주세요.")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-contract-developer"), { target: { value: "contract-person" } });
  expect(screen.getByTestId("assignment-billing-price")).toHaveValue("9,000,000");
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  const dialog = await screen.findByRole("dialog", { name: "대체투입 저장" });
  expect(dialog).toHaveTextContent("계약 인력님의 「개발 프로젝트」 배정을 2026-11-14로 조기 철수합니다.");
  expect(screen.getByTestId("replacement-row-A-0009")).toHaveTextContent("2026-10-01 ~ 2026-12-31 · 월 MM 1.00 · 신규");
  expect(screen.getByTestId("replacement-check-A-0009")).toBeChecked();
  expect(screen.getByTestId("replacement-reason")).toHaveDisplayValue("고르세요");
  expect(within(within(dialog).getByText("철수 사유", { selector: "label" })).getByText("*")).toBeInTheDocument();
  expect(screen.getByTestId("replacement-memo")).toHaveValue("대체투입(김개발)");
  fireEvent.click(screen.getByTestId("replacement-together"));
  expect(screen.getByTestId("replacement-error")).toHaveTextContent("철수 사유를 골라 주세요.");
  fireEvent.change(screen.getByTestId("replacement-reason"), { target: { value: "HR" } });
  fireEvent.click(screen.getByTestId("replacement-together"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toMatchObject({
    inputTypeCode: "SUB",
    contractDeveloperId: "contract-person",
    replacementAction: "together",
    earlyWithdrawalAssignmentIds: ["A-0009"],
    reasonCode: "HR",
    memo: "대체투입(김개발)",
    billingPrice: 9000000,
  });
});

it("project change resets dates and clears the contractual developer and its price", async () => {
  contracts = [{ developerId: "contract-person", name: "계약 인력", endDate: "2026-10-31", billingPrice: 9000000, overlappingAssignments: [] }];
  form({ projects: [project, { ...project, projectId: "P-0002", projectName: "다른 프로젝트", startDate: "2026-03-01" }] });
  await pick();
  await waitFor(() => expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000"));
  fireEvent.change(screen.getByTestId("assignment-input-type"), { target: { value: "SUB" } });
  await screen.findByRole("option", { name: "계약 인력 · 9,000,000원 (철수 2026-10-31)" });
  fireEvent.change(screen.getByTestId("assignment-contract-developer"), { target: { value: "contract-person" } });
  expect(screen.getByTestId("assignment-billing-price")).toHaveValue("9,000,000");
  fireEvent.change(screen.getByTestId("assignment-project"), { target: { value: "P-0002" } });
  expect(screen.getByTestId("assignment-contract-developer")).toHaveValue("");
  expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000");
  expect(screen.getByTestId("assignment-start-date")).toHaveValue("2026-03-01");
  expect(writes()).toHaveLength(0);
});

it("closing a touched form asks before leaving", async () => {
  const onClose = vi.fn();
  form({ onClose });
  await ready();
  fireEvent.change(screen.getByTestId("assignment-monthly-mm"), { target: { value: "0.5" } });
  fireEvent.click(screen.getByTestId("assignment-form-cancel"));
  expect(screen.getByRole("dialog", { name: "저장하지 않은 내용이 있습니다" })).toHaveTextContent("나가면 입력한 내용이 사라집니다.");
  expect(onClose).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("assignment-leave-go"));
  expect(onClose).toHaveBeenCalled();
});

it("candidate card sits before the filters with the mockup card layout", async () => {
  candidates = [{ candidateId: "12", projectId: "P-0001", projectName: "개발 프로젝트", developerId: "developer", name: "김개발", jobCode: "BE", jobName: "백엔드 개발", startDate: "2026-12-01", endDate: "2026-12-31" }];
  page();
  const card = await screen.findByTestId("assignment-candidates");
  const filters = screen.getByTestId("assignment-filters");
  expect(card.compareDocumentPosition(filters) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(card.firstElementChild).toHaveClass("assignment-card-h");
});

it("assign form subtitle follows how it was opened", async () => {
  form();
  expect(await screen.findByRole("dialog", { name: "투입 배정" })).toHaveTextContent("오른쪽 창 입력(유형 B)");
  cleanup();
  form({ recommendedMonth: "2026-12" });
  expect(await screen.findByRole("dialog", { name: "투입 배정" })).toHaveTextContent("AI 인력 추천에서 채움");
});

it("shows the no-price notice whenever the price field is hidden, with a reason that fits", async () => {
  form({ roles: ["PM"] });
  await ready();
  expect(screen.getByTestId("assignment-no-price")).toHaveTextContent("청구 단가는 개발자 월 단가(대체투입은 계약상 인력 단가)로 저장됩니다. 단가는 파트장·재무가 고칩니다.");
  cleanup();
  form();
  await ready();
  expect(screen.queryByTestId("assignment-no-price")).not.toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-input-type"), { target: { value: "FREE" } });
  expect(screen.getByTestId("assignment-no-price")).toHaveTextContent("무상지원은 매출이 없어 청구 단가를 0으로 저장합니다.");
});

it("monthly MM drops letters at once", async () => {
  form();
  await ready();
  fireEvent.change(screen.getByTestId("assignment-monthly-mm"), { target: { value: "0.5a" } });
  expect(screen.getByTestId("assignment-monthly-mm")).toHaveValue("0.5");
});

function Where() {
  const location = useLocation();
  return <div data-testid="where">{location.pathname + " " + JSON.stringify(location.state ?? null)}</div>;
}
const formWithWhere = (onClose = vi.fn()) =>
  render(
    <MemoryRouter>
      <AssignmentForm projects={[project]} roles={["FIN"]} onClose={onClose} onSaved={vi.fn()} />
      <Where />
    </MemoryRouter>,
  );
it("AI recommend passes the target only for a running non-internal project and closes without asking", async () => {
  const onClose = vi.fn();
  formWithWhere(onClose);
  await ready();
  fireEvent.change(screen.getByTestId("assignment-monthly-mm"), { target: { value: "0.5" } });
  fireEvent.change(screen.getByTestId("assignment-start-date"), { target: { value: "2026-12-05" } });
  fireEvent.click(screen.getByTestId("assignment-ai-recommend"));
  expect(screen.queryByRole("dialog", { name: "저장하지 않은 내용이 있습니다" })).not.toBeInTheDocument();
  expect(onClose).toHaveBeenCalled();
  // 주소 state 없이 UI-30 요청 통로(createScreenRequest)로 넘긴다.
  const sent = () => {
    const received: AiRecommendationEvent[] = [];
    subscribeAiRecommendation((event) => received.push(event))();
    return received.map(({ requestId: _id, ...rest }) => rest);
  };
  expect(screen.getByTestId("where")).toHaveTextContent("/screens/UI-30 null");
  expect(sent()).toEqual([{ targetType: "project", targetId: "P-0001", jobCode: "BE", startMonth: "2026-12" }]);
  cleanup();
  project.statusCode = "PRE";
  formWithWhere();
  await ready();
  fireEvent.click(screen.getByTestId("assignment-ai-recommend"));
  expect(screen.getByTestId("where")).toHaveTextContent("/screens/UI-30 null");
  expect(sent()).toEqual([{}]);
  cleanup();
  project.statusCode = "ING";
  project.contractTypeCode = "IN";
  formWithWhere();
  await ready();
  fireEvent.click(screen.getByTestId("assignment-ai-recommend"));
  expect(screen.getByTestId("where")).toHaveTextContent("/screens/UI-30 null");
  expect(sent()).toEqual([{}]);
});
it("the form shows the given subtitle and extra warning, and marks the hosting screen dirty", async () => {
  render(
    <MemoryRouter>
      <AssignmentForm
        projects={[project]}
        roles={["FIN"]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
        subtitle="AI 인력 추천에서 채움"
        screenId="UI-30"
        extraWarning="다른 기회 가배정은 그대로 둡니다."
      />
    </MemoryRouter>,
  );
  await ready();
  expect(screen.getByTestId("assignment-form")).toHaveTextContent("AI 인력 추천에서 채움");
  expect(screen.getByTestId("assignment-form-warning-0")).toHaveTextContent("다른 기회 가배정은 그대로 둡니다.");
  expect(screen.getByTestId("assignment-billing-price")).toBeInTheDocument();
});

it("change modal resets every value on type change, uses next month for MM, commas the price and focuses the first error", async () => {
  detail();
  await openHistory();
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  const dialog = await screen.findByRole("dialog", { name: "변경 기록" });
  // 시안 .fld: 이름표 왼쪽·칸 오른쪽
  expect(within(dialog).getByTestId("assignment-change-field-type")).toHaveClass("assignment-fld");
  expect(within(dialog).getByText("변경 종류", { selector: "label" })).toHaveClass("assignment-fl");
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "EARLY" } });
  fireEvent.change(screen.getByTestId("assignment-change-reason"), { target: { value: "HR" } });
  fireEvent.change(screen.getByTestId("assignment-change-memo"), { target: { value: "메모" } });
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "RATE" } });
  expect(screen.getByTestId("assignment-change-reason")).toHaveValue("");
  expect(screen.getByTestId("assignment-change-memo")).toHaveValue("");
  expect(screen.getByTestId("assignment-change-date")).toHaveValue("2026-12");
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "UPRC" } });
  expect(screen.getByTestId("assignment-change-price")).not.toHaveAttribute("type", "number");
  fireEvent.change(screen.getByTestId("assignment-change-price"), { target: { value: "9500000원" } });
  expect(screen.getByTestId("assignment-change-price")).toHaveValue("9,500,000");
  fireEvent.change(screen.getByTestId("assignment-change-price"), { target: { value: "" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText("단가를 숫자로 넣어 주세요.")).toBeInTheDocument();
  await waitFor(() => expect(screen.getByTestId("assignment-change-price")).toHaveFocus());
  fireEvent.change(screen.getByTestId("assignment-change-price"), { target: { value: "8,000,000" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText(/단가와 같습니다\.$/)).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("monthly MM change defaults to the start date when it is after next month", async () => {
  row.startDate = "2027-02-10";
  row.endDate = "2027-06-30";
  detail();
  await openHistory();
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  fireEvent.change(await screen.findByTestId("assignment-change-type"), { target: { value: "RATE" } });
  expect(screen.getByTestId("assignment-change-date")).toHaveValue("2027-02");
});

const PRICE_HINT = "적용 시작월부터 청구·매출을 새 단가로 계산하고 앞선 달은 그대로 둡니다.";
const MM_HINT = "적용 시작월부터 월 MM·원가·매출을 다시 계산하고 앞선 달은 그대로 둡니다.";
const laterChanges = () => {
  // 12월부터 단가 850만·월 MM 0.80으로 바꿔 둔 배정(지금 값은 11월 값)
  row.months = [
    { month: "202611", monthlyMm: 1, billingPrice: 8000000 },
    { month: "202612", monthlyMm: 0.8, billingPrice: 8500000 },
  ];
};

it("price change hint and same-price check use the price in effect for the chosen month", async () => {
  laterChanges();
  detail();
  await openHistory();
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  const dialog = await screen.findByRole("dialog", { name: "변경 기록" });
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "UPRC" } });
  expect(dialog).toHaveTextContent(`2026-11 지금 월 8,000,000원. ${PRICE_HINT}`);
  fireEvent.change(screen.getByTestId("assignment-change-date"), { target: { value: "2026-12" } });
  expect(dialog).toHaveTextContent(`2026-12 지금 월 8,500,000원. ${PRICE_HINT}`);
  fireEvent.change(screen.getByTestId("assignment-change-price"), { target: { value: "8500000" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText("2026-12 단가와 같습니다.")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
  // 지금 단가(800만)로 12월을 되돌리는 것은 그 달 값과 다르므로 저장한다.
  fireEvent.change(screen.getByTestId("assignment-change-price"), { target: { value: "8000000" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toMatchObject({ changeTypeCode: "UPRC", effectiveDate: "2026-12-01", newBillingPrice: 8000000 });
});

it("monthly MM change hint and same-value check use the contract MM in effect for the chosen month", async () => {
  laterChanges();
  detail();
  await openHistory();
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  const dialog = await screen.findByRole("dialog", { name: "변경 기록" });
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "RATE" } });
  expect(screen.getByTestId("assignment-change-date")).toHaveValue("2026-12");
  expect(dialog).toHaveTextContent(`지금 0.80. ${MM_HINT}`);
  fireEvent.change(screen.getByTestId("assignment-change-mm"), { target: { value: "0.8" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText("현재 월 MM과 같습니다.")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-change-mm"), { target: { value: "1" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toMatchObject({ changeTypeCode: "RATE", effectiveDate: "2026-12-01", newMonthlyMm: 1 });
});

it("a month without its own value falls back to the current price and monthly MM", async () => {
  row.months = [{ month: "202612", monthlyMm: 0.8, billingPrice: 8500000 }];
  detail();
  await openHistory();
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  const dialog = await screen.findByRole("dialog", { name: "변경 기록" });
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "UPRC" } });
  expect(dialog).toHaveTextContent(`2026-11 지금 월 8,000,000원. ${PRICE_HINT}`);
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "RATE" } });
  fireEvent.change(screen.getByTestId("assignment-change-date"), { target: { value: "2026-11" } });
  expect(dialog).toHaveTextContent(`지금 1.00. ${MM_HINT}`);
  fireEvent.change(screen.getByTestId("assignment-change-mm"), { target: { value: "1" } });
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(dialog).findByText("현재 월 MM과 같습니다.")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});

it("correction modal focuses the first missing field", async () => {
  history[0] = { ...history[0], changeTypeCode: "EXT", changeTypeName: "연장" };
  detail();
  await openHistory();
  fireEvent.click(within(await screen.findByTestId("assignment-history-1")).getByRole("button", { name: "정정" }));
  fireEvent.change(screen.getByTestId("assignment-correction-value"), { target: { value: "종료 2027-01-31" } });
  fireEvent.click(screen.getByTestId("assignment-correction-save"));
  await waitFor(() => expect(screen.getByTestId("assignment-correction-reason")).toHaveFocus());
  expect(screen.getByTestId("assignment-correction-reason")).toHaveAttribute("aria-invalid", "true");
});

it("bulk extension marks the field required and saves with Ctrl+S", async () => {
  page();
  fireEvent.click(await screen.findByTestId("list-select-A-0001"));
  fireEvent.click(screen.getByTestId("list-bulk-extension"));
  const dialog = screen.getByRole("dialog", { name: "연장 가능성 변경" });
  expect(within(within(dialog).getByText("연장 가능성", { selector: "label" })).getByText("*")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-bulk-value"), { target: { value: "L" } });
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toMatchObject({ extensionPossibilityCode: "L" });
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("1건의 연장 가능성을 '낮음'으로 바꿨습니다.");
});

const candidate = (): AssignmentCandidate => ({
  candidateId: "12",
  modifiedAt: "2026-11-14T01:02:03.456789Z",
  projectId: "P-0001",
  projectName: "개발 프로젝트",
  developerId: "developer",
  name: "김개발",
  managementNumber: "D-0001",
  jobCode: "BE",
  jobName: "백엔드 개발",
  startDate: "2026-12-01",
  endDate: "2026-12-31",
});
const candidateReads = () => request.mock.calls.filter(([u]) => String(u) === "/api/common/assignments/candidates").length;
const listReads = () => request.mock.calls.filter(([u, init]) => String(u).startsWith("/api/common/assignments?") && !(init as RequestInit | undefined)?.method).length;

it("removing a candidate sends its modifiedAt and a 409 toasts and reloads the list", async () => {
  candidates = [candidate()];
  page();
  fireEvent.click(await screen.findByTestId("candidate-remove-12"));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "빼기" }));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(body()).toEqual({ modifiedAt: "2026-11-14T01:02:03.456789Z" });
  cleanup();
  request.mockClear();
  writeFailure = { status: 409, message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요." };
  page();
  fireEvent.click(await screen.findByTestId("candidate-remove-12"));
  await waitFor(() => expect(listReads()).toBe(1));
  const before = candidateReads();
  fireEvent.click(within(screen.getByRole("alertdialog")).getByRole("button", { name: "빼기" }));
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
  await waitFor(() => expect(candidateReads()).toBeGreaterThan(before));
  expect(listReads()).toBe(2);
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
});

it("saving from a candidate shows server field errors for project and developer under the fields", async () => {
  candidates = [candidate()];
  page();
  fireEvent.click(await screen.findByTestId("candidate-confirm-12"));
  await waitFor(() => expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000"));
  writeFailure = {
    status: 400,
    message: "입력 내용을 확인해 주세요.",
    errors: [
      { field: "projectId", message: "종료·실주 프로젝트에는 배정할 수 없습니다." },
      { field: "developerId", message: "개발자를 골라 주세요." },
    ],
  };
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await within(screen.getByTestId("assignment-field-project")).findByText("종료·실주 프로젝트에는 배정할 수 없습니다.")).toBeInTheDocument();
  expect(within(screen.getByTestId("assignment-field-developer")).getByText("개발자를 골라 주세요.")).toBeInTheDocument();
  expect(screen.getByTestId("assignment-project")).toHaveAttribute("aria-invalid", "true");
  await waitFor(() => expect(screen.getByTestId("assignment-project")).toHaveFocus());
  expect(screen.getByTestId("assignment-form")).toBeInTheDocument();
});

it("an indirect developer is rejected as a developer field error", async () => {
  form();
  await pick();
  await waitFor(() => expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000"));
  writeFailure = { status: 400, message: "입력 내용을 확인해 주세요.", errors: [{ field: "developerId", message: "간접 인력은 투입 배정 대상이 아닙니다." }] };
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await within(screen.getByTestId("assignment-field-developer")).findByText("간접 인력은 투입 배정 대상이 아닙니다.")).toBeInTheDocument();
  expect(screen.getByTestId("assignment-developer")).toHaveAttribute("aria-invalid", "true");
});

it("a 409 on confirming a candidate closes the form, toasts and reloads the list", async () => {
  candidates = [candidate()];
  page();
  await waitFor(() => expect(listReads()).toBe(1));
  fireEvent.click(await screen.findByTestId("candidate-confirm-12"));
  await waitFor(() => expect(screen.getByTestId("assignment-billing-price")).toHaveValue("8,000,000"));
  const before = candidateReads();
  writeFailure = { status: 409, message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요." };
  fireEvent.click(screen.getByTestId("assignment-form-save"));
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
  expect(screen.queryByTestId("assignment-form")).not.toBeInTheDocument();
  await waitFor(() => expect(candidateReads()).toBeGreaterThan(before));
  expect(listReads()).toBe(2);
});

it("date helpers distinguish first open, project change and recommendation", () => {
  expect(assignmentInitialDates("2026-11-15", project)).toEqual({ startDate: "2026-11-15", endDate: "2026-12-31" });
  expect(assignmentInitialDates("2026-11-15", project, true).startDate).toBe("2026-01-01");
  expect(assignmentInitialDates("2026-11-15", { startDate: "2026-12-10", endDate: "2026-12-31" }, false, "2026-12").startDate).toBe("2026-12-10");
  expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
  expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
  expect(assignmentPriceEditable(["PM"])).toBe(false);
  expect(assignmentPriceEditable(["SALES_LDR"])).toBe(true);
});

it("a 409 on bulk cancel or bulk extension toasts and reloads the list, and cancel sends once on double click", async () => {
  writeFailure = { status: 409, message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요." };
  page();
  fireEvent.click(await screen.findByTestId("list-select-A-0001"));
  await waitFor(() => expect(listReads()).toBe(1));
  fireEvent.click(screen.getByTestId("list-bulk-cancel"));
  const button = within(screen.getByRole("alertdialog")).getByRole("button", { name: "배정 취소" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(await screen.findByTestId("assignment-toast")).toHaveTextContent("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
  expect(writes()).toHaveLength(1);
  await waitFor(() => expect(listReads()).toBe(2));
  fireEvent.click(await screen.findByTestId("list-select-A-0001"));
  writeFailure = { status: 409, message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요." };
  fireEvent.click(screen.getByTestId("list-bulk-extension"));
  fireEvent.click(screen.getByTestId("assignment-bulk-save"));
  await waitFor(() => expect(listReads()).toBe(3));
  expect(screen.queryByTestId("assignment-bulk-dialog")).not.toBeInTheDocument();
});

it("a 409 on a change closes the dialog, reloads the detail and tells the page; server field errors go under the fields", async () => {
  const onChanged = vi.fn();
  inRouter(<AssignmentDetail id={row.assignmentId} roles={["FIN"]} readOnly={false} onClose={vi.fn()} onChanged={onChanged} />);
  await openHistory();
  fireEvent.click(await screen.findByTestId("assignment-change-open"));
  fireEvent.change(screen.getByTestId("assignment-change-type"), { target: { value: "EXTP" } });
  fireEvent.change(screen.getByTestId("assignment-change-extension"), { target: { value: "H" } });
  writeFailure = { status: 400, message: "입력값을 확인해 주세요.", errors: [{ field: "reasonCode", message: "변경 사유를 골라 주세요." }] };
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  expect(await within(screen.getByTestId("assignment-change-field-reason")).findByRole("alert")).toHaveTextContent("변경 사유를 골라 주세요.");
  const reads = () => request.mock.calls.filter(([u, init]) => String(u) === "/api/common/assignments/A-0001" && !(init as RequestInit | undefined)?.method).length;
  const before = reads();
  writeFailure = { status: 409, message: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요." };
  fireEvent.click(screen.getByTestId("assignment-change-save"));
  await waitFor(() => expect(onChanged).toHaveBeenCalledWith("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.", "warn"));
  expect(screen.queryByTestId("assignment-change-dialog")).not.toBeInTheDocument();
  await waitFor(() => expect(reads()).toBeGreaterThan(before));
});

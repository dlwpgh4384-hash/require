import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { testAccount, WithTestAccount } from "../../test/testAccounts";
import { screenPermission } from "../../shared/permission";
import { CandidatePage } from "./CandidatePage";
import { CandidateForm } from "./CandidateForm";
import { CandidateDetail, CandidateContactForm } from "./CandidateDetail";
import {
  candidateDefaults,
  candidateErrors,
  candidatePayload,
  candidatePolicy,
  mergeCandidateExtraction,
  resumeFileError,
  resumeResultText,
  type Candidate,
  type CandidateOptions,
} from "./candidates";
import { addDays, koreanToday } from "../../shared/time";

const options: CandidateOptions = {
  codes: {
    EMP_TYPE: [
      { code: "EMP", codeName: "정규직" },
      { code: "FL", codeName: "프리랜서" },
      { code: "OS", codeName: "외주" },
    ],
    JOB: [
      { code: "BE", codeName: "백엔드" },
      { code: "PM", codeName: "PM" },
    ],
    GRADE: [
      { code: "HIGH", codeName: "고급" },
      { code: "MD", codeName: "중급" },
    ],
    CAND_SRC: [
      { code: "SITE", codeName: "구인 사이트" },
      { code: "REF", codeName: "지인 추천" },
    ],
    CNTC_STAT: [
      { code: "BEFORE", codeName: "연락 전" },
      { code: "CALLED", codeName: "연락함" },
      { code: "HOLD", codeName: "보류" },
      { code: "CTRT", codeName: "계약 완료" },
    ],
  },
  companies: [{ clientId: "vendor", clientName: "협력사" }],
};
let row: Candidate;
let other: Candidate;
let request: ReturnType<typeof vi.fn>;
let writeStatus: number;
let listFailed: boolean;
let listEmpty: boolean;
let shortagesEmpty: boolean;
let matchFailed: boolean;
let matchEmpty: boolean;
let lowMarginRate: number | undefined;
let itemLowMarginRate: number | undefined;
let contactFailed: boolean;
let extractionFailed: boolean;
let extraction: unknown;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const failure = (status: number, field = "memo") => ({
  ok: false,
  status,
  json: async () => ({
    success: false,
    message:
      status === 409
        ? "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."
        : "처리하지 못했습니다. 다시 시도해 주세요.",
    errors: [{ field, message: `${field} 입력 오류` }],
  }),
});
const writes = () =>
  request.mock.calls.filter((call) =>
    ["POST", "PUT"].includes(
      (call[1] as RequestInit | undefined)?.method || "",
    ),
  );
const calls = (path: string) =>
  request.mock.calls.filter((call) => String(call[0]) === path).length;
const defaultExtraction = () => ({
  fileName: "resume.txt",
  careerYears: 6.5,
  fields: [
    { field: "name", value: "추출 이름", estimated: false },
    { field: "phone", value: "010-2345-6789", estimated: false },
    {
      field: "primaryJobCode",
      value: "PM",
      estimated: true,
      originalText: "프로젝트 관리",
    },
    { field: "gradeCode", value: "HIGH", estimated: false },
    { field: "privacyAgreement", value: "true", estimated: false },
    {
      field: "availableDate",
      value: "2027-01-04",
      estimated: true,
      originalText: "2027.01.04부터",
    },
    { field: "homeAreaCode", value: "1168000000", estimated: false },
    { field: "homeAddress", value: "서울 강남구", estimated: false },
  ],
});
beforeEach(() => {
  writeStatus = 200;
  listFailed = false;
  listEmpty = false;
  shortagesEmpty = false;
  matchFailed = false;
  matchEmpty = false;
  lowMarginRate = 15;
  itemLowMarginRate = undefined;
  contactFailed = false;
  extractionFailed = false;
  extraction = defaultExtraction();
  row = {
    ...candidateDefaults(options),
    candidateId: "candidate",
    name: "지원자",
    desiredMonthlyRate: 6500000,
    employeeTypeCode: "FL",
    employeeTypeName: "프리랜서",
    primaryJobCode: "BE",
    primaryJobName: "백엔드",
    secondaryJobCode: "PM",
    secondaryJobName: "PM",
    skills: ["Java"],
    gradeName: "중급",
    sourceName: "구인 사이트",
    phone: "02-123-4567",
    email: "person@example.org",
    privacyAgreement: true,
    privacyAgreementDate: "2026-10-01",
    privacyAgreementUserId: "admin",
    privacyAgreementUserName: "관리자",
    homeAreaCode: "1168000000",
    homeAddress: "서울 강남구",
    companyName: null,
    contactStatusCode: "BEFORE",
    contactStatusName: "연락 전",
    lastContactDate: "2026-10-15",
    modifiedAt: null,
    daysSinceLastContact: 31,
    overdueContact: true,
  };
  other = {
    ...row,
    candidateId: "other",
    name: "외주 후보",
    employeeTypeCode: "OS",
    employeeTypeName: "외주",
    companyId: "vendor",
    companyName: "솔빛시스템",
    contactStatusCode: "CTRT",
    contactStatusName: "계약 완료",
    overdueContact: true,
    daysSinceLastContact: 40,
    privacyAgreement: false,
    privacyAgreementDate: null,
  };
  request = vi.fn(async (input: string, init?: RequestInit) => {
    const url = new URL(input, "http://test");
    const path = url.pathname;
    if (path === "/api/common/candidate-resume-extractions") {
      if (extractionFailed) return failure(400, "file");
      return ok(extraction);
    }
    if (["POST", "PUT"].includes(init?.method || "")) {
      if (writeStatus !== 200) return failure(writeStatus);
      const payload = JSON.parse(String(init?.body));
      if (path.endsWith("/contacts"))
        row = {
          ...row,
          contactStatusCode: payload.contactStatusCode,
          modifiedAt: "2026-11-16T00:00:00Z",
        };
      else if (init?.method === "POST")
        return ok({ ...row, ...payload, candidateId: "new" });
      else row = { ...row, ...payload, modifiedAt: "2026-11-16T00:00:00Z" };
      return ok(row);
    }
    if (path.startsWith("/api/common/code-groups/")) {
      const group = path.split("/")[4];
      return ok({
        items: (options.codes[group] || []).map((item) => ({
          ...item,
          useYn: "Y",
        })),
      });
    }
    if (path === "/api/common/clients")
      return ok({
        items: [
          {
            clientId: "vendor",
            clientName: "협력사",
            clientTypeCode: "VNDR",
            useYn: "Y",
          },
        ],
      });
    if (path === "/api/common/developers/areas")
      return ok({
        items: url.searchParams.has("parentAreaCode")
          ? [{ areaCode: "1168000000", areaName: "강남구" }]
          : [{ areaCode: "1100000000", areaName: "서울" }],
      });
    if (path === "/api/common/candidate-summary")
      return ok({
        totalCount: 3,
        activeCount: 1,
        interviewScheduledCount: 0,
        availableByNextMonthCount: 1,
        overdueContactCount: 1,
      });
    if (path === "/api/common/candidate-shortages")
      return ok({
        items: shortagesEmpty
          ? []
          : [
              {
                jobCode: "BE",
                jobName: "백엔드",
                shortageStartMonth: "2026-12",
                maxShortage: 1.6,
                availableCandidateCount: 1,
                totalCandidateCount: 2,
              },
            ],
      });
    if (path === "/api/common/candidates")
      return listFailed
        ? failure(500)
        : ok({ items: listEmpty ? [] : [row, other] });
    if (path.endsWith("/contacts"))
      return contactFailed
        ? failure(500)
        : ok({
            items: [
              {
                contactStatusCode: "BEFORE",
                contactStatusName: "연락 전",
                memo: "등록",
                recordedAt: "2026-11-14T16:00:00Z",
                recordedBy: "SYSTEM",
                systemRecord: true,
              },
            ],
          });
    if (path.endsWith("/matches"))
      return matchFailed
        ? failure(500)
        : ok({
            ...(lowMarginRate === undefined ? {} : { lowMarginRate }),
            items: matchEmpty
              ? []
              : [
                  {
                    opportunityId: "opp",
                    opportunityName: "영업 기회",
                    winningProbability: 50,
                    jobName: "백엔드",
                    gradeName: "중급",
                    startMonth: "2026-12",
                    slotMonthlyRate: 8000000,
                    expectedProfitRate: 14.99,
                    lowMargin: false,
                    ...(itemLowMarginRate === undefined
                      ? {}
                      : { lowMarginRate: itemLowMarginRate }),
                  },
                  {
                    opportunityId: "opp2",
                    opportunityName: "두 번째 기회",
                    winningProbability: 70,
                    jobName: "백엔드",
                    gradeName: "고급",
                    startMonth: "2027-01",
                    slotMonthlyRate: 9000000,
                    expectedProfitRate: 20,
                    lowMargin: true,
                    ...(itemLowMarginRate === undefined
                      ? {}
                      : { lowMarginRate: itemLowMarginRate }),
                  },
                ],
          });
    if (path === "/api/common/candidates/candidate") return ok(row);
    throw new Error(`Unexpected test request: ${input}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
async function readyForm() {
  await waitFor(() =>
    expect(screen.getByTestId("candidate-form-save")).toBeEnabled(),
  );
}
function renderForm(
  edit = true,
  overrides: Partial<Parameters<typeof CandidateForm>[0]> = {},
) {
  return render(
    <CandidateForm
      options={options}
      candidate={edit ? row : undefined}
      onCancel={vi.fn()}
      onSaved={vi.fn()}
      onConflict={vi.fn()}
      {...overrides}
    />,
  );
}
function renderDetail(
  overrides: Partial<Parameters<typeof CandidateDetail>[0]> = {},
) {
  return render(
    <CandidateDetail
      candidateId="candidate"
      options={options}
      onClose={vi.fn()}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
      {...overrides}
    />,
  );
}
async function fillCreate() {
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-name"), {
    target: { value: "새 후보" },
  });
  fireEvent.change(screen.getByTestId("candidate-skills"), {
    target: { value: "Java, Java, SQL" },
  });
  fireEvent.change(screen.getByTestId("candidate-desired-monthly-rate"), {
    target: { value: "6500000" },
  });
}
function upload(file: File) {
  fireEvent.change(screen.getByTestId("candidate-resume"), {
    target: { files: [file] },
  });
}
const nextMonth = () => {
  const [year, month] = koreanToday().split("-").map(Number);
  return month === 12
    ? `${year + 1}-01-01`
    : `${year}-${String(month + 1).padStart(2, "0")}-01`;
};

it("renders mockup KPIs with total count and next-month sub line", async () => {
  render(<CandidatePage />);
  const kpis = await screen.findByTestId("candidate-summary");
  expect(within(kpis).getByTestId("candidate-kpi-total")).toHaveTextContent(
    "후보3명진행 중 1명",
  );
  expect(
    within(kpis).getByTestId("candidate-kpi-available"),
  ).toHaveTextContent(
    `다음 달 시작까지 투입 가능1명투입 가능일 ${nextMonth()}까지`,
  );
  expect(within(kpis).getByTestId("candidate-kpi-overdue")).toHaveTextContent(
    "30일 넘게 연락 안 함1명",
  );
  expect(within(kpis).getByTestId("candidate-kpi-interview")).toHaveTextContent(
    "면접 예정0명",
  );
});
it("renders the shortage card with mockup headers, colours and filter button", async () => {
  render(<CandidatePage />);
  const card = await screen.findByTestId("candidate-shortages");
  expect(card).toHaveTextContent("부족 직무와 맞는 후보");
  expect(card).toHaveTextContent(
    "다음 달부터 6개월 · 영업 기회 기준(미충원 제외)",
  );
  await within(card).findByTestId("candidate-shortage-0");
  for (const label of [
    "직무",
    "부족 시작",
    "가장 많이 부족",
    "그때까지 투입 가능한 후보",
  ])
    expect(within(card).getByRole("columnheader", { name: label })).toBeVisible();
  expect(within(card).getByText("1.6명")).toHaveClass("candidate-bad");
  expect(within(card).getByTestId("candidate-shortage-count-0")).toHaveTextContent(
    "1명 (전체 2명)",
  );
  expect(within(card).getByTestId("candidate-shortage-count-0")).toHaveClass(
    "candidate-warn",
  );
  fireEvent.change(screen.getByTestId("candidate-filter-grade-code"), {
    target: { value: "MD" },
  });
  fireEvent.click(within(card).getByText("후보 보기"));
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        (call) => String(call[0]) === "/api/common/candidates?jobCode=BE",
      ),
    ).toBe(true),
  );
  expect(screen.getByTestId("candidate-filter-job-code")).toHaveValue("BE");
  expect(screen.getByTestId("candidate-filter-grade-code")).toHaveValue("");
});
it("shows the mockup empty texts for shortages and list", async () => {
  listEmpty = true;
  shortagesEmpty = true;
  render(<CandidatePage />);
  expect(
    await screen.findByText("부족이 예상되는 직무가 없습니다."),
  ).toBeInTheDocument();
  expect(
    await screen.findByText("조건에 맞는 결과가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.queryByTestId("list-retry")).not.toBeInTheDocument();
});
it("failed list exposes retry and then real rows", async () => {
  listFailed = true;
  render(<CandidatePage />);
  fireEvent.click(await screen.findByTestId("list-retry"));
  listFailed = false;
  fireEvent.click(await screen.findByTestId("list-retry"));
  expect(await screen.findByTestId("list-row-candidate")).toBeInTheDocument();
});
it("list follows mockup columns, status badges and n-days badge only for open candidates", async () => {
  render(<CandidatePage />);
  const first = await screen.findByTestId("list-row-candidate");
  const list = screen.getByTestId("candidate-list");
  const headers = within(list)
    .getAllByRole("columnheader")
    .map((cell) => cell.textContent?.replace(/[▲▼↑↓]/g, "").trim());
  expect(headers).toEqual([
    "이름",
    "구분",
    "직무",
    "등급",
    "기술",
    "거주지",
    "희망 단가",
    "투입 가능일",
    "연락 상태",
    "최근 연락",
    "출처",
  ]);
  expect(within(first).getByText("연락 전")).toHaveClass("ls-b-neutral");
  expect(within(first).getByText("31일 전")).toHaveClass("ls-b-warn");
  expect(within(first).getByText("6,500,000")).toBeInTheDocument();
  const closed = screen.getByTestId("list-row-other");
  expect(within(closed).getByText("계약 완료")).toHaveClass("ls-b-ok");
  expect(within(closed).queryByText("40일 전")).not.toBeInTheDocument();
  expect(within(closed).getByText("솔빛시스템")).toHaveClass("ls-cap");
  // 머리를 누르면 그 열로 정렬한다.
  fireEvent.click(screen.getByTestId("list-head-name"));
  await waitFor(() =>
    expect(
      list.querySelectorAll("tbody tr")[0].getAttribute("data-testid"),
    ).toBe("list-row-other"),
  );
});
it("whole row opens the drawer with mockup header, notes, consent and red low margin", async () => {
  render(<CandidatePage />);
  fireEvent.click(await screen.findByTestId("list-row-candidate"));
  const drawer = await screen.findByTestId("candidate-detail");
  expect(drawer).toHaveClass("ls-drawer");
  expect(drawer).toHaveTextContent("프리랜서 · 백엔드 (보조 PM) · 중급");
  expect(await within(drawer).findByTestId("candidate-rate-note")).toHaveTextContent(
    "본인에게 주는 금액",
  );
  expect(within(drawer).getByTestId("candidate-consent")).toHaveTextContent(
    "동의 확인 10-01 관리자",
  );
  expect(await within(drawer).findByTestId("candidate-match-0")).toHaveTextContent(
    "영업 기회 50%",
  );
  for (const label of ["영업 기회", "직무 · 등급", "시작", "자리 단가", "예상 이익률"])
    expect(within(drawer).getByRole("columnheader", { name: label })).toBeVisible();
  // 서버가 준 기준(15%)으로 빨강을 정한다.
  expect(within(drawer).getByTestId("candidate-match-rate-0")).toHaveTextContent(
    "15.0%",
  );
  expect(within(drawer).getByTestId("candidate-match-rate-0")).toHaveClass(
    "candidate-bad",
  );
  expect(within(drawer).getByTestId("candidate-match-rate-1")).not.toHaveClass(
    "candidate-bad",
  );
  expect(within(drawer).getByTestId("candidate-match-note")).toHaveTextContent(
    "예상 이익률 = (자리 단가 − 희망 단가 − 팀비·경비) ÷ 자리 단가. 기준 15%보다 낮으면 단가를 다시 협의하세요.",
  );
  expect(screen.getByTestId("candidate-edit")).toHaveClass("ls-btn-sm");
  expect(screen.getByTestId("candidate-contact")).toHaveTextContent("연락 기록");
  fireEvent.click(screen.getByTestId("candidate-detail-close"));
  expect(screen.queryByTestId("candidate-detail")).not.toBeInTheDocument();
});
it("without a server threshold the existing low-margin flag colours the rate", async () => {
  lowMarginRate = undefined;
  renderDetail();
  expect(await screen.findByTestId("candidate-match-rate-1")).toHaveClass(
    "candidate-bad",
  );
  expect(screen.getByTestId("candidate-match-rate-0")).not.toHaveClass(
    "candidate-bad",
  );
  expect(screen.getByTestId("candidate-match-note")).toHaveTextContent(
    "기준보다 낮으면 단가를 다시 협의하세요.",
  );
});
it("a per-seat threshold from the operating standard decides the red colour", async () => {
  lowMarginRate = undefined;
  itemLowMarginRate = 25;
  renderDetail();
  expect(await screen.findByTestId("candidate-match-rate-0")).toHaveClass(
    "candidate-bad",
  );
  expect(screen.getByTestId("candidate-match-rate-1")).toHaveClass(
    "candidate-bad",
  );
  expect(screen.getByTestId("candidate-match-note")).toHaveTextContent(
    "기준 25%보다 낮으면 단가를 다시 협의하세요.",
  );
});
it("outsourcing note, no consent line and empty matches text", async () => {
  row = {
    ...row,
    employeeTypeCode: "OS",
    employeeTypeName: "외주",
    companyName: "솔빛시스템",
    privacyAgreement: false,
    privacyAgreementDate: null,
  };
  matchEmpty = true;
  renderDetail();
  expect(await screen.findByTestId("candidate-rate-note")).toHaveTextContent(
    "업체에 주는 금액(부가세 별도)",
  );
  expect(screen.getByTestId("candidate-detail")).toHaveTextContent(
    "외주 · 솔빛시스템 · 백엔드",
  );
  expect(screen.queryByTestId("candidate-consent")).not.toBeInTheDocument();
  expect(
    await screen.findByText(
      "지금 진행 중인 영업 기회 가운데 맞는 자리가 없습니다.",
    ),
  ).toBeInTheDocument();
});
it("contact tab shows count and a date/status/memo table", async () => {
  renderDetail();
  const tab = await screen.findByTestId("candidate-detail-tab-contacts");
  await waitFor(() => expect(tab).toHaveTextContent("연락 기록 (1)"));
  fireEvent.click(tab);
  const history = await screen.findByTestId("candidate-history-0");
  expect(history).toHaveTextContent("2026-11-15");
  expect(within(history).getByText("연락 전")).toHaveClass("ls-b-neutral");
  expect(history).toHaveTextContent("등록");
  const panel = screen.getByTestId("candidate-contacts-panel");
  expect(
    within(panel)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["날짜", "상태", "메모"]);
});
it("narrow screens show list cards like other screens", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: query === "(max-width: 767px)",
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  render(<CandidatePage />);
  const card = await screen.findByTestId("list-card-candidate");
  expect(card).toHaveTextContent("지원자");
  expect(card).toHaveTextContent("백엔드 · 중급 · 프리랜서");
  expect(card).toHaveTextContent(`${row.availableDate}부터`);
  fireEvent.click(card);
  expect(await screen.findByTestId("candidate-detail")).toBeInTheDocument();
});
it("Enter shortcut searches with the current filters", async () => {
  render(<CandidatePage />);
  await screen.findByTestId("list-row-candidate");
  await waitFor(() =>
    expect(screen.getByTestId("candidate-filter-availability")).toHaveTextContent(
      "3개월 안",
    ),
  );
  fireEvent.change(screen.getByTestId("candidate-filter-availability"), {
    target: { value: "next-month" },
  });
  (document.activeElement as HTMLElement | null)?.blur();
  fireEvent.keyDown(window, { key: "Enter" });
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        (call) =>
          String(call[0]) ===
          "/api/common/candidates?availabilityWindow=next-month",
      ),
    ).toBe(true),
  );
  expect(screen.getByText("투입 가능")).toBeInTheDocument();
});
it("excel download asks the server with the applied filters and toasts the count", async () => {
  render(<CandidatePage />);
  await screen.findByTestId("list-row-candidate");
  const original = request.getMockImplementation()!;
  const created = vi.fn(() => "blob:candidate");
  vi.stubGlobal(
    "URL",
    class extends URL {
      static createObjectURL = created;
      static revokeObjectURL = vi.fn();
    },
  );
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => {});
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    String(url).includes("/excel?")
      ? ({
          ok: true,
          headers: new Headers({
            "Content-Type":
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "X-Total-Count": "3",
            "Content-Disposition":
              "attachment; filename*=UTF-8''%EC%99%B8%EB%B6%80.xlsx",
          }),
          blob: async () => new Blob(["PK"]),
        } as unknown as Response)
      : original(url, init),
  );
  fireEvent.click(screen.getByTestId("list-download"));
  expect(await screen.findByTestId("candidate-toast")).toHaveTextContent(
    "엑셀 파일을 만들었습니다 (조회 결과 3건)",
  );
  expect(
    request.mock.calls.some((call) =>
      String(call[0]).startsWith("/api/common/candidates/excel?"),
    ),
  ).toBe(true);
  expect(click).toHaveBeenCalledOnce();
  expect(created).toHaveBeenCalledOnce();
  click.mockRestore();
});
it("failed excel download shows the server message and no completion toast", async () => {
  render(<CandidatePage />);
  await screen.findByTestId("list-row-candidate");
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    String(url).includes("/excel?")
      ? ({
          ok: false,
          headers: new Headers(),
          json: async () => ({ message: "다운로드 실패" }),
        } as unknown as Response)
      : original(url, init),
  );
  fireEvent.click(screen.getByTestId("list-download"));
  expect(await screen.findByTestId("candidate-toast")).toHaveTextContent(
    "다운로드 실패",
  );
  expect(screen.queryByText(/엑셀 파일을 만들었습니다/)).not.toBeInTheDocument();
});
it("new form uses mockup title, defaults, labels, texts and focuses the name", async () => {
  renderForm(false);
  await readyForm();
  expect(screen.getByTestId("candidate-form-dialog")).toHaveAccessibleName(
    "외부 인력 후보 등록",
  );
  expect(screen.getByTestId("candidate-name")).toHaveFocus();
  expect(screen.getByTestId("candidate-form")).toHaveAttribute("novalidate");
  expect(screen.getByTestId("candidate-grade-code")).toHaveValue("MD");
  expect(screen.getByTestId("candidate-source-code")).toHaveValue("SITE");
  expect(screen.getByTestId("candidate-employee-type-code")).toHaveValue("FL");
  expect(
    screen.getByTestId("candidate-employee-type-code").querySelectorAll("option"),
  ).toHaveLength(2);
  expect(screen.getByTestId("candidate-primary-job-code")).toHaveValue("BE");
  expect(screen.getByTestId("candidate-available-date")).toHaveValue(
    addDays(koreanToday(), 30),
  );
  expect(screen.getByTestId("candidate-privacy-agreement")).not.toBeChecked();
  expect(screen.getByLabelText(/후보에게 개인정보\(전화번호·이메일\) 수집·이용 동의를 받았습니다/)).toBe(
    screen.getByTestId("candidate-privacy-agreement"),
  );
  expect(screen.getByText("(전화번호·이메일을 넣으면 필수)")).toBeInTheDocument();
  expect(screen.getByLabelText(/거주지\(시·도\)/)).toBe(
    screen.getByTestId("candidate-area-0"),
  );
  expect(screen.getByLabelText(/거주지\(시·군·구\)/)).toHaveTextContent(
    "시·도를 먼저 고르세요",
  );
  expect(screen.getByTestId("candidate-area-0")).toHaveTextContent("선택 안 함");
  expect(screen.getByTestId("candidate-secondary-job-code")).toHaveTextContent(
    "선택 안 함",
  );
  expect(screen.getByLabelText(/희망 단가\(원\/월\)/)).toBe(
    screen.getByTestId("candidate-desired-monthly-rate"),
  );
  expect(screen.getByTestId("candidate-phone")).toHaveAttribute(
    "placeholder",
    "010-1234-5678",
  );
  expect(screen.getByTestId("candidate-email")).toHaveAttribute(
    "placeholder",
    "name@example.com",
  );
  expect(screen.getByTestId("candidate-skills")).toHaveAttribute(
    "placeholder",
    "예: Java, Spring, Oracle",
  );
  expect(
    screen.getByText(
      "쉼표(,)로 구분해 여러 개 적습니다. 개발자 풀의 보유 스킬과 같습니다.",
    ),
  ).toBeInTheDocument();
  expect(screen.getByTestId("candidate-memo-counter")).toHaveTextContent("0/500");
  fireEvent.change(screen.getByTestId("candidate-memo"), {
    target: { value: "메모" },
  });
  expect(screen.getByTestId("candidate-memo-counter")).toHaveTextContent("2/500");
  fireEvent.change(screen.getByTestId("candidate-desired-monthly-rate"), {
    target: { value: "6500000" },
  });
  expect(screen.getByTestId("candidate-desired-monthly-rate")).toHaveValue(
    "6,500,000",
  );
  // 시안: [이력서 등록]은 단추 줄 왼쪽 첫 단추.
  const buttons = screen
    .getByTestId("candidate-form")
    .querySelectorAll(".candidate-form-foot button");
  expect([...buttons].map((button) => button.textContent)).toEqual([
    "이력서 등록",
    "취소",
    "등록",
  ]);
  expect(screen.getByTestId("candidate-resume")).toHaveAttribute(
    "accept",
    ".txt,.doc,.docx,.pdf,.ppt,.pptx,.hwp,.hwpx",
  );
});
it("outsourcing shows the company select with mockup option and help", async () => {
  renderForm(false, { options: { ...options, companies: [] } });
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-employee-type-code"), {
    target: { value: "OS" },
  });
  expect(screen.getByTestId("candidate-company-id")).toHaveTextContent(
    "협력사를 고르세요",
  );
  expect(
    screen.getByText(
      "거래처에 협력사로 등록된 업체만 나옵니다. 없으면 「기준정보 › 거래처·영업 파트」에서 먼저 등록하세요.",
    ),
  ).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("candidate-employee-type-code"), {
    target: { value: "FL" },
  });
  expect(screen.queryByTestId("candidate-company-id")).not.toBeInTheDocument();
});
it("empty create shows mockup field errors under fields and focuses the first", async () => {
  renderForm(false);
  await readyForm();
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  expect(screen.getByTestId("candidate-error-name")).toHaveTextContent(
    "이름을 넣어 주세요.",
  );
  expect(
    screen.getByTestId("candidate-error-desiredMonthlyRate"),
  ).toHaveTextContent("희망 단가를 넣어 주세요.");
  expect(screen.getByTestId("candidate-error-skills")).toHaveTextContent(
    "기술을 하나 이상 적어 주세요.",
  );
  await waitFor(() => expect(screen.getByTestId("candidate-name")).toHaveFocus());
  expect(writes()).toHaveLength(0);
  fireEvent.change(screen.getByTestId("candidate-name"), {
    target: { value: "이" },
  });
  expect(screen.queryByTestId("candidate-error-name")).not.toBeInTheDocument();
});
it("checks company, consent, phone and email with mockup messages", async () => {
  renderForm(false);
  await fillCreate();
  fireEvent.change(screen.getByTestId("candidate-employee-type-code"), {
    target: { value: "OS" },
  });
  fireEvent.change(screen.getByTestId("candidate-phone"), {
    target: { value: "전화" },
  });
  fireEvent.change(screen.getByTestId("candidate-email"), {
    target: { value: "bad-mail" },
  });
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  expect(screen.getByTestId("candidate-error-companyId")).toHaveTextContent(
    "외주는 소속 협력사를 골라 주세요.",
  );
  expect(
    screen.getByTestId("candidate-error-privacyAgreement"),
  ).toHaveTextContent(
    "전화번호·이메일을 저장하려면 개인정보 수집·이용 동의를 받았는지 체크해 주세요.",
  );
  expect(screen.getByTestId("candidate-error-phone")).toHaveTextContent(
    "전화번호는 숫자와 - 로 적어 주세요.",
  );
  expect(screen.getByTestId("candidate-error-email")).toHaveTextContent(
    "이메일 형식을 확인해 주세요.",
  );
  await waitFor(() =>
    expect(screen.getByTestId("candidate-company-id")).toHaveFocus(),
  );
  // 전화번호·이메일을 모두 지우면 동의 오류도 지운다.
  fireEvent.change(screen.getByTestId("candidate-phone"), {
    target: { value: "" },
  });
  expect(
    screen.getByTestId("candidate-error-privacyAgreement"),
  ).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("candidate-email"), {
    target: { value: "" },
  });
  expect(
    screen.queryByTestId("candidate-error-privacyAgreement"),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("update has mockup title, no resume button and sends raw nullable version", async () => {
  const saved = vi.fn();
  renderForm(true, { onSaved: saved });
  await readyForm();
  expect(screen.getByTestId("candidate-form-dialog")).toHaveAccessibleName(
    "지원자 정보 수정",
  );
  expect(screen.queryByTestId("candidate-resume")).not.toBeInTheDocument();
  expect(screen.queryByTestId("candidate-resume-button")).not.toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByTestId("candidate-area-1")).toHaveValue("1168000000"),
  );
  expect(screen.getByTestId("candidate-area-0")).toHaveValue("1100000000");
  expect(screen.getByTestId("candidate-desired-monthly-rate")).toHaveValue(
    "6,500,000",
  );
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.modifiedAt).toBeNull();
  expect(body).not.toHaveProperty("candidateId");
  expect(body).not.toHaveProperty("privacyAgreementDate");
});
it("area list loads once and is not re-read on every pick", async () => {
  row = { ...row, homeAreaCode: null, homeAddress: null };
  renderForm();
  await readyForm();
  const rootCalls = () => calls("/api/common/developers/areas");
  expect(rootCalls()).toBe(1);
  fireEvent.change(screen.getByTestId("candidate-area-0"), {
    target: { value: "1100000000" },
  });
  await waitFor(() =>
    expect(screen.getByTestId("candidate-area-1")).toBeEnabled(),
  );
  fireEvent.change(screen.getByTestId("candidate-area-1"), {
    target: { value: "1168000000" },
  });
  fireEvent.change(screen.getByTestId("candidate-area-0"), {
    target: { value: "" },
  });
  expect(rootCalls()).toBe(1);
  expect(screen.getByTestId("candidate-area-1")).toHaveTextContent(
    "시·도를 먼저 고르세요",
  );
});
it("successful create toasts the mockup text and shows the new candidate first", async () => {
  render(<CandidatePage />);
  await screen.findByTestId("list-row-candidate");
  await waitFor(() =>
    expect(screen.getByTestId("candidate-register")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("candidate-register"));
  await fillCreate();
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  await waitFor(() =>
    expect(
      screen.queryByTestId("candidate-form-dialog"),
    ).not.toBeInTheDocument(),
  );
  expect(await screen.findByTestId("candidate-toast")).toHaveTextContent(
    "새 후보님을 후보로 등록했습니다.",
  );
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.skills).toEqual(["Java", "SQL"]);
  expect(body.desiredMonthlyRate).toBe(6500000);
  expect(body.employeeTypeCode).toBe("FL");
  expect(body.privacyAgreement).toBe(false);
  await waitFor(() =>
    expect(
      screen
        .getByTestId("candidate-list")
        .querySelectorAll("tbody tr")[0]
        .getAttribute("data-testid"),
    ).toBe("list-row-new"),
  );
  expect(screen.queryByTestId("candidate-detail")).not.toBeInTheDocument();
});
it("edit from the drawer toasts the mockup text and reloads", async () => {
  const notice = vi.fn();
  renderDetail({ onNotice: notice });
  await waitFor(() =>
    expect(screen.getByTestId("candidate-edit")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("candidate-edit"));
  await readyForm();
  const before = calls("/api/common/candidates/candidate");
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  await waitFor(() =>
    expect(notice).toHaveBeenCalledWith(
      "지원자님 정보를 고쳤습니다. 맞는 자리 이익률도 새 값으로 계산했습니다.",
    ),
  );
  await waitFor(() =>
    expect(calls("/api/common/candidates/candidate")).toBeGreaterThan(before),
  );
});
it("400 retains draft with field errors", async () => {
  writeStatus = 400;
  renderForm();
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-memo"), {
    target: { value: "작성한 메모" },
  });
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  expect(await screen.findByText("memo 입력 오류")).toBeInTheDocument();
  expect(screen.getByTestId("candidate-memo")).toHaveValue("작성한 메모");
  fireEvent.change(screen.getByTestId("candidate-memo"), {
    target: { value: "수정" },
  });
  expect(screen.queryByText("memo 입력 오류")).not.toBeInTheDocument();
});
it("409 update closes dialog, warns and reloads current detail", async () => {
  writeStatus = 409;
  const notice = vi.fn();
  renderDetail({ onNotice: notice });
  await waitFor(() =>
    expect(screen.getByTestId("candidate-edit")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("candidate-edit"));
  await readyForm();
  const previous = calls("/api/common/candidates/candidate");
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  await waitFor(() =>
    expect(
      screen.queryByTestId("candidate-form-dialog"),
    ).not.toBeInTheDocument(),
  );
  expect(notice).toHaveBeenCalledWith(
    "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
    "warn",
  );
  await waitFor(() =>
    expect(calls("/api/common/candidates/candidate")).toBeGreaterThan(
      previous,
    ),
  );
});
it("contact dialog follows the mockup: title, current status, memo error and help", async () => {
  const cancel = vi.fn();
  render(
    <CandidateContactForm
      candidate={row}
      options={options}
      onCancel={cancel}
      onSaved={vi.fn()}
      onConflict={vi.fn()}
    />,
  );
  expect(screen.getByTestId("candidate-contact-dialog")).toHaveAccessibleName(
    "지원자 연락 기록",
  );
  const status = screen.getByTestId("candidate-contact-status");
  expect(status).toHaveValue("BEFORE");
  expect(
    [...status.querySelectorAll("option")].map((option) => option.textContent),
  ).toEqual(["연락 전", "연락함", "보류", "계약 완료"]);
  expect(screen.getByText("예: 단가 협의, 면접 일정")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("candidate-contact-save"));
  expect(
    await screen.findByTestId("candidate-contact-memo-error"),
  ).toHaveTextContent("메모를 적어 주세요.");
  fireEvent.click(screen.getByTestId("candidate-contact-cancel"));
  expect(cancel).toHaveBeenCalled();
  expect(writes()).toHaveLength(0);
});
it("contact dialog asks before discarding a typed memo", async () => {
  const cancel = vi.fn();
  render(
    <CandidateContactForm
      candidate={row}
      options={options}
      onCancel={cancel}
      onSaved={vi.fn()}
      onConflict={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByTestId("candidate-contact-memo"), {
    target: { value: "통화" },
  });
  fireEvent.click(screen.getByTestId("candidate-contact-cancel"));
  expect(cancel).not.toHaveBeenCalled();
  expect(await screen.findByTestId("candidate-contact-discard")).toBeVisible();
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(cancel).toHaveBeenCalled();
});
it("same-state contact submits nullable version and toasts the mockup text", async () => {
  const notice = vi.fn();
  renderDetail({ onNotice: notice });
  await waitFor(() =>
    expect(screen.getByTestId("candidate-contact")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("candidate-contact"));
  fireEvent.change(screen.getByTestId("candidate-contact-memo"), {
    target: { value: "통화함" },
  });
  fireEvent.click(screen.getByTestId("candidate-contact-save"));
  await waitFor(() =>
    expect(notice).toHaveBeenCalledWith("연락 기록을 저장했습니다."),
  );
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    modifiedAt: null,
    contactStatusCode: "BEFORE",
    memo: "통화함",
  });
});
it("409 contact closes dialog and refreshes detail", async () => {
  writeStatus = 409;
  const notice = vi.fn();
  renderDetail({ onNotice: notice });
  await waitFor(() =>
    expect(screen.getByTestId("candidate-contact")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("candidate-contact"));
  fireEvent.change(screen.getByTestId("candidate-contact-memo"), {
    target: { value: "확인" },
  });
  fireEvent.click(screen.getByTestId("candidate-contact-save"));
  await waitFor(() =>
    expect(
      screen.queryByTestId("candidate-contact-dialog"),
    ).not.toBeInTheDocument(),
  );
  expect(notice).toHaveBeenCalledWith(
    "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
    "warn",
  );
  await waitFor(() =>
    expect(calls("/api/common/candidates/candidate")).toBe(2),
  );
});
it("an account without 민감 체크 and 등록·수정 hides amounts and edit controls without adding HTTP auth", async () => {
  // PM 처음 권한에는 외부 인력 후보(M012) 등록·수정이 있으므로, 조회만 있는 표로 본다(서버 값만 쓴다).
  const viewOnly = testAccount(["PM"]);
  viewOnly.meta.permissions = viewOnly.meta.permissions!.map((p) =>
    p.screenId === "UI-33" ? { ...p, createYn: "N", updateYn: "N" } : p,
  );
  render(
    <WithTestAccount account={viewOnly}>
      <CandidatePage />
    </WithTestAccount>,
  );
  fireEvent.click(await screen.findByTestId("list-row-candidate"));
  expect(screen.queryByText("희망 단가")).not.toBeInTheDocument();
  expect(screen.queryByTestId("candidate-register")).not.toBeInTheDocument();
  await screen.findByTestId("candidate-match-0");
  expect(screen.queryByText("예상 이익률")).not.toBeInTheDocument();
  expect(screen.queryByTestId("candidate-edit")).not.toBeInTheDocument();
  expect(screen.queryByTestId("candidate-rate-note")).not.toBeInTheDocument();
});
it("terminal status keeps edit and matching", async () => {
  row.contactStatusCode = "CTRT";
  row.contactStatusName = "계약 완료";
  renderDetail();
  expect(await screen.findByTestId("candidate-match-0")).toBeInTheDocument();
  expect(screen.getByTestId("candidate-edit")).toBeEnabled();
});
it("matching failure can retry without losing candidate basic fields", async () => {
  matchFailed = true;
  renderDetail();
  expect(
    await screen.findByTestId("candidate-matches-retry"),
  ).toHaveClass("ls-btn");
  matchFailed = false;
  fireEvent.click(screen.getByTestId("candidate-matches-retry"));
  expect(await screen.findByTestId("candidate-match-0")).toBeInTheDocument();
});
it("contact failure exposes its own retry", async () => {
  contactFailed = true;
  renderDetail();
  fireEvent.click(await screen.findByTestId("candidate-detail-tab-contacts"));
  fireEvent.click(await screen.findByTestId("candidate-contacts-retry"));
  contactFailed = false;
  fireEvent.click(await screen.findByTestId("candidate-contacts-retry"));
  expect(await screen.findByTestId("candidate-history-0")).toBeInTheDocument();
});
it("resume import fills empty and default fields with the exact mockup result text", async () => {
  renderForm(false);
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-name"), {
    target: { value: "입력 이름" },
  });
  upload(new File(["resume"], "resume.txt", { type: "text/plain" }));
  expect(await screen.findByTestId("candidate-resume-result")).toHaveTextContent(
    "「resume.txt」에서 4칸을 채웠습니다(✓ 2 · ⚠ 2). ⚠ 칸과 빈칸을 확인하고 [등록]을 누르세요. 개인정보 동의는 직접 체크합니다. 파일은 저장하지 않습니다. 이미 적은 1칸은 그대로 두었습니다.",
  );
  expect(screen.getByTestId("candidate-name")).toHaveValue("입력 이름");
  expect(screen.getByTestId("candidate-phone")).toHaveValue("010-2345-6789");
  expect(screen.getByTestId("candidate-mark-phone")).toHaveTextContent(
    "✓ 이력서에서 가져옴",
  );
  // 처음 값(첫 직무·오늘+30)과 같은 칸은 빈칸으로 보고 채운다.
  expect(screen.getByTestId("candidate-primary-job-code")).toHaveValue("PM");
  expect(screen.getByTestId("candidate-mark-primaryJobCode")).toHaveTextContent(
    "⚠ 확인해 주세요 · 원문: 프로젝트 관리",
  );
  expect(screen.getByTestId("candidate-available-date")).toHaveValue(
    "2027-01-04",
  );
  await waitFor(() =>
    expect(screen.getByTestId("candidate-area-1")).toHaveValue("1168000000"),
  );
  expect(screen.getByTestId("candidate-grade-code")).toHaveValue("MD");
  expect(screen.getByTestId("candidate-career-hint")).toHaveTextContent(
    "경력 약 6년, 등급은 직접 고르세요",
  );
  expect(screen.getByTestId("candidate-privacy-agreement")).not.toBeChecked();
  expect(writes()).toHaveLength(1);
  expect(writes()[0][1].body).toBeInstanceOf(FormData);
});
it("resume import that finds nothing says so without the skipped sentence", async () => {
  extraction = { fileName: "empty.pdf", fields: [], careerYears: null };
  renderForm(false);
  await readyForm();
  upload(new File(["x"], "empty.pdf", { type: "application/pdf" }));
  expect(await screen.findByTestId("candidate-resume-result")).toHaveTextContent(
    "「empty.pdf」에서 가져올 칸을 찾지 못했습니다. 직접 적어 주세요. 파일은 저장하지 않습니다.",
  );
  expect(screen.queryByTestId("candidate-career-hint")).not.toBeInTheDocument();
});
it("images and oversize files are refused before upload", async () => {
  renderForm(false);
  await readyForm();
  upload(new File(["bad"], "scan.jpg", { type: "image/jpeg" }));
  expect(await screen.findByTestId("candidate-form-error")).toHaveTextContent(
    "Word·PDF·PPT·한글(HWP)·TXT 파일만 올릴 수 있습니다. 스캔한 그림 파일은 글자를 꺼낼 수 없어 직접 적어 주세요.",
  );
  const big = new File(["x"], "big.docx");
  Object.defineProperty(big, "size", { value: 10 * 1024 * 1024 + 1 });
  upload(big);
  expect(await screen.findByTestId("candidate-form-error")).toHaveTextContent(
    "10MB 이하 파일만 올릴 수 있습니다.",
  );
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("candidate-name")).toHaveValue("");
});
it("server refusal of a resume shows its message and leaves the form", async () => {
  extractionFailed = true;
  renderForm(false);
  await readyForm();
  upload(new File(["bad"], "bad.hwp"));
  expect(await screen.findByText("file 입력 오류")).toBeInTheDocument();
  expect(screen.getByTestId("candidate-name")).toHaveValue("");
  expect(writes()).toHaveLength(1);
});
it("resume import can be cancelled and the late answer is ignored", async () => {
  let resolve: (value: unknown) => void = () => undefined;
  let signal: AbortSignal | undefined;
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (input: string, init?: RequestInit) => {
    if (String(input) === "/api/common/candidate-resume-extractions") {
      signal = init?.signal ?? undefined;
      return new Promise((done) => {
        resolve = done;
      });
    }
    return original(input, init);
  });
  renderForm(false);
  await readyForm();
  upload(new File(["resume"], "resume.docx"));
  fireEvent.click(await screen.findByTestId("candidate-resume-cancel"));
  expect(signal?.aborted).toBe(true);
  await act(async () => {
    resolve(ok(defaultExtraction()));
  });
  expect(screen.queryByTestId("candidate-resume-result")).not.toBeInTheDocument();
  expect(screen.getByTestId("candidate-name")).toHaveValue("");
  await readyForm();
});
it("cancel unchanged form never calls API", async () => {
  const cancel = vi.fn();
  renderForm(false, { onCancel: cancel });
  await readyForm();
  fireEvent.click(screen.getByTestId("candidate-form-cancel"));
  expect(cancel).toHaveBeenCalled();
  expect(writes()).toHaveLength(0);
});
it("payload deduplicates skills and removes primary-equal secondary job", () => {
  const result = candidatePayload({
    ...row,
    desiredMonthlyRate: row.desiredMonthlyRate ?? 0,
    secondaryJobCode: row.primaryJobCode,
    companyId: "not-outsourcing",
    skills: ["Java", " Java "],
  });
  expect(result.skills).toEqual(["Java"]);
  expect(result.secondaryJobCode).toBeNull();
  expect(result.companyId).toBeNull();
  expect(result).not.toHaveProperty("candidateId");
});
it("policy uses the server permission table and 민감 flag, not role names", () => {
  const of = (roles: Parameters<typeof testAccount>[0]) => {
    const account = testAccount(roles);
    return candidatePolicy(
      screenPermission(account.meta.permissions, "UI-33"),
      account.meta.sensitive === true,
    );
  };
  expect(of(["ADMIN"])).toEqual({ canManage: true, showAmounts: true });
  // 파트장: 등록·수정은 있지만 민감 체크 N이라 희망 단가는 숨김(⑨-1, 파트장 예외 없음).
  expect(of(["SALES_LDR"])).toEqual({ canManage: true, showAmounts: false });
  // 경영진: 조회만, 민감 Y.
  expect(of(["EXEC"])).toEqual({ canManage: false, showAmounts: true });
});
it("extraction merger ignores unknown/duplicate fields, invalid money, grade and consent", () => {
  const initial = candidateDefaults(options);
  const merged = mergeCandidateExtraction(
    initial,
    {
      fileName: "x.txt",
      fields: [
        { field: "name", value: "첫 이름", estimated: false },
        { field: "name", value: "다른 이름", estimated: false },
        { field: "desiredMonthlyRate", value: "-3", estimated: false },
        { field: "candidateId", value: "bad", estimated: false },
        { field: "gradeCode", value: "HIGH", estimated: false },
        { field: "privacyAgreement", value: "true", estimated: false },
      ],
    },
    initial,
  );
  expect(merged.applied).toEqual(["name"]);
  expect(merged.input.name).toBe("첫 이름");
  expect(merged.input.desiredMonthlyRate).toBeNaN();
  expect(merged.input.gradeCode).toBe("MD");
  expect(merged.input.privacyAgreement).toBe(false);
});
it("extraction merger fills default source/date, keeps typed values and switches to outsourcing for a company", () => {
  const initial = candidateDefaults(options);
  const typed = { ...initial, sourceCode: "REF" };
  const merged = mergeCandidateExtraction(
    typed,
    {
      fileName: "x.txt",
      fields: [
        { field: "sourceCode", value: "SITE", estimated: false },
        {
          field: "availableDate",
          value: "2027-02-01",
          estimated: true,
          originalText: "2027년 2월",
        },
        {
          field: "companyId",
          value: "vendor",
          estimated: true,
          originalText: "소속: 협력사",
        },
      ],
    },
    initial,
  );
  expect(merged.input.sourceCode).toBe("REF");
  expect(merged.input.availableDate).toBe("2027-02-01");
  expect(merged.input.employeeTypeCode).toBe("OS");
  expect(merged.input.companyId).toBe("vendor");
  expect(merged.skipped).toBe(1);
  expect(merged.warn).toBe(3);
  expect(resumeResultText("x.txt", merged)).toBe(
    "「x.txt」에서 3칸을 채웠습니다(✓ 0 · ⚠ 3). ⚠ 칸과 빈칸을 확인하고 [등록]을 누르세요. 개인정보 동의는 직접 체크합니다. 파일은 저장하지 않습니다. 이미 적은 1칸은 그대로 두었습니다.",
  );
});
it("file and field checks follow the mockup", () => {
  expect(resumeFileError(new File(["x"], "a.png"))).toMatch(/그림 파일/);
  expect(resumeFileError(new File(["x"], "a.HWPX"))).toBeNull();
  expect(resumeFileError(new File(["x"], "a.rtf"))).toMatch(/Word·PDF/);
  const errors = candidateErrors({
    ...candidateDefaults(options),
    name: "이",
    desiredMonthlyRate: 0,
    skills: ["Java"],
    phone: "010-1234-5678",
    privacyAgreement: true,
  });
  expect(errors).toEqual({ desiredMonthlyRate: "희망 단가를 넣어 주세요." });
});

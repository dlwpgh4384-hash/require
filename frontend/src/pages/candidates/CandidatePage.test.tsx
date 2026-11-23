import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { CandidatePage } from "./CandidatePage";
import { CandidateForm } from "./CandidateForm";
import { CandidateDetail, CandidateContactForm } from "./CandidateDetail";
import {
  candidateDefaults,
  candidatePayload,
  candidatePolicy,
  mergeCandidateExtraction,
  type Candidate,
  type CandidateOptions,
} from "./candidates";
import { addDays, koreanToday } from "../../shared/time";

const options: CandidateOptions = {
  codes: {
    EMP_TYPE: [
      { code: "FL", codeName: "프리랜서" },
      { code: "OS", codeName: "외주" },
    ],
    JOB: [
      { code: "BE", codeName: "백엔드" },
      { code: "PM", codeName: "PM" },
    ],
    GRADE: [{ code: "MD", codeName: "중급" }],
    CAND_SRC: [{ code: "SITE", codeName: "구인 사이트" }],
    CNTC_STAT: [
      { code: "BEFORE", codeName: "연락 전" },
      { code: "HOLD", codeName: "보류" },
      { code: "CTRT", codeName: "계약 완료" },
    ],
  },
  companies: [{ clientId: "vendor", clientName: "협력사" }],
};
let row: Candidate;
let request: ReturnType<typeof vi.fn>;
let writeStatus: number;
let listFailed: boolean;
let listEmpty: boolean;
let matchFailed: boolean;
let contactFailed: boolean;
let extractionFailed: boolean;
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
beforeEach(() => {
  writeStatus = 200;
  listFailed = false;
  listEmpty = false;
  matchFailed = false;
  contactFailed = false;
  extractionFailed = false;
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
    lastContactDate: "2026-11-15",
    modifiedAt: null,
    daysSinceLastContact: 31,
    overdueContact: true,
  };
  request = vi.fn(async (input: string, init?: RequestInit) => {
    const url = new URL(input, "http://test");
    const path = url.pathname;
    if (path === "/api/common/candidate-resume-extractions") {
      if (extractionFailed) return failure(400, "file");
      return ok({
        fileName: "resume.txt",
        careerYears: 6,
        fields: [
          { field: "name", value: "추출 이름", estimated: false },
          { field: "phone", value: "02-234-5678", estimated: false },
          {
            field: "employeeTypeCode",
            value: "FL",
            estimated: true,
            originalText: "프리랜서 경력",
          },
          { field: "gradeCode", value: "HIGH", estimated: false },
          { field: "privacyAgreement", value: "true", estimated: false },
          {
            field: "availableDate",
            value: "2027-01-01",
            estimated: true,
            originalText: "내년부터",
          },
        ],
      });
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
        activeCount: 1,
        interviewScheduledCount: 0,
        availableByNextMonthCount: 1,
        overdueContactCount: 1,
      });
    if (path === "/api/common/candidate-shortages")
      return ok({
        items: [
          {
            jobCode: "BE",
            jobName: "백엔드",
            shortageStartMonth: "2026-12",
            maxShortage: 1.5,
            availableCandidateCount: 1,
            totalCandidateCount: 2,
          },
        ],
      });
    if (path === "/api/common/candidates")
      return listFailed ? failure(500) : ok({ items: listEmpty ? [] : [row] });
    if (path.endsWith("/contacts"))
      return contactFailed
        ? failure(500)
        : ok({
            items: [
              {
                contactStatusCode: "BEFORE",
                contactStatusName: "연락 전",
                memo: "시스템이 등록",
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
            items: [
              {
                opportunityId: "opp",
                opportunityName: "영업 기회",
                winningProbability: 50,
                jobName: "백엔드",
                gradeName: "중급",
                startMonth: "2026-12",
                slotMonthlyRate: 8000000,
                expectedProfitRate: 14.99,
                lowMargin: true,
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
async function fillCreate() {
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-name"), {
    target: { value: "새 후보" },
  });
  fireEvent.change(screen.getByTestId("candidate-employee-type-code"), {
    target: { value: "FL" },
  });
  fireEvent.change(screen.getByTestId("candidate-primary-job-code"), {
    target: { value: "BE" },
  });
  fireEvent.change(screen.getByTestId("candidate-skills"), {
    target: { value: "Java, Java, SQL" },
  });
  fireEvent.change(screen.getByTestId("candidate-desired-monthly-rate"), {
    target: { value: "0" },
  });
}
it("renders real summary, shortages, filters and list with low-margin detail", async () => {
  render(<CandidatePage />);
  expect(
    await screen.findByTestId("candidate-open-candidate"),
  ).toHaveTextContent("지원자");
  fireEvent.click(screen.getByTestId("candidate-open-candidate"));
  expect(await screen.findByText("영업 기회 (50%)")).toBeInTheDocument();
  expect(screen.getByText(/단가를 다시 협의/)).toHaveClass("candidate-warning");
  expect(screen.getByText(/동의 확인일:/)).toHaveTextContent("2026-10-01");
  expect(screen.getByTestId("candidate-basic-panel")).toHaveTextContent(
    "02-123-4567",
  );
  expect(screen.getByTestId("candidate-basic-panel")).toHaveTextContent("PM");
});
it("shows empty list distinctly from a failed request", async () => {
  listEmpty = true;
  render(<CandidatePage />);
  expect(
    await screen.findByText("조회된 자료가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.queryByTestId("candidate-list-retry")).not.toBeInTheDocument();
});
it("failed list exposes retry and then real rows", async () => {
  listFailed = true;
  render(<CandidatePage />);
  fireEvent.click(await screen.findByTestId("candidate-list-retry"));
  listFailed = false;
  fireEvent.click(await screen.findByTestId("candidate-list-retry"));
  expect(
    await screen.findByTestId("candidate-open-candidate"),
  ).toBeInTheDocument();
});
it("filters by shortage job and sends only approved query fields", async () => {
  render(<CandidatePage />);
  fireEvent.click(await screen.findByTestId("candidate-shortage-view-0"));
  await waitFor(() =>
    expect(
      request.mock.calls.some((call) =>
        String(call[0]).includes("/candidates?jobCode=BE"),
      ),
    ).toBe(true),
  );
});
it("new form derives DB code defaults and Korean today plus thirty", async () => {
  renderForm(false);
  await readyForm();
  expect(screen.getByTestId("candidate-grade-code")).toHaveValue("MD");
  expect(screen.getByTestId("candidate-source-code")).toHaveValue("SITE");
  expect(screen.getByTestId("candidate-available-date")).toHaveValue(
    addDays(koreanToday(), 30),
  );
  expect(screen.getByTestId("candidate-privacy-agreement")).not.toBeChecked();
  expect(
    screen.getByTestId("candidate-form").querySelector(".candidate-form-grid"),
  ).toBeInTheDocument();
});
it("requires outsourcing company and guides registration if no company exists", async () => {
  renderForm(false, { options: { ...options, companies: [] } });
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-employee-type-code"), {
    target: { value: "OS" },
  });
  expect(
    screen.getByText("거래처에서 먼저 등록해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByTestId("candidate-company-id")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("candidate-employee-type-code"), {
    target: { value: "FL" },
  });
  expect(screen.queryByTestId("candidate-company-id")).not.toBeInTheDocument();
});
it("requires consent for contact details and clears field error when editing", async () => {
  renderForm();
  await readyForm();
  fireEvent.click(screen.getByTestId("candidate-privacy-agreement"));
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("candidate-privacy-agreement")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  fireEvent.click(screen.getByTestId("candidate-privacy-agreement"));
  expect(screen.getByTestId("candidate-privacy-agreement")).toHaveAttribute(
    "aria-invalid",
    "false",
  );
});
it("update has no resume input or writable identity and sends raw nullable version", async () => {
  const saved = vi.fn();
  renderForm(true, { onSaved: saved });
  await readyForm();
  expect(screen.queryByTestId("candidate-resume")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("candidateId")).not.toBeInTheDocument();
  expect(screen.getByTestId("candidate-area-0")).toHaveValue("1100000000");
  expect(screen.getByTestId("candidate-area-1")).toHaveValue("1168000000");
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.modifiedAt).toBeNull();
  expect(body).not.toHaveProperty("candidateId");
  expect(body).not.toHaveProperty("privacyAgreementDate");
});
it("invalid amount and empty skills do not write", async () => {
  renderForm(false);
  await readyForm();
  fireEvent.submit(screen.getByTestId("candidate-form"));
  expect(screen.getByTestId("candidate-desired-monthly-rate")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(screen.getByTestId("candidate-skills")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(writes()).toHaveLength(0);
});
it("successful create closes and pins new candidate first until explicit query", async () => {
  render(<CandidatePage />);
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
    "저장했습니다.",
  );
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.skills).toEqual(["Java", "SQL"]);
  expect(body.desiredMonthlyRate).toBe(0);
  expect(
    await screen.findByTestId("candidate-row-candidate"),
  ).toHaveTextContent("새 후보");
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
it("409 update closes dialog and reloads current detail", async () => {
  writeStatus = 409;
  render(
    <CandidateDetail
      candidateId="candidate"
      options={options}
      onClose={vi.fn()}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("candidate-edit")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("candidate-edit"));
  await readyForm();
  const previous = request.mock.calls.filter(
    (call) => call[0] === "/api/common/candidates/candidate",
  ).length;
  fireEvent.click(screen.getByTestId("candidate-form-save"));
  await waitFor(() =>
    expect(
      screen.queryByTestId("candidate-form-dialog"),
    ).not.toBeInTheDocument(),
  );
  await waitFor(() =>
    expect(
      request.mock.calls.filter(
        (call) => call[0] === "/api/common/candidates/candidate",
      ).length,
    ).toBeGreaterThan(previous),
  );
});
it("contact validation requires memo, cancel writes nothing", async () => {
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
  fireEvent.click(screen.getByTestId("candidate-contact-save"));
  expect(
    await screen.findByText("입력 값을 확인해 주세요."),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("candidate-contact-cancel"));
  expect(cancel).toHaveBeenCalled();
  expect(writes()).toHaveLength(0);
});
it("same-state contact submits nullable version and mandatory memo", async () => {
  const saved = vi.fn();
  render(
    <CandidateContactForm
      candidate={row}
      options={options}
      onCancel={vi.fn()}
      onSaved={saved}
      onConflict={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByTestId("candidate-contact-memo"), {
    target: { value: "통화함" },
  });
  fireEvent.click(screen.getByTestId("candidate-contact-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    modifiedAt: null,
    contactStatusCode: "BEFORE",
    memo: "통화함",
  });
});
it("409 contact closes dialog and refreshes detail", async () => {
  writeStatus = 409;
  render(
    <CandidateDetail
      candidateId="candidate"
      options={options}
      onClose={vi.fn()}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
    />,
  );
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
  await waitFor(() =>
    expect(
      request.mock.calls.filter(
        (call) => call[0] === "/api/common/candidates/candidate",
      ).length,
    ).toBe(2),
  );
});
it("PM policy hides amount headers and values and edit controls without adding HTTP auth", async () => {
  render(<CandidatePage futureRoles={["PM"]} />);
  await screen.findByTestId("candidate-open-candidate");
  expect(screen.queryByText("희망 단가")).not.toBeInTheDocument();
  expect(screen.queryByTestId("candidate-register")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("candidate-open-candidate"));
  await screen.findByText("영업 기회 (50%)");
  expect(screen.queryByText("예상 이익률")).not.toBeInTheDocument();
  expect(screen.queryByTestId("candidate-edit")).not.toBeInTheDocument();
});
it("terminal status keeps edit and matching in development mode", async () => {
  row.contactStatusCode = "CTRT";
  row.contactStatusName = "계약 완료";
  render(
    <CandidateDetail
      candidateId="candidate"
      options={options}
      onClose={vi.fn()}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
    />,
  );
  expect(await screen.findByText("영업 기회 (50%)")).toBeInTheDocument();
  expect(screen.getByTestId("candidate-edit")).toBeEnabled();
});
it("matching failure can retry without losing candidate basic fields", async () => {
  matchFailed = true;
  render(
    <CandidateDetail
      candidateId="candidate"
      options={options}
      onClose={vi.fn()}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
    />,
  );
  expect(
    await screen.findByTestId("candidate-matches-retry"),
  ).toBeInTheDocument();
  matchFailed = false;
  fireEvent.click(screen.getByTestId("candidate-matches-retry"));
  expect(await screen.findByText("영업 기회 (50%)")).toBeInTheDocument();
});
it("contact failure exposes its own retry and history marks SYSTEM actor", async () => {
  contactFailed = true;
  render(
    <CandidateDetail
      candidateId="candidate"
      options={options}
      onClose={vi.fn()}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByTestId("candidate-contacts-tab"));
  fireEvent.click(await screen.findByTestId("candidate-contacts-retry"));
  contactFailed = false;
  fireEvent.click(await screen.findByTestId("candidate-contacts-retry"));
  expect(await screen.findByTestId("candidate-history-0")).toHaveTextContent(
    "SYSTEM · 시스템",
  );
});
it("resume extraction merges only empty fields, never defaults, grade or consent", async () => {
  renderForm(false);
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-name"), {
    target: { value: "입력 이름" },
  });
  fireEvent.change(screen.getByTestId("candidate-resume"), {
    target: {
      files: [new File(["resume"], "resume.txt", { type: "text/plain" })],
    },
  });
  expect(await screen.findByText(/채운 칸 2개/)).toBeInTheDocument();
  expect(screen.getByTestId("candidate-name")).toHaveValue("입력 이름");
  expect(screen.getByTestId("candidate-phone")).toHaveValue("02-234-5678");
  expect(screen.getByTestId("candidate-grade-code")).toHaveValue("MD");
  expect(screen.getByTestId("candidate-privacy-agreement")).not.toBeChecked();
  expect(screen.getByTestId("candidate-available-date")).toHaveValue(
    addDays(koreanToday(), 30),
  );
  expect(
    screen.getByText(/⚠ 확인해 주세요: 프리랜서 경력/),
  ).toBeInTheDocument();
  expect(writes()).toHaveLength(1);
  expect(writes()[0][1].body).toBeInstanceOf(FormData);
});
it("failed extraction leaves form and no candidate write", async () => {
  extractionFailed = true;
  renderForm(false);
  await readyForm();
  fireEvent.change(screen.getByTestId("candidate-resume"), {
    target: { files: [new File(["bad"], "bad.jpg", { type: "image/jpeg" })] },
  });
  expect(
    await screen.findByText("처리하지 못했습니다. 다시 시도해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByTestId("candidate-name")).toHaveValue("");
  expect(writes()).toHaveLength(1);
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
    secondaryJobCode: row.primaryJobCode,
    companyId: "not-outsourcing",
    skills: ["Java", " Java "],
  });
  expect(result.skills).toEqual(["Java"]);
  expect(result.secondaryJobCode).toBeNull();
  expect(result.companyId).toBeNull();
  expect(result).not.toHaveProperty("candidateId");
});
it("policy seam supports administrators and part leaders while PM is masked", () => {
  expect(candidatePolicy(["ADMIN"])).toEqual({
    canManage: true,
    showAmounts: true,
  });
  expect(candidatePolicy(["SALES_LDR"])).toEqual({
    canManage: true,
    showAmounts: true,
  });
  expect(candidatePolicy(["ADMIN", "PM"])).toEqual({
    canManage: true,
    showAmounts: true,
  });
  expect(candidatePolicy(["PM"])).toEqual({
    canManage: false,
    showAmounts: false,
  });
});
it("extraction merger ignores unknown/duplicate fields and invalid money", () => {
  const initial = candidateDefaults(options);
  const merged = mergeCandidateExtraction(initial, {
    fileName: "x.txt",
    fields: [
      { field: "name", value: "첫 이름", estimated: false },
      { field: "name", value: "다른 이름", estimated: false },
      { field: "desiredMonthlyRate", value: "-3", estimated: false },
      { field: "candidateId", value: "bad", estimated: false },
    ],
  });
  expect(merged.applied).toEqual(["name"]);
  expect(merged.input.name).toBe("첫 이름");
  expect(merged.input.desiredMonthlyRate).toBeNaN();
});

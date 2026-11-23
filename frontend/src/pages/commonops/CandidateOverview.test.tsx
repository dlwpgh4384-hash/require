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
import { useState } from "react";
import { CandidatePage } from "./CandidatePage";
import { CandidateResumeImport } from "./CandidateResumeImport";
import {
  mergeCandidateResume,
  type CandidateResumeExtraction,
} from "./candidateOverview";

const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data }),
});
let request: ReturnType<typeof vi.fn>;
const summary = {
  activeCount: 7,
  interviewScheduledCount: 2,
  availableByNextMonthCount: 3,
  overdueContactCount: 1,
};
const shortages = {
  items: [
    {
      jobCode: "DEV",
      jobName: "개발",
      shortageStartMonth: "2027-01",
      maxShortage: 1.5,
      availableCandidateCount: 2,
      totalCandidateCount: 4,
    },
  ],
};
const extraction: CandidateResumeExtraction = {
  fileName: "resume.txt",
  careerYears: 7,
  reviewRequired: true,
  fields: [
    { field: "name", value: "추출이름", estimated: false },
    { field: "phone", value: "010-1234-5678", estimated: false },
    {
      field: "primaryJobCode",
      value: "DEV",
      estimated: true,
      originalText: "Java 개발 경력",
    },
    { field: "desiredMonthlyRate", value: "6500000", estimated: false },
    {
      field: "availableDate",
      value: "2027-01-01",
      estimated: true,
      originalText: "1월 가능",
    },
  ],
};
beforeEach(() => {
  request = vi.fn(async (path: string) => {
    if (path === "/api/common/candidate-summary") return ok(summary);
    if (path === "/api/common/candidate-shortages") return ok(shortages);
    if (path === "/api/common/candidate-resume-extractions")
      return ok(extraction);
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
it("renders API summary and shortage and sends the selected job to the owning list", async () => {
  const show = vi.fn();
  render(
    <CandidatePage onShowCandidates={show}>
      <p>후보 목록 영역</p>
    </CandidatePage>,
  );
  expect(screen.getByTestId("candidate-overview-page")).toHaveClass(
    "screen-body",
  );
  expect(await screen.findByTestId("candidate-summary")).toHaveTextContent(
    "진행 중7",
  );
  expect(screen.getByTestId("candidate-summary")).toHaveTextContent(
    "30일 초과 미연락1",
  );
  expect(await screen.findByTestId("candidate-shortage-DEV")).toHaveTextContent(
    "2 (4)",
  );
  expect(screen.getByText("영업 기회 기준(미충원 제외)")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "후보 보기" }));
  expect(show).toHaveBeenCalledWith("DEV");
  expect(request.mock.calls.map((call) => call[0])).toEqual([
    "/api/common/candidate-summary",
    "/api/common/candidate-shortages",
  ]);
});
it("renders zero summary and shortage empty state without fabricating rows", async () => {
  request.mockImplementation(async (path: string) =>
    ok(
      path.endsWith("summary")
        ? {
            activeCount: 0,
            interviewScheduledCount: 0,
            availableByNextMonthCount: 0,
            overdueContactCount: 0,
          }
        : { items: [] },
    ),
  );
  render(<CandidatePage onShowCandidates={vi.fn()} />);
  expect(
    await screen.findByText("조회된 자료가 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "후보 보기" }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("candidate-summary")).toHaveTextContent("진행 중0");
});
it("handles independent request errors and retries without hiding successful summary", async () => {
  request.mockImplementation(async (path: string) => {
    if (path.endsWith("shortages")) throw new Error("offline");
    return ok(summary);
  });
  render(<CandidatePage onShowCandidates={vi.fn()} />);
  expect(
    await screen.findByTestId("candidate-shortage-error"),
  ).toBeInTheDocument();
  expect(screen.getByTestId("candidate-summary")).toHaveTextContent("진행 중7");
  request.mockImplementation(async (path: string) =>
    ok(path.endsWith("summary") ? summary : shortages),
  );
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(
    await screen.findByRole("button", { name: "후보 보기" }),
  ).toBeEnabled();
});
it("refreshes aggregates after the owning workspace changes revision", async () => {
  const { rerender } = render(
    <CandidatePage onShowCandidates={vi.fn()} revision={0} />,
  );
  await screen.findByTestId("candidate-summary");
  rerender(<CandidatePage onShowCandidates={vi.fn()} revision={1} />);
  await waitFor(() => expect(request).toHaveBeenCalledTimes(4));
});

const initial = {
  name: "직접 입력",
  phone: "",
  primaryJobCode: "",
  desiredMonthlyRate: "" as string | number,
  gradeCode: "MD",
  availableDate: "2027-02-01",
  privacyAgreement: false,
};
function Form() {
  const [draft, setDraft] = useState(initial);
  return (
    <>
      <label>
        이름
        <input
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
        />
      </label>
      <CandidateResumeImport draft={draft} onChange={setDraft} />
      <output data-testid="draft">{JSON.stringify(draft)}</output>
    </>
  );
}
const upload = () =>
  fireEvent.change(screen.getByTestId("candidate-resume-file"), {
    target: {
      files: [
        new File(["희망금액650만원"], "resume.txt", { type: "text/plain" }),
      ],
    },
  });
it("merges only actual empty fields, leaves defaults and consent untouched and counts only applied fields", async () => {
  render(<Form />);
  upload();
  expect(
    await screen.findByTestId("candidate-resume-result"),
  ).toHaveTextContent("채운 칸 3개 · 확실 2개 · 추정 1개");
  const draft = JSON.parse(screen.getByTestId("draft").textContent || "{}");
  expect(draft).toMatchObject({
    name: "직접 입력",
    gradeCode: "MD",
    availableDate: "2027-02-01",
    privacyAgreement: false,
    phone: "010-1234-5678",
    desiredMonthlyRate: 6500000,
    primaryJobCode: "DEV",
  });
  expect(screen.getByText("⚠ 확인해 주세요")).toBeInTheDocument();
  expect(
    screen.getByTestId("candidate-resume-review-primaryJobCode"),
  ).toHaveTextContent("Java 개발 경력");
  expect(screen.getByText("경력 연수: 7년")).toBeInTheDocument();
  expect(
    screen.getByText("파일과 이력서 원문은 저장하지 않습니다."),
  ).toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(1);
  expect(request.mock.calls[0][1]).toMatchObject({ method: "POST" });
  expect((request.mock.calls[0][1] as RequestInit).body).toBeInstanceOf(
    FormData,
  );
});
it("uses the latest typed values when extraction finishes", async () => {
  let finish!: (result: ReturnType<typeof ok>) => void;
  request.mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  render(<Form />);
  upload();
  fireEvent.change(screen.getByLabelText("이름"), {
    target: { value: "파싱 중 입력" },
  });
  await act(async () => finish(ok(extraction)));
  await screen.findByTestId("candidate-resume-result");
  expect(JSON.parse(screen.getByTestId("draft").textContent || "{}").name).toBe(
    "파싱 중 입력",
  );
});
it("shows the exact zero-field message and makes no create or upload-record request", async () => {
  request.mockResolvedValue(ok({ ...extraction, fields: [] }));
  render(<Form />);
  upload();
  expect(
    await screen.findByText("가져올 칸을 찾지 못했습니다. 직접 적어 주세요."),
  ).toBeInTheDocument();
  expect(request.mock.calls.map((call) => call[0])).toEqual([
    "/api/common/candidate-resume-extractions",
  ]);
});
it.each(["edit", "readOnly"])("hides resume import in %s mode", (mode) => {
  render(
    <CandidateResumeImport
      draft={initial}
      onChange={vi.fn()}
      mode={mode === "edit" ? "edit" : "create"}
      readOnly={mode === "readOnly"}
    />,
  );
  expect(screen.queryByTestId("candidate-resume-file")).not.toBeInTheDocument();
  expect(request).not.toHaveBeenCalled();
});
it("rejects images and files over ten MiB before a request and preserves the draft", async () => {
  render(<Form />);
  fireEvent.change(screen.getByTestId("candidate-resume-file"), {
    target: {
      files: [new File(["image"], "resume.jpg", { type: "image/jpeg" })],
    },
  });
  expect(
    await screen.findByTestId("candidate-resume-error"),
  ).toBeInTheDocument();
  const large = new File(["text"], "resume.txt", { type: "text/plain" });
  Object.defineProperty(large, "size", { value: 10 * 1024 * 1024 + 1 });
  fireEvent.change(screen.getByTestId("candidate-resume-file"), {
    target: { files: [large] },
  });
  expect(request).not.toHaveBeenCalled();
  expect(JSON.parse(screen.getByTestId("draft").textContent || "{}")).toEqual(
    initial,
  );
});
it("renders server file errors without merging and allows retry", async () => {
  request.mockResolvedValue({
    ok: false,
    status: 400,
    json: async () => ({
      success: false,
      message: "확인해 주세요.",
      errors: [{ field: "file", message: "손상된 문서입니다." }],
    }),
  });
  render(<Form />);
  upload();
  expect(await screen.findByText("손상된 문서입니다.")).toBeInTheDocument();
  expect(JSON.parse(screen.getByTestId("draft").textContent || "{}")).toEqual(
    initial,
  );
  request.mockResolvedValue(ok(extraction));
  upload();
  expect(
    await screen.findByTestId("candidate-resume-result"),
  ).toHaveTextContent("채운 칸 3개");
  expect(
    screen.queryByTestId("candidate-resume-error"),
  ).not.toBeInTheDocument();
});
it("cancels in-flight extraction without calling onChange even if a late result arrives", async () => {
  let finish!: (result: ReturnType<typeof ok>) => void;
  request.mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const change = vi.fn();
  render(<CandidateResumeImport draft={initial} onChange={change} />);
  upload();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  await act(async () => finish(ok(extraction)));
  expect(change).not.toHaveBeenCalled();
  expect(
    screen.queryByTestId("candidate-resume-result"),
  ).not.toBeInTheDocument();
});
it("ignores forbidden grade/consent fields, zero is not empty, and duplicate extraction fields count once", () => {
  const merged = mergeCandidateResume(
    {
      gradeCode: "MD",
      privacyAgreement: false,
      desiredMonthlyRate: 0,
      phone: "",
      skills: [] as string[],
    },
    {
      fileName: "resume.txt",
      fields: [
        ...extraction.fields,
        { field: "gradeCode", value: "HIGH", estimated: false },
        { field: "privacyAgreement", value: "true", estimated: false },
        { field: "phone", value: "010-9999-9999", estimated: false },
        { field: "skills", value: "Java,Java,SQL", estimated: false },
      ] as CandidateResumeExtraction["fields"],
    },
  );
  expect(merged.draft).toMatchObject({
    gradeCode: "MD",
    privacyAgreement: false,
    desiredMonthlyRate: 0,
    phone: "010-1234-5678",
    skills: ["Java", "SQL"],
  });
  expect(
    merged.applied.filter((field) => field.field === "phone"),
  ).toHaveLength(1);
});

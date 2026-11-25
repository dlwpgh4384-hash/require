import { StrictMode } from "react";
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
import { AiRecommendationPage } from "./AiRecommendationPage";
import { requestAiRecommendation } from "./aiRecommendationRequest";
import * as api from "./aiRecommendations";
import type { AiCard, AiInput, AiOptions } from "./aiRecommendations";

vi.mock("./aiRecommendations", async (importOriginal) => {
  const original = await importOriginal<typeof import("./aiRecommendations")>();
  return {
    ...original,
    getCommonAiRecommendationOptions: vi.fn(),
    postCommonAiRecommendation: vi.fn(),
    postCommonAiRecommendationAssignment: vi.fn(),
    postCommonAiRecommendationExclusion: vi.fn(),
    putCommonAiRecommendationTentativeAssignment: vi.fn(),
  };
});
vi.mock("../projects/projects", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../projects/projects")>()),
  getCommonProject: vi.fn(async () => ({
    projectId: "project",
    startDate: "2026-12-15",
    endDate: "2027-12-31",
    projectName: "프로젝트",
  })),
}));
vi.mock("../salespipeline/salesOpportunities", () => ({
  getCommonSalesOpportunity: vi.fn(async () => ({ projectId: "project" })),
}));
vi.mock("../assignments/AssignmentForm", () => ({
  AssignmentForm: ({
    onClose,
    onSaved,
    initialValues,
  }: {
    onClose: () => void;
    onSaved: (row: unknown, message: string) => void;
    initialValues: { developerId: string; startDate: string };
  }) => (
    <section data-testid="assignment-form">
      <p>{initialValues.startDate}</p>
      <button onClick={onClose}>배정 취소</button>
      <button
        onClick={() =>
          onSaved(
            {
              assignmentId: "saved",
              developerId: initialValues.developerId,
              projectId: "project",
            },
            "저장",
          )
        }
      >
        같은 인력 저장
      </button>
      <button
        onClick={() =>
          onSaved(
            {
              assignmentId: "saved",
              developerId: "other",
              projectId: "project",
            },
            "저장",
          )
        }
      >
        다른 인력 저장
      </button>
    </section>
  ),
}));
let options: AiOptions;
let cards: AiCard[];
const defaultCard = (): AiCard => ({
  developerId: "person",
  developerName: "추천 인력",
  managementNumber: "M001",
  jobName: "백엔드",
  gradeName: "고급",
  employeeTypeName: "정규",
  totalScore: 87,
  scores: Object.fromEntries(
    [
      "job",
      "skill",
      "customerIndustry",
      "grade",
      "performance",
      "cost",
      "availability",
      "workplace",
    ].map((key) => [key, { name: key, score: 5, maximumScore: 10 }]),
  ),
  reason: "실제 직무와 스킬을 근거로 추천했습니다.",
  otherTentativeOpportunityName: "다른 기회",
  assignmentCandidate: false,
});
const page = (strict = false) =>
  render(
    <MemoryRouter>
      {strict ? (
        <StrictMode>
          <AiRecommendationPage />
        </StrictMode>
      ) : (
        <AiRecommendationPage />
      )}
    </MemoryRouter>,
  );
const recommendation = () => vi.mocked(api.postCommonAiRecommendation);
const settled = async () => {
  await screen.findByTestId("ai-card-person");
  await waitFor(() => expect(screen.queryByTestId("ai-loading")).toBeNull());
};
beforeEach(() => {
  vi.clearAllMocks();
  options = {
    targets: [
      {
        targetType: "opportunity",
        targetId: "opportunity",
        targetName: "진행 기회",
        startMonth: "2026-12",
        endMonth: null,
        needs: [
          {
            needSn: 1,
            jobCode: "BE",
            jobName: "백엔드",
            requiredCount: 1,
            startMonth: "2026-12",
          },
          {
            needSn: 2,
            jobCode: "FE",
            jobName: "프론트",
            requiredCount: 2,
            startMonth: "2027-01",
          },
        ],
      },
      {
        targetType: "project",
        targetId: "project",
        targetName: "진행 프로젝트",
        startMonth: "2026-12",
        endMonth: "2027-12",
        needs: [],
      },
    ],
    jobs: [
      { code: "BE", name: "백엔드" },
      { code: "FE", name: "프론트" },
    ],
    grades: [
      { code: "MD", name: "중급" },
      { code: "SR", name: "고급" },
    ],
    exclusionReasons: [{ code: "OTHER", name: "기타" }],
  };
  cards = [defaultCard()];
  vi.mocked(api.getCommonAiRecommendationOptions).mockImplementation(async () =>
    structuredClone(options),
  );
  recommendation().mockImplementation(async (input: AiInput) => {
    const { requestId, ...condition } = input;
    return {
      recommendationId: requestId,
      condition,
      items: structuredClone(cards),
    };
  });
  vi.mocked(api.postCommonAiRecommendationExclusion).mockResolvedValue({
    excluded: true,
  });
  vi.mocked(api.postCommonAiRecommendationAssignment).mockResolvedValue({
    userActCode: "ASGN",
  });
  vi.mocked(api.putCommonAiRecommendationTentativeAssignment).mockResolvedValue(
    {
      assignment: {
        assignmentId: "",
        developerId: "person",
        opportunityId: "opportunity",
        opportunityName: "진행 기회",
        needSn: 2,
        startMonth: "2026-12",
      },
      releasedOpportunityName: null,
      overCapacityCount: 1,
      message:
        "이 자리는 1명을 넘었습니다. 영업 기회 상세에서 가배정 인력을 볼 수 있습니다.",
    },
  );
});
afterEach(cleanup);
it("menu starts with first target and senior grade and eight score bars", async () => {
  page();
  await settled();
  expect(screen.getByTestId("ai-grade")).toHaveValue("SR");
  expect(screen.getByTestId("ai-target")).toHaveValue(
    "opportunity:opportunity",
  );
  expect(screen.getAllByRole("meter")).toHaveLength(8);
  expect(screen.getByText("추천 1명")).toBeInTheDocument();
  expect(screen.getByText("가배정 있음: 다른 기회")).toBeInTheDocument();
});
it("StrictMode does not create two executions for the initial event", async () => {
  page(true);
  await settled();
  expect(recommendation()).toHaveBeenCalledTimes(1);
});
it("job month grade and need drafts retain applied cards without recommending", async () => {
  page();
  await settled();
  const applied = screen.getByTestId("ai-applied").textContent;
  fireEvent.change(screen.getByTestId("ai-job"), { target: { value: "FE" } });
  expect(screen.getByTestId("ai-need")).toHaveValue("2");
  fireEvent.change(screen.getByTestId("ai-month"), {
    target: { value: "2027-02" },
  });
  fireEvent.change(screen.getByTestId("ai-grade"), { target: { value: "MD" } });
  fireEvent.change(screen.getByTestId("ai-need"), { target: { value: "1" } });
  expect(recommendation()).toHaveBeenCalledTimes(1);
  expect(screen.getByTestId("ai-applied").textContent).toBe(applied);
  fireEvent.click(screen.getByTestId("ai-recommend"));
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(recommendation().mock.calls[1][0]).toMatchObject({
    jobCode: "FE",
    gradeCode: "MD",
    startMonth: "2027-02",
    needSn: 1,
  });
});
it("target change immediately recommends and switches tentative to assignment", async () => {
  page();
  await settled();
  fireEvent.change(screen.getByTestId("ai-target"), {
    target: { value: "project:project" },
  });
  await waitFor(() =>
    expect(screen.getByTestId("ai-assign-person")).toHaveTextContent("배정"),
  );
  expect(recommendation()).toHaveBeenCalledTimes(2);
  expect(screen.getByTestId("ai-notice")).toHaveTextContent(
    "대상을 바꿔 다시 추천했습니다.",
  );
});
it("same target requests execute individually with distinct ids and final condition", async () => {
  page();
  await settled();
  requestAiRecommendation({
    targetType: "opportunity",
    targetId: "opportunity",
    jobCode: "FE",
    startMonth: "2027-01",
  });
  requestAiRecommendation({
    targetType: "opportunity",
    targetId: "opportunity",
    jobCode: "BE",
    startMonth: "2027-02",
  });
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(3));
  await waitFor(() =>
    expect(screen.getByTestId("ai-applied")).toHaveTextContent("2027-02"),
  );
  expect(
    new Set(recommendation().mock.calls.map(([input]) => input.requestId)).size,
  ).toBe(3);
});
it("early project month is corrected and after-end month disables execution", async () => {
  requestAiRecommendation({
    targetType: "project",
    targetId: "project",
    startMonth: "2026-01",
  });
  page();
  await settled();
  expect(screen.getByTestId("ai-month")).toHaveValue("2026-12");
  fireEvent.change(screen.getByTestId("ai-month"), {
    target: { value: "2028-01" },
  });
  expect(screen.getByTestId("ai-recommend")).toBeDisabled();
  expect(
    screen.getByText("시작월이 프로젝트 종료월 뒤입니다."),
  ).toBeInTheDocument();
});
it("needless opportunity recommends and excludes but cannot tentatively assign", async () => {
  options.targets[0].needs = [];
  page();
  await settled();
  expect(
    screen.getByText("필요 인력을 먼저 등록해 주세요"),
  ).toBeInTheDocument();
  expect(screen.getByTestId("ai-assign-person")).toBeDisabled();
  fireEvent.click(screen.getByTestId("ai-exclude-person"));
  expect(screen.getByTestId("ai-exclusion-confirm")).toBeDisabled();
  fireEvent.change(screen.getByTestId("ai-exclusion-reason"), {
    target: { value: "OTHER" },
  });
  fireEvent.click(screen.getByTestId("ai-exclusion-confirm"));
  await waitFor(() =>
    expect(api.postCommonAiRecommendationExclusion).toHaveBeenCalledTimes(1),
  );
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
});
it("changing only need submits current slot against original execution and refills once", async () => {
  page();
  await settled();
  const id = recommendation().mock.calls[0][0].requestId;
  fireEvent.change(screen.getByTestId("ai-need"), { target: { value: "2" } });
  fireEvent.click(screen.getByTestId("ai-assign-person"));
  await waitFor(() =>
    expect(
      api.putCommonAiRecommendationTentativeAssignment,
    ).toHaveBeenCalledWith(id, { developerId: "person", needSn: 2 }),
  );
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(screen.getByTestId("ai-notice")).toHaveTextContent(
    "이 자리는 1명을 넘었습니다.",
  );
});
it("converted candidates show badge and disable tentative", async () => {
  cards[0].assignmentCandidate = true;
  page();
  await settled();
  expect(screen.getByText("투입 배정 후보")).toBeInTheDocument();
  expect(screen.getByTestId("ai-assign-person")).toBeDisabled();
});
it("empty candidates show actual empty message", async () => {
  cards = [];
  page();
  await screen.findByText("조건에 맞는 사람이 없습니다.");
  expect(screen.getByText("추천 0명")).toBeInTheDocument();
});
it("failed new recommendation preserves applied snapshot and offers same-id retry", async () => {
  page();
  await settled();
  const applied = screen.getByTestId("ai-applied").textContent;
  recommendation().mockRejectedValueOnce(new Error("추천 실패"));
  fireEvent.click(screen.getByTestId("ai-recommend"));
  await screen.findByText("추천 실패");
  expect(screen.getByTestId("ai-applied").textContent).toBe(applied);
  const failedId = recommendation().mock.calls[1][0].requestId;
  fireEvent.click(screen.getByTestId("ai-retry"));
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(3));
  expect(recommendation().mock.calls[2][0].requestId).toBe(failedId);
});
it("cancel exclusion performs no write or refill and provides no undo", async () => {
  page();
  await settled();
  fireEvent.click(screen.getByTestId("ai-exclude-person"));
  fireEvent.click(screen.getByTestId("ai-exclusion-cancel"));
  expect(api.postCommonAiRecommendationExclusion).not.toHaveBeenCalled();
  expect(recommendation()).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("되돌리기")).toBeNull();
});
it.each(["배정 취소", "다른 인력 저장"])(
  "%s does not record ASGN",
  async (action) => {
    requestAiRecommendation({ targetType: "project", targetId: "project" });
    page();
    await settled();
    fireEvent.click(screen.getByTestId("ai-assign-person"));
    const form = await screen.findByTestId("assignment-form");
    expect(form).toHaveTextContent("2026-12-15");
    fireEvent.click(within(form).getByText(action));
    expect(api.postCommonAiRecommendationAssignment).not.toHaveBeenCalled();
  },
);
it("same-person assignment records ASGN and follow-up retry never repeats assignment save", async () => {
  vi.mocked(api.postCommonAiRecommendationAssignment).mockRejectedValueOnce(
    new Error("기록 실패"),
  );
  requestAiRecommendation({ targetType: "project", targetId: "project" });
  page();
  await settled();
  fireEvent.click(screen.getByTestId("ai-assign-person"));
  fireEvent.click(
    within(await screen.findByTestId("assignment-form")).getByText(
      "같은 인력 저장",
    ),
  );
  await screen.findByText("기록 실패");
  expect(api.postCommonAiRecommendationAssignment).toHaveBeenCalledWith(
    recommendation().mock.calls[0][0].requestId,
    { developerId: "person", assignmentId: "saved" },
  );
  expect(screen.queryByTestId("assignment-form")).toBeNull();
  fireEvent.click(screen.getByTestId("ai-record-retry"));
  await waitFor(() =>
    expect(api.postCommonAiRecommendationAssignment).toHaveBeenCalledTimes(2),
  );
  expect(screen.queryByTestId("assignment-form")).toBeNull();
});
it("refresh keeps the full target list and replaces a closed opportunity", async () => {
  page();
  await settled();
  vi.mocked(api.getCommonAiRecommendationOptions).mockClear();
  options.targets = options.targets.filter(
    (target) => target.targetType === "project",
  );
  fireEvent.focus(window);
  await waitFor(() =>
    expect(screen.getByTestId("ai-target")).toHaveValue("project:project"),
  );
  expect(api.getCommonAiRecommendationOptions).toHaveBeenCalledWith();
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(screen.getByTestId("ai-notice")).toHaveTextContent(
    "대상을 바꿔 다시 추천했습니다.",
  );
});
it("no targets uses the agreed message", async () => {
  options.targets = [];
  page();
  await screen.findByText("진행 중인 대상이 없습니다.");
  expect(recommendation()).not.toHaveBeenCalled();
});
it("reopening resets to the first target rather than persisting a closed tab selection", async () => {
  const view = page();
  await settled();
  fireEvent.change(screen.getByTestId("ai-target"), {
    target: { value: "project:project" },
  });
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  view.unmount();
  page();
  await settled();
  expect(screen.getByTestId("ai-target")).toHaveValue(
    "opportunity:opportunity",
  );
});

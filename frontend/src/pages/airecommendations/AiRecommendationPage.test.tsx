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
import {
  newAiRequestId,
  requestAiRecommendation,
} from "./aiRecommendationRequest";
import * as api from "./aiRecommendations";
import type { AiCard, AiInput, AiOptions } from "./aiRecommendations";
import { AssignmentRequestError } from "../assignments/assignments";
import { getCommonSalesOpportunity } from "../salespipeline/salesOpportunities";
import { withdrawalRefreshEvent } from "../withdrawals/withdrawals";

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
type FormProps = {
  onClose: () => void;
  onSaved: (row: unknown, message: string) => void;
  initialValues: { developerId: string; startDate: string };
  roles: readonly string[];
  subtitle?: string;
  screenId?: string;
  extraWarning?: string;
};
vi.mock("../assignments/AssignmentForm", () => ({
  AssignmentForm: (props: FormProps) => (
    <section data-testid="assignment-form">
      <p>{props.initialValues.startDate}</p>
      <p data-testid="form-roles">{props.roles.join(",")}</p>
      <p data-testid="form-subtitle">{props.subtitle}</p>
      <p data-testid="form-screen">{props.screenId}</p>
      <p data-testid="form-warning">{props.extraWarning ?? ""}</p>
      <button onClick={props.onClose}>배정 취소</button>
      <button
        onClick={() =>
          props.onSaved(
            {
              assignmentId: "saved",
              developerId: props.initialValues.developerId,
              projectId: "project",
            },
            "배정을 저장했습니다. 경고: MM 1.0 초과",
          )
        }
      >
        같은 인력 저장
      </button>
      <button
        onClick={() =>
          props.onSaved(
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
const keys = [
  "job",
  "skill",
  "customerIndustry",
  "grade",
  "performance",
  "cost",
  "availability",
  "workplace",
];
const defaultCard = (): AiCard => ({
  developerId: "person",
  developerName: "추천 인력",
  managementNumber: "M001",
  jobName: "백엔드",
  gradeName: "고급",
  employeeTypeName: "정규",
  totalScore: 87,
  scores: Object.fromEntries(
    keys.map((key) => [key, { name: key, score: 5, maximumScore: 10 }]),
  ),
  reason: "요청 직무와 주 직무가 같습니다. 시작월에 다른 프로젝트에 투입 중입니다",
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
  // 「이번 달」(한국 달력)을 2026-11로 고정(날짜만, 타이머는 진짜).
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-25T03:00:00Z"));
  options = {
    targets: [
      {
        targetType: "project",
        targetId: "project",
        targetName: "진행 프로젝트",
        startMonth: "2026-12",
        endMonth: "2027-12",
        needs: [],
      },
      {
        targetType: "opportunity",
        targetId: "opportunity",
        targetName: "진행 기회",
        startMonth: "2026-12",
        endMonth: null,
        needs: [
          {
            needSn: 7,
            jobCode: "FE",
            jobName: "프론트",
            requiredCount: 2,
            startMonth: "2027-01",
          },
          {
            needSn: 3,
            jobCode: "BE",
            jobName: "백엔드",
            requiredCount: 1,
            startMonth: "2026-12",
          },
        ],
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
    exclusionReasons: [
      { code: "SCHED", name: "일정이 맞지 않음" },
      { code: "OTHER", name: "기타" },
    ],
  };
  cards = [defaultCard()];
  vi.mocked(getCommonSalesOpportunity).mockImplementation(
    async () => ({ projectId: "project" }) as never,
  );
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
        needSn: 7,
        startMonth: "2026-12",
      },
      releasedOpportunityName: null,
      overCapacityCount: 1,
      message:
        "이 자리는 4명을 넘었습니다. 영업 기회 상세에서 가배정 인력을 볼 수 있습니다.",
    },
  );
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});
it("menu starts with the first opportunity, senior grade, mockup labels and eight score bars", async () => {
  page();
  await settled();
  expect(screen.getByTestId("ai-grade")).toHaveValue("SR");
  expect(screen.getByTestId("ai-target")).toHaveValue(
    "opportunity:opportunity",
  );
  // 기회 먼저, 그다음 프로젝트
  expect(
    within(screen.getByTestId("ai-target"))
      .getAllByRole("option")
      .map((o) => o.textContent),
  ).toEqual(["[기회] 진행 기회", "[프로젝트] 진행 프로젝트"]);
  expect(screen.getByText("어떤 자리에 추천할까요?")).toBeInTheDocument();
  expect(screen.getByLabelText(/프로젝트·영업 기회/)).toBeInTheDocument();
  expect(screen.queryByRole("heading", { level: 1 })).toBeNull();
  expect(screen.queryAllByRole("meter")).toHaveLength(0);
  const bars = keys.map((key) => screen.getByTestId(`ai-score-person-${key}`));
  expect(bars.map((b) => b.firstChild?.textContent)).toEqual([
    "직무",
    "스킬",
    "고객·업종",
    "등급",
    "성과",
    "비용",
    "가용일",
    "근무지",
  ]);
  expect(screen.getByText("추천 1명")).toBeInTheDocument();
  expect(screen.getByText("가배정 있음: 다른 기회")).toHaveClass("ls-b-warn");
  expect(screen.getByTestId("ai-applied")).toHaveTextContent(
    "진행 기회 · 백엔드 · 2026-12부터 · 고급",
  );
  expect(screen.getByTestId("ai-card-person")).toHaveTextContent("87/80점");
  expect(screen.getByTestId("ai-why-person")).toHaveTextContent(
    "요청 직무와 주 직무가 같습니다. 시작월에 다른 프로젝트에 투입 중입니다.",
  );
  expect(screen.getByTestId("ai-recommend")).toHaveClass("ls-btn-p");
  expect(screen.getByTestId("ai-assign-person")).toHaveClass(
    "ls-btn-sm",
    "ls-btn-p",
  );
  expect(screen.getByTestId("ai-exclude-person")).toHaveClass("ls-btn-dt");
  expect(
    screen.getByText(/나이·성별·학력은 쓰지 않습니다/),
  ).toBeInTheDocument();
  expect(screen.queryByText(/신규 기본값 3점/)).toBeNull();
});
it("slot options are numbered by NEED_SN order and the job picks the same-job slot", async () => {
  page();
  await settled();
  expect(
    within(screen.getByTestId("ai-need"))
      .getAllByRole("option")
      .map((o) => o.textContent),
  ).toEqual(["1. 백엔드 1명 · 2026-12", "2. 프론트 2명 · 2027-01"]);
  expect(screen.getByTestId("ai-need")).toHaveValue("3");
  fireEvent.change(screen.getByTestId("ai-job"), { target: { value: "FE" } });
  expect(screen.getByTestId("ai-need")).toHaveValue("7");
});
it("server sentences are shown as sent (only the final period is added); the screen builds none", async () => {
  cards = [
    {
      ...defaultCard(),
      developerId: "a",
      reason: "지금 대기 중입니다. 보유 스킬: Java",
    },
    {
      ...defaultCard(),
      developerId: "b",
      jobName: "프론트",
      reason: "2026-11-30 철수 후 바로 투입할 수 있습니다.",
      scores: Object.fromEntries(
        keys.map((key) => [key, { name: key, score: 10, maximumScore: 10 }]),
      ),
    },
  ];
  page();
  await screen.findByTestId("ai-card-a");
  expect(screen.getByTestId("ai-why-a").textContent).toBe(
    "지금 대기 중입니다. 보유 스킬: Java.",
  );
  // 만점이어도 화면이 따로 문장을 만들지 않는다(예전 대체 문장 없음).
  expect(screen.getByTestId("ai-why-b").textContent).toBe(
    "2026-11-30 철수 후 바로 투입할 수 있습니다.",
  );
  expect(screen.queryByText(/같은 고객사 프로젝트 경험이 있습니다/)).toBeNull();
  expect(screen.queryByText(/시작월에 바로 투입할 수 있습니다/)).toBeNull();
});
it("StrictMode does not create two executions for the initial event", async () => {
  page(true);
  await settled();
  expect(recommendation()).toHaveBeenCalledTimes(1);
});
it("job month grade and need drafts retain applied cards until 추천 받기, which toasts", async () => {
  page();
  await settled();
  const applied = screen.getByTestId("ai-applied").textContent;
  fireEvent.change(screen.getByTestId("ai-job"), { target: { value: "FE" } });
  fireEvent.change(screen.getByTestId("ai-month"), {
    target: { value: "2027-02" },
  });
  fireEvent.change(screen.getByTestId("ai-grade"), { target: { value: "MD" } });
  fireEvent.change(screen.getByTestId("ai-need"), { target: { value: "3" } });
  expect(recommendation()).toHaveBeenCalledTimes(1);
  expect(screen.getByTestId("ai-applied").textContent).toBe(applied);
  fireEvent.click(screen.getByTestId("ai-recommend"));
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(recommendation().mock.calls[1][0]).toMatchObject({
    jobCode: "FE",
    gradeCode: "MD",
    startMonth: "2027-02",
    needSn: 3,
  });
  expect(await screen.findByTestId("ai-toast")).toHaveTextContent(
    "추천을 다시 계산했습니다.",
  );
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
  expect(screen.getByTestId("ai-toast")).toHaveTextContent(
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
  expect(screen.getByTestId("ai-toast")).toHaveTextContent(
    "대상을 바꿔 다시 추천했습니다.",
  );
});
it("an open tab reloads options for a new requested target before using it", async () => {
  page();
  await settled();
  options.targets.push({
    targetType: "opportunity",
    targetId: "new-opportunity",
    targetName: "새 기회",
    startMonth: "2027-03",
    endMonth: null,
    needs: [
      {
        needSn: 1,
        jobCode: "BE",
        jobName: "백엔드",
        requiredCount: 1,
        startMonth: "2027-03",
      },
    ],
  });
  requestAiRecommendation({
    targetType: "opportunity",
    targetId: "new-opportunity",
    jobCode: "BE",
    startMonth: "2027-03",
  });
  await waitFor(() =>
    expect(screen.getByTestId("ai-target")).toHaveValue(
      "opportunity:new-opportunity",
    ),
  );
  expect(api.getCommonAiRecommendationOptions).toHaveBeenCalledWith({
    targetType: "opportunity",
    targetId: "new-opportunity",
  });
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(recommendation().mock.calls[1][0]).toMatchObject({
    targetId: "new-opportunity",
    needSn: 1,
  });
});
it.each([
  ["unknown id", { targetType: "opportunity", targetId: "closed" }],
  [
    "bad month",
    { targetType: "opportunity", targetId: "opportunity", startMonth: "2026-13" },
  ],
] as const)(
  "%s on a fresh tab falls back to the first running target without an empty message",
  async (_, request) => {
    vi.mocked(getCommonSalesOpportunity).mockImplementation(
      async () => ({ projectId: null }) as never,
    );
    vi.mocked(api.getCommonAiRecommendationOptions).mockImplementation(
      async (target) => {
        if (target?.targetId === "closed")
          throw new AssignmentRequestError("진행 중인 대상을 찾을 수 없습니다.", [], 400);
        return structuredClone(options);
      },
    );
    requestAiRecommendation(request);
    page();
    await settled();
    expect(screen.getByTestId("ai-target")).toHaveValue(
      "opportunity:opportunity",
    );
    expect(screen.getByTestId("ai-month")).toHaveValue("2026-12");
    expect(screen.queryByText("진행 중인 대상이 없습니다.")).toBeNull();
    expect(screen.queryByTestId("ai-error")).toBeNull();
  },
);
it("a request for a won opportunity switches to its project", async () => {
  options.targets = options.targets.filter((t) => t.targetType === "project");
  requestAiRecommendation({
    targetType: "opportunity",
    targetId: "won",
    jobCode: "FE",
  });
  page();
  await settled();
  expect(screen.getByTestId("ai-target")).toHaveValue("project:project");
  expect(getCommonSalesOpportunity).toHaveBeenCalledWith("won");
  expect(recommendation().mock.calls[0][0]).toMatchObject({
    targetType: "project",
    jobCode: "FE",
  });
});
it("early project month is corrected with the mockup toast and after-end month disables execution", async () => {
  requestAiRecommendation({
    targetType: "project",
    targetId: "project",
    startMonth: "2026-01",
  });
  page();
  await settled();
  expect(screen.getByTestId("ai-month")).toHaveValue("2026-12");
  expect(screen.getByTestId("ai-toast")).toHaveTextContent(
    "시작월이 프로젝트 시작월보다 앞이라 2026-12로 맞춰 추천했습니다.",
  );
  fireEvent.change(screen.getByTestId("ai-month"), {
    target: { value: "2026-05" },
  });
  fireEvent.click(screen.getByTestId("ai-recommend"));
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(recommendation().mock.calls[1][0].startMonth).toBe("2026-12");
  fireEvent.change(screen.getByTestId("ai-month"), {
    target: { value: "2028-01" },
  });
  expect(screen.getByTestId("ai-recommend")).toBeDisabled();
  expect(
    screen.getByText("시작월이 프로젝트 종료월 뒤입니다."),
  ).toBeInTheDocument();
});
it("an opportunity keeps an early requested month (no project clamp)", async () => {
  requestAiRecommendation({
    targetType: "opportunity",
    targetId: "opportunity",
    startMonth: "2026-10",
  });
  page();
  await settled();
  expect(screen.getByTestId("ai-month")).toHaveValue("2026-10");
  expect(recommendation().mock.calls[0][0].startMonth).toBe("2026-10");
});
/** L-13: 프로젝트 시작월 = max(이번 달, 프로젝트 시작월). 이번 달은 2026-11로 고정. */
const pastProject = () => {
  options.targets[0].startMonth = "2026-03";
};
it("a past project month from the assignment drawer is raised to this month with the past-month toast", async () => {
  pastProject();
  requestAiRecommendation({
    targetType: "project",
    targetId: "project",
    jobCode: "BE",
    startMonth: "2026-03",
  });
  page();
  await settled();
  expect(screen.getByTestId("ai-month")).toHaveValue("2026-11");
  expect(recommendation().mock.calls[0][0].startMonth).toBe("2026-11");
  expect(screen.getByTestId("ai-toast")).toHaveTextContent(
    "시작월이 지난 달이라 2026-11로 맞춰 추천했습니다.",
  );
  // [추천 받기]로 지난 달을 고쳐 넣어도 같게 맞춘다.
  fireEvent.change(screen.getByTestId("ai-month"), {
    target: { value: "2026-05" },
  });
  fireEvent.click(screen.getByTestId("ai-recommend"));
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(recommendation().mock.calls[1][0].startMonth).toBe("2026-11");
});
it("menu open and target change use this month for a project that started earlier, without a clamp toast", async () => {
  pastProject();
  page();
  await settled();
  fireEvent.change(screen.getByTestId("ai-target"), {
    target: { value: "project:project" },
  });
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(recommendation().mock.calls[1][0]).toMatchObject({
    targetType: "project",
    startMonth: "2026-12",
  });
  // 대상을 바꾸면 고치던 시작월(기회 2026-12)을 이어받고, 이번 달 뒤라 그대로 둔다.
  // 메뉴로 열 때(요청 월 없음) 프로젝트는 max(이번 달, 프로젝트 시작월) = 2026-11, 알림 없음.
  cleanup();
  vi.clearAllMocks();
  options.targets = options.targets.filter((t) => t.targetType === "project");
  page();
  await settled();
  expect(recommendation().mock.calls[0][0].startMonth).toBe("2026-11");
  expect(screen.queryByTestId("ai-toast")).toBeNull();
});
it("B8: after a target change finishes, focus returns to the target select", async () => {
  page();
  await settled();
  const select = screen.getByTestId("ai-target");
  fireEvent.change(select, { target: { value: "project:project" } });
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(select).toBeEnabled());
  await waitFor(() => expect(select).toHaveFocus());
});
it("A5: an empty request to an open tab keeps the current target without re-running or a toast", async () => {
  page();
  await settled();
  fireEvent.change(screen.getByTestId("ai-target"), {
    target: { value: "project:project" },
  });
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(screen.getByTestId("ai-target")).toBeEnabled());
  const toast = screen.getByTestId("ai-toast").textContent;
  requestAiRecommendation({});
  await new Promise((resolve) => setTimeout(resolve, 50));
  expect(recommendation()).toHaveBeenCalledTimes(2);
  expect(screen.getByTestId("ai-target")).toHaveValue("project:project");
  expect(screen.getByTestId("ai-toast").textContent).toBe(toast);
});
it("F18: a 409 on [가배정] reloads targets so a deleted slot disappears", async () => {
  page();
  await settled();
  vi.mocked(api.getCommonAiRecommendationOptions).mockClear();
  // 누르기 전에 3번 줄(백엔드)이 지워졌다.
  options.targets[1].needs = options.targets[1].needs.filter((n) => n.needSn !== 3);
  vi.mocked(api.putCommonAiRecommendationTentativeAssignment).mockRejectedValueOnce(
    new AssignmentRequestError("이 기회의 필요 인력 줄을 골라 주세요.", [], 409),
  );
  fireEvent.click(screen.getByTestId("ai-assign-person"));
  expect(await screen.findByTestId("ai-error")).toHaveTextContent(
    "이 기회의 필요 인력 줄을 골라 주세요.",
  );
  await waitFor(() =>
    expect(api.getCommonAiRecommendationOptions).toHaveBeenCalledWith(),
  );
  await waitFor(() =>
    expect(
      within(screen.getByTestId("ai-need"))
        .getAllByRole("option")
        .map((o) => o.textContent),
    ).toEqual(["1. 프론트 2명 · 2027-01"]),
  );
  expect(screen.getByTestId("ai-need")).toHaveValue("7");
  expect(screen.getByTestId("ai-error")).toHaveTextContent(
    "이 기회의 필요 인력 줄을 골라 주세요.",
  );
});
it("the result header keeps the executed target name when that target drops out of the list", async () => {
  page();
  await settled();
  options.targets = options.targets.filter((t) => t.targetType === "project");
  recommendation().mockRejectedValueOnce(new Error("추천 실패"));
  fireEvent.focus(window);
  await screen.findByText("추천 실패");
  expect(screen.getByTestId("ai-applied")).toHaveTextContent(
    "진행 기회 · 백엔드 · 2026-12부터 · 고급",
  );
});
it("request ids work on plain http pages without crypto.randomUUID", () => {
  const original = globalThis.crypto;
  vi.stubGlobal("crypto", {
    getRandomValues: original.getRandomValues.bind(original),
  });
  try {
    const a = newAiRequestId();
    expect(a).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
    );
    expect(newAiRequestId()).not.toBe(a);
  } finally {
    vi.unstubAllGlobals();
  }
});
it("needless opportunity recommends and excludes with the mockup dialog but cannot tentatively assign", async () => {
  options.targets[1].needs = [];
  page();
  await settled();
  expect(
    screen.getByText("필요 인력을 먼저 등록해 주세요"),
  ).toBeInTheDocument();
  expect(screen.getByTestId("ai-assign-person")).toBeDisabled();
  fireEvent.click(screen.getByTestId("ai-exclude-person"));
  const dialog = screen.getByTestId("ai-exclusion-dialog");
  expect(dialog).toHaveAccessibleName("추천 인력님을 추천에서 뺄까요?");
  expect(dialog).toHaveTextContent(
    "이 자리·이 직무의 추천에서만 빠집니다. 직무를 바꾸거나 다른 자리를 고르면 다시 나옵니다.",
  );
  expect(screen.getByTestId("ai-exclusion-reason")).toHaveValue("SCHED");
  expect(screen.getByTestId("ai-exclusion-confirm")).toHaveClass("app-danger");
  fireEvent.change(screen.getByTestId("ai-exclusion-reason"), {
    target: { value: "OTHER" },
  });
  fireEvent.click(screen.getByTestId("ai-exclusion-confirm"));
  await waitFor(() =>
    expect(api.postCommonAiRecommendationExclusion).toHaveBeenCalledWith(
      expect.any(String),
      { developerId: "person", reasonCode: "OTHER" },
    ),
  );
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  expect(await screen.findByTestId("ai-toast")).toHaveTextContent(
    "추천 인력님을 이 자리(백엔드) 추천에서 뺐습니다.",
  );
});
it("tentative submits the current slot, refills with the executed condition and toasts the server text", async () => {
  const changed = vi.fn();
  window.addEventListener(withdrawalRefreshEvent, changed);
  page();
  await settled();
  const id = recommendation().mock.calls[0][0].requestId;
  fireEvent.change(screen.getByTestId("ai-need"), { target: { value: "7" } });
  fireEvent.change(screen.getByTestId("ai-grade"), { target: { value: "MD" } });
  fireEvent.click(screen.getByTestId("ai-assign-person"));
  await waitFor(() =>
    expect(
      api.putCommonAiRecommendationTentativeAssignment,
    ).toHaveBeenCalledWith(id, { developerId: "person", needSn: 7 }),
  );
  await waitFor(() => expect(recommendation()).toHaveBeenCalledTimes(2));
  // 다시 채울 때는 마지막으로 실행한 조건(고급·자리 3)을 쓰고 고치는 중인 칸은 그대로
  expect(recommendation().mock.calls[1][0]).toMatchObject({
    gradeCode: "SR",
    needSn: 3,
  });
  expect(screen.getByTestId("ai-grade")).toHaveValue("MD");
  // 서버 글 그대로: n은 자리 인원(4명 자리에 5번째면 4명).
  expect(await screen.findByTestId("ai-toast")).toHaveTextContent(
    "이 자리는 4명을 넘었습니다.",
  );
  expect(changed).toHaveBeenCalledTimes(1);
  window.removeEventListener(withdrawalRefreshEvent, changed);
});
it("converted candidates show the badge and the click reaches the server 409", async () => {
  cards[0].assignmentCandidate = true;
  vi.mocked(api.putCommonAiRecommendationTentativeAssignment).mockRejectedValueOnce(
    new AssignmentRequestError(
      "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.",
      [],
      409,
    ),
  );
  page();
  await settled();
  expect(screen.getByText("투입 배정 후보")).toBeInTheDocument();
  const button = screen.getByTestId("ai-assign-person");
  expect(button).toBeEnabled();
  fireEvent.click(button);
  expect(await screen.findByTestId("ai-error")).toHaveTextContent(
    "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.",
  );
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
it("the assignment drawer gets UI-12 roles, the mockup subtitle, UI-30 dirty mark and the keep-tentative line", async () => {
  requestAiRecommendation({ targetType: "project", targetId: "project" });
  page();
  await settled();
  expect(screen.getByTestId("ai-assign-person")).toHaveTextContent("배정");
  fireEvent.click(screen.getByTestId("ai-assign-person"));
  const form = await screen.findByTestId("assignment-form");
  expect(within(form).getByTestId("form-roles")).toHaveTextContent(
    "FIN,SALES_LDR",
  );
  expect(within(form).getByTestId("form-subtitle")).toHaveTextContent(
    "AI 인력 추천에서 채움",
  );
  expect(within(form).getByTestId("form-screen")).toHaveTextContent("UI-30");
  expect(within(form).getByTestId("form-warning")).toHaveTextContent(
    "다른 기회 「다른 기회」 가배정은 그대로 둡니다.",
  );
  expect(screen.queryByText("추천 인력을 투입 배정합니다.")).toBeNull();
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
it("same-person assignment shows the UI-12 notice, signals other tabs, records ASGN and retries only the record", async () => {
  const changed = vi.fn();
  window.addEventListener(withdrawalRefreshEvent, changed);
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
  expect(await screen.findByTestId("ai-toast")).toHaveTextContent(
    "배정을 저장했습니다. 경고: MM 1.0 초과",
  );
  expect(changed).toHaveBeenCalledTimes(1);
  await screen.findByText("기록 실패");
  expect(api.postCommonAiRecommendationAssignment).toHaveBeenCalledWith(
    recommendation().mock.calls[0][0].requestId,
    { developerId: "person", assignmentId: "saved" },
  );
  expect(screen.queryByTestId("assignment-form")).toBeNull();
  const retry = screen.getByTestId("ai-record-retry");
  // 2차 SFR-130-13: 기록이 실패하면 [다시 저장]
  expect(retry).toHaveTextContent(/^다시 저장$/);
  await waitFor(() => expect(retry).toBeEnabled());
  fireEvent.click(retry);
  await waitFor(() =>
    expect(api.postCommonAiRecommendationAssignment).toHaveBeenCalledTimes(2),
  );
  expect(screen.queryByTestId("assignment-form")).toBeNull();
  window.removeEventListener(withdrawalRefreshEvent, changed);
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
  expect(screen.getByTestId("ai-toast")).toHaveTextContent(
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

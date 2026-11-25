import { useState } from "react";
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
import { MemoryRouter, useLocation } from "react-router-dom";
import { AiAnalysisPage } from "./AiAnalysisPage";
import { AiAnalysisWorkspaceProvider } from "./AiAnalysisWorkspace";
import { UserPreferenceTools } from "../userpreferences/UserPreferenceTools";
import {
  publishScreenPeriod,
  screenPeriodChannel,
} from "../../shared/layout/screenPeriodChannel";
import { AssignmentRequestError } from "../assignments/assignments";
import * as api from "./aiAnalysis";
vi.mock("./aiAnalysis", async (original) => ({
  ...(await original<typeof import("./aiAnalysis")>()),
  getCommonAiAnalysisQuestions: vi.fn(),
  postCommonAiAnalysisQuestion: vi.fn(),
  getCommonAiAnalysisProgress: vi.fn(),
  getCommonAiAnalysisQuestionExcel: vi.fn(),
}));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
});
const answer: api.AnalysisAnswer = {
  questionId: "101",
  answerStatus: "OK",
  conclusion: "원천 조회 결과",
  baseMonth: "2026-11",
  provisional: false,
  table: {
    columns: [
      { key: "month", label: "연월" },
      { key: "revenue", label: "매출" },
    ],
    rows: [["2026-11", 123456]],
  },
  assumptions: ["기존 계산 사용"],
  usedData: "F-01 · UI-08 · 2026-11 · 마감",
  suggestedQuestions: [],
  actions: [
    {
      label: "원래 화면 열기",
      targetScreenId: "UI-08",
      period: { startMonth: "2026-11", endMonth: "2026-11" },
    },
  ],
};
beforeEach(() => {
  vi.mocked(api.getCommonAiAnalysisQuestions).mockResolvedValue({ items: [] });
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue(answer);
  vi.mocked(api.getCommonAiAnalysisQuestionExcel).mockResolvedValue(
    "AI분석.xlsx",
  );
  vi.mocked(api.getCommonAiAnalysisProgress).mockResolvedValue({
    requestId: "running",
    stage: "데이터 찾는 중",
    completed: false,
  });
});
function Location() {
  return <p data-testid="analysis-location">{useLocation().pathname}</p>;
}
function Workspace({ mobile = false }: { mobile?: boolean }) {
  const [page, setPage] = useState(true);
  return (
    <MemoryRouter initialEntries={["/screens/UI-26"]}>
      <AiAnalysisWorkspaceProvider>
        <UserPreferenceTools
          screenId="UI-08"
          menus={[]}
          available={() => true}
          onNavigate={() => {}}
          mobile={mobile}
        />
        <button
          onClick={() => {
            setPage(false);
            window.dispatchEvent(new Event("ai-analysis-tab-closed"));
          }}
        >
          탭 닫기
        </button>
        <button onClick={() => setPage(true)}>탭 열기</button>
        {page && <AiAnalysisPage />}
        <Location />
      </AiAnalysisWorkspaceProvider>
    </MemoryRouter>
  );
}
async function ask(question = "이번 달 매출") {
  fireEvent.change(screen.getByTestId("analysis-question"), {
    target: { value: question },
  });
  fireEvent.click(screen.getByTestId("analysis-send"));
  return screen.findByText("원천 조회 결과");
}
it("renders actual empty history and a question form instead of a placeholder", async () => {
  render(<AiAnalysisPage />);
  expect(
    await screen.findByText("저장된 질문이 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("질문")).toBeInTheDocument();
  expect(screen.queryByText("준비 중")).not.toBeInTheDocument();
});
it("renders exact source numbers, conclusion, assumptions, data and working marker", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    ...answer,
    provisional: true,
  });
  render(<AiAnalysisPage />);
  await ask();
  expect(screen.getByText("123,456원")).toBeInTheDocument();
  for (const label of ["한 줄 결론", "가정", "사용한 데이터", "잠정"])
    expect(screen.getByText(label)).toBeInTheDocument();
  expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledWith(
    expect.objectContaining({ question: "이번 달 매출", screenId: "UI-26" }),
  );
  expect(
    vi.mocked(api.postCommonAiAnalysisQuestion).mock.calls[0][0],
  ).not.toHaveProperty("period");
});
it.each(api.ANALYSIS_SUGGESTIONS)(
  "executes recommended question %s",
  async (question) => {
    render(<AiAnalysisPage />);
    fireEvent.click(screen.getByRole("button", { name: question }));
    await screen.findByText("원천 조회 결과");
    expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledWith(
      expect.objectContaining({ question }),
    );
  },
);
it("opening and closing the real header modal preserves the same conversation without duplicate POST", async () => {
  render(<Workspace />);
  await ask();
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  const dialog = screen.getByTestId("ai-analysis-dialog");
  expect(within(dialog).getByText("원천 조회 결과")).toBeInTheDocument();
  fireEvent.click(within(dialog).getByTestId("analysis-cancel"));
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  expect(
    within(screen.getByTestId("ai-analysis-dialog")).getByText(
      "원천 조회 결과",
    ),
  ).toBeInTheDocument();
  expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledTimes(1);
});
it("header question sends the actual source screen period", async () => {
  publishScreenPeriod("UI-08", { startMonth: "2026-01", endMonth: "2026-09" });
  render(<Workspace mobile />);
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  const dialog = within(screen.getByTestId("ai-analysis-dialog"));
  fireEvent.change(dialog.getByLabelText("질문"), {
    target: { value: "매출" },
  });
  fireEvent.click(dialog.getByTestId("analysis-send"));
  await waitFor(() =>
    expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledWith(
      expect.objectContaining({
        screenId: "UI-08",
        period: { startMonth: "2026-01", endMonth: "2026-09" },
      }),
    ),
  );
});
it("cancel clears only the draft, never the answer or history", async () => {
  render(<AiAnalysisPage />);
  await ask();
  fireEvent.change(screen.getByLabelText("질문"), {
    target: { value: "보내지 않은 질문" },
  });
  fireEvent.click(screen.getByTestId("analysis-cancel"));
  expect(screen.getByLabelText("질문")).toHaveValue("");
  expect(screen.getByText("원천 조회 결과")).toBeInTheDocument();
});
it("rejects blank and overlong input before a request", async () => {
  render(<AiAnalysisPage />);
  fireEvent.click(screen.getByTestId("analysis-send"));
  expect(screen.getByTestId("analysis-question-error")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("질문"), {
    target: { value: "😀".repeat(1001) },
  });
  fireEvent.click(screen.getByTestId("analysis-send"));
  expect(api.postCommonAiAnalysisQuestion).not.toHaveBeenCalled();
});
it("renders API field errors and retains the unsent value", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockRejectedValue(
    new AssignmentRequestError(
      "질문 확인",
      [{ field: "question", message: "질문 확인" }],
      400,
    ),
  );
  render(<AiAnalysisPage />);
  fireEvent.change(screen.getByLabelText("질문"), {
    target: { value: "매출" },
  });
  fireEvent.click(screen.getByTestId("analysis-send"));
  expect(
    await screen.findByTestId("analysis-question-error"),
  ).toHaveTextContent("질문 확인");
  expect(screen.getByLabelText("질문")).toHaveValue("매출");
});
it("history replay requests fresh data and a fresh identity", async () => {
  vi.mocked(api.getCommonAiAnalysisQuestions).mockResolvedValue({
    items: [
      {
        questionId: "old",
        question: "이번 달 매출",
        askedAt: "2026-11-24T23:00:00Z",
        answerStatus: "OK",
      },
    ],
  });
  render(<AiAnalysisPage />);
  const button = await screen.findByTestId("analysis-replay-old");
  fireEvent.click(button);
  await screen.findByText("원천 조회 결과");
  // Refreshing history replaces the list, so use the currently mounted button.
  await waitFor(() =>
    expect(screen.getByTestId("analysis-replay-old")).not.toBeDisabled(),
  );
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    ...answer,
    questionId: "102",
    conclusion: "새 원천 결과",
  });
  fireEvent.click(screen.getByTestId("analysis-replay-old"));
  await screen.findByText("새 원천 결과");
  const calls = vi.mocked(api.postCommonAiAnalysisQuestion).mock.calls;
  expect(calls[0][0].requestId).not.toEqual(calls[1][0].requestId);
  expect(screen.getByText("2026-11-25 08:00")).toBeInTheDocument();
});
it("closing UI-26 and a full remount clear dialogue but keep server history", async () => {
  vi.mocked(api.getCommonAiAnalysisQuestions).mockResolvedValue({
    items: [
      {
        questionId: "saved",
        question: "매출",
        askedAt: "2026-11-25T00:00:00Z",
        answerStatus: "OK",
      },
    ],
  });
  const mounted = render(<Workspace />);
  await ask();
  fireEvent.click(screen.getByText("탭 닫기"));
  fireEvent.click(screen.getByText("탭 열기"));
  expect(screen.queryByText("원천 조회 결과")).not.toBeInTheDocument();
  expect(screen.getByTestId("analysis-replay-saved")).toBeInTheDocument();
  mounted.unmount();
  render(<AiAnalysisPage />);
  expect(
    await screen.findByTestId("analysis-replay-saved"),
  ).toBeInTheDocument();
  expect(screen.queryByText("원천 조회 결과")).not.toBeInTheDocument();
});
it("hides Excel and numeric table for policy refusal and shows recommendations", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    ...answer,
    answerStatus: "NO_ANS",
    conclusion: "이 내용을 볼 권한이 없습니다.",
    table: undefined,
    actions: [],
    suggestedQuestions: ["이번 달 회사 매출"],
  });
  render(<AiAnalysisPage />);
  fireEvent.click(screen.getByTestId("analysis-suggestion-1"));
  expect(
    await screen.findByText("이 내용을 볼 권한이 없습니다."),
  ).toBeInTheDocument();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(screen.queryByText("엑셀 다운로드")).not.toBeInTheDocument();
  expect(
    screen.getAllByRole("button", { name: "이번 달 회사 매출" }),
  ).not.toHaveLength(0);
});
it("renders failed history with retry and a failed request without fabricated answer", async () => {
  vi.mocked(api.getCommonAiAnalysisQuestions).mockRejectedValueOnce(
    new Error("offline"),
  );
  vi.mocked(api.postCommonAiAnalysisQuestion).mockRejectedValue(
    new Error("offline"),
  );
  render(<AiAnalysisPage />);
  expect(
    await screen.findByTestId("analysis-history-retry"),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("analysis-history-retry"));
  await screen.findByText("저장된 질문이 없습니다.");
  fireEvent.click(screen.getByTestId("analysis-suggestion-2"));
  expect(
    await screen.findByText("질문을 처리하지 못했습니다. 다시 질문해 주세요."),
  ).toBeInTheDocument();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
it("polls actual server stages without timer-based synthetic transitions", async () => {
  vi.useFakeTimers();
  let complete!: (answer: api.AnalysisAnswer) => void;
  vi.mocked(api.postCommonAiAnalysisQuestion).mockReturnValue(
    new Promise((resolve) => {
      complete = resolve;
    }),
  );
  render(<AiAnalysisPage />);
  fireEvent.click(screen.getByTestId("analysis-suggestion-2"));
  expect(screen.getByTestId("analysis-send")).toBeDisabled();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(500);
  });
  expect(screen.getByTestId("analysis-progress")).toHaveTextContent(
    "데이터 찾는 중",
  );
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2000);
  });
  expect(screen.getByTestId("analysis-progress")).toHaveTextContent(
    "데이터 찾는 중",
  );
  vi.mocked(api.getCommonAiAnalysisProgress).mockResolvedValue({
    requestId: "running",
    stage: "계산 중",
    completed: false,
  });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(500);
  });
  expect(screen.getByTestId("analysis-progress")).toHaveTextContent("계산 중");
  await act(async () => {
    complete(answer);
  });
  expect(screen.queryByTestId("analysis-progress")).not.toBeInTheDocument();
});
it("answer actions navigate via the shared period channel and saved Excel calls only its endpoint", async () => {
  const receive = vi.fn();
  const unsubscribe = screenPeriodChannel("UI-08").subscribe(receive);
  render(<Workspace />);
  await ask();
  fireEvent.click(screen.getByText("원래 화면 열기"));
  expect(receive).toHaveBeenCalledWith(answer.actions[0].period);
  expect(screen.getByTestId("analysis-location")).toHaveTextContent(
    "/screens/UI-08",
  );
  fireEvent.click(screen.getByText("엑셀 다운로드"));
  await waitFor(() =>
    expect(api.getCommonAiAnalysisQuestionExcel).toHaveBeenCalledWith("101"),
  );
  expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledTimes(1);
  unsubscribe();
});

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
const scrollIntoView = vi.fn();
beforeEach(() => {
  Element.prototype.scrollIntoView = scrollIntoView;
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
});
const answer: api.AnalysisAnswer = {
  questionId: "101",
  answerStatus: "OK",
  conclusion: "원천 조회 결과",
  forecast: false,
  provisional: false,
  tables: [
    {
      columns: [
        { key: "month", label: "연월", align: "left", format: "month" },
        { key: "revenue", label: "매출", align: "right", format: "money" },
        { key: "rate", label: "이익률", align: "right", format: "rate" },
        { key: "mm", label: "MM", align: "right", format: "mm" },
        { key: "count", label: "인원", align: "right", format: "count" },
      ],
      rows: [["2026-11", 123456000, 12.345, 1.5, 3]],
    },
  ],
  assumptions: ["기존 계산 사용"],
  usedData: "영업이익표 · 2026-11",
  baseMonth: "2026-11",
  excel: true,
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
function Workspace({
  mobile = false,
  showPage = true,
}: {
  mobile?: boolean;
  showPage?: boolean;
}) {
  const [page, setPage] = useState(showPage);
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
function send(question: string) {
  fireEvent.change(screen.getByTestId("analysis-question"), {
    target: { value: question },
  });
  fireEvent.submit(screen.getByTestId("analysis-question").closest("form")!);
}
async function ask(question = "이번 달 매출") {
  send(question);
  return screen.findByText("원천 조회 결과");
}
it("renders actual empty history and the mockup one-line input without the old empty text", async () => {
  render(<AiAnalysisPage />);
  expect(
    await screen.findByText("저장된 질문이 없습니다."),
  ).toBeInTheDocument();
  const input = screen.getByLabelText("질문");
  expect(input.tagName).toBe("INPUT");
  expect(input).toHaveAttribute(
    "placeholder",
    "예: 다음 달 철수하는 백엔드 개발자는 몇 명?",
  );
  expect(input).toHaveAttribute("autocomplete", "off");
  expect(screen.getByTestId("analysis-send")).toHaveAccessibleName("보내기");
  expect(screen.queryByTestId("analysis-cancel")).not.toBeInTheDocument();
  expect(screen.queryByText("숫자와 예측을 질문해 보세요.")).toBeNull();
  expect(screen.getByText("질문 기록").tagName).not.toBe("H2");
  expect(screen.queryByText("준비 중")).not.toBeInTheDocument();
});
it("Enter sends, clears the input and scrolls to the newest message", async () => {
  render(<AiAnalysisPage />);
  const input = screen.getByTestId("analysis-question");
  fireEvent.change(input, { target: { value: "이번 달 매출" } });
  fireEvent.submit(input.closest("form")!);
  expect(input).toHaveValue("");
  await screen.findByText("원천 조회 결과");
  expect(scrollIntoView).toHaveBeenCalled();
  expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledWith(
    expect.objectContaining({ question: "이번 달 매출", screenId: "UI-26" }),
  );
  expect(
    vi.mocked(api.postCommonAiAnalysisQuestion).mock.calls[0][0],
  ).not.toHaveProperty("period");
});
it("empty input is ignored silently", async () => {
  render(<AiAnalysisPage />);
  fireEvent.change(screen.getByTestId("analysis-question"), {
    target: { value: "   " },
  });
  fireEvent.click(screen.getByTestId("analysis-send"));
  expect(screen.queryByTestId("analysis-question-error")).toBeNull();
  expect(screen.queryByRole("alert")).toBeNull();
  expect(api.postCommonAiAnalysisQuestion).not.toHaveBeenCalled();
});
it("renders answer in mockup order without headings, with common formats", async () => {
  render(<AiAnalysisPage />);
  await ask();
  const card = screen.getByTestId("analysis-answer-101");
  for (const heading of ["한 줄 결론", "가정", "사용한 데이터", "표"])
    expect(within(card).queryByRole("heading", { name: heading })).toBeNull();
  expect(within(card).queryByText(/^기준월/)).toBeNull();
  const row = within(screen.getByTestId("analysis-table-101-0-row-0"));
  expect(row.getByText("2026-11")).toBeInTheDocument();
  expect(row.getByText("123,456,000")).toHaveClass("ai-num");
  expect(row.getByText("12.3%")).toBeInTheDocument();
  expect(row.getByText("1.50")).toBeInTheDocument();
  expect(row.getByText("3")).toBeInTheDocument();
  expect(row.getByText("2026-11")).not.toHaveClass("ai-num");
  expect(screen.getByText("가정: 기존 계산 사용")).toHaveClass("ai-cap");
  expect(screen.getByTestId("analysis-scope-101")).toHaveTextContent(
    "사용한 데이터: 영업이익표 · 2026-11",
  );
  const order = [...card.children].map((node) => node.className);
  expect(order[0]).toBe("ai-one");
  expect(order.at(-1)).toBe("ai-ex");
  expect(within(card).getByText("엑셀 다운로드")).toBeInTheDocument();
});
it("forecast answer shows the 예측 badge, provisional the 잠정 badge in the line and the scope", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValueOnce({
    ...answer,
    forecast: true,
    provisional: true,
  });
  render(<AiAnalysisPage />);
  await ask();
  const one = screen.getByTestId("analysis-conclusion-101");
  expect(within(one).getByText("예측")).toHaveClass("ls-b-info");
  expect(within(one).queryByText("잠정")).toBeNull();
  expect(
    within(screen.getByTestId("analysis-scope-101")).getByText("잠정"),
  ).toHaveClass("ls-b-warn");
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValueOnce({
    ...answer,
    questionId: "102",
    conclusion: "두 번째",
    provisional: true,
  });
  await waitFor(() => expect(screen.getByTestId("analysis-send")).toBeEnabled());
  fireEvent.change(screen.getByTestId("analysis-question"), {
    target: { value: "다시" },
  });
  fireEvent.click(screen.getByTestId("analysis-send"));
  await screen.findByText("두 번째");
  expect(
    within(screen.getByTestId("analysis-conclusion-102")).getByText("잠정"),
  ).toHaveClass("ls-b-warn");
});
it("renders only ** pairs as bold and escapes everything else", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    ...answer,
    conclusion: "매출은 **1.2억 원** <img src=x onerror=alert(1)> 입니다 **끝",
  });
  render(<AiAnalysisPage />);
  fireEvent.click(screen.getByTestId("analysis-suggestion-2"));
  const one = await screen.findByTestId("analysis-conclusion-101");
  expect(one.querySelector("strong")).toHaveTextContent("1.2억 원");
  expect(one.querySelectorAll("strong")).toHaveLength(1);
  expect(one.querySelector("img")).toBeNull();
  expect(one).toHaveTextContent("<img src=x onerror=alert(1)> 입니다 **끝");
});
it("renders tones, a total row and the line chart", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    ...answer,
    tables: [
      {
        title: "직무별",
        columns: [
          { key: "job", label: "직무", align: "left", format: "text" },
          { key: "m1", label: "2027-01", align: "right", format: "count" },
        ],
        rows: [
          ["백엔드", -2],
          ["프론트", 3],
        ],
        tones: [
          [null, "bad"],
          [null, "warn"],
        ],
        totalRow: ["전체", 1],
      },
    ],
    chart: {
      type: "line",
      labels: ["1월", "2월"],
      series: [
        { name: "매출", values: [100, 200] },
        { name: "영업이익", values: [10, null] },
      ],
    },
  });
  render(<AiAnalysisPage />);
  fireEvent.click(screen.getByTestId("analysis-suggestion-2"));
  await screen.findByText("직무별");
  expect(screen.getByText("−2")).toHaveClass("ai-tone-bad");
  expect(screen.getByText("3")).toHaveClass("ai-tone-warn");
  expect(
    within(screen.getByTestId("analysis-table-101-0-total")).getByText("전체")
      .tagName,
  ).toBe("STRONG");
  expect(screen.getByTestId("analysis-chart-101")).toHaveAttribute(
    "aria-label",
    "매출, 영업이익 추이",
  );
});
it("shows the three mockup suggestions once and sends each", async () => {
  expect(api.ANALYSIS_SUGGESTIONS).toEqual([
    "다음 분기 대기 인력은?",
    "이번 달 적자 인력은?",
    "이번 달 매출과 영업이익은?",
  ]);
  render(<AiAnalysisPage />);
  for (const question of api.ANALYSIS_SUGGESTIONS) {
    await waitFor(() =>
      expect(screen.getByRole("button", { name: question })).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole("button", { name: question }));
    await waitFor(() =>
      expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledWith(
        expect.objectContaining({ question }),
      ),
    );
  }
});
it("NO_ANS answer shows only the line and suggestions not already shown below", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    questionId: "201",
    answerStatus: "NO_ANS",
    conclusion: "이 질문에는 아직 답을 찾지 못했습니다. 아래처럼 물어보세요.",
    suggestedQuestions: [...api.ANALYSIS_SUGGESTIONS, "9월 적자 프로젝트는?"],
    assumptions: ["무시"],
    baseMonth: "2026-11",
    actions: [],
  });
  render(<AiAnalysisPage />);
  fireEvent.click(screen.getByTestId("analysis-suggestion-1"));
  const card = await screen.findByTestId("analysis-answer-201");
  expect([...card.children].map((node) => node.className)).toEqual([
    "ai-one",
    "ai-ex",
  ]);
  expect(within(card).getAllByRole("button")).toHaveLength(1);
  expect(
    within(card).getByRole("button", { name: "9월 적자 프로젝트는?" }),
  ).toBeInTheDocument();
  for (const question of api.ANALYSIS_SUGGESTIONS)
    expect(screen.getAllByRole("button", { name: question })).toHaveLength(1);
  expect(within(card).queryByText(/가정|사용한 데이터|기준월/)).toBeNull();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(screen.queryByText("엑셀 다운로드")).not.toBeInTheDocument();
});
it("salary without permission shows the mockup second line under the conclusion", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    questionId: "202",
    answerStatus: "NO_ANS",
    conclusion: "이 내용을 볼 권한이 없습니다.",
    usedData:
      "급여는 급여를 볼 수 있는 권한(시스템 관리자·경영진·재무 담당)에서만 답합니다.",
    suggestedQuestions: [],
    baseMonth: "2026-11",
  });
  render(<AiAnalysisPage />);
  send("이번 달 급여");
  const card = await screen.findByTestId("analysis-answer-202");
  expect([...card.children].map((node) => node.className)).toEqual([
    "ai-one",
    "ai-scope",
  ]);
  expect(within(card).getByTestId("analysis-scope-202")).toHaveTextContent(
    /^급여는 급여를 볼 수 있는 권한\(시스템 관리자·경영진·재무 담당\)에서만 답합니다\.$/,
  );
});
it("ambiguous project buttons show only the project name and ask the full question", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValueOnce({
    questionId: "203",
    answerStatus: "NO_ANS",
    conclusion: "어느 프로젝트인지 골라 주세요.",
    suggestedQuestions: ["가온은행 차세대 이익률 추이", "가온카드 차세대 이익률 추이"],
    suggestionLabels: ["가온은행 차세대", "가온카드 차세대"],
    baseMonth: "2026-11",
  });
  render(<AiAnalysisPage />);
  send("가온 이익률 추이");
  const card = await screen.findByTestId("analysis-answer-203");
  const button = within(card).getByRole("button", { name: "가온카드 차세대" });
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue(answer);
  fireEvent.click(button);
  await waitFor(() =>
    expect(api.postCommonAiAnalysisQuestion).toHaveBeenLastCalledWith(
      expect.objectContaining({ question: "가온카드 차세대 이익률 추이" }),
    ),
  );
});
it("hidden tables stay off the screen and a rate chart labels its ticks in percent", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    ...answer,
    questionId: "204",
    tables: [{ ...answer.tables![0], hidden: true }],
    chart: {
      type: "line",
      labels: ["9월", "10월"],
      series: [{ name: "마진율", values: [12, 18] }],
      format: "rate",
    },
  });
  render(<AiAnalysisPage />);
  await ask();
  const card = await screen.findByTestId("analysis-answer-204");
  expect(within(card).queryByRole("table")).toBeNull();
  const chart = within(card).getByTestId("analysis-chart-204");
  const ticks = [...chart.querySelectorAll("text.ax")].map((node) => node.textContent);
  expect(ticks).toContain("20%");
  expect(ticks).not.toContain("0");
  expect(within(card).getByText("엑셀 다운로드")).toBeInTheDocument();
});
it("header drawer opens on the right with the mockup title and closes on backdrop and Esc", async () => {
  render(<Workspace showPage={false} />);
  const opener = screen.getByTestId("ai-analysis-open");
  opener.focus();
  fireEvent.click(opener);
  const drawer = screen.getByTestId("ai-analysis-dialog");
  expect(drawer).toHaveClass("ls-drawer");
  expect(drawer).toHaveAttribute("aria-label", "AI에게 묻기");
  expect(
    within(drawer).getByText("지금 화면을 보면서 질문할 수 있습니다"),
  ).toBeInTheDocument();
  expect(within(drawer).queryByText(/현재 화면 · 기간/)).toBeNull();
  expect(document.querySelector('[data-screen-id="UI-38"]')).toBeNull();
  fireEvent.click(screen.getByTestId("detail-panel-scrim"));
  expect(screen.queryByTestId("ai-analysis-dialog")).toBeNull();
  expect(opener).toHaveFocus();
  fireEvent.click(opener);
  expect(screen.getByTestId("ai-analysis-dialog")).toBeInTheDocument();
  fireEvent.keyDown(window, { key: "Escape" });
  expect(screen.queryByTestId("ai-analysis-dialog")).toBeNull();
});
it("opening and closing the header drawer preserves the same conversation without duplicate POST", async () => {
  render(<Workspace />);
  await ask();
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  const dialog = screen.getByTestId("ai-analysis-dialog");
  expect(within(dialog).getByText("원천 조회 결과")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("ai-analysis-dialog-close"));
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
  render(<Workspace mobile showPage={false} />);
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  const dialog = within(screen.getByTestId("ai-analysis-dialog"));
  // 창을 연 뒤 화면 기간이 바뀌면 보낼 때의 기간을 보낸다.
  publishScreenPeriod("UI-08", { startMonth: "2026-01", endMonth: "2026-10" });
  fireEvent.change(dialog.getByLabelText("질문"), {
    target: { value: "매출" },
  });
  fireEvent.click(dialog.getByTestId("analysis-send"));
  await waitFor(() =>
    expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledWith(
      expect.objectContaining({
        screenId: "UI-08",
        period: { startMonth: "2026-01", endMonth: "2026-10" },
      }),
    ),
  );
});
it("rejects overlong input before a request and keeps it", async () => {
  render(<AiAnalysisPage />);
  fireEvent.change(screen.getByLabelText("질문"), {
    target: { value: "😀".repeat(1001) },
  });
  fireEvent.click(screen.getByTestId("analysis-send"));
  expect(screen.getByTestId("analysis-question-error")).toBeInTheDocument();
  expect(screen.getByLabelText("질문")).toHaveValue("😀".repeat(1001));
  expect(api.postCommonAiAnalysisQuestion).not.toHaveBeenCalled();
});
it("renders API field errors and restores the unsent value", async () => {
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
it("history shows at most 20 questions without time, and replay requests a fresh identity", async () => {
  vi.mocked(api.getCommonAiAnalysisQuestions).mockResolvedValue({
    items: Array.from({ length: 25 }, (_, i) => ({
      questionId: `q${i}`,
      question: i === 0 ? "이번 달 매출" : `질문 ${i}`,
      askedAt: "2026-11-24T23:00:00Z",
      answerStatus: "OK" as const,
    })),
  });
  render(<AiAnalysisPage />);
  const button = await screen.findByTestId("analysis-replay-q0");
  expect(screen.getAllByTestId(/^analysis-replay-/)).toHaveLength(20);
  expect(screen.queryByText("2026-11-25 08:00")).toBeNull();
  expect(screen.queryByText("클릭: 다시 질문")).toBeNull();
  fireEvent.click(button);
  await screen.findByText("원천 조회 결과");
  await waitFor(() =>
    expect(screen.getByTestId("analysis-replay-q0")).not.toBeDisabled(),
  );
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    ...answer,
    questionId: "102",
    conclusion: "새 원천 결과",
  });
  fireEvent.click(screen.getByTestId("analysis-replay-q0"));
  await screen.findByText("새 원천 결과");
  const calls = vi.mocked(api.postCommonAiAnalysisQuestion).mock.calls;
  expect(calls[0][0].question).toBe("이번 달 매출");
  expect(calls[0][0].requestId).not.toEqual(calls[1][0].requestId);
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
  expect(screen.getAllByRole("alert")).toHaveLength(1);
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
it("shows all three steps and bolds the server's current stage without timer transitions", async () => {
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
  const steps = screen.getByTestId("analysis-progress");
  expect(steps).toHaveTextContent("데이터 찾는 중 → 계산 중 → 정리 중");
  expect(steps).toHaveAttribute("aria-live", "polite");
  expect(screen.queryByText("질문을 접수하고 있습니다.")).toBeNull();
  expect(document.querySelectorAll("[aria-live]")).toHaveLength(1);
  const current = () =>
    screen.getByTestId("analysis-progress").querySelector(".ai-on");
  expect(current()).toHaveTextContent("데이터 찾는 중");
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2500);
  });
  expect(current()).toHaveTextContent("데이터 찾는 중");
  vi.mocked(api.getCommonAiAnalysisProgress).mockResolvedValue({
    requestId: "running",
    stage: "계산 중",
    completed: false,
  });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(500);
  });
  expect(current()).toHaveTextContent("계산 중");
  await act(async () => {
    complete(answer);
  });
  expect(screen.queryByTestId("analysis-progress")).not.toBeInTheDocument();
});
it("answer actions pass the period, navigate and close the drawer; saved Excel calls only its endpoint", async () => {
  const receive = vi.fn();
  const unsubscribe = screenPeriodChannel("UI-08").subscribe(receive);
  render(<Workspace showPage={false} />);
  fireEvent.click(screen.getByTestId("ai-analysis-open"));
  const dialog = within(screen.getByTestId("ai-analysis-dialog"));
  fireEvent.click(dialog.getByTestId("analysis-suggestion-2"));
  await dialog.findByText("원천 조회 결과");
  fireEvent.click(dialog.getByText("엑셀 다운로드"));
  await waitFor(() =>
    expect(api.getCommonAiAnalysisQuestionExcel).toHaveBeenCalledWith("101"),
  );
  fireEvent.click(dialog.getByText("원래 화면 열기"));
  expect(receive).toHaveBeenCalledWith(answer.actions![0].period);
  expect(screen.getByTestId("analysis-location")).toHaveTextContent(
    "/screens/UI-08",
  );
  expect(screen.queryByTestId("ai-analysis-dialog")).toBeNull();
  expect(api.postCommonAiAnalysisQuestion).toHaveBeenCalledTimes(1);
  unsubscribe();
});
it("Excel failure shows the common Toast", async () => {
  vi.mocked(api.getCommonAiAnalysisQuestionExcel).mockRejectedValue(
    new Error("offline"),
  );
  render(<AiAnalysisPage />);
  await ask();
  fireEvent.click(screen.getByText("엑셀 다운로드"));
  expect(await screen.findByTestId("analysis-excel-toast-101")).toHaveClass(
    "app-toast",
  );
});
it("tolerates answers with only the required fields", async () => {
  vi.mocked(api.postCommonAiAnalysisQuestion).mockResolvedValue({
    questionId: "301",
    answerStatus: "OK",
    conclusion: "짧은 답",
  });
  render(<AiAnalysisPage />);
  fireEvent.click(screen.getByTestId("analysis-suggestion-0"));
  const card = await screen.findByTestId("analysis-answer-301");
  expect([...card.children].map((node) => node.className)).toEqual(["ai-one"]);
});

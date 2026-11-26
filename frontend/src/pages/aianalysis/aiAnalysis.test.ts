import { afterEach, expect, it, vi } from "vitest";
import {
  formatAnalysisCell,
  getCommonAiAnalysisProgress,
  getCommonAiAnalysisQuestionExcel,
  getCommonAiAnalysisQuestions,
  isRightAligned,
  postCommonAiAnalysisQuestion,
  splitBold,
} from "./aiAnalysis";
it("formats cells with the common rules and never shows raw long decimals", () => {
  // 시안 won(): 표 금액은 원 단위 천 단위 쉼표(억·만 줄임은 결론 글에만).
  expect(formatAnalysisCell(123456000, "money")).toBe("123,456,000");
  expect(formatAnalysisCell(-35000000, "money")).toBe("−35,000,000");
  expect(formatAnalysisCell(1234.6, "money")).toBe("1,235");
  expect(formatAnalysisCell(12.345, "rate")).toBe("12.3%");
  expect(formatAnalysisCell(0.333333, "mm")).toBe("0.33");
  expect(formatAnalysisCell(1234.4, "count")).toBe("1,234");
  expect(formatAnalysisCell(-0.001, "mm")).toBe("0.00");
  expect(formatAnalysisCell("12.5", "rate")).toBe("12.5%");
  expect(formatAnalysisCell(null, "money")).toBe("–");
  expect(formatAnalysisCell("2026-11", "month")).toBe("2026-11");
  expect(formatAnalysisCell("백엔드")).toBe("백엔드");
  expect(isRightAligned({ key: "a", label: "a", format: "money" })).toBe(true);
  expect(isRightAligned({ key: "a", label: "a", format: "text" })).toBe(false);
  expect(
    isRightAligned({ key: "a", label: "a", format: "money", align: "left" }),
  ).toBe(false);
});
it("splits only paired ** markers", () => {
  expect(splitBold("a **b** c **d")).toEqual([
    { text: "a ", bold: false },
    { text: "b", bold: true },
    { text: " c **d", bold: false },
  ]);
  expect(splitBold("<b>x</b>")).toEqual([{ text: "<b>x</b>", bold: false }]);
});
import { AssignmentRequestError } from "../assignments/assignments";
import { downloadFile } from "../../shared/list/download";
vi.mock("../../shared/list/download", () => ({
  downloadFile: vi.fn().mockResolvedValue(undefined),
}));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});
it("uses the approved relative paths and creates a question without client actor or roles", async () => {
  const fetcher = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ success: true, data: { items: [] } }),
  });
  vi.stubGlobal("fetch", fetcher);
  await getCommonAiAnalysisQuestions();
  await getCommonAiAnalysisProgress("request_1");
  const input = { requestId: "request_1", question: "매출", screenId: "UI-26" };
  await postCommonAiAnalysisQuestion(input);
  expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
    "/api/common/ai-analysis/questions",
    "/api/common/ai-analysis/requests/request_1",
    "/api/common/ai-analysis/questions",
  ]);
  expect(fetcher.mock.calls[2][1]).toMatchObject({
    method: "POST",
    body: JSON.stringify(input),
  });
});
it("preserves API field validation errors", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        message: "질문 확인",
        errors: [{ field: "question", message: "입력 필요" }],
      }),
    }),
  );
  await expect(
    postCommonAiAnalysisQuestion({
      requestId: "r",
      question: "",
      screenId: "UI-26",
    }),
  ).rejects.toBeInstanceOf(AssignmentRequestError);
});
it("downloads only the saved question endpoint", async () => {
  await getCommonAiAnalysisQuestionExcel("121");
  expect(downloadFile).toHaveBeenCalledWith(
    "/api/common/ai-analysis/questions/121/excel",
    "AI분석.xlsx",
  );
});

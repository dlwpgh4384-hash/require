import { afterEach, expect, it, vi } from "vitest";
import {
  getCommonAiAnalysisProgress,
  getCommonAiAnalysisQuestionExcel,
  getCommonAiAnalysisQuestions,
  postCommonAiAnalysisQuestion,
} from "./aiAnalysis";
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

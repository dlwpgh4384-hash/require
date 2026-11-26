import { AiAnalysisConversation } from "./AiAnalysisConversation";
import {
  AiAnalysisWorkspaceProvider,
  useAiAnalysis,
  useOptionalAiAnalysis,
} from "./AiAnalysisWorkspace";
import { ANALYSIS_HISTORY_LIMIT } from "./aiAnalysis";
import "./aianalysis.css";

/** 시안 SCR['UI-26']: 왼쪽 질문 기록(.hist), 오른쪽 대화(chatHtml). */
function AnalysisPageContent() {
  const workspace = useAiAnalysis();

  return (
    <div
      className="screen-body ai-chat"
      data-testid="ai-analysis-page"
      data-screen-id="UI-26"
    >
      <aside className="ai-hist" aria-label="질문 기록">
        <div className="ai-nq-t">질문 기록</div>
        {workspace.historyLoading ? (
          <p className="ai-cap" role="status">
            질문 기록을 불러오는 중입니다.
          </p>
        ) : workspace.historyError ? (
          <>
            <p className="ai-cap" role="alert">
              {workspace.historyError}
            </p>
            <button
              type="button"
              data-testid="analysis-history-retry"
              onClick={() => {
                void workspace.reloadHistory();
              }}
            >
              다시 시도
            </button>
          </>
        ) : workspace.history.length === 0 ? (
          <p className="ai-cap">저장된 질문이 없습니다.</p>
        ) : (
          workspace.history.slice(0, ANALYSIS_HISTORY_LIMIT).map((item) => (
            <button
              type="button"
              key={item.questionId}
              data-testid={`analysis-replay-${item.questionId}`}
              disabled={workspace.busy}
              onClick={() => {
                void workspace.send(item.question, "UI-26");
              }}
            >
              {item.question}
            </button>
          ))
        )}
      </aside>
      <AiAnalysisConversation />
    </div>
  );
}
export function AiAnalysisPage() {
  const workspace = useOptionalAiAnalysis();
  return workspace ? (
    <AnalysisPageContent />
  ) : (
    <AiAnalysisWorkspaceProvider>
      <AnalysisPageContent />
    </AiAnalysisWorkspaceProvider>
  );
}

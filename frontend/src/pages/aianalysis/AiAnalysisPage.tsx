import { formatDateTime } from "../../shared/time";
import { AiAnalysisConversation } from "./AiAnalysisConversation";
import {
  AiAnalysisWorkspaceProvider,
  useAiAnalysis,
  useOptionalAiAnalysis,
} from "./AiAnalysisWorkspace";
import "./aianalysis.css";

function AnalysisPageContent() {
  const workspace = useAiAnalysis();

  return (
    <div
      className="screen-body ai-analysis-page"
      data-testid="ai-analysis-page"
      data-screen-id="UI-26"
    >
      <aside className="ai-analysis-history" aria-label="질문 기록">
        <h2>질문 기록</h2>
        <small>클릭: 다시 질문</small>
        {workspace.historyLoading ? (
          <p role="status">질문 기록을 불러오는 중입니다.</p>
        ) : workspace.historyError ? (
          <>
            <p role="alert">{workspace.historyError}</p>
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
          <p>저장된 질문이 없습니다.</p>
        ) : (
          <ul>
            {workspace.history.map((item) => (
              <li
                key={item.questionId}
                data-testid={`analysis-history-${item.questionId}`}
              >
                <button
                  type="button"
                  data-testid={`analysis-replay-${item.questionId}`}
                  disabled={workspace.busy}
                  onClick={() => {
                    void workspace.send(item.question, "UI-26");
                  }}
                >
                  {item.question}
                  <small>{formatDateTime(item.askedAt)}</small>
                </button>
              </li>
            ))}
          </ul>
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

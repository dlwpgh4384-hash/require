import { useId, useState } from "react";
import { AiAnalysisAnswer } from "./AiAnalysisAnswer";
import { useAiAnalysis } from "./AiAnalysisWorkspace";
import { ANALYSIS_SUGGESTIONS, type AnalysisPeriod } from "./aiAnalysis";

export function AiAnalysisConversation({
  screenId = "UI-26",
  period,
  onClose,
}: {
  screenId?: string;
  period?: AnalysisPeriod;
  onClose?: () => void;
}) {
  const workspace = useAiAnalysis();
  const [draft, setDraft] = useState("");
  const id = useId();
  const send = async (text: string) => {
    if (await workspace.send(text, screenId, period)) setDraft("");
  };
  return (
    <section
      className="ai-analysis-conversation"
      data-testid="analysis-conversation"
    >
      <div className="ai-analysis-turns" aria-live="polite">
        {workspace.turns.length === 0 && <p>숫자와 예측을 질문해 보세요.</p>}
        {workspace.turns.map((turn) => (
          <section key={turn.id} data-testid={`analysis-turn-${turn.id}`}>
            <p className="ai-analysis-question">{turn.question}</p>
            {turn.answer && (
              <AiAnalysisAnswer
                answer={turn.answer}
                onNavigate={onClose}
                disabled={workspace.busy}
                onQuestion={(question) => {
                  void send(question);
                }}
              />
            )}
            {turn.error && <p role="alert">{turn.error}</p>}
          </section>
        ))}
        {workspace.busy && (
          <p role="status" data-testid="analysis-progress">
            {workspace.progress || "질문을 접수하고 있습니다."}
          </p>
        )}
      </div>
      <div className="ai-analysis-suggestions">
        {ANALYSIS_SUGGESTIONS.map((text, index) => (
          <button
            type="button"
            data-testid={`analysis-suggestion-${index}`}
            key={text}
            disabled={workspace.busy}
            onClick={() => {
              void send(text);
            }}
          >
            {text}
          </button>
        ))}
      </div>
      {workspace.turns
        .at(-1)
        ?.answer?.suggestedQuestions.filter(
          (text) => !(ANALYSIS_SUGGESTIONS as readonly string[]).includes(text),
        )
        .map((text, i) => (
          <button
            type="button"
            data-testid={`analysis-extra-suggestion-${i}`}
            key={text}
            disabled={workspace.busy}
            onClick={() => {
              void send(text);
            }}
          >
            {text}
          </button>
        ))}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void send(draft);
        }}
      >
        <label htmlFor={id}>질문</label>
        <textarea
          id={id}
          data-testid="analysis-question"
          value={draft}
          aria-invalid={!!workspace.fieldError}
          onChange={(event) => setDraft(event.target.value)}
        />
        {workspace.fieldError && (
          <p role="alert" data-testid="analysis-question-error">
            {workspace.fieldError}
          </p>
        )}
        <div className="ai-analysis-actions">
          <button
            type="button"
            data-testid="analysis-cancel"
            onClick={() => {
              if (onClose) onClose();
              else setDraft("");
            }}
          >
            취소
          </button>
          <button
            type="submit"
            data-testid="analysis-send"
            className="ls-btn ls-btn-p"
            disabled={workspace.busy}
          >
            질문 보내기
          </button>
        </div>
      </form>
    </section>
  );
}

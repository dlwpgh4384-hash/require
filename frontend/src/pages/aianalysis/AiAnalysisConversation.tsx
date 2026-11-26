import { Fragment, useEffect, useId, useRef, useState } from "react";
import { AiAnalysisAnswer } from "./AiAnalysisAnswer";
import { useAiAnalysis } from "./AiAnalysisWorkspace";
import { ANALYSIS_SUGGESTIONS, type AnalysisPeriod } from "./aiAnalysis";

/** 시안 진행 단계(데이터 찾는 중 → 계산 중 → 정리 중). 지금 단계는 서버가 알려 준 값. */
const STEPS = ["데이터 찾는 중", "계산 중", "정리 중"] as const;

const sendIcon = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M4 12l16-8-6 16-2-7z" />
  </svg>
);

/**
 * 시안 chatHtml(): 대화(.msgs) → 추천 질문(.ex) → 한 줄 입력(.ask).
 * UI-26 화면과 헤더 [AI에게 묻기] 창이 같은 부품·같은 대화를 쓴다.
 */
export function AiAnalysisConversation({
  screenId = "UI-26",
  readPeriod,
  onClose,
}: {
  screenId?: string;
  /** 보낼 때 그 화면의 지금 기간을 읽는다(창을 연 뒤 화면 기간을 바꿔도 바뀐 기간을 보냄). */
  readPeriod?: () => AnalysisPeriod | undefined;
  onClose?: () => void;
}) {
  const workspace = useAiAnalysis();
  const [draft, setDraft] = useState("");
  const id = useId();
  const msgs = useRef<HTMLDivElement>(null);
  const { ensureHistory } = workspace;
  useEffect(() => ensureHistory(), [ensureHistory]);
  const send = async (text: string, fromInput = false) => {
    if (workspace.busy || !text.trim()) return;
    // 너무 긴 질문은 보내지 않으니 입력을 비우지 않는다(안내는 workspace.send가 띄움).
    if (fromInput && [...text.trim()].length <= 1000) setDraft("");
    const result = await workspace.send(text, screenId, readPeriod?.());
    // 질문 글이 틀렸으면 고칠 수 있게 입력을 되돌린다.
    if (fromInput && result === "invalid") setDraft(text);
  };
  const last = workspace.turns.at(-1);
  // 시안 ask(): 보낼 때와 답이 왔을 때 가장 새 말로 내린다.
  useEffect(() => {
    const node = msgs.current?.lastElementChild as HTMLElement | null;
    node?.scrollIntoView?.({ block: "nearest" });
  }, [workspace.turns.length, last?.answer, last?.error, workspace.busy]);
  const step = Math.max(0, STEPS.indexOf(workspace.progress as (typeof STEPS)[number]));
  return (
    <div className="ai-conv" data-testid="analysis-conversation">
      <div className="ai-msgs" ref={msgs}>
        {workspace.turns.map((turn) => {
          const pending = !turn.answer && !turn.error;
          return (
            <Fragment key={turn.id}>
              <div className="ai-q" data-testid={`analysis-turn-${turn.id}`}>
                {turn.question}
              </div>
              {turn.answer ? (
                <AiAnalysisAnswer
                  answer={turn.answer}
                  onNavigate={onClose}
                  disabled={workspace.busy}
                  hiddenSuggestions={ANALYSIS_SUGGESTIONS}
                  onQuestion={(question) => {
                    void send(question);
                  }}
                />
              ) : turn.error ? (
                <div className="ai-a">
                  <div className="ai-one" role="alert">
                    {turn.error}
                  </div>
                </div>
              ) : (
                pending && (
                  <div className="ai-a">
                    <div className="ai-steps" aria-live="polite" data-testid="analysis-progress">
                      {STEPS.map((label, k) => (
                        <Fragment key={label}>
                          {k > 0 && " → "}
                          <span className={k === step ? "ai-on" : undefined}>{label}</span>
                        </Fragment>
                      ))}
                    </div>
                  </div>
                )
              )}
            </Fragment>
          );
        })}
      </div>
      <div className="ai-ex">
        {ANALYSIS_SUGGESTIONS.map((text, index) => (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
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
      <form
        className="ai-ask"
        onSubmit={(event) => {
          event.preventDefault();
          void send(draft, true);
        }}
      >
        <label htmlFor={id} className="ai-sr">
          질문
        </label>
        <input
          id={id}
          className="ls-in"
          data-testid="analysis-question"
          placeholder="예: 다음 달 철수하는 백엔드 개발자는 몇 명?"
          autoComplete="off"
          value={draft}
          aria-invalid={!!workspace.fieldError}
          aria-describedby={workspace.fieldError ? `${id}-error` : undefined}
          onChange={(event) => setDraft(event.target.value)}
        />
        <button
          type="submit"
          data-testid="analysis-send"
          className="ls-btn ls-btn-p"
          aria-label="보내기"
          disabled={workspace.busy}
        >
          {sendIcon}
          <span className="ai-hide-m">보내기</span>
        </button>
      </form>
      {workspace.fieldError && (
        <p className="ai-field-error" role="alert" id={`${id}-error`} data-testid="analysis-question-error">
          {workspace.fieldError}
        </p>
      )}
    </div>
  );
}

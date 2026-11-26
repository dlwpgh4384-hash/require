import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { newAiRequestId } from "../../shared/layout/aiRequestId";
import * as api from "./aiAnalysis";
import { AssignmentRequestError } from "../assignments/assignments";

interface Turn {
  id: string;
  question: string;
  answer?: api.AnalysisAnswer;
  error?: string;
}
/** ok=답 받음, error=처리 실패(답 자리에 안내), invalid=질문 글 오류(입력 그대로 둠), ignored=빈 질문·처리 중. */
export type SendResult = "ok" | "error" | "invalid" | "ignored";
interface Workspace {
  turns: Turn[];
  history: api.AnalysisHistoryItem[];
  historyLoading: boolean;
  historyError: string;
  busy: boolean;
  progress: string;
  fieldError: string;
  send: (
    question: string,
    screenId: string,
    period?: api.AnalysisPeriod,
  ) => Promise<SendResult>;
  reloadHistory: () => Promise<void>;
  ensureHistory: () => void;
  clear: () => void;
}
const Context = createContext<Workspace | null>(null);
export function AiAnalysisWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [history, setHistory] = useState<api.AnalysisHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState("");
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState("");
  const [fieldError, setFieldError] = useState("");
  const active = useRef<string | null>(null);
  const generation = useRef(0);
  const reloadHistory = useCallback(async () => {
    setHistoryLoading(true);
    setHistoryError("");
    try {
      setHistory(
        (await api.getCommonAiAnalysisQuestions()).items.slice(
          0,
          api.ANALYSIS_HISTORY_LIMIT,
        ),
      );
    } catch {
      setHistoryError("질문 기록을 불러오지 못했습니다.");
    } finally {
      setHistoryLoading(false);
    }
  }, []);
  // 질문 기록은 UI-26 화면이나 [AI에게 묻기] 창이 처음 열릴 때 한 번 읽는다(다른 화면만 열면 요청 없음).
  const historyRequested = useRef(false);
  const ensureHistory = useCallback(() => {
    if (historyRequested.current) return;
    historyRequested.current = true;
    void reloadHistory();
  }, [reloadHistory]);
  const clear = useCallback(() => {
    generation.current++;
    setTurns([]);
    setFieldError("");
  }, []);
  useEffect(() => {
    window.addEventListener("ai-analysis-tab-closed", clear);
    return () => window.removeEventListener("ai-analysis-tab-closed", clear);
  }, [clear]);
  useEffect(
    () => () => {
      active.current = null;
    },
    [],
  );
  const send = useCallback(
    async (question: string, screenId: string, period?: api.AnalysisPeriod) => {
      if (active.current) return "ignored";
      const trimmed = question.trim();
      // 시안 ask(): 빈 질문은 아무 말 없이 무시한다.
      if (!trimmed) return "ignored";
      if ([...trimmed].length > 1000) {
        setFieldError("질문은 1~1000자로 입력해 주세요.");
        return "invalid";
      }
      const id = newAiRequestId();
      const epoch = generation.current;
      active.current = id;
      setBusy(true);
      setFieldError("");
      setProgress("");
      setTurns((previous) => [...previous, { id, question: trimmed }]);
      let polling = false;
      const poll = async () => {
        if (polling || active.current !== id) return;
        polling = true;
        try {
          const value = await api.getCommonAiAnalysisProgress(id);
          if (active.current === id) setProgress(value.stage);
        } catch {
          /* The POST may not have registered its progress yet. */
        } finally {
          polling = false;
        }
      };
      const timer = window.setInterval(() => {
        void poll();
      }, 500);
      try {
        const answer = await api.postCommonAiAnalysisQuestion({
          requestId: id,
          question: trimmed,
          screenId,
          ...(period ? { period } : {}),
        });
        if (generation.current === epoch)
          setTurns((previous) =>
            previous.map((turn) =>
              turn.id === id ? { ...turn, answer } : turn,
            ),
          );
        await reloadHistory();
        return "ok";
      } catch (error) {
        if (generation.current === epoch) {
          if (
            error instanceof AssignmentRequestError &&
            error.errors.some((field) => field.field === "question")
          ) {
            setFieldError(error.message);
            setTurns((previous) => previous.filter((turn) => turn.id !== id));
            return "invalid";
          } else
            setTurns((previous) =>
              previous.map((turn) =>
                turn.id === id
                  ? {
                      ...turn,
                      error: "질문을 처리하지 못했습니다. 다시 질문해 주세요.",
                    }
                  : turn,
              ),
            );
        }
        return "error";
      } finally {
        window.clearInterval(timer);
        active.current = null;
        setBusy(false);
        setProgress("");
      }
    },
    [reloadHistory],
  );
  return (
    <Context.Provider
      value={{
        turns,
        history,
        historyLoading,
        historyError,
        busy,
        progress,
        fieldError,
        send,
        reloadHistory,
        ensureHistory,
        clear,
      }}
    >
      {children}
    </Context.Provider>
  );
}
export function useOptionalAiAnalysis() {
  return useContext(Context);
}
export function useAiAnalysis() {
  const value = useOptionalAiAnalysis();
  if (!value) throw new Error("AI analysis workspace is required");
  return value;
}

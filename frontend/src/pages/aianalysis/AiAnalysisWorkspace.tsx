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
  ) => Promise<boolean>;
  reloadHistory: () => Promise<void>;
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
      setHistory((await api.getCommonAiAnalysisQuestions()).items);
    } catch {
      setHistoryError("질문 기록을 불러오지 못했습니다.");
    } finally {
      setHistoryLoading(false);
    }
  }, []);
  useEffect(() => {
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
      if (active.current) return false;
      const trimmed = question.trim();
      if (!trimmed || [...trimmed].length > 1000) {
        setFieldError("질문은 1~1000자로 입력해 주세요.");
        return false;
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
        return true;
      } catch (error) {
        if (generation.current === epoch) {
          if (
            error instanceof AssignmentRequestError &&
            error.errors.some((field) => field.field === "question")
          ) {
            setFieldError(error.message);
            setTurns((previous) => previous.filter((turn) => turn.id !== id));
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
        return false;
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

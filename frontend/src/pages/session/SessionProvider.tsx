import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  SessionApiError,
  sessionTiming,
  type SessionApi,
  type SessionTiming,
} from "./sessionApi";

interface SessionContextValue {
  remainingSeconds: number | null;
  loading: boolean;
  extending: boolean;
  error: string;
  extend: () => Promise<void>;
  retry: () => Promise<void>;
  acceptTiming: (timing: SessionTiming) => void;
  expire: () => void;
}
const SessionContext = createContext<SessionContextValue | null>(null);
export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error("SessionProvider가 필요합니다.");
  return value;
}

/** UI-34 shell boundary. Unmounting the protected subtree discards local drafts, including dirty tabs. */
export function SessionProvider({
  api,
  initial,
  children,
  discardDrafts,
  onExpired,
}: {
  api: SessionApi;
  initial?: SessionTiming;
  children: ReactNode;
  discardDrafts: () => void;
  onExpired: () => void;
}) {
  const [sample, setSample] = useState<{
    timing: SessionTiming;
    receivedAt: number;
  } | null>(() =>
    initial ? { timing: initial, receivedAt: Date.now() } : null,
  );
  const latest = useRef(
    initial ? Date.parse(initial.serverRespondedAt) : -Infinity,
  );
  const [now, setNow] = useState(Date.now);
  const [loading, setLoading] = useState(!initial);
  const [extending, setExtending] = useState(false);
  const [error, setError] = useState("");
  const [expired, setExpired] = useState(false);
  const ended = useRef(false);
  const pending = useRef(false);
  const callbacks = useRef({ discardDrafts, onExpired });
  callbacks.current = { discardDrafts, onExpired };
  const expire = useCallback(() => {
    if (ended.current) return;
    ended.current = true;
    setExpired(true);
    setSample(null);
    callbacks.current.discardDrafts();
    callbacks.current.onExpired();
  }, []);
  const acceptTiming = useCallback(
    (timing: SessionTiming) => {
      const timestamp = Date.parse(timing.serverRespondedAt);
      if (ended.current || timestamp <= latest.current) return;
      latest.current = timestamp;
      const receivedAt = Date.now();
      setSample({ timing, receivedAt });
      setNow(receivedAt);
      setError("");
      if (timing.remainingSeconds === 0) expire();
    },
    [expire],
  );
  const handleError = useCallback(
    (failure: unknown) => {
      if (failure instanceof SessionApiError && failure.status === 401)
        expire();
      else
        setError(
          failure instanceof Error
            ? failure.message
            : "세션 시간을 확인하지 못했습니다.",
        );
    },
    [expire],
  );
  const retry = useCallback(async () => {
    if (ended.current || pending.current) return;
    pending.current = true;
    setLoading(true);
    setError("");
    try {
      acceptTiming(await api.remaining());
    } catch (failure) {
      handleError(failure);
    } finally {
      pending.current = false;
      setLoading(false);
    }
  }, [api, acceptTiming, handleError]);
  useEffect(() => {
    const timingReceived = (event: Event) => {
      const timing = sessionTiming((event as CustomEvent<unknown>).detail);
      if (timing) acceptTiming(timing);
    };
    window.addEventListener("account-session-timing", timingReceived);
    return () =>
      window.removeEventListener("account-session-timing", timingReceived);
  }, [acceptTiming]);
  useEffect(() => {
    if (initial) acceptTiming(initial);
  }, [initial, acceptTiming]);
  useEffect(() => {
    let active = true;
    if (!initial) {
      setLoading(true);
      api
        .remaining()
        .then((timing) => {
          if (active) acceptTiming(timing);
        })
        .catch((failure) => {
          if (active) handleError(failure);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }
    return () => {
      active = false;
    };
  }, [api, initial, acceptTiming, handleError]);
  useEffect(() => {
    if (!sample || expired) return;
    const tick = () => setNow(Date.now());
    const timer = window.setInterval(tick, 1000);
    window.addEventListener("focus", tick);
    document.addEventListener("visibilitychange", tick);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", tick);
      document.removeEventListener("visibilitychange", tick);
    };
  }, [sample, expired]);
  const remainingSeconds = sample
    ? Math.max(
        0,
        sample.timing.remainingSeconds -
          Math.floor(Math.max(0, now - sample.receivedAt) / 1000),
      )
    : null;
  useEffect(() => {
    if (remainingSeconds === 0) expire();
  }, [remainingSeconds, expire]);
  const extend = useCallback(async () => {
    if (ended.current || pending.current) return;
    pending.current = true;
    setExtending(true);
    setError("");
    try {
      acceptTiming(await api.extend());
    } catch (failure) {
      handleError(failure);
    } finally {
      pending.current = false;
      setExtending(false);
    }
  }, [api, acceptTiming, handleError]);
  return (
    <SessionContext.Provider
      value={{
        remainingSeconds,
        loading,
        extending,
        error,
        extend,
        retry,
        acceptTiming,
        expire,
      }}
    >
      {expired ? (
        <p role="status" data-testid="session-expired">
          세션이 만료되었습니다. 다시 로그인해 주세요.
        </p>
      ) : loading && !sample ? (
        <p role="status" data-testid="session-loading">
          세션 시간을 확인하고 있습니다.
        </p>
      ) : !sample ? (
        <div data-testid="session-error">
          <p role="alert">{error}</p>
          <button
            type="button"
            data-testid="session-retry"
            onClick={() => void retry()}
          >
            다시 시도
          </button>
        </div>
      ) : (
        children
      )}
    </SessionContext.Provider>
  );
}

export function SessionTimer() {
  const { remainingSeconds, extending, error, extend } = useSession();
  const time =
    remainingSeconds === null
      ? "—"
      : `${String(Math.floor(remainingSeconds / 60)).padStart(2, "0")}:${String(remainingSeconds % 60).padStart(2, "0")}`;
  return (
    <div className="hd-sess" data-screen-id="UI-34" data-testid="session-left">
      자동 로그아웃까지 <b>{time}</b>
      <button
        type="button"
        className="hd-sess-ext"
        data-testid="session-extend"
        disabled={extending || remainingSeconds === 0}
        onClick={() => void extend()}
      >
        {extending ? "연장 중…" : "연장"}
      </button>
      {error && (
        <p role="alert" data-testid="session-message">
          {error}
        </p>
      )}
    </div>
  );
}

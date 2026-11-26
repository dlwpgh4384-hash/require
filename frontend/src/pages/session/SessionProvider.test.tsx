import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { useState } from "react";
import { SessionProvider, SessionTimer, useSession } from "./SessionProvider";
import {
  SessionApiError,
  type SessionApi,
  type SessionTiming,
} from "./sessionApi";

const started = "2026-01-02T03:04:05Z";
const initial: SessionTiming = {
  remainingSeconds: 1800,
  sessionMinutes: 30,
  serverRespondedAt: started,
};
let api: SessionApi;
const discardDrafts = vi.fn();
const onExpired = vi.fn();
beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(started));
  vi.clearAllMocks();
  api = { remaining: vi.fn(), extend: vi.fn() };
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});
function Draft() {
  const [text, setText] = useState("");
  const { acceptTiming, expire } = useSession();
  return (
    <>
      <SessionTimer />
      <label>
        미저장 입력
        <input
          aria-label="미저장 입력"
          value={text}
          onChange={(event) => setText(event.target.value)}
          data-testid="session-draft"
        />
      </label>
      <button data-testid="local-action" onClick={() => setText(text + "a")}>
        로컬 동작
      </button>
      <button
        data-testid="server-response"
        onClick={() =>
          acceptTiming({
            ...initial,
            serverRespondedAt: new Date(Date.now()).toISOString(),
          })
        }
      >
        사용자 서버 응답
      </button>
      <button data-testid="old-response" onClick={() => acceptTiming(initial)}>
        늦은 응답
      </button>
      <button data-testid="unauthorized-response" onClick={expire}>
        서버 401
      </button>
    </>
  );
}
function show(timing: SessionTiming | undefined = initial) {
  return render(
    <SessionProvider
      api={api}
      initial={timing}
      discardDrafts={discardDrafts}
      onExpired={onExpired}
    >
      <Draft />
    </SessionProvider>,
  );
}
async function clickExtend() {
  await act(async () => {
    fireEvent.click(screen.getByTestId("session-extend"));
  });
}

describe("UI-34 server-owned inactivity", () => {
  it("renders countdown and extension in existing shell classes", () => {
    show();
    expect(screen.getByTestId("session-left")).toHaveTextContent(
      "자동 로그아웃까지 30:00",
    );
    expect(screen.getByTestId("session-left")).toHaveClass("hd-sess");
    expect(screen.getByTestId("session-left")).toHaveAttribute(
      "data-screen-id",
      "UI-34",
    );
    expect(screen.getByTestId("session-extend")).toHaveClass("hd-sess-ext");
  });
  it("clicks, typing and mouse movement never restart time or call the API", () => {
    show();
    act(() => vi.advanceTimersByTime(60000));
    fireEvent.change(screen.getByLabelText("미저장 입력"), {
      target: { value: "초안" },
    });
    fireEvent.click(screen.getByTestId("local-action"));
    fireEvent.mouseMove(document.body);
    fireEvent.keyDown(document.body, { key: "a" });
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:00");
    expect(api.remaining).not.toHaveBeenCalled();
    expect(api.extend).not.toHaveBeenCalled();
  });
  it("starts one initial read and never polls session or notifications", async () => {
    vi.mocked(api.remaining).mockResolvedValue(initial);
    await act(async () => {
      render(
        <SessionProvider
          api={api}
          discardDrafts={discardDrafts}
          onExpired={onExpired}
        >
          <Draft />
        </SessionProvider>,
      );
    });
    act(() => vi.advanceTimersByTime(600000));
    expect(api.remaining).toHaveBeenCalledTimes(1);
    expect(api.extend).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("does not optimistically reset during pending extension", async () => {
    let resolve!: (value: SessionTiming) => void;
    vi.mocked(api.extend).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    show();
    act(() => vi.advanceTimersByTime(60000));
    await clickExtend();
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:00");
    expect(screen.getByTestId("session-extend")).toBeDisabled();
    fireEvent.click(screen.getByTestId("session-extend"));
    expect(api.extend).toHaveBeenCalledTimes(1);
    await act(async () =>
      resolve({
        ...initial,
        serverRespondedAt: new Date(Date.now()).toISOString(),
      }),
    );
    expect(screen.getByTestId("session-left")).toHaveTextContent("30:00");
  });
  it("uses returned SESSION_MIN instead of a thirty-minute constant", async () => {
    show();
    act(() => vi.advanceTimersByTime(1000));
    vi.mocked(api.extend).mockResolvedValue({
      remainingSeconds: 720,
      sessionMinutes: 12,
      serverRespondedAt: new Date(Date.now()).toISOString(),
    });
    await clickExtend();
    expect(screen.getByTestId("session-left")).toHaveTextContent("12:00");
  });
  it("a normal server response restarts time but a stale response cannot", () => {
    show();
    act(() => vi.advanceTimersByTime(60000));
    fireEvent.click(screen.getByTestId("server-response"));
    expect(screen.getByTestId("session-left")).toHaveTextContent("30:00");
    act(() => vi.advanceTimersByTime(10000));
    fireEvent.click(screen.getByTestId("old-response"));
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:50");
  });
  it("background tab resumption accounts for elapsed wall time without a request", () => {
    show();
    vi.setSystemTime(new Date(Date.parse(started) + 600000));
    fireEvent(window, new Event("focus"));
    expect(screen.getByTestId("session-left")).toHaveTextContent("20:00");
    expect(api.remaining).not.toHaveBeenCalled();
  });
  it("accepts bootstrap timing events without another fetch wrapper or request", () => {
    show();
    act(() => vi.advanceTimersByTime(60000));
    act(() => {
      window.dispatchEvent(
        new CustomEvent("account-session-timing", {
          detail: {
            ...initial,
            serverRespondedAt: new Date(Date.now()).toISOString(),
          },
        }),
      );
    });
    expect(screen.getByTestId("session-left")).toHaveTextContent("30:00");
    expect(api.remaining).not.toHaveBeenCalled();
    expect(api.extend).not.toHaveBeenCalled();
  });
  it("ignores invalid and stale bootstrap timing events", () => {
    show();
    act(() => vi.advanceTimersByTime(60000));
    act(() => {
      window.dispatchEvent(
        new CustomEvent("account-session-timing", { detail: initial }),
      );
      window.dispatchEvent(
        new CustomEvent("account-session-timing", {
          detail: {
            ...initial,
            remainingSeconds: -1,
            serverRespondedAt: new Date(Date.now()).toISOString(),
          },
        }),
      );
    });
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:00");
    expect(onExpired).not.toHaveBeenCalled();
  });
  it("uses newer owner metadata without remounting the protected draft", () => {
    const view = show();
    fireEvent.change(screen.getByLabelText("미저장 입력"), {
      target: { value: "유지" },
    });
    act(() => vi.advanceTimersByTime(60000));
    view.rerender(
      <SessionProvider
        api={api}
        initial={{
          ...initial,
          serverRespondedAt: new Date(Date.now()).toISOString(),
        }}
        discardDrafts={discardDrafts}
        onExpired={onExpired}
      >
        <Draft />
      </SessionProvider>,
    );
    expect(screen.getByTestId("session-left")).toHaveTextContent("30:00");
    expect(screen.getByLabelText("미저장 입력")).toHaveValue("유지");
    expect(api.remaining).not.toHaveBeenCalled();
  });
  it("expiration removes dirty inputs and callbacks run once without a warning dialog", () => {
    show({ ...initial, remainingSeconds: 2 });
    fireEvent.change(screen.getByLabelText("미저장 입력"), {
      target: { value: "저장되지 않은 값" },
    });
    act(() => vi.advanceTimersByTime(1000));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(discardDrafts).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1000));
    expect(screen.queryByLabelText("미저장 입력")).not.toBeInTheDocument();
    expect(screen.getByTestId("session-expired")).toBeInTheDocument();
    expect(discardDrafts).toHaveBeenCalledTimes(1);
    expect(onExpired).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(5000));
    expect(onExpired).toHaveBeenCalledTimes(1);
  });
  it("401 from extension discards input immediately", async () => {
    show();
    vi.mocked(api.extend).mockRejectedValue(
      new SessionApiError(401, "다시 로그인해 주세요."),
    );
    await clickExtend();
    expect(discardDrafts).toHaveBeenCalledOnce();
    expect(onExpired).toHaveBeenCalledOnce();
    expect(screen.queryByTestId("session-draft")).not.toBeInTheDocument();
  });
  it("401 from another business request uses the same expiration boundary", () => {
    show();
    fireEvent.click(screen.getByTestId("unauthorized-response"));
    expect(onExpired).toHaveBeenCalledOnce();
    expect(screen.queryByTestId("session-draft")).not.toBeInTheDocument();
  });
  it("403 preserves input and countdown and shows the original Korean error", async () => {
    show();
    act(() => vi.advanceTimersByTime(30000));
    fireEvent.change(screen.getByLabelText("미저장 입력"), {
      target: { value: "유지할 값" },
    });
    vi.mocked(api.extend).mockRejectedValue(
      new SessionApiError(403, "요청 토큰을 확인해 주세요."),
    );
    await clickExtend();
    expect(screen.getByTestId("session-message")).toHaveTextContent(
      "요청 토큰을 확인해 주세요.",
    );
    expect(screen.getByLabelText("미저장 입력")).toHaveValue("유지할 값");
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:30");
    expect(discardDrafts).not.toHaveBeenCalled();
  });
  it("transport error never increases remaining time", async () => {
    show();
    act(() => vi.advanceTimersByTime(30000));
    vi.mocked(api.extend).mockRejectedValue(
      new Error("서버에 연결하지 못했습니다."),
    );
    await clickExtend();
    expect(screen.getByTestId("session-message")).toHaveTextContent(
      "서버에 연결하지 못했습니다.",
    );
    expect(screen.getByTestId("session-left")).toHaveTextContent("29:30");
  });
  it("loading has no fake countdown or editable protected content", () => {
    vi.mocked(api.remaining).mockReturnValue(new Promise(() => {}));
    render(
      <SessionProvider
        api={api}
        discardDrafts={discardDrafts}
        onExpired={onExpired}
      >
        <Draft />
      </SessionProvider>,
    );
    expect(screen.getByTestId("session-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("session-draft")).not.toBeInTheDocument();
  });
  it("failed initial read offers a user-triggered retry", async () => {
    vi.mocked(api.remaining)
      .mockRejectedValueOnce(new Error("연결을 확인해 주세요."))
      .mockResolvedValueOnce(initial);
    await act(async () => {
      render(
        <SessionProvider
          api={api}
          discardDrafts={discardDrafts}
          onExpired={onExpired}
        >
          <Draft />
        </SessionProvider>,
      );
    });
    expect(screen.getByRole("alert")).toHaveTextContent(
      "연결을 확인해 주세요.",
    );
    await act(async () => {
      fireEvent.click(screen.getByTestId("session-retry"));
    });
    expect(screen.getByTestId("session-left")).toHaveTextContent("30:00");
    expect(api.remaining).toHaveBeenCalledTimes(2);
  });
  it("a delayed extension response never remounts drafts after expiration", async () => {
    let resolve!: (value: SessionTiming) => void;
    vi.mocked(api.extend).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    show({ ...initial, remainingSeconds: 1 });
    await clickExtend();
    act(() => vi.advanceTimersByTime(1000));
    await act(async () =>
      resolve({
        ...initial,
        serverRespondedAt: new Date(Date.now()).toISOString(),
      }),
    );
    expect(screen.getByTestId("session-expired")).toBeInTheDocument();
    expect(screen.queryByTestId("session-draft")).not.toBeInTheDocument();
  });
});

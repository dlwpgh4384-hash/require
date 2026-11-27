import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  MfaApiError,
  type MfaApi,
  type MfaDelivery,
  type MfaLogin,
  type MfaResponse,
} from "./mfaApi";
import "./mfa.css";
import "../commonops/accountAccess.css";

export interface MfaPageProps {
  api: MfaApi;
  initial?: MfaDelivery | null;
  deliveryError?: string | null;
  onCompleted: (response: MfaResponse<MfaLogin>) => void;
  // The session owner discards pending auth (logout) before returning to UI-22.
  onRestart: (message?: string) => void | Promise<void>;
}

// UI-39: standalone administrator challenge, never a business menu.
export function MfaPage({
  api,
  initial,
  deliveryError,
  onCompleted,
  onRestart,
}: MfaPageProps) {
  const [delivery, setDelivery] = useState(initial);
  const [code, setCode] = useState("");
  const [message, setMessage] = useState(deliveryError ?? "");
  const [fieldError, setFieldError] = useState("");
  const [failedDelivery, setFailedDelivery] = useState(Boolean(deliveryError));
  const [busy, setBusy] = useState(false);
  const [restartRequired, setRestartRequired] = useState(
    !initial && !deliveryError,
  );
  const [now, setNow] = useState(() => Date.now());
  const inFlight = useRef(false);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const seconds = (date?: string) =>
    date ? Math.max(0, Math.ceil((Date.parse(date) - now) / 1000)) : 0;
  const remaining = seconds(delivery?.expiresAt);
  const wait = seconds(delivery?.resendAvailableAt);

  async function run(action: "verify" | "resend") {
    if (inFlight.current || restartRequired) return;
    if (action === "verify" && (failedDelivery || remaining === 0)) return;
    if (action === "resend" && wait > 0) return;
    if (action === "verify" && !/^[0-9]{6}$/.test(code)) {
      setFieldError("인증번호 6자리를 넣어 주세요.");
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setMessage("");
    setFieldError("");
    try {
      if (action === "verify") {
        onCompleted(await api.verify(code));
      } else {
        const response = await api.resend();
        setDelivery(response.data);
        setCode("");
        setFailedDelivery(false);
        setNow(Date.now());
        setMessage("인증번호를 다시 보냈습니다.");
      }
    } catch (error) {
      const text =
        error instanceof Error
          ? error.message
          : "서버에 연결하지 못했습니다. 다시 시도해 주세요.";
      if (error instanceof MfaApiError) {
        setFieldError(
          error.errors
            .filter((item) => item.field === "code")
            .map((item) => item.message)
            .join(" "),
        );
        if (error.status === 401 || error.meta.nextScreenId === "UI-22") {
          setRestartRequired(true);
          await onRestart(text);
        }
        if (
          text ===
          "인증번호를 보내지 못했습니다. 잠시 후 [다시 보내기]를 눌러 주세요."
        )
          setFailedDelivery(true);
      }
      setMessage(text);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    void run("verify");
  }
  // 시안 renderMfa: 로고 카드, 「○○로 인증번호 6자리를 보냈습니다…」, 칸 옆 남은 시간, [확인], 아래 [다시 보내기]·[처음으로], 안내.
  return (
    <main
      className="account-access mfa-page"
      data-testid="mfa-page"
      data-screen-id="UI-39"
    >
      <section className="account-card mfa-card" aria-labelledby="mfa-title">
        <div className="account-brand">
          <span className="account-logo" aria-hidden="true">
            SI
          </span>
          <span>프로젝트·원가 관리</span>
        </div>
        <h1 id="mfa-title">인증번호 확인</h1>
        <p className="account-sub">
          {delivery ? (
            <>
              <strong>{delivery.maskedEmail}</strong>로 인증번호 6자리를
              보냈습니다.{" "}
            </>
          ) : null}
          시스템 관리자 계정은 2단계 인증이 필요합니다.
        </p>
        {!initial && !deliveryError && (
          <p role="alert" className="account-error">
            진행 중인 인증이 없습니다. 처음부터 다시 로그인해 주세요.
          </p>
        )}
        <form onSubmit={submit} className="account-form" noValidate>
          <div className="account-field">
            <label htmlFor="mfa-code">인증번호</label>
            <div className="mfa-code-row">
              <input
                id="mfa-code"
                data-testid="mfa-code-input"
                value={code}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                aria-invalid={Boolean(fieldError)}
                aria-describedby="mfa-code-error"
                disabled={busy || failedDelivery || restartRequired}
                onChange={(event) => {
                  setCode(event.target.value);
                  setFieldError("");
                }}
              />
              {delivery && (
                <span className="account-sub mfa-left" data-testid="mfa-remaining">
                  {remaining > 0
                    ? `남은 시간 ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}`
                    : "시간이 지났습니다"}
                </span>
              )}
            </div>
            <span
              id="mfa-code-error"
              className="account-error"
              data-testid="mfa-code-error"
            >
              {fieldError}
            </span>
            {delivery && remaining === 0 && !failedDelivery && (
              <span className="account-error">
                인증번호 시간이 지났습니다. [다시 보내기]를 눌러 주세요.
              </span>
            )}
            {message && (
              <span
                role="status"
                className="mfa-message"
                data-testid="mfa-message"
              >
                {message}
              </span>
            )}
          </div>
          <button
            type="submit"
            className="account-primary"
            data-testid="mfa-verify-button"
            disabled={
              busy || failedDelivery || restartRequired || remaining === 0
            }
          >
            확인
          </button>
          <div className="account-row account-row-between">
            <button
              type="button"
              className="mfa-small"
              data-testid="mfa-resend-button"
              disabled={busy || restartRequired || wait > 0}
              onClick={() => void run("resend")}
            >
              다시 보내기{wait > 0 ? ` (${wait}초 뒤)` : ""}
            </button>
            <button
              type="button"
              className="account-text-button"
              data-testid="mfa-restart-button"
              disabled={busy}
              onClick={() => void onRestart()}
            >
              처음으로
            </button>
          </div>
          <p className="account-cap">
            5분 안에 넣어 주세요. 다시 보내면 이전 번호는 쓸 수 없습니다. 5번
            틀리면 처음부터 다시 로그인합니다.
          </p>
        </form>
      </section>
    </main>
  );
}

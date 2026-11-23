import { useEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import "./feedback.css";

// 열린 창 쌓임: Esc·Tab은 맨 위 창만 받는다(창 위에 확인 창이 뜨면 아래 창은 키를 받지 않음).
const openDialogs: object[] = [];

/** FR-0106 공통 창: Esc로 닫고, 포커스를 창 안에 가두고, 닫으면 부른 버튼으로 포커스를 돌려준다. */
export function Dialog({
  title,
  children,
  onClose,
  testId = "app-dialog",
  role = "dialog",
  closable = false,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  testId?: string;
  role?: "dialog" | "alertdialog";
  /** 시안 모달 머리의 [×] 닫기 단추를 보인다. */
  closable?: boolean;
}) {
  const panel = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const me = {};
    openDialogs.push(me);
    const caller = document.activeElement as HTMLElement | null;
    const first =
      // 여러 줄 입력칸(사유 등)도 첫 입력칸으로 본다(00-1 코드 리뷰 2026-11-23).
      panel.current?.querySelector<HTMLElement>(
        "input:not(:disabled),select:not(:disabled),textarea:not(:disabled)",
      ) ??
      panel.current?.querySelector<HTMLElement>("button,a[href],[tabindex='0']");
    (first ?? panel.current)?.focus();
    const keydown = (event: KeyboardEvent) => {
      if (openDialogs[openDialogs.length - 1] !== me) return;
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopImmediatePropagation();
        close.current();
      }
      if (event.key === "Tab") {
        const controls = [
          ...(panel.current?.querySelectorAll<HTMLElement>(
            "button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex='0']",
          ) ?? []),
        ];
        const index = controls.indexOf(document.activeElement as HTMLElement);
        if (!controls.length) {
          event.preventDefault();
          panel.current?.focus();
        } else if (event.shiftKey && index <= 0) {
          event.preventDefault();
          controls[controls.length - 1].focus();
        } else if (
          !event.shiftKey &&
          (index < 0 || index === controls.length - 1)
        ) {
          event.preventDefault();
          controls[0].focus();
        }
      }
    };
    window.addEventListener("keydown", keydown, true);
    return () => {
      window.removeEventListener("keydown", keydown, true);
      const at = openDialogs.indexOf(me);
      if (at >= 0) openDialogs.splice(at, 1);
      caller?.focus();
    };
  }, []);
  return (
    <div className="app-scrim" data-testid={`${testId}-scrim`}>
      <div
        ref={panel}
        role={role}
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        data-testid={testId}
        className="app-dialog"
      >
        {closable ? (
          <div className="app-dialog-head">
            <h2>{title}</h2>
            <button
              type="button"
              className="app-dialog-close"
              aria-label="닫기"
              data-testid={`${testId}-close`}
              onClick={() => close.current()}
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                aria-hidden="true"
              >
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
        ) : (
          <h2>{title}</h2>
        )}
        {children}
      </div>
    </div>
  );
}

function ConfirmButtons({
  onCancel,
  onProceed,
  proceedLabel,
  destructive,
}: {
  onCancel: () => void;
  onProceed: () => void;
  proceedLabel: string;
  destructive: boolean;
}) {
  const called = useRef(false);
  return (
    <div className="app-actions" data-testid="confirmation-actions">
      <button
        type="button"
        data-testid="confirmation-cancel"
        onClick={onCancel}
      >
        취소
      </button>
      <button
        type="button"
        data-testid="confirmation-proceed"
        className={destructive ? "app-danger" : "primary-button"}
        onClick={() => {
          if (!called.current) {
            called.current = true;
            onProceed();
          }
        }}
      >
        {proceedLabel}
      </button>
    </div>
  );
}

/** 위험한 작업 확인 창: 질문형 제목, 일어날 일 설명, [취소]와 실행 버튼(위험하면 빨강). */
export function ConfirmDialog({
  title,
  description,
  onCancel,
  onProceed,
  proceedLabel = "확인",
  destructive = false,
  testId = "confirm-dialog",
}: {
  title: string;
  description: string;
  onCancel: () => void;
  onProceed: () => void;
  proceedLabel?: string;
  destructive?: boolean;
  testId?: string;
}) {
  return (
    // 시안의 모든 작은 창(openModal) 머리에는 [×] 닫기가 있다 — 확인 창도 같다(사용자 결정 2026-11-18).
    <Dialog title={title} onClose={onCancel} testId={testId} role="alertdialog" closable>
      <p>{description}</p>
      <ConfirmButtons
        onCancel={onCancel}
        onProceed={onProceed}
        proceedLabel={proceedLabel}
        destructive={destructive}
      />
    </Dialog>
  );
}

/**
 * 이미 열린 창 위에 따로 띄우는 확인(예: 저장하지 않고 닫기). 시안 confirmBox처럼 원래 창 위에 새 작은 창이 뜬다.
 * (전에는 원래 창 안 아래쪽에 끼워 넣어 창이 깨져 보였다 — 2026-11-19 사용자 발견)
 */
export function InlineConfirmation({
  title,
  description,
  onCancel,
  onProceed,
  proceedLabel = "나가기",
  destructive = false,
  testId = "inline-confirmation",
}: {
  title: string;
  description: string;
  onCancel: () => void;
  onProceed: () => void;
  proceedLabel?: string;
  destructive?: boolean;
  testId?: string;
}) {
  return createPortal(
    <div className="app-confirm-layer">
      <ConfirmDialog
        title={title}
        description={description}
        onCancel={onCancel}
        onProceed={onProceed}
        proceedLabel={proceedLabel}
        destructive={destructive}
        testId={testId}
      />
    </div>,
    document.body,
  );
}

/** 완료 알림: 초록색으로 3초 보여 준다. */
/** 시안 toast(msg, kind): ok=초록 글·체크 아이콘, info=보통 글·파란 i, warn=주황 글·i. */
export type ToastKind = "ok" | "info" | "warn";
export function Toast({
  message,
  onDismiss,
  testId = "app-toast",
  kind = "ok",
}: {
  message: string;
  onDismiss: () => void;
  testId?: string;
  kind?: ToastKind;
}) {
  const dismiss = useRef(onDismiss);
  dismiss.current = onDismiss;
  useEffect(() => {
    const timer = window.setTimeout(() => dismiss.current(), 3000);
    return () => window.clearTimeout(timer);
  }, [message]);
  return (
    <div role="status" data-testid={testId} className={`app-toast app-toast-${kind}`}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d={kind === "ok" ? "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 12l3 3 5-6" : "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01"} />
      </svg>
      <span>{message}</span>
    </div>
  );
}

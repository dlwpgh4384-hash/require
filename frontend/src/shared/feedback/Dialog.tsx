import { useEffect, useRef, type ReactNode } from "react";
import "./feedback.css";

/** FR-0106 공통 창: Esc로 닫고, 포커스를 창 안에 가두고, 닫으면 부른 버튼으로 포커스를 돌려준다. */
export function Dialog({
  title,
  children,
  onClose,
  testId = "app-dialog",
  role = "dialog",
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  testId?: string;
  role?: "dialog" | "alertdialog";
}) {
  const panel = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const caller = document.activeElement as HTMLElement | null;
    const first = panel.current?.querySelector<HTMLElement>(
      "input,select,button,[tabindex='0']",
    );
    (first ?? panel.current)?.focus();
    const keydown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopImmediatePropagation();
        close.current();
      }
      if (event.key === "Tab") {
        const controls = [
          ...(panel.current?.querySelectorAll<HTMLElement>(
            "button:not(:disabled),input:not(:disabled),select:not(:disabled),[tabindex='0']",
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
      caller?.focus();
    };
  }, []);
  return (
    <div className="app-scrim">
      <div
        ref={panel}
        role={role}
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        data-testid={testId}
        className="app-dialog"
      >
        <h2>{title}</h2>
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
    <div className="app-actions">
      <button type="button" data-testid="confirmation-cancel" onClick={onCancel}>
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
    <Dialog title={title} onClose={onCancel} testId={testId} role="alertdialog">
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

/** 이미 열린 창 안에서 묻는 확인(예: 미저장 상태로 닫기). */
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
  const section = useRef<HTMLElement>(null);
  useEffect(() => {
    section.current?.querySelector<HTMLButtonElement>("button")?.focus();
  }, []);
  return (
    <section ref={section} role="alertdialog" aria-label={title} data-testid={testId}>
      <h3>{title}</h3>
      <p>{description}</p>
      <ConfirmButtons
        onCancel={onCancel}
        onProceed={onProceed}
        proceedLabel={proceedLabel}
        destructive={destructive}
      />
    </section>
  );
}

/** 완료 알림: 초록색으로 3초 보여 준다. */
export function Toast({
  message,
  onDismiss,
  testId = "app-toast",
}: {
  message: string;
  onDismiss: () => void;
  testId?: string;
}) {
  const dismiss = useRef(onDismiss);
  dismiss.current = onDismiss;
  useEffect(() => {
    const timer = window.setTimeout(() => dismiss.current(), 3000);
    return () => window.clearTimeout(timer);
  }, [message]);
  return (
    <p role="status" data-testid={testId} className="app-toast">
      {message}
    </p>
  );
}

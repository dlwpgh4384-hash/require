import { useEffect, useRef, type ReactNode } from "react";
import "./userPreferences.css";

export function UserPreferenceDialog({
  title,
  children,
  onClose,
  testId = "preference-dialog",
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  testId?: string;
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
    <div className="preference-scrim">
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        data-testid={testId}
        className="preference-dialog"
      >
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function UserPreferenceConfirmation({
  title,
  description,
  onCancel,
  onProceed,
  destructive = false,
}: {
  title: string;
  description: string;
  onCancel: () => void;
  onProceed: () => void;
  destructive?: boolean;
}) {
  const called = useRef(false);
  const section = useRef<HTMLElement>(null);
  useEffect(() => {
    section.current?.querySelector<HTMLButtonElement>("button")?.focus();
  }, []);
  return (
    <section
      ref={section}
      role="alertdialog"
      aria-label={title}
      data-testid="preference-confirmation"
    >
      <h3>{title}</h3>
      <p>{description}</p>
      <div className="preference-actions">
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
          className={destructive ? "preference-danger" : "primary-button"}
          onClick={() => {
            if (!called.current) {
              called.current = true;
              onProceed();
            }
          }}
        >
          {destructive ? "실행" : "나가기"}
        </button>
      </div>
    </section>
  );
}

export function UserPreferenceToast({
  message,
  onDismiss,
}: {
  message: string;
  onDismiss: () => void;
}) {
  const dismiss = useRef(onDismiss);
  dismiss.current = onDismiss;
  useEffect(() => {
    const timer = window.setTimeout(() => dismiss.current(), 3000);
    return () => window.clearTimeout(timer);
  }, [message]);
  return (
    <p
      role="status"
      data-testid="preference-success"
      className="preference-toast"
    >
      {message}
    </p>
  );
}

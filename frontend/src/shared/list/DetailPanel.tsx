import { useEffect, useRef, type ReactNode } from "react";
import "./list.css";

export interface DetailTab {
  key: string;
  label: string;
  content: ReactNode;
}

/** SFR-010-06 행 상세 패널: 오른쪽에서 열리는 패널. 제목·작은 제목·버튼·탭, Esc와 바깥 클릭으로 닫는다. */
export function DetailPanel({
  title,
  sub,
  actions,
  tabs,
  activeTab,
  onTab,
  children,
  onClose,
  testId = "detail-panel",
}: {
  title: string;
  sub?: string;
  actions?: ReactNode;
  tabs?: DetailTab[];
  activeTab?: string;
  onTab?: (key: string) => void;
  children?: ReactNode;
  onClose: () => void;
  testId?: string;
}) {
  const panel = useRef<HTMLElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const caller = document.activeElement as HTMLElement | null;
    panel.current?.focus();
    const keydown = (event: KeyboardEvent) => {
      // 패널 위에 다른 창(확인 창 등)이 열려 있으면 그 창이 Esc를 받는다.
      if (event.key !== "Escape" || event.defaultPrevented) return;
      if (document.querySelector(".app-scrim")) return;
      event.preventDefault();
      close.current();
    };
    window.addEventListener("keydown", keydown);
    return () => {
      window.removeEventListener("keydown", keydown);
      caller?.focus?.();
    };
  }, []);
  const current = tabs?.find((tab) => tab.key === activeTab) ?? tabs?.[0];
  return (
    <>
      <div className="ls-scrim" onClick={onClose} />
      <section
        ref={panel}
        className="ls-drawer"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        data-testid={testId}
      >
        <div className="ls-dh">
          <div>
            <div className="ls-dt">{title}</div>
            {sub && <div className="ls-cap">{sub}</div>}
          </div>
          <div className="ls-da">
            {actions}
            <button
              type="button"
              className="ls-ibtn"
              aria-label="닫기"
              data-testid={`${testId}-close`}
              onClick={onClose}
            >
              ✕
            </button>
          </div>
        </div>
        {tabs && (
          <div className="ls-tabs" role="tablist">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={tab.key === current?.key}
                className={`ls-tb${tab.key === current?.key ? " ls-on" : ""}`}
                data-testid={`${testId}-tab-${tab.key}`}
                onClick={() => onTab?.(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        )}
        <div className="ls-db">{current ? current.content : children}</div>
      </section>
    </>
  );
}

/** 상세 패널 안의 「이름 : 값」 목록. */
export function KeyValues({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="ls-kv">
      {items.map(([label, value]) => (
        <div key={label} className="ls-kv-row">
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** 상세 패널 안내 띠. kind는 warn(잠김)·lock(중지)·info. */
export function Banner({
  kind,
  children,
  action,
}: {
  kind: "warn" | "lock" | "info" | "bad";
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className={`ls-banner ls-banner-${kind}`} role="status">
      <span>{children}</span>
      {action}
    </div>
  );
}

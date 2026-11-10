import { useEffect, useRef, useState, type ReactNode } from "react";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";
import { DisabledTools } from "./DisabledTools";
import "./shell.css";

export interface AppLayoutProps {
  sidebar?: ReactNode;
  title?: ReactNode;
  breadcrumb?: string[];
  screenId?: string;
  navigationKey?: string;
  actions?: ReactNode;
  children?: ReactNode;
}

export function AppLayout({
  sidebar,
  title,
  breadcrumb,
  screenId,
  navigationKey,
  actions,
  children,
}: AppLayoutProps) {
  const mainRef = useRef<HTMLElement>(null);
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 767px)").matches,
  );
  const [expanded, setExpanded] = useState(
    () => !window.matchMedia("(max-width: 767px)").matches,
  );
  useEffect(() => {
    const media = window.matchMedia("(max-width: 767px)");
    const change = () => {
      setMobile(media.matches);
      setExpanded(!media.matches);
    };
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  useEffect(() => {
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape" && mobile) setExpanded(false);
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [mobile]);
  useEffect(() => {
    if (mainRef.current) mainRef.current.scrollTop = 0;
    if (mobile) setExpanded(false);
  }, [navigationKey, mobile]);
  const closeNavigation = () => {
    if (mobile) {
      setExpanded(false);
      document
        .querySelector<HTMLButtonElement>('[data-testid="navigation-toggle"]')
        ?.focus();
    }
  };
  return (
    <div data-testid="app-layout" className="app-layout">
      <a className="skip-link" href="#screen-content">
        본문 바로가기
      </a>
      <Header
        expanded={expanded}
        onToggle={() => setExpanded((value) => !value)}
        title={title}
        actions={actions}
      />
      <div className="app-body">
        {mobile && expanded && (
          <button
            data-testid="navigation-scrim"
            className="navigation-scrim"
            type="button"
            aria-label="메뉴 닫기"
            onClick={() => setExpanded(false)}
          />
        )}
        <Sidebar
          expanded={expanded}
          onToggle={() => setExpanded((value) => !value)}
          onNavigate={closeNavigation}
        >
          {sidebar}
        </Sidebar>
        <main
          data-testid="app-main"
          id="screen-content"
          tabIndex={-1}
          ref={mainRef}
        >
          <div className="screen-tabs">
            <DisabledTools names={["화면 탭"]} />
          </div>
          <section className="page">
            <div className="page-title">
              <div className="title-stack">
                {breadcrumb && breadcrumb.length > 0 && (
                  <nav aria-label="현재 화면 경로" className="breadcrumb">
                    <ol>
                      {breadcrumb.map((label, index) => (
                        <li key={index}>{label}</li>
                      ))}
                    </ol>
                  </nav>
                )}
                <div className="title-row">
                  <h1>{title ?? "메뉴 탐색"}</h1>
                  {screenId && <span className="screen-id">{screenId}</span>}
                </div>
              </div>
              <DisabledTools names={["상단 도구줄"]} />
            </div>
            <div className="page-content">{children}</div>
          </section>
        </main>
      </div>
    </div>
  );
}

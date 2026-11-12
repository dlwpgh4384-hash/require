import { useEffect, useRef, useState, type ReactNode } from "react";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";
import { DisabledTools } from "./DisabledTools";
import { useViewport } from "./useViewport";
import "./shell.css";

export interface AppLayoutProps {
  sidebar?: ReactNode;
  title?: ReactNode;
  breadcrumb?: string[];
  screenId?: string;
  navigationKey?: string;
  /** 상단 도구줄 오른쪽(메뉴 찾기·AI·알림·사용자 메뉴). 없으면 '준비 중' 자리만 둔다. */
  actions?: ReactNode;
  /** 왼쪽 메뉴 위 즐겨찾기·최근 본 화면. 없으면 '준비 중' 자리만 둔다. */
  quickAccess?: ReactNode;
  /** 화면 탭 줄. 없으면 '준비 중' 자리만 둔다. 휴대폰에서는 탭을 쓰지 않는다. */
  tabs?: ReactNode;
  /** 제목줄 화면 번호 옆(즐겨찾기 별). */
  titleAddon?: ReactNode;
  /** 제목줄 오른쪽: 그 화면의 작업 버튼 자리. 없으면 '준비 중' 자리만 둔다. */
  screenActions?: ReactNode;
  /** 상단 로고를 누르면 갈 주소(대시보드). */
  logoHref?: string;
  /**
   * PC·태블릿 메뉴 접기 상태. 주면 이 값을 따르고(저장값 우선), 바꾸면 onMenuFoldChange로 알린다.
   * 주지 않으면 화면 폭으로 정한다: 1280px 이상 펼침, 768~1279px 접힘.
   */
  menuFolded?: boolean;
  onMenuFoldChange?: (folded: boolean) => void;
  children?: ReactNode;
}

export function AppLayout({
  sidebar,
  title,
  breadcrumb,
  screenId,
  navigationKey,
  actions,
  quickAccess,
  tabs,
  titleAddon,
  screenActions,
  logoHref,
  menuFolded,
  onMenuFoldChange,
  children,
}: AppLayoutProps) {
  const mainRef = useRef<HTMLElement>(null);
  const { mobile, tablet } = useViewport();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [localFolded, setLocalFolded] = useState<boolean | null>(null);
  useEffect(() => {
    setMobileOpen(false);
    setLocalFolded(null);
  }, [mobile, tablet]);
  const folded = menuFolded ?? localFolded ?? tablet;
  const expanded = mobile ? mobileOpen : !folded;
  const toggle = () => {
    if (mobile) {
      setMobileOpen((value) => !value);
    } else if (onMenuFoldChange) {
      onMenuFoldChange(!folded);
    } else {
      setLocalFolded(!folded);
    }
  };
  useEffect(() => {
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape" && mobile) setMobileOpen(false);
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [mobile]);
  useEffect(() => {
    if (mainRef.current) mainRef.current.scrollTop = 0;
    if (mobile) setMobileOpen(false);
  }, [navigationKey, mobile]);
  const closeNavigation = () => {
    if (mobile) {
      setMobileOpen(false);
      document
        .querySelector<HTMLButtonElement>('[data-testid="navigation-toggle"]')
        ?.focus();
    }
  };
  return (
    <div
      data-testid="app-layout"
      className={`app-layout${mobile ? " is-mobile" : tablet ? " is-tablet" : ""}`}
    >
      <a className="skip-link" href="#screen-content">
        본문 바로가기
      </a>
      <Header
        expanded={expanded}
        onToggle={toggle}
        title={title}
        actions={actions}
        logoHref={logoHref}
      />
      <div className="app-body">
        {mobile && expanded && (
          <button
            data-testid="navigation-scrim"
            className="navigation-scrim"
            type="button"
            aria-label="메뉴 닫기"
            onClick={() => setMobileOpen(false)}
          />
        )}
        <Sidebar
          expanded={expanded}
          onToggle={toggle}
          onNavigate={closeNavigation}
          quickAccess={quickAccess}
        >
          {sidebar}
        </Sidebar>
        <main
          data-testid="app-main"
          id="screen-content"
          tabIndex={-1}
          ref={mainRef}
        >
          {!mobile && (
            <div className="screen-tabs">
              {tabs ?? <DisabledTools names={["화면 탭"]} />}
            </div>
          )}
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
                  {titleAddon}
                  {screenId && <span className="screen-id">{screenId}</span>}
                </div>
              </div>
              {screenActions ?? <DisabledTools names={["상단 도구줄"]} />}
            </div>
            <div className="page-content">{children}</div>
          </section>
        </main>
      </div>
    </div>
  );
}

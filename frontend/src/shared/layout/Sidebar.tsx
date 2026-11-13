import type { ReactNode } from "react";
import { DisabledTools } from "./DisabledTools";
import { ShellIcon } from "./ShellIcon";

export interface SidebarProps {
  expanded: boolean;
  onToggle: () => void;
  onNavigate: () => void;
  /** 즐겨찾기·최근 본 화면 목록. 없으면 '준비 중' 자리만 둔다. 접힌 상태에서는 숨긴다. */
  quickAccess?: ReactNode;
  children?: ReactNode;
}

export function Sidebar({
  expanded,
  onToggle,
  onNavigate,
  quickAccess,
  children,
}: SidebarProps) {
  return (
    <aside
      data-testid="app-sidebar"
      id="app-sidebar"
      className={`sidebar${expanded ? " is-expanded" : " is-collapsed"}`}
      aria-label="왼쪽 메뉴"
    >
      {(quickAccess === undefined || expanded) && (
        <div
          className="quick-access"
          data-testid="sidebar-quick-access"
          onClick={(event) => {
            if ((event.target as HTMLElement).closest("button")) onNavigate();
          }}
        >
          {quickAccess ?? <DisabledTools names={["즐겨찾기", "최근 본 화면"]} />}
        </div>
      )}
      <nav
        aria-label="업무 화면"
        data-testid="sidebar-menu"
        onClick={(event) => {
          if ((event.target as HTMLElement).closest("a[href]")) onNavigate();
        }}
      >
        {children}
      </nav>
      <button
        type="button"
        className="sidebar-collapse"
        data-testid="sidebar-collapse"
        onClick={onToggle}
        aria-label={expanded ? "메뉴 접기" : "메뉴 펼치기"}
        aria-expanded={expanded}
        aria-controls="app-sidebar"
      >
        <ShellIcon name="collapse" />
        <span className="menu-label">{expanded ? "메뉴 접기" : "메뉴 펼치기"}</span>
      </button>
    </aside>
  );
}

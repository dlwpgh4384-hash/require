import type { ReactNode } from "react";
import { DisabledTools } from "./DisabledTools";
import { ShellIcon } from "./ShellIcon";

export interface SidebarProps {
  expanded: boolean;
  onToggle: () => void;
  onNavigate: () => void;
  children?: ReactNode;
}

export function Sidebar({
  expanded,
  onToggle,
  onNavigate,
  children,
}: SidebarProps) {
  return (
    <aside
      data-testid="app-sidebar"
      id="app-sidebar"
      className={`sidebar${expanded ? " is-expanded" : " is-collapsed"}`}
      aria-label="왼쪽 메뉴"
    >
      <div className="quick-access">
        <DisabledTools names={["즐겨찾기", "최근 본 화면"]} />
      </div>
      <nav
        aria-label="업무 화면"
        onClick={(event) => {
          if ((event.target as HTMLElement).closest("a[href]")) onNavigate();
        }}
      >
        {children}
      </nav>
      <button
        type="button"
        className="sidebar-collapse"
        onClick={onToggle}
        aria-label={expanded ? "메뉴 접기" : "메뉴 펼치기"}
        aria-expanded={expanded}
        aria-controls="app-sidebar"
      >
        <ShellIcon name="collapse" />
        <span className="menu-label">메뉴 접기</span>
      </button>
    </aside>
  );
}

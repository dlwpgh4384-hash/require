import type { ReactNode } from "react";
import { InterfaceIcon } from "./InterfaceIcon";

interface HeaderProps {
  title: string;
  mobile: boolean;
  menuOpen: boolean;
  onToggle: () => void;
  actions?: ReactNode;
}
export function Header({
  title,
  mobile,
  menuOpen,
  onToggle,
  actions,
}: HeaderProps) {
  return (
    <header className="header" data-testid="app-header">
      {mobile && (
        <button
          className="menu-toggle"
          onClick={onToggle}
          aria-controls="sidebar"
          aria-expanded={menuOpen}
          data-testid="menu-toggle"
        >
          {menuOpen ? "메뉴 접기" : "메뉴 펼치기"}
          <InterfaceIcon name="panel" />
        </button>
      )}
      <div className="header-context">
        <span className="header-eyebrow">업무 공간</span>
        <div className="header-title">{title || "메뉴 탐색"}</div>
      </div>
      <div className="header-actions">{actions}</div>
      <div className="future-tools">
        <button disabled data-testid="header-toolbar">
          <InterfaceIcon name="tools" />
          상단 도구줄: 준비 중
        </button>
        <button disabled data-testid="header-shortcuts">
          <InterfaceIcon name="keyboard" />
          단축키: 준비 중
        </button>
        <button disabled data-testid="header-notifications">
          <InterfaceIcon name="bell" />
          알림: 준비 중
        </button>
      </div>
    </header>
  );
}

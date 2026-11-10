import type { ReactNode } from "react";
import { DisabledTools } from "./DisabledTools";

export interface HeaderProps {
  expanded: boolean;
  onToggle: () => void;
  title?: ReactNode;
  actions?: ReactNode;
}

export function Header({ expanded, onToggle, actions }: HeaderProps) {
  return (
    <header data-testid="global-header" className="global-header">
      <button
        data-testid="navigation-toggle"
        className="icon-button"
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        aria-controls="app-sidebar"
        aria-label="메뉴 접기·펼치기"
      >
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        >
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
      <span className="header-brand" aria-label="프로젝트·원가 관리">
        <span className="brand-mark" aria-hidden="true">
          SI
        </span>
        <span className="header-title">프로젝트·원가 관리</span>
      </span>
      <div className="header-actions">{actions}</div>
      <DisabledTools names={["단축키", "알림"]} />
    </header>
  );
}

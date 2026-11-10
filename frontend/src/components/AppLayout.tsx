import { useState, type ReactNode } from "react";
import type { MenuNode } from "../menuTree";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";
import { InterfaceIcon } from "./InterfaceIcon";

export interface LayoutProps {
  mobile: boolean;
  tree: MenuNode[];
  status: "loading" | "error" | "success";
  /** Backend-resolved menuId; duplicate scrnId values must not select multiple rows. */
  selected?: string;
  title: string;
  onRetry: () => void;
  children: ReactNode;
}
export function AppLayout(props: LayoutProps) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const visible = !props.mobile || mobileOpen;
  return (
    <div className="app-layout" data-testid="app-layout">
      <a className="skip-link" href="#content" data-testid="skip-link">
        본문으로 이동
      </a>
      <Header
        title={props.title}
        mobile={props.mobile}
        menuOpen={visible}
        onToggle={() => setMobileOpen((open) => !open)}
      />
      <div className="app-body">
        <Sidebar
          tree={props.tree}
          visible={visible}
          status={props.status}
          selected={props.selected}
          onRetry={props.onRetry}
        />
        <div className="workspace">
          <div className="tabbar">
            <button disabled data-testid="screen-tabs">
              <InterfaceIcon name="tabs" />
              화면 탭: 준비 중
            </button>
          </div>
          <main
            id="content"
            tabIndex={-1}
            aria-label="선택 화면"
            aria-busy={props.status === "loading"}
            data-testid="screen-main"
          >
            {props.children}
          </main>
        </div>
      </div>
    </div>
  );
}

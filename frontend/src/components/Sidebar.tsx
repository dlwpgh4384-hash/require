import { useEffect, useId, useState } from "react";
import { Link } from "react-router-dom";
import { screenPath, type MenuNode } from "../menuTree";
import { InterfaceIcon } from "./InterfaceIcon";

const iconPaths: Record<string, string> = {
  grid: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  db: "M3 6c0-5 18-5 18 0v12c0 5-18 5-18 0z M3 6c0 5 18 5 18 0 M3 12c0 5 18 5 18 0",
  folder: "M2 5h8l2 3h10v12H2z",
  calendar: "M3 5h18v16H3z M7 2v6 M17 2v6 M3 10h18",
  receipt: "M5 2h14v20l-3-2-4 2-4-2-3 2z M8 7h8 M8 12h8",
  chart: "M3 3v18h18 M7 17v-5 M12 17V8 M17 17V4",
  wallet: "M3 5h17v15H3z M15 10h7v6h-7z",
  lock: "M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4",
  sparkle: "m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3z",
  gear: "M12 2v4 M12 18v4 M2 12h4 M18 12h4 M5 5l3 3 M16 16l3 3 M5 19l3-3 M16 8l3-3 M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0",
};
function MenuIcon({ name }: { name: string | null }) {
  if (!name || !Object.hasOwn(iconPaths, name)) return null;
  return (
    <svg
      className="menu-icon"
      viewBox="0 0 24 24"
      aria-hidden="true"
      data-icon={name}
    >
      <path d={iconPaths[name]} />
    </svg>
  );
}
function containsSelection(node: MenuNode, selected?: string): boolean {
  return node.group
    ? node.children.some((child) => containsSelection(child, selected))
    : node.item.menuId === selected;
}

function Branch({ node, selected }: { node: MenuNode; selected?: string }) {
  const active = containsSelection(node, selected);
  const [open, setOpen] = useState(active);
  const submenuId = useId();
  useEffect(() => {
    if (active) setOpen(true);
  }, [active, selected]);
  const { item } = node;
  if (!node.group)
    return (
      <li
        data-testid={`menu-row-${item.menuId.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
      >
        <Link
          className="menu-leaf"
          to={screenPath(item.scrnId!)}
          aria-current={selected === item.menuId ? "page" : undefined}
          data-testid={`menu-leaf-${item.menuId.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
        >
          <MenuIcon name={item.iconNm} />
          <span className="menu-label">{item.menuNm}</span>
        </Link>
      </li>
    );
  return (
    <li
      data-testid={`menu-row-${item.menuId.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
    >
      <button
        className="menu-group"
        data-active={active || undefined}
        aria-expanded={open}
        aria-controls={submenuId}
        onClick={() => setOpen((value) => !value)}
        data-testid={`menu-group-${item.menuId.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
      >
        <MenuIcon name={item.iconNm} />
        <span className="menu-label">{item.menuNm}</span>
        <InterfaceIcon name="chevron" className="menu-chevron" />
      </button>
      <ul id={submenuId} hidden={!open} className="submenu">
        {node.children.map((child) => (
          <Branch key={child.item.menuId} node={child} selected={selected} />
        ))}
      </ul>
    </li>
  );
}
interface SidebarProps {
  tree: MenuNode[];
  visible: boolean;
  status: "loading" | "error" | "success";
  selected?: string;
  onRetry: () => void;
}
export function Sidebar({
  tree,
  visible,
  status,
  selected,
  onRetry,
}: SidebarProps) {
  return (
    <aside
      id="sidebar"
      className="sidebar"
      hidden={!visible}
      data-testid="app-sidebar"
    >
      <div className="sidebar-heading">
        <span className="eyebrow">메뉴 탐색</span>
        <p>필요한 업무 화면을 찾아보세요.</p>
      </div>
      <div className="nav-quick">
        <button disabled data-testid="sidebar-favorites">
          <InterfaceIcon name="star" />
          즐겨찾기: 준비 중
        </button>
        <button disabled data-testid="sidebar-recent-screens">
          <InterfaceIcon name="history" />
          최근 본 화면: 준비 중
        </button>
      </div>
      <nav aria-label="업무 메뉴" aria-busy={status === "loading"}>
        <div className="nav-label">업무 메뉴</div>
        {status === "loading" && (
          <div className="nav-state" role="status">
            <p>메뉴를 불러오는 중입니다</p>
            <div className="menu-skeleton" aria-hidden="true">
              <span />
              <span />
              <span />
            </div>
          </div>
        )}
        {status === "error" && (
          <div role="alert" className="nav-state nav-error">
            <InterfaceIcon name="alert" />
            <p>메뉴 조회 오류</p>
            <p className="nav-help">
              메뉴를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
            </p>
            <button
              className="primary"
              onClick={onRetry}
              data-testid="menu-retry"
            >
              <InterfaceIcon name="retry" />
              다시 시도
            </button>
          </div>
        )}
        {status === "success" &&
          (tree.length ? (
            <ul>
              {tree.map((node) => (
                <Branch
                  key={node.item.menuId}
                  node={node}
                  selected={selected}
                />
              ))}
            </ul>
          ) : (
            <div className="nav-state" role="status">
              <InterfaceIcon name="empty" />
              <p>등록된 메뉴 없음</p>
              <p className="nav-help">현재 표시할 수 있는 메뉴가 없습니다.</p>
            </div>
          ))}
      </nav>
    </aside>
  );
}

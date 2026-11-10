import { useEffect, useState, type ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { AppLayout } from "../shared/layout/AppLayout";
import { NavigationMenuIcon } from "./NavigationMenuIcon";
import { NavigationState } from "../shared/layout/NavigationState";
import { ShellIcon } from "../shared/layout/ShellIcon";

export interface ShellMenuItem {
  menuId: string;
  upperMenuId: string | null;
  menuNm: string;
  scrnId: string | null;
  menuLvl: number;
  sortOrd: number;
  pcOnlyYn: "Y" | "N";
  iconNm: string | null;
}
export interface ShellScreenAccess {
  scrnId: string;
  available: boolean;
  pcOnlyYn: "Y" | "N";
}
export interface ShellMenuResponse {
  success: true;
  data: ShellMenuItem[];
  meta: { screenAccess: ShellScreenAccess[] };
}

type ReadState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; response: ShellMenuResponse };
const order = (a: ShellMenuItem, b: ShellMenuItem) =>
  a.sortOrd - b.sortOrd ||
  (a.menuId < b.menuId ? -1 : a.menuId > b.menuId ? 1 : 0);

function menuPath(
  items: ShellMenuItem[],
  current: string | null,
): ShellMenuItem[] {
  const path: ShellMenuItem[] = [];
  const visited = new Set<string>();
  let item = items.find(
    (entry) => current !== null && entry.scrnId === current,
  );
  while (item && !visited.has(item.menuId)) {
    visited.add(item.menuId);
    path.unshift(item);
    const parentId = item.upperMenuId;
    item = items.find((entry) => entry.menuId === parentId);
  }
  return path;
}

function MenuBranches({
  items,
  parent,
  open,
  toggle,
  current,
  hidden = false,
}: {
  items: ShellMenuItem[];
  parent: string | null;
  open: Set<string>;
  toggle: (id: string) => void;
  current: string | null;
  hidden?: boolean;
}) {
  return (
    <ul className="menu-tree" hidden={hidden}>
      {items
        .filter((item) => (item.upperMenuId || null) === parent)
        .sort(order)
        .map((item) => {
          const group = items.some(
            (child) => child.upperMenuId === item.menuId,
          );
          return (
            <li
              key={item.menuId}
              data-testid={`menu-item-${item.menuId.toLowerCase()}`}
            >
              {group ? (
                <button
                  className={
                    menuPath(items, current).some(
                      (entry) => entry.menuId === item.menuId,
                    )
                      ? "has-active"
                      : undefined
                  }
                  data-testid={`menu-toggle-${item.menuId.toLowerCase()}`}
                  type="button"
                  aria-label={item.menuNm}
                  aria-expanded={open.has(item.menuId)}
                  aria-controls={`menu-children-${item.menuId}`}
                  onClick={() => toggle(item.menuId)}
                >
                  <NavigationMenuIcon name={item.iconNm} />
                  <span className="menu-label">{item.menuNm}</span>
                  <ShellIcon
                    name="chevron"
                    className={`menu-chevron${open.has(item.menuId) ? " is-open" : ""}`}
                  />
                </button>
              ) : (
                <Link
                  to={`/screens/${encodeURIComponent(item.scrnId!)}`}
                  aria-current={current === item.scrnId ? "page" : undefined}
                  aria-label={item.menuNm}
                >
                  <NavigationMenuIcon name={item.iconNm} />
                  <span className="menu-label">{item.menuNm}</span>
                </Link>
              )}
              {group && (
                <div
                  className="menu-children"
                  id={`menu-children-${item.menuId}`}
                >
                  <MenuBranches
                    items={items}
                    parent={item.menuId}
                    open={open}
                    toggle={toggle}
                    current={current}
                    hidden={!open.has(item.menuId)}
                  />
                </div>
              )}
            </li>
          );
        })}
    </ul>
  );
}

// This is the entry composition; domain screen wrappers can replace its body through renderScreen.
export function NavigationRoot({
  renderScreen,
}: {
  renderScreen?: (scrnId: string, menu: ShellMenuItem | undefined) => ReactNode;
}) {
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState<ReadState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [open, setOpen] = useState<Set<string>>(() => new Set());
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 767px)").matches,
  );
  useEffect(() => {
    const media = window.matchMedia("(max-width: 767px)");
    const change = () => setMobile(media.matches);
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: "loading" });
    fetch("/api/common/menus", { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error("Menu unavailable");
        const body: ShellMenuResponse = await response.json();
        if (
          body.success !== true ||
          !Array.isArray(body.data) ||
          !Array.isArray(body.meta?.screenAccess)
        )
          throw new Error("Invalid menu response");
        if (!controller.signal.aborted)
          setState({ status: "ready", response: body });
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ status: "error" });
      });
    return () => controller.abort();
  }, [attempt]);

  const raw = state.status === "ready" ? state.response.data : [];
  const visible = new Set<string>();
  const visiting = new Set<string>();
  function include(item: ShellMenuItem): boolean {
    if (visiting.has(item.menuId) || (mobile && item.pcOnlyYn === "Y"))
      return false;
    visiting.add(item.menuId);
    const children = raw.filter((child) => child.upperMenuId === item.menuId);
    const screenAvailable =
      state.status === "ready" &&
      state.response.meta.screenAccess.some(
        (access) => access.scrnId === item.scrnId && access.available,
      );
    const eligible = children.length
      ? children.map(include).some(Boolean)
      : !!item.scrnId && screenAvailable;
    visiting.delete(item.menuId);
    if (eligible) visible.add(item.menuId);
    return eligible;
  }
  raw.filter((item) => !item.upperMenuId).forEach(include);
  const items = raw.filter((item) => visible.has(item.menuId));
  const first = items
    .filter(
      (item) =>
        item.scrnId && !raw.some((child) => child.upperMenuId === item.menuId),
    )
    .sort(order)[0];
  const match = /^\/screens\/([^/]+)$/.exec(location.pathname);
  let screenId: string | null = null;
  try {
    screenId = match ? decodeURIComponent(match[1]) : null;
  } catch {
    screenId = null;
  }
  useEffect(() => {
    if (
      state.status === "ready" &&
      location.pathname === "/" &&
      first?.scrnId
    ) {
      navigate(`/screens/${encodeURIComponent(first.scrnId)}`, {
        replace: true,
      });
    }
  }, [state.status, location.pathname, first?.scrnId, navigate]);
  const retry = () => setAttempt((value) => value + 1);
  const failure = (compact = false) => (
    <NavigationState kind="error" compact={compact}>
      <button
        className="primary-button"
        data-testid="menu-retry"
        type="button"
        onClick={retry}
      >
        <ShellIcon name="refresh" />
        다시 시도
      </button>
    </NavigationState>
  );
  const menu = raw.find((item) => item.scrnId === screenId);
  const access =
    state.status === "ready"
      ? state.response.meta.screenAccess.find(
          (item) => item.scrnId === screenId,
        )
      : undefined;
  let body: ReactNode;
  if (state.status === "loading") body = <NavigationState kind="loading" />;
  else if (state.status === "error") body = failure();
  else if (location.pathname === "/" && !first)
    body = <NavigationState kind="empty" />;
  else if (!access)
    body = (
      <NavigationState kind="missing">
        <Link
          className="primary-button"
          to={
            first?.scrnId ? `/screens/${encodeURIComponent(first.scrnId)}` : "/"
          }
        >
          첫 화면으로 가기
        </Link>
      </NavigationState>
    );
  else if (!access.available) body = <NavigationState kind="unavailable" />;
  else if (mobile && access.pcOnlyYn === "Y")
    body = <NavigationState kind="pc-only" />;
  else
    body = renderScreen ? (
      renderScreen(access.scrnId, menu)
    ) : (
      <NavigationState kind="ready" />
    );
  const sidebar =
    state.status === "loading" ? (
      <NavigationState kind="loading" compact />
    ) : state.status === "error" ? (
      failure(true)
    ) : !first ? (
      <NavigationState kind="empty" compact />
    ) : (
      <MenuBranches
        items={items}
        parent={null}
        open={open}
        current={screenId}
        toggle={(id) =>
          setOpen((previous) => {
            const next = new Set(previous);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
          })
        }
      />
    );
  return (
    <AppLayout
      sidebar={sidebar}
      navigationKey={location.key}
      breadcrumb={
        state.status === "ready" && access?.available
          ? menuPath(raw, screenId).map((item) => item.menuNm)
          : undefined
      }
      screenId={
        state.status === "ready" && access?.available
          ? access.scrnId
          : undefined
      }
      title={
        state.status === "ready" && access?.available ? menu?.menuNm : undefined
      }
    >
      <div className="shell-state">{body}</div>
    </AppLayout>
  );
}

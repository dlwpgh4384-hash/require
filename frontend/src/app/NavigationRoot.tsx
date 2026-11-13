import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { AppLayout } from "../shared/layout/AppLayout";
import { NavigationMenuIcon } from "./NavigationMenuIcon";
import { NavigationState } from "../shared/layout/NavigationState";
import { ShellIcon } from "../shared/layout/ShellIcon";
import {
  ScreenDirtyContext,
  ScreenTabs,
  type ScreenTab,
} from "../shared/layout/ScreenTabs";
import { ConfirmDialog } from "../shared/feedback/Dialog";
import { feedbackMessages } from "../shared/feedback/messages";
import { MENU_REFRESH_EVENT } from "./menuRefresh";

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

/** 화면 틀이 확장 기능(즐겨찾기·도구줄 등)에 넘겨주는 현재 상태. */
export interface ShellContext {
  ready: boolean;
  failed: boolean;
  /** 보이는 메뉴(사용 중·계층 정상·휴대폰 PC 전용 제외) */
  menus: ShellMenuItem[];
  mobile: boolean;
  /** 지금 본문에 보이는 화면 */
  screenId: string | null;
  available: (screenId: string) => boolean;
  open: (screenId: string) => void;
}
export interface ShellExtensions {
  quickAccess?: ReactNode;
  actions?: ReactNode;
  titleAddon?: ReactNode;
  screenActions?: ReactNode;
  menuFolded?: boolean;
  onMenuFoldChange?: (folded: boolean) => void;
}

export const DASHBOARD_SCREEN = "UI-16";
export const MAX_TABS = 8;

type ReadState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; response: ShellMenuResponse };
type Pending =
  | { kind: "limit"; target: string; victim: string; previous: string | null }
  | { kind: "close"; id: string }
  | { kind: "leave"; target: string; previous: string }
  | null;

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
  extensions,
  tabs: tabsEnabled = false,
  onScreenOpened,
}: {
  renderScreen?: (scrnId: string, menu: ShellMenuItem | undefined) => ReactNode;
  /** 즐겨찾기·도구줄 같은 확장 기능. 화면 틀 상태를 받아 자리에 넣을 내용을 돌려준다. */
  extensions?: (context: ShellContext) => ShellExtensions;
  /** FR-0103 화면 탭을 쓴다(PC·태블릿). */
  tabs?: boolean;
  /** 사용자가 화면을 열었을 때(최근 본 화면 기록용). 첫 화면 자동 열기에는 부르지 않는다. */
  onScreenOpened?: (screenId: string) => void;
}) {
  const location = useLocation();
  const navigate = useNavigate();
  const [state, setState] = useState<ReadState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [open, setOpen] = useState<Set<string>>(() => new Set());
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 767px)").matches,
  );
  const [tabList, setTabList] = useState<string[]>([]);
  const [current, setCurrent] = useState<string | null>(null);
  const [dirty, setDirtyState] = useState<Set<string>>(() => new Set());
  const [pending, setPending] = useState<Pending>(null);
  const seen = useRef<Record<string, number>>({});
  const tick = useRef(0);
  const autoOpened = useRef(false);
  useEffect(() => {
    const refresh = () => setAttempt((value) => value + 1);
    window.addEventListener(MENU_REFRESH_EVENT, refresh);
    return () => window.removeEventListener(MENU_REFRESH_EVENT, refresh);
  }, []);
  useEffect(() => {
    const media = window.matchMedia("(max-width: 767px)");
    const change = () => setMobile(media.matches);
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
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
  const accessOf = (id: string | null) =>
    state.status === "ready" && id !== null
      ? state.response.meta.screenAccess.find((item) => item.scrnId === id)
      : undefined;
  const openable = (id: string | null) => {
    const access = accessOf(id);
    return !!access && access.available && !(mobile && access.pcOnlyYn === "Y");
  };
  const tabsOn = tabsEnabled && !mobile;
  const keepMounted = tabsOn || (tabsEnabled && mobile);

  // 주소 공유: "/#UI-12" 형식으로 들어오면 그 화면을 연다.
  const hashScreen = /^#([A-Za-z0-9_-]+)$/.exec(location.hash)?.[1] ?? null;
  useEffect(() => {
    if (state.status !== "ready" || location.pathname !== "/") return;
    if (hashScreen) {
      navigate(`/screens/${encodeURIComponent(hashScreen)}`, { replace: true });
    } else if (first?.scrnId) {
      autoOpened.current = true;
      navigate(`/screens/${encodeURIComponent(first.scrnId)}`, {
        replace: true,
      });
    }
  }, [state.status, location.pathname, hashScreen, first?.scrnId, navigate]);

  // FR-0101: 지금 화면이 속한 메뉴 묶음은 자동으로 펼친다.
  const pathKey = menuPath(raw, screenId)
    .slice(0, -1)
    .map((item) => item.menuId)
    .join(",");
  useEffect(() => {
    if (!pathKey) return;
    setOpen((previous) => {
      const ids = pathKey.split(",");
      if (ids.every((id) => previous.has(id))) return previous;
      const next = new Set(previous);
      ids.forEach((id) => next.add(id));
      return next;
    });
  }, [pathKey]);

  const markSeen = (id: string) => {
    tick.current += 1;
    seen.current[id] = tick.current;
  };
  const activate = (id: string) => {
    markSeen(id);
    setCurrent(id);
    if (autoOpened.current) autoOpened.current = false;
    else onScreenOpened?.(id);
  };

  // 화면 이동이 들어오면 탭·휴대폰 규칙에 따라 보여 줄 화면을 정한다.
  const ready = state.status === "ready";
  const routeOpenable = openable(screenId);
  useEffect(() => {
    if (!ready || !keepMounted || pending) return;
    if (!screenId || !routeOpenable) {
      if (!tabsOn) setCurrent(null);
      return;
    }
    if (screenId === current) return;
    if (!tabsOn) {
      // 휴대폰: 탭 없이 화면을 바꾸기 전에 미저장 입력을 묻는다.
      if (current && dirty.has(current)) {
        setPending({ kind: "leave", target: screenId, previous: current });
        return;
      }
      activate(screenId);
      return;
    }
    if (tabList.includes(screenId)) {
      activate(screenId);
      return;
    }
    const base =
      openable(DASHBOARD_SCREEN) && !tabList.includes(DASHBOARD_SCREEN)
        ? [DASHBOARD_SCREEN, ...tabList]
        : tabList;
    if (base.includes(screenId)) {
      setTabList(base);
      activate(screenId);
      return;
    }
    if (base.length < MAX_TABS) {
      setTabList([...base, screenId]);
      activate(screenId);
      return;
    }
    const victim = base
      .filter((id) => id !== DASHBOARD_SCREEN && id !== current)
      .sort((a, b) => (seen.current[a] ?? 0) - (seen.current[b] ?? 0))[0];
    setTabList(base);
    setPending({ kind: "limit", target: screenId, victim, previous: current });
  }, [
    ready,
    keepMounted,
    tabsOn,
    screenId,
    routeOpenable,
    pending,
    current,
    tabList,
  ]);

  // 휴대폰↔PC로 폭이 바뀌면 화면 상태를 다시 맞춘다(처음 그릴 때는 그대로 둔다).
  const previousTabsOn = useRef(tabsOn);
  useEffect(() => {
    if (previousTabsOn.current === tabsOn) return;
    previousTabsOn.current = tabsOn;
    if (!tabsOn) setTabList((list) => (list.length ? [] : list));
    setCurrent(null);
  }, [tabsOn]);

  // 새로고침·창 닫기: 미저장 입력이 있으면 브라우저 기본 경고를 띄운다.
  useEffect(() => {
    if (dirty.size === 0) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty.size]);

  const setDirty = useCallback((id: string, value: boolean) => {
    setDirtyState((previous) => {
      if (previous.has(id) === value) return previous;
      const next = new Set(previous);
      if (value) next.add(id);
      else next.delete(id);
      return next;
    });
  }, []);
  const clearDirty = (id: string) => setDirty(id, false);
  const go = (id: string, replace = false) =>
    navigate(`/screens/${encodeURIComponent(id)}`, { replace });

  const closeTab = (id: string) => {
    const index = tabList.indexOf(id);
    const rest = tabList.filter((tab) => tab !== id);
    clearDirty(id);
    setTabList(rest);
    delete seen.current[id];
    if (id === current) {
      const next = rest[Math.max(0, index - 1)] ?? rest[0] ?? null;
      setCurrent(null);
      if (next) go(next);
      else navigate("/");
    }
  };
  const requestClose = (id: string) => {
    if (dirty.has(id)) setPending({ kind: "close", id });
    else closeTab(id);
  };
  const resolvePending = (proceed: boolean) => {
    const item = pending;
    setPending(null);
    if (!item) return;
    if (item.kind === "close") {
      if (proceed) closeTab(item.id);
    } else if (item.kind === "limit") {
      if (proceed) {
        clearDirty(item.victim);
        delete seen.current[item.victim];
        setTabList((list) => [
          ...list.filter((id) => id !== item.victim),
          item.target,
        ]);
        activate(item.target);
      } else if (item.previous) go(item.previous, true);
    } else if (item.kind === "leave") {
      if (proceed) {
        clearDirty(item.previous);
        activate(item.target);
      } else go(item.previous, true);
    }
  };

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
  const menuFor = (id: string | null) => raw.find((item) => item.scrnId === id);
  const screenBody = (id: string) =>
    renderScreen ? (
      renderScreen(id, menuFor(id))
    ) : (
      <NavigationState kind="ready" />
    );

  // 본문에 보일 화면: 탭·휴대폰 화면이 정해져 있으면 그것, 아니면 주소의 화면.
  const shown =
    keepMounted && current && (pending || routeOpenable) ? current : screenId;
  const access = accessOf(shown);
  let routeBody: ReactNode = null;
  if (state.status === "loading")
    routeBody = <NavigationState kind="loading" />;
  else if (state.status === "error") routeBody = failure();
  else if (location.pathname === "/" && !first)
    routeBody = <NavigationState kind={raw.length ? "no-screen" : "empty"} />;
  else if (!access)
    routeBody = (
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
  else if (!access.available)
    routeBody = <NavigationState kind="unavailable" />;
  else if (mobile && access.pcOnlyYn === "Y")
    routeBody = (
      <NavigationState
        kind="pc-only"
        description={`「${menuFor(shown)?.menuNm ?? shown}」은 큰 표를 다루는 화면이라 휴대폰에서는 열리지 않습니다`}
      >
        <Link
          className="primary-button"
          data-testid="pc-only-dashboard"
          to={`/screens/${DASHBOARD_SCREEN}`}
        >
          대시보드로
        </Link>
      </NavigationState>
    );
  else if (!keepMounted) routeBody = screenBody(access.scrnId);

  const panelIds = tabsOn ? tabList : current ? [current] : [];
  const showPanels =
    keepMounted &&
    !!current &&
    shown === current &&
    !!access?.available &&
    !(mobile && access.pcOnlyYn === "Y");
  const body = (
    <ScreenDirtyContext.Provider value={setDirty}>
      {keepMounted &&
        panelIds.map((id) => (
          <div
            key={id}
            className="screen-tab-panel"
            data-testid={`screen-panel-${id.toLowerCase()}`}
            hidden={!(showPanels && id === current)}
          >
            {screenBody(id)}
          </div>
        ))}
      {!showPanels && routeBody}
    </ScreenDirtyContext.Provider>
  );

  const sidebar =
    state.status === "loading" ? (
      <NavigationState kind="loading" compact />
    ) : state.status === "error" ? (
      failure(true)
    ) : !first ? (
      <NavigationState kind={raw.length ? "no-screen" : "empty"} compact />
    ) : (
      <MenuBranches
        items={items}
        parent={null}
        open={open}
        current={shown}
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
  const available = (id: string) => openable(id);
  const shownAvailable = state.status === "ready" && !!access?.available;
  const extra = extensions?.({
    ready: state.status === "ready",
    failed: state.status === "error",
    menus: items,
    mobile,
    screenId: shownAvailable ? shown : null,
    available,
    open: (id) => go(id),
  });
  const tabItems: ScreenTab[] = tabList.map((id) => ({
    id,
    label: menuFor(id)?.menuNm ?? id,
    dirty: dirty.has(id),
    fixed: id === DASHBOARD_SCREEN,
  }));
  const victimName = (id: string) => menuFor(id)?.menuNm ?? id;
  return (
    <>
      <AppLayout
        sidebar={sidebar}
        navigationKey={location.key}
        breadcrumb={
          shownAvailable
            ? menuPath(raw, shown).map((item) => item.menuNm)
            : undefined
        }
        screenId={shownAvailable ? access!.scrnId : undefined}
        title={shownAvailable ? menuFor(shown)?.menuNm : undefined}
        tabs={
          tabsEnabled ? (
            <ScreenTabs
              tabs={tabItems}
              active={showPanels ? current : null}
              onSelect={(id) => go(id)}
              onClose={requestClose}
            />
          ) : undefined
        }
        screenActions={
          tabsEnabled ? (
            <div className="screen-actions" data-testid="screen-actions">
              {shownAvailable && !(mobile && access?.pcOnlyYn === "Y")
                ? extra?.screenActions
                : undefined}
            </div>
          ) : undefined
        }
        logoHref={tabsEnabled ? `/screens/${DASHBOARD_SCREEN}` : undefined}
        quickAccess={extra?.quickAccess}
        actions={extra?.actions}
        titleAddon={
          shownAvailable && !(mobile && access?.pcOnlyYn === "Y")
            ? extra?.titleAddon
            : undefined
        }
        menuFolded={extra?.menuFolded}
        onMenuFoldChange={extra?.onMenuFoldChange}
      >
        <div className="shell-state">{body}</div>
      </AppLayout>
      {pending?.kind === "limit" && (
        <ConfirmDialog
          title="가장 오래 안 본 탭을 닫고 열까요?"
          description={
            dirty.has(pending.victim)
              ? `가장 오래 안 본 「${victimName(pending.victim)}」 탭에 저장하지 않은 내용이 있습니다. 닫으면 입력한 내용이 사라집니다. 닫고 새 화면을 열까요?`
              : `화면 탭은 ${MAX_TABS}개까지 열 수 있습니다. 가장 오래 안 본 「${victimName(pending.victim)}」 탭을 닫고 새 화면을 엽니다.`
          }
          proceedLabel="닫고 열기"
          destructive={dirty.has(pending.victim)}
          testId="tab-limit-dialog"
          onCancel={() => resolvePending(false)}
          onProceed={() => resolvePending(true)}
        />
      )}
      {(pending?.kind === "close" || pending?.kind === "leave") && (
        <ConfirmDialog
          title="저장하지 않고 나갈까요?"
          description={feedbackMessages.unsaved}
          proceedLabel="나가기"
          destructive
          testId="unsaved-dialog"
          onCancel={() => resolvePending(false)}
          onProceed={() => resolvePending(true)}
        />
      )}
    </>
  );
}

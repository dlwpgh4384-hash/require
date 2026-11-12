import { useEffect, useRef, useState, type RefObject } from "react";
import { Toast } from "../../shared/feedback/Dialog";
import { ScreenSettingsDialog } from "./ScreenSettingsDialog";
import { UserPreferenceDialog } from "./UserPreferenceDialog";
import type { PreferenceMenu } from "./PersonalScreenPreferences";
import { ShellIcon } from "../../shared/layout/ShellIcon";

export function MenuSearchDialog({
  menus,
  loading,
  error,
  mobile,
  available,
  onSelect,
  onClose,
}: {
  menus: PreferenceMenu[];
  loading: boolean;
  error?: string;
  mobile: boolean;
  available: (id: string) => boolean;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const search = query.trim().toLocaleLowerCase();
  const matches = menus.filter(
    (item) =>
      item.scrnId &&
      available(item.scrnId) &&
      (!mobile || item.pcOnlyYn !== "Y") &&
      `${item.menuNm} ${item.scrnId}`.toLocaleLowerCase().includes(search),
  );
  return (
    <UserPreferenceDialog
      title="메뉴 찾기"
      onClose={onClose}
      testId="menu-search-dialog"
    >
      {/* UI-36 */}
      <label htmlFor="preference-menu-query">화면 이름·번호</label>
      <input
        id="preference-menu-query"
        data-testid="menu-search-query"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
      />
      {loading ? (
        <p role="status">메뉴를 불러오는 중입니다.</p>
      ) : error ? (
        <p role="alert">메뉴를 불러오지 못했습니다.</p>
      ) : matches.length === 0 ? (
        <p>일치하는 메뉴가 없습니다.</p>
      ) : (
        <ul className="preference-search-results">
          {matches.map((item) => (
            <li
              key={item.scrnId}
              data-testid={`search-result-${item.scrnId!.toLowerCase()}`}
            >
              <button
                type="button"
                data-testid={`search-open-${item.scrnId!.toLowerCase()}`}
                onClick={() => onSelect(item.scrnId!)}
              >
                {item.menuNm} <small>{item.scrnId}</small>
              </button>
            </li>
          ))}
        </ul>
      )}
      <button type="button" data-testid="menu-search-close" onClick={onClose}>
        닫기
      </button>
    </UserPreferenceDialog>
  );
}

export function ShortcutHelpDialog({ onClose }: { onClose: () => void }) {
  return (
    <UserPreferenceDialog
      title="단축키 보기"
      onClose={onClose}
      testId="shortcut-help-dialog"
    >
      {/* UI-37 */}
      <dl>
        <dt>Ctrl+K</dt>
        <dd>메뉴 찾기</dd>
        <dt>Esc</dt>
        <dd>창 닫기</dd>
        <dt>Enter</dt>
        <dd>조회 (업무 화면 연결 시)</dd>
        <dt>Ctrl+S</dt>
        <dd>저장 (업무 화면 연결 시)</dd>
        <dt>Ctrl+← / Ctrl+→</dt>
        <dd>이전·다음 페이지 (업무 화면 연결 시)</dd>
      </dl>
      <button type="button" data-testid="shortcut-help-close" onClick={onClose}>
        닫기
      </button>
    </UserPreferenceDialog>
  );
}

/**
 * 시안 「자동 로그아웃까지 mm:ss [연장]」. 운영 기준 SESSION_MIN(30분, 공통-07).
 * 지금은 보이는 것만 만든다: 화면을 누르면 30분으로 돌아가고 0이 되어도 로그아웃하지 않는다.
 * 실제 로그아웃과 운영 기준 표(TB_SY_OPER_STD)에서 분을 읽는 일은 마지막 로그인(UI-22) 작업에서 붙인다.
 */
const SESSION_MINUTES = 30;
let sessionEnd = Date.now() + SESSION_MINUTES * 60_000;
if (typeof document !== "undefined")
  document.addEventListener("click", () => {
    sessionEnd = Date.now() + SESSION_MINUTES * 60_000;
  });
export function sessionLeft(now = Date.now()) {
  const seconds = Math.max(0, Math.round((sessionEnd - now) / 1000));
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}
function SessionTimer({ onExtend }: { onExtend: () => void }) {
  const [left, setLeft] = useState(() => sessionLeft());
  useEffect(() => {
    const timer = window.setInterval(() => setLeft(sessionLeft()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return (
    <div className="hd-sess" data-testid="session-left">
      자동 로그아웃까지 <b>{left}</b>
      <button
        type="button"
        className="hd-sess-ext"
        data-testid="session-extend"
        onClick={() => {
          sessionEnd = Date.now() + SESSION_MINUTES * 60_000;
          setLeft(sessionLeft());
          onExtend();
        }}
      >
        연장
      </button>
    </div>
  );
}

/** 로그인(UI-22) 전까지 서버 사용자는 admin으로 고정한다. 헤더 이름 자리에 그대로 보인다. */
const CURRENT_USER = { name: "시스템 관리자", role: "관리자" };

export function UserPreferenceTools({
  menus,
  mobile,
  available,
  onNavigate,
  menuLoading = false,
  menuError,
  onDirtyChange,
}: {
  menus: PreferenceMenu[];
  mobile: boolean;
  available: (id: string) => boolean;
  onNavigate: (id: string) => void;
  menuLoading?: boolean;
  menuError?: string;
  onDirtyChange?: (dirty: boolean) => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const menuAnchor = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: MouseEvent) => {
      if (!menuAnchor.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMenuOpen(false);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", escape);
    };
  }, [menuOpen]);
  const [dialog, setDialog] = useState<
    "settings" | "search" | "shortcuts" | null
  >(null);
  useEffect(() => {
    if (mobile || dialog) return;
    const openSearch = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setDialog("search");
      }
    };
    window.addEventListener("keydown", openSearch);
    return () => window.removeEventListener("keydown", openSearch);
  }, [mobile, dialog]);
  return (
    <div data-testid="user-preference-tools" className="preference-tools">
      {mobile && (
        <button
          type="button"
          className="hd-ibtn"
          data-testid="menu-search-open"
          aria-label="메뉴 찾기"
          onClick={() => setDialog("search")}
        >
          <ShellIcon name="search" />
        </button>
      )}
      <button
        type="button"
        className="hd-ai"
        data-testid="ai-unavailable"
        aria-label="AI에게 묻기 (준비 중)"
        title="AI에게 묻기는 AI 분석 도우미 작업에서 붙입니다."
        onClick={() => setNotice("AI에게 묻기는 준비 중입니다.")}
      >
        <ShellIcon name="spark" />
        {!mobile && <span>AI에게 묻기</span>}
      </button>
      <button
        type="button"
        className="hd-ibtn"
        data-testid="notification-unavailable"
        aria-label="알림 (준비 중)"
        title="알림은 마지막 로그인 작업에서 붙입니다."
        onClick={() => setNotice("알림은 준비 중입니다.")}
      >
        <ShellIcon name="bell" />
      </button>
      <div className="hd-anchor" ref={menuAnchor}>
        <button
          type="button"
          className="hd-umenu"
          data-testid="user-menu-open"
          aria-expanded={menuOpen}
          aria-label="사용자 메뉴"
          onClick={() => setMenuOpen(!menuOpen)}
        >
          <ShellIcon name="user" />
          {!mobile && (
            <span>
              {CURRENT_USER.name} <span className="hd-sub">({CURRENT_USER.role})</span>
            </span>
          )}
          <ShellIcon name="chevronDown" />
        </button>
        {menuOpen && (
          <div
            data-testid="user-menu-panel"
            className="preference-user-menu"
            role="menu"
          >
            <div className="hd-pop-h">
              {CURRENT_USER.name} <span className="hd-sub">{CURRENT_USER.role}</span>
            </div>
            {(
              [
                ["profile-unavailable", "내 정보", null],
                ["password-unavailable", "비밀번호 변경", null],
                ["screen-settings-open", "화면 설정", "settings"],
                ["shortcut-help-open", "단축키 보기", "shortcuts"],
                ["logout-unavailable", "로그아웃", null],
              ] as const
            ).map(([testId, label, target]) => (
              <button
                key={testId}
                type="button"
                role="menuitem"
                className="hd-pop-i"
                data-testid={testId}
                onClick={() => {
                  setMenuOpen(false);
                  if (target) setDialog(target);
                  else setNotice(`${label}은(는) 준비 중입니다. 마지막 로그인 작업에서 붙입니다.`);
                }}
              >
                {label}
              </button>
            ))}
            <SessionTimer onExtend={() => setNotice("자동 로그아웃 시간을 30분으로 다시 늘렸습니다.")} />
          </div>
        )}
      </div>
      {notice && (
        <Toast
          message={notice}
          onDismiss={() => setNotice("")}
          testId="header-notice"
        />
      )}
      {dialog === "settings" && (
        <ScreenSettingsDialog
          onClose={() => setDialog(null)}
          onDirtyChange={onDirtyChange}
        />
      )}
      {dialog === "search" && (
        <MenuSearchDialog
          menus={menus}
          loading={menuLoading}
          error={menuError}
          mobile={mobile}
          available={available}
          onClose={() => setDialog(null)}
          onSelect={(id) => {
            onNavigate(id);
            setDialog(null);
          }}
        />
      )}
      {dialog === "shortcuts" && (
        <ShortcutHelpDialog onClose={() => setDialog(null)} />
      )}
    </div>
  );
}

export function usePreferenceBusinessShortcuts(
  handlers: {
    onSearch?: () => void;
    onSave?: () => void;
    onPreviousPage?: () => void;
    onNextPage?: () => void;
    dialogTarget?: RefObject<HTMLElement>;
  },
  enabled = true,
) {
  useEffect(() => {
    if (!enabled) return;
    const keydown = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;
      const dialogs = document.querySelectorAll(
        '[role="dialog"], [role="alertdialog"]',
      );
      const scope = handlers.dialogTarget?.current;
      if (scope?.closest("[hidden]")) return;
      if (dialogs.length) {
        const active = document.activeElement;
        if (
          !scope ||
          !active ||
          !scope.contains(active) ||
          (active instanceof Element && active.closest('[role="alertdialog"]'))
        )
          return;
        if (
          event.ctrlKey &&
          event.key.toLowerCase() === "s" &&
          handlers.onSave
        ) {
          event.preventDefault();
          handlers.onSave();
        }
        return;
      }
      const target = event.target;
      if (
        target instanceof Element &&
        target.matches("input,select,textarea,[contenteditable='true']")
      )
        return;
      const handler =
        event.ctrlKey && event.key.toLowerCase() === "s"
          ? handlers.onSave
          : event.ctrlKey && event.key === "ArrowLeft"
            ? handlers.onPreviousPage
            : event.ctrlKey && event.key === "ArrowRight"
              ? handlers.onNextPage
              : !event.ctrlKey && event.key === "Enter"
                ? handlers.onSearch
                : undefined;
      if (handler) {
        event.preventDefault();
        handler();
      }
    };
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [
    enabled,
    handlers.onSearch,
    handlers.onSave,
    handlers.onPreviousPage,
    handlers.onNextPage,
    handlers.dialogTarget,
  ]);
}

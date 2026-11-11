import { useEffect, useState } from "react";
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
      <button
        type="button"
        data-testid="menu-search-open"
        aria-label="메뉴 찾기"
        onClick={() => setDialog("search")}
      >
        {mobile ? <ShellIcon name="search" /> : "메뉴 찾기 (Ctrl+K)"}
      </button>
      <button
        type="button"
        data-testid="ai-unavailable"
        disabled
        title="준비 중"
        aria-label="AI에게 묻기 준비 중"
      >
        {mobile ? <ShellIcon name="spark" /> : "AI에게 묻기 · 준비 중"}
      </button>
      <button
        type="button"
        data-testid="notification-unavailable"
        disabled
        title="준비 중"
        aria-label="알림 준비 중"
      >
        {mobile ? <ShellIcon name="bell" /> : "알림 · 준비 중"}
      </button>
      <button
        type="button"
        data-testid="user-menu-open"
        aria-expanded={menuOpen}
        aria-label="시스템 관리자"
        onClick={() => setMenuOpen(!menuOpen)}
      >
        {mobile ? <ShellIcon name="user" /> : "시스템 관리자"}
      </button>
      {menuOpen && (
        <div data-testid="user-menu-panel" className="preference-user-menu">
          <button
            type="button"
            data-testid="screen-settings-open"
            onClick={() => setDialog("settings")}
          >
            화면 설정
          </button>
          <button
            type="button"
            data-testid="shortcut-help-open"
            onClick={() => setDialog("shortcuts")}
          >
            단축키 보기
          </button>
          <button type="button" data-testid="profile-unavailable" disabled>
            내 정보 · 준비 중
          </button>
          <button type="button" data-testid="password-unavailable" disabled>
            비밀번호 변경 · 준비 중
          </button>
          <button type="button" data-testid="logout-unavailable" disabled>
            로그아웃 · 준비 중
          </button>
        </div>
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
  },
  enabled = true,
) {
  useEffect(() => {
    if (!enabled) return;
    const keydown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || document.querySelector('[role="dialog"]'))
        return;
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
  ]);
}

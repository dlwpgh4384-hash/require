import { useEffect, useRef, useState, type RefObject } from "react";
import { ScreenSettingsDialog } from "./ScreenSettingsDialog";
import { UserPreferenceDialog } from "./UserPreferenceDialog";
import type { PreferenceMenu } from "./PersonalScreenPreferences";
import { ShellIcon } from "../../shared/layout/ShellIcon";
import { AiAnalysisDrawer } from "../aianalysis/AiAnalysisDrawer";
import { useOptionalAccountSession } from "../commonops/AccountSessionBoundary";
import {
  AccountLogout,
  AccountMyInfo,
  AccountPasswordDialog,
} from "../commonops/AccountAccessPages";
import {
  firstRoleLabel,
  PASSWORD_CHANGED_MESSAGE,
  roleLabels,
  roleShortLabels,
} from "../commonops/accountAccessApi";
import { NotificationBell } from "../notifications/NotificationBell";
import { SessionTimer, useOptionalSession } from "../session/SessionProvider";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { useCanViewScreen } from "../../shared/permission";
import {
  AiAnalysisWorkspaceProvider,
  useOptionalAiAnalysis,
} from "../aianalysis/AiAnalysisWorkspace";

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
  screenId = "UI-26",
  menus,
  mobile,
  available,
  onNavigate,
  menuLoading = false,
  menuError,
  onDirtyChange,
  dirty = false,
}: {
  screenId?: string;
  menus: PreferenceMenu[];
  mobile: boolean;
  available: (id: string) => boolean;
  onNavigate: (id: string) => void;
  menuLoading?: boolean;
  menuError?: string;
  onDirtyChange?: (dirty: boolean) => void;
  /** 저장하지 않은 탭이 있으면 로그아웃 전에 확인한다(명세 ③-4). */
  dirty?: boolean;
}) {
  const account = useOptionalAccountSession();
  const session = useOptionalSession();
  const canView = useCanViewScreen();
  const [notice, setNotice] = useState<{ message: string; kind: ToastKind }>();
  const [menuOpen, setMenuOpen] = useState(false);
  const [analysisOpen, setAnalysisOpen] = useState(false);
  const analysis = useOptionalAiAnalysis();

  const menuAnchor = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: MouseEvent) => {
      if (!menuAnchor.current?.contains(event.target as Node))
        setMenuOpen(false);
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
    "settings" | "search" | "shortcuts" | "info" | "password" | null
  >(null);
  const info = account?.info;
  const accountApi = account?.api;
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
      {/* [AI에게 묻기]는 AI 분석 도우미(UI-26) 조회 권한이 있을 때만(⑦-5). */}
      {canView("UI-26") && (
        <button
          type="button"
          className="hd-ai"
          data-testid="ai-analysis-open"
          aria-label="AI에게 묻기"
          onClick={() => setAnalysisOpen(true)}
        >
          <ShellIcon name="spark" />
          {!mobile && <span>AI에게 묻기</span>}
        </button>
      )}

      {/* 시안 renderHeader: 종은 [AI에게 묻기] 오른쪽. 로그인한 사람에게만(알림은 사람마다). */}
      {info && (
        <NotificationBell
          onNavigate={(path) => {
            const id = /^\/screens\/([^/?#]+)$/.exec(path)?.[1];
            if (id) onNavigate(id);
          }}
        />
      )}

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
            <span data-testid="account-identity">
              {info?.name ?? "사용자 메뉴"}
              {info && (
                <span className="hd-sub"> ({firstRoleLabel(roleShortLabels(info))})</span>
              )}
            </span>
          )}
          <ShellIcon name="chevronDown" className="hd-umenu-chev" />
        </button>
        {menuOpen && (
          <div
            data-testid="user-menu-panel"
            className="preference-user-menu"
            role="menu"
          >
            {/* 시안 userPop 머리: 이름 + 「첫 역할 외 n」(마우스를 올리면 역할 전체, 명세 ④-1) */}
            <div className="hd-pop-h">
              <span>{info?.name ?? "사용자 메뉴"}</span>
              {info && (
                <span
                  className="hd-cap"
                  title={roleLabels(info).join(", ") || undefined}
                >
                  {firstRoleLabel(roleLabels(info))}
                </span>
              )}
            </div>
            {(
              [
                ["account-open-info", "내 정보", "info", Boolean(accountApi)],
                [
                  "account-open-password",
                  "비밀번호 변경",
                  "password",
                  Boolean(accountApi),
                ],
                ["screen-settings-open", "화면 설정", "settings", true],
                ["shortcut-help-open", "단축키 보기", "shortcuts", true],
              ] as const
            )
              .filter((item) => item[3])
              .map(([testId, label, target]) => (
                <button
                  key={testId}
                  type="button"
                  role="menuitem"
                  className="hd-pop-i"
                  data-testid={testId}
                  onClick={() => {
                    setMenuOpen(false);
                    setDialog(target);
                  }}
                >
                  {label}
                </button>
              ))}
            {accountApi && account?.signOut && (
              <AccountLogout
                api={accountApi}
                dirty={dirty}
                menuItem
                className="hd-pop-i"
                onLoggedOut={account.signOut}
              />
            )}
            {session && (
              <SessionTimer
                onExtended={(message) => setNotice({ message, kind: "ok" })}
              />
            )}
          </div>
        )}
      </div>
      {dialog === "info" && accountApi && (
        <AccountMyInfo api={accountApi} onClose={() => setDialog(null)} />
      )}
      {dialog === "password" && accountApi && account && (
        <AccountPasswordDialog
          api={accountApi}
          meta={account.meta}
          onClose={() => setDialog(null)}
          onCompleted={() => {
            setDialog(null);
            setNotice({ message: PASSWORD_CHANGED_MESSAGE, kind: "ok" });
            // 바뀐 수정 시각·변경 날짜를 다시 받는다(다음 변경 때 409가 나지 않게).
            void account.refresh().catch(() => undefined);
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice.message}
          kind={notice.kind}
          testId="account-header-message"
          onDismiss={() => setNotice(undefined)}
        />
      )}
      {analysisOpen &&
        (analysis ? (
          <AiAnalysisDrawer
            screenId={screenId}
            onClose={() => setAnalysisOpen(false)}
          />
        ) : (
          <AiAnalysisWorkspaceProvider>
            <AiAnalysisDrawer
              screenId={screenId}
              onClose={() => setAnalysisOpen(false)}
            />
          </AiAnalysisWorkspaceProvider>
        ))}

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
    /** 화면 뿌리. 숨은 탭 안이면 키를 막지 않고 그냥 넘긴다(보이는 탭이 받게, 00-1 코드 리뷰 2026-11-23). */
    scope?: RefObject<HTMLElement>;
  },
  enabled = true,
) {
  useEffect(() => {
    if (!enabled) return;
    const keydown = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;
      if (handlers.scope?.current?.closest("[hidden]")) return;
      const save =
        (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s";
      // 숨은 화면 탭 안에 열려 있는 대화상자(상세 패널)는 세지 않는다(보이는 탭 Ctrl+S가 막히지 않게).
      const dialogs = Array.from(
        document.querySelectorAll('[role="dialog"], [role="alertdialog"]'),
      ).filter((dialog) => !dialog.closest("[hidden]"));
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
        if (save && handlers.onSave) {
          event.preventDefault();
          handlers.onSave();
        }
        return;
      }
      const target = event.target;
      // 입력칸에서는 Ctrl/Cmd+S만 받는다(입력 중에도 저장). Enter·Ctrl+화살표는 입력칸 동작에 맡긴다.
      const editing =
        target instanceof Element &&
        target.matches("input,select,textarea,[contenteditable='true']");
      // 단추·링크·탭에서 누른 Enter는 그 단추가 받는다(조회로 가로채지 않는다).
      const control =
        target instanceof Element &&
        !!target.closest(
          "button,a[href],summary,[role='button'],[role='tab'],[role='checkbox'],[role='menuitem'],[role='link']",
        );
      if (editing && !save) return;
      const handler = save
        ? handlers.onSave
        : event.ctrlKey && event.key === "ArrowLeft"
          ? handlers.onPreviousPage
          : event.ctrlKey && event.key === "ArrowRight"
            ? handlers.onNextPage
            : !event.ctrlKey &&
                !event.metaKey &&
                event.key === "Enter" &&
                !control
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

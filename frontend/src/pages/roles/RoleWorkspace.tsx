import { createContext, useCallback, useContext, useState, type ReactNode } from "react";

export type RoleDialog = "add" | "copy" | "history" | null;

/** 제목줄(틀)과 권한 화면 본문이 나눠 쓰는 상태: 열 창, 변경 이력 건수, 버튼 잠금. */
const RoleWorkspace = createContext({
  dialog: null as RoleDialog,
  setDialog: (_dialog: RoleDialog) => {},
  historyCount: null as number | null,
  setHistoryCount: (_count: number | null) => {},
  ready: false,
  setReady: (_ready: boolean) => {},
  /** 추가한 역할을 보고 있을 때만 있는 [사용 중지] 동작(기본 5개 역할은 null). */
  stopRole: null as (() => void) | null,
  setStopRole: (_stop: (() => void) | null) => {},
});
export function RoleWorkspaceProvider({ children }: { children: ReactNode }) {
  const [dialog, setDialog] = useState<RoleDialog>(null);
  const [historyCount, setHistoryCount] = useState<number | null>(null);
  const [ready, setReady] = useState(false);
  const [stopRole, setStopRoleState] = useState<(() => void) | null>(null);
  // 함수를 상태로 둘 때는 감싸서 넣는다(그냥 넣으면 React가 갱신 함수로 부른다).
  // 늘 같은 함수여야 한다: 화면이 이 함수를 useEffect 의존값으로 쓰므로, 바뀌면 다시 걸고 또 바뀌는 무한 반복이 된다.
  const setStopRole = useCallback((stop: (() => void) | null) => setStopRoleState(() => stop), []);
  return (
    <RoleWorkspace.Provider
      value={{
        dialog,
        setDialog,
        historyCount,
        setHistoryCount,
        ready,
        setReady,
        stopRole,
        setStopRole,
      }}
    >
      {children}
    </RoleWorkspace.Provider>
  );
}
export const useRoleWorkspace = () => useContext(RoleWorkspace);

/** 시안 UI-24 제목줄 오른쪽: ([사용 중지]) [변경 이력 (n)] [역할 복사] [+ 역할 추가]. */
export function RoleTitleActions() {
  const { setDialog, historyCount, ready, stopRole } = useRoleWorkspace();
  return (
    <>
      {stopRole && (
        <button
          type="button"
          className="ls-btn-dt"
          data-testid="role-stop"
          disabled={!ready}
          onClick={stopRole}
        >
          사용 중지
        </button>
      )}
      <button
        type="button"
        data-testid="role-history-open"
        disabled={!ready}
        onClick={() => setDialog("history")}
      >
        변경 이력 ({historyCount ?? 0})
      </button>
      <button
        type="button"
        data-testid="role-copy-open"
        disabled={!ready}
        onClick={() => setDialog("copy")}
      >
        역할 복사
      </button>
      <button
        type="button"
        className="roles-add"
        data-testid="role-add-open"
        disabled={!ready}
        onClick={() => setDialog("add")}
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
        역할 추가
      </button>
    </>
  );
}

import { createContext, useContext, useState, type ReactNode } from "react";

export type RoleDialog = "add" | "copy" | "history" | null;

/** 제목줄(틀)과 권한 화면 본문이 나눠 쓰는 상태: 열 창, 변경 이력 건수, 버튼 잠금. */
const RoleWorkspace = createContext({
  dialog: null as RoleDialog,
  setDialog: (_dialog: RoleDialog) => {},
  historyCount: null as number | null,
  setHistoryCount: (_count: number | null) => {},
  ready: false,
  setReady: (_ready: boolean) => {},
});
export function RoleWorkspaceProvider({ children }: { children: ReactNode }) {
  const [dialog, setDialog] = useState<RoleDialog>(null);
  const [historyCount, setHistoryCount] = useState<number | null>(null);
  const [ready, setReady] = useState(false);
  return (
    <RoleWorkspace.Provider
      value={{
        dialog,
        setDialog,
        historyCount,
        setHistoryCount,
        ready,
        setReady,
      }}
    >
      {children}
    </RoleWorkspace.Provider>
  );
}
export const useRoleWorkspace = () => useContext(RoleWorkspace);

/** 시안 UI-24 제목줄 오른쪽: [변경 이력 (n)] [역할 복사] [+ 역할 추가]. */
export function RoleTitleActions() {
  const { setDialog, historyCount, ready } = useRoleWorkspace();
  return (
    <>
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

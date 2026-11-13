import { createContext, useContext, useState, type ReactNode } from "react";

/** 제목줄(틀)의 [+ 메뉴 등록]과 메뉴 화면 본문이 나눠 쓰는 상태. 틀 밖(단독 시험)에서는 inShell=false. */
const MenuWorkspace = createContext({
  inShell: false,
  openSignal: 0,
  requestOpen: () => {},
  ready: false,
  setReady: (_ready: boolean) => {},
});
export function MenuWorkspaceProvider({ children }: { children: ReactNode }) {
  const [openSignal, setOpenSignal] = useState(0);
  const [ready, setReady] = useState(false);
  return (
    <MenuWorkspace.Provider
      value={{
        inShell: true,
        openSignal,
        requestOpen: () => setOpenSignal((value) => value + 1),
        ready,
        setReady,
      }}
    >
      {children}
    </MenuWorkspace.Provider>
  );
}
export const useMenuWorkspace = () => useContext(MenuWorkspace);

/** 시안 UI-25 제목줄 오른쪽: 파란 [+ 메뉴 등록]. */
export function MenuTitleActions() {
  const { requestOpen, ready } = useMenuWorkspace();
  return (
    <button
      type="button"
      className="primary-button menu-registration-title"
      data-testid="menu-registration-open"
      disabled={!ready}
      onClick={requestOpen}
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
      메뉴 등록
    </button>
  );
}

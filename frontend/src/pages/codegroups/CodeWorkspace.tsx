import { createContext, useContext, useState, type ReactNode } from "react";

export type CodeAction = "group" | "code" | "map";
export type CodeWorkspaceTab = "codes" | "maps" | "standards" | "history";

const CodeWorkspace = createContext({
  inShell: false,
  signal: { action: "group" as CodeAction, revision: 0 },
  requestOpen: (_action: CodeAction) => {},
  ready: false,
  canAddCode: false,
  setReady: (_value: boolean) => {},
  setCanAddCode: (_value: boolean) => {},
  /** 지금 보이는 탭과 고른 그룹이 수당코드(값 그룹)인지: 제목줄 단추를 시안대로 바꾼다. */
  tab: "codes" as CodeWorkspaceTab,
  setTab: (_tab: CodeWorkspaceTab) => {},
  numeric: false,
  setNumeric: (_value: boolean) => {},
});
export function CodeWorkspaceProvider({ children }: { children: ReactNode }) {
  const [signal, setSignal] = useState({
    action: "group" as CodeAction,
    revision: 0,
  });
  const [ready, setReady] = useState(false);
  const [canAddCode, setCanAddCode] = useState(false);
  const [tab, setTab] = useState<CodeWorkspaceTab>("codes");
  const [numeric, setNumeric] = useState(false);
  return (
    <CodeWorkspace.Provider
      value={{
        inShell: true,
        signal,
        requestOpen: (action) =>
          setSignal((current) => ({ action, revision: current.revision + 1 })),
        ready,
        canAddCode,
        setReady,
        setCanAddCode,
        tab,
        setTab,
        numeric,
        setNumeric,
      }}
    >
      {children}
    </CodeWorkspace.Provider>
  );
}
export const useCodeWorkspace = () => useContext(CodeWorkspace);

const plus = (
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
);

/**
 * 시안 UI-27 제목줄 오른쪽 단추(탭마다 다름):
 * 코드 탭 = [그룹 추가] (수당코드를 고르면 [수당코드 추가]), 엑셀 표기 대응 = [추가], 나머지 탭 = 없음.
 */
export function CodeTitleActions() {
  const workspace = useCodeWorkspace();
  if (workspace.tab === "maps")
    return (
      <button
        type="button"
        className="primary-button code-title-button"
        data-testid="code-map-title-add"
        onClick={() => workspace.requestOpen("map")}
      >
        {plus}
        추가
      </button>
    );
  if (workspace.tab !== "codes") return null;
  if (workspace.numeric)
    return (
      <button
        type="button"
        className="primary-button code-title-button"
        data-testid="code-title-add"
        disabled={!workspace.ready || !workspace.canAddCode}
        onClick={() => workspace.requestOpen("code")}
      >
        {plus}
        수당코드 추가
      </button>
    );
  return (
    <button
      type="button"
      className="code-title-button"
      data-testid="code-group-title-add"
      disabled={!workspace.ready}
      onClick={() => workspace.requestOpen("group")}
    >
      {plus}
      그룹 추가
    </button>
  );
}

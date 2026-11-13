import { createContext, useContext, useState, type ReactNode } from "react";

const CodeWorkspace = createContext({
  inShell: false,
  signal: { action: "group" as "group" | "code", revision: 0 },
  requestOpen: (_action: "group" | "code") => {},
  ready: false,
  canAddCode: false,
  setReady: (_value: boolean) => {},
  setCanAddCode: (_value: boolean) => {},
});
export function CodeWorkspaceProvider({ children }: { children: ReactNode }) {
  const [signal, setSignal] = useState({
    action: "group" as "group" | "code",
    revision: 0,
  });
  const [ready, setReady] = useState(false);
  const [canAddCode, setCanAddCode] = useState(false);
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
      }}
    >
      {children}
    </CodeWorkspace.Provider>
  );
}
export const useCodeWorkspace = () => useContext(CodeWorkspace);
export function CodeTitleActions() {
  const workspace = useCodeWorkspace();
  return (
    <div className="app-actions">
      <button
        type="button"
        data-testid="code-group-title-add"
        disabled={!workspace.ready}
        onClick={() => workspace.requestOpen("group")}
      >
        그룹 추가
      </button>
      {workspace.canAddCode && (
        <button
          type="button"
          className="primary-button"
          data-testid="code-title-add"
          disabled={!workspace.ready}
          onClick={() => workspace.requestOpen("code")}
        >
          코드 추가
        </button>
      )}
    </div>
  );
}

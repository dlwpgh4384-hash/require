import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { CandidatePage } from "./CandidatePage";

const CandidateWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function CandidateWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <CandidateWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </CandidateWorkspaceContext.Provider>
  );
}
export function CandidateTitleActions() {
  const context = useContext(CandidateWorkspaceContext);
  return <div ref={context.setHost} data-testid="candidate-title-actions" />;
}
/** 화면 뿌리(screen-body)는 CandidatePage 하나만 둔다(겹쳐 감싸지 않는다). */
export function CandidateWorkspace() {
  const context = useContext(CandidateWorkspaceContext);
  return (
    <CandidatePage
      testId="candidate-workspace"
      renderActions={
        context.host
          ? (actions) => createPortal(actions, context.host!)
          : undefined
      }
    />
  );
}

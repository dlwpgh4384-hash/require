import { createContext, useContext, useState, type ReactNode } from "react";

const CashFlowStatementWorkspace = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function CashFlowStatementWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <CashFlowStatementWorkspace.Provider value={{ host, setHost }}>
      {children}
    </CashFlowStatementWorkspace.Provider>
  );
}
export function CashFlowStatementTitleActions() {
  const context = useContext(CashFlowStatementWorkspace);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="cash-flow-title-actions"
    />
  );
}
export const useCashFlowStatementTitleHost = () =>
  useContext(CashFlowStatementWorkspace).host;

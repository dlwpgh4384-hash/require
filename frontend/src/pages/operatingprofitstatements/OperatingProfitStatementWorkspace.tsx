import { createContext, useContext, useState, type ReactNode } from "react";

const OperatingProfitStatementWorkspace = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function OperatingProfitStatementWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <OperatingProfitStatementWorkspace.Provider value={{ host, setHost }}>
      {children}
    </OperatingProfitStatementWorkspace.Provider>
  );
}
export function OperatingProfitStatementTitleActions() {
  const context = useContext(OperatingProfitStatementWorkspace);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="operating-title-actions"
    />
  );
}
export const useOperatingProfitStatementTitleHost = () =>
  useContext(OperatingProfitStatementWorkspace).host;

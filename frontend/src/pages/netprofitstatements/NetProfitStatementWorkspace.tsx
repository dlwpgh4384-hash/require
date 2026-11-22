import { createContext, useContext, useState, type ReactNode } from "react";

const NetProfitStatementWorkspace = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function NetProfitStatementWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <NetProfitStatementWorkspace.Provider value={{ host, setHost }}>
      {children}
    </NetProfitStatementWorkspace.Provider>
  );
}
export function NetProfitStatementTitleActions() {
  const context = useContext(NetProfitStatementWorkspace);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="net-profit-title-actions"
    />
  );
}
export const useNetProfitStatementTitleHost = () =>
  useContext(NetProfitStatementWorkspace).host;

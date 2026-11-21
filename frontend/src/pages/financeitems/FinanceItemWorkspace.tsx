import { createContext, useContext, useState, type ReactNode } from "react";
const FinanceItemWorkspaceContext = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function FinanceItemWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <FinanceItemWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </FinanceItemWorkspaceContext.Provider>
  );
}
export function FinanceItemTitleActions() {
  const context = useContext(FinanceItemWorkspaceContext);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="finance-item-title-actions"
    />
  );
}
export const useFinanceItemTitleHost = () =>
  useContext(FinanceItemWorkspaceContext).host;

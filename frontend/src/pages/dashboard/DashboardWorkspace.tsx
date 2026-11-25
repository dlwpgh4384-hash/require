import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { UI16Page } from "./UI16Page";

const DashboardContext = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function DashboardWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <DashboardContext.Provider value={{ host, setHost }}>
      {children}
    </DashboardContext.Provider>
  );
}
export function DashboardTitleActions() {
  const { setHost } = useContext(DashboardContext);
  return (
    <div
      ref={setHost}
      className="project-title-actions"
      data-testid="dashboard-title-actions"
    />
  );
}
export function DashboardWorkspace() {
  const { host } = useContext(DashboardContext);
  return (
    <UI16Page
      renderActions={
        host ? (actions) => createPortal(actions, host) : () => null
      }
    />
  );
}

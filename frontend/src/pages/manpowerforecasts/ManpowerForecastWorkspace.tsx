import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { UI17Page } from "./UI17Page";

const ManpowerForecastWorkspaceContext = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function ManpowerForecastWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <ManpowerForecastWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </ManpowerForecastWorkspaceContext.Provider>
  );
}
export function ManpowerForecastTitleActions() {
  const { setHost } = useContext(ManpowerForecastWorkspaceContext);
  return (
    <div
      ref={setHost}
      className="project-title-actions"
      data-testid="manpower-forecast-title-actions"
    />
  );
}
export function ManpowerForecastWorkspace() {
  const { host } = useContext(ManpowerForecastWorkspaceContext);
  return (
    <UI17Page
      renderActions={
        host ? (actions) => createPortal(actions, host) : () => null
      }
    />
  );
}

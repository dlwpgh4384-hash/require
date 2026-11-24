import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { UI28Page } from "./UI28Page";

const ManpowerBoardTitleContext = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });

export function ManpowerBoardWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <ManpowerBoardTitleContext.Provider value={{ host, setHost }}>
      {children}
    </ManpowerBoardTitleContext.Provider>
  );
}
export function ManpowerBoardTitleActions() {
  const { setHost } = useContext(ManpowerBoardTitleContext);
  return (
    <div
      ref={setHost}
      className="project-title-actions"
      data-testid="manpower-board-title-actions"
    />
  );
}
export function ManpowerBoardWorkspace() {
  const { host } = useContext(ManpowerBoardTitleContext);
  return (
    <UI28Page
      renderActions={
        host ? (actions) => createPortal(actions, host) : undefined
      }
    />
  );
}

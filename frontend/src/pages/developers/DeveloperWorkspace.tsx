import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { DeveloperPage } from "./DeveloperPage";

const DeveloperWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function DeveloperWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <DeveloperWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </DeveloperWorkspaceContext.Provider>
  );
}
export function DeveloperTitleActions() {
  const context = useContext(DeveloperWorkspaceContext);
  return <div ref={context.setHost} data-testid="developer-title-actions" />;
}
export function DeveloperWorkspace() {
  const context = useContext(DeveloperWorkspaceContext);
  return (
    <div
      className="screen-body developer-workspace"
      data-testid="developer-workspace"
      data-screen-id="UI-02"
    >
      <DeveloperPage
        renderActions={
          context.host
            ? (actions) => createPortal(actions, context.host!)
            : undefined
        }
      />
    </div>
  );
}

import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { ClientPage } from "./ClientPage";

const ClientPartContext = createContext({
  tab: "clients",
  setTab: (_value: string) => {},
  createSignal: 0,
  requestCreate: () => {},
  inShell: false,
  actionsHost: null as HTMLDivElement | null,
  setActionsHost: (_host: HTMLDivElement | null) => {},
  addingPart: false,
  setAddingPart: (_value: boolean) => {},
});
export function ClientPartWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [tab, setTab] = useState("clients");
  const [createSignal, setCreateSignal] = useState(0);
  const [actionsHost, setActionsHost] = useState<HTMLDivElement | null>(null);
  const [addingPart, setAddingPart] = useState(false);
  return (
    <ClientPartContext.Provider
      value={{
        tab,
        setTab,
        createSignal,
        requestCreate: () => setCreateSignal((value) => value + 1),
        inShell: true,
        actionsHost,
        setActionsHost,
        addingPart,
        setAddingPart,
      }}
    >
      {children}
    </ClientPartContext.Provider>
  );
}
export const useClientPartWorkspace = () => useContext(ClientPartContext);
export function ClientPartTitleActions({
  partActions,
  uploadActions,
}: {
  partActions?: ReactNode;
  uploadActions?: ReactNode;
}) {
  const workspace = useContext(ClientPartContext);
  return workspace.tab === "parts" ? (
    <>{partActions}</>
  ) : (
    <>
      {uploadActions}
      <div ref={workspace.setActionsHost} data-testid="client-title-actions" />
    </>
  );
}
export function ClientPartWorkspace({ partPage }: { partPage: ReactNode }) {
  const workspace = useContext(ClientPartContext);
  const [localTab, setLocalTab] = useState("clients");
  const tab = workspace.inShell ? workspace.tab : localTab;
  const setTab = workspace.inShell ? workspace.setTab : setLocalTab;
  return (
    <section className="screen-body client-part-workspace" data-testid="client-part-workspace" data-screen-id="UI-15">
      <nav aria-label="거래처·영업 파트" className="client-workspace-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={tab === "clients"}
          data-testid="client-workspace-clients"
          aria-pressed={tab === "clients"}
          onClick={() => setTab("clients")}
        >
          거래처
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "parts"}
          data-testid="client-workspace-parts"
          aria-pressed={tab === "parts"}
          onClick={() => setTab("parts")}
        >
          영업 파트
        </button>
      </nav>
      <div data-testid={`client-workspace-${tab}-panel`}>
        {tab === "clients" ? (
          <ClientPage
            createSignal={workspace.createSignal}
            renderActions={
              workspace.inShell && workspace.actionsHost
                ? (actions) => createPortal(actions, workspace.actionsHost!)
                : undefined
            }
          />
        ) : (
          partPage
        )}
      </div>
    </section>
  );
}

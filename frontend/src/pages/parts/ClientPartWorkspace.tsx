import type { ReactNode } from "react";
import { useClientPartWorkspace } from "../clients/ClientPartWorkspace";
import "./parts.css";

export {
  ClientPartWorkspaceProvider,
  useClientPartWorkspace,
} from "../clients/ClientPartWorkspace";

/** Both tab renderers consume the shell's single UI-15 workspace state. */
export function ClientPartWorkspace({
  clients,
  parts,
}: {
  clients: ReactNode;
  parts: ReactNode;
}) {
  const workspace = useClientPartWorkspace();
  return (
    <section data-testid="client-part-workspace" data-screen-id="UI-15">
      <div role="tablist" aria-label="거래처·영업 파트" className="part-tabs">
        <button
          type="button"
          role="tab"
          aria-selected={workspace.tab === "clients"}
          data-testid="client-part-clients-tab"
          onClick={() => workspace.setTab("clients")}
        >
          거래처
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={workspace.tab === "parts"}
          data-testid="client-part-parts-tab"
          onClick={() => workspace.setTab("parts")}
        >
          영업 파트
        </button>
      </div>
      <div role="tabpanel" data-testid={`client-part-${workspace.tab}-panel`}>
        {workspace.tab === "clients" ? clients : parts}
      </div>
    </section>
  );
}
export function ClientPartTitleActions({
  clientActions,
}: {
  clientActions?: ReactNode;
}) {
  const workspace = useClientPartWorkspace();
  if (workspace.tab === "clients") return <>{clientActions}</>;
  return <PartTitleActions />;
}
export function PartTitleActions() {
  const workspace = useClientPartWorkspace();
  return (
    <button
      type="button"
      className="primary-button"
      data-testid="part-title-add"
      onClick={() => workspace.setAddingPart(true)}
    >
      파트 추가
    </button>
  );
}

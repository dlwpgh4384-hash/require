import { createContext, useContext, useState, type ReactNode } from "react";

const BusinessPlanComparisonWorkspace = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function BusinessPlanComparisonWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <BusinessPlanComparisonWorkspace.Provider value={{ host, setHost }}>
      {children}
    </BusinessPlanComparisonWorkspace.Provider>
  );
}
export function BusinessPlanComparisonTitleActions() {
  const { setHost } = useContext(BusinessPlanComparisonWorkspace);
  return (
    <div
      ref={setHost}
      className="project-title-actions"
      data-testid="business-plan-title-actions"
    />
  );
}
export const useBusinessPlanComparisonTitleHost = () =>
  useContext(BusinessPlanComparisonWorkspace).host;

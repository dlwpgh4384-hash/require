import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

export function useProjectWorkspaceState() {
  const [revision, setRevision] = useState(0);
  const [registering, setRegistering] = useState(false);
  const [readOnly, setReadOnly] = useState(false);
  return useMemo(
    () => ({
      revision,
      registering,
      readOnly,
      setReadOnly,
      openRegistration: () => setRegistering(true),
      closeRegistration: () => setRegistering(false),
      refresh: () => setRevision((n) => n + 1),
    }),
    [revision, registering, readOnly],
  );
}
type ProjectWorkspace = ReturnType<typeof useProjectWorkspaceState>;
const Context = createContext<ProjectWorkspace | null>(null);
export function ProjectWorkspaceProvider({
  children,
  value,
}: {
  children: ReactNode;
  value?: ProjectWorkspace;
}) {
  const owned = useProjectWorkspaceState();
  return <Context.Provider value={value ?? owned}>{children}</Context.Provider>;
}
export const useOptionalProjectWorkspace = () => useContext(Context);
export function ProjectTitleActions() {
  const workspace = useOptionalProjectWorkspace();
  return (
    <button
      type="button"
      className="primary-button"
      data-testid="project-title-register"
      disabled={!workspace || workspace.readOnly}
      onClick={() => workspace?.openRegistration()}
    >
      프로젝트 등록
    </button>
  );
}

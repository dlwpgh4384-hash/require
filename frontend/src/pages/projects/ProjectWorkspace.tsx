import { createContext, useContext, useState, type ReactNode } from "react";

/** 화면 틀 제목줄 오른쪽 자리. 프로젝트 화면이 이 자리에 [양식 내려받기][엑셀 업로드][+ 등록]을 그린다(시안). */
const ProjectWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function ProjectWorkspaceProvider({ children }: { children: ReactNode }) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <ProjectWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </ProjectWorkspaceContext.Provider>
  );
}
export function ProjectTitleActions() {
  const context = useContext(ProjectWorkspaceContext);
  return <div ref={context.setHost} className="project-title-actions" data-testid="project-title-actions" />;
}
export const useProjectTitleHost = () => useContext(ProjectWorkspaceContext).host;

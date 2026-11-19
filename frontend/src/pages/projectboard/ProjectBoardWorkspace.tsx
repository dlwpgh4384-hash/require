import { createContext, useContext, useState, type ReactNode } from "react";

/**
 * 화면 틀 제목줄 오른쪽 자리(프로젝트 현황판 전용). 이슈 모아 보기 탭의 [이슈 등록]을 여기에 그린다(시안 actions).
 * 다른 화면과 한 자리를 같이 쓰면 두 탭이 함께 열렸을 때 단추가 섞이므로 화면마다 따로 둔다.
 */
const ProjectBoardWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function ProjectBoardWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <ProjectBoardWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </ProjectBoardWorkspaceContext.Provider>
  );
}
export function ProjectBoardTitleActions() {
  const context = useContext(ProjectBoardWorkspaceContext);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="project-board-title-actions"
    />
  );
}
export const useProjectBoardTitleHost = () =>
  useContext(ProjectBoardWorkspaceContext).host;

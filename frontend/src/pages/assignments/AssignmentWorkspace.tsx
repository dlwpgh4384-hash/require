import { createContext, useContext, useState, type ReactNode } from "react";

/**
 * 화면 틀 제목줄 오른쪽 자리(투입 배정 전용). 프로젝트 화면과 한 자리를 같이 쓰면
 * 두 탭이 함께 열렸을 때 두 화면의 단추가 한 자리에 두 줄로 들어간다(2026-11-18 사용자 발견).
 */
const AssignmentWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function AssignmentWorkspaceProvider({ children }: { children: ReactNode }) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <AssignmentWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </AssignmentWorkspaceContext.Provider>
  );
}
export function AssignmentTitleActions() {
  const context = useContext(AssignmentWorkspaceContext);
  return <div ref={context.setHost} className="project-title-actions" data-testid="assignment-title-actions" />;
}
export const useAssignmentTitleHost = () => useContext(AssignmentWorkspaceContext).host;

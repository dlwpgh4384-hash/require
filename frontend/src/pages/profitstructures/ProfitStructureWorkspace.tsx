import { createContext, useContext, useState, type ReactNode } from "react";

/**
 * 화면 틀 제목줄 오른쪽 자리(전체 수익 구조 전용). 시안 actions의 [엑셀 다운로드]를 여기에 그린다.
 * 다른 화면과 한 자리를 같이 쓰면 두 탭이 함께 열렸을 때 단추가 섞이므로 화면마다 따로 둔다.
 */
const ProfitStructureWorkspaceContext = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function ProfitStructureWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <ProfitStructureWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </ProfitStructureWorkspaceContext.Provider>
  );
}
export function ProfitStructureTitleActions() {
  const context = useContext(ProfitStructureWorkspaceContext);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="profit-title-actions"
    />
  );
}
export const useProfitStructureTitleHost = () =>
  useContext(ProfitStructureWorkspaceContext).host;

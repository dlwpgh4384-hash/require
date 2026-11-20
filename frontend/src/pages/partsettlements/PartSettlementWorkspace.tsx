import { createContext, useContext, useState, type ReactNode } from "react";

/**
 * 화면 틀 제목줄 오른쪽 자리(영업 파트 이익금 전용). [정산 확정]·[확정 취소]를 여기에 그린다(시안 actions).
 * 다른 화면과 한 자리를 같이 쓰면 두 탭이 함께 열렸을 때 단추가 섞이므로 화면마다 따로 둔다.
 * 엑셀 다운로드는 목록 도구줄 한 곳에만 둔다(제목줄에는 두지 않음).
 */
const PartSettlementWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function PartSettlementWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <PartSettlementWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </PartSettlementWorkspaceContext.Provider>
  );
}
export function PartSettlementTitleActions() {
  const context = useContext(PartSettlementWorkspaceContext);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="part-settlement-title-actions"
    />
  );
}
export const usePartSettlementTitleHost = () =>
  useContext(PartSettlementWorkspaceContext).host;

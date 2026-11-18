import { createContext, useContext, useState, type ReactNode } from "react";

/**
 * 화면 틀 제목줄 오른쪽 자리(철수 리스트 전용). 다른 화면과 한 자리를 같이 쓰면
 * 두 탭이 함께 열렸을 때 두 화면의 단추가 한 자리에 두 줄로 들어간다(2026-11-18 사용자 발견, 투입 배정과 같은 문제).
 */
const WithdrawalWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function WithdrawalWorkspaceProvider({ children }: { children: ReactNode }) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <WithdrawalWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </WithdrawalWorkspaceContext.Provider>
  );
}
export function WithdrawalTitleActions() {
  const context = useContext(WithdrawalWorkspaceContext);
  return <div ref={context.setHost} className="project-title-actions" data-testid="withdrawal-title-actions" />;
}
export const useWithdrawalTitleHost = () => useContext(WithdrawalWorkspaceContext).host;

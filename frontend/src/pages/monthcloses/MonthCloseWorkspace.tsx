import { createContext, useContext, useState, type ReactNode } from "react";

/**
 * 화면 틀 제목줄 오른쪽 자리(월 마감 전용). [다시 점검]·[마감하기]·[마감 해제]를 여기에 그린다(시안 actions).
 * 다른 화면과 한 자리를 같이 쓰면 두 탭이 함께 열렸을 때 단추가 섞이므로 화면마다 따로 둔다.
 */
const MonthCloseWorkspaceContext = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function MonthCloseWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <MonthCloseWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </MonthCloseWorkspaceContext.Provider>
  );
}
export function MonthCloseTitleActions() {
  const context = useContext(MonthCloseWorkspaceContext);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="month-close-title-actions"
    />
  );
}
export const useMonthCloseTitleHost = () =>
  useContext(MonthCloseWorkspaceContext).host;

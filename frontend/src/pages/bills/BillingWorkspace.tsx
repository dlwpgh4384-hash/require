import { createContext, useContext, useState, type ReactNode } from "react";

/**
 * 화면 틀 제목줄 오른쪽 자리(청구·입금 전용). [+ 청구 등록]을 여기에 그린다(시안 actions).
 * 다른 화면과 한 자리를 같이 쓰면 두 탭이 함께 열렸을 때 단추가 섞이므로 화면마다 따로 둔다.
 * 엑셀 다운로드는 목록 도구줄 한 곳에만 둔다(제목줄에는 두지 않음).
 */
const BillingWorkspaceContext = createContext({
  host: null as HTMLDivElement | null,
  setHost: (_host: HTMLDivElement | null) => {},
});
export function BillingWorkspaceProvider({ children }: { children: ReactNode }) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <BillingWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </BillingWorkspaceContext.Provider>
  );
}
export function BillingTitleActions() {
  const context = useContext(BillingWorkspaceContext);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="billing-title-actions"
    />
  );
}
export const useBillingTitleHost = () =>
  useContext(BillingWorkspaceContext).host;

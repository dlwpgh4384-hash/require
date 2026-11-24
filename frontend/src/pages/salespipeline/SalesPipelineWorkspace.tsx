import { createContext, useContext, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { SalesPipelinePage } from "./SalesPipelinePage";

/** 시안 titleBar: 탭마다 다른 단추([＋ 기회 등록]·「사용 중지 포함」·[⬇ 엑셀 다운로드])는 제목줄 오른쪽에 둔다. */
const SalesPipelineWorkspaceContext = createContext<{
  host: HTMLDivElement | null;
  setHost: (host: HTMLDivElement | null) => void;
}>({ host: null, setHost: () => undefined });
export function SalesPipelineWorkspaceProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [host, setHost] = useState<HTMLDivElement | null>(null);
  return (
    <SalesPipelineWorkspaceContext.Provider value={{ host, setHost }}>
      {children}
    </SalesPipelineWorkspaceContext.Provider>
  );
}
export function SalesPipelineTitleActions() {
  const context = useContext(SalesPipelineWorkspaceContext);
  return (
    <div
      ref={context.setHost}
      className="project-title-actions"
      data-testid="sales-pipeline-title-actions"
    />
  );
}
/** 화면 뿌리(screen-body)는 SalesPipelinePage 하나만 둔다(겹쳐 감싸지 않는다). */
export function SalesPipelineWorkspace() {
  const { host } = useContext(SalesPipelineWorkspaceContext);
  return (
    <SalesPipelinePage
      renderActions={
        host ? (actions) => createPortal(actions, host) : undefined
      }
    />
  );
}

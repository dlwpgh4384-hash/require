import { DetailPanel } from "../../shared/list";
import { currentScreenPeriod } from "../../shared/layout/screenPeriodChannel";
import { AiAnalysisConversation } from "./AiAnalysisConversation";
import "./aianalysis.css";

/**
 * 시안 ai: openDrawer({title:'AI에게 묻기', sub:'지금 화면을 보면서 질문할 수 있습니다'}):
 * 오른쪽 600px 상세 창(공통 DetailPanel). 바깥 클릭·Esc로 닫고, 닫으면 헤더 단추로 초점이 돌아간다.
 * 지금 화면 ID와 그 화면 기간(보내는 그때의 기간)을 같이 보낸다.
 */
export function AiAnalysisDrawer({
  screenId,
  onClose,
}: {
  screenId: string;
  onClose: () => void;
}) {
  return (
    <DetailPanel
      title="AI에게 묻기"
      sub="지금 화면을 보면서 질문할 수 있습니다"
      onClose={onClose}
      testId="ai-analysis-dialog"
    >
      <AiAnalysisConversation
        screenId={screenId}
        readPeriod={() => currentScreenPeriod(screenId)}
        onClose={onClose}
      />
    </DetailPanel>
  );
}

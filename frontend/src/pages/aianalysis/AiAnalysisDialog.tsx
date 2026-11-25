import { Dialog } from "../../shared/feedback/Dialog";
import { currentScreenPeriod } from "../../shared/layout/screenPeriodChannel";
import { AiAnalysisConversation } from "./AiAnalysisConversation";
import "./aianalysis.css";

export function AiAnalysisDialog({
  screenId,
  onClose,
}: {
  screenId: string;
  onClose: () => void;
}) {
  const period = currentScreenPeriod(screenId);
  return (
    <Dialog
      title="AI에게 묻기"
      onClose={onClose}
      testId="ai-analysis-dialog"
      closable
    >
      <div
        className="ai-analysis-dialog"
        data-testid="analysis-modal-content"
        data-screen-id="UI-38"
      >
        <p>지금 화면을 보면서 질문할 수 있습니다</p>
        <p>
          현재 화면 · 기간: {screenId} ·{" "}
          {period ? `${period.startMonth}~${period.endMonth}` : "이번 달"}
        </p>
        <AiAnalysisConversation
          screenId={screenId}
          period={period}
          onClose={onClose}
        />
      </div>
    </Dialog>
  );
}

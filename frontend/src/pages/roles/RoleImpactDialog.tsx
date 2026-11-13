import { Dialog } from "../../shared/feedback/Dialog";
import type { RoleImpact } from "./roles";

/** UI-24-M2 */
export function RoleImpactDialog({
  impact,
  busy,
  onCancel,
  onSave,
}: {
  impact: RoleImpact;
  busy: boolean;
  onCancel: () => void;
  onSave: () => void;
}) {
  return (
    <Dialog
      title="담당범위 정리 확인"
      testId="role-impact-dialog"
      onClose={() => {
        if (!busy) onCancel();
      }}
    >
      {impact.headquartersUserCount > 0 && (
        <p>{impact.headquartersUserCount}명의 본사 선택이 지워집니다.</p>
      )}
      {impact.projectUserCount > 0 && (
        <p>{impact.projectUserCount}명의 담당 프로젝트가 지워집니다.</p>
      )}
      {impact.affectedUserIds.length === 0 && (
        <p>현재 정리할 담당범위가 없습니다. 다시 확인한 설정을 저장합니다.</p>
      )}
      <p>PM 계정의 담당 프로젝트와 다른 역할로 유지되는 범위는 남습니다.</p>
      <div className="app-actions">
        <button
          type="button"
          data-testid="role-impact-cancel"
          disabled={busy}
          onClick={onCancel}
        >
          취소
        </button>
        <button
          type="button"
          data-testid="role-impact-save"
          className="primary-button"
          disabled={busy}
          onClick={onSave}
        >
          저장
        </button>
      </div>
    </Dialog>
  );
}

import { Dialog } from "../../shared/feedback/Dialog";
import { formatDateTime } from "../../shared/time";
import type { RoleChange } from "./roles";

/** 시안 「<역할> · 권한 변경 이력 (n)」 창. 바뀐 항목마다 1줄, 최근 순. */
export function RoleHistoryDialog({
  roleName,
  items,
  failure,
  onRetry,
  onClose,
}: {
  roleName: string;
  items: RoleChange[] | null;
  failure: string;
  onRetry: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog
      title={`${roleName} · 권한 변경 이력 (${items?.length ?? 0})`}
      testId="role-history-dialog"
      onClose={onClose}
    >
      <div className="roles-history">
        {failure ? (
          <p role="alert" className="field-error">
            {failure}{" "}
            <button type="button" onClick={onRetry}>
              다시 시도
            </button>
          </p>
        ) : !items ? (
          <p role="status">변경 이력을 불러오는 중입니다.</p>
        ) : items.length === 0 ? (
          <p className="roles-empty">아직 바꾼 기록이 없습니다.</p>
        ) : (
          <table className="roles-history-table">
            <thead>
              <tr>
                <th scope="col">일시</th>
                <th scope="col">항목</th>
                <th scope="col">바꾸기 전</th>
                <th scope="col">바꾼 뒤</th>
                <th scope="col">바꾼 사람</th>
              </tr>
            </thead>
            <tbody>
              {items.map((change, index) => (
                <tr
                  key={`${change.changedAt}-${index}`}
                  data-testid={`role-history-row-${index}`}
                >
                  <td>{formatDateTime(change.changedAt)}</td>
                  <td>{change.item}</td>
                  <td>{change.before ?? "–"}</td>
                  <td>{change.after ?? "–"}</td>
                  <td>{change.changedBy ?? "–"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div className="app-actions">
        <button
          type="button"
          data-testid="role-history-close"
          onClick={onClose}
        >
          닫기
        </button>
      </div>
    </Dialog>
  );
}

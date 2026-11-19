import { useState } from "react";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { ProjectDetail } from "../projects/ProjectDetail";
import { useOpenProjectEdit } from "../projects/projectEdit";
import { ProjectIssuesPanel } from "./ProjectIssuesPanel";

// UI-31의 이슈 탭 본문. 라우터/현황판은 부모가 소유한다.
export function ProjectIssuesView({
  onChanged,
  onOpenCount,
  revision = 0,
}: {
  onChanged?: (message?: string) => void;
  onOpenCount?: (count: number) => void;
  revision?: number;
}) {
  const [detail, setDetail] = useState<string | null>(null);
  const [localRevision, setLocalRevision] = useState(0);
  const [notice, setNotice] = useState<{
    message: string;
    kind: ToastKind;
  } | null>(null);
  const openEdit = useOpenProjectEdit();
  return (
    <div data-testid="project-issues-view">
      <ProjectIssuesPanel
        revision={revision + localRevision}
        onChanged={onChanged}
        onOpenCount={onOpenCount}
        onOpenProject={setDetail}
      />
      {detail && (
        <ProjectDetail
          key={detail}
          projectId={detail}
          initialTab="issues"
          boardOperations
          dirtyScreenId="UI-31"
          renderIssues={(project, changed) => (
            <ProjectIssuesPanel project={project} onChanged={changed} />
          )}
          onClose={() => {
            setDetail(null);
            setLocalRevision((v) => v + 1);
          }}
          onEdit={(project) => {
            setDetail(null);
            openEdit(project);
          }}
          onChanged={(message, kind) => {
            setLocalRevision((v) => v + 1);
            onChanged?.(message);
            if (message) setNotice({ message, kind: kind ?? "ok" });
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice.message}
          kind={notice.kind}
          testId="project-issues-toast"
          onDismiss={() => setNotice(null)}
        />
      )}
    </div>
  );
}

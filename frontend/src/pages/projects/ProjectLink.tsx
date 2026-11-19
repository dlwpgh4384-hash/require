import { useState } from "react";
import { createPortal } from "react-dom";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { ProjectDetail } from "./ProjectDetail";
import { projectDetailIssues } from "./projectDetailIssues";
import { useOpenProjectEdit } from "./projectEdit";
import "./projects.css";

export function ProjectLink({
  projectId,
  name,
  testId,
  readOnly = false,
}: {
  projectId?: string | null;
  name: string;
  testId: string;
  readOnly?: boolean;
}) {
  const [open, setOpen] = useState(false);
  // 다른 화면(철수 리스트 상세 등)에서 연 상세에서 저장해도 완료 알림이 보이게 한다(2026-11-19 사용자 발견: 인원 계획 저장 뒤 알림 없음).
  const [notice, setNotice] = useState<{ message: string; kind?: ToastKind } | null>(null);
  const openEdit = useOpenProjectEdit();
  if (!projectId) return <span>{name}</span>;
  return (
    <>
      <button
        type="button"
        className="link-button"
        data-testid={testId}
        onClick={(event) => {
          event.stopPropagation();
          setOpen(true);
        }}
      >
        {name}
      </button>
      {/* 상세 창은 표 칸 밖(body)에 그린다. 칸 안에 두면 칸의 줄바꿈 없음·높이 40px·줄 높이를 물려받아
          글자가 겹친다(개발자 풀·거래처에서 연 프로젝트 상세, 2026-11-18 사용자 발견). */}
      {open &&
        createPortal(
          // React 이벤트는 포털을 지나 원래 자리(표 줄)로 올라가므로, 상세 안의 클릭이 줄 클릭이 되지 않게 막는다.
          <div onClick={(event) => event.stopPropagation()}>
            <ProjectDetail
              projectId={projectId}
              readOnly={readOnly}
              // 프로젝트 화면에서 연 상세와 같은 기능: [이번 주 상태 적기]·[이슈 등록] 등(2026-11-19 사용자 결정).
              boardOperations
              renderIssues={projectDetailIssues(readOnly, "")}
              onClose={() => setOpen(false)}
              onEdit={(project) => {
                // 시안 editform: 상세를 닫고 프로젝트 화면의 수정 폼을 연다.
                setOpen(false);
                openEdit(project);
              }}
              onChanged={(message, kind) => {
                if (message) setNotice({ message, kind });
              }}
            />
          </div>,
          document.body,
        )}
      {notice &&
        createPortal(
          <Toast
            message={notice.message}
            kind={notice.kind}
            testId="project-link-toast"
            onDismiss={() => setNotice(null)}
          />,
          document.body,
        )}
    </>
  );
}

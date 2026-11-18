import { useState } from "react";
import { createPortal } from "react-dom";
import { ProjectDetail } from "./ProjectDetail";
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
              onClose={() => setOpen(false)}
            />
          </div>,
          document.body,
        )}
    </>
  );
}

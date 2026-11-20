import type { ReactNode } from "react";
import "./projects.css";

/**
 * 시안 fcF: 창(.modal) 안 한 칸 — 칸 이름(필수면 빨간 *) · 입력 · 도움말을 위에서 아래로.
 * 프로젝트 상세·이슈·주간 메모 창이 같이 쓴다(.project-modal 안에서 시안 크기).
 */
export function ProjectField({
  id,
  label,
  required = false,
  help,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  help?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="project-fc">
      <label htmlFor={id}>
        {label}
        {required && <span className="project-bad"> *</span>}
      </label>
      {children}
      {help && <span className="project-help">{help}</span>}
    </div>
  );
}

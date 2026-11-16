import { useState } from "react";
import { ProjectDetail } from "./ProjectDetail";
import "./projects.css";

export function ProjectLink({
  projectId,
  name,
  testId,
}: {
  projectId?: string | null;
  name: string;
  testId: string;
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
      {open && (
        <ProjectDetail projectId={projectId} onClose={() => setOpen(false)} />
      )}
    </>
  );
}

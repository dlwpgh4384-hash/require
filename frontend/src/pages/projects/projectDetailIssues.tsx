import type { ReactNode } from "react";
import { ProjectIssuesPanel } from "../projectissues/ProjectIssuesPanel";
import type { Project } from "./projects";

/**
 * 프로젝트 상세 [이슈] 탭 본문(안내 글·[이슈 등록]·표). 프로젝트 화면·현황판·이슈 탭·다른 화면에서 연 상세가
 * 모두 이것을 써서 같은 기능을 보인다(2026-11-19 사용자 발견: 다른 화면에서 연 상세에는 [이슈 등록]이 없음).
 * 읽기 전용이면 넘기지 않아 상세의 기본 표만 보인다.
 * dirtyScreenId는 쓰다 만 이슈가 있을 때 「●」를 붙일 화면 탭. 빈 글자면 표시하지 않는다.
 */
export function projectDetailIssues(
  readOnly: boolean,
  dirtyScreenId: string,
): ((project: Project, changed: (message?: string) => void) => ReactNode) | undefined {
  if (readOnly) return undefined;
  return (project, changed) => (
    <ProjectIssuesPanel project={project} onChanged={changed} dirtyScreenId={dirtyScreenId} />
  );
}

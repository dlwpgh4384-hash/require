import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { addDays, formatMonthDay, koreanToday } from "../../shared/time";
import type { CodeItem } from "../codegroups/codeGroups";
import type { Project } from "../projects/projects";
import * as api from "./projectIssues";
// 로그인(UI-22) 전: 헤더와 같은 현재 사용자 이름을 담당자 자리에 보인다(새 이슈 담당자 = 등록한 사람).
import { CURRENT_USER } from "../userpreferences/UserPreferenceTools";
import "../projects/projects.css";
import "./projectIssues.css";

export type ProjectIssueOption = Pick<
  Project,
  "projectId" | "projectName" | "statusCode" | "endDate"
>;
export const projectIssueCanCreate = (project: ProjectIssueOption) =>
  project.statusCode !== "END" &&
  project.statusCode !== "LOSE" &&
  project.endDate >= koreanToday();

/** 시안 issLog: 「10-15 김PM 등록 · 10-20 이관리 고침」(한국 날짜). */
export const projectIssueAudit = (issue: api.ProjectIssue) =>
  `${formatMonthDay(issue.createdAt)} ${issue.createdByName ?? issue.createdBy} 등록` +
  (issue.updatedAt
    ? ` · ${formatMonthDay(issue.updatedAt)} ${issue.updatedByName ?? issue.updatedBy ?? ""} 고침`
    : "");

// UI-31-M3 / SCR-UI-31: 이슈 등록·수정·종료(시안 issueModal). 프로젝트와 담당자(등록한 로그인 계정)는 고치지 않는다.
export function ProjectIssueDialog({
  issue,
  projectId,
  projects,
  importance,
  statuses,
  onClose,
  onSaved,
  dirtyScreenId = "UI-31",
}: {
  issue?: api.ProjectIssue;
  projectId?: string;
  projects: ProjectIssueOption[];
  importance: CodeItem[];
  statuses: CodeItem[];
  onClose: () => void;
  onSaved: (issue: api.ProjectIssue, created: boolean) => void | Promise<void>;
  dirtyScreenId?: string;
}) {
  const [initial] = useState<api.ProjectIssueInput>(() => ({
    projectId: issue?.projectId ?? projectId ?? "",
    title: issue?.title ?? "",
    dueDate: issue?.dueDate ?? addDays(koreanToday(), 7),
    importanceCode:
      issue?.importanceCode ??
      (importance.some((c) => c.code === "MID") ? "MID" : ""),
    statusCode:
      issue?.statusCode ??
      (statuses.some((c) => c.code === "OPEN") ? "OPEN" : ""),
    actionText: issue?.actionText ?? null,
  }));
  const [values, setValues] = useState(initial);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [discard, setDiscard] = useState(false);
  const pending = useRef(false);
  const setDirty = useScreenDirty();
  const dirty = JSON.stringify(initial) !== JSON.stringify(values);
  useEffect(() => {
    setDirty(dirtyScreenId, dirty);
    return () => setDirty(dirtyScreenId, false);
  }, [dirty, dirtyScreenId, setDirty]);
  const close = () => {
    if (!pending.current) {
      if (dirty) setDiscard(true);
      else onClose();
    }
  };
  const update = (name: keyof api.ProjectIssueInput, value: string) =>
    setValues((v) => ({ ...v, [name]: value }));
  const fixedProject = !!issue || !!projectId;
  const projectName =
    issue?.projectName ??
    projects.find((p) => p.projectId === values.projectId)?.projectName ??
    values.projectId;
  const save = async () => {
    if (pending.current) return;
    // 시안 issueModal 검사 문구
    if (!values.projectId) return setError("프로젝트를 골라 주세요.");
    if (!values.title.trim()) return setError("제목을 넣어 주세요.");
    if (!values.dueDate) return setError("기한을 넣어 주세요.");
    if ([...values.title].length > 100)
      return setError("제목은 100자까지 입력해 주세요.");
    if ([...(values.actionText ?? "")].length > 500)
      return setError("조치 내용은 500자까지 입력해 주세요.");
    pending.current = true;
    setBusy(true);
    setError("");
    const body = {
      ...values,
      title: values.title.trim(),
      actionText: values.actionText?.trim() ? values.actionText.trim() : null,
    };
    try {
      const saved = issue
        ? await api.putCommonProjectBoardIssue(
            issue.issueId,
            body,
            issue.modifiedAt,
          )
        : await api.postCommonProjectBoardIssue(body);
      await onSaved(saved, !issue);
    } catch (reason) {
      setError(api.projectIssueFailure(reason));
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return (
    <>
      <Dialog
        title={issue ? "이슈 수정" : "이슈 등록"}
        onClose={close}
        closable
        testId="project-issue-dialog"
      >
        <form
          className="project-issue-form"
          data-testid="project-issue-form"
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <fieldset disabled={busy}>
            <label>
              프로젝트
              {fixedProject ? (
                <span
                  className="project-issue-fixed"
                  data-testid="project-issue-project-name"
                >
                  {projectName}
                </span>
              ) : (
                <select
                  className="ls-in"
                  data-testid="project-issue-project"
                  value={values.projectId}
                  onChange={(e) => update("projectId", e.target.value)}
                >
                  <option value="">선택</option>
                  {projects.filter(projectIssueCanCreate).map((p) => (
                    <option key={p.projectId} value={p.projectId}>
                      {p.projectName}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <label>
              제목
              <input
                className="ls-in"
                data-testid="project-issue-title"
                value={values.title}
                maxLength={100}
                onChange={(e) => update("title", e.target.value)}
              />
            </label>
            <label>
              중요도
              <select
                className="ls-in"
                data-testid="project-issue-importance"
                value={values.importanceCode}
                onChange={(e) => update("importanceCode", e.target.value)}
              >
                {importance.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
              </select>
              <span className="project-cap">
                높음: 일정·원가에 바로 영향 · 중간: 2주 안에 풀어야 함 · 낮음: 그
                밖
              </span>
            </label>
            <div className="project-issue-owner" data-testid="project-issue-owner">
              <span className="project-issue-owner-label">담당자</span>
              <span>
                {issue
                  ? issue.assigneeName || issue.assigneeId
                  : CURRENT_USER.name}
              </span>
              <span
                className="project-cap"
                title="이슈를 올린 로그인 계정이 담당자가 되고, 다른 사람이 고쳐도 바뀌지 않습니다."
              >
                (로그인 계정)
              </span>
            </div>
            <label>
              기한
              <input
                className="ls-in project-issue-date"
                type="date"
                data-testid="project-issue-due-date"
                value={values.dueDate}
                onChange={(e) => update("dueDate", e.target.value)}
              />
            </label>
            <label>
              상태
              <select
                className="ls-in"
                data-testid="project-issue-status"
                value={values.statusCode}
                onChange={(e) => update("statusCode", e.target.value)}
              >
                {statuses.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
              </select>
            </label>
            <label>
              조치 내용
              <textarea
                className="ls-in"
                data-testid="project-issue-action"
                maxLength={500}
                rows={3}
                value={values.actionText ?? ""}
                onChange={(e) => update("actionText", e.target.value)}
              />
            </label>
          </fieldset>
          {issue && (
            <p className="project-cap" data-testid="project-issue-audit">
              {projectIssueAudit(issue)}
            </p>
          )}
          {error && (
            <p
              className="project-error"
              role="alert"
              data-testid="project-issue-save-error"
            >
              {error}
            </p>
          )}
          <div className="app-actions">
            <button
              type="button"
              className="ls-btn"
              data-testid="project-issue-cancel"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              type="submit"
              className="ls-btn ls-btn-p"
              data-testid="project-issue-save"
              disabled={busy}
            >
              {busy ? "저장 중…" : "저장"}
            </button>
          </div>
        </form>
      </Dialog>
      {discard && (
        <ConfirmDialog
          title="입력한 내용을 버릴까요?"
          description="저장하지 않은 내용이 있습니다."
          testId="project-issue-discard"
          onCancel={() => setDiscard(false)}
          onProceed={onClose}
          proceedLabel="버리기"
        />
      )}
    </>
  );
}

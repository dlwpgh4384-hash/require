import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { formatDateTime, koreanToday } from "../../shared/time";
import type { CodeItem } from "../codegroups/codeGroups";
import type { Project } from "../projects/projects";
import * as api from "./projectIssues";
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
const defaultDate = () => {
  const day = new Date(`${koreanToday()}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + 7);
  return day.toISOString().slice(0, 10);
};

// UI-31-M3 / SCR-UI-31: 이슈 등록·수정·종료. 프로젝트와 등록 사용자는 수정하지 않는다.
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
  onSaved: (issue: api.ProjectIssue) => void | Promise<void>;
  dirtyScreenId?: string;
}) {
  const [initial] = useState<api.ProjectIssueInput>(() => ({
    projectId: issue?.projectId ?? projectId ?? "",
    title: issue?.title ?? "",
    dueDate: issue?.dueDate ?? defaultDate(),
    importanceCode:
      issue?.importanceCode ??
      (importance.some((c) => c.code === "MID") ? "MID" : ""),
    statusCode:
      issue?.statusCode ??
      (statuses.some((c) => c.code === "OPEN") ? "OPEN" : ""),
    actionText: issue?.actionText ?? null,
  }));
  const [values, setValues] = useState(initial);
  const [fields, setFields] = useState<Record<string, string>>({});
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
  const save = async () => {
    if (pending.current) return;
    const invalid: Record<string, string> = {};
    for (const name of [
      "projectId",
      "title",
      "dueDate",
      "importanceCode",
      "statusCode",
    ] as const)
      if (!values[name].trim()) invalid[name] = "필수 입력입니다.";
    if ([...values.title].length > 100)
      invalid.title = "제목은 100자까지 입력해 주세요.";
    if ([...(values.actionText ?? "")].length > 500)
      invalid.actionText = "조치는 500자까지 입력해 주세요.";
    setFields(invalid);
    if (Object.keys(invalid).length) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const saved = issue
        ? await api.putCommonProjectBoardIssue(issue.issueId, values)
        : await api.postCommonProjectBoardIssue(values);
      await onSaved(saved);
    } catch (reason) {
      setError(api.projectIssueFailure(reason));
      if (reason instanceof api.ProjectIssueRequestError)
        setFields(
          Object.fromEntries(reason.errors.map((f) => [f.field, f.message])),
        );
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const fieldError = (name: string) =>
    fields[name] ? (
      <span
        className="project-error"
        data-testid={`project-issue-${name.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}-error`}
      >
        {fields[name]}
      </span>
    ) : null;
  return (
    <>
      <Dialog
        title={`프로젝트 이슈 ${issue ? "수정" : "등록"}`}
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
              프로젝트 *
              <select
                className="ls-in"
                data-testid="project-issue-project"
                value={values.projectId}
                disabled={!!issue || !!projectId}
                onChange={(e) => update("projectId", e.target.value)}
              >
                <option value="">선택</option>
                {projects
                  .filter(
                    (p) =>
                      issue?.projectId === p.projectId ||
                      projectIssueCanCreate(p),
                  )
                  .map((p) => (
                    <option key={p.projectId} value={p.projectId}>
                      {p.projectName}
                    </option>
                  ))}
                {issue &&
                  !projects.some((p) => p.projectId === issue.projectId) && (
                    <option value={issue.projectId}>{issue.projectName}</option>
                  )}
              </select>
              {fieldError("projectId")}
            </label>
            <label>
              제목 *
              <input
                className="ls-in"
                data-testid="project-issue-title"
                value={values.title}
                maxLength={100}
                onChange={(e) => update("title", e.target.value)}
              />
              {fieldError("title")}
            </label>
            <label>
              기한 *
              <input
                className="ls-in"
                type="date"
                data-testid="project-issue-due-date"
                value={values.dueDate}
                onChange={(e) => update("dueDate", e.target.value)}
              />
              {fieldError("dueDate")}
            </label>
            <label>
              중요도 *
              <select
                className="ls-in"
                data-testid="project-issue-importance"
                value={values.importanceCode}
                onChange={(e) => update("importanceCode", e.target.value)}
              >
                <option value="">선택</option>
                {importance.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
              </select>
              {fieldError("importanceCode")}
            </label>
            <p>
              담당자: {issue ? issue.assigneeName : "등록 사용자 자동고정"}{" "}
              (변경 불가)
            </p>
            <label>
              상태 *
              <select
                className="ls-in"
                data-testid="project-issue-status"
                value={values.statusCode}
                onChange={(e) => update("statusCode", e.target.value)}
              >
                <option value="">선택</option>
                {statuses.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
              </select>
              {fieldError("statusCode")}
            </label>
            <label>
              조치 내용
              <textarea
                className="ls-in"
                data-testid="project-issue-action"
                maxLength={500}
                value={values.actionText ?? ""}
                onChange={(e) => update("actionText", e.target.value)}
              />
              {fieldError("actionText")}
            </label>
          </fieldset>
          {issue && (
            <p data-testid="project-issue-audit">
              등록자 {issue.createdBy} · 등록일{" "}
              {formatDateTime(issue.createdAt)} / 수정자{" "}
              {issue.updatedBy ?? "–"} · 고친 시각{" "}
              {formatDateTime(issue.updatedAt)}
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

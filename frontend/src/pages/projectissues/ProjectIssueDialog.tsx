import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { addDays, formatMonthDay, koreanToday } from "../../shared/time";
import type { CodeItem } from "../codegroups/codeGroups";
import type { Project } from "../projects/projects";
import { ProjectField } from "../projects/ProjectField";
import * as api from "./projectIssues";
import { useOptionalAccountSession } from "../commonops/AccountSessionBoundary";
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

/** 칸 이름 → 입력 칸 id(첫 오류 칸으로 커서를 옮길 때). */
const FIELD_IDS: Partial<Record<keyof api.ProjectIssueInput, string>> = {
  projectId: "project-issue-project",
  title: "project-issue-title",
  dueDate: "project-issue-due-date",
  importanceCode: "project-issue-importance",
  statusCode: "project-issue-status",
  actionText: "project-issue-action",
};

// UI-31-M3 / SCR-UI-31: 이슈 등록·수정·종료(시안 issueModal). 프로젝트와 담당자(등록한 로그인 계정)는 고치지 않는다.
export function ProjectIssueDialog({
  issue,
  projectId,
  projects,
  importance,
  statuses,
  onClose,
  onSaved,
  onConflict,
  dirtyScreenId = "UI-31",
}: {
  issue?: api.ProjectIssue;
  projectId?: string;
  projects: ProjectIssueOption[];
  importance: CodeItem[];
  statuses: CodeItem[];
  onClose: () => void;
  onSaved: (issue: api.ProjectIssue, created: boolean) => void | Promise<void>;
  /** 저장이 409(다른 사람이 먼저 고침)면 부른다. 없으면 창 안에 안내만 띄운다. */
  onConflict?: (message: string) => void;
  dirtyScreenId?: string;
}) {
  const account = useOptionalAccountSession();
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
  const [errorField, setErrorField] = useState("");
  const [focusRequest, setFocusRequest] = useState(0);
  const [busy, setBusy] = useState(false);
  const [discard, setDiscard] = useState(false);
  const pending = useRef(false);
  const setDirty = useScreenDirty();
  const dirty = JSON.stringify(initial) !== JSON.stringify(values);
  useEffect(() => {
    // 빈 글자면 표시할 화면 탭이 없다(다른 화면에서 연 프로젝트 상세).
    if (!dirtyScreenId) return;
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
  // 틀린 칸은 빨간 테두리(aria-invalid)로도 보인다. 문구는 시안처럼 아래 한 줄.
  const invalid = (field: keyof api.ProjectIssueInput) =>
    errorField === field ? { "aria-invalid": true as const } : {};
  const fail = (field: keyof api.ProjectIssueInput, message: string) => {
    setErrorField(field);
    setError(message);
    setFocusRequest((value) => value + 1);
  };
  // FR-0106: 오류가 난 첫 칸으로 커서를 옮긴다(저장 중에는 칸이 잠겨 있으므로 잠금이 풀린 뒤).
  useEffect(() => {
    if (!focusRequest || busy || !errorField) return;
    const id = FIELD_IDS[errorField as keyof api.ProjectIssueInput];
    if (id) document.getElementById(id)?.focus();
  }, [focusRequest, busy, errorField]);
  const save = async () => {
    if (pending.current) return;
    // 시안 issueModal 검사 문구
    if (!values.projectId) return fail("projectId", "프로젝트를 골라 주세요.");
    if (!values.title.trim()) return fail("title", "제목을 넣어 주세요.");
    if (!values.dueDate) return fail("dueDate", "기한을 넣어 주세요.");
    if ([...values.title].length > 100)
      return fail("title", "제목은 100자까지 입력해 주세요.");
    if ([...(values.actionText ?? "")].length > 500)
      return fail("actionText", "조치 내용은 500자까지 입력해 주세요.");
    pending.current = true;
    setBusy(true);
    setError("");
    setErrorField("");
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
      const message = api.projectIssueFailure(reason);
      // FR-0106: 그 사이 다른 사람이 고쳤으면(409) 창을 닫고 목록을 다시 읽게 한다(옛 수정 시각으로 다시 보내지 않게).
      if (
        onConflict &&
        reason instanceof api.ProjectIssueRequestError &&
        reason.status === 409
      )
        return onConflict(message);
      // 서버가 알려 준 칸 오류는 그 칸에 표시한다.
      const field =
        reason instanceof api.ProjectIssueRequestError
          ? reason.errors.find((e) => e.field in FIELD_IDS)?.field
          : undefined;
      if (field) fail(field as keyof api.ProjectIssueInput, message);
      else setError(message);
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
          className="project-modal project-issue-form"
          data-testid="project-issue-form"
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          {/* 시안 issueModal(fcF): 필수 칸은 이름 뒤 빨간 *, 조치 내용만 선택 */}
          <fieldset disabled={busy}>
            <ProjectField id="project-issue-project" label="프로젝트" required>
              {fixedProject ? (
                <span
                  className="project-issue-fixed"
                  data-testid="project-issue-project-name"
                >
                  {projectName}
                </span>
              ) : (
                <select
                  id="project-issue-project"
                  className="ls-in"
                  data-testid="project-issue-project"
                  value={values.projectId}
                  onChange={(e) => update("projectId", e.target.value)}
                  {...invalid("projectId")}
                >
                  <option value="">선택</option>
                  {projects.filter(projectIssueCanCreate).map((p) => (
                    <option key={p.projectId} value={p.projectId}>
                      {p.projectName}
                    </option>
                  ))}
                </select>
              )}
            </ProjectField>
            <ProjectField id="project-issue-title" label="제목" required>
              <input
                id="project-issue-title"
                className="ls-in"
                data-testid="project-issue-title"
                value={values.title}
                maxLength={100}
                onChange={(e) => update("title", e.target.value)}
                {...invalid("title")}
              />
            </ProjectField>
            <ProjectField
              id="project-issue-importance"
              label="중요도"
              required
              help="높음: 일정·원가에 바로 영향 · 중간: 2주 안에 풀어야 함 · 낮음: 그 밖"
            >
              <select
                id="project-issue-importance"
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
            </ProjectField>
            <div
              className="project-issue-owner"
              data-testid="project-issue-owner"
            >
              <span className="project-issue-owner-label">담당자</span>
              <span>
                {issue
                  ? issue.assigneeName || issue.assigneeId
                  : (account?.info?.name ?? "로그인 계정")}
              </span>
              <span
                className="project-cap"
                title="이슈를 올린 로그인 계정이 담당자가 되고, 다른 사람이 고쳐도 바뀌지 않습니다."
              >
                (로그인 계정)
              </span>
            </div>
            <ProjectField id="project-issue-due-date" label="기한" required>
              <input
                id="project-issue-due-date"
                className="ls-in project-issue-date"
                type="date"
                data-testid="project-issue-due-date"
                value={values.dueDate}
                onChange={(e) => update("dueDate", e.target.value)}
                {...invalid("dueDate")}
              />
            </ProjectField>
            <ProjectField id="project-issue-status" label="상태" required>
              <select
                id="project-issue-status"
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
            </ProjectField>
            <ProjectField id="project-issue-action" label="조치 내용">
              <textarea
                id="project-issue-action"
                className="ls-in"
                data-testid="project-issue-action"
                maxLength={500}
                rows={3}
                value={values.actionText ?? ""}
                onChange={(e) => update("actionText", e.target.value)}
                {...invalid("actionText")}
              />
            </ProjectField>
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

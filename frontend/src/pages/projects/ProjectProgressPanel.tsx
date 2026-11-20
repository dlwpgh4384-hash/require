import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
import { Badge } from "../../shared/list";
import { addDays, formatMonthDay, koreanToday } from "../../shared/time";
import { ProjectField } from "./ProjectField";
import { projectFailure } from "./projects";
import * as api from "./projectBoardDetails";

/** 식-74 단계 상태: 완료 / 지연(예정 종료일 지남) / 진행 중 / 예정. */
export const stageState = (
  step: Pick<api.BoardStep, "plannedStartDate" | "plannedEndDate" | "completedDate">,
  today: string,
) =>
  step.completedDate
    ? "완료"
    : step.plannedEndDate < today
      ? "지연"
      : step.plannedStartDate <= today
        ? "진행 중"
        : "예정";
export const stageKind = (state: string) =>
  state === "완료"
    ? "ok"
    : state === "지연"
      ? "bad"
      : state === "진행 중"
        ? "info"
        : "neutral";
const icon = (d: string) => (
  <svg
    width="14"
    height="14"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);
const plusIcon = icon("M12 5v14M5 12h14");
const planIcon = icon("M4 6h16M4 12h10M4 18h7");

/**
 * UI-31-M1, UI-31-M2: 프로젝트 상세 [진행 현황]의 단계 표와 주간 메모(시안 pmTabs: 단계 → 주간 메모).
 * 단계는 프로젝트를 등록·수주하거나 현황판을 열 때 자동으로 만든다(없으면 여기서도 한 번 만든다).
 */
export function ProjectProgressPanel({
  projectId,
  projectName = "",
  contractTypeCode,
  statusCode,
  startDate,
  endDate,
  plannedProgress = null,
  actualProgress = null,
  readOnly = false,
  onChanged,
}: {
  projectId: string;
  projectName?: string;
  contractTypeCode: string;
  statusCode: string;
  startDate?: string;
  endDate: string;
  plannedProgress?: number | null;
  actualProgress?: number | null;
  readOnly?: boolean;
  onChanged?: (message?: string) => void;
}) {
  const [memos, setMemos] = useState<api.BoardMemo[]>([]);
  const [steps, setSteps] = useState<api.BoardStep[]>([]);
  const [stepVersion, setStepVersion] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [modal, setModal] = useState<"memo" | "dates" | null>(null);
  const [selected, setSelected] = useState<api.BoardMemo | null>(null);
  const [input, setInput] = useState({
    workDone: "",
    nextWork: "",
    requests: "",
    actualProgress: "",
  });
  const [ends, setEnds] = useState<Record<string, string>>({});
  const [modalError, setModalError] = useState("");
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [closing, setClosing] = useState(false);
  const [completing, setCompleting] = useState<api.BoardStep | null>(null);
  const pending = useRef(false);
  const today = koreanToday();
  const locked =
    readOnly ||
    statusCode === "LOSE" ||
    statusCode === "END" ||
    endDate < today;
  const operational = contractTypeCode === "MO" || contractTypeCode === "DP";
  const typeLabel = contractTypeCode === "MO" ? "월단위" : "파견";
  const planText =
    plannedProgress == null ? "–" : String(Number(plannedProgress.toFixed(1)));
  async function load() {
    setLoading(true);
    setError("");
    try {
      const [m, s] = await Promise.all([
        api.getCommonProjectBoardWeeklyMemos(projectId),
        api.getCommonProjectBoardSteps(projectId),
      ]);
      let stepList = s;
      if (!locked && (s.items ?? []).length === 0) {
        await api.postCommonProjectBoardStepsEnsure(projectId);
        stepList = await api.getCommonProjectBoardSteps(projectId);
      }
      setMemos(m.items ?? []);
      setSteps(stepList.items ?? []);
      setStepVersion(stepList.modifiedAt ?? null);
    } catch (reason) {
      setError(projectFailure(reason));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, [projectId]);
  const close = () => {
    if (pending.current) return;
    if (dirty) setClosing(true);
    else setModal(null);
  };
  const openMemo = (memo?: api.BoardMemo) => {
    setSelected(memo ?? null);
    const progress = memo
      ? memo.actualProgress
      : actualProgress == null
        ? null
        : Math.round(actualProgress);
    setInput({
      workDone: memo?.workDone ?? "",
      nextWork: memo?.nextWork ?? "",
      requests: memo?.requests ?? "",
      actualProgress: progress == null ? "" : String(progress),
    });
    setDirty(false);
    setModalError("");
    setModal("memo");
  };
  const openDates = () => {
    setEnds(
      Object.fromEntries(steps.map((s) => [s.stepId, s.plannedEndDate])),
    );
    setDirty(false);
    setModalError("");
    setModal("dates");
  };
  async function perform(
    write: () => Promise<unknown>,
    message: string,
    inModal = false,
  ) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setModalError("");
    setError("");
    try {
      await write();
      if (inModal) {
        setModal(null);
        setDirty(false);
      }
      await load();
      onChanged?.(message);
    } catch (reason) {
      const text = projectFailure(reason);
      if (inModal) setModalError(text);
      else setError(text);
      // 그 사이 다른 사람이 바꿨으면(409) 최신 값을 다시 불러 둔다.
      void load();
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  // 진척률 칸 오류일 때 그 칸에 빨간 테두리
  const progressInvalid = modalError.startsWith("실적 진척률");
  function saveMemo() {
    const done = input.workDone.trim();
    const next = input.nextWork.trim();
    const request = input.requests.trim();
    let progress: number | null = null;
    // 실적 진척률은 필수(시안 「실적 진척률 *」, 2026-11-20 사용자 결정). 0~100 정수.
    if (!operational && input.actualProgress.trim() === "")
      return setModalError("실적 진척률을 입력해 주세요.");
    if (!operational) {
      progress = Number(input.actualProgress.trim());
      if (!Number.isInteger(progress) || progress < 0 || progress > 100)
        return setModalError("실적 진척률은 0~100 사이 정수로 넣어 주세요.");
    }
    // 한 일·할 일은 선택. 운영형(진척률 칸 없음)은 셋 중 하나는 적어야 한다.
    if (operational && !done && !next && !request)
      return setModalError("한 일, 할 일, 요청 사항 중 하나는 적어 주세요.");
    const body: api.MemoInput = {
      workDone: done || null,
      nextWork: next || null,
      requests: request || null,
      actualProgress: operational ? null : progress,
    };
    const memo = selected;
    void perform(
      () =>
        memo
          ? api.putCommonProjectBoardWeeklyMemo(
              projectId,
              memo.memoId,
              body,
              memo.modifiedAt,
            )
          : api.postCommonProjectBoardWeeklyMemo(projectId, body),
      memo ? "주간 메모를 고쳤습니다." : "주간 상태를 저장했습니다.",
      true,
    );
  }
  function saveDates() {
    const begin = startDate ?? steps[0]?.plannedStartDate ?? "";
    // 시안 stagePlanModal 검사: 종료일만 고치고 시작일은 앞 단계 종료일 다음날로 맞춘다.
    for (const [index, step] of steps.entries()) {
      const last = index === steps.length - 1;
      if (!step.completedDate && !last && !ends[step.stepId])
        return setModalError("모든 계획 종료일을 입력해 주세요.");
    }
    let previousEnd: string | null = null;
    for (const [index, step] of steps.entries()) {
      const low = index === 0 ? begin : (previousEnd ?? begin);
      if (step.completedDate) {
        previousEnd = step.plannedEndDate;
        continue;
      }
      const last = index === steps.length - 1;
      const end = last ? endDate : ends[step.stepId];
      if (end <= low)
        return setModalError(
          `「${step.stepName}」 계획 종료일은 ${low}보다 늦어야 합니다.`,
        );
      if (end > endDate)
        return setModalError(
          `「${step.stepName}」 계획 종료일이 프로젝트 종료일(${endDate})을 넘을 수 없습니다.`,
        );
      previousEnd = end;
    }
    previousEnd = null;
    const items: api.StepDate[] = steps.map((step, index) => {
      if (step.completedDate) {
        previousEnd = step.plannedEndDate;
        return {
          stepId: step.stepId,
          plannedStartDate: step.plannedStartDate,
          plannedEndDate: step.plannedEndDate,
        };
      }
      const last = index === steps.length - 1;
      const start =
        index === 0 ? begin : addDays(previousEnd ?? begin, 1);
      const end = last ? endDate : ends[step.stepId];
      previousEnd = end;
      return { stepId: step.stepId, plannedStartDate: start, plannedEndDate: end };
    });
    void perform(
      () => api.putCommonProjectBoardSteps(projectId, items, stepVersion),
      "단계 계획을 저장했습니다.",
      true,
    );
  }
  return (
    <section className="project-progress-panel" data-testid="project-progress-panel">
      {error && (
        <div role="alert" className="project-progress-error">
          {error}{" "}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="project-progress-retry"
            onClick={() => void load()}
          >
            다시 시도
          </button>
        </div>
      )}
      <div className="project-card-h">
        <h3 className="project-h3">단계</h3>
        {!locked && (
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="project-stage-edit"
            disabled={busy || loading || !steps.length}
            onClick={openDates}
          >
            {planIcon} 단계 계획 입력
          </button>
        )}
      </div>
      <div className="ls-twrap">
        <table className="ls-t" data-testid="project-stages">
          <thead>
            <tr>
              <th>단계</th>
              <th className="ls-right">비중</th>
              <th>예정 기간</th>
              <th>완료일</th>
              <th>상태</th>
              {!locked && <th aria-label="완료 처리" />}
            </tr>
          </thead>
          <tbody>
            {steps.map((s) => {
              const state = stageState(s, today);
              return (
                <tr key={s.stepId} data-testid={`project-stage-${s.stepId}`}>
                  <td>{s.stepName}</td>
                  <td className="ls-right">{Number(s.weight)}%</td>
                  <td>
                    {s.plannedStartDate.slice(2, 7)} ~{" "}
                    {s.plannedEndDate.slice(2, 7)}
                  </td>
                  <td>{s.completedDate ? s.completedDate.slice(2) : "–"}</td>
                  <td>
                    <Badge kind={stageKind(state)}>{state}</Badge>
                  </td>
                  {!locked && (
                    <td className="ls-center">
                      {!s.completedDate && s.plannedStartDate <= today && (
                        <button
                          type="button"
                          className="ls-btn ls-btn-sm"
                          data-testid={`project-stage-complete-${s.stepId}`}
                          disabled={busy}
                          onClick={() => setCompleting(s)}
                        >
                          완료
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
        {loading && !steps.length && (
          <p className="project-sub project-empty">불러오는 중입니다.</p>
        )}
        {!loading && !error && !steps.length && (
          <p className="project-sub project-empty">등록된 단계가 없습니다.</p>
        )}
      </div>
      <div className="project-card-h project-card-h-gap">
        <h3 className="project-h3">주간 메모</h3>
        {!locked && (
          <button
            type="button"
            className="ls-btn ls-btn-sm ls-btn-p"
            data-testid="project-memo-add"
            disabled={busy || loading}
            onClick={() => openMemo()}
          >
            {plusIcon} 이번 주 상태 적기
          </button>
        )}
      </div>
      <div className="project-memos" data-testid="project-memos">
        {memos.map((m) => (
          <div
            key={m.memoId}
            className="project-memo"
            data-testid={`project-memo-${m.memoId}`}
          >
            <div className="project-memo-head">
              <span>
                <strong>
                  {m.writtenDate
                    ? m.writtenDate.slice(5)
                    : formatMonthDay(m.createdAt)}{" "}
                  작성
                </strong>
                {m.updatedAt && (
                  <span className="project-cap">
                    {" "}
                    · {formatMonthDay(m.updatedAt)} 고침
                  </span>
                )}
              </span>
              <span className="project-memo-side">
                {m.actualProgress != null && (
                  <span className="project-cap">
                    실적 진척률 {m.actualProgress}%
                  </span>
                )}
                {!locked && (
                  <button
                    type="button"
                    className="project-lnk"
                    data-testid={`project-memo-edit-${m.memoId}`}
                    disabled={busy}
                    onClick={() => openMemo(m)}
                  >
                    수정
                  </button>
                )}
              </span>
            </div>
            {m.workDone && (
              <div>
                <span className="project-cap">한 일</span> {m.workDone}
              </div>
            )}
            {m.nextWork && (
              <div>
                <span className="project-cap">할 일</span> {m.nextWork}
              </div>
            )}
            {m.requests && (
              <div>
                <span className="project-cap">요청</span>{" "}
                <span className="project-warn-text">{m.requests}</span>
              </div>
            )}
          </div>
        ))}
        {!loading && !error && !memos.length && (
          <p className="project-sub project-empty">적은 주간 메모가 없습니다.</p>
        )}
      </div>
      {modal === "memo" && (
        <Dialog
          title={selected ? "주간 메모 수정" : "이번 주 상태 적기"}
          testId="project-memo-dialog"
          onClose={close}
          closable
        >
          {/* 시안 memoModal: 창 틀 .project-modal(폭 400), 칸 fcF.
              실적 진척률은 시안대로 필수(*), 2026-11-20 사용자 결정. */}
          <div className="project-modal project-progress-form">
            <p className="project-cap">
              {projectName} ·{" "}
              {selected
                ? `${selected.writtenDate ?? formatMonthDay(selected.createdAt)} 작성`
                : today}
            </p>
            {operational ? (
              <p className="project-note">
                운영형({typeLabel}) 프로젝트는 진척률을 기간 기준으로 자동
                계산합니다.
              </p>
            ) : (
              <ProjectField
                id="project-memo-progress"
                label="실적 진척률"
                required
                help={`계획 진척률은 ${planText}%입니다`}
              >
                <span className="project-progress-rate">
                  <input
                    id="project-memo-progress"
                    className="ls-in"
                    inputMode="numeric"
                    data-testid="project-memo-progress"
                    value={input.actualProgress}
                    disabled={busy}
                    aria-invalid={progressInvalid || undefined}
                    onChange={(e) => {
                      setInput({ ...input, actualProgress: e.target.value });
                      setDirty(true);
                    }}
                  />
                  <span className="project-sub">%</span>
                </span>
              </ProjectField>
            )}
            {(
              [
                ["workDone", "이번 주 한 일", ""],
                ["nextWork", "다음 주 할 일", ""],
                ["requests", "요청 사항", "없으면 비워 두세요"],
              ] as const
            ).map(([key, label, placeholder]) => (
              <ProjectField key={key} id={`project-memo-${key}`} label={label}>
                <textarea
                  id={`project-memo-${key}`}
                  className="ls-in"
                  rows={2}
                  data-testid={`project-memo-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
                  value={input[key]}
                  placeholder={placeholder || undefined}
                  disabled={busy}
                  maxLength={200}
                  onChange={(e) => {
                    setInput({ ...input, [key]: e.target.value });
                    setDirty(true);
                  }}
                />
              </ProjectField>
            ))}
            {modalError && (
              <p className="project-error" role="alert" data-testid="project-progress-error">
                {modalError}
              </p>
            )}
            <div className="app-actions">
              <button
                type="button"
                className="ls-btn"
                data-testid="project-progress-cancel"
                disabled={busy}
                onClick={close}
              >
                취소
              </button>
              <button
                type="button"
                className="ls-btn ls-btn-p"
                data-testid="project-progress-save"
                disabled={busy}
                onClick={saveMemo}
              >
                {busy ? "저장 중…" : "저장"}
              </button>
            </div>
          </div>
        </Dialog>
      )}
      {modal === "dates" && (
        <Dialog
          title="단계 계획 입력"
          testId="project-dates-dialog"
          onClose={close}
          closable
        >
          {/* 시안 stagePlanModal: 창 틀 .project-modal(폭 400) */}
          <div className="project-modal project-progress-form">
            <p className="project-cap">
              {projectName} · 전체 {startDate ?? steps[0]?.plannedStartDate} ~{" "}
              {endDate}
            </p>
            <p className="project-sub">
              첫 단계 계획 시작일은 프로젝트 시작일, 마지막 단계 계획 종료일은
              프로젝트 종료일로 고정됩니다. 완료된 단계의 계획 날짜는 고칠 수
              없습니다.
            </p>
            <div className="ls-twrap">
              <table className="ls-t">
                <thead>
                  <tr>
                    <th>단계</th>
                    <th className="ls-right">비중</th>
                    <th>계획 시작일</th>
                    <th>계획 종료일</th>
                  </tr>
                </thead>
                <tbody>
                  {steps.map((s, index) => {
                    const last = index === steps.length - 1;
                    return (
                      <tr key={s.stepId} data-testid={`project-date-row-${s.stepId}`}>
                        <td>{s.stepName}</td>
                        <td className="ls-right">{Number(s.weight)}%</td>
                        <td>
                          {index === 0
                            ? (startDate ?? s.plannedStartDate)
                            : "(앞 단계 종료일 다음날)"}
                        </td>
                        <td>
                          {s.completedDate || last ? (
                            <span>{last ? endDate : s.plannedEndDate}</span>
                          ) : (
                            <input
                              className="ls-in project-progress-date"
                              type="date"
                              data-testid={`project-date-${index}-end`}
                              value={ends[s.stepId] ?? ""}
                              disabled={busy}
                              onChange={(e) => {
                                setEnds({ ...ends, [s.stepId]: e.target.value });
                                setDirty(true);
                              }}
                            />
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {modalError && (
              <p className="project-error" role="alert" data-testid="project-progress-error">
                {modalError}
              </p>
            )}
            <div className="app-actions">
              <button
                type="button"
                className="ls-btn"
                data-testid="project-progress-cancel"
                disabled={busy}
                onClick={close}
              >
                취소
              </button>
              <button
                type="button"
                className="ls-btn ls-btn-p"
                data-testid="project-progress-save"
                disabled={busy}
                onClick={saveDates}
              >
                {busy ? "저장 중…" : "저장"}
              </button>
            </div>
          </div>
        </Dialog>
      )}
      {completing && (
        <ConfirmDialog
          title={`「${completing.stepName}」 단계를 완료로 바꿀까요?`}
          description="완료일은 오늘로 기록됩니다."
          proceedLabel="완료 처리"
          testId="project-stage-complete-confirm"
          onCancel={() => setCompleting(null)}
          onProceed={() => {
            const step = completing;
            setCompleting(null);
            void perform(
              () => api.postCommonProjectBoardStepComplete(projectId, step.stepId),
              `${step.stepName} 단계를 완료로 바꿨습니다.`,
            );
          }}
        />
      )}
      {closing && (
        <ConfirmDialog
          title="입력한 내용을 버릴까요?"
          description="저장하지 않은 내용이 있습니다."
          proceedLabel="버리기"
          onCancel={() => setClosing(false)}
          onProceed={() => {
            setClosing(false);
            setModal(null);
            setDirty(false);
          }}
        />
      )}
    </section>
  );
}

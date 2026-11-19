import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
import { koreanToday } from "../../shared/time";
import { projectFailure, ProjectRequestError } from "./projects";
import * as api from "./projectBoardDetails";

/** UI-31-M1, UI-31-M2: board detail progress operations. */
export function ProjectProgressPanel({
  projectId,
  contractTypeCode,
  statusCode,
  endDate,
  readOnly = false,
  onChanged,
}: {
  projectId: string;
  contractTypeCode: string;
  statusCode: string;
  endDate: string;
  readOnly?: boolean;
  onChanged?: (message?: string) => void;
}) {
  const [memos, setMemos] = useState<api.BoardMemo[]>([]);
  const [steps, setSteps] = useState<api.BoardStep[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [modal, setModal] = useState<"memo" | "dates" | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [input, setInput] = useState<api.MemoInput>({
    workDone: null,
    nextWork: null,
    requests: null,
    actualProgress: null,
  });
  const [dates, setDates] = useState<api.StepDate[]>([]);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [modalError, setModalError] = useState("");
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [closing, setClosing] = useState(false);
  const pending = useRef(false);
  const today = koreanToday();
  const locked =
    readOnly ||
    statusCode === "LOSE" ||
    statusCode === "END" ||
    endDate < today;
  const operational = contractTypeCode === "MO" || contractTypeCode === "DP";
  async function load() {
    setLoading(true);
    setError("");
    try {
      const [m, s] = await Promise.all([
        api.getCommonProjectBoardWeeklyMemos(projectId),
        api.getCommonProjectBoardSteps(projectId),
      ]);
      if (!locked && s.items.length === 0) {
        await api.postCommonProjectBoardStepsEnsure(projectId);
        s.items = (await api.getCommonProjectBoardSteps(projectId)).items;
      }
      setMemos(m.items);
      setSteps(s.items);
    } catch (reason) {
      setError(projectFailure(reason));
      throw reason;
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load().catch(() => undefined);
  }, [projectId]);
  const close = () => {
    if (!pending.current) {
      if (dirty) setClosing(true);
      else setModal(null);
    }
  };
  const openMemo = (memo?: api.BoardMemo) => {
    setSelected(memo?.memoId ?? null);
    setInput({
      workDone: memo?.workDone ?? null,
      nextWork: memo?.nextWork ?? null,
      requests: memo?.requests ?? null,
      actualProgress: memo?.actualProgress ?? null,
    });
    setDirty(false);
    setFields({});
    setModalError("");
    setModal("memo");
  };
  async function perform(write: () => Promise<unknown>, inModal = false) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setFields({});
    setModalError("");
    setMessage("");
    try {
      await write();
      if (inModal) {
        setModal(null);
        setDirty(false);
      }
      await load();
      setMessage("저장했습니다.");
      onChanged?.("저장했습니다.");
    } catch (reason) {
      const text = projectFailure(reason);
      if (inModal) {
        setModalError(text);
        if (reason instanceof ProjectRequestError)
          setFields(
            Object.fromEntries(reason.errors.map((e) => [e.field, e.message])),
          );
      } else setError(text);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  function saveMemo() {
    const errors: Record<string, string> = {};
    for (const key of ["workDone", "nextWork", "requests"] as const)
      if ((input[key]?.length ?? 0) > 200)
        errors[key] = "200자 이내로 입력해 주세요.";
    if (
      operational &&
      ![input.workDone, input.nextWork, input.requests].some((v) => v?.trim())
    )
      errors.workDone = "한 일·할 일·요청 사항 중 하나를 적어 주세요.";
    if (
      !operational &&
      input.actualProgress != null &&
      (!Number.isInteger(input.actualProgress) ||
        input.actualProgress < 0 ||
        input.actualProgress > 100)
    )
      errors.actualProgress = "0~100 정수를 입력해 주세요.";
    if (Object.keys(errors).length) {
      setFields(errors);
      return;
    }
    const body = {
      ...input,
      actualProgress: operational ? null : input.actualProgress,
    };
    void perform(
      () =>
        selected
          ? api.putCommonProjectBoardWeeklyMemo(projectId, selected, body)
          : api.postCommonProjectBoardWeeklyMemo(projectId, body),
      true,
    );
  }
  return (
    <section data-testid="project-progress-panel">
      {locked && <p>종료·실주 프로젝트는 조회만 가능합니다.</p>}
      {message && (
        <p role="status" data-testid="project-progress-message">
          {message}
        </p>
      )}
      {error && (
        <div role="alert">
          {error}
          <button
            data-testid="project-progress-retry"
            onClick={() => void load().catch(() => undefined)}
          >
            다시 시도
          </button>
        </div>
      )}
      {loading && <p>불러오는 중입니다.</p>}
      <header>
        <h3>주간 메모</h3>
        {!locked && (
          <button
            data-testid="project-memo-add"
            disabled={busy || loading}
            onClick={() => openMemo()}
          >
            메모 등록
          </button>
        )}
      </header>
      <table>
        <thead>
          <tr>
            <th>작성일</th>
            <th>한 일</th>
            <th>할 일</th>
            <th>요청 사항</th>
            {!operational && <th>실적 진척률</th>}
            <th>수정</th>
          </tr>
        </thead>
        <tbody>
          {memos.map((m) => (
            <tr key={m.memoId} data-testid={`project-memo-${m.memoId}`}>
              <td>
                {m.createdAt}
                {m.updatedAt && (
                  <small>
                    {m.updatedAt} 고침 ({m.updatedBy})
                  </small>
                )}
              </td>
              <td>{m.workDone ?? "–"}</td>
              <td>{m.nextWork ?? "–"}</td>
              <td>{m.requests ?? "–"}</td>
              {!operational && (
                <td>
                  {m.actualProgress == null ? "–" : `${m.actualProgress}%`}
                </td>
              )}
              <td>
                {!locked && (
                  <button
                    data-testid={`project-memo-edit-${m.memoId}`}
                    disabled={busy}
                    onClick={() => openMemo(m)}
                  >
                    수정
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!loading && !error && !memos.length && <p>조회 결과가 없습니다.</p>}
      <header>
        <h3>단계 계획</h3>
        {!locked && (
          <>
            <button
              data-testid="project-stage-ensure"
              disabled={busy || loading || steps.length > 0}
              onClick={() =>
                void perform(() =>
                  api.postCommonProjectBoardStepsEnsure(projectId),
                )
              }
            >
              기본 단계 보완
            </button>
            <button
              data-testid="project-stage-edit"
              disabled={busy || loading || !steps.length}
              onClick={() => {
                setDates(
                  steps.map(({ stepId, plannedStartDate, plannedEndDate }) => ({
                    stepId,
                    plannedStartDate,
                    plannedEndDate,
                  })),
                );
                setFields({});
                setModalError("");
                setDirty(false);
                setModal("dates");
              }}
            >
              날짜 수정
            </button>
          </>
        )}
      </header>
      <table>
        <thead>
          <tr>
            <th>단계</th>
            <th>비중</th>
            <th>시작</th>
            <th>종료</th>
            <th>완료일</th>
            <th>처리</th>
          </tr>
        </thead>
        <tbody>
          {steps.map((s) => (
            <tr key={s.stepId} data-testid={`project-stage-${s.stepId}`}>
              <td>{s.stepName}</td>
              <td>{s.weight}%</td>
              <td>{s.plannedStartDate}</td>
              <td>{s.plannedEndDate}</td>
              <td>{s.completedDate ?? "–"}</td>
              <td>
                {!locked && !s.completedDate && (
                  <button
                    data-testid={`project-stage-complete-${s.stepId}`}
                    disabled={busy || s.plannedStartDate > today}
                    onClick={() =>
                      void perform(() =>
                        api.postCommonProjectBoardStepComplete(
                          projectId,
                          s.stepId,
                        ),
                      )
                    }
                  >
                    완료
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {!loading && !error && !steps.length && <p>조회 결과가 없습니다.</p>}
      {modal && (
        <Dialog
          title={
            modal === "memo"
              ? selected
                ? "메모 수정"
                : "메모 등록"
              : "단계 날짜 수정"
          }
          testId={`project-${modal}-dialog`}
          onClose={close}
          closable
        >
          {modalError && <p role="alert">{modalError}</p>}
          {modal === "memo" ? (
            <>
              {(
                [
                  ["workDone", "한 일"],
                  ["nextWork", "할 일"],
                  ["requests", "요청 사항"],
                ] as const
              ).map(([key, label]) => (
                <label key={key}>
                  {label}
                  <textarea
                    data-testid={`project-memo-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
                    value={input[key] ?? ""}
                    disabled={busy}
                    maxLength={200}
                    onChange={(e) => {
                      setInput({ ...input, [key]: e.target.value });
                      setDirty(true);
                    }}
                  />
                  {fields[key] && <span role="alert">{fields[key]}</span>}
                </label>
              ))}
              {!operational && (
                <label>
                  실적 진척률
                  <input
                    type="number"
                    min={0}
                    max={100}
                    step={1}
                    data-testid="project-memo-progress"
                    value={input.actualProgress ?? ""}
                    disabled={busy}
                    onChange={(e) => {
                      setInput({
                        ...input,
                        actualProgress:
                          e.target.value === "" ? null : Number(e.target.value),
                      });
                      setDirty(true);
                    }}
                  />
                  {fields.actualProgress && (
                    <span role="alert">{fields.actualProgress}</span>
                  )}
                </label>
              )}
            </>
          ) : (
            <>
              <p>
                단계명·비중은 변경할 수 없습니다. 완료한 단계는 날짜도 변경할 수
                없습니다.
              </p>
              {dates.map((d, i) => (
                <div
                  key={d.stepId}
                  data-testid={`project-date-row-${d.stepId}`}
                >
                  <strong>
                    {steps[i].stepName} ({steps[i].weight}%)
                  </strong>
                  {(["plannedStartDate", "plannedEndDate"] as const).map(
                    (key) => (
                      <label key={key}>
                        {key === "plannedStartDate" ? "시작일" : "종료일"}
                        <input
                          type="date"
                          data-testid={`project-date-${i}-${key === "plannedStartDate" ? "start" : "end"}`}
                          disabled={busy || !!steps[i].completedDate}
                          value={d[key]}
                          onChange={(e) => {
                            setDates(
                              dates.map((v) =>
                                v.stepId === d.stepId
                                  ? { ...v, [key]: e.target.value }
                                  : v,
                              ),
                            );
                            setDirty(true);
                          }}
                        />
                      </label>
                    ),
                  )}
                </div>
              ))}
              {Object.entries(fields).map(([key, value]) => (
                <p key={key} role="alert">
                  {value}
                </p>
              ))}
            </>
          )}
          <footer>
            <button
              data-testid="project-progress-cancel"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              data-testid="project-progress-save"
              disabled={busy}
              onClick={() =>
                modal === "memo"
                  ? saveMemo()
                  : void perform(
                      () => api.putCommonProjectBoardSteps(projectId, dates),
                      true,
                    )
              }
            >
              {busy ? "저장 중…" : "저장"}
            </button>
          </footer>
        </Dialog>
      )}
      {closing && (
        <ConfirmDialog
          title="변경 사항 버리기"
          description="저장하지 않은 변경 사항을 버리시겠습니까?"
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

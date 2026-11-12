import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import {
  feedbackMessages,
  focusFirstError,
} from "../../shared/feedback/messages";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { UserAssignmentFields } from "../commonops/UserAssignmentFields";
import type { CommonUserOptionsResult } from "../commonops/userOptions";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import * as api from "./users";

const blank: api.UserCreate = {
  userId: "",
  name: "",
  email: "",
  roleCds: [],
  headquarters: false,
  projectIds: [],
};
const message = (failure: unknown) =>
  failure instanceof Error
    ? failure.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

// UI-23-M1/M2/M4/M5: registration and account details share the same assignment fields.
export function UserDialog({
  userId,
  options,
  pageSize,
  onClose,
  onSaved,
}: {
  userId: string | null;
  options: CommonUserOptionsResult;
  pageSize?: number;
  onClose: () => void;
  onSaved: (notice: string) => void;
}) {
  const [account, setAccount] = useState<api.UserAccount | null>(null);
  const [form, setForm] = useState<api.UserCreate>(blank);
  const [baseline, setBaseline] = useState<api.UserCreate>(blank);
  const [loading, setLoading] = useState(userId !== null);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [tab, setTab] = useState<"account" | "logs">("account");
  const [closing, setClosing] = useState(false);
  const [impact, setImpact] = useState<api.UserImpact | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [logPage, setLogPage] = useState(1);
  const [logs, setLogs] = useState<api.UserPageResult<{
    occurredAt: string;
    action: string;
    target: string;
  }> | null>(null);
  const [logError, setLogError] = useState("");
  const [logAttempt, setLogAttempt] = useState(0);
  const container = useRef<HTMLFormElement>(null);
  const dialogScope = useRef<HTMLDivElement>(null);
  const setDirty = useScreenDirty();
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useEffect(() => {
    setDirty("UI-23", dirty);
    return () => setDirty("UI-23", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    if (!userId) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    void api.getCommonUser(userId, controller.signal).then(
      (value) => {
        if (controller.signal.aborted) return;
        setAccount(value);
        setForm(value);
        setBaseline(value);
        setLoading(false);
      },
      (failure) => {
        if (!controller.signal.aborted) {
          setError(message(failure));
          setLoading(false);
        }
      },
    );
    return () => controller.abort();
  }, [userId, attempt]);
  useEffect(() => {
    if (!userId || tab !== "logs") return;
    const controller = new AbortController();
    setLogs(null);
    setLogError("");
    void api
      .getCommonUserAccessLogs(userId, logPage, pageSize, controller.signal)
      .then(
        (value) => {
          if (!controller.signal.aborted) setLogs(value);
        },
        (failure) => {
          if (!controller.signal.aborted) setLogError(message(failure));
        },
      );
    return () => controller.abort();
  }, [userId, tab, logPage, pageSize, logAttempt]);
  useEffect(() => {
    if (Object.keys(errors).length) focusFirstError(container.current);
  }, [errors]);
  const close = () => {
    if (busyRef.current) return;
    if (impact) {
      setImpact(null);
      setForm(baseline);
    } else if (closing) setClosing(false);
    else if (dirty) setClosing(true);
    else onClose();
  };
  const failure = (value: unknown) => {
    setError(message(value));
    if (value instanceof api.UserRequestError)
      setErrors(
        Object.fromEntries(
          value.errors.map((item) => [item.field, item.message]),
        ),
      );
  };
  const persist = async (confirmed: string[]) => {
    const assignments = {
      roleCds: form.roleCds,
      headquarters: form.headquarters,
      projectIds: form.projectIds,
    };
    const result =
      userId && account
        ? await api.putCommonUser(userId, {
            ...assignments,
            modDttm: account.modDttm,
            confirmedProjectIds: confirmed,
          })
        : await api.putCommonUsers({
            ...assignments,
            userId: form.userId,
            name: form.name,
            email: form.email,
          });
    setForm(result.account);
    setBaseline(result.account);
    setDirty("UI-23", false);
    onSaved(
      result.transferredProjects.length
        ? `저장했습니다. 담당 PM을 이관했습니다: ${result.transferredProjects.map((item) => item.projectName).join(", ")}`
        : "저장했습니다.",
    );
    onClose();
  };
  const validate = () => {
    const invalid: Record<string, string> = {};
    if (!userId) {
      if (!/^[a-z0-9]{4,20}$/.test(form.userId))
        invalid.userId = feedbackMessages.format(
          "아이디는 영문 소문자·숫자 4~20자로",
        );
      if (!form.name.trim()) invalid.name = feedbackMessages.required("이름");
      else if (form.name.length > 50)
        invalid.name = feedbackMessages.format("이름은 50자 이내로");
      if (
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ||
        form.email.length > 100
      )
        invalid.email = feedbackMessages.format(
          "이메일은 100자 이내 이메일 형식으로",
        );
    }
    if (!form.roleCds.length)
      invalid.roleCds = feedbackMessages.required("역할");
    setErrors(invalid);
    return Object.keys(invalid).length === 0;
  };
  const submit = async () => {
    if (busyRef.current || closing || impact || loading || !validate()) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      if (userId) {
        const result = await api.getCommonUserChangeImpact(
          [userId],
          "ROLE_CHANGE",
          form.roleCds,
        );
        if (result.projectCount) {
          setImpact(result);
          return;
        }
      }
      await persist([]);
    } catch (value) {
      failure(value);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  const continueSave = async () => {
    if (!impact || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      await persist(impact.projects.map((item) => item.projectId));
    } catch (value) {
      setImpact(null);
      failure(value);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  usePreferenceBusinessShortcuts(
    {
      onSave: () => {
        if (tab === "account" && !closing && !impact) void submit();
      },
      dialogTarget: dialogScope,
    },
    !busy && !loading,
  );
  const action = async (kind: "reset" | "unlock" | "resume") => {
    if (!account || busyRef.current || dirty) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      if (kind === "reset")
        await api.putCommonUserPasswordReset(account.userId, account.modDttm);
      if (kind === "unlock")
        await api.putCommonUserUnlock(account.userId, account.modDttm);
      if (kind === "resume")
        await api.putCommonUserResume(account.userId, account.modDttm);
      const latest = await api.getCommonUser(account.userId);
      setAccount(latest);
      setForm(latest);
      setBaseline(latest);
      onSaved(
        kind === "reset"
          ? "비밀번호를 초기화했습니다. 다음 로그인에서 변경이 필요합니다."
          : "처리했습니다.",
      );
    } catch (value) {
      failure(value);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  return (
    <Dialog
      title={userId ? "사용자 상세" : "사용자 등록"}
      onClose={close}
      testId="user-dialog"
    >
      <div ref={dialogScope} data-testid="user-dialog-content">
        {closing ? (
          <InlineConfirmation
            title="나가시겠습니까?"
            description={feedbackMessages.unsaved}
            onCancel={() => setClosing(false)}
            onProceed={onClose}
          />
        ) : impact ? (
          <InlineConfirmation
            title="담당 프로젝트를 해제할까요?"
            description={`${baseline.roleCds.includes("PM") && !form.roleCds.includes("PM") ? "PM이 비게 됩니다." : "담당 프로젝트 지정이 제거됩니다."} 담당 프로젝트 ${impact.projectCount}건: ${impact.projects.map((item) => item.projectName).join(", ")}`}
            proceedLabel="계속"
            onCancel={() => {
              setImpact(null);
              setForm(baseline);
            }}
            onProceed={() => void continueSave()}
          />
        ) : (
          <>
            {userId && (
              <div role="tablist" aria-label="사용자 상세 탭">
                <button
                  type="button"
                  role="tab"
                  aria-selected={tab === "account"}
                  data-testid="user-account-tab"
                  onClick={() => setTab("account")}
                >
                  계정 정보
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={tab === "logs"}
                  data-testid="user-logs-tab"
                  onClick={() => setTab("logs")}
                >
                  접근 기록
                </button>
              </div>
            )}
            {loading ? (
              <p role="status">계정 정보를 불러오는 중입니다.</p>
            ) : !userId || account ? (
              <>
                <form
                  ref={container}
                  hidden={tab !== "account"}
                  data-testid="user-account-panel"
                  role="tabpanel"
                  noValidate
                  onSubmit={(event) => {
                    event.preventDefault();
                    void submit();
                  }}
                >
                  {(
                    [
                      ["userId", "아이디"],
                      ["name", "이름"],
                      ["email", "이메일"],
                    ] as const
                  ).map(([field, label]) => (
                    <label key={field}>
                      {label}
                      {!userId && " *"}
                      <input
                        data-testid={`user-${field === "userId" ? "id" : field}-input`}
                        name={field}
                        value={form[field]}
                        readOnly={!!userId}
                        disabled={busy}
                        aria-invalid={!!errors[field]}
                        onChange={(event) =>
                          setForm({ ...form, [field]: event.target.value })
                        }
                      />
                      {errors[field] && (
                        <span className="field-error">{errors[field]}</span>
                      )}
                    </label>
                  ))}
                  {account && (
                    <p>
                      상태:{" "}
                      {options.userStates.find(
                        (state) => state.code === account.userStatCd,
                      )?.name ?? account.userStatCd}
                    </p>
                  )}
                  <UserAssignmentFields
                    options={options}
                    value={form}
                    disabled={busy}
                    errors={errors}
                    onChange={(value) =>
                      setForm({
                        ...form,
                        ...value,
                        projectIds: options.roles.some(
                          (role) =>
                            role.assignedProjectScope &&
                            value.roleCds.includes(role.roleCd),
                        )
                          ? value.projectIds
                          : [],
                      })
                    }
                  />
                  {account && (
                    <div className="app-actions">
                      {account.userStatCd === "LOCK" && (
                        <>
                          <p>비밀번호 5회 실패로 잠긴 계정입니다.</p>
                          <button
                            type="button"
                            data-testid="user-unlock"
                            disabled={busy || dirty}
                            onClick={() => void action("unlock")}
                          >
                            잠금 해제
                          </button>
                        </>
                      )}
                      {account.userStatCd === "STOP" && (
                        <button
                          type="button"
                          data-testid="user-resume"
                          disabled={busy || dirty}
                          onClick={() => void action("resume")}
                        >
                          다시 사용
                        </button>
                      )}
                      <button
                        type="button"
                        data-testid="user-password-reset"
                        disabled={busy || dirty}
                        onClick={() => void action("reset")}
                      >
                        비밀번호 초기화
                      </button>
                    </div>
                  )}
                  {dirty && account && (
                    <small>
                      수정한 내용을 저장하거나 취소한 뒤 계정 작업을 실행해
                      주세요.
                    </small>
                  )}
                  <div className="app-actions">
                    <button
                      type="button"
                      data-testid="user-form-cancel"
                      disabled={busy}
                      onClick={close}
                    >
                      취소
                    </button>
                    <button
                      type="submit"
                      data-testid="user-form-save"
                      className="primary-button"
                      disabled={busy}
                    >
                      {busy ? "저장 중" : "저장"}
                    </button>
                  </div>
                </form>
                {tab === "logs" && (
                  <section role="tabpanel" data-testid="user-logs-panel">
                    {logError ? (
                      <p role="alert">{logError}</p>
                    ) : !logs ? (
                      <p role="status">기록을 불러오는 중입니다.</p>
                    ) : !logs.items.length ? (
                      <p>접근 기록이 없습니다.</p>
                    ) : (
                      <table>
                        <thead>
                          <tr>
                            <th>일시</th>
                            <th>작업</th>
                            <th>대상</th>
                          </tr>
                        </thead>
                        <tbody>
                          {logs.items.map((log, index) => (
                            <tr
                              key={`${log.occurredAt}-${index}`}
                              data-testid={`user-log-row-${index}`}
                            >
                              <td>{log.occurredAt}</td>
                              <td>{log.action}</td>
                              <td>{log.target}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                    <button
                      type="button"
                      data-testid="user-logs-retry"
                      onClick={() => setLogAttempt((value) => value + 1)}
                    >
                      기록 재시도
                    </button>
                    <button
                      type="button"
                      data-testid="user-logs-previous"
                      disabled={logPage <= 1 || !logs}
                      onClick={() => setLogPage((value) => value - 1)}
                    >
                      이전
                    </button>
                    <button
                      type="button"
                      data-testid="user-logs-next"
                      disabled={!logs || logPage * logs.pageSize >= logs.total}
                      onClick={() => setLogPage((value) => value + 1)}
                    >
                      다음
                    </button>
                    <button
                      type="button"
                      data-testid="user-logs-close"
                      onClick={close}
                    >
                      닫기
                    </button>
                  </section>
                )}
              </>
            ) : null}
            {error && (
              <div role="alert">
                <p>{error}</p>
                <button
                  type="button"
                  data-testid="user-detail-retry"
                  disabled={busy}
                  onClick={() => {
                    if (dirty) setClosing(true);
                    else setAttempt((value) => value + 1);
                  }}
                >
                  다시 불러오기
                </button>
              </div>
            )}
            {(loading || (userId && !account)) && (
              <button
                type="button"
                data-testid="user-detail-close"
                onClick={close}
              >
                닫기
              </button>
            )}
          </>
        )}
      </div>
    </Dialog>
  );
}

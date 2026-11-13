import { useEffect, useId, useRef, useState } from "react";
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

interface FormValue {
  userId: string;
  name: string;
  email: string;
  roleCds: string[];
  headquarters: boolean;
  projectIds: string[];
}
const blank: FormValue = {
  userId: "",
  name: "",
  email: "",
  roleCds: [],
  headquarters: false,
  projectIds: [],
};
const fromAccount = (account: api.UserAccount): FormValue => ({
  userId: account.userId,
  name: account.name,
  email: account.email,
  roleCds: account.roleCds,
  headquarters: account.headquarters,
  projectIds: account.projectIds,
});
const message = (failure: unknown) =>
  failure instanceof Error
    ? failure.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

/** 시안 「사용자 등록」·「사용자 수정」 창. account가 있으면 수정(아이디는 바꿀 수 없음). */
export function UserDialog({
  account,
  options,
  onClose,
  onSaved,
}: {
  account: api.UserAccount | null;
  options: CommonUserOptionsResult;
  onClose: () => void;
  onSaved: (notice: string, account: api.UserAccount) => void;
}) {
  const editing = account !== null;
  const initial = account ? fromAccount(account) : blank;
  const [form, setForm] = useState<FormValue>(initial);
  const [error, setError] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [closing, setClosing] = useState(false);
  const [impact, setImpact] = useState<api.UserImpact | null>(null);
  const container = useRef<HTMLFormElement>(null);
  const dialogScope = useRef<HTMLDivElement>(null);
  const id = useId();
  const setDirty = useScreenDirty();
  const dirty = JSON.stringify(form) !== JSON.stringify(initial);
  useEffect(() => {
    setDirty("UI-23", dirty);
    return () => setDirty("UI-23", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    if (Object.keys(errors).length) focusFirstError(container.current);
  }, [errors]);
  const close = () => {
    if (busyRef.current) return;
    if (impact) setImpact(null);
    else if (closing) setClosing(false);
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
  /** PM 이관 안내: 다른 PM이 맡던 프로젝트를 골랐으면 그 사람 이름. */
  const movedFrom = (result: api.UserSaveResult) => {
    const names = [
      ...new Set(
        result.transferredProjects
          .map(
            (project) =>
              options.projects.find(
                (option) => option.projectId === project.projectId,
              )?.pmName,
          )
          .filter((name): name is string => !!name),
      ),
    ];
    return names.length
      ? ` ${names.join(", ")}님의 담당 프로젝트에서 뺐습니다.`
      : "";
  };
  const persist = async (confirmed: string[]) => {
    const assignments = {
      roleCds: form.roleCds,
      headquarters: form.headquarters,
      projectIds: form.projectIds,
    };
    const result = account
      ? await api.putCommonUser(account.userId, {
          ...assignments,
          name: form.name.trim(),
          email: form.email.trim(),
          modDttm: account.modDttm,
          confirmedProjectIds: confirmed,
        })
      : await api.putCommonUsers({
          ...assignments,
          userId: form.userId.trim(),
          name: form.name.trim(),
          email: form.email.trim(),
        });
    setDirty("UI-23", false);
    onSaved(
      account
        ? `사용자 정보를 저장했습니다. 변경 이력에 남겼습니다.${movedFrom(result)}`
        : `${result.account.name}님을 등록했습니다. ${result.account.email}로 임시 비밀번호를 보냈습니다.${movedFrom(result)}`,
      result.account,
    );
  };
  const validate = () => {
    const invalid: Record<string, string> = {};
    if (!editing) {
      if (!form.userId.trim())
        invalid.userId = feedbackMessages.required("아이디");
      else if (!/^[a-z0-9]{4,20}$/.test(form.userId.trim()))
        invalid.userId = feedbackMessages.format(
          "아이디는 영문 소문자·숫자 4~20자로",
        );
    }
    if (!form.name.trim()) invalid.name = feedbackMessages.required("이름");
    else if (form.name.trim().length > 20)
      invalid.name = feedbackMessages.format("이름은 20자 이내로");
    if (!form.email.trim()) invalid.email = feedbackMessages.required("이메일");
    else if (
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) ||
      form.email.trim().length > 100
    )
      invalid.email = "이메일 형식이 맞지 않습니다.";
    if (!form.roleCds.length) invalid.roleCds = "역할을 1개 이상 골라 주세요.";
    setErrors(invalid);
    return Object.keys(invalid).length === 0;
  };
  const submit = async () => {
    if (busyRef.current || closing || impact || !validate()) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      if (account) {
        const result = await api.getCommonUserChangeImpact(
          [account.userId],
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
        if (!closing && !impact) void submit();
      },
      dialogTarget: dialogScope,
    },
    !busy,
  );
  const field = (
    key: "userId" | "name" | "email",
    label: string,
    help: string | null,
    extra: { maxLength: number; readOnly?: boolean; inputMode?: "email" },
  ) => (
    <div className="uf-field" data-testid={`user-field-${key.toLowerCase()}`}>
      <label htmlFor={`${id}-${key}`} className="uf-req">
        {label}
      </label>
      <input
        id={`${id}-${key}`}
        data-testid={`user-${key}-input`}
        value={form[key]}
        maxLength={extra.maxLength}
        readOnly={extra.readOnly}
        inputMode={extra.inputMode}
        autoComplete="off"
        aria-invalid={!!errors[key]}
        aria-describedby={
          `${help ? `${id}-${key}-help` : ""}${errors[key] ? ` ${id}-${key}-error` : ""}`.trim() ||
          undefined
        }
        onChange={(event) => setForm({ ...form, [key]: event.target.value })}
      />
      {help && (
        <span id={`${id}-${key}-help`} className="uf-help">
          {help}
        </span>
      )}
      {errors[key] && (
        <p id={`${id}-${key}-error`} className="field-error">
          {errors[key]}
        </p>
      )}
    </div>
  );
  return (
    <Dialog
      title={editing ? "사용자 수정" : "사용자 등록"}
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
            description={`${account?.roleCds.includes("PM") && !form.roleCds.includes("PM") ? "PM이 비게 됩니다." : "담당 프로젝트 지정이 지워집니다."} 담당 프로젝트 ${impact.projectCount}건: ${impact.projects.map((item) => item.projectName).join(", ")}`}
            proceedLabel="계속"
            onCancel={() => setImpact(null)}
            onProceed={() => void continueSave()}
          />
        ) : (
          <form
            ref={container}
            className="user-form"
            data-testid="user-account-panel"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
          >
            {field(
              "userId",
              "아이디",
              editing
                ? "아이디는 바꿀 수 없습니다."
                : "영문 소문자·숫자 4~20자, 중복 불가",
              { maxLength: 20, readOnly: editing },
            )}
            {field("name", "이름", null, { maxLength: 20 })}
            {field(
              "email",
              "이메일",
              editing
                ? null
                : "저장하면 이 주소로 임시 비밀번호를 보냅니다. 첫 로그인 때 바꿔야 합니다.",
              { maxLength: 100, inputMode: "email" },
            )}
            <UserAssignmentFields
              options={options}
              value={form}
              disabled={busy}
              userId={account?.userId ?? null}
              leaderParts={account?.leaderParts ?? []}
              errors={errors}
              onChange={(value) => setForm({ ...form, ...value })}
            />
            {error && !Object.keys(errors).length && (
              <p role="alert" className="field-error" data-testid="user-error">
                {error}
              </p>
            )}
            <div className="app-actions">
              <button
                type="button"
                data-testid="user-cancel"
                disabled={busy}
                onClick={close}
              >
                취소
              </button>
              <button
                type="submit"
                className="primary-button"
                data-testid="user-save"
                disabled={busy}
              >
                {editing ? "저장" : "등록"}
              </button>
            </div>
          </form>
        )}
      </div>
    </Dialog>
  );
}

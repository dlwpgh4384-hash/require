import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import * as api from "./roles";

/** UI-24-M1 */
export function RoleCopyDialog({
  source,
  onClose,
  onSaved,
  onDirty,
}: {
  source: api.RoleSettings;
  onClose: () => void;
  onSaved: (settings: api.RoleSettings) => void;
  onDirty: (dirty: boolean) => void;
}) {
  const [name, setName] = useState("");
  const [scope, setScope] = useState(source.role.dataScopeCd);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState("");
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [closing, setClosing] = useState(false);
  const dirty = name !== "" || scope !== source.role.dataScopeCd;
  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);
  const close = () => {
    if (lock.current) return;
    if (dirty) setClosing(true);
    else onClose();
  };
  const submit = async () => {
    if (lock.current) return;
    const invalid: Record<string, string> = {};
    if (!name.trim() || [...name].length > 50)
      invalid.roleName = "새 역할 이름을 1~50자로 입력해 주세요.";
    if (!scope) invalid.dataScopeCd = "데이터 범위를 선택해 주세요.";
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    try {
      onSaved(
        await api.postCommonRoleCopy({
          sourceRoleId: source.role.roleId,
          roleName: name,
          dataScopeCd: scope,
        }),
      );
    } catch (error) {
      setFailure(
        error instanceof Error ? error.message : "역할을 만들지 못했습니다.",
      );
      if (error instanceof api.RoleRequestError)
        setErrors(
          Object.fromEntries(
            error.errors.map((item) => [item.field, item.message]),
          ),
        );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  return (
    <Dialog title="역할 추가·복사" testId="role-copy-dialog" onClose={close}>
      {closing ? (
        <InlineConfirmation
          title="나가시겠습니까?"
          description={feedbackMessages.unsaved}
          onCancel={() => setClosing(false)}
          onProceed={onClose}
        />
      ) : (
        <form
          className="role-copy-form"
          data-testid="role-copy-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <label htmlFor="role-copy-name">새 역할 이름 *</label>
          <input
            id="role-copy-name"
            data-testid="role-copy-name"
            autoComplete="off"
            value={name}
            disabled={busy}
            aria-invalid={!!errors.roleName}
            onChange={(event) => setName(event.target.value)}
          />
          {errors.roleName && (
            <p className="field-error" role="alert">
              {errors.roleName}
            </p>
          )}
          <label htmlFor="role-copy-scope">데이터 범위 *</label>
          <select
            id="role-copy-scope"
            data-testid="role-copy-scope"
            value={scope}
            disabled={busy}
            aria-invalid={!!errors.dataScopeCd}
            onChange={(event) => setScope(event.target.value)}
          >
            {source.dataScopes.map((option) => (
              <option
                key={option.code}
                value={option.code}
                data-testid={`role-copy-scope-option-${option.code.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
              >
                {option.name}
              </option>
            ))}
          </select>
          {errors.dataScopeCd && (
            <p className="field-error" role="alert">
              {errors.dataScopeCd}
            </p>
          )}
          {failure && (
            <p role="alert" className="field-error">
              {failure}
            </p>
          )}
          <div className="app-actions">
            <button
              type="button"
              data-testid="role-copy-cancel"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              type="submit"
              className="primary-button"
              data-testid="role-copy-save"
              disabled={busy}
            >
              저장
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import * as api from "./roles";

/**
 * UI-24-M1 시안 「역할 추가」·「역할 복사」 창.
 * - 추가: 새 역할 이름·데이터 범위. 모든 메뉴가 「권한 없음」으로 시작한다.
 * - 복사: 복사할 역할·새 역할 이름. 권한 표·데이터 범위·민감 항목을 그대로 복사한다.
 */
export function RoleCopyDialog({
  mode,
  roles,
  selectedRoleId,
  dataScopes,
  onClose,
  onSaved,
  onDirty,
}: {
  mode: "add" | "copy";
  roles: api.Role[];
  selectedRoleId: string;
  dataScopes: { code: string; name: string }[];
  onClose: () => void;
  onSaved: (settings: api.RoleSettings) => void;
  onDirty: (dirty: boolean) => void;
}) {
  const adding = mode === "add";
  const initialScope = dataScopes.some((option) => option.code === "PROJ")
    ? "PROJ"
    : (dataScopes[0]?.code ?? "");
  const [name, setName] = useState("");
  const [sourceId, setSourceId] = useState(selectedRoleId);
  const [scope, setScope] = useState(initialScope);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState("");
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [closing, setClosing] = useState(false);
  const dirty =
    name !== "" ||
    (adding ? scope !== initialScope : sourceId !== selectedRoleId);
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
    const source = roles.find((role) => role.roleId === sourceId);
    const invalid: Record<string, string> = {};
    if (!name.trim() || [...name].length > 50)
      invalid.roleName = "새 역할 이름을 1~50자로 입력해 주세요.";
    if (adding && !scope) invalid.dataScopeCd = "데이터 범위를 선택해 주세요.";
    if (!adding && !source)
      invalid.sourceRoleId = "복사할 역할을 선택해 주세요.";
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    try {
      onSaved(
        await api.postCommonRoleCopy(
          adding
            ? { roleName: name, dataScopeCd: scope }
            : {
                sourceRoleId: sourceId,
                roleName: name,
                dataScopeCd: source!.dataScopeCd,
              },
        ),
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
  const nameField = (
    <>
      <label htmlFor="role-copy-name" className="uf-req">
        새 역할 이름
      </label>
      <input
        id="role-copy-name"
        className="ls-in"
        data-testid="role-copy-name"
        autoComplete="off"
        placeholder="예: 프로젝트 지원"
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
    </>
  );
  return (
    <Dialog
      title={adding ? "역할 추가" : "역할 복사"}
      testId="role-copy-dialog"
      onClose={close}
    >
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
          {adding ? (
            <>
              {nameField}
              <label htmlFor="role-copy-scope" className="uf-req">
                데이터 범위
              </label>
              <select
                id="role-copy-scope"
                className="ls-in"
                data-testid="role-copy-scope"
                value={scope}
                disabled={busy}
                aria-invalid={!!errors.dataScopeCd}
                onChange={(event) => setScope(event.target.value)}
              >
                {dataScopes.map((option) => (
                  <option key={option.code} value={option.code}>
                    {option.name}
                  </option>
                ))}
              </select>
              {errors.dataScopeCd && (
                <p className="field-error" role="alert">
                  {errors.dataScopeCd}
                </p>
              )}
              <p className="roles-hint">
                새 역할은 모든 메뉴가 「권한 없음」으로 시작합니다.
              </p>
            </>
          ) : (
            <>
              <label htmlFor="role-copy-source" className="uf-req">
                복사할 역할
              </label>
              <select
                id="role-copy-source"
                className="ls-in"
                data-testid="role-copy-source"
                value={sourceId}
                disabled={busy}
                aria-invalid={!!errors.sourceRoleId}
                onChange={(event) => setSourceId(event.target.value)}
              >
                {roles.map((role) => (
                  <option key={role.roleId} value={role.roleId}>
                    {role.roleName}
                  </option>
                ))}
              </select>
              {errors.sourceRoleId && (
                <p className="field-error" role="alert">
                  {errors.sourceRoleId}
                </p>
              )}
              <p className="roles-hint">
                권한 표·데이터 범위·민감 항목을 그대로 복사합니다.
              </p>
              {nameField}
            </>
          )}
          {failure && !Object.keys(errors).length && (
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
              {adding ? "추가" : "복사"}
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

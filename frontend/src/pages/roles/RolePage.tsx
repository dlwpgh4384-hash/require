import { useEffect, useRef, useState } from "react";
import { ConfirmDialog, Toast } from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { getCommonUserOptions } from "../commonops/userOptions";
import { RoleCopyDialog } from "./RoleCopyDialog";
import { RoleImpactDialog } from "./RoleImpactDialog";
import * as api from "./roles";
import "./roles.css";

const message = (error: unknown) =>
  error instanceof Error ? error.message : "권한 정보를 처리하지 못했습니다.";

/** 시스템 › 권한 UI-24 (SCR-UI-24). 개발 단계의 실행자는 고정 admin이다. */
export function RolePage() {
  const [roles, setRoles] = useState<api.Role[] | null>(null);
  const [selected, setSelected] = useState("");
  const [settings, setSettings] = useState<api.RoleSettings | null>(null);
  const [draft, setDraft] = useState<api.RoleUpdate | null>(null);
  const [listFailure, setListFailure] = useState("");
  const [failure, setFailure] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [revision, setRevision] = useState(0);
  const [listRevision, setListRevision] = useState(0);
  const [copying, setCopying] = useState(false);
  const [copyDirty, setCopyDirty] = useState(false);
  const [impact, setImpact] = useState<api.RoleImpact | null>(null);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [notice, setNotice] = useState("");
  const [pending, setPending] = useState<(() => void) | null>(null);
  const setDirty = useScreenDirty();
  const dirty =
    !!settings &&
    !!draft &&
    JSON.stringify(draft) !== JSON.stringify(api.roleDraft(settings));
  useEffect(() => {
    setDirty("UI-24", dirty || copyDirty);
    return () => setDirty("UI-24", false);
  }, [dirty, copyDirty, setDirty]);
  useEffect(() => {
    const controller = new AbortController();
    setListFailure("");
    void api.getCommonRoles(controller.signal).then(
      (value) => {
        if (controller.signal.aborted) return;
        setRoles(value.items);
        setSelected((old) =>
          value.items.some((role) => role.roleId === old)
            ? old
            : (value.items[0]?.roleId ?? ""),
        );
      },
      (error) => {
        if (!controller.signal.aborted) setListFailure(message(error));
      },
    );
    return () => controller.abort();
  }, [listRevision]);
  useEffect(() => {
    const controller = new AbortController();
    setSettings(null);
    setDraft(null);
    setFailure("");
    setErrors({});
    if (selected)
      void api.getCommonRole(selected, controller.signal).then(
        (value) => {
          if (!controller.signal.aborted) {
            setSettings(value);
            setDraft(api.roleDraft(value));
          }
        },
        (error) => {
          if (!controller.signal.aborted) setFailure(message(error));
        },
      );
    return () => controller.abort();
  }, [selected, revision]);
  const guard = (action: () => void) => {
    if (lock.current) return;
    if (dirty) setPending(() => action);
    else action();
  };
  const failed = (error: unknown) => {
    setFailure(message(error));
    setErrors(
      error instanceof api.RoleRequestError
        ? Object.fromEntries(
            error.errors.map((item) => [item.field, item.message]),
          )
        : {},
    );
  };
  const persist = async (confirmedAffectedUserIds: string[]) => {
    if (!draft || !settings) return;
    const saved = await api.putCommonRole(selected, {
      ...draft,
      confirmedAffectedUserIds,
    });
    setImpact(null);
    setSettings(saved);
    setDraft(api.roleDraft(saved));
    setNotice("권한을 저장했습니다. 다음 요청부터 적용됩니다.");
    const refreshed = await api.getCommonRole(selected);
    setSettings(refreshed);
    setDraft(api.roleDraft(refreshed));
    setListRevision((value) => value + 1);
  };
  const save = async (confirmed?: string[]) => {
    if (lock.current || !settings || !draft) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    setErrors({});
    try {
      if (confirmed) await persist(confirmed);
      else if (draft.dataScopeCd !== settings.role.dataScopeCd) {
        const next = await api.getCommonRoleChangeImpact(
          selected,
          draft.dataScopeCd,
        );
        if (next.affectedUserIds.length) setImpact(next);
        else await persist([]);
      } else await persist([]);
    } catch (error) {
      failed(error);
      setImpact(null);
      if (
        error instanceof api.RoleRequestError &&
        error.errors.some((item) => item.field === "confirmedAffectedUserIds")
      ) {
        try {
          setImpact(
            await api.getCommonRoleChangeImpact(selected, draft.dataScopeCd),
          );
        } catch (retryError) {
          failed(retryError);
        }
      }
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const stop = async () => {
    if (lock.current || !settings || settings.role.basicRole) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    try {
      await api.postCommonRoleStop(selected);
      setSettings(null);
      setDraft(null);
      setNotice("역할의 사용을 중지했습니다.");
      setListRevision((value) => value + 1);
      await getCommonUserOptions();
    } catch (error) {
      failed(error);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const changeMenu = (
    menuId: string,
    action: api.RoleAction,
    checked: boolean,
  ) => {
    if (draft)
      setDraft({
        ...draft,
        menuAuths: draft.menuAuths.map((menu) =>
          menu.menuId === menuId
            ? { ...menu, [action]: checked ? "Y" : "N" }
            : menu,
        ),
      });
  };
  return (
    <section
      className="roles-page screen-body"
      data-testid="role-page"
      data-screen-id="UI-24"
      aria-label="권한"
    >
      <div className="roles-toolbar">
        <div role="tablist" aria-label="역할" className="roles-tabs">
          {roles?.map((role) => (
            <button
              type="button"
              role="tab"
              aria-selected={selected === role.roleId}
              data-testid={`role-tab-${role.roleId.toLowerCase()}`}
              key={role.roleId}
              disabled={busy}
              onClick={() => guard(() => setSelected(role.roleId))}
            >
              {role.roleName}
            </button>
          ))}
        </div>
        <button
          type="button"
          data-testid="role-copy-open"
          disabled={!settings || busy}
          onClick={() => guard(() => setCopying(true))}
        >
          역할 추가·복사
        </button>
      </div>
      {listFailure && (
        <div role="alert">
          {listFailure}{" "}
          <button
            type="button"
            data-testid="role-list-retry"
            onClick={() => setListRevision((value) => value + 1)}
          >
            재시도
          </button>
        </div>
      )}
      {!roles && !listFailure && <p role="status">역할을 불러오는 중입니다.</p>}
      {roles?.length === 0 && <p>활성 역할이 없습니다.</p>}
      {selected && !settings && !failure && (
        <p role="status">권한 설정을 불러오는 중입니다.</p>
      )}
      {settings && draft && (
        <form
          data-testid="role-settings"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <fieldset disabled={busy} className="roles-settings">
            <legend>{settings.role.roleName}</legend>
            <div className="roles-scope">
              <label htmlFor="role-data-scope">데이터 범위 *</label>
              <select
                id="role-data-scope"
                data-testid="role-data-scope"
                value={draft.dataScopeCd}
                aria-invalid={!!errors.dataScopeCd}
                onChange={(event) =>
                  setDraft({ ...draft, dataScopeCd: event.target.value })
                }
              >
                {settings.dataScopes.map((scope) => (
                  <option
                    key={scope.code}
                    value={scope.code}
                    data-testid={`role-data-scope-option-${scope.code.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
                  >
                    {scope.name}
                  </option>
                ))}
              </select>
            </div>
            {errors.dataScopeCd && (
              <p className="field-error">{errors.dataScopeCd}</p>
            )}
            <label className="roles-sensitive">
              <span>민감 항목</span>
              <input
                type="checkbox"
                data-testid="role-sensitive"
                checked={draft.snstvViewYn === "Y"}
                onChange={(event) =>
                  setDraft({
                    ...draft,
                    snstvViewYn: event.target.checked ? "Y" : "N",
                  })
                }
              />{" "}
              보기
            </label>
            <p className="roles-help">
              급여·지급 단가·개인별 원가·거래처 지급 계좌
            </p>
          </fieldset>
          <div className="roles-matrix-wrap">
            <table className="roles-matrix">
              <caption>메뉴 × 동작 권한 표</caption>
              <thead>
                <tr>
                  <th scope="col">묶음</th>
                  <th scope="col">메뉴</th>
                  {api.roleActions.map(([key, label]) => (
                    <th
                      scope="col"
                      key={key}
                      data-testid={`role-action-heading-${key.toLowerCase()}`}
                    >
                      {label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {settings.menuAuths.map((menu, index) => (
                  <tr
                    key={menu.menuId}
                    data-testid={`role-menu-${menu.menuId.toLowerCase()}`}
                  >
                    <th scope="row">{menu.groupName}</th>
                    <td>{menu.menuName}</td>
                    {api.roleActions.map(([key, label]) => {
                      const protectedFlag =
                        (selected === "ADMIN" &&
                          menu.menuId === "M028" &&
                          key === "updateYn") ||
                        (selected === "SALES_LDR" &&
                          menu.menuId === "M024" &&
                          key !== "readYn");
                      return (
                        <td
                          key={key}
                          data-testid={`role-action-cell-${menu.menuId.toLowerCase()}-${key.toLowerCase()}`}
                        >
                          <input
                            type="checkbox"
                            aria-label={`${menu.menuName} ${label}`}
                            data-testid={`role-${menu.menuId.toLowerCase()}-${key.toLowerCase()}`}
                            checked={draft.menuAuths[index][key] === "Y"}
                            disabled={busy || protectedFlag}
                            title={
                              protectedFlag
                                ? "업무 보호 규칙으로 변경할 수 없습니다."
                                : undefined
                            }
                            onChange={(event) =>
                              changeMenu(menu.menuId, key, event.target.checked)
                            }
                          />
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            {settings.menuAuths.length === 0 && <p>조회할 권한이 없습니다.</p>}
          </div>
          <div className="roles-footer">
            {!settings.role.basicRole && (
              <button
                type="button"
                className="app-danger"
                data-testid="role-stop"
                disabled={busy}
                onClick={() =>
                  guard(() => {
                    void stop();
                  })
                }
              >
                사용 중지
              </button>
            )}
            <div className="app-actions">
              <button
                type="button"
                data-testid="role-cancel"
                disabled={busy}
                onClick={() => guard(() => setDraft(api.roleDraft(settings)))}
              >
                취소
              </button>
              <button
                type="submit"
                className="primary-button"
                data-testid="role-save"
                disabled={busy}
              >
                저장
              </button>
            </div>
          </div>
        </form>
      )}
      {failure && (
        <div role="alert" data-testid="role-failure" className="field-error">
          {failure}
          {Object.entries(errors).map(([field, text]) => (
            <p
              key={field}
              data-testid={`role-field-error-${field.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
            >
              {text}
            </p>
          ))}
          {!settings && (
            <button
              type="button"
              data-testid="role-settings-retry"
              onClick={() => setRevision((value) => value + 1)}
            >
              재시도
            </button>
          )}
        </div>
      )}
      {copying && settings && (
        <RoleCopyDialog
          source={settings}
          onDirty={setCopyDirty}
          onClose={() => setCopying(false)}
          onSaved={(created) => {
            setCopying(false);
            setDraft(null);
            setSettings(null);
            setSelected(created.role.roleId);
            setListRevision((value) => value + 1);
            setNotice("새 역할을 만들었습니다.");
          }}
        />
      )}
      {impact && (
        <RoleImpactDialog
          impact={impact}
          busy={busy}
          onCancel={() => setImpact(null)}
          onSave={() => {
            void save(impact.affectedUserIds);
          }}
        />
      )}
      {pending && (
        <ConfirmDialog
          title="변경 내용을 버릴까요?"
          description={feedbackMessages.unsaved}
          testId="role-dirty-confirm"
          onCancel={() => setPending(null)}
          onProceed={() => {
            const action = pending;
            setPending(null);
            if (settings) setDraft(api.roleDraft(settings));
            action();
          }}
        />
      )}
      {notice && (
        <Toast
          message={notice}
          testId="role-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

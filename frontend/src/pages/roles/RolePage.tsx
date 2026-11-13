import { Fragment, useEffect, useRef, useState } from "react";
import { ConfirmDialog, Toast } from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { getCommonUserOptions } from "../commonops/userOptions";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { RoleCopyDialog } from "./RoleCopyDialog";
import { RoleHistoryDialog } from "./RoleHistoryDialog";
import { RoleImpactDialog } from "./RoleImpactDialog";
import { useRoleWorkspace } from "./RoleWorkspace";
import * as api from "./roles";
import { Banner } from "../../shared/list";
import "../../shared/list/list.css";
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
  const [copyDirty, setCopyDirty] = useState(false);
  const [impact, setImpact] = useState<api.RoleImpact | null>(null);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const root = useRef<HTMLElement>(null);
  const [notice, setNotice] = useState("");
  const [pending, setPending] = useState<(() => void) | null>(null);
  const [history, setHistory] = useState<api.RoleChange[] | null>(null);
  const [historyFailure, setHistoryFailure] = useState("");
  const [historyRevision, setHistoryRevision] = useState(0);
  const workspace = useRoleWorkspace();
  const { dialog, setDialog, setHistoryCount, setReady } = workspace;
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
    setReady(!!settings && !busy);
    return () => setReady(false);
  }, [settings, busy, setReady]);
  useEffect(() => () => setDialog(null), [setDialog]);
  // 제목줄의 [역할 추가]·[역할 복사]도 저장하지 않은 입력이 있으면 먼저 확인한다.
  useEffect(() => {
    if ((dialog === "add" || dialog === "copy") && dirty) {
      const next = dialog;
      setDialog(null);
      setPending(() => () => setDialog(next));
    }
  }, [dialog, dirty, setDialog]);
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
  useEffect(() => {
    const controller = new AbortController();
    setHistory(null);
    setHistoryFailure("");
    setHistoryCount(null);
    if (selected)
      void api.getCommonRoleChangeHistory(selected, controller.signal).then(
        (items) => {
          if (controller.signal.aborted) return;
          setHistory(items);
          setHistoryCount(items.length);
        },
        (error) => {
          if (!controller.signal.aborted) setHistoryFailure(message(error));
        },
      );
    return () => controller.abort();
  }, [selected, historyRevision, setHistoryCount]);
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
    // 서버는 바뀐 항목마다 이력 1줄(데이터 범위·민감 항목·메뉴별 동작)을 남긴다. 시안 문구에 그 줄 수를 보여 준다.
    const lines = api.changedItemCount(api.roleDraft(settings), draft);
    const saved = await api.putCommonRole(selected, {
      ...draft,
      confirmedAffectedUserIds,
    });
    setImpact(null);
    setSettings(saved);
    setDraft(api.roleDraft(saved));
    setNotice(
      `권한을 저장했습니다. 이 역할 사용자의 다음 요청부터 적용됩니다. 변경 이력에 ${lines}줄 남겼습니다.`,
    );
    const refreshed = await api.getCommonRole(selected);
    setSettings(refreshed);
    setDraft(api.roleDraft(refreshed));
    setListRevision((value) => value + 1);
    setHistoryRevision((value) => value + 1);
  };
  const save = async (confirmed?: string[]) => {
    if (lock.current || !settings || !draft) return;
    if (!confirmed && !dirty) {
      setNotice("바꾼 내용이 없습니다.");
      return;
    }
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
  const cancel = () => {
    if (!settings) return;
    if (!dirty) {
      setNotice("바꾼 내용이 없습니다.");
      return;
    }
    guard(() => setDraft(api.roleDraft(settings)));
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
  // 00-1 공통 단축키: Ctrl+S = 저장(창이 열려 있거나 다른 탭이면 쓰지 않는다).
  usePreferenceBusinessShortcuts(
    {
      onSave: () => {
        if (!root.current?.closest("[hidden]")) void save();
      },
    },
    !dialog && !impact && !pending,
  );
  const roleName = settings?.role.roleName ?? "";
  return (
    <section
      ref={root}
      className="roles-page screen-body"
      data-testid="role-page"
      data-screen-id="UI-24"
      aria-label="권한"
    >
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
      {dirty && settings && (
        <Banner kind="warn">
          「{settings.role.roleName}」에 저장하지 않은 변경이 있습니다.
        </Banner>
      )}
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
          <fieldset
            disabled={busy}
            className="roles-settings"
            aria-label={`${roleName} 설정`}
          >
            <div className="roles-field">
              <label htmlFor="role-data-scope">데이터 범위</label>
              <div>
                <select
                  id="role-data-scope"
                  className="ls-in"
                  data-testid="role-data-scope"
                  value={draft.dataScopeCd}
                  aria-invalid={!!errors.dataScopeCd}
                  aria-describedby="role-data-scope-help"
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
                {errors.dataScopeCd && (
                  <p className="field-error">{errors.dataScopeCd}</p>
                )}
                <p id="role-data-scope-help" className="roles-hint">
                  어디까지 볼지 정합니다. 전체 = 회사 전부, 담당 파트 = 기준정보
                  › 거래처·영업 파트의 영업 파트 탭에서 이번 달 파트장인 파트 +
                  시스템 › 사용자에서 고른 본사, 담당 프로젝트 = 시스템 ›
                  사용자에서 사람마다 정한 프로젝트만. 보통 PM은 담당 프로젝트,
                  파트장은 담당 파트, 나머지는 전체.
                </p>
              </div>
            </div>
            <div className="roles-field">
              <span id="role-sensitive-label">민감 항목</span>
              <label className="roles-sensitive">
                <input
                  type="checkbox"
                  data-testid="role-sensitive"
                  aria-labelledby="role-sensitive-label role-sensitive-text"
                  checked={draft.snstvViewYn === "Y"}
                  onChange={(event) =>
                    setDraft({
                      ...draft,
                      snstvViewYn: event.target.checked ? "Y" : "N",
                    })
                  }
                />
                <span id="role-sensitive-text">
                  급여·지급 단가·개인별 원가·거래처 지급 계좌 보기
                </span>
              </label>
            </div>
          </fieldset>
          <div className="roles-matrix-wrap">
            <table className="roles-matrix" aria-label="메뉴 × 동작 권한 표">
              <thead>
                <tr>
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
                {settings.menuAuths.map((menu, index) => {
                  const startsGroup =
                    !!menu.groupName &&
                    (index === 0 ||
                      settings.menuAuths[index - 1].groupName !==
                        menu.groupName);
                  return (
                    <Fragment key={menu.menuId}>
                      {startsGroup && (
                        <tr className="roles-group">
                          <th scope="rowgroup" colSpan={8}>
                            {menu.groupName}
                          </th>
                        </tr>
                      )}
                      <tr
                        data-testid={`role-menu-${menu.menuId.toLowerCase()}`}
                      >
                        <th scope="row">
                          {menu.menuName}{" "}
                          <span className="roles-screen-id">
                            {menu.screenId}
                          </span>
                        </th>
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
                                  changeMenu(
                                    menu.menuId,
                                    key,
                                    event.target.checked,
                                  )
                                }
                              />
                            </td>
                          );
                        })}
                      </tr>
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
            {settings.menuAuths.length === 0 && <p>조회할 권한이 없습니다.</p>}
          </div>
          <div className="roles-notes">
            <p>
              역할이 여러 개인 사용자는 넓은 쪽(합집합) 권한을 따릅니다.
              「확정」은 월 마감의 마감하기, 영업 파트 이익금의 정산 확정·취소,
              영업 파이프라인의 견적 승인·반려입니다. 저장하면 이 역할 사용자의
              다음 요청부터 적용됩니다. 관리자가 0명이 되는 변경은 저장할 수
              없습니다.
            </p>
            <p>
              처음 값: 관리자·재무 7개, 경영진 조회·다운로드(사용자·권한·메뉴·
              공통코드는 없음), 파트장 조회·등록·수정·다운로드, PM
              조회·등록·수정. 파트장·PM은 사용자·권한·메뉴·공통코드·월 마감,
              재무·자금 6개, 개인 원가·수익표, 프로젝트 원가 분석이 「권한
              없음」으로 시작하고(PM은 영업 파트 이익금도), 필요하면 이 표에서
              켭니다.
            </p>
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
                onClick={cancel}
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
      {(dialog === "add" || dialog === "copy") && settings && roles && (
        <RoleCopyDialog
          mode={dialog}
          roles={roles}
          selectedRoleId={selected}
          dataScopes={settings.dataScopes}
          onDirty={setCopyDirty}
          onClose={() => setDialog(null)}
          onSaved={(created) => {
            setDialog(null);
            setDraft(null);
            setSettings(null);
            setSelected(created.role.roleId);
            setListRevision((value) => value + 1);
            setNotice("새 역할을 만들었습니다.");
          }}
        />
      )}
      {dialog === "history" && (
        <RoleHistoryDialog
          roleName={roleName}
          items={history}
          failure={historyFailure}
          onRetry={() => setHistoryRevision((value) => value + 1)}
          onClose={() => setDialog(null)}
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

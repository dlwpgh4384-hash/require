import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { useCodeWorkspace } from "./CodeWorkspace";
import * as api from "./codeGroups";
import "../../shared/list/list.css";
import "./codeGroups.css";

export type CodeTab = "codes" | "maps" | "standards" | "history";
type CodePanels = Partial<Record<Exclude<CodeTab, "codes">, ReactNode>>;
export interface CodePanelContext {
  dirty: boolean;
  onDirtyChange: (dirty: boolean) => void;
  onChanged: () => Promise<void>;
  historyRevision: number;
}
const tabs: { key: CodeTab; label: string }[] = [
  { key: "codes", label: "코드" },
  { key: "maps", label: "엑셀 표기 대응" },
  { key: "standards", label: "운영 기준값" },
  { key: "history", label: "변경 이력" },
];
const message = (error: unknown) =>
  error instanceof Error ? error.message : "공통코드를 처리하지 못했습니다.";

/** UI-27 / SCR-UI-27. PC 전용 직접 주소 제한은 기존 NavigationRoot가 소유한다. */
export function CodePage({
  panels = {},
}: {
  panels?: CodePanels | ((context: CodePanelContext) => CodePanels);
}) {
  const [tab, setTab] = useState<CodeTab>("codes");
  const [groups, setGroups] = useState<api.CodeGroup[] | null>(null);
  const [groupId, setGroupId] = useState("");
  const [snapshot, setSnapshot] = useState<api.CodeItem[] | null>(null);
  const [draft, setDraft] = useState<api.CodeItem[]>([]);
  const [failure, setFailure] = useState("");
  const [groupFailure, setGroupFailure] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [revision, setRevision] = useState(0);
  const [historyRevision, setHistoryRevision] = useState(0);
  const [panelDirty, setPanelDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refreshPending, setRefreshPending] = useState(false);
  const [notice, setNotice] = useState("");
  const [modal, setModal] = useState<"group" | "code" | "delete" | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<api.CodeItem | null>(null);
  const [newGroup, setNewGroup] = useState({ groupId: "", groupName: "" });
  const [newCode, setNewCode] = useState<api.CodeInput>({
    code: "",
    codeName: "",
  });
  const [modalFailure, setModalFailure] = useState("");
  const [pending, setPending] = useState<(() => void) | null>(null);
  const lock = useRef(false);
  const root = useRef<HTMLElement>(null);
  const workspace = useCodeWorkspace();
  const setDirty = useScreenDirty();
  const group = groups?.find((row) => row.groupId === groupId);
  const system = group?.sysCodeYn === "Y";
  const numeric = group?.valCodeYn === "Y";
  const extras = api.codeExtraFields(groupId);
  const changed = snapshot
    ? draft.filter(
        (row) =>
          JSON.stringify(row) !==
          JSON.stringify(snapshot.find((old) => old.code === row.code)),
      )
    : [];
  const dirty = changed.length > 0;
  const dialogDirty =
    modal === "group"
      ? !!(newGroup.groupId || newGroup.groupName)
      : modal === "code"
        ? !!(
            newCode.code ||
            newCode.codeName ||
            extras.some((field) => newCode[field.key] !== undefined)
          )
        : false;
  useEffect(() => {
    setDirty("UI-27", dirty || dialogDirty || panelDirty);
    return () => setDirty("UI-27", false);
  }, [dirty, dialogDirty, panelDirty, setDirty]);
  const { setReady, setCanAddCode } = workspace;
  useEffect(() => {
    setReady(tab === "codes" && groups !== null && !busy && !refreshPending);
    setCanAddCode(tab === "codes" && !!group && !system);
    return () => {
      setReady(false);
      setCanAddCode(false);
    };
  }, [
    tab,
    groups,
    group,
    system,
    busy,
    refreshPending,
    setReady,
    setCanAddCode,
  ]);
  useEffect(() => {
    const controller = new AbortController();
    setGroupFailure("");
    void api.getCommonCodeGroups(controller.signal).then(
      (result) => {
        if (controller.signal.aborted) return;
        setGroups(result.items);
        setGroupId((current) => current || result.items[0]?.groupId || "");
      },
      (error) => {
        if (!controller.signal.aborted) setGroupFailure(message(error));
      },
    );
    return () => controller.abort();
  }, [revision]);
  useEffect(() => {
    if (!groupId) return;
    const controller = new AbortController();
    setSnapshot(null);
    setDraft([]);
    setFailure("");
    setErrors({});
    void api.getCommonCodes(groupId, controller.signal).then(
      (result) => {
        if (controller.signal.aborted) return;
        setSnapshot(result.items);
        setDraft(result.items);
      },
      (error) => {
        if (!controller.signal.aborted) setFailure(message(error));
      },
    );
    return () => controller.abort();
  }, [groupId, revision]);
  const open = (kind: "group" | "code") => {
    if (
      busy ||
      refreshPending ||
      !groups ||
      (kind === "code" && (!group || system))
    )
      return;
    if (dirty) {
      setFailure("먼저 변경 내용을 저장하거나 취소해 주세요.");
      return;
    }
    setNewGroup({ groupId: "", groupName: "" });
    setNewCode({ code: "", codeName: "" });
    setErrors({});
    setModalFailure("");
    setModal(kind);
  };
  const handledSignal = useRef(0);
  useEffect(() => {
    if (workspace.signal.revision > handledSignal.current) {
      handledSignal.current = workspace.signal.revision;
      open(workspace.signal.action);
    }
  }, [workspace.signal]);
  const discard = () => {
    if (snapshot) setDraft(snapshot);
    setPanelDirty(false);
    setErrors({});
    setFailure("");
  };
  const guard = (action: () => void) => {
    if (busy || refreshPending) return;
    if (dirty || panelDirty) setPending(() => action);
    else action();
  };
  const refresh = async (id: string) => {
    const [nextGroups, nextCodes] = await Promise.all([
      api.getCommonCodeGroups(),
      api.getCommonCodes(id),
    ]);
    setGroups(nextGroups.items);
    setSnapshot(nextCodes.items);
    setDraft(nextCodes.items);
    setRefreshPending(false);
  };
  const refreshConsumers = async () => {
    setHistoryRevision((value) => value + 1);
    if (groupId) await refresh(groupId);
    else {
      const result = await api.getCommonCodeGroups();
      setGroups(result.items);
      setGroupId(result.items[0]?.groupId ?? "");
    }
  };
  const resolvedPanels =
    typeof panels === "function"
      ? panels({
          dirty: dirty || dialogDirty,
          onDirtyChange: setPanelDirty,
          onChanged: refreshConsumers,
          historyRevision,
        })
      : panels;
  const errorFields = (error: unknown) =>
    error instanceof api.CodeRequestError
      ? Object.fromEntries(
          error.errors.map((entry) => [entry.field, entry.message]),
        )
      : {};
  const save = async () => {
    if (lock.current || !snapshot || !group || refreshPending) return;
    if (!dirty) {
      setNotice("바꾼 내용이 없습니다.");
      return;
    }
    const invalid = Object.assign(
      {},
      ...changed.map((row) => api.validateCode(row, groupId)),
    );
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    let saved = false;
    try {
      const result = await api.putCommonCodes(groupId, changed);
      saved = true;
      setSnapshot(result.items);
      setDraft(result.items);
      setHistoryRevision((value) => value + 1);
      api.notifyCodeOptionsChanged(groupId);
      await refresh(groupId);
      setNotice("공통코드를 저장했습니다.");
    } catch (error) {
      setFailure(
        saved
          ? "저장은 완료되었으나 다시 읽지 못했습니다. 재조회해 주세요."
          : message(error),
      );
      if (saved) setRefreshPending(true);
      else setErrors(errorFields(error));
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  usePreferenceBusinessShortcuts({
    onSave: () => {
      if (tab === "codes" && !root.current?.closest("[hidden]") && !modal)
        void save();
    },
  });
  const mutate = async () => {
    if (lock.current || !modal) return;
    const invalid: Record<string, string> =
      modal === "code" ? api.validateCode(newCode, groupId) : {};
    if (modal === "group") {
      if (!/^[A-Z][A-Z0-9_]{0,29}$/.test(newGroup.groupId))
        invalid.groupId =
          "그룹 ID는 대문자로 시작하고 대문자·숫자·_ 30자 이내로 입력해 주세요.";
      if (!newGroup.groupName.trim() || [...newGroup.groupName].length > 50)
        invalid.groupName = "그룹 이름을 1~50자로 입력해 주세요.";
    }
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    lock.current = true;
    setBusy(true);
    setModalFailure("");
    let committed = false;
    let selected = groupId;
    try {
      if (modal === "group") {
        const result = await api.postCommonCodeGroup(newGroup);
        selected = result.groupId;
      } else if (modal === "code") await api.postCommonCode(groupId, newCode);
      else if (deleteTarget)
        await api.postCommonCodeDelete(groupId, deleteTarget.code);
      committed = true;
      setHistoryRevision((value) => value + 1);
      api.notifyCodeOptionsChanged(selected);
      await refresh(selected);
      setGroupId(selected);
      setModal(null);
      setNotice("처리했습니다.");
    } catch (error) {
      if (committed) {
        setModal(null);
        setRefreshPending(true);
        setGroupId(selected);
        setFailure(
          "처리는 완료되었으나 다시 읽지 못했습니다. 재조회해 주세요.",
        );
      } else {
        setModalFailure(message(error));
        setErrors(errorFields(error));
      }
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const fieldError = (field: string) =>
    errors[field] && (
      <span className="field-error" role="alert">
        {errors[field]}
      </span>
    );
  const extraControl = (
    field: api.ExtraField,
    value: api.CodeInput,
    onChange: (patch: Partial<api.CodeItem>) => void,
    id: string,
    disabled = false,
  ) => (
    <label key={field.key}>
      {field.label}
      {field.key === "targetField" ? (
        <select
          aria-label={`${id} ${field.label}`}
          data-testid={`code-${id.toLowerCase()}-${field.key.toLowerCase()}`}
          disabled={disabled}
          value={String(value[field.key] ?? "")}
          onChange={(event) => onChange({ [field.key]: event.target.value })}
        >
          <option value="">선택</option>
          {api.targetFields.map((option) => (
            <option key={option}>{option}</option>
          ))}
        </select>
      ) : (
        <input
          className="ls-in ls-in-sm"
          type={field.numeric ? "number" : "text"}
          step={field.numeric ? "0.0001" : undefined}
          aria-label={`${id} ${field.label}`}
          data-testid={`code-${id.toLowerCase()}-${field.key.toLowerCase()}`}
          disabled={disabled}
          maxLength={field.numeric ? undefined : 100}
          value={value[field.key] ?? ""}
          onChange={(event) =>
            onChange({
              [field.key]: field.numeric
                ? event.target.value === ""
                  ? undefined
                  : Number(event.target.value)
                : event.target.value,
            })
          }
        />
      )}
      {fieldError(field.key)}
    </label>
  );
  return (
    <section
      ref={root}
      className="code-page screen-body"
      data-testid="code-page"
      data-screen-id="UI-27"
    >
      <div role="tablist" aria-label="공통코드 작업" className="code-tabs">
        {tabs.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={tab === item.key}
            aria-controls={`code-panel-${item.key}`}
            data-testid={`code-tab-${item.key}`}
            onClick={() => {
              if (item.key !== tab) guard(() => setTab(item.key));
            }}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div
        role="tabpanel"
        id="code-panel-codes"
        hidden={tab !== "codes"}
        data-testid="code-panel-codes"
      >
        {!workspace.inShell && (
          <div className="app-actions">
            <button
              type="button"
              data-testid="code-group-add"
              disabled={!groups || busy || refreshPending}
              onClick={() => open("group")}
            >
              그룹 추가
            </button>
            {group && !system && (
              <button
                type="button"
                data-testid="code-add"
                disabled={!snapshot || busy || refreshPending}
                onClick={() => open("code")}
              >
                {numeric ? "수당코드 추가" : "코드 추가"}
              </button>
            )}
          </div>
        )}
        {groupFailure && <p role="alert">{groupFailure}</p>}
        {!groups && !groupFailure && (
          <p role="status">코드 그룹을 불러오는 중입니다.</p>
        )}
        {groups?.length === 0 && <p>등록된 코드 그룹이 없습니다.</p>}
        <div className="code-columns">
          <nav
            className="code-group-list"
            aria-label="코드 그룹"
            data-testid="code-group-list"
          >
            {groups?.map((row) => (
              <button
                key={row.groupId}
                type="button"
                aria-pressed={row.groupId === groupId}
                data-testid={`code-group-${row.groupId.toLowerCase()}`}
                disabled={busy || refreshPending}
                onClick={() => {
                  if (row.groupId !== groupId)
                    guard(() => setGroupId(row.groupId));
                }}
              >
                <span>
                  {row.groupName}{" "}
                  {row.sysCodeYn === "Y" && (
                    <span role="img" aria-label="시스템 그룹">
                      🔒
                    </span>
                  )}
                </span>
                <span>{row.codeCount}</span>
              </button>
            ))}
          </nav>
          <div className="code-detail">
            {group && (
              <h2>
                {group.groupName} <small>{group.groupId}</small>
              </h2>
            )}
            {system && <p className="code-banner">이름만 바꿀 수 있습니다.</p>}
            {dirty && (
              <p className="code-banner">저장하지 않은 변경이 있습니다.</p>
            )}
            {groupId && !snapshot && !failure && (
              <p role="status">코드를 불러오는 중입니다.</p>
            )}
            {snapshot && (
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  void save();
                }}
                noValidate
              >
                <fieldset disabled={busy || refreshPending}>
                  <div className="code-table-wrap">
                    <table aria-label="그룹별 코드 표">
                      <thead>
                        <tr>
                          <th>코드</th>
                          <th>이름</th>
                          {numeric ? (
                            <>
                              <th>값</th>
                              <th>단위</th>
                            </>
                          ) : (
                            <>
                              <th>순서</th>
                              <th>사용</th>
                            </>
                          )}
                          {extras.map((field) => (
                            <th key={field.key}>{field.label}</th>
                          ))}
                          {!system && <th aria-label="삭제" />}
                        </tr>
                      </thead>
                      <tbody>
                        {draft.map((row) => (
                          <tr
                            key={row.code}
                            data-testid={`code-row-${row.code.toLowerCase()}`}
                          >
                            <td>{row.code}</td>
                            <td>
                              <input
                                className="ls-in ls-in-sm"
                                aria-label={`${row.code} 이름`}
                                data-testid={`code-name-${row.code.toLowerCase()}`}
                                maxLength={100}
                                value={row.codeName}
                                aria-invalid={!!errors.codeName}
                                onChange={(event) =>
                                  setDraft((rows) =>
                                    rows.map((item) =>
                                      item.code === row.code
                                        ? {
                                            ...item,
                                            codeName: event.target.value,
                                          }
                                        : item,
                                    ),
                                  )
                                }
                              />
                              {fieldError("codeName")}
                            </td>
                            {numeric ? (
                              <>
                                <td>
                                  <input
                                    className="ls-in ls-in-sm"
                                    type="number"
                                    step="0.0001"
                                    aria-label={`${row.code} 값`}
                                    data-testid={`code-value-${row.code.toLowerCase()}`}
                                    disabled={system}
                                    value={row.codeVal ?? 0}
                                    onChange={(event) =>
                                      setDraft((rows) =>
                                        rows.map((item) =>
                                          item.code === row.code
                                            ? {
                                                ...item,
                                                codeVal: Number(
                                                  event.target.value || 0,
                                                ),
                                              }
                                            : item,
                                        ),
                                      )
                                    }
                                  />
                                  {fieldError("codeVal")}
                                </td>
                                <td>{row.unitNm}</td>
                              </>
                            ) : (
                              <>
                                <td>{row.sortOrd}</td>
                                <td>
                                  <input
                                    type="checkbox"
                                    aria-label={`${row.code} 사용`}
                                    data-testid={`code-use-${row.code.toLowerCase()}`}
                                    disabled={system}
                                    checked={row.useYn === "Y"}
                                    onChange={(event) =>
                                      setDraft((rows) =>
                                        rows.map((item) =>
                                          item.code === row.code
                                            ? {
                                                ...item,
                                                useYn: event.target.checked
                                                  ? "Y"
                                                  : "N",
                                              }
                                            : item,
                                        ),
                                      )
                                    }
                                  />
                                </td>
                              </>
                            )}
                            {extras.map((field) => (
                              <td key={field.key}>
                                {extraControl(
                                  field,
                                  row,
                                  (patch) =>
                                    setDraft((rows) =>
                                      rows.map((item) =>
                                        item.code === row.code
                                          ? { ...item, ...patch }
                                          : item,
                                      ),
                                    ),
                                  row.code,
                                  system,
                                )}
                              </td>
                            ))}
                            {!system && (
                              <td>
                                <button
                                  type="button"
                                  data-testid={`code-delete-${row.code.toLowerCase()}`}
                                  aria-label={`${row.code} 삭제`}
                                  onClick={() => {
                                    if (dirty) {
                                      setFailure(
                                        "먼저 변경 내용을 저장하거나 취소해 주세요.",
                                      );
                                      return;
                                    }
                                    setDeleteTarget(row);
                                    setModalFailure("");
                                    setErrors({});
                                    setModal("delete");
                                  }}
                                >
                                  삭제
                                </button>
                              </td>
                            )}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {snapshot.length === 0 && <p>등록된 코드가 없습니다.</p>}
                  <div className="app-actions">
                    <button
                      type="button"
                      data-testid="code-cancel"
                      onClick={discard}
                    >
                      취소
                    </button>
                    <button
                      type="submit"
                      className="primary-button"
                      data-testid="code-save"
                    >
                      저장
                    </button>
                  </div>
                </fieldset>
              </form>
            )}
            {failure && (
              <p role="alert" data-testid="code-failure">
                {failure}
              </p>
            )}
          </div>
        </div>
        {(failure || groupFailure) && (
          <button
            type="button"
            data-testid="code-retry"
            disabled={busy}
            onClick={() => {
              if (dirty) {
                setFailure("먼저 변경 내용을 저장하거나 취소해 주세요.");
                return;
              }
              setBusy(true);
              setFailure("");
              if (refreshPending && groupId)
                void refresh(groupId)
                  .catch((error) => setFailure(message(error)))
                  .finally(() => setBusy(false));
              else {
                setRevision((value) => value + 1);
                setBusy(false);
              }
            }}
          >
            재조회
          </button>
        )}
      </div>
      {tabs
        .filter((item) => item.key !== "codes")
        .map((item) => (
          <div
            key={item.key}
            role="tabpanel"
            hidden={tab !== item.key}
            id={`code-panel-${item.key}`}
            data-testid={`code-panel-${item.key}`}
          >
            {tab === item.key &&
              (resolvedPanels[item.key as Exclude<CodeTab, "codes">] ?? (
                <p role="alert">{item.label} 화면이 연결되지 않았습니다.</p>
              ))}
          </div>
        ))}
      {modal && (
        <Dialog
          title={
            modal === "group"
              ? "그룹 추가"
              : modal === "code"
                ? numeric
                  ? "수당코드 추가"
                  : "코드 추가"
                : "코드를 삭제할까요?"
          }
          testId={`code-${modal}-dialog`}
          onClose={() => {
            if (!busy) {
              setModal(null);
              setErrors({});
            }
          }}
        >
          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void mutate();
            }}
          >
            <fieldset disabled={busy}>
              {modal === "group" ? (
                <>
                  <label>
                    그룹 ID *
                    <input
                      aria-label="그룹 ID"
                      data-testid="new-code-group-id"
                      maxLength={30}
                      value={newGroup.groupId}
                      onChange={(event) =>
                        setNewGroup((current) => ({
                          ...current,
                          groupId: event.target.value,
                        }))
                      }
                    />
                    {fieldError("groupId")}
                  </label>
                  <label>
                    그룹 이름 *
                    <input
                      aria-label="그룹 이름"
                      data-testid="new-code-group-name"
                      maxLength={50}
                      value={newGroup.groupName}
                      onChange={(event) =>
                        setNewGroup((current) => ({
                          ...current,
                          groupName: event.target.value,
                        }))
                      }
                    />
                    {fieldError("groupName")}
                  </label>
                </>
              ) : modal === "code" ? (
                <>
                  <label>
                    코드 *
                    <input
                      aria-label="새 코드"
                      data-testid="new-code-id"
                      maxLength={20}
                      value={newCode.code}
                      onChange={(event) =>
                        setNewCode((current) => ({
                          ...current,
                          code: event.target.value,
                        }))
                      }
                    />
                    {fieldError("code")}
                  </label>
                  <label>
                    이름 *
                    <input
                      aria-label="새 코드 이름"
                      data-testid="new-code-name"
                      maxLength={100}
                      value={newCode.codeName}
                      onChange={(event) =>
                        setNewCode((current) => ({
                          ...current,
                          codeName: event.target.value,
                        }))
                      }
                    />
                    {fieldError("codeName")}
                  </label>
                  {numeric && (
                    <>
                      <label>
                        값
                        <input
                          aria-label="새 수당 값"
                          data-testid="new-code-value"
                          type="number"
                          step="0.0001"
                          value={newCode.codeVal ?? 0}
                          onChange={(event) =>
                            setNewCode((current) => ({
                              ...current,
                              codeVal: Number(event.target.value || 0),
                            }))
                          }
                        />
                        {fieldError("codeVal")}
                      </label>
                      <label>
                        단위
                        <input
                          aria-label="새 수당 단위"
                          data-testid="new-code-unit"
                          maxLength={10}
                          value={newCode.unitNm ?? ""}
                          onChange={(event) =>
                            setNewCode((current) => ({
                              ...current,
                              unitNm: event.target.value,
                            }))
                          }
                        />
                        {fieldError("unitNm")}
                      </label>
                    </>
                  )}
                  {extras.map((field) =>
                    extraControl(
                      field,
                      newCode,
                      (patch) =>
                        setNewCode((current) => ({ ...current, ...patch })),
                      "new",
                    ),
                  )}
                </>
              ) : (
                <p>
                  {deleteTarget?.codeName} ({deleteTarget?.code}) 코드를
                  목록에서 숨깁니다. 사용된 코드는 삭제할 수 없습니다.
                </p>
              )}
              {modalFailure && <p role="alert">{modalFailure}</p>}
              <div className="app-actions">
                <button
                  type="button"
                  data-testid="code-dialog-cancel"
                  onClick={() => {
                    setModal(null);
                    setErrors({});
                  }}
                >
                  취소
                </button>
                <button
                  type="submit"
                  data-testid="code-dialog-submit"
                  className={
                    modal === "delete" ? "app-danger" : "primary-button"
                  }
                >
                  {modal === "delete" ? "삭제" : "추가"}
                </button>
              </div>
            </fieldset>
          </form>
        </Dialog>
      )}
      {pending && (
        <Dialog
          title="변경 내용을 버릴까요?"
          testId="code-discard-dialog"
          onClose={() => setPending(null)}
        >
          <p>저장하지 않은 내용이 있습니다.</p>
          <div className="app-actions">
            <button
              type="button"
              data-testid="code-discard-cancel"
              onClick={() => setPending(null)}
            >
              취소
            </button>
            <button
              type="button"
              data-testid="code-discard-proceed"
              onClick={() => {
                discard();
                pending();
                setPending(null);
              }}
            >
              버리기
            </button>
          </div>
        </Dialog>
      )}
      {notice && (
        <Toast
          message={notice}
          testId="code-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

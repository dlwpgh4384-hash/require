import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { useCodeWorkspace } from "./CodeWorkspace";
import * as api from "./codeGroups";
import { Banner, DEFAULT_PAGE_SIZE, ListPager } from "../../shared/list";
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
/** 시안 UI-27 문구. */
const NOTE_GENERAL =
  "이름을 바꾸면 저장 즉시 선택 목록에 반영되고, 지난 자료에도 새 이름으로 보입니다(코드별 적용 시작일은 두지 않음). 한 번도 쓰지 않은 코드만 [삭제]할 수 있습니다(목록에서 사라지고 변경 이력에 남음). 쓴 코드는 지울 수 없고 사용만 끕니다. 그룹 자체의 이름 바꾸기·사용 끄기는 없습니다.";
const NOTE_EXTRA_GROUPS =
  "추가값 칸은 직무·스킬·프로젝트 단계명·은행·이력서 이름표·이력서 직무 단어 그룹에만 있습니다.";
const NUMERIC_INTRO =
  "그룹장·팀장 수당, 4대보험(회사) 부담률, 퇴직금 적립률, 팀비·프로젝트경비(1인당 월) 기본값을 관리합니다. 바꾸면 UI-03 개인원가수익표와 전체 원가 계산에 바로 적용됩니다.";
const NUMERIC_NOTE =
  "프로젝트별로 실제 팀비·경비·예산을 따로 입력했으면(UI-01) 그 값이 우선합니다. 여기 값은 새 프로젝트의 기본값입니다.";
const lockIcon = (
  <svg
    width="14"
    height="14"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    aria-hidden="true"
  >
    <rect x="5" y="11" width="14" height="10" rx="2" />
    <path d="M8 11V7a4 4 0 0 1 8 0v4" />
  </svg>
);
/** 시안: 시스템 그룹(자물쇠)을 위에, 업무 그룹(코드 수)을 아래에 둔다. */
const systemFirst = (rows: api.CodeGroup[]) => [
  ...rows.filter((row) => row.sysCodeYn === "Y"),
  ...rows.filter((row) => row.sysCodeYn !== "Y"),
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
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
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
  const { setReady, setCanAddCode, setTab: setWorkspaceTab, setNumeric } =
    workspace;
  useEffect(() => {
    setWorkspaceTab(tab);
    setNumeric(numeric);
  }, [tab, numeric, setWorkspaceTab, setNumeric]);
  useEffect(() => setPage(1), [groupId]);
  const sortedGroups = groups ? systemFirst(groups) : null;
  const pagedDraft = numeric
    ? draft
    : draft.slice((page - 1) * pageSize, page * pageSize);
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
        setGroupId(
          (current) => current || systemFirst(result.items)[0]?.groupId || "",
        );
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
      if (workspace.signal.action !== "map") open(workspace.signal.action);
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
      setGroupId(systemFirst(result.items)[0]?.groupId ?? "");
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
        const text = message(error);
        // 시안: 「이름」은(는) 사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.
        setModalFailure(
          modal === "delete" && deleteTarget && text.startsWith("사용된 코드")
            ? `「${deleteTarget.codeName}」은(는) ${text}`
            : text,
        );
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
      {id === "new" ? (
        <span className="code-fl">
          {field.label}
          <span className="field-required"> *</span>
        </span>
      ) : (
        field.label
      )}
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
      {id === "new" && <span className="code-help">{field.help}</span>}
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
            {sortedGroups?.map((row) => (
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
                <span>{row.groupName}</span>
                {row.sysCodeYn === "Y" ? (
                  <span
                    className="code-group-lock"
                    role="img"
                    aria-label="시스템 그룹"
                  >
                    {lockIcon}
                  </span>
                ) : (
                  <span className="code-group-count">{row.codeCount}</span>
                )}
              </button>
            ))}
          </nav>
          <div className="code-detail">
            {system && (
              <Banner kind="lock">
                <span className="code-banner-icon">{lockIcon}</span>
                계산 규칙에 쓰는 시스템 코드입니다. 이름만 바꿀 수 있고
                추가·삭제·사용 끄기는 할 수 없습니다.
              </Banner>
            )}
            {numeric && <p className="code-intro">{NUMERIC_INTRO}</p>}
            {group && (
              <div className="code-detail-head">
                <h2 className="code-tl">
                  <strong>{group.groupName}</strong>{" "}
                  {!numeric && <span className="code-sub">{group.groupId}</span>}{" "}
                  · {snapshot ? snapshot.length : group.codeCount}개
                </h2>
                {workspace.inShell && !system && !numeric && (
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm code-add-button"
                    data-testid="code-add"
                    disabled={!snapshot || busy || refreshPending}
                    onClick={() => open("code")}
                  >
                    <svg
                      width="16"
                      height="16"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.8"
                      strokeLinecap="round"
                      aria-hidden="true"
                    >
                      <path d="M12 5v14M5 12h14" />
                    </svg>
                    코드 추가
                  </button>
                )}
              </div>
            )}
            {dirty && (
              <Banner kind="warn">
                저장하지 않은 변경 {changed.length}건이 있습니다.
              </Banner>
            )}
            {groupId && !snapshot && !failure && (
              <p role="status">코드를 불러오는 중입니다.</p>
            )}
            {snapshot && (
              <form
                id="code-form"
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
                          {extras.map((field) => (
                            <th
                              key={field.key}
                              className={field.numeric ? "num" : undefined}
                            >
                              {field.label}
                            </th>
                          ))}
                          {numeric ? (
                            <>
                              <th>값</th>
                              <th>단위</th>
                            </>
                          ) : (
                            <>
                              <th className="ctr">순서</th>
                              <th className="ctr">사용</th>
                            </>
                          )}
                          {!system && !numeric && <th className="ctr">삭제</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {pagedDraft.map((row) => (
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
                            {extras.map((field) => (
                              <td
                                key={field.key}
                                className={field.numeric ? "num" : undefined}
                              >
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
                                <td className="ctr">{row.sortOrd}</td>
                                <td className="ctr">
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
                            {!system && !numeric && (
                              <td className="ctr">
                                <button
                                  type="button"
                                  className="ls-btn ls-btn-sm ls-btn-t"
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
                  {!numeric && snapshot.length > 0 && (
                    <ListPager
                      page={page}
                      pageSize={pageSize}
                      total={draft.length}
                      onPage={setPage}
                      onPageSize={(size) => {
                        setPageSize(size);
                        setPage(1);
                      }}
                    />
                  )}
                  {extras.length > 0 && (
                    <p className="code-note" data-testid="code-extra-help">
                      {extras
                        .map((field) => `${field.label}: ${field.help}`)
                        .join(" · ")}
                      . {NOTE_EXTRA_GROUPS}
                    </p>
                  )}
                  <p className="code-note">
                    {numeric ? NUMERIC_NOTE : NOTE_GENERAL}
                  </p>
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
        {snapshot && (
          <div className="app-actions code-footer">
            <button
              type="button"
              data-testid="code-cancel"
              disabled={busy || refreshPending}
              onClick={discard}
            >
              취소
            </button>
            <button
              type="submit"
              form="code-form"
              className="primary-button"
              data-testid="code-save"
              disabled={busy || refreshPending}
            >
              저장
            </button>
          </div>
        )}
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
              ? "코드 그룹 추가"
              : modal === "code"
                ? numeric
                  ? "수당코드 추가"
                  : `코드 추가 · ${group?.groupName ?? ""}`
                : "코드를 삭제할까요?"
          }
          testId={`code-${modal}-dialog`}
          closable
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
                    <span className="code-fl">그룹 ID<span className="field-required"> *</span></span>
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
                    <span className="code-help">
                      영문 대문자로 시작, 영문 대문자·숫자·_ 30자까지. 만든
                      뒤에는 바꿀 수 없습니다.
                    </span>
                    {fieldError("groupId")}
                  </label>
                  <label>
                    <span className="code-fl">그룹 이름<span className="field-required"> *</span></span>
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
                    <span className="code-help">
                      새 업무 코드 그룹입니다. 만든 뒤에는 이름을 바꾸거나 끌 수
                      없습니다.
                    </span>
                    {fieldError("groupName")}
                  </label>
                </>
              ) : modal === "code" ? (
                <>
                  <label>
                    <span className="code-fl">코드<span className="field-required"> *</span></span>
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
                    {!numeric && (
                      <span className="code-help">
                        영문 대문자·숫자·_ , 그룹 안에서 중복 불가
                      </span>
                    )}
                    {fieldError("code")}
                  </label>
                  <label>
                    <span className="code-fl">이름<span className="field-required"> *</span></span>
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
                        <span className="code-fl">값<span className="field-required"> *</span></span>
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
                        <span className="code-fl">단위<span className="field-required"> *</span></span>
                        <select
                          aria-label="새 수당 단위"
                          data-testid="new-code-unit"
                          value={newCode.unitNm ?? ""}
                          onChange={(event) =>
                            setNewCode((current) => ({
                              ...current,
                              unitNm: event.target.value,
                            }))
                          }
                        >
                          <option value="">선택</option>
                          <option value="원">원</option>
                          <option value="%">%</option>
                        </select>
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

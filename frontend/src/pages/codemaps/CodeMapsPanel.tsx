import { useEffect, useRef, useState } from "react";
import {
  Dialog,
  InlineConfirmation,
  Toast,
} from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useCodeWorkspace } from "../codegroups/CodeWorkspace";
import * as api from "./codeMaps";
import "../../shared/list/list.css";
import "./codeMaps.css";

const message = (error: unknown) =>
  error instanceof Error ? error.message : "처리하지 못했습니다.";
const empty = (): api.CodeMapItem => ({ excelText: "", groupId: "", code: "" });

/** UI-27 / SCR-UI-27: CodePage의 엑셀 표기 대응 탭. 라우팅과 제목은 CodeWorkspace 소유. */
export function CodeMapsPanel({
  onChanged,
  onDirtyChange,
  dirty = false,
}: {
  onChanged?: () => void | Promise<void>;
  onDirtyChange?: (dirty: boolean) => void;
  dirty?: boolean;
}) {
  const [rows, setRows] = useState<api.CodeMapItem[] | null>(null);
  const [selection, setSelection] = useState<string[]>([]);
  const [failure, setFailure] = useState("");
  const [revision, setRevision] = useState(0);
  const [refreshPending, setRefreshPending] = useState(false);
  const [dialog, setDialog] = useState<"add" | "delete" | null>(null);
  const [draft, setDraft] = useState(empty);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [dialogFailure, setDialogFailure] = useState("");
  const [groups, setGroups] = useState<api.CodeMapGroup[] | null>(null);
  const [options, setOptions] = useState<api.CodeMapOption[] | null>(null);
  const [optionFailure, setOptionFailure] = useState("");
  const [optionRevision, setOptionRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [closing, setClosing] = useState(false);
  const lock = useRef(false);
  const setScreenDirty = useScreenDirty();
  const form = useRef<HTMLFormElement>(null);
  const workspace = useCodeWorkspace();
  const handledSignal = useRef(workspace.signal.revision);
  const draftDirty = dialog === "add" && Object.values(draft).some(Boolean);
  useEffect(() => {
    if (onDirtyChange) {
      onDirtyChange(draftDirty);
      return () => onDirtyChange(false);
    }
    setScreenDirty("UI-27", dirty || draftDirty);
    return () => setScreenDirty("UI-27", false);
  }, [draftDirty, dirty, onDirtyChange, setScreenDirty]);
  useEffect(() => {
    const controller = new AbortController();
    setFailure("");
    setRefreshPending(true);
    void api.getCommonCodeMaps(controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) {
          setRows(result.items);
          setSelection([]);
          setRefreshPending(false);
        }
      },
      (error) => {
        if (!controller.signal.aborted) setFailure(message(error));
      },
    );
    return () => controller.abort();
  }, [revision]);
  useEffect(() => {
    if (dialog !== "add") return;
    const controller = new AbortController();
    setGroups(null);
    setOptionFailure("");
    void api.getCodeMapGroups(controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setGroups(result.items);
      },
      (error) => {
        if (!controller.signal.aborted) setOptionFailure(message(error));
      },
    );
    return () => controller.abort();
  }, [dialog, optionRevision]);
  useEffect(() => {
    setOptions(null);
    if (dialog !== "add" || !draft.groupId) return;
    const controller = new AbortController();
    setOptionFailure("");
    void api.getCodeMapOptions(draft.groupId, controller.signal).then(
      (result) => {
        if (!controller.signal.aborted)
          setOptions(result.items.filter((row) => row.useYn === "Y"));
      },
      (error) => {
        if (!controller.signal.aborted) setOptionFailure(message(error));
      },
    );
    return () => controller.abort();
  }, [dialog, draft.groupId, optionRevision]);
  useEffect(() => {
    if (Object.keys(errors).length)
      form.current
        ?.querySelector<HTMLElement>("[aria-invalid='true']")
        ?.focus();
  }, [errors]);
  const close = () => {
    if (busy) return;
    if (draftDirty) setClosing(true);
    else setDialog(null);
  };
  const selected = (rows ?? []).filter((row) =>
    selection.includes(api.codeMapKey(row)),
  );
  const refresh = async () => {
    setRefreshPending(true);
    const result = await api.getCommonCodeMaps();
    setRows(result.items);
    setSelection([]);
    await onChanged?.();
    window.dispatchEvent(new Event(api.CODE_MAP_CHANGED_EVENT));
    setRefreshPending(false);
  };
  const submit = async () => {
    if (lock.current || refreshPending) return;
    const invalid: Record<string, string> = {};
    if (dialog === "add") {
      if (!draft.excelText.trim() || [...draft.excelText].length > 100)
        invalid.excelText = "엑셀 표기는 1~100자로 입력해 주세요.";
      if (!groups?.some((row) => row.groupId === draft.groupId))
        invalid.groupId = "코드 그룹을 골라 주세요.";
      if (!options?.some((row) => row.code === draft.code))
        invalid.code = "바꿀 코드를 골라 주세요.";
    } else if (!selected.length) invalid.items = "삭제할 대응을 선택해 주세요.";
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    lock.current = true;
    setBusy(true);
    setDialogFailure("");
    let saved = false;
    try {
      if (dialog === "add") await api.postCommonCodeMap(draft);
      else await api.postCommonCodeMapDelete(selected);
      saved = true;
      setDialog(null);
      setDraft(empty());
      setNotice("표기 대응을 반영했습니다.");
      await refresh();
    } catch (error) {
      if (saved)
        setFailure(
          "반영은 완료되었으나 다시 읽지 못했습니다. 재조회해 주세요.",
        );
      else {
        setDialogFailure(message(error));
        if (error instanceof api.CodeMapRequestError)
          setErrors(
            Object.fromEntries(
              error.errors.map((row) => [row.field, row.message]),
            ),
          );
      }
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const open = (kind: "add" | "delete") => {
    if (dirty) {
      setNotice("저장하지 않은 변경을 먼저 저장하거나 취소해 주세요.");
      return;
    }
    if (kind === "delete" && !selected.length) {
      setNotice("삭제할 대응을 선택해 주세요.");
      return;
    }
    setDraft(empty());
    setErrors({});
    setDialogFailure("");
    setClosing(false);
    setDialog(kind);
  };
  // 시안: [추가]는 제목줄 오른쪽(CodeTitleActions)에 있다.
  useEffect(() => {
    if (workspace.signal.revision <= handledSignal.current) return;
    handledSignal.current = workspace.signal.revision;
    if (workspace.signal.action === "map" && rows && !busy && !refreshPending)
      open("add");
  }, [workspace.signal]);
  return (
    <section
      className="code-maps-panel ls-root"
      aria-label="엑셀 표기 대응"
      data-testid="code-maps-panel"
      data-screen-id="UI-27"
    >
      <p className="code-maps-intro">
        엑셀 업로드 때 이 표로 표기를 코드로 바꿔 읽고, 바꾼 사실을 경고로
        알립니다(예: 「정직원」을 「정규직」으로 바꿈). 표에 없는 모르는 표기는
        오류로 알려 주고 관리자가 여기에 추가합니다.
      </p>
      <div className="code-maps-toolbar">
        <h2>
          엑셀 표기 대응
          <span className="code-maps-count"> · {rows?.length ?? 0}개</span>
        </h2>
        {!workspace.inShell && (
          <button
            type="button"
            className="ls-btn ls-btn-p ls-btn-sm"
            data-testid="code-map-add"
            disabled={!rows || busy || refreshPending}
            onClick={() => open("add")}
          >
            추가
          </button>
        )}
        <button
          type="button"
          className="ls-btn ls-btn-dt ls-btn-sm"
          data-testid="code-map-delete"
          disabled={!rows || busy || refreshPending}
          onClick={() => open("delete")}
        >
          삭제
        </button>
      </div>
      {!rows && !failure && <p role="status">표기 대응을 불러오는 중입니다.</p>}
      {rows && (
        <table className="code-maps-table" aria-label="엑셀 표기 대응 목록">
          <thead>
            <tr>
              <th className="code-maps-check">
                <span className="visually-hidden">선택</span>
              </th>
              <th>엑셀 표기</th>
              <th>코드 그룹</th>
              <th>바꿀 코드</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={4} className="code-maps-empty">
                  등록된 표기가 없습니다.
                </td>
              </tr>
            )}
            {rows.map((row, index) => (
              <tr
                key={api.codeMapKey(row)}
                data-testid={`code-map-row-${index}`}
              >
                <td>
                  <input
                    type="checkbox"
                    data-testid={`code-map-select-${index}`}
                    aria-label={`${row.excelText} ${row.groupId} 선택`}
                    disabled={busy || refreshPending}
                    checked={selection.includes(api.codeMapKey(row))}
                    onChange={(event) =>
                      setSelection((keys) =>
                        event.target.checked
                          ? [...keys, api.codeMapKey(row)]
                          : keys.filter((key) => key !== api.codeMapKey(row)),
                      )
                    }
                  />
                </td>
                <td>{row.excelText}</td>
                <td>{row.groupId}</td>
                <td>{row.code}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {failure && (
        <div role="alert">
          {failure}
          <button
            type="button"
            data-testid="code-map-retry"
            disabled={busy}
            onClick={() => setRevision((value) => value + 1)}
          >
            재조회
          </button>
        </div>
      )}
      <p className="code-maps-note">
        처음에는 비어 있습니다. 업로드에서 모르는 표기가 나오면 여기에
        추가합니다. 바꾼 내용은 [변경 이력]에 남습니다.
      </p>
      {dialog && (
        <Dialog
          title={dialog === "add" ? "엑셀 표기 추가" : "표기 대응 삭제 확인"}
          testId={`code-map-${dialog}-dialog`}
          onClose={close}
        >
          <form
            ref={form}
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
          >
            <fieldset disabled={busy || closing} className="code-maps-fields">
              {dialog === "add" ? (
                <>
                  <label>
                    엑셀 표기
                    <input
                      className="ls-in"
                      data-testid="code-map-text"
                      maxLength={100}
                      value={draft.excelText}
                      aria-invalid={!!errors.excelText}
                      onChange={(event) =>
                        setDraft({ ...draft, excelText: event.target.value })
                      }
                    />
                    <span className="code-maps-help">
                      엑셀에 실제로 적혀 오는 글자 그대로
                    </span>
                  </label>
                  {errors.excelText && (
                    <p className="field-error">{errors.excelText}</p>
                  )}
                  <label>
                    코드 그룹
                    <select
                      className="ls-in"
                      data-testid="code-map-group"
                      value={draft.groupId}
                      disabled={!groups}
                      aria-invalid={!!errors.groupId}
                      onChange={(event) => {
                        setDraft({
                          ...draft,
                          groupId: event.target.value,
                          code: "",
                        });
                        setOptions(null);
                      }}
                    >
                      <option value="">선택해 주세요</option>
                      {groups?.map((row) => (
                        <option key={row.groupId} value={row.groupId}>
                          {row.groupName}
                        </option>
                      ))}
                    </select>
                  </label>
                  {errors.groupId && (
                    <p className="field-error">{errors.groupId}</p>
                  )}
                  <label>
                    바꿀 코드
                    <select
                      className="ls-in"
                      data-testid="code-map-code"
                      value={draft.code}
                      disabled={!options}
                      aria-invalid={!!errors.code}
                      onChange={(event) =>
                        setDraft({ ...draft, code: event.target.value })
                      }
                    >
                      <option value="">선택해 주세요</option>
                      {options?.map((row) => (
                        <option key={row.code} value={row.code}>
                          {row.code} · {row.codeName}
                        </option>
                      ))}
                    </select>
                  </label>
                  {errors.code && <p className="field-error">{errors.code}</p>}
                  {(!groups || (draft.groupId && !options)) &&
                    !optionFailure && (
                      <p role="status">선택 목록을 불러오는 중입니다.</p>
                    )}
                  {groups?.length === 0 && <p>등록된 그룹이 없습니다.</p>}
                  {options?.length === 0 && (
                    <p>사용할 수 있는 코드가 없습니다.</p>
                  )}
                  {optionFailure && (
                    <div role="alert">
                      {optionFailure}
                      <button
                        type="button"
                        data-testid="code-map-options-retry"
                        onClick={() => setOptionRevision((value) => value + 1)}
                      >
                        선택 목록 재시도
                      </button>
                    </div>
                  )}
                </>
              ) : (
                <>
                  <ul>
                    {selected.map((row) => (
                      <li key={api.codeMapKey(row)}>
                        {row.excelText} / {row.groupId} / {row.code}
                      </li>
                    ))}
                  </ul>
                  <p>
                    선택한 대응을 실제 삭제할까요? 다음 업로드부터 모르는 표기는
                    오류가 됩니다.
                  </p>
                  {errors.items && (
                    <p className="field-error">{errors.items}</p>
                  )}
                </>
              )}
              {dialogFailure && <p role="alert">{dialogFailure}</p>}
              <div className="app-actions">
                <button
                  type="button"
                  data-testid="code-map-cancel"
                  onClick={close}
                >
                  취소
                </button>
                <button
                  type="submit"
                  className={
                    dialog === "delete" ? "app-danger" : "primary-button"
                  }
                  data-testid="code-map-submit"
                  disabled={
                    dialog === "add" && (!groups || !options || !!optionFailure)
                  }
                >
                  {dialog === "add" ? "추가" : "삭제"}
                </button>
              </div>
            </fieldset>
          </form>
          {closing && (
            <InlineConfirmation
              title="입력을 버리고 닫을까요?"
              description="아직 추가하지 않은 입력은 사라집니다."
              onCancel={() => setClosing(false)}
              onProceed={() => {
                setClosing(false);
                setDraft(empty());
                setDialog(null);
              }}
            />
          )}
        </Dialog>
      )}
      {notice && (
        <Toast
          message={notice}
          onDismiss={() => setNotice("")}
          testId="code-map-toast"
        />
      )}
    </section>
  );
}

import { useEffect, useRef, useState } from "react";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { koreanThisMonth } from "../../shared/time";
import * as api from "./operatingStandards";
import "../../shared/list/list.css";
import "./operatingStandards.css";

const message = (error: unknown) =>
  error instanceof Error ? error.message : "운영 기준값을 처리하지 못했습니다.";
const monthNow = () => {
  const month = koreanThisMonth();
  return `${month.slice(0, 4)}-${month.slice(4)}`;
};

/** SCR-UI-27 운영 기준값 탭. 네 탭의 CodePage 작업 공간에서 이 패널을 사용한다. */
export function OperatingStandardsPanel({
  onChanged,
  onDirtyChange,
}: {
  onChanged?: () => void | Promise<void>;
  onDirtyChange?: (dirty: boolean) => void;
}) {
  const [snapshot, setSnapshot] = useState<api.OperatingStandard[] | null>(
    null,
  );
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [refreshPending, setRefreshPending] = useState(false);
  const [revision, setRevision] = useState(0);
  const [confirmSave, setConfirmSave] = useState(false);
  const [month, setMonth] = useState(monthNow);
  const [historyCode, setHistoryCode] = useState<string | null>(null);
  const [deleteRow, setDeleteRow] = useState<api.OperatingStandard | null>(
    null,
  );
  const [historyDraft, setHistoryDraft] = useState<Record<string, string>>({});
  const lock = useRef(false);
  const root = useRef<HTMLElement>(null);
  const setDirty = useScreenDirty();
  const current = api.currentOperatingRows(snapshot ?? [], monthNow());
  const changed = current.filter(
    (row) =>
      draft[row.standardCode] !== undefined &&
      draft[row.standardCode] !== row.value,
  );
  const historyDirty = (snapshot ?? []).some(
    (row) =>
      historyDraft[api.operatingKey(row)] !== undefined &&
      historyDraft[api.operatingKey(row)] !== row.value,
  );
  const dirty = changed.length > 0 || historyDirty;
  useEffect(() => {
    if (onDirtyChange) {
      onDirtyChange(dirty);
      return () => onDirtyChange(false);
    }
    setDirty("UI-27", dirty);
    return () => setDirty("UI-27", false);
  }, [dirty, setDirty, onDirtyChange]);
  useEffect(() => {
    const controller = new AbortController();
    setFailure("");
    void api.getCommonOperatingStandards(controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) {
          setSnapshot(result.items);
          setDraft({});
        }
      },
      (error) => {
        if (!controller.signal.aborted) setFailure(message(error));
      },
    );
    return () => controller.abort();
  }, [revision]);
  useEffect(() => {
    if (Object.keys(errors).length)
      root.current
        ?.querySelector<HTMLElement>("[aria-invalid='true']")
        ?.focus();
  }, [errors]);
  const changesForMonth = (): api.OperatingStandardChange[] =>
    changed.map((row) => ({
      standardCode: row.standardCode,
      value: draft[row.standardCode],
      effectiveMonth: row.immediate ? null : month,
      action:
        row.immediate ||
        snapshot?.some(
          (item) =>
            item.standardCode === row.standardCode &&
            item.effectiveMonth === month,
        )
          ? "update"
          : "add",
    }));
  const refresh = async () => {
    const result = await api.getCommonOperatingStandards();
    setSnapshot(result.items);
    setDraft({});
    setHistoryDraft({});
    await onChanged?.();
    setRefreshPending(false);
  };
  const submit = async (changes: api.OperatingStandardChange[]) => {
    if (lock.current || refreshPending || !changes.length) return;
    const invalid = api.validateOperatingChanges(
      snapshot ?? [],
      changes,
      changes.find((row) => row.effectiveMonth)?.effectiveMonth ?? month,
    );
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    let saved = false;
    try {
      const result = await api.putCommonOperatingStandards(changes);
      saved = true;
      setDeleteRow(null);
      setSnapshot(result.items);
      setDraft({});
      setHistoryDraft({});
      setConfirmSave(false);
      setNotice("운영 기준값을 저장했습니다.");
      await refresh();
    } catch (error) {
      if (saved) {
        setRefreshPending(true);
        setFailure(
          "저장은 완료되었으나 다시 읽지 못했습니다. 재조회해 주세요.",
        );
      } else {
        setFailure(message(error));
        if (error instanceof api.OperatingStandardsRequestError)
          setErrors(
            Object.fromEntries(
              error.errors.map((entry) => [entry.field, entry.message]),
            ),
          );
      }
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const openSave = () => {
    if (!changed.length) {
      setNotice("바꾼 내용이 없습니다.");
      return;
    }
    const invalid = api.validateOperatingChanges(
      snapshot ?? [],
      changesForMonth(),
      month,
    );
    setErrors(invalid);
    if (!Object.keys(invalid).length) setConfirmSave(true);
  };
  const discard = () => {
    setDraft({});
    setHistoryDraft({});
    setErrors({});
    setFailure("");
  };
  const blocked = busy || refreshPending;
  return (
    <section
      ref={root}
      className="operating-standards-panel"
      data-testid="operating-standards-panel"
      data-screen-id="SCR-UI-27"
      aria-label="운영 기준값"
    >
      {!snapshot && !failure && (
        <p role="status">운영 기준값을 불러오는 중입니다.</p>
      )}
      {dirty && (
        <p className="operating-standards-warning">
          저장하지 않은 변경이 있습니다. 저장 또는 취소해 주세요.
        </p>
      )}
      {snapshot && !snapshot.length && <p>등록된 운영 기준값이 없습니다.</p>}
      {snapshot && snapshot.length > 0 && (
        <>
          <p className="operating-standards-help">
            미마감 시작월 이력은 수정·삭제할 수 있습니다. 마감된 값은 새
            시작월로 추가해 주세요.
          </p>
          {api.operatingGroups.map((group) => {
            const rows = group.codes.flatMap((code) =>
              current.filter((row) => row.standardCode === code),
            );
            if (!rows.length) return null;
            return (
              <section
                key={group.title}
                className="operating-standards-group"
                aria-label={group.title}
                data-testid={`operating-group-${api.operatingTestKey(group.codes[0])}`}
              >
                <h3>{group.title}</h3>
                <div className="operating-standards-grid">
                  {rows.map((row) => {
                    const key = api.operatingTestKey(row.standardCode);
                    const error =
                      errors[row.standardCode] ??
                      (draft[row.standardCode] !== undefined
                        ? errors.value
                        : undefined);
                    return (
                      <div
                        key={row.standardCode}
                        className="operating-standard-card"
                        data-testid={`operating-card-${key}`}
                      >
                        <label htmlFor={`operating-value-${key}`}>
                          {row.standardName}
                        </label>
                        <div className="operating-standard-value">
                          <input
                            id={`operating-value-${key}`}
                            className="ls-in ls-in-sm"
                            data-testid={`operating-value-${key}`}
                            type={
                              row.standardCode === "CASH_BASE_YM"
                                ? "month"
                                : "text"
                            }
                            inputMode={
                              row.standardCode === "CASH_BASE_YM"
                                ? undefined
                                : "decimal"
                            }
                            value={draft[row.standardCode] ?? row.value}
                            disabled={blocked || (row.immediate && row.closed)}
                            aria-invalid={!!error}
                            onChange={(event) => {
                              setDraft((old) => ({
                                ...old,
                                [row.standardCode]: event.target.value,
                              }));
                              setErrors({});
                            }}
                          />
                          <span>{row.unit}</span>
                        </div>
                        {error && (
                          <p className="field-error" role="alert">
                            {error}
                          </p>
                        )}
                        {row.immediate ? (
                          <p>
                            {row.closed
                              ? "시작 월 마감 후 잠금"
                              : "적용 시작월 없이 바로 반영"}
                          </p>
                        ) : (
                          <div className="operating-standard-history-link">
                            <span>적용 {row.effectiveMonth}</span>
                            <button
                              type="button"
                              data-testid={`operating-history-${key}`}
                              disabled={blocked}
                              onClick={() => {
                                if (dirty) {
                                  setFailure("먼저 저장 또는 취소해 주세요.");
                                  return;
                                }
                                setHistoryDraft({});
                                setErrors({});
                                setFailure("");
                                setHistoryCode(row.standardCode);
                              }}
                            >
                              이력
                            </button>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </section>
            );
          })}
          <div className="app-actions">
            <button
              type="button"
              data-testid="operating-cancel"
              disabled={blocked}
              onClick={discard}
            >
              취소
            </button>
            <button
              type="button"
              className="primary-button"
              data-testid="operating-save"
              disabled={blocked}
              onClick={openSave}
            >
              저장
            </button>
          </div>
        </>
      )}
      {failure && (
        <div role="alert" data-testid="operating-failure">
          {failure}
          {(!snapshot || refreshPending) && (
            <button
              type="button"
              data-testid="operating-retry"
              disabled={busy}
              onClick={() => {
                if (!snapshot) setRevision((old) => old + 1);
                else {
                  setBusy(true);
                  void refresh()
                    .then(
                      () => setFailure(""),
                      (error) => setFailure(message(error)),
                    )
                    .finally(() => setBusy(false));
                }
              }}
            >
              {refreshPending ? "재조회" : "재시도"}
            </button>
          )}
        </div>
      )}
      {confirmSave && (
        <Dialog
          title="운영 기준값 저장"
          testId="operating-save-dialog"
          onClose={() => {
            if (!busy) setConfirmSave(false);
          }}
        >
          {failure && <p role="alert">{failure}</p>}
          <div data-testid="operating-save-summary">
            {changed.map((row) => (
              <p
                key={row.standardCode}
                data-testid={`operating-change-${api.operatingTestKey(row.standardCode)}`}
              >
                {row.standardName}: {row.value} → {draft[row.standardCode]}
              </p>
            ))}
          </div>
          {changed.some((row) => !row.immediate) && (
            <label>
              적용 시작월
              <input
                type="month"
                aria-label="적용 시작월"
                data-testid="operating-effective-month"
                value={month}
                disabled={blocked}
                onChange={(event) => {
                  setMonth(event.target.value);
                  setErrors({});
                }}
              />
            </label>
          )}
          {errors.effectiveMonth && (
            <p className="field-error" role="alert">
              {errors.effectiveMonth}
            </p>
          )}
          {Object.keys(errors).length > 0 && (
            <p role="alert">{Object.values(errors).join(" ")}</p>
          )}
          <div className="app-actions">
            <button
              type="button"
              data-testid="operating-save-cancel"
              disabled={busy}
              onClick={() => setConfirmSave(false)}
            >
              취소
            </button>
            <button
              type="button"
              className="primary-button"
              data-testid="operating-save-confirm"
              disabled={
                blocked || (changed.some((row) => !row.immediate) && !month)
              }
              onClick={() => void submit(changesForMonth())}
            >
              저장
            </button>
          </div>
        </Dialog>
      )}
      {historyCode && (
        <Dialog
          title={`${snapshot?.find((row) => row.standardCode === historyCode)?.standardName ?? "운영 기준값"} 이력`}
          testId="operating-history-dialog"
          onClose={() => {
            if (busy) return;
            setHistoryCode(null);
            setHistoryDraft({});
            setErrors({});
          }}
        >
          {failure && <p role="alert">{failure}</p>}
          <table className="operating-history-table">
            <thead>
              <tr>
                <th>적용 시작월</th>
                <th>값</th>
                <th>동작</th>
              </tr>
            </thead>
            <tbody>
              {(snapshot ?? [])
                .filter((row) => row.standardCode === historyCode)
                .sort((a, b) =>
                  b.effectiveMonth!.localeCompare(a.effectiveMonth!),
                )
                .map((row) => {
                  const key = api.operatingKey(row);
                  const testKey = `${api.operatingTestKey(row.standardCode)}-${row.effectiveMonth}`;
                  return (
                    <tr
                      key={key}
                      data-testid={`operating-history-row-${testKey}`}
                    >
                      <td>{row.effectiveMonth}</td>
                      <td>
                        {row.closed ? (
                          row.value
                        ) : (
                          <input
                            className="ls-in ls-in-sm"
                            aria-label={`${row.effectiveMonth} 이력 값`}
                            data-testid={`operating-history-value-${testKey}`}
                            value={historyDraft[key] ?? row.value}
                            disabled={
                              blocked ||
                              (historyDirty && historyDraft[key] === undefined)
                            }
                            aria-invalid={
                              !!(errors[row.standardCode] ?? errors.value)
                            }
                            onChange={(event) => {
                              setHistoryDraft((old) => ({
                                ...old,
                                [key]: event.target.value,
                              }));
                              setErrors({});
                            }}
                          />
                        )}
                      </td>
                      <td>
                        {row.closed ? (
                          "마감 · 읽기 전용 (새 시작월로 추가)"
                        ) : (
                          <>
                            <button
                              type="button"
                              data-testid={`operating-history-save-${testKey}`}
                              disabled={
                                blocked ||
                                (historyDirty &&
                                  historyDraft[key] === undefined)
                              }
                              onClick={() =>
                                void submit([
                                  {
                                    standardCode: row.standardCode,
                                    effectiveMonth: row.effectiveMonth,
                                    value: historyDraft[key] ?? row.value,
                                    action: "update",
                                  },
                                ])
                              }
                            >
                              수정 저장
                            </button>
                            <button
                              type="button"
                              className="app-danger"
                              data-testid={`operating-history-delete-${testKey}`}
                              disabled={blocked}
                              onClick={() => {
                                if (dirty) {
                                  setFailure("먼저 저장 또는 취소해 주세요.");
                                  return;
                                }
                                setDeleteRow(row);
                              }}
                            >
                              삭제
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
          {Object.keys(errors).length > 0 && (
            <p role="alert">{Object.values(errors).join(" ")}</p>
          )}
          <div className="app-actions">
            <button
              type="button"
              data-testid="operating-history-close"
              disabled={busy}
              onClick={() => {
                setHistoryCode(null);
                setHistoryDraft({});
                setErrors({});
              }}
            >
              닫기
            </button>
          </div>
        </Dialog>
      )}
      {deleteRow && (
        <Dialog
          title="운영 기준값 이력 삭제"
          testId="operating-delete-dialog"
          onClose={() => {
            if (!busy) setDeleteRow(null);
          }}
        >
          <p>
            {deleteRow.standardName} · {deleteRow.effectiveMonth} 이력을
            삭제하시겠습니까?
          </p>
          {failure && <p role="alert">{failure}</p>}
          <div className="app-actions">
            <button
              type="button"
              data-testid="operating-delete-cancel"
              disabled={busy}
              onClick={() => setDeleteRow(null)}
            >
              취소
            </button>
            <button
              type="button"
              data-testid="operating-delete-confirm"
              disabled={blocked}
              onClick={() =>
                void submit([
                  {
                    standardCode: deleteRow.standardCode,
                    effectiveMonth: deleteRow.effectiveMonth,
                    action: "delete",
                  },
                ])
              }
            >
              삭제
            </button>
          </div>
        </Dialog>
      )}
      {notice && (
        <Toast
          message={notice}
          testId="operating-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

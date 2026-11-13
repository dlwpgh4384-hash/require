import { useEffect, useRef, useState } from "react";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { koreanThisMonth } from "../../shared/time";
import { Banner } from "../../shared/list";
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
  const [closedMonth, setClosedMonth] = useState<string | null>(null);
  const [confirmSave, setConfirmSave] = useState(false);
  const [month, setMonth] = useState(monthNow);
  const [historyCode, setHistoryCode] = useState<string | null>(null);
  const lock = useRef(false);
  const root = useRef<HTMLElement>(null);
  const setDirty = useScreenDirty();
  const current = api.currentOperatingRows(snapshot ?? [], monthNow());
  const changed = current.filter(
    (row) =>
      draft[row.standardCode] !== undefined &&
      draft[row.standardCode] !== row.value,
  );
  const dirty = changed.length > 0;
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
      setClosedMonth(result.closedMonth ?? null);
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
      setClosedMonth(result.closedMonth ?? null);
    setDraft({});
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
      setSnapshot(result.items);
      setClosedMonth(result.closedMonth ?? null);
      setDraft({});
      setConfirmSave(false);
      // 시안: 운영 기준값 n건을 저장했습니다(2026-11부터 적용).
      const start = changes.find((row) => row.effectiveMonth)?.effectiveMonth;
      setNotice(
        changes.some((row) => row.action === "delete")
          ? "운영 기준값 이력을 지웠습니다."
          : `운영 기준값 ${changes.length}건을 저장했습니다${start ? `(${start}부터 적용)` : ""}.`,
      );
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
    setErrors({});
    setFailure("");
  };
  const blocked = busy || refreshPending;
  const historyDef = api.operatingRows.find((def) => def.key === historyCode);
  // 이력 창: 같은 적용 시작월의 칸들을 한 줄로 묶는다(최신 달 먼저, 칸 순서는 시안 순서).
  const historyMonths = Object.values(
    (snapshot ?? [])
      .filter((row) => historyDef?.codes.includes(row.standardCode))
      .reduce<Record<string, { month: string; rows: api.OperatingStandard[] }>>(
        (groups, row) => {
          const key = row.effectiveMonth ?? "immediate";
          (groups[key] ??= { month: key, rows: [] }).rows.push(row);
          return groups;
        },
        {},
      ),
  )
    .map((group) => ({
      ...group,
      rows: group.rows.sort(
        (a, b) =>
          historyDef!.codes.indexOf(a.standardCode) -
          historyDef!.codes.indexOf(b.standardCode),
      ),
    }))
    .sort((a, b) => b.month.localeCompare(a.month));
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
      {snapshot && !snapshot.length && <p>등록된 운영 기준값이 없습니다.</p>}
      {snapshot && snapshot.length > 0 && (
        <>
          <Banner kind="info">
            <svg
              className="ls-banner-icon"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
            </svg>
            계산에 쓰는 기준값을 한곳에서 관리합니다. 값을 바꾸면 적용
            시작월부터 쓰고, 그 앞 달은 예전 값 그대로 둡니다. 칸이 여러 개인
            줄은 칸마다 따로 저장됩니다. 자금수지 시작 월·기초 잔액은 처음 한 번
            넣는 값이라 적용 시작월 없이 바로 바뀌고, 시작 월을 마감하면 고칠 수
            없습니다. 신호등 기준값(일정 5·10%p, 원가 5·15%p, 업데이트 없음
            14일)은 바꾸지 않는 고정값이라 여기 없습니다.
          </Banner>
          {dirty && (
            <Banner kind="warn">
              저장하지 않은 변경 {changed.length}건이 있습니다.
            </Banner>
          )}
          <div className="operating-standards-table-wrap">
            <table
              className="operating-standards-table"
              aria-label="운영 기준값 표"
            >
              <thead>
                <tr>
                  <th scope="col">기준값</th>
                  <th scope="col">값</th>
                  <th scope="col">단위</th>
                  <th scope="col">적용 시작월</th>
                  <th scope="col">쓰는 곳</th>
                  <th scope="col">
                    <span className="visually-hidden">이력</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {api.operatingRows.map((def) => {
                  const rows = def.codes.flatMap((code) =>
                    current.filter((row) => row.standardCode === code),
                  );
                  if (!rows.length) return null;
                  const first = rows[0];
                  const multi = !!def.parts;
                  // 시안: 칸이 여러 개인 줄도 「바꾼 시점(적용 시작월)」 수로 센다.
                  const historyCount = new Set(
                    (snapshot ?? [])
                      .filter((row) => def.codes.includes(row.standardCode))
                      .map((row) => row.effectiveMonth ?? "immediate"),
                  ).size;
                  const rowError = rows
                    .map(
                      (row) =>
                        errors[row.standardCode] ??
                        (draft[row.standardCode] !== undefined
                          ? errors.value
                          : undefined),
                    )
                    .find(Boolean);
                  const input = (row: api.OperatingStandard, label?: string) => {
                    const key = api.operatingTestKey(row.standardCode);
                    const error =
                      errors[row.standardCode] ??
                      (draft[row.standardCode] !== undefined
                        ? errors.value
                        : undefined);
                    return (
                      <input
                        id={`operating-value-${key}`}
                        className="ls-in ls-in-sm"
                        data-testid={`operating-value-${key}`}
                        aria-label={row.standardName}
                        title={label ? row.standardCode : undefined}
                        type={
                          row.standardCode === "CASH_BASE_YM" ? "month" : "text"
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
                    );
                  };
                  return (
                    <tr
                      key={def.key}
                      data-testid={`operating-row-${api.operatingTestKey(def.key)}`}
                      aria-label={
                        def.parts
                          ? def.key === "AI_SCORE"
                            ? "AI 배점"
                            : def.label
                          : undefined
                      }
                    >
                      <th scope="row">
                        {def.label}{" "}
                        <span className="operating-code">{def.key}</span>
                        {first.immediate && first.closed && (
                          <span
                            className="operating-lock-note"
                            title="시작 월을 마감해 수정할 수 없습니다"
                          >
                            {" "}
                            ⓘ
                          </span>
                        )}
                      </th>
                      <td className="operating-value-cell">
                        {multi ? (
                          <div className="operating-parts">
                            {rows.map((row, index) => (
                              <label key={row.standardCode}>
                                <span className="operating-part-label">
                                  {def.parts![index]}
                                </span>
                                {input(row, def.parts![index])}
                                <span className="operating-part-label">
                                  {row.unit}
                                </span>
                              </label>
                            ))}
                          </div>
                        ) : (
                          input(first)
                        )}
                        {rowError && (
                          <p className="field-error" role="alert">
                            {rowError}
                          </p>
                        )}
                      </td>
                      <td>{multi ? "" : first.unit}</td>
                      <td>
                        {first.immediate ? (
                          <span className="operating-sub">–</span>
                        ) : (
                          first.effectiveMonth
                        )}
                      </td>
                      <td className="operating-sub">{def.uses}</td>
                      <td>
                        <button
                          type="button"
                          className="operating-history-link"
                          data-testid={`operating-history-${api.operatingTestKey(first.standardCode)}`}
                          disabled={blocked}
                          onClick={() => setHistoryCode(def.key)}
                        >
                          이력 {historyCount}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="app-actions operating-standards-footer">
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
          closable
          onClose={() => {
            if (!busy) setConfirmSave(false);
          }}
        >
          {failure && <p role="alert">{failure}</p>}
          <div data-testid="operating-save-summary">
            <p>{changed.length}건을 바꿉니다.</p>
            <ul className="operating-change-list">
              {changed.map((row) => (
                <li
                  key={row.standardCode}
                  data-testid={`operating-change-${api.operatingTestKey(row.standardCode)}`}
                >
                  {row.standardName}: {row.value} →{" "}
                  <strong>{draft[row.standardCode]}</strong> {row.unit}
                </li>
              ))}
            </ul>
          </div>
          {changed.some((row) => !row.immediate) && (
            <label className="operating-month-field">
              <span className="operating-field-label">적용 시작월 *</span>
              <input
                type="month"
                className="ls-in"
                aria-label="적용 시작월"
                data-testid="operating-effective-month"
                value={month}
                disabled={blocked}
                onChange={(event) => {
                  setMonth(event.target.value);
                  setErrors({});
                }}
              />
              <span className="operating-help">
                마감한 달{closedMonth ? `(${closedMonth})` : ""}까지는 바꿀 수
                없습니다. 그 앞 달은 예전 값 그대로 계산합니다.
              </span>
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
          title={`${historyDef?.label ?? "운영 기준값"} · 이력`}
          testId="operating-history-dialog"
          closable
          onClose={() => {
            if (busy) return;
            setHistoryCode(null);
            setErrors({});
          }}
        >
          {failure && <p role="alert">{failure}</p>}
          {/* 시안: 보기 전용. 한 달에 한 줄이고 여러 칸 값은 「25 / 20 / … 점」으로 한 칸에 보인다. 값은 본 표에서 바꾼다. */}
          <table className="operating-history-table">
            <thead>
              <tr>
                <th>적용 시작월</th>
                <th>값</th>
                <th>처리자</th>
              </tr>
            </thead>
            <tbody>
              {historyMonths.map(({ month: rowMonth, rows }) => {
                const first = rows[0];
                return (
                  <tr
                    key={rowMonth}
                    data-testid={`operating-history-row-${api.operatingTestKey(first.standardCode)}-${rowMonth}`}
                  >
                    <td>{first.effectiveMonth ?? "–"}</td>
                    <td>
                      {rows.map((row) => row.value).join(" / ")} {first.unit}
                    </td>
                    <td>{first.changedBy ?? "–"}</td>
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
                setErrors({});
              }}
            >
              닫기
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

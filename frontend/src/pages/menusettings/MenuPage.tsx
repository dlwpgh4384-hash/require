import { useEffect, useRef, useState } from "react";
import { requestMenuRefresh } from "../../app/menuRefresh";
import { Toast } from "../../shared/feedback/Dialog";
import { Banner } from "../../shared/list";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { MenuRegistration } from "../commonops/MenuRegistration";
import { MenuRegistrationRequestError } from "../commonops/menuRegistration";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import * as api from "./menuSettings";
import "../../shared/list/list.css";
import "./menuSettings.css";

const message = (error: unknown) =>
  error instanceof Error ? error.message : "메뉴를 처리하지 못했습니다.";

/** 시스템 › 메뉴 UI-25 / SCR-UI-25. 휴대폰 제한은 기존 NavigationRoot가 소유한다. */
export function MenuPage() {
  const [snapshot, setSnapshot] = useState<api.MenuSettingItem[] | null>(null);
  const [draft, setDraft] = useState<api.MenuSettingItem[]>([]);
  const [moved, setMoved] = useState<string[]>([]);
  const [failure, setFailure] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [dialogDirty, setDialogDirty] = useState(false);
  const [refreshPending, setRefreshPending] = useState(false);
  const root = useRef<HTMLElement>(null);
  const lock = useRef(false);
  const dragged = useRef<string | null>(null);
  const setDirty = useScreenDirty();
  const changed = snapshot
    ? draft.filter((row) => {
        const old = snapshot.find((item) => item.menuId === row.menuId);
        return (
          !old ||
          JSON.stringify(api.updateItem(old)) !==
            JSON.stringify(api.updateItem(row))
        );
      })
    : [];
  const dirty = changed.length > 0;
  useEffect(() => {
    setDirty("UI-25", dirty || dialogDirty);
    return () => setDirty("UI-25", false);
  }, [dirty, dialogDirty, setDirty]);
  useEffect(() => {
    const controller = new AbortController();
    setFailure("");
    void api.getCommonMenuSettings(controller.signal).then(
      (result) => {
        if (controller.signal.aborted) return;
        setSnapshot(result.items);
        setDraft(result.items);
        setMoved([]);
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
  const refresh = async () => {
    const result = await api.getCommonMenuSettings();
    setSnapshot(result.items);
    setDraft(result.items);
    setMoved([]);
    setRefreshPending(false);
  };
  const discard = () => {
    if (snapshot) setDraft(snapshot);
    setMoved([]);
    setErrors({});
    setFailure("");
  };
  const save = async () => {
    if (lock.current || !snapshot || refreshPending) return;
    if (!dirty) {
      setNotice("바꾼 내용이 없습니다.");
      return;
    }
    const invalid: Record<string, string> = {};
    changed.forEach((row) => {
      if (!row.menuNm.trim() || [...row.menuNm].length > 50)
        invalid[row.menuId] = "메뉴 이름을 1~50자로 입력해 주세요.";
    });
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    let saved = false;
    try {
      const input = {
        items: changed.map(api.updateItem),
        movedMenuIds: moved.filter((id) =>
          changed.some((row) => row.menuId === id),
        ),
      };
      const result = await api.putCommonMenuSettings(input);
      saved = true;
      setSnapshot(result.items);
      setDraft(result.items);
      setMoved([]);
      requestMenuRefresh();
      setNotice("메뉴를 저장했습니다.");
      await refresh();
    } catch (error) {
      if (saved) {
        setRefreshPending(true);
        setFailure(
          "저장은 완료되었으나 표를 다시 읽지 못했습니다. 재조회해 주세요.",
        );
      } else {
        setFailure(message(error));
        if (error instanceof MenuRegistrationRequestError)
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
  usePreferenceBusinessShortcuts({
    onSave: () => {
      if (!root.current?.closest("[hidden]")) void save();
    },
  });
  const change = (id: string, value: Partial<api.MenuSettingItem>) => {
    setDraft((rows) =>
      rows.map((row) => (row.menuId === id ? { ...row, ...value } : row)),
    );
    setErrors({});
  };
  const move = (source: string, target: string) => {
    if (busy || refreshPending) return;
    try {
      const next = api.moveMenu(draft, source, target);
      if (JSON.stringify(next) === JSON.stringify(draft)) return;
      setDraft(next);
      setMoved((ids) => (ids.includes(source) ? ids : [...ids, source]));
      setFailure("");
    } catch (error) {
      setFailure(message(error));
    }
  };
  return (
    <section
      ref={root}
      className="menu-settings-page screen-body"
      aria-label="메뉴 관리"
      data-testid="menu-settings-page"
      data-screen-id="UI-25"
    >
      <div className="menu-settings-toolbar">
        <MenuRegistration
          dirty={dirty}
          disabled={!snapshot || busy || refreshPending}
          onDiscard={discard}
          onRegistered={refresh}
          onDialogDirty={setDialogDirty}
          registeredScreenIds={(snapshot ?? []).flatMap((row) =>
            row.scrnId ? [row.scrnId] : [],
          )}
        />
      </div>
      {dirty && (
        <Banner kind="warn">
          저장하지 않은 변경이 있습니다. 아래 [저장]을 눌러야 모든 사용자 메뉴에
          반영됩니다.
        </Banner>
      )}
      {!snapshot && !failure && <p role="status">메뉴를 불러오는 중입니다.</p>}
      {snapshot && (
        <form
          data-testid="menu-settings-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <fieldset
            disabled={busy || refreshPending}
            className="menu-settings-fields"
          >
            <div className="menu-settings-table-wrap">
              <table aria-label="메뉴 트리 표" className="menu-settings-table">
                <thead>
                  <tr>
                    <th scope="col">이동</th>
                    <th scope="col">메뉴 이름</th>
                    <th scope="col">화면 번호</th>
                    <th scope="col">순서</th>
                    <th scope="col">사용</th>
                    <th scope="col">PC 전용</th>
                  </tr>
                </thead>
                <tbody>
                  {api.menuTree(draft).map((row) => {
                    const key = row.menuId.toLowerCase();
                    const error = errors[row.menuId] ?? errors.menuNm;
                    return (
                      <tr
                        key={row.menuId}
                        data-testid={`menu-settings-row-${key}`}
                        className={
                          row.menuLvl === 1
                            ? "menu-settings-group"
                            : "menu-settings-child"
                        }
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={(event) => {
                          event.preventDefault();
                          if (dragged.current)
                            move(dragged.current, row.menuId);
                          dragged.current = null;
                        }}
                      >
                        <td>
                          <button
                            type="button"
                            draggable={!busy && !refreshPending}
                            className="menu-settings-handle"
                            data-testid={`menu-settings-handle-${key}`}
                            aria-label={`${row.menuNm} 이동`}
                            onDragStart={(event) => {
                              dragged.current = row.menuId;
                              event.dataTransfer.setData(
                                "text/plain",
                                row.menuId,
                              );
                            }}
                            onDragEnd={() => {
                              dragged.current = null;
                            }}
                            onKeyDown={(event) => {
                              if (
                                !event.altKey ||
                                !["ArrowUp", "ArrowDown"].includes(event.key)
                              )
                                return;
                              event.preventDefault();
                              const siblings = draft
                                .filter(
                                  (item) =>
                                    item.upperMenuId === row.upperMenuId,
                                )
                                .sort(api.menuOrder);
                              const index = siblings.findIndex(
                                (item) => item.menuId === row.menuId,
                              );
                              if (event.key === "ArrowUp" && index > 0)
                                move(row.menuId, siblings[index - 1].menuId);
                              if (
                                event.key === "ArrowDown" &&
                                index + 1 < siblings.length
                              ) {
                                // 같은 이동을 역방향 대상 바로 앞 삽입으로 표현하되 직접 대상만 추적한다.
                                const next = [...siblings];
                                const [item] = next.splice(index, 1);
                                next.splice(index + 1, 0, item);
                                const orders = new Map(
                                  next.map((item, i) => [item.menuId, i + 1]),
                                );
                                setDraft(
                                  draft.map((item) =>
                                    orders.has(item.menuId)
                                      ? {
                                          ...item,
                                          sortOrd: orders.get(item.menuId)!,
                                        }
                                      : item,
                                  ),
                                );
                                setMoved((ids) =>
                                  ids.includes(row.menuId)
                                    ? ids
                                    : [...ids, row.menuId],
                                );
                              }
                            }}
                          >
                            ⋮⋮
                          </button>
                        </td>
                        <td>
                          <input
                            className="ls-in"
                            aria-label={`${row.menuId} 메뉴 이름`}
                            aria-invalid={!!error}
                            data-testid={`menu-settings-name-${key}`}
                            value={row.menuNm}
                            onChange={(event) =>
                              change(row.menuId, { menuNm: event.target.value })
                            }
                          />
                          {error && (
                            <p className="field-error" role="alert">
                              {error}
                            </p>
                          )}
                        </td>
                        <td>{row.scrnId}</td>
                        <td>{row.sortOrd}</td>
                        <td>
                          {row.menuLvl === 2 && (
                            <input
                              type="checkbox"
                              aria-label={`${row.menuId} 사용`}
                              data-testid={`menu-settings-use-${key}`}
                              checked={row.useYn === "Y"}
                              onChange={(event) =>
                                change(row.menuId, {
                                  useYn: event.target.checked ? "Y" : "N",
                                })
                              }
                            />
                          )}
                        </td>
                        <td>
                          {row.menuLvl === 2 && (
                            <input
                              type="checkbox"
                              aria-label={`${row.menuId} PC 전용`}
                              data-testid={`menu-settings-pc-${key}`}
                              checked={row.pcOnlyYn === "Y"}
                              onChange={(event) =>
                                change(row.menuId, {
                                  pcOnlyYn: event.target.checked ? "Y" : "N",
                                })
                              }
                            />
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {snapshot.length === 0 && <p>등록된 메뉴가 없습니다.</p>}
            <p className="menu-settings-help">
              일반 메뉴는 다른 묶음으로 이동할 수 있습니다. 시스템 메뉴는 묶음
              밖 이동·끄기가 불가하며 이름 수정은 가능합니다.
            </p>
            <div className="app-actions">
              <button
                type="button"
                data-testid="menu-settings-cancel"
                onClick={discard}
              >
                취소
              </button>
              <button
                type="submit"
                className="primary-button"
                data-testid="menu-settings-save"
              >
                저장
              </button>
            </div>
          </fieldset>
        </form>
      )}
      {failure && (
        <div role="alert" data-testid="menu-settings-failure">
          {failure}
          {(!snapshot || refreshPending) && (
            <button
              type="button"
              data-testid="menu-settings-retry"
              disabled={busy}
              onClick={() => {
                if (!snapshot) setRevision((value) => value + 1);
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
      {notice && (
        <Toast
          message={notice}
          testId="menu-settings-toast"
          onDismiss={() => setNotice("")}
        />
      )}
    </section>
  );
}

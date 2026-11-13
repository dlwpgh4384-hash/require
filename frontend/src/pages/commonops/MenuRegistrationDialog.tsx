import { useEffect, useRef, useState } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import * as api from "./menuRegistration";
import "../../shared/list/list.css";
import "./menuRegistration.css";

/** UI-25-M1 / SCR-UI-25-M1: ID와 아이콘은 서버가 정한다. */
export function MenuRegistrationDialog({
  registeredScreenIds,
  onClose,
  onSaved,
  onDirty,
}: {
  registeredScreenIds: readonly string[];
  onClose: () => void;
  onSaved: (item: api.MenuRegistrationItem) => void;
  onDirty?: (dirty: boolean) => void;
}) {
  const [level, setLevel] = useState<1 | 2>(2);
  const [name, setName] = useState("");
  const [parent, setParent] = useState("");
  const [screenId, setScreenId] = useState("");
  const [pcOnly, setPcOnly] = useState(false);
  const [options, setOptions] = useState<api.MenuRegistrationOptions | null>(
    null,
  );
  const [optionFailure, setOptionFailure] = useState("");
  const [revision, setRevision] = useState(0);
  const [failure, setFailure] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [closing, setClosing] = useState(false);
  const lock = useRef(false);
  const form = useRef<HTMLFormElement>(null);
  const dirty =
    name !== "" || parent !== "" || screenId !== "" || pcOnly || level !== 2;
  useEffect(() => {
    onDirty?.(dirty);
    return () => onDirty?.(false);
  }, [dirty, onDirty]);
  useEffect(() => {
    const controller = new AbortController();
    setOptions(null);
    setOptionFailure("");
    void api.getCommonMenuOptions(level, controller.signal).then(
      (value) => {
        if (!controller.signal.aborted) setOptions(value);
      },
      () => {
        if (!controller.signal.aborted)
          setOptionFailure("선택 목록을 불러오지 못했습니다.");
      },
    );
    return () => controller.abort();
  }, [level, revision]);
  useEffect(() => {
    if (Object.keys(errors).length)
      form.current
        ?.querySelector<HTMLElement>("[aria-invalid='true']")
        ?.focus();
  }, [errors]);
  const screens =
    options?.screens.filter(
      (screen) => !registeredScreenIds.includes(screen.scrnId),
    ) ?? [];
  const close = () => {
    if (lock.current) return;
    if (dirty) setClosing(true);
    else onClose();
  };
  const submit = async () => {
    if (lock.current) return;
    const invalid: Record<string, string> = {};
    if (!name.trim() || [...name].length > 50)
      invalid.menuNm = "메뉴 이름을 1~50자로 입력해 주세요.";
    if (level === 2) {
      if (!options?.upperMenus.some((row) => row.menuId === parent))
        invalid.upperMenuId = "상위 대메뉴를 선택해 주세요.";
      if (!screens.some((row) => row.scrnId === screenId))
        invalid.scrnId = "개발된 미등록 화면을 선택해 주세요.";
    }
    setErrors(invalid);
    if (Object.keys(invalid).length || !options) return;
    lock.current = true;
    setBusy(true);
    setFailure("");
    try {
      const input: api.MenuCreateInput =
        level === 1
          ? { menuLvl: 1, menuNm: name }
          : {
              menuLvl: 2,
              menuNm: name,
              upperMenuId: parent,
              scrnId: screenId,
              pcOnlyYn: pcOnly ? "Y" : "N",
            };
      onSaved(await api.postCommonMenu(input));
    } catch (error) {
      setFailure(
        error instanceof Error ? error.message : "메뉴를 등록하지 못했습니다.",
      );
      setErrors(
        error instanceof api.MenuRegistrationRequestError
          ? Object.fromEntries(
              error.errors.map((item) => [item.field, item.message]),
            )
          : {},
      );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  };
  const fieldError = (key: string) =>
    errors[key] ? (
      <p
        className="field-error"
        role="alert"
        data-testid={`menu-registration-error-${key.toLowerCase()}`}
      >
        {errors[key]}
      </p>
    ) : null;
  return (
    <Dialog title="메뉴 등록" testId="menu-registration-dialog" onClose={close}>
      {closing ? (
        <InlineConfirmation
          title="나가시겠습니까?"
          description={feedbackMessages.unsaved}
          onCancel={() => setClosing(false)}
          onProceed={onClose}
        />
      ) : (
        <form
          ref={form}
          className="menu-registration-form"
          data-testid="menu-registration-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <fieldset
            className="menu-registration-level"
            data-testid="menu-registration-level"
            disabled={busy}
          >
            <legend>레벨 *</legend>
            {(
              [
                [1, "대메뉴(묶음)"],
                [2, "서브메뉴"],
              ] as const
            ).map(([value, label]) => (
              <label key={value}>
                <input
                  type="radio"
                  name="menu-registration-level"
                  data-testid={`menu-registration-level-${value}`}
                  checked={level === value}
                  onChange={() => {
                    setLevel(value);
                    setErrors({});
                    setFailure("");
                  }}
                />{" "}
                {label}
              </label>
            ))}
          </fieldset>
          <label htmlFor="menu-registration-name">메뉴 이름 *</label>
          <input
            id="menu-registration-name"
            data-testid="menu-registration-name"
            className="ls-in"
            autoComplete="off"
            maxLength={50}
            value={name}
            disabled={busy}
            aria-invalid={!!errors.menuNm}
            aria-describedby="menu-registration-name-help"
            onChange={(event) => setName(event.target.value)}
          />
          <span id="menu-registration-name-help" className="menu-registration-help">
            50자 이내
          </span>
          {fieldError("menuNm")}
          {level === 2 ? (
            <>
              <label htmlFor="menu-registration-parent">상위 대메뉴 *</label>
              <select
                id="menu-registration-parent"
                data-testid="menu-registration-parent"
                className="ls-in"
                value={parent}
                disabled={busy || !options}
                aria-invalid={!!errors.upperMenuId}
                onChange={(event) => setParent(event.target.value)}
              >
                <option value="">선택해 주세요</option>
                {options?.upperMenus.map((row) => (
                  <option key={row.menuId} value={row.menuId}>
                    {row.menuNm}
                  </option>
                ))}
              </select>
              {fieldError("upperMenuId")}
              <label htmlFor="menu-registration-screen">화면 번호 *</label>
              <select
                id="menu-registration-screen"
                data-testid="menu-registration-screen"
                className="ls-in"
                value={screenId}
                disabled={busy || !options || !screens.length}
                aria-invalid={!!errors.scrnId}
                aria-describedby="menu-registration-screen-help"
                onChange={(event) => setScreenId(event.target.value)}
              >
                <option value="">
                  {options && !screens.length
                    ? "메뉴에 없는 개발된 화면이 없습니다"
                    : "선택해 주세요"}
                </option>
                {screens.map((row) => (
                  <option key={row.scrnId} value={row.scrnId}>
                    {row.scrnId} · {row.menuNm}
                  </option>
                ))}
              </select>
              <span
                id="menu-registration-screen-help"
                className="menu-registration-help"
              >
                개발이 끝난 화면만 고를 수 있습니다. 새 화면이 개발되면 여기에
                나옵니다.
              </span>
              {fieldError("scrnId")}
              <label className="menu-registration-check">
                <input
                  type="checkbox"
                  data-testid="menu-registration-pc"
                  checked={pcOnly}
                  disabled={busy}
                  aria-invalid={!!errors.pcOnlyYn}
                  onChange={(event) => setPcOnly(event.target.checked)}
                />
                PC 전용
              </label>
              {fieldError("pcOnlyYn")}
              {options && !screens.length && (
                <p
                  data-testid="menu-registration-no-screens"
                  className="visually-hidden"
                >
                  메뉴에 없는 개발된 화면이 없습니다. 대메뉴는 등록할 수
                  있습니다.
                </p>
              )}
            </>
          ) : (
            <p>대메뉴는 화면 번호가 없습니다.</p>
          )}
          {!options && !optionFailure && (
            <p role="status">선택 목록을 불러오는 중입니다.</p>
          )}
          {optionFailure && (
            <div role="alert">
              {optionFailure}{" "}
              <button
                type="button"
                data-testid="menu-registration-options-retry"
                onClick={() => setRevision((value) => value + 1)}
              >
                재시도
              </button>
            </div>
          )}
          <p className="menu-registration-hint">
            순서는 그 묶음의 맨 끝이고, 저장 즉시 적용됩니다. 새 메뉴는 모든
            역할에 「권한 없음」으로 추가됩니다.
          </p>
          {failure && (
            <p role="alert" data-testid="menu-registration-failure">
              {failure}
            </p>
          )}
          <div className="app-actions">
            <button
              type="button"
              data-testid="menu-registration-cancel"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              type="submit"
              className="primary-button"
              data-testid="menu-registration-submit"
              disabled={
                busy ||
                !options ||
                (level === 2 && (!screens.length || !options.upperMenus.length))
              }
            >
              등록
            </button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

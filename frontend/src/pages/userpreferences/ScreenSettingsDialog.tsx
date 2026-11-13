import { useEffect, useRef, useState } from "react";
import { useUserPreferences } from "./UserPreferencesProvider";
import {
  UserPreferenceConfirmation,
  UserPreferenceDialog,
  UserPreferenceToast,
} from "./UserPreferenceDialog";
import {
  digitsOnly,
  unsavedPreferenceMessage,
  validateScreenSettings,
} from "./screenSettingsValidation";
import {
  preferenceConflict,
  preferenceSaveError,
  UserPreferenceRequestError,
  type UserPreferenceResult,
} from "./userPreferences";

type Props = { onClose: () => void; onDirtyChange?: (dirty: boolean) => void };

// SCR-UI-35 / UI-35: route 없는 화면 설정 창.
export function ScreenSettingsDialog(props: Props) {
  const { states, reload } = useUserPreferences();
  const page = states.PAGE_SIZE;
  const dense = states.DENSE;
  if (page.status === "loading" || dense.status === "loading")
    return (
      <UserPreferenceDialog
        title="화면 설정"
        onClose={props.onClose}
        testId="screen-settings-dialog"
      >
        <p role="status">설정을 불러오는 중입니다.</p>
        <button
          type="button"
          data-testid="settings-loading-close"
          onClick={props.onClose}
        >
          닫기
        </button>
      </UserPreferenceDialog>
    );
  if (
    page.status === "error" ||
    dense.status === "error" ||
    !page.data ||
    !dense.data
  )
    return (
      <UserPreferenceDialog
        title="화면 설정"
        onClose={props.onClose}
        testId="screen-settings-dialog"
      >
        <p role="alert">설정을 불러오지 못했습니다. 다시 시도해 주세요.</p>
        <button
          type="button"
          data-testid="settings-retry"
          onClick={() => {
            void reload("PAGE_SIZE");
            void reload("DENSE");
          }}
        >
          다시 불러오기
        </button>
        <button
          type="button"
          data-testid="settings-error-close"
          onClick={props.onClose}
        >
          닫기
        </button>
      </UserPreferenceDialog>
    );
  return <SettingsForm {...props} page={page.data} dense={dense.data} />;
}

function SettingsForm({
  onClose,
  onDirtyChange,
  page,
  dense,
}: Props & { page: UserPreferenceResult; dense: UserPreferenceResult }) {
  const { save, reload } = useUserPreferences();
  const [baseline, setBaseline] = useState({ page, dense });
  const [pageSize, setPageSize] = useState(page.prefVal ?? "");
  const [density, setDensity] = useState(dense.prefVal ?? "");
  const [errors, setErrors] = useState<{ pageSize?: string; dense?: string }>(
    {},
  );
  const [failure, setFailure] = useState("");
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [toast, setToast] = useState(false);
  const pageInput = useRef<HTMLInputElement>(null);
  const denseInput = useRef<HTMLSelectElement>(null);
  const busy = useRef(false);
  const dirty =
    pageSize !== (baseline.page.prefVal ?? "") ||
    density !== (baseline.dense.prefVal ?? "");
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  const requestClose = () => {
    if (saving) return;
    if (confirming) {
      setConfirming(false);
      setReloading(false);
    } else if (dirty) {
      setReloading(false);
      setConfirming(true);
    } else onClose();
  };
  const focusError = (invalidPage: boolean) => {
    const target = invalidPage ? pageInput.current : denseInput.current;
    target?.focus();
    target?.scrollIntoView?.({ block: "nearest" });
  };
  const submit = async () => {
    if (busy.current) return;
    const validation = validateScreenSettings(pageSize, density);
    setErrors(validation);
    setFailure("");
    setToast(false);
    if (Object.keys(validation).length) {
      focusError(!!validation.pageSize);
      return;
    }
    busy.current = true;
    setSaving(true);
    const results = await Promise.allSettled([
      save("PAGE_SIZE", pageSize, baseline.page.modDttm),
      save("DENSE", density, baseline.dense.modDttm),
    ]);
    const next = { ...baseline };
    const nextErrors: { pageSize?: string; dense?: string } = {};
    results.forEach((result, index) => {
      if (result.status === "fulfilled") {
        if (index === 0) {
          next.page = result.value;
          setPageSize(result.value.prefVal ?? "");
        } else {
          next.dense = result.value;
          setDensity(result.value.prefVal ?? "");
        }
      } else {
        const reason = result.reason;
        const message =
          reason instanceof UserPreferenceRequestError && reason.status === 409
            ? preferenceConflict
            : preferenceSaveError;
        setFailure(message);
        const field =
          reason instanceof UserPreferenceRequestError
            ? reason.errors.find((error) => error.field === "prefVal")
            : undefined;
        if (index === 0) nextErrors.pageSize = field?.message ?? message;
        else nextErrors.dense = field?.message ?? message;
      }
    });
    setBaseline(next);
    setErrors(nextErrors);
    setToast(results.every((result) => result.status === "fulfilled"));
    busy.current = false;
    setSaving(false);
    if (Object.keys(nextErrors).length) focusError(!!nextErrors.pageSize);
  };
  return (
    <UserPreferenceDialog
      title="화면 설정"
      onClose={requestClose}
      testId="screen-settings-dialog"
    >
      {confirming ? (
        <UserPreferenceConfirmation
          title="화면 설정을 닫을까요?"
          description={unsavedPreferenceMessage}
          onCancel={() => {
            setConfirming(false);
            setReloading(false);
          }}
          onProceed={() => {
            if (reloading) {
              void reload("PAGE_SIZE");
              void reload("DENSE");
            } else onClose();
          }}
        />
      ) : (
        <form
          data-testid="screen-settings-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          {(!baseline.page.found || !baseline.dense.found) && (
            <p>
              아직 저장하지 않은 설정입니다. 목록 건수와 보기 밀도를 선택해
              주세요.
            </p>
          )}
          <label htmlFor="preference-page-size">
            기본 목록 건수 <span aria-label="필수">*</span>
          </label>
          <input
            ref={pageInput}
            id="preference-page-size"
            data-testid="settings-page-size"
            inputMode="numeric"
            required
            list="preference-page-sizes"
            value={pageSize}
            disabled={saving}
            aria-invalid={!!errors.pageSize}
            aria-describedby={
              errors.pageSize ? "settings-page-error" : "settings-page-hint"
            }
            onChange={(event) => setPageSize(digitsOnly(event.target.value))}
          />
          <datalist id="preference-page-sizes">
            <option value="20" />
            <option value="50" />
            <option value="100" />
          </datalist>
          <small id="settings-page-hint">
            20 / 50 / 100 중에서 입력해 주세요.
          </small>
          {errors.pageSize && (
            <p id="settings-page-error" className="field-error">
              {errors.pageSize}
            </p>
          )}
          <label htmlFor="preference-dense">
            보기 밀도 <span aria-label="필수">*</span>
          </label>
          <select
            ref={denseInput}
            id="preference-dense"
            data-testid="settings-density"
            required
            value={density}
            disabled={saving}
            aria-invalid={!!errors.dense}
            aria-describedby={errors.dense ? "settings-dense-error" : undefined}
            onChange={(event) => setDensity(event.target.value)}
          >
            <option value="" disabled>
              선택해 주세요
            </option>
            <option value="넓게">넓게</option>
            <option value="좁게">좁게</option>
          </select>
          {errors.dense && (
            <p id="settings-dense-error" className="field-error">
              {errors.dense}
            </p>
          )}
          {failure && (
            <div role="alert">
              <p>{failure}</p>
              <button
                type="button"
                data-testid="settings-reload"
                onClick={() => {
                  if (dirty) {
                    setReloading(true);
                    setConfirming(true);
                    return;
                  }
                  void reload("PAGE_SIZE");
                  void reload("DENSE");
                }}
              >
                다시 불러오기
              </button>
            </div>
          )}
          {toast && (
            <UserPreferenceToast
              message="저장했습니다."
              onDismiss={() => setToast(false)}
            />
          )}
          <div className="app-actions">
            <button
              type="button"
              data-testid="settings-defaults"
              disabled
              aria-describedby="settings-defaults-hint"
            >
              기본값으로
            </button>
            <button
              type="button"
              data-testid="settings-cancel"
              disabled={saving}
              onClick={requestClose}
            >
              취소
            </button>
            <button
              type="submit"
              data-testid="settings-save"
              className="primary-button"
              disabled={saving}
            >
              {saving ? "저장 중" : "저장"}
            </button>
          </div>
          <small id="settings-defaults-hint">
            기본값은 아직 확인되지 않았습니다. 확인 후 기본값으로 기능을 사용할
            수 있습니다.
          </small>
        </form>
      )}
    </UserPreferenceDialog>
  );
}

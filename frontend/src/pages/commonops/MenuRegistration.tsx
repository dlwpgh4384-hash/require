import { useRef, useState } from "react";
import { ConfirmDialog, Toast } from "../../shared/feedback/Dialog";
import { feedbackMessages } from "../../shared/feedback/messages";
import { requestMenuRefresh } from "../../app/menuRefresh";
import { MenuRegistrationDialog } from "./MenuRegistrationDialog";
import type { MenuRegistrationItem } from "./menuRegistration";

/** UI-25 / SCR-UI-25: 관리 표의 draft는 부모 화면이 소유하며 등록은 이를 저장하지 않는다. */
export function MenuRegistration({
  dirty,
  onDiscard,
  onRegistered,
  registeredScreenIds,
  disabled = false,
  onDialogDirty,
}: {
  dirty: boolean;
  onDiscard: () => void;
  /** 등록 뒤 관리 GET을 다시 읽는다. 현재 draft의 PUT 저장을 호출하지 않는다. */
  onRegistered: (item: MenuRegistrationItem) => void | Promise<void>;
  registeredScreenIds: readonly string[];
  disabled?: boolean;
  onDialogDirty?: (dirty: boolean) => void;
}) {
  const [open, setOpen] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [notice, setNotice] = useState("");
  const [refreshFailure, setRefreshFailure] = useState("");
  const [saved, setSaved] = useState<MenuRegistrationItem | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const refreshingLock = useRef(false);
  const refresh = async (item: MenuRegistrationItem) => {
    if (refreshingLock.current) return;
    refreshingLock.current = true;
    setRefreshing(true);
    setRefreshFailure("");
    requestMenuRefresh();
    try {
      await onRegistered(item);
      setSaved(null);
    } catch {
      setRefreshFailure(
        "등록은 완료되었으나 표를 다시 읽지 못했습니다. 재조회해 주세요.",
      );
    } finally {
      refreshingLock.current = false;
      setRefreshing(false);
    }
  };
  return (
    <div data-testid="menu-registration" data-screen-id="UI-25">
      <button
        type="button"
        data-testid="menu-registration-open"
        disabled={disabled || refreshing || !!saved}
        onClick={() => {
          if (dirty) setConfirm(true);
          else setOpen(true);
        }}
      >
        메뉴 등록
      </button>
      {confirm && (
        <ConfirmDialog
          title="변경 내용을 버릴까요?"
          description={feedbackMessages.unsaved}
          testId="menu-registration-discard"
          proceedLabel="버리기"
          destructive
          onCancel={() => setConfirm(false)}
          onProceed={() => {
            onDiscard();
            setConfirm(false);
            setOpen(true);
          }}
        />
      )}
      {open && (
        <MenuRegistrationDialog
          registeredScreenIds={registeredScreenIds}
          onDirty={onDialogDirty}
          onClose={() => setOpen(false)}
          onSaved={(item) => {
            setOpen(false);
            setSaved(item);
            setNotice("메뉴를 등록했습니다.");
            void refresh(item);
          }}
        />
      )}
      {refreshFailure && (
        <div role="alert" data-testid="menu-registration-refresh-failure">
          {refreshFailure}
          <button
            type="button"
            data-testid="menu-registration-refresh-retry"
            disabled={refreshing}
            onClick={() => {
              if (saved) void refresh(saved);
            }}
          >
            재조회
          </button>
        </div>
      )}
      {notice && (
        <Toast
          testId="menu-registration-toast"
          message={notice}
          onDismiss={() => setNotice("")}
        />
      )}
    </div>
  );
}

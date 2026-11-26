import { useEffect, useState } from "react";
import { NotificationPopup, notificationCount } from "./NotificationsPage";
import { notificationApi, type NotificationApi } from "./notificationApi";
import { Toast } from "../../shared/feedback/Dialog";

export function NotificationBell({
  onNavigate,
  api = notificationApi,
}: {
  onNavigate: (path: string) => void;
  api?: NotificationApi;
}) {
  const [count, setCount] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => {
    let active = true;
    api
      .list()
      .then((data) => {
        if (active) setCount(data.unreadCount);
      })
      .catch((failure) => {
        if (active)
          setError(
            failure instanceof Error
              ? failure.message
              : "알림을 불러오지 못했습니다.",
          );
      });
    return () => {
      active = false;
    };
  }, [api]);
  return (
    <div className="notification-bell" data-testid="notification-bell">
      <button
        type="button"
        data-testid="M25-521"
        aria-label="알림"
        aria-expanded={open}
        title={error || "알림"}
        onClick={() => {
          setOpen(true);
          setError("");
        }}
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          aria-hidden="true"
        >
          <path d="M18 8a6 6 0 0 0-12 0v5l-2 3h16l-2-3V8M10 20h4" />
        </svg>
        {count !== null && count > 0 && (
          <span data-testid="notification-badge">
            {notificationCount(count)}
          </span>
        )}
      </button>
      {open && (
        <NotificationPopup
          api={api}
          onClose={() => setOpen(false)}
          onCountChange={setCount}
          onNotice={setNotice}
          onNavigate={(path) => {
            onNavigate(path);
            setOpen(false);
          }}
        />
      )}
      {notice && !open && (
        <Toast
          message={notice}
          kind="info"
          testId="notification-bell-message"
          onDismiss={() => setNotice("")}
        />
      )}
    </div>
  );
}

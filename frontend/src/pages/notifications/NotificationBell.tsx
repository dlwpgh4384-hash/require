import { useEffect, useRef, useState } from "react";
import { NotificationPopup, notificationCount } from "./NotificationsPage";
import { notificationApi, type NotificationApi } from "./notificationApi";
import { Toast } from "../../shared/feedback/Dialog";

/** 시안 renderHeader 종: 안 읽은 수 빨간 점(99+), 누르면 바로 아래 알림 팝업. */
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
  const anchor = useRef<HTMLDivElement>(null);
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
  const unread = count ?? 0;
  return (
    <div
      className="hd-anchor notification-bell"
      data-testid="notification-bell"
      ref={anchor}
    >
      <button
        type="button"
        className="hd-ibtn"
        data-testid="M25-521"
        aria-label={`알림 ${unread}건`}
        aria-expanded={open}
        title={error || undefined}
        onClick={() => {
          setOpen(!open);
          setError("");
        }}
      >
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21h4" />
        </svg>
        {unread > 0 && (
          <span className="hd-dot" data-testid="notification-badge">
            {notificationCount(unread)}
          </span>
        )}
      </button>
      {open && (
        <NotificationPopup
          api={api}
          anchor={anchor}
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

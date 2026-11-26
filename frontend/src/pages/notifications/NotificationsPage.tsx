import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import {
  notificationApi,
  type NotificationApi,
  type NotificationListing,
} from "./notificationApi";
import "./notifications.css";

export function notificationCount(count: number) {
  return count > 99 ? "99+" : String(count);
}

export function notificationTime(value: string, now = new Date()) {
  const parts = (date: Date) =>
    Object.fromEntries(
      new Intl.DateTimeFormat("sv-SE", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      })
        .formatToParts(date)
        .map((part) => [part.type, part.value]),
    );
  const date = parts(new Date(value));
  const today = parts(now);
  const yesterday = parts(new Date(now.getTime() - 86400000));
  const sameDay = (other: Record<string, string>) =>
    date.year === other.year &&
    date.month === other.month &&
    date.day === other.day;
  return `${sameDay(today) ? "오늘" : sameDay(yesterday) ? "어제" : `${date.month}-${date.day}`} ${date.hour}:${date.minute}`;
}

export interface NotificationPopupProps {
  api?: NotificationApi;
  onClose: () => void;
  onNavigate: (path: string) => void;
  onNotice?: (message: string) => void;
  onCountChange?: (count: number) => void;
  isMobile?: boolean;
}

// UI-42: the header popup and the route-backed popup use exactly this component.
export function NotificationPopup({
  api = notificationApi,
  onClose,
  onNavigate,
  onNotice,
  onCountChange,
  isMobile = window.matchMedia?.("(max-width: 767px)").matches ?? false,
}: NotificationPopupProps) {
  const [listing, setListing] = useState<NotificationListing | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const mounted = useRef(false);
  const pending = useRef(false);
  const countChanged = useRef(onCountChange);
  countChanged.current = onCountChange;

  async function load() {
    setLoading(true);
    setError("");
    try {
      const data = await api.list();
      if (mounted.current) {
        setListing(data);
        countChanged.current?.(data.unreadCount);
      }
      return true;
    } catch (failure) {
      if (mounted.current) {
        setListing(null);
        setError(
          failure instanceof Error
            ? failure.message
            : "알림을 불러오지 못했습니다.",
        );
      }
      return false;
    } finally {
      if (mounted.current) setLoading(false);
    }
  }
  useEffect(() => {
    mounted.current = true;
    void load();
    return () => {
      mounted.current = false;
    };
  }, [api]);

  async function readAll() {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.readAll();
      const refreshed = await load();
      if (mounted.current && refreshed) setMessage("모두 읽음 처리했습니다.");
    } catch (failure) {
      if (mounted.current)
        setError(
          failure instanceof Error
            ? failure.message
            : "알림을 처리하지 못했습니다.",
        );
    } finally {
      pending.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  async function select(notificationId: number) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.read(notificationId);
      // Always refresh after the committed read, including a newly revoked permission.
      const refreshed = await load();
      if (!mounted.current || !refreshed) return;
      const navigation = result.meta.navigation;
      if (!navigation || navigation.status === "DENIED") {
        setMessage("이 화면을 볼 권한이 없습니다.");
      } else if (navigation.status === "MISSING") {
        setMessage("이 알림의 대상이 더 이상 없습니다.");
      } else if (isMobile && navigation.pcOnly) {
        setMessage("PC에서 열 수 있는 화면입니다.");
      } else if (
        navigation.screenId &&
        /^UI-[0-9]{2}$/.test(navigation.screenId)
      ) {
        if (navigation.status === "OUT_OF_SCOPE") {
          setMessage("이 건은 지금 볼 수 있는 범위 밖입니다.");
          onNotice?.("이 건은 지금 볼 수 있는 범위 밖입니다.");
        }
        // Deliberately no targetKey, query string, selected row or automatic detail drawer.
        onNavigate(`/screens/${navigation.screenId}`);
      }
    } catch (failure) {
      if (mounted.current)
        setError(
          failure instanceof Error
            ? failure.message
            : "알림을 처리하지 못했습니다.",
        );
    } finally {
      pending.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  return (
    <Dialog title="알림" onClose={onClose} testId="notifications-popup">
      <section
        data-testid="notifications-page"
        data-screen-id="UI-42"
        className="notifications-content"
        aria-busy={loading || busy}
      >
        <div className="notifications-toolbar">
          <span>
            미읽음{" "}
            <strong data-testid="notification-unread-count">
              {listing ? notificationCount(listing.unreadCount) : "–"}
            </strong>
          </span>
          <button
            type="button"
            data-testid="M25-523"
            disabled={loading || busy || !listing?.unreadCount}
            onClick={() => void readAll()}
          >
            모두 읽음
          </button>
          <button type="button" data-testid="M25-524" onClick={onClose}>
            닫기
          </button>
        </div>
        {loading && (
          <p role="status" data-testid="notifications-loading">
            알림을 불러오는 중입니다.
          </p>
        )}
        {error && (
          <div role="alert" data-testid="notifications-error">
            <p>{error}</p>
            <button
              type="button"
              data-testid="notifications-retry"
              disabled={busy || loading}
              onClick={() => void load()}
            >
              다시 시도
            </button>
          </div>
        )}
        {!loading && listing?.items.length === 0 && (
          <p data-testid="notifications-empty">알림이 없습니다.</p>
        )}
        {listing && (
          <ul className="notifications-list">
            {listing.items.map((item) => (
              <li
                key={item.notificationId}
                data-testid={`notification-row-${item.notificationId}`}
              >
                <button
                  type="button"
                  data-action-id="M25-522"
                  data-testid={`notification-read-${item.notificationId}`}
                  className={`notification-row ${item.readYn === "N" ? "notification-unread" : ""}`}
                  disabled={busy || loading}
                  onClick={() => void select(item.notificationId)}
                >
                  <span>{item.content}</span>
                  <small>
                    <time dateTime={item.notifiedAt}>
                      {notificationTime(item.notifiedAt)}
                    </time>
                    {item.readYn === "N" && <span> · 미읽음</span>}
                  </small>
                </button>
              </li>
            ))}
          </ul>
        )}
        <p className="notifications-retention">알림은 30일 동안 보관합니다.</p>
        {message && (
          <Toast
            message={message}
            kind="info"
            testId="notifications-message"
            onDismiss={() => setMessage("")}
          />
        )}
      </section>
    </Dialog>
  );
}

export function NotificationsPage() {
  const navigate = useNavigate();
  return (
    <NotificationPopup
      onClose={() => navigate("/", { replace: true })}
      onNavigate={(path) => navigate(path)}
    />
  );
}

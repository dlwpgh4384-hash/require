import { useMemo, useState } from "react";
import {
  createBrowserRouter,
  Outlet,
  useNavigate,
  useOutletContext,
  useLocation,
} from "react-router-dom";
import { CommonNavigationPage } from "../pages/common/CommonNavigationPage";
import { UserPreferencesProvider } from "../pages/userpreferences";
import {
  AccountSessionBoundary,
  useAccountSession,
} from "../pages/commonops/AccountSessionBoundary";
import { firstAccountScreen } from "../pages/commonops/accountAccessApi";
import {
  SessionProvider,
  SessionTimer,
} from "../pages/session/SessionProvider";
import { createSessionApi, sessionTiming } from "../pages/session/sessionApi";
import { NotificationPopup } from "../pages/notifications/NotificationsPage";
import { NotificationBell } from "../pages/notifications/NotificationBell";
import { createNotificationApi } from "../pages/notifications/notificationApi";
import { Toast } from "../shared/feedback/Dialog";
import { reconcileAccountScreens } from "./menuRefresh";

function AccountApplication() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <AccountSessionBoundary
      onAllowedScreensChanged={(screenIds) => {
        reconcileAccountScreens(screenIds);
        const screenId = /^\/screens\/([^/]+)$/.exec(location.pathname)?.[1];
        if (
          screenId &&
          !["UI-22", "UI-34", "UI-39", "UI-40", "UI-41", "UI-42"].includes(
            screenId,
          ) &&
          !screenIds.includes(screenId)
        ) {
          const target = screenIds.includes("UI-16")
            ? "UI-16"
            : (screenIds[0] ?? "UI-34");
          navigate(`/screens/${target}`, { replace: true });
        }
      }}
    >
      <AuthenticatedApplication />
    </AccountSessionBoundary>
  );
}

function AuthenticatedApplication() {
  const { info, meta } = useAccountSession();
  const navigate = useNavigate();
  const location = useLocation();
  const api = useMemo(() => createSessionApi((...args) => fetch(...args)), []);
  const initial = useMemo(
    () => sessionTiming(meta) ?? undefined,
    [meta.remainingSeconds, meta.sessionMinutes, meta.serverRespondedAt],
  );
  const [generation, setGeneration] = useState(0);
  const [notice, setNotice] = useState("");
  return (
    <SessionProvider
      key={info?.userId}
      api={api}
      initial={initial}
      discardDrafts={() => setGeneration((value) => value + 1)}
      onExpired={() => {
        window.dispatchEvent(new Event("account-session-expired"));
        navigate("/screens/UI-22", { replace: true });
      }}
    >
      <div data-testid="account-session-tools">
        <SessionTimer />
        {location.pathname !== "/screens/UI-42" && (
          <NotificationBell onNavigate={(path) => navigate(path)} />
        )}
      </div>
      <UserPreferencesProvider key={generation}>
        <Outlet context={{ notify: setNotice }} />
        {notice && (
          <Toast
            message={notice}
            kind="info"
            testId="account-notification-message"
            onDismiss={() => setNotice("")}
          />
        )}
      </UserPreferencesProvider>
    </SessionProvider>
  );
}

function AccountNotifications() {
  const navigate = useNavigate();
  const { notify } = useOutletContext<{ notify: (message: string) => void }>();
  const { meta } = useAccountSession();
  const api = useMemo(
    () => createNotificationApi((...args) => fetch(...args)),
    [],
  );
  const first = firstAccountScreen(
    meta.permissions ?? [],
    window.matchMedia?.("(max-width: 767px)").matches ?? false,
  );
  return (
    <NotificationPopup
      api={api}
      onClose={() => navigate(`/screens/${first.screenId}`, { replace: true })}
      onNotice={notify}
      onNavigate={(path) => navigate(path)}
    />
  );
}

export function createAppRouter() {
  return createBrowserRouter([
    {
      element: <AccountApplication />,
      children: [
        { path: "/", element: <CommonNavigationPage /> },
        // UI-22/39/40 are standalone; UI-41 uses the account owner's header dialog.
        { path: "/screens/UI-22", element: null },
        { path: "/screens/UI-39", element: null },
        { path: "/screens/UI-40", element: null },
        { path: "/screens/UI-41", element: null },
        // UI-42 is authenticated but never requires a business menu row.
        { path: "/screens/UI-42", element: <AccountNotifications /> },
        // UI-07 (M019/G007), UI-08 (M020/G007), UI-09 (M021/G007) resolve through screenRegistry.
        // UI-18 (M022/G007) resolves through screenRegistry.
        // UI-33 (M012/G004) resolves through screenRegistry at /screens/UI-33.
        // UI-29 (M011/G004) resolves through screenRegistry at /screens/UI-29.
        // UI-28 (M009/G004) resolves through screenRegistry at /screens/UI-28.
        // UI-30 (M026/G009) resolves through screenRegistry; recommendation clients stay feature-local.
        // UI-17 (M010/G004) — 인력 수요 예측 resolves through screenRegistry at /screens/UI-17.
        // Keep the shared screen route; feature pages own their API clients.
        { path: "/screens/:scrnId", element: <CommonNavigationPage /> },
        { path: "*", element: <CommonNavigationPage /> },
      ],
    },
  ]);
}

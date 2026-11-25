import { createBrowserRouter, Outlet } from "react-router-dom";
import { CommonNavigationPage } from "../pages/common/CommonNavigationPage";
import { UserPreferencesProvider } from "../pages/userpreferences";

export function createAppRouter() {
  return createBrowserRouter([
    {
      element: (
        <UserPreferencesProvider>
          <Outlet />
        </UserPreferencesProvider>
      ),
      children: [
        { path: "/", element: <CommonNavigationPage /> },
        // UI-07 (M019/G007), UI-08 (M020/G007), UI-09 (M021/G007) resolve through screenRegistry.
        // UI-18 (M022/G007) resolves through screenRegistry.
        // UI-33 (M012/G004) resolves through screenRegistry at /screens/UI-33.
        // UI-29 (M011/G004) resolves through screenRegistry at /screens/UI-29.
        // UI-28 (M009/G004) resolves through screenRegistry at /screens/UI-28.
        // UI-17 (M010/G004) — 인력 수요 예측 resolves through screenRegistry at /screens/UI-17.
        // Keep the shared screen route; feature pages own their API clients.
        { path: "/screens/:scrnId", element: <CommonNavigationPage /> },
        { path: "*", element: <CommonNavigationPage /> },
      ],
    },
  ]);
}

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
        // UI-04 (M015/G006) and UI-32 (M017/G006) use screenRegistry inside the shared shell.
        { path: "/screens/:scrnId", element: <CommonNavigationPage /> },
        { path: "*", element: <CommonNavigationPage /> },
      ],
    },
  ]);
}

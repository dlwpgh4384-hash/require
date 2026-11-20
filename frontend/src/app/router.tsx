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
        // UI-04 (M015/G006) is resolved by screenRegistry inside the shared shell.
        { path: "/screens/:scrnId", element: <CommonNavigationPage /> },
        { path: "*", element: <CommonNavigationPage /> },
      ],
    },
  ]);
}

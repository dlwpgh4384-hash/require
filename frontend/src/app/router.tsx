import { createBrowserRouter } from "react-router-dom";
import { CommonNavigationPage } from "../pages/common/CommonNavigationPage";

export function createAppRouter() {
  return createBrowserRouter([
    { path: "/", element: <CommonNavigationPage /> },
    { path: "/screens/:scrnId", element: <CommonNavigationPage /> },
    { path: "*", element: <CommonNavigationPage /> },
  ]);
}

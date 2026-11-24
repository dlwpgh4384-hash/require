import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { createAppRouter } from "./router";
import { wrapNetworkErrors } from "../shared/network";

// 네트워크가 끊겼을 때 영어 「Failed to fetch」 대신 우리말 문구를 보인다(공통).
wrapNetworkErrors(window);

const router = createAppRouter();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
);

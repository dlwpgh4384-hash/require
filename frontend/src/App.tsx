import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { AppLayout } from "./components/AppLayout";
import { ScreenOutlet } from "./pages/ScreenOutlet";
import { loadMenus, type MenuResponse } from "./services/menus";
import { firstScreen, screenPath, visibleTree } from "./menuTree";
import { useMobile } from "./useMobile";

export default function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const mobile = useMobile();
  const [data, setData] = useState<MenuResponse>();
  const [status, setStatus] = useState<"loading" | "error" | "success">(
    "loading",
  );
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setStatus("loading");
    loadMenus(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) {
          setData(value);
          setStatus("success");
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setStatus("error");
      });
    return () => controller.abort();
  }, [attempt]);
  const tree = useMemo(
    () =>
      visibleTree(
        data?.data.data ?? [],
        data?.meta.parentMenuIds ?? [],
        mobile,
      ),
    [data, mobile],
  );
  const first = firstScreen(tree);
  const selected = (() => {
    if (location.pathname === "/") return undefined;
    const match = /^\/screens\/([^/]+)\/?$/.exec(location.pathname);
    if (!match) return null;
    try {
      return decodeURIComponent(match[1]);
    } catch {
      return null;
    }
  })();
  useEffect(() => {
    if (status === "success" && selected === undefined && first?.scrnId) {
      navigate(screenPath(first.scrnId), { replace: true });
    }
  }, [selected, first?.scrnId, navigate, status]);
  const screenState =
    selected && data && Object.hasOwn(data.meta.screenStates, selected)
      ? data.meta.screenStates[selected]
      : undefined;
  const title = screenState?.available ? (screenState.menuNm ?? "") : "";
  return (
    <AppLayout
      mobile={mobile}
      tree={tree}
      status={status}
      selected={
        screenState?.available ? (screenState.menuId ?? undefined) : undefined
      }
      title={title}
      onRetry={() => setAttempt((value) => value + 1)}
    >
      <ScreenOutlet
        status={status}
        selected={selected}
        state={screenState}
        mobile={mobile}
        first={first}
        onFirst={() => first?.scrnId && navigate(screenPath(first.scrnId))}
      />
    </AppLayout>
  );
}

import { createContext, useContext, useEffect, useState } from "react";

/** 메뉴 옆 미수 건수와 월 마감 경고. 화면 이동·저장 때만 새로 읽는다. */
export const MENU_BADGE_REFRESH_EVENT = "navigation-menu-badges-refresh";
export function requestMenuBadgeRefresh() {
  window.dispatchEvent(new Event(MENU_BADGE_REFRESH_EVENT));
}

export type MenuBadgeCounts = Record<string, number | boolean>;
export const MenuBadgeContext = createContext<MenuBadgeCounts>({});

/** 메뉴를 읽은 뒤, 화면 주소가 바뀌거나 저장 신호가 오면 미수 건수를 다시 읽는다. 실패하면 배지만 숨긴다. */
export function useMenuBadgeCounts(
  pathname: string,
  ready: boolean,
  monthCloseReady = false,
): MenuBadgeCounts {
  const [counts, setCounts] = useState<MenuBadgeCounts>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const refresh = () => setAttempt((value) => value + 1);
    window.addEventListener(MENU_BADGE_REFRESH_EVENT, refresh);
    return () => window.removeEventListener(MENU_BADGE_REFRESH_EVENT, refresh);
  }, []);
  useEffect(() => {
    // 메뉴를 다 읽은 뒤에만 읽는다(메뉴 읽기 실패·다시 시도와 섞이지 않게).
    const controller = new AbortController();
    setCounts({});
    const read = (screen: string, path: string, field: string) => {
      Promise.resolve()
        .then(() => fetch(path, { signal: controller.signal }))
        .then(async (response) => {
          if (!response.ok) throw new Error("Badge unavailable");
          const body = await response.json();
          const value: unknown = body?.data?.[field];
          const valid =
            screen === "UI-14"
              ? typeof value === "boolean"
              : typeof value === "number" &&
                Number.isFinite(value) &&
                value >= 0;
          if (body?.success !== true || !valid)
            throw new Error("Invalid badge response");
          if (!controller.signal.aborted)
            setCounts((previous) => ({
              ...previous,
              [screen]: value as number | boolean,
            }));
        })
        .catch(() => {
          if (!controller.signal.aborted)
            setCounts((previous) => {
              const next = { ...previous };
              delete next[screen];
              return next;
            });
        });
    };
    if (ready) read("UI-13", "/api/common/bills/unpaid-count", "count");
    if (monthCloseReady)
      read("UI-14", "/api/common/month-close-badge", "needsAttention");
    return () => controller.abort();
  }, [pathname, attempt, ready, monthCloseReady]);
  return {
    ...(ready && counts["UI-13"] !== undefined
      ? { "UI-13": counts["UI-13"] }
      : {}),
    ...(monthCloseReady && counts["UI-14"] !== undefined
      ? { "UI-14": counts["UI-14"] }
      : {}),
  };
}

/** 시안 .nb: 0이면 숨긴다. */
export function MenuBadge({ scrnId }: { scrnId: string | null }) {
  const counts = useContext(MenuBadgeContext);
  const count = scrnId ? counts[scrnId] : undefined;
  if (!count) return null;
  if (scrnId === "UI-14")
    return count === true ? (
      <span
        className="menu-nb nb warn"
        aria-label="이번 달 미마감"
        data-testid="menu-badge-UI-14"
      >
        !
      </span>
    ) : null;
  return (
    <span
      className="menu-nb"
      aria-label={`미수 ${count}건`}
      data-testid={`menu-badge-${scrnId}`}
    >
      {count}
    </span>
  );
}

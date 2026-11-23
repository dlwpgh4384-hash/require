import { createContext, useContext, useEffect, useState } from "react";

/** 메뉴 옆 숫자 배지(SFR-001-04, 시안 MENU_BADGE). 지금은 청구·입금(UI-13)의 미수 건수만 붙인다.
 * 월 마감(UI-14)의 「!」는 월 마감 화면 작업 때 붙인다. 자동으로 계속 읽지 않고 화면 이동·저장 때만 새로 읽는다. */
export const MENU_BADGE_REFRESH_EVENT = "navigation-menu-badges-refresh";
export function requestMenuBadgeRefresh() {
  window.dispatchEvent(new Event(MENU_BADGE_REFRESH_EVENT));
}

export type MenuBadgeCounts = Record<string, number>;
export const MenuBadgeContext = createContext<MenuBadgeCounts>({});

/** 메뉴를 읽은 뒤, 화면 주소가 바뀌거나 저장 신호가 오면 미수 건수를 다시 읽는다. 실패하면 배지만 숨긴다. */
export function useMenuBadgeCounts(pathname: string, ready: boolean): MenuBadgeCounts {
  const [counts, setCounts] = useState<MenuBadgeCounts>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const refresh = () => setAttempt((value) => value + 1);
    window.addEventListener(MENU_BADGE_REFRESH_EVENT, refresh);
    return () => window.removeEventListener(MENU_BADGE_REFRESH_EVENT, refresh);
  }, []);
  useEffect(() => {
    // 메뉴를 다 읽은 뒤에만 읽는다(메뉴 읽기 실패·다시 시도와 섞이지 않게).
    if (!ready) return;
    const controller = new AbortController();
    // 배지는 곁들이 정보라 읽기 실패(동기 오류 포함)가 화면을 깨지 않게 Promise로 감싼다.
    Promise.resolve()
      .then(() => fetch("/api/common/bills/unpaid-count", { signal: controller.signal }))
      .then(async (response) => {
        if (!response.ok) throw new Error("Badge unavailable");
        const body = await response.json();
        const count = body?.data?.count;
        if (!controller.signal.aborted)
          setCounts(typeof count === "number" ? { "UI-13": count } : {});
      })
      .catch(() => {
        if (!controller.signal.aborted) setCounts({});
      });
    return () => controller.abort();
  }, [pathname, attempt, ready]);
  return counts;
}

/** 시안 .nb: 0이면 숨긴다. */
export function MenuBadge({ scrnId }: { scrnId: string | null }) {
  const counts = useContext(MenuBadgeContext);
  const count = scrnId ? counts[scrnId] : undefined;
  if (!count) return null;
  return (
    <span className="menu-nb" aria-label={`미수 ${count}건`} data-testid={`menu-badge-${scrnId}`}>
      {count}
    </span>
  );
}

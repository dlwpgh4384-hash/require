/** 메뉴를 바꾼 화면과 공통 틀의 단방향 갱신 신호. 탭을 닫거나 역할 draft를 바꾸지 않는다. */
export const MENU_REFRESH_EVENT = "navigation-menus-refresh";
export function requestMenuRefresh() {
  window.dispatchEvent(new Event(MENU_REFRESH_EVENT));
}

export const ACCOUNT_SCREENS_CHANGED_EVENT =
  "navigation-account-screens-changed";
export function reconcileAccountScreens(screenIds: string[]) {
  window.dispatchEvent(
    new CustomEvent(ACCOUNT_SCREENS_CHANGED_EVENT, { detail: screenIds }),
  );
  requestMenuRefresh();
}

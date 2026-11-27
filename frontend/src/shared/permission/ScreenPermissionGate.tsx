import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import {
  PERMISSION_ACTIONS,
  usePermission,
  type PermissionAction,
  type ScreenPermission,
} from "./permission";

/**
 * 화면 하나의 동작 단추·입력 칸에 메뉴 × 동작 권한을 거는 틀(시안 applyActPerm, SFR-008-02, ⑦-5).
 * - 권한 없는 동작 단추는 그리지 않는다(hidden). 단추 글자로 동작을 가린다(시안 규칙 그대로).
 * - 수정 권한이 없으면 입력 칸을 잠그고(검색·기간·탭·쪽 넘기기는 그대로) 안내 띠를 하나 보인다.
 * - 등록 권한만 있으면 새 자료 창(제목이 「…등록」이거나 data-permission-new)에서만 쓸 수 있다.
 * 화면이 단추에 `data-permission="REG"` 처럼 동작을 직접 적으면 글자 규칙보다 그것을 따른다.
 */
export const READONLY_NOTICE =
  "수정 권한이 없어 보기만 할 수 있습니다(권한 › 메뉴 × 동작 표의 「수정」).";

const RULES: [PermissionAction, RegExp][] = [
  ["REG", /^(\+\s*)?(.*\s)?(등록|배정|추가)$|메뉴 등록|역할 복사/],
  ["UPLOAD", /업로드|양식 내려받기/],
  ["EXCEL", /다운로드/],
  ["DEL", /삭제|배정 취소/],
  ["CNFM", /확정|마감하기|승인|반려/],
  ["MOD", /^수정$|^저장$|변경$|사용 중지|다시 사용|잠금 해제|비밀번호 초기화/],
];
/** 권한과 상관없는 곳: 검색·필터, 기간, 탭, 쪽 넘기기, 표 도구, 금액 단위. */
const FREE = [
  ".ls-filters",
  ".ls-filter",
  ".ls-tbar",
  ".ls-pager",
  ".ls-pop",
  ".ls-unitsel",
  ".ls-tabs",
  ".ls-chk",
  ".ls-dh",
  "[role='tablist']",
  "[role='search']",
  "[class*='period']",
  "[class*='year-bar']",
  "[class*='fbar']",
  "[class*='-tabs']",
  "[class*='search']",
  "[class*='toolbar']",
  "[data-permission-free]",
].join(",");
const HIDDEN = "data-permission-hidden";
const LOCKED = "data-permission-locked";
const DRAG = "data-permission-drag";

function label(element: Element) {
  const text = (element.textContent ?? "").replace(/\s+/g, " ").trim();
  return text || element.getAttribute("aria-label") || "";
}
/** 새 자료를 넣는 창인지(등록 권한으로 쓸 수 있는 곳). */
function newRecordArea(element: Element) {
  const area = element.closest(
    "[data-permission-new],[role='dialog'],.ls-drawer,form",
  );
  if (!area) return false;
  if (area.hasAttribute("data-permission-new")) return true;
  const heading = area.querySelector("h1,h2,h3,[role='heading']");
  return /등록$/.test(label(heading ?? area.ownerDocument.createElement("i")));
}
function actionOf(button: Element): PermissionAction | null {
  const explicit = button.getAttribute("data-permission");
  if (explicit && (PERMISSION_ACTIONS as string[]).includes(explicit))
    return explicit as PermissionAction;
  if (explicit) return null;
  const text = label(button);
  return RULES.find(([, rule]) => rule.test(text))?.[0] ?? null;
}
function allowed(
  permission: ScreenPermission,
  action: PermissionAction,
  element: Element,
) {
  if (permission.can(action)) return true;
  // 새 자료 창의 [저장]·입력은 등록 권한으로 본다.
  return action === "MOD" && permission.can("REG") && newRecordArea(element);
}

/** 규칙을 한 번 건다. 잠근 입력 칸 수를 돌려준다. */
export function applyScreenPermission(
  root: Element,
  permission: ScreenPermission,
  lockInputs = true,
) {
  root.querySelectorAll("button,a[data-permission]").forEach((button) => {
    if (button.hasAttribute(HIDDEN) || button.closest(FREE)) return;
    const action = actionOf(button);
    if (action && !allowed(permission, action, button)) {
      button.setAttribute(HIDDEN, "");
      (button as HTMLElement).hidden = true;
    }
  });
  let locked = 0;
  if (!lockInputs || permission.can("MOD")) return locked;
  root.querySelectorAll("input,select,textarea").forEach((element) => {
    const input = element as HTMLInputElement;
    if (input.type === "hidden" || input.type === "search") return;
    if (input.closest(FREE) || allowed(permission, "MOD", input)) return;
    if (input.hasAttribute(LOCKED)) {
      if (!input.disabled) input.disabled = true;
      locked += 1;
      return;
    }
    if (input.disabled) return;
    input.setAttribute(LOCKED, "");
    input.disabled = true;
    locked += 1;
  });
  root.querySelectorAll("[draggable='true']").forEach((element) => {
    element.setAttribute(DRAG, "");
    element.setAttribute("draggable", "false");
  });
  return locked;
}
function release(root: Element) {
  root.querySelectorAll(`[${HIDDEN}]`).forEach((element) => {
    element.removeAttribute(HIDDEN);
    (element as HTMLElement).hidden = false;
  });
  root.querySelectorAll(`[${LOCKED}]`).forEach((element) => {
    element.removeAttribute(LOCKED);
    (element as HTMLInputElement).disabled = false;
  });
  root.querySelectorAll(`[${DRAG}]`).forEach((element) => {
    element.removeAttribute(DRAG);
    element.setAttribute("draggable", "true");
  });
}

export function ScreenPermissionGate({
  screenId,
  children,
  lockInputs = true,
  notice = true,
}: {
  screenId: string;
  children: ReactNode;
  /** 입력 칸 잠금(질문 칸만 있는 AI 화면 등은 끈다). */
  lockInputs?: boolean;
  /** 수정 권한 없음 안내 띠(제목줄 단추 자리에서는 끈다). */
  notice?: boolean;
}) {
  const permission = usePermission(screenId);
  const root = useRef<HTMLDivElement>(null);
  const [locked, setLocked] = useState(false);
  const key = PERMISSION_ACTIONS.filter((action) => !permission.can(action)).join(",");
  useLayoutEffect(() => {
    const element = root.current;
    if (!element) return;
    if (!key) {
      setLocked(false);
      return;
    }
    const apply = () => {
      const count = applyScreenPermission(element, permission, lockInputs);
      if (count > 0) setLocked(true);
    };
    apply();
    const observer = new MutationObserver(apply);
    observer.observe(element, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ["disabled", "draggable"],
    });
    return () => {
      observer.disconnect();
      release(element);
    };
    // permission은 key가 같으면 같은 결과다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, lockInputs]);
  return (
    <div
      ref={root}
      className="screen-permission"
      data-permission-screen={screenId}
      style={{ display: "contents" }}
    >
      {notice && locked && (
        <div
          className="ls-banner ls-banner-info"
          role="note"
          data-testid="permission-readonly-notice"
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4" />
          </svg>
          <span>{READONLY_NOTICE}</span>
        </div>
      )}
      {children}
    </div>
  );
}

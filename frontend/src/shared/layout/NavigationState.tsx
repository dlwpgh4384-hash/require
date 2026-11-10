import type { ReactNode } from "react";
import { ShellIcon, type ShellIconName } from "./ShellIcon";

type StateKind =
  | "loading"
  | "error"
  | "empty"
  | "unavailable"
  | "pc-only"
  | "missing"
  | "ready";
const content: Record<
  StateKind,
  { title: string; description: string; icon: ShellIconName }
> = {
  loading: {
    title: "로딩 중",
    description: "메뉴 정보를 불러오고 있습니다.",
    icon: "clock",
  },
  error: {
    title: "메뉴를 불러오지 못했습니다",
    description: "연결 상태를 확인한 뒤 다시 시도해 주세요.",
    icon: "alert",
  },
  empty: {
    title: "등록된 메뉴 없음",
    description: "현재 표시할 수 있는 메뉴가 없습니다.",
    icon: "empty",
  },
  unavailable: {
    title: "사용할 수 없는 화면입니다",
    description: "메뉴의 사용 상태 또는 연결 정보를 확인해 주세요.",
    icon: "lock",
  },
  "pc-only": {
    title: "PC 전용 화면입니다",
    description:
      "넓은 화면에서 이용해 주세요. 화면을 넓히면 같은 메뉴로 돌아옵니다.",
    icon: "screen",
  },
  missing: {
    title: "없는 화면입니다",
    description: "화면 주소를 확인하거나 첫 화면으로 이동해 주세요.",
    icon: "search",
  },
  ready: {
    title: "준비 중",
    description:
      "이 화면의 업무 기능은 아직 제공되지 않습니다. 다른 메뉴를 탐색할 수 있습니다.",
    icon: "screen",
  },
};

export function NavigationState({
  kind,
  children,
  compact = false,
}: {
  kind: StateKind;
  children?: ReactNode;
  compact?: boolean;
}) {
  const item = content[kind];
  return (
    <div
      className={`navigation-state state-${kind}${compact ? " is-compact" : ""}`}
      role={
        kind === "error" ? "alert" : kind === "loading" ? "status" : undefined
      }
      aria-busy={kind === "loading" ? true : undefined}
    >
      {kind === "loading" ? (
        <div className="menu-skeleton" aria-hidden="true">
          <i />
          <i />
          <i />
        </div>
      ) : (
        <span className="state-icon">
          <ShellIcon name={item.icon} />
        </span>
      )}
      <p className="state-title">{item.title}</p>
      <p className="state-description">{item.description}</p>
      {children && <div className="state-actions">{children}</div>}
    </div>
  );
}

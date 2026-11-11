import type { ReactNode } from "react";
import { ShellIcon, type ShellIconName } from "./ShellIcon";

type StateKind =
  | "loading"
  | "error"
  | "empty"
  | "no-screen"
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
  // FR-0002: 메뉴는 있지만 열 수 있는 화면이 하나도 없을 때
  "no-screen": {
    title: "열 수 있는 화면이 없습니다",
    description: "메뉴의 사용 상태나 화면 폭을 확인해 주세요.",
    icon: "empty",
  },
  unavailable: {
    title: "사용할 수 없는 화면입니다",
    description: "메뉴의 사용 상태 또는 연결 정보를 확인해 주세요.",
    icon: "lock",
  },
  // FR-0107: 휴대폰에서 PC 전용 화면 주소로 들어왔을 때
  "pc-only": {
    title: "PC에서 이용해 주세요",
    description: "이 화면은 큰 표를 다루는 화면이라 휴대폰에서는 열리지 않습니다",
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
  description,
}: {
  kind: StateKind;
  children?: ReactNode;
  compact?: boolean;
  /** 기본 설명 대신 보여 줄 문구(예: PC 전용 화면 이름이 들어간 안내). */
  description?: string;
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
      <p className="state-description">{description ?? item.description}</p>
      {children && <div className="state-actions">{children}</div>}
    </div>
  );
}

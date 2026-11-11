import { ShellIcon, type ShellIconName } from "./ShellIcon";

const toolIcons: Record<ToolName, ShellIconName> = {
  즐겨찾기: "star",
  "최근 본 화면": "clock",
  "화면 탭": "tabs",
  "상단 도구줄": "tools",
  단축키: "keyboard",
  알림: "bell",
};

export type ToolName =
  | "즐겨찾기"
  | "최근 본 화면"
  | "화면 탭"
  | "상단 도구줄"
  | "단축키"
  | "알림";

export function DisabledTools({ names }: { names: ToolName[] }) {
  return (
    <div
      data-testid={`disabled-tools-${names.map((name) => ({ 즐겨찾기: "favorites", "최근 본 화면": "recent-screens", "화면 탭": "screen-tabs", "상단 도구줄": "toolbar", 단축키: "shortcuts", 알림: "notifications" })[name]).join("-")}`}
      className="disabled-tools"
    >
      {names.map((name) => (
        <button
          data-testid={`disabled-tool-${{ 즐겨찾기: "favorites", "최근 본 화면": "recent-screens", "화면 탭": "screen-tabs", "상단 도구줄": "toolbar", 단축키: "shortcuts", 알림: "notifications" }[name]}`}
          key={name}
          type="button"
          disabled
          aria-label={`${name} 준비 중`}
          title={`${name} 준비 중`}
        >
          <ShellIcon name={toolIcons[name]} />
          <span className="tool-label">{name}</span>
          <span className="tool-note">준비 중</span>
        </button>
      ))}
    </div>
  );
}

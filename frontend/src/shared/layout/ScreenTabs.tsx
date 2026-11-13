import { createContext, useContext } from "react";
import { ShellIcon } from "./ShellIcon";

export interface ScreenTab {
  id: string;
  label: string;
  dirty: boolean;
  fixed: boolean;
}

/** FR-0103 화면 탭 줄. 탭 열기·닫기 규칙은 화면 틀(NavigationRoot)이 정한다. */
export function ScreenTabs({
  tabs,
  active,
  onSelect,
  onClose,
}: {
  tabs: ScreenTab[];
  active: string | null;
  onSelect: (id: string) => void;
  onClose: (id: string) => void;
}) {
  return (
    <div
      className="tab-strip"
      role="tablist"
      aria-label="열린 화면"
      data-testid="screen-tab-list"
    >
      {tabs.map((tab) => (
        <div
          key={tab.id}
          className={`screen-tab${tab.id === active ? " is-active" : ""}`}
          data-testid={`screen-tab-${tab.id.toLowerCase()}`}
        >
          <button
            type="button"
            role="tab"
            aria-selected={tab.id === active}
            className="screen-tab-label"
            data-testid={`screen-tab-select-${tab.id.toLowerCase()}`}
            onClick={() => onSelect(tab.id)}
          >
            {tab.dirty && (
              <span className="tab-dirty" aria-label="저장하지 않은 내용 있음">
                ●{" "}
              </span>
            )}
            {tab.label}
          </button>
          {!tab.fixed && (
            <button
              type="button"
              className="screen-tab-close"
              data-testid={`screen-tab-close-${tab.id.toLowerCase()}`}
              aria-label={`${tab.label} 탭 닫기`}
              onClick={() => onClose(tab.id)}
            >
              <ShellIcon name="x" size={14} />
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

/** 업무 화면이 "저장하지 않은 입력이 있음"을 화면 틀에 알린다(탭 ● 표시와 닫기 확인에 쓴다). */
export const ScreenDirtyContext = createContext<
  (screenId: string, dirty: boolean) => void
>(() => undefined);

export function useScreenDirty() {
  return useContext(ScreenDirtyContext);
}

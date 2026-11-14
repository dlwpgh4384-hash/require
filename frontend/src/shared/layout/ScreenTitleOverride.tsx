import {
  createContext,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from "react";

/**
 * 시안의 「폼 화면」(예: 거래처 등록): 제목줄이 [‹ 목록] + 폼 제목으로 바뀌고 화면 작업 단추는 숨는다.
 * 경로·즐겨찾기 별·화면 번호는 그대로 둔다. 그 화면이 열려 있을 때만 적용한다.
 */
export interface ScreenTitleOverride {
  screenId: string;
  title: string;
  onBack: () => void;
}

type Setter = (value: ScreenTitleOverride | null) => void;
const OverrideContext = createContext<Setter>(() => {});

export const ScreenTitleOverrideProvider = OverrideContext.Provider;

/** 폼이 열려 있는 동안 제목줄을 바꾼다. 닫히면(언마운트) 원래 제목으로 돌아간다. */
export function useScreenTitleOverride(
  screenId: string,
  title: string | null,
  onBack: () => void,
) {
  const set = useContext(OverrideContext);
  const back = useRef(onBack);
  back.current = onBack;
  useEffect(() => {
    if (!title) return;
    set({ screenId, title, onBack: () => back.current() });
    return () => set(null);
  }, [set, screenId, title]);
}

export function BackToListButton({ onClick }: { onClick: () => void }): ReactNode {
  return (
    <button
      type="button"
      className="title-back"
      data-testid="screen-title-back"
      onClick={onClick}
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M15 18l-6-6 6-6" />
      </svg>{" "}
      목록
    </button>
  );
}

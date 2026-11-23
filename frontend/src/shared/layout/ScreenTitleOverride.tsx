import {
  createContext,
  useCallback,
  useState,
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

/**
 * 화면별로 따로 둔다(탭마다 폼이 열려 있어도 서로 덮지 않게). owner는 그 폼 하나를 가리켜,
 * 닫힐 때 자기가 건 것만 지운다(같은 화면의 다른 폼 것은 남긴다).
 */
type Setter = (screenId: string, owner: symbol, value: Omit<ScreenTitleOverride, "screenId"> | null) => void;
const OverrideContext = createContext<Setter>(() => {});

/** AppLayout이 쓰는 화면별 제목 바꾸기 저장소. */
export function useScreenTitleOverrides() {
  const [map, setMap] = useState<Record<string, { owner: symbol; title: string; onBack: () => void }>>({});
  const set = useCallback<Setter>((screenId, owner, value) => {
    setMap((current) => {
      if (value) return { ...current, [screenId]: { owner, ...value } };
      if (current[screenId]?.owner !== owner) return current;
      const next = { ...current };
      delete next[screenId];
      return next;
    });
  }, []);
  const get = (screenId: string | undefined): ScreenTitleOverride | null => {
    const value = screenId ? map[screenId] : undefined;
    return value && screenId ? { screenId, title: value.title, onBack: value.onBack } : null;
  };
  return { set, get };
}

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
  const owner = useRef(Symbol("screen-title-override"));
  useEffect(() => {
    if (!title) return;
    const mine = owner.current;
    set(screenId, mine, { title, onBack: () => back.current() });
    return () => set(screenId, mine, null);
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

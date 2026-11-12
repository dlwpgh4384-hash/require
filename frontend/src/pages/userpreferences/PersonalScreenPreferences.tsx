import { useState } from "react";
import { ShellIcon } from "../../shared/layout/ShellIcon";
import { useUserPreferences } from "./UserPreferencesProvider";
import { UserPreferenceToast } from "./UserPreferenceDialog";
import {
  preferenceScreenIds,
  recentScreenIds,
  visiblePreferenceScreens,
} from "./userPreferences";

export interface PreferenceMenu {
  scrnId: string | null;
  menuNm: string;
  pcOnlyYn: "Y" | "N";
}

// UI-34: title-row action. The shell supplies the selected API-backed screen ID.
export function FavoriteScreenButton({ screenId }: { screenId: string }) {
  const { states, reload, save } = useUserPreferences();
  const state = states.FAV;
  const [toast, setToast] = useState(false);
  const [message, setMessage] = useState("");
  let ids: string[] = [];
  try {
    ids = preferenceScreenIds(state.data?.prefVal ?? null);
  } catch {
    return <p role="alert">화면 목록을 다시 불러와 주세요.</p>;
  }
  const selected = ids.includes(screenId);
  const toggle = async () => {
    if (!state.data || state.saving) return;
    if (!selected && ids.length >= 10) {
      setMessage("즐겨찾기는 최대 10개까지 추가할 수 있습니다.");
      return;
    }
    setMessage("");
    setToast(false);
    try {
      await save(
        "FAV",
        JSON.stringify(
          selected ? ids.filter((id) => id !== screenId) : [...ids, screenId],
        ),
        state.data.modDttm,
      );
      if (!selected) setToast(true);
    } catch {
      /* The provider retains the previous server value and displays a persistent error. */
    }
  };
  return (
    <span data-testid="favorite-screen-action">
      <button
        type="button"
        data-testid="favorite-screen-toggle"
        aria-label={selected ? "즐겨찾기 해제" : "즐겨찾기 추가"}
        aria-pressed={selected}
        disabled={state.status !== "ready" || state.saving}
        onClick={() => {
          void toggle();
        }}
      >
        {selected ? "★" : "☆"}
      </button>
      {(message || state.error) && (
        <span role="alert">{message || state.error}</span>
      )}
      {state.error && (
        <button
          type="button"
          data-testid="favorite-reload"
          onClick={() => {
            void reload("FAV");
          }}
        >
          다시 불러오기
        </button>
      )}
      {toast && (
        <UserPreferenceToast
          message="즐겨찾기에 추가했습니다."
          onDismiss={() => setToast(false)}
        />
      )}
    </span>
  );
}

export function PersonalScreenLists({
  menus,
  available,
  mobile,
  collapsed,
  onNavigate,
}: {
  menus: PreferenceMenu[];
  available: (id: string) => boolean;
  mobile: boolean;
  collapsed: boolean;
  onNavigate: (screenId: string) => void;
}) {
  const { states, reload } = useUserPreferences();
  if (collapsed) return null;
  return (
    <div
      data-testid="personal-screen-lists"
      className="preference-personal-lists"
    >
      {(["FAV", "RECENT"] as const).map((type) => {
        const state = states[type];
        let ids: string[] = [];
        let invalid = false;
        try {
          ids = preferenceScreenIds(state.data?.prefVal ?? null);
        } catch {
          invalid = true;
        }
        const list = visiblePreferenceScreens(ids, menus, mobile, available);
        return (
          <section
            key={type}
            aria-label={type === "FAV" ? "즐겨찾기" : "최근 본 화면"}
          >
            <h2>
              <ShellIcon name={type === "FAV" ? "star" : "clock"} size={14} />
              {type === "FAV" ? "즐겨찾기" : "최근 본 화면"}
            </h2>
            {state.status === "loading" ? (
              <p role="status">설정을 불러오는 중입니다.</p>
            ) : state.status === "error" || invalid ? (
              <p role="alert">화면 목록을 불러오지 못했습니다.</p>
            ) : list.length === 0 ? (
              <p>
                {type === "FAV"
                  ? "화면 제목 옆 ☆를 누르면 추가됩니다"
                  : "아직 없습니다"}
              </p>
            ) : (
              <ul>
                {list.map((item) => (
                  <li
                    key={item.scrnId}
                    data-testid={`${type.toLowerCase()}-${item.scrnId!.toLowerCase()}`}
                  >
                    <button
                      type="button"
                      data-testid={`${type.toLowerCase()}-open-${item.scrnId!.toLowerCase()}`}
                      onClick={() => onNavigate(item.scrnId!)}
                    >
                      {item.menuNm} <small>{item.scrnId}</small>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {(state.error || invalid) && (
              <div role="alert">
                <p>{state.error}</p>
                <button
                  type="button"
                  data-testid={`${type.toLowerCase()}-reload`}
                  onClick={() => {
                    void reload(type);
                  }}
                >
                  다시 불러오기
                </button>
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}

export function useRecentScreenVisit() {
  const { states, save } = useUserPreferences();
  // Invoke only after a user navigation (including explicit direct entry) was accepted.
  // Never call from preference restoration, viewport changes or initial dashboard setup.
  return async (screenId: string) => {
    const state = states.RECENT;
    if (state.status !== "ready" || !state.data || state.saving || state.error)
      return false;
    try {
      await save(
        "RECENT",
        JSON.stringify(
          recentScreenIds(preferenceScreenIds(state.data.prefVal), screenId),
        ),
        state.data.modDttm,
      );
      return true;
    } catch {
      return false;
    }
  };
}

export function useMenuFoldPreference(mobile: boolean, tablet: boolean) {
  const { states, save, reload } = useUserPreferences();
  const state = states.MENU_FOLD;
  const stored = state.data?.found ? state.data.prefVal === "true" : undefined;
  return {
    folded: stored ?? tablet,
    loading: state.status === "loading",
    error: state.error,
    reload: () => reload("MENU_FOLD"),
    setFolded: async (folded: boolean) => {
      if (
        mobile ||
        state.status !== "ready" ||
        !state.data ||
        state.saving ||
        state.error
      )
        return false;
      try {
        await save("MENU_FOLD", String(folded), state.data.modDttm);
        return true;
      } catch {
        return false;
      }
    },
  };
}

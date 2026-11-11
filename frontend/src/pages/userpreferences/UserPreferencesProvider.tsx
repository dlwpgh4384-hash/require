import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  getCommonUserPreference,
  putCommonUserPreference,
  UserPreferenceRequestError,
  preferenceConflict,
  preferenceSaveError,
  type PreferenceType,
  type UserPreferenceResult,
} from "./userPreferences";

export type GlobalPreferenceType = Exclude<PreferenceType, "PERIOD">;
const globalTypes: GlobalPreferenceType[] = [
  "MENU_FOLD",
  "FAV",
  "RECENT",
  "PAGE_SIZE",
  "DENSE",
];
export interface GlobalPreferenceState {
  status: "loading" | "error" | "ready";
  data?: UserPreferenceResult;
  error?: string;
  saving?: boolean;
}
type States = Record<GlobalPreferenceType, GlobalPreferenceState>;
interface PreferencesContext {
  states: States;
  reload: (type: GlobalPreferenceType) => Promise<void>;
  save: (
    type: GlobalPreferenceType,
    value: string,
    token: string | null,
  ) => Promise<UserPreferenceResult>;
}
const Context = createContext<PreferencesContext | null>(null);
const initial = () =>
  Object.fromEntries(
    globalTypes.map((type) => [type, { status: "loading" }]),
  ) as States;

export function UserPreferencesProvider({ children }: { children: ReactNode }) {
  const [states, setStates] = useState<States>(initial);
  const reload = useCallback(
    async (type: GlobalPreferenceType, signal?: AbortSignal) => {
      setStates((previous) => ({
        ...previous,
        [type]: { ...previous[type], status: "loading", error: undefined },
      }));
      try {
        const result = await getCommonUserPreference(type, "GLOBAL", signal);
        if (!signal?.aborted)
          setStates((previous) => ({
            ...previous,
            [type]: { status: "ready", data: result.data },
          }));
      } catch {
        if (!signal?.aborted)
          setStates((previous) => ({
            ...previous,
            [type]: {
              ...previous[type],
              status: "error",
              error: "설정을 불러오지 못했습니다. 다시 시도해 주세요.",
            },
          }));
      }
    },
    [],
  );
  useEffect(() => {
    const controller = new AbortController();
    globalTypes.forEach((type) => {
      void reload(type, controller.signal);
    });
    return () => controller.abort();
  }, [reload]);
  const save = useCallback(
    async (type: GlobalPreferenceType, value: string, token: string | null) => {
      setStates((previous) => ({
        ...previous,
        [type]: { ...previous[type], saving: true, error: undefined },
      }));
      try {
        await putCommonUserPreference(type, "GLOBAL", value, token);
        const result = await getCommonUserPreference(type, "GLOBAL");
        setStates((previous) => ({
          ...previous,
          [type]: { status: "ready", data: result.data },
        }));
        return result.data;
      } catch (error) {
        setStates((previous) => ({
          ...previous,
          [type]: {
            ...previous[type],
            saving: false,
            error:
              error instanceof UserPreferenceRequestError &&
              error.status === 409
                ? preferenceConflict
                : preferenceSaveError,
          },
        }));
        throw error;
      }
    },
    [],
  );
  const page = states.PAGE_SIZE.data?.found
    ? (states.PAGE_SIZE.data.prefVal ?? undefined)
    : undefined;
  const dense = states.DENSE.data?.found
    ? states.DENSE.data.prefVal === "좁게"
    : undefined;
  return (
    <Context.Provider value={{ states, reload, save }}>
      <div
        data-testid="user-preferences"
        data-page-size={page}
        data-density={
          dense === undefined ? undefined : dense ? "compact" : "comfortable"
        }
        className={dense ? "user-preferences is-dense" : "user-preferences"}
      >
        {children}
      </div>
    </Context.Provider>
  );
}
export function useUserPreferences() {
  const context = useContext(Context);
  if (!context) throw new Error("UserPreferencesProvider가 필요합니다.");
  return context;
}
export function useScreenPresentation() {
  const { states } = useUserPreferences();
  return {
    pageSize:
      states.PAGE_SIZE.data?.found && states.PAGE_SIZE.data.prefVal !== null
        ? Number(states.PAGE_SIZE.data.prefVal)
        : undefined,
    dense: states.DENSE.data?.found
      ? states.DENSE.data.prefVal === "좁게"
      : undefined,
  };
}

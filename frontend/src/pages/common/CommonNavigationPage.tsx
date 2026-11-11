import { useEffect, useState } from "react";
import { NavigationRoot, type ShellMenuItem } from "../../app/NavigationRoot";
import { useViewport } from "../../shared/layout/useViewport";
import {
  FavoriteScreenButton,
  PersonalScreenLists,
  UserPreferenceTools,
  useMenuFoldPreference,
  useOptionalUserPreferences,
  useRecentScreenVisit,
  useUserPreferences,
} from "../userpreferences";
import { PlaceholderScreen } from "./PlaceholderScreen";

const renderScreen = (scrnId: string) => <PlaceholderScreen scrnId={scrnId} />;

/** 공통 화면 틀(00) + 공통 기능(00-1): 탭, 즐겨찾기·최근, 도구줄, 메뉴 접기 저장. */
function PreferenceNavigation() {
  const { mobile, tablet } = useViewport();
  const fold = useMenuFoldPreference(mobile, tablet);
  const visit = useRecentScreenVisit();
  const { states } = useUserPreferences();
  const recent = states.RECENT;
  // 최근 본 화면은 한 번에 하나씩 순서대로 저장한다(수정 시각 확인 때문에).
  const [visits, setVisits] = useState<string[]>([]);
  useEffect(() => {
    if (!visits.length || recent.status !== "ready" || recent.saving) return;
    const [next, ...rest] = visits;
    setVisits(rest);
    void visit(next);
  }, [visits, recent.status, recent.saving, visit]);
  return (
    <NavigationRoot
      tabs
      renderScreen={renderScreen}
      onScreenOpened={(id) => setVisits((list) => [...list, id])}
      extensions={(context) => {
        const menus = context.menus as ShellMenuItem[];
        return {
          quickAccess: (
            <PersonalScreenLists
              menus={menus}
              available={context.available}
              mobile={context.mobile}
              collapsed={false}
              onNavigate={context.open}
            />
          ),
          actions: (
            <UserPreferenceTools
              menus={menus}
              mobile={context.mobile}
              available={context.available}
              onNavigate={context.open}
              menuLoading={!context.ready && !context.failed}
              menuError={context.failed ? "메뉴를 불러오지 못했습니다." : undefined}
            />
          ),
          titleAddon: context.screenId ? (
            <FavoriteScreenButton screenId={context.screenId} />
          ) : undefined,
          menuFolded: mobile ? undefined : fold.folded,
          onMenuFoldChange: (folded) => {
            void fold.setFolded(folded);
          },
        };
      }}
    />
  );
}

export function CommonNavigationPage() {
  const preferences = useOptionalUserPreferences();
  if (!preferences) return <NavigationRoot tabs renderScreen={renderScreen} />;
  return <PreferenceNavigation />;
}

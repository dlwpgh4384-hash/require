import { useEffect, useRef, useState } from "react";
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
import { UserPage } from "../users/UserPage";
import { RolePage } from "../roles/RolePage";
import { MenuPage } from "../menusettings/MenuPage";
import {
  MenuTitleActions,
  MenuWorkspaceProvider,
} from "../menusettings/MenuWorkspace";
import {
  RoleTitleActions,
  RoleWorkspaceProvider,
} from "../roles/RoleWorkspace";
import {
  UserRegistrationButton,
  UserWorkspaceProvider,
} from "../users/UserWorkspace";

const renderScreen = (scrnId: string) =>
  scrnId === "UI-23" ? (
    <UserPage />
  ) : scrnId === "UI-24" ? (
    <RolePage />
  ) : scrnId === "UI-25" ? (
    <MenuPage />
  ) : (
    <PlaceholderScreen scrnId={scrnId} />
  );

/** 공통 화면 틀(00) + 공통 기능(00-1): 탭, 즐겨찾기·최근, 도구줄, 메뉴 접기 저장. */
function PreferenceNavigation() {
  const { mobile, tablet } = useViewport();
  const fold = useMenuFoldPreference(mobile, tablet);
  const visit = useRecentScreenVisit();
  const { states } = useUserPreferences();
  const recent = states.RECENT;
  // 최근 본 화면은 한 번에 하나씩 순서대로 저장한다(수정 시각 확인 때문에).
  const [visits, setVisits] = useState<string[]>([]);
  const processingVisits = useRef<string[] | null>(null);
  useEffect(() => {
    if (
      !visits.length ||
      recent.status !== "ready" ||
      recent.saving ||
      processingVisits.current === visits
    )
      return;
    // 저장 응답이 큐 제거보다 먼저 반영되어도 같은 항목을 다시 저장하지 않는다.
    processingVisits.current = visits;
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
          screenActions:
            context.screenId === "UI-23" ? (
              <UserRegistrationButton />
            ) : context.screenId === "UI-24" ? (
              <RoleTitleActions />
            ) : context.screenId === "UI-25" ? (
              <MenuTitleActions />
            ) : undefined,
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
              menuError={
                context.failed ? "메뉴를 불러오지 못했습니다." : undefined
              }
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
  return (
    <UserWorkspaceProvider>
      <RoleWorkspaceProvider>
      <MenuWorkspaceProvider>
        {preferences ? (
          <PreferenceNavigation />
        ) : (
          <NavigationRoot
            tabs
            renderScreen={renderScreen}
            extensions={(context) => ({
              screenActions:
                context.screenId === "UI-23" ? (
                  <UserRegistrationButton />
                ) : context.screenId === "UI-24" ? (
                  <RoleTitleActions />
                ) : context.screenId === "UI-25" ? (
                  <MenuTitleActions />
                ) : undefined,
            })}
          />
        )}
      </MenuWorkspaceProvider>
      </RoleWorkspaceProvider>
    </UserWorkspaceProvider>
  );
}

import { useEffect, useRef, useState } from "react";
import { CashFlowStatementWorkspaceProvider } from "../cashflowstatements/CashFlowStatementWorkspace";
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
import { findScreen } from "./screenRegistry";
import { ProjectWorkspaceProvider } from "../projects/ProjectWorkspace";
import { AssignmentWorkspaceProvider } from "../assignments/AssignmentWorkspace";
import { WithdrawalWorkspaceProvider } from "../withdrawals/WithdrawalWorkspace";
import { ProjectBoardWorkspaceProvider } from "../projectboard/ProjectBoardWorkspace";
import { BillingWorkspaceProvider } from "../bills/BillingWorkspace";
import { PartSettlementWorkspaceProvider } from "../partsettlements/PartSettlementWorkspace";
import { FinanceItemWorkspaceProvider } from "../financeitems/FinanceItemWorkspace";
import { ProfitStructureWorkspaceProvider } from "../profitstructures/ProfitStructureWorkspace";
import { OperatingProfitStatementWorkspaceProvider } from "../operatingprofitstatements/OperatingProfitStatementWorkspace";
import { NetProfitStatementWorkspaceProvider } from "../netprofitstatements/NetProfitStatementWorkspace";
import { BusinessPlanComparisonWorkspaceProvider } from "../commonops/BusinessPlanComparisonWorkspace";
import { CodeWorkspaceProvider } from "../codegroups/CodeWorkspace";
import { ClientPartWorkspaceProvider } from "../clients/ClientPartWorkspace";
import { DeveloperWorkspaceProvider } from "../developers/DeveloperWorkspace";
import { MenuWorkspaceProvider } from "../menusettings/MenuWorkspace";
import { RoleWorkspaceProvider } from "../roles/RoleWorkspace";
import { UserWorkspaceProvider } from "../users/UserWorkspace";
import { MonthCloseWorkspaceProvider } from "../monthcloses/MonthCloseWorkspace";
import { CandidateWorkspaceProvider } from "../candidates/CandidateWorkspace";
import { SalesPipelineWorkspaceProvider } from "../salespipeline/SalesPipelineWorkspace";

// 만든 화면은 화면 목록(screenRegistry) 한 곳에서 고른다. 없으면 「준비 중」.
const renderScreen = (scrnId: string) => {
  const entry = findScreen(scrnId);
  return entry ? <entry.Page /> : <PlaceholderScreen scrnId={scrnId} />;
};
const screenActions = (scrnId: string | null | undefined) => {
  const entry = findScreen(scrnId);
  return entry?.TitleActions ? <entry.TitleActions /> : undefined;
};

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
          screenActions: screenActions(context.screenId),
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
    <CashFlowStatementWorkspaceProvider>
      <BusinessPlanComparisonWorkspaceProvider>
        <UserWorkspaceProvider>
          <RoleWorkspaceProvider>
            <MenuWorkspaceProvider>
              <CodeWorkspaceProvider>
                <ClientPartWorkspaceProvider>
                  <DeveloperWorkspaceProvider>
                    <ProjectWorkspaceProvider>
                      <AssignmentWorkspaceProvider>
                        <WithdrawalWorkspaceProvider>
                          <ProjectBoardWorkspaceProvider>
                            <BillingWorkspaceProvider>
                              <PartSettlementWorkspaceProvider>
                                <FinanceItemWorkspaceProvider>
                                  <ProfitStructureWorkspaceProvider>
                                    <OperatingProfitStatementWorkspaceProvider>
                                      <NetProfitStatementWorkspaceProvider>
                                        <MonthCloseWorkspaceProvider>
                                          <CandidateWorkspaceProvider>
                                            <SalesPipelineWorkspaceProvider>
                                              {preferences ? (
                                                <PreferenceNavigation />
                                              ) : (
                                                <NavigationRoot
                                                  tabs
                                                  renderScreen={renderScreen}
                                                  extensions={(context) => ({
                                                    screenActions: screenActions(
                                                      context.screenId,
                                                    ),
                                                  })}
                                                />
                                              )}
                                            </SalesPipelineWorkspaceProvider>
                                          </CandidateWorkspaceProvider>
                                        </MonthCloseWorkspaceProvider>
                                      </NetProfitStatementWorkspaceProvider>
                                    </OperatingProfitStatementWorkspaceProvider>
                                  </ProfitStructureWorkspaceProvider>
                                </FinanceItemWorkspaceProvider>
                              </PartSettlementWorkspaceProvider>
                            </BillingWorkspaceProvider>
                          </ProjectBoardWorkspaceProvider>
                        </WithdrawalWorkspaceProvider>
                      </AssignmentWorkspaceProvider>
                    </ProjectWorkspaceProvider>
                  </DeveloperWorkspaceProvider>
                </ClientPartWorkspaceProvider>
              </CodeWorkspaceProvider>
            </MenuWorkspaceProvider>
          </RoleWorkspaceProvider>
        </UserWorkspaceProvider>
      </BusinessPlanComparisonWorkspaceProvider>
    </CashFlowStatementWorkspaceProvider>
  );
}

import type { ReactElement } from "react";
import { BillingPage } from "../bills/BillingPage";
import { ProjectPage } from "../projects/ProjectPage";
import { AssignmentPage } from "../assignments/AssignmentPage";
import { WithdrawalPage } from "../withdrawals/WithdrawalPage";
import { ProjectTitleActions } from "../projects/ProjectWorkspace";
import { AssignmentTitleActions } from "../assignments/AssignmentWorkspace";
import { WithdrawalTitleActions } from "../withdrawals/WithdrawalWorkspace";
import { UserPage } from "../users/UserPage";
import { RolePage } from "../roles/RolePage";
import { MenuPage } from "../menusettings/MenuPage";
import { CodePage } from "../codegroups/CodePage";
import { CodeTitleActions } from "../codegroups/CodeWorkspace";
import { CodeMapsPanel } from "../codemaps/CodeMapsPanel";
import { OperatingStandardsPanel } from "../operatingstandards/OperatingStandardsPanel";
import { CodeHistoryPanel } from "../commonops/CodeHistoryPanel";
import { ManpowerComparisonPage } from "../commonops/ManpowerComparisonPage";
import { ProjectBoardPage } from "../projectboard/ProjectBoardPage";
import { ProjectBoardTitleActions } from "../projectboard/ProjectBoardWorkspace";
import {
  ClientPartWorkspace,
  ClientPartTitleActions,
} from "../clients/ClientPartWorkspace";
import { PartPage } from "../parts/PartPage";
import { PartTitleActions } from "../parts/ClientPartWorkspace";
import {
  DeveloperWorkspace,
  DeveloperTitleActions,
} from "../developers/DeveloperWorkspace";
import { MenuTitleActions } from "../menusettings/MenuWorkspace";
import { RoleTitleActions } from "../roles/RoleWorkspace";
import { UserRegistrationButton } from "../users/UserWorkspace";

/** 만든 화면 한 개: 화면 ID, 본문, 제목줄 단추, 본문 맨 바깥 시험 ID, 입력 칸이 있는지. */
export interface ScreenEntry {
  id: string;
  Page: () => ReactElement;
  TitleActions?: () => ReactElement;
  /** 화면 본문 맨 바깥 요소의 data-testid(공통 화면 틀 시험이 이것이 보이는지 본다). */
  rootTestId: string;
  /** 처음 열었을 때 글 입력 칸(textbox)이 있는 화면인지. */
  hasInputs: boolean;
}

/**
 * 만든 업무 화면 목록(한 곳). 공통 화면 틀이 여기서 본문·제목줄 단추를 고르고,
 * 공통 화면 틀 시험도 여기서 만든 화면과 「준비 중」 화면을 나눈다. 새 화면은 여기에 한 줄만 더한다.
 */
export const screenRegistry: readonly ScreenEntry[] = [
  {
    id: "UI-13",
    Page: () => <BillingPage />,
    rootTestId: "billing-page",
    hasInputs: false,
  },
  {
    id: "UI-31",
    Page: () => <ProjectBoardPage />,
    TitleActions: () => <ProjectBoardTitleActions />,
    rootTestId: "project-board-page",

    hasInputs: false,
  },
  {
    id: "UI-11",
    Page: () => <ManpowerComparisonPage />,
    rootTestId: "manpower-comparison-page",
    hasInputs: false,
  },
  {
    id: "UI-01",
    Page: () => <ProjectPage />,
    TitleActions: () => <ProjectTitleActions />,
    rootTestId: "project-page",
    hasInputs: true,
  },
  {
    id: "UI-02",
    Page: () => <DeveloperWorkspace />,
    TitleActions: () => <DeveloperTitleActions />,
    rootTestId: "developer-workspace",
    hasInputs: true,
  },
  {
    id: "UI-12",
    Page: () => <AssignmentPage />,
    TitleActions: () => <AssignmentTitleActions />,
    rootTestId: "assignment-page",
    hasInputs: true,
  },
  {
    id: "UI-05",
    Page: () => <WithdrawalPage />,
    TitleActions: () => <WithdrawalTitleActions />,
    rootTestId: "withdrawal-page",
    hasInputs: false,
  },
  {
    id: "UI-15",
    Page: () => <ClientPartWorkspace partPage={<PartPage />} />,
    TitleActions: () => (
      <ClientPartTitleActions partActions={<PartTitleActions />} />
    ),
    rootTestId: "client-part-workspace",
    hasInputs: true,
  },
  {
    id: "UI-23",
    Page: () => <UserPage />,
    TitleActions: () => <UserRegistrationButton />,
    rootTestId: "user-page",
    hasInputs: true,
  },
  {
    id: "UI-24",
    Page: () => <RolePage />,
    TitleActions: () => <RoleTitleActions />,
    rootTestId: "role-page",
    hasInputs: false,
  },
  {
    id: "UI-25",
    Page: () => <MenuPage />,
    TitleActions: () => <MenuTitleActions />,
    rootTestId: "menu-settings-page",
    hasInputs: false,
  },
  {
    id: "UI-27",
    Page: () => (
      <CodePage
        panels={({ dirty, onDirtyChange, onChanged, historyRevision }) => ({
          maps: (
            <CodeMapsPanel
              dirty={dirty}
              onDirtyChange={onDirtyChange}
              onChanged={onChanged}
            />
          ),
          standards: (
            <OperatingStandardsPanel
              onDirtyChange={onDirtyChange}
              onChanged={onChanged}
            />
          ),
          history: <CodeHistoryPanel refreshKey={historyRevision} />,
        })}
      />
    ),
    TitleActions: () => <CodeTitleActions />,
    rootTestId: "code-page",
    hasInputs: false,
  },
];

const byId = new Map(screenRegistry.map((entry) => [entry.id, entry]));
/** 화면 ID로 만든 화면을 찾는다(없으면 「준비 중」 화면). */
export const findScreen = (id: string | null | undefined) =>
  id ? byId.get(id) : undefined;

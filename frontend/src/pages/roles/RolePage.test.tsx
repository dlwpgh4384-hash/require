import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import type { ReactNode } from "react";
import { RolePage } from "./RolePage";
import { RoleTitleActions, RoleWorkspaceProvider } from "./RoleWorkspace";
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import {
  roleActions,
  type Role,
  type RoleSettings,
  type RoleMenu,
  type RoleImpact,
  type RoleChange,
} from "./roles";

/** 제목줄 단추([변경 이력]·[역할 복사]·[역할 추가])와 본문을 화면 틀처럼 함께 그린다. */
const renderPage = (wrap: (page: ReactNode) => ReactNode = (page) => page) =>
  render(
    <RoleWorkspaceProvider>
      {wrap(
        <>
          <RoleTitleActions />
          <RolePage />
        </>,
      )}
    </RoleWorkspaceProvider>,
  );

let request: ReturnType<typeof vi.fn>;
let roles: Role[];
let settings: Map<string, RoleSettings>;
let impact: RoleImpact;
let failList: boolean;
let failSettings: boolean;
let failSave: boolean;
let failCopy: boolean;
let stopAssigned: boolean;
let drift: boolean;
let failedImpact: boolean;
let slowSettings: boolean;
let release: (() => void) | undefined;
let histories: Map<string, RoleChange[]>;
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data }),
});
const error = (field: string, message: string) => ({
  ok: false,
  status: 400,
  json: async () => ({
    success: false,
    meta: {},
    message,
    errors: [{ field, message }],
  }),
});
const writes = (method?: string) =>
  request.mock.calls.filter((call: unknown[]) => {
    const verb = (call[1] as RequestInit | undefined)?.method;
    return method ? verb === method : verb === "PUT" || verb === "POST";
  });
const menu = (id: string, name: string, flag: "Y" | "N"): RoleMenu => ({
  menuId: id,
  menuName: name,
  screenId: `UI-${id.slice(1)}`,
  groupName: "합성 묶음",
  readYn: flag,
  createYn: flag,
  updateYn: flag,
  deleteYn: flag,
  uploadYn: flag,
  downloadYn: flag,
  cnfmYn: flag,
});
const basicIds = ["ADMIN", "EXEC", "FIN", "SALES_LDR", "PM"];
beforeEach(() => {
  roles = basicIds.map((id, index) => ({
    roleId: id,
    roleName: [
      "시스템 관리자",
      "경영진",
      "재무 담당",
      "영업 파트장",
      "일반 사용자(PM)",
    ][index],
    dataScopeCd: id === "PM" ? "PROJ" : id === "SALES_LDR" ? "PART" : "ALL",
    snstvViewYn: index < 3 ? "Y" : "N",
    useYn: "Y",
    basicRole: true,
  }));
  roles.push({
    roleId: "R-001",
    roleName: "합성 추가 역할",
    dataScopeCd: "PART",
    snstvViewYn: "N",
    useYn: "Y",
    basicRole: false,
  });
  settings = new Map(
    roles.map((role) => [
      role.roleId,
      {
        role: { ...role },
        menuAuths: [
          menu("M028", "권한", role.roleId === "ADMIN" ? "Y" : "N"),
          menu("M024", "월 마감", "N"),
          menu("M011", "파이프라인", "N"),
        ],
        dataScopes: [
          { code: "ALL", name: "전체" },
          { code: "PART", name: "담당 파트" },
          { code: "PROJ", name: "담당 프로젝트" },
        ],
      },
    ]),
  );
  impact = {
    headquartersUserCount: 1,
    projectUserCount: 0,
    affectedUserIds: ["single"],
    preservedPmUserIds: ["pmowned"],
  };
  failList = false;
  failSettings = false;
  failSave = false;
  failCopy = false;
  stopAssigned = false;
  drift = false;
  failedImpact = false;
  slowSettings = false;
  release = undefined;
  histories = new Map([
    [
      "ADMIN",
      [
        {
          changedAt: "2026-11-13T01:00:00Z",
          item: "민감 항목",
          before: "안 보기",
          after: "보기",
          changedBy: "시스템 관리자",
        },
      ],
    ],
  ]);
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/user-options")
      return success({
        roles: roles
          .filter((role) => role.useYn === "Y")
          .map((role) => ({
            roleCd: role.roleId,
            roleName: role.roleName,
            assignedPartScope: role.dataScopeCd === "PART",
            assignedProjectScope: role.dataScopeCd === "PROJ",
          })),
        projects: [],
        userStates: [],
      });
    if (path === "/api/common/roles") {
      if (init?.method === "POST") {
        if (failCopy)
          return error("roleName", "이미 쓰고 있는 역할 이름입니다.");
        const input = JSON.parse(String(init.body));
        const blank = !input.sourceRoleId;
        const source = settings.get(input.sourceRoleId ?? "ADMIN")!;
        const role: Role = {
          ...source.role,
          roleId: "R-002",
          roleName: input.roleName,
          dataScopeCd: input.dataScopeCd,
          snstvViewYn: blank ? "N" : source.role.snstvViewYn,
          basicRole: false,
        };
        const value = {
          ...source,
          role,
          menuAuths: blank
            ? source.menuAuths.map((row) => menu(row.menuId, row.menuName, "N"))
            : source.menuAuths,
        };
        roles.push(role);
        settings.set(role.roleId, value);
        return success(value);
      }
      if (failList) throw new Error("목록 조회 실패");
      return success({ items: roles.filter((role) => role.useYn === "Y") });
    }
    const id = decodeURIComponent(path.split("/")[4]);
    if (path.includes("/change-history"))
      return success({ items: histories.get(id) ?? [] });
    if (path.includes("/change-impact")) {
      if (failedImpact) throw new Error("영향 조회 실패");
      return success(impact);
    }
    if (path.endsWith("/stop")) {
      if (stopAssigned)
        return error(
          "roleId",
          "2명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요.",
        );
      roles = roles.map((role) =>
        role.roleId === id ? { ...role, useYn: "N" } : role,
      );
      return success({ roleId: id, useYn: "N" });
    }
    if (init?.method === "PUT") {
      if (drift) {
        drift = false;
        impact = {
          ...impact,
          headquartersUserCount: 2,
          affectedUserIds: ["single", "newowned"],
        };
        return error(
          "confirmedAffectedUserIds",
          "영향 사용자를 다시 조회하고 확인해 주세요.",
        );
      }
      if (failSave) return error("dataScopeCd", "데이터 범위를 확인해 주세요.");
      const input = JSON.parse(String(init.body));
      const old = settings.get(id)!;
      const value: RoleSettings = {
        ...old,
        role: {
          ...old.role,
          dataScopeCd: input.dataScopeCd,
          snstvViewYn: input.snstvViewYn,
        },
        menuAuths: old.menuAuths.map((row) => ({
          ...row,
          ...input.menuAuths.find(
            (next: { menuId: string }) => next.menuId === row.menuId,
          ),
        })),
      };
      settings.set(id, value);
      return success(value);
    }
    if (failSettings) throw new Error("설정 조회 실패");
    if (slowSettings)
      await new Promise<void>((resolve) => {
        release = resolve;
      });
    return success(settings.get(id));
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
const loaded = async () => {
  await screen.findByTestId("role-settings");
  await waitFor(() =>
    expect(screen.getByTestId("role-copy-open")).toBeEnabled(),
  );
};
const select = async (id: string) => {
  fireEvent.click(screen.getByTestId(`role-tab-${id.toLowerCase()}`));
  await waitFor(() =>
    expect(screen.getByTestId("role-data-scope")).toHaveValue(
      settings.get(id)!.role.dataScopeCd,
    ),
  );
  await waitFor(() =>
    expect(
      screen.getByRole("group", {
        name: `${settings.get(id)!.role.roleName} 설정`,
      }),
    ).toBeInTheDocument(),
  );
};

it("loads persisted tabs and settings with no SELF option or stop control on the five basic roles", async () => {
  renderPage();
  await loaded();
  expect(screen.getByTestId("role-page")).toHaveClass("screen-body");
  expect(screen.getByLabelText("데이터 범위")).toHaveValue("ALL");
  expect(
    screen.queryByRole("option", { name: "본인" }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("role-sensitive")).toBeChecked();
  expect(screen.getByTestId("role-m028-updateyn")).toBeDisabled();
  for (const id of basicIds) {
    await select(id);
    expect(screen.queryByTestId("role-stop")).not.toBeInTheDocument();
  }
  await select("R-001");
  expect(screen.getByTestId("role-stop")).toBeEnabled();
});
it("keeps save absent until settings are actually loaded", async () => {
  slowSettings = true;
  renderPage();
  await screen.findByText("권한 설정을 불러오는 중입니다.");
  expect(screen.queryByTestId("role-save")).not.toBeInTheDocument();
  expect(screen.getByTestId("role-copy-open")).toBeDisabled();
  release!();
  await loaded();
});
it("shows active-role and matrix empty states without invented rows", async () => {
  roles = [];
  renderPage();
  await screen.findByText("활성 역할이 없습니다.");
  expect(screen.queryByTestId("role-save")).not.toBeInTheDocument();
});
it("shows an empty persisted matrix while keeping real settings editable", async () => {
  settings.get("ADMIN")!.menuAuths = [];
  renderPage();
  await loaded();
  expect(screen.getByText("조회할 권한이 없습니다.")).toBeInTheDocument();
  expect(screen.getByTestId("role-save")).toBeEnabled();
});
it("retries list failures without manufacturing tabs", async () => {
  failList = true;
  renderPage();
  await screen.findByTestId("role-list-retry");
  expect(screen.queryByTestId("role-tab-admin")).not.toBeInTheDocument();
  failList = false;
  fireEvent.click(screen.getByTestId("role-list-retry"));
  await loaded();
});
it("retries settings failures and never saves an unread matrix", async () => {
  failSettings = true;
  renderPage();
  await screen.findByTestId("role-settings-retry");
  expect(screen.queryByTestId("role-save")).not.toBeInTheDocument();
  failSettings = false;
  fireEvent.click(screen.getByTestId("role-settings-retry"));
  await loaded();
});
it("saves scope sensitive and action flags without labels or rename and re-reads the selected role", async () => {
  renderPage();
  await loaded();
  fireEvent.click(screen.getByTestId("role-sensitive"));
  fireEvent.click(screen.getByTestId("role-m011-cnfmyn"));
  expect(
    screen.getByText("「시스템 관리자」에 저장하지 않은 변경이 있습니다."),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("role-save"));
  expect(await screen.findByTestId("role-toast")).toHaveTextContent(
    "권한을 저장했습니다. 이 역할 사용자의 다음 요청부터 적용됩니다. 변경 이력에 2줄 남겼습니다.",
  );
  expect(
    screen.queryByText("「시스템 관리자」에 저장하지 않은 변경이 있습니다."),
  ).not.toBeInTheDocument();
  const call = writes("PUT")[0];
  const input = JSON.parse(call[1].body);
  expect(input.snstvViewYn).toBe("N");
  expect(input.confirmedAffectedUserIds).toEqual([]);
  expect(input).not.toHaveProperty("roleId");
  expect(input).not.toHaveProperty("roleName");
  expect(input.menuAuths[0]).not.toHaveProperty("menuName");
  expect(input.menuAuths[0]).not.toHaveProperty("groupName");
  expect(input.menuAuths[2].cnfmYn).toBe("Y");
  await waitFor(() =>
    expect(
      request.mock.calls.filter(
        (call) => call[0] === "/api/common/roles/ADMIN" && !call[1]?.method,
      ).length,
    ).toBeGreaterThan(1),
  );
  expect(screen.getByTestId("role-sensitive")).not.toBeChecked();
});
it("keeps the draft and field errors on failed save", async () => {
  failSave = true;
  renderPage();
  await loaded();
  fireEvent.click(screen.getByTestId("role-sensitive"));
  fireEvent.click(screen.getByTestId("role-save"));
  await screen.findByTestId("role-failure");
  expect(screen.getByTestId("role-sensitive")).not.toBeChecked();
  expect(screen.getByTestId("role-data-scope")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
});
it("allows sensitive changes on every role and permanently locks only leader non-read close actions", async () => {
  renderPage();
  await loaded();
  await select("SALES_LDR");
  expect(screen.getByTestId("role-sensitive")).toBeEnabled();
  for (const [key] of roleActions)
    expect(screen.getByTestId(`role-m024-${key.toLowerCase()}`)).toHaveProperty(
      "disabled",
      key !== "readYn",
    );
  expect(screen.getByTestId("role-m028-updateyn")).toBeEnabled();
  fireEvent.click(screen.getByTestId("role-m024-readyn"));
  fireEvent.click(screen.getByTestId("role-save"));
  await screen.findByTestId("role-toast");
  expect(writes("PUT")).toHaveLength(1);
});
it("shows cleanup counts and cancellation makes zero PUT requests and preserves the draft", async () => {
  renderPage();
  await loaded();
  await select("R-001");
  fireEvent.change(screen.getByTestId("role-data-scope"), {
    target: { value: "ALL" },
  });
  fireEvent.click(screen.getByTestId("role-save"));
  await screen.findByText("1명의 본사 선택이 지워집니다.");
  fireEvent.click(screen.getByTestId("role-impact-cancel"));
  expect(writes("PUT")).toHaveLength(0);
  expect(settings.get("R-001")!.role.dataScopeCd).toBe("PART");
  expect(screen.getByTestId("role-data-scope")).toHaveValue("ALL");
});
it("confirmed cleanup sends exactly previewed user IDs and preserves PM guidance", async () => {
  impact = { ...impact, headquartersUserCount: 0, projectUserCount: 1 };
  renderPage();
  await loaded();
  await select("PM");
  fireEvent.change(screen.getByTestId("role-data-scope"), {
    target: { value: "ALL" },
  });
  fireEvent.click(screen.getByTestId("role-save"));
  await screen.findByText("1명의 담당 프로젝트가 지워집니다.");
  expect(
    screen.getByText(
      "PM 계정의 담당 프로젝트와 다른 역할로 유지되는 범위는 남습니다.",
    ),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("role-impact-save"));
  await screen.findByTestId("role-toast");
  expect(JSON.parse(writes("PUT")[0][1].body).confirmedAffectedUserIds).toEqual(
    ["single"],
  );
});
it("drift re-fetches impact and requires a new confirmation, not an automatic retry PUT", async () => {
  drift = true;
  renderPage();
  await loaded();
  await select("R-001");
  fireEvent.change(screen.getByTestId("role-data-scope"), {
    target: { value: "ALL" },
  });
  fireEvent.click(screen.getByTestId("role-save"));
  await screen.findByTestId("role-impact-dialog");
  fireEvent.click(screen.getByTestId("role-impact-save"));
  await screen.findByText("2명의 본사 선택이 지워집니다.");
  expect(writes("PUT")).toHaveLength(1);
  fireEvent.click(screen.getByTestId("role-impact-save"));
  await screen.findByTestId("role-toast");
  expect(JSON.parse(writes("PUT")[1][1].body).confirmedAffectedUserIds).toEqual(
    ["single", "newowned"],
  );
});
it("a failed impact query preserves input and performs no PUT", async () => {
  failedImpact = true;
  renderPage();
  await loaded();
  await select("R-001");
  fireEvent.change(screen.getByTestId("role-data-scope"), {
    target: { value: "ALL" },
  });
  fireEvent.click(screen.getByTestId("role-save"));
  await screen.findByTestId("role-failure");
  expect(writes("PUT")).toHaveLength(0);
  expect(screen.getByTestId("role-data-scope")).toHaveValue("ALL");
});
it("scope changes with no actual cleanup save without a new destructive confirmation", async () => {
  impact = {
    headquartersUserCount: 0,
    projectUserCount: 0,
    affectedUserIds: [],
    preservedPmUserIds: [],
  };
  renderPage();
  await loaded();
  await select("R-001");
  fireEvent.change(screen.getByTestId("role-data-scope"), {
    target: { value: "ALL" },
  });
  fireEvent.click(screen.getByTestId("role-save"));
  await screen.findByTestId("role-toast");
  expect(screen.queryByTestId("role-impact-dialog")).not.toBeInTheDocument();
});
it("copy (시안): picks the role to copy, sends its scope and selects the new tab", async () => {
  renderPage();
  await loaded();
  fireEvent.click(screen.getByTestId("role-copy-open"));
  expect(screen.getByRole("dialog", { name: "역할 복사" })).toBeInTheDocument();
  expect(screen.getByTestId("role-copy-source")).toHaveValue("ADMIN");
  expect(
    screen.getByText("권한 표·데이터 범위·민감 항목을 그대로 복사합니다."),
  ).toBeInTheDocument();
  expect(screen.queryByTestId("role-copy-scope")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("role-copy-save"));
  await screen.findByText("새 역할 이름을 1~50자로 입력해 주세요.");
  expect(writes("POST")).toHaveLength(0);
  fireEvent.change(screen.getByTestId("role-copy-source"), {
    target: { value: "PM" },
  });
  fireEvent.change(screen.getByTestId("role-copy-name"), {
    target: { value: " 합성 새 역할 " },
  });
  fireEvent.click(screen.getByTestId("role-copy-save"));
  await waitFor(() =>
    expect(screen.getByTestId("role-tab-r-002")).toHaveAttribute(
      "aria-selected",
      "true",
    ),
  );
  expect(JSON.parse(writes("POST")[0][1].body)).toEqual({
    sourceRoleId: "PM",
    roleName: " 합성 새 역할 ",
    dataScopeCd: "PROJ",
  });
  expect(screen.queryByTestId("role-copy-dialog")).not.toBeInTheDocument();
});
it("add (시안): name and scope only, starts with every menu 「권한 없음」", async () => {
  renderPage();
  await loaded();
  fireEvent.click(screen.getByTestId("role-add-open"));
  expect(screen.getByRole("dialog", { name: "역할 추가" })).toBeInTheDocument();
  expect(screen.getByTestId("role-copy-scope")).toHaveValue("PROJ");
  expect(
    screen.getByText("새 역할은 모든 메뉴가 「권한 없음」으로 시작합니다."),
  ).toBeInTheDocument();
  expect(screen.queryByTestId("role-copy-source")).not.toBeInTheDocument();
  fireEvent.change(screen.getByTestId("role-copy-name"), {
    target: { value: "프로젝트 지원" },
  });
  fireEvent.click(screen.getByTestId("role-copy-save"));
  await waitFor(() =>
    expect(screen.getByTestId("role-tab-r-002")).toHaveAttribute(
      "aria-selected",
      "true",
    ),
  );
  expect(JSON.parse(writes("POST")[0][1].body)).toEqual({
    roleName: "프로젝트 지원",
    dataScopeCd: "PROJ",
  });
  await waitFor(() =>
    expect(screen.getByTestId("role-m028-readyn")).not.toBeChecked(),
  );
});
it("title row shows 변경 이력 (n) and opens the history list (시안)", async () => {
  renderPage();
  await loaded();
  await waitFor(() =>
    expect(screen.getByTestId("role-history-open")).toHaveTextContent(
      "변경 이력 (1)",
    ),
  );
  fireEvent.click(screen.getByTestId("role-history-open"));
  const dialog = screen.getByRole("dialog", {
    name: "시스템 관리자 · 권한 변경 이력 (1)",
  });
  expect(within(dialog).getByTestId("role-history-row-0")).toHaveTextContent(
    "민감 항목",
  );
  fireEvent.click(screen.getByTestId("role-history-close"));
  await select("EXEC");
  await waitFor(() =>
    expect(screen.getByTestId("role-history-open")).toHaveTextContent(
      "변경 이력 (0)",
    ),
  );
  fireEvent.click(screen.getByTestId("role-history-open"));
  expect(screen.getByText("아직 바꾼 기록이 없습니다.")).toBeInTheDocument();
});
it("matrix shows group rows and screen numbers, and notes under the table (시안)", async () => {
  renderPage();
  await loaded();
  expect(
    screen.getByRole("rowheader", { name: /권한 UI-028/ }),
  ).toBeInTheDocument();
  expect(screen.getAllByText("합성 묶음")).toHaveLength(1);
  expect(
    screen.getByText(/역할이 여러 개인 사용자는 넓은 쪽\(합집합\) 권한을/),
  ).toBeInTheDocument();
});
it("Ctrl+S saves, and saving with no change says 바꾼 내용이 없습니다", async () => {
  renderPage();
  await loaded();
  fireEvent.click(screen.getByTestId("role-save"));
  expect(await screen.findByTestId("role-toast")).toHaveTextContent(
    "바꾼 내용이 없습니다.",
  );
  expect(writes("PUT")).toHaveLength(0);
  fireEvent.click(screen.getByTestId("role-sensitive"));
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(writes("PUT")).toHaveLength(1));
});
it("copy duplicate errors preserve the entered name and modal", async () => {
  failCopy = true;
  renderPage();
  await loaded();
  fireEvent.click(screen.getByTestId("role-copy-open"));
  fireEvent.change(screen.getByTestId("role-copy-name"), {
    target: { value: "경영진" },
  });
  fireEvent.click(screen.getByTestId("role-copy-save"));
  await waitFor(() =>
    expect(screen.getByTestId("role-copy-name")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByTestId("role-copy-name")).toHaveValue("경영진");
});
it("copy cancel reuses dirty confirmation without creating a role", async () => {
  renderPage();
  await loaded();
  fireEvent.click(screen.getByTestId("role-copy-open"));
  fireEvent.change(screen.getByTestId("role-copy-name"), {
    target: { value: "초안" },
  });
  fireEvent.click(screen.getByTestId("role-copy-cancel"));
  const confirmation = await screen.findByTestId("inline-confirmation");
  fireEvent.click(within(confirmation).getByTestId("confirmation-cancel"));
  expect(screen.getByTestId("role-copy-name")).toHaveValue("초안");
  fireEvent.click(screen.getByTestId("role-copy-cancel"));
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(screen.queryByTestId("role-copy-dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("stop runs directly, refetches active tabs and user options, without a stop confirmation", async () => {
  renderPage();
  await loaded();
  await select("R-001");
  fireEvent.click(screen.getByTestId("role-stop"));
  await waitFor(() =>
    expect(screen.queryByTestId("role-tab-r-001")).not.toBeInTheDocument(),
  );
  expect(writes("POST")[0][0]).toBe("/api/common/roles/R-001/stop");
  await waitFor(() =>
    expect(
      request.mock.calls.some((call) => call[0] === "/api/common/user-options"),
    ).toBe(true),
  );
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
});
it("assigned-role stop displays exact count and user-screen guidance and keeps its active tab", async () => {
  stopAssigned = true;
  renderPage();
  await loaded();
  await select("R-001");
  fireEvent.click(screen.getByTestId("role-stop"));
  await screen.findByTestId("role-failure");
  expect(screen.getByTestId("role-failure")).toHaveTextContent(
    "2명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요.",
  );
  expect(screen.getByTestId("role-tab-r-001")).toBeInTheDocument();
});
it("dirty cancel and role navigation use existing confirmation and notify the shell", async () => {
  const dirty = vi.fn();
  renderPage((page) => (
    <ScreenDirtyContext.Provider value={dirty}>
      {page}
    </ScreenDirtyContext.Provider>
  ));
  await loaded();
  fireEvent.click(screen.getByTestId("role-sensitive"));
  await waitFor(() => expect(dirty).toHaveBeenCalledWith("UI-24", true));
  fireEvent.click(screen.getByTestId("role-tab-exec"));
  const confirmation = await screen.findByTestId("role-dirty-confirm");
  fireEvent.click(within(confirmation).getByTestId("confirmation-cancel"));
  expect(screen.getByTestId("role-sensitive")).not.toBeChecked();
  fireEvent.click(screen.getByTestId("role-cancel"));
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(screen.getByTestId("role-sensitive")).toBeChecked();
  expect(writes()).toHaveLength(0);
});

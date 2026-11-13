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
import { CodePage } from "./CodePage";
import { CodeTitleActions, CodeWorkspaceProvider } from "./CodeWorkspace";
import type { CodeGroup, CodeItem } from "./codeGroups";
import { CODE_OPTIONS_CHANGED } from "./codeGroups";
import { MemoryRouter } from "react-router-dom";
import { CommonNavigationPage } from "../common/CommonNavigationPage";

let groups: CodeGroup[];
let rows: Record<string, CodeItem[]>;
let request: ReturnType<typeof vi.fn>;
let failRead = false;
let failSave = false;
let failRefresh = false;
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const error = () => ({
  ok: false,
  status: 400,
  json: async () => ({
    success: false,
    meta: {},
    message: "이름을 확인해 주세요.",
    errors: [{ field: "codeName", message: "이름을 확인해 주세요." }],
  }),
});
const writes = () =>
  request.mock.calls.filter((call: unknown[]) =>
    ["POST", "PUT"].includes(
      (call[1] as RequestInit | undefined)?.method ?? "",
    ),
  );
beforeEach(() => {
  failRead = false;
  failSave = false;
  failRefresh = false;
  groups = [
    {
      groupId: "GENERAL",
      groupName: "일반 그룹",
      sysCodeYn: "N",
      valCodeYn: "N",
      codeCount: 2,
    },
    {
      groupId: "SYSTEM",
      groupName: "시스템 그룹",
      sysCodeYn: "Y",
      valCodeYn: "N",
      codeCount: 1,
    },
    {
      groupId: "ALWNC",
      groupName: "수당코드",
      sysCodeYn: "N",
      valCodeYn: "Y",
      codeCount: 1,
    },
    ...["JOB", "SKILL", "PROJ_STEP", "BANK", "RSM_LABEL", "RSM_JOB_KW"].map(
      (groupId) => ({
        groupId,
        groupName: groupId,
        sysCodeYn: "N" as const,
        valCodeYn: "N" as const,
        codeCount: 0,
      }),
    ),
  ];
  rows = {
    GENERAL: [
      { code: "ONE", codeName: "첫 이름", sortOrd: 1, useYn: "Y" },
      { code: "TWO", codeName: "둘째 이름", sortOrd: 2, useYn: "N" },
    ],
    SYSTEM: [{ code: "FIXED", codeName: "고정 이름", sortOrd: 1, useYn: "Y" }],
    ALWNC: [
      {
        code: "TEAM_FEE",
        codeName: "팀비",
        sortOrd: 1,
        useYn: "Y",
        codeVal: 50000,
        unitNm: "원",
      },
    ],
    JOB: [],
    SKILL: [],
    PROJ_STEP: [],
    BANK: [],
    RSM_LABEL: [],
    RSM_JOB_KW: [],
  };
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (init?.method === "POST" || init?.method === "PUT") {
      if (failSave) return error();
      if (path === "/api/common/code-groups") {
        const input = JSON.parse(String(init.body));
        const group = {
          ...input,
          sysCodeYn: "N",
          valCodeYn: "N",
          codeCount: 0,
        };
        groups.push(group);
        rows[group.groupId] = [];
        return success(group);
      }
      const match = path.match(
        /^\/api\/common\/code-groups\/([^/]+)\/codes(?:\/([^/]+)\/delete)?$/,
      );
      if (!match) throw new Error("예상하지 않은 요청");
      const id = decodeURIComponent(match[1]);
      if (match[2]) {
        rows[id] = rows[id].filter(
          (row) => row.code !== decodeURIComponent(match[2]),
        );
        return success({ groupId: id, code: match[2], delYn: "Y" });
      }
      const input = JSON.parse(String(init.body));
      if (init.method === "PUT") {
        rows[id] = rows[id].map(
          (row) =>
            input.items.find((item: CodeItem) => item.code === row.code) ?? row,
        );
        if (failRefresh) failRead = true;
        return success({ items: rows[id] });
      }
      const row = {
        ...input,
        code: input.code.toUpperCase(),
        sortOrd: 3,
        useYn: "Y",
        ...(id === "ALWNC" ? { codeVal: input.codeVal ?? 0 } : {}),
      };
      rows[id].push(row);
      return success(row);
    }
    if (failRead) throw new Error("조회 실패");
    if (path === "/api/common/code-groups") return success({ items: groups });
    const match = path.match(/^\/api\/common\/code-groups\/([^/]+)\/codes$/);
    if (match) return success({ items: rows[decodeURIComponent(match[1])] });
    throw new Error("예상하지 않은 요청");
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
const selectGroup = async (id: string) => {
  fireEvent.click(screen.getByTestId(`code-group-${id.toLowerCase()}`));
  await waitFor(() =>
    expect(
      screen.getByTestId(`code-group-${id.toLowerCase()}`),
    ).toHaveAttribute("aria-pressed", "true"),
  );
  await waitFor(() =>
    expect(
      screen.queryByText("코드를 불러오는 중입니다."),
    ).not.toBeInTheDocument(),
  );
};
// 시안: 처음에는 맨 위 시스템 그룹이 선택된다. 업무 시험은 GENERAL을 골라서 시작한다.
const ready = async () => {
  render(<CodePage />);
  await screen.findByLabelText("FIXED 이름");
  await selectGroup("GENERAL");
  await screen.findByLabelText("ONE 이름");
};
it("네 탭과 실제 그룹 수·자물쇠·일반 네 칸을 표시하고 날짜나 순서 편집을 만들지 않는다", async () => {
  await ready();
  expect(screen.getAllByRole("tab")).toHaveLength(4);
  expect(screen.getByRole("img", { name: "시스템 그룹" })).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: "순서" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: "사용" }),
  ).toBeInTheDocument();
  expect(screen.queryByText("적용 시작일")).not.toBeInTheDocument();
  expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
  expect(screen.getByTestId("code-group-list")).toHaveClass("code-group-list");
});
it("시스템 이름만 편집하고 추가·삭제·사용변경을 허용하지 않는다", async () => {
  await ready();
  await selectGroup("SYSTEM");
  expect(screen.getByTestId("banner-lock")).toHaveTextContent(
    "계산 규칙에 쓰는 시스템 코드입니다. 이름만 바꿀 수 있고 추가·삭제·사용 끄기는 할 수 없습니다.",
  );
  expect(screen.getByLabelText("FIXED 사용")).toBeDisabled();
  expect(screen.queryByTestId("code-add")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("FIXED 삭제")).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("FIXED 이름"), {
    target: { value: "변경 이름" },
  });
  fireEvent.click(screen.getByTestId("code-save"));
  await waitFor(() => expect(rows.SYSTEM[0].codeName).toBe("변경 이름"));
});
it("이름과 여러 사용 체크를 한 번 저장하고 다시 조회하며 옵션 소비자에게 알린다", async () => {
  const changed = vi.fn();
  window.addEventListener(CODE_OPTIONS_CHANGED, changed);
  await ready();
  fireEvent.change(screen.getByLabelText("ONE 이름"), {
    target: { value: "저장 이름" },
  });
  fireEvent.click(screen.getByLabelText("ONE 사용"));
  fireEvent.click(screen.getByLabelText("TWO 사용"));
  fireEvent.click(screen.getByTestId("code-save"));
  await waitFor(() =>
    expect(screen.getByText("공통코드 2건을 저장했습니다. 선택 목록에 바로 반영됩니다.")).toBeInTheDocument(),
  );
  expect(writes()).toHaveLength(1);
  expect(writes()[0][1].method).toBe("PUT");
  expect(rows.GENERAL.map((row) => row.useYn)).toEqual(["N", "Y"]);
  expect(changed).toHaveBeenCalledTimes(1);
  window.removeEventListener(CODE_OPTIONS_CHANGED, changed);
});
it("dirty 삭제는 API와 확인창 없이 안내하고 변경을 유지한다", async () => {
  await ready();
  fireEvent.change(screen.getByLabelText("ONE 이름"), {
    target: { value: "미저장" },
  });
  fireEvent.click(screen.getByLabelText("ONE 삭제"));
  expect(
    screen.getByText("먼저 변경 내용을 저장하거나 취소해 주세요."),
  ).toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
  expect(screen.getByLabelText("ONE 이름")).toHaveValue("미저장");
  fireEvent.click(screen.getByTestId("code-cancel"));
  expect(screen.getByLabelText("ONE 이름")).toHaveValue("첫 이름");
  fireEvent.click(screen.getByLabelText("ONE 삭제"));
  expect(screen.getByRole("dialog")).toBeInTheDocument();
});
it("삭제 확인 취소는 쓰지 않고 확인 성공은 표에서 제외한다", async () => {
  await ready();
  fireEvent.click(screen.getByLabelText("ONE 삭제"));
  fireEvent.click(screen.getByTestId("code-dialog-cancel"));
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByLabelText("ONE 삭제"));
  fireEvent.click(screen.getByTestId("code-dialog-submit"));
  await waitFor(() =>
    expect(screen.queryByLabelText("ONE 이름")).not.toBeInTheDocument(),
  );
  expect(rows.GENERAL).toHaveLength(1);
});
it("삭제 거부는 확인창과 기존 행을 유지한다", async () => {
  await ready();
  failSave = true;
  fireEvent.click(screen.getByLabelText("ONE 삭제"));
  fireEvent.click(screen.getByTestId("code-dialog-submit"));
  expect(
    await within(screen.getByRole("dialog")).findByText(
      "이름을 확인해 주세요.",
    ),
  ).toBeInTheDocument();
  expect(rows.GENERAL).toHaveLength(2);
});
it("빈 이름은 저장 요청 없이 필드 오류를 표시한다", async () => {
  await ready();
  fireEvent.change(screen.getByLabelText("ONE 이름"), {
    target: { value: " " },
  });
  fireEvent.click(screen.getByTestId("code-save"));
  expect(
    screen.getAllByText("이름을 1~100자로 입력해 주세요.").length,
  ).toBeGreaterThan(0);
  expect(writes()).toHaveLength(0);
});
it("서버 필드 오류에서 draft를 유지한다", async () => {
  await ready();
  failSave = true;
  fireEvent.change(screen.getByLabelText("ONE 이름"), {
    target: { value: "보존 이름" },
  });
  fireEvent.click(screen.getByTestId("code-save"));
  await waitFor(() =>
    expect(screen.getByTestId("code-failure")).toHaveTextContent(
      "이름을 확인해 주세요.",
    ),
  );
  expect(screen.getByLabelText("ONE 이름")).toHaveValue("보존 이름");
});
it("저장 후 재조회 실패는 중복 저장을 막고 재조회로 회복한다", async () => {
  await ready();
  failRefresh = true;
  fireEvent.change(screen.getByLabelText("ONE 이름"), {
    target: { value: "저장됨" },
  });
  fireEvent.click(screen.getByTestId("code-save"));
  await screen.findByText(
    "저장은 완료되었으나 다시 읽지 못했습니다. 재조회해 주세요.",
  );
  expect(screen.getByTestId("code-save")).toBeDisabled();
  failRead = false;
  fireEvent.click(screen.getByTestId("code-retry"));
  await waitFor(() =>
    expect(screen.getByTestId("code-save")).not.toBeDisabled(),
  );
  expect(writes()).toHaveLength(1);
});
it("그룹 이동 취소는 미저장 값을 유지하고 버리기는 서버 스냅샷으로 복원한다", async () => {
  await ready();
  fireEvent.change(screen.getByLabelText("ONE 이름"), {
    target: { value: "draft" },
  });
  fireEvent.click(screen.getByTestId("code-group-system"));
  fireEvent.click(screen.getByTestId("code-discard-cancel"));
  expect(screen.getByLabelText("ONE 이름")).toHaveValue("draft");
  fireEvent.click(screen.getByTestId("code-group-system"));
  fireEvent.click(screen.getByTestId("code-discard-proceed"));
  await screen.findByLabelText("FIXED 이름");
  expect(writes()).toHaveLength(0);
});
it.each([
  ["JOB", ["관련 직무", "견적 기준 단가(원)"]],
  ["SKILL", ["필요한 직무"]],
  ["PROJ_STEP", ["비중(%)", "적용 계약유형"]],
  ["BANK", ["은행 코드"]],
  ["RSM_LABEL", ["넣을 칸"]],
  ["RSM_JOB_KW", ["직무"]],
])("%s 그룹은 해당 추가값만 필수 입력한다", async (id, labels) => {
  await ready();
  await selectGroup(String(id));
  fireEvent.click(screen.getByTestId("code-add"));
  const dialog = within(screen.getByRole("dialog"));
  for (const label of labels)
    expect(dialog.getByLabelText(`new ${label}`)).toBeInTheDocument();
  fireEvent.change(dialog.getByLabelText("새 코드"), {
    target: { value: "new_code" },
  });
  fireEvent.change(dialog.getByLabelText("새 코드 이름"), {
    target: { value: "신규" },
  });
  fireEvent.click(dialog.getByTestId("code-dialog-submit"));
  expect(writes()).toHaveLength(0);
});
it("업무 코드 추가는 소문자를 보내고 재조회 대문자를 표시한다", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("code-add"));
  fireEvent.change(screen.getByLabelText("새 코드"), {
    target: { value: "new_code" },
  });
  fireEvent.change(screen.getByLabelText("새 코드 이름"), {
    target: { value: "새 항목" },
  });
  fireEvent.click(screen.getByTestId("code-dialog-submit"));
  await screen.findByLabelText("NEW_CODE 이름");
  expect(writes()).toHaveLength(1);
});
it("수당은 네 칸이며 비운 값은 0으로 저장한다", async () => {
  await ready();
  await selectGroup("ALWNC");
  expect(
    screen.getByRole("columnheader", { name: "단위" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "순서" }),
  ).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("TEAM_FEE 값"), {
    target: { value: "" },
  });
  fireEvent.click(screen.getByTestId("code-save"));
  await waitFor(() => expect(rows.ALWNC[0].codeVal).toBe(0));
});
it("새 그룹 등록 후 선택과 제목에 ID·이름을 표시한다", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("code-group-add"));
  fireEvent.change(screen.getByLabelText("그룹 ID"), {
    target: { value: "NEW_GROUP" },
  });
  fireEvent.change(screen.getByLabelText("그룹 이름"), {
    target: { value: "새 그룹" },
  });
  fireEvent.click(screen.getByTestId("code-dialog-submit"));
  await waitFor(() =>
    expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent(
      "새 그룹 NEW_GROUP",
    ),
  );
  expect(screen.queryByLabelText("그룹 ID")).not.toBeInTheDocument();
});
it("그룹 형식 검증과 창 취소는 서버에 쓰지 않는다", async () => {
  await ready();
  fireEvent.click(screen.getByTestId("code-group-add"));
  fireEvent.change(screen.getByLabelText("그룹 ID"), {
    target: { value: "1BAD" },
  });
  fireEvent.change(screen.getByLabelText("그룹 이름"), {
    target: { value: "새 그룹" },
  });
  fireEvent.click(screen.getByTestId("code-dialog-submit"));
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("code-dialog-cancel"));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("빈 목록과 조회 실패를 구별하고 재시도한다", async () => {
  failRead = true;
  render(<CodePage />);
  await screen.findByText("조회 실패");
  expect(
    screen.queryByText("등록된 코드 그룹이 없습니다."),
  ).not.toBeInTheDocument();
  failRead = false;
  groups = [];
  fireEvent.click(screen.getByTestId("code-retry"));
  await screen.findByText("등록된 코드 그룹이 없습니다.");
});
it("다른 단계 패널은 네 탭 슬롯으로 렌더링하며 수당 탭을 추가하지 않는다", async () => {
  render(
    <CodePage
      panels={{
        maps: <p>대응 업무</p>,
        standards: <p>운영 업무</p>,
        history: <p>이력 업무</p>,
      }}
    />,
  );
  await screen.findByLabelText("FIXED 이름");
  fireEvent.click(screen.getByRole("tab", { name: "변경 이력" }));
  expect(screen.getByText("이력 업무")).toBeVisible();
  expect(screen.getAllByRole("tab")).toHaveLength(4);
  expect(screen.queryByRole("tab", { name: "수당" })).not.toBeInTheDocument();
});
it("작업 공간 제목줄에서 등록 창을 열고 본문 단추를 중복 표시하지 않는다", async () => {
  render(
    <CodeWorkspaceProvider>
      <CodeTitleActions />
      <CodePage />
    </CodeWorkspaceProvider>,
  );
  await screen.findByLabelText("FIXED 이름");
  expect(screen.queryByTestId("code-group-add")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("code-group-title-add"));
  expect(
    screen.getByRole("dialog", { name: "코드 그룹 추가" }),
  ).toBeInTheDocument();
});
it("시안대로 시스템 그룹을 위에 두고 자물쇠만, 업무 그룹은 코드 수를 보인다", async () => {
  await ready();
  const names = Array.from(
    screen.getByTestId("code-group-list").querySelectorAll("button"),
  ).map((button) => button.dataset.testid);
  expect(names[0]).toBe("code-group-system");
  expect(
    within(screen.getByTestId("code-group-system")).getByRole("img", {
      name: "시스템 그룹",
    }),
  ).toBeInTheDocument();
  expect(screen.getByTestId("code-group-general")).toHaveTextContent("2");
  expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent(
    "GENERAL · 2개",
  );
  expect(
    screen.getByRole("columnheader", { name: "삭제" }),
  ).toBeInTheDocument();
  expect(screen.getByTestId("list-range")).toHaveTextContent("1-2 / 2건");
});
it("제목줄 단추는 탭과 그룹에 따라 그룹 추가·수당코드 추가·추가·없음으로 바뀐다", async () => {
  render(
    <CodeWorkspaceProvider>
      <CodeTitleActions />
      <CodePage panels={{ maps: <p>대응 업무</p>, standards: <p>운영</p> }} />
    </CodeWorkspaceProvider>,
  );
  await screen.findByLabelText("FIXED 이름");
  expect(screen.getByTestId("code-group-title-add")).toHaveTextContent(
    "그룹 추가",
  );
  await selectGroup("ALWNC");
  await waitFor(() =>
    expect(screen.getByTestId("code-title-add")).toHaveTextContent(
      "수당코드 추가",
    ),
  );
  expect(
    screen.queryByRole("columnheader", { name: "삭제" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "엑셀 표기 대응" }));
  await waitFor(() =>
    expect(screen.getByTestId("code-map-title-add")).toHaveTextContent("추가"),
  );
  fireEvent.click(screen.getByRole("tab", { name: "운영 기준값" }));
  await waitFor(() =>
    expect(screen.queryByTestId("code-map-title-add")).not.toBeInTheDocument(),
  );
  expect(screen.queryByTestId("code-group-title-add")).not.toBeInTheDocument();
});
it.each([false, true])(
  "UI-27 직접 주소의 휴대폰=%s 접근은 기존 PC 전용 가드를 따른다",
  async (mobile) => {
    vi.stubGlobal(
      "matchMedia",
      vi.fn(() => ({
        matches: mobile,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    );
    const featureRequest = request;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (path: string, init?: RequestInit) => {
        if (path === "/api/common/menus")
          return {
            ok: true,
            status: 200,
            json: async () => ({
              success: true,
              meta: {
                screenAccess: [
                  { scrnId: "UI-27", available: true, pcOnlyYn: "Y" },
                ],
              },
              data: [
                {
                  menuId: "OWNED_CODE",
                  upperMenuId: null,
                  menuNm: "공통코드",
                  scrnId: "UI-27",
                  menuLvl: 1,
                  sortOrd: 1,
                  pcOnlyYn: "Y",
                  iconNm: null,
                },
              ],
            }),
          };
        return featureRequest(path, init);
      }),
    );
    render(
      <MemoryRouter initialEntries={["/screens/UI-27"]}>
        <CommonNavigationPage />
      </MemoryRouter>,
    );
    if (mobile) {
      expect(await screen.findByText(/PC에서/)).toBeInTheDocument();
      expect(screen.queryByTestId("code-page")).not.toBeInTheDocument();
      expect(featureRequest).not.toHaveBeenCalled();
    } else {
      expect(await screen.findByTestId("code-page")).toBeVisible();
      await screen.findByLabelText("FIXED 이름");
      expect(screen.getByTestId("code-group-title-add")).toBeEnabled();
    }
  },
);

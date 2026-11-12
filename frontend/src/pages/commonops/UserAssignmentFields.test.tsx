import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  UserAssignmentFields,
  UserAssignmentOptions,
  type UserAssignmentValue,
} from "./UserAssignmentFields";
import {
  getCommonUserOptions,
  userOptionsError,
  type CommonUserOptionsResult,
} from "./userOptions";

function options(): CommonUserOptionsResult {
  return {
    roles: [
      {
        roleCd: "ADMIN",
        roleName: "시스템 관리자",
        assignedProjectScope: false,
      },
      {
        roleCd: "CUSTOM_ROLE",
        roleName: "담당 프로젝트 사용자",
        assignedProjectScope: true,
      },
      { roleCd: "OTHER", roleName: "다른 역할", assignedProjectScope: false },
    ],
    projects: [
      { projectId: "closed-project", projectName: "완료 프로젝트" },
      { projectId: "active-project", projectName: "진행 프로젝트" },
    ],
    userStates: [{ code: "LOCK", name: "잠김" }],
  };
}
const emptyValue: UserAssignmentValue = {
  roleCds: [],
  headquarters: false,
  projectIds: [],
};
function Harness({
  remote = false,
  initial = emptyValue,
}: {
  remote?: boolean;
  initial?: UserAssignmentValue;
}) {
  const [value, setValue] = useState(initial);
  return (
    <>
      {remote ? (
        <UserAssignmentOptions value={value} onChange={setValue} />
      ) : (
        <UserAssignmentFields
          options={options()}
          value={value}
          onChange={setValue}
        />
      )}
      <output data-testid="assignment-value">{JSON.stringify(value)}</output>
    </>
  );
}
function response(data = options()) {
  return {
    ok: true,
    status: 200,
    json: async () => ({ success: true, meta: {}, data }),
  };
}
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  request = vi.fn(async () => response());
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("사용자 역할·담당 선택", () => {
  it("DB 역할을 표시하고 담당 프로젝트 역할을 선택해야 프로젝트를 표시한다", () => {
    render(<Harness />);
    expect(
      screen.getByRole("checkbox", { name: "시스템 관리자" }),
    ).not.toBeChecked();
    expect(
      screen.queryByRole("group", { name: "담당 프로젝트" }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "시스템 관리자" }));
    expect(
      screen.queryByRole("group", { name: "담당 프로젝트" }),
    ).not.toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("checkbox", { name: "담당 프로젝트 사용자" }),
    );
    const projects = screen.getByRole("group", { name: "담당 프로젝트" });
    expect(
      within(projects).getByRole("checkbox", { name: "완료 프로젝트" }),
    ).toBeVisible();
    fireEvent.click(
      within(projects).getByRole("checkbox", { name: "진행 프로젝트" }),
    );
    fireEvent.click(
      within(projects).getByRole("checkbox", { name: "완료 프로젝트" }),
    );
    expect(
      JSON.parse(screen.getByTestId("assignment-value").textContent!),
    ).toEqual({
      roleCds: ["ADMIN", "CUSTOM_ROLE"],
      headquarters: false,
      projectIds: ["active-project", "closed-project"],
    });
    fireEvent.click(
      within(projects).getByRole("checkbox", { name: "진행 프로젝트" }),
    );
    expect(
      JSON.parse(screen.getByTestId("assignment-value").textContent!)
        .projectIds,
    ).toEqual(["closed-project"]);
  });

  it("여러 역할의 합집합으로 적격성을 판정하고 역할 이름으로 추측하지 않는다", () => {
    const data = options();
    data.roles[0].roleName = "프로젝트 PM";
    const { rerender } = render(
      <UserAssignmentFields
        options={data}
        value={{ ...emptyValue, roleCds: ["ADMIN"] }}
        onChange={vi.fn()}
      />,
    );
    expect(
      screen.queryByRole("group", { name: "담당 프로젝트" }),
    ).not.toBeInTheDocument();
    rerender(
      <UserAssignmentFields
        options={data}
        value={{ ...emptyValue, roleCds: ["ADMIN", "CUSTOM_ROLE", "OTHER"] }}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByRole("group", { name: "담당 프로젝트" })).toBeVisible();
  });

  it("적격 역할을 제거해도 부모의 영향 확인 전 프로젝트 값을 임의로 삭제하지 않는다", () => {
    render(
      <Harness
        initial={{
          roleCds: ["CUSTOM_ROLE"],
          headquarters: false,
          projectIds: ["active-project"],
        }}
      />,
    );
    fireEvent.click(
      screen.getByRole("checkbox", { name: "담당 프로젝트 사용자" }),
    );
    expect(
      screen.queryByRole("group", { name: "담당 프로젝트" }),
    ).not.toBeInTheDocument();
    expect(
      JSON.parse(screen.getByTestId("assignment-value").textContent!),
    ).toEqual({
      roleCds: [],
      headquarters: false,
      projectIds: ["active-project"],
    });
    fireEvent.click(
      screen.getByRole("checkbox", { name: "담당 프로젝트 사용자" }),
    );
    expect(
      screen.getByRole("checkbox", { name: "진행 프로젝트" }),
    ).toBeChecked();
  });

  it("담당 파트는 본사와 미선택만 제공하고 업무 원천 안내를 표시한다", () => {
    render(<Harness />);
    const part = screen.getByRole("combobox", { name: "담당 파트" });
    expect(
      within(part)
        .getAllByRole("option")
        .map((item) => item.textContent),
    ).toEqual(["–", "본사(직접 영업)"]);
    expect(part).toHaveAccessibleDescription(
      "영업1·영업2 같은 영업 파트의 파트장은 기준정보 › 거래처·영업 파트에서 정합니다.",
    );
    fireEvent.change(part, { target: { value: "true" } });
    expect(
      JSON.parse(screen.getByTestId("assignment-value").textContent!)
        .headquarters,
    ).toBe(true);
    fireEvent.change(part, { target: { value: "false" } });
    expect(
      JSON.parse(screen.getByTestId("assignment-value").textContent!)
        .headquarters,
    ).toBe(false);
    expect(request).not.toHaveBeenCalled();
  });

  it("사용 불가 상태는 입력을 잠그고 API 칸 오류는 해당 영역에 연결한다", () => {
    render(
      <UserAssignmentFields
        options={options()}
        disabled
        value={{ ...emptyValue, roleCds: ["CUSTOM_ROLE"] }}
        onChange={vi.fn()}
        errors={{
          roleCds: "역할을 선택해 주세요.",
          headquarters: "본사 구성을 확인해 주세요.",
          projectIds: "프로젝트를 확인해 주세요.",
        }}
      />,
    );
    for (const input of screen.getAllByRole("checkbox"))
      expect(input).toBeDisabled();
    expect(screen.getByRole("combobox", { name: "담당 파트" })).toBeDisabled();
    expect(screen.getByRole("group", { name: /역할/ })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(
      screen.getByRole("group", { name: /역할/ }),
    ).toHaveAccessibleDescription("역할을 선택해 주세요.");
    expect(
      screen.getByRole("combobox", { name: "담당 파트" }),
    ).toHaveAccessibleDescription(/본사 구성을 확인해 주세요/);
    expect(
      screen.getByRole("group", { name: "담당 프로젝트" }),
    ).toHaveAccessibleDescription("프로젝트를 확인해 주세요.");
  });

  it("빈 옵션은 합성 역할이나 프로젝트 없이 빈 상태로 안내한다", () => {
    const { rerender } = render(
      <UserAssignmentFields
        options={{ roles: [], projects: [], userStates: [] }}
        value={emptyValue}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByText("선택할 역할이 없습니다.")).toBeVisible();
    expect(screen.queryAllByRole("checkbox")).toHaveLength(0);
    rerender(
      <UserAssignmentFields
        options={{ ...options(), projects: [] }}
        value={{ ...emptyValue, roleCds: ["CUSTOM_ROLE"] }}
        onChange={vi.fn()}
      />,
    );
    expect(screen.getByText("선택할 프로젝트가 없습니다.")).toBeVisible();
  });
});

describe("사용자 선택 목록 요청", () => {
  it("옵션 로딩 후 상대 GET 응답을 표시하고 상태 원천도 클라이언트에서 보존한다", async () => {
    render(<Harness remote />);
    expect(screen.getByTestId("user-options-loading")).toHaveTextContent(
      "선택 목록을 불러오는 중입니다.",
    );
    expect(
      await screen.findByRole("checkbox", { name: "담당 프로젝트 사용자" }),
    ).toBeVisible();
    expect(request).toHaveBeenCalledWith("/api/common/user-options", {
      signal: expect.any(AbortSignal),
    });
    expect(request).toHaveBeenCalledTimes(1);
    expect((await getCommonUserOptions()).userStates).toEqual([
      { code: "LOCK", name: "잠김" },
    ]);
  });

  it("요청 실패는 내부 메시지를 숨기고 재시도로 정상 선택을 복원한다", async () => {
    request.mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({
        success: false,
        message: "private SQL",
        errors: [],
      }),
    });
    render(<Harness remote />);
    expect(await screen.findByTestId("user-options-error")).toHaveTextContent(
      userOptionsError,
    );
    expect(screen.queryByText(/private SQL/)).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 불러오기" }));
    expect(
      await screen.findByRole("checkbox", { name: "시스템 관리자" }),
    ).toBeVisible();
    expect(request).toHaveBeenCalledTimes(2);
  });

  it.each([
    { success: true, data: { roles: null, projects: [], userStates: [] } },
    {
      success: true,
      data: {
        ...options(),
        roles: [{ roleCd: "PM", roleName: "PM", assignedProjectScope: "true" }],
      },
    },
    { success: true, data: { ...options(), projects: [{}] } },
    { success: true, data: { ...options(), userStates: [{ code: "USE" }] } },
    { success: false, data: options() },
  ])("정상 HTTP라도 계약과 다른 응답이면 오류를 표시한다 %#", async (body) => {
    request.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => body,
    });
    render(<Harness remote />);
    expect(await screen.findByTestId("user-options-error")).toHaveTextContent(
      userOptionsError,
    );
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("JSON이 아닌 본문과 네트워크 오류를 안전한 오류로 변환한다", async () => {
    request.mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => {
        throw new Error("private html");
      },
    });
    await expect(getCommonUserOptions()).rejects.toThrow(userOptionsError);
    request.mockRejectedValueOnce(new Error("private connection"));
    await expect(getCommonUserOptions()).rejects.toThrow(userOptionsError);
  });

  it("화면이 닫히면 진행 중 요청을 취소한다", async () => {
    let resolve!: (value: ReturnType<typeof response>) => void;
    request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const view = render(<Harness remote />);
    await waitFor(() => expect(request).toHaveBeenCalledTimes(1));
    const signal = request.mock.calls[0][1].signal as AbortSignal;
    view.unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => {
      resolve(response());
    });
  });
});

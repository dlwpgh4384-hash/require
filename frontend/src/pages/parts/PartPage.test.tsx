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
import { PartPage } from "./PartPage";
import { PartCreateDialog } from "./PartCreateDialog";
import { PartRenameDialog } from "./PartRenameDialog";
import { PartStandardChangeDialog } from "./PartStandardChangeDialog";
import { PartStopDialog } from "./PartStopDialog";
import { PartStandardsPanel } from "./PartStandardsPanel";
import { PartChangeHistoryPanel } from "./PartChangeHistoryPanel";
import {
  ClientPartWorkspace,
  ClientPartTitleActions,
} from "./ClientPartWorkspace";
import { ClientPartWorkspaceProvider } from "../clients/ClientPartWorkspace";
import type { Part, PartOptions, PartStandard } from "./parts";

let part: Part;
let options: PartOptions;
let standards: PartStandard[];
let request: ReturnType<typeof vi.fn>;
let failedPath: string;
let failureStatus: number;
let empty: boolean;
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const writes = () =>
  request.mock.calls.filter((call: unknown[]) =>
    ["POST", "PUT"].includes(
      (call[1] as RequestInit | undefined)?.method ?? "",
    ),
  );
beforeEach(() => {
  failedPath = "";
  failureStatus = 0;
  empty = false;
  part = {
    partId: "owned-part",
    partName: "영업 1파트",
    leaderUserId: "leader",
    leaderName: "김파트장",
    distributionRate: 38.25,
    salesExpense: 1234567,
    effectiveMonth: "2026-01",
    endMonth: null,
    useYn: "Y",
    modifiedAt: null,
    personCount: 0,
    projectCount: 0,
    scheduledChange: "2026-12부터 45%",
  };
  options = {
    leaders: [{ userId: "leader", userName: "김파트장" }],
    distributionRate: 41.25,
    salesExpense: 1700000,
    closedMonth: "2026-08",
    earliestMonth: "2026-09",
  };
  standards = [
    { ...part, state: "적용 중" },
    { ...part, effectiveMonth: "2026-12", distributionRate: 45, state: "예정" },
  ];
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (init?.method === "PUT" || init?.method === "POST") {
      if (failureStatus)
        return {
          ok: false,
          status: failureStatus,
          json: async () => ({
            success: false,
            meta: {},
            message:
              failureStatus === 409
                ? "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."
                : "지금 기준과 같습니다. 바꿀 값을 입력해 주세요.",
            errors: [
              { field: "distributionRate", message: "지금 기준과 같습니다. 바꿀 값을 입력해 주세요." },
            ],
          }),
        };
      const input = JSON.parse(String(init.body));
      if (path.endsWith("/standards")) return success({ items: standards });
      part = {
        ...part,
        ...input,
        partId: "owned-part",
        modifiedAt: "2026-09-01T01:00:00Z",
      };
      if (path.endsWith("/stop")) part.useYn = "N";
      if (path.endsWith("/resume")) part.useYn = "Y";
      return success(part);
    }
    if (failedPath && path === failedPath)
      throw new Error("요청에 실패했습니다.");
    if (path === "/api/common/parts")
      return success({ items: empty ? [] : [part] });
    if (path.endsWith("/options")) return success(options);
    if (path.endsWith("/standards"))
      return success({ items: empty ? [] : standards });
    if (path.endsWith("/change-history")) return success({ items: [] });
    throw new Error(`예상하지 않은 요청: ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
const readyCreate = async () => {
  const saved = vi.fn();
  const closed = vi.fn();
  render(<PartCreateDialog onClose={closed} onSaved={saved} />);
  await waitFor(() =>
    expect(screen.getByTestId("part-rate-input")).toHaveValue("41.25"),
  );
  return { saved, closed };
};
const fillCreate = () => {
  fireEvent.change(screen.getByTestId("part-name-input"), {
    target: { value: "새 영업 파트" },
  });
  fireEvent.change(screen.getByTestId("part-leader-select"), {
    target: { value: "leader" },
  });
};
it("목록에서 현재값·예정 변경·안내와 모든 업무 열을 표시한다", async () => {
  render(<PartPage />);
  expect(await screen.findByText("영업 1파트")).toBeInTheDocument();
  expect(screen.getByText("38.25%")).toBeInTheDocument();
  // 시안: 예정된 변경은 파란(안내) 배지 「2026-12부터 45%」
  expect(screen.getByText("2026-12부터 45%")).toBeInTheDocument();
  expect(screen.getByText("2026-12부터 45%")).toHaveClass("ls-b-info");
  expect(screen.getByText(/지난 달 정산은 바뀌지 않습니다/)).toBeInTheDocument();
  for (const label of ["파트장", "소속 인력", "진행 프로젝트", "사용 여부"])
    expect(
      screen.getByRole("columnheader", { name: new RegExp(label) }),
    ).toBeInTheDocument();
  expect(screen.getByTestId("part-add-button")).toBeEnabled();
});
it("빈 목록과 실패 후 다시 시도를 처리한다", async () => {
  failedPath = "/api/common/parts";
  render(<PartPage />);
  await screen.findByText("요청에 실패했습니다.");
  failedPath = "";
  empty = true;
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(
    await screen.findByText("조건에 맞는 결과가 없습니다."),
  ).toBeInTheDocument();
});
it("워크스페이스는 기존 경로의 두 탭과 파트 제목줄 추가를 제공한다", async () => {
  render(
    <ClientPartWorkspaceProvider>
      <ClientPartTitleActions clientActions={<span>거래처 작업</span>} />
      <ClientPartWorkspace
        clients={<span>거래처 화면</span>}
        parts={<PartPage />}
      />
    </ClientPartWorkspaceProvider>,
  );
  expect(screen.getByText("거래처 화면")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("client-part-parts-tab"));
  await screen.findByText("영업 1파트");
  expect(screen.queryByTestId("part-add-button")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("part-title-add"));
  expect(
    await screen.findByRole("dialog", { name: "영업 파트 추가" }),
  ).toBeInTheDocument();
});
it("새 폼의 운영 기본값·허용 시작월을 DB 옵션 응답에서 읽는다", async () => {
  await readyCreate();
  expect(screen.getByTestId("part-expense-input")).toHaveValue("1,700,000");
  expect(screen.getByTestId("part-month-input")).toHaveAttribute(
    "min",
    "2026-09",
  );
  expect(screen.getByTestId("part-month-input")).toHaveValue("2026-09");
  expect(
    within(screen.getByTestId("part-leader-select")).getAllByRole("option"),
  ).toHaveLength(2);
});
it("숫자 아닌 글자를 제거하고 금액 콤마를 표시하며 범위를 검증한다", async () => {
  await readyCreate();
  fillCreate();
  fireEvent.change(screen.getByTestId("part-rate-input"), {
    target: { value: "101abc" },
  });
  fireEvent.change(screen.getByTestId("part-expense-input"), {
    target: { value: "1234567원" },
  });
  expect(screen.getByTestId("part-rate-input")).toHaveValue("101");
  expect(screen.getByTestId("part-expense-input")).toHaveValue("1,234,567");
  fireEvent.click(screen.getByTestId("part-save"));
  // 시안: 창 아래 한 줄
  expect(await screen.findByTestId("part-save-error")).toHaveTextContent(
    "이익 배분율은 0~100 사이 숫자로 입력해 주세요.",
  );
  expect(writes()).toHaveLength(0);
});
it.each([
  ["partName", "", "파트명을 입력해 주세요."],
  ["leaderUserId", "", "파트장을 골라 주세요."],
  ["salesExpense", "", "공통관리비를 입력해 주세요."],
  ["effectiveMonth", "2026-08", "적용 시작월은 마감된 달(2026-08) 다음 달부터 고를 수 있습니다."],
] as const)("추가 창 %s 오류는 시안 문구 한 줄로 알린다", async (key, value, message) => {
  await readyCreate();
  fillCreate();
  const testIds = {
    partName: "part-name-input",
    leaderUserId: "part-leader-select",
    salesExpense: "part-expense-input",
    effectiveMonth: "part-month-input",
  } as const;
  fireEvent.change(screen.getByTestId(testIds[key]), { target: { value } });
  fireEvent.click(screen.getByTestId("part-save"));
  expect(await screen.findByTestId("part-save-error")).toHaveTextContent(message);
  // 칸마다 따로 붙는 알림은 없다(한 줄만).
  expect(screen.getAllByRole("alert")).toHaveLength(1);
  expect(screen.getByTestId(testIds[key])).toHaveAttribute("aria-invalid", "true");
  expect(writes()).toHaveLength(0);
});
it("소수 초과 배분율·관리비 자릿수를 저장하지 않는다", async () => {
  await readyCreate();
  fillCreate();
  fireEvent.change(screen.getByTestId("part-rate-input"), {
    target: { value: "1.234" },
  });
  fireEvent.change(screen.getByTestId("part-expense-input"), {
    target: { value: "1000000000000000" },
  });
  fireEvent.click(screen.getByTestId("part-save"));
  // 한 줄에는 첫 오류(배분율)만, 두 칸 모두 잘못 표시는 남는다.
  expect(await screen.findByTestId("part-save-error")).toHaveTextContent(/소수 둘째 자리/);
  expect(screen.getByTestId("part-rate-input")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(screen.getByTestId("part-expense-input")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  fireEvent.change(screen.getByTestId("part-rate-input"), {
    target: { value: "1.23" },
  });
  fireEvent.click(screen.getByTestId("part-save"));
  expect(await screen.findByText(/15자리 이내/)).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("적격 파트장 없음은 저장 불가이고 옵션 실패는 다시 시도할 수 있다", async () => {
  failedPath = "/api/common/parts/options";
  render(<PartCreateDialog onClose={vi.fn()} onSaved={vi.fn()} />);
  await screen.findByText("요청에 실패했습니다.");
  expect(screen.getByTestId("part-save")).toBeDisabled();
  failedPath = "";
  options.leaders = [];
  fireEvent.click(screen.getByTestId("part-options-retry"));
  await screen.findByText("선택할 수 있는 영업 파트장이 없습니다.");
  expect(screen.getByTestId("part-save")).toBeDisabled();
});
it("생성은 사용자 ID·수정토큰 없이 전송하고 성공 콜백을 호출한다", async () => {
  const { saved } = await readyCreate();
  fillCreate();
  fireEvent.click(screen.getByTestId("part-save"));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith("owned-part", "새 영업 파트을 추가했습니다."),
  );
  const [path, init] = writes()[0];
  expect(path).toBe("/api/common/parts");
  expect(JSON.parse(init.body)).toEqual({
    partName: "새 영업 파트",
    effectiveMonth: "2026-09",
    leaderUserId: "leader",
    distributionRate: 41.25,
    salesExpense: 1700000,
  });
});
it("400 필드 오류를 표시하고 입력 초안을 유지한다", async () => {
  await readyCreate();
  fillCreate();
  failureStatus = 400;
  fireEvent.click(screen.getByTestId("part-save"));
  await screen.findByTestId("part-save-error");
  expect(screen.getByTestId("part-name-input")).toHaveValue("새 영업 파트");
  expect(screen.getByTestId("part-rate-input")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
});
it("409는 초안을 유지하고 최신 aggregate token을 다시 읽어 저장한다", async () => {
  const saved = vi.fn();
  render(<PartRenameDialog part={part} onClose={vi.fn()} onSaved={saved} />);
  fireEvent.change(screen.getByTestId("part-name-input"), {
    target: { value: "수정 이름" },
  });
  failureStatus = 409;
  fireEvent.click(screen.getByTestId("part-save"));
  await screen.findByTestId("part-refresh-version");
  expect(screen.getByTestId("part-name-input")).toHaveValue("수정 이름");
  expect(screen.getByTestId("part-save")).toBeDisabled();
  part.modifiedAt = "2026-10-01T00:00:00.123456Z";
  failureStatus = 0;
  fireEvent.click(screen.getByTestId("part-refresh-version"));
  await waitFor(() => expect(screen.getByTestId("part-save")).toBeEnabled());
  fireEvent.click(screen.getByTestId("part-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(JSON.parse(writes()[1][1].body)).toEqual({
    partName: "수정 이름",
    modifiedAt: "2026-10-01T00:00:00.123456Z",
  });
});
it("Ctrl+S는 활성 폼에서 한 번만 저장하고 unmount 뒤 제거한다", async () => {
  const saved = await readyCreate();
  fillCreate();
  screen.getByTestId("part-name-input").focus();
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(saved.saved).toHaveBeenCalledTimes(1));
  expect(writes()).toHaveLength(1);
  cleanup();
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  expect(writes()).toHaveLength(1);
});
it("미저장 창 닫기는 확인하며 취소는 쓰지 않는다", async () => {
  const { closed } = await readyCreate();
  fillCreate();
  fireEvent.click(screen.getByTestId("part-cancel"));
  expect(screen.getByRole("alertdialog")).toBeInTheDocument();
  expect(closed).not.toHaveBeenCalled();
  expect(writes()).toHaveLength(0);
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "나가기",
    }),
  );
  expect(closed).toHaveBeenCalledTimes(1);
  expect(writes()).toHaveLength(0);
});
it.each(["personCount", "projectCount"] as const)(
  "%s 관계가 있으면 중지를 막고 이동을 안내한다",
  (field) => {
    render(
      <PartStopDialog
        part={{ ...part, [field]: 1 }}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    // 시안: 「사용을 중지할 수 없습니다」 + 인원·건수 안내 + [확인]만
    expect(screen.getByText("사용을 중지할 수 없습니다")).toBeInTheDocument();
    expect(screen.getByText(/먼저 다른 파트로 옮겨 주세요/)).toBeInTheDocument();
    expect(screen.queryByTestId("part-save")).toBeNull();
    expect(screen.getByTestId("part-blocked-ok")).toBeEnabled();
    expect(writes()).toHaveLength(0);
  },
);
it("관계 없는 중지는 nullable token을 그대로 전송한다", async () => {
  const saved = vi.fn();
  render(<PartStopDialog part={part} onClose={vi.fn()} onSaved={saved} />);
  fireEvent.click(screen.getByTestId("part-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(writes()[0][0]).toBe("/api/common/parts/owned-part/stop");
  expect(JSON.parse(writes()[0][1].body)).toEqual({ modifiedAt: null });
});
it("중지 확인 창은 시안 제목·본문이고 저장 알림은 「사용을 중지했습니다.」", async () => {
  const saved = vi.fn();
  render(<PartStopDialog part={part} onClose={vi.fn()} onSaved={saved} />);
  expect(
    screen.getByRole("dialog", { name: "영업 1파트의 사용을 중지할까요?" }),
  ).toBeInTheDocument();
  expect(screen.getByText("중지하면 새 인력·프로젝트에 고를 수 없습니다.")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("part-save"));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith("owned-part", "사용을 중지했습니다."),
  );
});
it("파트명 수정은 빈 이름을 막고 저장 알림은 「파트명을 바꿨습니다.」", async () => {
  render(<PartPage />);
  fireEvent.click(await screen.findByText("영업 1파트"));
  fireEvent.click(await screen.findByTestId("part-more"));
  fireEvent.click(screen.getByTestId("part-rename"));
  fireEvent.change(screen.getByTestId("part-name-input"), { target: { value: " " } });
  fireEvent.click(screen.getByTestId("part-save"));
  expect(await screen.findByTestId("part-save-error")).toHaveTextContent("파트명을 입력해 주세요.");
  expect(writes()).toHaveLength(0);
  fireEvent.change(screen.getByTestId("part-name-input"), { target: { value: "영업 9파트" } });
  fireEvent.click(screen.getByTestId("part-save"));
  expect(await screen.findByTestId("part-saved-toast")).toHaveTextContent("파트명을 바꿨습니다.");
});
it("기준 변경 저장 알림은 「{파트} 기준을 바꿨습니다. {월}부터 적용됩니다.」", async () => {
  const saved = vi.fn();
  render(
    <PartStandardChangeDialog part={part} onClose={vi.fn()} onSaved={saved} />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("part-rate-input")).toHaveValue("38.25"),
  );
  fireEvent.change(screen.getByTestId("part-rate-input"), { target: { value: "40" } });
  fireEvent.click(screen.getByTestId("part-save"));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith(
      "owned-part",
      "영업 1파트 기준을 바꿨습니다. 2026-09부터 적용됩니다.",
    ),
  );
});
it("기준 변경에서 지금 기준과 같으면 저장하지 않고 시안 문구로 알린다", async () => {
  render(
    <PartStandardChangeDialog part={part} onClose={vi.fn()} onSaved={vi.fn()} />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("part-rate-input")).toHaveValue("38.25"),
  );
  fireEvent.click(screen.getByTestId("part-save"));
  expect(await screen.findByTestId("part-save-error")).toHaveTextContent(
    "지금 기준과 같습니다. 바꿀 값을 입력해 주세요.",
  );
  expect(writes()).toHaveLength(0);
});
it("기준 변경은 선택월 기존값을 읽고 master 버전을 보낸다", async () => {
  const saved = vi.fn();
  standards.forEach((row) => {
    row.modifiedAt = "2026-09-01T00:00:00Z";
  });
  render(
    <PartStandardChangeDialog part={part} onClose={vi.fn()} onSaved={saved} />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("part-rate-input")).toHaveValue("38.25"),
  );
  fireEvent.change(screen.getByTestId("part-month-input"), {
    target: { value: "2026-12" },
  });
  expect(screen.getByTestId("part-rate-input")).toHaveValue("45");
  fireEvent.change(screen.getByTestId("part-rate-input"), {
    target: { value: "46" },
  });
  fireEvent.click(screen.getByTestId("part-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  expect(JSON.parse(writes()[0][1].body)).toMatchObject({
    effectiveMonth: "2026-12",
    distributionRate: 46,
    modifiedAt: "2026-09-01T00:00:00Z",
  });
});
it("기준 이력은 현재·예정·지난 상태와 기간을 읽기 전용으로 표시한다", async () => {
  standards.push({
    ...standards[0],
    effectiveMonth: "2025-01",
    endMonth: "2025-12",
    state: "지난",
  });
  render(<PartStandardsPanel partId={part.partId} />);
  await screen.findByText("적용 중");
  expect(screen.getByText("예정")).toBeInTheDocument();
  expect(screen.getByText("지난 기준")).toBeInTheDocument();
  expect(screen.getByText("2025-01 ~ 2025-12")).toBeInTheDocument();
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("기준 이력 실패를 재시도하고 빈 이력을 표시한다", async () => {
  failedPath = "/api/common/parts/owned-part/standards";
  render(<PartStandardsPanel partId={part.partId} />);
  await screen.findByText("요청에 실패했습니다.");
  failedPath = "";
  empty = true;
  fireEvent.click(screen.getByTestId("part-standards-retry"));
  expect(await screen.findByText("기준 이력이 없습니다.")).toBeInTheDocument();
});
it("변경 기록은 빈 상태와 조회 실패를 처리하고 편집하지 않는다", async () => {
  failedPath = "/api/common/parts/owned-part/change-history";
  render(<PartChangeHistoryPanel partId={part.partId} />);
  await screen.findByText("요청에 실패했습니다.");
  failedPath = "";
  fireEvent.click(screen.getByTestId("part-history-retry"));
  expect(await screen.findByText("변경 기록이 없습니다.")).toBeInTheDocument();
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
});
it("중지 파트는 상세에서 중지 단추를 숨긴다", async () => {
  part.useYn = "N";
  render(<PartPage />);
  fireEvent.click(await screen.findByText("영업 1파트"));
  await screen.findByTestId("part-detail-panel");
  // 시안: [⋯] 메뉴에 파트명 수정·사용 중지
  fireEvent.click(screen.getByTestId("part-more"));
  expect(screen.queryByTestId("part-stop")).not.toBeInTheDocument();
  expect(screen.getByTestId("part-rename")).toBeEnabled();
});
it("시안 UI-15: 중지 파트는 띠를 보이고 ⋯ [다시 사용]은 확인 없이 바로 되돌린다", async () => {
  part.useYn = "N";
  render(<PartPage />);
  fireEvent.click(await screen.findByText("영업 1파트"));
  const panel = await screen.findByTestId("part-detail-panel");
  expect(within(panel).getByText("사용을 중지한 파트입니다.")).toBeVisible();
  fireEvent.click(screen.getByTestId("part-more"));
  fireEvent.click(screen.getByTestId("part-resume"));
  expect(await screen.findByText("영업 1파트을 다시 사용합니다.")).toBeVisible();
  const resume = writes().filter((call) => String(call[0]).endsWith("/owned-part/resume"));
  expect(resume).toHaveLength(1);
  expect(JSON.parse(String((resume[0][1] as RequestInit).body))).toEqual({ modifiedAt: null });
  // 확인 창(작은 창)은 뜨지 않는다. 상세 패널 자체는 role=dialog라 제외하고 본다.
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  expect(screen.getAllByRole("dialog")).toHaveLength(1);
  await waitFor(() =>
    expect(within(screen.getByTestId("part-detail-panel")).queryByText("사용을 중지한 파트입니다.")).not.toBeInTheDocument(),
  );
});

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
import { OperatingStandardsPanel } from "./OperatingStandardsPanel";
import { ScreenDirtyContext } from "../../shared/layout/ScreenTabs";
import type {
  OperatingStandard,
  OperatingStandardChange,
} from "./operatingStandards";

let rows: OperatingStandard[];
let request: ReturnType<typeof vi.fn>;
let failGet: boolean;
let failPut: boolean;
let failAfterSave: boolean;
let onSaved: ReturnType<typeof vi.fn>;
let closedMonth: string | null = null;
const success = () => ({
  ok: true,
  status: 200,
  json: async () => ({
    success: true,
    meta: {},
    data: { items: structuredClone(rows), closedMonth },
  }),
});
const writes = () =>
  request.mock.calls.filter(
    (call) => (call[1] as RequestInit | undefined)?.method === "PUT",
  );
const row = (
  code: string,
  name: string,
  value: string,
  unit: string,
  month: string | null = "2026-01",
  closed = false,
): OperatingStandard => ({
  standardCode: code,
  standardName: name,
  value,
  unit,
  effectiveMonth: month,
  closed,
  immediate: month === null,
});
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-13T08:00:00Z"));
  rows = [
    row("PART_DSTRB_RT", "파트 배분율", "40", "%"),
    row("PART_SALES_EXPS", "공통관리비", "1500000", "원"),
    row("VAT_RT", "부가세율", "10", "%"),
    row("TGT_UTIL_RT", "목표 가동률", "90", "%"),
    row("TGT_MRGN_RT", "목표 마진율", "20", "%"),
    row("LOW_MRGN_GAP", "저마진 경고폭", "5", "%p"),
    row("DUN_D1", "미수 알림 1단계", "7", "일"),
    row("DUN_D2", "미수 알림 2단계", "30", "일"),
    row("DUN_D3", "미수 알림 3단계", "60", "일"),
    row("WTHDR_NOTI_D", "철수 알림 일수", "30", "일"),
    row("EXT_PRBL_H", "연장 확률 높음", "80", "%"),
    row("EXT_PRBL_M", "연장 확률 보통", "50", "%"),
    row("EXT_PRBL_L", "연장 확률 낮음", "0", "%"),
    row("STEP_PRBL_1", "영업 단계 확률 발굴", "10", "%"),
    row("STEP_PRBL_2", "영업 단계 확률 제안", "30", "%"),
    row("STEP_PRBL_3", "영업 단계 확률 협상", "60", "%"),
    row("STEP_PRBL_4", "영업 단계 확률 계약 직전", "90", "%"),
    row("AI_SCORE_JOB", "AI 추천 배점 직무", "25", "점"),
    row("AI_SCORE_SKILL", "AI 추천 배점 스킬", "20", "점"),
    row("AI_SCORE_CUST", "AI 추천 배점 고객사·업종", "15", "점"),
    row("AI_SCORE_GRADE", "AI 추천 배점 등급", "10", "점"),
    row("AI_SCORE_PERF", "AI 추천 배점 과거 성과", "5", "점"),
    row("AI_SCORE_COST", "AI 추천 배점 비용 효과", "5", "점"),
    row("AI_SCORE_AVAIL", "AI 추천 배점 투입 가능일", "10", "점"),
    row("AI_SCORE_LOC", "AI 추천 배점 근무지", "10", "점"),
    row("CASH_BASE_YM", "자금수지 시작 월", "2026-01", "연월", null),
    row("CASH_BASE_AMT", "자금수지 기초 잔액", "1150000000", "원", null),
    row("BDGT_WARN_RT", "예산 소진 주의", "80", "%"),
  ];
  failGet = false;
  failPut = false;
  failAfterSave = false;
  onSaved = vi.fn();
  closedMonth = null;
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path !== "/api/common/operating-standards")
      throw new Error(`예상하지 않은 요청 ${path}`);
    if (init?.method === "PUT") {
      if (failPut)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            meta: {},
            message: "마감한 달에는 적용할 수 없습니다.",
            errors: [
              {
                field: "effectiveMonth",
                message: "마감한 달에는 적용할 수 없습니다.",
              },
            ],
          }),
        };
      const changes: OperatingStandardChange[] = JSON.parse(
        String(init.body),
      ).items;
      for (const change of changes) {
        const index = rows.findIndex(
          (item) =>
            item.standardCode === change.standardCode &&
            item.effectiveMonth === change.effectiveMonth,
        );
        if (change.action === "delete") rows.splice(index, 1);
        else if (change.action === "update")
          rows[index] = { ...rows[index], value: change.value! };
        else
          rows.push({
            ...rows.find((item) => item.standardCode === change.standardCode)!,
            value: change.value!,
            effectiveMonth: change.effectiveMonth,
          });
      }
      if (failAfterSave) failGet = true;
      return success();
    }
    if (failGet) throw new Error("조회 실패");
    return success();
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});
async function mount() {
  render(<OperatingStandardsPanel onChanged={onSaved} />);
  await screen.findByLabelText("부가세율");
}
function change(code: string, value: string) {
  fireEvent.change(screen.getByTestId(`operating-value-${code}`), {
    target: { value },
  });
}
function openSave() {
  fireEvent.click(screen.getByTestId("operating-save"));
}
async function confirm(month = "2027-02") {
  fireEvent.change(screen.getByTestId("operating-effective-month"), {
    target: { value: month },
  });
  fireEvent.click(screen.getByTestId("operating-save-confirm"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  await waitFor(() =>
    expect(
      screen.queryByTestId("operating-save-dialog"),
    ).not.toBeInTheDocument(),
  );
}
it("renders API values in the mockup table rows with eight AI inputs in one row", async () => {
  await mount();
  for (const [code, value] of [
    ["part-dstrb-rt", "40"],
    ["part-sales-exps", "1500000"],
    ["vat-rt", "10"],
    ["tgt-util-rt", "90"],
    ["tgt-mrgn-rt", "20"],
    ["low-mrgn-gap", "5"],
    ["dun-d1", "7"],
    ["dun-d2", "30"],
    ["dun-d3", "60"],
    ["wthdr-noti-d", "30"],
    ["ext-prbl-h", "80"],
    ["ext-prbl-m", "50"],
    ["ext-prbl-l", "0"],
    ["step-prbl-1", "10"],
    ["step-prbl-2", "30"],
    ["step-prbl-3", "60"],
    ["step-prbl-4", "90"],
  ])
    expect(screen.getByTestId(`operating-value-${code}`)).toHaveValue(value);
  const ai = screen.getByTestId("operating-row-ai-score");
  expect(ai).toHaveAccessibleName("AI 배점");
  expect(within(ai).getAllByRole("textbox")).toHaveLength(8);
  expect(
    within(ai)
      .getAllByRole("textbox")
      .map((input) => (input as HTMLInputElement).value),
  ).toEqual(["25", "20", "15", "10", "5", "5", "10", "10"]);
  // 시안(최종): 운영 기준값 표에 예산 소진 주의도 있다.
  expect(screen.getByLabelText("예산 소진 주의")).toHaveValue("80");
  expect(
    within(screen.getByTestId("operating-row-vat-rt")).getByText("식-05·49"),
  ).toBeInTheDocument();
});
it("shows values without trailing zeros", async () => {
  rows.find((item) => item.standardCode === "VAT_RT")!.value = "10.0000";
  rows.find((item) => item.standardCode === "LOW_MRGN_GAP")!.value = "0.0330";
  await mount();
  expect(screen.getByLabelText("부가세율")).toHaveValue("10");
  expect(screen.getByLabelText("저마진 경고폭")).toHaveValue("0.033");
});
it("loading state lasts until the actual read completes", async () => {
  let resolve!: (value: ReturnType<typeof success>) => void;
  request.mockImplementationOnce(
    () =>
      new Promise((done) => {
        resolve = done;
      }),
  );
  render(<OperatingStandardsPanel />);
  expect(
    screen.getByText("운영 기준값을 불러오는 중입니다."),
  ).toBeInTheDocument();
  resolve(success());
  await screen.findByLabelText("부가세율");
});
it("empty API response is displayed without invented rows", async () => {
  rows = [];
  render(<OperatingStandardsPanel />);
  await screen.findByText("등록된 운영 기준값이 없습니다.");
  expect(screen.queryByTestId("operating-save")).not.toBeInTheDocument();
});
it("failed read offers retry of the same endpoint", async () => {
  failGet = true;
  render(<OperatingStandardsPanel />);
  await screen.findByText("조회 실패");
  failGet = false;
  fireEvent.click(screen.getByTestId("operating-retry"));
  await screen.findByLabelText("부가세율");
  expect(writes()).toHaveLength(0);
});
it("draft marks UI-27 dirty and cancel restores server values with zero writes", async () => {
  const dirty = vi.fn();
  render(
    <ScreenDirtyContext.Provider value={dirty}>
      <OperatingStandardsPanel />
    </ScreenDirtyContext.Provider>,
  );
  await screen.findByLabelText("부가세율");
  change("vat-rt", "11");
  expect(dirty).toHaveBeenCalledWith("UI-27", true);
  fireEvent.click(screen.getByTestId("operating-cancel"));
  expect(screen.getByLabelText("부가세율")).toHaveValue("10");
  expect(dirty).toHaveBeenLastCalledWith("UI-27", false);
  expect(writes()).toHaveLength(0);
});
it("M4 cancel preserves parent draft and sends no PUT", async () => {
  await mount();
  change("vat-rt", "11");
  openSave();
  expect(screen.getByTestId("operating-save-summary")).toHaveTextContent(
    "10 → 11",
  );
  fireEvent.click(screen.getByTestId("operating-save-cancel"));
  expect(screen.getByLabelText("부가세율")).toHaveValue("11");
  expect(writes()).toHaveLength(0);
});
it("anonymous future save sends one code per row and refreshes history consumers", async () => {
  await mount();
  change("vat-rt", "11");
  change("part-dstrb-rt", "42");
  openSave();
  await confirm();
  const payload = JSON.parse(String((writes()[0][1] as RequestInit).body));
  expect(payload.items).toEqual([
    {
      standardCode: "PART_DSTRB_RT",
      value: "42",
      effectiveMonth: "2027-02",
      action: "add",
    },
    {
      standardCode: "VAT_RT",
      value: "11",
      effectiveMonth: "2027-02",
      action: "add",
    },
  ]);
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(
    request.mock.calls.filter((call) => !(call[1] as RequestInit)?.method)
      .length,
  ).toBeGreaterThan(1);
  expect(screen.getByLabelText("부가세율")).toHaveValue("10");
});
it("existing unclosed identity uses explicit update not add", async () => {
  await mount();
  change("vat-rt", "11");
  openSave();
  await confirm("2026-01");
  expect(
    JSON.parse(String((writes()[0][1] as RequestInit).body)).items[0].action,
  ).toBe("update");
});
it("AI sum other than 100 has a field error and zero calls", async () => {
  await mount();
  change("ai-score-job", "26");
  openSave();
  expect(
    screen.getByText("AI 배점 합계는 100이어야 합니다."),
  ).toBeInTheDocument();
  expect(screen.queryByTestId("operating-save-dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("AI sum 100 opens confirmation with both changed fields", async () => {
  await mount();
  change("ai-score-job", "26");
  change("ai-score-skill", "19");
  openSave();
  expect(screen.getByTestId("operating-save-summary")).toHaveTextContent(
    "25 → 26",
  );
  await confirm();
  expect(
    JSON.parse(String((writes()[0][1] as RequestInit).body)).items,
  ).toHaveLength(2);
});
it("reversed receivable stages reject before a write", async () => {
  await mount();
  change("dun-d1", "30");
  change("dun-d2", "7");
  openSave();
  expect(
    screen.getAllByText("미수 알림은 1단계 < 2단계 < 3단계로 입력해 주세요.")
      .length,
  ).toBeGreaterThan(0);
  expect(writes()).toHaveLength(0);
});
it.each(["-1", "100.0001"])(
  "probability %s is rejected at the field",
  async (value) => {
    await mount();
    change("ext-prbl-h", value);
    openSave();
    expect(
      screen.getByText("확률은 0~100으로 입력해 주세요."),
    ).toBeInTheDocument();
    expect(writes()).toHaveLength(0);
  },
);
it.each(["0", "100"])("probability boundary %s is saved", async (value) => {
  await mount();
  change("ext-prbl-h", value);
  openSave();
  await confirm();
  expect(
    JSON.parse(String((writes()[0][1] as RequestInit).body)).items[0].value,
  ).toBe(value);
});
it("closed application month rejection keeps draft and shows field error in M4", async () => {
  failPut = true;
  await mount();
  change("vat-rt", "11");
  openSave();
  fireEvent.click(screen.getByTestId("operating-save-confirm"));
  await waitFor(() =>
    expect(
      within(screen.getByTestId("operating-save-dialog")).getAllByText(
        "마감한 달에는 적용할 수 없습니다.",
      ).length,
    ).toBeGreaterThan(0),
  );
  expect(screen.getByLabelText("부가세율")).toHaveValue("11");
  expect(rows.find((item) => item.standardCode === "VAT_RT")!.value).toBe("10");
});
it("cash-only save has no effective month and sends null while unlocked", async () => {
  await mount();
  change("cash-base-amt", "123.1250");
  openSave();
  expect(
    screen.queryByTestId("operating-effective-month"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("operating-save-confirm"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(
    JSON.parse(String((writes()[0][1] as RequestInit).body)).items,
  ).toEqual([
    {
      standardCode: "CASH_BASE_AMT",
      value: "123.1250",
      effectiveMonth: null,
      action: "update",
    },
  ]);
});
it("cash base closed locks both inputs but ordinary closed history can get a new month", async () => {
  rows = rows.map((item) => ({ ...item, closed: true }));
  await mount();
  expect(screen.getByLabelText("자금수지 시작 월")).toBeDisabled();
  expect(screen.getByLabelText("자금수지 기초 잔액")).toBeDisabled();
  expect(screen.getByLabelText("부가세율")).not.toBeDisabled();
  change("vat-rt", "11");
  openSave();
  expect(screen.getByTestId("operating-effective-month")).toBeInTheDocument();
});
it("successful write followed by failed GET disables duplicate saves until requery", async () => {
  failAfterSave = true;
  await mount();
  change("vat-rt", "11");
  openSave();
  await confirm();
  await waitFor(() =>
    expect(screen.getByTestId("operating-save")).toBeDisabled(),
  );
  expect(screen.getByTestId("operating-failure")).toHaveTextContent(
    "저장은 완료되었으나 다시 읽지 못했습니다.",
  );
  failGet = false;
  fireEvent.click(screen.getByTestId("operating-retry"));
  await waitFor(() =>
    expect(screen.getByTestId("operating-save")).not.toBeDisabled(),
  );
  expect(writes()).toHaveLength(1);
});

it("save dialog names the last closed month like the mockup", async () => {
  closedMonth = "2026-09";
  await mount();
  change("vat-rt", "11");
  openSave();
  expect(screen.getByTestId("operating-save-dialog")).toHaveTextContent(
    "마감한 달(2026-09)까지는 바꿀 수 없습니다.",
  );
});
it("history shows one row per month with the parts of a multi-part value", async () => {
  rows = rows.map((item) =>
    item.standardCode.startsWith("AI_SCORE_") ? { ...item, closed: true } : item,
  );
  await mount();
  fireEvent.click(screen.getByTestId("operating-history-ai-score-job"));
  const history = screen.getByTestId("operating-history-dialog");
  expect(within(history).getAllByRole("row")).toHaveLength(2);
  expect(
    screen.getByTestId("operating-history-row-ai-score-job-2026-01"),
  ).toHaveTextContent("25 / 20 / 15 / 10 / 5 / 5 / 10 / 10 점");
});
it("history window is view-only like the mockup", async () => {
  rows.push(row("VAT_RT", "부가세율", "11", "%", "2026-10"));
  await mount();
  change("vat-rt", "12");
  fireEvent.click(screen.getByTestId("operating-history-vat-rt"));
  const history = screen.getByTestId("operating-history-dialog");
  expect(
    within(history)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["적용 시작월", "값", "처리자"]);
  expect(within(history).getAllByRole("row")).toHaveLength(3);
  expect(
    screen.getByTestId("operating-history-row-vat-rt-2026-10"),
  ).toHaveTextContent("11 %");
  expect(within(history).queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    within(history).queryByRole("button", { name: /저장|삭제/ }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("operating-history-close"));
  expect(screen.getByLabelText("부가세율")).toHaveValue("12");
  expect(writes()).toHaveLength(0);
});
it("Ctrl+S opens the save dialog on this tab (FR-0104)", async () => {
  await mount();
  change("vat-rt", "11");
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  expect(screen.getByTestId("operating-save-dialog")).toBeInTheDocument();
});
it("value boxes drop letters at once (FR-0106)", async () => {
  await mount();
  change("vat-rt", "1a2");
  expect(screen.getByLabelText("부가세율")).toHaveValue("12");
});
it("save sends back the read modifiedAt so a later change is refused (FR-0106)", async () => {
  rows = rows.map((item) =>
    item.standardCode === "VAT_RT"
      ? { ...item, modifiedAt: "2026-11-14T00:00:00.123456Z" }
      : item,
  );
  await mount();
  change("vat-rt", "11");
  openSave();
  await confirm();
  expect(
    JSON.parse(String((writes()[0][1] as RequestInit).body)).items[0].modifiedAt,
  ).toBe("2026-11-14T00:00:00.123456Z");
});

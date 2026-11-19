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
import { MemoryRouter } from "react-router-dom";
import { BillChecksPanel } from "./BillChecksPanel";
import type {
  BillCheckDetail,
  BillCheckResult,
  BillCheckRow,
} from "./billChecks";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
let request: ReturnType<typeof vi.fn>;
let result: BillCheckResult;
let detail: BillCheckDetail;
let commandFailure: boolean;
let commandConflict: boolean;
const CONFLICT = "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.";
const row = (overrides: Partial<BillCheckRow> = {}): BillCheckRow => ({
  projectId: "selected-project",
  projectName: "점검 프로젝트",
  projectType: "MO",
  judgement: "청구 없음",
  headcount: 1,
  monthlyMm: 1,
  referenceAmount: 8000000,
  billedSupplyAmount: 0,
  difference: 8000000,
  explanation: "투입은 있는데 이 달 청구가 없습니다",
  latestBillId: null,
  allowedAction: "MISSING",
  ...overrides,
});
const onNotice = vi.fn();
const onOpenBill = vi.fn();
const renderPanel = () =>
  render(
    <MemoryRouter>
      <BillChecksPanel onOpenBill={onOpenBill} onNotice={onNotice} />
    </MemoryRouter>,
  );
const posts = () =>
  request.mock.calls.filter(([, init]) => init?.method === "POST");
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
  commandFailure = false;
  commandConflict = false;
  onNotice.mockReset();
  onOpenBill.mockReset();
  result = {
    checkMonth: "202610",
    summary: { totalCount: 2, missingCount: 1, underCount: 0, overCount: 0, matchedCount: 1 },
    items: [
      row({ projectId: "ok-project", projectName: "맞는 프로젝트", judgement: "맞음", billedSupplyAmount: 8000000, difference: 0, explanation: "", allowedAction: "NONE" }),
      row(),
    ],
    calculationMethod: "DB에서 조회한 기준금액 차이 계산",
    plannedBasis: false,
    months: ["202609", "202610", "202611"],
    lastClosedMonth: "202610",
  };
  detail = {
    bill: {
      billId: "selected-bill",
      projectId: "selected-project",
      projectName: "점검 프로젝트",
      customerName: "고객사",
      billOrder: 2,
      billMonth: "202611",
      billDate: "2026-11-15",
      supplyAmount: 2000000,
      vatRate: 10,
      vatAmount: 200000,
      totalAmount: 2200000,
      receiptSumAmount: 0,
      unpaidAmount: 2200000,
      payDueDate: "2026-12-25",
      taxInvoiceNo: null,
      status: "UNPAID",
      cancelled: false,
      cancelReason: null,
      cancelledBy: null,
      cancelledAt: null,
      modifiedAt: "2026-11-15T01:00:00.123456Z",
    },
    receipts: [],
    missingTaxInvoiceNumber: true,
  };
  request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (init?.method === "POST") {
      if (commandConflict)
        return {
          ok: false,
          status: 409,
          json: async () => ({
            success: false,
            message: CONFLICT,
            errors: [{ field: "modifiedAt", message: CONFLICT }],
          }),
        };
      if (commandFailure)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: "점검 조건이 변경되었습니다. 다시 조회해 주세요.",
            errors: [{ field: "checkMonth", message: "점검 조건이 변경되었습니다. 다시 조회해 주세요." }],
          }),
        };
      return ok(detail);
    }
    if (path.startsWith("/api/common/user-preferences/"))
      return ok({ found: false, prefVal: null });
    if (path.startsWith("/api/common/bill-options?"))
      return ok({
        defaultBillDate: "2026-11-15",
        defaultPayDueDate: "2026-11-25",
        vatRate: 10,
        projects: [{ projectId: "selected-project", projectName: "점검 프로젝트", customerName: "고객사" }],
      });
    if (path.startsWith("/api/common/bills/")) return ok(detail);
    if (path.startsWith("/api/common/bill-checks")) return ok(result);
    throw new Error(`예상하지 않은 요청: ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

it("시안처럼 요약 5칸·점검 월 단추·열 이름·맞지 않는 줄 먼저를 보여 준다", async () => {
  renderPanel();
  await screen.findByText("점검 프로젝트");
  const summary = screen.getByLabelText("점검 요약");
  expect(within(screen.getByTestId("bill-check-kpi-total")).getByText("2건")).toBeVisible();
  expect(within(screen.getByTestId("bill-check-kpi-missing")).getByText("1건")).toBeVisible();
  expect(within(screen.getByTestId("bill-check-kpi-amount")).getByText("800만 원")).toBeVisible();
  for (const label of ["점검 프로젝트", "맞음", "청구 없음", "금액 차이", "빠진 것으로 보이는 금액"])
    expect(within(summary).getAllByText(label).length).toBeGreaterThan(0);
  expect(screen.getByTestId("bill-check-month-202610")).toHaveAttribute("aria-pressed", "true");
  expect(screen.getByTestId("bill-check-month-202611")).toHaveTextContent("11월 (진행 중)");
  expect(screen.getByTestId("bill-check-month-202609")).toHaveTextContent(/^9월$/);
  for (const name of ["판정", "계약유형", "투입 인원", "투입 MM", "투입 기준 금액", "청구 공급가", "차이", "설명"])
    expect(screen.getByRole("columnheader", { name: new RegExp(name) })).toBeVisible();
  const rows = within(screen.getByTestId("bill-check-list")).getAllByRole("row").slice(1);
  expect(within(rows[0]).getByText("점검 프로젝트")).toBeVisible();
  expect(within(rows[0]).getByText("투입은 있는데 이 달 청구가 없습니다")).toBeVisible();
  expect(within(rows[1]).getByText("–")).toBeVisible();
  expect(screen.queryByTestId("bill-check-planned")).not.toBeInTheDocument();
  expect(screen.getByText("DB에서 조회한 기준금액 차이 계산")).toBeInTheDocument();
});

it("진행 중인 달은 예정 기준 안내를 보이고 월 단추로 다시 읽는다", async () => {
  renderPanel();
  await screen.findByText("점검 프로젝트");
  result = { ...result, checkMonth: "202611", plannedBasis: true };
  fireEvent.click(screen.getByTestId("bill-check-month-202611"));
  expect(await screen.findByTestId("bill-check-planned")).toHaveTextContent(
    "이번 달은 아직 끝나지 않아 투입 예정 기준으로 봅니다.",
  );
  expect(request).toHaveBeenCalledWith("/api/common/bill-checks?checkMonth=202611", expect.anything());
});

it("[청구 등록]은 투입 기준 금액을 채운 창을 열고 고친 공급가로 등록한다", async () => {
  renderPanel();
  fireEvent.click(await screen.findByTestId("bill-check-action-selected-project"));
  expect(await screen.findByTestId("bill-check-message")).toHaveTextContent(
    "10월 투입 기준 금액 8,000,000원으로 채웠습니다. 고쳐서 등록할 수 있습니다.",
  );
  expect(screen.getByTestId("bill-check-supply")).toHaveValue("8,000,000");
  fireEvent.change(screen.getByTestId("bill-check-supply"), { target: { value: "7,900,000" } });
  fireEvent.click(screen.getByTestId("bill-check-save"));
  await waitFor(() => expect(posts()).toHaveLength(1));
  const [url, init] = posts()[0];
  expect(url).toBe("/api/common/bill-checks/missing");
  expect(JSON.parse(String(init.body))).toMatchObject({ projectId: "selected-project", checkMonth: "202610", supplyAmount: 7900000, vatRate: 10 });
  await waitFor(() => expect(onNotice).toHaveBeenCalledWith("점검 프로젝트 2026-10 청구를 등록했습니다."));
});

it("마감 전 달 금액 차이는 그 달 최근 청구를 맞춘 값으로 고치는 창을 연다", async () => {
  result.items = [row({ judgement: "적게 청구", billedSupplyAmount: 2000000, difference: 6000000, latestBillId: "selected-bill", allowedAction: "CORRECTION", explanation: "투입 기준보다 적게 청구했습니다" })];
  renderPanel();
  const action = await screen.findByTestId("bill-check-action-selected-project");
  expect(action).toHaveTextContent("청구 고치기");
  fireEvent.click(action);
  expect(await screen.findByTestId("bill-check-message")).toHaveTextContent(
    "투입 기준에 맞추려면 공급가를 2,000,000원에서 8,000,000원으로 고칩니다(차이 +6,000,000원).",
  );
  expect(screen.getByRole("dialog", { name: "청구 수정 · selected-bill" })).toBeVisible();
  fireEvent.click(screen.getByTestId("bill-check-save"));
  await waitFor(() => expect(posts()[0][0]).toBe("/api/common/bill-checks/correction"));
  // 00-1 동시 수정: 읽은 청구의 수정 시각을 함께 보낸다
  expect(JSON.parse(String(posts()[0][1].body))).toMatchObject({ modifiedAt: "2026-11-15T01:00:00.123456Z", supplyAmount: 8000000 });
  await waitFor(() => expect(onNotice).toHaveBeenCalledWith("청구를 고쳤습니다."));
});

it("[청구 고치기] 중 다른 사람이 먼저 바꿨으면(409) 공통 알림을 띄우고 다시 불러온다", async () => {
  result.items = [row({ judgement: "적게 청구", billedSupplyAmount: 2000000, difference: 6000000, latestBillId: "selected-bill", allowedAction: "CORRECTION" })];
  commandConflict = true;
  renderPanel();
  fireEvent.click(await screen.findByTestId("bill-check-action-selected-project"));
  await screen.findByTestId("bill-check-message");
  const loads = () => request.mock.calls.filter(([url, init]) => !init?.method && String(url).startsWith("/api/common/bill-checks")).length;
  const before = loads();
  fireEvent.click(screen.getByTestId("bill-check-save"));
  await waitFor(() => expect(onNotice).toHaveBeenCalledWith(CONFLICT, "warn"));
  expect(screen.queryByTestId("bill-check-form")).not.toBeInTheDocument();
  await waitFor(() => expect(loads()).toBeGreaterThan(before));
});

it("[청구 등록] 창을 고친 채 닫으면 공통 문구로 묻는다", async () => {
  renderPanel();
  fireEvent.click(await screen.findByTestId("bill-check-action-selected-project"));
  await screen.findByTestId("bill-check-message");
  fireEvent.change(screen.getByTestId("bill-check-tax-number"), { target: { value: "1" } });
  fireEvent.click(screen.getByTestId("bill-check-form-cancel"));
  const confirm = screen.getByRole("alertdialog");
  expect(confirm).toHaveTextContent("저장하지 않은 내용이 있습니다");
  expect(confirm).toHaveTextContent("나가면 입력한 내용이 사라집니다.");
});

it("마감한 달 적게 청구는 추가 청구(차액) 창, 많이 청구는 안내 후 청구 상세", async () => {
  result.items = [
    row({ judgement: "적게 청구", billedSupplyAmount: 5000000, difference: 3000000, latestBillId: "selected-bill", allowedAction: "ADDITIONAL" }),
    row({ projectId: "over", projectName: "많이 낸 프로젝트", judgement: "많이 청구", billedSupplyAmount: 9000000, difference: -1000000, latestBillId: "over-bill", allowedAction: "DETAIL" }),
  ];
  renderPanel();
  fireEvent.click(await screen.findByTestId("bill-check-action-over"));
  expect(onNotice).toHaveBeenCalledWith(
    "10월은 마감한 달이라 청구를 줄일 수 없습니다. 고객사와 정산한 뒤 다음 달 청구에서 빼 주세요.",
    "info",
  );
  expect(onOpenBill).toHaveBeenCalledWith("over-bill");
  fireEvent.click(screen.getByTestId("bill-check-action-selected-project"));
  expect(await screen.findByRole("dialog", { name: "추가 청구 (차액)" })).toBeVisible();
  expect(await screen.findByTestId("bill-check-message")).toHaveTextContent(
    "10월은 마감한 달이라 기존 청구는 고칠 수 없습니다. 모자란 3,000,000원을 같은 달 추가 청구로 등록합니다.",
  );
  expect(screen.getByTestId("bill-check-supply")).toHaveValue("3,000,000");
});

it("서버가 막으면 창에 이유를 보이고 알림을 띄우지 않는다", async () => {
  commandFailure = true;
  renderPanel();
  fireEvent.click(await screen.findByTestId("bill-check-action-selected-project"));
  await screen.findByTestId("bill-check-message");
  fireEvent.click(screen.getByTestId("bill-check-save"));
  await waitFor(() =>
    expect(screen.getByTestId("bill-check-error")).toHaveTextContent("점검 조건이 변경되었습니다. 다시 조회해 주세요."),
  );
  expect(onNotice).not.toHaveBeenCalled();
});

it("공급가를 비우면 보내지 않는다", async () => {
  renderPanel();
  fireEvent.click(await screen.findByTestId("bill-check-action-selected-project"));
  await screen.findByTestId("bill-check-message");
  fireEvent.change(screen.getByTestId("bill-check-supply"), { target: { value: "" } });
  fireEvent.click(screen.getByTestId("bill-check-save"));
  expect(screen.getByTestId("bill-check-error")).toHaveTextContent("공급가를 입력해 주세요.");
  expect(posts()).toHaveLength(0);
});

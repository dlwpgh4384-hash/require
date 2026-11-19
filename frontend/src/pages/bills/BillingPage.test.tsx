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
import { BillingPage } from "./BillingPage";
import { BillingTitleActions, BillingWorkspaceProvider } from "./BillingWorkspace";
import type { Bill, BillList } from "./bills";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, width: 1280 }),
}));
const CONFLICT = "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.";
let bills: Bill[];
let request: ReturnType<typeof vi.fn>;
let duplicate: boolean;
let nextMonthClosed: boolean;
let conflict: boolean;
let nextFailure: string | null;
let createFailure: { field: string; message: string } | null;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const fail = (status: number, message: string, field = "billMonth") => ({
  ok: false,
  status,
  json: async () => ({ success: false, message, errors: [{ field, message }] }),
});
const make = (over: Partial<Bill> = {}): Bill => ({
  billId: "B-202611-001",
  projectId: "P-0001",
  projectName: "시험 프로젝트",
  customerName: "시험 거래처",
  billOrder: 3,
  billMonth: "202611",
  billDate: "2026-11-15",
  supplyAmount: 1000000,
  vatRate: 10,
  vatAmount: 100000,
  totalAmount: 1100000,
  receiptSumAmount: 0,
  unpaidAmount: 1100000,
  payDueDate: "2026-12-25",
  taxInvoiceNo: null,
  status: "UNPAID",
  cancelled: false,
  cancelReason: null,
  cancelledBy: null,
  cancelledAt: null,
  dunningStep: null,
  daysOverdue: 0,
  canEdit: true,
  canCancel: true,
  canReceive: true,
  canCopy: true,
  canNextMonth: true,
  modifiedAt: "2026-11-15T01:00:00.123456Z",
  ...over,
});
const list = (): BillList => {
  const active = bills.filter((b) => !b.cancelled);
  return {
    items: bills,
    readOnly: false,
    summary: {
      yearBillCount: active.length,
      yearBillTotal: active.reduce((s, b) => s + b.totalAmount, 0),
      currentMonthBillTotal: active.filter((b) => b.billMonth === "202611").reduce((s, b) => s + b.totalAmount, 0),
      unpaidCount: active.filter((b) => b.unpaidAmount > 0).length,
      unpaidTotal: active.reduce((s, b) => s + b.unpaidAmount, 0),
      overdueCount: active.filter((b) => b.status === "LATE").length,
    },
    totals: { supplyAmount: 0, vatAmount: 0, totalAmount: 0, receiptSumAmount: 0, unpaidAmount: 0 },
  };
};
const options = (over: Record<string, unknown> = {}) => ({
  projects: [{ projectId: "P-0001", projectName: "시험 프로젝트", customerName: "시험 거래처", projectType: "MO" }],
  statusOptions: [
    { code: "PAID", name: "입금 완료" },
    { code: "UNPAID", name: "미수" },
    { code: "LATE", name: "연체" },
    { code: "CNCL", name: "취소됨" },
  ],
  defaultBillMonth: "202611",
  defaultBillDate: "2026-11-15",
  defaultPayDueDate: "2026-12-25",
  estimatedSupplyAmount: 1200000,
  vatRate: 10,
  monthClosed: false,
  ...over,
});
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
  duplicate = nextMonthClosed = conflict = false;
  nextFailure = null;
  createFailure = null;
  bills = [
    make(),
    make({ billId: "B-202610-001", billMonth: "202610", billDate: "2026-10-15", billOrder: 2, status: "LATE", payDueDate: "2026-11-05", daysOverdue: 10, dunningStep: 1, canCopy: false, canNextMonth: false, canEdit: false, canCancel: false }),
    make({ billId: "B-202609-001", billMonth: "202609", billDate: "2026-09-15", billOrder: 1, cancelled: true, status: "CNCL", unpaidAmount: 0, cancelReason: "중복", canCopy: false, canNextMonth: false, canEdit: false, canCancel: false, canReceive: false }),
  ];
  request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const path = url.split("?")[0];
    const method = init?.method ?? "GET";
    if (path.startsWith("/api/common/user-preferences/")) return ok({ found: false, prefVal: null });
    if (path === "/api/common/bill-options") {
      const month = new URL(url, "http://x").searchParams.get("billMonth");
      return ok(options(month === "202612" && nextMonthClosed ? { monthClosed: true } : {}));
    }
    if (path === "/api/common/bill-checks")
      return ok({
        checkMonth: "202610",
        summary: { totalCount: 2, missingCount: 1, underCount: 1, overCount: 0, matchedCount: 0 },
        items: [
          { projectId: "a", projectName: "a", projectType: "MO", judgement: "청구 없음", headcount: 1, monthlyMm: 1, referenceAmount: 1, billedSupplyAmount: 0, difference: 1, explanation: "", latestBillId: null, allowedAction: "MISSING" },
          { projectId: "b", projectName: "b", projectType: "MO", judgement: "적게 청구", headcount: 1, monthlyMm: 1, referenceAmount: 2, billedSupplyAmount: 1, difference: 1, explanation: "", latestBillId: "x", allowedAction: "CORRECTION" },
        ],
        calculationMethod: "",
        plannedBasis: false,
        months: ["202610"],
        lastClosedMonth: "202610",
      });
    if (path === "/api/common/bill-dunning")
      return ok({ cards: [], items: [{ billId: "B-202610-001" }, { billId: "B-202608-009" }] });
    if (path === "/api/common/bills" && method === "GET") return ok(list());
    if (path === "/api/common/bills/excel") {
      return {
        ok: true,
        status: 200,
        headers: new Headers({
          "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "X-Total-Count": "1",
          "Content-Disposition": "attachment; filename*=UTF-8''%EC%B2%AD%EA%B5%AC.xlsx",
        }),
        blob: async () => new Blob(["x"]),
      };
    }
    if (path === "/api/common/bills" && method === "POST") {
      const body = JSON.parse(String(init?.body));
      if (createFailure) return fail(400, createFailure.message, createFailure.field);
      if (duplicate && !body.duplicateConfirmed)
        return {
          ok: true,
          status: 200,
          json: async () => ({
            success: true,
            meta: {},
            data: null,
            warning: "이 프로젝트의 2026-12 청구가 이미 있습니다(B-202612-001 · 4차). 같은 달 청구를 하나 더 만들려면 [등록]을 한 번 더 누르세요.",
          }),
        };
      const saved = make({ billId: "B-202612-001", billMonth: body.billMonth, supplyAmount: body.supplyAmount });
      return ok({ bill: saved, receipts: [], missingTaxInvoiceNumber: true });
    }
    if (path.endsWith("/next-month")) {
      if (nextFailure) return fail(400, nextFailure);
      return ok({ bill: make({ billId: "B-202612-001", billMonth: "202612", supplyAmount: 1200000 }), receipts: [], missingTaxInvoiceNumber: true });
    }
    if (path.endsWith("/cancel") && method === "POST") {
      if (conflict) return fail(409, CONFLICT, "modifiedAt");
      return ok({ bill: make({ cancelled: true, status: "CNCL" }), receipts: [], missingTaxInvoiceNumber: true });
    }
    if (path.startsWith("/api/common/bills/") && method === "PUT") {
      if (conflict) return fail(409, CONFLICT, "modifiedAt");
      return ok({ bill: make(), receipts: [], missingTaxInvoiceNumber: true });
    }
    if (path.startsWith("/api/common/bills/") && method === "GET") {
      const id = decodeURIComponent(path.split("/").at(-1)!);
      const found = bills.find((b) => b.billId === id) ?? make();
      return ok({
        bill: found,
        receipts: [
          { receiptId: "9", receiptDate: "2026-11-01", amount: 100, cancelled: true, cancelReason: "정정", cancelledBy: "admin", cancelledAt: "2026-11-02T00:00:00Z", canCancel: false, modifiedAt: "2026-11-02T00:00:00Z" },
        ],
        missingTaxInvoiceNumber: !found.taxInvoiceNo,
      });
    }
    throw new Error(`예상하지 않은 요청: ${method} ${url}`);
  });
  vi.stubGlobal("fetch", request);
  vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: vi.fn(() => "blob:x"), revokeObjectURL: vi.fn() }));
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
const mount = () =>
  render(
    <BillingWorkspaceProvider>
      <BillingTitleActions />
      <BillingPage />
    </BillingWorkspaceProvider>,
  );
const calls = (method: string, part: string) =>
  request.mock.calls.filter(([u, i]) => (i?.method ?? "GET") === method && String(u).includes(part));

it("시안 틀: 제목줄 [+ 청구 등록], 연도 줄 설명, 건수 탭, 요약 4칸, 「n차」, 합계 줄", async () => {
  mount();
  await screen.findByTestId("list-row-B-202611-001");
  expect(screen.getByTestId("billing-title-actions")).toContainElement(screen.getByTestId("bill-register"));
  expect(screen.queryByRole("heading", { name: "청구·입금" })).not.toBeInTheDocument();
  expect(screen.getByTestId("bill-year")).toHaveTextContent("그 해에 청구한 건");
  expect(screen.queryByTestId("bill-lock-banner")).not.toBeInTheDocument();
  await waitFor(() => expect(screen.getByTestId("bill-tab-checks-count")).toHaveTextContent("2"));
  expect(screen.getByTestId("bill-tab-dunning")).toHaveTextContent("미수 알림 (2)");
  expect(screen.getByTestId("bill-kpi-year")).toHaveTextContent("2026년 청구");
  expect(screen.getByTestId("bill-kpi-year")).toHaveTextContent("220만 원");
  expect(screen.getByTestId("bill-kpi-year")).toHaveTextContent("2건 · 부가세 포함");
  expect(screen.getByTestId("bill-kpi-month")).toHaveTextContent("110만 원1건");
  expect(screen.getByTestId("bill-kpi-late")).toHaveTextContent("1건입금 예정일 지남");
  expect(screen.getByTestId("list-cell-B-202611-001-billOrder")).toHaveTextContent("3차");
  expect(screen.getByTestId("list-cell-B-202610-001-daysOverdue")).toHaveTextContent("1단계 · 10일");
  expect(screen.getByTestId("list-cell-B-202609-001-status")).toHaveTextContent("취소됨");
  // 취소된 청구는 합계에서 뺀다
  expect(screen.getByTestId("list-footer-totalAmount")).toHaveTextContent("2,200,000");
  expect(screen.queryByText("조회 결과가 없습니다")).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-totals")).not.toBeInTheDocument();
  // 빠른 청구는 가장 최근 청구 줄에만. 가장 최근이 아닌 줄은 빈칸(시안 quickBillBtns)
  expect(screen.getByTestId("bill-copy-B-202611-001")).toBeVisible();
  expect(screen.queryByTestId("bill-copy-B-202610-001")).not.toBeInTheDocument();
  expect(screen.getByTestId("list-cell-B-202610-001-actions")).toBeEmptyDOMElement();
  expect(screen.getByTestId("list-cell-B-202609-001-actions")).toBeEmptyDOMElement();
});

it("빠른 청구: 프로젝트의 가장 최근 청구인데 쓸 수 없으면 「–」, 아니면 빈칸", async () => {
  bills = [
    make({ billId: "B-202611-007", projectId: "P-0002", projectName: "끝나는 프로젝트", canCopy: false, canNextMonth: false }),
    make({ billId: "B-202610-007", projectId: "P-0002", projectName: "끝나는 프로젝트", billMonth: "202610", canCopy: false, canNextMonth: false }),
  ];
  mount();
  await screen.findByTestId("list-row-B-202611-007");
  expect(screen.getByTestId("list-cell-B-202611-007-actions")).toHaveTextContent("–");
  expect(screen.getByTestId("list-cell-B-202610-007-actions")).toBeEmptyDOMElement();
});

it("청구월 조건은 청구가 있는 달만 고르고 [조회]·[초기화]로 화면에서 거른다", async () => {
  mount();
  await screen.findByTestId("list-row-B-202611-001");
  const month = screen.getByTestId("bill-filter-billMonth");
  expect([...month.querySelectorAll("option")].map((o) => o.textContent)).toEqual(["전체", "2026-10", "2026-11"]);
  fireEvent.change(month, { target: { value: "202610" } });
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() => expect(screen.queryByTestId("list-row-B-202611-001")).not.toBeInTheDocument());
  expect(screen.getByTestId("list-row-B-202610-001")).toBeVisible();
  fireEvent.click(screen.getByTestId("list-reset"));
  expect(await screen.findByTestId("list-row-B-202611-001")).toBeVisible();
  expect(calls("GET", "/api/common/bills?")).toHaveLength(1);
});

it("[엑셀 다운로드]는 목록 도구줄에만 있고 지금 조건으로 받는다", async () => {
  mount();
  await screen.findByTestId("list-row-B-202611-001");
  expect(screen.getByTestId("billing-title-actions")).not.toHaveTextContent("엑셀");
  fireEvent.change(screen.getByTestId("bill-filter-status"), { target: { value: "LATE" } });
  fireEvent.click(screen.getByTestId("list-search"));
  fireEvent.click(screen.getByTestId("list-download"));
  await waitFor(() => expect(screen.getByTestId("bill-toast")).toHaveTextContent("엑셀 파일을 만들었습니다 (조회 결과 1건)"));
  expect(calls("GET", "/api/common/bills/excel")[0][0]).toBe("/api/common/bills/excel?year=2026&status=LATE");
});

it("[고쳐서 청구]는 다음 달 등록 창을 시안 문구로 채우고 원청구 번호를 함께 보낸다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("bill-copy-B-202611-001"));
  const dialog = await screen.findByRole("dialog", { name: "청구 등록 · 지난 청구 복사" });
  expect(within(dialog).getByTestId("bill-form-message")).toHaveTextContent(
    "B-202611-001(2026-11) 청구를 바탕으로 2026-12 청구를 채웠습니다. 고칠 곳만 고쳐서 등록하세요.",
  );
  expect(within(dialog).getByTestId("bill-form-month")).toHaveValue("2026-12");
  expect(within(dialog).getByTestId("bill-form-supplyAmount")).toHaveValue("1,200,000");
  expect(within(dialog).getByTestId("bill-form-vatRate")).toHaveValue("10");
  expect(within(dialog).getByTestId("bill-form-taxInvoiceNo")).toHaveValue("");
  expect(within(dialog).getByTestId("bill-form-payDueDate")).toHaveValue("2027-01-25");
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  await waitFor(() => expect(calls("POST", "/api/common/bills")).toHaveLength(1));
  expect(JSON.parse(String(calls("POST", "/api/common/bills")[0][1].body))).toMatchObject({
    projectId: "P-0001",
    billMonth: "202612",
    supplyAmount: 1200000,
    sourceBillId: "B-202611-001",
    duplicateConfirmed: false,
  });
  expect(await screen.findByTestId("bill-toast")).toHaveTextContent("시험 프로젝트 2026-12 청구를 등록했습니다.");
});

it("다음 달이 마감이면 [고쳐서 청구] 창을 열지 않고 시안 안내를 띄운다", async () => {
  nextMonthClosed = true;
  mount();
  fireEvent.click(await screen.findByTestId("bill-copy-B-202611-001"));
  expect(await screen.findByTestId("bill-toast")).toHaveTextContent("2026-12은 마감한 달이라 청구를 만들 수 없습니다.");
  expect(screen.queryByTestId("bill-form")).not.toBeInTheDocument();
});

it("같은 달 청구가 있으면 경고하고 [등록]을 한 번 더 누르면 만든다", async () => {
  duplicate = true;
  mount();
  fireEvent.click(await screen.findByTestId("bill-copy-B-202611-001"));
  const dialog = await screen.findByRole("dialog", { name: "청구 등록 · 지난 청구 복사" });
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  await waitFor(() =>
    expect(within(dialog).getByTestId("bill-form-error")).toHaveTextContent(
      "이 프로젝트의 2026-12 청구가 이미 있습니다(B-202612-001 · 4차). 같은 달 청구를 하나 더 만들려면 [등록]을 한 번 더 누르세요.",
    ),
  );
  expect(within(dialog).getByTestId("bill-form-save")).toHaveTextContent("등록");
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  await waitFor(() => expect(calls("POST", "/api/common/bills")).toHaveLength(2));
  expect(JSON.parse(String(calls("POST", "/api/common/bills")[1][1].body)).duplicateConfirmed).toBe(true);
});

it("[다음 달 청구]는 바로 만들고 시안 알림을, 막히면 서버 안내를 띄운다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("bill-next-B-202611-001"));
  expect(await screen.findByTestId("bill-toast")).toHaveTextContent(
    "시험 프로젝트 2026-12 청구를 만들었습니다(공급가 1,200,000원). 세금계산서 번호는 나중에 넣으세요.",
  );
  nextFailure = "2026-12은 마감한 달이라 청구를 만들 수 없습니다.";
  fireEvent.click(await screen.findByTestId("bill-next-B-202611-001"));
  await waitFor(() =>
    expect(screen.getByTestId("bill-toast")).toHaveTextContent("2026-12은 마감한 달이라 청구를 만들 수 없습니다."),
  );
});

it("줄을 누르면 오른쪽 상세(청구 정보·입금 내역 (n))와 머리 단추가 열린다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("list-row-B-202611-001"));
  const panel = await screen.findByTestId("bill-detail");
  expect(await within(panel).findByTestId("bill-detail-tab-receipts")).toHaveTextContent("입금 내역 (0)");
  expect(within(panel).getByTestId("bill-detail-no-invoice")).toHaveTextContent("번호 미입력");
  expect(within(panel).getByTestId("kv-row-부가세")).toHaveTextContent("100,000 원 (10%)");
  expect(within(panel).getByTestId("kv-row-차수")).toHaveTextContent("3차");
  for (const id of ["bill-detail-edit", "bill-detail-cancel", "bill-detail-receive"])
    expect(within(panel).getByTestId(id)).toBeVisible();
  fireEvent.click(within(panel).getByTestId("bill-detail-tab-receipts"));
  expect(within(panel).getByTestId("receipt-row-9")).toHaveTextContent("취소됨 · 정정");
});

it("[수정]은 수정 시각을 보내고, 다른 사람이 먼저 바꿨으면(409) 알리고 다시 읽는다", async () => {
  conflict = true;
  mount();
  fireEvent.click(await screen.findByTestId("list-row-B-202611-001"));
  fireEvent.click(await screen.findByTestId("bill-detail-edit"));
  const dialog = await screen.findByRole("dialog", { name: "청구 수정 · B-202611-001" });
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  await waitFor(() => expect(screen.getByTestId("bill-toast")).toHaveTextContent(CONFLICT));
  const put = calls("PUT", "/api/common/bills/B-202611-001")[0];
  expect(JSON.parse(String(put[1].body))).toMatchObject({ modifiedAt: "2026-11-15T01:00:00.123456Z", supplyAmount: 1000000 });
  await waitFor(() => expect(calls("GET", "/api/common/bills?").length).toBeGreaterThan(1));
});

it("[청구 취소]는 사유와 수정 시각을 보내고 시안 알림을 띄운다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("list-row-B-202611-001"));
  fireEvent.click(await screen.findByTestId("bill-detail-cancel"));
  const dialog = await screen.findByTestId("bill-cancel-dialog");
  expect(dialog).toHaveTextContent("시험 프로젝트 · B-202611-001 (3차, 2026-11, 합계 1,100,000원)을 취소합니다.");
  fireEvent.click(within(dialog).getByTestId("bill-cancel-confirm"));
  expect(within(dialog).getByTestId("bill-cancel-error")).toHaveTextContent("취소 사유를 입력해 주세요.");
  fireEvent.change(within(dialog).getByTestId("bill-cancel-reason"), { target: { value: "두 번 등록" } });
  fireEvent.click(within(dialog).getByTestId("bill-cancel-confirm"));
  expect(await screen.findByTestId("bill-toast")).toHaveTextContent("B-202611-001 청구를 취소했습니다. 기록은 남습니다.");
  expect(JSON.parse(String(calls("POST", "/cancel")[0][1].body))).toEqual({ reason: "두 번 등록", modifiedAt: "2026-11-15T01:00:00.123456Z" });
});

it("새 청구 등록 창: 프로젝트를 고르면 추정 공급가를 채우고 칸을 검사한다", async () => {
  mount();
  await screen.findByTestId("list-row-B-202611-001");
  fireEvent.click(screen.getByTestId("bill-register"));
  const dialog = await screen.findByRole("dialog", { name: "청구 등록" });
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  // 틀린 칸 밑에 이유를 보이고 첫 오류 칸으로 간다(00-1 FR-0106)
  expect(within(dialog).getByTestId("bill-form-error-projectId")).toHaveTextContent("프로젝트를 골라 주세요.");
  expect(within(dialog).getByTestId("bill-form-project")).toHaveAttribute("aria-invalid", "true");
  await waitFor(() => expect(document.activeElement).toBe(within(dialog).getByTestId("bill-form-project")));
  expect(within(dialog).getByTestId("bill-form-error")).toBeEmptyDOMElement();
  await waitFor(() => expect(within(dialog).getByTestId("bill-form-project").querySelectorAll("option").length).toBe(2));
  fireEvent.change(within(dialog).getByTestId("bill-form-project"), { target: { value: "P-0001" } });
  await waitFor(() => expect(within(dialog).getByTestId("bill-form-supplyAmount")).toHaveValue("1,200,000"));
  expect(within(dialog).getByTestId("bill-form-vatRate")).toHaveValue("10");
  fireEvent.change(within(dialog).getByTestId("bill-form-vatRate"), { target: { value: "101" } });
  expect(within(dialog).queryByTestId("bill-form-error-projectId")).not.toBeInTheDocument();
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  expect(within(dialog).getByTestId("bill-form-error-vatRate")).toHaveTextContent("부가세율을 0~100 사이로 입력해 주세요.");
  fireEvent.change(within(dialog).getByTestId("bill-form-vatRate"), { target: { value: "10" } });
  fireEvent.change(within(dialog).getByTestId("bill-form-payDueDate"), { target: { value: "2026-11-01" } });
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  expect(within(dialog).getByTestId("bill-form-error-payDueDate")).toHaveTextContent("입금 예정일이 청구일보다 빠릅니다.");
  fireEvent.change(within(dialog).getByTestId("bill-form-billDate"), { target: { value: "" } });
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  expect(within(dialog).getByTestId("bill-form-error-billDate")).toHaveTextContent("청구일을 입력해 주세요.");
  await waitFor(() => expect(document.activeElement).toBe(within(dialog).getByTestId("bill-form-billDate")));
  expect(calls("POST", "/api/common/bills")).toHaveLength(0);
});

it("서버 칸 오류(errors[].field)는 그 칸 밑에 붙인다", async () => {
  createFailure = { field: "taxInvoiceNo", message: "세금계산서 번호는 30자까지 입력해 주세요." };
  mount();
  fireEvent.click(await screen.findByTestId("bill-copy-B-202611-001"));
  const dialog = await screen.findByRole("dialog", { name: "청구 등록 · 지난 청구 복사" });
  fireEvent.click(within(dialog).getByTestId("bill-form-save"));
  expect(await within(dialog).findByTestId("bill-form-error-taxInvoiceNo")).toHaveTextContent(
    "세금계산서 번호는 30자까지 입력해 주세요.",
  );
  expect(within(dialog).getByTestId("bill-form-taxInvoiceNo")).toHaveAttribute("aria-invalid", "true");
  await waitFor(() => expect(document.activeElement).toBe(within(dialog).getByTestId("bill-form-taxInvoiceNo")));
  expect(within(dialog).getByTestId("bill-form-error")).toBeEmptyDOMElement();
});

it("등록 창을 고친 채 닫으면 공통 문구로 묻는다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("bill-copy-B-202611-001"));
  const dialog = await screen.findByRole("dialog", { name: "청구 등록 · 지난 청구 복사" });
  fireEvent.change(within(dialog).getByTestId("bill-form-taxInvoiceNo"), { target: { value: "1" } });
  fireEvent.click(within(dialog).getByTestId("bill-form-cancel"));
  const confirm = within(dialog).getByTestId("bill-form-dirty");
  expect(confirm).toHaveTextContent("저장하지 않은 내용이 있습니다");
  expect(confirm).toHaveTextContent("나가면 입력한 내용이 사라집니다.");
  fireEvent.click(within(confirm).getByRole("button", { name: "나가기" }));
  await waitFor(() => expect(screen.queryByTestId("bill-form")).not.toBeInTheDocument());
});

it("[청구 취소] 창은 사유를 적은 채 닫으면 공통 문구로 묻는다", async () => {
  mount();
  fireEvent.click(await screen.findByTestId("list-row-B-202611-001"));
  fireEvent.click(await screen.findByTestId("bill-detail-cancel"));
  const dialog = await screen.findByTestId("bill-cancel-dialog");
  fireEvent.change(within(dialog).getByTestId("bill-cancel-reason"), { target: { value: "잘못 등록" } });
  fireEvent.click(within(dialog).getByTestId("bill-cancel-back"));
  const confirm = within(dialog).getByTestId("bill-cancel-dirty");
  expect(confirm).toHaveTextContent("저장하지 않은 내용이 있습니다");
  expect(confirm).toHaveTextContent("나가면 입력한 내용이 사라집니다.");
  fireEvent.click(within(confirm).getByRole("button", { name: "취소" }));
  expect(within(dialog).getByTestId("bill-cancel-reason")).toHaveValue("잘못 등록");
  fireEvent.click(within(dialog).getByTestId("bill-cancel-back"));
  fireEvent.click(within(within(dialog).getByTestId("bill-cancel-dirty")).getByRole("button", { name: "나가기" }));
  await waitFor(() => expect(screen.queryByTestId("bill-cancel-dialog")).not.toBeInTheDocument());
  expect(calls("POST", "/cancel")).toHaveLength(0);
});

it("지난 해는 탭 없이 읽기 전용 띠를 보이고 등록·빠른 청구를 숨긴다", async () => {
  mount();
  await screen.findByTestId("list-row-B-202611-001");
  fireEvent.click(screen.getByTestId("bill-year-prev"));
  expect(await screen.findByTestId("bill-lock-banner")).toHaveTextContent("2025년은 지난 해라 읽기만 할 수 있습니다.");
  expect(screen.queryByTestId("bill-tab-list")).not.toBeInTheDocument();
  expect(screen.queryByTestId("bill-register")).not.toBeInTheDocument();
  await waitFor(() => expect(calls("GET", "/api/common/bills?year=2025")).toHaveLength(1));
  expect(screen.queryByTestId("bill-copy-B-202611-001")).not.toBeInTheDocument();
  expect(screen.getByTestId("bill-year-next")).not.toBeDisabled();
});

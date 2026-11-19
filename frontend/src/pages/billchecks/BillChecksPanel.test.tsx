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
let queryFailure: boolean;
let optionsFailure: boolean;
let commandFailure: boolean;
let detailFailure: boolean;
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
  explanation: "새 투입자 김투입의 금액과 같습니다.",
  latestBillId: null,
  allowedAction: "MISSING",
  ...overrides,
});
const renderPanel = (onOpenBill?: (id: string) => void) =>
  render(
    <MemoryRouter>
      <BillChecksPanel onOpenBill={onOpenBill} />
    </MemoryRouter>,
  );
const posts = () =>
  request.mock.calls.filter(([, init]) => init?.method === "POST");
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T01:00:00Z"));
  queryFailure = optionsFailure = commandFailure = detailFailure = false;
  result = {
    checkMonth: "202610",
    summary: {
      totalCount: 1,
      missingCount: 1,
      underCount: 0,
      overCount: 0,
      matchedCount: 0,
    },
    items: [row()],
    calculationMethod: "DB에서 조회한 기준금액 차이 계산",
    plannedBasis: false,
  };
  detail = {
    bill: {
      billId: "selected-bill",
      projectId: "selected-project",
      projectName: "점검 프로젝트",
      customerName: "고객사",
      billOrder: 2,
      billMonth: "202610",
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
    },
    receipts: [],
    missingTaxInvoiceNumber: true,
  };
  request = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (init?.method === "POST") {
      if (commandFailure)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: "점검 조건이 변경되었습니다.",
            errors: [
              { field: "checkMonth", message: "점검 월을 다시 조회해 주세요." },
            ],
          }),
        };
      result = {
        ...result,
        items: [
          row({
            judgement: "맞음",
            billedSupplyAmount: 8000000,
            difference: 0,
            allowedAction: "NONE",
          }),
        ],
      };
      return ok(detail);
    }
    if (path.startsWith("/api/common/bill-options?")) {
      if (optionsFailure) throw new Error("등록 정보 조회 실패");
      return ok({
        defaultBillDate: "2026-11-15",
        defaultPayDueDate: "2026-11-25",
        vatRate: 10,
        projects: [
          {
            projectId: "selected-project",
            projectName: "점검 프로젝트",
            customerName: "고객사",
          },
        ],
      });
    }
    if (path.startsWith("/api/common/bills/")) {
      if (detailFailure) throw new Error("대상 청구가 없습니다.");
      return ok(detail);
    }
    if (path.startsWith("/api/common/bill-checks")) {
      if (queryFailure) throw new Error("점검 조회 실패");
      return ok(result);
    }
    throw new Error(`예상하지 않은 요청: ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

it("서버가 정한 마지막 마감월을 먼저 조회하고 다섯 요약과 DB 계산설명을 표시한다", async () => {
  renderPanel();
  await screen.findByText("점검 프로젝트");
  expect(request.mock.calls[0][0]).toBe("/api/common/bill-checks");
  expect(screen.getByLabelText("점검 월")).toHaveValue("2026-10");
  const summary = screen.getByLabelText("점검 요약");
  for (const label of ["전체", "청구 없음", "적게 청구", "많이 청구", "맞음"])
    expect(within(summary).getByText(label)).toBeVisible();
  expect(
    screen.getByText("DB에서 조회한 기준금액 차이 계산"),
  ).toBeInTheDocument();
});
it("미마감 진행월은 예정 기준임을 보여 준다", async () => {
  result.plannedBasis = true;
  renderPanel();
  expect(
    await screen.findByText("진행 중인 달은 예정 기준입니다."),
  ).toBeVisible();
});
it("0건이어도 조회와 요약과 표머리를 남긴다", async () => {
  result.items = [];
  result.summary = {
    totalCount: 0,
    missingCount: 0,
    underCount: 0,
    overCount: 0,
    matchedCount: 0,
  };
  renderPanel();
  expect(await screen.findByText("조회 결과가 없습니다")).toBeVisible();
  expect(
    screen.getByRole("columnheader", { name: /투입 기준 금액/ }),
  ).toBeVisible();
  expect(screen.getByTestId("bill-check-search")).toBeEnabled();
  expect(
    screen.queryByTestId("bill-check-action-selected-project"),
  ).not.toBeInTheDocument();
});
it("조회 실패는 지역 오류와 같은 조건 재시도로 복구한다", async () => {
  queryFailure = true;
  renderPanel();
  expect(await screen.findByText("점검 조회 실패")).toBeVisible();
  queryFailure = false;
  fireEvent.click(screen.getByTestId("bill-check-retry"));
  expect(await screen.findByText("점검 프로젝트")).toBeVisible();
  expect(posts()).toHaveLength(0);
});
it("월 버튼은 승인된 checkMonth 하나만 전달한다", async () => {
  renderPanel();
  await screen.findByText("점검 프로젝트");
  fireEvent.click(screen.getByTestId("bill-check-month-09"));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/api/common/bill-checks?checkMonth=202609",
      expect.anything(),
    ),
  );
});
it.each([
  ["MISSING", "청구 등록"],
  ["ADDITIONAL", "추가 청구"],
  ["CORRECTION", "청구 고치기"],
] as const)(
  "%s 점검행에는 그 상태의 한 행동만 보인다",
  async (action, label) => {
    result.items = [
      row({ allowedAction: action, latestBillId: "selected-bill" }),
    ];
    renderPanel();
    expect(await screen.findByRole("button", { name: label })).toBeVisible();
    for (const other of ["청구 등록", "추가 청구", "청구 고치기"].filter(
      (value) => value !== label,
    ))
      expect(
        screen.queryByRole("button", { name: other }),
      ).not.toBeInTheDocument();
  },
);
it("진행 중 선택지에 없는 과거 점검 프로젝트도 월 기본값으로 등록한다", async () => {
  result.items = [
    row({ projectId: "ended-project", projectName: "종료 프로젝트" }),
  ];
  renderPanel();
  fireEvent.click(await screen.findByRole("button", { name: "청구 등록" }));
  await waitFor(() =>
    expect(screen.getByTestId("bill-check-save")).toBeEnabled(),
  );
  const optionsCall = request.mock.calls.find(([path]) =>
    String(path).startsWith("/api/common/bill-options?"),
  );
  expect(optionsCall).toBeDefined();
  const query = new URLSearchParams(String(optionsCall![0]).split("?")[1]);
  expect(query.get("billMonth")).toBe("202610");
  expect(query.has("projectId")).toBe(false);
  fireEvent.click(screen.getByTestId("bill-check-save"));
  await waitFor(() => expect(posts()).toHaveLength(1));
  expect(JSON.parse(String(posts()[0][1].body))).toMatchObject({
    projectId: "ended-project",
    checkMonth: "202610",
  });
});
it("맞음과 읽기 전용 행은 저장 행동이 없다", async () => {
  result.items = [row({ allowedAction: "NONE", judgement: "맞음" })];
  renderPanel();
  await screen.findByText("점검 프로젝트");
  expect(
    screen.queryByTestId("bill-check-action-selected-project"),
  ).not.toBeInTheDocument();
});
it("마감월 많이청구는 감액없이 안내 후 선택한 원청구 상세로 간다", async () => {
  result.items = [
    row({
      allowedAction: "DETAIL",
      judgement: "많이 청구",
      latestBillId: "selected-bill",
      difference: -1000000,
    }),
  ];
  const open = vi.fn();
  renderPanel(open);
  fireEvent.click(await screen.findByRole("button", { name: "상세 확인" }));
  expect(open).toHaveBeenCalledWith("selected-bill");
  expect(screen.getByTestId("bill-check-notice")).toHaveTextContent(
    "마감한 달은 감액할 수 없습니다",
  );
  expect(screen.queryByTestId("bill-check-form")).not.toBeInTheDocument();
  expect(posts()).toHaveLength(0);
});
it("부모 상세 핸들러 없이도 승인된 상세 GET으로 읽기 전용 상세를 열 수 있다", async () => {
  result.items = [
    row({ allowedAction: "DETAIL", latestBillId: "selected-bill" }),
  ];
  renderPanel();
  fireEvent.click(await screen.findByRole("button", { name: "상세 확인" }));
  const panel = await screen.findByTestId("bill-check-detail");
  await within(panel).findByText("selected-bill");
  expect(within(panel).getByText("번호 미입력")).toBeVisible();
  expect(
    within(panel).queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(posts()).toHaveLength(0);
});
it.each([
  ["MISSING", "missing", 8000000],
  ["ADDITIONAL", "additional", 5000000],
  ["CORRECTION", "correction", 7000000],
] as const)(
  "%s 창은 서버 공급가를 고정하고 전용 명령 후 같은 월을 재조회한다",
  async (action, endpoint, supply) => {
    result.items = [
      row({
        allowedAction: action,
        latestBillId: action === "MISSING" ? null : "selected-bill",
        difference: 5000000,
      }),
    ];
    renderPanel();
    fireEvent.click(
      await screen.findByTestId("bill-check-action-selected-project"),
    );
    const form = await screen.findByTestId("bill-check-form");
    const submit = within(form).getByTestId("bill-check-save");
    await waitFor(() => expect(submit).toBeEnabled());
    expect(within(form).getByTestId("bill-check-project")).toHaveAttribute(
      "readonly",
    );
    expect(within(form).getByTestId("bill-check-project")).toHaveValue(
      "점검 프로젝트 · 고객사",
    );
    expect(within(form).getByTestId("bill-check-month-fixed")).toHaveAttribute(
      "readonly",
    );
    expect(within(form).getByTestId("bill-check-supply")).toHaveValue(
      String(supply),
    );
    expect(within(form).getByTestId("bill-check-supply")).toHaveAttribute(
      "readonly",
    );
    fireEvent.click(submit);
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0][0]).toBe(`/api/common/bill-checks/${endpoint}`);
    const body = JSON.parse(String(posts()[0][1].body));
    expect(body.projectId).toBe("selected-project");
    expect(body.checkMonth).toBe("202610");
    expect(Object.keys(body).sort()).toEqual(
      [
        "projectId",
        "checkMonth",
        "vatRate",
        "billDate",
        "payDueDate",
        "taxInvoiceNo",
      ].sort(),
    );
    expect(await screen.findByTestId("bill-check-toast")).toHaveTextContent(
      "청구를 저장했습니다",
    );
    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/common/bill-checks?checkMonth=202610",
        expect.anything(),
      ),
    );
  },
);
it("점검 옵션 실패는 저장을 막고 다시 시도할 수 있다", async () => {
  optionsFailure = true;
  renderPanel();
  fireEvent.click(
    await screen.findByTestId("bill-check-action-selected-project"),
  );
  expect(await screen.findByText("등록 정보 조회 실패")).toBeVisible();
  expect(screen.getByTestId("bill-check-save")).toBeDisabled();
  optionsFailure = false;
  fireEvent.click(screen.getByTestId("bill-check-options-retry"));
  await waitFor(() =>
    expect(screen.getByTestId("bill-check-save")).toBeEnabled(),
  );
});
it("400 필드 오류를 표시하고 실패를 성공으로 알리지 않는다", async () => {
  commandFailure = true;
  renderPanel();
  fireEvent.click(
    await screen.findByTestId("bill-check-action-selected-project"),
  );
  await waitFor(() =>
    expect(screen.getByTestId("bill-check-save")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("bill-check-save"));
  expect(
    await screen.findByText("점검 월을 다시 조회해 주세요."),
  ).toBeVisible();
  expect(screen.getByTestId("bill-check-form")).toBeVisible();
  expect(screen.queryByTestId("bill-check-toast")).not.toBeInTheDocument();
});
it("변경한 입력을 닫으면 버리기 확인만 하고 저장 요청은 보내지 않는다", async () => {
  renderPanel();
  fireEvent.click(
    await screen.findByTestId("bill-check-action-selected-project"),
  );
  await waitFor(() =>
    expect(screen.getByTestId("bill-check-save")).toBeEnabled(),
  );
  fireEvent.change(screen.getByTestId("bill-check-tax-number"), {
    target: { value: "수정한 번호" },
  });
  fireEvent.click(screen.getByTestId("bill-check-form-cancel"));
  expect(
    screen.getByRole("alertdialog", { name: "저장하지 않고 닫을까요?" }),
  ).toBeVisible();
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(screen.queryByTestId("bill-check-form")).not.toBeInTheDocument();
  expect(posts()).toHaveLength(0);
});
it("상세 조회 실패는 저장을 노출하지 않고 다시 조회한다", async () => {
  detailFailure = true;
  result.items = [
    row({ allowedAction: "DETAIL", latestBillId: "selected-bill" }),
  ];
  renderPanel();
  fireEvent.click(await screen.findByRole("button", { name: "상세 확인" }));
  expect(await screen.findByText("대상 청구가 없습니다.")).toBeVisible();
  expect(screen.queryByTestId("bill-check-save")).not.toBeInTheDocument();
  detailFailure = false;
  fireEvent.click(screen.getByTestId("bill-check-detail-retry"));
  expect(await screen.findByText("selected-bill")).toBeVisible();
  expect(posts()).toHaveLength(0);
});
it("입금 예정일 검증은 명령 전에 필드 오류로 표시한다", async () => {
  renderPanel();
  fireEvent.click(
    await screen.findByTestId("bill-check-action-selected-project"),
  );
  await waitFor(() =>
    expect(screen.getByTestId("bill-check-save")).toBeEnabled(),
  );
  fireEvent.change(screen.getByTestId("bill-check-due-date"), {
    target: { value: "2026-11-01" },
  });
  fireEvent.click(screen.getByTestId("bill-check-save"));
  expect(
    await screen.findByText("입금 예정일은 청구일 이후여야 합니다."),
  ).toBeVisible();
  expect(posts()).toHaveLength(0);
});
it("변경 없는 취소는 별도 확인이나 저장 없이 닫힌다", async () => {
  renderPanel();
  fireEvent.click(
    await screen.findByTestId("bill-check-action-selected-project"),
  );
  await waitFor(() =>
    expect(screen.getByTestId("bill-check-save")).toBeEnabled(),
  );
  fireEvent.click(screen.getByTestId("bill-check-form-cancel"));
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  expect(posts()).toHaveLength(0);
});

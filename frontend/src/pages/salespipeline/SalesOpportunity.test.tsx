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
import { SalesOpportunityForm } from "./SalesOpportunityForm";
import { SalesOpportunityDetail } from "./SalesOpportunityDetail";
import {
  SALES_QUOTE_URL,
  SALES_TENTATIVE_CHECK_URL,
  salesOpportunityErrors,
  salesOpportunityPayload,
  salesWinDefaults,
  type SalesOpportunity,
  type SalesOpportunityInput,
  type SalesOpportunityOptions,
} from "./salesOpportunities";

vi.mock("../projects/ProjectLink", () => ({
  ProjectLink: ({ projectId, name }: { projectId: string; name: string }) => (
    <button data-testid="sales-opportunity-project" data-project-id={projectId}>
      {name}
    </button>
  ),
}));
const options: SalesOpportunityOptions = {
  codes: {
    SALES_STEP: [
      { code: "FIND", codeName: "발굴" },
      { code: "PROP", codeName: "제안" },
      { code: "NEGO", codeName: "협상" },
    ],
    JOB: [{ code: "BE", codeName: "백엔드", estimateUnitPrice: 9000000 }],
    GRADE: [
      { code: "MD", codeName: "중급" },
      { code: "HIGH", codeName: "고급" },
    ],
    CTRT_TYPE: [
      { code: "TK", codeName: "턴키" },
      { code: "MO", codeName: "월단위" },
      { code: "DP", codeName: "파견" },
      { code: "IN", codeName: "내부(자체 개발)" },
    ],
    PROJ_STAT: [
      { code: "ING", codeName: "진행" },
      { code: "PRE", codeName: "수주예정" },
      { code: "END", codeName: "종료" },
    ],
    LOSE_RSN: [{ code: "PRICE", codeName: "가격" }],
    APRV_STAT: [
      { code: "NONE", codeName: "승인 불필요" },
      { code: "WAIT", codeName: "승인 대기" },
    ],
    OPP_STAT: [
      { code: "ING", codeName: "진행" },
      { code: "WIN", codeName: "수주" },
      { code: "LOSE", codeName: "실주" },
      { code: "STOP", codeName: "사용 안 함" },
    ],
    APRV_ACT: [
      { code: "REQ", codeName: "요청" },
      { code: "REJ", codeName: "반려" },
    ],
  },
  customers: [{ clientId: "customer", clientName: "고객" }],
  parts: [{ partId: "part", partName: "파트" }],
  developers: [
    { developerId: "dev", name: "개발자", primaryJobCode: "BE" },
    { developerId: "dev2", name: "둘째", primaryJobCode: "BE" },
  ],
};
let row: SalesOpportunity;
let request: ReturnType<typeof vi.fn>;
let fail: number;
let newCheckStatus: number;
let handedOff: boolean;
let quoteBody: Record<string, unknown>;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
/** 저장·처리 요청만(실시간 견적·가배정 경고 확인은 빼고). */
const writes = () =>
  request.mock.calls.filter(
    (call) =>
      ["POST", "PUT"].includes(
        (call[1] as RequestInit | undefined)?.method || "",
      ) &&
      call[0] !== SALES_QUOTE_URL &&
      !String(call[0]).endsWith("/tentative-assignment-check"),
  );
const calls = (url: string) =>
  request.mock.calls.filter((call) => call[0] === url);
beforeEach(() => {
  fail = 0;
  newCheckStatus = 200;
  handedOff = false;
  quoteBody = {
    expectedRevenue: 60000000,
    expectedCost: 51000000,
    profitRate: 15.0399,
    minimumUnitPrice: 8500000,
    targetUnitPrice: 10630000,
    breakEvenAmount: 51000000,
    targetAmount: 64000000,
    approvalRate: 15,
    targetRate: 20,
    profitBand: "목표 미달",
    needQuotes: [
      {
        rowIndex: 0,
        minimumUnitPrice: 8500000,
        targetUnitPrice: 10630000,
      },
    ],
  };
  row = {
    opportunityId: "opp",
    opportunityName: "기회",
    customerId: "customer",
    customerName: "고객",
    salesPartId: "part",
    stageCode: "FIND",
    winningProbability: 10,
    expectedAmount: 60000000,
    contractTypeCode: null,
    startMonth: "2026-11",
    expectedMonths: 6,
    workplace: "서울",
    nextAction: null,
    nextActionDueDate: null,
    memo: null,
    needs: [
      {
        needSn: 3,
        jobCode: "BE",
        gradeCode: null,
        requiredCount: 1,
        startMonth: "2026-11",
        months: 6,
        monthlyMm: 1,
        estimatedUnitPrice: null,
        tentativeDeveloperIds: [],
      },
    ],
    modifiedAt: "2026-11-15T00:00:00Z",
    approvalStatusCode: "WAIT",
    opportunityStatusCode: "ING",
    projectId: null,
    resultDate: null,
    lossReasonCode: null,
    approvalHistory: [],
  };
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/sales-pipeline")
      return ok({
        stages: [
          { stageCode: "FIND", defaultProbability: 10 },
          { stageCode: "PROP", defaultProbability: 30 },
          { stageCode: "NEGO", defaultProbability: 60 },
        ],
      });
    if (init?.method) {
      if (path === SALES_QUOTE_URL) return ok(quoteBody);
      if (path === SALES_TENTATIVE_CHECK_URL) {
        if (newCheckStatus !== 200)
          return {
            ok: false,
            status: newCheckStatus,
            json: async () => ({ success: false, message: "없음" }),
          };
        // 실제 서버 모양: 사람 한 명씩 묻고, needSn null = 묻은 그 줄, 숫자 = 같은 사람이 있는 저장된 다른 줄.
        const body = JSON.parse(String(init.body));
        const warnings: { needSn: number | null; developerId: string; message: string }[] = [];
        if (body.developerId === "dev")
          warnings.push({
            needSn: null,
            developerId: "dev",
            message: "「다른 기회」에 가배정되어 있습니다. 저장하면 이 기회로 옮깁니다.",
          });
        if (body.opportunityId === "opp" && body.developerId === "dev2")
          warnings.push(
            { needSn: null, developerId: "dev2", message: "같은 사람이 두 줄에 있습니다." },
            { needSn: 3, developerId: "dev2", message: "같은 사람이 두 줄에 있습니다." },
            {
              needSn: null,
              developerId: "dev2",
              message: "해당 월에 1.0 MM 이상 확정 투입되어 있습니다.",
            },
          );
        return ok({ warnings });
      }
      if (handedOff && init.method === "PUT" && path === "/api/common/sales-opportunities/opp")
        return {
          ok: false,
          status: 409,
          json: async () => ({
            success: false,
            message: "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.",
            errors: [
              {
                field: "needs[0].tentativeDeveloperIds",
                message: "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.",
              },
            ],
          }),
        };
      if (fail)
        return {
          ok: false,
          status: fail,
          json: async () => ({
            success: false,
            message:
              fail === 409
                ? "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."
                : "저장 실패",
            errors: [
              { field: "opportunityName", message: "기회명을 확인해 주세요." },
            ],
          }),
        };
      const body = JSON.parse(String(init.body));
      if (path.endsWith("/reject")) {
        row.approvalStatusCode = "REJ";
        row.approvalHistory = [
          {
            actionCode: "REJ",
            actedAt: "2026-11-15T16:00:00Z",
            reason: body.reason || null,
          },
        ];
      } else if (path.endsWith("/approve")) row.approvalStatusCode = "OK";
      else if (path.endsWith("/stop")) row.opportunityStatusCode = "STOP";
      else if (path.endsWith("/resume")) row.opportunityStatusCode = "ING";
      else if (path.endsWith("/lose")) {
        row.opportunityStatusCode = "LOSE";
        row.lossReasonCode = body.lossReasonCode;
      } else if (path.endsWith("/win")) {
        row.opportunityStatusCode = "WIN";
        row.projectId = "P-2610";
      } else if (!path.endsWith("/stage") && !path.endsWith("/tentative-assignment"))
        row = { ...row, ...body };
      row.modifiedAt = "2026-11-16T00:00:00Z";
    }
    return ok(row);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
const form = (edit = false, initial: SalesOpportunityInput = row) => {
  const saved = vi.fn();
  render(
    <SalesOpportunityForm
      initial={initial}
      opportunity={edit ? row : undefined}
      options={options}
      onSaved={saved}
      onCancel={vi.fn()}
    />,
  );
  return saved;
};
const blank = (): SalesOpportunityInput => ({
  opportunityName: "",
  customerId: "",
  salesPartId: "",
  stageCode: "",
  winningProbability: 0,
  expectedAmount: 0,
  contractTypeCode: null,
  startMonth: "",
  expectedMonths: null,
  workplace: null,
  nextAction: null,
  nextActionDueDate: null,
  memo: null,
  needs: [],
});
const detail = (extra: Partial<Parameters<typeof SalesOpportunityDetail>[0]> = {}) => {
  const props = {
    opportunityId: "opp",
    options,
    onEdit: vi.fn(),
    onChanged: vi.fn(),
    onClose: vi.fn(),
    ...extra,
  };
  render(<SalesOpportunityDetail {...props} />);
  return props;
};
const openMenu = async (item: string) => {
  fireEvent.click(await screen.findByTestId("sales-opportunity-more"));
  fireEvent.click(screen.getByTestId(`sales-opportunity-${item}`));
};

// ── 등록·수정 폼 ──
it("creates with a whitelisted payload and asks the pipeline to highlight for six seconds", async () => {
  const saved = form();
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith(
      expect.objectContaining({ opportunityId: "opp" }),
      6000,
      expect.arrayContaining([
        { kind: "ok", message: "「기회」 기회를 등록했습니다." },
      ]),
    ),
  );
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body).not.toHaveProperty("opportunityId");
  expect(body).not.toHaveProperty("approvalStatusCode");
  expect(body).not.toHaveProperty("modifiedAt");
  // 저장 응답이 최신 기회라 따로 다시 읽지 않는다.
  expect(request).not.toHaveBeenCalledWith(
    "/api/common/sales-opportunities/opp",
    undefined,
  );
});
it("reports a successful save as saved even when a later detail read would fail", async () => {
  const fallback = request.getMockImplementation()!;
  request.mockImplementation(async (path: string, init?: RequestInit) =>
    path === "/api/common/sales-opportunities/opp" && !init?.method
      ? { ok: false, status: 500, json: async () => ({ message: "조회 실패" }) }
      : fallback(path, init),
  );
  const saved = form(true);
  fireEvent.change(screen.getByTestId("sales-opportunity-opportunity-name"), {
    target: { value: "수정" },
  });
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith(
      expect.objectContaining({
        opportunityId: "opp",
        modifiedAt: "2026-11-16T00:00:00Z",
      }),
      6000,
      [{ kind: "ok", message: "저장했습니다." }],
    ),
  );
  expect(writes()).toHaveLength(1);
  expect(screen.queryByText("조회 실패")).not.toBeInTheDocument();
});
it("formats money inputs with the shared formatter and keeps a blank unit price blank", () => {
  form(false, { ...row, expectedAmount: 60000000 });
  expect(screen.getByTestId("sales-opportunity-expected-amount")).toHaveValue(
    "60,000,000",
  );
  const price = screen.getByTestId("sales-need-0-estimatedunitprice");
  expect(price).toHaveValue("");
  expect(price).toHaveAttribute("placeholder", "9,000,000");
  fireEvent.change(price, { target: { value: "8,5a00,000" } });
  expect(price).toHaveValue("8,500,000");
});
it("retains existing need identity and the latest version for update", async () => {
  const saved = form(true);
  fireEvent.change(screen.getByTestId("sales-opportunity-opportunity-name"), {
    target: { value: "수정" },
  });
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.needs[0].needSn).toBe(3);
  expect(body.modifiedAt).toBe("2026-11-15T00:00:00Z");
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith(expect.anything(), 6000, [
      { kind: "ok", message: "저장했습니다." },
    ]),
  );
});
it("accepts 100 percent and MM 1 but rejects 101 and MM zero as separate field rules", () => {
  expect(salesOpportunityErrors({ ...row, winningProbability: 100 })).toEqual(
    {},
  );
  expect(
    salesOpportunityErrors({ ...row, winningProbability: 101 }),
  ).toHaveProperty("winningProbability", "0~100 사이 숫자로 입력해 주세요.");
  expect(
    salesOpportunityErrors({
      ...row,
      needs: [{ ...row.needs[0], monthlyMm: 0 }],
    })["needs[0]"],
  ).toBe("1번째 줄의 월 MM(0 초과 1.0 이하) 칸을 확인해 주세요.");
  expect(
    salesOpportunityErrors({
      ...row,
      needs: [{ ...row.needs[0], jobCode: "", requiredCount: 0 }],
    })["needs[0]"],
  ).toBe("1번째 줄의 직무, 인원(1명 이상) 칸을 확인해 주세요.");
  expect(
    salesOpportunityErrors({ ...row, opportunityName: " ", customerId: "" }),
  ).toMatchObject({
    opportunityName: "기회명을 입력해 주세요.",
    customerId: "고객사를 골라 주세요.",
  });
  expect(
    salesOpportunityErrors({ ...row, winningProbability: 0.29 }),
  ).toEqual({});
});
it("does not quote an incomplete draft, shows the row error under the line and focuses it", async () => {
  row.needs[0].jobCode = "";
  form();
  expect(
    screen.getByText("필요 인력을 넣으면 예상 이익률을 바로 계산합니다."),
  ).toBeVisible();
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  expect(
    await screen.findByText("1번째 줄의 직무 칸을 확인해 주세요."),
  ).toBeVisible();
  expect(screen.getByTestId("sales-need-0-jobcode")).toHaveFocus();
  expect(calls(SALES_QUOTE_URL)).toHaveLength(0);
  expect(writes()).toHaveLength(0);
  fireEvent.change(screen.getByTestId("sales-need-0-jobcode"), {
    target: { value: "BE" },
  });
  expect(
    screen.queryByText("1번째 줄의 직무 칸을 확인해 주세요."),
  ).not.toBeInTheDocument();
});
it("focuses the first basic field error with the mockup text", async () => {
  form(false, { ...row, opportunityName: "" });
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  expect(await screen.findByText("기회명을 입력해 주세요.")).toBeVisible();
  expect(screen.getByTestId("sales-opportunity-opportunity-name")).toHaveFocus();
  expect(screen.getByTestId("sales-opportunity-opportunity-name")).toHaveAttribute(
    "maxLength",
    "100",
  );
  expect(screen.getByTestId("sales-opportunity-workplace")).toHaveAttribute(
    "maxLength",
    "200",
  );
  expect(screen.getByTestId("sales-opportunity-next-action")).toHaveAttribute(
    "maxLength",
    "200",
  );
  expect(screen.getByTestId("sales-opportunity-memo")).toHaveAttribute(
    "maxLength",
    "500",
  );
  expect(screen.getByTestId("sales-opportunity-form")).toHaveAttribute(
    "novalidate",
  );
});
it("shows real-time quote with row-specific prices, rounded rate and band", async () => {
  form();
  const card = screen.getByTestId("sales-quote-card");
  const line = await within(card).findByTestId("sales-quote-row-0");
  expect(line).toHaveTextContent("8,500,000");
  expect(line).toHaveTextContent("10,630,000");
  expect(line).toHaveTextContent("9,000,000");
  expect(within(card).getByTestId("sales-quote-rate")).toHaveTextContent(
    "15.0%",
  );
  expect(within(card).getByTestId("sales-quote-rate")).toHaveTextContent(
    "목표 미달",
  );
  expect(within(card).getByTestId("sales-quote-break-even")).toHaveTextContent(
    "5,100만 원",
  );
  expect(within(card).getByTestId("sales-quote-break-even")).toHaveTextContent(
    "목표 이익률 20%면 6,400만 원",
  );
  expect(card).toHaveTextContent("목표 이익률 20% · 15% 미만이면 승인 필요");
  expect(writes()).toHaveLength(0);
});
it("shows the approval-needed banner and a dash for missing amounts", async () => {
  quoteBody = {
    ...quoteBody,
    profitRate: -2.345,
    profitBand: "손해 예상",
    breakEvenAmount: undefined,
    targetAmount: undefined,
    targetRate: undefined,
  };
  form();
  const card = screen.getByTestId("sales-quote-card");
  expect(
    await within(card).findByText(/예상 이익률이 승인 기준\(15%\)보다 낮습니다/),
  ).toBeVisible();
  expect(within(card).getByTestId("sales-quote-rate")).toHaveTextContent(
    "−2.3%",
  );
  expect(within(card).getByTestId("sales-quote-break-even")).toHaveTextContent(
    "목표 이익률을 맞추는 금액 –",
  );
});
it("requires at least one need and shows an empty need error", async () => {
  row.needs = [];
  form(true);
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  expect(
    await screen.findByText("필요 인력을 1줄 이상 넣어 주세요."),
  ).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("cannot delete the last need line", () => {
  form();
  expect(screen.getByTestId("sales-need-0-remove")).toBeDisabled();
  fireEvent.click(screen.getByTestId("sales-opportunity-add-need"));
  expect(screen.getByTestId("sales-need-0-remove")).toBeEnabled();
});
it("starts a new opportunity at the first stage default, not 0%, with one line", async () => {
  form(false, blank());
  await waitFor(() =>
    expect(
      screen.getByTestId("sales-opportunity-winning-probability"),
    ).toHaveValue("10"),
  );
  expect(screen.getByTestId("sales-opportunity-select-stage-code")).toHaveValue(
    "FIND",
  );
  expect(screen.getByText("발굴 (기본 10%)")).toBeInTheDocument();
  expect(screen.getByTestId("sales-opportunity-need-0")).toBeInTheDocument();
  expect(screen.getByTestId("sales-need-0-gradecode")).toHaveValue("MD");
  expect(screen.getByTestId("sales-opportunity-expected-months")).toHaveValue(
    "6",
  );
});
it("probability follows the stage unless typed by hand", async () => {
  form(false, blank());
  const probability = screen.getByTestId("sales-opportunity-winning-probability");
  await waitFor(() => expect(probability).toHaveValue("10"));
  fireEvent.change(screen.getByTestId("sales-opportunity-select-stage-code"), {
    target: { value: "PROP" },
  });
  expect(probability).toHaveValue("30");
  fireEvent.change(probability, { target: { value: "45" } });
  fireEvent.change(screen.getByTestId("sales-opportunity-select-stage-code"), {
    target: { value: "NEGO" },
  });
  expect(probability).toHaveValue("45");
});
it("edit keeps a probability that differs from the old stage default", async () => {
  row.winningProbability = 25;
  form(true);
  await screen.findByText("제안 (기본 30%)");
  fireEvent.change(screen.getByTestId("sales-opportunity-select-stage-code"), {
    target: { value: "PROP" },
  });
  expect(
    screen.getByTestId("sales-opportunity-winning-probability"),
  ).toHaveValue("25");
});
it("edit moves a stage-default probability to the new stage default", async () => {
  form(true);
  await screen.findByText("제안 (기본 30%)");
  fireEvent.change(screen.getByTestId("sales-opportunity-select-stage-code"), {
    target: { value: "PROP" },
  });
  expect(
    screen.getByTestId("sales-opportunity-winning-probability"),
  ).toHaveValue("30");
});
it("offers only turnkey, monthly and dispatch contract types", () => {
  form();
  const select = screen.getByTestId("sales-opportunity-select-contract-type-code");
  const labels = within(select)
    .getAllByRole("option")
    .map((o) => o.textContent);
  expect(labels).toEqual(["선택", "턴키", "월단위", "파견"]);
});
it("keeps long-form input on conflict and explicitly reloads only the version", async () => {
  form(true);
  fail = 409;
  fireEvent.change(screen.getByTestId("sales-opportunity-opportunity-name"), {
    target: { value: "내 입력" },
  });
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  expect(await screen.findByTestId("sales-opportunity-toast")).toHaveClass(
    "app-toast-warn",
  );
  row = { ...row, modifiedAt: "2026-11-16T00:00:00Z" };
  fireEvent.click(await screen.findByText("최신 버전 불러오기 (입력 유지)"));
  await screen.findByText(
    "최신 버전을 불러왔습니다. 입력을 확인한 뒤 저장해 주세요.",
  );
  expect(screen.getByTestId("sales-opportunity-opportunity-name")).toHaveValue(
    "내 입력",
  );
  fail = 0;
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  await waitFor(() => expect(writes()).toHaveLength(2));
  expect(JSON.parse(String(writes()[1][1].body)).modifiedAt).toBe(
    "2026-11-16T00:00:00Z",
  );
});
it("displays server field errors without losing input", async () => {
  form();
  fail = 400;
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  expect(await screen.findByText("기회명을 확인해 주세요.")).toBeVisible();
  expect(screen.getByTestId("sales-opportunity-opportunity-name")).toHaveValue(
    "기회",
  );
});
it("locks form controls for a closed opportunity", () => {
  row.opportunityStatusCode = "WIN";
  form(true);
  expect(screen.getByTestId("sales-opportunity-save")).toBeDisabled();
  expect(screen.getByText("종료된 기회는 수정할 수 없습니다.")).toBeVisible();
  expect(calls(SALES_QUOTE_URL)).toHaveLength(0);
});
it("deletes from the edit form through a confirm dialog", async () => {
  const onCancel = vi.fn();
  render(
    <SalesOpportunityForm
      initial={row}
      opportunity={row}
      options={options}
      onSaved={vi.fn()}
      onCancel={onCancel}
    />,
  );
  fireEvent.click(screen.getByTestId("sales-opportunity-delete"));
  expect(screen.getByText("「기회」을 삭제할까요?")).toBeVisible();
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  await waitFor(() => expect(onCancel).toHaveBeenCalled());
  expect(writes()[0][0]).toBe("/api/common/sales-opportunities/opp/stop");
});
it("asks before leaving a changed form", () => {
  const onCancel = vi.fn();
  render(
    <SalesOpportunityForm
      initial={row}
      options={options}
      onSaved={vi.fn()}
      onCancel={onCancel}
    />,
  );
  fireEvent.change(screen.getByTestId("sales-opportunity-opportunity-name"), {
    target: { value: "바꿈" },
  });
  fireEvent.click(screen.getByTestId("sales-opportunity-cancel"));
  expect(screen.getByText("저장하지 않은 내용이 있습니다")).toBeVisible();
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(onCancel).toHaveBeenCalled();
});

// ── 가배정 경고(SFR-129-08) ──
it("warns locally for the same person on two new lines without disabling save", async () => {
  form(false, blank());
  fireEvent.click(screen.getByTestId("sales-opportunity-add-need"));
  for (const i of [0, 1])
    fireEvent.change(screen.getByTestId(`sales-need-${i}-developers`), {
      target: { value: "dev" },
    });
  expect(screen.getAllByText(/같은 사람을 두 줄에 가배정했습니다/)).toHaveLength(2);
  expect(screen.getByTestId("sales-opportunity-save")).toBeEnabled();
});
it("asks the server about a new opportunity's unsaved line and shows the warning under it", async () => {
  const draft = blank();
  draft.needs = [{ ...row.needs[0], needSn: undefined, tentativeDeveloperIds: ["dev"] }];
  form(false, draft);
  expect(
    await screen.findByText(/「다른 기회」에 가배정되어 있습니다\. 저장하면 이 기회로 옮깁니다\./),
  ).toBeVisible();
  const sent = calls(SALES_TENTATIVE_CHECK_URL);
  expect(sent).toHaveLength(1);
  // 새 기회는 opportunityId를 빼고 보낸다.
  expect(JSON.parse(String(sent[0][1].body))).toEqual({
    developerId: "dev",
    startMonth: "2026-11",
  });
  expect(within(screen.getByTestId("sales-opportunity-need-0")).getByTestId("sales-need-0-warning")).toBeVisible();
});
it("edit asks once per person and month with the opportunity, maps saved-line warnings and drops the line's own pair", async () => {
  // 저장된 줄(needSn 3)에 dev2가 그대로 있다 → 서버의 「두 줄」 짝은 이 줄 자신이라 버리고, 1.0 MM 경고만 남는다.
  row.needs[0].tentativeDeveloperIds = ["dev2"];
  form(true);
  expect(
    await screen.findByText(/해당 월에 1\.0 MM 이상 확정 투입되어 있습니다\./),
  ).toBeVisible();
  expect(screen.queryByText(/같은 사람이 두 줄에 있습니다/)).toBeNull();
  expect(JSON.parse(String(calls(SALES_TENTATIVE_CHECK_URL)[0][1].body))).toEqual({
    developerId: "dev2",
    startMonth: "2026-11",
    opportunityId: "opp",
  });
  // 같은 사람·같은 달의 새 줄을 더하면 요청은 한 번이고, 두 줄 모두 화면의 「두 줄」 경고가 보인다.
  request.mockClear();
  fireEvent.click(screen.getByTestId("sales-opportunity-add-need"));
  fireEvent.change(screen.getByTestId("sales-need-1-developers"), {
    target: { value: "dev2" },
  });
  expect(await screen.findAllByText(/같은 사람을 두 줄에 가배정했습니다/)).toHaveLength(2);
  await waitFor(() => expect(calls(SALES_TENTATIVE_CHECK_URL)).toHaveLength(1));
});
it("a failed warning check never blocks saving", async () => {
  newCheckStatus = 500;
  row.needs[0].tentativeDeveloperIds = ["dev"];
  const saved = form(true);
  await waitFor(() => expect(calls(SALES_TENTATIVE_CHECK_URL)).toHaveLength(1));
  expect(screen.queryByTestId("sales-need-0-warning")).toBeNull();
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
});
it("a handed-off person (409 on the line) shows under that line, not the version-conflict banner", async () => {
  row.needs[0].tentativeDeveloperIds = ["dev2"];
  form(true);
  handedOff = true;
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  const line = await screen.findByText("투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.", {
    selector: ".so-errmsg",
  });
  expect(line.closest("tr")?.previousElementSibling).toHaveAttribute(
    "data-testid",
    "sales-opportunity-need-0",
  );
  expect(screen.getByTestId("sales-need-0-developers")).toHaveClass("so-err");
  expect(screen.queryByTestId("sales-opportunity-reload")).toBeNull();
});

// ── 상세 서랍 ──
it("renders loading then header, tabs and the approval banner for WAIT ongoing", async () => {
  row.approvalHistory = [
    { actionCode: "REQ", actedAt: "2026-11-14T15:30:00Z", reason: null },
  ];
  // 띠는 저장 때 판정한 이익률·승인 기준(상세 응답)을 보이고, 견적을 다시 계산하지 않는다.
  row.expectedProfitRate = 14.96;
  row.approvalRate = 15;
  quoteBody.profitRate = 30;
  detail({ drawer: true });
  expect(screen.getByText("불러오는 중…")).toBeVisible();
  expect(await screen.findByTestId("sales-opportunity-approve")).toBeVisible();
  expect(screen.getByText("고객 · 발굴 · 확률 10%")).toBeVisible();
  expect(screen.getByTestId("sales-opportunity-edit")).toHaveTextContent("수정");
  expect(
    screen.getAllByRole("tab").map((t) => t.textContent),
  ).toEqual(["기본 정보", "필요 인력 (1명)", "견적 점검"]);
  const banner = await screen.findByText(/견적 승인 대기 — 예상 이익률 15.0% \(기준 15%\)/);
  expect(banner).toHaveTextContent("2026-11-15 요청");
  expect(calls(SALES_QUOTE_URL)).toHaveLength(0);
  expect(screen.queryByText(/승인 불필요|WAIT|NONE/)).not.toBeInTheDocument();
  expect(screen.getByTestId("kv-row-예상 금액")).toHaveTextContent(
    "60,000,000 원 확률 반영 600만 원",
  );
});
it("shows no approval banner or raw code when approval is not needed", async () => {
  row.approvalStatusCode = "NONE";
  detail();
  await screen.findByTestId("sales-opportunity-edit");
  expect(screen.queryByText(/승인/)).not.toBeInTheDocument();
  expect(screen.queryByText(/NONE|ING/)).not.toBeInTheDocument();
});
it("shows the rejection banner with KST date and reason, and no reason part when empty", async () => {
  row.approvalStatusCode = "REJ";
  row.approvalHistory = [
    { actionCode: "REJ", actedAt: "2026-11-15T16:00:00Z", reason: "이익률 낮음" },
  ];
  detail();
  expect(
    await screen.findByText(
      "견적 반려 (2026-11-16) · 사유: 이익률 낮음 — 단가나 인원을 고쳐 다시 저장하면 다시 승인을 요청합니다.",
    ),
  ).toBeVisible();
  cleanup();
  row.approvalHistory[0].reason = null;
  detail();
  expect(
    await screen.findByText(
      "견적 반려 (2026-11-16) — 단가나 인원을 고쳐 다시 저장하면 다시 승인을 요청합니다.",
    ),
  ).toBeVisible();
  expect(screen.queryByTestId("sales-opportunity-approve")).not.toBeInTheDocument();
});
it("does not block winning a rejected opportunity and offers only three contract types", async () => {
  row.approvalStatusCode = "REJ";
  detail();
  await openMenu("win");
  expect(screen.getByTestId("sales-action-contractenddate")).toHaveValue(
    "2027-04-30",
  );
  expect(
    within(screen.getByTestId("sales-action-contracttypecode"))
      .getAllByRole("option")
      .map((o) => o.textContent),
  ).toEqual(["턴키", "월단위", "파견"]);
  expect(
    within(screen.getByTestId("sales-action-projectstatuscode"))
      .getAllByRole("option")
      .map((o) => o.textContent),
  ).toEqual(["진행", "수주예정"]);
});
it("defaults blank opportunity period to six months and blank type to turnkey", () => {
  const defaults = salesWinDefaults({ ...row, expectedMonths: null });
  expect(defaults.contractTypeCode).toBe("TK");
  expect(defaults.contractEndDate).toBe("2027-04-30");
  expect(
    salesWinDefaults({ ...row, startMonth: "2027-09", expectedMonths: 6 })
      .contractEndDate,
  ).toBe("2028-02-29");
  expect(salesWinDefaults({ ...row, contractTypeCode: "IN" }).contractTypeCode).toBe(
    "TK",
  );
  expect(
    salesOpportunityPayload({ ...row, expectedMonths: null }).expectedMonths,
  ).toBeNull();
});
it("rejects equal win dates before sending", async () => {
  detail();
  await openMenu("win");
  fireEvent.change(screen.getByTestId("sales-action-contractenddate"), {
    target: { value: "2026-11-01" },
  });
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(
    await screen.findByText(
      "계약 기간을 확인해 주세요. 종료일이 시작일보다 앞입니다.",
    ),
  ).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("wins, then the drawer closes and the screen shows the mockup notice", async () => {
  row.needs[0].tentativeDeveloperIds = ["dev"];
  const onNotice = vi.fn();
  const props = detail({ onNotice });
  await openMenu("win");
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  await waitFor(() =>
    expect(onNotice).toHaveBeenCalledWith({
      kind: "ok",
      message:
        "수주 처리했습니다. 프로젝트 P-2610를 만들었습니다. 가배정한 1명은 투입 배정 화면의 「배정 후보」로 넘어갔습니다.",
    }),
  );
  expect(props.onClose).toHaveBeenCalled();
  expect(JSON.parse(String(writes()[0][1].body))).toMatchObject({
    contractAmount: 60000000,
    contractTypeCode: "TK",
    projectStatusCode: "ING",
    modifiedAt: "2026-11-15T00:00:00Z",
  });
});
it("allows empty rejection reason and refreshes the banner after success", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-reject"));
  expect(screen.getByText("견적을 반려할까요?")).toBeVisible();
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(
    await screen.findByText(/^견적 반려 \(2026-11-16\) — /),
  ).toBeVisible();
  expect(
    await screen.findByText("견적을 반려했습니다. 영업 담당자에게 알렸습니다."),
  ).toBeVisible();
  expect(JSON.parse(String(writes()[0][1].body))).toMatchObject({
    reason: "",
    modifiedAt: "2026-11-15T00:00:00Z",
  });
});
it("rejects a 301-character reason without writing", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-reject"));
  expect(screen.getByTestId("sales-action-reason")).toHaveAttribute(
    "maxLength",
    "300",
  );
  fireEvent.change(screen.getByTestId("sales-action-reason"), {
    target: { value: "가".repeat(301) },
  });
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(
    await screen.findByText("반려 사유는 300자 이내로 넣어 주세요."),
  ).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("requires a loss reason without changing state", async () => {
  detail();
  await openMenu("lose");
  expect(screen.getByText("「기회」를 실주로 바꿉니다. 가배정한 0명은 풀립니다.")).toBeVisible();
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(await screen.findByText("실주 사유를 골라 주세요.")).toBeVisible();
  // 공통 규칙: 첫 오류 칸으로 커서가 가고, 고르는 칸의 이름은 「실주 사유」.
  const reason = screen.getByRole("combobox", { name: "실주 사유" });
  expect(reason).toBe(screen.getByTestId("sales-action-lossreasoncode"));
  await waitFor(() => expect(reason).toHaveFocus());
  expect(reason).toHaveAttribute("aria-invalid", "true");
  expect(screen.getByRole("textbox", { name: "메모" })).toBe(
    screen.getByTestId("sales-action-memo"),
  );
  expect(writes()).toHaveLength(0);
  fireEvent.change(screen.getByTestId("sales-action-lossreasoncode"), {
    target: { value: "PRICE" },
  });
  expect(screen.queryByText("실주 사유를 골라 주세요.")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(await screen.findByText("실주 처리했습니다.")).toBeVisible();
  expect(await screen.findByText(/실주 — 사유: 가격/)).toBeVisible();
});
it("uses the delete-labelled confirm dialog for soft stop only", async () => {
  detail();
  await openMenu("stop");
  expect(screen.getByText("「기회」을 삭제할까요?")).toBeVisible();
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  await screen.findByTestId("sales-opportunity-resume");
  expect(writes()[0][0]).toBe("/api/common/sales-opportunities/opp/stop");
  expect(writes()[0][1].method).toBe("POST");
  expect(
    screen.getByText(
      "삭제했습니다. 종료 탭의 [사용 중지 포함]에서 다시 쓸 수 있습니다.",
    ),
  ).toBeVisible();
});
it("STOP shows resume but no edit or approval controls", async () => {
  row.opportunityStatusCode = "STOP";
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-resume"));
  expect(screen.getByText("「기회」을 다시 사용할까요?")).toBeVisible();
  expect(
    screen.queryByTestId("sales-opportunity-edit"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("sales-opportunity-approve"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("confirmation-proceed"));
  expect(await screen.findByText("「기회」을 다시 사용합니다.")).toBeVisible();
});
it("WIN displays existing project link and no destructive controls", async () => {
  row.opportunityStatusCode = "WIN";
  row.projectId = "project";
  row.resultDate = "2026-11-20";
  detail({ drawer: true });
  expect(
    await screen.findByTestId("sales-opportunity-project"),
  ).toHaveAttribute("data-project-id", "project");
  expect(screen.getByText("고객 · 수주")).toBeVisible();
  expect(
    screen.queryByTestId("sales-opportunity-more"),
  ).not.toBeInTheDocument();
});
it("needs tab lists lines and tentative people", async () => {
  row.needs[0].tentativeDeveloperIds = ["dev"];
  row.needs[0].estimatedUnitPrice = 9500000;
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-detail-tab-needs"));
  expect(screen.getByTestId("sales-detail-need-0")).toHaveTextContent(
    "백엔드–12026-11 · 6개월1.009,500,000",
  );
  expect(screen.getByText("가배정 인력 (1)")).toBeVisible();
  expect(screen.getByTestId("sales-detail-tentative-dev")).toHaveTextContent(
    "1. 백엔드",
  );
});
it("quote tab computes the saved needs", async () => {
  row.approvalStatusCode = "NONE";
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-detail-tab-quote"));
  expect(await screen.findByTestId("sales-quote-rate")).toHaveTextContent(
    "15.0%목표 미달",
  );
});
it("short-action conflict reloads detail and warns", async () => {
  detail();
  const approve = await screen.findByTestId("sales-opportunity-approve");
  fail = 409;
  fireEvent.click(approve);
  expect(
    await screen.findByText("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."),
  ).toBeVisible();
  expect(screen.getByTestId("sales-opportunity-detail-toast")).toHaveClass(
    "app-toast-warn",
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await waitFor(() =>
    expect(
      request.mock.calls.filter(
        (c) => c[0] === "/api/common/sales-opportunities/opp" && !c[1],
      ),
    ).toHaveLength(2),
  );
});
it("dialog conflict closes the dialog and reloads", async () => {
  detail();
  await openMenu("lose");
  fireEvent.change(screen.getByTestId("sales-action-lossreasoncode"), {
    target: { value: "PRICE" },
  });
  fail = 409;
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  await screen.findByText("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
  expect(
    screen.queryByTestId("sales-opportunity-action-dialog"),
  ).not.toBeInTheDocument();
});
it("approves directly with the mockup notice", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-approve"));
  expect(await screen.findByText("견적을 승인했습니다.")).toBeVisible();
  expect(await screen.findByText(/견적 승인됨 \(경영진\)/)).toBeVisible();
});
it("uses the action response instead of a second read, so a failed re-read is not a failed action", async () => {
  row.expectedProfitRate = 14.2;
  row.approvedProfitRate = 13.8;
  const fallback = request.getMockImplementation()!;
  let reads = 0;
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/sales-opportunities/opp" && !init?.method && reads++)
      return { ok: false, status: 500, json: async () => ({ message: "조회 실패" }) };
    return fallback(path, init);
  });
  const props = detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-approve"));
  expect(await screen.findByText("견적을 승인했습니다.")).toBeVisible();
  // 승인됨 띠는 승인한 때의 이익률(APRV_PRFT_RT)을 보인다.
  expect(
    await screen.findByText(/견적 승인됨 \(경영진\) — 예상 이익률 13.8%/),
  ).toBeVisible();
  expect(reads).toBe(1);
  expect(props.onChanged).toHaveBeenCalledWith(
    expect.objectContaining({ approvalStatusCode: "OK", modifiedAt: "2026-11-16T00:00:00Z" }),
  );
  expect(screen.queryByText("조회 실패")).not.toBeInTheDocument();
});
it("win dialog labels every field and focuses the first error", async () => {
  detail();
  await openMenu("win");
  const amount = screen.getByRole("textbox", { name: "계약금액" });
  expect(amount).toHaveValue("60,000,000");
  expect(screen.getByRole("combobox", { name: "계약유형" })).toBeVisible();
  expect(screen.getByLabelText("계약 시작일")).toBeVisible();
  expect(screen.getByLabelText("계약 종료일")).toBeVisible();
  fireEvent.change(amount, { target: { value: "" } });
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(await screen.findByText("계약금액을 입력해 주세요.")).toBeVisible();
  await waitFor(() => expect(amount).toHaveFocus());
  fireEvent.change(amount, { target: { value: "70,000,000" } });
  fireEvent.change(screen.getByLabelText("계약 종료일"), {
    target: { value: "2026-10-01" },
  });
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(
    await screen.findByText("계약 기간을 확인해 주세요. 종료일이 시작일보다 앞입니다."),
  ).toBeVisible();
  await waitFor(() => expect(screen.getByLabelText("계약 종료일")).toHaveFocus());
  expect(writes()).toHaveLength(0);
});
it("reject dialog names the reason field", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-reject"));
  expect(screen.getByRole("textbox", { name: "반려 사유 (선택)" })).toBe(
    screen.getByTestId("sales-action-reason"),
  );
});
it("drawer mode renders the shared detail panel with the opportunity title", async () => {
  detail({ drawer: true });
  const panel = await screen.findByRole("dialog", { name: "기회" });
  expect(within(panel).getByText("고객 · 발굴 · 확률 10%")).toBeVisible();
  expect(within(panel).getAllByRole("tab")).toHaveLength(3);
});
it("failed detail displays error and no action controls", async () => {
  request.mockImplementation(async () => ({
    ok: false,
    status: 500,
    json: async () => ({ message: "조회 실패" }),
  }));
  detail();
  expect(await screen.findByText("조회 실패")).toBeVisible();
  expect(
    screen.queryByTestId("sales-opportunity-edit"),
  ).not.toBeInTheDocument();
});

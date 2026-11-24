import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { SalesOpportunityForm } from "./SalesOpportunityForm";
import { SalesOpportunityDetail } from "./SalesOpportunityDetail";
import {
  salesOpportunityErrors,
  salesOpportunityPayload,
  salesWinDefaults,
  type SalesOpportunity,
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
    JOB: [{ code: "BE", codeName: "백엔드" }],
    GRADE: [{ code: "HIGH", codeName: "고급" }],
    CTRT_TYPE: [{ code: "TK", codeName: "턴키" }],
    PROJ_STAT: [
      { code: "ING", codeName: "진행" },
      { code: "PRE", codeName: "수주예정" },
    ],
    LOSE_RSN: [{ code: "PRICE", codeName: "가격" }],
    APRV_ACT: [{ code: "REJ", codeName: "반려" }],
  },
  customers: [{ clientId: "customer", clientName: "고객" }],
  parts: [{ partId: "part", partName: "파트" }],
  developers: [{ developerId: "dev", name: "개발자" }],
};
let row: SalesOpportunity;
let request: ReturnType<typeof vi.fn>;
let fail: number;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const writes = () =>
  request.mock.calls.filter((call) =>
    ["POST", "PUT"].includes(
      (call[1] as RequestInit | undefined)?.method || "",
    ),
  );
beforeEach(() => {
  fail = 0;
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
    if (init?.method) {
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
      if (path.endsWith("/quote"))
        return ok({
          expectedRevenue: 60000000,
          expectedCost: 51000000,
          profitRate: 15,
          minimumUnitPrice: 8500000,
          targetUnitPrice: 10630000,
          needQuotes: [
            {
              rowIndex: 0,
              minimumUnitPrice: 8500000,
              targetUnitPrice: 10630000,
            },
          ],
        });
      if (path.endsWith("/tentative-assignment-check"))
        return ok({
          warnings: [
            {
              needSn: 3,
              developerId: "dev",
              message: "다른 기회에 가배정되어 있습니다.",
            },
          ],
        });
      const body = JSON.parse(String(init.body));
      if (path.endsWith("/reject")) {
        row.approvalStatusCode = "REJ";
        row.approvalHistory = [
          {
            actionCode: "REJ",
            actedAt: "2026-11-16T00:00:00Z",
            reason: body.reason || null,
          },
        ];
      } else if (path.endsWith("/approve")) row.approvalStatusCode = "OK";
      else if (path.endsWith("/stop")) row.opportunityStatusCode = "STOP";
      else if (path.endsWith("/resume")) row.opportunityStatusCode = "ING";
      else if (
        !path.endsWith("/win") &&
        !path.endsWith("/stage") &&
        !path.endsWith("/lose") &&
        !path.endsWith("/tentative-assignment")
      )
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
const form = (edit = false) => {
  const saved = vi.fn();
  render(
    <SalesOpportunityForm
      initial={row}
      opportunity={edit ? row : undefined}
      options={options}
      onSaved={saved}
      onCancel={vi.fn()}
    />,
  );
  return saved;
};
const detail = () =>
  render(
    <SalesOpportunityDetail
      opportunityId="opp"
      options={options}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
      onClose={vi.fn()}
    />,
  );
it("creates with a whitelisted payload and asks the pipeline to highlight for six seconds", async () => {
  const saved = form();
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  await waitFor(() =>
    expect(saved).toHaveBeenCalledWith(
      expect.objectContaining({ opportunityId: "opp" }),
      6000,
    ),
  );
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body).not.toHaveProperty("opportunityId");
  expect(body).not.toHaveProperty("approvalStatusCode");
  expect(body).not.toHaveProperty("modifiedAt");
  expect(request).toHaveBeenCalledWith(
    "/api/common/sales-opportunities/opp",
    undefined,
  );
});
it("retains existing need identity and the latest version for update", async () => {
  form(true);
  fireEvent.change(screen.getByTestId("sales-opportunity-opportunity-name"), {
    target: { value: "수정" },
  });
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.needs[0].needSn).toBe(3);
  expect(body.modifiedAt).toBe("2026-11-15T00:00:00Z");
});
it("accepts 100 percent and MM 1 but rejects 101 and MM zero as separate field rules", () => {
  expect(salesOpportunityErrors({ ...row, winningProbability: 100 })).toEqual(
    {},
  );
  expect(
    salesOpportunityErrors({ ...row, winningProbability: 101 }),
  ).toHaveProperty("winningProbability");
  expect(
    salesOpportunityErrors({
      ...row,
      needs: [{ ...row.needs[0], monthlyMm: 0 }],
    })["needs[0].monthlyMm"],
  ).toContain("1번째 줄의 월 MM");
});
it("does not quote an incomplete draft and leaves it editable", async () => {
  row.needs[0].jobCode = "";
  form();
  fireEvent.click(screen.getByTestId("sales-opportunity-quote"));
  expect(
    await screen.findByText("1번째 줄의 직무 칸을 확인해 주세요."),
  ).toBeVisible();
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("sales-need-0-jobcode")).toBeEnabled();
});
it("shows row-specific minimum and target quote prices", async () => {
  form();
  fireEvent.click(screen.getByTestId("sales-opportunity-quote"));
  expect(await screen.findByText(/최소 단가 8,500,000/)).toBeVisible();
  expect(screen.getByText(/이익률 15%/)).toBeVisible();
});
it("requires at least one need and shows an empty need error", async () => {
  form();
  fireEvent.click(screen.getByTestId("sales-need-0-remove"));
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  expect(
    await screen.findByText("필요 인력을 한 줄 이상 넣어 주세요."),
  ).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("keeps long-form input on conflict and explicitly reloads only the version", async () => {
  form(true);
  fail = 409;
  fireEvent.change(screen.getByTestId("sales-opportunity-opportunity-name"), {
    target: { value: "내 입력" },
  });
  fireEvent.click(screen.getByTestId("sales-opportunity-save"));
  fireEvent.click(await screen.findByText("최신 버전 불러오기 (입력 유지)"));
  await screen.findByText(
    "최신 버전을 불러왔습니다. 입력을 확인한 뒤 저장해 주세요.",
  );
  expect(screen.getByTestId("sales-opportunity-opportunity-name")).toHaveValue(
    "내 입력",
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
});
it("warns for same person in two needs without disabling save", () => {
  row.needs[0].tentativeDeveloperIds = ["dev"];
  row.needs.push({ ...row.needs[0], needSn: 4 });
  form();
  expect(screen.getAllByText("같은 사람이 두 줄에 있습니다.")).toHaveLength(2);
  expect(screen.getByTestId("sales-opportunity-save")).toBeEnabled();
});
it("renders loading then approval controls only for WAIT ongoing", async () => {
  detail();
  expect(screen.getByText("불러오는 중…")).toBeVisible();
  expect(await screen.findByTestId("sales-opportunity-approve")).toBeVisible();
  expect(screen.getByTestId("sales-opportunity-win")).toBeEnabled();
});
it("does not block winning a rejected opportunity and hides approval buttons", async () => {
  row.approvalStatusCode = "REJ";
  detail();
  await screen.findByText("견적이 반려됐습니다.");
  expect(
    screen.queryByTestId("sales-opportunity-approve"),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("sales-opportunity-win"));
  expect(screen.getByTestId("sales-action-contractenddate")).toHaveValue(
    "2027-04-30",
  );
});
it("defaults blank opportunity period to six months and blank type to turnkey", () => {
  const defaults = salesWinDefaults({ ...row, expectedMonths: null });
  expect(defaults.contractTypeCode).toBe("TK");
  expect(defaults.contractEndDate).toBe("2027-04-30");
  expect(
    salesOpportunityPayload({ ...row, expectedMonths: null }).expectedMonths,
  ).toBeNull();
});
it("rejects equal win dates before sending", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-win"));
  fireEvent.change(screen.getByTestId("sales-action-contractenddate"), {
    target: { value: "2026-11-01" },
  });
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(
    await screen.findByText("종료일은 시작일보다 뒤여야 합니다."),
  ).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("allows empty rejection reason and refreshes history after success", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-reject"));
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  await screen.findByText("견적이 반려됐습니다.");
  expect(screen.getByTestId("sales-approval-history-0")).not.toHaveTextContent(
    "null",
  );
  expect(JSON.parse(String(writes()[0][1].body))).toMatchObject({
    reason: "",
    modifiedAt: "2026-11-15T00:00:00Z",
  });
});
it("rejects a 301-character reason without writing", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-reject"));
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
  fireEvent.click(await screen.findByTestId("sales-opportunity-lose"));
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  expect(await screen.findByText("실주 사유를 골라 주세요.")).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("uses the delete-labelled dialog for soft stop only", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-stop"));
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  await screen.findByTestId("sales-opportunity-resume");
  expect(writes()[0][0]).toBe("/api/common/sales-opportunities/opp/stop");
  expect(writes()[0][1].method).toBe("POST");
});
it("STOP shows resume but no edit or approval controls", async () => {
  row.opportunityStatusCode = "STOP";
  detail();
  await screen.findByTestId("sales-opportunity-resume");
  expect(
    screen.queryByTestId("sales-opportunity-edit"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("sales-opportunity-approve"),
  ).not.toBeInTheDocument();
});
it("WIN displays existing project link and no destructive controls", async () => {
  row.opportunityStatusCode = "WIN";
  row.projectId = "project";
  detail();
  expect(
    await screen.findByTestId("sales-opportunity-project"),
  ).toHaveAttribute("data-project-id", "project");
  expect(
    screen.queryByTestId("sales-opportunity-stop"),
  ).not.toBeInTheDocument();
});
it("check warnings do not prevent tentative save", async () => {
  detail();
  fireEvent.click(
    await screen.findByTestId("sales-opportunity-tentative-assignment"),
  );
  fireEvent.change(screen.getByTestId("sales-action-developerid"), {
    target: { value: "dev" },
  });
  fireEvent.click(screen.getByTestId("sales-tentative-check"));
  await screen.findByText(/다른 기회에 가배정되어 있습니다/);
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  await waitFor(() => expect(writes()).toHaveLength(2));
  expect(writes()[1][0]).toBe(
    "/api/common/sales-opportunities/opp/tentative-assignment",
  );
});
it("short-action conflict closes dialog and reloads detail", async () => {
  detail();
  fireEvent.click(await screen.findByTestId("sales-opportunity-approve"));
  fail = 409;
  fireEvent.click(screen.getByTestId("sales-action-confirm"));
  await screen.findByText("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(
    request.mock.calls.filter(
      (c) => c[0] === "/api/common/sales-opportunities/opp" && !c[1],
    ),
  ).toHaveLength(2);
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

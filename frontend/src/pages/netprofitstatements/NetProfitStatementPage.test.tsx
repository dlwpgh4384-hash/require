import { afterEach, beforeEach, expect, it, vi } from "vitest";
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
import { MemoryRouter } from "react-router-dom";
import { NetProfitStatementPage } from "./NetProfitStatementPage";
import {
  NetProfitStatementTitleActions,
  NetProfitStatementWorkspaceProvider,
} from "./NetProfitStatementWorkspace";
import type { NetProfitStatementResult } from "./netProfitStatements";
import * as api from "./netProfitStatements";

const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data: structuredClone(data) }),
});
function report(month = "202602"): NetProfitStatementResult {
  const first = month.startsWith("2025"),
    provisional = month === "202602";
  const count = first ? 13 : 15;
  return {
    year: Number(month.slice(0, 4)),
    baseMonth: month,
    provisional,
    provisionalNotice: provisional
      ? "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."
      : null,
    summary: {
      cumulativeNetProfit: 1000000,
      netProfitRate: 12.3,
      baseMonthNetProfit: 450000,
      previousMonthChangePercent: -5.2,
    },
    numericTable: {
      columns: [
        ...Array.from({ length: 12 }, (_, i) => ({
          label: `${i + 1}월`,
          provisional: false,
          working: provisional && i === 1,
        })),
        { label: "누계", provisional, working: false },
        ...(first
          ? []
          : [
              { label: "전년 누계", provisional: false, working: false },
              { label: "전년 대비", provisional: false, working: false },
            ]),
      ],
      rows: [
        {
          label: "당기순이익",
          style: "bold",
          isCost: false,
          values: Array.from({ length: count }, (_, i) => ({
            value: i === 2 ? null : i === 14 ? -5.2 : 1000000,
            format: i === 14 ? "percent" : "money",
          })),
        },
        {
          label: "전월 조정",
          style: "plain",
          isCost: false,
          values: Array.from({ length: count }, (_, i) => ({
            value: i === 0 ? -100000 : i === 1 ? 100000 : null,
            format: "money",
          })),
        },
        {
          label: "법인세(월할)",
          style: "plain",
          isCost: true,
          values: Array.from({ length: count }, () => ({
            value: 20000,
            format: "money",
          })),
        },
      ],
    },
    contributionVisible: !first,
    contributions: first
      ? []
      : [
          {
            contractTypeCode: "TK",
            contractTypeName: "턴키",
            amount: 900000,
            sharePercent: 75,
          },
          {
            contractTypeCode: "MO",
            contractTypeName: "월단위",
            amount: 300000,
            sharePercent: 25,
          },
          {
            contractTypeCode: "DP",
            contractTypeName: "파견",
            amount: -100000,
            sharePercent: null,
          },
        ],
  };
}
let request: ReturnType<typeof vi.fn>;
let failure: number, empty: boolean, losses: boolean;
beforeEach(() => {
  failure = 0;
  empty = false;
  losses = false;
  request = vi.fn(async (path: string, _init?: RequestInit) => {
    if (path.startsWith("/api/common/user-preferences/")) {
      const [prefTypeCd, prefKey] = path
        .split("/")
        .slice(-2)
        .map(decodeURIComponent);
      return ok(
        { prefTypeCd, prefKey, found: false, prefVal: null, modDttm: null },
        {
          periodBasis: {
            latestYm: "202602",
            cashBaseYm: "202601",
            closedMonths: ["202601"],
          },
        },
      );
    }
    if (path.includes("/excel?"))
      return {
        ok: false,
        status: 400,
        json: async () => ({ message: "파일 조건 오류", errors: [] }),
      };
    if (failure)
      return {
        ok: false,
        status: failure,
        json: async () => ({
          success: false,
          message: "기간 조회 실패",
          errors:
            failure === 400
              ? [{ field: "baseMonth", message: "기준월을 확인해 주세요." }]
              : [],
        }),
      };
    const result = report(
      new URL(path, "https://test.invalid").searchParams.get("baseMonth")!,
    );
    if (empty) result.numericTable.rows = [];
    if (losses)
      result.contributions = result.contributions.map((c) => ({
        ...c,
        amount: -1000,
        sharePercent: null,
      }));
    return ok(result);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
function mount() {
  return render(
    <MemoryRouter initialEntries={["/screens/UI-09"]}>
      <NetProfitStatementWorkspaceProvider>
        <NetProfitStatementTitleActions />
        <NetProfitStatementPage />
      </NetProfitStatementWorkspaceProvider>
    </MemoryRouter>,
  );
}
async function ready(month = "202602") {
  await waitFor(() =>
    expect(screen.getByTestId("net-profit-summary")).toHaveAttribute(
      "data-base-month",
      month,
    ),
  );
}
const reads = () =>
  request.mock.calls.filter(([path]) =>
    String(path).startsWith("/api/common/net-profit-statements?"),
  );

it("renders two readonly summary cards and one common title download without login", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("net-profit-kpis").children).toHaveLength(2);
  expect(screen.getByTestId("net-profit-kpi-cumulative")).toHaveTextContent(
    "순이익률 12.3%",
  );
  expect(screen.getByTestId("net-profit-kpi-base")).toHaveTextContent(
    "2026-02 순이익",
  );
  expect(screen.getByTestId("net-profit-kpi-base")).toHaveTextContent(
    "▼ 5.2% 전월 대비",
  );
  expect(screen.getByTestId("net-profit-kpis")).not.toHaveTextContent("잠정");
  expect(screen.getAllByRole("button", { name: "엑셀 다운로드" })).toHaveLength(
    1,
  );
  expect(
    within(screen.getByTestId("net-profit-title-actions")).getByTestId(
      "net-profit-excel",
    ),
  ).toBeEnabled();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /저장|업로드|조회|납부/ }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("계산 방법")).not.toBeInTheDocument();
  expect(reads()[0][0]).toBe(
    "/api/common/net-profit-statements?year=2026&baseMonth=202602",
  );
});
it("reuses table styles, current-month header, provisional copy and signed adjustment cells", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("money-unit"), {
    target: { value: "원" },
  });
  expect(
    screen.getByText(
      "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다.",
    ),
  ).toBeVisible();
  expect(screen.getByRole("columnheader", { name: /2월 작업중/ })).toHaveClass(
    "operating-cur",
  );
  expect(screen.getByRole("columnheader", { name: /누계 잠정/ })).toBeVisible();
  expect(screen.getByTestId("operating-row-0")).toHaveClass("operating-b");
  const adjustment = within(screen.getByTestId("operating-row-1"));
  expect(adjustment.getByText("−100,000")).toHaveClass("ls-neg");
  expect(adjustment.getByText("100,000")).toBeVisible();
  expect(adjustment.queryByText("+100,000")).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("operating-row-0")).getAllByRole("cell")[3],
  ).toHaveTextContent("–");
  expect(screen.getByTestId("net-profit-tax-note")).toHaveTextContent(
    "법인세는 연간 예상액을 달마다 나눈 금액(월할)입니다. 실제로 낸 법인세는 자금수지표에만 잡힙니다. 내부(자체 개발) 비용은 판관비(연구개발비)로 처리합니다.",
  );
  expect(screen.getByTestId("net-profit-link-UI-21")).toHaveAttribute(
    "href",
    "/screens/UI-21",
  );
});
it("closed month removes provisional flags", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "1" },
  });
  await ready("202601");
  expect(screen.queryByText(/아직 마감 전이라/)).not.toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: "누계" })).toBeVisible();
});
it("shows gross-profit shares and losses in red without a loss bar", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("money-unit"), {
    target: { value: "원" },
  });
  expect(screen.getByTestId("net-profit-contributions")).toHaveTextContent(
    "2026-02 매출총이익 기준",
  );
  expect(screen.getByTestId("net-profit-bar-tk")).toHaveAttribute(
    "aria-valuenow",
    "75",
  );
  expect(screen.getByTestId("net-profit-bar-mo")).toHaveAttribute(
    "aria-valuenow",
    "25",
  );
  expect(screen.getByTestId("net-profit-contribution-tk")).toHaveTextContent(
    "75.0% · 90만 원",
  );
  // 적자 유형: 막대는 비우고(0) 빨간 「적자 · −금액」만
  expect(screen.getByTestId("net-profit-bar-dp")).toHaveAttribute(
    "aria-valuenow",
    "0",
  );
  expect(
    within(screen.getByTestId("net-profit-contribution-dp")).getByText(
      "적자 · −10만 원",
    ),
  ).toHaveClass("operating-bad");
  expect(screen.getByTestId("net-profit-contribution-note")).toHaveTextContent(
    "적자 유형은 비율에서 빼고 금액만 표시합니다. 내부(자체 개발) 비용은 판관비(연구개발비)로 처리되어 여기에 넣지 않습니다.",
  );
});
it("all-loss operational months retain amounts and omit percentages and bars", async () => {
  losses = true;
  mount();
  await ready();
  expect(screen.getByTestId("net-profit-contribution-tk")).toHaveTextContent(
    "적자",
  );
  for (const meter of screen.getAllByRole("meter"))
    expect(meter).toHaveAttribute("aria-valuenow", "0");
  expect(screen.queryByTestId("net-profit-pre-start")).not.toBeInTheDocument();
});
it("prior year allows January and December, hides comparison columns and pre-start cards", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("period-year-prev"));
  await ready("202512");
  expect(screen.getByTestId("period-month")).toBeEnabled();
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "1" },
  });
  await ready("202501");
  expect(
    screen.queryByRole("columnheader", { name: "전년 누계" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "전년 대비" }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("net-profit-table-caption")).not.toHaveTextContent(
    "전년",
  );
  expect(screen.getByTestId("net-profit-pre-start")).toHaveTextContent(
    "2025년은 월별 손익만 옮겨 와서 계약 유형별 기여도는 볼 수 없습니다.",
  );
  expect(
    screen.queryByTestId("net-profit-contribution-tk"),
  ).not.toBeInTheDocument();
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "12" },
  });
  await ready("202512");
});
it("empty response retains table and period controls", async () => {
  empty = true;
  mount();
  await ready();
  expect(screen.getByTestId("net-profit-empty")).toBeVisible();
  expect(screen.getByTestId("period-selector")).toHaveAttribute(
    "data-variant",
    "cumulativeBar",
  );
  expect(screen.getByRole("columnheader", { name: "항목" })).toBeVisible();
});
it("query failure disables export and same-period retry recovers", async () => {
  failure = 500;
  mount();
  expect(await screen.findByText("기간 조회 실패")).toBeVisible();
  expect(screen.getByTestId("net-profit-excel")).toBeDisabled();
  expect(screen.queryByTestId("net-profit-empty")).not.toBeInTheDocument();
  failure = 0;
  fireEvent.click(screen.getByTestId("net-profit-retry"));
  await ready();
  expect(reads()).toHaveLength(2);
});
it("field validation is attached to period and focuses month", async () => {
  failure = 400;
  mount();
  expect(
    await screen.findByTestId("net-profit-error-baseMonth"),
  ).toHaveTextContent("기준월을 확인해 주세요.");
  expect(screen.getByTestId("period-month")).toHaveFocus();
});
it("permission response is distinct and does not invent a login flow", async () => {
  failure = 403;
  mount();
  expect(
    await screen.findByText("이 화면을 조회할 권한이 없습니다."),
  ).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "로그인" }),
  ).not.toBeInTheDocument();
});
it("download sends chosen period and amountUnit without columns and shows file failure", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("money-unit"), {
    target: { value: "백만 원" },
  });
  fireEvent.click(screen.getByTestId("net-profit-excel"));
  expect(await screen.findByTestId("net-profit-toast")).toHaveTextContent(
    "파일 조건 오류",
  );
  const call = request.mock.calls.find(([path]) =>
    String(path).includes("/excel?"),
  );
  const params = new URL(String(call![0]), "https://test.invalid").searchParams;
  expect([...params.keys()]).toEqual(["year", "baseMonth", "amountUnit"]);
  expect(params.get("amountUnit")).toBe("백만 원");
  expect(screen.getByTestId("net-profit-toast")).not.toHaveTextContent(
    "내려받았습니다",
  );
});
it("successful download shows completion only after the download helper resolves", async () => {
  let complete!: (file: string) => void;
  const exportFile = vi
    .spyOn(api, "getCommonNetProfitStatementsExcel")
    .mockImplementation(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    );
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("net-profit-excel"));
  expect(screen.getByTestId("net-profit-excel")).toBeDisabled();
  expect(screen.queryByTestId("net-profit-toast")).not.toBeInTheDocument();
  await act(async () => complete("순이익표.xlsx"));
  expect(await screen.findByTestId("net-profit-toast")).toHaveTextContent(
    "엑셀 파일을 내려받았습니다.",
  );
  expect(exportFile).toHaveBeenCalledWith(
    { year: 2026, baseMonth: "202602" },
    "천 원",
  );
});
it("zero contribution keeps zero amount without a visible bar", async () => {
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (!path.startsWith("/api/common/net-profit-statements?"))
      return original(path, init);
    const result = report();
    result.contributions[1] = {
      ...result.contributions[1],
      amount: 0,
      sharePercent: 0,
    };
    return ok(result);
  });
  mount();
  await ready();
  expect(screen.getByTestId("net-profit-contribution-mo")).toHaveTextContent(
    "0.0% · 0만 원",
  );
  expect(screen.getByTestId("net-profit-bar-mo")).toHaveAttribute(
    "aria-valuenow",
    "0",
  );
});
it("stale period response cannot replace the selected prior year", async () => {
  const original = request.getMockImplementation()!;
  let resolve!: (value: unknown) => void;
  request.mockImplementation((path: string, init?: RequestInit) =>
    path.includes("net-profit-statements?year=2026")
      ? new Promise((r) => {
          resolve = r;
        })
      : original(path, init),
  );
  mount();
  await waitFor(() => expect(reads()).toHaveLength(1));
  expect(screen.getByTestId("net-profit-loading")).toBeVisible();
  fireEvent.click(screen.getByTestId("period-year-prev"));
  await ready("202512");
  await act(async () => resolve(ok(report())));
  expect(screen.getByTestId("net-profit-summary")).toHaveAttribute(
    "data-base-month",
    "202512",
  );
});

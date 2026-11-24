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
import { OperatingProfitStatementPage } from "./OperatingProfitStatementPage";
import { OperatingProfitStatementTable } from "./OperatingProfitStatementTable";
import {
  OperatingProfitStatementTitleActions,
  OperatingProfitStatementWorkspaceProvider,
} from "./OperatingProfitStatementWorkspace";
import type { OperatingResult } from "./operatingProfitStatements";

const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data: structuredClone(data) }),
});
const metric = {
  amount: 1000000,
  previousYearChangePercent: -5.2,
  previousMonthChangePercent: null,
};
function report(month = "202602"): OperatingResult {
  const provisional = month === "202602";
  return {
    year: Number(month.slice(0, 4)),
    baseMonth: month,
    provisional,
    provisionalNotice: provisional
      ? "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."
      : null,
    summary: {
      cumulativeRevenue: metric,
      cumulativeGrossProfit: metric,
      cumulativeOperatingProfit: metric,
      operatingProfitRate: 12.3,
      baseMonthOperatingProfit: metric,
    },
    numericTable: {
      columns: [
        ...Array.from({ length: 12 }, (_, i) => ({
          label: `${i + 1}월`,
          provisional: false,
          working: provisional && i === 1,
        })),
        { label: "누계", provisional, working: false },
        { label: "전년 누계", provisional: false, working: false },
        { label: "전년 대비", provisional: false, working: false },
      ],
      rows: [
        {
          label: "서버 매출",
          style: "bold",
          isCost: false,
          values: Array.from({ length: 15 }, (_, i) => ({
            value: i === 14 ? -5.2 : i > 1 && i < 12 ? null : 1000000,
            format: i === 14 ? "percent" : "money",
          })),
        },
        {
          label: "서버 비용",
          style: "indent",
          isCost: true,
          values: Array.from({ length: 15 }, (_, i) => ({
            value: i === 14 ? 3.1 : null,
            format: i === 14 ? "percent" : "money",
          })),
        },
        {
          label: "영업이익률",
          style: "bold",
          isCost: false,
          values: Array.from({ length: 15 }, (_, i) => ({
            value: i === 14 ? 1.2 : 12.3,
            format: i === 14 ? "percentage-point" : "percent",
          })),
        },
      ],
    },
  };
}
let request: ReturnType<typeof vi.fn>;
let failure: number, empty: boolean;
const upload = {
  uploadId: "18",
  modifiedAt: null,
  statusName: "검사완료",
  successCount: 1,
  warningCount: 0,
  errorCount: 0,
  issues: [],
};
beforeEach(() => {
  failure = 0;
  empty = false;
  request = vi.fn(async (path: string, init?: RequestInit) => {
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
    if (path.startsWith("/api/common/operating-profit-statements?")) {
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
      return ok(result);
    }
    if (path === "/api/common/uploads" && init?.method === "POST")
      return ok(upload);
    if (path === "/api/common/uploads/18") return ok(upload);
    if (path === "/api/common/uploads/18/apply")
      return ok({ ...upload, statusName: "반영완료" });
    if (path.includes("UPLD_STAT"))
      return ok({ items: [{ code: "APPLIED", codeName: "반영완료" }] });
    if (path.includes("/excel?"))
      return {
        ok: false,
        status: 400,
        json: async () => ({ message: "파일 조건 오류", errors: [] }),
      };
    return ok({ items: [] });
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
    <MemoryRouter initialEntries={["/screens/UI-08"]}>
      <OperatingProfitStatementWorkspaceProvider>
        <OperatingProfitStatementTitleActions />
        <OperatingProfitStatementPage />
      </OperatingProfitStatementWorkspaceProvider>
    </MemoryRouter>,
  );
}
async function ready(month = "202602") {
  await waitFor(() =>
    expect(screen.getByTestId("operating-summary")).toHaveAttribute(
      "data-base-month",
      month,
    ),
  );
}
const reads = () =>
  request.mock.calls.filter(([path]) =>
    String(path).startsWith("/api/common/operating-profit-statements?"),
  );

it("renders latest cumulative period, exactly four server-backed cards and public title controls", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("period-selector")).toHaveAttribute(
    "data-variant",
    "cumulativeBar",
  );
  expect(screen.getByTestId("period-selector")).toHaveTextContent("1~2월 누계");
  expect(screen.getByTestId("operating-kpis").children).toHaveLength(4);
  expect(screen.getByTestId("operating-kpi-operating")).toHaveTextContent(
    "12.3%",
  );
  const title = within(screen.getByTestId("operating-title-actions"));
  expect(title.getByRole("button", { name: "전년 손익 양식" })).toBeEnabled();
  expect(title.getByRole("button", { name: "전년 손익 업로드" })).toBeEnabled();
  expect(screen.queryByRole("tablist")).not.toBeInTheDocument();
  expect(reads()[0][0]).toBe(
    "/api/common/operating-profit-statements?year=2026&baseMonth=202602",
  );
});
it("renders exact provisional copy and server headers, row style, cost direction and percent points without recalculation", async () => {
  mount();
  await ready();
  expect(
    screen.getByText(
      "2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다.",
    ),
  ).toBeVisible();
  expect(
    screen.getByRole("columnheader", { name: /2월 작업중/ }),
  ).toBeVisible();
  expect(screen.getByRole("columnheader", { name: /누계 잠정/ })).toBeVisible();
  const revenue = screen.getByTestId("operating-row-0"),
    cost = screen.getByTestId("operating-row-1");
  expect(revenue).toHaveClass("operating-b");
  expect(within(cost).getAllByRole("cell")[0]).toHaveClass("operating-ind");
  expect(within(revenue).getByText("−5.2%")).toHaveClass("operating-bad");
  expect(within(cost).getByText("+3.1%")).toHaveClass("operating-bad");
  expect(screen.getByText("+1.2%p")).toBeVisible();
  expect(within(revenue).getAllByRole("cell")[3]).toHaveTextContent("–");
  // 시안: 누계 3칸은 전년 같은 기간 대비만, 기준월 칸은 전월 대비만(값이 없으면 비움). 이름에 「잠정」을 붙이지 않는다.
  for (const id of ["revenue", "gross", "operating"])
    expect(screen.getByTestId(`operating-kpi-${id}`)).toHaveTextContent(
      "▼ 5.2% 전년 같은 기간 대비",
    );
  expect(screen.getByTestId("operating-kpi-operating")).toHaveTextContent(
    "이익률 12.3% · ▼ 5.2% 전년 같은 기간 대비",
  );
  expect(screen.getByTestId("operating-kpi-base")).toHaveTextContent(
    "2026-02 영업이익",
  );
  expect(screen.getByTestId("operating-kpi-base")).not.toHaveTextContent(
    "전년",
  );
  expect(screen.getByTestId("operating-kpis")).not.toHaveTextContent("잠정");
  expect(screen.queryByText(/전월 대비/)).not.toBeInTheDocument();
  expect(screen.getByTestId("operating-table-caption")).toHaveTextContent(
    "2026년 · 2026-02까지 실적 · 전년 같은 기간(1~2월)과 비교",
  );
  expect(
    screen.getByRole("columnheader", { name: /2월 작업중/ }),
  ).toHaveClass("operating-cur");
  expect(screen.getByTestId("operating-note")).toHaveTextContent(
    "매출 인식: 턴키는 청구일, 월단위·파견은 귀속월 기준. 부가세 제외. 전년 대비는 같은 기간 누계끼리 비교하며, 비용 항목은 늘면 빨강으로 표시합니다.",
  );
  expect(screen.queryByTestId("operating-formulas")).not.toBeInTheDocument();
});
it("a query error with the same text as its field shows only once", async () => {
  const base = request.getMockImplementation()!;
  request.mockImplementation(async (url: string, init?: RequestInit) =>
    String(url).startsWith("/api/common/operating-profit-statements?")
      ? { ok: false, status: 400, json: async () => ({ message: "조회 연도를 확인해 주세요.", errors: [{ field: "year", message: "조회 연도를 확인해 주세요." }] }) }
      : base(url, init),
  );
  mount();
  expect(await screen.findByTestId("operating-error-year")).toHaveTextContent("조회 연도를 확인해 주세요.");
  // 띠와 칸 밑에 같은 문구가 두 번 나오지 않는다(2026-11-24 사용자 결정).
  expect(screen.getAllByText("조회 연도를 확인해 주세요.")).toHaveLength(1);
});
it("closed base removes provisional banner and markers", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("period-month"), {
    target: { value: "1" },
  });
  await ready("202601");
  expect(screen.queryByText(/아직 마감 전이라/)).not.toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: "누계" })).toBeVisible();
});
it("favorable cost decrease and sales increase are not red and adjustments retain signs", () => {
  const table = report().numericTable;
  table.rows[0].values[14].value = 5.2;
  table.rows[1].values[14].value = -3.1;
  table.rows.push({
    label: "전월 조정",
    style: "plain",
    isCost: false,
    values: [
      { value: -100000, format: "money" },
      { value: 100000, format: "money" },
    ],
  });
  render(<OperatingProfitStatementTable table={table} unit="원" />);
  expect(screen.getByText("+5.2%")).not.toHaveClass("operating-bad");
  expect(screen.getByText("−3.1%")).not.toHaveClass("operating-bad");
  // 시안 money(): 음수는 빨간 −, 양수는 부호 없이.
  expect(screen.getByText("−100,000")).toHaveClass("ls-neg");
  expect(screen.getByText("100,000")).toBeVisible();
});
it("mobile keeps report and export with unchanged common upload hiding", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: query === "(max-width: 767px)",
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  mount();
  await ready();
  expect(
    screen.queryByRole("button", { name: "전년 손익 업로드" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "전년 손익 양식" }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("operating-excel")).toBeEnabled();
  expect(screen.getByTestId("operating-table-scroll")).toHaveClass(
    "operating-table-scroll",
  );
});
it("previous year opens at December and still lets the month change down to 2025", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("period-year-prev"));
  await ready("202512");
  // 시안: 지난 해도 누계 기준월 1~12월을 고를 수 있다.
  expect(screen.getByTestId("period-month")).toBeEnabled();
  expect(screen.getByTestId("period-selector")).toHaveTextContent(
    "1~12월 누계",
  );
  expect(screen.getByTestId("period-selector")).toHaveTextContent("결산 완료");
  expect(screen.getByTestId("period-year-prev")).toBeDisabled();
});
it("first year without previous-year columns drops the comparison caption and note", async () => {
  const original = request.getMockImplementation()!;
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    const response = await original(path, init);
    if (!String(path).includes("operating-profit-statements?year=2025"))
      return response;
    const body = await response.json();
    body.data.numericTable.columns = body.data.numericTable.columns.slice(0, 13);
    for (const row of body.data.numericTable.rows)
      row.values = row.values.slice(0, 13);
    return { ...response, json: async () => body };
  });
  mount();
  await ready();
  fireEvent.click(screen.getByTestId("period-year-prev"));
  await ready("202512");
  expect(screen.getByTestId("operating-table-caption")).toHaveTextContent(
    /^2025년 · 2025-12까지 실적$/,
  );
  expect(
    screen.queryByRole("columnheader", { name: "전년 누계" }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("operating-note")).toHaveTextContent(
    /^매출 인식: 턴키는 청구일, 월단위·파견은 귀속월 기준\. 부가세 제외\.$/,
  );
});
it("empty data retains report header period controls upload controls and download", async () => {
  empty = true;
  mount();
  await ready();
  expect(screen.getByTestId("operating-empty")).toBeVisible();
  expect(
    within(screen.getByTestId("operating-numeric-table")).getByRole(
      "columnheader",
      { name: "항목" },
    ),
  ).toBeVisible();
  expect(screen.getByTestId("period-selector")).toBeVisible();
  expect(screen.getByTestId("operating-excel")).toBeEnabled();
});
it("failed request is not disguised as empty and retry refreshes the same period", async () => {
  failure = 500;
  mount();
  expect(await screen.findByText("기간 조회 실패")).toBeVisible();
  expect(screen.queryByTestId("operating-empty")).not.toBeInTheDocument();
  expect(screen.getByTestId("operating-excel")).toBeDisabled();
  failure = 0;
  fireEvent.click(screen.getByTestId("operating-retry"));
  await ready();
  expect(reads()).toHaveLength(2);
});
it("field validation focuses period and permission failure has a distinct message without login", async () => {
  failure = 400;
  const view = mount();
  expect(
    await screen.findByTestId("operating-error-baseMonth"),
  ).toHaveTextContent("기준월을 확인해 주세요.");
  expect(screen.getByTestId("period-month")).toHaveFocus();
  view.unmount();
  failure = 403;
  mount();
  expect(
    await screen.findByText("이 화면을 조회할 권한이 없습니다."),
  ).toBeVisible();
});
it("download uses only chosen period and amountUnit and exposes failure", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByTestId("money-unit"), {
    target: { value: "백만 원" },
  });
  fireEvent.click(screen.getByTestId("operating-excel"));
  expect(await screen.findByTestId("operating-toast")).toHaveTextContent(
    "파일 조건 오류",
  );
  const call = request.mock.calls.find(([path]) =>
    String(path).includes("/excel?"),
  );
  const params = new URL(String(call![0]), "https://test.invalid").searchParams;
  expect([...params.keys()]).toEqual(["year", "baseMonth", "amountUnit"]);
  expect(params.get("amountUnit")).toBe("백만 원");
});
it("common B11 dialog inspects and applies through uploads only then reloads same report", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("button", { name: "전년 손익 업로드" }));
  expect(await screen.findByTestId("upload-dialog")).toBeVisible();
  expect(screen.queryByTestId("upload-year-month")).not.toBeInTheDocument();
  fireEvent.change(screen.getByTestId("upload-file"), {
    target: { files: [new File(["workbook"], "previous.xlsx")] },
  });
  fireEvent.click(screen.getByRole("button", { name: /^검사$/ }));
  expect(await screen.findByTestId("upload-result-dialog")).toBeVisible();
  expect(screen.getByTestId("upload-result-dialog")).toHaveTextContent(
    "빈칸은 값 없음으로 두고 화면에 「–」로 보입니다. 엑셀 값은 다시 계산하지 않습니다. 같은 연월을 다시 올리면 그 달을 덮어씁니다.",
  );
  await waitFor(() => expect(screen.getByTestId("upload-apply")).toBeEnabled());
  const uploadCall = request.mock.calls.find(
    ([path]) => path === "/api/common/uploads",
  )!;
  const body = uploadCall[1]?.body as FormData;
  expect(body.get("formTypeCode")).toBe("B11");
  expect(body.has("yearMonth")).toBe(false);
  fireEvent.click(screen.getByTestId("upload-apply"));
  await waitFor(() => expect(reads()).toHaveLength(2));
  expect(request).toHaveBeenCalledWith(
    "/api/common/uploads/18/apply",
    expect.objectContaining({ body: JSON.stringify({ modifiedAt: null }) }),
  );
});
it("aborts stale period requests so their late response cannot overwrite selected December", async () => {
  const original = request.getMockImplementation()!;
  let resolve!: (value: unknown) => void;
  request.mockImplementation((path: string, init?: RequestInit) =>
    String(path).includes("operating-profit-statements?year=2026")
      ? new Promise((r) => {
          resolve = r;
        })
      : original(path, init),
  );
  mount();
  await waitFor(() => expect(reads()).toHaveLength(1));
  fireEvent.click(screen.getByTestId("period-year-prev"));
  await ready("202512");
  await act(async () => resolve(ok(report())));
  expect(screen.getByTestId("operating-summary")).toHaveAttribute(
    "data-base-month",
    "202512",
  );
});

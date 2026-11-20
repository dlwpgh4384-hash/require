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
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { ProjectCostAnalysisPage } from "./ProjectCostAnalysisPage";
import { validProjectCostAnalysisPeriod } from "./useProjectCostAnalysisPeriod";
import type {
  CommonProjectCostAnalysisRow,
  CommonProjectCostAnalysisResult,
} from "./projectCostAnalyses";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
vi.mock("../../shared/time", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../shared/time")>()),
  koreanToday: () => "2026-06-15",
}));
let fetcher: ReturnType<typeof vi.fn>;
let result: CommonProjectCostAnalysisResult;
let saved: string | null;
let failed: boolean;
let monthlyFailed: boolean;
let validation: boolean;
let statusOptions: { code: string; codeName: string; useYn: string }[];
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
const row = (): CommonProjectCostAnalysisRow => ({
  projectId: "P",
  projectName: "프로젝트 알파",
  contractTypeCode: "TK",
  contractTypeName: "턴키",
  projectStatus: "진행",
  originalContractAmount: 10000,
  contractAmount: 12000,
  plannedMm: 12,
  actualMm: 4,
  cumulativeRevenue: 6000,
  totalCost: 4000,
  cumulativeProfit: 2000,
  cumulativeMarginRate: 33.333,
  expectedFinalMarginRate: 25,
  monthlyMarginRate: null,
  targetDifference: 5,
  budgetUsageRate: 80,
  unitPricePerMm: 1500,
  costPerMm: 1000,
  profitPerMm: 500,
  freeSupportMm: 0,
  freeSupportCost: 0,
  researchCost: null,
  lowMargin: false,
  budgetExceeded: false,
  alertCount: 0,
  alerts: [],
  budgetColor: "주황",
  missingMonthlyCost: false,
  missingMonthlyCostReason: null,
  costComposition: "DB 원가 정의 · 배분 원가·팀비·프로젝트경비·추가 직접 경비",
});
beforeEach(() => {
  saved = null;
  failed = false;
  monthlyFailed = false;
  validation = false;
  statusOptions = [
    { code: "ING", codeName: "진행", useYn: "Y" },
    { code: "END", codeName: "종료", useYn: "Y" },
  ];
  result = {
    summary: {
      cumulativeRevenue: 6000,
      cumulativeProfit: 2000,
      lowMarginCount: 0,
      budgetExceededCount: 0,
      researchCost: 0,
    },
    items: [row()],
    totalCount: 1,
    projectOptions: [{ projectId: "P", projectName: "프로젝트 알파" }],
  };
  fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path.includes("user-preferences"))
      return ok(
        {
          prefTypeCd: path.split("/").at(-2),
          prefKey: path.split("/").at(-1),
          prefVal: saved,
          modDttm: null,
          found: saved !== null,
        },
        {
          periodBasis: {
            cashBaseYm: "202501",
            latestYm: "202602",
            closedMonths: ["202601"],
          },
        },
      );
    if (path.includes("CTRT_TYPE"))
      return ok({
        items: [
          { code: "TK", codeName: "턴키", useYn: "Y" },
          { code: "MO", codeName: "월단위", useYn: "Y" },
          { code: "IN", codeName: "내부", useYn: "Y" },
        ],
      });
    if (path.includes("PROJ_STAT"))
      return ok({
        items: statusOptions,
      });
    if (path.includes("monthly-costs")) {
      if (monthlyFailed) throw new Error("월별 원가 통신 실패");
      return ok({
        items: [{ yearMonth: "202601", cost: 4000, distributionRevenue: 5000 }],
      });
    }
    if (path.includes("workforce"))
      return ok({
        items: [
          {
            developerId: "D",
            developerName: "선택 인력",
            jobName: "개발",
            monthlyMm: 0.5,
          },
        ],
      });
    if (path.includes("causes"))
      return ok({
        items: [
          {
            causeName: "무상지원",
            amount: 500,
            task: "PM이 투입을 확인합니다.",
            targetScreenId: "UI-11",
            navigationContext: JSON.stringify({
              projectId: "P",
              year: 2026,
              baseMonth: 6,
            }),
          },
        ],
        progressExcessAmount: null,
        remainderExplanation: null,
        primaryBarColor: "회색",
        referenceNotice: "경보가 없는 프로젝트라 참고용",
      });
    if (path.includes("simulation"))
      return ok({
        virtual: true,
        marginRateBefore: 25,
        marginRateAfter: 20,
        marginRateChange: -5,
        calculationBasis: "DB 계산 정의 · 가상",
      });
    if (path.includes("project-cost-analyses/excel"))
      return {
        ok: true,
        status: 200,
        headers: new Headers({
          "Content-Type":
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        }),
        blob: async () => new Blob(["test transfer"]),
      };
    if (path.includes("project-cost-analyses")) {
      if (failed) throw new Error("목록 통신 실패");
      if (validation)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: "조건 오류",
            errors: [
              { field: "contractTypeCode", message: "유형을 확인하세요." },
            ],
            meta: {},
          }),
        };
      return ok(result);
    }
    throw new Error(`Unexpected request: ${path} ${init?.method ?? "GET"}`);
  });
  vi.stubGlobal("fetch", fetcher);
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
function show() {
  return render(
    <MemoryRouter>
      <ProjectCostAnalysisPage />
    </MemoryRouter>,
  );
}
async function ready() {
  return within(screen.getByTestId("project-cost-list")).findByRole("cell", {
    name: "프로젝트 알파",
  });
}
async function open() {
  fireEvent.click(await ready());
  await screen.findByTestId("project-cost-detail");
}
it("first visit defaults to current Korean month even when latest closed month is earlier", async () => {
  show();
  await ready();
  expect(screen.getByRole("combobox", { name: "기준월" })).toHaveValue("6");
  expect(
    fetcher.mock.calls.some(
      ([path]) =>
        String(path).includes("year=2026") &&
        String(path).includes("baseMonth=6"),
    ),
  ).toBe(true);
});
it("saved prior-year months take precedence and remain selectable without a December override", async () => {
  saved = JSON.stringify({ year: 2025, month: 3 });
  show();
  await ready();
  expect(screen.getByRole("combobox", { name: "기준월" })).toHaveValue("3");
  expect(
    within(screen.getByRole("combobox", { name: "기준월" })).getAllByRole(
      "option",
    ),
  ).toHaveLength(12);
  expect(
    validProjectCostAnalysisPeriod({ year: 2026, baseMonth: 7 }, 2025),
  ).toBe(false);
});
it("empty response preserves five summaries filters toolbar and table headers", async () => {
  result.items = [];
  result.totalCount = 0;
  show();
  await screen.findByText("조건에 맞는 결과가 없습니다.");
  // Loading removes the download action; inspect the current toolbar after the response.
  const download = await within(
    screen.getByTestId("project-cost-list"),
  ).findByRole("button", { name: /엑셀/ });
  expect(screen.getByTestId("project-cost-summary").children).toHaveLength(5);
  expect(screen.getByRole("combobox", { name: "프로젝트명" })).toBeVisible();
  expect(download).toBeVisible();
  expect(download).toBeEnabled();
  expect(
    within(screen.getByTestId("project-cost-list")).getByRole("columnheader", {
      name: /누적 원가/,
    }),
  ).toBeVisible();
});
it("failed request is not an empty result and retries the same conditions", async () => {
  failed = true;
  show();
  await screen.findByText(/목록 통신 실패/);
  expect(
    screen.queryByText("조건에 맞는 결과가 없습니다."),
  ).not.toBeInTheDocument();
  failed = false;
  fireEvent.click(screen.getByRole("button", { name: /다시 시도/ }));
  await ready();
});
it("field errors are shown and the first invalid filter receives focus", async () => {
  show();
  await ready();
  validation = true;
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await screen.findByText("유형을 확인하세요.");
  expect(screen.getByRole("combobox", { name: "계약유형" })).toHaveFocus();
});
it("filters apply only to list requests and existing summary remains API-owned", async () => {
  show();
  await ready();
  fireEvent.change(screen.getByRole("combobox", { name: "프로젝트명" }), {
    target: { value: "P" },
  });
  fireEvent.click(screen.getByRole("checkbox", { name: "경보 있는 것만" }));
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(
        ([path]) =>
          String(path).includes("projectId=P") &&
          String(path).includes("alertOnly=true"),
      ),
    ).toBe(true),
  );
  expect(screen.getByText(/요약은 목록 조건과 관계없이/)).toBeVisible();
});
it.each([
  ["ING", "업무 진행 중", "진행"],
  ["END", "업무 종료됨", "종료"],
])(
  "maps %s display names to the existing projectStatus request enum",
  async (code, label, value) => {
    statusOptions = [{ code, codeName: label, useYn: "Y" }];
    show();
    await ready();
    await screen.findByRole("option", { name: label });
    fireEvent.change(screen.getByRole("combobox", { name: "상태" }), {
      target: { value },
    });
    fireEvent.click(screen.getByRole("button", { name: "조회" }));
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(([path]) => {
          const url = new URL(String(path), "http://test");
          return (
            url.pathname === "/api/common/project-cost-analyses" &&
            url.searchParams.get("projectStatus") === value
          );
        }),
      ).toBe(true),
    );
  },
);
it("focuses the period control when the API rejects baseMonth", async () => {
  show();
  await ready();
  fetcher.mockImplementationOnce(async () => ({
    ok: false,
    status: 400,
    json: async () => ({
      success: false,
      meta: {},
      message: "기준월을 확인해 주세요.",
      errors: [{ field: "baseMonth", message: "기준월을 확인해 주세요." }],
    }),
  }));
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(screen.getByRole("combobox", { name: "기준월" })).toHaveFocus(),
  );
  expect(
    screen.getByRole("combobox", { name: "기준월" }).closest("label"),
  ).toHaveTextContent("기준월을 확인해 주세요.");
});
it("missing plan and salary retain explicit unavailable labels and reasons", async () => {
  result.items[0] = {
    ...row(),
    plannedMm: null,
    expectedFinalMarginRate: null,
    missingMonthlyCost: true,
    missingMonthlyCostReason: "D 월 인건비 없음",
  };
  show();
  await ready();
  expect(screen.getByText("계획 없음")).toBeVisible();
  expect(screen.getByText("단가 없음")).toHaveAttribute(
    "title",
    "D 월 인건비 없음",
  );
});
it("internal row leaves commercial cells blank and displays actual MM and research cost", async () => {
  result.items[0] = {
    ...row(),
    contractTypeCode: "IN",
    contractTypeName: "내부",
    contractAmount: null,
    originalContractAmount: null,
    plannedMm: null,
    cumulativeRevenue: null,
    totalCost: null,
    cumulativeProfit: null,
    cumulativeMarginRate: null,
    expectedFinalMarginRate: null,
    monthlyMarginRate: null,
    targetDifference: null,
    budgetUsageRate: null,
    unitPricePerMm: null,
    costPerMm: null,
    profitPerMm: null,
    freeSupportMm: null,
    freeSupportCost: null,
    researchCost: 4000,
  };
  show();
  const tr = (await ready()).closest("tr")!;
  expect(within(tr).queryByText("계획 없음")).not.toBeInTheDocument();
  expect(within(tr).getByText("내부")).toBeVisible();
});
it("detail keeps exact monthly/workforce tab order and uses only selected project", async () => {
  show();
  await ready();
  await open();
  const tabs = within(screen.getByTestId("project-cost-detail")).getAllByRole(
    "tab",
  );
  expect(tabs[0]).toHaveTextContent("월별 원가");
  expect(tabs[1]).toHaveTextContent("투입 인력");
  expect(await screen.findByText("배분 기준 매출(참고)")).toBeVisible();
  fireEvent.click(tabs[1]);
  expect(await screen.findByText("선택 인력")).toBeVisible();
  expect(
    fetcher.mock.calls.some(([path]) => String(path).includes("/P/workforce?")),
  ).toBe(true);
  expect(screen.queryByText("급여")).not.toBeInTheDocument();
});
it("detail failure retries locally without losing the list", async () => {
  monthlyFailed = true;
  show();
  await ready();
  await open();
  await screen.findByText(/월별 원가 통신 실패/);
  monthlyFailed = false;
  fireEvent.click(
    within(screen.getByTestId("project-cost-detail")).getByRole("button", {
      name: "다시 시도",
    }),
  );
  expect(await screen.findByText("202601")).toBeVisible();
  expect(screen.getByTestId("project-cost-list")).toBeInTheDocument();
});
it("grey causes show guidance and navigate with decoded context to existing screen", async () => {
  function Destination() {
    const location = useLocation();
    return (
      <pre data-testid="destination">{JSON.stringify(location.state)}</pre>
    );
  }
  render(
    <MemoryRouter initialEntries={["/screens/UI-04"]}>
      <Routes>
        <Route path="/screens/UI-04" element={<ProjectCostAnalysisPage />} />
        <Route path="/screens/UI-11" element={<Destination />} />
      </Routes>
    </MemoryRouter>,
  );
  await ready();
  await open();
  fireEvent.click(screen.getByRole("button", { name: "원가 초과 원인 열기" }));
  expect(
    await screen.findByText("경보가 없는 프로젝트라 참고용"),
  ).toBeVisible();
  expect(
    screen.getByTestId("project-cost-cause-0").querySelector(".is-grey"),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "이동" }));
  expect(await screen.findByTestId("destination")).toHaveTextContent(
    '"projectId":"P"',
  );
});
it("virtual simulation validates fields without saving and requires discard confirmation", async () => {
  show();
  await ready();
  await open();
  fireEvent.click(screen.getByRole("button", { name: "마진 시뮬레이션 열기" }));
  fireEvent.click(screen.getByRole("button", { name: "계산" }));
  expect(screen.getByLabelText("인원 변화")).toHaveFocus();
  for (const [label, value] of [
    ["인원 변화", "-1"],
    ["1인 월 원가", "100"],
    ["적용 개월", "2"],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "계산" }));
  expect(
    await screen.findByTestId("project-cost-simulation-result"),
  ).toHaveTextContent("가상");
  fireEvent.click(screen.getByRole("button", { name: "취소·닫기" }));
  expect(screen.getByRole("alertdialog")).toHaveTextContent(
    "나가면 입력한 내용이 사라집니다.",
  );
  fireEvent.click(
    within(screen.getByRole("alertdialog")).getByRole("button", {
      name: "취소",
    }),
  );
  expect(screen.getByLabelText("인원 변화")).toHaveValue(-1);
  expect(
    fetcher.mock.calls.filter(
      ([path, init]) =>
        String(path).includes("project-cost-analyses") &&
        init?.method &&
        init.method !== "GET",
    ),
  ).toHaveLength(0);
});
it("monthly simulation retains months and shows one-month notice", async () => {
  result.items[0] = {
    ...row(),
    contractTypeCode: "MO",
    contractTypeName: "월단위",
  };
  show();
  await ready();
  await open();
  fireEvent.click(screen.getByRole("button", { name: "마진 시뮬레이션 열기" }));
  expect(screen.getByText("월단위·파견은 한 달 기준")).toBeVisible();
  expect(screen.getByLabelText("적용 개월")).toBeVisible();
  expect(screen.getByLabelText("1인 단가")).toBeVisible();
});

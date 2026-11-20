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
import { PersonalCostProfitPage } from "./PersonalCostProfitPage";
import { DeveloperDetail } from "../developers/DeveloperDetail";
import {
  subscribeDeveloperAction,
  type DeveloperRequest,
} from "../developers/developerRequest";
import type {
  PersonalCostProfitResult,
  PersonalCostProfitRow,
} from "./personalCostProfitTypes";

vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile: false, tablet: false }),
}));
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, data: structuredClone(data), meta }),
});
let request: ReturnType<typeof vi.fn>;
let result: PersonalCostProfitResult;
let failed: boolean;
let validation: boolean;
const row = (): PersonalCostProfitRow => ({
  developerId: "D1",
  developerName: "김개발",
  managementNumber: "K-0001",
  affiliationName: "소속",
  positionName: "사원",
  employeeTypeName: "정규직",
  joinDate: "2026-01-01",
  operatingRate: 100,
  mm: 1.01,
  unitPrice: 0,
  monthlySalary: 1000,
  allowance: 20,
  companyInsurance: 10,
  retirementReserve: 80,
  personalCost: 1110,
  teamExpenses: 5,
  projectExpenses: 5,
  totalCost: 1120,
  operatingProfit: -1120,
  profitRate: null,
  mmOverOne: true,
  personalInsurance: 12,
  withholdingTax: 0,
  incomeTax: 24,
  netPay: 984,
  allocations: [
    {
      projectId: "P1",
      assignmentId: "PAID",
      projectName: "동일 프로젝트",
      jobName: "백엔드",
      contractTypeCode: "MO",
      free: false,
      mm: 0.5,
      unitPrice: 0,
      allocatedCost: 550,
      operatingProfit: -550,
    },
    {
      projectId: "P1",
      assignmentId: "FREE",
      projectName: "동일 프로젝트",
      jobName: "백엔드",
      contractTypeCode: "MO",
      free: true,
      mm: 0.51,
      unitPrice: 0,
      allocatedCost: 570,
      operatingProfit: -570,
    },
    {
      projectId: "IDLE",
      assignmentId: "IDLE",
      projectName: "남는 몫(대기 원가)",
      jobName: null,
      contractTypeCode: null,
      free: false,
      mm: 0,
      unitPrice: 0,
      allocatedCost: 3000,
      operatingProfit: -3000,
    },
  ],
});
beforeEach(() => {
  failed = false;
  validation = false;
  const person = row();
  result = {
    items: [person],
    totals: { totalCost: 1120, monthlySalary: 1000, personalCost: 1110 },
    summary: {
      totalPeople: 2,
      averageOperatingPeople: 0.5,
      averageEarningPeople: 0,
      averageUnitPrice: 0,
      averageCost: 1120,
      averageOperatingProfit: -1120,
      lossPeople: 1,
      operatingRate: 50,
      earningRate: 0,
      leavePeople: 1,
      unitPricePeople: 1,
      averageDirectPeople: 1,
    },
    contributions: [
      {
        jobCode: "BE",
        jobName: "백엔드",
        gradeCode: "MD",
        gradeName: "중급",
        people: 1,
        contributionPerPerson: -1120,
      },
    ],
  };
  request = vi.fn(async (path: string) => {
    if (path.startsWith("/api/common/user-preferences/"))
      return ok(
        {
          prefTypeCd: path.split("/").at(-2),
          prefKey: path.split("/").at(-1),
          found: false,
          prefVal: null,
          modDttm: null,
        },
        {
          periodBasis: {
            latestYm: "202602",
            cashBaseYm: "202501",
            closedMonths: ["202601"],
          },
        },
      );
    if (path.startsWith("/api/common/personal-cost-profits?")) {
      if (failed) throw new Error("network");
      if (validation)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            meta: {},
            message: "직무를 확인해 주세요.",
            errors: [
              { field: "jobCode", message: "사용할 수 없는 직무입니다." },
            ],
          }),
        };
      return ok(result);
    }
    if (path.includes("/code-groups/EMP_TYPE/"))
      return ok({ items: [{ code: "FT", codeName: "정규직", useYn: "Y" }] });
    if (path.includes("/code-groups/JOB/"))
      return ok({ items: [{ code: "BE", codeName: "백엔드", useYn: "Y" }] });
    if (path.startsWith("/api/common/clients?"))
      return ok({
        items: [{ clientId: "C1", clientName: "소속", useYn: "Y" }],
      });
    if (path === "/api/common/parts")
      return ok({ items: [{ partId: "PART", partName: "영업1", useYn: "Y" }] });
    if (path === "/api/common/developers/D1")
      return ok({
        developerId: "D1",
        name: "김개발",
        managementNumber: "M1",
        organizationName: "소속",
        useYn: "Y",
        employeeTypeName: "정규직",
        skillNames: [],
        workStatusName: "가동",
      });
    if (path.endsWith("/statuses"))
      return ok({
        items: [
          {
            statusId: "S1",
            statusName: "휴직",
            startDate: "2026-02-01",
            endDate: null,
          },
        ],
        canManageStatuses: true,
        canCancelPastRetirement: true,
      });
    return ok({ items: [] });
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
const mount = () =>
  render(
    <MemoryRouter>
      <PersonalCostProfitPage />
    </MemoryRouter>,
  );
const ready = async () => {
  await screen.findByTestId("list-row-D1");
};
const query = () =>
  new URL(
    request.mock.calls
      .filter(([path]) =>
        String(path).startsWith("/api/common/personal-cost-profits?"),
      )
      .at(-1)![0],
    "https://example.invalid",
  ).searchParams;

it("renders the five summaries, exact year guidance, required table order and null profit rate without invented badges", async () => {
  mount();
  await ready();
  expect(screen.getByText("그 해 1월부터 지금까지 누계")).toBeVisible();
  expect(
    screen
      .getByTestId("personal-cost-profit-summary")
      .querySelectorAll(".ls-kpi"),
  ).toHaveLength(5);
  // 시안 kpi 5칸(공통 KpiCards): 이름·값·보조 문구, 금액은 kor()(만 원·억 원)
  const summary = screen.getByTestId("personal-cost-profit-summary");
  expect(
    [...summary.querySelectorAll(".ls-kpi")].map((card) => card.textContent),
  ).toEqual([
    "총 인원2명휴직 1명 포함 · 월평균 가동 0.5 · 가득 0.0",
    "인당 매출 (누계)0만 원단가 칸이 있는 1명(0 포함)",
    "인당 원가 (누계)0만 원원가합계 ÷ 1명 (휴직 제외)",
    "인당 영업이익 (누계)−0만 원영업이익 합 ÷ 1명 · 적자 1명",
    "가동률 · 가득률 ⓘ50.0% · 0.0%1~2월 월평균 · 월평균 재직 직접 인력 1.0명 기준(간접 제외, 퇴사자는 퇴사한 달까지)",
  ]);
  expect(within(summary).getByTitle(/가동률 = 그 달 일하는/)).toBeVisible();
  // 올해 배지(시안 yearonly 막대), 기준월·표시 방식 고르기는 없음
  expect(within(screen.getByTestId("year-bar")).getByText("올해")).toBeVisible();
  expect(screen.queryByLabelText("표시 방식")).not.toBeInTheDocument();
  expect(screen.queryByText(/기준월/)).not.toBeInTheDocument();
  const headers = within(screen.getByTestId("personal-cost-profit-list"))
    .getAllByRole("columnheader")
    .map((header) => header.textContent?.replace(/ [▲▼]/g, ""));
  expect(headers).toEqual([
    "이름",
    "소속",
    "직위",
    "구분",
    "입사일",
    "가동률 ⓘ",
    "MM",
    "매출",
    "급여 ⓘ",
    "그룹장·팀장 수당",
    "4대보험(회사)",
    "퇴직금 적립",
    "개인별 원가 ⓘ",
    "팀비 ⓘ",
    "프로젝트경비 ⓘ",
    "원가합계 ⓘ",
    "영업이익 ⓘ",
    "이익률",
  ]);
  // 이름 아래 관리번호
  expect(
    within(screen.getByTestId("list-cell-D1-developerName")).getByText(
      "K-0001",
    ),
  ).toBeVisible();
  // 도구 줄: 「총 N명」만(「총 N건」 없음)
  expect(screen.getByTestId("personal-period-text")).toHaveTextContent(
    "2026년 1~2월 누계 · 총 1명",
  );
  expect(screen.queryByText(/총 1건/)).not.toBeInTheDocument();
  expect(
    within(screen.getByTestId("list-row-D1")).getByText("1.0 초과"),
  ).toBeVisible();
  expect(
    within(screen.getByTestId("list-row-D1")).getByText("–"),
  ).toBeVisible();
  expect(
    screen.queryByText(/Infinity|NaN|잠정|작업중|마감/),
  ).not.toBeInTheDocument();
  expect(query().get("displayMode")).toBe("연누계");
  expect(query().get("baseMonth")).toBe("2");
});
it("keeps the year, conditions, list tools and headers for zero results", async () => {
  result.items = [];
  result.contributions = [];
  mount();
  await screen.findByTestId("list-empty");
  expect(screen.getByTestId("year-bar")).toBeVisible();
  expect(screen.getByTestId("list-search")).toBeVisible();
  expect(screen.getByTestId("list-download")).toBeVisible();
  expect(screen.getByRole("columnheader", { name: "이름" })).toBeVisible();
  expect(screen.getByLabelText("이름")).toBeVisible();
});
it("keeps contribution matrix folded initially with exactly four grades and missing cells shown as zero people", async () => {
  mount();
  await ready();
  const panel = screen.getByTestId("personal-cost-profit-contributions");
  expect(panel).not.toHaveAttribute("open");
  fireEvent.click(
    screen.getByTestId("personal-cost-profit-contributions-toggle"),
  );
  expect(
    within(panel)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["직무", "초급", "중급", "고급", "특급", "전체"]);
  expect(
    within(panel).getByRole("rowheader", { name: "백엔드" }),
  ).toBeVisible();
  // 시안: 「금액 (인원)」, 없는 칸은 「–」, 단위는 화면 단위(처음 천 원)
  expect(panel.querySelector("summary")).toHaveTextContent(
    "직무·등급별 인당 공헌이익 (인당 평균 영업이익 · 단위 천 원)",
  );
  const cells = [
    ...screen.getByTestId("personal-contribution-BE").querySelectorAll("td"),
  ].map((cell) => cell.textContent);
  expect(cells).toEqual(["–", "−1 (1)", "–", "–", "−1 (1)"]);
});
it("money cells, footer and contribution cells follow the selected unit", async () => {
  mount();
  await ready();
  expect(screen.getByLabelText("단위")).toHaveValue("천 원");
  expect(screen.getByTestId("list-cell-D1-totalCost")).toHaveTextContent("1");
  expect(screen.getByTestId("list-cell-D1-operatingProfit")).toHaveTextContent(
    "−1",
  );
  expect(
    screen.getByTestId("list-cell-D1-operatingProfit").querySelector(".ls-neg"),
  ).not.toBeNull();
  fireEvent.change(screen.getByLabelText("단위"), { target: { value: "원" } });
  expect(screen.getByTestId("list-cell-D1-totalCost")).toHaveTextContent(
    "1,120",
  );
  expect(screen.getByTestId("list-cell-D1-operatingProfit")).toHaveTextContent(
    "−1,120",
  );
  expect(screen.getByTestId("list-footer-totalCost")).toHaveTextContent(
    "1,120",
  );
  expect(
    screen.getByTestId("personal-cost-profit-contributions"),
  ).toHaveTextContent("단위 원");
  fireEvent.change(screen.getByLabelText("단위"), {
    target: { value: "백만 원" },
  });
  expect(screen.getByTestId("list-cell-D1-totalCost")).toHaveTextContent("0");
  // 바꾼 단위는 계정 설정(AMT_UNIT/GLOBAL)에 저장한다
  await waitFor(() =>
    expect(
      request.mock.calls.filter(
        ([path, init]) =>
          path === "/api/common/user-preferences/AMT_UNIT/GLOBAL" &&
          init?.method === "PUT",
      ),
    ).toHaveLength(2),
  );
});
it("opens with the unit saved for the account (AMT_UNIT)", async () => {
  const base = request.getMockImplementation()!;
  request.mockImplementation(async (path: string, init?: RequestInit) =>
    path === "/api/common/user-preferences/AMT_UNIT/GLOBAL" && !init?.method
      ? ok({
          prefTypeCd: "AMT_UNIT",
          prefKey: "GLOBAL",
          found: true,
          prefVal: "원",
          modDttm: "2026-11-20T00:00:00Z",
        })
      : base(path, init),
  );
  mount();
  await ready();
  await waitFor(() => expect(screen.getByLabelText("단위")).toHaveValue("원"));
  expect(screen.getByTestId("list-cell-D1-totalCost")).toHaveTextContent(
    "1,120",
  );
});
it("shows 자사 and – for empty affiliation and position, and a spacer instead of ▸ without projects", async () => {
  result.items[0].affiliationName = "";
  result.items[0].positionName = "";
  result.items[0].allocations = [];
  mount();
  await ready();
  expect(screen.getByTestId("list-cell-D1-affiliationName")).toHaveTextContent(
    "자사",
  );
  expect(screen.getByTestId("list-cell-D1-positionName")).toHaveTextContent(
    "–",
  );
  expect(screen.queryByTestId("personal-expand-D1")).not.toBeInTheDocument();
  // 단추 자리만큼 빈칸(시안 width:28px)
  expect(
    screen
      .getByTestId("list-cell-D1-developerName")
      .querySelector(".personal-cost-profit-exp-space"),
  ).toBeInTheDocument();
  expect(screen.getByTestId("list-cell-D1-mm")).toHaveTextContent("–");
});
it("offers 자사 in the affiliation filter and the mockup detail options with an applied count", async () => {
  mount();
  await ready();
  await waitFor(() =>
    expect(screen.getByLabelText("소속")).toHaveTextContent("자사"),
  );
  expect(screen.getByLabelText("이름")).toHaveAttribute(
    "placeholder",
    "이름 일부",
  );
  // 서버 한도(100자)와 같게 입력 칸에서 막는다
  expect(screen.getByLabelText("이름")).toHaveAttribute("maxLength", "100");
  expect(screen.getByLabelText("직원 구분")).toBeVisible();
  expect(screen.queryByLabelText("보기")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("personal-advanced"));
  expect(screen.getByTestId("personal-advanced")).toHaveTextContent(
    "상세 조건 ▲",
  );
  expect(
    [...(screen.getByLabelText("보기") as HTMLSelectElement).options].map(
      (option) => option.text,
    ),
  ).toEqual(["전체", "적자 인력만", "동시 투입·1.0 초과만"]);
  expect(
    [...(screen.getByLabelText("턴키 단가") as HTMLSelectElement).options].map(
      (option) => option.text,
    ),
  ).toEqual(["포함", "미포함 (엑셀 「턴키단가미포함」 시트와 같음)"]);
  fireEvent.change(screen.getByLabelText("보기"), {
    target: { value: "적자만" },
  });
  expect(screen.getByTestId("personal-advanced")).toHaveTextContent(
    "상세 조건 ▲ (1개 적용)",
  );
  fireEvent.change(screen.getByLabelText("소속"), {
    target: { value: "__self" },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() => expect(query().get("affiliationId")).toBe("__self"));
  expect(query().get("view")).toBe("적자만");
});
it("shows the mockup calculation table with the monthly-average period text", async () => {
  mount();
  await ready();
  const box = screen.getByTestId("personal-cost-profit-calculation");
  const names = [...box.querySelectorAll("tbody tr")].map(
    (row) => row.querySelector("td")?.textContent,
  );
  expect(names.slice(0, 5)).toEqual([
    "기간",
    "개인별 원가",
    "원가합계",
    "단가(매출)",
    "영업이익",
  ]);
  expect(box).toHaveTextContent("2026년 1월~2월 매달 값을 더함 (연 누계)");
  fireEvent.click(screen.getByTestId("personal-mode-월평균"));
  await waitFor(() =>
    expect(box).toHaveTextContent(
      "1~2월 누계 ÷ 그 사람이 일한 달 수 (월 평균)",
    ),
  );
});
it("submits literal name and DB code filters, advanced OR-view and turnkey choice with no contract filter", async () => {
  mount();
  await ready();
  fireEvent.change(screen.getByLabelText("이름"), { target: { value: "김%" } });
  await waitFor(() =>
    expect(screen.getByLabelText("직무")).toHaveTextContent("백엔드"),
  );
  fireEvent.change(screen.getByLabelText("직무"), { target: { value: "BE" } });
  fireEvent.change(screen.getByLabelText("영업 파트"), {
    target: { value: "PART" },
  });
  fireEvent.click(screen.getByTestId("personal-advanced"));
  fireEvent.change(screen.getByLabelText("보기"), {
    target: { value: "동시 투입·1.0 초과만" },
  });
  fireEvent.change(screen.getByLabelText("턴키 단가"), {
    target: { value: "false" },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() => expect(query().get("name")).toBe("김%"));
  expect(query().get("jobCode")).toBe("BE");
  expect(query().get("salesPartId")).toBe("PART");
  expect(query().get("view")).toBe("동시 투입·1.0 초과만");
  expect(query().get("includeTurnkey")).toBe("false");
  expect(screen.queryByLabelText("계약유형")).not.toBeInTheDocument();
});
it("reset preserves period and columns while removing optional codes and restoring initial choices", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByLabelText("이전 해"));
  await waitFor(() => expect(query().get("year")).toBe("2025"));
  expect(query().get("baseMonth")).toBe("12");
  fireEvent.change(screen.getByLabelText("이름"), { target: { value: "김" } });
  fireEvent.click(screen.getByTestId("list-reset"));
  await waitFor(() => expect(screen.getByLabelText("이름")).toHaveValue(""));
  expect(query().get("year")).toBe("2025");
  expect(query().get("view")).toBe("전체");
});
it("display modes round trip as GET-only queries", async () => {
  mount();
  await ready();
  // 시안 도구 줄 [연 누계][월 평균] 붙은 단추
  expect(screen.getByTestId("personal-mode-연누계")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  fireEvent.click(screen.getByTestId("personal-mode-월평균"));
  await waitFor(() => expect(query().get("displayMode")).toBe("월평균"));
  await ready();
  expect(screen.getByTestId("personal-mode-월평균")).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  expect(screen.getByTestId("personal-mode-월평균")).toHaveClass("ls-btn-p");
  expect(screen.getByTestId("personal-period-text")).toHaveTextContent(
    "2026년 1~2월 월 평균(÷ 사람마다 일한 달 수) · 총 1명",
  );
  expect(
    screen.getByRole("columnheader", { name: /^단가/ }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: /^월 급여/ }),
  ).toBeInTheDocument();
  expect(screen.getByText("평균 단가 (월)")).toBeVisible();
  fireEvent.click(screen.getByTestId("personal-mode-연누계"));
  await waitFor(() => expect(query().get("displayMode")).toBe("연누계"));
  expect(
    request.mock.calls.every(
      ([, init]) => !init?.method || init.method === "GET",
    ),
  ).toBe(true);
});
it("individual and all-project expansion preserve paid/free identity and idle rows", async () => {
  mount();
  await ready();
  // 시안 ibtn sm: 아이콘 단추(chevR → chevD 16px), 이름 「… 프로젝트별 보기」, 펼침 상태
  const expand = screen.getByRole("button", { name: "김개발 프로젝트별 보기" });
  expect(expand).toHaveClass("ls-ibtn", "ls-ibtn-sm");
  expect(expand).toHaveAttribute("aria-expanded", "false");
  expect(expand.querySelector("svg")).toHaveAttribute("width", "16");
  expect(expand.querySelector("path")).toHaveAttribute("d", "M9 6l6 6-6 6");
  expect(expand).not.toHaveTextContent("▸");
  fireEvent.click(screen.getByTestId("personal-expand-D1"));
  expect(expand).toHaveAttribute("aria-expanded", "true");
  expect(expand.querySelector("path")).toHaveAttribute("d", "M6 9l6 6 6-6");
  // 같은 표의 칸에 맞춘 펼친 줄(연월 칸 없음): 이름 칸 「└ 프로젝트 · 직무」, 무상지원 배지
  const paid = screen.getByTestId("personal-allocation-PAID");
  const free = screen.getByTestId("personal-allocation-FREE");
  expect(paid.tagName).toBe("TR");
  expect(paid.querySelectorAll("td")).toHaveLength(18);
  expect(paid.querySelectorAll("td")[0]).toHaveTextContent(
    "└ 동일 프로젝트 · 백엔드",
  );
  expect(free).toHaveTextContent("무상지원");
  expect(paid).not.toHaveTextContent("무상지원");
  expect(free.querySelectorAll("td")[6]).toHaveTextContent("0.51");
  // 무상지원은 단가 「–」, 원가합계 칸은 배분 원가와 계산 도움말
  expect(free.querySelectorAll("td")[7]).toHaveTextContent("–");
  expect(free.querySelector("td span[title]")).toHaveAttribute(
    "title",
    "배분 원가 = 원가합계 × 0.51 ÷ 1.01",
  );
  expect(screen.queryByText(/202602/)).not.toBeInTheDocument();
  const idle = screen.getByTestId("personal-idle-D1");
  expect(idle.querySelectorAll("td")[0]).toHaveTextContent(
    "└ 남는 몫(대기 원가)",
  );
  expect(idle.querySelectorAll("td")[15]).toHaveTextContent("3");
  expect(idle.querySelectorAll("td")[16]).toHaveTextContent("−3");
  expect(screen.getByTestId("personal-expand-all")).toHaveTextContent(
    "모두 접기",
  );
  fireEvent.click(screen.getByTestId("personal-expand-all"));
  expect(
    screen.queryByTestId("personal-allocation-PAID"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("personal-expand-all")).toHaveTextContent(
    "프로젝트 모두 펼치기",
  );
  fireEvent.click(screen.getByTestId("personal-expand-all"));
  expect(screen.getByTestId("personal-allocation-PAID")).toBeVisible();
});
it("does not offer sensitive column headings or column options when policy omits their wire fields", async () => {
  delete result.items[0].monthlySalary;
  delete result.items[0].personalCost;
  delete result.totals.monthlySalary;
  delete result.totals.personalCost;
  mount();
  await ready();
  expect(
    screen.queryByRole("columnheader", { name: /^급여/ }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: /^개인별 원가/ }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("list-columns"));
  expect(
    screen.queryByRole("checkbox", { name: "급여" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("checkbox", { name: "개인별 원가" }),
  ).not.toBeInTheDocument();
  // 시안 SENS03: 구성 열(수당·4대보험·세금·퇴직금·실지급액)도 표와 열 설정에서 빠진다
  for (const name of [
    "그룹장·팀장 수당",
    "4대보험(회사)",
    "4대보험(본인)",
    "3.3% 원천세",
    "근로소득세",
    "퇴직금 적립",
    "실지급액",
  ]) {
    expect(
      screen.queryByRole("checkbox", { name }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("columnheader", { name: new RegExp(`^${name.replace(/[().]/g, "\\$&")}`) }),
    ).not.toBeInTheDocument();
  }
  expect(screen.getByRole("checkbox", { name: "팀비" })).toBeInTheDocument();
});
it("shows upload reference columns only after column selection and passes their keys in requests", async () => {
  mount();
  await ready();
  expect(
    screen.queryByRole("columnheader", { name: /^근로소득세/ }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("list-columns"));
  fireEvent.click(screen.getByRole("checkbox", { name: "근로소득세" }));
  await waitFor(() => expect(query().get("columns")).toContain("incomeTax"));
  await ready();
  // 참고 열은 제목 뒤 「 (참고)」, 순서는 시안 C03(4대보험(회사) 뒤)
  expect(
    screen.getByRole("columnheader", { name: "근로소득세 (참고)" }),
  ).toBeVisible();
  const headers = within(screen.getByTestId("personal-cost-profit-list"))
    .getAllByRole("columnheader")
    .map((header) => header.textContent);
  expect(headers.indexOf("근로소득세 (참고)")).toBe(
    headers.indexOf("4대보험(회사)") + 1,
  );
});
it("failed requests keep the condition skeleton and recover through the list retry", async () => {
  failed = true;
  mount();
  await screen.findByTestId("list-error");
  expect(screen.getByLabelText("이름")).toBeVisible();
  failed = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  await ready();
});
it("shows field-local errors and focuses the offending DB option", async () => {
  validation = true;
  mount();
  expect(await screen.findByText("사용할 수 없는 직무입니다.")).toBeVisible();
  expect(screen.getByLabelText("직무")).toHaveFocus();
});
it("시안 openPerson: 사람을 누르면 개발자 풀과 같은 상세 — [수정]·[사용 중지]는 개발자 풀로 넘기고 [상태 등록]은 상세 안에서 연다", async () => {
  const received: DeveloperRequest[] = [];
  const stop = subscribeDeveloperAction((value) => received.push(value));
  try {
    mount();
    await ready();
    fireEvent.click(screen.getByTestId("list-row-D1"));
    let detail = await screen.findByTestId("developer-detail");
    await waitFor(() =>
      expect(within(detail).getByText("M1 · 소속 · 정규직")).toBeVisible(),
    );
    // 근무 상태 이력 탭의 [상태 등록](서버가 canManageStatuses를 준 경우)
    fireEvent.click(within(detail).getByRole("tab", { name: /근무 상태/ }));
    expect(
      within(detail).getByRole("button", { name: /상태 등록/ }),
    ).toBeVisible();
    // [수정] → 상세를 닫고 개발자 풀(UI-02)에 수정 요청
    fireEvent.click(within(detail).getByRole("button", { name: "수정" }));
    expect(screen.queryByTestId("developer-detail")).not.toBeInTheDocument();
    expect(received).toEqual([
      expect.objectContaining({
        action: "edit",
        developer: expect.objectContaining({ developerId: "D1" }),
      }),
    ]);
    // [사용 중지] → 개발자 풀의 확인 창으로 넘김(여기서 바로 바꾸지 않음)
    fireEvent.click(screen.getByTestId("list-row-D1"));
    detail = await screen.findByTestId("developer-detail");
    await waitFor(() =>
      expect(
        within(detail).getByRole("button", { name: "사용 중지" }),
      ).toBeEnabled(),
    );
    fireEvent.click(within(detail).getByRole("button", { name: "사용 중지" }));
    expect(received.at(-1)).toEqual(
      expect.objectContaining({ action: "stop" }),
    );
    expect(
      request.mock.calls.some(
        ([, init]) => init && (init as RequestInit).method && (init as RequestInit).method !== "GET",
      ),
    ).toBe(false);
  } finally {
    stop();
  }
});
it("screen-body: 다른 업무 화면처럼 흰 카드로 감싸지 않는다", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("personal-cost-profit-page")).toHaveClass(
    "screen-body",
  );
});
it("default developer detail preserves the existing UI-02 edit and stop callbacks", async () => {
  const onEdit = vi.fn(),
    onStop = vi.fn();
  render(
    <DeveloperDetail
      developerId="D1"
      onClose={vi.fn()}
      onEdit={onEdit}
      onStop={onStop}
      onChanged={vi.fn()}
      onNotice={vi.fn()}
    />,
  );
  const detail = await screen.findByTestId("developer-detail");
  await waitFor(() =>
    expect(within(detail).getByRole("button", { name: "수정" })).toBeEnabled(),
  );
  fireEvent.click(within(detail).getByRole("button", { name: "수정" }));
  expect(onEdit).toHaveBeenCalledWith(
    expect.objectContaining({ developerId: "D1" }),
  );
  fireEvent.click(within(detail).getByRole("button", { name: "사용 중지" }));
  expect(onStop).toHaveBeenCalledWith(
    expect.objectContaining({ developerId: "D1" }),
  );
});

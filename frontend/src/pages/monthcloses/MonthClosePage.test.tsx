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
import { MemoryRouter, useLocation } from "react-router-dom";
import { MonthClosePage } from "./MonthClosePage";
import {
  canReopenMonthClose,
  type MonthClose,
  type MonthCloseHistoryItem,
} from "./monthCloses";

const badgeRefresh = vi.hoisted(() => vi.fn());
vi.mock("../../app/menuBadges", () => ({
  requestMenuBadgeRefresh: badgeRefresh,
}));
let mobile = false;
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => ({ mobile, tablet: false }),
}));
let row: MonthClose;
let history: MonthCloseHistoryItem[];
let fetcher: ReturnType<typeof vi.fn>;
let detailFailure: boolean;
let historyFailure: boolean;
let impactFailure: boolean;
let writeFailure: number;
let fieldFailure: boolean;
const ok = (data: unknown, meta: unknown = {}) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta, data: structuredClone(data) }),
});
const failed = (status: number, message: string, errors: unknown[] = []) => ({
  ok: false,
  status,
  json: async () => ({ success: false, meta: {}, message, errors }),
});
const writes = () =>
  fetcher.mock.calls.filter(
    ([url, init]) =>
      String(url).includes("/month-closes/") && init?.method === "PUT",
  );
function Location() {
  return <p data-testid="test-location">{useLocation().pathname}</p>;
}
function mount(roles?: readonly string[]) {
  render(
    <MemoryRouter initialEntries={["/screens/UI-14"]}>
      <MonthClosePage roles={roles} />
      <Location />
    </MemoryRouter>,
  );
}
async function ready() {
  expect(await screen.findByTestId("month-close-check-5")).toBeInTheDocument();
}
async function openReopen() {
  const button = await screen.findByRole("button", { name: "마감 해제" });
  await waitFor(() => expect(button).toBeEnabled());
  fireEvent.click(button);
  return screen.findByTestId("month-close-reopen-dialog");
}
beforeEach(() => {
  mobile = false;
  detailFailure = false;
  historyFailure = false;
  impactFailure = false;
  writeFailure = 0;
  fieldFailure = false;
  badgeRefresh.mockClear();
  row = {
    yearMonth: "2026-10",
    isClosed: false,
    modifiedAt: null,
    closedAt: null,
    closedBy: null,
    totalCount: 6,
    attentionCount: 2,
    checks: [
      {
        name: "급여 업로드",
        status: "정상",
        description: "0명 업로드 완료",
        targetScreenId: null,
      },
      {
        name: "MM 1.0 초과자",
        status: "정상",
        description: "0명 MM 초과",
        targetScreenId: "UI-12",
      },
      {
        name: "세금계산서 번호 미입력",
        status: "확인 필요",
        description: "2건 번호 미입력",
        targetScreenId: "UI-13",
      },
      {
        name: "재무 항목",
        status: "정상",
        description: "0건 입력",
        targetScreenId: null,
      },
      {
        name: "영업 파트 정산",
        status: "확인 필요",
        description: "2개 중 1개 미확정",
        targetScreenId: "UI-19",
      },
      {
        name: "청구 누락 점검",
        status: "정상",
        description: "0건 청구 누락",
        targetScreenId: "UI-13",
      },
    ],
  };
  history = [];
  fetcher = vi.fn(async (url: string, init?: RequestInit) => {
    if (
      /\/PERIOD\/UI-(12|13)$/.test(url) &&
      init?.method === "PUT" &&
      JSON.parse(JSON.parse(String(init.body)).prefVal).month !== undefined
    )
      return failed(400, "연도형 기간에는 월을 입력하지 말아 주세요.");
    if (url.includes("/user-preferences/"))
      return ok(
        {
          prefTypeCd: "PERIOD",
          prefKey: url.split("/").at(-1),
          found: true,
          prefVal:
            init?.method === "PUT"
              ? JSON.parse(String(init.body)).prefVal
              : JSON.stringify({ year: 2026, month: 10 }),
          modDttm: null,
        },
        {
          periodBasis: {
            latestYm: row.isClosed ? "202611" : "202610",
            cashBaseYm: "202601",
            closedMonths: row.isClosed ? ["202610"] : [],
          },
        },
      );
    if (url.startsWith("/api/common/month-close-history"))
      return historyFailure
        ? failed(500, "이력 조회 실패")
        : ok({ items: history });
    if (url.endsWith("/reopen-impact"))
      return impactFailure
        ? failed(500, "조정 조회 실패")
        : ok({ yearMonth: row.yearMonth, previousAdjustmentCount: 3 });
    if (init?.method === "PUT") {
      if (fieldFailure)
        return failed(400, "사유를 확인해 주세요.", [
          { field: "reason", message: "사유 입력 오류" },
        ]);
      if (writeFailure)
        return failed(
          writeFailure,
          "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
        );
      row.isClosed = url.endsWith("/confirm");
      row.modifiedAt = "2026-11-01T01:02:03.123456Z";
      row.closedAt = row.isClosed ? row.modifiedAt : null;
      row.closedBy = row.isClosed ? "저장 처리자" : null;
      history.push({
        yearMonth: row.yearMonth,
        action: row.isClosed ? "마감" : "해제",
        processor: "저장 처리자",
        occurredAt: row.modifiedAt,
        reason: row.isClosed ? null : JSON.parse(String(init.body)).reason,
        remainingChecks: row.isClosed ? ["영업 파트 정산"] : [],
      });
      return ok(row);
    }
    return detailFailure ? failed(500, "점검 조회 실패") : ok(row);
  });
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("renders exactly six live checks and zero reference counts as normal", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("banner-warn")).toHaveTextContent(
    "점검 6개 중 2개 확인 필요",
  );
  expect(screen.getAllByTestId(/^month-close-check-\d+$/)).toHaveLength(6);
  expect(screen.getByTestId("month-close-check-0")).toHaveTextContent("정상");
  expect(screen.getByTestId("month-close-check-3")).toHaveTextContent("정상");
  expect(await screen.findByText("마감 이력 없음")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("recheck uses only GET and displays changed source results", async () => {
  mount();
  await ready();
  row.checks[2].description = "1건 번호 미입력";
  fireEvent.click(screen.getByRole("button", { name: "다시 점검" }));
  expect(await screen.findByText("1건 번호 미입력")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("loading shows no fabricated counts or mutation action", async () => {
  fetcher.mockImplementation(() => new Promise(() => undefined));
  mount();
  expect(screen.getByText("기간을 불러오는 중입니다.")).toBeInTheDocument();
  expect(screen.queryByTestId("banner-warn")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "마감하기" }),
  ).not.toBeInTheDocument();
});
it("failed check request offers a retry without mutation controls", async () => {
  detailFailure = true;
  mount();
  expect(await screen.findByText("점검 조회 실패")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "마감하기" }),
  ).not.toBeInTheDocument();
  detailFailure = false;
  fireEvent.click(screen.getByRole("button", { name: "다시 점검" }));
  await ready();
});
it("history failures remain in their own retryable region", async () => {
  historyFailure = true;
  mount();
  await ready();
  expect(await screen.findByText("이력 조회 실패")).toBeInTheDocument();
  historyFailure = false;
  fireEvent.click(screen.getByTestId("month-close-history-retry"));
  expect(await screen.findByText("마감 이력 없음")).toBeInTheDocument();
});
it("one confirmation dialog permits remaining warnings and sends only the nullable version", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("button", { name: "마감하기" }));
  const dialog = await screen.findByTestId("month-close-confirm-dialog");
  expect(dialog).toHaveTextContent("남은 점검: 2개");
  expect(screen.getAllByRole("alertdialog")).toHaveLength(1);
  fireEvent.click(within(dialog).getByRole("button", { name: /^마감$/ }));
  expect(await screen.findByTestId("banner-lock")).toHaveTextContent(
    "저장 처리자",
  );
  expect(writes()).toHaveLength(1);
  expect(writes()[0][0]).toBe("/api/common/month-closes/2026-10/confirm");
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({ modifiedAt: null });
  expect(badgeRefresh).toHaveBeenCalledOnce();
  expect(await screen.findByTestId("month-close-history-0")).toHaveTextContent(
    "영업 파트 정산",
  );
  expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveValue(
    "2026-10",
  );
});
it("409 retains the confirmation dialog and original month/state", async () => {
  writeFailure = 409;
  mount();
  await ready();
  fireEvent.click(screen.getByRole("button", { name: "마감하기" }));
  const dialog = await screen.findByTestId("month-close-confirm-dialog");
  fireEvent.click(within(dialog).getByRole("button", { name: /^마감$/ }));
  await waitFor(() =>
    expect(dialog).toHaveTextContent("다른 사람이 먼저 바꿨습니다."),
  );
  expect(screen.getByTestId("banner-warn")).toBeInTheDocument();
  expect(badgeRefresh).not.toHaveBeenCalled();
});
it("cancelling confirmation makes no write", async () => {
  mount();
  await ready();
  fireEvent.click(screen.getByRole("button", { name: "마감하기" }));
  fireEvent.click(
    within(await screen.findByTestId("month-close-confirm-dialog")).getByRole(
      "button",
      { name: "취소" },
    ),
  );
  expect(
    screen.queryByTestId("month-close-confirm-dialog"),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("closed state exposes only reopen, and non-admin policy hides it", async () => {
  row.isClosed = true;
  row.modifiedAt = "2026-11-01T00:00:00Z";
  mount(["FIN"]);
  await ready();
  expect(canReopenMonthClose(["ADMIN"])).toBe(true);
  expect(canReopenMonthClose(["FIN"])).toBe(false);
  expect(
    screen.queryByRole("button", { name: "마감 해제" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "마감하기" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "다시 점검" }),
  ).not.toBeInTheDocument();
});
it("reopen waits for impact and never displays fake zero on failure", async () => {
  row.isClosed = true;
  impactFailure = true;
  mount();
  const dialog = await openReopen();
  expect(await within(dialog).findByText("조정 조회 실패")).toBeInTheDocument();
  expect(within(dialog).getByRole("button", { name: /^해제$/ })).toBeDisabled();
  expect(within(dialog).queryByText(/전월 조정: 0건/)).not.toBeInTheDocument();
  impactFailure = false;
  fireEvent.click(
    within(dialog).getByRole("button", { name: "조정 건수 다시 조회" }),
  );
  expect(
    await within(dialog).findByText(/다음달 전월 조정: 3건/),
  ).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("blank reason is focused and never sent", async () => {
  row.isClosed = true;
  mount();
  const dialog = await openReopen();
  const submit = within(dialog).getByRole("button", { name: /^해제$/ });
  await waitFor(() => expect(submit).toBeEnabled());
  fireEvent.change(within(dialog).getByLabelText("해제 사유 (필수)"), {
    target: { value: "   " },
  });
  fireEvent.click(submit);
  expect(
    await within(dialog).findByText("해제 사유를 1~500자로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(within(dialog).getByLabelText("해제 사유 (필수)")).toHaveFocus();
  expect(writes()).toHaveLength(0);
});
it("server field failure retains reopen reason and focuses the field", async () => {
  row.isClosed = true;
  fieldFailure = true;
  mount();
  const dialog = await openReopen();
  const input = within(dialog).getByLabelText("해제 사유 (필수)");
  const submit = within(dialog).getByRole("button", { name: /^해제$/ });
  await waitFor(() => expect(submit).toBeEnabled());
  fireEvent.change(input, { target: { value: "정정 필요" } });
  fireEvent.click(submit);
  expect(await within(dialog).findByText("사유 입력 오류")).toBeInTheDocument();
  expect(input).toHaveValue("정정 필요");
  expect(input).toHaveFocus();
});
it("successful reopen refreshes same month and history and preserves reason payload", async () => {
  row.isClosed = true;
  row.modifiedAt = "2026-11-01T00:00:00Z";
  mount();
  const dialog = await openReopen();
  const submit = within(dialog).getByRole("button", { name: /^해제$/ });
  await waitFor(() => expect(submit).toBeEnabled());
  fireEvent.change(within(dialog).getByLabelText("해제 사유 (필수)"), {
    target: { value: " 다음달 조정 검토 " },
  });
  fireEvent.click(submit);
  expect(await screen.findByTestId("banner-warn")).toBeInTheDocument();
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    modifiedAt: "2026-11-01T00:00:00Z",
    reason: " 다음달 조정 검토 ",
  });
  expect(await screen.findByTestId("month-close-history-0")).toHaveTextContent(
    "해제",
  );
  expect(badgeRefresh).toHaveBeenCalledOnce();
});
it.each([
  [1, "UI-12"],
  [2, "UI-13"],
  [4, "UI-19"],
  [5, "UI-13"],
])(
  "check %s saves the target screen's period shape before navigating to %s",
  async (index, target) => {
    mount();
    await ready();
    fireEvent.click(screen.getByTestId(`month-close-visit-${index}`));
    await waitFor(() =>
      expect(screen.getByTestId("test-location")).toHaveTextContent(
        `/screens/${target}`,
      ),
    );
    const preference = fetcher.mock.calls.find(
      ([url, init]) =>
        url.endsWith(`/PERIOD/${target}`) && init?.method === "PUT",
    );
    expect(preference).toBeDefined();
    expect(JSON.parse(JSON.parse(String(preference![1].body)).prefVal)).toEqual(
      target === "UI-19" ? { year: 2026, month: 10 } : { year: 2026 },
    );
  },
);
it("unresolved check destinations do not expose invented navigation", async () => {
  mount();
  await ready();
  expect(screen.queryByTestId("month-close-visit-0")).not.toBeInTheDocument();
  expect(screen.queryByTestId("month-close-visit-3")).not.toBeInTheDocument();
});
it("mobile direct entry is PC-only and never loads business data", async () => {
  mobile = true;
  mount();
  expect(
    screen.getByText("PC에서 이용할 수 있는 화면입니다."),
  ).toBeInTheDocument();
  await act(async () => undefined);
  expect(
    fetcher.mock.calls.some(
      ([url]) =>
        url.includes("/month-closes/") || url.includes("/month-close-history"),
    ),
  ).toBe(false);
});

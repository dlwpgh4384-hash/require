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
import { useEffect } from "react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { MonthClosePage } from "./MonthClosePage";
import {
  MonthCloseTitleActions,
  MonthCloseWorkspaceProvider,
} from "./MonthCloseWorkspace";
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
let impactCount: number;
let writeFailure: number;
let fieldFailure: boolean;
let visited: string[];
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
const detailReads = () =>
  fetcher.mock.calls.filter(
    ([url, init]) =>
      /\/month-closes\/\d{4}-\d{2}$/.test(String(url)) && !init?.method,
  );
const historyReads = () =>
  fetcher.mock.calls.filter(([url]) =>
    String(url).startsWith("/api/common/month-close-history"),
  );
const preferenceReads = () =>
  fetcher.mock.calls.filter(
    ([url, init]) =>
      String(url).includes("/user-preferences/") && !init?.method,
  );
function Location() {
  const location = useLocation();
  useEffect(() => {
    visited.push(location.pathname);
  }, [location]);
  return <p data-testid="test-location">{location.pathname}</p>;
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
async function readySubmit(dialog: HTMLElement) {
  const submit = within(dialog).getByRole("button", { name: "마감 해제" });
  await waitFor(() => expect(submit).toBeEnabled());
  return submit;
}
function closeRow() {
  row.isClosed = true;
  row.reopenable = true;
  row.modifiedAt = "2026-11-01T00:00:00Z";
  row.closedAt = "2026-11-01T00:00:00Z";
  row.closedBy = "홍길동";
}
beforeEach(() => {
  mobile = false;
  detailFailure = false;
  historyFailure = false;
  impactFailure = false;
  impactCount = 3;
  writeFailure = 0;
  fieldFailure = false;
  visited = [];
  badgeRefresh.mockClear();
  row = {
    yearMonth: "2026-10",
    isClosed: false,
    reopenable: false,
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
        description: "없음",
        targetScreenId: "UI-12",
      },
      {
        name: "세금계산서 번호 미입력",
        status: "확인 필요",
        description: "2건 — 계산서 번호를 아직 안 넣은 청구",
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
        description: "2개 파트 중 1개 미확정",
        targetScreenId: "UI-19",
      },
      {
        name: "청구 누락 점검",
        status: "정상",
        description: "투입과 청구가 맞음",
        targetScreenId: "UI-13",
      },
    ],
  };
  history = [];
  fetcher = vi.fn(async (url: string, init?: RequestInit) => {
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
        : ok({ yearMonth: row.yearMonth, previousAdjustmentCount: impactCount });
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
      row.reopenable = row.isClosed;
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

it("renders the mockup banner and six live checks, reference counts as normal", async () => {
  mount();
  await ready();
  expect(screen.getByTestId("banner-warn")).toHaveTextContent(
    "2026-10 마감 전 점검 6개 중 2개를 확인해야 합니다.",
  );
  expect(screen.getByRole("heading", { name: "마감 점검" })).toBeInTheDocument();
  expect(screen.getAllByTestId(/^month-close-check-\d+$/)).toHaveLength(6);
  expect(screen.getByTestId("month-close-check-0")).toHaveTextContent("정상");
  expect(screen.getByTestId("month-close-check-3")).toHaveTextContent("정상");
  expect(
    screen.queryByText(/마감 뒤 정정은 다음달 재무 항목의/),
  ).not.toBeInTheDocument();
  expect(await screen.findByText("마감 이력 없음")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("no remaining checks shows the info banner", async () => {
  row.attentionCount = 0;
  row.checks = row.checks.map((check) => ({ ...check, status: "정상" }));
  mount();
  await ready();
  expect(screen.getByTestId("banner-info")).toHaveTextContent(
    "2026-10 마감 전 점검 6개 중 0개를 확인해야 합니다.",
  );
  expect(screen.queryByTestId(/^month-close-visit-/)).not.toBeInTheDocument();
});
it("recheck uses only GET, keeps the rows while reloading and announces the result", async () => {
  mount();
  await ready();
  row.checks[2].description = "1건 — 계산서 번호를 아직 안 넣은 청구";
  let release!: () => void;
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementationOnce(
    (url: string, init?: RequestInit) =>
      new Promise((resolve) => {
        release = () => resolve(implementation(url, init));
      }),
  );
  fireEvent.click(screen.getByRole("button", { name: "다시 점검" }));
  expect(screen.getByTestId("month-close-check-5")).toBeInTheDocument();
  expect(screen.getByTestId("banner-warn")).toBeInTheDocument();
  await act(async () => release());
  expect(
    await screen.findByText("1건 — 계산서 번호를 아직 안 넣은 청구"),
  ).toBeInTheDocument();
  expect(await screen.findByTestId("month-close-toast")).toHaveTextContent(
    "점검을 다시 했습니다. 확인할 항목 2개가 남아 있습니다.",
  );
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
it("a non-JSON server error shows the common month close message", async () => {
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation((url: string, init?: RequestInit) =>
    /\/month-closes\/\d{4}-\d{2}$/.test(url)
      ? Promise.resolve({
          ok: false,
          status: 502,
          json: async () => {
            throw new SyntaxError("Unexpected token <");
          },
        })
      : implementation(url, init),
  );
  mount();
  expect(
    await screen.findByText("월 마감을 처리하지 못했습니다."),
  ).toBeInTheDocument();
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
it("history uses the shared table and shows the action as a badge", async () => {
  history = [
    {
      yearMonth: "2026-10",
      action: "해제",
      processor: "홍길동",
      occurredAt: "2026-11-02T01:00:00Z",
      reason: "9월 청구 누락 2건 반영",
      remainingChecks: [],
    },
    {
      yearMonth: "2026-10",
      action: "마감",
      processor: "홍길동",
      occurredAt: "2026-11-01T09:20:00Z",
      reason: null,
      remainingChecks: ["영업 파트 정산", "청구 누락 점검"],
    },
  ];
  mount();
  const reopened = await screen.findByTestId("month-close-history-0");
  const region = screen.getByTestId("month-close-history");
  expect(within(region).getByRole("heading", { name: "마감 이력" })).toBeInTheDocument();
  expect(region.querySelector(".ls-twrap table.ls-t")).not.toBeNull();
  expect(within(reopened).getByText("해제")).toHaveClass("ls-b", "ls-b-warn");
  // 한국 시간으로 보인다(UTC 01:00 → 10:00).
  expect(reopened).toHaveTextContent("2026-11-02 10:00");
  const closed = screen.getByTestId("month-close-history-1");
  expect(within(closed).getByText("마감")).toHaveClass("ls-b", "ls-b-lock");
  expect(closed).toHaveTextContent("2026-11-01 18:20");
  expect(closed).toHaveTextContent("영업 파트 정산, 청구 누락 점검");
  expect(closed).toHaveTextContent("–");
});
it("one confirmation dialog permits remaining warnings and sends only the nullable version", async () => {
  mount();
  await ready();
  await waitFor(() => expect(historyReads()).toHaveLength(1));
  const detailCount = detailReads().length;
  const preferenceCount = preferenceReads().length;
  fireEvent.click(screen.getByRole("button", { name: "마감하기" }));
  const dialog = await screen.findByTestId("month-close-confirm-dialog");
  expect(dialog).toHaveAccessibleName("2026-10를 마감할까요?");
  expect(dialog).toHaveTextContent(
    "마감하면 이 달 자료는 고칠 수 없습니다. 확인할 항목 2개(세금계산서 번호 미입력, 영업 파트 정산)가 남아 있습니다. 남은 항목은 마감 이력에 함께 남습니다. 확정하지 않은 영업 파트 정산은 마감과 함께 자동 확정됩니다(마감 뒤 틀린 금액은 다음 달에 반영).",
  );
  expect(screen.getAllByRole("alertdialog")).toHaveLength(1);
  fireEvent.click(within(dialog).getByRole("button", { name: "마감하기" }));
  expect(await screen.findByTestId("banner-lock")).toHaveTextContent(
    "2026-10는 2026-11-01 10:02 저장 처리자이(가) 마감했습니다. 마감 해제는 시스템 관리자만 할 수 있습니다.",
  );
  expect(await screen.findByTestId("month-close-toast")).toHaveTextContent(
    "2026-10를 마감했습니다.",
  );
  expect(writes()).toHaveLength(1);
  expect(writes()[0][0]).toBe("/api/common/month-closes/2026-10/confirm");
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({ modifiedAt: null });
  expect(badgeRefresh).toHaveBeenCalledOnce();
  expect(await screen.findByTestId("month-close-history-0")).toHaveTextContent(
    "영업 파트 정산",
  );
  // 저장 결과를 그대로 쓴다: 점검·기간 기준을 다시 읽지 않고 이력만 다시 읽는다.
  expect(detailReads()).toHaveLength(detailCount);
  expect(preferenceReads()).toHaveLength(preferenceCount);
  expect(historyReads()).toHaveLength(2);
  expect(screen.getByRole("combobox", { name: "조회 월" })).toHaveValue(
    "2026-10",
  );
});
it("no remaining checks says every check is normal in the confirmation", async () => {
  row.attentionCount = 0;
  row.checks = row.checks.map((check) => ({ ...check, status: "정상" }));
  mount();
  await ready();
  fireEvent.click(screen.getByRole("button", { name: "마감하기" }));
  expect(
    await screen.findByTestId("month-close-confirm-dialog"),
  ).toHaveTextContent("점검 항목이 모두 정상입니다.");
});
it("409 closes the confirmation, shows the common conflict message and reloads", async () => {
  writeFailure = 409;
  mount();
  await ready();
  await waitFor(() => expect(historyReads()).toHaveLength(1));
  const detailCount = detailReads().length;
  fireEvent.click(screen.getByRole("button", { name: "마감하기" }));
  const dialog = await screen.findByTestId("month-close-confirm-dialog");
  fireEvent.click(within(dialog).getByRole("button", { name: "마감하기" }));
  expect(await screen.findByTestId("month-close-error")).toHaveTextContent(
    "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
  );
  expect(
    screen.queryByTestId("month-close-confirm-dialog"),
  ).not.toBeInTheDocument();
  await waitFor(() => expect(detailReads()).toHaveLength(detailCount + 1));
  await waitFor(() => expect(historyReads()).toHaveLength(2));
  expect(screen.getByTestId("banner-warn")).toBeInTheDocument();
  expect(badgeRefresh).not.toHaveBeenCalled();
});
it("409 on reopen closes the dialog and reloads", async () => {
  closeRow();
  writeFailure = 409;
  mount();
  const dialog = await openReopen();
  const submit = await readySubmit(dialog);
  fireEvent.change(within(dialog).getByLabelText("해제 사유 *"), {
    target: { value: "정정" },
  });
  fireEvent.click(submit);
  expect(await screen.findByTestId("month-close-error")).toHaveTextContent(
    "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
  );
  expect(
    screen.queryByTestId("month-close-reopen-dialog"),
  ).not.toBeInTheDocument();
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
  closeRow();
  mount(["FIN"]);
  await ready();
  expect(canReopenMonthClose(["ADMIN"])).toBe(true);
  expect(canReopenMonthClose(["FIN"])).toBe(false);
  expect(screen.getByTestId("banner-lock")).toHaveTextContent(
    "마감 해제는 시스템 관리자만 할 수 있습니다.",
  );
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
it("a closed month shows every check as 확인 완료 except payroll, with no 확인하기 (mockup closeItems)", async () => {
  closeRow();
  mount();
  await ready();
  const payroll = row.checks.findIndex((check) => check.name === "급여 업로드");
  row.checks.forEach((check, index) => {
    const line = screen.getByTestId(`month-close-check-${index}`);
    expect(line).toHaveTextContent(
      index === payroll ? check.description : "확인 완료",
    );
    expect(line).not.toHaveTextContent("확인 필요");
  });
  expect(
    screen.queryByRole("button", { name: "확인하기" }),
  ).not.toBeInTheDocument();
});
it("history reads every month, not only the selected one (mockup)", async () => {
  mount();
  await ready();
  await waitFor(() =>
    expect(
      fetcher.mock.calls.some(
        ([url]) => String(url) === "/api/common/month-close-history",
      ),
    ).toBe(true),
  );
  expect(
    fetcher.mock.calls.some(([url]) =>
      String(url).includes("month-close-history?"),
    ),
  ).toBe(false);
});
it("a closed month that is not the most recent one hides reopen and says why", async () => {
  closeRow();
  row.reopenable = false;
  mount();
  await ready();
  expect(screen.getByTestId("banner-lock")).toHaveTextContent(
    "2026-10는 2026-11-01 09:00 홍길동이(가) 마감했습니다. 가장 최근에 마감한 달만 해제할 수 있습니다.",
  );
  expect(
    screen.queryByRole("button", { name: "마감 해제" }),
  ).not.toBeInTheDocument();
});
it("an older response without reopenable never offers reopen", async () => {
  closeRow();
  delete row.reopenable;
  mount();
  await ready();
  expect(screen.getByTestId("banner-lock")).toHaveTextContent(
    "가장 최근에 마감한 달만 해제할 수 있습니다.",
  );
  expect(
    screen.queryByRole("button", { name: "마감 해제" }),
  ).not.toBeInTheDocument();
});
it("closed banner without time or processor has no stray separators", async () => {
  closeRow();
  row.closedAt = null;
  row.closedBy = null;
  mount();
  await ready();
  const banner = screen.getByTestId("banner-lock");
  expect(banner).toHaveTextContent(
    "2026-10는 마감했습니다. 마감 해제는 시스템 관리자만 할 수 있습니다.",
  );
  expect(banner.textContent).not.toContain("·");
  expect(banner.textContent).not.toContain("이(가)");
});
it("reopen waits for impact and never displays fake zero on failure", async () => {
  closeRow();
  impactFailure = true;
  mount();
  const dialog = await openReopen();
  expect(dialog).toHaveAccessibleName("2026-10 마감을 해제할까요?");
  expect(await within(dialog).findByText("조정 조회 실패")).toBeInTheDocument();
  expect(
    within(dialog).getByRole("button", { name: "마감 해제" }),
  ).toBeDisabled();
  expect(within(dialog).queryByText(/전월 조정이/)).not.toBeInTheDocument();
  impactFailure = false;
  fireEvent.click(
    within(dialog).getByRole("button", { name: "조정 건수 다시 조회" }),
  );
  await waitFor(() =>
    expect(dialog).toHaveTextContent(
      "11월에 넣은 전월 조정이 3건 있습니다. 이 달을 직접 고치면 같은 정정이 두 번 들어가지 않게 그 줄은 지워 주세요.",
    ),
  );
  expect(dialog).toHaveTextContent(
    "해제하면 이 달 자료를 다시 고칠 수 있습니다. 다시 마감할 때 이 달 숫자를 새로 고정하고, 법인세 월할은 이 달부터 마감 안 된 달끼리 다시 나눕니다. 사유는 기록에 남습니다.",
  );
  const reason = within(dialog).getByLabelText("해제 사유 *");
  expect(reason).toHaveAttribute("maxLength", "500");
  expect(reason).toHaveAttribute("placeholder", "예: 9월 청구 누락 2건 반영");
  expect(within(dialog).getByRole("button", { name: "마감 해제" })).toHaveClass(
    "app-danger",
  );
  expect(writes()).toHaveLength(0);
});
it("zero previous adjustments leaves out the adjustment sentence", async () => {
  closeRow();
  impactCount = 0;
  mount();
  const dialog = await openReopen();
  await readySubmit(dialog);
  expect(dialog).not.toHaveTextContent("전월 조정이");
  expect(dialog).toHaveTextContent("사유는 기록에 남습니다.");
});
it("blank reason is focused and never sent", async () => {
  closeRow();
  mount();
  const dialog = await openReopen();
  const submit = await readySubmit(dialog);
  // 오류가 없을 때는 없는 오류 칸을 가리키지 않는다.
  expect(within(dialog).getByLabelText("해제 사유 *")).not.toHaveAttribute(
    "aria-describedby",
  );
  fireEvent.change(within(dialog).getByLabelText("해제 사유 *"), {
    target: { value: "   " },
  });
  fireEvent.click(submit);
  expect(
    await within(dialog).findByText("해제 사유를 입력해 주세요."),
  ).toBeInTheDocument();
  expect(within(dialog).getByLabelText("해제 사유 *")).toHaveFocus();
  expect(within(dialog).getByLabelText("해제 사유 *")).toHaveAccessibleDescription(
    "해제 사유를 입력해 주세요.",
  );
  expect(writes()).toHaveLength(0);
});
it("server field failure retains reopen reason and focuses the field", async () => {
  closeRow();
  fieldFailure = true;
  mount();
  const dialog = await openReopen();
  const input = within(dialog).getByLabelText("해제 사유 *");
  const submit = await readySubmit(dialog);
  fireEvent.change(input, { target: { value: "정정 필요" } });
  fireEvent.click(submit);
  expect(await within(dialog).findByText("사유 입력 오류")).toBeInTheDocument();
  expect(input).toHaveValue("정정 필요");
  expect(input).toHaveFocus();
});
it("successful reopen uses the returned state, refreshes history and preserves reason payload", async () => {
  closeRow();
  mount();
  const dialog = await openReopen();
  const submit = await readySubmit(dialog);
  const detailCount = detailReads().length;
  fireEvent.change(within(dialog).getByLabelText("해제 사유 *"), {
    target: { value: " 다음달 조정 검토 " },
  });
  fireEvent.click(submit);
  expect(await screen.findByTestId("banner-warn")).toBeInTheDocument();
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    modifiedAt: "2026-11-01T00:00:00Z",
    reason: " 다음달 조정 검토 ",
  });
  expect(await screen.findByTestId("month-close-toast")).toHaveTextContent(
    "2026-10 마감을 해제했습니다. 영업 파트 정산 확정은 그대로입니다.",
  );
  expect(await screen.findByTestId("month-close-history-0")).toHaveTextContent(
    "해제",
  );
  expect(detailReads()).toHaveLength(detailCount);
  expect(badgeRefresh).toHaveBeenCalledOnce();
});
it.each([
  [2, "UI-13"],
  [4, "UI-19"],
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
        String(url).endsWith(`/PERIOD/${target}`) && init?.method === "PUT",
    );
    expect(preference).toBeDefined();
    expect(JSON.parse(JSON.parse(String(preference![1].body)).prefVal)).toEqual(
      target === "UI-19" ? { year: 2026, month: 10 } : { year: 2026 },
    );
  },
);
it("double clicking 확인하기 navigates only once", async () => {
  mount();
  await ready();
  const button = screen.getByTestId("month-close-visit-2");
  fireEvent.click(button);
  fireEvent.click(button);
  await waitFor(() =>
    expect(screen.getByTestId("test-location")).toHaveTextContent(
      "/screens/UI-13",
    ),
  );
  expect(visited.filter((path) => path === "/screens/UI-13")).toHaveLength(1);
});
it("normal checks and unresolved destinations show no 확인하기", async () => {
  mount();
  await ready();
  for (const index of [0, 1, 3, 5])
    expect(
      screen.queryByTestId(`month-close-visit-${index}`),
    ).not.toBeInTheDocument();
  expect(screen.getAllByRole("button", { name: "확인하기" })).toHaveLength(2);
});
it("draws the actions in the screen title bar when the shell provides it", async () => {
  render(
    <MemoryRouter initialEntries={["/screens/UI-14"]}>
      <MonthCloseWorkspaceProvider>
        <MonthCloseTitleActions />
        <MonthClosePage />
      </MonthCloseWorkspaceProvider>
    </MemoryRouter>,
  );
  await ready();
  const bar = screen.getByTestId("month-close-title-actions");
  expect(within(bar).getByRole("button", { name: "다시 점검" })).toHaveClass(
    "ls-btn",
  );
  expect(within(bar).getByRole("button", { name: "마감하기" })).toHaveClass(
    "ls-btn",
    "ls-btn-p",
  );
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

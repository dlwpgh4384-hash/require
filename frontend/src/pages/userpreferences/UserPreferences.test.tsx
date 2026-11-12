import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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
import { useState, type ReactNode } from "react";
import { ScreenSettingsDialog } from "./ScreenSettingsDialog";
import { UserPreferencesProvider } from "./UserPreferencesProvider";
import { useScreenPresentation } from "./UserPreferencesProvider";
import {
  FavoriteScreenButton,
  PersonalScreenLists,
  useMenuFoldPreference,
  useRecentScreenVisit,
} from "./PersonalScreenPreferences";
import {
  MenuSearchDialog,
  UserPreferenceTools,
  usePreferenceBusinessShortcuts,
} from "./UserPreferenceTools";
import {
  UserPreferenceConfirmation,
  UserPreferenceToast,
} from "./UserPreferenceDialog";
import {
  preferenceConflict,
  recentScreenIds,
  type PreferenceType,
  type UserPreferenceResult,
} from "./userPreferences";

const token = "2026-01-02T03:04:05.123456Z";
let rows: Map<string, UserPreferenceResult>;
let failureType: string | undefined;
let failureStatus: number;
let failReads: boolean;
let request: ReturnType<typeof vi.fn>;
const puts = () =>
  request.mock.calls.filter((call) => call[1]?.method === "PUT");
function row(
  type: PreferenceType,
  value: string | null,
  key = "GLOBAL",
): UserPreferenceResult {
  return {
    prefTypeCd: type,
    prefKey: key,
    found: value !== null,
    prefVal: value,
    modDttm: value === null ? null : token,
  };
}
beforeEach(() => {
  rows = new Map(
    [
      row("PAGE_SIZE", "20"),
      row("DENSE", "넓게"),
      row("FAV", "[]"),
      row("RECENT", "[]"),
      row("MENU_FOLD", null),
    ].map((item) => [item.prefTypeCd, item]),
  );
  failureType = undefined;
  failureStatus = 500;
  failReads = false;
  request = vi.fn(async (url: string, init?: RequestInit) => {
    const type = decodeURIComponent(url.split("/").at(-2)!) as PreferenceType;
    if (init?.method === "PUT") {
      if (failureType === type)
        return {
          ok: false,
          status: failureStatus,
          json: async () => ({
            success: false,
            message: "private SQL",
            errors: [{ field: "prefVal", message: "private SQL" }],
          }),
        };
      const input = JSON.parse(String(init.body));
      rows.set(type, {
        ...rows.get(type)!,
        found: true,
        prefVal: input.prefVal,
        modDttm: "2026-01-02T03:04:05.123457Z",
      });
    } else if (failReads) throw new Error("private connection error");
    return {
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        meta: {},
        data: { ...rows.get(type)! },
      }),
    };
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
function wrap(children: ReactNode) {
  return render(<UserPreferencesProvider>{children}</UserPreferencesProvider>);
}
function Presentation() {
  const state = useScreenPresentation();
  return (
    <output data-testid="presentation">
      {state.pageSize ?? "미설정"}/
      {state.dense === undefined ? "미설정" : state.dense ? "좁게" : "넓게"}
    </output>
  );
}
async function settings() {
  const close = vi.fn();
  wrap(
    <>
      <ScreenSettingsDialog onClose={close} />
      <Presentation />
    </>,
  );
  await screen.findByTestId("settings-page-size");
  return close;
}

describe("screen settings contract", () => {
  it("loads real settings without issuing a write", async () => {
    await settings();
    expect(screen.getByTestId("settings-page-size")).toHaveValue("20");
    expect(puts()).toHaveLength(0);
  });
  it("keeps missing settings unset until explicit valid input instead of inventing defaults", async () => {
    rows.set("PAGE_SIZE", row("PAGE_SIZE", null));
    rows.set("DENSE", row("DENSE", null));
    await settings();
    expect(screen.getByText(/아직 저장하지 않은 설정/)).toBeVisible();
    expect(screen.getByTestId("settings-page-size")).toHaveValue("");
    expect(screen.getByTestId("settings-density")).toHaveValue("");
    expect(screen.getByTestId("presentation")).toHaveTextContent(
      "미설정/미설정",
    );
    expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
      "data-page-size",
    );
    expect(screen.getByTestId("user-preferences")).not.toHaveAttribute(
      "data-density",
    );
    fireEvent.click(screen.getByTestId("settings-save"));
    expect(screen.getByTestId("settings-page-size")).toHaveFocus();
    expect(screen.getByText("기본 목록 건수를 입력해 주세요.")).toBeVisible();
    expect(screen.getByText("보기 밀도를 선택해 주세요.")).toBeVisible();
    expect(puts()).toHaveLength(0);
  });
  it("shows loading instead of an editable default form", () => {
    request.mockImplementation(() => new Promise(() => {}));
    wrap(<ScreenSettingsDialog onClose={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "설정을 불러오는 중입니다.",
    );
    expect(screen.queryByTestId("settings-page-size")).not.toBeInTheDocument();
  });
  it("does not disguise failed reads as default settings", async () => {
    failReads = true;
    wrap(<ScreenSettingsDialog onClose={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "설정을 불러오지 못했습니다.",
    );
    expect(screen.queryByTestId("settings-page-size")).not.toBeInTheDocument();
    failReads = false;
    fireEvent.click(screen.getByTestId("settings-retry"));
    expect(await screen.findByTestId("settings-page-size")).toHaveValue("20");
  });
  it("validates every field before PUT and focuses the first error", async () => {
    await settings();
    fireEvent.change(screen.getByTestId("settings-page-size"), {
      target: { value: "30" },
    });
    fireEvent.click(screen.getByTestId("settings-save"));
    expect(
      screen.getByText("목록 건수는 20, 50, 100 중에서 입력해 주세요."),
    ).toBeVisible();
    expect(screen.getByTestId("settings-page-size")).toHaveFocus();
    expect(screen.getByTestId("settings-page-size")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(puts()).toHaveLength(0);
  });
  it("removes non-digits immediately", async () => {
    await settings();
    fireEvent.change(screen.getByTestId("settings-page-size"), {
      target: { value: "5x0한" },
    });
    expect(screen.getByTestId("settings-page-size")).toHaveValue("50");
  });
  it("does not replace saved settings with unconfirmed defaults", async () => {
    rows.set("PAGE_SIZE", row("PAGE_SIZE", "100"));
    rows.set("DENSE", row("DENSE", "좁게"));
    await settings();
    expect(screen.getByTestId("settings-defaults")).toBeDisabled();
    fireEvent.click(screen.getByTestId("settings-defaults"));
    expect(screen.getByTestId("settings-page-size")).toHaveValue("100");
    expect(screen.getByTestId("settings-density")).toHaveValue("좁게");
    expect(screen.getByText(/기본값은 아직 확인되지 않았습니다/)).toBeVisible();
    expect(puts()).toHaveLength(0);
  });
  it("saves both fields, keeps wire microseconds unchanged, and applies the readback immediately", async () => {
    await settings();
    fireEvent.change(screen.getByTestId("settings-page-size"), {
      target: { value: "50" },
    });
    fireEvent.change(screen.getByTestId("settings-density"), {
      target: { value: "좁게" },
    });
    fireEvent.click(screen.getByTestId("settings-save"));
    expect(await screen.findByTestId("preference-success")).toHaveTextContent(
      "저장했습니다.",
    );
    expect(screen.getByTestId("presentation")).toHaveTextContent("50/좁게");
    expect(puts()).toHaveLength(2);
    expect(JSON.parse(puts()[0][1].body)).toEqual({
      prefVal: "50",
      modDttm: token,
    });
    expect(
      request.mock.calls.filter((call) => call[1]?.method !== "PUT").length,
    ).toBeGreaterThan(5);
  });
  it("applies a successful field and keeps the failed field input without a full-success toast", async () => {
    failureType = "DENSE";
    await settings();
    fireEvent.change(screen.getByTestId("settings-page-size"), {
      target: { value: "50" },
    });
    fireEvent.change(screen.getByTestId("settings-density"), {
      target: { value: "좁게" },
    });
    fireEvent.click(screen.getByTestId("settings-save"));
    await waitFor(() =>
      expect(screen.getByTestId("settings-save")).toBeEnabled(),
    );
    expect(screen.getByTestId("presentation")).toHaveTextContent("50/넓게");
    expect(screen.getByTestId("settings-density")).toHaveValue("좁게");
    expect(screen.queryByTestId("preference-success")).not.toBeInTheDocument();
    expect(screen.queryByText("private SQL")).not.toBeInTheDocument();
  });
  it("keeps stale input on conflict without retry or overwrite", async () => {
    failureType = "PAGE_SIZE";
    failureStatus = 409;
    await settings();
    fireEvent.change(screen.getByTestId("settings-page-size"), {
      target: { value: "50" },
    });
    fireEvent.click(screen.getByTestId("settings-save"));
    await waitFor(() =>
      expect(screen.getByTestId("settings-save")).toBeEnabled(),
    );
    expect(screen.getByRole("alert")).toHaveTextContent(preferenceConflict);
    expect(screen.getByTestId("settings-page-size")).toHaveValue("50");
    expect(rows.get("PAGE_SIZE")?.prefVal).toBe("20");
    expect(puts()).toHaveLength(2);
  });
  it("maps a server prefVal 400 error to the edited field", async () => {
    failureType = "PAGE_SIZE";
    failureStatus = 400;
    await settings();
    fireEvent.click(screen.getByTestId("settings-save"));
    await waitFor(() =>
      expect(screen.getByTestId("settings-page-size")).toHaveAttribute(
        "aria-invalid",
        "true",
      ),
    );
    expect(screen.getByTestId("settings-page-size")).toHaveFocus();
    expect(screen.queryByText("private SQL")).not.toBeInTheDocument();
  });
  it("dirty cancel opens one confirmation, cancel keeps input, and leave closes once", async () => {
    const close = await settings();
    fireEvent.change(screen.getByTestId("settings-page-size"), {
      target: { value: "50" },
    });
    fireEvent.click(screen.getByTestId("settings-cancel"));
    expect(screen.getAllByRole("alertdialog")).toHaveLength(1);
    fireEvent.click(screen.getByTestId("confirmation-cancel"));
    expect(screen.getByTestId("settings-page-size")).toHaveValue("50");
    expect(close).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId("settings-cancel"));
    fireEvent.click(screen.getByTestId("confirmation-proceed"));
    expect(close).toHaveBeenCalledTimes(1);
    expect(puts()).toHaveLength(0);
  });
});

const menus = [
  { scrnId: "UI-12", menuNm: "투입 배정", pcOnlyYn: "N" as const },
  { scrnId: "UI-14", menuNm: "월 마감", pcOnlyYn: "Y" as const },
  { scrnId: "UI-01", menuNm: "프로젝트", pcOnlyYn: "N" as const },
];
const available = (id: string) => id !== "UI-01";
describe("personal lists and tools", () => {
  it("empty favorites and recent lists use their exact guidance", async () => {
    wrap(
      <PersonalScreenLists
        menus={menus}
        available={available}
        mobile={false}
        collapsed={false}
        onNavigate={vi.fn()}
      />,
    );
    expect(
      await screen.findByText("화면 제목 옆 ☆를 누르면 추가됩니다"),
    ).toBeVisible();
    expect(screen.getByText("아직 없습니다")).toBeVisible();
  });
  it("filters unavailable, removed and mobile PC-only entries without changing stored preferences", async () => {
    rows.set("FAV", row("FAV", '["UI-12","UI-14","UI-01","REMOVED"]'));
    wrap(
      <PersonalScreenLists
        menus={menus}
        available={available}
        mobile
        collapsed={false}
        onNavigate={vi.fn()}
      />,
    );
    expect(
      await screen.findByRole("button", { name: /투입 배정/ }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /월 마감|프로젝트/ }),
    ).not.toBeInTheDocument();
    expect(puts()).toHaveLength(0);
  });
  it("collapsed lists are hidden", () => {
    wrap(
      <PersonalScreenLists
        menus={menus}
        available={available}
        mobile={false}
        collapsed
        onNavigate={vi.fn()}
      />,
    );
    expect(
      screen.queryByTestId("personal-screen-lists"),
    ).not.toBeInTheDocument();
  });
  it("favorites add and remove through PUT and readback", async () => {
    wrap(<FavoriteScreenButton screenId="UI-12" />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "즐겨찾기 추가" }),
      ).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 추가" }));
    expect(await screen.findByTestId("preference-success")).toHaveTextContent(
      "즐겨찾기에 추가했습니다.",
    );
    expect(rows.get("FAV")?.prefVal).toBe('["UI-12"]');
    fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 해제" }));
    await waitFor(() => expect(rows.get("FAV")?.prefVal).toBe("[]"));
  });
  it("eleventh favorite is rejected without writes", async () => {
    rows.set(
      "FAV",
      row(
        "FAV",
        JSON.stringify(Array.from({ length: 10 }, (_, index) => `UI-${index}`)),
      ),
    );
    wrap(<FavoriteScreenButton screenId="UI-12" />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "즐겨찾기 추가" }),
      ).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 추가" }));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "즐겨찾기는 최대 10개까지 추가할 수 있습니다.",
    );
    expect(puts()).toHaveLength(0);
  });
  it("recent helper moves repeated visits first and retains exactly five", () => {
    let ids: string[] = [];
    for (const id of ["A", "B", "C", "D", "E", "F"])
      ids = recentScreenIds(ids, id);
    expect(ids).toEqual(["F", "E", "D", "C", "B"]);
    expect(recentScreenIds(ids, "C")).toEqual(["C", "F", "E", "D", "B"]);
  });
  it("restoring RECENT makes no write; six explicit successful visits persist the newest five", async () => {
    function Visits() {
      const visit = useRecentScreenVisit();
      return (
        <>
          {["A", "B", "C", "D", "E", "F"].map((id) => (
            <button
              key={id}
              onClick={() => {
                void visit(id);
              }}
            >
              {id}
            </button>
          ))}
        </>
      );
    }
    const first = wrap(<Visits />);
    await waitFor(() => expect(request).toHaveBeenCalledTimes(5));
    expect(puts()).toHaveLength(0);
    for (const id of ["A", "B", "C", "D", "E", "F"]) {
      fireEvent.click(screen.getByRole("button", { name: id }));
      await waitFor(() =>
        expect(JSON.parse(rows.get("RECENT")!.prefVal!)[0]).toBe(id),
      );
      await act(async () => {});
    }
    expect(JSON.parse(rows.get("RECENT")!.prefVal!)).toEqual([
      "F",
      "E",
      "D",
      "C",
      "B",
    ]);
    first.unmount();
    const before = puts().length;
    wrap(<Visits />);
    await act(async () => {});
    expect(puts()).toHaveLength(before);
  });
  it("stored menu fold wins over viewport default and mobile never saves it", async () => {
    rows.set("MENU_FOLD", row("MENU_FOLD", "false"));
    function Fold({ mobile }: { mobile: boolean }) {
      const fold = useMenuFoldPreference(mobile, true);
      return (
        <button
          disabled={fold.loading}
          onClick={() => {
            void fold.setFolded(true);
          }}
        >
          {fold.folded ? "접힘" : "펼침"}
        </button>
      );
    }
    const view = wrap(<Fold mobile />);
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "펼침" })).toBeEnabled(),
    );
    fireEvent.click(screen.getByRole("button", { name: "펼침" }));
    expect(puts()).toHaveLength(0);
    view.rerender(
      <UserPreferencesProvider>
        <Fold mobile={false} />
      </UserPreferencesProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: "펼침" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "접힘" })).toBeVisible(),
    );
    expect(rows.get("MENU_FOLD")?.prefVal).toBe("true");
  });
  it("menu search filters by screen number and respects mobile availability", () => {
    render(
      <MenuSearchDialog
        menus={menus}
        loading={false}
        mobile
        available={available}
        onSelect={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByTestId("menu-search-query"), {
      target: { value: "UI-12" },
    });
    expect(screen.getByRole("button", { name: /투입 배정/ })).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /월 마감/ }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByTestId("menu-search-query"), {
      target: { value: "없는 메뉴" },
    });
    expect(screen.getByText("일치하는 메뉴가 없습니다.")).toBeVisible();
  });
  it("desktop Ctrl+K opens search(헤더 버튼 없음, 시안) and Escape closes only that dialog", () => {
    wrap(
      <UserPreferenceTools
        menus={menus}
        mobile={false}
        available={available}
        onNavigate={vi.fn()}
      />,
    );
    expect(screen.queryByRole("button", { name: "메뉴 찾기" })).not.toBeInTheDocument();
    const caller = screen.getByTestId("user-menu-open");
    caller.focus();
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });
    expect(screen.getByRole("dialog", { name: "메뉴 찾기" })).toBeVisible();
    fireEvent.keyDown(window, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(caller).toHaveFocus();
  });
  it("mobile Ctrl+K is inactive but search icon is usable and later tools only say 준비 중", () => {
    wrap(
      <UserPreferenceTools
        menus={menus}
        mobile
        available={available}
        onNavigate={vi.fn()}
      />,
    );
    fireEvent.keyDown(window, { key: "k", ctrlKey: true });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId("menu-search-open"));
    expect(screen.getByRole("dialog")).toBeVisible();
    fireEvent.keyDown(window, { key: "Escape" });
    fireEvent.click(screen.getByTestId("user-menu-open"));
    for (const id of [
      "ai-unavailable",
      "notification-unavailable",
      "profile-unavailable",
      "password-unavailable",
      "logout-unavailable",
    ]) {
      if (!screen.queryByTestId(id)) fireEvent.click(screen.getByTestId("user-menu-open"));
      fireEvent.click(screen.getByTestId(id));
      expect(screen.getByTestId("header-notice")).toHaveTextContent("준비 중");
    }
    expect(puts()).toHaveLength(0);
  });
  it("business shortcuts only call registered handlers", () => {
    const save = vi.fn();
    function Harness() {
      usePreferenceBusinessShortcuts({ onSave: save });
      return <p>연결점</p>;
    }
    const view = render(<Harness />);
    fireEvent.keyDown(window, { key: "s", ctrlKey: true });
    expect(save).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(window, { key: "Enter" });
    expect(puts()).toHaveLength(0);
    view.unmount();
    fireEvent.keyDown(window, { key: "s", ctrlKey: true });
    expect(save).toHaveBeenCalledTimes(1);
  });
  it("danger confirmation cancel does not execute, and repeated execution is once only", () => {
    const proceed = vi.fn();
    const cancel = vi.fn();
    render(
      <UserPreferenceConfirmation
        title="삭제할까요?"
        description="내용이 삭제됩니다."
        destructive
        onCancel={cancel}
        onProceed={proceed}
      />,
    );
    fireEvent.click(screen.getByTestId("confirmation-cancel"));
    expect(proceed).not.toHaveBeenCalled();
    expect(cancel).toHaveBeenCalledTimes(1);
    const execute = screen.getByTestId("confirmation-proceed");
    expect(execute).toHaveClass("app-danger");
    fireEvent.click(execute);
    fireEvent.click(execute);
    expect(proceed).toHaveBeenCalledTimes(1);
  });
  it("success toast expires at three seconds", () => {
    vi.useFakeTimers();
    function Harness() {
      const [open, setOpen] = useState(true);
      return open ? (
        <UserPreferenceToast
          message="저장했습니다."
          onDismiss={() => setOpen(false)}
        />
      ) : null;
    }
    render(<Harness />);
    act(() => {
      vi.advanceTimersByTime(2999);
    });
    expect(screen.getByTestId("preference-success")).toBeVisible();
    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(screen.queryByTestId("preference-success")).not.toBeInTheDocument();
    vi.useRealTimers();
  });
});

describe("시안 맞춤: 자동 로그아웃 시간·[연장]", () => {
  it("사용자 메뉴 맨 아래에 남은 시간과 [연장]이 있고, 연장하면 30분으로 돌아간다", () => {
    vi.useRealTimers();
    wrap(
      <UserPreferenceTools
        menus={menus}
        mobile={false}
        available={available}
        onNavigate={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByTestId("user-menu-open"));
    expect(screen.getByTestId("session-left")).toHaveTextContent(/자동 로그아웃까지 (29:5\d|30:00)/);
    fireEvent.click(screen.getByTestId("session-extend"));
    expect(screen.getByTestId("session-left")).toHaveTextContent("30:00");
    expect(screen.getByTestId("header-notice")).toHaveTextContent(
      "자동 로그아웃 시간을 30분으로 다시 늘렸습니다.",
    );
  });
});

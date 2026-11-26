import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { NotificationPopup, notificationTime } from "./NotificationsPage";
import { NotificationBell } from "./NotificationBell";
import { wrapNetworkErrors } from "../../shared/network";
import {
  createNotificationApi,
  notificationApi,
  type NotificationApi,
  type NotificationListing,
  type NotificationRead,
} from "./notificationApi";

let api: NotificationApi;
let listing: NotificationListing;
const onClose = vi.fn();
const onNavigate = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  listing = {
    unreadCount: 2,
    items: [
      {
        notificationId: 12,
        content: "새 알림",
        readYn: "N",
        notifiedAt: "2026-11-26T01:00:00Z",
        screenId: "UI-13",
        targetKey: "bill-private",
      },
      {
        notificationId: 11,
        content: "볼 수 없는 알림입니다",
        readYn: "N",
        notifiedAt: "2026-11-25T01:00:00Z",
      },
    ],
  };
  api = {
    list: vi.fn(async () => listing),
    read: vi.fn(),
    readAll: vi.fn(async () => undefined),
  };
});
afterEach(() => {
  cleanup();
  document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  vi.unstubAllGlobals();
});
function show(isMobile = false) {
  return render(
    <NotificationPopup
      api={api}
      onClose={onClose}
      onNavigate={onNavigate}
      isMobile={isMobile}
    />,
  );
}
function result(
  status: "ALLOWED" | "DENIED" | "OUT_OF_SCOPE" | "MISSING",
  pcOnly = false,
): NotificationRead {
  return {
    success: true,
    data: { notificationId: 12, readYn: "Y", readAt: "2026-11-26T02:00:00Z" },
    meta: {
      navigation: {
        status,
        screenId: status === "DENIED" ? null : "UI-13",
        pcOnly,
      },
    },
  };
}
async function ready() {
  await screen.findByText("새 알림");
}
async function select() {
  await ready();
  fireEvent.click(screen.getByTestId("notification-read-12"));
}

describe("UI-42 own notifications", () => {
  it("shows loading until the real API resolves", async () => {
    let resolve!: (value: NotificationListing) => void;
    vi.mocked(api.list).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    show();
    expect(screen.getByTestId("notifications-loading")).toBeInTheDocument();
    expect(screen.getByTestId("M25-523")).toBeDisabled();
    await act(async () => resolve(listing));
    await ready();
  });
  it("renders server order, masked content, retention and unread styling", async () => {
    show();
    await ready();
    expect(
      screen
        .getAllByTestId(/^notification-row-/)
        .map((node) => node.dataset.testid),
    ).toEqual(["notification-row-12", "notification-row-11"]);
    expect(screen.getByTestId("notification-read-12")).toHaveClass(
      "notification-unread",
    );
    expect(screen.getByText("볼 수 없는 알림입니다")).toBeInTheDocument();
    expect(
      screen.getByText("알림은 30일 동안 보관합니다."),
    ).toBeInTheDocument();
    expect(screen.getByTestId("notifications-page")).toHaveAttribute(
      "data-screen-id",
      "UI-42",
    );
  });
  it("renders 99+ for one hundred unread in both popup and bell", async () => {
    listing.unreadCount = 100;
    render(<NotificationBell api={api} onNavigate={onNavigate} />);
    expect(await screen.findByTestId("notification-badge")).toHaveTextContent(
      "99+",
    );
    fireEvent.click(screen.getByTestId("M25-521"));
    await waitFor(() =>
      expect(screen.getByTestId("notification-unread-count")).toHaveTextContent(
        "99+",
      ),
    );
  });
  it("shows empty state and disables read all", async () => {
    listing = { unreadCount: 0, items: [] };
    show();
    expect(await screen.findByTestId("notifications-empty")).toHaveTextContent(
      "알림이 없습니다.",
    );
    expect(screen.getByTestId("M25-523")).toBeDisabled();
  });
  it("shows original failure and allows retry", async () => {
    vi.mocked(api.list).mockRejectedValueOnce(
      new Error("다시 로그인해 주세요."),
    );
    show();
    expect(await screen.findByTestId("notifications-error")).toHaveTextContent(
      "다시 로그인해 주세요.",
    );
    fireEvent.click(screen.getByTestId("notifications-retry"));
    await ready();
  });
  it("read all refreshes unread flags and count without navigation or close", async () => {
    vi.mocked(api.readAll).mockImplementation(async () => {
      listing = {
        unreadCount: 0,
        items: listing.items.map((item) => ({ ...item, readYn: "Y" })),
      };
    });
    show();
    await ready();
    const location = window.location.href;
    fireEvent.click(screen.getByTestId("M25-523"));
    await waitFor(() =>
      expect(screen.getByTestId("notification-unread-count")).toHaveTextContent(
        "0",
      ),
    );
    expect(screen.getByTestId("notification-read-12")).not.toHaveClass(
      "notification-unread",
    );
    expect(window.location.href).toBe(location);
    expect(onNavigate).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
  it("failed read all preserves rows and stays on screen", async () => {
    vi.mocked(api.readAll).mockRejectedValue(
      new Error("요청 토큰을 확인해 주세요."),
    );
    show();
    await ready();
    fireEvent.click(screen.getByTestId("M25-523"));
    expect(await screen.findByTestId("notifications-error")).toHaveTextContent(
      "요청 토큰을 확인해 주세요.",
    );
    expect(screen.getByTestId("notification-unread-count")).toHaveTextContent(
      "2",
    );
    expect(onNavigate).not.toHaveBeenCalled();
  });
  it("does not announce success when read all commits but refresh fails", async () => {
    show();
    await ready();
    vi.mocked(api.list).mockRejectedValueOnce(
      new Error("알림을 불러오지 못했습니다."),
    );
    fireEvent.click(screen.getByTestId("M25-523"));
    expect(await screen.findByTestId("notifications-error")).toHaveTextContent(
      "알림을 불러오지 못했습니다.",
    );
    expect(
      screen.queryByTestId("notifications-message"),
    ).not.toBeInTheDocument();
    expect(onNavigate).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });
  it("does not navigate or retain original text when the post-read refresh fails", async () => {
    vi.mocked(api.read).mockResolvedValue(result("ALLOWED"));
    show();
    await ready();
    vi.mocked(api.list).mockRejectedValueOnce(
      new Error("다시 로그인해 주세요."),
    );
    fireEvent.click(screen.getByTestId("notification-read-12"));
    expect(await screen.findByTestId("notifications-error")).toHaveTextContent(
      "다시 로그인해 주세요.",
    );
    expect(screen.queryByText("새 알림")).not.toBeInTheDocument();
    expect(onNavigate).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId("notifications-retry"));
    await ready();
    expect(api.read).toHaveBeenCalledTimes(1);
  });
  it("reads selected ID first and navigates only to screen without opening detail", async () => {
    let resolve!: (value: NotificationRead) => void;
    vi.mocked(api.read).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    show();
    await select();
    expect(api.read).toHaveBeenCalledWith(12);
    expect(onNavigate).not.toHaveBeenCalled();
    listing = { ...listing, unreadCount: 1 };
    await act(async () => resolve(result("ALLOWED")));
    await waitFor(() =>
      expect(onNavigate).toHaveBeenCalledWith("/screens/UI-13"),
    );
    expect(screen.getByTestId("notification-unread-count")).toHaveTextContent(
      "1",
    );
  });
  it("revoked permission marks read then shows denied without navigation", async () => {
    vi.mocked(api.read).mockResolvedValue(result("DENIED"));
    show();
    await select();
    expect(
      await screen.findByTestId("notifications-message"),
    ).toHaveTextContent("이 화면을 볼 권한이 없습니다.");
    expect(api.list).toHaveBeenCalledTimes(2);
    expect(onNavigate).not.toHaveBeenCalled();
  });
  it("out of scope shows message and opens screen only", async () => {
    vi.mocked(api.read).mockResolvedValue(result("OUT_OF_SCOPE"));
    show();
    await select();
    expect(
      await screen.findByTestId("notifications-message"),
    ).toHaveTextContent("이 건은 지금 볼 수 있는 범위 밖입니다.");
    expect(onNavigate).toHaveBeenCalledWith("/screens/UI-13");
  });
  it("missing target displays missing message and never navigates", async () => {
    vi.mocked(api.read).mockResolvedValue(result("MISSING"));
    show();
    await select();
    expect(
      await screen.findByTestId("notifications-message"),
    ).toHaveTextContent("이 알림의 대상이 더 이상 없습니다.");
    expect(onNavigate).not.toHaveBeenCalled();
  });
  it("mobile refuses PC only destination after marking read", async () => {
    vi.mocked(api.read).mockResolvedValue(result("ALLOWED", true));
    show(true);
    await select();
    expect(
      await screen.findByTestId("notifications-message"),
    ).toHaveTextContent("PC에서 열 수 있는 화면입니다.");
    expect(onNavigate).not.toHaveBeenCalled();
  });
  it("desktop permits PC only destination", async () => {
    vi.mocked(api.read).mockResolvedValue(result("ALLOWED", true));
    show(false);
    await select();
    await waitFor(() =>
      expect(onNavigate).toHaveBeenCalledWith("/screens/UI-13"),
    );
  });
  it("failed read does not navigate", async () => {
    vi.mocked(api.read).mockRejectedValue(new Error("권한이 없습니다."));
    show();
    await select();
    expect(await screen.findByTestId("notifications-error")).toHaveTextContent(
      "권한이 없습니다.",
    );
    expect(onNavigate).not.toHaveBeenCalled();
  });
  it("close returns to the caller", async () => {
    show();
    await ready();
    fireEvent.click(screen.getByTestId("M25-524"));
    expect(onClose).toHaveBeenCalledOnce();
  });
  it("formats today yesterday and older times in Korea", () => {
    const now = new Date("2026-11-26T01:00:00Z");
    expect(notificationTime("2026-11-26T00:05:00Z", now)).toBe("오늘 09:05");
    expect(notificationTime("2026-11-25T00:05:00Z", now)).toBe("어제 09:05");
    expect(notificationTime("2026-11-23T00:05:00Z", now)).toBe("11-23 09:05");
  });
});

describe("notification HTTP contract", () => {
  it("delegates CSRF bootstrap once to the existing shared transport", async () => {
    const request = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      if (input === "/api/health") {
        document.cookie = "XSRF-TOKEN=issued-notification-token; path=/";
        return new Response("{}", { status: 200 });
      }
      return new Response(null, { status: 204 });
    });
    const target = { fetch: request };
    wrapNetworkErrors(target);
    vi.stubGlobal("fetch", target.fetch);
    await notificationApi.readAll();
    expect(request).toHaveBeenCalledTimes(2);
    expect(request.mock.calls[0][0]).toBe("/api/health");
    const [path, init] = request.mock.calls[1];
    expect(path).toBe("/api/common/notifications/read-all");
    expect(init?.credentials).toBe("same-origin");
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe(
      "issued-notification-token",
    );
  });
  it("uses the installed transport without its own token or health request", async () => {
    const request = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", request);
    await notificationApi.readAll();
    expect(request).toHaveBeenCalledTimes(1);
    expect(request).toHaveBeenCalledWith("/api/common/notifications/read-all", {
      method: "PUT",
      credentials: "same-origin",
    });
  });
  it("uses selected id relative paths and same-origin credentials", async () => {
    const request = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        new Response(JSON.stringify(result("ALLOWED")), { status: 200 }),
      );
    await createNotificationApi(request).read(12);
    expect(request).toHaveBeenCalledWith("/api/common/notifications/12/read", {
      method: "PUT",
      credentials: "same-origin",
    });
  });
  it("accepts real 204 read all without parsing JSON", async () => {
    const request = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response(null, { status: 204 }));
    await expect(
      createNotificationApi(request).readAll(),
    ).resolves.toBeUndefined();
  });
  it("preserves approved field error array", async () => {
    const errors = [
      { field: "notificationId", message: "입력 값의 형식을 확인해 주세요." },
    ];
    const request = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          message: "입력 오류",
          errors,
        }),
        { status: 400 },
      ),
    );
    await expect(createNotificationApi(request).read(12)).rejects.toMatchObject(
      { status: 400, errors },
    );
  });
});

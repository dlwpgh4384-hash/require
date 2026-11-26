export interface NotificationItem {
  notificationId: number;
  content: string;
  screenId?: string | null;
  targetKey?: string | null;
  readYn: "Y" | "N";
  notifiedAt: string;
}
export interface NotificationListing {
  items: NotificationItem[];
  unreadCount: number;
}
export interface NotificationNavigation {
  status: "ALLOWED" | "DENIED" | "OUT_OF_SCOPE" | "MISSING";
  screenId: string | null;
  pcOnly: boolean;
}
export interface NotificationRead {
  success: true;
  data: { notificationId: number; readYn: "Y"; readAt: string };
  meta: { navigation?: NotificationNavigation };
}
export class NotificationApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(
      errors.length ? errors.map((error) => error.message).join("\n") : message,
    );
  }
}
export interface NotificationApi {
  list(): Promise<NotificationListing>;
  read(notificationId: number): Promise<NotificationRead>;
  readAll(): Promise<void>;
}

// The shared network wrapper owns CSRF bootstrap, cookies and session expiry handling.
export function createNotificationApi(request: typeof fetch): NotificationApi {
  async function call(path: string, method: "GET" | "PUT") {
    const response = await request(path, {
      method,
      credentials: "same-origin",
    });
    if (response.status === 204 && response.ok) return;
    const body = await response.json();
    if (!response.ok || body.success !== true) {
      throw new NotificationApiError(
        response.status,
        body.message ?? "알림을 처리하지 못했습니다.",
        body.errors ?? [],
      );
    }
    return body;
  }
  return {
    list: async () =>
      (await call("/api/common/notifications", "GET"))
        .data as NotificationListing,
    read: async (notificationId) =>
      (await call(
        `/api/common/notifications/${notificationId}/read`,
        "PUT",
      )) as NotificationRead,
    readAll: async () => {
      await call("/api/common/notifications/read-all", "PUT");
    },
  };
}

export const notificationApi = createNotificationApi((input, init) =>
  fetch(input, init),
);

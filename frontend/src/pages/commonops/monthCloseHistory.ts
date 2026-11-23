import { projectRequest } from "../projects/projects";

export interface MonthCloseHistoryItem {
  yearMonth: string;
  action: "마감" | "해제";
  processor: string;
  occurredAt: string;
  reason: string | null;
  remainingChecks: string[];
}

export interface MonthCloseHistory {
  items: MonthCloseHistoryItem[];
}

export async function getCommonMonthCloseHistory(
  yearMonth?: string,
  signal?: AbortSignal,
): Promise<MonthCloseHistory> {
  const query =
    yearMonth === undefined ? "" : `?${new URLSearchParams({ yearMonth })}`;
  const data = await projectRequest<MonthCloseHistory>(
    `/api/common/month-close-history${query}`,
    { signal },
  );
  if (
    !Array.isArray(data?.items) ||
    data.items.some(
      (item) =>
        !item ||
        typeof item.yearMonth !== "string" ||
        !["마감", "해제"].includes(item.action) ||
        typeof item.processor !== "string" ||
        typeof item.occurredAt !== "string" ||
        !(item.reason === null || typeof item.reason === "string") ||
        !Array.isArray(item.remainingChecks) ||
        item.remainingChecks.some((name) => typeof name !== "string"),
    )
  ) {
    throw new Error("마감 이력 응답을 확인해 주세요.");
  }
  return data;
}

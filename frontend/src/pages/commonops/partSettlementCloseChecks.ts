import { projectRequest } from "../projects/projects";

export interface PartSettlementCloseItem {
  partId: string;
  confirmed: boolean;
  modifiedAt: string | null;
}

export interface PartSettlementCloseCheck {
  yearMonth: string;
  totalCount: number;
  unconfirmedCount: number;
  items: PartSettlementCloseItem[];
}

export function getCommonPartSettlementCloseCheck(
  yearMonth: string,
  signal?: AbortSignal,
): Promise<PartSettlementCloseCheck> {
  return projectRequest<PartSettlementCloseCheck>(
    `/api/common/part-settlement-close-checks?${new URLSearchParams({ yearMonth })}`,
    { signal },
  );
}

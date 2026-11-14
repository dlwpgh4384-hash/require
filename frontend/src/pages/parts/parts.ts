export interface Part {
  partId: string;
  partName: string;
  leaderUserId: string;
  leaderName: string;
  distributionRate: number;
  salesExpense: number;
  effectiveMonth: string;
  endMonth: string | null;
  useYn: "Y" | "N";
  modifiedAt: string | null;
  personCount: number;
  projectCount: number;
  scheduledChange: string | null;
}
export interface PartStandard {
  partId: string;
  leaderUserId: string;
  leaderName: string;
  distributionRate: number;
  salesExpense: number;
  effectiveMonth: string;
  endMonth: string | null;
  modifiedAt: string | null;
  state: "적용 중" | "예정" | "지난";
}
export interface PartOptions {
  leaders: { userId: string; userName: string }[];
  distributionRate: number;
  salesExpense: number;
  closedMonth: string | null;
  earliestMonth: string;
}
export interface PartChange {
  changedAt: string;
  fieldName: string;
  beforeValue: string | null;
  afterValue: string | null;
  changedBy: string;
}
export interface PartValues {
  effectiveMonth: string;
  leaderUserId: string;
  distributionRate: number;
  salesExpense: number;
}
export class PartRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function partRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    const business = response.status === 400 || response.status === 409;
    throw new PartRequestError(
      response.status,
      business && typeof body.message === "string"
        ? body.message
        : "파트 정보를 처리하지 못했습니다. 다시 시도해 주세요.",
      business && Array.isArray(body.errors) ? body.errors : [],
    );
  }
  return body.data as T;
}
const root = "/api/common/parts";
const idPath = (id: string) => `${root}/${encodeURIComponent(id)}`;
const write = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const getCommonParts = (signal?: AbortSignal) =>
  partRequest<{ items: Part[] }>(root, { signal });
export const getCommonPartOptions = (signal?: AbortSignal) =>
  partRequest<PartOptions>(`${root}/options`, { signal });
export const getCommonPartStandards = (id: string, signal?: AbortSignal) =>
  partRequest<{ items: PartStandard[] }>(`${idPath(id)}/standards`, { signal });
export const getCommonPartChangeHistory = (id: string, signal?: AbortSignal) =>
  partRequest<{ items: PartChange[] }>(`${idPath(id)}/change-history`, {
    signal,
  });
export const postCommonPart = (body: PartValues & { partName: string }) =>
  partRequest<Part>(root, write("POST", body));
export const putCommonPart = (
  id: string,
  body: { partName: string; modifiedAt: string | null },
) => partRequest<Part>(idPath(id), write("PUT", body));
export const putCommonPartStandards = (
  id: string,
  body: PartValues & { modifiedAt: string | null },
) =>
  partRequest<{ items: PartStandard[] }>(
    `${idPath(id)}/standards`,
    write("PUT", body),
  );
export const putCommonPartStop = (id: string, modifiedAt: string | null) =>
  partRequest<Part>(`${idPath(id)}/stop`, write("PUT", { modifiedAt }));
export const partErrorMessage = (value: unknown) =>
  value instanceof Error
    ? value.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";
export const partMoney = (value: number) => value.toLocaleString("ko-KR");

/** SFR-011 영업 파트 엑셀 다운로드 주소: 정렬·보이는 열 그대로. */
export function partsExcelUrl(sort: { key: string; dir: string } | null, columns: string[]) {
  const params = new URLSearchParams();
  if (sort) {
    params.set("sort", sort.key);
    params.set("dir", sort.dir);
  }
  columns.forEach((column) => params.append("columns", column));
  return `/api/common/parts/excel?${params}`;
}

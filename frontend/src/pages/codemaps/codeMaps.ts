export interface CodeMapItem {
  excelText: string;
  groupId: string;
  code: string;
}
export interface CodeMapGroup {
  groupId: string;
  groupName: string;
}
export interface CodeMapOption {
  code: string;
  codeName: string;
  useYn: string;
}
export class CodeMapRequestError extends Error {
  constructor(
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    throw new CodeMapRequestError(
      response.status === 400 && typeof body.message === "string"
        ? body.message
        : "표기 대응을 처리하지 못했습니다. 다시 시도해 주세요.",
      response.status === 400 && Array.isArray(body.errors) ? body.errors : [],
    );
  }
  return body.data;
}
export const getCommonCodeMaps = (signal?: AbortSignal) =>
  request<{ items: CodeMapItem[] }>("/api/common/code-maps", { signal });
export const postCommonCodeMap = (item: CodeMapItem) =>
  request<CodeMapItem>("/api/common/code-maps", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(item),
  });
export const postCommonCodeMapDelete = (items: CodeMapItem[]) =>
  request<{ deleted: CodeMapItem[] }>("/api/common/code-maps/delete", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items }),
  });
export const getCodeMapGroups = (signal?: AbortSignal) =>
  request<{ items: CodeMapGroup[] }>("/api/common/code-groups", { signal });
export const getCodeMapOptions = (groupId: string, signal?: AbortSignal) =>
  request<{ items: CodeMapOption[] }>(
    `/api/common/code-groups/${encodeURIComponent(groupId)}/codes`,
    { signal },
  );
export const CODE_MAP_CHANGED_EVENT = "common-code-maps-changed";
export const codeMapKey = (item: CodeMapItem) =>
  JSON.stringify([item.excelText, item.groupId]);

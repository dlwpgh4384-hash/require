export interface CodeHistoryItem {
  changedAt: string;
  kind: string;
  item: string;
  before: string;
  after: string;
  processor: string;
}
export interface CodeHistoryResult {
  items: CodeHistoryItem[];
}

export async function getCommonCodeChangeHistory(
  signal?: AbortSignal,
): Promise<CodeHistoryResult> {
  const failure = () =>
    new Error("변경 이력을 불러오지 못했습니다. 다시 시도해 주세요.");
  const response = await fetch("/api/common/code-change-history", { signal });
  const body = await response.json().catch(() => {
    throw failure();
  });
  if (!response.ok || body.success !== true) {
    throw new Error(
      response.status === 400 && typeof body.message === "string"
        ? body.message
        : "변경 이력을 불러오지 못했습니다. 다시 시도해 주세요.",
    );
  }
  if (!Array.isArray(body.data?.items))
    throw new Error("변경 이력 응답을 확인해 주세요.");
  return body.data;
}

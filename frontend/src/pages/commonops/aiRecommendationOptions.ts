import { useEffect, useState } from "react";

export interface AiRecommendationChoice {
  code: string;
  name: string;
}
export interface AiRecommendationNeed {
  needSn: number;
  jobCode: string;
  jobName: string;
  requiredCount: number;
  startMonth: string;
}
export interface AiRecommendationTarget {
  targetType: "project" | "opportunity";
  targetId: string;
  targetName: string;
  startMonth: string;
  endMonth: string | null;
  needs: AiRecommendationNeed[];
}
export interface AiRecommendationOptions {
  targets: AiRecommendationTarget[];
  jobs: AiRecommendationChoice[];
  grades: AiRecommendationChoice[];
  exclusionReasons: AiRecommendationChoice[];
}
export interface AiRecommendationOptionsFilter {
  targetType?: AiRecommendationTarget["targetType"];
  targetId?: string;
}

export const aiRecommendationOptionsError =
  "추천 조건을 불러오지 못했습니다. 다시 시도해 주세요.";

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
function month(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(value)
  );
}
function choices(value: unknown): value is AiRecommendationChoice[] {
  return (
    Array.isArray(value) &&
    value.every(
      (item: unknown) =>
        object(item) &&
        typeof item.code === "string" &&
        item.code.length > 0 &&
        typeof item.name === "string",
    )
  );
}
function options(value: unknown): value is AiRecommendationOptions {
  return (
    object(value) &&
    choices(value.jobs) &&
    choices(value.grades) &&
    choices(value.exclusionReasons) &&
    Array.isArray(value.targets) &&
    value.targets.every(
      (target: unknown) =>
        object(target) &&
        (target.targetType === "project" ||
          target.targetType === "opportunity") &&
        typeof target.targetId === "string" &&
        target.targetId.length > 0 &&
        typeof target.targetName === "string" &&
        month(target.startMonth) &&
        (target.endMonth === null || month(target.endMonth)) &&
        Array.isArray(target.needs) &&
        target.needs.every(
          (need: unknown) =>
            object(need) &&
            typeof need.needSn === "number" &&
            Number.isInteger(need.needSn) &&
            typeof need.jobCode === "string" &&
            typeof need.jobName === "string" &&
            typeof need.requiredCount === "number" &&
            Number.isInteger(need.requiredCount) &&
            month(need.startMonth),
        ),
    )
  );
}

export async function getCommonAiRecommendationOptions(
  filter: AiRecommendationOptionsFilter = {},
  signal?: AbortSignal,
): Promise<AiRecommendationOptions> {
  const query = new URLSearchParams();
  if (filter.targetType !== undefined)
    query.set("targetType", filter.targetType);
  if (filter.targetId !== undefined) query.set("targetId", filter.targetId);
  const suffix = query.size ? `?${query.toString()}` : "";
  try {
    const response = await fetch(
      `/api/common/ai-recommendation-options${suffix}`,
      { signal },
    );
    const body: unknown = await response.json();
    if (
      !response.ok ||
      !object(body) ||
      body.success !== true ||
      !options(body.data)
    ) {
      throw new Error(aiRecommendationOptionsError);
    }
    return body.data;
  } catch {
    throw new Error(aiRecommendationOptionsError);
  }
}

type AiRecommendationOptionsState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; data: AiRecommendationOptions };

export function useAiRecommendationOptions(
  filter: AiRecommendationOptionsFilter = {},
) {
  const { targetType, targetId } = filter;
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<AiRecommendationOptionsState>({
    status: "loading",
  });
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: "loading" });
    void getCommonAiRecommendationOptions(
      { targetType, targetId },
      controller.signal,
    ).then(
      (data) => {
        if (!controller.signal.aborted) setState({ status: "ready", data });
      },
      () => {
        if (!controller.signal.aborted) setState({ status: "error" });
      },
    );
    return () => controller.abort();
  }, [targetType, targetId, revision]);
  return { state, reload: () => setRevision((value) => value + 1) };
}

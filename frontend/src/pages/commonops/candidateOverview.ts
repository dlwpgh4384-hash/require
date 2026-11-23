import { developerRequest } from "../developers/developers";

export interface CandidateSummary {
  activeCount: number;
  interviewScheduledCount: number;
  availableByNextMonthCount: number;
  overdueContactCount: number;
}
export interface CandidateShortage {
  jobCode: string;
  jobName: string;
  shortageStartMonth: string;
  maxShortage: number;
  availableCandidateCount: number;
  totalCandidateCount: number;
}
export const candidateResumeFields = [
  "name",
  "desiredMonthlyRate",
  "employeeTypeCode",
  "companyId",
  "phone",
  "email",
  "primaryJobCode",
  "secondaryJobCode",
  "skills",
  "availableDate",
  "homeAreaCode",
  "homeAddress",
] as const;
export type CandidateResumeFieldName = (typeof candidateResumeFields)[number];
export interface CandidateResumeField {
  field: CandidateResumeFieldName;
  value: string;
  estimated: boolean;
  originalText?: string | null;
}
export interface CandidateResumeExtraction {
  fileName: string;
  fields: CandidateResumeField[];
  careerYears?: number | null;
  reviewRequired?: boolean;
}
export const getCommonCandidateSummary = (signal?: AbortSignal) =>
  developerRequest<CandidateSummary>("/api/common/candidate-summary", {
    signal,
  });
export const getCommonCandidateShortages = (signal?: AbortSignal) =>
  developerRequest<{ items: CandidateShortage[] }>(
    "/api/common/candidate-shortages",
    { signal },
  );
export const postCommonCandidateResumeExtraction = (
  file: File,
  signal?: AbortSignal,
) => {
  const body = new FormData();
  body.append("file", file);
  return developerRequest<CandidateResumeExtraction>(
    "/api/common/candidate-resume-extractions",
    { method: "POST", body, signal },
  );
};
const empty = (value: unknown) =>
  value == null || value === "" || (Array.isArray(value) && value.length === 0);

export function mergeCandidateResume<T extends object>(
  draft: T,
  extraction: CandidateResumeExtraction,
) {
  const next = { ...draft } as Record<string, unknown>;
  const applied: CandidateResumeField[] = [];
  const addressEmpty = empty(next.homeAreaCode) && empty(next.homeAddress);
  for (const field of extraction.fields) {
    if (
      !(candidateResumeFields as readonly string[]).includes(field.field) ||
      !empty(next[field.field]) ||
      !field.value
    )
      continue;
    if (
      (field.field === "homeAreaCode" || field.field === "homeAddress") &&
      !addressEmpty
    )
      continue;
    if (field.estimated && !field.originalText?.trim()) continue;
    let value: unknown = field.value;
    if (field.field === "desiredMonthlyRate") {
      if (!/^[0-9]{1,15}$/.test(field.value)) continue;
      value = Number(field.value);
    }
    if (field.field === "skills") {
      value = [
        ...new Set(
          field.value
            .split(",")
            .map((item) => item.trim())
            .filter(Boolean),
        ),
      ];
      if (!(value as string[]).length) continue;
    }
    next[field.field] = value;
    applied.push(field);
  }
  return { draft: next as T, applied };
}

export const candidateOverviewMessages = {
  loading: "불러오는 중…",
  failed: "처리하지 못했습니다. 다시 시도해 주세요.",
  empty: "조회된 자료가 없습니다.",
  noFields: "가져올 칸을 찾지 못했습니다. 직접 적어 주세요.",
  notRetained: "파일과 이력서 원문은 저장하지 않습니다.",
  review: "⚠ 확인해 주세요",
  certain: "✓ 이력서에서 가져옴",
};

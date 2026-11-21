import { downloadFile } from "../../shared/list/download";

export interface UploadIssue {
  row: number;
  column: string;
  inputValue: string;
  reason: string;
  severity: "오류" | "경고";
}
export interface UploadResult {
  uploadId: string;
  modifiedAt: string | null;
  statusName: string;
  successCount: number;
  warningCount: number;
  errorCount: number;
  issues: UploadIssue[];
  /** B.8: 관리번호를 비운 줄에 반영 때 붙을 번호(시안 「붙을 번호」). */
  plannedDevelopers?: { row: number; managementNumber: string }[];
  plannedProjects?: { row: number; projectId: string }[];
  distributionMismatchCount?: number;
  /** B.1: 고른 연월(YYYY-MM). */
  yearMonth?: string;
}
export class UploadRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
const root = "/api/common/uploads";
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success === false)
    throw new UploadRequestError(
      response.status,
      body.message || "처리하지 못했습니다. 다시 시도해 주세요.",
      body.errors || [],
    );
  return body.data as T;
}
export const getCommonUploadTemplate = (formTypeCode: string) =>
  downloadFile(
    `${root}/templates/${encodeURIComponent(formTypeCode)}`,
    "client-template.xlsx",
  );
export const getCommonUpload = (uploadId: string, signal?: AbortSignal) =>
  request<UploadResult>(`${root}/${encodeURIComponent(uploadId)}`, { signal });
export const getCommonUploadErrors = (uploadId: string) =>
  downloadFile(
    `${root}/${encodeURIComponent(uploadId)}/errors`,
    "upload-errors.xlsx",
  );
export function postCommonUpload(
  formTypeCode: string,
  file: File,
  context?: { projectId: string; distributionStepCode: string },
  /** 한 달 양식(B.2 재무 항목 등): 고른 연월 YYYY-MM. 주지 않으면 보내지 않는다. */
  yearMonth?: string,
) {
  const body = new FormData();
  body.set("formTypeCode", formTypeCode);
  body.set("file", file);
  if (yearMonth) body.set("yearMonth", yearMonth);
  if (context) {
    body.set("projectId", context.projectId);
    body.set("distributionStepCode", context.distributionStepCode);
  }
  return request<UploadResult>(root, { method: "POST", body });
}
export const postCommonUploadApply = (
  uploadId: string,
  modifiedAt: string | null,
) =>
  request<UploadResult>(`${root}/${encodeURIComponent(uploadId)}/apply`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ modifiedAt }),
  });
export const uploadFailure = (reason: unknown) =>
  reason instanceof Error
    ? reason.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

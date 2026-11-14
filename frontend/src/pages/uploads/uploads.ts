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
export function postCommonUpload(formTypeCode: string, file: File) {
  const body = new FormData();
  body.set("formTypeCode", formTypeCode);
  body.set("file", file);
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

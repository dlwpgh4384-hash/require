import { listMessages } from "./listTypes";

/**
 * SFR-011 엑셀 다운로드: 서버가 만든 .xlsx를 받아 저장한다.
 * 파일 이름은 서버의 Content-Disposition(filename*=UTF-8'')을 따른다.
 */
export async function downloadFile(
  url: string,
  fallbackName: string,
  createError?: (
    message: string,
    errors: { field: string; message: string }[],
  ) => Error,
  /** 받기 전에 응답 머리(예: X-Total-Count)를 읽거나 확인한다. 던지면 저장하지 않는다. */
  onResponse?: (response: Response) => void,
) {
  const response = await fetch(url);
  if (!response.ok) {
    let message: string = listMessages.downloadError;
    let errors: { field: string; message: string }[] = [];
    try {
      const body = await response.json();
      if (
        (response.status === 400 || response.status === 409) &&
        typeof body.message === "string"
      )
        message = body.message;
      if (response.status === 400 || response.status === 409)
        errors = Array.isArray(body.errors) ? body.errors : [];
    } catch {
      /* 본문이 JSON이 아니면 기본 문구 */
    }
    throw createError ? createError(message, errors) : new Error(message);
  }
  onResponse?.(response);
  const header = response.headers.get("Content-Disposition") ?? "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)?.[1];
  // filename*=UTF-8''… 가 없으면 filename="…"(서버 양식 이름)을 쓴다
  const plain = /filename="?([^";]+)"?/i.exec(header)?.[1];
  const name = encoded ? decodeURIComponent(encoded) : plain || fallbackName;
  const blob = await response.blob();
  const link = document.createElement("a");
  const href = URL.createObjectURL(blob);
  link.href = href;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 1000);
  return name;
}

import { listMessages } from "./listTypes";

/**
 * SFR-011 엑셀 다운로드: 서버가 만든 .xlsx를 받아 저장한다.
 * 파일 이름은 서버의 Content-Disposition(filename*=UTF-8'')을 따른다.
 */
export async function downloadFile(url: string, fallbackName: string) {
  const response = await fetch(url);
  if (!response.ok) {
    let message: string = listMessages.downloadError;
    try {
      const body = await response.json();
      if (
        (response.status === 400 || response.status === 409) &&
        typeof body.message === "string"
      )
        message = body.message;
    } catch {
      /* 본문이 JSON이 아니면 기본 문구 */
    }
    throw new Error(message);
  }
  const header = response.headers.get("Content-Disposition") ?? "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)?.[1];
  const name = encoded ? decodeURIComponent(encoded) : fallbackName;
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

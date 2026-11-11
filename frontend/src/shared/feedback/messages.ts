// FR-0106 안내 문구 형식. 존댓말로 쓰고 내부 코드·영문 오류는 보여 주지 않는다.
const withObject = (label: string) => {
  const last = label.charCodeAt(label.length - 1);
  const hasFinal = last >= 0xac00 && last <= 0xd7a3 && (last - 0xac00) % 28 !== 0;
  return `${label}${hasFinal ? "을" : "를"}`;
};

export const feedbackMessages = {
  required: (label: string) => `${withObject(label)} 입력해 주세요.`,
  format: (rule: string, example?: string) =>
    `${rule} 입력해 주세요.${example ? ` (예: ${example})` : ""}`,
  range: (min: number, max: number) =>
    `${min}~${max} 사이 숫자로 입력해 주세요.`,
  dateOrder: "종료일이 시작일보다 앞입니다.",
  duplicate: (label: string) => `이미 등록된 ${label}입니다.`,
  sameValue: "지금 기준과 같습니다.",
  unsaved: "저장하지 않은 내용이 있습니다. 나가면 입력한 내용이 사라집니다.",
  conflict: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
} as const;

/** 숫자 칸: 숫자가 아닌 글자는 입력 즉시 지운다. */
export function digitsOnly(value: string) {
  return value.replace(/[^0-9]/g, "");
}

/** 저장할 때 첫 오류 칸으로 커서와 스크롤을 옮긴다. */
export function focusFirstError(container: ParentNode | null | undefined) {
  const target = container?.querySelector<HTMLElement>('[aria-invalid="true"]');
  if (!target) return false;
  target.focus();
  target.scrollIntoView?.({ block: "center" });
  return true;
}

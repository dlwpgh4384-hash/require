export function validateScreenSettings(
  pageSize: string,
  dense: string,
): { pageSize?: string; dense?: string } {
  const errors: { pageSize?: string; dense?: string } = {};
  if (!pageSize) errors.pageSize = "기본 목록 건수를 입력해 주세요.";
  else if (!["20", "50", "100"].includes(pageSize))
    errors.pageSize = "목록 건수는 20, 50, 100 중에서 입력해 주세요.";
  if (!["넓게", "좁게"].includes(dense))
    errors.dense = "보기 밀도를 선택해 주세요.";
  return errors;
}
export const unsavedPreferenceMessage =
  "저장하지 않은 내용이 있습니다. 나가면 입력한 내용이 사라집니다.";
export function digitsOnly(value: string) {
  return value.replace(/[^0-9]/g, "");
}

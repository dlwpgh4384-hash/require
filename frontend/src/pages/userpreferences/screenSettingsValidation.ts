import { digitsOnly, feedbackMessages } from "../../shared/feedback/messages";

export function validateScreenSettings(
  pageSize: string,
  dense: string,
): { pageSize?: string; dense?: string } {
  const errors: { pageSize?: string; dense?: string } = {};
  if (!pageSize) errors.pageSize = feedbackMessages.required("기본 목록 건수");
  else if (!["20", "50", "100"].includes(pageSize))
    errors.pageSize = "목록 건수는 20, 50, 100 중에서 입력해 주세요.";
  if (!["넓게", "좁게"].includes(dense))
    errors.dense = "보기 밀도를 선택해 주세요.";
  return errors;
}
export const unsavedPreferenceMessage = feedbackMessages.unsaved;
export { digitsOnly };

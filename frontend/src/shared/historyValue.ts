/** 변경 이력의 「사용 여부」 값을 시안처럼 사용 / 중지로 보인다(저장 값 Y/N은 그대로, 2026-11-24 사용자 결정). */
export const historyValue = (fieldName: string, value: string | null | undefined) =>
  fieldName === "사용 여부" && (value === "Y" || value === "N")
    ? value === "Y"
      ? "사용"
      : "중지"
    : value;

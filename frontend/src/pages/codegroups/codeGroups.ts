export interface CodeGroup {
  groupId: string;
  groupName: string;
  sysCodeYn: "Y" | "N";
  valCodeYn: "Y" | "N";
  codeCount: number;
}
export interface CodeItem {
  code: string;
  /** 마지막 변경 시각(UTC). 저장할 때 그대로 보내 동시 수정을 막는다(FR-0106). */
  modifiedAt?: string;
  codeName: string;
  sortOrd: number;
  useYn: "Y" | "N";
  codeVal?: number;
  unitNm?: string;
  relatedJobs?: string;
  estimateUnitPrice?: number;
  requiredJobs?: string;
  weight?: number;
  contractTypes?: string;
  bankCode?: string;
  targetField?: string;
  job?: string;
}
export type CodeInput = Pick<CodeItem, "code" | "codeName"> & Partial<CodeItem>;
export class CodeRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true) {
    throw new CodeRequestError(
      response.status,
      (response.status === 400 || response.status === 409) &&
        typeof body.message === "string"
        ? body.message
        : "공통코드를 처리하지 못했습니다. 다시 시도해 주세요.",
      response.status === 400 && Array.isArray(body.errors) ? body.errors : [],
    );
  }
  if (!body.data || typeof body.data !== "object")
    throw new Error("공통코드 응답을 확인해 주세요.");
  return body.data as T;
}
const root = "/api/common/code-groups";
const codesPath = (groupId: string) =>
  `${root}/${encodeURIComponent(groupId)}/codes`;
const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const getCommonCodeGroups = (signal?: AbortSignal) =>
  request<{ items: CodeGroup[] }>(root, { signal });
export const getCommonCodes = (groupId: string, signal?: AbortSignal) =>
  request<{ items: CodeItem[] }>(codesPath(groupId), { signal });
export const postCommonCodeGroup = (input: {
  groupId: string;
  groupName: string;
}) => request<CodeGroup>(root, json("POST", input));
export const postCommonCode = (groupId: string, input: CodeInput) =>
  request<CodeItem>(codesPath(groupId), json("POST", input));
export const putCommonCodes = (groupId: string, items: CodeItem[]) =>
  request<{ items: CodeItem[] }>(codesPath(groupId), json("PUT", { items }));
export const postCommonCodeDelete = (groupId: string, code: string) =>
  request<{ groupId: string; code: string; delYn: "Y" }>(
    `${codesPath(groupId)}/${encodeURIComponent(code)}/delete`,
    { method: "POST" },
  );
export const CODE_OPTIONS_CHANGED = "common-code-options-changed";
export function notifyCodeOptionsChanged(groupId: string) {
  window.dispatchEvent(
    new CustomEvent(CODE_OPTIONS_CHANGED, { detail: { groupId } }),
  );
}
export type ExtraField = {
  key: keyof CodeItem;
  label: string;
  numeric?: boolean;
  /** 금액(원): 콤마를 넣어 보이고 숫자만 받는다(SFR-006-02). */
  money?: boolean;
  /** 시안 UI-27 표 아래 안내·추가 창 도움말 문구. */
  help: string;
};
export function codeExtraFields(groupId: string): ExtraField[] {
  switch (groupId) {
    case "JOB":
      return [
        { key: "relatedJobs", label: "관련 직무", help: "직무 코드, 쉼표로 구분 (AI 추천 식-70·다음 자리 식-80)" },
        { key: "estimateUnitPrice", label: "견적 기준 단가(원)", numeric: true, money: true, help: "월 단가, 견적·다음 자리 추천에 씀" },
      ];
    case "SKILL":
      return [{ key: "requiredJobs", label: "필요한 직무", help: "직무 코드, 쉼표로 구분 (AI 추천 스킬 점수 식-70)" }];
    case "PROJ_STEP":
      return [
        { key: "weight", label: "비중(%)", numeric: true, help: "진척률 계산에 쓰는 단계 비중" },
        { key: "contractTypes", label: "적용 계약유형", help: "계약유형 코드, 쉼표로 구분" },
      ];
    case "BANK":
      return [{ key: "bankCode", label: "은행 코드", help: "숫자 3자리" }];
    case "RSM_LABEL":
      return [{ key: "targetField", label: "넣을 칸", help: "NAME 이름·TEL 전화번호·MAIL 이메일·ADDR 거주지·SKILL 기술·WANT 희망 단가·FROM 투입 가능일·VNDR 소속 업체 중 하나 (외부 인력 후보 [이력서 등록])" }];
    case "RSM_JOB_KW":
      return [{ key: "job", label: "직무", help: "직무 코드, 쉼표로 구분 (외부 인력 후보 [이력서 등록]의 주 직무 추정)" }];
    default:
      return [];
  }
}
export const targetFields = [
  "NAME",
  "TEL",
  "MAIL",
  "ADDR",
  "SKILL",
  "WANT",
  "FROM",
  "VNDR",
];
export function validateCode(
  input: CodeInput,
  groupId: string,
): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!/^[A-Za-z0-9_]{1,20}$/.test(input.code))
    errors.code = "코드는 대문자·숫자·_ 20자 이내로 입력해 주세요.";
  if (!input.codeName.trim() || [...input.codeName].length > 100)
    errors.codeName = "이름을 1~100자로 입력해 주세요.";
  for (const field of codeExtraFields(groupId)) {
    const value = input[field.key];
    if (value === undefined || value === null || String(value).trim() === "")
      errors[field.key] = `${field.label}을 입력해 주세요.`;
  }
  if (input.targetField && !targetFields.includes(input.targetField))
    errors.targetField =
      "넣을 칸은 NAME·TEL·MAIL·ADDR·SKILL·WANT·FROM·VNDR 중 하나로 넣어 주세요.";
  return errors;
}

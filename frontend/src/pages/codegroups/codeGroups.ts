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
/** 시안 코드 이름 칸 maxlength(입력 규칙, DB 칸 크기는 그대로). */
export const CODE_NAME_MAX = 50;
/** 시안 「수당코드 추가」 이름 칸 maxlength. */
export const ALLOWANCE_NAME_MAX = 30;
/** 시안 추가값 칸 이름: 괄호 설명을 뺀 이름(예: 견적 기준 단가(원) → 견적 기준 단가). */
const extraName = (label: string) => label.replace(/\s*\(.*\)/, "");
/** 시안 문구로 코드 입력을 검사한다. 칸마다 첫 오류 하나. */
export function validateCode(
  input: CodeInput,
  groupId: string,
): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!/^[A-Za-z0-9_]{1,20}$/.test(input.code))
    errors.code = "코드는 영문 대문자·숫자·_ 로 넣어 주세요.";
  if (!input.codeName.trim() || [...input.codeName].length > CODE_NAME_MAX)
    errors.codeName = "이름을 넣어 주세요.";
  for (const field of codeExtraFields(groupId)) {
    const value = input[field.key];
    if (value === undefined || value === null || String(value).trim() === "")
      errors[field.key] = `「${extraName(field.label)}」 칸을 채워 주세요.`;
  }
  if (
    input.targetField &&
    !targetFields.includes(input.targetField.trim().toUpperCase())
  )
    errors.targetField =
      "넣을 칸은 NAME·TEL·MAIL·ADDR·SKILL·WANT·FROM·VNDR 중 하나로 넣어 주세요.";
  return errors;
}
/**
 * 시안 코드 표 [저장] 검사: 이름이 빈 줄이 있으면 「코드 이름은 비울 수 없습니다.」,
 * 추가값 오류는 「코드 · 오류」 한 줄. 문제가 없으면 빈 문자열.
 */
export function codeTableError(rows: CodeItem[], groupId: string): string {
  if (rows.some((row) => !row.codeName.trim()))
    return "코드 이름은 비울 수 없습니다.";
  for (const row of rows) {
    const errors = validateCode(row, groupId);
    const first = codeExtraFields(groupId)
      .map((field) => errors[field.key])
      .find(Boolean);
    if (first) return `${row.code} · ${first}`;
  }
  return "";
}

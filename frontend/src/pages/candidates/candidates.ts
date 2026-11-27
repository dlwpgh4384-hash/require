import type { ScreenPermission } from "../../shared/permission";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonClients } from "../clients/clients";
import {
  developerRequest,
  DeveloperRequestError,
} from "../developers/developers";
import { addDays, koreanToday } from "../../shared/time";

export { DeveloperRequestError as CandidateRequestError };
export interface CandidateInput {
  name: string;
  desiredMonthlyRate: number;
  employeeTypeCode: string;
  companyId: string | null;
  phone: string | null;
  email: string | null;
  privacyAgreement: boolean;
  primaryJobCode: string;
  secondaryJobCode: string | null;
  skills: string[];
  gradeCode: string;
  availableDate: string;
  homeAreaCode: string | null;
  homeAddress: string | null;
  sourceCode: string;
  memo: string | null;
}
export interface Candidate extends Omit<CandidateInput, "desiredMonthlyRate"> {
  /** 민감 칸을 볼 수 없는 사람에게는 서버가 보내지 않는다. */
  desiredMonthlyRate?: number;
  candidateId: string;
  employeeTypeName: string;
  companyName: string | null;
  primaryJobName: string;
  secondaryJobName: string | null;
  gradeName: string;
  sourceName: string;
  contactStatusCode: string;
  contactStatusName: string;
  lastContactDate: string;
  modifiedAt: string | null;
  privacyAgreementDate: string | null;
  privacyAgreementUserId: string | null;
  privacyAgreementUserName: string | null;
  daysSinceLastContact: number;
  overdueContact: boolean;
}
export interface CandidateContact {
  contactStatusCode: string;
  contactStatusName: string;
  memo: string;
  recordedAt: string;
  recordedBy: string;
  systemRecord: boolean;
}
export interface CandidateMatch {
  opportunityId: string;
  opportunityName: string;
  winningProbability: number;
  jobName: string;
  gradeName: string;
  startMonth: string;
  slotMonthlyRate: number;
  expectedProfitRate: number;
  lowMargin: boolean;
  /** 운영 기준값의 「낮은 이익률」 기준(%). 서버가 주면 빨강 표시와 안내 문구에 쓴다. */
  lowMarginRate?: number | null;
}
export interface CandidateMatches {
  items: CandidateMatch[];
  /** 자리마다 주지 않고 응답에 한 번 줄 때의 같은 기준(%). */
  lowMarginRate?: number | null;
}
export interface CandidateFilter {
  jobCode?: string;
  gradeCode?: string;
  employeeTypeCode?: string;
  contactStatusCode?: string;
  availabilityWindow?: string;
}
export interface CandidateOptions {
  codes: Record<string, { code: string; codeName: string }[]>;
  companies: { clientId: string; clientName: string }[];
}
export interface CandidateSummary {
  /** 전체 후보 수(시안 「후보 N명」). */
  totalCount?: number;
  activeCount: number;
  interviewScheduledCount: number;
  availableByNextMonthCount: number;
  overdueContactCount: number;
}
export interface CandidateShortage {
  jobCode: string;
  jobName: string;
  shortageStartMonth: string;
  maxShortage: number;
  availableCandidateCount: number;
  totalCandidateCount: number;
}
export interface CandidateExtraction {
  fileName: string;
  fields: {
    field: string;
    value: string;
    estimated: boolean;
    originalText?: string | null;
  }[];
  careerYears?: number | null;
}

/** 시안 v1.8 UI-33 문구(창·칸 오류·이력서 등록·알림). */
export const candidateMessages = {
  name: "이름을 넣어 주세요.",
  rate: "희망 단가를 넣어 주세요.",
  company: "외주는 소속 협력사를 골라 주세요.",
  skills: "기술을 하나 이상 적어 주세요.",
  consent:
    "전화번호·이메일을 저장하려면 개인정보 수집·이용 동의를 받았는지 체크해 주세요.",
  phone: "전화번호는 숫자와 - 로 적어 주세요.",
  email: "이메일 형식을 확인해 주세요.",
  required: "입력 값을 확인해 주세요.",
  memo: "메모를 적어 주세요.",
  resumeType:
    "Word·PDF·PPT·한글(HWP)·TXT 파일만 올릴 수 있습니다. 스캔한 그림 파일은 글자를 꺼낼 수 없어 직접 적어 주세요.",
  resumeSize: "10MB 이하 파일만 올릴 수 있습니다.",
  ok: "✓ 이력서에서 가져옴",
  warn: "⚠ 확인해 주세요",
  conflict: "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.",
  created: (name: string) => `${name}님을 후보로 등록했습니다.`,
  updated: (name: string) =>
    `${name}님 정보를 고쳤습니다. 맞는 자리 이익률도 새 값으로 계산했습니다.`,
  contacted: "연락 기록을 저장했습니다.",
} as const;

/** 이력서로 받는 파일 확장자. 시안 CN_EXT에서 서버가 읽지 못하는 rtf는 뺐다(그림 파일도 받지 않는다). */
export const RESUME_EXTENSIONS = [
  "txt",
  "doc",
  "docx",
  "pdf",
  "ppt",
  "pptx",
  "hwp",
  "hwpx",
] as const;
export const RESUME_ACCEPT = RESUME_EXTENSIONS.map((ext) => `.${ext}`).join(",");
export const RESUME_MAX_BYTES = 10 * 1024 * 1024;
/** 올리기 전에 확장자·크기를 본다. 맞지 않으면 시안 문구를, 맞으면 null. */
export function resumeFileError(file: File): string | null {
  const ext = (file.name.split(".").pop() || "").toLowerCase();
  if (
    !file.name.includes(".") ||
    !(RESUME_EXTENSIONS as readonly string[]).includes(ext)
  )
    return candidateMessages.resumeType;
  if (file.size > RESUME_MAX_BYTES) return candidateMessages.resumeSize;
  return null;
}

const root = "/api/common/candidates";
const target = (id: string) => `${root}/${encodeURIComponent(id)}`;
const write = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export const getCommonCandidates = (
  filter: CandidateFilter = {},
  signal?: AbortSignal,
) => {
  const query = new URLSearchParams(
    Object.entries(filter).filter(([, value]) => !!value) as [string, string][],
  );
  return developerRequest<{ items: Candidate[] }>(`${root}?${query}`, {
    signal,
  });
};
const xlsx =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
/** 목록 엑셀(SFR-011): 지금 조건의 전체 결과를 받고 건수를 돌려준다. */
export async function getCommonCandidatesExcel(
  filter: CandidateFilter = {},
): Promise<number> {
  const query = new URLSearchParams(
    Object.entries(filter).filter(([, value]) => !!value) as [string, string][],
  );
  const response = await fetch(`${root}/excel?${query}`);
  if (!response.ok || !response.headers.get("Content-Type")?.includes(xlsx)) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    try {
      message = (await response.json()).message || message;
    } catch {
      /* binary error */
    }
    throw new Error(message);
  }
  const count = response.headers.get("X-Total-Count");
  if (count === null || !/^\d+$/.test(count))
    throw new Error("조회 결과 건수를 확인하지 못했습니다.");
  const disposition = response.headers.get("Content-Disposition") || "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || "외부 인력 후보.xlsx";
  link.dataset.testid = "candidate-download-link";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 1000);
  return Number(count);
}
export const getCommonCandidate =(id: string, signal?: AbortSignal) =>
  developerRequest<Candidate>(target(id), { signal });
export const getCommonCandidateContacts = (id: string, signal?: AbortSignal) =>
  developerRequest<{ items: CandidateContact[] }>(`${target(id)}/contacts`, {
    signal,
  });
export const getCommonCandidateMatches = (id: string, signal?: AbortSignal) =>
  developerRequest<CandidateMatches>(`${target(id)}/matches`, { signal });
export function candidatePayload(input: CandidateInput): CandidateInput {
  return {
    name: input.name.trim(),
    desiredMonthlyRate: input.desiredMonthlyRate,
    employeeTypeCode: input.employeeTypeCode,
    companyId: input.employeeTypeCode === "OS" ? input.companyId : null,
    phone: input.phone?.trim() || null,
    email: input.email?.trim() || null,
    privacyAgreement: input.privacyAgreement,
    primaryJobCode: input.primaryJobCode,
    secondaryJobCode:
      input.secondaryJobCode === input.primaryJobCode
        ? null
        : input.secondaryJobCode || null,
    skills: [
      ...new Set(input.skills.map((value) => value.trim()).filter(Boolean)),
    ],
    gradeCode: input.gradeCode,
    availableDate: input.availableDate,
    homeAreaCode: input.homeAreaCode || null,
    homeAddress: input.homeAddress || null,
    sourceCode: input.sourceCode,
    memo: input.memo?.trim() || null,
  };
}
export const postCommonCandidate = (input: CandidateInput) =>
  developerRequest<Candidate>(root, write("POST", candidatePayload(input)));
export const putCommonCandidate = (
  id: string,
  input: CandidateInput,
  modifiedAt: string | null,
) =>
  developerRequest<Candidate>(
    target(id),
    write("PUT", { ...candidatePayload(input), modifiedAt }),
  );
export const postCommonCandidateContact = (
  id: string,
  body: { modifiedAt: string | null; contactStatusCode: string; memo: string },
) => developerRequest<Candidate>(`${target(id)}/contacts`, write("POST", body));
export const getCommonCandidateSummary = (signal?: AbortSignal) =>
  developerRequest<CandidateSummary>("/api/common/candidate-summary", {
    signal,
  });
export const getCommonCandidateShortages = (signal?: AbortSignal) =>
  developerRequest<{ items: CandidateShortage[] }>(
    "/api/common/candidate-shortages",
    { signal },
  );
export const postCommonCandidateResumeExtraction = (
  file: File,
  signal?: AbortSignal,
) => {
  const body = new FormData();
  body.append("file", file);
  return developerRequest<CandidateExtraction>(
    "/api/common/candidate-resume-extractions",
    { method: "POST", body, signal },
  );
};
export async function getCandidateOptions(): Promise<CandidateOptions> {
  const groups = ["EMP_TYPE", "JOB", "GRADE", "CNTC_STAT", "CAND_SRC"];
  const [codes, clients] = await Promise.all([
    Promise.all(
      groups.map(
        async (group) =>
          [
            group,
            (await getCommonCodes(group)).items.filter(
              (code) => code.useYn === "Y",
            ),
          ] as const,
      ),
    ),
    getCommonClients({ useYn: "Y" }),
  ]);
  return {
    codes: Object.fromEntries(codes),
    companies: clients.items.filter(
      (client) =>
        client.useYn === "Y" &&
        ["VNDR", "BOTH"].includes(client.clientTypeCode),
    ),
  };
}
/** 등록 창·조건의 구분 목록(프리랜서·외주만). */
export const candidateEmployeeTypes = (options: CandidateOptions) =>
  (options.codes.EMP_TYPE || []).filter((item) =>
    ["FL", "OS"].includes(item.code),
  );
/** 시안 등록 창 처음 값: 구분 프리랜서, 주 직무 첫 직무, 등급 중급, 투입 가능일 오늘+30, 출처 구인 사이트. */
export function candidateDefaults(options: CandidateOptions): CandidateInput {
  const code = (group: string, label: string) =>
    options.codes[group]?.find((value) => value.codeName === label)?.code || "";
  const types = candidateEmployeeTypes(options);
  return {
    name: "",
    desiredMonthlyRate: Number.NaN,
    employeeTypeCode:
      types.find((item) => item.code === "FL")?.code || types[0]?.code || "",
    companyId: null,
    phone: null,
    email: null,
    privacyAgreement: false,
    primaryJobCode: options.codes.JOB?.[0]?.code || "",
    secondaryJobCode: null,
    skills: [],
    gradeCode: code("GRADE", "중급"),
    availableDate: addDays(koreanToday(), 30),
    homeAreaCode: null,
    homeAddress: null,
    sourceCode: code("CAND_SRC", "구인 사이트"),
    memo: null,
  };
}
/** 외부 인력 후보(UI-33) 권한: 등록·수정은 메뉴 × 동작 권한, 희망 단가·예상 이익률은 민감 체크(⑦-9·⑨-1). 역할 이름은 안 본다. */
export function candidatePolicy(permission: ScreenPermission, sensitive: boolean) {
  return {
    canManage: permission.can("REG") || permission.can("MOD"),
    showAmounts: sensitive,
  };
}

const PHONE = /^[0-9][0-9-]{7,19}$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
/** 시안 [등록]·[저장] 칸 검사. 칸 이름 → 문구. */
export function candidateErrors(draft: CandidateInput) {
  const errors: Record<string, string> = {};
  if (!draft.name.trim()) errors.name = candidateMessages.name;
  if (
    !Number.isSafeInteger(draft.desiredMonthlyRate) ||
    draft.desiredMonthlyRate <= 0 ||
    draft.desiredMonthlyRate > 999999999999999
  )
    errors.desiredMonthlyRate = candidateMessages.rate;
  if (draft.employeeTypeCode === "OS" && !draft.companyId)
    errors.companyId = candidateMessages.company;
  if (!draft.skills.some((value) => value.trim()))
    errors.skills = candidateMessages.skills;
  const phone = draft.phone?.trim() || "";
  const email = draft.email?.trim() || "";
  if ((phone || email) && !draft.privacyAgreement)
    errors.privacyAgreement = candidateMessages.consent;
  if (phone && !PHONE.test(phone)) errors.phone = candidateMessages.phone;
  if (email && !EMAIL.test(email)) errors.email = candidateMessages.email;
  for (const key of [
    "employeeTypeCode",
    "primaryJobCode",
    "gradeCode",
    "availableDate",
    "sourceCode",
  ] as const)
    if (!draft[key]?.trim()) errors[key] = candidateMessages.required;
  return errors;
}

export interface ResumeMark {
  kind: "ok" | "warn";
  raw?: string | null;
}
export interface CandidateResumeMerge {
  input: CandidateInput;
  /** 채운 칸 이름(차례대로). */
  applied: string[];
  marks: Record<string, ResumeMark>;
  ok: number;
  warn: number;
  /** 이미 적은 칸이라 그대로 둔 칸 수. */
  skipped: number;
}
const RESUME_KEYS: (keyof CandidateInput)[] = [
  "name",
  "desiredMonthlyRate",
  "employeeTypeCode",
  "companyId",
  "phone",
  "email",
  "primaryJobCode",
  "secondaryJobCode",
  "skills",
  "availableDate",
  "homeAreaCode",
  "sourceCode",
  "memo",
];
const blank = (value: unknown) =>
  value == null ||
  value === "" ||
  (typeof value === "number" && Number.isNaN(value)) ||
  (Array.isArray(value) && !value.length);
/**
 * 시안 candFill: 빈칸만 채운다. 처음 값(투입 가능일 오늘+30, 출처 구인 사이트, 구분 프리랜서, 첫 직무)과
 * 같은 칸도 빈칸으로 본다. 등급·개인정보 동의는 채우지 않는다. 거주지 이름은 행정구역 코드와 함께 한 칸으로 센다.
 */
export function mergeCandidateExtraction(
  input: CandidateInput,
  result: CandidateExtraction,
  defaults?: CandidateInput,
): CandidateResumeMerge {
  const next = { ...input };
  const applied: string[] = [];
  const marks: Record<string, ResumeMark> = {};
  let ok = 0;
  let warn = 0;
  let skipped = 0;
  const empty = (field: keyof CandidateInput) =>
    blank(next[field]) ||
    (!!defaults &&
      !blank(defaults[field]) &&
      JSON.stringify(next[field]) === JSON.stringify(defaults[field]));
  const seen = new Set<string>();
  const items = result.fields.filter((item) => {
    if (seen.has(item.field)) return false;
    seen.add(item.field);
    return true;
  });
  const mark = (field: string, kind: "ok" | "warn", raw?: string | null) => {
    applied.push(field);
    marks[field] = { kind, raw: raw || null };
    if (kind === "ok") ok++;
    else warn++;
  };
  // 소속 업체는 구분을 본 뒤에 넣는다(시안: 소속이 있고 구분이 처음 값이면 외주로 바꾸고 ⚠).
  for (const item of [
    ...items.filter((x) => x.field !== "companyId"),
    ...items.filter((x) => x.field === "companyId"),
  ]) {
    const field = item.field as keyof CandidateInput;
    if (!RESUME_KEYS.includes(field) || !item.value?.trim()) continue;
    const text = item.value.trim();
    let value: unknown = text;
    if (field === "desiredMonthlyRate") {
      if (!/^[0-9]{1,15}$/.test(text) || !(Number(text) > 0)) continue;
      value = Number(text);
    }
    if (field === "skills") {
      value = [
        ...new Set(
          text
            .split(",")
            .map((part) => part.trim())
            .filter(Boolean),
        ),
      ];
      if (!(value as string[]).length) continue;
    }
    if (field === "secondaryJobCode" && value === next.primaryJobCode) continue;
    if (!empty(field)) {
      skipped++;
      continue;
    }
    if (field === "companyId" && next.employeeTypeCode !== "OS") {
      if (!empty("employeeTypeCode") || applied.includes("employeeTypeCode"))
        continue;
      next.employeeTypeCode = "OS";
      mark("employeeTypeCode", "warn", item.originalText?.trim() || "외주");
    }
    Object.assign(next, { [field]: value });
    const raw = item.originalText?.trim();
    mark(field, item.estimated ? "warn" : "ok", raw && raw !== text ? raw : null);
    if (field === "homeAreaCode") {
      const address = items.find((x) => x.field === "homeAddress")?.value;
      next.homeAddress = address?.trim() || null;
    }
  }
  return { input: next, applied, marks, ok, warn, skipped };
}
/** 시안 이력서 등록 결과 문구(창 위). */
export function resumeResultText(fileName: string, merge: CandidateResumeMerge) {
  const count = merge.ok + merge.warn;
  if (!count)
    return `「${fileName}」에서 가져올 칸을 찾지 못했습니다. 직접 적어 주세요. 파일은 저장하지 않습니다.`;
  return `「${fileName}」에서 ${count}칸을 채웠습니다(✓ ${merge.ok} · ⚠ ${merge.warn}). ⚠ 칸과 빈칸을 확인하고 [등록]을 누르세요. 개인정보 동의는 직접 체크합니다. 파일은 저장하지 않습니다.${merge.skipped ? ` 이미 적은 ${merge.skipped}칸은 그대로 두었습니다.` : ""}`;
}
/** 상세 이메일 옆 「동의 확인 MM-DD 이름」. 동의하지 않았으면 빈 글. */
export function consentNote(candidate: Candidate) {
  if (!candidate.privacyAgreement || !candidate.privacyAgreementDate) return "";
  const date = candidate.privacyAgreementDate.slice(0, 10);
  const who =
    candidate.privacyAgreementUserName || candidate.privacyAgreementUserId || "";
  return `동의 확인 ${date.slice(5)}${who ? ` ${who}` : ""}`;
}
/** 보류·계약 완료: 「n일 전」을 붙이지 않는다(SFR-133-03). */
export const closedCandidate = (candidate: { contactStatusName: string }) =>
  candidate.contactStatusName === "보류" ||
  candidate.contactStatusName === "계약 완료";
/** 시안 cstBadge 색: 연락 전 회색, 연락함 파랑, 면접 예정 주황, 보류 잠김, 계약 완료 초록. */
export function contactStatusKind(name: string) {
  return (
    (
      {
        "연락 전": "neutral",
        연락함: "info",
        "면접 예정": "warn",
        보류: "lock",
        "계약 완료": "ok",
      } as const
    )[name] ?? "neutral"
  );
}

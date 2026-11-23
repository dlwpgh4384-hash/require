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
export interface Candidate extends CandidateInput {
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
export const getCommonCandidate = (id: string) =>
  developerRequest<Candidate>(target(id));
export const getCommonCandidateContacts = (id: string) =>
  developerRequest<{ items: CandidateContact[] }>(`${target(id)}/contacts`);
export const getCommonCandidateMatches = (id: string) =>
  developerRequest<{ items: CandidateMatch[] }>(`${target(id)}/matches`);
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
export const getCommonCandidateSummary = () =>
  developerRequest<CandidateSummary>("/api/common/candidate-summary");
export const getCommonCandidateShortages = () =>
  developerRequest<{ items: CandidateShortage[] }>(
    "/api/common/candidate-shortages",
  );
export const postCommonCandidateResumeExtraction = (file: File) => {
  const body = new FormData();
  body.append("file", file);
  return developerRequest<CandidateExtraction>(
    "/api/common/candidate-resume-extractions",
    { method: "POST", body },
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
export function candidateDefaults(options: CandidateOptions): CandidateInput {
  const code = (group: string, label: string) =>
    options.codes[group]?.find((value) => value.codeName === label)?.code || "";
  return {
    name: "",
    desiredMonthlyRate: Number.NaN,
    employeeTypeCode: "",
    companyId: null,
    phone: null,
    email: null,
    privacyAgreement: false,
    primaryJobCode: "",
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
export function candidatePolicy(roles: string[]) {
  const canManage = roles.includes("ADMIN") || roles.includes("SALES_LDR");
  return { canManage, showAmounts: canManage || !roles.includes("PM") };
}
export function mergeCandidateExtraction(
  input: CandidateInput,
  result: CandidateExtraction,
) {
  const next = { ...input };
  const applied: string[] = [];
  const keys: (keyof CandidateInput)[] = [
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
    "homeAddress",
    "sourceCode",
    "memo",
  ];
  for (const item of result.fields) {
    const field = item.field as keyof CandidateInput;
    if (!keys.includes(field) || applied.includes(field)) continue;
    const current = next[field];
    const value =
      field === "desiredMonthlyRate"
        ? Number(item.value)
        : field === "skills"
          ? [
              ...new Set(
                item.value
                  .split(",")
                  .map((value) => value.trim())
                  .filter(Boolean),
              ),
            ]
          : item.value;
    if (
      field === "desiredMonthlyRate" &&
      (!item.value.trim() ||
        !Number.isSafeInteger(value) ||
        Number(value) < 0 ||
        Number(value) > 999999999999999)
    )
      continue;
    if (
      (current == null ||
        current === "" ||
        (typeof current === "number" && Number.isNaN(current)) ||
        (Array.isArray(current) && !current.length)) &&
      value != null &&
      value !== "" &&
      !(Array.isArray(value) && !value.length)
    ) {
      Object.assign(next, { [field]: value });
      applied.push(field);
    }
  }
  return { input: next, applied };
}

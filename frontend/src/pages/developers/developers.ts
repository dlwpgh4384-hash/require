import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonClients } from "../clients/clients";
import { getCommonParts } from "../parts/parts";

export interface DeveloperInput {
  managementNumber?: string;
  name: string;
  employeeTypeCode: string;
  organizationId: string;
  gradeCode?: string | null;
  primaryJobCode: string;
  secondaryJobCode?: string | null;
  positionCode?: string;
  directClassCode: string;
  salesPartId: string;
  hireDate?: string | null;
  monthlyRate?: number | null;
  monthlyPay?: number | null;
  skillCodes?: string[];
  homeAreaCode?: string | null;
  homeAddress?: string | null;
}
export interface Developer extends DeveloperInput {
  developerId: string;
  managementNumber: string;
  organizationName: string;
  employeeTypeName: string;
  gradeName?: string | null;
  primaryJobName: string;
  workStatusName: string;
  retirementScheduled: boolean;
  retirementDate: string | null;
  useYn: string;
  secondaryJobName?: string | null;
  positionName?: string | null;
  directClassName?: string | null;
  salesPartName?: string | null;
  skillNames?: string[];
  /** 동시 수정 막기: 저장할 때 그대로 돌려보낸다(다르면 409). */
  modifiedAt?: string | null;
}
export interface DeveloperListRow
  extends Omit<
    Developer,
    "monthlyPay" | "homeAreaCode" | "homeAddress" | "skillCodes" | "modifiedAt"
  > {
  currentProjectName: string | null;
  additionalProjectCount: number;
  currentMonthMm: number;
  overOneMm: boolean;
  withdrawalDate: string | null;
}
export interface DeveloperAssignment {
  projectName: string;
  jobName: string;
  startDate: string;
  endDate: string;
  mm: number;
}
export interface DeveloperStatus {
  statusId?: string | null;
  statusName: string;
  startDate: string;
  endDate: string | null;
  reason: string | null;
  registeredBy: string | null;
  ongoing?: boolean;
  retirementScheduled?: boolean;
  /** 휴직 줄이 재직(복귀)으로 닫힘(시안 「재직으로 닫힘」) */
  closedByReturn?: boolean;
  /** 재직·퇴사 줄의 달이 마감됨(시안 「마감됨」) */
  monthClosed?: boolean;
  /** 재직 줄을 취소하면 돌아갈 휴직 종료일(없으면 복직일 미정) */
  restoreEndDate?: string | null;
}
export interface DeveloperStatusList {
  items: DeveloperStatus[];
  /** 관리자·재무: 상태 등록·수정·취소(시안 canStatMe) */
  canManageStatuses: boolean;
  /** 시스템 관리자: 퇴사일이 지난 퇴사 취소(시안 isAdminMe) */
  canCancelPastRetirement: boolean;
}
export interface DeveloperChange {
  changedAt: string;
  fieldName: string;
  beforeValue: string | null;
  afterValue: string | null;
  changedBy: string;
  reason?: string | null;
}
export interface DeveloperArea {
  areaCode: string;
  areaName: string;
}
export interface DeveloperFilter {
  employeeTypeCode: string;
  jobCode: string;
  workStatusName: string;
  directClassCode: string;
  q: string;
  includeStopped: boolean;
}
export interface DeveloperOptions {
  codes: Record<string, { code: string; codeName: string }[]>;
  organizations: { id: string; name: string; useYn: string }[];
  parts: { id: string; name: string; useYn: string }[];
}
export class DeveloperRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
export async function developerRequest<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new DeveloperRequestError(
      response.status,
      body.message || "처리하지 못했습니다. 다시 시도해 주세요.",
      body.errors || [],
    );
  return body.data as T;
}
const root = "/api/common/developers";
/** 본사(직접 영업)의 예약 파트 ID(DDL TB_BS_PART.PART_ID 주석). */
export const HQ_PART = "HQ";
/** 시안 표기: 직접/간접 코드 이름 「간접(관리직)」을 「간접」으로 보인다. */
export const directLabel = (name: string | null | undefined) =>
  (name || "").replace(/\(관리직\)$/, "") || "–";
/** 금액: 콤마, 값 없음 「–」(공통 기능). */
export const won = (value: number | null | undefined) =>
  value == null ? "–" : value.toLocaleString("ko-KR");
const target = (id: string) => `${root}/${encodeURIComponent(id)}`;
const write = (method: string, body?: unknown): RequestInit => ({
  method,
  ...(body === undefined
    ? {}
    : {
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
});
export const developerQuery = (filter: Partial<DeveloperFilter>) =>
  new URLSearchParams(
    Object.entries(filter)
      .filter(([, v]) => v !== "" && v !== undefined)
      .map(([k, v]) => [k, String(v)]),
  ).toString();
export const developersExcelUrl = (filter: Partial<DeveloperFilter>) =>
  `${root}/excel?${developerQuery(filter)}`;
export const getCommonDevelopers = (
  filter: Partial<DeveloperFilter>,
  signal?: AbortSignal,
) =>
  developerRequest<{ items: DeveloperListRow[] }>(
    `${root}?${developerQuery(filter)}`,
    { signal },
  );
export const getCommonDeveloper = (id: string) =>
  developerRequest<Developer>(target(id));
export const getCommonDeveloperAssignments = (id: string) =>
  developerRequest<{ items: DeveloperAssignment[] }>(
    `${target(id)}/assignments`,
  );
export const getCommonDeveloperStatuses = (id: string) =>
  developerRequest<DeveloperStatusList>(`${target(id)}/statuses`);
export const getCommonDeveloperChangeHistory = (id: string) =>
  developerRequest<{ items: DeveloperChange[] }>(
    `${target(id)}/change-history`,
  );
export const getCommonDeveloperAreas = (parentAreaCode?: string) =>
  developerRequest<{ items: DeveloperArea[] }>(
    `${root}/areas${parentAreaCode ? `?parentAreaCode=${encodeURIComponent(parentAreaCode)}` : ""}`,
  );
export function developerPayload(
  input: DeveloperInput,
  editing: boolean,
): DeveloperInput {
  const body: DeveloperInput = {
    name: input.name.trim(),
    employeeTypeCode: input.employeeTypeCode,
    organizationId: input.organizationId,
    primaryJobCode: input.primaryJobCode,
    salesPartId: input.salesPartId,
    directClassCode: input.directClassCode,
    gradeCode: input.gradeCode || null,
    secondaryJobCode: input.secondaryJobCode || null,
    positionCode: input.positionCode,
    hireDate: input.hireDate || null,
    monthlyRate: input.monthlyRate ?? null,
    monthlyPay: input.monthlyPay ?? null,
    skillCodes: input.skillCodes || [],
    homeAreaCode: input.homeAreaCode || null,
  };
  if (!editing && input.managementNumber?.trim())
    body.managementNumber = input.managementNumber.trim();
  return body;
}
export const postCommonDeveloper = (body: DeveloperInput) =>
  developerRequest<Developer>(
    root,
    write("POST", developerPayload(body, false)),
  );
export const putCommonDeveloper = (
  id: string,
  body: DeveloperInput,
  modifiedAt: string | null,
) =>
  developerRequest<Developer>(
    target(id),
    write("PUT", { ...developerPayload(body, true), modifiedAt }),
  );
export const postCommonDeveloperStatus = (
  id: string,
  body: {
    statusName: string;
    date: string;
    endDate?: string | null;
    reason?: string;
  },
) =>
  developerRequest<DeveloperStatusList>(
    `${target(id)}/statuses`,
    write("POST", body),
  );
export const putCommonDeveloperStatusEndDate = (
  id: string,
  statusId: string,
  endDate: string | null,
) =>
  developerRequest<DeveloperStatusList>(
    `${target(id)}/statuses/${encodeURIComponent(statusId)}/end-date`,
    write("PUT", { endDate }),
  );
export const postCommonDeveloperReturnCancel = (id: string, statusId: string) =>
  developerRequest<DeveloperStatusList>(
    `${target(id)}/statuses/${encodeURIComponent(statusId)}/return-cancel`,
    write("POST"),
  );
export const postCommonDeveloperRetirementCancel = (
  id: string,
  statusId: string,
  reason: string,
) =>
  developerRequest<DeveloperStatusList>(
    `${target(id)}/statuses/${encodeURIComponent(statusId)}/retirement-cancel`,
    write("POST", { reason }),
  );
export const putCommonDeveloperUseStatus = (
  developerIds: string[],
  useYn: string,
) =>
  developerRequest<{
    processedDeveloperIds: string[];
    rejected: { developerId: string; name: string; reason: string }[];
  }>(`${root}/use-status`, write("PUT", { developerIds, useYn }));
export async function getDeveloperOptions(): Promise<DeveloperOptions> {
  const groups = [
    "EMP_TYPE",
    "JOB",
    "GRADE",
    "JBPS",
    "DRCT_CLS",
    "SKILL",
    "WORK_STAT",
  ];
  const [codes, clients, parts] = await Promise.all([
    Promise.all(
      groups.map(
        async (group) =>
          [
            group,
            (await getCommonCodes(group)).items.filter((c) => c.useYn === "Y"),
          ] as const,
      ),
    ),
    getCommonClients({ useYn: "" }),
    getCommonParts(),
  ]);
  return {
    codes: Object.fromEntries(codes),
    organizations: clients.items
      .filter((c) => ["VNDR", "BOTH"].includes(c.clientTypeCode))
      .map((c) => ({ id: c.clientId, name: c.clientName, useYn: c.useYn })),
    // 시안: 사용 중 파트 + 「본사(직접 영업)」(예약 ID HQ, TB_BS_PART.PART_ID 주석)
    parts: [
      ...parts.items.map((p) => ({
        id: p.partId,
        name: p.partName,
        useYn: p.useYn,
      })),
      { id: HQ_PART, name: "본사(직접 영업)", useYn: "Y" },
    ],
  };
}
export const developerFailure = (reason: unknown) =>
  reason instanceof Error
    ? reason.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

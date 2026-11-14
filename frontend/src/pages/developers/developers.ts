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
}
export interface DeveloperListRow
  extends Omit<
    Developer,
    "monthlyPay" | "homeAreaCode" | "homeAddress" | "skillCodes"
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
  registeredBy: string;
  ongoing?: boolean;
  retirementScheduled?: boolean;
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
  developerRequest<{ items: DeveloperStatus[] }>(`${target(id)}/statuses`);
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
export const putCommonDeveloper = (id: string, body: DeveloperInput) =>
  developerRequest<Developer>(
    target(id),
    write("PUT", developerPayload(body, true)),
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
  developerRequest<{ items: DeveloperStatus[] }>(
    `${target(id)}/statuses`,
    write("POST", body),
  );
export const putCommonDeveloperStatusEndDate = (
  id: string,
  statusId: string,
  endDate: string | null,
) =>
  developerRequest<{ items: DeveloperStatus[] }>(
    `${target(id)}/statuses/${encodeURIComponent(statusId)}/end-date`,
    write("PUT", { endDate }),
  );
export const postCommonDeveloperReturnCancel = (id: string, statusId: string) =>
  developerRequest<{ items: DeveloperStatus[] }>(
    `${target(id)}/statuses/${encodeURIComponent(statusId)}/return-cancel`,
    write("POST"),
  );
export const postCommonDeveloperRetirementCancel = (
  id: string,
  statusId: string,
  reason: string,
) =>
  developerRequest<{ items: DeveloperStatus[] }>(
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
    parts: parts.items.map((p) => ({
      id: p.partId,
      name: p.partName,
      useYn: p.useYn,
    })),
  };
}
export const developerFailure = (reason: unknown) =>
  reason instanceof Error
    ? reason.message
    : "처리하지 못했습니다. 다시 시도해 주세요.";

import { projectRequest } from "../projects/projects";
import { downloadFile } from "../../shared/list/download";

export interface Assignment {
  assignmentId: string;
  projectId: string;
  projectName: string;
  developerId: string;
  name: string;
  managementNumber?: string;
  jobCode?: string;
  jobName: string;
  startDate: string;
  endDate: string;
  billingPrice?: number | null;
  monthlyMm: number;
  yearMm: number;
  extensionPossibilityCode?: string | null;
  extensionPossibilityName: string;
  inputTypeCode?: string;
  inputTypeName: string;
  extensionCount: number;
  warnings: string[];
  monthlyMmTotal?: number;
  historyCount: number;
  modifiedAt: string | null;
}
export interface AssignmentHistory {
  historyId: string;
  assignmentId: string;
  effectiveDate: string;
  changeTypeCode: string;
  changeTypeName: string;
  beforeValue: string;
  afterValue: string;
  reason: string;
  memo: string;
  changedBy: string;
  changedAt: string | null;
  retroactiveYn: string;
  correctedYn: string;
  modifiedAt: string | null;
}
export interface AssignmentCandidate {
  candidateId: string;
  projectId: string;
  projectName: string;
  developerId: string;
  name: string;
  jobCode: string;
  jobName: string;
  startDate: string;
  endDate: string;
}
export interface AssignmentContractDeveloper {
  developerId: string;
  name: string;
  endDate: string;
  billingPrice: number | null;
  overlappingAssignments: Assignment[];
}
export interface AssignmentInput {
  projectId: string;
  developerId: string;
  jobCode: string;
  startDate: string;
  endDate: string;
  monthlyMm: number;
  inputTypeCode: string;
  billingPrice?: number;
  contractDeveloperId?: string;
  replacementAction?: "together" | "assignment-only";
  earlyWithdrawalAssignmentIds?: string[];
  reasonCode?: string;
  memo?: string;
}
export interface AssignmentFilter {
  year: number;
  projectId?: string;
  jobCode?: string;
  warning?: string;
  keyword?: string;
}
export interface AssignmentChange {
  modifiedAt: string | null;
  changeTypeCode: string;
  effectiveDate: string;
  newEndDate?: string;
  newBillingPrice?: number;
  newMonthlyMm?: number;
  newExtensionPossibilityCode?: string;
  newInputTypeCode?: string;
  reasonCode?: string;
  memo?: string;
}
const root = "/api/common/assignments";
const query = (filter: object) => {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return params.toString();
};
const command = <T>(path: string, method: string, body?: unknown) =>
  projectRequest<T>(path, {
    method,
    ...(body === undefined
      ? {}
      : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
  });
export const getCommonAssignments = (
  filter: AssignmentFilter,
  signal?: AbortSignal,
) =>
  projectRequest<{ items: Assignment[] }>(`${root}?${query(filter)}`, {
    signal,
  });
export const getCommonAssignment = (id: string) =>
  projectRequest<Assignment>(`${root}/${encodeURIComponent(id)}`);
export const getCommonAssignmentCandidates = () =>
  projectRequest<{ items: AssignmentCandidate[] }>(`${root}/candidates`);
export const getCommonAssignmentContractDevelopers = (
  projectId: string,
  developerId: string,
  startDate: string,
) =>
  projectRequest<{ items: AssignmentContractDeveloper[] }>(
    `${root}/contract-developers?${query({ projectId, developerId, startDate })}`,
  );
export const getCommonAssignmentHistory = (id: string, changeTypeCode = "") =>
  projectRequest<{ items: AssignmentHistory[] }>(
    `${root}/${encodeURIComponent(id)}/history?${query({ changeTypeCode })}`,
  );
export const postCommonAssignment = (body: AssignmentInput) =>
  command<Assignment>(root, "POST", body);
export const postCommonAssignmentCandidateRemove = (id: string) =>
  command<{ candidateId: string }>(
    `${root}/candidates/${encodeURIComponent(id)}/remove`,
    "POST",
  );
export const postCommonAssignmentChange = (
  id: string,
  body: AssignmentChange,
) =>
  command<Assignment>(
    `${root}/${encodeURIComponent(id)}/changes`,
    "POST",
    body,
  );
export const postCommonAssignmentHistoryCorrection = (
  id: string,
  history: AssignmentHistory,
  correctedValue: string,
  reason: string,
) =>
  command<AssignmentHistory>(
    `${root}/${encodeURIComponent(id)}/history/${encodeURIComponent(history.historyId)}/corrections`,
    "POST",
    { modifiedAt: history.modifiedAt, correctedValue, reason },
  );
export const postCommonAssignmentCancel = (assignments: Assignment[]) =>
  command<{ items: Assignment[] }>(`${root}/cancel`, "POST", {
    assignments: assignments.map(({ assignmentId, modifiedAt }) => ({
      assignmentId,
      modifiedAt,
    })),
  });
export const putCommonAssignmentExtensionPossibility = (
  assignments: Assignment[],
  extensionPossibilityCode: string,
) =>
  command<{ items: Assignment[] }>(`${root}/extension-possibility`, "PUT", {
    assignments: assignments.map(({ assignmentId, modifiedAt }) => ({
      assignmentId,
      modifiedAt,
    })),
    extensionPossibilityCode,
  });
export const getCommonAssignmentsExcel = (filter: AssignmentFilter) =>
  downloadFile(`${root}/excel?${query(filter)}`, "assignments.xlsx");
export const assignmentPriceEditable = (roles: readonly string[]) =>
  roles.includes("FIN") || roles.includes("SALES_LDR");
export function assignmentInitialDates(
  today: string,
  project: { startDate: string; endDate: string },
  changed = false,
  recommendedMonth?: string,
) {
  return {
    startDate: recommendedMonth
      ? [recommendedMonth + "-01", project.startDate].sort().at(-1)!
      : changed
        ? project.startDate
        : today,
    endDate: project.endDate,
  };
}

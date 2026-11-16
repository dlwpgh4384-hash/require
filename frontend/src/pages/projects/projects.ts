import { downloadFile } from "../../shared/list/download";

export interface Project {
  projectId: string;
  projectName: string;
  customerId: string | null;
  customerName: string;
  contractTypeCode: string;
  contractTypeName: string;
  startDate: string;
  endDate: string;
  salesPartId: string | null;
  salesPartName: string | null;
  workplace: string | null;
  statusCode: string;
  statusName: string;
  estimateAmount: number | null;
  contractAmount: number;
  originalContractAmount: number;
  budgetAmount: number | null;
  budgetManualYn: string;
  teamExpenseAmount: number;
  projectExpenseAmount: number;
  feature: string | null;
  pmName: string | null;
  trafficLight: string | null;
  canEdit: boolean;
  canChangeBudget: boolean;
  canChangeSalesPart: boolean;
  canSaveManpowerPlan: boolean;
  canLose: boolean;
  canStart: boolean;
  assignedPeopleCount: number;
  preContractPeopleCount: number;
  distributionStepName: string | null;
  distributionMismatchCount: number;
  distributionTotalWarning: boolean;
  loseDate: string | null;
  loseReasonName: string | null;
  loseMemo: string | null;
  assignments?: {
    name: string;
    jobName: string;
    startDate: string;
    endDate: string;
    mm: number;
  }[];
  progress?: {
    plannedProgressRate: number | null;
    actualProgressRate: number | null;
    steps: {
      stepName: string;
      weightRate: number;
      plannedStartDate: string;
      plannedEndDate: string;
      completedDate: string | null;
    }[];
  };
  issues?: {
    issueTitle: string;
    importanceName: string;
    dueDate: string | null;
    statusName: string;
    action: string | null;
  }[];
}
export interface ProjectInput {
  projectName: string;
  customerId?: string;
  contractTypeCode?: string;
  startDate: string;
  endDate: string;
  salesPartId?: string;
  workplace?: string;
  statusCode?: string;
  estimateAmount?: number;
  contractAmount?: number;
  budgetAmount?: number;
  teamExpenseAmount?: number;
  projectExpenseAmount?: number;
  feature?: string;
}
export interface ProjectExpense {
  expenseId: string;
  yearMonth: string;
  itemCode: string;
  itemName: string;
  amount: number;
  memo: string | null;
}
export interface ProjectHistory {
  changedAt: string;
  itemName: string;
  beforeValue: string | null;
  afterValue: string | null;
  changedBy: string;
}
export interface ProjectPart {
  salesPartId: string;
  salesPartName: string;
  effectiveMonth: string;
  reason: string | null;
  changedBy: string;
  scheduled: boolean;
}
export interface ProjectJob {
  jobCode: string;
  jobName: string;
  plannedMm: number | null;
  actualMm: number;
  differenceMm: number | null;
}
export interface ProjectDistributionData {
  project: Project;
  items: {
    rowTypeCode: string;
    managementNumber: string | null;
    name: string;
    jobNames: string[];
    itemName: string | null;
    monthlyAmounts: Record<string, number>;
    totalAmount: number;
  }[];
  uploads: {
    uploadedAt: string;
    stepName: string;
    stepCode: string;
    fileName: string;
    uploadedBy: string;
  }[];
  mismatches: { name: string; kind: string; months: string[] }[];
  totalAmountWarning: boolean;
}
export class ProjectRequestError extends Error {
  constructor(
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
const root = "/api/common/projects";
export async function projectRequest<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new ProjectRequestError(
      body.message || "프로젝트를 처리하지 못했습니다.",
      body.errors || [],
    );
  return body.data as T;
}
const command = <T>(
  id: string,
  suffix: string,
  method: string,
  body?: unknown,
) =>
  projectRequest<T>(`${root}/${encodeURIComponent(id)}${suffix}`, {
    method,
    ...(body === undefined
      ? {}
      : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
  });
export const getCommonProjects = (signal?: AbortSignal) =>
  projectRequest<{ items: Project[]; unassignedCount: number }>(root, {
    signal,
  });
export const getCommonProject = (id: string, signal?: AbortSignal) =>
  projectRequest<Project>(`${root}/${encodeURIComponent(id)}`, { signal });
export const postCommonProject = (body: ProjectInput) =>
  projectRequest<Project>(root, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
export const putCommonProject = (id: string, body: ProjectInput) =>
  command<Project>(id, "", "PUT", body);
export const putCommonProjectBudget = (id: string, budgetAmount: number) =>
  command<Project>(id, "/budget", "PUT", { budgetAmount });
export const getCommonProjectChangeHistory = (id: string) =>
  command<{ items: ProjectHistory[] }>(id, "/change-history", "GET");
export const getCommonProjectDirectExpenses = (id: string) =>
  command<{ items: ProjectExpense[] }>(id, "/direct-expenses", "GET");
export const postCommonProjectDirectExpense = (
  id: string,
  body: { yearMonth: string; itemCode: string; amount: number; memo?: string },
) => command<ProjectExpense>(id, "/direct-expenses", "POST", body);
export const postCommonProjectDirectExpenseDelete = (
  id: string,
  expenseId: string,
) =>
  command<{ deleted: boolean }>(
    id,
    `/direct-expenses/${encodeURIComponent(expenseId)}/delete`,
    "POST",
  );
export const getCommonProjectSalesParts = (id: string) =>
  command<{ items: ProjectPart[] }>(id, "/sales-parts", "GET");
export const putCommonProjectSalesPart = (
  id: string,
  body: { salesPartId: string; effectiveMonth: string; reason?: string },
) => command<{ items: ProjectPart[] }>(id, "/sales-part", "PUT", body);
export const getCommonProjectManpowerPlan = (id: string) =>
  command<{ items: ProjectJob[]; canSave: boolean }>(
    id,
    "/manpower-plan",
    "GET",
  );
export const putCommonProjectManpowerPlan = (
  id: string,
  jobs: { jobCode: string; plannedMm: number | null }[],
) =>
  command<{ items: ProjectJob[]; canSave: boolean }>(
    id,
    "/manpower-plan",
    "PUT",
    { jobs },
  );
export const getCommonProjectLoseImpact = (id: string) =>
  command<{ activePeopleCount: number; futurePeopleCount: number }>(
    id,
    "/lose-impact",
    "GET",
  );
export const postCommonProjectStart = (id: string) =>
  command<Project>(id, "/start", "POST");
export const postCommonProjectLose = (
  id: string,
  reasonCode: string,
  memo?: string,
) =>
  command<Project>(id, "/lose", "POST", {
    reasonCode,
    ...(memo ? { memo } : {}),
  });
export const getCommonProjectDistribution = (id: string) =>
  command<ProjectDistributionData>(id, "/distribution", "GET");
export const getCommonProjectDistributionTemplate = (id: string) =>
  downloadFile(
    `${root}/${encodeURIComponent(id)}/distribution/template`,
    "distribution.xlsx",
  );
export const projectToday = () =>
  new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
export const won = (value: number | null | undefined) =>
  value == null ? "–" : value.toLocaleString("ko-KR");
export const projectFailure = (error: unknown) =>
  error instanceof Error ? error.message : "다시 시도해 주세요.";
export interface ProjectFilters {
  year: string;
  contractTypeCode: string;
  statusCode: string;
  salesPartId: string;
  unassignedOnly: boolean;
  keyword: string;
}
export function filterProjects(rows: Project[], f: ProjectFilters) {
  const yearly = rows.filter(
    (p) => p.startDate <= `${f.year}-12-31` && p.endDate >= `${f.year}-01-01`,
  );
  const unassignedCount = yearly.filter(
    (p) => p.contractTypeCode !== "IN" && !p.salesPartId,
  ).length;
  return {
    unassignedCount,
    items: yearly
      .filter(
        (p) =>
          (!f.contractTypeCode || p.contractTypeCode === f.contractTypeCode) &&
          (!f.statusCode || p.statusCode === f.statusCode) &&
          (!f.salesPartId ||
            (f.salesPartId === "unassigned"
              ? !p.salesPartId
              : p.salesPartId === f.salesPartId)) &&
          (!f.unassignedOnly ||
            (p.contractTypeCode !== "IN" && !p.salesPartId)) &&
          (!f.keyword ||
            `${p.projectName} ${p.customerName}`
              .toLowerCase()
              .includes(f.keyword.trim().toLowerCase())),
      )
      .sort(
        (a, b) =>
          b.startDate.localeCompare(a.startDate) ||
          a.projectId.localeCompare(b.projectId),
      ),
  };
}

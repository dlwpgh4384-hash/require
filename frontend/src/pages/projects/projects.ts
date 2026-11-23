import { koreanToday } from "../../shared/time";
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
  /** 신호등 이유(현황판과 같은 판정·문구). 종료·실주는 없음. */
  signalReasons?: string[];
  signalReasonLevels?: string[];
  /** 마지막 업데이트(메모 작성일, 없으면 단계를 처음 만든 날)와 지난 날 수, 14일 초과 여부. */
  lastUpdateDate?: string | null;
  daysWithoutUpdate?: number | null;
  staleUpdate?: boolean | null;
  /** 턴키만: 원가 사용률(%)·진척 대비 원가(%p). */
  costUsageRate?: number | null;
  costProgressGap?: number | null;

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
  /** 동시 수정 확인용 마지막 수정 시각(UTC). 수정·예산 저장 때 그대로 보낸다. */
  modifiedAt?: string | null;
  /** 상세에서만: 마지막 마감월(YYYY-MM). */
  closedMonth?: string | null;
  memos?: {
    writtenDate: string;
    thisWeek: string | null;
    nextWeek: string | null;
    request: string | null;
    actualProgressRate: number | null;
  }[];
  assignments?: {
    name: string;
    managementNumber?: string | null;
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
    importanceCode?: string;
    statusCode?: string;
    importanceName: string;
    dueDate: string | null;
    statusName: string;
    action: string | null;
  }[];
}
export interface ProjectInput {
  /** 수정 때만: 읽은 프로젝트의 modifiedAt(다르면 409). */
  modifiedAt?: string | null;
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
    /** HTTP 상태(409면 그 사이 다른 사람이 바꾼 것, FR-0106). 모르면 0. */
    public status = 0,
  ) {
    super(message);
  }
}
/** FR-0106: 다른 사람이 먼저 바꿔 저장이 막혔는지(409). */
export const isProjectConflict = (error: unknown) =>
  error instanceof ProjectRequestError && error.status === 409;
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
      response.status,
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
export const putCommonProjectBudget = (id: string, budgetAmount: number, modifiedAt?: string | null) =>
  command<Project>(id, "/budget", "PUT", { budgetAmount, modifiedAt: modifiedAt ?? null });
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
/** 한국 날짜 「오늘」(YYYY-MM-DD). 공통 shared/time을 쓴다. */
export const projectToday = () => koreanToday();
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

/** 시안 계약유형 배지 글자: 내부(자체 개발)는 「내부」. */
export const typeLabel = (p: Pick<Project, "contractTypeCode" | "contractTypeName">) =>
  p.contractTypeCode === "IN" ? "내부" : p.contractTypeName;
/** 시안 상태 배지 색: 진행 파랑, 수주예정 주황, 종료·실주 회색. */
export const statusKind = (code: string) =>
  code === "ING" ? "info" : code === "PRE" ? "warn" : "lock";
export const HQ_PART = "HQ";
export interface ProjectListQuery {
  year: string;
  contractTypeCode: string;
  statusCode: string;
  salesPartId: string;
  q: string;
}
export const projectsExcelUrl = (f: ProjectListQuery) => {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(f)) if (value) query.set(key, value);
  return `${root}/excel?${query}`;
};
export interface ProjectBudgetRule {
  targetMarginRate: number;
  budgetFactor: number;
}
/** 예산 기본값 규칙(식-19). 계산식은 서버가 계산표에서 읽어 곱하는 수만 준다. */
export const getCommonProjectBudgetRule = async (): Promise<ProjectBudgetRule | null> => {
  const rule = await projectRequest<ProjectBudgetRule>(`${root}/budget-rule`);
  const factor = Number(rule?.budgetFactor);
  const margin = Number(rule?.targetMarginRate);
  return Number.isFinite(factor) && Number.isFinite(margin) ? { budgetFactor: factor, targetMarginRate: margin } : null;
};
export const defaultBudget = (contractAmount: number, rule: ProjectBudgetRule | null) =>
  rule ? Math.floor(contractAmount * rule.budgetFactor) : null;

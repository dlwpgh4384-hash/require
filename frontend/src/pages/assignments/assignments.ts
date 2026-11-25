import { ProjectRequestError } from "../projects/projects";
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
  /** 상세만: 투입 기간의 달마다 그 달에 적용되는 계약 월 MM·청구 단가(단가를 볼 수 없으면 null). */
  months?: AssignmentMonth[];
}
export interface AssignmentMonth {
  month: string;
  monthlyMm: number;
  billingPrice: number | null;
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
  /** 후보 빼기 때 같이 보내는 수정 시각(다른 사람이 먼저 바꿨으면 409). */
  modifiedAt?: string | null;
  projectId: string;
  projectName: string;
  developerId: string;
  name: string;
  /** 시안 「이름 관리번호」. 서버가 주면 보인다. */
  managementNumber?: string | null;
  jobCode: string;
  jobName: string;
  startDate: string;
  endDate: string;
}
export interface AssignmentContractDeveloper {
  developerId: string;
  name: string;
  /** 시안 「이름 · 직무 · 단가」. 서버가 주면 보인다. */
  jobName?: string | null;
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
  /** 배정 후보 [확정]으로 연 창이면 그 후보 ID(저장하면 후보에서 빠진다). */
  candidateId?: string;
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
/** 투입 배정 요청 실패: 프로젝트 요청 실패와 같고, 409(다른 사람이 먼저 바꿈)를 가리려고 HTTP 상태를 같이 둔다. */
export class AssignmentRequestError extends ProjectRequestError {
  constructor(
    message: string,
    errors: { field: string; message: string }[],
    public status: number,
  ) {
    super(message, errors);
  }
}
async function projectRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new AssignmentRequestError(body.message || "프로젝트를 처리하지 못했습니다.", body.errors || [], response.status);
  return body.data as T;
}
export const isAssignmentConflict = (error: unknown) => error instanceof AssignmentRequestError && error.status === 409;
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
export const postCommonAssignmentCandidateRemove = (candidate: Pick<AssignmentCandidate, "candidateId" | "modifiedAt">) =>
  command<{ candidateId: string }>(
    `${root}/candidates/${encodeURIComponent(candidate.candidateId)}/remove`,
    "POST",
    candidate.modifiedAt ? { modifiedAt: candidate.modifiedAt } : undefined,
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
  downloadFile(`${root}/excel?${query(filter)}`, `투입 배정_${filter.year}년.xlsx`);
/** 로그인을 붙이기 전(고정 사용자) 투입 배정 창이 쓰는 역할. 투입 배정(UI-12)·AI 인력 추천(UI-30)이 같이 쓴다. */
export const assignmentDevelopmentRoles: readonly string[] = ["FIN", "SALES_LDR"];
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
/** 시안 MM 표기(mmf): 소수 둘째 자리. */
export const mmf = (value: number | null | undefined) =>
  value == null || Number.isNaN(Number(value)) ? "–" : Number(value).toFixed(2);
/** 시안 연장 가능성 글자와 배지 색(높음 초록·보통 노랑·낮음 빨강). 코드 이름의 「(80%)」는 쓰지 않는다. */
export const EXT_LABEL: Record<string, string> = { H: "높음", M: "보통", L: "낮음" };
export const EXT_KIND: Record<string, "ok" | "warn" | "bad"> = { H: "ok", M: "warn", L: "bad" };
/** 시안 변경 종류 글자(띄어쓰기 포함). */
export const CHANGE_LABEL: Record<string, string> = {
  INPUT: "투입",
  EXT: "연장",
  EARLY: "조기 철수",
  UPRC: "단가 변경",
  RATE: "월 MM 변경",
  EXTP: "연장 가능성 변경",
  KIND: "투입 구분 변경",
  CRCT: "정정",
  CNCL: "취소",
};
/** 시안 변경 이력 칩 순서. */
export const HISTORY_CHIPS = ["INPUT", "EXT", "EARLY", "UPRC", "RATE", "EXTP", "KIND", "CRCT", "CNCL"];
/** 시안 [변경 기록] 종류 순서. */
export const CHANGE_TYPES = ["EXT", "EARLY", "UPRC", "RATE", "EXTP", "KIND"];
/** 시안 투입 구분 순서와 글자. */
export const KIND_CODES = ["NEW", "ADD", "SUB", "FREE"];
export const KIND_LABEL: Record<string, string> = { NEW: "신규", ADD: "추가투입", SUB: "대체투입", FREE: "무상지원" };
/** 날짜(YYYY-MM-DD)에 n일 더하기. 한국 달력 날짜라 시각대 변환이 없다. */
export function addDays(day: string, n: number) {
  const [y, m, d] = day.split("-").map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + n));
  const two = (v: number) => String(v).padStart(2, "0");
  return `${t.getUTCFullYear()}-${two(t.getUTCMonth() + 1)}-${two(t.getUTCDate())}`;
}

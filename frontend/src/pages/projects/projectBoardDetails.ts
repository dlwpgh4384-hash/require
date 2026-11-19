import { projectRequest } from "./projects";

export interface BoardMemo {
  memoId: string;
  projectId: string;
  workDone: string | null;
  nextWork: string | null;
  requests: string | null;
  actualProgress: number | null;
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
  /** 작성일(한국 날짜, 고쳐도 그대로). */
  writtenDate?: string;
  /** 동시 수정 확인 값: 수정 때 그대로 보낸다. */
  modifiedAt?: string;
}
export interface BoardStep {
  stepId: string;
  projectId: string;
  stepName: string;
  weight: number;
  plannedStartDate: string;
  plannedEndDate: string;
  completedDate: string | null;
}
export type MemoInput = Pick<
  BoardMemo,
  "workDone" | "nextWork" | "requests" | "actualProgress"
>;
export type StepDate = Pick<
  BoardStep,
  "stepId" | "plannedStartDate" | "plannedEndDate"
>;
const request = <T>(
  id: string,
  suffix: string,
  method = "GET",
  body?: unknown,
) =>
  projectRequest<T>(`/api/common/projects/${encodeURIComponent(id)}${suffix}`, {
    method,
    ...(body === undefined
      ? {}
      : {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
  });
export const getCommonProjectBoardWeeklyMemos = (id: string) =>
  request<{ items: BoardMemo[] }>(id, "/weekly-memos");
export const postCommonProjectBoardWeeklyMemo = (id: string, body: MemoInput) =>
  request<BoardMemo>(id, "/weekly-memos", "POST", body);
export const putCommonProjectBoardWeeklyMemo = (
  id: string,
  memoId: string,
  body: MemoInput,
  modifiedAt?: string | null,
) =>
  request<BoardMemo>(
    id,
    `/weekly-memos/${encodeURIComponent(memoId)}`,
    "PUT",
    { ...body, modifiedAt: modifiedAt ?? null },
  );
export const getCommonProjectBoardSteps = (id: string) =>
  request<{ items: BoardStep[]; modifiedAt?: string | null }>(id, "/steps");
export const postCommonProjectBoardStepsEnsure = (id: string) =>
  request<{ items: BoardStep[] }>(id, "/steps/ensure", "POST");
/** 단계 계획 저장: 목록 조회 때 받은 modifiedAt을 함께 보낸다(그 사이 바뀌었으면 409). */
export const putCommonProjectBoardSteps = (
  id: string,
  items: StepDate[],
  modifiedAt?: string | null,
) =>
  request<{ items: BoardStep[] }>(id, "/steps", "PUT", {
    items,
    modifiedAt: modifiedAt ?? null,
  });

export const postCommonProjectBoardStepComplete = (
  id: string,
  stepId: string,
) =>
  request<BoardStep>(
    id,
    `/steps/${encodeURIComponent(stepId)}/complete`,
    "POST",
  );

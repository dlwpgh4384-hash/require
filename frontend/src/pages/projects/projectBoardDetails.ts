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
) =>
  request<BoardMemo>(
    id,
    `/weekly-memos/${encodeURIComponent(memoId)}`,
    "PUT",
    body,
  );
export const getCommonProjectBoardSteps = (id: string) =>
  request<{ items: BoardStep[] }>(id, "/steps");
export const postCommonProjectBoardStepsEnsure = (id: string) =>
  request<{ items: BoardStep[] }>(id, "/steps/ensure", "POST");
export const putCommonProjectBoardSteps = (id: string, items: StepDate[]) =>
  request<{ items: BoardStep[] }>(id, "/steps", "PUT", { items });
export const postCommonProjectBoardStepComplete = (
  id: string,
  stepId: string,
) =>
  request<BoardStep>(
    id,
    `/steps/${encodeURIComponent(stepId)}/complete`,
    "POST",
  );

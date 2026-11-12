import { useEffect, useState } from "react";

export interface CommonUserOptionsResult {
  roles: { roleCd: string; roleName: string; assignedProjectScope: boolean }[];
  projects: { projectId: string; projectName: string }[];
  userStates: { code: string; name: string }[];
}

export const userOptionsError =
  "선택 목록을 불러오지 못했습니다. 다시 시도해 주세요.";

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function options(value: unknown): value is CommonUserOptionsResult {
  return (
    object(value) &&
    Array.isArray(value.roles) &&
    value.roles.every(
      (role: unknown) =>
        object(role) &&
        typeof role.roleCd === "string" &&
        role.roleCd.length > 0 &&
        typeof role.roleName === "string" &&
        typeof role.assignedProjectScope === "boolean",
    ) &&
    Array.isArray(value.projects) &&
    value.projects.every(
      (project: unknown) =>
        object(project) &&
        typeof project.projectId === "string" &&
        typeof project.projectName === "string",
    ) &&
    Array.isArray(value.userStates) &&
    value.userStates.every(
      (state: unknown) =>
        object(state) &&
        typeof state.code === "string" &&
        state.code.length > 0 &&
        typeof state.name === "string",
    )
  );
}

export async function getCommonUserOptions(
  signal?: AbortSignal,
): Promise<CommonUserOptionsResult> {
  try {
    const response = await fetch("/api/common/user-options", { signal });
    const body: unknown = await response.json();
    if (
      !response.ok ||
      !object(body) ||
      body.success !== true ||
      !options(body.data)
    ) {
      throw new Error(userOptionsError);
    }
    return body.data;
  } catch {
    throw new Error(userOptionsError);
  }
}

export type UserOptionsState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; data: CommonUserOptionsResult };

export function useCommonUserOptions() {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<UserOptionsState>({ status: "loading" });
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: "loading" });
    void getCommonUserOptions(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setState({ status: "ready", data });
      },
      () => {
        if (!controller.signal.aborted) setState({ status: "error" });
      },
    );
    return () => controller.abort();
  }, [attempt]);
  return { state, reload: () => setAttempt((value) => value + 1) };
}

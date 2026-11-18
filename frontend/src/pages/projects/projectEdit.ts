import { useCallback } from "react";
import { useInRouterContext, useNavigate } from "react-router-dom";
import type { Project } from "./projects";

/**
 * 시안 editform: 어느 화면에서 연 프로젝트 상세든 [수정]을 누르면 상세를 닫고
 * 프로젝트(UI-01) 화면의 수정 폼을 연다(2026-11-19 사용자 결정, 시안대로).
 * 프로젝트 화면이 아직 안 열려 있으면 열릴 때, 이미 열려 있으면 바로 요청을 받는다.
 */
type Listener = (project: Project) => void;
let pending: Project | null = null;
const listeners = new Set<Listener>();

export function requestProjectEdit(project: Project) {
  if (listeners.size === 0) {
    pending = project;
    return;
  }
  pending = null;
  listeners.forEach((listener) => listener(project));
}

/** 프로젝트 화면이 쓴다: 남아 있는 요청을 먼저 받고, 이후 요청을 기다린다. */
export function subscribeProjectEdit(listener: Listener) {
  listeners.add(listener);
  if (pending) {
    const project = pending;
    pending = null;
    listener(project);
  }
  return () => {
    listeners.delete(listener);
  };
}

/** 다른 화면이 쓴다: 수정 요청을 남기고 프로젝트 화면 탭으로 간다. */
export function useOpenProjectEdit() {
  const inRouter = useInRouterContext();
  // 라우터 밖(단위 시험)에서는 이동 없이 요청만 남긴다. 한 화면 안에서 라우터 유무는 바뀌지 않는다.
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const navigate = inRouter ? useNavigate() : null;
  return useCallback(
    (project: Project) => {
      requestProjectEdit(project);
      navigate?.("/screens/UI-01");
    },
    [navigate],
  );
}

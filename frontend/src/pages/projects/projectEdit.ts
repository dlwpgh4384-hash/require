import { createScreenRequest } from "../../shared/layout/screenRequest";
import type { Project } from "./projects";

/**
 * 시안 editform: 어느 화면에서 연 프로젝트 상세든 [수정]을 누르면 상세를 닫고
 * 프로젝트(UI-01) 화면의 수정 폼을 연다(2026-11-19 사용자 결정, 시안대로).
 * 프로젝트 화면이 아직 안 열려 있으면 열릴 때, 이미 열려 있으면 바로 요청을 받는다.
 */
const edit = createScreenRequest<Project>("UI-01");

export const requestProjectEdit = edit.request;
/** 프로젝트 화면이 쓴다: 남아 있는 요청을 먼저 받고, 이후 요청을 기다린다. */
export const subscribeProjectEdit = edit.subscribe;
/** 다른 화면이 쓴다: 수정 요청을 남기고 프로젝트 화면 탭으로 간다. */
export const useOpenProjectEdit = edit.useOpen;

/**
 * 시안 distopen: 어느 화면에서 연 프로젝트 상세든 턴키 [배분표]를 누르면 상세를 닫고
 * 프로젝트(UI-01) 화면의 배분표 화면을 연다. 값은 프로젝트 ID.
 */
const distribution = createScreenRequest<string>("UI-01");

export const requestProjectDistribution = distribution.request;
/** 프로젝트 화면이 쓴다. */
export const subscribeProjectDistribution = distribution.subscribe;
/** 다른 화면이 쓴다: 배분표 요청을 남기고 프로젝트 화면 탭으로 간다. */
export const useOpenProjectDistribution = distribution.useOpen;

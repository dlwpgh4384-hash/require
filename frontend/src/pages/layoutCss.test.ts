import { createElement as h } from "react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { MemoryRouter } from "react-router-dom";
import { AssignmentForm } from "./assignments/AssignmentForm";
import { ProjectBoardPage } from "./projectboard/ProjectBoardPage";
import { ProjectField } from "./projects/ProjectField";
import { Banner } from "../shared/list/DetailPanel";
import { Dialog } from "../shared/feedback/Dialog";
import "../shared/feedback/feedback.css?render-test";
import "./projectboard/projectBoard.css?render-test";
import "./assignments/assignments.css?render-test";
import "./projects/projects.css?render-test";
import "../shared/list/list.css?render-test";

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => ({
      ok: true,
      status: 200,
      json: async () => ({
        success: true,
        meta: {},
        data: url.startsWith("/api/common/project-board")
          ? {
              summary: {
                progress: { total: 0, normal: 0, caution: 0, danger: 0 },
                riskCount: 0,
                openIssues: { total: 0, high: 0 },
                overdueIssueCount: 0,
                staleProjectCount: 0,
              },
              items: [],
              staleProjects: [],
              calculationMethod: "",
            }
          : { items: [] },
      }),
    })),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("현황판 탭 줄: 시안 .content 간격 24에 맞춰 위에 8px를 더한다", async () => {
  render(h(MemoryRouter, null, h(ProjectBoardPage)));
  const tabs = screen.getByRole("tablist", { name: "현황판 보기" });
  expect(getComputedStyle(tabs).marginTop).toBe("8px");
  await waitFor(() => expect(fetch).toHaveBeenCalled());
});

it("배정 창: AI 추천 단추는 다른 칸처럼 폭 가득, 설명 글은 바깥 여백 없음", async () => {
  render(
    h(
      MemoryRouter,
      null,
      h(AssignmentForm, {
        projects: [],
        roles: [],
        onClose: vi.fn(),
        onSaved: vi.fn(),
      }),
    ),
  );
  const button = screen.getByTestId("assignment-ai-recommend");
  expect(button).toHaveTextContent("이 자리에 맞는 사람 AI 추천 받기");
  expect(getComputedStyle(button).alignSelf).not.toBe("flex-start");
  const descriptions = screen
    .getByTestId("assignment-form")
    .querySelectorAll(".assignment-form > p");
  expect(descriptions.length).toBeGreaterThan(0);
  descriptions.forEach((text) =>
    expect(getComputedStyle(text).margin).toBe("0px"),
  );
  await waitFor(() =>
    expect(screen.getByTestId("assignment-form-save")).not.toBeDisabled(),
  );
});

it("시안 창 칸 이름은 공통 창 label 여백을 끈다", () => {
  render(
    h(Dialog, {
      title: "프로젝트 입력",
      onClose: vi.fn(),
      children: h(ProjectField, {
        id: "project-name",
        label: "프로젝트명",
        children: h("input", { id: "project-name" }),
      }),
    }),
  );
  const input = screen.getByRole("textbox", { name: "프로젝트명" });
  const label = screen.getByText("프로젝트명");
  expect(input).toHaveFocus();
  expect(getComputedStyle(label).margin).toBe("0px");
});

it("프로젝트 화면에서도 공통 Banner가 아이콘을 맞춘다", () => {
  render(
    h(
      "div",
      { className: "project-page" },
      h(Banner, {
        kind: "warn",
        icon: h("svg", { "data-testid": "project-banner-icon" }),
        children: "상태 확인 필요",
      }),
    ),
  );
  expect(screen.getByRole("status")).toHaveTextContent("상태 확인 필요");
  expect(
    getComputedStyle(screen.getByTestId("project-banner-icon")).flexShrink,
  ).toBe("0");
});

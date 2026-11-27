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
import "./candidates/candidates.css?render-test";
import "./salespipeline/salesOpportunities.css?render-test";
import "./manpowerboard/manpowerBoard.css?render-test";
import "./userpreferences/userPreferences.css?render-test";
import "./operatingstandards/operatingStandards.css?render-test";
import "../shared/layout/shell.css?render-test";
import { PlaceholderScreen } from "./common/PlaceholderScreen";
import { NavigationState } from "../shared/layout/NavigationState";
import { ShortcutHelpDialog } from "./userpreferences/UserPreferenceTools";

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

it("후보 메모 칸: 시안 textarea.in(높이 자동·최소 76·줄 높이 20)으로 .ls-in 높이 36을 푼다", () => {
  render(
    h(
      "form",
      { className: "candidate-contact-form" },
      h("textarea", { className: "ls-in", "aria-label": "메모" }),
    ),
  );
  const style = getComputedStyle(screen.getByRole("textbox", { name: "메모" }));
  expect(style.height).toBe("auto");
  expect(style.minHeight).toBe("76px");
  expect(style.lineHeight).toBe("20px");
  expect(style.resize).toBe("vertical");
});

it("영업 필요 인력 표: 칸 위 맞춤 없음(시안 가운데), 고르기 칸 오른쪽 4px", () => {
  render(
    h(
      "table",
      { className: "so-needs" },
      h(
        "tbody",
        null,
        h(
          "tr",
          null,
          h(
            "td",
            null,
            h("select", { className: "so-in", "aria-label": "필요 인력" }),
          ),
        ),
      ),
    ),
  );
  expect(getComputedStyle(screen.getByRole("cell")).verticalAlign).not.toBe(
    "top",
  );
  expect(getComputedStyle(screen.getByRole("combobox")).paddingRight).toBe(
    "4px",
  );
});

it("서랍 안 요약 칸은 시안 .db .kpis처럼 늘 두 칸, 사이 8px", () => {
  render(
    h(
      "div",
      { className: "ls-db" },
      h("section", { className: "ls-kpis", "aria-label": "요약" }),
    ),
  );
  const style = getComputedStyle(screen.getByRole("region", { name: "요약" }));
  expect(style.gridTemplateColumns).toBe("repeat(2, minmax(0, 1fr))");
  expect(style.gap).toBe("8px");
});

it("인력 운영 보드 탭 줄: 시안 .content>.tabs(좌우 여백 0, 아래 -8px)", () => {
  render(
    h(
      "div",
      { className: "manpower-board-page" },
      h("div", { className: "ls-tabs", role: "tablist" }),
    ),
  );
  const style = getComputedStyle(screen.getByRole("tablist"));
  expect(style.padding).toBe("0px");
  expect(style.marginBottom).toBe("-8px");
});

it("왼쪽 즐겨찾기·최근 본 화면: 시안 .nq-t·.nq-empty 간격", () => {
  render(
    h(
      "section",
      { className: "preference-personal-lists" },
      h("h2", null, "즐겨찾기"),
      h("p", null, "최근 본 화면 없음"),
      h("ul"),
    ),
  );
  expect(getComputedStyle(screen.getByRole("heading")).padding).toBe(
    "8px 12px 4px",
  );
  expect(getComputedStyle(screen.getByText("최근 본 화면 없음")).padding).toBe(
    "2px 12px 4px",
  );
  expect(getComputedStyle(screen.getByRole("list")).margin).toBe("0px");
});

it("운영 기준값 저장 창: 시안 .mb·.mf 간격", () => {
  render(
    h(
      "div",
      { className: "code-page" },
      h(
        "div",
        { className: "app-dialog", "data-testid": "operating-save-dialog" },
        h("section", {
          className: "operating-save-body",
          "aria-label": "저장 내용",
        }),
        h("footer", {
          className: "app-actions",
          "data-testid": "save-actions",
        }),
      ),
    ),
  );
  const body = getComputedStyle(
    screen.getByRole("region", { name: "저장 내용" }),
  );
  expect(body.gap).toBe("12px");
  expect(body.padding).toBe("4px 24px 16px");
  expect(getComputedStyle(screen.getByTestId("save-actions")).padding).toBe(
    "12px 24px 20px",
  );
});

// 화면 틀 카드(.shell-state 흰 바탕·테두리): 실제 화면은 회색 바탕 위에, 준비 중·안내만 카드로.
function shellFrame(...panels: ReturnType<typeof h>[]) {
  render(h("div", { className: "shell-state", "data-testid": "frame" }, ...panels));
  return getComputedStyle(screen.getByTestId("frame"));
}
const panel = (hidden: boolean, ...children: ReturnType<typeof h>[]) =>
  h("div", { className: "screen-tab-panel", hidden }, ...children);
const CLEAR = /^(transparent|rgba\(0, 0, 0, 0\))$/;
const gate = (child: ReturnType<typeof h>) =>
  h("div", { className: "screen-permission", style: { display: "contents" } }, child);

it("화면 틀: 등록·수정·상세 폼처럼 뿌리에 screen-body가 없어도 실제 화면이면 카드 없음", () => {
  // 예: 청구 등록(BillForm)·후보 상세·거래처 폼 — 권한 틀 안 폼 뿌리
  const style = shellFrame(
    panel(true, gate(h("section", { className: "screen-body" }))),
    panel(false, gate(h("form", { className: "bill-form" }))),
  );
  expect(style.borderRadius).toMatch(/^0(px)?$/);
  expect(style.backgroundColor).toMatch(CLEAR);
});

it("화면 틀: data-screen-id가 있는 화면 뿌리도 카드 없음(권한 틀 없이 그려도)", () => {
  const style = shellFrame(
    panel(false, h("div", { "data-screen-id": "UI-03" })),
  );
  expect(style.borderRadius).toMatch(/^0(px)?$/);
});

it("화면 틀: 준비 중 화면·메뉴 없음 안내는 카드를 그대로 둔다", () => {
  const ready = shellFrame(
    panel(true, gate(h("section", { className: "screen-body" }))),
    panel(false, h(PlaceholderScreen, { scrnId: "UI-99" })),
  );
  expect(screen.getByTestId("navigation-state-ready")).toBeInTheDocument();
  expect(ready.borderRadius).toBe("8px");
  cleanup();
  const empty = shellFrame(h(NavigationState, { kind: "no-screen" }));
  expect(empty.borderRadius).toBe("8px");
});

it("단축키 창: 시안 keys(.modal 400·머리 16/16/8/24·본문 4/24/16·표 13px 40줄·단추 줄 12/24/20)", () => {
  render(h(ShortcutHelpDialog, { onClose: vi.fn() }));
  const dialog = screen.getByRole("dialog", { name: "단축키" });
  const style = getComputedStyle(dialog);
  expect(style.width).toBe("min(400px, 100%)");
  expect(style.padding).toBe("0px");
  expect(
    getComputedStyle(dialog.querySelector(".app-dialog-head")!).padding,
  ).toBe("16px 16px 8px 24px");
  expect(
    getComputedStyle(dialog.querySelector(".shortcut-help-body")!).padding,
  ).toBe("4px 24px 16px");
  const wrap = getComputedStyle(dialog.querySelector(".shortcut-help-wrap")!);
  expect(wrap.overflow).toBe("auto");
  expect(wrap.borderRadius).toBe("8px");
  expect(getComputedStyle(dialog.querySelector("table")!).fontSize).toBe("13px");
  const cell = getComputedStyle(dialog.querySelector("td")!);
  expect(cell.height).toBe("40px");
  expect(cell.padding).toBe("0px 12px");
  expect(
    getComputedStyle(dialog.querySelector(".shortcut-help-actions")!).padding,
  ).toBe("12px 24px 20px");
});

// 화면 시안(v1.8) 간격·크기를 CSS 글로 확인한다(jsdom은 스타일을 계산하지 않음).
import { expect, it } from "vitest";
// 시험 도구(vitest)는 CSS를 빈 글로 바꾸므로 파일을 직접 읽는다(node:fs, 타입 없이 동적 import).
const readText = async (path: string): Promise<string> => {
  const fs: { readFileSync(file: URL, encoding: "utf8"): string } = await import(
    "node:" + "fs"
  );
  return fs.readFileSync(new URL(path, import.meta.url), "utf8");
};
const projectBoardCss = await readText("./projectboard/projectBoard.css");
const assignmentsCss = await readText("./assignments/assignments.css");
const projectsCss = await readText("./projects/projects.css");
const candidatesCss = await readText("./candidates/candidates.css");
const salesCss = await readText("./salespipeline/salesOpportunities.css");
const listCss = await readText("../shared/list/list.css");
const boardCss = await readText("./manpowerboard/manpowerBoard.css");
const preferencesCss = await readText("./userpreferences/userPreferences.css");
const operatingCss = await readText(
  "./operatingstandards/operatingStandards.css",
);

it("후보 메모 칸: 시안 textarea.in(높이 자동·최소 76·줄 높이 20)으로 .ls-in 높이 36을 푼다", () => {
  const css = rule(candidatesCss, ".candidate-contact-form textarea");
  expect(css).toContain("height: auto");
  expect(css).toContain("min-height: 76px");
  expect(css).toContain("line-height: 20px");
  expect(css).toContain("resize: vertical");
});

it("영업 필요 인력 표: 칸 위 맞춤 없음(시안 가운데), 고르기 칸 오른쪽 4px", () => {
  expect(salesCss).not.toContain(".so-needs td");
  expect(rule(salesCss, ".so-needs select.so-in")).toContain(
    "padding-right: 4px",
  );
});

it("서랍 안 요약 칸은 시안 .db .kpis처럼 늘 두 칸, 사이 8px", () => {
  const css = rule(listCss, ".ls-db .ls-kpis");
  expect(css).toContain("grid-template-columns: repeat(2, minmax(0, 1fr))");
  expect(css).toContain("gap: 8px");
});

it("인력 운영 보드 탭 줄: 시안 .content>.tabs(좌우 여백 0, 아래 -8px)", () => {
  const css = rule(boardCss, ".manpower-board-page .ls-tabs");
  expect(css).toContain("padding: 0");
  expect(css).toContain("margin-bottom: -8px");
});

it("왼쪽 즐겨찾기·최근 본 화면: 시안 .nq-t·.nq-empty 간격", () => {
  expect(rule(preferencesCss, ".preference-personal-lists h2")).toContain(
    "padding: 8px 12px 4px",
  );
  expect(rule(preferencesCss, ".preference-personal-lists p")).toContain(
    "padding: 2px 12px 4px",
  );
  expect(rule(preferencesCss, ".preference-personal-lists ul")).toContain(
    "margin: 0",
  );
});

it("운영 기준값 저장 창: 시안 .mb·.mf 간격", () => {
  const body = rule(operatingCss, ".operating-save-body");
  expect(body).toContain("gap: 12px");
  expect(body).toContain("padding: 4px 24px 16px");
  expect(
    rule(
      operatingCss,
      '.code-page .app-dialog[data-testid="operating-save-dialog"] > .app-actions',
    ),
  ).toContain("padding: 12px 24px 20px");
});

const rule = (css: string, selector: string) => {
  const at = css.indexOf(`${selector} {`);
  expect(at, selector).toBeGreaterThanOrEqual(0);
  return css.slice(at, css.indexOf("}", at));
};

it("현황판 탭 줄: 시안 .content 간격 24에 맞춰 위에 8px를 더한다", () => {
  expect(rule(projectBoardCss, ".project-board-tabs")).toContain(
    "margin-top: 8px",
  );
});

it("배정 창: AI 추천 단추는 다른 칸처럼 폭 가득, 설명 글은 바깥 여백 없음(시안 p{margin:0})", () => {
  expect(rule(assignmentsCss, ".assignment-ai")).not.toContain("align-self");
  expect(rule(assignmentsCss, ".assignment-form > p")).toContain("margin: 0");
});

it("시안 창 칸 이름(.fc .fl)은 공통 창 label 여백을 끈다", () => {
  expect(rule(projectsCss, ".app-dialog .project-fc label")).toContain(
    "margin: 0",
  );
});

it("화면별로 띠 아이콘을 맞추던 규칙은 없다(공통 Banner icon이 맡음)", () => {
  expect(projectsCss).not.toContain(".project-page .ls-banner svg");
});

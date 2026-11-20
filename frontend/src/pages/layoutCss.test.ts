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

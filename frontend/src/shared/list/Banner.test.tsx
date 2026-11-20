import { afterEach, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { Banner } from "./DetailPanel";
// 시험 도구(vitest)는 CSS를 빈 글로 바꾸므로 파일을 직접 읽는다(node:fs, 타입 없이 동적 import).
const readText = async (path: string): Promise<string> => {
  const fs: { readFileSync(file: URL, encoding: "utf8"): string } = await import(
    "node:" + "fs"
  );
  return fs.readFileSync(new URL(path, import.meta.url), "utf8");
};
const listCss = await readText("./list.css");
const feedbackCss = await readText("../feedback/feedback.css");

afterEach(cleanup);

// 시안 .banner: 아이콘은 글(span) 앞에 따로 두어 아이콘과 글이 어긋나지 않게 한다.
it("Banner icon: 아이콘은 글 span 밖, 앞에 놓는다", () => {
  render(
    <Banner kind="warn" icon={<svg data-testid="icon" />}>
      2주 넘게 상태를 적지 않은 프로젝트
    </Banner>,
  );
  const band = screen.getByTestId("banner-warn");
  const [first, second] = [...band.children];
  expect(first).toBe(screen.getByTestId("icon"));
  expect(second.tagName).toBe("SPAN");
  expect(second).toHaveTextContent("2주 넘게 상태를 적지 않은 프로젝트");
  expect(second.querySelector("svg")).toBeNull();
});

it("Banner: icon을 주지 않으면 예전과 같이 글 span 하나와 action", () => {
  render(
    <Banner kind="info" action={<button type="button">다시 시도</button>}>
      안내
    </Banner>,
  );
  const band = screen.getByTestId("banner-info");
  expect([...band.children].map((el) => el.tagName)).toEqual([
    "SPAN",
    "BUTTON",
  ]);
});

it("띠 아이콘은 줄어들지 않는다(list.css)", () => {
  expect(listCss).toMatch(/\.ls-banner > svg[\s\S]*?flex: none/);
});

it("창 안 작은 글 회색 규칙이 칸 오류(.field-error) 빨강을 덮지 않는다(feedback.css)", () => {
  expect(feedbackCss).toContain(".app-dialog small:not(.field-error)");
  expect(feedbackCss).not.toMatch(/\.app-dialog small\s*\{/);
});

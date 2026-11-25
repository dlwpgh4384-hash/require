import { afterEach, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { Banner } from "./DetailPanel";
import { Dialog } from "../feedback/Dialog";
import "./list.css?render-test";
import "../feedback/feedback.css?render-test";

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

it("띠 아이콘은 줄어들지 않는다", () => {
  render(
    <Banner kind="warn" icon={<svg data-testid="icon" />}>
      안내
    </Banner>,
  );
  expect(getComputedStyle(screen.getByTestId("icon")).flexShrink).toBe("0");
});

it("창 안 작은 글 회색 규칙이 칸 오류 빨강을 덮지 않는다", () => {
  // 색 값을 시험에 적지 않는다: 창 밖 공통 칸 오류(.field-error) 색과 같고, 도움말은 공통 회색 변수.
  render(
    <>
      <small className="field-error" data-testid="outside-error">
        바깥 오류
      </small>
      <Dialog title="입력 확인" onClose={() => undefined}>
        <small>도움말</small>
        <small className="field-error" role="alert">
          필수 입력입니다
        </small>
      </Dialog>
    </>,
  );
  const shared = getComputedStyle(screen.getByTestId("outside-error")).color;
  expect(shared).not.toBe("");
  expect(screen.getByRole("alert")).toHaveTextContent("필수 입력입니다");
  expect(getComputedStyle(screen.getByRole("alert")).color).toBe(shared);
  expect(getComputedStyle(screen.getByText("도움말")).color).toBe(
    "var(--text-sub)",
  );
});

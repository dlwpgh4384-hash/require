import { afterEach, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { Dialog } from "../../shared/feedback/Dialog";
import "../../shared/feedback/feedback.css?render-test";
import "./developers.css?render-test";

afterEach(cleanup);

// 개인 원가·수익표(UI-03) 상세에서 연 [상태 등록] 창: .developer-workspace 밖이라도 fieldset 테두리가 없어야 한다(개발자 풀과 같음).
it("status form fieldset has no border outside the developer pool workspace", () => {
  render(
    <div className="personal-cost-profit-page">
      <Dialog title="근무 상태 등록" onClose={() => undefined} closable>
        <form className="developer-status-form">
          <fieldset data-testid="status-fieldset">
            <label htmlFor="x">시작일</label>
            <input id="x" />
          </fieldset>
        </form>
      </Dialog>
    </div>,
  );
  const style = getComputedStyle(screen.getByTestId("status-fieldset"));
  expect(style.borderTopWidth).toBe("0px");
  expect(style.paddingTop).toBe("0px");
  expect(style.display).toBe("flex");
});

it("every shared dialog fieldset drops the browser default border", () => {
  render(
    <Dialog title="아무 창" onClose={() => undefined}>
      <form>
        <fieldset data-testid="any-fieldset">
          <input aria-label="칸" />
        </fieldset>
      </form>
    </Dialog>,
  );
  const style = getComputedStyle(screen.getByTestId("any-fieldset"));
  expect(style.borderTopWidth).toBe("0px");
  expect(style.marginTop).toBe("0px");
  expect(style.paddingTop).toBe("0px");
});

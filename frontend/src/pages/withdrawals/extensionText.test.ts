import { expect, it } from "vitest";
import { extensionText } from "./withdrawals";

// 시안 UI-05: 연장 가능성은 「높음·보통·낮음」만 보인다. 공통코드 이름의 「 (80%)」는 화면에서 뺀다.
it("공통코드 이름에서 확률 표기만 뺀다", () => {
  expect(extensionText("높음 (80%)")).toBe("높음");
  expect(extensionText("보통 (50%)")).toBe("보통");
  expect(extensionText("낮음 (0%)")).toBe("낮음");
  expect(extensionText("높음")).toBe("높음");
  expect(extensionText(null)).toBe("");
});

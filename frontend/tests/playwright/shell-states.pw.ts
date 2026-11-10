import { test, expect } from "@playwright/test";

// Browser-only layout/fault injection checks, separate from jsdom component tests.
for (const width of [320, 390, 768, 1280]) {
  test(`shell routes and retry remain usable when the menu API fails at ${width}px`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    let requests = 0;
    await page.route("**/api/common/menus", (route) => {
      requests += 1;
      return route.abort("connectionfailed");
    });
    for (const path of [
      "/",
      "/screens/UI-01",
      "/screens/unregistered-preview",
    ]) {
      const response = await page.goto(path);
      expect(response?.status()).toBe(200);
      await expect(
        page
          .getByRole("main")
          .getByRole("heading", { name: "메뉴를 확인한 뒤 화면을 표시합니다" }),
      ).toBeVisible();
      if (width < 768)
        await page.getByRole("button", { name: "메뉴 펼치기" }).click();
      const retry = page.getByRole("button", { name: "다시 시도" });
      await expect(retry).toBeVisible();
      const before = requests;
      await retry.click();
      await expect(retry).toBeVisible();
      expect(requests).toBeGreaterThan(before);
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= window.innerWidth,
        ),
      ).toBe(true);
      await expect(page.getByRole("banner")).toHaveCount(1);
      await expect(page.getByRole("main")).toHaveCount(1);
      await expect(page.getByRole("navigation")).toHaveCount(1);
      for (const label of [
        "즐겨찾기",
        "최근 본 화면",
        "화면 탭",
        "상단 도구줄",
        "단축키",
        "알림",
      ]) {
        await expect(
          page.getByRole("button", { name: `${label}: 준비 중` }),
        ).toBeDisabled();
      }
    }
    expect(errors).toEqual([]);
    await page.screenshot({
      path: testInfo.outputPath(`shell-error-${width}.png`),
      fullPage: true,
    });
  });
}

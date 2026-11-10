import { test, expect } from "@playwright/test";
import type { MenuResponse } from "../../src/services/menus";
import { firstScreen, screenPath, visibleTree } from "../../src/menuTree";

// Opt-in real API/DB suite; never collected by Vitest.
test("real menu API, DB first-screen navigation, direct entry, reload and persistence boundary", async ({
  page,
  request,
}) => {
  const result = await request.get("/api/common/menus");
  expect(result.status()).toBe(200);
  const payload = (await result.json()) as MenuResponse;
  expect(payload.success).toBe(true);
  const first = firstScreen(
    visibleTree(payload.data.data, payload.meta.parentMenuIds, false),
  );
  expect(first?.scrnId).toBeTruthy();
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: first!.menuNm, exact: true }),
  ).toBeVisible();
  await page.goto(screenPath(first!.scrnId!));
  await page.reload();
  await expect(
    page.getByRole("main").getByText("준비 중", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("navigation")).toHaveCount(1);
  await expect(page.getByRole("banner")).toHaveCount(1);
  expect(
    await page.evaluate(() => ({
      local: localStorage.length,
      session: sessionStorage.length,
    })),
  ).toEqual({ local: 0, session: 0 });
  expect(await (await request.get("/api/common/menus")).json()).toEqual(
    payload,
  );
});

test("unknown address does not automatically redirect and first-screen button uses DB data", async ({
  page,
  request,
}) => {
  const payload = (await (
    await request.get("/api/common/menus")
  ).json()) as MenuResponse;
  const first = firstScreen(
    visibleTree(payload.data.data, payload.meta.parentMenuIds, false),
  );
  await page.goto(`/screens/unregistered-${crypto.randomUUID()}`);
  await expect(
    page.getByText("없는 화면입니다", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "첫 화면으로" }).click();
  await expect(
    page.getByRole("heading", { name: first!.menuNm, exact: true }),
  ).toBeVisible();
});

test("real PC-only selection is restricted on narrow viewport and restored on desktop", async ({
  page,
  request,
}) => {
  const payload = (await (
    await request.get("/api/common/menus")
  ).json()) as MenuResponse;
  const pc = payload.data.data.find(
    (item) =>
      item.pcOnlyYn === "Y" &&
      item.scrnId &&
      !payload.meta.parentMenuIds.includes(item.menuId),
  );
  expect(pc).toBeDefined();
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(screenPath(pc!.scrnId!));
  await expect(
    page.getByRole("main").getByText("준비 중", { exact: true }),
  ).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByText("PC 전용 화면입니다")).toBeVisible();
  await page.getByRole("button", { name: "메뉴 펼치기" }).click();
  await expect(
    page
      .getByRole("navigation")
      .getByRole("link", { name: pc!.menuNm, exact: true }),
  ).toHaveCount(0);
  await page.reload();
  await expect(page.getByText("PC 전용 화면입니다")).toBeVisible();
  await page.setViewportSize({ width: 1280, height: 900 });
  await expect(
    page.getByRole("heading", { name: pc!.menuNm, exact: true }),
  ).toBeVisible();
});

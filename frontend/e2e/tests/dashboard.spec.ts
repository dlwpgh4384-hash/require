import { test, expect } from "@playwright/test";

test("UI-16 uses live summary, one body request, responsive layout and real Excel download", async ({
  page,
}, testInfo) => {
  const requests: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/api/common/dashboard")
      requests.push(request.url());
  });
  const responsePromise = page.waitForResponse(
    (response) => new URL(response.url()).pathname === "/api/common/dashboard",
  );
  await page.goto("/screens/UI-16");
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const { data } = await response.json();
  await expect(page.getByTestId("dashboard-basis")).toContainText(
    data.selectedMonth,
  );
  await expect(page.getByTestId("dashboard-basis")).toContainText(data.today);
  await expect(page.getByTestId("dashboard-close-count")).toContainText(
    `${data.closing.completedCount}/${data.closing.totalCount}`,
  );
  expect(requests).toHaveLength(1);
  expect(data.manpower).toHaveLength(6);
  expect(data.cash).toHaveLength(3);
  // 수급 숫자 표는 닫힌 「표로 보기」 안에 있으니 열고 본다.
  const supply = page.getByTestId("dashboard-supply");
  await supply.locator("details summary").click();
  for (const month of data.manpower)
    await expect(
      page.getByTestId(`dashboard-manpower-${month.month}`),
    ).toBeVisible();
  await expect(
    page.getByRole("button", { name: /저장|업로드|삭제/ }),
  ).toHaveCount(0);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: testInfo.outputPath("dashboard.png"),
    fullPage: true,
  });
  const downloadResponse = page.waitForResponse(
    (r) => new URL(r.url()).pathname === "/api/common/dashboard/excel",
  );
  const downloadEvent = page.waitForEvent("download");
  await page.getByTestId("dashboard-excel").click();
  const [fileResponse, file] = await Promise.all([
    downloadResponse,
    downloadEvent,
  ]);
  expect(fileResponse.status()).toBe(200);
  expect(file.suggestedFilename()).toMatch(/^대시보드_.+\.xlsx$/);
  const count = fileResponse.headers()["x-total-count"];
  expect(count).toMatch(/^\d+$/);
  await expect(
    page.getByText(`엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`),
  ).toBeVisible();
  // 화면은 visible=true 줄만 그리고 번호도 그 줄들 안에서 센다.
  const visible = data.todos.filter(
    (t: { visible: boolean }) => t.visible,
  ) as { targetScreenId: string | null }[];
  const index = visible.findIndex((t) => t.targetScreenId);
  if (index >= 0) {
    await page.getByTestId(`dashboard-todo-${index}`).click();
    await expect(page).toHaveURL(
      new RegExp(`/screens/${visible[index].targetScreenId}$`),
    );
    await page.getByTestId("header-logo").click();
    await expect(page.getByTestId("dashboard-page")).toBeVisible();
  }
});

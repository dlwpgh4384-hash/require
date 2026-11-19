import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  BillingOptionsFields,
  useBillingOptions,
} from "./BillingOptionsFields";
import type { BillingOptions } from "./billingLookups";

const value = (over: Partial<BillingOptions> = {}): BillingOptions => ({
  projects: [
    {
      projectId: "chosen",
      projectName: "청구 대상",
      customerName: "조회 고객",
      projectType: "MO",
    },
  ],
  statusOptions: [{ code: "UNPAID", name: "미수" }],
  defaultBillMonth: "202611",
  defaultBillDate: "2026-11-15",
  defaultPayDueDate: "2026-12-25",
  estimatedSupplyAmount: null,
  vatRate: 10,
  monthClosed: false,
  ...over,
});
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data }),
});
let request: ReturnType<typeof vi.fn>;
beforeEach(() => {
  request = vi.fn().mockResolvedValue(ok(value()));
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
it("loads the approved options endpoint and shows customer names with selected project ids", async () => {
  const onProject = vi.fn();
  const onMonth = vi.fn();
  render(
    <BillingOptionsFields
      projectId=""
      billMonth="202611"
      onProject={onProject}
      onMonth={onMonth}
    />,
  );
  expect(
    await screen.findByRole("option", { name: "청구 대상 · 조회 고객" }),
  ).toHaveValue("chosen");
  expect(request.mock.calls[0][0]).toBe(
    "/api/common/bill-options?billMonth=202611",
  );
  fireEvent.change(screen.getByRole("combobox", { name: "프로젝트" }), {
    target: { value: "chosen" },
  });
  expect(onProject).toHaveBeenCalledWith("chosen");
  fireEvent.change(screen.getByLabelText("청구월"), {
    target: { value: "2026-12" },
  });
  expect(onMonth).toHaveBeenCalledWith("202612");
});
it("fixed edit, copy, and check identity cannot be changed", async () => {
  render(
    <BillingOptionsFields
      fixed
      projectId="chosen"
      billMonth="202611"
      onProject={vi.fn()}
      onMonth={vi.fn()}
    />,
  );
  await screen.findByRole("option", { name: "청구 대상 · 조회 고객" });
  expect(screen.getByRole("combobox", { name: "프로젝트" })).toBeDisabled();
  expect(screen.getByLabelText("청구월")).toBeDisabled();
});
it("empty server options do not remove controls or break the form", async () => {
  request.mockResolvedValue(ok(value({ projects: [] })));
  render(
    <BillingOptionsFields
      projectId=""
      billMonth="202611"
      onProject={vi.fn()}
      onMonth={vi.fn()}
    />,
  );
  expect(await screen.findByText("조회 결과가 없습니다")).toBeVisible();
  expect(screen.getByRole("combobox", { name: "프로젝트" })).toBeVisible();
  expect(screen.getByLabelText("청구월")).toBeVisible();
});
it("failed options show retry rather than invented projects", async () => {
  request.mockRejectedValueOnce(new Error("선택지 조회 실패"));
  render(
    <BillingOptionsFields
      projectId=""
      billMonth="202611"
      onProject={vi.fn()}
      onMonth={vi.fn()}
    />,
  );
  expect(await screen.findByText("선택지 조회 실패")).toBeVisible();
  expect(screen.getByRole("combobox", { name: "프로젝트" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "재시도" }));
  expect(
    await screen.findByRole("option", { name: "청구 대상 · 조회 고객" }),
  ).toBeVisible();
});
it("selected project and changed month reload server estimate, rate, and due date without extra query keys", async () => {
  request.mockImplementation(async (path: string) =>
    ok(
      value(
        path.includes("202612")
          ? {
              defaultBillMonth: "202612",
              defaultPayDueDate: "2027-01-25",
              estimatedSupplyAmount: 12345,
              vatRate: 0,
            }
          : { estimatedSupplyAmount: 9000 },
      ),
    ),
  );
  const { result, rerender } = renderHook(
    ({ projectId, billMonth }) => useBillingOptions(projectId, billMonth),
    { initialProps: { projectId: "chosen", billMonth: "202611" } },
  );
  await waitFor(() =>
    expect(result.current.data?.estimatedSupplyAmount).toBe(9000),
  );
  rerender({ projectId: "chosen", billMonth: "202612" });
  await waitFor(() =>
    expect(result.current.data?.estimatedSupplyAmount).toBe(12345),
  );
  expect(result.current.data?.defaultPayDueDate).toBe("2027-01-25");
  expect(result.current.data?.vatRate).toBe(0);
  expect(request.mock.calls.map(([path]) => path)).toEqual([
    "/api/common/bill-options?projectId=chosen&billMonth=202611",
    "/api/common/bill-options?projectId=chosen&billMonth=202612",
  ]);
});
it("loading leaves identity controls present and blocks selecting stale projects", () => {
  request.mockImplementation(() => new Promise(() => undefined));
  render(
    <BillingOptionsFields
      projectId=""
      billMonth="202611"
      onProject={vi.fn()}
      onMonth={vi.fn()}
    />,
  );
  expect(screen.getByRole("status")).toHaveTextContent(
    "청구 선택지를 불러오는 중입니다.",
  );
  expect(screen.getByRole("combobox", { name: "프로젝트" })).toBeDisabled();
});

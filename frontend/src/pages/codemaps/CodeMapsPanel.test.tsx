import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { CodeMapsPanel } from "./CodeMapsPanel";
import type { CodeMapItem } from "./codeMaps";

let rows: CodeMapItem[];
let request: ReturnType<typeof vi.fn>;
let failList: boolean;
let failOptions: boolean;
let failWrite: boolean;
let failRefresh: boolean;
const item = (): CodeMapItem => ({
  excelText: "정직원",
  groupId: "EMP_TYPE",
  code: "FULL",
});
const success = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const failure = () => ({
  ok: false,
  status: 400,
  json: async () => ({
    success: false,
    meta: {},
    message: "같은 그룹에 등록된 표기입니다.",
    errors: [{ field: "excelText", message: "중복된 표기입니다." }],
  }),
});
const writes = () =>
  request.mock.calls.filter(
    (call: unknown[]) =>
      (call[1] as RequestInit | undefined)?.method === "POST",
  );
beforeEach(() => {
  rows = [];
  failList = false;
  failOptions = false;
  failWrite = false;
  failRefresh = false;
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path === "/api/common/code-maps" && init?.method === "POST") {
      if (failWrite) return failure();
      const value = JSON.parse(String(init.body)) as CodeMapItem;
      rows.push(value);
      if (failRefresh) failList = true;
      return success(value);
    }
    if (path === "/api/common/code-maps/delete") {
      if (failWrite) return failure();
      const selected = JSON.parse(String(init?.body)).items as CodeMapItem[];
      rows = rows.filter(
        (row) =>
          !selected.some(
            (value) =>
              value.excelText === row.excelText &&
              value.groupId === row.groupId,
          ),
      );
      return success({ deleted: selected });
    }
    if (path === "/api/common/code-maps")
      return failList ? failure() : success({ items: rows });
    if (path === "/api/common/code-groups")
      return failOptions
        ? failure()
        : success({
            items: [
              { groupId: "EMP_TYPE", groupName: "직원 구분" },
              { groupId: "OTHER", groupName: "다른 그룹" },
            ],
          });
    if (path === "/api/common/code-groups/EMP_TYPE/codes")
      return failOptions
        ? failure()
        : success({
            items: [
              { code: "FULL", codeName: "정규직", useYn: "Y" },
              { code: "STOP", codeName: "중지", useYn: "N" },
            ],
          });
    if (path === "/api/common/code-groups/OTHER/codes")
      return success({
        items: [{ code: "OTHER_CODE", codeName: "다른 코드", useYn: "Y" }],
      });
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

async function openAdd() {
  await waitFor(() => expect(screen.getByTestId("code-map-add")).toBeEnabled());
  fireEvent.click(screen.getByTestId("code-map-add"));
  await screen.findByRole("option", { name: "직원 구분" });
}
async function chooseTarget() {
  fireEvent.change(screen.getByTestId("code-map-group"), {
    target: { value: "EMP_TYPE" },
  });
  await screen.findByRole("option", { name: "FULL · 정규직" });
  fireEvent.change(screen.getByTestId("code-map-code"), {
    target: { value: "FULL" },
  });
}
async function readyToDelete() {
  await screen.findByText("정직원");
  fireEvent.click(
    screen.getByRole("checkbox", { name: "정직원 EMP_TYPE 선택" }),
  );
  fireEvent.click(screen.getByTestId("code-map-delete"));
  return screen.getByTestId("code-map-delete-dialog");
}

it("shows loading without a fabricated empty result", async () => {
  let resolve!: (value: ReturnType<typeof success>) => void;
  request.mockImplementationOnce(
    () =>
      new Promise<ReturnType<typeof success>>((done) => {
        resolve = done;
      }),
  );
  render(<CodeMapsPanel />);
  expect(
    screen.getByText("표기 대응을 불러오는 중입니다."),
  ).toBeInTheDocument();
  expect(screen.queryByText("등록된 표기가 없습니다.")).not.toBeInTheDocument();
  expect(screen.getByTestId("code-map-add")).toBeDisabled();
  resolve(success({ items: [] }));
  await screen.findByText("등록된 표기가 없습니다.");
});
it("starts empty and shows the three contract columns", async () => {
  render(<CodeMapsPanel />);
  await screen.findByText("등록된 표기가 없습니다.");
  expect(
    screen.getByRole("columnheader", { name: "엑셀 표기" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: "코드 그룹" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: "바꿀 코드" }),
  ).toBeInTheDocument();
});
it("distinguishes failed list from empty and retries", async () => {
  failList = true;
  render(<CodeMapsPanel />);
  await screen.findByTestId("code-map-retry");
  expect(screen.queryByText("등록된 표기가 없습니다.")).not.toBeInTheDocument();
  failList = false;
  fireEvent.click(screen.getByTestId("code-map-retry"));
  await screen.findByText("등록된 표기가 없습니다.");
});
it("adds from actual group options, refreshes the list and notifies history consumers", async () => {
  const changed = vi.fn();
  render(<CodeMapsPanel onChanged={changed} />);
  await openAdd();
  fireEvent.change(screen.getByTestId("code-map-text"), {
    target: { value: "正직원" },
  });
  await chooseTarget();
  expect(
    screen.queryByRole("option", { name: "STOP · 중지" }),
  ).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("code-map-submit"));
  await screen.findByText("正직원");
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    excelText: "正직원",
    groupId: "EMP_TYPE",
    code: "FULL",
  });
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("validates required text without posting", async () => {
  render(<CodeMapsPanel />);
  await openAdd();
  await chooseTarget();
  fireEvent.click(screen.getByTestId("code-map-submit"));
  expect(screen.getByText("엑셀 표기를 넣어 주세요.")).toBeInTheDocument();
  expect(screen.getByTestId("code-map-text")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(writes()).toHaveLength(0);
});
it("시안 UI-27: 추가 창은 첫 그룹이 골라져 있고, 표기 칸은 50자·「예: 정직원」", async () => {
  const dirty = vi.fn();
  render(<CodeMapsPanel onDirtyChange={dirty} />);
  await openAdd();
  expect(screen.getByTestId("code-map-group")).toHaveValue("EMP_TYPE");
  expect(
    within(screen.getByTestId("code-map-group")).queryByRole("option", {
      name: "선택해 주세요",
    }),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("code-map-text")).toHaveAttribute(
    "maxLength",
    "50",
  );
  expect(screen.getByTestId("code-map-text")).toHaveAttribute(
    "placeholder",
    "예: 정직원",
  );
  await screen.findByRole("option", { name: "FULL · 정규직" });
  // 미리 골라 둔 그룹만으로는 바꾼 것이 아니다.
  expect(dirty).not.toHaveBeenCalledWith(true);
  fireEvent.click(screen.getByTestId("code-map-cancel"));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("시안 UI-27: 같은 그룹 중복·코드 이름과 같은 표기는 보내지 않는다", async () => {
  rows = [item()];
  render(<CodeMapsPanel />);
  await screen.findByText("정직원");
  await openAdd();
  await chooseTarget();
  fireEvent.change(screen.getByTestId("code-map-text"), {
    target: { value: " 정직원 " },
  });
  fireEvent.click(screen.getByTestId("code-map-submit"));
  expect(
    screen.getByText("같은 그룹에 이미 있는 표기입니다."),
  ).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("code-map-text"), {
    target: { value: "정규직" },
  });
  fireEvent.click(screen.getByTestId("code-map-submit"));
  expect(
    screen.getByText("코드 이름과 같아서 따로 대응할 필요가 없습니다."),
  ).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("validates required code instead of sending a blank selection", async () => {
  render(<CodeMapsPanel />);
  await openAdd();
  fireEvent.change(screen.getByTestId("code-map-text"), {
    target: { value: "새 표기" },
  });
  fireEvent.change(screen.getByTestId("code-map-group"), {
    target: { value: "EMP_TYPE" },
  });
  await screen.findByRole("option", { name: "FULL · 정규직" });
  fireEvent.click(screen.getByTestId("code-map-submit"));
  expect(screen.getByText("바꿀 코드를 골라 주세요.")).toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("retains draft and field errors when server rejects a duplicate", async () => {
  failWrite = true;
  render(<CodeMapsPanel />);
  await openAdd();
  await chooseTarget();
  fireEvent.change(screen.getByTestId("code-map-text"), {
    target: { value: "정직원" },
  });
  fireEvent.click(screen.getByTestId("code-map-submit"));
  await screen.findByText("중복된 표기입니다.");
  expect(screen.getByTestId("code-map-text")).toHaveValue("정직원");
  expect(screen.getByTestId("code-map-code")).toHaveValue("FULL");
  expect(screen.getByRole("dialog")).toBeInTheDocument();
});
it("clears old code when changing groups", async () => {
  render(<CodeMapsPanel />);
  await openAdd();
  await chooseTarget();
  fireEvent.change(screen.getByTestId("code-map-group"), {
    target: { value: "OTHER" },
  });
  await screen.findByRole("option", { name: "OTHER_CODE · 다른 코드" });
  expect(screen.getByTestId("code-map-code")).toHaveValue("");
  expect(
    screen.queryByRole("option", { name: "FULL · 정규직" }),
  ).not.toBeInTheDocument();
});
it("blocks submit on option failure and allows retry", async () => {
  render(<CodeMapsPanel />);
  await waitFor(() => expect(screen.getByTestId("code-map-add")).toBeEnabled());
  failOptions = true;
  fireEvent.click(screen.getByTestId("code-map-add"));
  await screen.findByTestId("code-map-options-retry");
  expect(screen.getByTestId("code-map-submit")).toBeDisabled();
  failOptions = false;
  fireEvent.click(screen.getByTestId("code-map-options-retry"));
  await screen.findByRole("option", { name: "직원 구분" });
  expect(writes()).toHaveLength(0);
});
it("cancels a dirty add without posting and preserves draft when discard is cancelled", async () => {
  const dirty = vi.fn();
  render(<CodeMapsPanel onDirtyChange={dirty} />);
  await openAdd();
  fireEvent.change(screen.getByTestId("code-map-text"), {
    target: { value: "입력 유지" },
  });
  fireEvent.click(screen.getByTestId("code-map-cancel"));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByText("취소"));
  expect(screen.getByTestId("code-map-text")).toHaveValue("입력 유지");
  fireEvent.click(screen.getByTestId("code-map-cancel"));
  fireEvent.click(within(screen.getByRole("alertdialog")).getByText("나가기"));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
  expect(dirty).toHaveBeenLastCalledWith(false);
});
it("cancels deletion without API call or changing selection", async () => {
  rows = [item()];
  render(<CodeMapsPanel />);
  const dialog = await readyToDelete();
  fireEvent.click(within(dialog).getByText("취소"));
  expect(writes()).toHaveLength(0);
  expect(rows).toHaveLength(1);
  expect(screen.getByRole("checkbox")).toBeChecked();
});
it("confirms physical deletion then refreshes and clears selection", async () => {
  rows = [item()];
  const changed = vi.fn();
  render(<CodeMapsPanel onChanged={changed} />);
  const dialog = await readyToDelete();
  expect(
    within(dialog).getByText(/다음 엑셀 업로드부터 이 표기는 모르는 표기로 오류가 납니다/),
  ).toBeInTheDocument();
  fireEvent.click(within(dialog).getByText("삭제"));
  await screen.findByText("등록된 표기가 없습니다.");
  expect(writes()).toHaveLength(1);
  expect(writes()[0][0]).toBe("/api/common/code-maps/delete");
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({ items: [item()] });
  expect(changed).toHaveBeenCalledTimes(1);
});
it("keeps delete confirmation and selection after failure, allowing retry", async () => {
  rows = [item()];
  failWrite = true;
  render(<CodeMapsPanel />);
  const dialog = await readyToDelete();
  fireEvent.click(within(dialog).getByText("삭제"));
  await within(dialog).findByRole("alert");
  expect(screen.getByRole("checkbox")).toBeChecked();
  failWrite = false;
  fireEvent.click(within(dialog).getByText("삭제"));
  await screen.findByText("등록된 표기가 없습니다.");
});
it("dirty parent blocks destructive confirmation and requests", async () => {
  rows = [item()];
  render(<CodeMapsPanel dirty />);
  await screen.findByText("정직원");
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.click(screen.getByTestId("code-map-delete"));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByTestId("code-map-toast")).toHaveTextContent(
    "먼저 저장하거나 취소",
  );
  expect(writes()).toHaveLength(0);
});
it("requires a selected row before opening deletion", async () => {
  render(<CodeMapsPanel />);
  await screen.findByText("등록된 표기가 없습니다.");
  fireEvent.click(screen.getByTestId("code-map-delete"));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("does not resend committed create when refresh fails", async () => {
  failRefresh = true;
  render(<CodeMapsPanel />);
  await openAdd();
  await chooseTarget();
  fireEvent.change(screen.getByTestId("code-map-text"), {
    target: { value: "정직원" },
  });
  fireEvent.click(screen.getByTestId("code-map-submit"));
  await screen.findByText(/반영은 완료되었으나/);
  expect(screen.getByTestId("code-map-add")).toBeDisabled();
  failList = false;
  fireEvent.click(screen.getByTestId("code-map-retry"));
  await screen.findByText("정직원");
  expect(writes()).toHaveLength(1);
});
it("시안 UI-27: 목록은 표기 굵게, 그룹 이름, 「코드 · 코드 이름」으로 보인다", async () => {
  rows = [item(), { excelText: "없는 코드", groupId: "EMP_TYPE", code: "GONE" }];
  render(<CodeMapsPanel />);
  const first = await screen.findByTestId("code-map-row-0");
  expect(within(first).getByText("정직원").tagName).toBe("STRONG");
  await waitFor(() => expect(within(first).getByText("직원 구분")).toBeVisible());
  expect(within(first).getByText("FULL · 정규직")).toBeVisible();
  // 공통코드에서 사라진 코드는 이름 대신 「–」
  expect(within(screen.getByTestId("code-map-row-1")).getByText("GONE · –")).toBeVisible();
  expect(writes()).toHaveLength(0);
});

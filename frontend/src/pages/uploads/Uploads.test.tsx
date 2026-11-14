import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { UploadDialog } from "./UploadDialog";
import { UploadResultDialog } from "./UploadResultDialog";
import { UploadActions } from "./UploadActions";
import { type UploadResult } from "./uploads";

let result: UploadResult;
let request: ReturnType<typeof vi.fn>;
let failGet: boolean;
let failApply: number;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const error = (status: number) => ({
  ok: false,
  status,
  json: async () => ({
    success: false,
    message:
      status === 409
        ? "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."
        : "파일을 확인해 주세요.",
    errors: [
      {
        field: status === 409 ? "modifiedAt" : "file",
        message: "입력을 확인해 주세요.",
      },
    ],
  }),
});
const posts = () =>
  request.mock.calls.filter(
    (call: unknown[]) =>
      (call[1] as RequestInit | undefined)?.method === "POST",
  );
beforeEach(() => {
  failGet = false;
  failApply = 0;
  result = {
    uploadId: "81",
    modifiedAt: null,
    statusName: "검사완료",
    successCount: 1,
    warningCount: 0,
    errorCount: 0,
    issues: [],
  };
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (path.includes("/code-groups/UPLD_STAT/codes"))
      return ok({
        items: [
          { code: "CHECKED", codeName: "검사완료" },
          { code: "APPLIED", codeName: "반영완료" },
        ],
      });
    if (path === "/api/common/uploads" && init?.method === "POST")
      return failApply ? error(failApply) : ok(result);
    if (path.endsWith("/apply")) {
      if (failApply) return error(failApply);
      result = {
        ...result,
        statusName: "반영완료",
        modifiedAt: "2030-01-01T00:00:00.123456Z",
      };
      return ok(result);
    }
    if (path === "/api/common/uploads/81")
      return failGet ? error(400) : ok(result);
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
const file = () =>
  new File(["test-only workbook bytes"], "clients.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });

it("requires an xlsx and keeps selected file after server field error", async () => {
  failApply = 400;
  const checked = vi.fn();
  render(
    <UploadDialog formTypeCode="B7" onClose={vi.fn()} onChecked={checked} />,
  );
  fireEvent.click(screen.getByTestId("upload-check"));
  expect(screen.getByRole("alert")).toHaveTextContent("파일을 선택");
  expect(posts()).toHaveLength(0);
  fireEvent.change(screen.getByTestId("upload-file"), {
    target: { files: [file()] },
  });
  fireEvent.click(screen.getByTestId("upload-check"));
  expect(await screen.findByText("file: 입력을 확인해 주세요.")).toBeVisible();
  expect(screen.getByTestId("upload-selected-file")).toHaveTextContent(
    "clients.xlsx",
  );
  expect(checked).not.toHaveBeenCalled();
});
it("rejects a renamed file extension before HTTP and checks decimal size limit", () => {
  render(
    <UploadDialog formTypeCode="B7" onClose={vi.fn()} onChecked={vi.fn()} />,
  );
  fireEvent.change(screen.getByTestId("upload-file"), {
    target: { files: [new File(["text"], "clients.csv")] },
  });
  fireEvent.click(screen.getByTestId("upload-check"));
  expect(screen.getByRole("alert")).toHaveTextContent("xlsx");
  const oversized = file();
  Object.defineProperty(oversized, "size", { value: 10_000_001 });
  fireEvent.change(screen.getByTestId("upload-file"), {
    target: { files: [oversized] },
  });
  fireEvent.click(screen.getByTestId("upload-check"));
  expect(screen.getByRole("alert")).toHaveTextContent("10,000,000");
  expect(posts()).toHaveLength(0);
});
it("uploads multipart formTypeCode and selected bytes; inspection never calls apply", async () => {
  const checked = vi.fn();
  render(
    <UploadDialog formTypeCode="B7" onClose={vi.fn()} onChecked={checked} />,
  );
  const chosen = file();
  fireEvent.change(screen.getByTestId("upload-file"), {
    target: { files: [chosen] },
  });
  fireEvent.click(screen.getByTestId("upload-check"));
  await waitFor(() => expect(checked).toHaveBeenCalledWith(result));
  const body = posts()[0][1].body as FormData;
  expect(body.get("formTypeCode")).toBe("B7");
  expect(body.get("file")).toBe(chosen);
  expect(posts()).toHaveLength(1);
  expect(posts()[0][1].headers).toBeUndefined();
});
it("cancel closes selection with zero writes", () => {
  const close = vi.fn();
  render(
    <UploadDialog formTypeCode="B7" onClose={close} onChecked={vi.fn()} />,
  );
  fireEvent.click(screen.getByTestId("upload-cancel"));
  expect(close).toHaveBeenCalledOnce();
  expect(posts()).toHaveLength(0);
});
it("result reload is read-only, displays counts and closing never applies", async () => {
  const close = vi.fn();
  render(
    <UploadResultDialog uploadId="81" onClose={close} onApplied={vi.fn()} />,
  );
  expect(screen.getByText("검사 결과 조회 중…")).toBeVisible();
  expect(await screen.findByText("행 오류와 경고가 없습니다.")).toBeVisible();
  await waitFor(() => expect(screen.getByTestId("upload-apply")).toBeEnabled());
  // 시안 .kpis: 성공 / 경고(반영은 됨) / 오류(고쳐야 반영됨)
  expect(screen.getByTestId("upload-counts")).toHaveTextContent(
    /성공\s*1건.*경고\s*0건.*반영은 됨.*오류\s*0건.*고쳐야 반영됨/,
  );
  fireEvent.click(screen.getByTestId("upload-result-close"));
  expect(close).toHaveBeenCalledOnce();
  expect(posts()).toHaveLength(0);
});
it("mixed errors and warnings show original values and block all apply", async () => {
  result = {
    ...result,
    errorCount: 1,
    warningCount: 1,
    issues: [
      {
        row: 4,
        column: "이메일",
        inputValue: "",
        reason: "필수 입력입니다.",
        severity: "오류",
      },
      {
        row: 7,
        column: "업종",
        inputValue: "옛 업종",
        reason: "정보통신으로 바꿈",
        severity: "경고",
      },
    ],
  };
  render(
    <UploadResultDialog uploadId="81" onClose={vi.fn()} onApplied={vi.fn()} />,
  );
  expect(await screen.findByText("필수 입력입니다.")).toBeVisible();
  expect(screen.getByText("옛 업종")).toBeVisible();
  expect(screen.getByTestId("upload-apply")).toBeDisabled();
  expect(screen.getByTestId("upload-errors-download")).toBeEnabled();
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  expect(posts()).toHaveLength(0);
});
it("warning-only result allows atomic apply, preserves null token and notifies refresh once", async () => {
  result = {
    ...result,
    successCount: 0,
    warningCount: 1,
    issues: [
      {
        row: 2,
        column: "구분",
        inputValue: "옛 고객",
        reason: "고객사로 바꿈",
        severity: "경고",
      },
    ],
  };
  const applied = vi.fn();
  render(
    <UploadResultDialog uploadId="81" onClose={vi.fn()} onApplied={applied} />,
  );
  await waitFor(() => expect(screen.getByTestId("upload-apply")).toBeEnabled());
  fireEvent.click(screen.getByTestId("upload-apply"));
  fireEvent.click(screen.getByTestId("upload-apply"));
  expect(await screen.findByTestId("upload-applied")).toHaveTextContent(
    "반영했습니다.",
  );
  expect(posts()).toHaveLength(1);
  expect(JSON.parse(posts()[0][1].body)).toEqual({ modifiedAt: null });
  expect(applied).toHaveBeenCalledOnce();
  expect(screen.getByTestId("upload-apply")).toBeDisabled();
});
it("already applied DB status disables replay control", async () => {
  result.statusName = "반영완료";
  render(
    <UploadResultDialog uploadId="81" onClose={vi.fn()} onApplied={vi.fn()} />,
  );
  expect(await screen.findByTestId("upload-applied")).toBeVisible();
  expect(screen.getByTestId("upload-apply")).toBeDisabled();
  expect(posts()).toHaveLength(0);
});
it("empty file result has no actionable apply", async () => {
  result.successCount = 0;
  render(
    <UploadResultDialog uploadId="81" onClose={vi.fn()} onApplied={vi.fn()} />,
  );
  expect(await screen.findByText("검사할 데이터 행이 없습니다.")).toBeVisible();
  expect(screen.getByTestId("upload-apply")).toBeDisabled();
});
it.each([400, 409])(
  "keeps result after %s, shows fields and offers fresh result read",
  async (status) => {
    failApply = status;
    render(
      <UploadResultDialog
        uploadId="81"
        onClose={vi.fn()}
        onApplied={vi.fn()}
      />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("upload-apply")).toBeEnabled(),
    );
    fireEvent.click(screen.getByTestId("upload-apply"));
    expect(await screen.findByTestId("upload-result-error")).toBeVisible();
    expect(screen.getByTestId("upload-counts")).toHaveTextContent(/성공\s*1건/);
    failApply = 0;
    fireEvent.click(screen.getByTestId("upload-result-retry"));
    await waitFor(() =>
      expect(screen.getByTestId("upload-apply")).toBeEnabled(),
    );
  },
);
it("failed initial read disables apply until retry succeeds", async () => {
  failGet = true;
  render(
    <UploadResultDialog uploadId="81" onClose={vi.fn()} onApplied={vi.fn()} />,
  );
  expect(await screen.findByTestId("upload-result-error")).toBeVisible();
  expect(screen.getByTestId("upload-apply")).toBeDisabled();
  failGet = false;
  fireEvent.click(screen.getByTestId("upload-result-retry"));
  await waitFor(() => expect(screen.getByTestId("upload-apply")).toBeEnabled());
});
it("Ctrl+S applies active result once and listener is removed on unmount", async () => {
  const view = render(
    <UploadResultDialog uploadId="81" onClose={vi.fn()} onApplied={vi.fn()} />,
  );
  await waitFor(() => expect(screen.getByTestId("upload-apply")).toBeEnabled());
  screen.getByTestId("upload-apply").focus();
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(posts()).toHaveLength(1));
  view.unmount();
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  expect(posts()).toHaveLength(1);
});
it("actions open inspection and transition to persisted result without login", async () => {
  render(<UploadActions onApplied={vi.fn()} />);
  fireEvent.click(screen.getByTestId("upload-open"));
  fireEvent.change(screen.getByTestId("upload-file"), {
    target: { files: [file()] },
  });
  fireEvent.click(screen.getByTestId("upload-check"));
  expect(await screen.findByTestId("upload-result-dialog")).toBeVisible();
  expect(screen.queryByTestId("upload-dialog")).not.toBeInTheDocument();
  expect(posts()).toHaveLength(1);
  expect(
    request.mock.calls.some((call) => String(call[0]).includes("login")),
  ).toBe(false);
});
it("template failures are visible rather than silently downloading an error", async () => {
  request.mockResolvedValueOnce(error(400));
  render(<UploadActions onApplied={vi.fn()} />);
  fireEvent.click(screen.getByTestId("upload-template"));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "파일을 확인해 주세요.",
  );
});
it.each([
  [false, true],
  [true, false],
])("FR-0107: mobile=%s shows the template and upload buttons=%s", (mobile, shown) => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query: string) => ({
      matches: mobile && query === "(max-width: 767px)",
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  render(<UploadActions onApplied={() => {}} />);
  expect(!!screen.queryByTestId("upload-open")).toBe(shown);
  expect(!!screen.queryByTestId("upload-template")).toBe(shown);
});

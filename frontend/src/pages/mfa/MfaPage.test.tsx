import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import type { ComponentProps } from "react";
import { MfaPage } from "./MfaPage";
import {
  createMfaApi,
  MfaApiError,
  type MfaApi,
  type MfaDelivery,
} from "./mfaApi";

let api: MfaApi;
let initial: MfaDelivery;
const onCompleted = vi.fn();
const onRestart = vi.fn();
beforeEach(() => {
  vi.clearAllMocks();
  initial = {
    maskedEmail: "a***@example.invalid",
    expiresAt: new Date(Date.now() + 300000).toISOString(),
    resendAvailableAt: new Date(Date.now() - 1000).toISOString(),
  };
  api = { verify: vi.fn(), resend: vi.fn() };
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});
function show(extra: Partial<ComponentProps<typeof MfaPage>> = {}) {
  return render(
    <MfaPage
      api={api}
      initial={initial}
      onCompleted={onCompleted}
      onRestart={onRestart}
      {...extra}
    />,
  );
}
function enter(code = "123456") {
  fireEvent.change(screen.getByLabelText("인증번호"), {
    target: { value: code },
  });
}

describe("UI-39 administrator pending challenge", () => {
  it("shows only masked email and one-time-code input", () => {
    show();
    expect(screen.getByTestId("mfa-page")).toHaveClass("account-access");
    expect(
      screen.getByRole("heading", { name: "인증번호 확인" }).parentElement,
    ).toHaveClass("account-card");
    expect(screen.getByText(initial.maskedEmail)).toBeInTheDocument();
    expect(screen.getByLabelText("인증번호")).toHaveAttribute(
      "autocomplete",
      "one-time-code",
    );
    expect(screen.getByTestId("mfa-page")).toHaveAttribute(
      "data-screen-id",
      "UI-39",
    );
  });
  it("rejects fewer than six digits without calling server", () => {
    show();
    enter("12345");
    fireEvent.click(screen.getByRole("button", { name: "인증번호 확인" }));
    expect(screen.getByTestId("mfa-code-error")).toHaveTextContent(
      "인증번호 6자리를 넣어 주세요.",
    );
    expect(api.verify).not.toHaveBeenCalled();
  });
  it("passes completion and metadata to session owner for forced password navigation", async () => {
    const response = {
      success: true as const,
      data: { nextScreenId: "UI-40" },
      meta: { modifiedAt: "2026-01-02T00:00:00Z" },
    };
    vi.mocked(api.verify).mockResolvedValue(response);
    show();
    enter();
    fireEvent.click(screen.getByTestId("mfa-verify-button"));
    await waitFor(() => expect(onCompleted).toHaveBeenCalledWith(response));
    expect(api.verify).toHaveBeenCalledWith("123456");
  });
  it("does not duplicate submissions while loading", async () => {
    let resolve!: (value: Awaited<ReturnType<MfaApi["verify"]>>) => void;
    vi.mocked(api.verify).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    show();
    enter();
    fireEvent.click(screen.getByTestId("mfa-verify-button"));
    fireEvent.click(screen.getByTestId("mfa-verify-button"));
    expect(screen.getByTestId("mfa-resend-button")).toBeDisabled();
    expect(api.verify).toHaveBeenCalledTimes(1);
    await act(async () =>
      resolve({ success: true, data: { nextScreenId: "UI-34" }, meta: {} }),
    );
  });
  it("keeps code and displays original server field error on failure", async () => {
    vi.mocked(api.verify).mockRejectedValue(
      new MfaApiError(400, "인증번호가 맞지 않습니다. (1/5)", [
        { field: "code", message: "인증번호가 맞지 않습니다. (1/5)" },
      ]),
    );
    show();
    enter();
    fireEvent.click(screen.getByTestId("mfa-verify-button"));
    await waitFor(() =>
      expect(screen.getByTestId("mfa-code-error")).toHaveTextContent(
        "인증번호가 맞지 않습니다. (1/5)",
      ),
    );
    expect(screen.getByLabelText("인증번호")).toHaveValue("123456");
  });
  it("disables resend until the server-provided availability instant", () => {
    vi.useFakeTimers();
    initial.resendAvailableAt = new Date(Date.now() + 60000).toISOString();
    show();
    expect(screen.getByTestId("mfa-resend-button")).toBeDisabled();
    act(() => vi.advanceTimersByTime(60000));
    expect(screen.getByTestId("mfa-resend-button")).toBeEnabled();
  });
  it("updates expiry, clears old input and waits after successful resend", async () => {
    const next = {
      ...initial,
      expiresAt: new Date(Date.now() + 300000).toISOString(),
      resendAvailableAt: new Date(Date.now() + 60000).toISOString(),
    };
    vi.mocked(api.resend).mockResolvedValue({
      success: true,
      meta: {},
      data: next,
    });
    show();
    enter();
    fireEvent.click(screen.getByTestId("mfa-resend-button"));
    await waitFor(() =>
      expect(screen.getByTestId("mfa-message")).toHaveTextContent(
        "인증번호를 다시 보냈습니다.",
      ),
    );
    expect(screen.getByLabelText("인증번호")).toHaveValue("");
    expect(screen.getByTestId("mfa-resend-button")).toBeDisabled();
  });
  it("expired code cannot be submitted but can be resent", () => {
    initial.expiresAt = new Date(Date.now() - 1).toISOString();
    show();
    expect(screen.getByTestId("mfa-verify-button")).toBeDisabled();
    expect(screen.getByTestId("mfa-resend-button")).toBeEnabled();
    expect(
      screen.getByText(
        "인증번호 시간이 지났습니다. [다시 보내기]를 눌러 주세요.",
      ),
    ).toBeInTheDocument();
  });
  it("blocks form submission after expiry even when an input still contains a code", () => {
    vi.useFakeTimers();
    show();
    enter();
    act(() => vi.advanceTimersByTime(300000));
    fireEvent.submit(screen.getByLabelText("인증번호").closest("form")!);
    expect(api.verify).not.toHaveBeenCalled();
    expect(screen.getByTestId("mfa-resend-button")).toBeEnabled();
  });
  it("blocks form submission without a pending context", () => {
    show({ initial: null });
    fireEvent.submit(screen.getByLabelText("인증번호").closest("form")!);
    expect(api.verify).not.toHaveBeenCalled();
    expect(api.resend).not.toHaveBeenCalled();
  });
  it("allows verification again only after delivery failure is recovered by resend", async () => {
    const message =
      "인증번호를 보내지 못했습니다. 잠시 후 [다시 보내기]를 눌러 주세요.";
    vi.mocked(api.resend)
      .mockRejectedValueOnce(new MfaApiError(400, message))
      .mockResolvedValueOnce({ success: true, meta: {}, data: initial });
    show();
    enter();
    fireEvent.click(screen.getByTestId("mfa-resend-button"));
    await waitFor(() =>
      expect(screen.getByTestId("mfa-message")).toHaveTextContent(message),
    );
    expect(screen.getByLabelText("인증번호")).toHaveValue("123456");
    fireEvent.submit(screen.getByLabelText("인증번호").closest("form")!);
    expect(api.verify).not.toHaveBeenCalled();
    expect(screen.getByTestId("mfa-verify-button")).toBeDisabled();
    fireEvent.click(screen.getByTestId("mfa-resend-button"));
    await waitFor(() =>
      expect(screen.getByTestId("mfa-verify-button")).toBeEnabled(),
    );
    expect(screen.getByLabelText("인증번호")).toHaveValue("");
    expect(onCompleted).not.toHaveBeenCalled();
  });
  it("mail failure exposes retry only and never bypasses MFA", () => {
    show({
      initial: null,
      deliveryError:
        "인증번호를 보내지 못했습니다. 잠시 후 [다시 보내기]를 눌러 주세요.",
    });
    expect(screen.getByTestId("mfa-verify-button")).toBeDisabled();
    expect(screen.getByLabelText("인증번호")).toBeDisabled();
    expect(screen.getByTestId("mfa-resend-button")).toBeEnabled();
    expect(onCompleted).not.toHaveBeenCalled();
  });
  it("missing pending context offers restart, not a business screen", () => {
    show({ initial: null });
    expect(screen.getByTestId("mfa-resend-button")).toBeDisabled();
    fireEvent.click(screen.getByTestId("mfa-restart-button"));
    expect(onRestart).toHaveBeenCalled();
    expect(onCompleted).not.toHaveBeenCalled();
  });
  it.each([401, 400])(
    "returns to login on expired session or fifth mistake (%s)",
    async (status) => {
      const message = "처음부터 다시 로그인해 주세요.";
      vi.mocked(api.verify).mockRejectedValue(
        new MfaApiError(status, message, [], { nextScreenId: "UI-22" }),
      );
      show();
      enter();
      fireEvent.click(screen.getByTestId("mfa-verify-button"));
      await waitFor(() => expect(onRestart).toHaveBeenCalledWith(message));
      expect(onCompleted).not.toHaveBeenCalled();
    },
  );
  it("shows forbidden response without completing authentication", async () => {
    vi.mocked(api.resend).mockRejectedValue(
      new MfaApiError(403, "권한이 없습니다."),
    );
    show();
    fireEvent.click(screen.getByTestId("mfa-resend-button"));
    await waitFor(() =>
      expect(screen.getByTestId("mfa-message")).toHaveTextContent(
        "권한이 없습니다.",
      ),
    );
    expect(onCompleted).not.toHaveBeenCalled();
  });
  it("shows transport failure without discarding entered code", async () => {
    vi.mocked(api.verify).mockRejectedValue(
      new Error("서버에 연결하지 못했습니다."),
    );
    show();
    enter();
    fireEvent.click(screen.getByTestId("mfa-verify-button"));
    await waitFor(() =>
      expect(screen.getByTestId("mfa-message")).toHaveTextContent(
        "서버에 연결하지 못했습니다.",
      ),
    );
    expect(screen.getByLabelText("인증번호")).toHaveValue("123456");
  });
  it.each([
    "인증번호를 너무 많이 요청했습니다. 처음부터 다시 로그인해 주세요.",
    "인증번호 요청이 너무 많습니다. 10분 뒤 다시 로그인해 주세요.",
  ])("preserves the original resend limit message: %s", async (message) => {
    vi.mocked(api.resend).mockRejectedValue(new MfaApiError(400, message));
    show();
    fireEvent.click(screen.getByTestId("mfa-resend-button"));
    await waitFor(() =>
      expect(screen.getByTestId("mfa-message")).toHaveTextContent(message),
    );
    expect(onCompleted).not.toHaveBeenCalled();
  });
});

describe("MFA API adapter", () => {
  it("uses approved relative POST paths, no account identity and no resend body", async () => {
    const request = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ success: true, meta: {}, data: {} }),
    });
    const client = createMfaApi(request as typeof fetch);
    await client.verify("123456");
    await client.resend();
    expect(request).toHaveBeenNthCalledWith(1, "/api/common/mfa/verify", {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      body: '{"code":"123456"}',
    });
    expect(request).toHaveBeenNthCalledWith(2, "/api/common/mfa/resend", {
      method: "POST",
      credentials: "same-origin",
    });
  });
  it("preserves original error array and restart metadata", async () => {
    const request = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({
        success: false,
        message: "원문 안내",
        meta: { nextScreenId: "UI-22" },
        errors: [{ field: "code", message: "원문 안내" }],
      }),
    });
    await expect(
      createMfaApi(request as typeof fetch).verify("123456"),
    ).rejects.toMatchObject({
      status: 400,
      message: "원문 안내",
      meta: { nextScreenId: "UI-22" },
      errors: [{ field: "code", message: "원문 안내" }],
    });
  });
});

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
import { ClientPage } from "./ClientPage";
import { ClientForm } from "./ClientForm";
import { ClientPartWorkspace } from "./ClientPartWorkspace";
import { clientPayload, formatBusinessNumber, type Client } from "./clients";

const codes = {
  CLNT_TYPE: [
    { code: "CUST", codeName: "고객사" },
    { code: "VNDR", codeName: "협력사" },
    { code: "BOTH", codeName: "고객·협력" },
  ],
  BANK: [{ code: "004", codeName: "은행" }],
  INDUTY: [{ code: "IT", codeName: "정보통신" }],
};
let row: Client;
let request: ReturnType<typeof vi.fn>;
let failRead: boolean;
let saveError: number;
let empty: boolean;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const failure = (status = 400) => ({
  ok: false,
  status,
  json: async () => ({
    success: false,
    message:
      status === 409
        ? "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."
        : "상호를 확인해 주세요.",
    errors: [{ field: "clientName", message: "상호를 확인해 주세요." }],
  }),
});
const writes = () =>
  request.mock.calls.filter((call: unknown[]) =>
    ["POST", "PUT"].includes(
      (call[1] as RequestInit | undefined)?.method || "",
    ),
  );
beforeEach(() => {
  failRead = false;
  saveError = 0;
  empty = false;
  row = {
    clientId: "client-a",
    clientTypeCode: "BOTH",
    clientName: "거래처A",
    businessNumber: "123-45-67890",
    representativeName: "대표",
    industryCode: "IT",
    contactName: "담당",
    contactPhone: "010",
    taxEmail: "a@example.test",
    bankCode: "004",
    paymentAccountSummary: "은행 5678",
    useYn: "Y",
    modifiedAt: null,
    projectCount: 2,
    personCount: 3,
  };
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (["POST", "PUT"].includes(init?.method || "")) {
      if (saveError) return failure(saveError);
      if (path.endsWith("use-status"))
        return ok({
          changed: [],
          failed: [
            {
              clientId: row.clientId,
              reason: "소속 인력이 있어 중지할 수 없습니다.",
            },
          ],
        });
      const input = JSON.parse(String(init?.body));
      row = {
        ...row,
        ...input,
        clientId: row.clientId,
        modifiedAt: "2030-01-01T00:00:00Z",
      };
      return ok({
        client: row,
        warnings: [
          "같은 상호의 거래처가 이미 있습니다(사업자번호 987-65-43210)",
        ],
      });
    }
    if (failRead) return failure();
    for (const [group, items] of Object.entries(codes))
      if (path.includes(`/code-groups/${group}/codes`)) return ok({ items });
    if (path.endsWith("payment-account"))
      return ok({
        bankCode: "004",
        accountNumber: "12345678",
        accountHolder: "계좌주",
      });
    if (path.endsWith("assignments"))
      return ok({
        items: [
          {
            projectId: "p",
            projectName: "오늘 프로젝트",
            personName: "김개발",
            companyName: "협력 소속",
            jobName: "개발",
            withdrawalDate: "2030-12-31",
          },
        ],
      });
    if (path.endsWith("change-history"))
      return ok({
        items: [
          {
            changedAt: "2030-01-01T00:00:00Z",
            fieldName: "신규 등록",
            beforeValue: null,
            afterValue: "신규 등록",
            changedBy: "admin",
          },
        ],
      });
    if (path === "/api/common/clients/client-a") return ok(row);
    if (path.startsWith("/api/common/clients?"))
      return ok({ items: empty ? [] : [row] });
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("shows API-backed twelve columns, masked accounts and default active query", async () => {
  render(<ClientPage />);
  expect(await screen.findByText("거래처A")).toBeVisible();
  expect(screen.getByText("은행 5678")).toBeVisible();
  expect(screen.queryByText("12345678")).not.toBeInTheDocument();
  expect(screen.getAllByRole("columnheader")).toHaveLength(13);
  expect(
    request.mock.calls.some((call) => String(call[0]).includes("useYn=Y")),
  ).toBe(true);
});
it("submits category and keyword without invented pagination parameters", async () => {
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.change(screen.getByTestId("client-type-filter"), {
    target: { value: "CUST" },
  });
  fireEvent.change(screen.getByTestId("client-search"), {
    target: { value: "123-45" },
  });
  fireEvent.click(screen.getByTestId("client-query"));
  await waitFor(() =>
    expect(
      request.mock.calls.some(
        (call) =>
          String(call[0]).includes("clientTypeCode=CUST") &&
          String(call[0]).includes("keyword=123-45"),
      ),
    ).toBe(true),
  );
});
it.each(["client-projects-client-a", "client-people-client-a"])(
  "%s opens the same client's today assignment lines",
  async (id) => {
    render(<ClientPage />);
    await screen.findByText("거래처A");
    fireEvent.click(screen.getByTestId(id));
    expect(await screen.findByText("오늘 프로젝트")).toBeVisible();
    expect(screen.getByText("김개발")).toBeVisible();
    expect(screen.getByText("협력 소속")).toBeVisible();
  },
);
it("detail reads selected client and counts readonly history", async () => {
  render(<ClientPage />);
  fireEvent.click(await screen.findByText("거래처A"));
  fireEvent.click(await screen.findByTestId("client-detail-history"));
  expect(await screen.findByText("admin")).toBeVisible();
  expect(screen.getByText("변경 이력(1)")).toBeVisible();
  expect(
    within(screen.getByTestId("client-history-panel")).queryByRole("button"),
  ).not.toBeInTheDocument();
});
it("loads full account only by explicit detail action", async () => {
  render(<ClientPage />);
  fireEvent.click(await screen.findByText("거래처A"));
  const read = await screen.findByTestId("client-account-read");
  expect(
    request.mock.calls.some((call) =>
      String(call[0]).endsWith("payment-account"),
    ),
  ).toBe(false);
  fireEvent.click(read);
  expect(await screen.findByTestId("client-account-full")).toHaveTextContent(
    "12345678",
  );
});
it("renders empty state", async () => {
  empty = true;
  render(<ClientPage />);
  expect(await screen.findByText("등록된 거래처가 없습니다.")).toBeVisible();
});
it("renders request failure with retry", async () => {
  failRead = true;
  render(<ClientPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "상호를 확인해 주세요.",
  );
  failRead = false;
  fireEvent.click(screen.getByTestId("client-retry"));
  expect(await screen.findByText("거래처A")).toBeVisible();
});
it("status confirmation cancellation writes nothing and failures show reasons", async () => {
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("client-select-client-a"));
  fireEvent.click(screen.getByTestId("client-stop-selected"));
  fireEvent.click(screen.getByTestId("client-status-cancel"));
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("client-stop-selected"));
  fireEvent.click(screen.getByTestId("client-status-save"));
  expect(await screen.findByText(/0건 변경, 1건 실패/)).toHaveTextContent(
    "소속 인력이 있어 중지할 수 없습니다.",
  );
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    items: [{ clientId: "client-a", modifiedAt: null }],
    useYn: "N",
  });
});
it("registration starts with guidance only and changes category-dependent fields", () => {
  render(<ClientForm codes={codes} onCancel={vi.fn()} onSaved={vi.fn()} />);
  expect(screen.queryByLabelText("상호")).not.toBeInTheDocument();
  expect(screen.getByText(/구분을 선택하면/)).toBeVisible();
  fireEvent.change(screen.getByLabelText("구분"), {
    target: { value: "CUST" },
  });
  expect(screen.getByLabelText("세금계산서 이메일")).toBeVisible();
  expect(screen.queryByLabelText("계좌번호")).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("구분"), {
    target: { value: "VNDR" },
  });
  expect(screen.getByLabelText("계좌번호")).toBeVisible();
  expect(screen.queryByLabelText("세금계산서 이메일")).not.toBeInTheDocument();
});
it("formats business number and strips account letters before save", () => {
  render(<ClientForm codes={codes} onCancel={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("구분"), {
    target: { value: "BOTH" },
  });
  fireEvent.change(screen.getByLabelText("사업자번호"), {
    target: { value: "1234567890" },
  });
  expect(screen.getByLabelText("사업자번호")).toHaveValue("123-45-67890");
  fireEvent.change(screen.getByLabelText("계좌번호"), {
    target: { value: "ab12-34cd" },
  });
  expect(screen.getByLabelText("계좌번호")).toHaveValue("12-34");
  expect(formatBusinessNumber("1234567890")).toBe("123-45-67890");
});
it("validates required category fields without HTTP writes", () => {
  render(<ClientForm codes={codes} onCancel={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("구분"), {
    target: { value: "VNDR" },
  });
  fireEvent.click(screen.getByTestId("client-form-save"));
  expect(screen.getAllByText("필수 입력입니다.").length).toBeGreaterThan(0);
  expect(writes()).toHaveLength(0);
});
it("update makes business number readonly and excludes it from payload", async () => {
  render(
    <ClientForm
      client={{ ...row, clientTypeCode: "CUST" }}
      codes={codes}
      onCancel={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  expect(screen.getByLabelText("사업자번호")).toHaveAttribute("readonly");
  fireEvent.change(screen.getByLabelText("담당자"), {
    target: { value: "새 담당" },
  });
  fireEvent.click(screen.getByTestId("client-form-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body).not.toHaveProperty("businessNumber");
  expect(body).not.toHaveProperty("accountNumber");
  expect(body.modifiedAt).toBeNull();
});
it("account confirmation cancel retains draft with zero writes; consent saves once", async () => {
  render(
    <ClientForm
      client={row}
      codes={codes}
      onCancel={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(screen.getByLabelText("계좌번호")).toHaveValue("12345678"),
  );
  fireEvent.change(screen.getByLabelText("계좌번호"), {
    target: { value: "87654321" },
  });
  fireEvent.click(screen.getByTestId("client-form-save"));
  expect(screen.getByText("다음 지급부터 새 계좌로 보냅니다")).toBeVisible();
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("client-account-cancel"));
  expect(screen.getByLabelText("계좌번호")).toHaveValue("87654321");
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("client-form-save"));
  fireEvent.click(screen.getByTestId("client-account-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
});
it.each([400, 409])(
  "preserves draft after %s and shows field/conflict feedback",
  async (status) => {
    saveError = status;
    render(
      <ClientForm
        client={{ ...row, clientTypeCode: "CUST" }}
        codes={codes}
        onCancel={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText("상호"), {
      target: { value: "내 입력" },
    });
    fireEvent.click(screen.getByTestId("client-form-save"));
    await waitFor(() =>
      expect(screen.getAllByRole("alert").length).toBeGreaterThan(0),
    );
    expect(screen.getByLabelText("상호")).toHaveValue("내 입력");
    if (status === 409)
      expect(screen.getByTestId("client-version-refresh")).toBeVisible();
  },
);
it("Ctrl+S saves active form once and cleans up listener", async () => {
  const mounted = render(
    <ClientForm
      client={{ ...row, clientTypeCode: "CUST" }}
      codes={codes}
      onCancel={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  await waitFor(() => expect(writes()).toHaveLength(1));
  mounted.unmount();
  fireEvent.keyDown(window, { key: "s", ctrlKey: true });
  expect(writes()).toHaveLength(1);
});
it("payload removes hidden fields and required nullable token is preserved", () => {
  expect(
    clientPayload(
      {
        clientTypeCode: "CUST",
        clientName: "a",
        businessNumber: "123-45-67890",
        bankCode: "004",
        accountNumber: "12345678",
        accountHolder: "name",
        modifiedAt: null,
      },
      true,
    ),
  ).toEqual({ clientTypeCode: "CUST", clientName: "a", modifiedAt: null });
});
it("workspace switches to supplied part feature without creating another router", async () => {
  render(<ClientPartWorkspace partPage={<p>영업 파트 목록</p>} />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("client-workspace-parts"));
  expect(screen.getByText("영업 파트 목록")).toBeVisible();
  expect(screen.queryByTestId("client-page")).not.toBeInTheDocument();
});
it("successful registration refreshes active list, highlights saved row and shows same-name warning", async () => {
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("client-create"));
  fireEvent.change(screen.getByLabelText("구분"), {
    target: { value: "CUST" },
  });
  fireEvent.change(screen.getByLabelText("상호"), {
    target: { value: "새 상호" },
  });
  fireEvent.change(screen.getByLabelText("사업자번호"), {
    target: { value: "9876543210" },
  });
  fireEvent.change(screen.getByLabelText("세금계산서 이메일"), {
    target: { value: "new@example.test" },
  });
  fireEvent.click(screen.getByTestId("client-form-save"));
  expect(await screen.findByText("새 상호")).toBeVisible();
  expect(screen.getByTestId("client-row-client-a")).toHaveClass(
    "client-highlight",
  );
  expect(screen.getByText(/같은 상호의 거래처가 이미 있습니다/)).toBeVisible();
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.businessNumber).toBe("987-65-43210");
  expect(body).not.toHaveProperty("modifiedAt");
});
it("failed full-account load disables edit save until retry succeeds", async () => {
  failRead = true;
  render(
    <ClientForm
      client={row}
      codes={codes}
      onCancel={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  const retry = await screen.findByTestId("client-account-retry");
  expect(screen.getByTestId("client-form-save")).toBeDisabled();
  expect(writes()).toHaveLength(0);
  failRead = false;
  fireEvent.click(retry);
  await waitFor(() =>
    expect(screen.getByLabelText("계좌번호")).toHaveValue("12345678"),
  );
  expect(screen.getByTestId("client-form-save")).toBeEnabled();
});

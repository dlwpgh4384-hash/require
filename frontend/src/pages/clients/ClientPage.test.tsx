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
let noAccount: boolean;
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
  noAccount = false;
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
      if (path.endsWith("use-status") && JSON.parse(String(init?.body)).useYn === "Y") {
        row = { ...row, useYn: "Y" };
        return ok({ changed: [row.clientId], failed: [] });
      }
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
          "같은 상호의 거래처가 이미 있습니다(사업자번호 987-65-43210). 사업자번호가 달라 등록은 되었습니다(지점 등). 맞는지 확인해 주세요.",
        ],
      });
    }
    if (failRead) return failure();
    for (const [group, items] of Object.entries(codes))
      if (path.includes(`/code-groups/${group}/codes`)) return ok({ items });
    if (path.endsWith("payment-account") && noAccount)
      return ok({ bankCode: "", accountNumber: "", accountHolder: "" });
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
            managementNumber: "K-0001",
            companyName: "협력 소속",
            jobName: "개발",
            withdrawalDate: "2030-12-31",
            monthlyMm: 1,
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
  // 시안: 목록 지급 계좌는 「은행 …5678」, 진행 프로젝트·투입 인력은 「N건」「N명」
  expect(screen.getByText("은행 …5678")).toBeVisible();
  expect(screen.queryByText("12345678")).not.toBeInTheDocument();
  expect(screen.getAllByRole("columnheader")).toHaveLength(13);
  expect(screen.getByTestId("list-count")).toHaveTextContent("1");
  expect(screen.getByTestId("list-reset")).toBeVisible();
  expect(screen.getByTestId("list-columns")).toBeVisible();
  expect(screen.getByTestId("list-download")).toBeVisible();
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
  fireEvent.click(screen.getByTestId("list-search"));
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
    // 투입 내역은 프로젝트 묶음 머리(배지)와 줄의 프로젝트 이름에 두 번 나온다.
    expect((await screen.findAllByText("오늘 프로젝트"))[0]).toBeVisible();
    expect(screen.getByText("김개발")).toBeVisible();
    expect(screen.getByText("협력 소속")).toBeVisible();
  },
);
it("detail reads selected client and counts readonly history", async () => {
  render(<ClientPage />);
  fireEvent.click(await screen.findByText("거래처A"));
  // 시안: [⋯] 메뉴 「변경 이력 보기」로 변경 이력 탭을 연다
  fireEvent.click(await screen.findByTestId("client-detail-more"));
  fireEvent.click(screen.getByTestId("client-detail-history"));
  expect(await screen.findByText("admin")).toBeVisible();
  expect(screen.getByText("변경 이력 (1)")).toBeVisible();
  expect(
    within(screen.getByTestId("client-changes")).queryByRole("button"),
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
  expect(screen.getByTestId("client-account-masked")).toHaveTextContent(
    "****5678",
  );
  fireEvent.click(read);
  await waitFor(() =>
    expect(screen.getByTestId("client-account-masked")).toHaveTextContent(
      "12345678",
    ),
  );
  expect(screen.getByText(/예금주 계좌주/)).toBeVisible();
  // 시안: [보기] 뒤 안내 알림
  expect(screen.getByTestId("client-toast")).toHaveTextContent(
    "계좌번호 전체를 보았습니다. 접근 기록에 남습니다.",
  );
  expect(screen.getByTestId("client-toast")).toHaveClass("app-toast-info");
});
it("renders empty state", async () => {
  empty = true;
  render(<ClientPage />);
  expect(await screen.findByTestId("list-empty")).toHaveTextContent(
    "조건에 맞는 결과가 없습니다.",
  );
});
it("renders request failure with retry", async () => {
  failRead = true;
  render(<ClientPage />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "상호를 확인해 주세요.",
  );
  failRead = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  expect(await screen.findByText("거래처A")).toBeVisible();
});
it("status confirmation cancellation writes nothing and failures show reasons", async () => {
  row = { ...row, projectCount: 0, personCount: 0 };
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("list-select-client-a"));
  fireEvent.click(screen.getByTestId("list-bulk-stop"));
  fireEvent.click(screen.getByTestId("client-status-cancel"));
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("list-bulk-stop"));
  fireEvent.click(screen.getByTestId("client-status-save"));
  // 시안: 「n건의 사용을 중지했습니다. (m건은 거래가 남아 제외)」
  expect(await screen.findByTestId("client-toast")).toHaveTextContent(
    "0건의 사용을 중지했습니다. (1건은 거래가 남아 제외)",
  );
  expect(JSON.parse(String(writes()[0][1].body))).toEqual({
    items: [{ clientId: "client-a", modifiedAt: null }],
    useYn: "N",
  });
});
it("registration shows basic fields with guidance and changes category-dependent fields", () => {
  render(<ClientForm codes={codes} onCancel={vi.fn()} onSaved={vi.fn()} />);
  // 시안: 기본 정보 칸은 처음부터 보이고, 안내 띠가 구분별 칸을 알려 준다.
  expect(screen.getByLabelText("상호")).toBeVisible();
  expect(
    screen.getByText(/구분을 고르면 고객사는 세금계산서 이메일/),
  ).toBeVisible();
  expect(screen.queryByLabelText("세금계산서 이메일")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("계좌번호")).not.toBeInTheDocument();
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
  // 시안 clientErrors 문구
  for (const text of [
    "상호를 입력해 주세요.",
    "사업자번호 10자리를 입력해 주세요. (예: 123-45-67890)",
    "은행을 골라 주세요.",
    "계좌번호를 숫자와 하이픈(-)으로 입력해 주세요.",
    "예금주를 입력해 주세요.",
  ])
    expect(screen.getByText(text)).toBeVisible();
  expect(screen.queryByText("필수 입력입니다.")).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
});
it("registration checks a text field when leaving it and a select when choosing (시안)", () => {
  render(<ClientForm codes={codes} onCancel={vi.fn()} onSaved={vi.fn()} />);
  const name = screen.getByLabelText("상호");
  fireEvent.blur(name);
  expect(screen.getByText("상호를 입력해 주세요.")).toBeVisible();
  expect(name).toHaveAttribute("aria-invalid", "true");
  fireEvent.change(name, { target: { value: "새 상호" } });
  fireEvent.blur(name);
  expect(screen.queryByText("상호를 입력해 주세요.")).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("구분"), { target: { value: "CUST" } });
  fireEvent.change(screen.getByLabelText("구분"), { target: { value: "" } });
  expect(screen.getByText("구분을 골라 주세요.")).toBeVisible();
  fireEvent.change(screen.getByLabelText("구분"), { target: { value: "CUST" } });
  expect(screen.queryByText("구분을 골라 주세요.")).not.toBeInTheDocument();
  const email = screen.getByLabelText("세금계산서 이메일");
  fireEvent.blur(email);
  expect(screen.getByText("고객사는 세금계산서 이메일이 꼭 필요합니다.")).toBeVisible();
  fireEvent.change(email, { target: { value: "bad" } });
  fireEvent.blur(email);
  expect(
    screen.getByText("이메일 형식이 맞지 않습니다. (예: tax@company.co.kr)"),
  ).toBeVisible();
  // 칸 아래 알림만 보이고 위 알림 상자는 없다.
  expect(screen.queryByText("입력 값을 확인해 주세요.")).not.toBeInTheDocument();
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
  // 시안 confirmBox: 제목·본문(은행 …끝4자리)·[바꾸기]
  expect(screen.getByText("지급 계좌를 바꿀까요?")).toBeVisible();
  expect(screen.getByTestId("client-account-confirm")).toHaveTextContent(
    "다음 지급부터 새 계좌(은행 …4321)로 보냅니다. 바꾼 기록이 남습니다.",
  );
  expect(screen.getByTestId("client-account-save")).toHaveTextContent("바꾸기");
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("client-account-cancel"));
  expect(screen.getByLabelText("계좌번호")).toHaveValue("87654321");
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("client-form-save"));
  fireEvent.click(screen.getByTestId("client-account-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
});
it("account set for the first time saves without the change confirmation (시안: 계좌가 있던 때만 묻는다)", async () => {
  noAccount = true;
  render(
    <ClientForm
      client={{ ...row, clientTypeCode: "VNDR", paymentAccountSummary: "" }}
      codes={codes}
      onCancel={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await waitFor(() => expect(screen.getByTestId("client-form-save")).toBeEnabled());
  fireEvent.change(screen.getByLabelText("은행"), { target: { value: "004" } });
  fireEvent.change(screen.getByLabelText("계좌번호"), { target: { value: "87654321" } });
  fireEvent.change(screen.getByLabelText("예금주"), { target: { value: "새 예금주" } });
  fireEvent.click(screen.getByTestId("client-form-save"));
  expect(screen.queryByTestId("client-account-confirm")).not.toBeInTheDocument();
  await waitFor(() => expect(writes()).toHaveLength(1));
});
it("edit with nothing changed does not save and reports it (시안: 바뀐 내용이 없습니다.)", async () => {
  const unchanged = vi.fn();
  render(
    <ClientForm
      client={{ ...row, clientTypeCode: "CUST" }}
      codes={codes}
      onCancel={vi.fn()}
      onSaved={vi.fn()}
      onUnchanged={unchanged}
    />,
  );
  fireEvent.click(screen.getByTestId("client-form-save"));
  expect(unchanged).toHaveBeenCalledTimes(1);
  expect(writes()).toHaveLength(0);
});
it("page edit with nothing changed closes the form and shows an info toast", async () => {
  render(<ClientPage />);
  fireEvent.click(await screen.findByText("거래처A"));
  fireEvent.click(await screen.findByTestId("client-detail-edit"));
  await waitFor(() =>
    expect(screen.getByLabelText("계좌번호")).toHaveValue("12345678"),
  );
  fireEvent.click(screen.getByTestId("client-form-save"));
  expect(screen.getByTestId("client-toast")).toHaveTextContent("바뀐 내용이 없습니다.");
  expect(screen.getByTestId("client-toast")).toHaveClass("app-toast-info");
  expect(screen.queryByTestId("client-form")).not.toBeInTheDocument();
  expect(await screen.findByTestId("client-detail-edit")).toBeVisible();
  expect(writes()).toHaveLength(0);
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
  // 바뀐 것이 있어야 저장한다(시안: 없으면 「바뀐 내용이 없습니다.」).
  fireEvent.change(screen.getByLabelText("담당자"), { target: { value: "새 담당" } });
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
  expect(screen.getByTestId("list-row-client-a")).toHaveClass("ls-hl");
  // 시안: 저장 알림과 따로, 같은 상호 안내 알림(안내 색)이 한 장 더 뜬다.
  expect(screen.getByTestId("client-toast")).toHaveTextContent("저장했습니다.");
  expect(screen.getByTestId("client-toast")).not.toHaveTextContent("같은 상호");
  expect(screen.getByTestId("client-warning-toast")).toHaveTextContent(
    "같은 상호의 거래처가 이미 있습니다(사업자번호 987-65-43210). 사업자번호가 달라 등록은 되었습니다(지점 등). 맞는지 확인해 주세요.",
  );
  expect(screen.getByTestId("client-warning-toast")).toHaveClass("app-toast-info");
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

it("contact phone keeps only digits and hyphens (FR-0106)", () => {
  render(<ClientForm codes={codes} onCancel={vi.fn()} onSaved={vi.fn()} />);
  fireEvent.change(screen.getByLabelText("연락처"), {
    target: { value: "02-12ab34" },
  });
  expect(screen.getByLabelText("연락처")).toHaveValue("02-1234");
});
it("leaving a form with unsaved input asks first (FR-0106)", () => {
  const cancel = vi.fn();
  render(<ClientForm codes={codes} onCancel={cancel} onSaved={vi.fn()} />);
  fireEvent.click(screen.getByTestId("client-form-cancel"));
  expect(cancel).toHaveBeenCalledTimes(1);
  cancel.mockClear();
  render(<ClientForm codes={codes} onCancel={cancel} onSaved={vi.fn()} />);
  fireEvent.change(screen.getAllByLabelText("상호")[1], {
    target: { value: "새 상호" },
  });
  fireEvent.click(screen.getAllByTestId("client-form-cancel")[1]);
  expect(cancel).not.toHaveBeenCalled();
  expect(screen.getByTestId("client-leave-confirm")).toHaveTextContent(
    "나가면 입력한 내용이 사라집니다.",
  );
  fireEvent.click(screen.getByTestId("client-leave-go"));
  expect(cancel).toHaveBeenCalledTimes(1);
});

it("거래가 남은 거래처는 중지하지 않고 시안 안내 창으로 알린다", async () => {
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("list-select-client-a"));
  fireEvent.click(screen.getByTestId("list-bulk-stop"));
  expect(screen.getByText("사용을 중지할 수 없습니다")).toBeInTheDocument();
  // 시안 why: 진행 프로젝트가 있으면 「진행 프로젝트 n건」
  expect(screen.getByText("거래처A — 진행 프로젝트 2건")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("client-status-blocked-ok"));
  expect(writes()).toHaveLength(0);
});
it("거래가 남은 이유가 소속 인력뿐이면 「소속 인력 n명」으로 쓴다", async () => {
  row = { ...row, clientTypeCode: "VNDR", projectCount: 0, personCount: 3 };
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("list-select-client-a"));
  fireEvent.click(screen.getByTestId("list-bulk-stop"));
  expect(screen.getByText("거래처A — 소속 인력 3명")).toBeInTheDocument();
});
it("stop confirm lists every name and shows the excluded ones inside the dialog", async () => {
  const other = { ...row, clientId: "client-b", clientName: "거래처B", projectCount: 0, personCount: 0 };
  const third = { ...row, clientId: "client-c", clientName: "거래처C", projectCount: 0, personCount: 0 };
  const list = [row, other, third];
  request.mockImplementation(async (path: string, init?: RequestInit) => {
    if (path.endsWith("use-status"))
      return ok({ changed: ["client-b", "client-c"], failed: [] });
    if (path.startsWith("/api/common/clients?")) return ok({ items: list });
    for (const [group, items] of Object.entries(codes))
      if (path.includes(`/code-groups/${group}/codes`)) return ok({ items });
    throw new Error(`Unexpected request ${path} ${init?.method}`);
  });
  render(<ClientPage />);
  await screen.findByText("거래처C");
  fireEvent.click(screen.getByTestId("list-select-client-a"));
  fireEvent.click(screen.getByTestId("list-select-client-b"));
  fireEvent.click(screen.getByTestId("list-select-client-c"));
  fireEvent.click(screen.getByTestId("list-bulk-stop"));
  const dialog = screen.getByTestId("client-status-confirm");
  expect(dialog).toHaveTextContent("2건의 사용을 중지할까요?");
  expect(dialog).toHaveTextContent(
    "거래처B, 거래처C — 중지하면 새 프로젝트에서 고를 수 없습니다. 기록은 그대로 남습니다.",
  );
  expect(dialog).toHaveTextContent("아래 거래처는 거래가 남아 있어 중지할 수 없습니다.");
  expect(within(dialog).getByText("거래처A — 진행 프로젝트 2건")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("client-status-save"));
  expect(await screen.findByTestId("client-toast")).toHaveTextContent(
    "2건의 사용을 중지했습니다. (1건은 거래가 남아 제외)",
  );
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body.items.map((item: { clientId: string }) => item.clientId)).toEqual(["client-b", "client-c"]);
});
it("zero project and people counts are plain text without a link (시안)", async () => {
  row = { ...row, projectCount: 0, personCount: 0 };
  render(<ClientPage />);
  await screen.findByText("거래처A");
  expect(screen.getByText("0건")).toBeVisible();
  expect(screen.getByText("0명")).toBeVisible();
  expect(screen.queryByTestId("client-projects-client-a")).not.toBeInTheDocument();
  expect(screen.queryByTestId("client-people-client-a")).not.toBeInTheDocument();
});
it("stopped client detail shows the lock banner and resumes from the menu without asking", async () => {
  row = { ...row, useYn: "N" };
  render(<ClientPage />);
  fireEvent.click(await screen.findByText("거래처A"));
  expect(
    await screen.findByText("사용을 중지한 거래처입니다. 새 프로젝트에서 고를 수 없습니다."),
  ).toBeVisible();
  fireEvent.click(screen.getByTestId("client-detail-more"));
  fireEvent.click(screen.getByTestId("client-detail-status"));
  expect(screen.queryByTestId("client-status-confirm")).not.toBeInTheDocument();
  expect(await screen.findByTestId("client-toast")).toHaveTextContent("다시 사용합니다.");
  expect(JSON.parse(String(writes()[0][1].body)).useYn).toBe("Y");
  // 상세는 그대로 열려 있고 다시 읽어 띠가 사라진다.
  await waitFor(() =>
    expect(
      screen.queryByText("사용을 중지한 거래처입니다. 새 프로젝트에서 고를 수 없습니다."),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByTestId("client-detail")).toBeVisible();
});
it("bulk resume with no stopped client only shows an info toast", async () => {
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("list-select-client-a"));
  fireEvent.click(screen.getByTestId("list-bulk-resume"));
  expect(screen.queryByTestId("client-status-confirm")).not.toBeInTheDocument();
  expect(screen.getByTestId("client-toast")).toHaveTextContent(
    "고른 것 중 사용 중지된 거래처가 없습니다.",
  );
  expect(screen.getByTestId("client-toast")).toHaveClass("app-toast-info");
  expect(writes()).toHaveLength(0);
});
it("bulk resume asks with the mockup sentence and reports n건을 다시 사용합니다.", async () => {
  row = { ...row, useYn: "N" };
  render(<ClientPage />);
  await screen.findByText("거래처A");
  fireEvent.click(screen.getByTestId("list-select-client-a"));
  fireEvent.click(screen.getByTestId("list-bulk-resume"));
  const dialog = screen.getByTestId("client-status-confirm");
  expect(dialog).toHaveTextContent("1건을 다시 사용할까요?");
  expect(dialog).toHaveTextContent("거래처A — 상태가 「사용」으로 돌아가고 변경 이력에 남습니다.");
  fireEvent.click(screen.getByTestId("client-status-save"));
  expect(await screen.findByTestId("client-toast")).toHaveTextContent("1건을 다시 사용합니다.");
});

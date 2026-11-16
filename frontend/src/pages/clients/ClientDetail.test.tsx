import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { ClientDetail } from "./ClientDetail";
import type { Client, ClientAssignment } from "./clients";

let client: Client;
let assignments: ClientAssignment[];
let failAssignments: boolean;
let request: ReturnType<typeof vi.fn>;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
beforeEach(() => {
  client = {
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
  assignments = [
    {
      projectId: "P-0001",
      projectName: "같은 이름",
      personName: "김개발",
      companyName: "협력 소속",
      jobName: "개발",
      withdrawalDate: "2030-12-31",
    },
    {
      projectId: "P-0001",
      projectName: "같은 이름",
      personName: "이개발",
      companyName: "자사",
      jobName: "개발",
      withdrawalDate: null,
    },
    {
      projectId: "P-0002",
      projectName: "같은 이름",
      personName: "박설계",
      companyName: "협력 소속",
      jobName: "설계",
      withdrawalDate: "2031-01-31",
    },
  ];
  failAssignments = false;
  request = vi.fn(async (path: string, _init?: RequestInit) => {
    if (path === `/api/common/clients/${client.clientId}`) return ok(client);
    if (path === `/api/common/clients/${client.clientId}/assignments`) {
      if (failAssignments)
        return {
          ok: false,
          status: 400,
          json: async () => ({
            success: false,
            message: "투입 내역을 불러오지 못했습니다.",
            errors: [],
          }),
        };
      return ok({ items: assignments });
    }
    if (path === `/api/common/clients/${client.clientId}/change-history`)
      return ok({ items: [] });
    if (
      path.startsWith("/api/common/code-groups/") ||
      path === "/api/common/parts"
    )
      return ok({ items: [] });
    if (path.startsWith("/api/common/projects/"))
      return {
        ok: false,
        status: 400,
        json: async () => ({
          success: false,
          message: "선택한 프로젝트를 불러오지 못했습니다.",
          errors: [],
        }),
      };
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
function mount() {
  return render(
    <ClientDetail
      clientId={client.clientId}
      initialTab="assignments"
      codes={{}}
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onStatus={vi.fn()}
    />,
  );
}
it("shows assignment failure separately and retries the selected client's real API", async () => {
  failAssignments = true;
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "투입 내역을 불러오지 못했습니다.",
  );
  expect(screen.queryByTestId("client-assignments")).not.toBeInTheDocument();
  expect(
    screen.queryByText("오늘 투입 중인 사람이 없습니다."),
  ).not.toBeInTheDocument();
  failAssignments = false;
  fireEvent.click(screen.getByTestId("client-detail-retry"));
  await screen.findByTestId("client-assignments");
  expect(
    request.mock.calls.filter(([path]) => path.endsWith("/assignments")),
  ).toHaveLength(2);
});
it("renders distinct project badges and only the approved assignment fields", async () => {
  mount();
  const table = await screen.findByTestId("client-assignments");
  expect(screen.getByText(/오늘 기준.*프로젝트 이름/)).toBeVisible();
  const summary = screen.getByTestId("client-assignment-projects");
  expect(summary).toHaveTextContent("프로젝트 2건");
  expect(
    within(summary).getByTestId("client-assignment-project-p-0001"),
  ).toHaveTextContent("같은 이름");
  expect(
    within(summary).getByTestId("client-assignment-project-p-0002"),
  ).toHaveTextContent("같은 이름");
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["프로젝트", "이름", "소속 · 직무", "철수일"]);
  const first = within(screen.getByTestId("client-assignment-0"));
  expect(first.getByText("김개발")).toBeVisible();
  expect(first.getByText("협력 소속")).toBeVisible();
  expect(first.getByText("개발")).toBeVisible();
  expect(first.getByText("2030-12-31")).toBeVisible();
  expect(
    within(screen.getByTestId("client-assignment-1")).getByText("–"),
  ).toBeVisible();
  expect(table).not.toHaveTextContent("MM");
  expect(within(table).getAllByRole("row")).toHaveLength(4);
  expect(request).toHaveBeenCalledWith(
    `/api/common/clients/${client.clientId}/assignments`,
    undefined,
  );
});
it("keeps an empty assignment list empty without fabricated badges", async () => {
  assignments = [];
  mount();
  expect(
    await screen.findByText("오늘 투입 중인 사람이 없습니다."),
  ).toBeVisible();
  expect(screen.queryByTestId("client-assignments")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("client-assignment-project-p-0001"),
  ).not.toBeInTheDocument();
});
it.each([
  [0, "P-0001"],
  [2, "P-0002"],
] as const)(
  "assignment %s opens by project ID and a detail failure leaves the original tab intact",
  async (index, projectId) => {
    const location = window.location.href;
    mount();
    await screen.findByTestId("client-assignments");
    fireEvent.click(screen.getByTestId(`client-project-${index}`));
    expect(await screen.findByTestId("project-detail-error")).toHaveTextContent(
      "선택한 프로젝트를 불러오지 못했습니다.",
    );
    expect(request).toHaveBeenCalledWith(`/api/common/projects/${projectId}`, {
      signal: undefined,
    });
    expect(screen.getByTestId("client-assignments")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("project-detail-dialog-close"));
    expect(screen.queryByTestId("project-detail")).not.toBeInTheDocument();
    expect(screen.getByTestId("client-detail-tab-assignments")).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(window.location.href).toBe(location);
    expect(
      request.mock.calls.filter(([, init]) =>
        ["POST", "PUT"].includes(init?.method ?? ""),
      ),
    ).toHaveLength(0);
  },
);
it("reopening rereads assignments and derives badges and job names from the new response", async () => {
  const first = mount();
  await screen.findByTestId("client-assignments");
  first.unmount();
  assignments = [{ ...assignments[2], jobName: "변경된 직무" }];
  mount();
  const table = await screen.findByTestId("client-assignments");
  expect(screen.getByTestId("client-assignment-projects")).toHaveTextContent(
    "프로젝트 1건",
  );
  expect(
    screen.queryByTestId("client-assignment-project-p-0001"),
  ).not.toBeInTheDocument();
  expect(within(table).getByText("변경된 직무")).toBeVisible();
  expect(within(table).queryByText("김개발")).not.toBeInTheDocument();
  expect(table).not.toHaveTextContent("MM");
  expect(
    request.mock.calls.filter(([path]) => path.endsWith("/assignments")),
  ).toHaveLength(2);
});

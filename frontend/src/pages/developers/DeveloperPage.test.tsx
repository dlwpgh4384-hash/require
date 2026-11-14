import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { DeveloperPage } from "./DeveloperPage";
import { DeveloperForm } from "./DeveloperForm";
import { DeveloperDetail } from "./DeveloperDetail";
import {
  developerPayload,
  type Developer,
  type DeveloperOptions,
  type DeveloperStatus,
} from "./developers";

const viewport = vi.hoisted(() => ({ mobile: false, width: 1280 }));
vi.mock("../../shared/layout/useViewport", () => ({
  useViewport: () => viewport,
}));
const codes = {
  EMP_TYPE: [{ code: "FT", codeName: "정규직" }],
  JOB: [
    { code: "DEV", codeName: "개발" },
    { code: "PM", codeName: "PM" },
  ],
  GRADE: [{ code: "HIGH", codeName: "고급" }],
  JBPS: [{ code: "MEMBER", codeName: "팀원" }],
  DRCT_CLS: [
    { code: "DRCT", codeName: "직접" },
    { code: "INDRCT", codeName: "간접(관리직)" },
  ],
  SKILL: [{ code: "JAVA", codeName: "Java" }],
  WORK_STAT: [
    { code: "IDLE", codeName: "대기" },
    { code: "LEAVE", codeName: "휴직" },
    { code: "RETIRE", codeName: "퇴사" },
  ],
};
const options: DeveloperOptions = {
  codes,
  organizations: [
    { id: "vendor", name: "협력사", useYn: "Y" },
    { id: "stopped", name: "중지 협력사", useYn: "N" },
  ],
  parts: [{ id: "part", name: "영업1", useYn: "Y" }],
};
let row: Developer;
let statuses: DeveloperStatus[];
let request: ReturnType<typeof vi.fn>;
let failed: boolean;
let empty: boolean;
let writeError: string;
let rejection: boolean;
let over: boolean;
const ok = (data: unknown) => ({
  ok: true,
  status: 200,
  json: async () => ({ success: true, meta: {}, data: structuredClone(data) }),
});
const failure = (field = "name", status = 400) => ({
  ok: false,
  status,
  json: async () => ({
    success: false,
    message: "요청을 확인해 주세요.",
    errors: [{ field, message: `${field} 입력 오류` }],
  }),
});
const writes = () =>
  request.mock.calls.filter((c) =>
    ["PUT", "POST"].includes((c[1] as RequestInit | undefined)?.method || ""),
  );
beforeEach(() => {
  viewport.mobile = false;
  failed = false;
  empty = false;
  writeError = "";
  rejection = false;
  over = false;
  row = {
    developerId: "d",
    managementNumber: "K-0001",
    name: "김개발",
    employeeTypeCode: "FT",
    employeeTypeName: "정규직",
    organizationId: "",
    organizationName: "자사",
    primaryJobCode: "DEV",
    primaryJobName: "개발",
    positionCode: "MEMBER",
    directClassCode: "DRCT",
    salesPartId: "part",
    hireDate: "2026-01-01",
    monthlyRate: 100,
    monthlyPay: 50,
    skillCodes: ["JAVA"],
    homeAreaCode: "1168010100",
    homeAddress: "서울 강남구 역삼동",
    workStatusName: "휴직",
    retirementScheduled: false,
    retirementDate: null,
    useYn: "Y",
  };
  statuses = [
    {
      statusId: "1",
      statusName: "휴직",
      startDate: "2026-11-01",
      endDate: null,
      reason: "휴직 사유",
      registeredBy: "admin",
      ongoing: true,
    },
    {
      statusId: null,
      statusName: "재직",
      startDate: "2026-01-01",
      endDate: null,
      reason: null,
      registeredBy: "admin",
    },
  ];
  request = vi.fn(async (path: string, init?: RequestInit) => {
    if (["PUT", "POST"].includes(init?.method || "")) {
      if (writeError) return failure(writeError);
      if (path.endsWith("use-status"))
        return ok({
          processedDeveloperIds: rejection ? [] : ["d"],
          rejected: rejection
            ? [
                {
                  developerId: "d",
                  name: "김개발",
                  reason:
                    "진행 투입이 남아 있습니다. 변경 기록의 조기 철수로 배정을 끝내세요.",
                },
              ]
            : [],
        });
      if (path === "/api/common/uploads")
        return ok({
          uploadId: "upload",
          modifiedAt: null,
          statusName: "검사완료",
          successCount: 1,
          warningCount: 0,
          errorCount: 0,
          items: [],
        });
      if (path.includes("/statuses")) return ok({ items: statuses });
      row = { ...row, ...JSON.parse(String(init?.body)) };
      return ok(row);
    }
    if (failed) return failure("name", 500);
    for (const [group, items] of Object.entries(codes))
      if (path.includes(`/code-groups/${group}/codes`))
        return ok({
          items: items.map((c) => ({ ...c, useYn: "Y", sortOrd: 1 })),
        });
    if (path.startsWith("/api/common/clients?"))
      return ok({
        items: [
          {
            clientId: "vendor",
            clientName: "협력사",
            clientTypeCode: "BOTH",
            useYn: "Y",
          },
          {
            clientId: "stopped",
            clientName: "중지 협력사",
            clientTypeCode: "VNDR",
            useYn: "N",
          },
        ],
      });
    if (path.startsWith("/api/common/parts"))
      return ok({ items: [{ partId: "part", partName: "영업1", useYn: "Y" }] });
    if (path.includes("/areas")) {
      const parent = new URL(path, "http://test.invalid").searchParams.get(
        "parentAreaCode",
      );
      return ok({
        items:
          parent === "1100000000"
            ? [{ areaCode: "1168000000", areaName: "강남구" }]
            : parent === "1168000000"
              ? [{ areaCode: "1168010100", areaName: "역삼동" }]
              : parent === "2600000000"
                ? []
                : [
                    { areaCode: "1100000000", areaName: "서울" },
                    { areaCode: "2600000000", areaName: "부산" },
                  ],
      });
    }
    if (path.endsWith("/statuses")) return ok({ items: statuses });
    if (path.endsWith("/assignments")) return ok({ items: [] });
    if (path.endsWith("/change-history"))
      return ok({
        items: [
          {
            changedAt: "2026-11-15T01:00:00Z",
            fieldName: "이름",
            beforeValue: "이전 이름",
            afterValue: row.name,
            changedBy: "admin",
            reason: null,
          },
        ],
      });
    if (path === "/api/common/developers/d") return ok(row);
    if (path.startsWith("/api/common/developers?"))
      return ok({
        items: empty
          ? []
          : [
              {
                ...row,
                currentProjectName: "대표 프로젝트",
                additionalProjectCount: 2,
                currentMonthMm: over ? 1.01 : 1,
                overOneMm: over,
                withdrawalDate: "2026-12-31",
              },
            ],
      });
    if (path === "/api/common/operating-standards")
      return ok({
        items: [
          {
            standardCode: "CASH_BASE_YM",
            value: "202601",
            standardName: "시작월",
            unit: "",
            effectiveMonth: null,
            closed: false,
            immediate: true,
          },
        ],
        closedMonth: "2026-10",
      });
    if (path === "/api/common/uploads/upload")
      return ok({
        uploadId: "upload",
        statusName: "검사완료",
        successCount: 1,
        warningCount: 0,
        errorCount: 0,
        modifiedAt: null,
        items: [],
      });
    if (path.includes("/code-groups/UPLD_STAT/"))
      return ok({ items: [{ code: "APPLIED", codeName: "반영완료" }] });
    throw new Error(`Unexpected request ${path}`);
  });
  vi.stubGlobal("fetch", request);
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("renders API-backed columns, representative project and exact MM without warning at 1.0", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  expect(screen.getByTestId("developer-page")).toHaveAttribute(
    "data-screen-id",
    "UI-02",
  );
  expect(screen.getByText(/대표 프로젝트 외 2건/)).toBeVisible();
  expect(screen.queryByText("1.0 초과")).not.toBeInTheDocument();
  expect(screen.getByTestId("list-count")).toHaveTextContent("1");
  for (const name of ["소속", "등급", "주 직무", "철수 예정일", "월 단가"])
    expect(screen.getByRole("columnheader", { name })).toBeVisible();
  expect(
    request.mock.calls.some((c) =>
      String(c[0]).includes("includeStopped=false"),
    ),
  ).toBe(true);
});
it("warns only when the API indicates over 1.0", async () => {
  over = true;
  render(<DeveloperPage />);
  expect(await screen.findByText("1.0 초과")).toHaveClass("developer-warning");
});
it("submits each filter, explicitly permits retirement without include stopped", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  for (const [key, value] of [
    ["employeeTypeCode", "FT"],
    ["jobCode", "DEV"],
    ["workStatusName", "퇴사"],
    ["directClassCode", "DRCT"],
  ])
    fireEvent.change(screen.getByTestId(`developer-filter-${key}`), {
      target: { value },
    });
  fireEvent.change(screen.getByTestId("developer-search"), {
    target: { value: "K-0001" },
  });
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() =>
    expect(
      request.mock.calls.some((c) => {
        const p = String(c[0]);
        return (
          p.includes("q=K-0001") &&
          decodeURIComponent(p).includes("workStatusName=퇴사") &&
          p.includes("employeeTypeCode=FT") &&
          p.includes("jobCode=DEV") &&
          p.includes("directClassCode=DRCT")
        );
      }),
    ).toBe(true),
  );
});
it("can include stopped rows and reset filters", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByTestId("developer-include-stopped"));
  fireEvent.click(screen.getByTestId("list-search"));
  await waitFor(() =>
    expect(
      request.mock.calls.some((c) =>
        String(c[0]).includes("includeStopped=true"),
      ),
    ).toBe(true),
  );
  fireEvent.click(screen.getByTestId("list-reset"));
  expect(screen.getByTestId("developer-include-stopped")).not.toBeChecked();
});
it("renders empty state", async () => {
  empty = true;
  render(<DeveloperPage />);
  expect(await screen.findByText("조건에 맞는 결과가 없습니다.")).toBeVisible();
});
it("renders load failure and retries", async () => {
  failed = true;
  render(<DeveloperPage />);
  await screen.findByText("요청을 확인해 주세요.");
  failed = false;
  fireEvent.click(screen.getByTestId("list-retry"));
  expect(await screen.findByText("김개발")).toBeVisible();
});
it("switches to full-screen registration without another URL", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByTestId("developer-create"));
  expect(await screen.findByTestId("developer-form")).toBeVisible();
  expect(screen.queryByTestId("developer-list")).not.toBeInTheDocument();
});
it("hides all four upload/template CTAs on mobile", async () => {
  viewport.mobile = true;
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  expect(
    screen.queryByTestId("developer-upload-actions"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("developer-create")).toBeVisible();
});
it("requires confirmation and reports an all-rejected stop with name and reason", async () => {
  rejection = true;
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByTestId("list-select-d"));
  fireEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  expect(writes()).toHaveLength(0);
  fireEvent.click(screen.getByTestId("developer-use-confirm"));
  expect(await screen.findByTestId("developer-use-result")).toHaveTextContent(
    "사용을 중지할 수 없습니다",
  );
  expect(screen.getByTestId("developer-rejected-d")).toHaveTextContent(
    "김개발 · 진행 투입",
  );
});
it("registration has team-member/direct defaults, no sales-part default and no stopped organization choice", async () => {
  render(
    <DeveloperForm options={options} onSaved={vi.fn()} onCancel={vi.fn()} />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("developer-area-0")).not.toBeDisabled(),
  );
  expect(screen.getByTestId("developer-positionCode")).toHaveValue("MEMBER");
  expect(screen.getByTestId("developer-directClassCode")).toHaveValue("DRCT");
  expect(screen.getByTestId("developer-sales-part")).toHaveValue("");
  expect(
    screen.queryByRole("option", { name: "중지 협력사" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/간접은 관리직만 선택/)).toBeVisible();
  expect(screen.queryByLabelText("연락처")).not.toBeInTheDocument();
});
it("restores address by code, locks management number, excludes identity/address text from update payload", async () => {
  const saved = vi.fn();
  render(
    <DeveloperForm
      developer={row}
      options={options}
      onSaved={saved}
      onCancel={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("developer-area-2")).toHaveValue("1168010100"),
  );
  expect(screen.getByTestId("developer-management-number")).toHaveAttribute(
    "readonly",
  );
  expect(screen.getByTestId("developer-management-number")).toHaveClass(
    "developer-locked",
  );
  fireEvent.change(screen.getByTestId("developer-name"), {
    target: { value: "새 이름" },
  });
  fireEvent.click(screen.getByTestId("developer-form-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body).not.toHaveProperty("managementNumber");
  expect(body).not.toHaveProperty("developerId");
  expect(body).not.toHaveProperty("modifiedAt");
  expect(body).not.toHaveProperty("homeAddress");
  expect(body.homeAreaCode).toBe("1168010100");
});
it("clears subordinate address when the province changes", async () => {
  render(
    <DeveloperForm
      developer={row}
      options={options}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("developer-area-2")).toHaveValue("1168010100"),
  );
  fireEvent.change(screen.getByTestId("developer-area-0"), {
    target: { value: "2600000000" },
  });
  expect(screen.getByTestId("developer-area-2")).toHaveValue("");
  expect(screen.getByTestId("developer-area-1")).toHaveValue("");
});
it("protects dirty data with an explicit discard confirmation", async () => {
  const cancel = vi.fn();
  render(
    <DeveloperForm options={options} onSaved={vi.fn()} onCancel={cancel} />,
  );
  fireEvent.change(screen.getByTestId("developer-name"), {
    target: { value: "수정" },
  });
  fireEvent.click(screen.getByTestId("developer-form-cancel"));
  expect(cancel).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("developer-discard"));
  expect(cancel).toHaveBeenCalledOnce();
});
it("shows ApiError field validation without navigating away", async () => {
  writeError = "name";
  render(
    <DeveloperForm
      developer={row}
      options={options}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(screen.getByTestId("developer-area-2")).toHaveValue("1168010100"),
  );
  fireEvent.click(screen.getByTestId("developer-form-save"));
  expect(await screen.findByText("name 입력 오류")).toBeVisible();
  expect(screen.getByTestId("developer-form")).toBeVisible();
});
it("keeps unresolved skills visible for deliberate resolution", async () => {
  row.skillCodes = ["폐기된 스킬"];
  render(
    <DeveloperForm
      developer={row}
      options={options}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByText(/확인 필요: 폐기된 스킬/)).toBeVisible();
  fireEvent.click(screen.getByTestId("developer-legacy-skill-폐기된 스킬"));
  expect(screen.queryByText(/확인 필요: 폐기된 스킬/)).not.toBeInTheDocument();
});
it("renders four detail tabs and the readonly skills/address", async () => {
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  expect(await screen.findByText("서울 강남구 역삼동")).toBeVisible();
  expect(screen.getAllByRole("tab")).toHaveLength(4);
  expect(screen.getByText("JAVA")).toBeVisible();
  fireEvent.click(screen.getByRole("tab", { name: "투입 이력" }));
  expect(screen.getByText("투입 이력이 없습니다.")).toBeVisible();
  fireEvent.click(screen.getByRole("tab", { name: "변경 이력" }));
  expect(screen.getByText("이전 이름")).toBeVisible();
});
it("highlights ongoing leave and keeps hire-date working row below it", async () => {
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  expect(screen.getByTestId("developer-status-1")).toHaveClass(
    "developer-state-highlight",
  );
  expect(screen.getByTestId("developer-status-hire")).toHaveTextContent(
    "2026-01-01",
  );
  expect(screen.queryByTestId("developer-return-null")).not.toBeInTheDocument();
});
it("does not offer end-date edit for a finished leave", async () => {
  statuses[0].ongoing = false;
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  expect(screen.queryByTestId("developer-end-1")).not.toBeInTheDocument();
});
it("hides status registration after retirement", async () => {
  row.workStatusName = "퇴사";
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  expect(screen.queryByTestId("developer-status-add")).not.toBeInTheDocument();
});
it("shows scheduled retirement without a date attached to the status label", async () => {
  row.retirementScheduled = true;
  row.retirementDate = "2026-12-01";
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  expect(await screen.findByText("휴직 · 퇴사 예정")).toBeVisible();
  expect(
    screen.queryByText("휴직 · 퇴사 예정 2026-12-01"),
  ).not.toBeInTheDocument();
});
it("return cancellation sends no body", async () => {
  statuses[0] = { ...statuses[0], statusName: "재직", ongoing: false };
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  fireEvent.click(screen.getByTestId("developer-return-1"));
  fireEvent.click(screen.getByTestId("developer-status-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(writes()[0][1].body).toBeUndefined();
});
it("retirement cancellation requires a reason before HTTP", async () => {
  statuses[0] = { ...statuses[0], statusName: "퇴사", ongoing: false };
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  fireEvent.click(screen.getByTestId("developer-retire-1"));
  fireEvent.click(screen.getByTestId("developer-status-save"));
  expect(screen.getByTestId("developer-status-error-reason")).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("end-date error stays in its small dialog", async () => {
  writeError = "endDate";
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  fireEvent.click(screen.getByTestId("developer-end-1"));
  fireEvent.change(screen.getByTestId("developer-status-end"), {
    target: { value: "2026-12-31" },
  });
  fireEvent.click(screen.getByTestId("developer-status-save"));
  expect(
    await screen.findByTestId("developer-status-error-endDate"),
  ).toBeVisible();
});
it("B1 requires the unclosed selected month while B8 has no month field", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByTestId("developer-pay-upload"));
  await waitFor(() =>
    expect(screen.getByTestId("developer-pay-month")).not.toBeDisabled(),
  );
  expect(screen.getByTestId("developer-pay-month")).toHaveAttribute(
    "min",
    "2026-11",
  );
  fireEvent.click(screen.getByTestId("developer-upload-close"));
  fireEvent.click(screen.getByTestId("developer-upload"));
  expect(screen.queryByTestId("developer-pay-month")).not.toBeInTheDocument();
});
it("rejects a renamed/unsupported file before upload HTTP", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByTestId("developer-upload"));
  fireEvent.change(screen.getByTestId("developer-upload-file"), {
    target: { files: [new File(["text"], "data.csv")] },
  });
  fireEvent.click(screen.getByTestId("developer-upload-check"));
  expect(await screen.findByText(/10,000,000 bytes 이하/)).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("payload never sends editable lifecycle identity on update", () => {
  expect(developerPayload(row, true)).not.toHaveProperty("managementNumber");
  expect(developerPayload(row, false)).toHaveProperty(
    "managementNumber",
    "K-0001",
  );
});

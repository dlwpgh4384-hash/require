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
let permission: { canManageStatuses: boolean; canCancelPastRetirement: boolean };
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
    skillNames: ["Java"],
    salesPartName: "영업1",
    positionName: "팀원",
    directClassName: "직접",
    modifiedAt: "2026-11-15T00:00:00Z",
    homeAreaCode: "1168010100",
    homeAddress: "서울 강남구 역삼동",
    workStatusName: "휴직",
    retirementScheduled: false,
    retirementDate: null,
    useYn: "Y",
  };
  permission = { canManageStatuses: true, canCancelPastRetirement: true };
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
      if (path.includes("/statuses")) return ok({ items: statuses, ...permission });
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
    if (path.endsWith("/statuses")) return ok({ items: statuses, ...permission });
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
  expect(screen.getByText("대표 프로젝트").closest("td")).toHaveTextContent("대표 프로젝트 외 2건");
  expect(screen.queryByText(/1\.0 초과/)).not.toBeInTheDocument();
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
  // 시안: 「이번 달 1.01 MM · 1.0 초과」
  expect(await screen.findByText(/이번 달 1\.01 MM · 1\.0 초과/)).toBeVisible();
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
it("can include stopped rows from the title bar check box and keeps it on filter reset", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  // 시안: 제목줄의 [사용 중지 포함]은 누르면 바로 다시 조회한다
  fireEvent.click(screen.getByTestId("developer-include-stopped"));
  await waitFor(() =>
    expect(
      request.mock.calls.some((c) =>
        String(c[0]).includes("includeStopped=true"),
      ),
    ).toBe(true),
  );
  fireEvent.click(screen.getByTestId("list-reset"));
  expect(screen.getByTestId("developer-include-stopped")).toBeChecked();
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
    "김개발 — 진행 투입",
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
  expect(screen.getByTestId("developer-salesPartId")).toHaveValue("");
  expect(
    screen.queryByRole("option", { name: "중지 협력사" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/관리직만 간접/)).toBeVisible();
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
  fireEvent.change(screen.getByTestId("developer-name"), {
    target: { value: "새 이름" },
  });
  fireEvent.click(screen.getByTestId("developer-save"));
  await waitFor(() => expect(saved).toHaveBeenCalled());
  const body = JSON.parse(String(writes()[0][1].body));
  expect(body).not.toHaveProperty("managementNumber");
  expect(body).not.toHaveProperty("developerId");
  // 공통 기능: 동시 수정 막기 — 상세의 modifiedAt을 그대로 돌려보낸다
  expect(body.modifiedAt).toBe("2026-11-15T00:00:00Z");
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
  fireEvent.click(screen.getByTestId("developer-cancel"));
  expect(cancel).not.toHaveBeenCalled();
  expect(screen.getByTestId("developer-leave-confirm")).toHaveTextContent("저장하지 않은 내용이 있습니다");
  fireEvent.click(screen.getByTestId("developer-leave-go"));
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
  fireEvent.click(screen.getByTestId("developer-save"));
  expect(await screen.findByText("name 입력 오류")).toBeVisible();
  expect(screen.getByTestId("developer-form")).toBeVisible();
});
it("keeps unresolved skills visible and blocks saving until they match the skill codes", async () => {
  row.skillCodes = ["폐기된 스킬"];
  render(
    <DeveloperForm
      developer={row}
      options={options}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
    />,
  );
  expect(screen.getByTestId("developer-skills")).toHaveValue("폐기된 스킬");
  await waitFor(() =>
    expect(screen.getByTestId("developer-area-2")).toHaveValue("1168010100"),
  );
  fireEvent.click(screen.getByTestId("developer-save"));
  expect(
    await screen.findByText("공통코드 「스킬」에 없는 이름입니다: 폐기된 스킬"),
  ).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("renders four detail tabs and the readonly skills/address", async () => {
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onStop={vi.fn()}
      onNotice={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  expect(await screen.findByText("서울 강남구 역삼동")).toBeVisible();
  expect(screen.getAllByRole("tab")).toHaveLength(4);
  expect(screen.getByText("Java")).toBeVisible();
  expect(screen.getByText("영업1")).toBeVisible();
  fireEvent.click(screen.getByRole("tab", { name: "투입 이력" }));
  expect(screen.getByText("투입 이력이 없습니다.")).toBeVisible();
  fireEvent.click(screen.getByRole("tab", { name: "변경 이력 (1)" }));
  expect(screen.getByText("이전 이름")).toBeVisible();
  // 시간 기준: 변경일은 한국 날짜로(2026-11-15T01:00Z → 2026-11-15)
  expect(screen.getByTestId("developer-change-0")).toHaveTextContent("2026-11-15");
});
it("highlights ongoing leave and keeps hire-date working row below it", async () => {
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onStop={vi.fn()}
      onNotice={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  // 시안: 진행 중인 휴직은 「진행 중」 배지
  expect(screen.getByTestId("developer-status-1")).toHaveTextContent("진행 중");
  expect(screen.getByTestId("developer-status-hire")).toHaveTextContent(
    "2026-01-01",
  );
  expect(screen.queryByTestId("developer-return-null")).not.toBeInTheDocument();
});
it("does not offer end-date edit for a finished leave", async () => {
  statuses[0] = { ...statuses[0], ongoing: false, startDate: "2020-01-01", endDate: "2020-01-31" };
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onStop={vi.fn()}
      onNotice={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  expect(screen.queryByTestId("developer-end-1")).not.toBeInTheDocument();
  expect(screen.getByText("끝남")).toBeVisible();
});
const openStatusTab = async () => {
  await screen.findByText("서울 강남구 역삼동");
  fireEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
};
const renderDetail = (onChanged = vi.fn(), onNotice = vi.fn()) =>
  render(
    <DeveloperDetail developerId="d" onClose={vi.fn()} onEdit={vi.fn()} onStop={vi.fn()} onNotice={onNotice} onChanged={onChanged} />,
  );
it("offers end-date edit for a leave that has not started yet (mockup)", async () => {
  statuses[0] = { ...statuses[0], ongoing: false, startDate: "2099-01-01", endDate: null };
  renderDetail();
  await openStatusTab();
  expect(screen.getByTestId("developer-end-1")).toBeVisible();
  expect(screen.getByText("복직일 미정")).toBeVisible();
  fireEvent.click(screen.getByTestId("developer-end-1"));
  // 시안: 「시작 ~ 복직일 미정 · 사유」, 종료일 칸은 오늘로 시작
  expect(screen.getByText("2099-01-01 ~ 복직일 미정 · 휴직 사유")).toBeVisible();
  expect(screen.getByTestId("developer-status-end")).not.toHaveValue("");
});
it("labels rows the mockup way: closed by return, closed month, admin only", async () => {
  permission.canCancelPastRetirement = false;
  statuses = [
    { statusId: "3", statusName: "퇴사", startDate: "2020-03-01", endDate: null, reason: null, registeredBy: "admin" },
    { statusId: "2", statusName: "재직", startDate: "2020-02-01", endDate: null, reason: "휴직 복귀", registeredBy: "admin", monthClosed: true },
    { statusId: "1", statusName: "휴직", startDate: "2020-01-01", endDate: "2020-01-31", reason: null, registeredBy: "admin", closedByReturn: true },
  ];
  renderDetail();
  await openStatusTab();
  expect(screen.getByText("관리자만 취소")).toBeVisible();
  expect(screen.getByText("마감됨")).toBeVisible();
  expect(screen.getByText("재직으로 닫힘")).toBeVisible();
  expect(screen.queryByTestId("developer-retire-3")).not.toBeInTheDocument();
});
it("hides status actions from people who cannot manage statuses", async () => {
  permission.canManageStatuses = false;
  renderDetail();
  await openStatusTab();
  expect(screen.queryByTestId("developer-status-add")).not.toBeInTheDocument();
  expect(screen.queryByTestId("developer-end-1")).not.toBeInTheDocument();
});
it("tells the list page the mockup message after saving an end date", async () => {
  const onChanged = vi.fn();
  renderDetail(onChanged);
  await openStatusTab();
  fireEvent.click(screen.getByTestId("developer-end-1"));
  fireEvent.change(screen.getByTestId("developer-status-end"), { target: { value: "2026-12-31" } });
  fireEvent.click(screen.getByTestId("developer-status-save"));
  await waitFor(() => expect(onChanged).toHaveBeenCalledWith("휴직 종료일을 바꿨습니다."));
});
it("hides status registration after retirement", async () => {
  row.workStatusName = "퇴사";
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onStop={vi.fn()}
      onNotice={vi.fn()}
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
      onStop={vi.fn()}
      onNotice={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  expect(await screen.findByText("퇴사 예정")).toBeVisible();
  expect(screen.queryByText(/2026-12-01/)).not.toBeInTheDocument();
});
it("return cancellation sends no body", async () => {
  statuses[0] = { ...statuses[0], statusName: "재직", ongoing: false };
  render(
    <DeveloperDetail
      developerId="d"
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onStop={vi.fn()}
      onNotice={vi.fn()}
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
      onStop={vi.fn()}
      onNotice={vi.fn()}
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
      onStop={vi.fn()}
      onNotice={vi.fn()}
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
  // 연월 목록은 한국 달력 이번 달부터 10개월: 날짜를 고정한다
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T03:00:00Z"));
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByTestId("developer-pay-upload"));
  await waitFor(() =>
    expect(screen.getByTestId("developer-pay-month")).not.toBeDisabled(),
  );
  // 시안: 연월은 목록에서 고르고, 마감한 달(2026-10까지)은 「(마감)」으로 못 고른다
  const closed = screen.getByRole("option", { name: "2026-10 (마감)" });
  expect(closed).toBeDisabled();
  fireEvent.click(screen.getByTestId("developer-upload-close"));
  fireEvent.click(screen.getByTestId("developer-upload"));
  expect(screen.queryByTestId("developer-pay-month")).not.toBeInTheDocument();
  vi.useRealTimers();
});
it("rejects a renamed/unsupported file before upload HTTP", async () => {
  render(<DeveloperPage />);
  await screen.findByText("김개발");
  fireEvent.click(screen.getByTestId("developer-upload"));
  fireEvent.change(screen.getByTestId("developer-upload-file"), {
    target: { files: [new File(["text"], "data.csv")] },
  });
  fireEvent.click(screen.getByTestId("developer-upload-check"));
  expect(await screen.findByText(/10MB 이하의 xlsx/)).toBeVisible();
  expect(writes()).toHaveLength(0);
});
it("payload never sends editable lifecycle identity on update", () => {
  expect(developerPayload(row, true)).not.toHaveProperty("managementNumber");
  expect(developerPayload(row, false)).toHaveProperty(
    "managementNumber",
    "K-0001",
  );
});

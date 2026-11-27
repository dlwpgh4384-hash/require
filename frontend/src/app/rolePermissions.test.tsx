import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { RouterProvider } from "react-router-dom";
import type { ShellMenuItem, ShellMenuResponse } from "./NavigationRoot";
import { createAppRouter } from "./router";
import {
  setTestAccount,
  testAccount,
  testPermissions,
  type TestAccount,
} from "../test/testAccountData";
import { READONLY_NOTICE } from "../shared/permission";

/**
 * 로그인(UI-22) ⑦-5·⑦-6·⑦-12: 역할마다 실제 로그인 틀(내 정보 권한표) → 메뉴·단추·입력 잠금.
 * 업무 화면 본문은 단추·입력 칸만 있는 시험 화면으로 바꿔, 공통 권한 틀(ScreenPermissionGate)만 본다.
 */
vi.mock("../pages/common/screenRegistry", () => {
  const Page = ({ id }: { id: string }) => (
    <section role="region" aria-label={`${id} 본문`}>
      <div className="ls-filters">
        <input aria-label="검색어" />
      </div>
      <button type="button">+ 프로젝트 등록</button>
      <button type="button">수정</button>
      <button type="button">삭제</button>
      <button type="button">엑셀 다운로드</button>
      <button type="button">엑셀 업로드</button>
      <button type="button">마감하기</button>
      <button type="button">자세히 보기</button>
      <input aria-label="메모" />
      <form>
        <h2>개발자 등록</h2>
        <input aria-label="새 이름" />
        <button type="button">저장</button>
      </form>
    </section>
  );
  const entry = (id: string) => ({
    id,
    Page: () => <Page id={id} />,
    TitleActions: () => <button type="button">+ 등록</button>,
    rootTestId: `${id}-page`,
    hasInputs: true,
  });
  // 실제 화면 목록처럼 화면마다 같은 부품을 돌려준다(매번 새 부품이면 그릴 때마다 다시 만들어진다).
  const entries = new Map<string, ReturnType<typeof entry>>();
  return {
    screenRegistry: [],
    findScreen: (id?: string | null) => {
      if (!id) return undefined;
      if (!entries.has(id)) entries.set(id, entry(id));
      return entries.get(id);
    },
  };
});

const GROUPS: [string, string][] = [
  ["G001", "대시보드"],
  ["G002", "기준정보"],
  ["G003", "프로젝트·투입"],
  ["G004", "인력 계획"],
  ["G005", "매출"],
  ["G006", "원가·수익"],
  ["G007", "재무·자금"],
  ["G008", "마감"],
  ["G009", "AI"],
  ["G010", "시스템"],
];
/** [메뉴, 대메뉴, 이름, 화면, PC 전용] — DML TB_SY_MENU. */
const LEAVES: [string, string, string, string, "Y" | "N"][] = [
  ["M001", "G001", "대시보드", "UI-16", "N"],
  ["M002", "G002", "거래처·영업 파트", "UI-15", "N"],
  ["M003", "G002", "개발자 풀", "UI-02", "N"],
  ["M004", "G003", "프로젝트", "UI-01", "N"],
  ["M005", "G003", "프로젝트 현황판", "UI-31", "N"],
  ["M006", "G003", "투입 배정", "UI-12", "N"],
  ["M007", "G003", "철수 리스트", "UI-05", "N"],
  ["M008", "G003", "인원 계획 대비 투입", "UI-11", "N"],
  ["M009", "G004", "인력 운영 보드", "UI-28", "N"],
  ["M010", "G004", "인력 수요 예측", "UI-17", "N"],
  ["M011", "G004", "영업 파이프라인", "UI-29", "N"],
  ["M012", "G004", "외부 인력 후보", "UI-33", "N"],
  ["M013", "G005", "청구·입금", "UI-13", "N"],
  ["M014", "G006", "개인 원가·수익표", "UI-03", "N"],
  ["M015", "G006", "프로젝트 원가 분석", "UI-04", "N"],
  ["M016", "G006", "영업 파트 이익금", "UI-19", "N"],
  ["M017", "G006", "단가 재협상 대상", "UI-32", "N"],
  ["M018", "G007", "재무 항목", "UI-10", "Y"],
  ["M019", "G007", "전체 수익 구조", "UI-07", "N"],
  ["M020", "G007", "영업이익표", "UI-08", "N"],
  ["M021", "G007", "순이익표", "UI-09", "N"],
  ["M022", "G007", "사업계획 대비 실적", "UI-18", "N"],
  ["M023", "G007", "자금수지표", "UI-21", "N"],
  ["M024", "G008", "월 마감", "UI-14", "Y"],
  ["M025", "G009", "AI 분석 도우미", "UI-26", "N"],
  ["M026", "G009", "AI 인력 추천", "UI-30", "N"],
  ["M027", "G010", "사용자", "UI-23", "Y"],
  ["M028", "G010", "권한", "UI-24", "Y"],
  ["M029", "G010", "메뉴", "UI-25", "Y"],
  ["M030", "G010", "공통코드", "UI-27", "Y"],
];
/** 서버 메뉴 API가 거르지 않고 모든 메뉴를 보낸다고 보고, 화면 쪽 거르기를 따로 확인한다(55번 점검 C01). */
function allMenus(): ShellMenuResponse {
  const data: ShellMenuItem[] = [
    ...GROUPS.map(([menuId, menuNm], index) => ({
      menuId,
      upperMenuId: null,
      menuNm,
      scrnId: null,
      menuLvl: 1,
      sortOrd: index + 1,
      pcOnlyYn: "N" as const,
      iconNm: null,
    })),
    ...LEAVES.map(([menuId, upperMenuId, menuNm, scrnId, pcOnlyYn], index) => ({
      menuId,
      upperMenuId,
      menuNm,
      scrnId,
      menuLvl: 2,
      sortOrd: index + 1,
      pcOnlyYn,
      iconNm: null,
    })),
  ];
  return {
    success: true,
    data,
    meta: {
      screenAccess: LEAVES.map(([, , , scrnId, pcOnlyYn]) => ({
        scrnId,
        available: true,
        pcOnlyYn,
      })),
    },
  };
}
function respond() {
  const request = vi.fn(async (url: string) => ({
    ok: true,
    status: 200,
    json: async () =>
      url === "/api/common/menus"
        ? allMenus()
        : {
            success: true,
            meta: {},
            data: url.includes("/user-preferences/")
              ? {
                  prefTypeCd: url.split("/").at(-2),
                  prefKey: url.split("/").at(-1),
                  found: false,
                  prefVal: null,
                  modDttm: null,
                }
              : { items: [] },
          },
  }));
  vi.stubGlobal("fetch", request);
  return request;
}

let router: ReturnType<typeof createAppRouter> | undefined;
beforeEach(() => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
});
afterEach(() => {
  cleanup();
  router?.dispose();
  router = undefined;
  window.history.replaceState(null, "", "/");
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
async function open(account: TestAccount, path: string) {
  setTestAccount(account);
  respond();
  window.history.replaceState(null, "", path);
  router = createAppRouter();
  render(<RouterProvider router={router} />);
}
const menu = (menuId: string) =>
  screen.queryByTestId(`menu-item-${menuId.toLowerCase()}`);
async function body(id: string) {
  // 첫 그림과 탭 그림이 바뀔 수 있어, 마지막에 문서에 있는 본문을 다시 찾는다.
  return waitFor(() => {
    const panel = screen.getByRole("region", { name: `${id} 본문` });
    expect(within(panel).getByRole("button", { name: "자세히 보기" })).toBeVisible();
    return panel;
  });
}
const button = (panel: HTMLElement, name: string) =>
  within(panel).queryByRole("button", { name });

describe("역할별 메뉴·동작 권한", () => {
  it("경영진: 시스템 메뉴가 없고, 조회·다운로드만 있어 다른 단추는 그리지 않고 입력을 잠근다", async () => {
    await open(testAccount(["EXEC"]), "/screens/UI-01");
    const panel = await body("UI-01");
    await waitFor(() => expect(menu("M004")).toBeInTheDocument());
    for (const id of ["M027", "M028", "M029", "M030", "G010"])
      expect(menu(id)).not.toBeInTheDocument();
    for (const name of ["+ 프로젝트 등록", "수정", "삭제", "엑셀 업로드", "마감하기", "저장"])
      expect(button(panel, name)).toBeNull();
    expect(button(panel, "엑셀 다운로드")).toBeVisible();
    // 제목줄 [+ 등록]도 같은 권한(등록 N)으로 숨긴다.
    expect(
      within(screen.getByTestId("screen-actions")).queryByRole("button", { name: "+ 등록" }),
    ).toBeNull();
    // 수정 N: 입력 잠금 + 안내 한 줄(SFR-008-02). 검색 칸은 그대로.
    await waitFor(() => expect(within(panel).getByLabelText("메모")).toBeDisabled());
    expect(within(panel).getByLabelText("새 이름")).toBeDisabled();
    expect(within(panel).getByLabelText("검색어")).toBeEnabled();
    // 숨은 대시보드 탭에도 같은 안내가 있다. 지금 보이는 화면에는 한 줄만.
    const notices = screen
      .getAllByTestId("permission-readonly-notice")
      .filter((element) => !element.closest("[hidden]"));
    expect(notices).toHaveLength(1);
    expect(notices[0]).toHaveTextContent(READONLY_NOTICE);
  });

  it("재무: 시스템 메뉴 4개를 포함해 모든 메뉴·단추가 보이고 잠금 안내가 없다", async () => {
    await open(testAccount(["FIN"]), "/screens/UI-01");
    const panel = await body("UI-01");
    await waitFor(() => expect(menu("M030")).toBeInTheDocument());
    for (const [id] of LEAVES) expect(menu(id)).toBeInTheDocument();
    for (const name of ["+ 프로젝트 등록", "수정", "삭제", "엑셀 다운로드", "엑셀 업로드", "마감하기"])
      expect(button(panel, name)).toBeVisible();
    expect(within(panel).getByLabelText("메모")).toBeEnabled();
    expect(screen.queryByTestId("permission-readonly-notice")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "AI에게 묻기" })).toBeVisible();
  });

  it("영업 파트장: 원가·재무·마감 메뉴가 없고, 등록·수정은 되지만 삭제·업로드·확정 단추는 없다", async () => {
    await open(testAccount(["SALES_LDR"]), "/screens/UI-01");
    const panel = await body("UI-01");
    await waitFor(() => expect(menu("M004")).toBeInTheDocument());
    for (const id of ["M014", "M015", "M018", "M019", "M024", "M027", "G008", "G010"])
      expect(menu(id)).not.toBeInTheDocument();
    expect(menu("M016")).toBeInTheDocument();
    for (const name of ["+ 프로젝트 등록", "수정", "엑셀 다운로드"])
      expect(button(panel, name)).toBeVisible();
    for (const name of ["삭제", "엑셀 업로드", "마감하기"]) expect(button(panel, name)).toBeNull();
    expect(within(panel).getByLabelText("메모")).toBeEnabled();
    expect(screen.queryByTestId("permission-readonly-notice")).not.toBeInTheDocument();
  });

  it("PM: 영업 파이프라인 주소로 들어오면 토스트 뒤 대시보드로 가고, 엑셀 단추가 없다", async () => {
    await open(testAccount(["PM"]), "/screens/UI-29");
    expect(await screen.findByTestId("account-forbidden-toast")).toHaveTextContent(
      "이 화면을 볼 권한이 없습니다. 대시보드로 갑니다.",
    );
    await waitFor(() => expect(router!.state.location.pathname).toBe("/screens/UI-16"));
    const panel = await body("UI-16");
    expect(menu("M011")).not.toBeInTheDocument();
    expect(menu("M012")).toBeInTheDocument();
    expect(button(panel, "엑셀 다운로드")).toBeNull();
    expect(button(panel, "+ 프로젝트 등록")).toBeVisible();
    expect(screen.getByRole("button", { name: "AI에게 묻기" })).toBeVisible();
  });

  // 시험 케이스 장부: 메뉴 권한이 없는 역할이 주소로 직접 열면 토스트 뒤 대시보드(처음 값 권한 = DML).
  it.each([
    ["PCP-17", "SALES_LDR", "UI-03", "M014"],
    ["PCP-17", "PM", "UI-03", "M014"],
    ["PCA-20", "PM", "UI-04", "M015"],
    ["PCA-20", "SALES_LDR", "UI-04", "M015"],
    ["RRN-16", "PM", "UI-32", "M017"],
    ["FIN-34", "SALES_LDR", "UI-10", "M018"],
    ["FIN-34", "PM", "UI-10", "M018"],
    ["PFS-29", "PM", "UI-07", "M019"],
    ["OPS-28", "SALES_LDR", "UI-08", "M020"],
    ["OPS-28", "PM", "UI-08", "M020"],
    ["NPS-24", "SALES_LDR", "UI-09", "M021"],
    ["NPS-24", "PM", "UI-09", "M021"],
    ["BPA-27", "SALES_LDR", "UI-18", "M022"],
    ["BPA-27", "PM", "UI-18", "M022"],
    ["CF-31", "SALES_LDR", "UI-21", "M023"],
    ["CF-31", "PM", "UI-21", "M023"],
    ["MCL-18", "SALES_LDR", "UI-14", "M024"],
    ["MCL-18", "PM", "UI-14", "M024"],
    ["USR-21", "EXEC", "UI-23", "M027"],
    ["USR-21", "SALES_LDR", "UI-23", "M027"],
    ["USR-21", "PM", "UI-23", "M027"],
  ] as const)(
    "%s: %s가 %s 주소로 들어오면 토스트 뒤 대시보드로 가고 메뉴 %s가 없다",
    async (_row, role, screenId, menuId) => {
      await open(testAccount([role]), `/screens/${screenId}`);
      expect(await screen.findByTestId("account-forbidden-toast")).toHaveTextContent(
        "이 화면을 볼 권한이 없습니다. 대시보드로 갑니다.",
      );
      await waitFor(() => expect(router!.state.location.pathname).toBe("/screens/UI-16"));
      await body("UI-16");
      expect(menu(menuId)).not.toBeInTheDocument();
    },
  );

  it.each([
    ["WDR-20", "UI-05"],
    ["ASN-24", "UI-12"],
    ["BIL-29", "UI-13"],
    ["PBD-22", "UI-31"],
    ["FIN-34", "UI-10"],
    ["PST-21", "UI-19"],
    ["CLI-25", "UI-15"],
    ["CAN-21", "UI-33"],
    ["AIR-19", "UI-30"],
    ["DEV-36", "UI-02"],
    ["MCL-18", "UI-14"],
    ["OPS-28", "UI-08"],
    ["BPA-27", "UI-18"],
  ] as const)(
    "%s: 경영진은 %s에서 등록·수정·업로드·확정 단추가 없고 엑셀 다운로드만 있다",
    async (_row, screenId) => {
      await open(testAccount(["EXEC"]), `/screens/${screenId}`);
      const panel = await body(screenId);
      for (const name of ["+ 프로젝트 등록", "수정", "삭제", "엑셀 업로드", "마감하기"])
        expect(button(panel, name)).toBeNull();
      expect(button(panel, "엑셀 다운로드")).toBeVisible();
    },
  );

  it.each([
    ["WDR-20", "UI-05"],
    ["MPC-17", "UI-11"],
    ["MPB-21", "UI-28"],
    ["MPF-15", "UI-17"],
    ["ASN-24", "UI-12"],
    ["BIL-29", "UI-13"],
    ["PBD-22", "UI-31"],
  ] as const)("%s: PM은 %s에서 엑셀 다운로드·업로드·확정 단추가 없다", async (_row, screenId) => {
    await open(testAccount(["PM"]), `/screens/${screenId}`);
    const panel = await body(screenId);
    for (const name of ["엑셀 다운로드", "엑셀 업로드", "삭제", "마감하기"])
      expect(button(panel, name)).toBeNull();
  });

  it.each([
    ["PST-21", "UI-19"],
    ["BIL-29", "UI-13"],
    ["CLI-25", "UI-15"],
  ] as const)("%s: 영업 파트장은 %s에서 확정(마감하기)·업로드·삭제 단추가 없다", async (_row, screenId) => {
    await open(testAccount(["SALES_LDR"]), `/screens/${screenId}`);
    const panel = await body(screenId);
    for (const name of ["엑셀 업로드", "삭제", "마감하기"]) expect(button(panel, name)).toBeNull();
    expect(button(panel, "엑셀 다운로드")).toBeVisible();
  });

  it("CF-27: 경영진 메뉴에 자금수지표(M023)가 있고 주소로 열면 그 화면이 열린다", async () => {
    await open(testAccount(["EXEC"]), "/screens/UI-21");
    await body("UI-21");
    expect(menu("M023")).toBeInTheDocument();
    expect(router!.state.location.pathname).toBe("/screens/UI-21");
  });

  it("등록만 있는 화면: 기존 자료 입력은 잠그고 새 자료 창(…등록)만 쓸 수 있다", async () => {
    const account = testAccount(["PM"]);
    account.meta.permissions = account.meta.permissions!.map((row) =>
      row.screenId === "UI-02" ? { ...row, updateYn: "N" } : row,
    );
    await open(account, "/screens/UI-02");
    const panel = await body("UI-02");
    await waitFor(() => expect(within(panel).getByLabelText("메모")).toBeDisabled());
    expect(button(panel, "수정")).toBeNull();
    expect(within(panel).getByLabelText("새 이름")).toBeEnabled();
    expect(button(panel, "저장")).toBeVisible();
    expect(button(panel, "+ 프로젝트 등록")).toBeVisible();
  });

  it("AI 분석 도우미 조회 권한이 없으면 [AI에게 묻기]를 그리지 않는다", async () => {
    const account = testAccount(["PM"]);
    account.meta.permissions = account.meta.permissions!.map((row) =>
      row.screenId === "UI-26" ? { ...row, readYn: "N" } : row,
    );
    await open(account, "/screens/UI-16");
    await body("UI-16");
    expect(menu("M025")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "AI에게 묻기" })).not.toBeInTheDocument();
  });

  it("볼 메뉴가 없는 사용자: 「볼 수 있는 화면이 없습니다」 + 안내 + [로그아웃]", async () => {
    await open(testAccount([], { permissions: testPermissions([]) }), "/screens/UI-16");
    expect(
      await screen.findByRole("heading", { name: "볼 수 있는 화면이 없습니다" }),
    ).toBeVisible();
    expect(screen.getByText("관리자에게 권한을 요청해 주세요.")).toBeVisible();
    expect(screen.getByRole("button", { name: "로그아웃" })).toBeVisible();
    expect(screen.queryByRole("region", { name: "UI-16 본문" })).not.toBeInTheDocument();
  });

  it("403을 받으면 내 정보를 다시 읽어 메뉴를 고치고, 없어진 메뉴의 탭을 닫는다(⑦-12)", async () => {
    await open(testAccount(["FIN"]), "/screens/UI-16");
    await body("UI-16");
    await act(async () => {
      await router!.navigate("/screens/UI-14");
    });
    await body("UI-14");
    expect(screen.getByRole("tab", { name: /월 마감/ })).toBeInTheDocument();
    // 관리자가 재무에서 월 마감 권한을 뺐다 → 다음 요청이 403.
    const reduced = testAccount(["FIN"]);
    reduced.meta.permissions = reduced.meta.permissions!.map((row) =>
      row.screenId === "UI-14" ? { ...row, readYn: "N" } : row,
    );
    setTestAccount(reduced);
    fireEvent(window, new Event("account-permissions-changed"));
    await waitFor(() => expect(menu("M024")).not.toBeInTheDocument());
    await waitFor(() =>
      expect(screen.queryByRole("tab", { name: /월 마감/ })).not.toBeInTheDocument(),
    );
    expect(router!.state.location.pathname).toBe("/screens/UI-16");
  });
});

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { useEffect, useState } from "react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { NavigationRoot, type ShellMenuResponse } from "./NavigationRoot";
import { useScreenDirty } from "../shared/layout/ScreenTabs";

const ids = ["UI-16", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"];
function menus(): ShellMenuResponse {
  return {
    success: true,
    data: [
      { menuId: "G", upperMenuId: null, menuNm: "묶음", scrnId: null, menuLvl: 1, sortOrd: 1, pcOnlyYn: "N", iconNm: null },
      ...ids.map((id, index) => ({
        menuId: `M${index}`,
        upperMenuId: "G",
        menuNm: `화면 ${id}`,
        scrnId: id,
        menuLvl: 2,
        sortOrd: index + 1,
        pcOnlyYn: "N" as const,
        iconNm: null,
      })),
    ],
    meta: { screenAccess: ids.map((id) => ({ scrnId: id, available: true, pcOnlyYn: "N" as const })) },
  };
}
let mobile = false;
beforeEach(() => {
  mobile = false;
  vi.stubGlobal("matchMedia", vi.fn((query: string) => ({
    matches: mobile && query.includes("max-width: 767px"),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })));
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => menus() }));
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

// 시험용 화면: 입력하면 화면 틀에 "저장 안 함"을 알린다.
function Editable({ id }: { id: string }) {
  const setDirty = useScreenDirty();
  const [text, setText] = useState("");
  useEffect(() => setDirty(id, text !== ""), [id, text, setDirty]);
  return <input aria-label={`${id} 입력`} value={text} onChange={(event) => setText(event.target.value)} />;
}
function Position() {
  return <div aria-label="주소">{useLocation().pathname}</div>;
}
function mount(path = "/screens/UI-16") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <NavigationRoot tabs renderScreen={(id) => <Editable id={id} />} />
      <Position />
    </MemoryRouter>,
  );
}
const tabs = () => within(screen.getByTestId("screen-tab-list"));
async function openScreen(id: string) {
  fireEvent.click(await screen.findByRole("link", { name: `화면 ${id}` }));
}

describe("screen tabs (FR-0103)", () => {
  it("opens a new tab once per screen and keeps the dashboard first and fixed", async () => {
    mount();
    await openScreen("S1");
    await openScreen("S1");
    await waitFor(() => expect(tabs().getAllByRole("tab")).toHaveLength(2));
    expect(tabs().getAllByRole("tab")[0]).toHaveTextContent("화면 UI-16");
    expect(screen.queryByTestId("screen-tab-close-ui-16")).not.toBeInTheDocument();
    expect(screen.getByTestId("screen-tab-close-s1")).toBeInTheDocument();
  });

  it("keeps inputs when switching tabs and marks unsaved tabs with ●", async () => {
    mount();
    await openScreen("S1");
    fireEvent.change(await screen.findByLabelText("S1 입력"), { target: { value: "값" } });
    await waitFor(() => expect(screen.getByTestId("screen-tab-s1")).toHaveTextContent("●"));
    fireEvent.click(tabs().getByRole("tab", { name: /화면 UI-16/ }));
    fireEvent.click(tabs().getByRole("tab", { name: /화면 S1/ }));
    expect(screen.getByLabelText("S1 입력")).toHaveValue("값");
  });

  it("asks before closing an unsaved tab and keeps it when cancelled", async () => {
    mount();
    await openScreen("S1");
    fireEvent.change(await screen.findByLabelText("S1 입력"), { target: { value: "값" } });
    fireEvent.click(screen.getByTestId("screen-tab-close-s1"));
    const dialog = await screen.findByTestId("unsaved-dialog");
    expect(dialog).toHaveTextContent("저장하지 않은 내용이 있습니다. 나가면 입력한 내용이 사라집니다.");
    fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
    expect(screen.getByTestId("screen-tab-s1")).toBeInTheDocument();
    expect(screen.getByLabelText("S1 입력")).toHaveValue("값");
    fireEvent.click(screen.getByTestId("screen-tab-close-s1"));
    fireEvent.click(within(await screen.findByTestId("unsaved-dialog")).getByRole("button", { name: "나가기" }));
    await waitFor(() => expect(screen.queryByTestId("screen-tab-s1")).not.toBeInTheDocument());
    expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-16");
  });

  it("asks to close the least recently seen tab at 8 tabs; cancel keeps 8, agree replaces it", async () => {
    mount();
    for (const id of ["S1", "S2", "S3", "S4", "S5", "S6", "S7"]) await openScreen(id);
    await waitFor(() => expect(tabs().getAllByRole("tab")).toHaveLength(8));
    await openScreen("S8");
    const dialog = await screen.findByTestId("tab-limit-dialog");
    expect(dialog).toHaveTextContent("「화면 S1」");
    fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
    await waitFor(() => expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/S7"));
    expect(tabs().getAllByRole("tab")).toHaveLength(8);
    await openScreen("S8");
    fireEvent.click(within(await screen.findByTestId("tab-limit-dialog")).getByRole("button", { name: "닫고 열기" }));
    await waitFor(() => expect(screen.getByTestId("screen-tab-s8")).toBeInTheDocument());
    expect(screen.queryByTestId("screen-tab-s1")).not.toBeInTheDocument();
    expect(tabs().getAllByRole("tab")).toHaveLength(8);
  });

  it("phones have no tabs and ask before leaving a screen with unsaved input", async () => {
    mobile = true;
    mount("/screens/S1");
    expect(screen.queryByTestId("screen-tab-list")).not.toBeInTheDocument();
    fireEvent.change(await screen.findByLabelText("S1 입력"), { target: { value: "값" } });
    fireEvent.click(screen.getByRole("button", { name: "메뉴 접기·펼치기" }));
    await openScreen("S2");
    const dialog = await screen.findByTestId("unsaved-dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
    await waitFor(() => expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/S1"));
    expect(screen.getByLabelText("S1 입력")).toHaveValue("값");
  });

  it("opens a shared #UI address and the logo returns to the dashboard", async () => {
    mount("/#S3");
    await waitFor(() => expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/S3"));
    fireEvent.click(screen.getByTestId("header-logo"));
    await waitFor(() => expect(screen.getByLabelText("주소")).toHaveTextContent("/screens/UI-16"));
  });
});

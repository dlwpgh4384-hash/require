import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { ListPager, ListView, type ListColumn } from ".";
import { Dialog } from "../feedback/Dialog";
import { createPreferenceSaver } from "../../pages/userpreferences/userPreferences";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

// 00-1 코드 리뷰(2026-11-23)에서 고친 공통 부품 동작
interface Row {
  id: string;
  name: string;
}
describe("00-1 review fixes", () => {
  it("clamps the page to the last page when the total shrinks", () => {
    const onPage = vi.fn();
    render(<ListPager page={3} pageSize={100} total={120} onPage={onPage} onPageSize={vi.fn()} />);
    expect(onPage).toHaveBeenCalledWith(2);
  });

  it("Enter on a button inside a row is left to the button and does not open the row", () => {
    const onRowClick = vi.fn();
    const onButton = vi.fn();
    const columns: ListColumn<Row>[] = [
      {
        key: "name",
        label: "이름",
        required: true,
        render: (row) => (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onButton(row.id);
            }}
          >
            {row.name} 단추
          </button>
        ),
      },
    ];
    render(
      <ListView
        screenId="TEST"
        columns={columns}
        visibleColumns={columns}
        hiddenColumns={[]}
        onToggleColumn={vi.fn()}
        onResetColumns={vi.fn()}
        rows={[{ id: "a", name: "가" }]}
        rowKey={(row) => row.id}
        total={1}
        page={1}
        pageSize={20}
        onPage={vi.fn()}
        onPageSize={vi.fn()}
        dense={false}
        onDense={vi.fn()}
        sort={null}
        onSort={vi.fn()}
        onRowClick={onRowClick}
      />,
    );
    fireEvent.keyDown(screen.getByRole("button", { name: "가 단추" }), { key: "Enter" });
    expect(onRowClick).not.toHaveBeenCalled();
    fireEvent.keyDown(screen.getByTestId("list-row-a"), { key: "Enter" });
    expect(onRowClick).toHaveBeenCalledTimes(1);
  });

  it("dialogs focus a textarea first and keep it in the Tab cycle", () => {
    render(
      <Dialog title="확정 취소" onClose={vi.fn()} closable>
        <textarea aria-label="사유" />
        <button type="button">확정 취소</button>
      </Dialog>,
    );
    expect(screen.getByLabelText("사유")).toHaveFocus();
    // 사유 칸이 Tab 목록 안에 있어 [×]로 되돌리지 않고 브라우저가 다음 단추로 옮기게 둔다(막지 않음).
    expect(fireEvent.keyDown(window, { key: "Tab" })).toBe(true);
    expect(screen.getByLabelText("사유")).toHaveFocus();
  });

  it("saves the same preference one after another so a quick second change is kept", async () => {
    const tokens: (string | null)[] = [];
    let version = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) => {
        const body = JSON.parse(String(init?.body));
        tokens.push(body.modDttm);
        if (body.modDttm !== (version ? `T${version}` : null))
          return { ok: false, status: 409, json: async () => ({ success: false }) };
        version += 1;
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { prefTypeCd: "PERIOD", prefKey: "UI-08", found: true, prefVal: body.prefVal, modDttm: `T${version}` },
          }),
        };
      }),
    );
    const saver = createPreferenceSaver("PERIOD", "UI-08");
    const first = saver.save("1월");
    const second = saver.save("2월");
    await Promise.all([first, second]);
    expect(tokens).toEqual([null, "T1"]);
    expect((await second).data.prefVal).toBe("2월");
  });
});

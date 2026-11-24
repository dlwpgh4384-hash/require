import { describe, expect, it } from "vitest";
import { mergeKeepingEdits } from "./mergeKeepingEdits";

type Row = { code: string; name: string; modifiedAt?: string };
const key = (row: Row) => row.code;

describe("mergeKeepingEdits", () => {
  it("keeps my edits with the new version and takes others' changes on rows I did not touch", () => {
    const snapshot: Row[] = [{ code: "A", name: "a", modifiedAt: "1" }, { code: "B", name: "b", modifiedAt: "1" }];
    const draft: Row[] = [{ code: "A", name: "내 수정", modifiedAt: "1" }, { code: "B", name: "b", modifiedAt: "1" }, { code: "N", name: "새 줄" }];
    const latest: Row[] = [{ code: "A", name: "a", modifiedAt: "2" }, { code: "B", name: "남의 수정", modifiedAt: "2" }];
    expect(mergeKeepingEdits(draft, snapshot, latest, key, "modifiedAt")).toEqual([
      { code: "A", name: "내 수정", modifiedAt: "2" },
      { code: "B", name: "남의 수정", modifiedAt: "2" },
      { code: "N", name: "새 줄" },
    ]);
  });
  it("drops rows that no longer exist", () => {
    const rows: Row[] = [{ code: "A", name: "a" }];
    expect(mergeKeepingEdits(rows, rows, [], key)).toEqual([]);
  });
});

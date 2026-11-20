import type { ReactNode } from "react";

/** 계산 방법 한 줄: [항목, 계산식, 참고]. */
export type FormulaRow = [string, ReactNode, ReactNode?];

/**
 * 시안 fxBox(id, rows, foot): 「ⓘ 계산 방법 · 이 화면의 숫자를 이렇게 계산합니다」 접는 칸과
 * 항목/계산식/참고 표, 아래 안내 한 줄. 처음에는 펼쳐 둔다(시안 S.sub['fx'+id]!==0).
 */
export function FormulaBox({
  rows,
  foot,
  testId = "formula-box",
}: {
  rows: FormulaRow[];
  foot?: ReactNode;
  testId?: string;
}) {
  return (
    <details className="ls-fx" open data-testid={testId}>
      <summary>
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
        </svg>{" "}
        계산 방법{" "}
        <span className="ls-cap ls-fx-sub">이 화면의 숫자를 이렇게 계산합니다</span>
      </summary>
      <div className="ls-twrap">
        <table className="ls-t ls-fx-table">
          <thead>
            <tr>
              <th className="ls-fx-name">항목</th>
              <th>계산식</th>
              <th className="ls-fx-ref">참고</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([name, formula, note]) => (
              <tr key={name}>
                <td>{name}</td>
                <td>{formula}</td>
                <td className="ls-cap">{note ?? ""}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {foot && <p className="ls-fx-note">{foot}</p>}
    </details>
  );
}

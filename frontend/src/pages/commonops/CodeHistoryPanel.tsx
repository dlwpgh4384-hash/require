import { useEffect, useState } from "react";
import { formatDateTime } from "../../shared/time";
import {
  getCommonCodeChangeHistory,
  type CodeHistoryItem,
} from "./codeHistory";
import "../../shared/list/list.css";
import "./codeHistory.css";

const kinds = ["전체", "코드", "그룹", "표기 대응", "운영 기준값"] as const;

/** UI-27 / SCR-UI-27 변경 이력 탭. CodePage가 탭 선택과 작업 공간을 소유한다. */
export function CodeHistoryPanel({ refreshKey = 0 }: { refreshKey?: number }) {
  const [items, setItems] = useState<CodeHistoryItem[] | null>(null);
  const [kind, setKind] = useState<string>("전체");
  const [failure, setFailure] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setItems(null);
    setFailure("");
    void getCommonCodeChangeHistory(controller.signal).then(
      (result) => {
        if (!controller.signal.aborted) setItems(result.items);
      },
      (error) => {
        if (!controller.signal.aborted)
          setFailure(
            error instanceof Error
              ? error.message
              : "변경 이력을 불러오지 못했습니다.",
          );
      },
    );
    return () => controller.abort();
  }, [revision, refreshKey]);
  const visible = items?.filter((row) => kind === "전체" || row.kind === kind);
  return (
    <section
      role="tabpanel"
      aria-label="변경 이력"
      className="code-history-panel ls-root"
      data-testid="code-history-panel"
      data-screen-id="SCR-UI-27"
      data-action-id="M25-39"
    >
      <div
        className="code-history-filters"
        role="group"
        aria-label="변경 이력 구분"
        data-action-id="M25-40"
      >
        {kinds.map((value, index) => (
          <button
            type="button"
            key={value}
            className="ls-btn ls-btn-sm"
            data-testid={`code-history-filter-${index}`}
            aria-pressed={kind === value}
            onClick={() => setKind(value)}
          >
            {value}
          </button>
        ))}
      </div>
      {failure ? (
        <p
          role="alert"
          className="field-error"
          data-testid="code-history-error"
        >
          {failure}{" "}
          <button
            type="button"
            className="ls-btn ls-btn-sm"
            data-testid="code-history-retry"
            onClick={() => setRevision((value) => value + 1)}
          >
            재시도
          </button>
        </p>
      ) : !items ? (
        <p role="status" data-testid="code-history-loading">
          변경 이력을 불러오는 중입니다.
        </p>
      ) : !visible?.length ? (
        <p className="ls-empty" data-testid="code-history-empty">
          기록이 없습니다.
        </p>
      ) : (
        <div className="code-history-table-scroll">
          <table className="ls-t" aria-label="공통코드 변경 이력">
            <thead>
              <tr>
                <th scope="col">일시</th>
                <th scope="col">구분</th>
                <th scope="col">항목</th>
                <th scope="col">전</th>
                <th scope="col">후</th>
                <th scope="col">처리자</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((row, index) => (
                <tr
                  key={`${row.changedAt}-${index}`}
                  data-testid={`code-history-row-${index}`}
                >
                  <td>
                    <time dateTime={row.changedAt}>
                      {formatDateTime(row.changedAt)}
                    </time>
                  </td>
                  <td>{row.kind}</td>
                  <td>{row.item}</td>
                  <td>{row.before || "–"}</td>
                  <td>{row.after || "–"}</td>
                  <td>{row.processor}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

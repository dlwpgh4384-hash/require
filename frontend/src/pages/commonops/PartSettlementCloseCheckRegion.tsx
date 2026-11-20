import { useEffect, useState } from "react";
import { ProjectRequestError } from "../projects/projects";
import {
  getCommonPartSettlementCloseCheck,
  type PartSettlementCloseCheck,
} from "./partSettlementCloseChecks";
import "../../shared/list/list.css";

/** UI-14 읽기 전용 영역. 월 선택과 마감 실행은 향후 월 마감 화면 소유자가 제공한다. */
export function PartSettlementCloseCheckRegion({
  yearMonth,
  refreshKey = 0,
}: {
  yearMonth: string;
  refreshKey?: string | number;
}) {
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<PartSettlementCloseCheck | null>(null);
  const [pending, setPending] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setPending(true);
    setResult(null);
    setError("");
    getCommonPartSettlementCloseCheck(yearMonth, controller.signal)
      .then((data) => {
        if (active) setResult(data);
      })
      .catch((failure: unknown) => {
        if (!active) return;
        setError(
          failure instanceof ProjectRequestError
            ? [
                failure.message,
                ...failure.errors.map((field) => field.message),
              ].join(" ")
            : "영업 파트 정산 마감 점검을 불러오지 못했습니다.",
        );
      })
      .finally(() => {
        if (active) setPending(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [yearMonth, refreshKey, reload]);

  return (
    <section
      className="ls-root"
      aria-label="영업 파트 정산 마감 점검"
      aria-busy={pending}
      data-screen-id="UI-14"
      data-testid="part-settlement-close-check-region"
    >
      <div>
        <h3>영업 파트 정산 마감 점검</h3>
        <p>
          {yearMonth} · 월말 사용 상태와 적용 기준이 유효한 비HQ 파트를
          조회합니다.
        </p>
        <button
          type="button"
          className="ls-btn"
          data-testid="part-settlement-close-check-refresh"
          disabled={pending}
          onClick={() => setReload((value) => value + 1)}
        >
          다시 조회
        </button>
      </div>
      {pending && (
        <p role="status" data-testid="part-settlement-close-check-loading">
          점검을 불러오는 중입니다.
        </p>
      )}
      {!pending && error && (
        <p role="alert" data-testid="part-settlement-close-check-error">
          {error}
        </p>
      )}
      {!pending && !error && result && (
        <>
          <p role="status" data-testid="part-settlement-close-check-summary">
            영업 파트 정산 {result.totalCount}개 중 {result.unconfirmedCount}개
            미확정
          </p>
          <div className="ls-twrap">
            <table className="ls-t" aria-label="영업 파트 정산 점검 대상">
              <thead>
                <tr>
                  <th scope="col">파트 ID</th>
                  <th scope="col">정산 상태</th>
                  <th scope="col">수정 시각</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((item) => (
                  <tr
                    key={item.partId}
                    data-testid={`part-settlement-close-check-row-${item.partId}`}
                  >
                    <td>{item.partId}</td>
                    <td>{item.confirmed ? "확정" : "미확정"}</td>
                    <td>
                      {item.modifiedAt ? (
                        <time dateTime={item.modifiedAt}>
                          {item.modifiedAt}
                        </time>
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {result.items.length === 0 && (
            <p className="ls-empty">마감 점검 대상 파트가 없습니다.</p>
          )}
          <p>
            읽기 전용 점검입니다. 정산 확정이나 월 마감 상태를 변경하지
            않습니다.
          </p>
        </>
      )}
    </section>
  );
}

import { useEffect, useState } from "react";
import {
  getCommonPartSettlementCloseChecks,
  type PartSettlementCloseCheck,
} from "./partSettlements";

/** UI-14 owner may embed this read-only region; it does not register or activate that screen. */
export function PartSettlementCloseCheckRegion({
  yearMonth,
}: {
  yearMonth: string;
}) {
  const [data, setData] = useState<PartSettlementCloseCheck | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController();
    setData(null);
    setError("");
    getCommonPartSettlementCloseChecks(yearMonth, abort.signal)
      .then((response) => {
        if (!abort.signal.aborted) setData(response.data);
      })
      .catch((failure) => {
        if (!abort.signal.aborted)
          setError(
            failure instanceof Error
              ? failure.message
              : "정산 점검을 불러오지 못했습니다.",
          );
      });
    return () => abort.abort();
  }, [yearMonth]);
  return (
    <section
      data-testid="part-settlement-close-check-region"
      aria-label="영업 파트 정산 점검"
    >
      <h3>영업 파트 정산 점검 · {yearMonth}</h3>
      {error ? (
        <p role="alert">{error}</p>
      ) : !data ? (
        <p role="status">점검 중입니다.</p>
      ) : (
        <>
          <p>미확정 {data.unconfirmedCount}개</p>
          <table>
            <thead>
              <tr>
                <th>파트</th>
                <th>정산 상태</th>
              </tr>
            </thead>
            <tbody>
              {(data.items ?? []).map((item) => (
                <tr
                  key={item.partId}
                  data-testid={`part-settlement-close-${item.partId}`}
                >
                  <td>{item.partId}</td>
                  <td>{item.confirmed ? "확정" : "미확정"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}

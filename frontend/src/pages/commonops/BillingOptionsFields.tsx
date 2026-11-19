import { useEffect, useState } from "react";
import { getCommonBillOptions, type BillingOptions } from "./billingLookups";

/** UI-13-M1: supply/default fields remain owned by the billing form, not a second form. */
export function useBillingOptions(projectId?: string, billMonth?: string) {
  const [data, setData] = useState<BillingOptions | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setData(null);
    setError("");
    void getCommonBillOptions({ projectId, billMonth }, controller.signal).then(
      (value) => {
        if (!controller.signal.aborted) setData(value);
      },
      (failure) => {
        if (!controller.signal.aborted)
          setError(
            failure instanceof Error
              ? failure.message
              : "청구 선택지를 불러오지 못했습니다.",
          );
      },
    );
    return () => controller.abort();
  }, [projectId, billMonth, revision]);
  return {
    data,
    error,
    loading: !data && !error,
    retry: () => setRevision((value) => value + 1),
  };
}

export function BillingOptionsFields({
  projectId,
  billMonth,
  fixed = false,
  onProject,
  onMonth,
}: {
  projectId: string;
  billMonth: string;
  fixed?: boolean;
  onProject: (id: string) => void;
  onMonth: (month: string) => void;
}) {
  // Keep the full eligible list while another hook/form requests the selected project's estimate.
  const { data, error, loading, retry } = useBillingOptions(
    undefined,
    billMonth || undefined,
  );
  return (
    <fieldset data-testid="billing-options-fields" data-screen-id="UI-13-M1">
      <legend>청구 대상</legend>
      <label>
        프로젝트
        <select
          data-testid="billing-project"
          aria-label="프로젝트"
          value={projectId}
          disabled={fixed || loading || !!error}
          onChange={(event) => onProject(event.target.value)}
        >
          <option value="">프로젝트 선택</option>
          {(data?.projects ?? []).map((project) => (
            <option key={project.projectId} value={project.projectId}>
              {project.projectName} · {project.customerName}
            </option>
          ))}
        </select>
      </label>
      <label>
        청구월
        <input
          type="month"
          data-testid="billing-month"
          aria-label="청구월"
          disabled={fixed}
          value={
            billMonth ? `${billMonth.slice(0, 4)}-${billMonth.slice(4)}` : ""
          }
          onChange={(event) => onMonth(event.target.value.replace("-", ""))}
        />
      </label>
      {loading && <p role="status">청구 선택지를 불러오는 중입니다.</p>}
      {error && (
        <p role="alert">
          {error}{" "}
          <button
            type="button"
            data-testid="billing-options-retry"
            onClick={retry}
          >
            재시도
          </button>
        </p>
      )}
      {data && !(data.projects ?? []).length && <p>조회 결과가 없습니다</p>}
    </fieldset>
  );
}

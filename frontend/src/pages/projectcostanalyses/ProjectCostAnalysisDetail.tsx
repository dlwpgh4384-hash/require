import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DetailPanel } from "../../shared/list/DetailPanel";
import { ConfirmDialog } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { ProjectRequestError } from "../projects/projects";
import { formatMoney, type MoneyUnit } from "../../shared/list/moneyUnit";
import {
  getCommonProjectCostAnalysisMonthlyCosts,
  getCommonProjectCostAnalysisWorkforce,
  getCommonProjectCostAnalysisCauses,
  getCommonProjectCostAnalysisSimulation,
  type CommonProjectCostAnalysisRow,
  type ProjectCostAnalysisPeriod,
  type ProjectCostAnalysisSimulationInput,
  type CommonProjectCostAnalysisSimulation,
} from "./projectCostAnalyses";

function useProjectCostAnalysisRegion<T>(
  load: (signal: AbortSignal) => Promise<T>,
  key: string,
  enabled = true,
) {
  const loader = useRef(load);
  loader.current = load;
  const [state, setState] = useState<{
    data?: T;
    error?: string;
    loading: boolean;
  }>({ loading: enabled });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    setState({ loading: true });
    loader
      .current(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ data, loading: false });
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setState({
            error:
              failure instanceof Error
                ? failure.message
                : "불러오지 못했습니다.",
            loading: false,
          });
      });
    return () => controller.abort();
  }, [key, enabled, version]);
  return { ...state, retry: () => setVersion((value) => value + 1) };
}
function RegionStatus({
  loading,
  error,
  retry,
  testId,
}: {
  loading: boolean;
  error?: string;
  retry: () => void;
  testId: string;
}) {
  return (
    <>
      {loading && <p role="status">불러오는 중입니다.</p>}
      {error && (
        <p role="alert">
          {error}{" "}
          <button type="button" data-testid={testId} onClick={retry}>
            다시 시도
          </button>
        </p>
      )}
    </>
  );
}
export function ProjectCostAnalysisDetail({
  row,
  period,
  unit,
  onClose,
}: {
  row: CommonProjectCostAnalysisRow;
  period: ProjectCostAnalysisPeriod;
  unit: MoneyUnit;
  onClose: () => void;
}) {
  const key = `${row.projectId}:${period.year}:${period.baseMonth}`;
  const monthly = useProjectCostAnalysisRegion(
    (signal) =>
      getCommonProjectCostAnalysisMonthlyCosts(row.projectId, period, signal),
    key,
  );
  const workforce = useProjectCostAnalysisRegion(
    (signal) =>
      getCommonProjectCostAnalysisWorkforce(row.projectId, period, signal),
    key,
  );
  const [tab, setTab] = useState("monthly");
  const [causesOpen, setCausesOpen] = useState(false);
  const causes = useProjectCostAnalysisRegion(
    (signal) =>
      getCommonProjectCostAnalysisCauses(row.projectId, period, signal),
    key,
    causesOpen,
  );
  const [simulationOpen, setSimulationOpen] = useState(false);
  const empty: ProjectCostAnalysisSimulationInput = {
    headcountChange: "",
    monthlyCostPerPerson: "",
    months: "",
    monthlyPricePerPerson: "",
  };
  const [input, setInput] = useState(empty);
  const [result, setResult] = useState<CommonProjectCostAnalysisSimulation>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [confirmation, setConfirmation] = useState<null | (() => void)>(null);
  const [message, setMessage] = useState("");
  const form = useRef<HTMLFormElement>(null);
  const markDirty = useScreenDirty();
  const navigate = useNavigate();
  const dirty =
    simulationOpen && Object.values(input).some((value) => value !== "");
  useEffect(() => {
    markDirty("UI-04", dirty);
    return () => markDirty("UI-04", false);
  }, [dirty, markDirty]);
  function leave(action: () => void) {
    if (dirty) setConfirmation(() => action);
    else action();
  }
  async function calculate() {
    const next: Record<string, string> = {};
    if (
      input.headcountChange === "" ||
      !Number.isFinite(Number(input.headcountChange))
    )
      next.headcountChange = "유한한 인원 변화를 입력해 주세요.";
    if (
      input.monthlyCostPerPerson === "" ||
      !Number.isFinite(Number(input.monthlyCostPerPerson)) ||
      Number(input.monthlyCostPerPerson) < 0
    )
      next.monthlyCostPerPerson = "1인 월 원가를 0 이상으로 입력해 주세요.";
    if (!Number.isInteger(Number(input.months)) || Number(input.months) <= 0)
      next.months = "적용 개월을 양의 정수로 입력해 주세요.";
    if (
      ["MO", "DP"].includes(row.contractTypeCode) &&
      (input.monthlyPricePerPerson === "" ||
        !Number.isFinite(Number(input.monthlyPricePerPerson)) ||
        Number(input.monthlyPricePerPerson) < 0)
    )
      next.monthlyPricePerPerson = "1인 단가를 0 이상으로 입력해 주세요.";
    setErrors(next);
    setResult(undefined);
    setMessage("");
    if (Object.keys(next).length) {
      form.current
        ?.querySelector<HTMLInputElement>(`[name="${Object.keys(next)[0]}"]`)
        ?.focus();
      return;
    }
    setPending(true);
    try {
      setResult(
        await getCommonProjectCostAnalysisSimulation(
          row.projectId,
          period,
          input,
        ),
      );
    } catch (failure) {
      const fields =
        failure instanceof ProjectRequestError
          ? Object.fromEntries(
              failure.errors.map((error) => [error.field, error.message]),
            )
          : {};
      setErrors(fields);
      setMessage(
        failure instanceof Error ? failure.message : "계산하지 못했습니다.",
      );
      form.current
        ?.querySelector<HTMLInputElement>(`[name="${Object.keys(fields)[0]}"]`)
        ?.focus();
    } finally {
      setPending(false);
    }
  }
  function go(target: string, context: string) {
    if (target !== "UI-11" && target !== "UI-32") {
      setMessage("이동할 화면을 확인할 수 없습니다.");
      return;
    }
    try {
      const value: unknown = JSON.parse(context);
      if (!value || typeof value !== "object") throw new Error();
      const state = value as ProjectCostAnalysisPeriod & { projectId: string };
      if (
        state.projectId !== row.projectId ||
        state.year !== period.year ||
        state.baseMonth !== period.baseMonth
      )
        throw new Error();
      leave(() =>
        navigate(`/screens/${target}`, {
          state: {
            projectId: state.projectId,
            year: state.year,
            baseMonth: state.baseMonth,
          },
        }),
      );
    } catch {
      setMessage("프로젝트 이동 문맥을 확인할 수 없습니다.");
    }
  }
  const monthlyContent = (
    <section data-testid="project-cost-monthly-panel">
      <RegionStatus {...monthly} testId="project-cost-monthly-retry" />
      {monthly.data && (
        <>
          <table>
            <thead>
              <tr>
                <th>월</th>
                <th>원가</th>
                {row.contractTypeCode === "TK" && <th>배분 기준 매출(참고)</th>}
              </tr>
            </thead>
            <tbody>
              {monthly.data.items.map((item) => (
                <tr
                  key={item.yearMonth}
                  data-testid={`project-cost-month-${item.yearMonth}`}
                >
                  <td>{item.yearMonth}</td>
                  <td>{formatMoney(item.cost, unit)}</td>
                  {row.contractTypeCode === "TK" && (
                    <td>
                      {item.distributionRevenue === null
                        ? ""
                        : formatMoney(item.distributionRevenue, unit)}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {monthly.data.items.length === 0 && <p>월별 원가가 없습니다.</p>}
        </>
      )}
    </section>
  );
  const workforceContent = (
    <section data-testid="project-cost-workforce-panel">
      <RegionStatus {...workforce} testId="project-cost-workforce-retry" />
      {workforce.data && (
        <>
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>직무</th>
                <th>MM</th>
              </tr>
            </thead>
            <tbody>
              {workforce.data.items.map((item, index) => (
                <tr
                  key={`${item.developerId}-${item.jobName}`}
                  data-testid={`project-cost-workforce-${index}`}
                >
                  <td>{item.developerName}</td>
                  <td>{item.jobName}</td>
                  <td>{item.monthlyMm}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {workforce.data.items.length === 0 && <p>투입 인력이 없습니다.</p>}
        </>
      )}
    </section>
  );
  return (
    <>
      <DetailPanel
        title="선택 프로젝트 원가 상세"
        sub={row.projectName}
        onClose={() => leave(onClose)}
        testId="project-cost-detail"
      >
        <div className="ls-tabs" role="tablist" aria-label="프로젝트 원가 상세">
          <button
            type="button"
            role="tab"
            className={`ls-tb${tab === "monthly" ? " ls-on" : ""}`}
            aria-selected={tab === "monthly"}
            data-testid="project-cost-monthly-tab"
            onClick={() => setTab("monthly")}
          >
            월별 원가{monthly.data ? ` (${monthly.data.items.length})` : ""}
          </button>
          <button
            type="button"
            role="tab"
            className={`ls-tb${tab === "workforce" ? " ls-on" : ""}`}
            aria-selected={tab === "workforce"}
            data-testid="project-cost-workforce-tab"
            onClick={() => setTab("workforce")}
          >
            투입 인력{workforce.data ? ` (${workforce.data.items.length})` : ""}
          </button>
        </div>
        <div role="tabpanel" data-testid="project-cost-detail-tab-panel">
          {tab === "monthly" ? monthlyContent : workforceContent}
        </div>
        <button
          type="button"
          className="ls-btn"
          data-testid="project-cost-causes-open"
          onClick={() => setCausesOpen((value) => !value)}
        >
          원가 초과 원인 열기
        </button>
        {causesOpen && (
          <section
            data-testid="project-cost-causes"
            className="project-cost-causes"
          >
            <RegionStatus {...causes} testId="project-cost-causes-retry" />
            {causes.data && (
              <>
                {causes.data.referenceNotice && (
                  <p>{causes.data.referenceNotice}</p>
                )}
                {causes.data.progressExcessAmount !== null && (
                  <p>
                    진척 대비 초과 금액{" "}
                    {formatMoney(causes.data.progressExcessAmount, unit)}
                  </p>
                )}
                {causes.data.remainderExplanation && (
                  <p>{causes.data.remainderExplanation}</p>
                )}
                {causes.data.items.length === 0 && (
                  <p>원가 초과 원인이 없습니다.</p>
                )}
                {causes.data.items.map((item, index) => (
                  <article
                    key={item.causeName}
                    data-testid={`project-cost-cause-${index}`}
                  >
                    <strong>
                      {item.causeName} · {formatMoney(item.amount, unit)}
                    </strong>
                    <div
                      className={`project-cost-cause-bar ${causes.data?.primaryBarColor === "빨강" ? "is-red" : "is-grey"}`}
                      style={{
                        width: `${Math.max(1, (100 * item.amount) / Math.max(1, ...(causes.data?.items.map((cause) => cause.amount) ?? [])))}%`,
                      }}
                    />
                    <p>
                      {item.task} ·{" "}
                      {item.targetScreenId === "UI-11" ? "PM" : "파트장"} (안내)
                    </p>
                    <button
                      type="button"
                      data-testid={`project-cost-cause-go-${index}`}
                      onClick={() =>
                        go(item.targetScreenId, item.navigationContext)
                      }
                    >
                      이동
                    </button>
                  </article>
                ))}
              </>
            )}
          </section>
        )}
        <button
          type="button"
          className="ls-btn"
          data-testid="project-cost-simulation-open"
          onClick={() => setSimulationOpen(true)}
        >
          마진 시뮬레이션 열기
        </button>
        {simulationOpen && (
          <section data-testid="project-cost-simulation">
            <h3>마진 시뮬레이션 · 가상</h3>
            {["MO", "DP"].includes(row.contractTypeCode) && (
              <p>월단위·파견은 한 달 기준</p>
            )}
            <form
              ref={form}
              onSubmit={(event) => {
                event.preventDefault();
                void calculate();
              }}
              noValidate
            >
              {(
                [
                  ["headcountChange", "인원 변화"],
                  ["monthlyCostPerPerson", "1인 월 원가"],
                  ["months", "적용 개월"],
                  ...(row.contractTypeCode === "TK" ||
                  row.contractTypeCode === "IN"
                    ? []
                    : [["monthlyPricePerPerson", "1인 단가"]]),
                ] as [keyof ProjectCostAnalysisSimulationInput, string][]
              ).map(([field, label], index) => (
                <label key={field}>
                  {label}
                  <input
                    name={field}
                    aria-label={label}
                    data-testid={`project-cost-simulation-input-${index}`}
                    type="number"
                    step={field === "months" ? "1" : "any"}
                    value={input[field]}
                    aria-invalid={!!errors[field]}
                    onChange={(event) => {
                      setInput({ ...input, [field]: event.target.value });
                      setResult(undefined);
                    }}
                  />
                  {errors[field] && <span role="alert">{errors[field]}</span>}
                </label>
              ))}
              <button
                type="submit"
                data-testid="project-cost-simulation-calculate"
                disabled={pending}
              >
                {pending ? "계산 중" : "계산"}
              </button>
              <button
                type="button"
                data-testid="project-cost-simulation-close"
                onClick={() =>
                  leave(() => {
                    setSimulationOpen(false);
                    setInput(empty);
                    setResult(undefined);
                    setErrors({});
                  })
                }
              >
                취소·닫기
              </button>
            </form>
            {message && <p role="alert">{message}</p>}
            {result && (
              <div data-testid="project-cost-simulation-result">
                <p>가상 · 업무 자료에 저장하지 않습니다.</p>
                <dl>
                  <dt>전 마진율</dt>
                  <dd>{result.marginRateBefore.toFixed(2)}%</dd>
                  <dt>가상 마진율</dt>
                  <dd>{result.marginRateAfter.toFixed(2)}%</dd>
                  <dt>변화</dt>
                  <dd>{result.marginRateChange.toFixed(2)}%p</dd>
                </dl>
                <p>{result.calculationBasis}</p>
              </div>
            )}
          </section>
        )}
      </DetailPanel>
      {confirmation && (
        <ConfirmDialog
          title="저장하지 않은 내용이 있습니다"
          description="나가면 입력한 내용이 사라집니다."
          onCancel={() => setConfirmation(null)}
          onProceed={() => {
            const action = confirmation;
            setConfirmation(null);
            action();
          }}
        />
      )}
    </>
  );
}

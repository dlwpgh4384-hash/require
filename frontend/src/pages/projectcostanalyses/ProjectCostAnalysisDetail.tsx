import { useEffect, useRef, useState, type ChangeEvent, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { DetailPanel, Banner, type DetailTab } from "../../shared/list/DetailPanel";
import { Badge } from "../../shared/list/ListView";
import { KpiCards, KpiCard } from "../../shared/list/KpiCards";
import { ProjectRequestError, typeLabel } from "../projects/projects";
import { ProjectDetail } from "../projects/ProjectDetail";
import { useOpenProjectEdit } from "../projects/projectEdit";
import { formatKoreanMoney, formatMoney } from "../../shared/list/moneyUnit";
import { useCanViewScreen, useSensitiveView } from "../../shared/permission";
import {
  formatMm,
  formatPercent,
  formatPoint,
  formatYearMonth,
  getCommonProjectCostAnalysisMonthlyCosts,
  getCommonProjectCostAnalysisWorkforce,
  getCommonProjectCostAnalysisCauses,
  getCommonProjectCostAnalysisSimulation,
  type CommonProjectCostAnalysisRow,
  type ProjectCostAnalysisPeriod,
  type ProjectCostAnalysisSimulationInput,
  type CommonProjectCostAnalysisSimulation,
} from "./projectCostAnalyses";

function useRegion<T>(load: (signal: AbortSignal) => Promise<T>, key: string, enabled = true) {
  const loader = useRef(load);
  loader.current = load;
  const [state, setState] = useState<{ data?: T; error?: string; loading: boolean }>({
    loading: enabled,
  });
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
            error: failure instanceof Error ? failure.message : "불러오지 못했습니다.",
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
          <button type="button" className="ls-btn ls-btn-sm" data-testid={testId} onClick={retry}>
            다시 시도
          </button>
        </p>
      )}
    </>
  );
}
const won = (value: number | null | undefined) => formatMoney(value, "원");
const dash = <span className="ls-cap">–</span>;
/** 숫자 칸: 숫자 아닌 글자는 입력 즉시 지운다(FR-0106). 인원 변화만 앞의 「−」를 받는다. */
const digits = (value: string, signed = false) => {
  const negative = signed && /^\s*[-−]/.test(value);
  const number = value.replace(/[^\d]/g, "");
  return (negative ? "-" : "") + number;
};
const comma = (value: string) =>
  value === "" || value === "-" ? value : Number(value).toLocaleString("ko-KR");
const roundTenThousand = (value: number | null | undefined, fallback: number) =>
  String(Math.round((value ?? fallback) / 1e4) * 1e4);

export function ProjectCostAnalysisDetail({
  row,
  period,
  targetMarginRate,
  monthlyMarginNotice,
  onClose,
}: {
  row: CommonProjectCostAnalysisRow;
  period: ProjectCostAnalysisPeriod;
  targetMarginRate: number;
  monthlyMarginNotice: string | null;
  onClose: () => void;
}) {
  // 투입 인력의 배분 원가는 민감 칸을 볼 수 있을 때만(아니면 서버가 보내지 않는다).
  const sensitive = useSensitiveView();
  const key = `${row.projectId}:${period.year}:${period.baseMonth}`;
  const internal = row.contractTypeCode === "IN";
  const turnkey = row.contractTypeCode === "TK";
  const [tab, setTab] = useState("monthly");
  const [projectOpen, setProjectOpen] = useState(false);
  const openEdit = useOpenProjectEdit();
  const navigate = useNavigate();
  const [message, setMessage] = useState("");
  const monthly = useRegion(
    (signal) => getCommonProjectCostAnalysisMonthlyCosts(row.projectId, period, signal),
    key,
  );
  const workforce = useRegion(
    (signal) => getCommonProjectCostAnalysisWorkforce(row.projectId, period, signal),
    key,
  );
  const [causesWanted, setCausesWanted] = useState(false);
  const causes = useRegion(
    (signal) => getCommonProjectCostAnalysisCauses(row.projectId, period, signal),
    key,
    causesWanted,
  );
  // 시안 기본값: 인원 1명, 1인 월 원가 = MM당 평균 원가, 개월 = 턴키는 남은 기간·월단위는 1, 월 단가 = MM당 평균 단가
  const [input, setInput] = useState<ProjectCostAnalysisSimulationInput>({
    headcountChange: "1",
    monthlyCostPerPerson: roundTenThousand(row.costPerMm, 6_000_000),
    months: String(turnkey ? Math.max(1, row.remainingMonths) : 1),
    monthlyPricePerPerson: roundTenThousand(row.unitPricePerMm, 7_000_000),
  });
  const [result, setResult] = useState<CommonProjectCostAnalysisSimulation>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const simulationBlocked = !turnkey && !!monthlyMarginNotice;
  useEffect(() => {
    if (tab !== "simulation" || internal || simulationBlocked) return;
    const next: Record<string, string> = {};
    if (input.headcountChange === "" || input.headcountChange === "-")
      next.headcountChange = "인원 변화를 입력해 주세요.";
    if (input.monthlyCostPerPerson === "") next.monthlyCostPerPerson = "1인 월 원가를 입력해 주세요.";
    if (input.months === "" || Number(input.months) < 1) next.months = "적용 개월을 1 이상으로 입력해 주세요.";
    // 말이 되는 범위만(2026-11-20): 개월은 턴키 남은 기간·월단위 12개월까지, 인원은 지금 투입 인원까지만 뺌
    else if (Number(input.months) > (turnkey ? Math.max(1, row.remainingMonths) : 12))
      next.months = turnkey
        ? `남은 기간 ${Math.max(1, row.remainingMonths)}개월 이하로 입력해 주세요.`
        : "12개월 이하로 입력해 주세요.";
    const currentPeople = new Set((workforce.data?.items ?? []).map((p) => p.developerId)).size;
    if (!next.headcountChange && workforce.data && Number(input.headcountChange) < -currentPeople)
      next.headcountChange = `지금 투입 인원 ${currentPeople}명까지만 뺄 수 있습니다.`;
    if (!turnkey && input.monthlyPricePerPerson === "")
      next.monthlyPricePerPerson = "추가 인원 1인 월 단가를 입력해 주세요.";
    setErrors(next);
    if (Object.keys(next).length) {
      setResult(undefined);
      return;
    }
    // 입력하면 바로 다시 계산한다(시안 data-sim). 짧게 기다렸다가 마지막 값만 보낸다.
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      getCommonProjectCostAnalysisSimulation(
        row.projectId,
        period,
        turnkey ? { ...input, monthlyPricePerPerson: "" } : input,
        controller.signal,
      )
        .then((value) => {
          if (!controller.signal.aborted) setResult(value);
        })
        .catch((failure) => {
          if (controller.signal.aborted) return;
          setResult(undefined);
          const fields =
            failure instanceof ProjectRequestError
              ? Object.fromEntries(failure.errors.map((error) => [error.field, error.message]))
              : {};
          setErrors(
            Object.keys(fields).length
              ? fields
              : { headcountChange: failure instanceof Error ? failure.message : "계산하지 못했습니다." },
          );
        });
    }, 250);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, key, input.headcountChange, input.monthlyCostPerPerson, input.months, input.monthlyPricePerPerson, workforce.data]);
  // 원가 초과 원인 단추는 옮겨 갈 화면 조회 권한이 있을 때만(⑦-5).
  const canView = useCanViewScreen();
  function go(target: string, context: string | null) {
    if ((target !== "UI-11" && target !== "UI-32") || !context) {
      setMessage("이동할 화면을 확인할 수 없습니다.");
      return;
    }
    try {
      const value = JSON.parse(context) as ProjectCostAnalysisPeriod & { projectId: string };
      if (
        value.projectId !== row.projectId ||
        value.year !== period.year ||
        value.baseMonth !== period.baseMonth
      )
        throw new Error();
      navigate(`/screens/${target}`, {
        state: { projectId: value.projectId, year: value.year, baseMonth: value.baseMonth },
      });
    } catch {
      setMessage("프로젝트 이동 문맥을 확인할 수 없습니다.");
    }
  }
  if (projectOpen)
    return (
      <ProjectDetail
        projectId={row.projectId}
        dirtyScreenId="UI-04"
        onClose={onClose}
        onEdit={(project) => {
          // 다른 화면에서 연 상세의 [수정]은 프로젝트 화면 수정 폼으로(명세 0장)
          onClose();
          openEdit(project);
        }}
      />
    );
  const marginLabel = turnkey ? "예상 최종 마진율" : "월간 마진율";
  const marginValue = turnkey ? row.expectedFinalMarginRate : row.monthlyMarginRate;
  const monthlyContent = (
    <section data-testid="project-cost-monthly-panel" className="project-cost-detail-section">
      <KpiCards aria-label="경과와 마진" className="project-cost-detail-kpis">
        <KpiCard
          label="경과"
          value={`${row.elapsedMonths}/${row.totalMonths}개월`}
          description={`남은 기간 ${row.remainingMonths}개월`}
        />
        {internal ? (
          <KpiCard
            label="연구개발비 누계"
            value={formatKoreanMoney(row.totalCost)}
            description="판관비로 처리"
          />
        ) : (
          <KpiCard
            label={marginLabel}
            value={formatPercent(marginValue)}
            description={
              row.targetDifference == null ? "" : `목표 대비 ${formatPoint(row.targetDifference)}`
            }
          />
        )}
      </KpiCards>
      <RegionStatus {...monthly} testId="project-cost-monthly-retry" />
      {monthly.data && (
        <div className="ls-twrap project-cost-free">
          <table className="ls-t">
            <thead>
              <tr>
                <th>월</th>
                <th className="ls-right">MM</th>
                <th className="ls-right">원가</th>
                <th className="ls-right">{turnkey ? "배분 기준 매출(참고)" : "매출"}</th>
                <th className="ls-right">마진율</th>
              </tr>
            </thead>
            <tbody>
              {monthly.data.items.map((item) => {
                const revenue = turnkey ? item.distributionRevenue : item.revenue;
                return (
                  <tr key={item.yearMonth} data-testid={`project-cost-month-${item.yearMonth}`}>
                    <td>{formatYearMonth(item.yearMonth)}</td>
                    <td className="ls-right">{formatMm(item.mm)}</td>
                    <td className="ls-right">{won(item.cost)}</td>
                    <td className="ls-right">{internal ? dash : won(revenue)}</td>
                    <td className="ls-right">
                      {internal || item.marginRate == null ? (
                        dash
                      ) : (
                        <span className={item.marginRate < 0 ? "ls-neg" : undefined}>
                          {formatPercent(item.marginRate)}
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {monthly.data && monthly.data.items.length === 0 && <p className="ls-cap">월별 원가가 없습니다.</p>}
      {turnkey && (
        <p className="ls-cap project-cost-note">
          턴키의 누적 매출은 진행기준(원가 발생에 맞춰 인식)으로 계산합니다. 이 표의 월별 금액도 같은 방식(그 달 배분표 합계, 없으면 그 달 원가 × (계약금액 ÷ 예산))이라 다 더하면 누적 매출과 같아집니다.
        </p>
      )}
    </section>
  );
  const people = workforce.data?.items ?? [];
  const workforceContent = (
    <section data-testid="project-cost-workforce-panel" className="project-cost-detail-section">
      <RegionStatus {...workforce} testId="project-cost-workforce-retry" />
      {workforce.data &&
        (people.length ? (
          <div className="ls-twrap project-cost-free">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>이름</th>
                  <th>직무</th>
                  <th className="ls-right">MM</th>
                  {sensitive && <th className="ls-right">배분 원가</th>}
                  <th className="ls-right">매출</th>
                  <th>구분</th>
                </tr>
              </thead>
              <tbody>
                {people.map((item, index) => (
                  <tr
                    key={`${item.developerId}-${item.jobName}-${item.inputTypeCode}`}
                    data-testid={`project-cost-workforce-${index}`}
                  >
                    <td>{item.developerName}</td>
                    <td>{item.jobName}</td>
                    <td className="ls-right">{formatMm(item.monthlyMm)}</td>
                    {sensitive && (
                      <td className="ls-right">{won(item.allocatedCost)}</td>
                    )}
                    <td className="ls-right">{internal || item.revenue == null ? "–" : won(item.revenue)}</td>
                    <td>
                      <Badge kind={item.inputTypeCode === "FREE" ? "warn" : "neutral"}>
                        {item.inputTypeName}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="ls-cap">투입 기록이 없습니다.</p>
        ))}
    </section>
  );
  const field = (
    name: keyof ProjectCostAnalysisSimulationInput,
    label: string,
    control: ReactNode,
    hint: string,
  ) => (
    <div className="project-cost-sim-field">
      <label htmlFor={`sim-${name}`}>{label}</label>
      {control}
      {errors[name] ? (
        <span role="alert" className="project-cost-field-error">
          {errors[name]}
        </span>
      ) : (
        hint && <span className="ls-cap">{hint}</span>
      )}
    </div>
  );
  const change = (name: keyof ProjectCostAnalysisSimulationInput, signed = false) =>
    (event: ChangeEvent<HTMLInputElement>) =>
      setInput((current) => ({ ...current, [name]: digits(event.target.value, signed) }));
  const before = turnkey ? "예상 최종" : "월간";
  const difference = result ? result.marginRateAfter - result.marginRateBefore : 0;
  const n = Number(input.headcountChange);
  const simulationContent = (
    <section data-testid="project-cost-simulation" className="project-cost-detail-section">
      <Banner kind="info">
        인원을 더하거나 빼 보면서 마진이 어떻게 바뀌는지 봅니다. <Badge kind="info">가상</Badge> 결과는 저장되지 않습니다.
      </Banner>
      <div className="project-cost-sim-fields">
        {field(
          "headcountChange",
          "인원 변화 (명, 빼려면 −)",
          <input
            id="sim-headcountChange"
            name="headcountChange"
            className="ls-in project-cost-num"
            inputMode="numeric"
            value={input.headcountChange}
            aria-invalid={!!errors.headcountChange}
            onChange={change("headcountChange", true)}
          />,
          "",
        )}
        {field(
          "monthlyCostPerPerson",
          "1인 월 원가",
          <span className="project-cost-money">
            <input
              id="sim-monthlyCostPerPerson"
              name="monthlyCostPerPerson"
              className="ls-in project-cost-num"
              inputMode="numeric"
              value={comma(input.monthlyCostPerPerson)}
              aria-invalid={!!errors.monthlyCostPerPerson}
              onChange={change("monthlyCostPerPerson")}
            />
            <span className="ls-cap">원</span>
          </span>,
          "지금 투입 인력 MM당 평균 원가",
        )}
        {field(
          "months",
          "적용 개월",
          <input
            id="sim-months"
            name="months"
            className="ls-in project-cost-num"
            inputMode="numeric"
            value={input.months}
            aria-invalid={!!errors.months}
            onChange={change("months")}
          />,
          turnkey ? `남은 기간 ${row.remainingMonths}개월` : "월단위·파견은 한 달 기준",
        )}
        {!turnkey &&
          field(
            "monthlyPricePerPerson",
            "추가 인원 1인 월 단가",
            <span className="project-cost-money">
              <input
                id="sim-monthlyPricePerPerson"
                name="monthlyPricePerPerson"
                className="ls-in project-cost-num"
                inputMode="numeric"
                value={comma(input.monthlyPricePerPerson)}
                aria-invalid={!!errors.monthlyPricePerPerson}
                onChange={change("monthlyPricePerPerson")}
              />
              <span className="ls-cap">원</span>
            </span>,
            "월단위·파견은 사람이 늘면 매출도 늘어납니다",
          )}
      </div>
      <div className="project-cost-sim-out" aria-live="polite" data-testid="project-cost-simulation-result">
        {simulationBlocked ? (
          <Banner kind="info">{monthlyMarginNotice}</Banner>
        ) : result ? (
          <>
            <div className="project-cost-scores">
              <div>
                <div className="ls-cap">지금 {before} 마진율</div>
                <div className="project-cost-score">{formatPercent(result.marginRateBefore)}</div>
              </div>
              <div>
                <div className="ls-cap">
                  바꾼 뒤 <Badge kind="info">가상</Badge>
                </div>
                <div className={`project-cost-score${result.marginRateAfter < 0 ? " is-bad" : ""}`}>
                  {formatPercent(result.marginRateAfter)}
                </div>
              </div>
              <div>
                <div className="ls-cap">차이</div>
                <div className={`project-cost-score ${difference < 0 ? "is-bad" : "is-ok"}`}>
                  {formatPoint(difference)}
                </div>
              </div>
            </div>
            <p className="ls-cap project-cost-note">
              {turnkey
                ? `원가 ${formatKoreanMoney(result.addedCost)}가 ${n >= 0 ? "늘어납니다" : "줄어듭니다"}(턴키는 계약금액이 그대로라 매출은 변하지 않음).`
                : `한 달 매출 ${formatKoreanMoney(result.addedRevenue)}, 원가 ${formatKoreanMoney(result.addedCost)}가 ${n >= 0 ? "늘어납니다" : "줄어듭니다"}.`}{" "}
              목표 마진율 {targetMarginRate}% 대비 {formatPoint(result.marginRateAfter - targetMarginRate)}.
            </p>
          </>
        ) : null}
      </div>
    </section>
  );
  const causeData = causes.data;
  const top = causeData ? Math.max(1, ...causeData.items.map((item) => item.amount)) : 1;
  const red = causeData?.primaryBarColor === "빨강";
  const causesContent = (
    <section data-testid="project-cost-causes" className="project-cost-detail-section project-cost-causes">
      <RegionStatus {...causes} testId="project-cost-causes-retry" />
      {causeData && (
        <>
          {causeData.actualProgressRate != null && causeData.expectedCostAtProgress != null && (
            <Banner kind={(causeData.progressExcessAmount ?? 0) > 0 ? "bad" : "info"}>
              실적 진척률 {causeData.actualProgressRate}%만큼이면 원가가{" "}
              <strong>{formatKoreanMoney(causeData.expectedCostAtProgress)}</strong>여야 하는데 실제로{" "}
              <strong>{formatKoreanMoney(causeData.cumulativeCost)}</strong>를 썼습니다 →{" "}
              {(causeData.progressExcessAmount ?? 0) > 0 ? (
                <strong>{formatKoreanMoney(causeData.progressExcessAmount)} 초과</strong>
              ) : (
                "초과 없음"
              )}
            </Banner>
          )}
          <p className="ls-cap">
            지금까지 원가를 늘린 원인을 금액이 큰 순서로 보여 줍니다.
            {red ? "" : " 경보가 없는 프로젝트라 참고용입니다(빨간색은 경보가 있을 때만)."}
          </p>
          {causeData.items.map((item, index) => (
            <article key={item.causeKey} data-testid={`project-cost-cause-${index}`} className="project-cost-cause">
              <div className="project-cost-cause-head">
                <strong>{item.causeName}</strong>
                <span>{item.amount ? formatKoreanMoney(item.amount) : "없음"}</span>
              </div>
              {/* 시안 .bar > .track: 세로로 쌓이는 칸 안에 막대를 바로 두면 flex:1 때문에 높이가 0이 된다 */}
              <div className="project-cost-bar">
                <div className="project-cost-track">
                  <div
                    className={`project-cost-fill${red && index === 0 && item.amount > 0 ? " is-bad" : " is-grey"}`}
                    style={{ width: `${(item.amount / top) * 100}%` }}
                  />
                </div>
              </div>
              <div className="ls-cap">{item.description}</div>
              {item.amount > 0 && (
                <div className="project-cost-cause-task">
                  <span>
                    고칠 사람: <strong>{item.owner}</strong> — {item.task}
                  </span>
                  {item.targetScreenId && item.targetLabel && canView(item.targetScreenId) && (
                    <button
                      type="button"
                      className="ls-btn ls-btn-sm"
                      data-testid={`project-cost-cause-go-${index}`}
                      onClick={() => go(item.targetScreenId!, item.navigationContext)}
                    >
                      {item.targetLabel}
                    </button>
                  )}
                </div>
              )}
            </article>
          ))}
          {causeData.remainderAmount != null && (
            <p className="ls-cap project-cost-note">
              나머지 {formatKoreanMoney(causeData.remainderAmount)}는 위 세 가지로 설명되지 않습니다(진척률을 낮게 적었거나, 일 자체가 예상보다 어려움). PM과 확인하세요.
            </p>
          )}
          <p className="ls-cap project-cost-note">
            투입 초과 = (실제 MM − 지금까지 계획 MM) × MM당 원가 · 단가 대비 원가 = 인력별 (배분 원가 − 단가 × MM × {causeData.costOverRate}%)의 양수를 달마다 더한 값 · 무상 지원 = 무상 지원 원가를 달마다 더한 값.
          </p>
          {message && <p role="alert">{message}</p>}
        </>
      )}
    </section>
  );
  const tabs: DetailTab[] = [
    { key: "monthly", label: "월별 원가", content: monthlyContent },
    {
      key: "workforce",
      label: workforce.data ? `투입 인력 (${people.length})` : "투입 인력",
      content: workforceContent,
    },
    ...(internal
      ? []
      : [
          { key: "simulation", label: "시뮬레이션", content: simulationContent },
          { key: "causes", label: "원가 초과 원인", content: causesContent },
        ]),
  ];
  return (
    <DetailPanel
      title={row.projectName}
      sub={`${row.projectId} · ${typeLabel(row)} · ${row.startDate} ~ ${row.endDate}`}
      actions={
        <button
          type="button"
          className="ls-btn ls-btn-sm"
          data-testid="project-cost-project-info"
          onClick={() => setProjectOpen(true)}
        >
          프로젝트 정보
        </button>
      }
      tabs={tabs}
      activeTab={tab}
      onTab={(next) => {
        setTab(next);
        if (next === "causes") setCausesWanted(true);
      }}
      onClose={onClose}
      testId="project-cost-detail"
    />
  );
}

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import {
  Badge,
  DetailPanel,
  KpiCard,
  KpiCards,
  ListView,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { koreanToday } from "../../shared/time";
import { SalesOpportunityForm } from "../salesopportunities/SalesOpportunityForm";
import { SalesOpportunityDetail } from "../salesopportunities/SalesOpportunityDetail";
import type {
  SalesOpportunity,
  SalesOpportunityOptions,
} from "../salesopportunities/salesOpportunities";
import { ProjectLink } from "../projects/ProjectLink";
import {
  developerFailure,
  getCommonSalesPipeline,
  getCommonSalesPipelineClosed,
  getCommonSalesPipelineForecast,
  getCommonSalesPipelineForecastExcel,
  getSalesPipelineOptions,
  salesPipelineDraft,
  type SalesPipeline,
  type SalesClosed,
  type SalesForecast,
} from "./salesPipeline";
import "./salesPipeline.css";

/** 인력계획 › 영업 파이프라인 UI-29. */
export function SalesPipelinePage({
  renderActions,
  testId = "sales-pipeline-page",
}: {
  renderActions?: (actions: ReactNode) => ReactNode;
  testId?: string;
}) {
  const [tab, setTab] = useState("ongoing");
  const [revision, refresh] = useState(0);
  const [pipeline, setPipeline] = useState<SalesPipeline | null>(null);
  const [closed, setClosed] = useState<SalesClosed[] | null>(null);
  const [forecast, setForecast] = useState<SalesForecast | null>(null);
  const [options, setOptions] = useState<SalesOpportunityOptions | null>(null);
  const [error, setError] = useState("");
  const [optionError, setOptionError] = useState("");
  const [result, setResult] = useState("");
  const [includeStopped, setIncludeStopped] = useState(false);
  const [unit, setUnit] = useState(10000);
  const [selected, setSelected] = useState<string | null>(null);
  const [editing, setEditing] = useState<SalesOpportunity | "new" | null>(null);
  const [highlight, setHighlight] = useState<{
    opportunityId: string;
    milliseconds: number;
  } | null>(null);
  const [notice, setNotice] = useState<{
    message: string;
    kind: "ok" | "warn" | "info";
  } | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [page, setPage] = useState(1),
    [pageSize, setPageSize] = useState(20),
    [dense, setDense] = useState(false);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [hidden, setHidden] = useState<string[]>([]);
  useEffect(() => {
    if (!highlight) return;
    const timer = window.setTimeout(
      () => setHighlight(null),
      highlight.milliseconds,
    );
    return () => window.clearTimeout(timer);
  }, [highlight]);
  useEffect(() => {
    let active = true;
    getSalesPipelineOptions()
      .then((value) => {
        if (active) {
          setOptions(value);
          setOptionError("");
        }
      })
      .catch((reason) => {
        if (active) setOptionError(developerFailure(reason));
      });
    return () => {
      active = false;
    };
  }, [revision]);
  useEffect(() => {
    let active = true;
    setError("");
    setPipeline(null);
    setClosed(null);
    setForecast(null);
    const request =
      tab === "ongoing"
        ? getCommonSalesPipeline().then((value) => {
            if (active) setPipeline(value);
          })
        : tab === "closed"
          ? getCommonSalesPipelineClosed(result, includeStopped).then(
              (value) => {
                if (active) setClosed(value.items);
              },
            )
          : getCommonSalesPipelineForecast().then((value) => {
              if (active) setForecast(value);
            });
    request.catch((reason) => {
      if (active) setError(developerFailure(reason));
    });
    return () => {
      active = false;
    };
  }, [tab, result, includeStopped, revision]);
  const label = (group: string, code: string) =>
    options?.codes[group]?.find((c) => c.code === code)?.codeName || code;
  const money = (amount: number) =>
    (amount / unit).toLocaleString("ko-KR", { maximumFractionDigits: 2 });
  const columns: ListColumn<SalesClosed>[] = [
    { key: "opportunityName", label: "기회명" },
    { key: "customerName", label: "고객사" },
    {
      key: "result",
      label: "결과",
      render: (row) => label("OPP_STAT", row.result),
    },
    {
      key: "expectedAmount",
      label: "예상금액",
      render: (row) => money(row.expectedAmount),
    },
    { key: "resultDate", label: "종료일" },
    {
      key: "projectId",
      label: "수주 프로젝트",
      render: (row) =>
        row.result === "WIN" && row.projectId ? (
          <ProjectLink
            projectId={row.projectId}
            name="프로젝트 열기"
            testId={`sales-project-${row.opportunityId}`}
          />
        ) : (
          "—"
        ),
    },
    {
      key: "lossReason",
      label: "실주사유",
      render: (row) => (row.result === "LOSE" ? row.lossReason || "—" : "—"),
    },
  ];
  const sortedClosed = useMemo(
    () =>
      !closed
        ? null
        : [...closed].sort((a, b) => {
            if (!sort) return 0;
            const x = a[sort.key as keyof SalesClosed],
              y = b[sort.key as keyof SalesClosed];
            const order =
              typeof x === "number" && typeof y === "number"
                ? x - y
                : String(x || "").localeCompare(String(y || ""));
            return sort.dir === "asc" ? order : -order;
          }),
    [closed, sort],
  );
  async function download() {
    setDownloading(true);
    setNotice(null);
    try {
      const count = await getCommonSalesPipelineForecastExcel();
      setNotice({
        kind: "ok",
        message: `엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`,
      });
    } catch (reason) {
      setNotice({ kind: "warn", message: developerFailure(reason) });
    } finally {
      setDownloading(false);
    }
  }
  const actions = (
    <button
      data-testid="sales-pipeline-create"
      disabled={!options}
      onClick={() => setEditing("new")}
    >
      기회 등록
    </button>
  );
  const loading =
    !error &&
    (tab === "ongoing" ? !pipeline : tab === "closed" ? !closed : !forecast);
  const forecastMonths = forecast?.months || [];
  const peakRevenue = Math.max(
    1,
    ...forecastMonths.map((m) => m.allWonRevenue),
  );
  const peakPeople = Math.max(
    1,
    ...forecastMonths.flatMap((m) => [m.demand, m.supply]),
  );
  return (
    <div
      className="screen-body sales-pipeline"
      data-screen-id="UI-29"
      data-testid={testId}
    >
      <div className="sales-pipeline-heading">
        <h1>영업 파이프라인</h1>
        {renderActions ? renderActions(actions) : actions}
      </div>
      <nav aria-label="영업 파이프라인 탭">
        {[
          ["ongoing", "진행"],
          ["closed", "종료"],
          ["forecast", "매출·인력 예측"],
        ].map(([key, text]) => (
          <button
            key={key}
            data-testid={`sales-tab-${key}`}
            aria-pressed={tab === key}
            onClick={() => {
              setTab(key);
              setPage(1);
            }}
          >
            {text}
          </button>
        ))}
      </nav>
      <label>
        금액 단위
        <select
          data-testid="sales-money-unit"
          value={unit}
          onChange={(event) => setUnit(Number(event.target.value))}
        >
          <option value={1}>원</option>
          <option value={10000}>만원</option>
          <option value={100000000}>억원</option>
        </select>
      </label>
      {optionError && (
        <p role="alert">
          {optionError}
          <button
            data-testid="sales-options-retry"
            onClick={() => refresh((v) => v + 1)}
          >
            다시 시도
          </button>
        </p>
      )}
      {error && (
        <p role="alert">
          {error}
          <button
            data-testid="sales-retry"
            onClick={() => refresh((v) => v + 1)}
          >
            다시 시도
          </button>
        </p>
      )}
      {loading && <p role="status">목록을 불러오는 중입니다.</p>}
      {tab === "ongoing" && pipeline && (
        <section data-testid="sales-ongoing-panel">
          <KpiCards aria-label="진행 기회 요약">
            <KpiCard label="진행 기회" value={pipeline.summary.ongoingCount} />
            <KpiCard
              label="14일 초과 미갱신"
              value={pipeline.summary.staleCount}
            />
            <KpiCard
              label="예상 금액 합"
              value={money(pipeline.summary.expectedAmount)}
            />
            <KpiCard
              label="확률 반영 금액"
              value={money(pipeline.summary.weightedAmount)}
            />
            <KpiCard
              label="필요 인력"
              value={`${pipeline.summary.weightedManpower} MM`}
            />
          </KpiCards>
          {!pipeline.summary.ongoingCount && (
            <p>조건에 맞는 결과가 없습니다.</p>
          )}
          <div className="sales-kanban">
            {pipeline.stages.map((stage) => (
              <section
                key={stage.stageCode}
                data-testid={`sales-stage-${stage.stageCode.toLowerCase()}`}
              >
                <h2>
                  {stage.stageName || label("SALES_STEP", stage.stageCode)}{" "}
                  {stage.defaultProbability != null &&
                    `${stage.defaultProbability}%`}
                </h2>
                <p>
                  {stage.count}건 · {money(stage.amount)}
                </p>
                {stage.items.map((card) => (
                  <article
                    key={card.opportunityId}
                    data-testid={`sales-card-${card.opportunityId}`}
                    className={
                      highlight?.opportunityId === card.opportunityId
                        ? "sales-card-highlight"
                        : undefined
                    }
                  >
                    <button
                      data-testid={`sales-open-${card.opportunityId}`}
                      onClick={() => setSelected(card.opportunityId)}
                    >
                      <strong>{card.opportunityName}</strong>
                    </button>
                    <p>
                      {card.customerName} · {card.startMonth}
                    </p>
                    <p>
                      {money(card.expectedAmount)} · {card.winningProbability}%
                    </p>
                    <p>
                      {card.requiredJobs
                        .map((job) => label("JOB", job))
                        .join(", ")}
                    </p>
                    <p
                      className={
                        card.nextActionDueDate &&
                        card.nextActionDueDate < koreanToday()
                          ? "sales-overdue"
                          : ""
                      }
                    >
                      {card.nextAction || "다음 할 일 없음"}{" "}
                      {card.nextActionDueDate}
                    </p>
                    {card.stale && <Badge kind="warn">미갱신</Badge>}
                    <Badge>{label("APRV_STAT", card.approvalStatusCode)}</Badge>
                    <Link
                      data-testid={`sales-recommend-${card.opportunityId}`}
                      to={{
                        pathname: "/screens/UI-30",
                        search: new URLSearchParams({
                          opportunityId: card.opportunityId,
                          jobCode: card.requiredJobs[0] || "",
                          startMonth: card.startMonth,
                        }).toString(),
                      }}
                      state={{
                        opportunityId: card.opportunityId,
                        jobCode: card.requiredJobs[0] || "",
                        startMonth: card.startMonth,
                      }}
                    >
                      인력 추천 (준비 중)
                    </Link>
                  </article>
                ))}
              </section>
            ))}
          </div>
        </section>
      )}
      {tab === "closed" && (
        <section data-testid="sales-closed-panel">
          <label>
            결과
            <select
              data-testid="sales-result-filter"
              value={result}
              onChange={(e) => {
                setResult(e.target.value);
                setPage(1);
              }}
            >
              <option value="">전체</option>
              {(options?.codes.OPP_STAT || [])
                .filter((c) => c.code !== "ING")
                .map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.codeName}
                  </option>
                ))}
            </select>
          </label>
          <label>
            <input
              data-testid="sales-include-stopped"
              type="checkbox"
              checked={includeStopped}
              onChange={(e) => {
                setIncludeStopped(e.target.checked);
                setPage(1);
              }}
            />
            사용 중지 포함
          </label>
          <ListView
            screenId="UI-29"
            testId="sales-closed-list"
            columns={columns}
            visibleColumns={columns.filter((c) => !hidden.includes(c.key))}
            hiddenColumns={hidden}
            onToggleColumn={(key) =>
              setHidden((h) =>
                h.includes(key) ? h.filter((k) => k !== key) : [...h, key],
              )
            }
            onResetColumns={() => setHidden([])}
            rows={
              sortedClosed?.slice((page - 1) * pageSize, page * pageSize) ??
              null
            }
            mobileRows={sortedClosed}
            rowKey={(row) => row.opportunityId}
            total={closed?.length || 0}
            page={page}
            pageSize={pageSize}
            onPage={setPage}
            onPageSize={(value) => {
              setPageSize(value);
              setPage(1);
            }}
            dense={dense}
            onDense={setDense}
            sort={sort}
            onSort={setSort}
            onRowClick={(row) => setSelected(row.opportunityId)}
            error={error}
            onRetry={() => refresh((v) => v + 1)}
          />
        </section>
      )}
      {tab === "forecast" && forecast && (
        <section data-testid="sales-forecast-panel">
          <KpiCards aria-label="매출·인력 예측 요약">
            <KpiCard
              label="6개월 예상 매출"
              value={money(
                forecastMonths.reduce(
                  (sum, m) =>
                    sum + m.projectRevenue + m.weightedOpportunityRevenue,
                  0,
                ),
              )}
            />
            <KpiCard
              label="모두 따면 예상 매출"
              value={money(
                forecastMonths.reduce((sum, m) => sum + m.allWonRevenue, 0),
              )}
            />
            <KpiCard
              label="최대 인력 수요"
              value={Math.max(0, ...forecastMonths.map((m) => m.demand))}
            />
            <KpiCard
              label="최대 인력 공급"
              value={Math.max(0, ...forecastMonths.map((m) => m.supply))}
            />
          </KpiCards>
          <button
            data-testid="sales-forecast-excel"
            disabled={downloading}
            onClick={download}
          >
            {downloading ? "만드는 중…" : "엑셀 다운로드"}
          </button>
          <h2>월별 예상 매출</h2>
          <div className="sales-chart" aria-label="6개월 예상 매출 막대">
            {forecastMonths.map((m) => (
              <div key={m.month} data-testid={`sales-revenue-${m.month}`}>
                <span>{m.month}</span>
                <div className="sales-bar-track">
                  <i
                    className="sales-bar-project"
                    style={{
                      width: `${(m.projectRevenue / peakRevenue) * 100}%`,
                    }}
                  />
                  <i
                    className="sales-bar-opportunity"
                    style={{
                      width: `${(m.weightedOpportunityRevenue / peakRevenue) * 100}%`,
                    }}
                  />
                </div>
                <span>
                  {money(m.projectRevenue + m.weightedOpportunityRevenue)}
                </span>
              </div>
            ))}
          </div>
          <p>진행 프로젝트 + 확률 반영 기회</p>
          <h2>인력 수요 / 공급</h2>
          <div className="sales-chart" aria-label="6개월 인력 수요 공급 막대">
            {forecastMonths.map((m) => (
              <div key={m.month} data-testid={`sales-manpower-${m.month}`}>
                <span>{m.month}</span>
                <div>
                  <meter
                    min={0}
                    max={peakPeople}
                    value={m.demand}
                    aria-label={`${m.month} 수요`}
                  />
                  <meter
                    min={0}
                    max={peakPeople}
                    value={m.supply}
                    aria-label={`${m.month} 공급`}
                  />
                </div>
                <span>
                  {m.demand} / {m.supply} MM
                </span>
              </div>
            ))}
          </div>
          <div className="sales-table-scroll">
            <table>
              <caption>월별 매출</caption>
              <thead>
                <tr>
                  <th>구분</th>
                  {forecastMonths.map((m) => (
                    <th key={m.month}>{m.month}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {(
                  [
                    ["진행 프로젝트", "projectRevenue"],
                    ["확률 반영 기회", "weightedOpportunityRevenue"],
                    ["모두 따면 (참고)", "allWonRevenue"],
                  ] as const
                ).map(([caption, field]) => (
                  <tr key={field}>
                    <th>{caption}</th>
                    {forecastMonths.map((m) => (
                      <td key={m.month}>{money(m[field])}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <h2>채용·외부인력 검토</h2>
          {!forecast.hiringReviews.length && (
            <p>조건에 맞는 결과가 없습니다.</p>
          )}
          {forecast.hiringReviews.map((row) => (
            <article
              key={row.jobCode}
              data-testid={`sales-hiring-${row.jobCode.toLowerCase()}`}
            >
              <strong>{row.jobName || label("JOB", row.jobCode)}</strong>
              <p>
                부족 시작월 {row.shortageStartMonth} · 최대 부족{" "}
                {row.maximumShortage} · 채용 시작 권장월{" "}
                {row.recommendedHiringMonth}
              </p>
              <Link
                data-testid={`sales-candidates-${row.jobCode.toLowerCase()}`}
                to={`/screens/UI-33?jobCode=${encodeURIComponent(row.jobCode)}`}
                state={{ jobCode: row.jobCode }}
              >
                후보 {row.candidateCount}명 보기
              </Link>
            </article>
          ))}
        </section>
      )}
      {selected && options && (
        <DetailPanel
          title="영업 기회 상세"
          onClose={() => setSelected(null)}
          testId="sales-pipeline-detail"
        >
          <SalesOpportunityDetail
            opportunityId={selected}
            options={options}
            onClose={() => setSelected(null)}
            onEdit={(row) => {
              setSelected(null);
              setEditing(row);
            }}
            onChanged={() => refresh((v) => v + 1)}
          />
        </DetailPanel>
      )}
      {editing && options && (
        <Dialog
          title={editing === "new" ? "기회 등록" : "기회 수정"}
          testId="sales-pipeline-form-dialog"
          onClose={() => setEditing(null)}
        >
          <SalesOpportunityForm
            initial={editing === "new" ? salesPipelineDraft() : editing}
            opportunity={editing === "new" ? undefined : editing}
            options={options}
            onCancel={() => setEditing(null)}
            onSaved={(row, highlightMilliseconds) => {
              setEditing(null);
              setSelected(row.opportunityId);
              setTab("ongoing");
              setHighlight({
                opportunityId: row.opportunityId,
                milliseconds: highlightMilliseconds,
              });
              refresh((v) => v + 1);
              setNotice({ kind: "ok", message: "저장했습니다." });
            }}
          />
        </Dialog>
      )}
      {notice && (
        <Toast
          kind={notice.kind}
          message={notice.message}
          onDismiss={() => setNotice(null)}
        />
      )}
    </div>
  );
}

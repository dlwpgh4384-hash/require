import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Badge,
  Banner,
  KpiCard,
  KpiCards,
  formatDecimal,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { isAllowed, yearRange } from "../../shared/period/periodRules";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import { koreanToday } from "../../shared/time";
import { projectFailure, ProjectRequestError } from "../projects/projects";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { ManpowerForecastBars } from "./ManpowerForecastBars";
import { ManpowerForecastTable } from "./ManpowerForecastTable";
import {
  getCommonManpowerForecasts,
  getCommonManpowerForecastsExcel,
  type ManpowerForecastResult,
} from "./manpowerForecasts";
import "../profitstructures/profitStructures.css";
import "./manpowerForecast.css";

const options = {
  kind: "year",
  allowNextYear: true,
  allowBeforeStart: false,
} as const;
// SCR-UI-17: 연도별 조회 전용 예측. 업무 입력·결과 저장 동작은 없다.
export function UI17Page({
  renderActions,
}: { renderActions?: (actions: ReactNode) => ReactNode } = {}) {
  const today = koreanToday();
  const currentYear = Number(today.slice(0, 4));
  const currentYm = today.slice(0, 7).replace("-", "");
  const period = useScreenPeriod("UI-17", options, currentYm);
  const year = period.state.status === "ready" ? period.state.value.year : null;
  const [response, setResponse] = useState<{
    year: number;
    data: ManpowerForecastResult;
  } | null>(null);
  const data = response?.year === year ? response.data : null;
  const [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [revision, setRevision] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    kind: ToastKind;
  } | null>(null);
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  useEffect(() => {
    if (year === null) return;
    const controller = new AbortController();
    setResponse(null);
    setError("");
    setFields([]);
    getCommonManpowerForecasts(year, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setResponse({ year, data: result });
      })
      .catch((reason) => {
        if (controller.signal.aborted) return;
        setError(projectFailure(reason));
        if (reason instanceof ProjectRequestError) setFields(reason.errors);
      });
    return () => controller.abort();
  }, [year, revision]);
  const download = async () => {
    if (!data || year === null || downloading) return;
    setDownloading(true);
    try {
      const count = await getCommonManpowerForecastsExcel(year);
      if (alive.current)
        setNotice({
          kind: "ok",
          message: `엑셀 파일을 만들었습니다 (조회 결과 ${count}건)`,
        });
    } catch (reason) {
      if (alive.current) {
        setNotice({ kind: "warn", message: projectFailure(reason) });
        if (reason instanceof ProjectRequestError) setFields(reason.errors);
      }
    } finally {
      if (alive.current) setDownloading(false);
    }
  };
  const actions = (
    <button
      type="button"
      className="ls-btn"
      data-testid="manpower-forecast-excel"
      disabled={!data || downloading}
      onClick={() => void download()}
    >
      {downloading ? "만드는 중…" : "엑셀 다운로드"}
    </button>
  );
  const choose = (next: number) => {
    if (
      period.state.status === "ready" &&
      isAllowed({ year: next }, period.state.basis, options)
    )
      period.setValue({ year: next });
  };
  return (
    <div
      className="screen-body profit-structures-page manpower-forecast-page"
      data-testid="manpower-forecast-page"
      data-screen-id="SCR-UI-17"
    >
      {renderActions ? (
        renderActions(actions)
      ) : (
        <div className="project-title-actions">{actions}</div>
      )}
      {period.state.status === "ready" && (
        <div className="manpower-forecast-year">
          <YearBar
            year={period.state.value.year}
            currentYear={currentYear}
            {...yearRange(period.state.basis, options)}
            caption=""
            onChange={choose}
          />
          <button
            type="button"
            className="ls-btn"
            data-testid="manpower-forecast-current-year"
            onClick={() => choose(currentYear)}
          >
            올해
          </button>
        </div>
      )}
      {fields.map((field, i) => (
        <p className="profit-field-error" key={`${field.field}-${i}`}>
          {field.field === "year"
            ? field.message
            : `${field.field}: ${field.message}`}
        </p>
      ))}
      <Banner kind="info">
        <Badge kind="info">예측</Badge> UI-28과 같은 계산
      </Banner>
      {period.state.status === "error" && (
        <p role="alert">{period.state.message}</p>
      )}
      {error && (
        <div role="alert">
          <p>{error}</p>
          <button
            type="button"
            className="ls-btn"
            data-testid="manpower-forecast-retry"
            onClick={() => setRevision((n) => n + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      {!data && !error && period.state.status !== "error" && (
        <p data-testid="manpower-forecast-loading">예측을 불러오는 중입니다.</p>
      )}
      {data && (
        <>
          <KpiCards
            aria-label="인력 수요 예측 요약"
            className="manpower-forecast-kpis"
          >
            <KpiCard
              label="마지막 달 가용 인력"
              value={`${formatDecimal(data.summary.lastMonthSupply)}명`}
            />
            <KpiCard
              label="마지막 달 필요 인력"
              value={`${formatDecimal(data.summary.lastMonthDemand)}명`}
            />
            <KpiCard
              label="가장 부족한 직무"
              value={
                data.summary.mostShortage?.jobName ??
                (data.months.length ? "부족 없음" : "–")
              }
              description={
                data.summary.mostShortage
                  ? `최대 ${formatDecimal(data.summary.mostShortage.maximumShortage)}명 부족`
                  : data.months.length
                    ? "모든 직무가 남거나 맞음"
                    : "예측 자료가 없습니다."
              }
            />
          </KpiCards>
          {data.months.length > 0 ? (
            <ManpowerForecastBars months={data.months} />
          ) : (
            <p data-testid="manpower-forecast-empty">예측 자료가 없습니다.</p>
          )}
        </>
      )}
      <ManpowerForecastTable
        months={data?.months ?? []}
        thresholds={
          data?.thresholds ?? {
            shortageThreshold: Infinity,
            surplusThreshold: Infinity,
          }
        }
      />
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

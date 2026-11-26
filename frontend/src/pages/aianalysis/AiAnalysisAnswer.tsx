import { useState } from "react";
import { useInRouterContext, useNavigate } from "react-router-dom";
import { Badge } from "../../shared/list";
import { Toast } from "../../shared/feedback/Dialog";
import { screenPeriodChannel } from "../../shared/layout/screenPeriodChannel";
import {
  LineChart,
  chartColor,
  percentTick,
} from "../profitstructures/ProfitStructureCharts";
import {
  formatAnalysisCell,
  getCommonAiAnalysisQuestionExcel,
  isRightAligned,
  splitBold,
  type AnalysisAnswer,
  type AnalysisCell,
  type AnalysisTable,
} from "./aiAnalysis";

/** 시안 lineChart 색 순서: 매출 --c1(파랑), 영업이익 --c3(초록), 그다음 --c2·--c4. */
const SERIES_COLORS = [chartColor.c1, chartColor.c3, chartColor.c2, chartColor.c4];

const downIcon = (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
  </svg>
);

/** 결론 글: **굵게**만 <strong>, 나머지는 글자 그대로(React가 이스케이프). */
export function BoldText({ text }: { text: string }) {
  return (
    <>
      {splitBold(text).map((part, i) =>
        part.bold ? <strong key={i}>{part.text}</strong> : <span key={i}>{part.text}</span>,
      )}
    </>
  );
}

function AnswerTable({ table, testId }: { table: AnalysisTable; testId: string }) {
  const cell = (value: AnalysisCell, col: number) => {
    const text = formatAnalysisCell(value, table.columns[col]?.format);
    // 시안 pname(): 「이름」 칸은 이름 밑에 작은 관리번호(서버 글은 「이름(관리번호)」).
    const person = table.columns[col]?.key === "name" ? /^(.+)\(([^()]+)\)$/.exec(text) : null;
    return person ? (
      <span className="ai-pname">
        {person[1]}
        <small>{person[2]}</small>
      </span>
    ) : (
      text
    );
  };
  const cls = (col: number) =>
    table.columns[col] && isRightAligned(table.columns[col]) ? "ai-num" : undefined;
  return (
    <>
      {table.title && <div className="ai-a-tt">{table.title}</div>}
      <div className="ls-twrap ai-twrap">
        <table className="ls-t" data-testid={testId}>
          <thead>
            <tr>
              {table.columns.map((column, col) => (
                <th key={column.key} className={cls(col)}>
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {table.rows.map((row, index) => (
              <tr key={index} data-testid={`${testId}-row-${index}`}>
                {row.map((value, col) => {
                  const tone = table.tones?.[index]?.[col];
                  return (
                    <td key={table.columns[col]?.key ?? col} className={cls(col)}>
                      {tone ? <span className={`ai-tone-${tone}`}>{cell(value, col)}</span> : cell(value, col)}
                    </td>
                  );
                })}
              </tr>
            ))}
            {table.totalRow && (
              <tr data-testid={`${testId}-total`}>
                {table.totalRow.map((value, col) => (
                  <td key={table.columns[col]?.key ?? col} className={cls(col)}>
                    <strong>{cell(value, col)}</strong>
                  </td>
                ))}
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}

/**
 * 시안 answer(): .one 한 줄 결론 → 표 → 꺾은선 → 회색 가정 글 → .scope 사용한 데이터 → .ex 다음 행동.
 * 답할 수 없음(NO_ANS)·처리 실패(ERR)는 한 줄과 추천 질문 단추만.
 */
export function AiAnalysisAnswer({
  answer,
  onNavigate,
  onQuestion,
  hiddenSuggestions = [],
  disabled,
}: {
  answer: AnalysisAnswer;
  onNavigate?: () => void;
  onQuestion?: (question: string) => void;
  /** 아래 추천 질문 줄에 이미 있는 질문은 답 안에 다시 두지 않는다. */
  hiddenSuggestions?: readonly string[];
  disabled?: boolean;
}) {
  const inRouter = useInRouterContext();
  // Same stable optional-router convention as createScreenRequest.
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const navigate = inRouter ? useNavigate() : null;
  const [toast, setToast] = useState("");
  const [downloading, setDownloading] = useState(false);
  const id = answer.questionId;
  const ok = answer.answerStatus === "OK";
  // 시안: 예측 답은 결론 앞에 「예측」(파랑), 예측이 아닌 마감 전 달 답은 「잠정」(주황).
  const lead = ok
    ? answer.forecast
      ? <Badge kind="info">예측</Badge>
      : answer.provisional
        ? <Badge kind="warn">잠정</Badge>
        : null
    : null;
  const one = (
    <div className="ai-one" data-testid={`analysis-conclusion-${id}`}>
      {lead}
      {lead && " "}
      <BoldText text={answer.conclusion} />
    </div>
  );
  if (!ok) {
    // 단추 글은 suggestionLabels(예: 프로젝트 이름만), 누르면 suggestedQuestions의 질문을 묻는다.
    const labels = answer.suggestionLabels ?? [];
    const suggestions = (answer.suggestedQuestions ?? [])
      .map((question, i) => ({ question, label: labels[i] ?? question }))
      .filter(({ question }) => !hiddenSuggestions.includes(question));
    return (
      <div className="ai-a" data-testid={`analysis-answer-${id}`}>
        {one}
        {/* 시안 급여 권한 답: 한 줄 결론 밑 둘째 줄(「급여는 … 권한(…)에서만 답합니다.」), 「사용한 데이터:」 머리말 없음. */}
        {answer.usedData && (
          <div className="ai-scope" data-testid={`analysis-scope-${id}`}>
            {answer.usedData}
          </div>
        )}
        {onQuestion && suggestions.length > 0 && (
          <div className="ai-ex">
            {suggestions.map(({ question, label }, i) => (
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid={`analysis-followup-${id}-${i}`}
                key={question}
                disabled={disabled}
                onClick={() => onQuestion(question)}
              >
                {label}
              </button>
            ))}
          </div>
        )}
      </div>
    );
  }
  const assumptions = answer.assumptions ?? [];
  const actions = answer.actions ?? [];
  const chart = answer.chart;
  return (
    <div className="ai-a" data-testid={`analysis-answer-${id}`}>
      {one}
      {/* hidden 표는 엑셀에만 쓴다(시안 매출 답: 결론+그래프만). 번호는 서버 표 순서 그대로. */}
      {(answer.tables ?? []).map((table, index) =>
        table.hidden ? null : (
          <AnswerTable key={index} table={table} testId={`analysis-table-${id}-${index}`} />
        ),
      )}
      {chart && chart.labels.length > 0 && chart.series.length > 0 && (
        <div className="ai-chart">
          <LineChart
            height={180}
            labels={chart.labels}
            series={chart.series.map((series, i) => ({
              name: series.name,
              color: SERIES_COLORS[i % SERIES_COLORS.length],
              values: series.values,
            }))}
            testId={`analysis-chart-${id}`}
            tickFormat={chart.format === "rate" ? percentTick : undefined}
          />
        </div>
      )}
      {assumptions.length > 0 && (
        <p className="ai-cap" data-testid={`analysis-assumptions-${id}`}>
          가정: {assumptions.join(" ")}
        </p>
      )}
      {answer.usedData && (
        <div className="ai-scope" data-testid={`analysis-scope-${id}`}>
          사용한 데이터: {answer.usedData}
          {answer.provisional && (
            <>
              {answer.provisionalMonth ? ` (${answer.provisionalMonth}는 ` : " ("}
              <Badge kind="warn">잠정</Badge>
              {")"}
            </>
          )}
        </div>
      )}
      {(actions.length > 0 || answer.excel) && (
        <div className="ai-ex">
          {actions.map((action, i) => (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid={`analysis-action-${id}-${i}`}
              key={`${action.targetScreenId}-${i}`}
              onClick={() => {
                screenPeriodChannel(action.targetScreenId).request(action.period);
                navigate?.(`/screens/${action.targetScreenId}`);
                onNavigate?.();
              }}
            >
              {action.label}
            </button>
          ))}
          {answer.excel && (
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid={`analysis-excel-${id}`}
              disabled={downloading}
              onClick={async () => {
                setDownloading(true);
                try {
                  await getCommonAiAnalysisQuestionExcel(id);
                } catch {
                  setToast("엑셀을 내려받지 못했습니다. 다시 시도해 주세요.");
                } finally {
                  setDownloading(false);
                }
              }}
            >
              {downIcon} 엑셀 다운로드
            </button>
          )}
        </div>
      )}
      {toast && (
        <Toast
          kind="warn"
          message={toast}
          testId={`analysis-excel-toast-${id}`}
          onDismiss={() => setToast("")}
        />
      )}
    </div>
  );
}

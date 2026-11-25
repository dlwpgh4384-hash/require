import { useState } from "react";
import { useInRouterContext, useNavigate } from "react-router-dom";
import { Badge } from "../../shared/list";
import { formatMoney } from "../../shared/list/moneyUnit";
import { screenPeriodChannel } from "../../shared/layout/screenPeriodChannel";
import {
  getCommonAiAnalysisQuestionExcel,
  type AnalysisAnswer,
} from "./aiAnalysis";

export function AiAnalysisAnswer({
  answer,
  onNavigate,
  onQuestion,
  disabled,
}: {
  answer: AnalysisAnswer;
  onNavigate?: () => void;
  onQuestion?: (question: string) => void;
  disabled?: boolean;
}) {
  const inRouter = useInRouterContext();
  // Same stable optional-router convention as createScreenRequest.
  // eslint-disable-next-line react-hooks/rules-of-hooks
  const navigate = inRouter ? useNavigate() : null;
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState(false);
  return (
    <article
      className="ai-analysis-answer"
      data-testid={`analysis-answer-${answer.questionId}`}
    >
      <h3>한 줄 결론</h3>
      <p>{answer.conclusion}</p>
      {answer.provisional && <Badge kind="warn">잠정</Badge>}
      {answer.table && (
        <div className="ai-analysis-table">
          <table>
            <caption>표</caption>
            <thead>
              <tr>
                {answer.table.columns.map((column) => (
                  <th key={column.key}>{column.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {answer.table.rows.map((row, index) => (
                <tr
                  key={index}
                  data-testid={`analysis-row-${answer.questionId}-${index}`}
                >
                  {row.map((cell, col) => (
                    <td key={answer.table!.columns[col].key}>
                      {typeof cell === "number"
                        ? /금액|매출|이익|원가|급여|수입|지출|이월금|청구|미수금/.test(
                            answer.table!.columns[col].label,
                          ) && !/률/.test(answer.table!.columns[col].label)
                          ? `${formatMoney(cell, "원")}원`
                          : cell.toLocaleString("ko-KR")
                        : (cell ?? "–")}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <h3>가정</h3>
      {answer.assumptions.length ? (
        <ul>
          {answer.assumptions.map((text, i) => (
            <li key={i}>{text}</li>
          ))}
        </ul>
      ) : (
        <p>추가 가정 없음</p>
      )}
      <h3>사용한 데이터</h3>
      <p>{answer.usedData}</p>
      <p>기준월 {answer.baseMonth}</p>
      <div className="ai-analysis-actions">
        {answer.actions.map((action, i) => (
          <button
            type="button"
            data-testid={`analysis-action-${answer.questionId}-${i}`}
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
        {answer.answerStatus === "OK" && !!answer.table?.columns.length && (
          <button
            type="button"
            data-testid={`analysis-excel-${answer.questionId}`}
            disabled={downloading}
            onClick={async () => {
              setDownloading(true);
              setError("");
              try {
                await getCommonAiAnalysisQuestionExcel(answer.questionId);
              } catch {
                setError("엑셀을 내려받지 못했습니다.");
              } finally {
                setDownloading(false);
              }
            }}
          >
            엑셀 다운로드
          </button>
        )}
      </div>
      {onQuestion &&
        answer.suggestedQuestions.map((question, i) => (
          <button
            type="button"
            data-testid={`analysis-followup-${answer.questionId}-${i}`}
            key={i}
            disabled={disabled}
            onClick={() => onQuestion(question)}
          >
            {question}
          </button>
        ))}
      {error && <p role="alert">{error}</p>}
    </article>
  );
}

import { Badge } from "../list";
import "./period.css";

const chevron = (d: string) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={d} />
  </svg>
);

/**
 * 시안 「연도만 고르는 화면」(프로젝트·투입 배정·철수 리스트 등) 맨 위 줄:
 * [‹] 2026년 [›] · 올해/지난 해 · 읽기 전용/내년 · 계획 배지 · 이 화면 기준 설명.
 */
export function YearBar({
  year,
  currentYear,
  min,
  max,
  caption,
  onChange,
  testId = "year-bar",
}: {
  year: number;
  currentYear: number;
  min: number;
  max: number;
  caption: string;
  onChange: (year: number) => void;
  testId?: string;
}) {
  return (
    <div className="year-bar" role="group" aria-label="조회 연도" data-testid={testId}>
      <div className="year-bar-nav">
        <button
          type="button"
          className="year-bar-arrow"
          aria-label="이전 해"
          data-testid={`${testId}-prev`}
          disabled={year <= min}
          onClick={() => onChange(year - 1)}
        >
          {chevron("M15 18l-6-6 6-6")}
        </button>
        <strong data-testid={`${testId}-year`}>{year}년</strong>
        <button
          type="button"
          className="year-bar-arrow"
          aria-label="다음 해"
          data-testid={`${testId}-next`}
          disabled={year >= max}
          onClick={() => onChange(year + 1)}
        >
          {chevron("M9 18l6-6-6-6")}
        </button>
      </div>
      {year < currentYear ? (
        <Badge kind="lock">지난 해 · 읽기 전용</Badge>
      ) : year > currentYear ? (
        <Badge kind="warn">내년 · 계획</Badge>
      ) : (
        <Badge kind="info">올해</Badge>
      )}
      <span className="year-bar-cap">{caption}</span>
    </div>
  );
}

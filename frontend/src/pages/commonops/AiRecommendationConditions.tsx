import { useEffect, useId, useRef, useState } from "react";
import {
  aiRecommendationOptionsError,
  useAiRecommendationOptions,
  type AiRecommendationOptions,
  type AiRecommendationOptionsFilter,
  type AiRecommendationTarget,
} from "./aiRecommendationOptions";
import "./aiRecommendationConditions.css";
import "../../shared/list/list.css";
import "../../shared/feedback/feedback.css";

export interface AiRecommendationDraft {
  targetType: AiRecommendationTarget["targetType"];
  targetId: string;
  jobCode: string;
  startMonth: string;
  gradeCode: string;
  needSn?: number;
}
export type AiRecommendationConditionChange = "initial" | "target" | "draft";
interface Props {
  value: AiRecommendationDraft | null;
  onChange: (
    value: AiRecommendationDraft,
    source: AiRecommendationConditionChange,
  ) => void;
  onRecommend: (value: AiRecommendationDraft) => void;
  disabled?: boolean;
  errors?: Partial<Record<keyof AiRecommendationDraft, string>>;
}

function defaultDraft(
  target: AiRecommendationTarget,
  options: AiRecommendationOptions,
): AiRecommendationDraft {
  const jobCode =
    target.needs[0]?.jobCode ??
    options.jobs.find((job) => job.code === "BE")?.code ??
    options.jobs[0]?.code ??
    "";
  const need =
    target.needs.find((item) => item.jobCode === jobCode) ?? target.needs[0];
  return {
    targetType: target.targetType,
    targetId: target.targetId,
    jobCode,
    startMonth: target.startMonth,
    gradeCode:
      options.grades.find((grade) => grade.code === "SR")?.code ??
      options.grades[0]?.code ??
      "",
    ...(target.targetType === "opportunity" && need
      ? { needSn: need.needSn }
      : {}),
  };
}

export function AiRecommendationConditions({
  options,
  value,
  onChange,
  onRecommend,
  disabled = false,
  errors = {},
}: Props & { options: AiRecommendationOptions }) {
  const id = useId();
  const initialized = useRef(false);
  const [showNeedHelp, setShowNeedHelp] = useState(false);
  const draft =
    value ??
    (options.targets[0] ? defaultDraft(options.targets[0], options) : null);
  useEffect(() => {
    if (!value && draft && !initialized.current) {
      initialized.current = true;
      onChange(draft, "initial");
    }
  }, [value, draft, onChange]);
  const target = options.targets.find(
    (item) =>
      item.targetType === draft?.targetType && item.targetId === draft.targetId,
  );
  if (!draft || options.targets.length === 0) {
    return (
      <section
        data-testid="ai-recommendation-conditions"
        className="ai-recommendation-conditions"
      >
        <p role="status">진행 중인 대상이 없습니다.</p>
      </section>
    );
  }
  const validMonth = /^(?!0000)[0-9]{4}-(0[1-9]|1[0-2])$/.test(
    draft.startMonth,
  );
  const afterEnd =
    target?.targetType === "project" &&
    target.endMonth !== null &&
    draft.startMonth > target.endMonth;
  const monthError = afterEnd
    ? "시작월이 프로젝트 종료월 뒤입니다."
    : errors.startMonth;
  const invalid =
    !target ||
    !validMonth ||
    afterEnd ||
    !options.jobs.some((job) => job.code === draft.jobCode) ||
    !options.grades.some((grade) => grade.code === draft.gradeCode);
  function update(next: AiRecommendationDraft) {
    onChange(next, "draft");
  }
  function selectJob(jobCode: string) {
    const { needSn: previousNeed, ...rest } = draft!;
    const need =
      target?.needs.find((item) => item.jobCode === jobCode) ??
      target?.needs[0];
    update({
      ...rest,
      jobCode,
      ...(target?.targetType === "opportunity" && need
        ? { needSn: need.needSn }
        : {}),
    });
  }
  return (
    <section
      data-testid="ai-recommendation-conditions"
      className="ai-recommendation-conditions"
    >
      <div className="ai-condition-row ai-condition-target">
        <label htmlFor={`${id}-target`}>
          프로젝트·영업 기회 <span className="field-required">*</span>
        </label>
        <select
          id={`${id}-target`}
          data-testid="ai-condition-target"
          disabled={disabled}
          value={
            target ? JSON.stringify([target.targetType, target.targetId]) : ""
          }
          aria-invalid={!!errors.targetId || !target}
          aria-describedby={`${id}-target-error`}
          onChange={(event) => {
            const next = options.targets.find(
              (item) =>
                JSON.stringify([item.targetType, item.targetId]) ===
                event.target.value,
            );
            if (!next) return;
            setShowNeedHelp(false);
            onChange(defaultDraft(next, options), "target");
          }}
        >
          {!target && <option value="">진행 중인 대상을 골라 주세요.</option>}
          {options.targets.map((item) => (
            <option
              key={JSON.stringify([item.targetType, item.targetId])}
              value={JSON.stringify([item.targetType, item.targetId])}
            >
              {item.targetType === "project" ? "프로젝트" : "영업 기회"} ·{" "}
              {item.targetName}
            </option>
          ))}
        </select>
        <span id={`${id}-target-error`} className="field-error">
          {errors.targetId ?? errors.targetType}
        </span>
      </div>
      <div className="ai-condition-row">
        <div className="ai-condition-field">
          <label htmlFor={`${id}-job`}>
            직무 <span className="field-required">*</span>
          </label>
          <select
            id={`${id}-job`}
            data-testid="ai-condition-job"
            disabled={disabled}
            value={draft.jobCode}
            aria-invalid={!!errors.jobCode}
            aria-describedby={`${id}-job-error`}
            onChange={(event) => selectJob(event.target.value)}
          >
            {!options.jobs.length && (
              <option value="">선택할 직무가 없습니다.</option>
            )}
            {options.jobs.map((job) => (
              <option key={job.code} value={job.code}>
                {job.name}
              </option>
            ))}
          </select>
          <span id={`${id}-job-error`} className="field-error">
            {errors.jobCode}
          </span>
        </div>
        {target?.targetType === "opportunity" && (
          <div className="ai-condition-field ai-condition-need">
            <div className="ai-condition-label">
              <label htmlFor={`${id}-need`}>가배정 자리</label>
              <button
                type="button"
                data-testid="ai-condition-need-help"
                className="ls-btn ls-btn-sm"
                aria-label="가배정 자리 설명"
                aria-expanded={showNeedHelp}
                aria-controls={`${id}-need-help`}
                onClick={() => setShowNeedHelp((show) => !show)}
              >
                ⓘ
              </button>
            </div>
            <select
              id={`${id}-need`}
              data-testid="ai-condition-need"
              value={draft.needSn ?? ""}
              disabled={disabled || target.needs.length === 0}
              aria-invalid={!!errors.needSn}
              aria-describedby={`${id}-need-help ${id}-need-error`}
              onChange={(event) =>
                update({ ...draft, needSn: Number(event.target.value) })
              }
            >
              {!target.needs.length && (
                <option value="">필요 인력을 먼저 등록해 주세요</option>
              )}
              {target.needs.length > 0 && draft.needSn === undefined && (
                <option value="">필요 인력 줄 선택</option>
              )}
              {target.needs.map((need) => (
                <option key={need.needSn} value={need.needSn}>
                  {need.needSn}번 · {need.jobName} · {need.requiredCount}명 ·{" "}
                  {need.startMonth}
                </option>
              ))}
            </select>
            <span id={`${id}-need-error`} className="field-error">
              {errors.needSn}
            </span>
            {showNeedHelp && (
              <p id={`${id}-need-help`} className="ai-condition-help">
                줄 번호·직무·필요 인원·시작월입니다. 같은 직무 줄을 먼저 고르며
                다른 줄로 바꿀 수 있습니다. 필요 인력이 없어도 추천할 수 있고
                가배정만 할 수 없습니다.
              </p>
            )}
          </div>
        )}
      </div>
      <div className="ai-condition-row">
        <div className="ai-condition-field">
          <label htmlFor={`${id}-month`}>
            시작월 <span className="field-required">*</span>
          </label>
          <input
            id={`${id}-month`}
            type="month"
            data-testid="ai-condition-month"
            disabled={disabled}
            value={draft.startMonth}
            aria-invalid={!!monthError}
            aria-describedby={`${id}-month-error`}
            onChange={(event) => {
              const month = event.target.value;
              update({
                ...draft,
                startMonth:
                  target?.targetType === "project" &&
                  month &&
                  month < target.startMonth
                    ? target.startMonth
                    : month,
              });
            }}
          />
          <span id={`${id}-month-error`} className="field-error">
            {monthError}
          </span>
        </div>
        <div className="ai-condition-field">
          <label htmlFor={`${id}-grade`}>
            등급 <span className="field-required">*</span>
          </label>
          <select
            id={`${id}-grade`}
            data-testid="ai-condition-grade"
            disabled={disabled}
            value={draft.gradeCode}
            aria-invalid={!!errors.gradeCode}
            aria-describedby={`${id}-grade-error`}
            onChange={(event) =>
              update({ ...draft, gradeCode: event.target.value })
            }
          >
            {!options.grades.length && (
              <option value="">선택할 등급이 없습니다.</option>
            )}
            {options.grades.map((grade) => (
              <option key={grade.code} value={grade.code}>
                {grade.name}
              </option>
            ))}
          </select>
          <span id={`${id}-grade-error`} className="field-error">
            {errors.gradeCode}
          </span>
        </div>
        <button
          type="button"
          data-testid="ai-condition-recommend"
          className="ls-btn ls-btn-p"
          disabled={disabled || invalid}
          onClick={() => onRecommend({ ...draft })}
        >
          추천 받기
        </button>
      </div>
      <p className="ai-condition-help">
        휴직·퇴사 예정자 / 해당기간 MM 1.0 제외 · 나이·성별·학력 미사용
      </p>
    </section>
  );
}

export function AiRecommendationOptionsPanel({
  filter,
  ...props
}: Props & { filter?: AiRecommendationOptionsFilter }) {
  const { state, reload } = useAiRecommendationOptions(filter);
  if (state.status === "loading") {
    return (
      <section data-testid="ai-recommendation-options-loading" role="status">
        추천 조건을 불러오는 중입니다.
      </section>
    );
  }
  if (state.status === "error") {
    return (
      <section data-testid="ai-recommendation-options-error" role="alert">
        <p>{aiRecommendationOptionsError}</p>
        <button
          type="button"
          className="ls-btn"
          data-testid="ai-recommendation-options-retry"
          disabled={props.disabled}
          onClick={reload}
        >
          다시 불러오기
        </button>
      </section>
    );
  }
  return <AiRecommendationConditions {...props} options={state.data} />;
}

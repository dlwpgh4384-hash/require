import { useState } from "react";
import { ListFilter, ListFilters } from "../../shared/list";
import {
  SELF_AFFILIATION,
  type PersonalCostProfitFilter,
  type PersonalCostProfitOptions,
} from "./personalCostProfitTypes";

/** 시안 UI-03 보기 고르기: 값은 서버 값, 글자는 시안. */
const VIEWS: [NonNullable<PersonalCostProfitFilter["view"]>, string][] = [
  ["전체", "전체"],
  ["적자만", "적자 인력만"],
  ["동시 투입·1.0 초과만", "동시 투입·1.0 초과만"],
];

/** 시안 UI-03 조건 줄: 이름 · 직원 구분 · 소속(전체/자사/거래처) · 영업 파트 · 직무 · [상세 조건 ▼]. */
export function PersonalCostProfitFilters({
  value,
  options,
  fields,
  onChange,
  onSearch,
  onReset,
}: {
  value: PersonalCostProfitFilter;
  options: PersonalCostProfitOptions | null;
  fields: Record<string, string>;
  onChange: (next: PersonalCostProfitFilter) => void;
  onSearch: () => void;
  onReset: () => void;
}) {
  const [more, setMore] = useState(false);
  const error = (key: string) =>
    fields[key] ? <span className="field-error">{fields[key]}</span> : null;
  // 상세 조건에 값이 든 수(시안 「 (N개 적용)」)
  const moreCount =
    (value.view && value.view !== "전체" ? 1 : 0) +
    (value.includeTurnkey === false ? 1 : 0);
  const select = (
    key: "employeeTypeCode" | "affiliationId" | "salesPartId" | "jobCode",
    label: string,
    extra: [string, string][] = [],
  ) => (
    <ListFilter key={key} label={label} htmlFor={`personal-${key}`}>
      <select
        id={`personal-${key}`}
        className="ls-in"
        data-testid={`personal-${key}`}
        value={value[key] ?? ""}
        onChange={(event) =>
          onChange({ ...value, [key]: event.target.value || undefined })
        }
      >
        <option value="">전체</option>
        {extra.map(([v, t]) => (
          <option key={v} value={v}>
            {t}
          </option>
        ))}
        {(options?.[key] ?? []).map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error(key)}
    </ListFilter>
  );
  return (
    <ListFilters
      onSearch={onSearch}
      onReset={onReset}
      below={
        more ? (
          <>
            <ListFilter label="보기" htmlFor="personal-view">
              <select
                id="personal-view"
                className="ls-in"
                data-testid="personal-view"
                value={value.view ?? "전체"}
                onChange={(event) =>
                  onChange({
                    ...value,
                    view: event.target
                      .value as PersonalCostProfitFilter["view"],
                  })
                }
              >
                {VIEWS.map(([v, t]) => (
                  <option key={v} value={v}>
                    {t}
                  </option>
                ))}
              </select>
              {error("view")}
            </ListFilter>
            <ListFilter label="턴키 단가" htmlFor="personal-turnkey">
              <select
                id="personal-turnkey"
                className="ls-in"
                data-testid="personal-turnkey"
                value={value.includeTurnkey === false ? "false" : "true"}
                onChange={(event) =>
                  onChange({
                    ...value,
                    includeTurnkey: event.target.value === "true",
                  })
                }
              >
                <option value="true">포함</option>
                <option value="false">
                  미포함 (엑셀 「턴키단가미포함」 시트와 같음)
                </option>
              </select>
            </ListFilter>
          </>
        ) : undefined
      }
    >
      <ListFilter label="이름" htmlFor="personal-name">
        <input
          id="personal-name"
          className="ls-in personal-cost-profit-name-in"
          data-testid="personal-name"
          placeholder="이름 일부"
          // 서버도 100자 넘으면 「이름은 100자 이내로 입력해 주세요.」
          maxLength={100}
          value={value.name ?? ""}
          onChange={(event) =>
            onChange({ ...value, name: event.target.value || undefined })
          }
        />
        {error("name")}
      </ListFilter>
      {select("employeeTypeCode", "직원 구분")}
      {select("affiliationId", "소속", [[SELF_AFFILIATION, "자사"]])}
      {select("salesPartId", "영업 파트")}
      {select("jobCode", "직무")}
      <button
        type="button"
        className="ls-btn ls-btn-t"
        data-testid="personal-advanced"
        aria-expanded={more}
        onClick={() => setMore((open) => !open)}
      >
        상세 조건 {more ? "▲" : "▼"}
        {moreCount ? ` (${moreCount}개 적용)` : ""}
      </button>
    </ListFilters>
  );
}

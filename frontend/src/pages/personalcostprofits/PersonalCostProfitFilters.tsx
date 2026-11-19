import { ListFilter, ListFilters } from "../../shared/list";
import type {
  PersonalCostProfitFilter,
  PersonalCostProfitOptions,
} from "./personalCostProfitTypes";

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
  const error = (key: string) =>
    fields[key] ? <span className="field-error">{fields[key]}</span> : null;
  return (
    <ListFilters onSearch={onSearch} onReset={onReset}>
      <ListFilter label="이름" htmlFor="personal-name">
        <input
          id="personal-name"
          data-testid="personal-name"
          value={value.name ?? ""}
          onChange={(event) =>
            onChange({ ...value, name: event.target.value || undefined })
          }
        />
        {error("name")}
      </ListFilter>
      {(
        [
          ["employeeTypeCode", "직원구분"],
          ["affiliationId", "소속"],
          ["salesPartId", "영업 파트"],
          ["jobCode", "직무"],
        ] as const
      ).map(([key, label]) => (
        <ListFilter key={key} label={label} htmlFor={`personal-${key}`}>
          <select
            id={`personal-${key}`}
            data-testid={`personal-${key}`}
            value={value[key] ?? ""}
            onChange={(event) =>
              onChange({ ...value, [key]: event.target.value || undefined })
            }
          >
            <option value="">전체</option>
            {(options?.[key] ?? []).map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {error(key)}
        </ListFilter>
      ))}
      <details className="personal-cost-profit-advanced">
        <summary data-testid="personal-advanced">상세 조건</summary>
        <label>
          보기
          <select
            aria-label="보기"
            data-testid="personal-view"
            value={value.view ?? "전체"}
            onChange={(event) =>
              onChange({
                ...value,
                view: event.target.value as PersonalCostProfitFilter["view"],
              })
            }
          >
            {["전체", "적자만", "동시 투입·1.0 초과만"].map((view) => (
              <option key={view}>{view}</option>
            ))}
          </select>
          {error("view")}
        </label>
        <label>
          턴키 단가
          <select
            aria-label="턴키 단가"
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
            <option value="false">미포함</option>
          </select>
        </label>
      </details>
    </ListFilters>
  );
}

import { useId } from "react";
import {
  useCommonUserOptions,
  userOptionsError,
  type CommonUserOptionsResult,
} from "./userOptions";
import "./userAssignment.css";

export interface UserAssignmentValue {
  roleCds: string[];
  headquarters: boolean;
  projectIds: string[];
}

interface Props {
  value: UserAssignmentValue;
  onChange: (value: UserAssignmentValue) => void;
  disabled?: boolean;
  errors?: Partial<Record<keyof UserAssignmentValue, string>>;
}

function toggle(values: string[], value: string, checked: boolean) {
  return checked
    ? [...values.filter((item) => item !== value), value]
    : values.filter((item) => item !== value);
}

/** UI-23 등록/상세의 역할·담당 영역. 저장과 영향 확인은 부모 계정 폼이 소유한다. */
export function UserAssignmentFields({
  options,
  value,
  onChange,
  disabled = false,
  errors = {},
}: Props & { options: CommonUserOptionsResult }) {
  const id = useId();
  const projectEligible = options.roles.some(
    (role) => role.assignedProjectScope && value.roleCds.includes(role.roleCd),
  );
  return (
    <div
      className="user-assignment-fields"
      data-testid="user-assignment-fields"
    >
      <fieldset
        disabled={disabled}
        aria-invalid={!!errors.roleCds}
        aria-describedby={errors.roleCds ? `${id}-role-error` : undefined}
      >
        <legend>
          역할 <span className="field-required">*</span>
        </legend>
        {options.roles.length === 0 && <p>선택할 역할이 없습니다.</p>}
        {options.roles.map((role, index) => (
          <label key={role.roleCd} data-testid={`user-role-row-${index}`}>
            <input
              type="checkbox"
              name="roleCds"
              value={role.roleCd}
              data-testid={`user-role-input-${index}`}
              checked={value.roleCds.includes(role.roleCd)}
              onChange={(event) =>
                onChange({
                  ...value,
                  roleCds: toggle(
                    value.roleCds,
                    role.roleCd,
                    event.target.checked,
                  ),
                })
              }
            />
            {role.roleName}
          </label>
        ))}
        {errors.roleCds && (
          <p id={`${id}-role-error`} className="field-error">
            {errors.roleCds}
          </p>
        )}
      </fieldset>
      <div>
        <label htmlFor={`${id}-headquarters`}>담당 파트</label>
        <select
          id={`${id}-headquarters`}
          name="headquarters"
          data-testid="user-headquarters-select"
          disabled={disabled}
          value={value.headquarters ? "true" : "false"}
          aria-invalid={!!errors.headquarters}
          aria-describedby={`${id}-part-hint${errors.headquarters ? ` ${id}-part-error` : ""}`}
          onChange={(event) =>
            onChange({ ...value, headquarters: event.target.value === "true" })
          }
        >
          <option value="false">–</option>
          <option value="true">본사(직접 영업)</option>
        </select>
        <small id={`${id}-part-hint`}>
          영업1·영업2 같은 영업 파트의 파트장은 기준정보 › 거래처·영업 파트에서
          정합니다.
        </small>
        {errors.headquarters && (
          <p id={`${id}-part-error`} className="field-error">
            {errors.headquarters}
          </p>
        )}
      </div>
      {projectEligible && (
        <fieldset
          disabled={disabled}
          aria-invalid={!!errors.projectIds}
          aria-describedby={
            errors.projectIds ? `${id}-project-error` : undefined
          }
        >
          <legend>담당 프로젝트</legend>
          {options.projects.length === 0 && <p>선택할 프로젝트가 없습니다.</p>}
          {options.projects.map((project, index) => (
            <label
              key={project.projectId}
              data-testid={`user-project-row-${index}`}
            >
              <input
                type="checkbox"
                name="projectIds"
                value={project.projectId}
                data-testid={`user-project-input-${index}`}
                checked={value.projectIds.includes(project.projectId)}
                onChange={(event) =>
                  onChange({
                    ...value,
                    projectIds: toggle(
                      value.projectIds,
                      project.projectId,
                      event.target.checked,
                    ),
                  })
                }
              />
              {project.projectName}
            </label>
          ))}
          {errors.projectIds && (
            <p id={`${id}-project-error`} className="field-error">
              {errors.projectIds}
            </p>
          )}
        </fieldset>
      )}
    </div>
  );
}

export function UserAssignmentOptions(props: Props) {
  const { state, reload } = useCommonUserOptions();
  if (state.status === "loading") {
    return (
      <div data-testid="user-options-loading" role="status">
        선택 목록을 불러오는 중입니다.
      </div>
    );
  }
  if (state.status === "error") {
    return (
      <div data-testid="user-options-error" role="alert">
        <p>{userOptionsError}</p>
        <button
          type="button"
          data-testid="user-options-retry"
          disabled={props.disabled}
          onClick={reload}
        >
          다시 불러오기
        </button>
      </div>
    );
  }
  return <UserAssignmentFields {...props} options={state.data} />;
}

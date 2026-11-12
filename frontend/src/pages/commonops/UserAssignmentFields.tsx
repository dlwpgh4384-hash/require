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
  /** 수정 중인 계정 아이디(자기 자신은 「지금 PM」에 쓰지 않는다). */
  userId?: string | null;
  /** 지금 파트장인 파트(수정 창 안내). */
  leaderParts?: string[];
}

function toggle(values: string[], value: string, checked: boolean) {
  return checked
    ? [...values.filter((item) => item !== value), value]
    : values.filter((item) => item !== value);
}

/**
 * UI-23 등록·수정 창의 역할·담당 파트·담당 프로젝트(시안 v1.8 그대로 항상 보임).
 * 본사 선택·담당 프로젝트를 가질 수 있는지는 서버가 저장할 때 검사한다(2026-11-12 사용자 결정).
 */
export function UserAssignmentFields({
  options,
  value,
  onChange,
  disabled = false,
  errors = {},
  userId = null,
  leaderParts = [],
}: Props & { options: CommonUserOptionsResult }) {
  const id = useId();
  return (
    <div
      className="user-assignment-fields"
      data-testid="user-assignment-fields"
    >
      <fieldset
        className="ua-field"
        disabled={disabled}
        aria-invalid={!!errors.roleCds}
        aria-describedby={`${id}-role-help${errors.roleCds ? ` ${id}-role-error` : ""}`}
      >
        <legend className="ua-label ua-req">역할</legend>
        {options.roles.length === 0 && <p>선택할 역할이 없습니다.</p>}
        <div className="ua-checks">
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
              />{" "}
              {role.roleName}
            </label>
          ))}
        </div>
        <span id={`${id}-role-help`} className="ua-help">
          여러 개 고를 수 있고, 겹치면 넓은 쪽 권한을 따릅니다.
        </span>
        {errors.roleCds && (
          <p id={`${id}-role-error`} className="field-error">
            {errors.roleCds}
          </p>
        )}
      </fieldset>
      <div className="ua-field">
        <label className="ua-label" htmlFor={`${id}-headquarters`}>
          담당 파트
        </label>
        <select
          id={`${id}-headquarters`}
          name="headquarters"
          className="ua-in"
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
        <span id={`${id}-part-hint`} className="ua-help">
          「본사(직접 영업)」을 고른 영업 파트장은 본사 영업 책임자로 본사
          프로젝트·본사 소속 인력을 보고, 본사 프로젝트 미수 2단계 알림을
          받습니다(없으면 경영진이 받음).{" "}
          <b>
            영업1·영업2 같은 영업 파트의 파트장은 기준정보 › 거래처·영업
            파트에서 정합니다.
          </b>
          {leaderParts.length > 0 && (
            <>
              <br />
              지금 파트장인 파트: {leaderParts.join(", ")}
            </>
          )}
        </span>
        {errors.headquarters && (
          <p id={`${id}-part-error`} className="field-error">
            {errors.headquarters}
          </p>
        )}
      </div>
      <fieldset
        className="ua-field"
        disabled={disabled}
        aria-invalid={!!errors.projectIds}
        aria-describedby={`${id}-project-help${errors.projectIds ? ` ${id}-project-error` : ""}`}
      >
        <legend className="ua-label">담당 프로젝트</legend>
        <div className="ua-projects" data-testid="user-project-list">
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
              />{" "}
              {project.projectName}
              {project.pmName && project.pmUserId !== userId && (
                <span className="ua-cap"> (지금 PM: {project.pmName})</span>
              )}
            </label>
          ))}
        </div>
        <span id={`${id}-project-help`} className="ua-help">
          PM 역할이면 고른 프로젝트의 PM이 됩니다. 한 프로젝트의 PM은 1명이라,
          다른 PM이 맡던 프로젝트를 고르면 그 사람에게서 빠집니다.
        </span>
        {errors.projectIds && (
          <p id={`${id}-project-error`} className="field-error">
            {errors.projectIds}
          </p>
        )}
      </fieldset>
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

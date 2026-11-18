import { useEffect, useId, useState } from "react";
import {
  Badge,
  ListFilter,
  ListFilters,
  listMessages,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { koreanToday } from "../../shared/time";
import { ProjectLink } from "../projects/ProjectLink";
import {
  getCommonProjects,
  projectFailure,
  ProjectRequestError,
  type Project,
} from "../projects/projects";
import {
  getCommonManpowerComparisons,
  type DifferenceStatus,
  type ManpowerComparisonFilter,
  type ManpowerComparisonResult,
} from "./manpowerComparisons";
import "../projects/projects.css";
import "./manpowerComparisons.css";

const overlaps = (
  project: Pick<Project, "startDate" | "endDate">,
  year: number,
) => project.startDate <= `${year}-12-31` && project.endDate >= `${year}-01-01`;
const mm = (value: number) =>
  value.toLocaleString("ko-KR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 20,
  });

/** SCR-UI-11: annual readonly comparison; the existing screen shell owns title and tabs. */
export function ManpowerComparisonPage() {
  const currentYear = Number(koreanToday().slice(0, 4));
  const [draft, setDraft] = useState<ManpowerComparisonFilter>(() => ({
    year: Math.min(2027, Math.max(2025, currentYear)),
  }));
  const [applied, setApplied] = useState<ManpowerComparisonFilter>(draft);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<ManpowerComparisonResult | null>(null);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [projects, setProjects] = useState<Project[]>([]);
  const [optionError, setOptionError] = useState("");
  const [optionRevision, setOptionRevision] = useState(0);
  const id = useId();

  useEffect(() => {
    const abort = new AbortController();
    setOptionError("");
    getCommonProjects(abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setProjects(result.items);
      })
      .catch((reason) => {
        if (!abort.signal.aborted) setOptionError(projectFailure(reason));
      });
    return () => abort.abort();
  }, [optionRevision]);

  useEffect(() => {
    const abort = new AbortController();
    setData(null);
    setError("");
    setFields({});
    getCommonManpowerComparisons(applied, abort.signal)
      .then((result) => {
        if (!abort.signal.aborted) setData(result);
      })
      .catch((reason) => {
        if (abort.signal.aborted) return;
        setError(projectFailure(reason));
        if (reason instanceof ProjectRequestError)
          setFields(
            Object.fromEntries(
              reason.errors.map((field) => [field.field, field.message]),
            ),
          );
      });
    return () => abort.abort();
  }, [applied, revision]);

  const changeYear = (year: number) =>
    setDraft((previous) => ({
      ...previous,
      year,
      projectId:
        previous.projectId &&
        projects.some(
          (p) => p.projectId === previous.projectId && overlaps(p, year),
        )
          ? previous.projectId
          : undefined,
    }));
  const search = () => {
    setApplied({ ...draft });
    setRevision((value) => value + 1);
  };
  const fieldError = (name: string) =>
    fields[name] ? (
      <span
        className="field-error"
        data-testid={`manpower-comparison-${name}-error`}
      >
        {fields[name]}
      </span>
    ) : null;

  return (
    <section
      className="project-page manpower-comparison-page"
      data-testid="manpower-comparison-page"
      data-screen-id="SCR-UI-11"
      data-action-id="M25-183"
    >
      <div data-action-id="M25-184">
        <YearBar
          year={draft.year}
          currentYear={currentYear}
          min={2025}
          max={2027}
          caption="인원 계획 대비 투입 · 조회 전용"
          onChange={changeYear}
          testId="manpower-comparison-year"
        />
        {fieldError("year")}
      </div>
      <ListFilters
        onSearch={search}
        onReset={() => setDraft({ year: draft.year })}
        testId="manpower-comparison-filters"
        activeCount={
          Number(!!draft.projectId) + Number(!!draft.differenceStatus)
        }
      >
        <ListFilter label="프로젝트" htmlFor={`${id}-project`}>
          <select
            id={`${id}-project`}
            data-testid="manpower-comparison-project"
            value={draft.projectId ?? ""}
            onChange={(event) =>
              setDraft({ ...draft, projectId: event.target.value || undefined })
            }
          >
            <option value="">전체</option>
            {projects
              .filter((project) => overlaps(project, draft.year))
              .map((project) => (
                <option key={project.projectId} value={project.projectId}>
                  {project.projectName}
                </option>
              ))}
          </select>
          {fieldError("projectId")}
        </ListFilter>
        <ListFilter label="차이" htmlFor={`${id}-difference`}>
          <select
            id={`${id}-difference`}
            data-testid="manpower-comparison-difference"
            value={draft.differenceStatus ?? ""}
            onChange={(event) =>
              setDraft({
                ...draft,
                differenceStatus: (event.target.value || undefined) as
                  | DifferenceStatus
                  | undefined,
              })
            }
          >
            <option value="">전체</option>
            <option value="부족">부족</option>
            <option value="초과">초과</option>
            <option value="맞음">맞음</option>
          </select>
          {fieldError("differenceStatus")}
        </ListFilter>
      </ListFilters>
      {optionError && (
        <div role="alert" data-testid="manpower-comparison-options-error">
          프로젝트 조건을 불러오지 못했습니다. {optionError}{" "}
          <button
            type="button"
            className="ls-btn"
            data-testid="manpower-comparison-options-retry"
            onClick={() => setOptionRevision((value) => value + 1)}
          >
            다시 시도
          </button>
        </div>
      )}
      <div className="ls-root">
        <div className="ls-twrap" aria-busy={!data && !error}>
          <table
            className="ls-t manpower-comparison-table"
            aria-label="인원 계획 대비 투입"
          >
            <thead>
              <tr>
                <th scope="col">프로젝트</th>
                <th scope="col">직무</th>
                <th scope="col" className="manpower-comparison-number">
                  계획 MM
                </th>
                <th scope="col" className="manpower-comparison-number">
                  실제·예정 MM
                </th>
                <th scope="col" className="manpower-comparison-number">
                  차이
                </th>
              </tr>
            </thead>
            <tbody>
              {error ? (
                <tr>
                  <td colSpan={5}>
                    <div
                      role="alert"
                      className="ls-empty"
                      data-testid="manpower-comparison-error"
                    >
                      <p>{error}</p>
                      <button
                        type="button"
                        className="ls-btn"
                        data-action-id="M25-186"
                        data-testid="manpower-comparison-retry"
                        onClick={() => setRevision((value) => value + 1)}
                      >
                        다시 시도
                      </button>
                    </div>
                  </td>
                </tr>
              ) : data === null ? (
                <tr>
                  <td colSpan={5}>
                    <p
                      role="status"
                      className="ls-empty"
                      data-testid="manpower-comparison-loading"
                    >
                      {listMessages.loading}
                    </p>
                  </td>
                </tr>
              ) : data.items.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <p
                      className="ls-empty"
                      data-testid="manpower-comparison-empty"
                    >
                      {listMessages.empty}
                    </p>
                  </td>
                </tr>
              ) : (
                data.items.map((row) => {
                  const key = `${row.projectId}-${row.jobCode}`;
                  return (
                    <tr
                      key={key}
                      data-testid={`manpower-comparison-row-${key}`}
                    >
                      <td>
                        <ProjectLink
                          projectId={row.projectId}
                          name={row.projectName}
                          testId={`manpower-comparison-project-${key}`}
                          readOnly
                        />
                      </td>
                      <td>{row.jobName}</td>
                      <td className="manpower-comparison-number">
                        {mm(row.plannedMm)}
                      </td>
                      <td className="manpower-comparison-number">
                        {mm(row.actualMm)}
                      </td>
                      <td className="manpower-comparison-number">
                        <Badge
                          kind={
                            row.differenceStatus === "부족"
                              ? "bad"
                              : row.differenceStatus === "초과"
                                ? "warn"
                                : "ok"
                          }
                        >
                          {row.differenceStatus} {mm(row.differenceMm)}
                        </Badge>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
            <tfoot data-testid="manpower-comparison-totals">
              <tr>
                <th scope="row" colSpan={2}>
                  합계
                </th>
                <td className="manpower-comparison-number">
                  {data ? mm(data.totals.plannedMm) : "–"}
                </td>
                <td className="manpower-comparison-number">
                  {data ? mm(data.totals.actualMm) : "–"}
                </td>
                <td className="manpower-comparison-number">
                  {data ? mm(data.totals.differenceMm) : "–"}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </section>
  );
}

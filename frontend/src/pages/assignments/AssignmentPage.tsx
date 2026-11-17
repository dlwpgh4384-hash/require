import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { YearBar } from "../../shared/period/YearBar";
import { koreanToday } from "../../shared/time";
import { Banner } from "../../shared/list/DetailPanel";
import { ConfirmDialog, Dialog } from "../../shared/feedback/Dialog";
import { ProjectLink } from "../projects/ProjectLink";
import { getCommonProjects, type Project } from "../projects/projects";
import { useProjectTitleHost } from "../projects/ProjectWorkspace";
import { getCommonCodes } from "../codegroups/codeGroups";
import { UploadActions } from "../uploads/UploadActions";
import { AssignmentForm } from "./AssignmentForm";
import { AssignmentDetail } from "./AssignmentDetail";
import {
  assignmentPriceEditable,
  getCommonAssignments,
  getCommonAssignmentCandidates,
  getCommonAssignmentsExcel,
  postCommonAssignmentCandidateRemove,
  postCommonAssignmentCancel,
  putCommonAssignmentExtensionPossibility,
  type Assignment,
  type AssignmentCandidate,
  type AssignmentFilter,
} from "./assignments";
import "./assignments.css";

const developmentRoles = ["FIN", "SALES_LDR"];
export function AssignmentPage({
  roles = developmentRoles,
}: {
  roles?: readonly string[];
}) {
  const currentYear = Number(koreanToday().slice(0, 4));
  const [filter, setFilter] = useState<AssignmentFilter>({ year: currentYear });
  const [rows, setRows] = useState<Assignment[]>([]),
    [candidates, setCandidates] = useState<AssignmentCandidate[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [jobs, setJobs] = useState<{ code: string; codeName: string }[]>([]),
    [extensions, setExtensions] = useState<
      { code: string; codeName: string }[]
    >([]);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [message, setMessage] = useState("");
  const [revision, setRevision] = useState(0),
    [selected, setSelected] = useState<string[]>([]),
    [busy, setBusy] = useState(false);
  const [form, setForm] = useState(false),
    [candidate, setCandidate] = useState<AssignmentCandidate | undefined>();
  const [detail, setDetail] = useState<string | null>(null),
    [cancelling, setCancelling] = useState(false),
    [bulk, setBulk] = useState(false),
    [extension, setExtension] = useState("");
  const titleHost = useProjectTitleHost();
  const readOnly = filter.year < currentYear;
  const refresh = () => setRevision((r) => r + 1);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError("");
    setSelected([]);
    Promise.all([
      getCommonAssignments(filter, controller.signal),
      getCommonAssignmentCandidates(),
      getCommonProjects(),
      getCommonCodes("JOB"),
      getCommonCodes("EXT_PSBL"),
    ])
      .then(([r, c, p, j, e]) => {
        if (active) {
          setRows(r.items ?? []);
          setCandidates(c.items ?? []);
          setProjects(p.items ?? []);
          setJobs(j.items ?? []);
          setExtensions(e.items ?? []);
        }
      })
      .catch((e) => {
        if (active && e.name !== "AbortError") setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      controller.abort();
    };
  }, [filter, revision]);
  useEffect(() => {
    if (readOnly) {
      setForm(false);
      setCancelling(false);
      setBulk(false);
    }
  }, [readOnly]);
  const create = (c?: AssignmentCandidate) => {
    setCandidate(c);
    setForm(true);
  };
  const remove = async (id: string) => {
    setBusy(true);
    try {
      await postCommonAssignmentCandidateRemove(id);
      refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const changeBulk = async (cancel: boolean) => {
    setBusy(true);
    setError("");
    try {
      const chosen = rows.filter((r) => selected.includes(r.assignmentId));
      if (cancel) await postCommonAssignmentCancel(chosen);
      else await putCommonAssignmentExtensionPossibility(chosen, extension);
      setCancelling(false);
      setBulk(false);
      setMessage(
        cancel ? "선택한 배정을 취소했습니다." : "연장 가능성을 변경했습니다.",
      );
      refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const actions = (
    <>
      {!readOnly && (
        <>
          <UploadActions
            formTypeCode="B5"
            targetName="투입 배정"
            onApplied={refresh}
          />
          <button
            data-testid="assignment-create"
            disabled={loading || !!error}
            onClick={() => create()}
          >
            + 배정
          </button>
        </>
      )}
      <button
        data-testid="assignment-excel"
        disabled={loading || busy || !!error}
        onClick={() => {
          setBusy(true);
          getCommonAssignmentsExcel(filter)
            .catch((e) => setError(e.message))
            .finally(() => setBusy(false));
        }}
      >
        엑셀 다운로드
      </button>
    </>
  );
  return (
    <section
      className="assignment-page"
      data-testid="assignment-page"
      data-screen-id="UI-12"
    >
      {titleHost && createPortal(actions, titleHost)}
      <YearBar
        year={filter.year}
        currentYear={currentYear}
        min={1}
        max={9999}
        caption="그 해에 투입 기간이 걸친 배정 · MM은 그 해 합계"
        onChange={(year) => setFilter((f) => ({ ...f, year }))}
        testId="assignment-year"
      />
      {readOnly && (
        <Banner kind="lock">지난해에는 읽기만 할 수 있습니다</Banner>
      )}
      {message && (
        <p role="status" data-testid="assignment-success">
          {message}
        </p>
      )}
      <div className="assignment-filters">
        <label>
          프로젝트
          <select
            data-testid="assignment-filter-project"
            value={filter.projectId ?? ""}
            onChange={(e) =>
              setFilter((f) => ({ ...f, projectId: e.target.value }))
            }
          >
            <option value="">전체</option>
            {projects
              .filter((p) => !["END", "LOSE"].includes(p.statusCode))
              .map((p) => (
                <option key={p.projectId} value={p.projectId}>
                  {p.projectName}
                </option>
              ))}
          </select>
        </label>
        <label>
          직무
          <select
            data-testid="assignment-filter-job"
            value={filter.jobCode ?? ""}
            onChange={(e) =>
              setFilter((f) => ({ ...f, jobCode: e.target.value }))
            }
          >
            <option value="">전체</option>
            {jobs.map((j) => (
              <option key={j.code} value={j.code}>
                {j.codeName}
              </option>
            ))}
          </select>
        </label>
        <label>
          경고
          <select
            data-testid="assignment-filter-warning"
            value={filter.warning ?? ""}
            onChange={(e) =>
              setFilter((f) => ({ ...f, warning: e.target.value }))
            }
          >
            <option value="">전체</option>
            <option>MM 1.0 초과</option>
            <option>미계약 선투입</option>
          </select>
        </label>
        <label>
          이름/관리번호
          <input
            data-testid="assignment-filter-keyword"
            value={filter.keyword ?? ""}
            onChange={(e) =>
              setFilter((f) => ({ ...f, keyword: e.target.value }))
            }
          />
        </label>
      </div>
      {loading ? (
        <p data-testid="assignment-loading">불러오는 중…</p>
      ) : error ? (
        <p role="alert">
          {error}
          <button data-testid="assignment-retry" onClick={refresh}>
            다시 시도
          </button>
        </p>
      ) : (
        <>
          <section
            className="assignment-candidates"
            data-testid="assignment-candidates"
          >
            <h2>배정 후보 ({candidates.length})</h2>
            {!candidates.length && <p>배정 후보가 없습니다.</p>}
            {candidates.map((c) => (
              <article
                key={c.candidateId}
                data-testid={`assignment-candidate-${c.candidateId}`}
              >
                <strong>{c.projectName}</strong>
                <span>
                  {c.name} · {c.jobName} · {c.startDate} ~ {c.endDate}
                </span>
                {!readOnly && (
                  <>
                    <button
                      data-testid={`candidate-confirm-${c.candidateId}`}
                      disabled={busy}
                      onClick={() => create(c)}
                    >
                      확정
                    </button>
                    <button
                      data-testid={`candidate-remove-${c.candidateId}`}
                      disabled={busy}
                      onClick={() => void remove(c.candidateId)}
                    >
                      빼기
                    </button>
                  </>
                )}
              </article>
            ))}
          </section>
          {!readOnly && selected.length > 0 && (
            <div className="assignment-bulk">
              <span>{selected.length}개 선택</span>
              <button
                data-testid="assignment-bulk-extension"
                disabled={busy}
                onClick={() => {
                  setExtension("");
                  setBulk(true);
                }}
              >
                연장 가능성 변경
              </button>
              <button
                data-testid="assignment-bulk-cancel"
                disabled={busy}
                onClick={() => setCancelling(true)}
              >
                배정 취소
              </button>
            </div>
          )}
          <div className="assignment-table-scroll">
            <table>
              <thead>
                <tr>
                  {!readOnly && <th>선택</th>}
                  {[
                    "경고",
                    "프로젝트",
                    "이름",
                    "직무",
                    "투입일",
                    "철수일",
                    ...(assignmentPriceEditable(roles) ? ["월 단가"] : []),
                    "월 MM",
                    "그 해 MM",
                    "연장 가능성",
                    "투입 구분",
                  ].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr
                    key={r.assignmentId}
                    data-testid={`assignment-row-${r.assignmentId}`}
                  >
                    {!readOnly && (
                      <td>
                        <input
                          type="checkbox"
                          data-testid={`assignment-select-${r.assignmentId}`}
                          aria-label={`${r.name} 배정 선택`}
                          checked={selected.includes(r.assignmentId)}
                          onChange={(e) =>
                            setSelected((s) =>
                              e.target.checked
                                ? [...s, r.assignmentId]
                                : s.filter((id) => id !== r.assignmentId),
                            )
                          }
                        />
                      </td>
                    )}
                    <td
                      title={`${r.warnings.join(" / ")} 월 MM 합계 ${r.monthlyMmTotal ?? "–"}`}
                    >
                      {r.warnings.length > 0 ? "⚠" : "–"}
                    </td>
                    <td>
                      <ProjectLink
                        projectId={r.projectId}
                        name={r.projectName}
                        testId={`assignment-project-${r.assignmentId}`}
                      />
                    </td>
                    <td>
                      <button
                        className="link-button"
                        data-testid={`assignment-open-${r.assignmentId}`}
                        onClick={() => setDetail(r.assignmentId)}
                      >
                        {r.name}
                      </button>
                      <small>{r.managementNumber}</small>
                    </td>
                    <td>{r.jobName}</td>
                    <td>{r.startDate}</td>
                    <td>{r.endDate}</td>
                    {assignmentPriceEditable(roles) && (
                      <td>
                        {projects.find((p) => p.projectId === r.projectId)
                          ?.contractTypeCode === "TK"
                          ? "배분표"
                          : (r.billingPrice?.toLocaleString("ko-KR") ?? "–")}
                      </td>
                    )}
                    <td>{r.monthlyMm.toFixed(2)}</td>
                    <td>{r.yearMm.toFixed(2)}</td>
                    <td>{r.extensionPossibilityName}</td>
                    <td>{r.inputTypeName}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!rows.length && (
            <p data-testid="assignment-empty">조회된 투입 배정이 없습니다.</p>
          )}
        </>
      )}
      {form && !readOnly && (
        <AssignmentForm
          projects={projects.filter((p) =>
            ["ING", "PRE"].includes(p.statusCode),
          )}
          candidate={candidate}
          roles={roles}
          onClose={() => setForm(false)}
          onSaved={(r) => {
            setForm(false);
            setMessage(["배정을 저장했습니다.", ...r.warnings].join(" "));
            setDetail(r.assignmentId);
            refresh();
          }}
        />
      )}
      {detail && (
        <AssignmentDetail
          id={detail}
          roles={roles}
          readOnly={readOnly}
          onClose={() => setDetail(null)}
          onChanged={refresh}
        />
      )}
      {!readOnly && cancelling && (
        <ConfirmDialog
          title="선택 배정을 취소할까요?"
          description="원배정과 취소 이력은 보존됩니다. 마감월 금액과 턴키 청구 매출은 바뀌지 않으며 전월 조정은 UI-10에서 직접 입력합니다."
          testId="assignment-cancel-confirm"
          proceedLabel="배정 취소"
          destructive
          onCancel={() => setCancelling(false)}
          onProceed={() => void changeBulk(true)}
        />
      )}
      {!readOnly && bulk && (
        <Dialog
          title="연장 가능성 변경"
          testId="assignment-bulk-dialog"
          onClose={() => setBulk(false)}
        >
          <label>
            연장 가능성
            <select
              data-testid="assignment-bulk-value"
              value={extension}
              onChange={(e) => setExtension(e.target.value)}
            >
              <option value="">미정</option>
              {extensions.map((e) => (
                <option key={e.code} value={e.code}>
                  {e.codeName}
                </option>
              ))}
            </select>
          </label>
          <div className="app-actions">
            <button
              data-testid="assignment-bulk-close"
              onClick={() => setBulk(false)}
            >
              취소
            </button>
            <button
              data-testid="assignment-bulk-save"
              disabled={busy}
              onClick={() => void changeBulk(false)}
            >
              저장
            </button>
          </div>
        </Dialog>
      )}
    </section>
  );
}

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import {
  ConfirmDialog,
  Dialog,
  Toast,
  type ToastKind,
} from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  ListFilter,
  ListFilters,
  ListView,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { koreanToday } from "../../shared/time";
import { feedbackMessages } from "../../shared/feedback/messages";
import { getCommonCodes } from "../codegroups/codeGroups";
import { ProjectLink } from "../projects/ProjectLink";
import {
  getCommonProjects,
  projectFailure,
  won,
  type Project,
} from "../projects/projects";
import { useAssignmentTitleHost } from "./AssignmentWorkspace";
import { UploadActions } from "../uploads/UploadActions";
import { AssignmentDetail } from "./AssignmentDetail";
import { AssignmentForm } from "./AssignmentForm";
import {
  EXT_KIND,
  EXT_LABEL,
  assignmentPriceEditable,
  getCommonAssignmentCandidates,
  getCommonAssignments,
  getCommonAssignmentsExcel,
  isAssignmentConflict,
  mmf,
  postCommonAssignmentCandidateRemove,
  postCommonAssignmentCancel,
  putCommonAssignmentExtensionPossibility,
  type Assignment,
  type AssignmentCandidate,
} from "./assignments";
import "../projects/projects.css";
import "./assignments.css";

type Filter = {
  projectId: string;
  jobCode: string;
  warning: string;
  keyword: string;
};
const emptyFilter: Filter = {
  projectId: "",
  jobCode: "",
  warning: "",
  keyword: "",
};
const developmentRoles = ["FIN", "SALES_LDR"];
const svg = (d: string, size = 16, className?: string) => (
  <svg
    className={className}
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d={d} />
  </svg>
);
export const warnIcon = (size = 16) =>
  svg("M12 3l9 16H3zM12 10v4M12 17h.01", size, "assignment-warn-icon");
const plusIcon = svg("M12 5v14M5 12h14");
const lockIcon = svg("M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4", 18);
/** 시안 extBadge: 높음 초록·보통 노랑·낮음 빨강, 없으면 「–」. */
export function ExtensionBadge({ code }: { code?: string | null }) {
  return code && EXT_LABEL[code] ? (
    <Badge kind={EXT_KIND[code]}>{EXT_LABEL[code]}</Badge>
  ) : (
    <span className="project-sub">–</span>
  );
}
const sortValue = (a: Assignment, key: string): string | number => {
  switch (key) {
    case "billingPrice":
      return a.billingPrice ?? -1;
    case "monthlyMm":
      return a.monthlyMm;
    case "yearMm":
      return a.yearMm;
    case "extensionPossibilityCode":
      return a.extensionPossibilityCode
        ? "HML".indexOf(a.extensionPossibilityCode)
        : 9;
    default:
      return String((a as unknown as Record<string, unknown>)[key] ?? "");
  }
};

/** 시안 투입 배정(UI-12): 연도 줄, 조건, 배정 후보 카드, 공통 목록, 상세·배정 창. */
export function AssignmentPage({
  roles = developmentRoles,
}: {
  roles?: readonly string[];
}) {
  const host = useAssignmentTitleHost();
  const presentation = useListPresentation();
  const currentYear = Number(koreanToday().slice(0, 4));
  const [year, setYear] = useState(currentYear);
  const [filter, setFilter] = useState(emptyFilter);
  const [applied, setApplied] = useState(emptyFilter);
  const [rows, setRows] = useState<Assignment[] | null>(null);
  const [error, setError] = useState("");
  const [candidates, setCandidates] = useState<AssignmentCandidate[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [jobs, setJobs] = useState<{ code: string; codeName: string }[]>([]);
  const [revision, setRevision] = useState(0);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<string[]>([]);
  const [allResults, setAllResults] = useState(false);
  const [form, setForm] = useState<{ candidate?: AssignmentCandidate } | null>(
    null,
  );
  const [detail, setDetail] = useState<{ id: string; tab?: string } | null>(
    null,
  );
  const [cancelling, setCancelling] = useState(false);
  const [bulk, setBulk] = useState(false);
  const [extension, setExtension] = useState("H");
  const [dropping, setDropping] = useState<AssignmentCandidate | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  busyRef.current = busy;
  const [highlight, setHighlight] = useState<string | null>(null);
  const highlightTimer = useRef<number>();
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const [downloading, setDownloading] = useState(false);
  const readOnly = year < currentYear;
  const canRate = assignmentPriceEditable(roles);
  const refresh = () => setRevision((n) => n + 1);
  const toast = (message: string, kind: ToastKind = "ok") => {
    setNoticeKind(kind);
    setNotice(message);
  };
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    setRows(null);
    setSelected([]);
    setAllResults(false);
    getCommonAssignments({ year, ...applied }, abort.signal)
      .then((r) => !abort.signal.aborted && setRows(r.items ?? []))
      .catch(
        (e) =>
          !abort.signal.aborted &&
          e?.name !== "AbortError" &&
          setError(projectFailure(e)),
      );
    return () => abort.abort();
  }, [year, applied, revision]);
  useEffect(() => {
    let active = true;
    Promise.all([
      getCommonAssignmentCandidates(),
      getCommonProjects(),
      getCommonCodes("JOB"),
    ])
      .then(([c, p, j]) => {
        if (!active) return;
        setCandidates(c.items ?? []);
        setProjects(p.items ?? []);
        setJobs(j.items ?? []);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [revision]);
  useEffect(() => {
    if (!readOnly) return;
    setForm(null);
    setCancelling(false);
    setBulk(false);
    setDropping(null);
  }, [readOnly]);
  useEffect(() => () => window.clearTimeout(highlightTimer.current), []);
  const projectById = useMemo(
    () => new Map(projects.map((p) => [p.projectId, p])),
    [projects],
  );
  const sorted = useMemo(() => {
    const list = [...(rows ?? [])];
    // 시안 기본 정렬: 프로젝트명 → 투입일(정렬 표시 없음)
    if (!sort)
      return list.sort(
        (a, b) =>
          a.projectName.localeCompare(b.projectName, "ko") ||
          a.startDate.localeCompare(b.startDate),
      );
    const dir = sort.dir === "asc" ? 1 : -1;
    return list.sort((a, b) => {
      const x = sortValue(a, sort.key);
      const y = sortValue(b, sort.key);
      return (x > y ? 1 : x < y ? -1 : 0) * dir;
    });
  }, [rows, sort]);
  const pageRows = sorted.slice(
    (page - 1) * presentation.pageSize,
    page * presentation.pageSize,
  );
  const targets = allResults
    ? sorted
    : sorted.filter((r) => selected.includes(r.assignmentId));
  const rateLabel = (a: Assignment) =>
    projectById.get(a.projectId)?.contractTypeCode === "TK" ? (
      <span className="project-sub">배분표</span>
    ) : a.inputTypeCode === "FREE" ? (
      <span className="project-sub">무상지원</span>
    ) : (
      won(a.billingPrice)
    );
  const columns: ListColumn<Assignment>[] = [
    {
      key: "warnings",
      label: "경고",
      sortable: false,
      align: "center",
      render: (a) =>
        a.warnings.length ? (
          <span
            className="assignment-warn"
            title={a.warnings.join(", ")}
            data-testid={`assignment-warn-${a.assignmentId}`}
          >
            {warnIcon(16)}
          </span>
        ) : null,
    },
    {
      key: "projectName",
      label: "프로젝트",
      required: true,
      render: (a) => (
        <ProjectLink
          projectId={a.projectId}
          name={a.projectName}
          testId={`assignment-project-${a.assignmentId}`}
        />
      ),
    },
    {
      key: "name",
      label: "이름",
      render: (a) => (
        <span className="assignment-pname">
          {a.name}
          <small>{a.managementNumber}</small>
        </span>
      ),
    },
    { key: "jobName", label: "직무" },
    { key: "startDate", label: "투입일" },
    { key: "endDate", label: "철수일" },
    ...(canRate
      ? [
          {
            key: "billingPrice",
            label: "단가",
            align: "right" as const,
            render: rateLabel,
          },
        ]
      : []),
    {
      key: "monthlyMm",
      label: "월 MM",
      align: "right",
      render: (a) =>
        (a.monthlyMmTotal ?? 0) > 1 ? (
          <span
            className="assignment-warn"
            title={`이 사람의 월 MM 합계 ${mmf(a.monthlyMmTotal)}`}
          >
            {mmf(a.monthlyMm)} ⚠
          </span>
        ) : (
          mmf(a.monthlyMm)
        ),
    },
    {
      key: "yearMm",
      label: `${year}년 MM`,
      align: "right",
      render: (a) => mmf(a.yearMm),
    },
    {
      key: "extensionPossibilityCode",
      label: "연장 가능성",
      render: (a) => <ExtensionBadge code={a.extensionPossibilityCode} />,
    },
    {
      key: "inputTypeName",
      label: "투입 구분",
      render: (a) => <Badge kind="neutral">{a.inputTypeName}</Badge>,
    },
  ];
  const cols = useListColumns("UI-12", columns);
  const download = async () => {
    setDownloading(true);
    try {
      await getCommonAssignmentsExcel({ year, ...applied });
    } catch (reason) {
      toast(projectFailure(reason), "warn");
    } finally {
      setDownloading(false);
    }
  };
  // 공통 동시 수정 처리: 다른 사람이 먼저 바꿨으면(409) 안내하고 목록을 다시 읽는다.
  const conflict = (reason: unknown) => {
    toast(
      reason instanceof Error && reason.message
        ? reason.message
        : feedbackMessages.conflict,
      "warn",
    );
    refresh();
  };
  const afterBulk = (message: string) => {
    setSelected([]);
    setAllResults(false);
    refresh();
    toast(message);
  };
  const changeExtension = async () => {
    const changed = targets.filter(
      (r) => (r.extensionPossibilityCode ?? "") !== extension,
    );
    setBusy(true);
    try {
      if (changed.length)
        await putCommonAssignmentExtensionPossibility(changed, extension);
      setBulk(false);
      const skipped = targets.length - changed.length;
      afterBulk(
        `${changed.length}건의 연장 가능성을 '${EXT_LABEL[extension] ?? "미정"}'으로 바꿨습니다.${skipped ? ` (이미 같은 값 ${skipped}건 제외)` : ""}`,
      );
    } catch (reason) {
      setBulk(false);
      // 다른 사람이 먼저 바꿨으면(409) 목록을 다시 읽어 새 수정 시각을 받는다.
      if (isAssignmentConflict(reason)) conflict(reason);
      else toast(projectFailure(reason), "warn");
    } finally {
      setBusy(false);
    }
  };
  // 공통 기능: 연장 가능성 변경 창도 Ctrl+S로 저장(다른 작은 창과 같게)
  const bulkSave = useRef(changeExtension);
  bulkSave.current = changeExtension;
  useEffect(() => {
    if (!bulk) return;
    const handler = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        if (!busyRef.current) void bulkSave.current();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [bulk]);
  const cancelAssignments = async () => {
    // 확인 창 단추는 보내는 동안 막히지 않으므로 두 번 눌러도 한 번만 보낸다.
    if (busyRef.current) return;
    busyRef.current = true;
    const count = targets.length;
    setBusy(true);
    try {
      await postCommonAssignmentCancel(targets);
      setCancelling(false);
      afterBulk(
        `${count}건의 배정을 취소했습니다. 변경 이력에 「취소」로 남겼습니다.`,
      );
    } catch (reason) {
      setCancelling(false);
      if (isAssignmentConflict(reason)) conflict(reason);
      else toast(projectFailure(reason), "warn");
    } finally {
      setBusy(false);
    }
  };
  const dropCandidate = async (c: AssignmentCandidate) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      await postCommonAssignmentCandidateRemove(c);
      setDropping(null);
      refresh();
      toast("배정 후보에서 뺐습니다.");
    } catch (reason) {
      setDropping(null);
      if (isAssignmentConflict(reason)) conflict(reason);
      else toast(projectFailure(reason), "warn");
    } finally {
      setBusy(false);
    }
  };
  const actions = readOnly ? null : (
    <div className="project-actions" data-testid="assignment-actions">
      <UploadActions
        formTypeCode="B5"
        targetName="투입 목록"
        onApplied={refresh}
      />
      <button
        type="button"
        className="primary-button project-title-button"
        data-testid="assignment-create"
        onClick={() => {
          setDetail(null);
          setForm({});
        }}
      >
        {plusIcon}
        배정
      </button>
    </div>
  );
  // 제목줄 자리가 없으면(다른 화면이 앞에 있을 때) 본문에 단추를 다시 그리지 않는다.
  const titleActions = (content: ReactNode) =>
    host ? createPortal(content, host) : null;
  const select = (
    key: "projectId" | "jobCode" | "warning",
    label: string,
    items: [string, string][],
  ) => (
    <ListFilter key={key} label={label} htmlFor={`assignment-filter-${key}`}>
      <select
        className="ls-in"
        id={`assignment-filter-${key}`}
        data-testid={`assignment-filter-${key}`}
        value={filter[key]}
        onChange={(e) => setFilter((f) => ({ ...f, [key]: e.target.value }))}
      >
        <option value="">전체</option>
        {items.map(([value, name]) => (
          <option
            key={value}
            value={value}
            data-testid={`assignment-filter-${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}-option-${items.findIndex((item) => item[0] === value)}`}
          >
            {name}
          </option>
        ))}
      </select>
    </ListFilter>
  );
  const resetFilters = () => {
    setFilter(emptyFilter);
    setApplied(emptyFilter);
    setPage(1);
  };
  const showCandidates = !readOnly && candidates.length > 0;
  return (
    <section
      className="screen-body assignment-page"
      data-testid="assignment-page"
      data-screen-id="UI-12"
    >
      {titleActions(actions)}
      <YearBar
        year={year}
        currentYear={currentYear}
        min={1}
        max={currentYear + 1}
        caption="그 해에 투입 기간이 걸친 배정 · MM은 그 해 합계"
        testId="assignment-year"
        onChange={(next) => {
          setYear(next);
          setPage(1);
          setDetail(null);
        }}
      />
      {readOnly && (
        <Banner kind="lock" icon={lockIcon}>
          <span data-testid="assignment-lock-banner">
            <strong>{year}년</strong>은 지난 해라 읽기만 할 수 있습니다.
          </span>
        </Banner>
      )}
      {showCandidates && (
        <section
          className="assignment-candidates"
          data-testid="assignment-candidates"
        >
          <div className="assignment-card-h">
            <strong>배정 후보 ({candidates.length})</strong>
            <span className="project-sub">
              영업 파이프라인에서 수주 처리한 기회의 가배정 인력입니다. [확정]을
              누르면 배정 창이 채워진 채로 열립니다.
            </span>
          </div>
          <div className="ls-twrap">
            <table className="ls-t">
              <thead>
                <tr>
                  <th>프로젝트</th>
                  <th>이름</th>
                  <th>직무</th>
                  <th>예정 기간</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {candidates.map((c) => (
                  <tr
                    key={c.candidateId}
                    data-testid={`assignment-candidate-${c.candidateId}`}
                  >
                    <td>
                      <ProjectLink
                        projectId={c.projectId}
                        name={c.projectName}
                        testId={`candidate-project-${c.candidateId}`}
                      />
                    </td>
                    <td>
                      {c.name}{" "}
                      {c.managementNumber && (
                        <span className="project-sub">
                          {c.managementNumber}
                        </span>
                      )}
                    </td>
                    <td>{c.jobName}</td>
                    <td>
                      {c.startDate} ~ {c.endDate}
                    </td>
                    <td className="assignment-candidate-acts">
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm ls-btn-p"
                        data-testid={`candidate-confirm-${c.candidateId}`}
                        disabled={busy}
                        onClick={() => {
                          setDetail(null);
                          setForm({ candidate: c });
                        }}
                      >
                        확정
                      </button>{" "}
                      <button
                        type="button"
                        className="ls-btn ls-btn-sm"
                        data-testid={`candidate-remove-${c.candidateId}`}
                        disabled={busy}
                        onClick={() => setDropping(c)}
                      >
                        빼기
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <ListFilters
        testId="assignment-filters"
        activeCount={Object.values(applied).filter(Boolean).length}
        onSearch={() => {
          setApplied({ ...filter });
          setPage(1);
        }}
        onReset={resetFilters}
      >
        {select(
          "projectId",
          "프로젝트",
          projects
            .filter((p) => !["END", "LOSE"].includes(p.statusCode))
            .map((p) => [p.projectId, p.projectName]),
        )}
        {select(
          "jobCode",
          "직무",
          jobs.map((j) => [j.code, j.codeName]),
        )}
        {select("warning", "경고", [
          ["MM 1.0 초과", "MM 1.0 초과"],
          ["미계약 선투입", "미계약 선투입"],
        ])}
        <ListFilter label="이름" htmlFor="assignment-filter-keyword">
          <input
            className="ls-in"
            id="assignment-filter-keyword"
            data-testid="assignment-filter-keyword"
            placeholder="이름 또는 관리번호"
            value={filter.keyword}
            onChange={(e) =>
              setFilter((f) => ({ ...f, keyword: e.target.value }))
            }
          />
        </ListFilter>
      </ListFilters>
      <div
        data-testid={
          rows !== null && !error && sorted.length === 0
            ? "assignment-empty"
            : "assignment-list-wrap"
        }
      >
        <ListView
          screenId="UI-12"
          testId="assignment-list"
          columns={columns}
          visibleColumns={cols.visible}
          hiddenColumns={cols.hidden}
          onToggleColumn={cols.toggle}
          onResetColumns={cols.reset}
          rows={rows === null && !error ? null : error ? [] : pageRows}
          rowKey={(a) => a.assignmentId}
          total={sorted.length}
          page={page}
          pageSize={presentation.pageSize}
          onPage={setPage}
          onPageSize={(size) => {
            presentation.setPageSize(size);
            setPage(1);
          }}
          dense={presentation.dense}
          onDense={presentation.setDense}
          sort={sort}
          onSort={(next) => {
            setSort(next);
            setPage(1);
          }}
          selectable={!readOnly}
          selected={selected}
          onSelect={(keys) => {
            setSelected(keys);
            setAllResults(false);
          }}
          allResultsSelected={allResults}
          onSelectAllResults={() => setAllResults(true)}
          bulkActions={
            readOnly
              ? []
              : [
                  {
                    key: "extension",
                    label: "연장 가능성 변경",
                    onClick: () => {
                      setExtension("H");
                      setBulk(true);
                    },
                  },
                  {
                    key: "cancel",
                    label: "배정 취소",
                    danger: true,
                    onClick: () => setCancelling(true),
                  },
                ]
          }
          onRowClick={(a) => setDetail({ id: a.assignmentId })}
          highlightKey={highlight}
          onResetFilters={resetFilters}
          mobileRows={rows === null ? null : sorted}
          renderCard={(a) => (
            // 시안 휴대폰 카드: 이름(관리번호 · 직무) + 경고 또는 연장 가능성 / 프로젝트 + MM / 기간
            <>
              <div className="ls-r1">
                <span className="assignment-pname">
                  {a.name}
                  <small>
                    {a.managementNumber} · {a.jobName}
                  </small>
                </span>
                {a.warnings.length ? (
                  <Badge kind="warn">
                    {warnIcon(12)}
                    {a.warnings[0]}
                  </Badge>
                ) : (
                  <ExtensionBadge code={a.extensionPossibilityCode} />
                )}
              </div>
              <div className="ls-r2">
                <span>{a.projectName}</span>
                <b>{mmf(a.monthlyMm)} MM</b>
              </div>
              <div className="ls-r2">
                <span>
                  {a.startDate} ~ {a.endDate}
                </span>
              </div>
            </>
          )}
          onDownload={() => void download()}
          downloading={downloading}
          error={error}
          onRetry={refresh}
        />
      </div>
      {form && !readOnly && (
        <AssignmentForm
          projects={projects.filter((p) =>
            ["ING", "PRE"].includes(p.statusCode),
          )}
          candidate={form.candidate}
          roles={roles}
          onClose={() => setForm(null)}
          onConflict={(reason) => {
            setForm(null);
            conflict(reason);
          }}
          onSaved={(row, message) => {
            setForm(null);
            refresh();
            window.clearTimeout(highlightTimer.current);
            setHighlight(row.assignmentId);
            highlightTimer.current = window.setTimeout(
              () => setHighlight(null),
              6000,
            );
            toast(message);
          }}
        />
      )}
      {detail && (
        <AssignmentDetail
          key={detail.id}
          id={detail.id}
          initialTab={detail.tab}
          roles={roles}
          readOnly={readOnly}
          onClose={() => setDetail(null)}
          onChanged={(message, kind) => {
            refresh();
            if (message) toast(message, kind);
          }}
        />
      )}
      {!readOnly && cancelling && (
        <ConfirmDialog
          title={`선택한 ${targets.length}건의 배정을 취소할까요?`}
          description="지우지 않고 변경 이력에 「취소」로 남깁니다. 투입 목록과 MM·원가 계산에서는 빠집니다."
          testId="assignment-cancel-confirm"
          proceedLabel="배정 취소"
          destructive
          onCancel={() => setCancelling(false)}
          onProceed={() => void cancelAssignments()}
        />
      )}
      {!readOnly && dropping && (
        <ConfirmDialog
          title={`${dropping.name}님을 배정 후보에서 뺄까요?`}
          description="배정하지 않고 후보 목록에서만 뺍니다. 인원 계획의 계획 MM은 그대로라 그 몫은 미충원으로 남습니다."
          testId="candidate-remove-confirm"
          proceedLabel="빼기"
          onCancel={() => setDropping(null)}
          onProceed={() => void dropCandidate(dropping)}
        />
      )}
      {!readOnly && bulk && (
        <Dialog
          title="연장 가능성 변경"
          testId="assignment-bulk-dialog"
          closable
          onClose={() => !busy && setBulk(false)}
        >
          <form
            data-testid="assignment-bulk-form"
            className="project-modal"
            noValidate
            onSubmit={(e) => {
              e.preventDefault();
              void changeExtension();
            }}
          >
            <p>
              선택한 {targets.length}건의 연장 가능성을 한 번에 바꿉니다. 건마다
              변경 이력이 1줄씩 남습니다.
            </p>
            <div className="project-fc">
              <label htmlFor="assignment-bulk-value">
                연장 가능성<span className="project-bad"> *</span>
              </label>
              <select
                id="assignment-bulk-value"
                data-testid="assignment-bulk-value"
                value={extension}
                onChange={(e) => setExtension(e.target.value)}
              >
                {["H", "M", "L", ""].map((code) => (
                  <option
                    key={code}
                    value={code}
                    data-testid={`assignment-bulk-value-option-${code.toLowerCase() || "undecided"}`}
                  >
                    {EXT_LABEL[code] ?? "미정"}
                  </option>
                ))}
              </select>
            </div>
            <div className="app-actions">
              <button
                type="button"
                data-testid="assignment-bulk-close"
                disabled={busy}
                onClick={() => setBulk(false)}
              >
                취소
              </button>
              <button
                type="submit"
                className="primary-button"
                data-testid="assignment-bulk-save"
                disabled={busy}
              >
                바꾸기
              </button>
            </div>
          </form>
        </Dialog>
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          onDismiss={() => setNotice("")}
          testId="assignment-toast"
        />
      )}
    </section>
  );
}

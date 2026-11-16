import { useEffect, useState } from "react";
import { ListFilters, ListView } from "../../shared/list/ListView";
import {
  useListColumns,
  useListPresentation,
} from "../../shared/list/useListPresentation";
import type { ListColumn } from "../../shared/list/listTypes";
import { UploadActions } from "../uploads/UploadActions";
import { ProjectForm } from "./ProjectForm";
import { ProjectDetail } from "./ProjectDetail";
import { ProjectDistribution } from "./ProjectDistribution";
import { useOptionalProjectWorkspace } from "./ProjectWorkspace";
import {
  filterProjects,
  getCommonProjects,
  projectFailure,
  projectToday,
  won,
  type Project,
  type ProjectFilters,
} from "./projects";
import "./projects.css";

export function ProjectPage() {
  const workspace = useOptionalProjectWorkspace();
  const presentation = useListPresentation();
  const initial = (): ProjectFilters => ({
    year: projectToday().slice(0, 4),
    contractTypeCode: "",
    statusCode: "",
    salesPartId: "",
    unassignedOnly: false,
    keyword: "",
  });
  const [filter, setFilter] = useState(initial);
  const [rows, setRows] = useState<Project[] | null>(null);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const [registering, setRegistering] = useState(false);
  const [editing, setEditing] = useState<Project | null>(null);
  const [detail, setDetail] = useState<string | null>(null);
  const [distribution, setDistribution] = useState<string | null>(null);
  const refresh = () => setRevision((n) => n + 1);
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    setRows(null);
    getCommonProjects(abort.signal)
      .then((r) => !abort.signal.aborted && setRows(r.items))
      .catch((e) => !abort.signal.aborted && setError(projectFailure(e)));
    return () => abort.abort();
  }, [revision, workspace?.revision]);
  const readOnly = filter.year < projectToday().slice(0, 4);
  useEffect(() => {
    workspace?.setReadOnly(readOnly);
  }, [readOnly, workspace?.setReadOnly]);
  const result = filterProjects(rows ?? [], filter);
  const change = <K extends keyof ProjectFilters>(
    key: K,
    value: ProjectFilters[K],
  ) => {
    setPage(1);
    setFilter((f) => ({ ...f, [key]: value }));
  };
  const columns: ListColumn<Project>[] = [
    {
      key: "projectName",
      label: "프로젝트",
      required: true,
      render: (p) => (
        <button
          className="ls-link"
          data-testid={`project-name-${p.projectId.toLowerCase()}`}
          onClick={(e) => {
            e.stopPropagation();
            setDetail(p.projectId);
          }}
        >
          {p.projectName}
          <small>{p.projectId}</small>
        </button>
      ),
    },
    { key: "customerName", label: "고객사" },
    { key: "contractTypeName", label: "계약유형" },
    {
      key: "startDate",
      label: "계약기간",
      render: (p) => `${p.startDate} ~ ${p.endDate}`,
    },
    {
      key: "assignedPeopleCount",
      label: "투입 인원",
      align: "right",
      render: (p) => `${p.assignedPeopleCount}명`,
    },
    {
      key: "salesPartName",
      label: "영업 파트",
      render: (p) => p.salesPartName ?? "미지정",
    },
    { key: "statusName", label: "상태" },
    {
      key: "contractAmount",
      label: "계약금액",
      align: "right",
      render: (p) =>
        p.contractTypeCode === "IN" ? "–" : won(p.contractAmount),
    },
    {
      key: "distributionStepName",
      label: "배분표",
      render: (p) =>
        p.contractTypeCode === "TK" ? (
          <button
            data-testid={`project-distribution-${p.projectId.toLowerCase()}`}
            onClick={(e) => {
              e.stopPropagation();
              setDistribution(p.projectId);
            }}
          >
            {p.distributionStepName ?? "올리기"}
            {p.distributionTotalWarning && " · 합계 차이"}
            {p.distributionMismatchCount > 0 &&
              ` · 불일치 ${p.distributionMismatchCount}`}
          </button>
        ) : (
          "–"
        ),
    },
  ];
  const columnState = useListColumns("UI-01", columns);
  const years = [
    ...new Set([
      projectToday().slice(0, 4),
      ...(rows ?? []).flatMap((p) => {
        const values = [];
        for (
          let n = Number(p.startDate.slice(0, 4));
          n <= Number(p.endDate.slice(0, 4));
          n++
        )
          values.push(String(n));
        return values;
      }),
    ]),
  ]
    .sort()
    .reverse();
  const options = (
    key: "contractTypeCode" | "statusCode" | "salesPartId",
    name: "contractTypeName" | "statusName" | "salesPartName",
  ) => [
    ...new Map(
      (rows ?? []).filter((p) => p[key]).map((p) => [p[key]!, p[name]!]),
    ).entries(),
  ];
  if (!readOnly && (registering || workspace?.registering || editing))
    return (
      <ProjectForm
        project={editing ?? undefined}
        onClose={() => {
          setRegistering(false);
          setEditing(null);
          workspace?.closeRegistration();
        }}
        onSaved={(p) => {
          setRegistering(false);
          setEditing(null);
          workspace?.closeRegistration();
          refresh();
          setDetail(p.projectId);
        }}
      />
    );
  if (distribution)
    return (
      <ProjectDistribution
        projectId={distribution}
        readOnly={readOnly}
        onBack={() => {
          setDistribution(null);
          refresh();
        }}
      />
    );
  return (
    <section
      className="project-page"
      data-testid="project-page"
      data-screen-id="UI-01"
    >
      <div className="project-page-heading">
        <h2>프로젝트</h2>
        <div className="project-actions">
          <UploadActions
            formTypeCode="B9"
            disabled={readOnly}
            onApplied={refresh}
          />
          <button
            className="primary-button"
            data-testid="project-register"
            disabled={readOnly}
            onClick={() => setRegistering(true)}
          >
            프로젝트 등록
          </button>
        </div>
      </div>
      {readOnly && (
        <p className="project-banner">
          지난 해 화면은 읽기 전용입니다. 연속 프로젝트는 올해에서 수정해
          주세요.
        </p>
      )}
      <ListFilters
        activeCount={
          [
            filter.contractTypeCode,
            filter.statusCode,
            filter.salesPartId,
            filter.keyword,
            filter.unassignedOnly,
          ].filter(Boolean).length
        }
        onSearch={() => setPage(1)}
        onReset={() => {
          setFilter(initial());
          setPage(1);
        }}
      >
        <label>
          연도
          <select
            data-testid="project-year"
            value={filter.year}
            onChange={(e) => change("year", e.target.value)}
          >
            {years.map((y) => (
              <option key={y} value={y}>
                {y}년
              </option>
            ))}
          </select>
        </label>
        <label>
          계약유형
          <select
            data-testid="project-filter-type"
            value={filter.contractTypeCode}
            onChange={(e) => change("contractTypeCode", e.target.value)}
          >
            <option value="">전체</option>
            {options("contractTypeCode", "contractTypeName").map(
              ([v, label]) => (
                <option key={v} value={v}>
                  {label}
                </option>
              ),
            )}
          </select>
        </label>
        <label>
          상태
          <select
            data-testid="project-filter-state"
            value={filter.statusCode}
            onChange={(e) => change("statusCode", e.target.value)}
          >
            <option value="">전체</option>
            {options("statusCode", "statusName").map(([v, label]) => (
              <option key={v} value={v}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          영업 파트
          <select
            data-testid="project-filter-part"
            value={filter.salesPartId}
            onChange={(e) => change("salesPartId", e.target.value)}
          >
            <option value="">전체</option>
            <option value="unassigned">미지정</option>
            {options("salesPartId", "salesPartName").map(([v, label]) => (
              <option key={v} value={v}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          검색
          <input
            data-testid="project-keyword"
            placeholder="프로젝트명 / 고객사"
            value={filter.keyword}
            onChange={(e) => change("keyword", e.target.value)}
          />
        </label>
      </ListFilters>
      {result.unassignedCount > 0 && (
        <aside
          className="project-banner"
          data-testid="project-unassigned-banner"
        >
          영업 파트 미지정 프로젝트 {result.unassignedCount}건{" "}
          <button
            data-testid="project-unassigned-only"
            aria-pressed={filter.unassignedOnly}
            onClick={() => change("unassignedOnly", !filter.unassignedOnly)}
          >
            {filter.unassignedOnly ? "전체 보기" : "이 건만 보기"}
          </button>
        </aside>
      )}
      <ListView
        screenId="UI-01"
        columns={columns}
        visibleColumns={columnState.visible}
        hiddenColumns={columnState.hidden}
        onToggleColumn={columnState.toggle}
        onResetColumns={columnState.reset}
        rows={
          rows === null
            ? null
            : result.items.slice(
                (page - 1) * presentation.pageSize,
                page * presentation.pageSize,
              )
        }
        mobileRows={rows === null ? null : result.items}
        rowKey={(p) => p.projectId}
        total={result.items.length}
        page={page}
        pageSize={presentation.pageSize}
        onPage={setPage}
        onPageSize={presentation.setPageSize}
        dense={presentation.dense}
        onDense={presentation.setDense}
        sort={null}
        onSort={() => undefined}
        error={error}
        onRetry={refresh}
        onRowClick={(p) => setDetail(p.projectId)}
        renderCard={(p) => (
          <article data-testid={`project-card-${p.projectId.toLowerCase()}`}>
            <button
              data-testid={`project-card-open-${p.projectId.toLowerCase()}`}
              onClick={() => setDetail(p.projectId)}
            >
              {p.projectName}
            </button>
            <p>
              {p.customerName} · {p.statusName}
            </p>
            <p>
              {p.startDate} ~ {p.endDate}
            </p>
            <p>
              {p.salesPartName ?? "미지정"} ·{" "}
              {p.contractTypeCode === "IN" ? "–" : won(p.contractAmount)}
            </p>
          </article>
        )}
      />
      {detail && (
        <ProjectDetail
          projectId={detail}
          onEdit={setEditing}
          readOnly={readOnly}
          onClose={() => setDetail(null)}
          onChanged={refresh}
          onDistribution={(id) => {
            setDetail(null);
            setDistribution(id);
          }}
        />
      )}
    </section>
  );
}

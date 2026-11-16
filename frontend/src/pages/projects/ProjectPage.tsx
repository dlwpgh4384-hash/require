import { useEffect, useMemo, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  Banner,
  ListFilter,
  ListFilters,
  ListView,
  downloadFile,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { YearBar } from "../../shared/period/YearBar";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonParts } from "../parts/parts";
import { UploadActions } from "../uploads/UploadActions";
import { ProjectDetail } from "./ProjectDetail";
import { ProjectDistribution } from "./ProjectDistribution";
import { ProjectForm } from "./ProjectForm";
import { useProjectTitleHost } from "./ProjectWorkspace";
import {
  HQ_PART,
  getCommonProjects,
  projectFailure,
  projectToday,
  projectsExcelUrl,
  statusKind,
  typeLabel,
  won,
  type Project,
  type ProjectListQuery,
} from "./projects";
import "./projects.css";

type Filter = Omit<ProjectListQuery, "year">;
const emptyFilter: Filter = { contractTypeCode: "", statusCode: "", salesPartId: "", q: "" };
const UNASSIGNED = "unassigned";
const PROJECT_UPLOAD_NOTES = [
  "프로젝트ID를 비운 줄은 반영할 때 ID가 붙습니다(위 표는 붙을 ID). 이 ID로 투입(B.5) 양식을 채웁니다. 업로드는 새로 등록만 합니다.",
];
const internal = (p: Project) => p.contractTypeCode === "IN";
const plusIcon = (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 5v14M5 12h14" />
  </svg>
);
const warnIcon = (size: number) => (
  <svg className="project-warn-icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M12 3l9 16H3zM12 10v4M12 17h.01" />
  </svg>
);
/** 시안 금액 줄임(휴대폰 카드): 1억 이상 「14.8억 원」, 그 아래 「5,300만 원」. */
const kor = (v: number) =>
  v >= 1e8 ? `${(v / 1e8).toFixed(1)}억 원` : `${Math.round(v / 1e4).toLocaleString("ko-KR")}만 원`;
/** 시안 영업 파트 칸: 내부 「없음(내부)」, 없음 「미지정」 배지, 본사 「본사」 배지 + 직접 영업. */
export function PartLabel({ project }: { project: Project }) {
  if (internal(project)) return <span className="project-sub">없음(내부)</span>;
  if (!project.salesPartId) return <Badge kind="warn">미지정</Badge>;
  if (project.salesPartId === HQ_PART)
    return (
      <span>
        <Badge kind="neutral">본사</Badge> <span className="project-cap">직접 영업</span>
      </span>
    );
  return <>{project.salesPartName}</>;
}
const sortValue = (p: Project, key: string): string | number => {
  switch (key) {
    case "contractAmount":
      return internal(p) ? -1 : p.contractAmount;
    case "assignedPeopleCount":
      return p.assignedPeopleCount;
    case "startDate":
      return p.startDate;
    case "contractTypeName":
      return typeLabel(p);
    case "salesPartName":
      return internal(p) ? "" : p.salesPartName ?? "";
    case "distributionStepName":
      return p.contractTypeCode === "TK" ? p.distributionStepName ?? "없음" : "";
    default:
      return String((p as unknown as Record<string, unknown>)[key] ?? "");
  }
};

export function ProjectPage() {
  const host = useProjectTitleHost();
  const presentation = useListPresentation();
  const currentYear = Number(projectToday().slice(0, 4));
  const [year, setYear] = useState(currentYear);
  const [rows, setRows] = useState<Project[] | null>(null);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [filter, setFilter] = useState(emptyFilter);
  const [applied, setApplied] = useState(emptyFilter);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [page, setPage] = useState(1);
  const [detail, setDetail] = useState<string | null>(null);
  const [form, setForm] = useState<{ project?: Project } | null>(null);
  const [distribution, setDistribution] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const [downloading, setDownloading] = useState(false);
  const [options, setOptions] = useState<{
    types: { code: string; codeName: string }[];
    states: { code: string; codeName: string }[];
    parts: { partId: string; partName: string }[];
  }>({ types: [], states: [], parts: [] });
  const refresh = () => setRevision((n) => n + 1);
  const toast = (message: string, kind: ToastKind = "ok") => {
    setNoticeKind(kind);
    setNotice(message);
  };
  useEffect(() => {
    const abort = new AbortController();
    setError("");
    setRows(null);
    getCommonProjects(abort.signal)
      .then((r) => !abort.signal.aborted && setRows(r.items))
      .catch((e) => !abort.signal.aborted && setError(projectFailure(e)));
    return () => abort.abort();
  }, [revision]);
  useEffect(() => {
    let active = true;
    Promise.all([getCommonCodes("CTRT_TYPE"), getCommonCodes("PROJ_STAT"), getCommonParts()])
      .then(([types, states, parts]) => {
        if (active)
          setOptions({
            types: types.items ?? [],
            states: states.items ?? [],
            parts: (parts.items ?? []).filter((p) => p.useYn === "Y"),
          });
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);
  const readOnly = year < currentYear;
  const minYear = Math.min(
    currentYear,
    ...(rows ?? []).map((p) => Number(p.startDate.slice(0, 4))),
  );
  const yearly = useMemo(
    () => (rows ?? []).filter((p) => p.startDate <= `${year}-12-31` && p.endDate >= `${year}-01-01`),
    [rows, year],
  );
  const unassigned = yearly.filter((p) => !internal(p) && !p.salesPartId).length;
  const shown = useMemo(() => {
    const q = applied.q.trim().toLowerCase();
    const list = yearly.filter(
      (p) =>
        (!applied.contractTypeCode || p.contractTypeCode === applied.contractTypeCode) &&
        (!applied.statusCode || p.statusCode === applied.statusCode) &&
        (!applied.salesPartId ||
          (applied.salesPartId === UNASSIGNED ? !internal(p) && !p.salesPartId : p.salesPartId === applied.salesPartId)) &&
        (!q || `${p.projectName} ${p.customerName}`.toLowerCase().includes(q)),
    );
    if (!sort)
      // 시안: 처음에는 시작일 늦은 순(정렬 표시 없음)
      return [...list].sort((a, b) => b.startDate.localeCompare(a.startDate) || a.projectId.localeCompare(b.projectId));
    const dir = sort.dir === "asc" ? 1 : -1;
    return [...list].sort((a, b) => {
      const x = sortValue(a, sort.key);
      const y = sortValue(b, sort.key);
      return (x > y ? 1 : x < y ? -1 : 0) * dir;
    });
  }, [yearly, applied, sort]);
  const openDistribution = (id: string) => {
    setDetail(null);
    setDistribution(id);
  };
  const distributionCell = (p: Project) => {
    if (p.contractTypeCode !== "TK") return <span className="project-sub">–</span>;
    if (!p.distributionStepName)
      return p.statusCode === "END" || p.statusCode === "LOSE" ? (
        <span className="project-sub">없음</span>
      ) : (
        <button
          type="button"
          className="project-lnk"
          data-testid={`project-distribution-${p.projectId.toLowerCase()}`}
          onClick={(e) => {
            e.stopPropagation();
            openDistribution(p.projectId);
          }}
        >
          올리기
        </button>
      );
    return (
      <span className="project-dist-cell">
        <button
          type="button"
          className="project-lnk"
          data-testid={`project-distribution-${p.projectId.toLowerCase()}`}
          onClick={(e) => {
            e.stopPropagation();
            openDistribution(p.projectId);
          }}
        >
          {p.distributionStepName}
        </button>
        {p.distributionTotalWarning && (
          <span className="project-warn" title="합계가 계약금액과 다름" data-testid={`project-distribution-total-${p.projectId.toLowerCase()}`}>
            {warnIcon(14)}
          </span>
        )}
        {p.distributionMismatchCount > 0 && <Badge kind="warn">불일치 {p.distributionMismatchCount}</Badge>}
      </span>
    );
  };
  const columns: ListColumn<Project>[] = [
    { key: "projectId", label: "프로젝트ID" },
    { key: "projectName", label: "프로젝트명", required: true },
    { key: "customerName", label: "고객사" },
    { key: "contractTypeName", label: "계약유형", render: (p) => <Badge kind="neutral">{typeLabel(p)}</Badge> },
    { key: "startDate", label: "계약 기간", render: (p) => `${p.startDate} ~ ${p.endDate}` },
    {
      key: "contractAmount",
      label: "계약금액",
      align: "right",
      render: (p) => (internal(p) ? <span className="project-sub">–</span> : won(p.contractAmount)),
    },
    { key: "assignedPeopleCount", label: "투입 인원", align: "right", render: (p) => `${p.assignedPeopleCount}명` },
    { key: "salesPartName", label: "영업 파트", render: (p) => <PartLabel project={p} /> },
    { key: "statusName", label: "상태", render: (p) => <Badge kind={statusKind(p.statusCode)}>{p.statusName}</Badge> },
    { key: "distributionStepName", label: "배분표", render: distributionCell },
  ];
  const cols = useListColumns("UI-01", columns);
  const download = async () => {
    setDownloading(true);
    try {
      await downloadFile(projectsExcelUrl({ year: String(year), ...applied }), `프로젝트_${year}년.xlsx`);
    } catch (reason) {
      toast(projectFailure(reason), "warn");
    } finally {
      setDownloading(false);
    }
  };
  const actions = readOnly ? null : (
    <div className="project-actions" data-testid="project-actions">
      <UploadActions formTypeCode="B9" targetName="프로젝트" notes={PROJECT_UPLOAD_NOTES} onApplied={refresh} />
      <button
        type="button"
        className="primary-button project-title-button"
        data-testid="project-register"
        onClick={() => {
          setDetail(null);
          setForm({});
        }}
      >
        {plusIcon}
        등록
      </button>
    </div>
  );
  const titleActions = (content: ReactNode) => (host ? createPortal(content, host) : content);
  const toastNode = notice && <Toast message={notice} kind={noticeKind} onDismiss={() => setNotice("")} testId="project-toast" />;
  if (form)
    return (
      <>
        <ProjectForm
          project={form.project}
          onClose={() => setForm(null)}
          onSaved={(p) => {
            setForm(null);
            refresh();
            toast("저장했습니다.");
            setDetail(p.projectId);
          }}
        />
        {toastNode}
      </>
    );
  if (distribution)
    return (
      <>
        <ProjectDistribution
          projectId={distribution}
          readOnly={readOnly}
          onBack={() => {
            setDistribution(null);
            refresh();
          }}
        />
        {toastNode}
      </>
    );
  const select = (key: "contractTypeCode" | "statusCode" | "salesPartId", label: string, items: [string, string][]) => (
    <ListFilter key={key} label={label} htmlFor={`project-filter-${key}`}>
      <select
        className="ls-in"
        id={`project-filter-${key}`}
        data-testid={`project-filter-${key}`}
        value={filter[key]}
        onChange={(e) => setFilter((f) => ({ ...f, [key]: e.target.value }))}
      >
        <option value="">전체</option>
        {items.map(([value, name]) => (
          <option key={value} value={value}>
            {name}
          </option>
        ))}
      </select>
    </ListFilter>
  );
  return (
    <section className="project-page" data-testid="project-page" data-screen-id="UI-01">
      {titleActions(actions)}
      <YearBar
        year={year}
        currentYear={currentYear}
        min={minYear}
        max={currentYear + 1}
        caption="그 해에 수행 기간이 걸친 프로젝트"
        testId="project-year"
        onChange={(next) => {
          setYear(next);
          setPage(1);
          setDetail(null);
        }}
      />
      {unassigned > 0 && (
        <Banner
          kind="warn"
          action={
            <button
              type="button"
              className="ls-btn ls-btn-sm"
              data-testid="project-unassigned-only"
              onClick={() => {
                const next = { ...emptyFilter, salesPartId: UNASSIGNED };
                setFilter(next);
                setApplied(next);
                setPage(1);
                toast("영업 파트가 없는 프로젝트만 보여 줍니다.", "info");
              }}
            >
              이 건만 보기
            </button>
          }
        >
          {warnIcon(18)}
          <span data-testid="project-unassigned-banner">
            영업 파트가 없는 프로젝트가 <strong>{unassigned}건</strong> 있습니다. 파트를 정해야 파트별 실적·권한·정산에 들어갑니다.
          </span>
        </Banner>
      )}
      <ListFilters
        testId="project-filters"
        activeCount={Object.values(applied).filter(Boolean).length}
        onSearch={() => {
          setApplied({ ...filter });
          setPage(1);
        }}
        onReset={() => {
          setFilter(emptyFilter);
          setApplied(emptyFilter);
          setPage(1);
        }}
      >
        {select(
          "contractTypeCode",
          "계약유형",
          options.types.map((c) => [c.code, c.code === "IN" ? "내부" : c.codeName]),
        )}
        {select("statusCode", "상태", options.states.map((c) => [c.code, c.codeName]))}
        {select("salesPartId", "영업 파트", [
          ...options.parts.map((p): [string, string] => [p.partId, p.partName]),
          [HQ_PART, "본사(직접 영업)"],
          [UNASSIGNED, "미지정"],
        ])}
        <ListFilter label="프로젝트명" htmlFor="project-search">
          <input
            className="ls-in project-search"
            id="project-search"
            data-testid="project-keyword"
            placeholder="프로젝트명 또는 고객사"
            value={filter.q}
            onChange={(e) => setFilter((f) => ({ ...f, q: e.target.value }))}
          />
        </ListFilter>
      </ListFilters>
      <ListView
        screenId="UI-01"
        testId="project-list"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        rows={rows === null && !error ? null : error ? [] : shown.slice((page - 1) * presentation.pageSize, page * presentation.pageSize)}
        rowKey={(p) => p.projectId}
        total={shown.length}
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
        onRowClick={(p) => setDetail(p.projectId)}
        mobileRows={rows === null ? null : shown}
        renderCard={(p) => (
          // 시안 휴대폰 카드: 이름 + 상태 / 고객사 · 유형 + 금액 / 기간 + 투입 인원
          <>
            <div className="ls-r1">
              <strong>{p.projectName}</strong>
              <Badge kind={statusKind(p.statusCode)}>{p.statusName}</Badge>
            </div>
            <div className="ls-r2">
              <span>
                {p.customerName} · {typeLabel(p)}
              </span>
              <b>{internal(p) ? "–" : kor(p.contractAmount)}</b>
            </div>
            <div className="ls-r2">
              <span>
                {p.startDate} ~ {p.endDate}
              </span>
              <span>{p.assignedPeopleCount}명</span>
            </div>
          </>
        )}
        onDownload={() => void download()}
        downloading={downloading}
        error={error}
        onRetry={refresh}
      />
      {detail && (
        <ProjectDetail
          key={detail}
          dirtyScreenId="UI-01"
          projectId={detail}
          readOnly={readOnly}
          onClose={() => setDetail(null)}
          onEdit={(p) => {
            setDetail(null);
            setForm({ project: p });
          }}
          onChanged={(message, kind) => {
            refresh();
            if (message) toast(message, kind);
          }}
          onDistribution={openDistribution}
        />
      )}
      {toastNode}
    </section>
  );
}

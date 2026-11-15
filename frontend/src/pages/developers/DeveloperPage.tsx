import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Dialog, Toast, type ToastKind } from "../../shared/feedback/Dialog";
import {
  Badge,
  ListFilter,
  ListFilters,
  ListView,
  downloadFile,
  useListColumns,
  useListPresentation,
  type ListColumn,
  type ListSort,
} from "../../shared/list";
import { koreanThisMonth } from "../../shared/time";
import { useViewport } from "../../shared/layout/useViewport";
import { getCommonOperatingStandards } from "../operatingstandards/operatingStandards";
import { getCommonUploadTemplate, type UploadResult } from "../uploads/uploads";
import { UploadResultDialog } from "../uploads/UploadResultDialog";
import { DeveloperDetail } from "./DeveloperDetail";
import { DeveloperForm } from "./DeveloperForm";
import {
  developerFailure,
  developerRequest,
  DeveloperRequestError,
  developersExcelUrl,
  directLabel,
  getCommonDevelopers,
  getDeveloperOptions,
  putCommonDeveloperUseStatus,
  won,
  type Developer,
  type DeveloperFilter,
  type DeveloperListRow,
  type DeveloperOptions,
} from "./developers";
import "./developers.css";

const emptyFilter: DeveloperFilter = {
  employeeTypeCode: "",
  jobCode: "",
  workStatusName: "",
  directClassCode: "",
  q: "",
  includeStopped: false,
};
/** 시안 상태 배지: 투입중 파랑, 철수예정 주황, 대기 빨강, 휴직·퇴사 회색. */
const statusKind = (name: string) =>
  name === "투입중" ? "info" : name === "철수예정" ? "warn" : name === "대기" ? "bad" : "lock";
const icon = (d: string) => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={d} />
  </svg>
);
const downIcon = icon("M12 3v12M7 10l5 5 5-5M5 21h14");
const upIcon = icon("M12 21V9M7 14l5-5 5 5M5 3h14");
const plusIcon = icon("M12 5v14M5 12h14");

export function DeveloperPage({
  createSignal = 0,
  renderActions,
}: {
  createSignal?: number;
  renderActions?: (actions: ReactNode) => ReactNode;
}) {
  const [rows, setRows] = useState<DeveloperListRow[] | null>(null);
  const [options, setOptions] = useState<DeveloperOptions | null>(null);
  const [filter, setFilter] = useState(emptyFilter);
  const [applied, setApplied] = useState(emptyFilter);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const [detail, setDetail] = useState<string | null>(null);
  const [form, setForm] = useState<{ developer?: Developer } | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  // 시안: 처음에는 이름순(정렬 표시 없음), 머리를 누르면 그 열로 정렬
  const [sort, setSort] = useState<ListSort | null>(null);
  const [pendingUse, setPendingUse] = useState<{ ids: string[]; useYn: string } | null>(null);
  const [blocked, setBlocked] = useState<{ developerId: string; name: string; reason: string }[] | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [notice, setNotice] = useState("");
  const [noticeKind, setNoticeKind] = useState<ToastKind>("ok");
  const [downloading, setDownloading] = useState(false);
  const presentation = useListPresentation();
  const refresh = () => setRevision((v) => v + 1);
  useEffect(() => {
    if (createSignal) setForm({});
  }, [createSignal]);
  useEffect(() => {
    const controller = new AbortController();
    setError("");
    setRows(null);
    Promise.all([getCommonDevelopers(applied, controller.signal), getDeveloperOptions()])
      .then(([r, o]) => {
        if (!controller.signal.aborted) {
          setRows(r.items);
          setOptions(o);
          setSelected([]);
        }
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(developerFailure(reason));
      });
    return () => controller.abort();
  }, [applied, revision]);
  const columns = useMemo<ListColumn<DeveloperListRow>[]>(
    () => [
      {
        key: "name",
        label: "이름",
        required: true,
        render: (r) => (
          <span className="developer-pname">
            {r.name}
            <small>{r.managementNumber}</small>
          </span>
        ),
      },
      { key: "organizationName", label: "소속" },
      {
        key: "employeeTypeName",
        label: "직원구분",
        render: (r) => <Badge kind="neutral">{r.employeeTypeName}</Badge>,
      },
      { key: "gradeName", label: "등급", render: (r) => r.gradeName || "–" },
      { key: "primaryJobName", label: "주 직무" },
      {
        key: "workStatusName",
        label: "상태",
        render: (r) => (
          <>
            <Badge kind={statusKind(r.workStatusName)}>{r.workStatusName}</Badge>
            {r.retirementScheduled && <Badge kind="warn">퇴사 예정</Badge>}
          </>
        ),
      },
      {
        key: "currentProjectName",
        label: "현재 프로젝트",
        render: (r) =>
          r.currentProjectName ? (
            <span className="developer-pname">
              <span>
                <span className="developer-project">{r.currentProjectName}</span>
                {r.additionalProjectCount > 0 && (
                  <span className="developer-sub-inline"> 외 {r.additionalProjectCount}건</span>
                )}
              </span>
              <small>
                이번 달 {r.currentMonthMm.toFixed(2)} MM{r.overOneMm ? " · 1.0 초과" : ""}
              </small>
            </span>
          ) : (
            "–"
          ),
      },
      { key: "withdrawalDate", label: "철수 예정일", render: (r) => r.withdrawalDate || "" },
      { key: "monthlyRate", label: "월 단가", align: "right", render: (r) => won(r.monthlyRate) },
    ],
    [],
  );
  const cols = useListColumns("UI-02", columns);
  const sorted = useMemo(() => {
    if (!rows) return null;
    const value = (r: DeveloperListRow): string | number => {
      const v = (r as unknown as Record<string, unknown>)[sort?.key ?? "name"];
      return typeof v === "number" ? v : String(v ?? "");
    };
    const list = [...rows].sort((a, b) => {
      const x = value(a), y = value(b);
      const order = typeof x === "number" && typeof y === "number" ? x - y : String(x).localeCompare(String(y), "ko");
      return sort?.dir === "desc" ? -order : order;
    });
    return list;
  }, [rows, sort]);
  // 시안: 고른 것 중 바꿀 대상이 없으면 창 대신 알림(「고른 것 중 사용 중지된 개발자가 없습니다.」)
  const askUse = (useYn: string) => {
    const ids = (rows || [])
      .filter((r) => selected.includes(r.developerId) && r.useYn !== useYn)
      .map((r) => r.developerId);
    if (!ids.length) {
      setNotice(useYn === "Y" ? "고른 것 중 사용 중지된 개발자가 없습니다." : "고른 것 중 사용 중인 개발자가 없습니다.");
      return;
    }
    setPendingUse({ ids, useYn });
  };
  const use = async () => {
    if (pending.current || !pendingUse) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const value = await putCommonDeveloperUseStatus(pendingUse.ids, pendingUse.useYn);
      setPendingUse(null);
      if (value.rejected.length) setBlocked(value.rejected);
      if (value.processedDeveloperIds.length)
        setNotice(
          `${value.processedDeveloperIds.length}명을 ${pendingUse.useYn === "N" ? "사용 중지" : "다시 사용"}했습니다.`,
        );
      setDetail(null);
      refresh();
    } catch (reason) {
      setError(developerFailure(reason));
      setPendingUse(null);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const download = async () => {
    setDownloading(true);
    try {
      await downloadFile(developersExcelUrl(applied), "developers.xlsx");
      setNotice(`엑셀 파일을 만들었습니다 (조회 결과 ${rows?.length ?? 0}건).`);
    } catch (e) {
      setError(developerFailure(e));
    } finally {
      setDownloading(false);
    }
  };
  const actions = (
    <div className="developer-actions">
      {/* 시안: 사용 중지·퇴사한 개발자는 기본으로 숨긴다(상태 조건에서 퇴사를 고르면 함께 보임) */}
      <label
        className="developer-stopped"
        title="사용 중지·퇴사한 개발자는 기본으로 숨깁니다(상태 조건에서 퇴사를 고르면 함께 보임)"
      >
        <input
          type="checkbox"
          data-testid="developer-include-stopped"
          checked={applied.includeStopped}
          onChange={(e) => {
            const includeStopped = e.target.checked;
            setFilter((f) => ({ ...f, includeStopped }));
            setApplied((f) => ({ ...f, includeStopped }));
            setPage(1);
          }}
        />{" "}
        사용 중지 포함
      </label>
      <DeveloperUploads disabled={busy || !options} onApplied={refresh} />
      <button
        type="button"
        className="primary-button developer-title-button"
        data-testid="developer-create"
        disabled={!options || !!error}
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
  const titleActions = renderActions?.(form ? null : actions);
  if (form && options)
    return (
      <>
        {titleActions}
        <DeveloperForm
          developer={form.developer}
          options={options}
          onCancel={() => setForm(null)}
          onSaved={() => {
            setForm(null);
            setDetail(null);
            setNotice("저장했습니다.");
            refresh();
          }}
        />
      </>
    );
  const select = (
    key: "employeeTypeCode" | "jobCode" | "workStatusName" | "directClassCode",
    label: string,
    group: string,
  ) => (
    <ListFilter key={key} label={label} htmlFor={`developer-filter-${key}`}>
      <select
        className="ls-in"
        id={`developer-filter-${key}`}
        data-testid={`developer-filter-${key}`}
        value={filter[key]}
        onChange={(e) => setFilter((f) => ({ ...f, [key]: e.target.value }))}
      >
        <option value="">전체</option>
        {options?.codes[group]
          ?.filter((c) => key !== "workStatusName" || c.code !== "WORK")
          .map((c) => (
            <option key={c.code} value={key === "workStatusName" ? c.codeName : c.code}>
              {key === "directClassCode" ? directLabel(c.codeName) : c.codeName}
            </option>
          ))}
      </select>
    </ListFilter>
  );
  const selectedRows = rows?.filter((r) => pendingUse?.ids.includes(r.developerId)) || [];
  return (
    <section className="developer-page" data-testid="developer-page" data-screen-id="UI-02">
      {renderActions ? titleActions : actions}
      <ListFilters
        testId="developer-filters"
        activeCount={
          [applied.directClassCode, applied.employeeTypeCode, applied.jobCode, applied.workStatusName, applied.q].filter(Boolean).length
        }
        onSearch={() => {
          setApplied({ ...filter });
          setPage(1);
        }}
        onReset={() => {
          setFilter({ ...emptyFilter, includeStopped: applied.includeStopped });
          setApplied({ ...emptyFilter, includeStopped: applied.includeStopped });
          setPage(1);
        }}
      >
        {select("directClassCode", "직접/간접", "DRCT_CLS")}
        {select("employeeTypeCode", "직원구분", "EMP_TYPE")}
        {select("jobCode", "직무", "JOB")}
        {select("workStatusName", "상태", "WORK_STAT")}
        <ListFilter label="이름" htmlFor="developer-search">
          <input
            className="ls-in developer-search"
            id="developer-search"
            data-testid="developer-search"
            placeholder="이름 또는 관리번호"
            value={filter.q}
            onChange={(e) => setFilter((f) => ({ ...f, q: e.target.value }))}
          />
        </ListFilter>
      </ListFilters>
      <ListView
        screenId="UI-02"
        testId="developer-list"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        rows={
          error
            ? []
            : (sorted?.slice((page - 1) * presentation.pageSize, page * presentation.pageSize) ?? null)
        }
        rowKey={(r) => r.developerId}
        total={rows?.length || 0}
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
        selectable
        selected={selected}
        onSelect={setSelected}
        onSelectAllResults={() => setSelected(rows?.map((r) => r.developerId) || [])}
        allResultsSelected={!!rows?.length && selected.length === rows.length}
        bulkActions={[
          { key: "stop", label: "사용 중지", danger: true, onClick: () => askUse("N") },
          { key: "resume", label: "다시 사용", onClick: () => askUse("Y") },
        ]}
        onRowClick={(r) => setDetail(r.developerId)}
        mobileRows={sorted}
        renderCard={(r) => (
          // 시안 휴대폰 카드: 이름·관리번호·소속 + 상태 / 직무·등급·직원구분 + 월 단가 / 현재 프로젝트 + 이번 달 MM
          <>
            <div className="ls-r1">
              <span className="developer-pname">
                {r.name}
                <small>
                  {r.managementNumber} · {r.organizationName}
                </small>
              </span>
              <Badge kind={statusKind(r.workStatusName)}>{r.workStatusName}</Badge>
            </div>
            <div className="ls-r2">
              <span>{[r.primaryJobName, r.gradeName, r.employeeTypeName].filter(Boolean).join(" · ")}</span>
              <b>{won(r.monthlyRate)}</b>
            </div>
            <div className="ls-r2">
              <span>{r.currentProjectName || "–"}</span>
              <span>{r.currentProjectName ? `${r.currentMonthMm.toFixed(2)} MM` : ""}</span>
            </div>
          </>
        )}
        onDownload={() => void download()}
        downloading={downloading}
        error={error}
        onRetry={refresh}
      />
      {detail && (
        <DeveloperDetail
          key={detail}
          developerId={detail}
          onClose={() => setDetail(null)}
          onEdit={(d) => {
            setForm({ developer: d });
            setDetail(null);
          }}
          onStop={(d) => setPendingUse({ ids: [d.developerId], useYn: d.useYn === "Y" ? "N" : "Y" })}
          onNotice={(message) => {
            setNoticeKind("warn");
            setNotice(message);
          }}
          onChanged={(message) => {
            refresh();
            if (message) {
              // 시안: 휴직 기간에 배정이 있으면 info, 나머지는 ok
              setNoticeKind(message.includes("투입 배정이") ? "info" : "ok");
              setNotice(message);
            }
          }}
        />
      )}
      {pendingUse && (
        <Dialog
          title={
            pendingUse.useYn === "N"
              ? `${pendingUse.ids.length}명의 사용을 중지할까요?`
              : `${pendingUse.ids.length}명을 다시 사용할까요?`
          }
          closable
          onClose={() => !busy && setPendingUse(null)}
          testId="developer-use-confirm-dialog"
        >
          <p>
            {selectedRows.length === 1
              ? selectedRows[0].name
              : `${selectedRows[0]?.name ?? ""} 외 ${Math.max(selectedRows.length - 1, 0)}명`}{" "}
            —{" "}
            {pendingUse.useYn === "N"
              ? "삭제가 아니라 배정 후보에서 빼는 것이며 기록은 남습니다."
              : "다시 배정 후보에 넣습니다."}
          </p>
          <div className="app-actions">
            <button data-testid="developer-use-cancel" disabled={busy} onClick={() => setPendingUse(null)}>
              취소
            </button>
            <button
              className={pendingUse.useYn === "N" ? "app-danger" : "primary-button"}
              data-testid="developer-use-confirm"
              disabled={busy || pendingUse.ids.length === 0}
              onClick={() => void use()}
            >
              {pendingUse.useYn === "N" ? "사용 중지" : "다시 사용"}
            </button>
          </div>
        </Dialog>
      )}
      {blocked && (
        <Dialog title="사용을 중지할 수 없습니다" closable onClose={() => setBlocked(null)} testId="developer-use-result">
          <p>아래 개발자는 처리하지 못했습니다.</p>
          <ul className="developer-blocked-list">
            {blocked.map((r) => (
              <li key={r.developerId} data-testid={`developer-rejected-${r.developerId}`}>
                {r.name} — {r.reason}
              </li>
            ))}
          </ul>
          <div className="app-actions">
            <button className="primary-button" data-testid="developer-use-result-close" onClick={() => setBlocked(null)}>
              확인
            </button>
          </div>
        </Dialog>
      )}
      {notice && (
        <Toast
          message={notice}
          kind={noticeKind}
          onDismiss={() => {
            setNotice("");
            setNoticeKind("ok");
          }}
        />
      )}
    </section>
  );
}

/** 시안 순서: [급여 양식 내려받기] [급여 업로드] [양식 내려받기] [엑셀 업로드]. 휴대폰에서는 숨긴다. */
function DeveloperUploads({ disabled, onApplied }: { disabled: boolean; onApplied: () => void }) {
  const { mobile } = useViewport();
  const [kind, setKind] = useState<"B8" | "B1" | null>(null);
  const [result, setResult] = useState<{ value: UploadResult; kind: string } | null>(null);
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState(false);
  const template = async (code: string) => {
    setError("");
    setDownloading(true);
    try {
      await getCommonUploadTemplate(code);
    } catch (e) {
      setError(developerFailure(e));
    } finally {
      setDownloading(false);
    }
  };
  if (mobile) return null;
  return (
    <>
      <button type="button" className="upload-title-button" data-testid="developer-pay-template" disabled={disabled || downloading} onClick={() => void template("B1")}>
        {downIcon}급여 양식 내려받기
      </button>
      <button type="button" className="upload-title-button" data-testid="developer-pay-upload" disabled={disabled} onClick={() => setKind("B1")}>
        {upIcon}급여 업로드
      </button>
      <button type="button" className="upload-title-button" data-testid="developer-template" disabled={disabled || downloading} onClick={() => void template("B8")}>
        {downIcon}양식 내려받기
      </button>
      <button type="button" className="upload-title-button" data-testid="developer-upload" disabled={disabled} onClick={() => setKind("B8")}>
        {upIcon}엑셀 업로드
      </button>
      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
      {kind && (
        <DeveloperUploadDialog
          kind={kind}
          onClose={() => setKind(null)}
          onChecked={(value) => {
            setResult({ value, kind });
            setKind(null);
          }}
        />
      )}
      {result && (
        <UploadResultDialog
          targetName={result.kind === "B1" ? `급여 ${result.value.yearMonth ?? ""}`.trim() : "개발자"}
          notes={result.kind === "B8" ? DEVELOPER_NOTES : []}
          uploadId={result.value.uploadId}
          initialResult={result.value}
          onClose={() => setResult(null)}
          onApplied={onApplied}
        />
      )}
    </>
  );
}
const DEVELOPER_NOTES = [
  "관리번호를 비운 줄은 반영할 때 번호가 붙습니다(위 표는 붙을 번호). 반영한 뒤 개발자 풀 목록을 내려받으면 모든 번호를 확인할 수 있고, 그 번호로 투입·급여 양식을 채웁니다. 업로드는 새로 등록만 하며, 이미 있는 사람을 고치는 건 화면에서 합니다.",
  "거주지는 행정구역 목록과 맞춰 저장합니다(「서울특별시」·「서울시」는 「서울」, 행정동은 법정동으로). 못 찾은 칸은 비우고 경고로 알려 주며, 오류로 막지 않습니다. 동 칸에는 동 이름(예: 자양동)만 적어 주세요.",
];

/** 시안 급여 업로드 창: 연월(이번 달부터 10개월, 마감한 달은 「(마감)」으로 못 고름)·파일. 개발자 업로드는 파일만. */
function DeveloperUploadDialog({
  kind,
  onClose,
  onChecked,
}: {
  kind: "B8" | "B1";
  onClose: () => void;
  onChecked: (value: UploadResult) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [months, setMonths] = useState<{ value: string; closed: boolean }[]>([]);
  const [month, setMonth] = useState("");
  const [periodLoading, setPeriodLoading] = useState(kind === "B1");
  const [periodError, setPeriodError] = useState("");
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  useEffect(() => {
    if (kind !== "B1") return;
    const controller = new AbortController();
    setPeriodLoading(true);
    setPeriodError("");
    getCommonOperatingStandards(controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        const base = value.items.find((s) => s.standardCode === "CASH_BASE_YM")?.value.replace(/[^0-9]/g, "") || "";
        const closedUntil = (value.closedMonth || "").replace("-", "");
        const now = koreanThisMonth();
        let year = Number(now.slice(0, 4)),
          m = Number(now.slice(4, 6));
        const list: { value: string; closed: boolean }[] = [];
        for (let i = 0; i < 10; i++) {
          const ym = `${year}${String(m).padStart(2, "0")}`;
          list.push({ value: `${year}-${String(m).padStart(2, "0")}`, closed: (!!base && ym < base) || (!!closedUntil && ym <= closedUntil) });
          m -= 1;
          if (m === 0) {
            m = 12;
            year -= 1;
          }
        }
        setMonths(list);
        setMonth(list.find((x) => !x.closed)?.value || "");
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setPeriodError(developerFailure(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setPeriodLoading(false);
      });
    return () => controller.abort();
  }, [kind, revision]);
  const inspect = async () => {
    if (pending.current || periodLoading || periodError) return;
    setError(null);
    if (!file || !file.name.toLowerCase().endsWith(".xlsx") || file.size === 0 || file.size > 10_000_000) {
      setError(
        new DeveloperRequestError(400, "10MB 이하의 xlsx 파일을 선택해 주세요.", [
          { field: "file", message: "xlsx 파일을 확인해 주세요." },
        ]),
      );
      return;
    }
    if (kind === "B1" && !month) {
      setError(new DeveloperRequestError(400, "마감하지 않은 연월을 골라 주세요.", [{ field: "yearMonth", message: "연월을 골라 주세요." }]));
      return;
    }
    pending.current = true;
    setBusy(true);
    try {
      const body = new FormData();
      body.set("formTypeCode", kind);
      body.set("file", file);
      if (kind === "B1") body.set("yearMonth", month);
      onChecked(await developerRequest<UploadResult>("/api/common/uploads", { method: "POST", body }));
    } catch (reason) {
      setError(reason);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return (
    <Dialog
      title={kind === "B1" ? "급여 업로드" : "엑셀 업로드 · 개발자"}
      closable
      onClose={() => !pending.current && onClose()}
      testId="developer-upload-dialog"
    >
      <form
        noValidate
        className={`upload-form developer-upload-form${kind === "B1" ? " developer-pay-form" : ""}`}
        onSubmit={(e) => {
          e.preventDefault();
          void inspect();
        }}
      >
        {kind === "B1" && (
          <div className="developer-fc">
            <label htmlFor="developer-pay-month" className="developer-req">
              연월
            </label>
            <select
              id="developer-pay-month"
              data-testid="developer-pay-month"
              className="ls-in"
              value={month}
              disabled={busy || periodLoading || !!periodError}
              onChange={(e) => setMonth(e.target.value)}
            >
              {months.map((m) => (
                <option key={m.value} value={m.value} disabled={m.closed}>
                  {m.value}
                  {m.closed ? " (마감)" : ""}
                </option>
              ))}
            </select>
            <span className="developer-help">마감한 달은 올릴 수 없습니다.</span>
            {periodLoading && <p role="status">마감 정보 읽는 중…</p>}
            {periodError && (
              <p role="alert">
                {periodError}
                <button type="button" data-testid="developer-period-retry" onClick={() => setRevision((v) => v + 1)}>
                  다시 시도
                </button>
              </p>
            )}
          </div>
        )}
        <div className="developer-fc">
          <label htmlFor="developer-upload-file" className="developer-req">
            파일
          </label>
          <input
            id="developer-upload-file"
            data-testid="developer-upload-file"
            type="file"
            accept=".xlsx"
            disabled={busy}
            onChange={(e) => setFile(e.target.files?.[0] || null)}
          />
        </div>
        {kind === "B1" ? (
          <p className="developer-cap">
            재무 담당만 보이는 버튼입니다. 급여 엑셀에는 직원구분·직접/간접 칸이 없고, 올릴 때 그 달 개발자의 직원구분·직접/간접 값을 급여 줄에 복사해 둡니다. 직원구분이 바뀌면(예: 11월부터 프리랜서) 그 달 급여를 올리기 전에 개발자 풀에서 먼저 바꾸세요. 지난 달은 그대로입니다.
          </p>
        ) : (
          <p className="developer-cap">xlsx 파일만 올릴 수 있고, 10MB·첫 시트·5,000행까지 읽습니다. 올리면 먼저 검사 결과를 보여 줍니다.</p>
        )}
        {error != null && (
          <div role="alert" className="field-error">
            <p>{developerFailure(error)}</p>
          </div>
        )}
        <div className="app-actions">
          <button type="button" data-testid="developer-upload-close" disabled={busy} onClick={onClose}>
            취소
          </button>
          <button type="submit" className="primary-button" data-testid="developer-upload-check" disabled={busy || periodLoading || !!periodError}>
            {busy ? "검사 중…" : "올리기"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Dialog, Toast } from "../../shared/feedback/Dialog";
import {
  ListFilter,
  ListFilters,
  ListView,
  downloadFile,
  useListColumns,
  useListPresentation,
  type ListColumn,
} from "../../shared/list";
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
  getCommonDevelopers,
  getDeveloperOptions,
  putCommonDeveloperUseStatus,
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
  const [pendingUse, setPendingUse] = useState<string | null>(null);
  const [result, setResult] = useState<{
    processedDeveloperIds: string[];
    rejected: { developerId: string; name: string; reason: string }[];
  } | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [notice, setNotice] = useState("");
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
    Promise.all([
      getCommonDevelopers(applied, controller.signal),
      getDeveloperOptions(),
    ])
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
  const columns = useMemo<ListColumn<DeveloperListRow>[]>(() => {
    const definitions: ListColumn<DeveloperListRow>[] = [
      {
        key: "name",
        label: "이름 (관리번호·소속)",
        required: true,
        render: (r) => (
          <>
            <strong>{r.name}</strong>
            <small className="developer-sub">
              {r.managementNumber} · {r.organizationName}
            </small>
          </>
        ),
      },
      { key: "organizationName", label: "소속" },
      { key: "employeeTypeName", label: "직원구분" },
      { key: "gradeName", label: "등급" },
      { key: "primaryJobName", label: "주 직무" },
      {
        key: "workStatusName",
        label: "상태",
        render: (r) => (
          <>
            {r.workStatusName}
            {r.retirementScheduled && (
              <span className="developer-warning"> · 퇴사 예정</span>
            )}
            {r.useYn === "N" && <small className="developer-sub">중지</small>}
          </>
        ),
      },
      {
        key: "currentProjectName",
        label: "현재 프로젝트",
        calc: "식-01 달력일 비례 MM, 식-95 합계가 1.0을 넘으면 경고",
        render: (r) => (
          <>
            {r.currentProjectName || "–"}
            {r.additionalProjectCount > 0 &&
              ` 외 ${r.additionalProjectCount}건`}
            <small className="developer-sub">
              이번 달 {r.currentMonthMm} MM{" "}
              {r.overOneMm && (
                <span className="developer-warning">1.0 초과</span>
              )}
            </small>
          </>
        ),
      },
      { key: "withdrawalDate", label: "철수 예정일" },
      {
        key: "monthlyRate",
        label: "월 단가",
        align: "right",
        render: (r) =>
          r.monthlyRate == null ? "–" : r.monthlyRate.toLocaleString(),
      },
    ];
    return definitions.map((c) => ({ ...c, sortable: false }));
  }, []);
  const cols = useListColumns("UI-02", columns);
  const use = async () => {
    if (pending.current || !pendingUse) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const value = await putCommonDeveloperUseStatus(selected, pendingUse);
      setResult(value);
      setPendingUse(null);
      refresh();
    } catch (reason) {
      setError(developerFailure(reason));
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const download = async () => {
    setDownloading(true);
    try {
      await downloadFile(developersExcelUrl(applied), "developers.xlsx");
    } catch (e) {
      setError(developerFailure(e));
    } finally {
      setDownloading(false);
    }
  };
  const actions = (
    <div className="developer-actions">
      <DeveloperUploads disabled={busy || !options} onApplied={refresh} />
      <button
        className="primary-button"
        data-testid="developer-create"
        disabled={!options || !!error}
        onClick={() => {
          setDetail(null);
          setForm({});
        }}
      >
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
          onSaved={(saved) => {
            const editing = !!form.developer;
            setForm(null);
            setDetail(editing ? saved.developerId : null);
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
            <option
              key={c.code}
              value={key === "workStatusName" ? c.codeName : c.code}
            >
              {c.codeName}
            </option>
          ))}
      </select>
    </ListFilter>
  );
  return (
    <section
      className="developer-page"
      data-testid="developer-page"
      data-screen-id="UI-02"
    >
      {renderActions ? titleActions : actions}
      <ListFilters
        testId="developer-filters"
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
        {select("employeeTypeCode", "직원구분", "EMP_TYPE")}
        {select("jobCode", "직무", "JOB")}
        {select("workStatusName", "상태", "WORK_STAT")}
        {select("directClassCode", "직접/간접", "DRCT_CLS")}
        <ListFilter label="이름 또는 관리번호" htmlFor="developer-search">
          <input
            className="ls-in"
            id="developer-search"
            data-testid="developer-search"
            value={filter.q}
            onChange={(e) => setFilter((f) => ({ ...f, q: e.target.value }))}
          />
        </ListFilter>
        <label>
          <input
            type="checkbox"
            data-testid="developer-include-stopped"
            checked={filter.includeStopped}
            onChange={(e) =>
              setFilter((f) => ({ ...f, includeStopped: e.target.checked }))
            }
          />
          사용 중지 포함
        </label>
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
            : (rows?.slice(
                (page - 1) * presentation.pageSize,
                page * presentation.pageSize,
              ) ?? null)
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
        sort={null}
        onSort={() => undefined}
        selectable
        selected={selected}
        onSelect={setSelected}
        onSelectAllResults={() =>
          setSelected(rows?.map((r) => r.developerId) || [])
        }
        allResultsSelected={!!rows?.length && selected.length === rows.length}
        bulkActions={[
          {
            key: "stop",
            label: "사용 중지",
            danger: true,
            onClick: () => setPendingUse("N"),
          },
          {
            key: "resume",
            label: "다시 사용",
            onClick: () => setPendingUse("Y"),
          },
        ]}
        onRowClick={(r) => setDetail(r.developerId)}
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
          onChanged={refresh}
        />
      )}
      {pendingUse && (
        <Dialog
          title={pendingUse === "N" ? "사용 중지" : "다시 사용"}
          onClose={() => !busy && setPendingUse(null)}
          testId="developer-use-confirm-dialog"
        >
          <p>
            선택한 개발자 {selected.length}명을{" "}
            {pendingUse === "N" ? "사용 중지" : "다시 사용"}할까요? 가능한
            대상만 처리합니다.
          </p>
          <ul>
            {rows
              ?.filter((r) => selected.includes(r.developerId))
              .map((r) => (
                <li key={r.developerId}>
                  {r.name} · {r.managementNumber}
                </li>
              ))}
          </ul>
          {error && <p role="alert">{error}</p>}
          <div className="app-actions">
            <button
              data-testid="developer-use-cancel"
              disabled={busy}
              onClick={() => setPendingUse(null)}
            >
              취소
            </button>
            <button
              data-testid="developer-use-confirm"
              disabled={busy || selected.length === 0}
              onClick={() => void use()}
            >
              확인
            </button>
          </div>
        </Dialog>
      )}
      {result && (
        <Dialog
          title={
            result.processedDeveloperIds.length === 0 &&
            result.rejected.length > 0
              ? "사용을 중지할 수 없습니다"
              : "처리 결과"
          }
          onClose={() => setResult(null)}
          testId="developer-use-result"
        >
          <p>
            처리 {result.processedDeveloperIds.length}명 · 처리하지 못한 대상{" "}
            {result.rejected.length}명
          </p>
          <ul>
            {result.rejected.map((r) => (
              <li
                key={r.developerId}
                data-testid={`developer-rejected-${r.developerId}`}
              >
                {r.name} · {r.reason}
              </li>
            ))}
          </ul>
          <button
            data-testid="developer-use-result-close"
            onClick={() => setResult(null)}
          >
            확인
          </button>
        </Dialog>
      )}
      {notice && <Toast message={notice} onDismiss={() => setNotice("")} />}
    </section>
  );
}

function DeveloperUploads({
  disabled,
  onApplied,
}: {
  disabled: boolean;
  onApplied: () => void;
}) {
  const { mobile } = useViewport();
  const [kind, setKind] = useState<"B8" | "B1" | null>(null);
  const [result, setResult] = useState<{
    value: UploadResult;
    kind: string;
  } | null>(null);
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
    <div
      className="developer-upload-actions"
      data-testid="developer-upload-actions"
    >
      <button
        data-testid="developer-template"
        disabled={disabled || downloading}
        onClick={() => void template("B8")}
      >
        양식 내려받기
      </button>
      <button
        data-testid="developer-upload"
        disabled={disabled}
        onClick={() => setKind("B8")}
      >
        엑셀 업로드
      </button>
      <button
        data-testid="developer-pay-template"
        disabled={disabled || downloading}
        onClick={() => void template("B1")}
      >
        급여 양식 내려받기
      </button>
      <button
        data-testid="developer-pay-upload"
        disabled={disabled}
        onClick={() => setKind("B1")}
      >
        급여 업로드
      </button>
      {error && <p role="alert">{error}</p>}
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
          targetName={result.kind === "B1" ? "급여" : "개발자"}
          uploadId={result.value.uploadId}
          initialResult={result.value}
          onClose={() => setResult(null)}
          onApplied={onApplied}
        />
      )}
    </div>
  );
}
function DeveloperUploadDialog({
  kind,
  onClose,
  onChecked,
}: {
  kind: string;
  onClose: () => void;
  onChecked: (value: UploadResult) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [month, setMonth] = useState("");
  const [minimum, setMinimum] = useState("");
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
        const base = value.items
          .find((s) => s.standardCode === "CASH_BASE_YM")
          ?.value.replace(/[^0-9]/g, "");
        const baseMonth =
          base?.length === 6 ? `${base.slice(0, 4)}-${base.slice(4, 6)}` : "";
        let afterClose = "";
        if (value.closedMonth) {
          const [year, m] = value.closedMonth.split("-").map(Number);
          afterClose =
            m === 12
              ? `${year + 1}-01`
              : `${year}-${String(m + 1).padStart(2, "0")}`;
        }
        const min = [baseMonth, afterClose].sort().at(-1) || "";
        if (!min)
          throw new Error("운영 기준의 시작월·마감 정보를 확인해 주세요.");
        setMinimum(min);
      })
      .catch((reason) => {
        if (!controller.signal.aborted)
          setPeriodError(developerFailure(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setPeriodLoading(false);
      });
    return () => controller.abort();
  }, [kind, revision]);
  const inspect = async () => {
    if (pending.current || periodLoading || periodError) return;
    setError(null);
    if (
      !file ||
      !file.name.toLowerCase().endsWith(".xlsx") ||
      file.size === 0 ||
      file.size > 10_000_000
    ) {
      setError(
        new DeveloperRequestError(
          400,
          "10,000,000 bytes 이하의 xlsx 파일을 선택해 주세요.",
          [{ field: "file", message: "xlsx 파일을 확인해 주세요." }],
        ),
      );
      return;
    }
    if (
      kind === "B1" &&
      (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month) || month < minimum)
    ) {
      setError(
        new DeveloperRequestError(400, "마감하지 않은 연월을 선택해 주세요.", [
          { field: "yearMonth", message: "연월을 확인해 주세요." },
        ]),
      );
      return;
    }
    pending.current = true;
    setBusy(true);
    try {
      const body = new FormData();
      body.set("formTypeCode", kind);
      body.set("file", file);
      if (kind === "B1") body.set("yearMonth", month);
      onChecked(
        await developerRequest<UploadResult>("/api/common/uploads", {
          method: "POST",
          body,
        }),
      );
    } catch (reason) {
      setError(reason);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  return (
    <Dialog
      title={kind === "B1" ? "급여 업로드" : "개발자 엑셀 업로드"}
      onClose={() => !pending.current && onClose()}
      testId="developer-upload-dialog"
    >
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void inspect();
        }}
      >
        {kind === "B1" && (
          <>
            <p>그 달의 전체 급여를 새 파일로 교체합니다.</p>
            <label htmlFor="developer-pay-month">연월 *</label>
            <input
              id="developer-pay-month"
              data-testid="developer-pay-month"
              type="month"
              min={minimum}
              value={month}
              disabled={busy || periodLoading || !!periodError}
              onChange={(e) => setMonth(e.target.value)}
            />
            {periodLoading && <p role="status">마감 정보 읽는 중…</p>}
            {periodError && (
              <p role="alert">
                {periodError}
                <button
                  type="button"
                  data-testid="developer-period-retry"
                  onClick={() => setRevision((v) => v + 1)}
                >
                  재시도
                </button>
              </p>
            )}
          </>
        )}
        <label htmlFor="developer-upload-file">
          {kind === "B1" ? "B.1" : "B.8"} 파일 *
        </label>
        <input
          id="developer-upload-file"
          data-testid="developer-upload-file"
          type="file"
          accept=".xlsx"
          disabled={busy}
          onChange={(e) => setFile(e.target.files?.[0] || null)}
        />
        {error != null && (
          <div role="alert">
            <p>{developerFailure(error)}</p>
            {error instanceof DeveloperRequestError &&
              error.errors.map((e) => (
                <p key={e.field}>
                  {e.field}: {e.message}
                </p>
              ))}
          </div>
        )}
        <div className="app-actions">
          <button
            type="button"
            data-testid="developer-upload-close"
            disabled={busy}
            onClick={onClose}
          >
            취소
          </button>
          <button
            type="submit"
            data-testid="developer-upload-check"
            disabled={busy || periodLoading || !!periodError}
          >
            {busy ? "검사 중…" : "올리기"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

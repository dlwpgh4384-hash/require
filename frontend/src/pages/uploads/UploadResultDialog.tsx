import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { useListPresentation } from "../../shared/list/useListPresentation";
import { getCommonCodes } from "../codegroups/codeGroups";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import {
  getCommonUpload,
  getCommonUploadErrors,
  postCommonUploadApply,
  uploadFailure,
  UploadRequestError,
  type UploadResult,
} from "./uploads";
import "./upload.css";

/** UI-15-M4: 닫기는 조회만 끝내며 반영 요청을 하지 않는다. */
export function UploadResultDialog({
  uploadId,
  initialResult,
  onClose,
  onApplied,
}: {
  uploadId: string;
  initialResult?: UploadResult;
  onClose: () => void;
  onApplied: () => void;
}) {
  const [result, setResult] = useState<UploadResult | null>(
    initialResult || null,
  );
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const [revision, setRevision] = useState(0);
  const [appliedName, setAppliedName] = useState<string | null>(null);
  const [applied, setApplied] = useState(false);
  const notified = useRef(false);
  const scope = useRef<HTMLElement>(null);
  const presentation = useListPresentation();
  const [page, setPage] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setFields([]);
    Promise.all([
      getCommonUpload(uploadId, controller.signal),
      getCommonCodes("UPLD_STAT", controller.signal),
    ])
      .then(([value, codes]) => {
        if (controller.signal.aborted) return;
        setResult(value);
        setAppliedName(
          codes.items.find((code) => code.code === "APPLIED")?.codeName || null,
        );
        setPage(0);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(uploadFailure(reason));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [uploadId, revision]);
  const alreadyApplied =
    applied || (!!appliedName && result?.statusName === appliedName);
  const total = result
    ? result.successCount + result.warningCount + result.errorCount
    : 0;
  const canApply =
    !!result &&
    !loading &&
    !busy &&
    !error &&
    !alreadyApplied &&
    result.errorCount === 0 &&
    total > 0;
  const apply = async () => {
    if (!canApply || !result || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    setFields([]);
    try {
      const next = await postCommonUploadApply(uploadId, result.modifiedAt);
      setResult(next);
      setPage(0);
      if (next.errorCount === 0) {
        setApplied(true);
        if (!notified.current) {
          notified.current = true;
          onApplied();
        }
      }
    } catch (reason) {
      setError(uploadFailure(reason));
      if (reason instanceof UploadRequestError) setFields(reason.errors);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  usePreferenceBusinessShortcuts(
    {
      onSave: () => {
        void apply();
      },
      dialogTarget: scope,
    },
    canApply,
  );
  const download = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      await getCommonUploadErrors(uploadId);
    } catch (reason) {
      setError(uploadFailure(reason));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  const shown =
    result?.issues.slice(
      page * presentation.pageSize,
      (page + 1) * presentation.pageSize,
    ) || [];
  return (
    <Dialog
      title="엑셀 검사 결과"
      testId="upload-result-dialog"
      closable
      onClose={() => !busyRef.current && onClose()}
    >
      <section
        ref={scope}
        className={`upload-result ${presentation.dense ? "upload-dense" : ""}`}
        data-testid="upload-result"
        data-screen-id="UI-15-M4"
      >
        {loading && <p role="status">검사 결과 조회 중…</p>}
        {busy && <p role="status">처리 중…</p>}
        {result && (
          <>
            <p data-testid="upload-status">{result.statusName}</p>
            <div className="upload-counts" data-testid="upload-counts">
              <span>성공 {result.successCount}건</span>
              <span className="upload-warning">
                경고 {result.warningCount}건
              </span>
              <span className="upload-invalid">오류 {result.errorCount}건</span>
            </div>
            {alreadyApplied && (
              <p role="status" data-testid="upload-applied">
                반영했습니다.
              </p>
            )}
            {!total && <p>검사할 데이터 행이 없습니다.</p>}
            {!!total && !result.issues.length && (
              <p>행 오류와 경고가 없습니다.</p>
            )}
            {!!result.issues.length && (
              <div className="upload-table-scroll">
                <table>
                  <thead>
                    <tr>
                      {["행", "열", "입력값", "이유", "구분"].map((label) => (
                        <th key={label}>{label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {shown.map((issue, index) => (
                      <tr
                        key={`${issue.row}-${issue.column}-${page}-${index}`}
                        data-testid={`upload-issue-${page}-${index}`}
                      >
                        <td>{issue.row}</td>
                        <td>{issue.column}</td>
                        <td>{issue.inputValue}</td>
                        <td>{issue.reason}</td>
                        <td
                          className={
                            issue.severity === "오류"
                              ? "upload-invalid"
                              : "upload-warning"
                          }
                        >
                          {issue.severity}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {result.issues.length > presentation.pageSize && (
              <nav aria-label="업로드 결과 페이지">
                <button
                  data-testid="upload-previous"
                  disabled={!page}
                  onClick={() => setPage((value) => value - 1)}
                >
                  이전
                </button>
                <span>{page + 1}</span>
                <button
                  data-testid="upload-next"
                  disabled={
                    (page + 1) * presentation.pageSize >= result.issues.length
                  }
                  onClick={() => setPage((value) => value + 1)}
                >
                  다음
                </button>
              </nav>
            )}
          </>
        )}
        {error && (
          <div
            role="alert"
            data-testid="upload-result-error"
            className="field-error"
          >
            {error}
            <button
              data-testid="upload-result-retry"
              disabled={busy}
              onClick={() => setRevision((value) => value + 1)}
            >
              다시 불러오기
            </button>
          </div>
        )}
        {fields.map((field, index) => (
          <p className="field-error" key={`${field.field}-${index}`}>
            {field.field}: {field.message}
          </p>
        ))}
        <footer className="app-actions">
          <button
            data-testid="upload-errors-download"
            disabled={!result || loading || busy || !result.errorCount}
            onClick={() => void download()}
          >
            오류 목록 내려받기
          </button>
          <button
            data-testid="upload-apply"
            className="primary-button"
            disabled={!canApply}
            onClick={() => void apply()}
          >
            반영
          </button>
          <button
            data-testid="upload-result-close"
            disabled={busy}
            onClick={onClose}
          >
            닫기
          </button>
        </footer>
      </section>
    </Dialog>
  );
}

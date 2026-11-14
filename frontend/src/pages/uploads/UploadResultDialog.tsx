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
  targetName = "거래처",
}: {
  uploadId: string;
  /** 시안 제목 「엑셀 업로드 결과 · 거래처」 */
  targetName?: string;
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
      title={`엑셀 업로드 결과 · ${targetName}`}
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
        <div className="upload-mb">
          {loading && <p role="status">검사 결과 조회 중…</p>}
          {busy && <p role="status">처리 중…</p>}
          {result && (
            <>
              <span className="ls-sr" data-testid="upload-status">{result.statusName}</span>
              {/* 시안 .kpis: 성공·경고(반영은 됨)·오류(고쳐야 반영됨) */}
              <div className="upload-kpis" data-testid="upload-counts">
                <div className="upload-kpi">
                  <span className="upload-kpi-l">성공</span>
                  <span className="upload-kpi-v upload-ok">{result.successCount}건</span>
                  <span className="upload-kpi-d" />
                </div>
                <div className="upload-kpi">
                  <span className="upload-kpi-l">경고</span>
                  <span className="upload-kpi-v upload-warning">{result.warningCount}건</span>
                  <span className="upload-kpi-d">반영은 됨</span>
                </div>
                <div className="upload-kpi">
                  <span className="upload-kpi-l">오류</span>
                  <span className="upload-kpi-v upload-invalid">{result.errorCount}건</span>
                  <span className="upload-kpi-d">고쳐야 반영됨</span>
                </div>
              </div>
              {alreadyApplied && (
                <p role="status" data-testid="upload-applied">
                  반영했습니다.
                </p>
              )}
              {result.errorCount > 0 && (
                <p>오류가 하나라도 있으면 파일 전체를 반영하지 않습니다. 아래 오류를 고쳐 다시 올려 주세요.</p>
              )}
              {!total && <p>검사할 데이터 행이 없습니다.</p>}
              {!!total && !result.issues.length && <p>행 오류와 경고가 없습니다.</p>}
              <p className="upload-cap">
                xlsx 파일만 올릴 수 있고, 10MB·첫 시트·5,000행까지 읽습니다. 넘으면 파일을 나눠 올려 주세요.
              </p>
              {!!result.issues.length && (
                <div className="ls-twrap upload-table-scroll">
                  <table className="ls-t">
                    <thead>
                      <tr>
                        <th>구분</th>
                        <th className="ls-right">행</th>
                        <th>열</th>
                        <th>입력값</th>
                        <th>이유</th>
                      </tr>
                    </thead>
                    <tbody>
                      {shown.map((issue, index) => (
                        <tr
                          key={`${issue.row}-${issue.column}-${page}-${index}`}
                          data-testid={`upload-issue-${page}-${index}`}
                        >
                          <td>
                            <span className={`ls-b ${issue.severity === "오류" ? "ls-b-bad" : "ls-b-warn"}`}>
                              {issue.severity}
                            </span>
                          </td>
                          <td className="ls-right">{issue.row}</td>
                          <td>{issue.column}</td>
                          <td>{issue.inputValue === "" ? "(빈칸)" : issue.inputValue}</td>
                          <td>{issue.reason}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {result.issues.length > presentation.pageSize && (
                <nav aria-label="업로드 결과 페이지" className="upload-pages">
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm"
                    data-testid="upload-previous"
                    disabled={!page}
                    onClick={() => setPage((value) => value - 1)}
                  >
                    이전
                  </button>
                  <span>{page + 1}</span>
                  <button
                    type="button"
                    className="ls-btn ls-btn-sm"
                    data-testid="upload-next"
                    disabled={(page + 1) * presentation.pageSize >= result.issues.length}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    다음
                  </button>
                </nav>
              )}
            </>
          )}
          {error && (
            <div role="alert" data-testid="upload-result-error" className="field-error">
              {error}
              <button
                type="button"
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
        </div>
        {/* 시안 .mf: 왼쪽 [오류 목록 내려받기], 오른쪽 [닫기][반영] */}
        <footer className="upload-mf">
          <button
            type="button"
            className="ls-btn"
            data-testid="upload-errors-download"
            disabled={!result || loading || busy || !result.errorCount}
            onClick={() => void download()}
          >
            오류 목록 내려받기
          </button>
          <div className="upload-mf-r">
            <button
              type="button"
              className="ls-btn"
              data-testid="upload-result-close"
              disabled={busy}
              onClick={onClose}
            >
              닫기
            </button>
            <button
              type="button"
              className="ls-btn ls-btn-p"
              data-testid="upload-apply"
              disabled={!canApply}
              onClick={() => void apply()}
            >
              반영
            </button>
          </div>
        </footer>
      </section>
    </Dialog>
  );
}

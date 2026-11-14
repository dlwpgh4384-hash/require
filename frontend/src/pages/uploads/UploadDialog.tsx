import { useRef, useState } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import {
  postCommonUpload,
  uploadFailure,
  UploadRequestError,
  type UploadResult,
} from "./uploads";
import "./upload.css";

/** UI-15-M3: 검사는 거래처를 등록하지 않는다. 다른 화면도 양식 코드만 전달해 재사용한다. */
export function UploadDialog({
  formTypeCode,
  onClose,
  onChecked,
}: {
  formTypeCode: string;
  onClose: () => void;
  onChecked: (result: UploadResult) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const inspect = async () => {
    if (busyRef.current) return;
    setFields([]);
    setError("");
    if (!file) {
      setError("엑셀 파일을 선택해 주세요.");
      return;
    }
    if (!file.name.toLowerCase().endsWith(".xlsx")) {
      setError("xlsx 파일을 선택해 주세요.");
      return;
    }
    if (file.size === 0 || file.size > 10_000_000) {
      setError("10,000,000 bytes 이하의 파일을 선택해 주세요.");
      return;
    }
    busyRef.current = true;
    setBusy(true);
    try {
      onChecked(await postCommonUpload(formTypeCode, file));
    } catch (reason) {
      setError(uploadFailure(reason));
      if (reason instanceof UploadRequestError) setFields(reason.errors);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  return (
    <Dialog
      title="거래처 엑셀 업로드"
      testId="upload-dialog"
      closable
      onClose={() => !busyRef.current && onClose()}
    >
      <section
        className="upload-form"
        data-testid="upload-form"
        data-screen-id="UI-15-M3"
      >
        <p>
          B.7:
          구분·상호·사업자등록번호·대표자·업종·담당자·연락처·이메일·은행·계좌번호·예금주
        </p>
        <p className="upload-help">
          신규 이관 전용입니다. 검사만으로 거래처를 등록하지 않습니다. 첫 시트,
          최대 5,000개 데이터 행을 검사합니다.
        </p>
        <label htmlFor="upload-file">
          엑셀 파일<span className="field-required">*</span>
        </label>
        <input
          id="upload-file"
          data-testid="upload-file"
          type="file"
          accept=".xlsx"
          disabled={busy}
          aria-invalid={!!error}
          onChange={(event) => {
            setFile(event.target.files?.[0] || null);
            setError("");
            setFields([]);
          }}
        />
        {file && <p data-testid="upload-selected-file">{file.name}</p>}
        {busy && <p role="status">검사 중…</p>}
        {error && (
          <p role="alert" className="field-error" data-testid="upload-error">
            {error}
          </p>
        )}
        {fields.map((field, index) => (
          <p className="field-error" key={`${field.field}-${index}`}>
            {field.field}: {field.message}
          </p>
        ))}
        <footer className="app-actions">
          <button data-testid="upload-cancel" disabled={busy} onClick={onClose}>
            취소
          </button>
          <button
            data-testid="upload-check"
            className="primary-button"
            disabled={busy}
            onClick={() => void inspect()}
          >
            파일 검사
          </button>
        </footer>
      </section>
    </Dialog>
  );
}

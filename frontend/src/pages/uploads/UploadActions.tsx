import { useState } from "react";
import { useViewport } from "../../shared/layout/useViewport";
import { UploadDialog } from "./UploadDialog";
import { UploadResultDialog } from "./UploadResultDialog";
import {
  getCommonUploadTemplate,
  uploadFailure,
  type UploadResult,
} from "./uploads";
import "./upload.css";

export function UploadActions({
  onApplied,
  disabled = false,
  formTypeCode = "B7",
}: {
  onApplied: () => void;
  disabled?: boolean;
  formTypeCode?: string;
}) {
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState(false);
  // FR-0107: 휴대폰에서는 엑셀 업로드·양식 받기 단추를 숨긴다.
  const { mobile } = useViewport();
  const download = async () => {
    setDownloading(true);
    setError("");
    try {
      await getCommonUploadTemplate(formTypeCode);
    } catch (reason) {
      setError(uploadFailure(reason));
    } finally {
      setDownloading(false);
    }
  };
  if (mobile) return null;
  return (
    <div className="upload-actions" data-testid="upload-actions">
      <button
        type="button"
        className="upload-title-button"
        data-testid="upload-template"
        disabled={disabled || downloading}
        onClick={() => void download()}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 4v11M7 10l5 5 5-5M5 20h14" /></svg>
        양식 내려받기
      </button>
      <button
        type="button"
        className="upload-title-button"
        data-testid="upload-open"
        disabled={disabled}
        onClick={() => setOpen(true)}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 20V9M7 14l5-5 5 5M5 4h14" /></svg>
        엑셀 업로드
      </button>
      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
      {open && (
        <UploadDialog
          formTypeCode={formTypeCode}
          onClose={() => setOpen(false)}
          onChecked={(value) => {
            setOpen(false);
            setResult(value);
          }}
        />
      )}
      {result && (
        <UploadResultDialog
          uploadId={result.uploadId}
          initialResult={result}
          onClose={() => setResult(null)}
          onApplied={onApplied}
        />
      )}
    </div>
  );
}

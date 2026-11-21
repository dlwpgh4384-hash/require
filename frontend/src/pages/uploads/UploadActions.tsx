import { useState, type ComponentProps } from "react";
import { useViewport } from "../../shared/layout/useViewport";
import { UploadDialog } from "./UploadDialog";
import { UploadResultDialog } from "./UploadResultDialog";
import {
  getCommonUploadTemplate,
  uploadFailure,
  type UploadResult,
} from "./uploads";
import "./upload.css";

type DialogOptions = Pick<
  ComponentProps<typeof UploadDialog>,
  "title" | "month" | "intro" | "checkLabel" | "beforeCheck"
>;

export function UploadActions({
  onApplied,
  disabled = false,
  formTypeCode = "B7",
  targetName,
  notes,
  dialog,
  onOpen,
  templateLabel = "양식 내려받기",
  uploadLabel = "엑셀 업로드",
}: {
  onApplied: () => void;
  disabled?: boolean;
  formTypeCode?: string;
  /** 결과 창 제목 「엑셀 업로드 결과 · {대상}」. 비우면 거래처. */
  targetName?: string;
  /** 결과 표 아래 안내(시안 양식별 설명). */
  notes?: string[];
  /** 업로드 창 모양(제목·연월·안내·단추 글·검사 전 확인). 주지 않으면 기본. */
  dialog?: DialogOptions;
  /** [엑셀 업로드]를 누를 때 화면이 먼저 확인할 일(예: 저장 안 한 내용). open()을 부르면 창이 열린다. */
  onOpen?: (open: () => void) => void;
  templateLabel?: string;
  uploadLabel?: string;
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
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M12 4v11M7 10l5 5 5-5M5 20h14" />
        </svg>
        {templateLabel}
      </button>
      <button
        type="button"
        className="upload-title-button"
        data-testid="upload-open"
        disabled={disabled}
        onClick={() => (onOpen ? onOpen(() => setOpen(true)) : setOpen(true))}
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M12 20V9M7 14l5-5 5 5M5 4h14" />
        </svg>
        {uploadLabel}
      </button>
      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
      {open && (
        <UploadDialog
          formTypeCode={formTypeCode}
          {...dialog}
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
          targetName={targetName}
          notes={notes}
          onClose={() => setResult(null)}
          onApplied={onApplied}
        />
      )}
    </div>
  );
}

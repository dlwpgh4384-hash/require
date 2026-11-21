import { useRef, useState, type ReactNode } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import {
  postCommonUpload,
  uploadFailure,
  UploadRequestError,
  type UploadResult,
} from "./uploads";
import "./upload.css";

/** 검사 전에 띄울 확인 창(예: B.2 같은 달 다시 올리기). null이면 바로 검사한다. */
export interface UploadCheckConfirm {
  title: string;
  description: string;
  proceedLabel: string;
}
/** 한 달 양식(B.2 등)의 연월 고르기. 값은 YYYY-MM. */
export interface UploadMonthChoice {
  value: string;
  options: string[];
  help?: string;
}

/** UI-15-M3: 검사는 거래처를 등록하지 않는다. 다른 화면도 양식 코드만 전달해 재사용한다. */
export function UploadDialog({
  formTypeCode,
  onClose,
  onChecked,
  projectId,
  distributionSteps = [],
  defaultStep = "",
  title,
  month,
  intro,
  checkLabel = "파일 검사",
  beforeCheck,
}: {
  formTypeCode: string;
  onClose: () => void;
  onChecked: (result: UploadResult) => void;
  projectId?: string;
  distributionSteps?: { code: string; codeName: string }[];
  /** B.6 처음 고른 단계(시안: 지금 계산에 쓰는 단계). */
  defaultStep?: string;
  /** 창 제목. 주지 않으면 양식별 기본 제목. */
  title?: string;
  /** 한 달 양식: 연월 칸(고른 값을 yearMonth로 보낸다). */
  month?: UploadMonthChoice;
  /** 양식 안내 글. 주지 않으면 양식별 기본 안내. */
  intro?: ReactNode;
  /** 검사 단추 글(시안 화면마다 다름, 예: 「올리기」). */
  checkLabel?: string;
  /** 검사 전에 확인할 내용을 돌려준다. null이면 확인 없이 검사한다. */
  beforeCheck?: (yearMonth: string) => Promise<UploadCheckConfirm | null>;
}) {
  const [step, setStep] = useState(defaultStep);
  const [yearMonth, setYearMonth] = useState(month?.value ?? "");
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState("");
  const [confirm, setConfirm] = useState<UploadCheckConfirm | null>(null);
  const [fields, setFields] = useState<{ field: string; message: string }[]>(
    [],
  );
  const run = async () => {
    if (!file) return;
    busyRef.current = true;
    setBusy(true);
    try {
      if (formTypeCode === "B6" && (!projectId || !step)) {
        setError("배분표 단계를 선택해 주세요.");
        return;
      }
      onChecked(
        formTypeCode === "B6"
          ? await postCommonUpload(formTypeCode, file, {
              projectId: projectId!,
              distributionStepCode: step,
            })
          : month
            ? await postCommonUpload(formTypeCode, file, undefined, yearMonth)
            : await postCommonUpload(formTypeCode, file),
      );
    } catch (reason) {
      setError(uploadFailure(reason));
      if (reason instanceof UploadRequestError) setFields(reason.errors);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };
  const inspect = async () => {
    if (busyRef.current) return;
    setFields([]);
    setError("");
    if (month && !yearMonth) {
      setError("연월을 골라 주세요.");
      return;
    }
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
    if (beforeCheck) {
      busyRef.current = true;
      setBusy(true);
      try {
        const ask = await beforeCheck(yearMonth);
        if (ask) {
          setConfirm(ask);
          return;
        }
      } catch (reason) {
        setError(uploadFailure(reason));
        return;
      } finally {
        busyRef.current = false;
        setBusy(false);
      }
    }
    await run();
  };
  return (
    <Dialog
      title={
        title ??
        `${formTypeCode === "B6" ? "배분표" : formTypeCode === "B9" ? "프로젝트" : "거래처"} 엑셀 업로드`
      }
      testId="upload-dialog"
      closable
      onClose={() => !busyRef.current && onClose()}
    >
      <section
        className="upload-form"
        data-testid="upload-form"
        data-screen-id="UI-15-M3"
      >
        {formTypeCode === "B6" && (
          <label>
            단계
            <select
              data-testid="upload-distribution-step"
              value={step}
              disabled={busy}
              onChange={(e) => setStep(e.target.value)}
            >
              <option value="">선택</option>
              {distributionSteps.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.codeName}
                </option>
              ))}
            </select>
          </label>
        )}
        {month && (
          <label>
            연월<span className="field-required">*</span>
            <select
              data-testid="upload-year-month"
              value={yearMonth}
              disabled={busy}
              onChange={(e) => setYearMonth(e.target.value)}
            >
              {month.options.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
            {month.help && <span className="upload-help">{month.help}</span>}
          </label>
        )}
        {intro ? (
          intro
        ) : formTypeCode === "B6" ? (
          <p className="upload-help">
            표 전체에 단계 하나를 정합니다. 계산에는 가장 나중 단계의 표를 씁니다.
          </p>
        ) : (
          <>
            <p>
              {formTypeCode === "B9"
                ? "B.9 프로젝트 신규 등록 전용 · 빈 프로젝트ID는 자동 발급"
                : "B.7: 구분·상호·사업자등록번호·대표자·업종·담당자·연락처·이메일·은행·계좌번호·예금주"}
            </p>
            <p className="upload-help">
              신규 이관 전용입니다. 검사만으로 {formTypeCode === "B9" ? "프로젝트" : "거래처"}를 등록하지 않습니다. 첫 시트,
              최대 5,000개 데이터 행을 검사합니다.
            </p>
          </>
        )}
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
        {formTypeCode === "B6" && (
          <p className="upload-help">[양식 받기]로 받은 파일에 사람·항목 줄 × 월 금액을 채워 올립니다.</p>
        )}
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
            {checkLabel}
          </button>
        </footer>
      </section>
      {confirm && (
        <InlineConfirmation
          title={confirm.title}
          description={confirm.description}
          proceedLabel={confirm.proceedLabel}
          testId="upload-check-confirm"
          onCancel={() => setConfirm(null)}
          onProceed={() => {
            setConfirm(null);
            void run();
          }}
        />
      )}
    </Dialog>
  );
}

import { useEffect, useRef, useState } from "react";
import { DeveloperRequestError } from "../developers/developers";
import {
  candidateOverviewMessages as messages,
  mergeCandidateResume,
  postCommonCandidateResumeExtraction,
  type CandidateResumeExtraction,
  type CandidateResumeField,
} from "./candidateOverview";
import "./candidateOverview.css";

export function CandidateResumeImport<T extends object>({
  draft,
  onChange,
  mode = "create",
  readOnly = false,
}: {
  draft: T;
  onChange: (next: T, applied: CandidateResumeField[]) => void;
  mode?: "create" | "edit";
  readOnly?: boolean;
}) {
  const latest = useRef({ draft, onChange });
  latest.current = { draft, onChange };
  const request = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<{
    extraction: CandidateResumeExtraction;
    applied: CandidateResumeField[];
  } | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => {
    if (mode !== "create" || readOnly) {
      request.current?.abort();
      setBusy(false);
      setResult(null);
    }
  }, [mode, readOnly]);
  if (mode !== "create" || readOnly) return null;
  const cancel = () => {
    request.current?.abort();
    setBusy(false);
    setError("");
    if (input.current) input.current.value = "";
  };
  const extract = async (file: File) => {
    request.current?.abort();
    setError("");
    if (
      !/\.(docx?|pdf|pptx?|hwp|txt)$/i.test(file.name) ||
      file.size === 0 ||
      file.size > 10 * 1024 * 1024
    ) {
      setBusy(false);
      setError(
        "10MB 이하의 텍스트 이력서(Word·PDF·PPT·HWP·TXT)를 확인해 주세요.",
      );
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    setBusy(true);
    try {
      const extraction = await postCommonCandidateResumeExtraction(
        file,
        controller.signal,
      );
      if (controller.signal.aborted) return;
      const merged = mergeCandidateResume(latest.current.draft, extraction);
      if (merged.applied.length)
        latest.current.onChange(merged.draft, merged.applied);
      setResult({ extraction, applied: merged.applied });
    } catch (reason) {
      if (!controller.signal.aborted)
        setError(
          reason instanceof DeveloperRequestError
            ? reason.errors.find((field) => field.field === "file")?.message ||
                reason.message
            : messages.failed,
        );
    } finally {
      if (!controller.signal.aborted) {
        setBusy(false);
        if (input.current) input.current.value = "";
      }
    }
  };
  const estimated =
    result?.applied.filter((field) => field.estimated).length ?? 0;
  return (
    <section
      className="candidate-resume-import"
      aria-label="이력서 등록"
      data-testid="candidate-resume-import"
    >
      <label>
        이력서 등록
        <input
          ref={input}
          type="file"
          accept=".doc,.docx,.pdf,.ppt,.pptx,.hwp,.txt"
          disabled={busy}
          data-testid="candidate-resume-file"
          onChange={(event) => {
            setError("");
            const file = event.target.files?.[0];
            if (file) void extract(file);
          }}
        />
      </label>
      <p>{messages.notRetained}</p>
      {busy && (
        <div>
          <p role="status">{messages.loading}</p>
          <button
            type="button"
            className="ls-btn"
            data-testid="candidate-resume-cancel"
            onClick={cancel}
          >
            취소
          </button>
        </div>
      )}
      {error && (
        <p role="alert" data-testid="candidate-resume-error">
          {error}
        </p>
      )}
      {result && (
        <div data-testid="candidate-resume-result" aria-live="polite">
          <p>
            {result.extraction.fileName} · 채운 칸 {result.applied.length}개 ·
            확실 {result.applied.length - estimated}개 · 추정 {estimated}개
          </p>
          {!result.applied.length && <p>{messages.noFields}</p>}
          {result.extraction.careerYears != null && (
            <p>경력 연수: {result.extraction.careerYears}년</p>
          )}
          <ul>
            {result.applied.map((field) => (
              <li
                key={field.field}
                data-testid={`candidate-resume-review-${field.field}`}
              >
                {field.estimated ? messages.review : messages.certain}
                {field.estimated && <span> · {field.originalText}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

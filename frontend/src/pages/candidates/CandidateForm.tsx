import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useAreaPicker } from "../../shared/area/AreaPicker";
import { developerFailure } from "../developers/developers";
import {
  CandidateRequestError,
  RESUME_ACCEPT,
  candidateDefaults,
  candidateEmployeeTypes,
  candidateErrors,
  candidateMessages as messages,
  mergeCandidateExtraction,
  postCommonCandidate,
  postCommonCandidateResumeExtraction,
  putCommonCandidate,
  resumeFileError,
  resumeResultText,
  type Candidate,
  type CandidateInput,
  type CandidateOptions,
  type ResumeMark,
} from "./candidates";
import "./candidates.css";

const money = (value: number) =>
  Number.isNaN(value) ? "" : value.toLocaleString("ko-KR");

/** 외부 인력 후보 등록(UI-33-M1)·정보 수정(UI-33-M2) 창. 시안 candNew. */
export function CandidateForm({
  candidate,
  options,
  onCancel,
  onSaved,
  onConflict,
}: {
  candidate?: Candidate;
  options: CandidateOptions;
  onCancel: () => void;
  onSaved: (candidate: Candidate) => void;
  onConflict: () => void;
}) {
  const defaults = useRef(candidateDefaults(options));
  const [draft, setDraft] = useState<CandidateInput>(() =>
    candidate
      ? {
          ...candidate,
          // 서버가 희망 단가를 보내지 않은 경우(민감 칸 없음) 빈 칸으로 둔다(NaN = 빈 칸).
          desiredMonthlyRate: candidate.desiredMonthlyRate ?? Number.NaN,
        }
      : { ...defaults.current },
  );
  const [skillText, setSkillText] = useState(draft.skills.join(", "));
  const original = useRef(JSON.stringify(draft));
  const dirty = original.current !== JSON.stringify(draft);
  const setDirty = useScreenDirty();
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [leaving, setLeaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [extracting, setExtracting] = useState(false);
  const extraction = useRef<AbortController | null>(null);
  const [resume, setResume] = useState<{
    text: string;
    marks: Record<string, ResumeMark>;
    careerYears: number | null;
  } | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const currentDraft = useRef(draft);
  currentDraft.current = draft;
  const [memoLength, setMemoLength] = useState([...(draft.memo || "")].length);
  useEffect(() => {
    setDirty("UI-33", dirty);
    return () => setDirty("UI-33", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    nameRef.current?.focus();
    return () => extraction.current?.abort();
  }, []);
  // 거주지 시·도 → 시·군·구(공통 행정구역 고르기, 두 단계). 시·도 목록은 창을 열 때 한 번(그리고 [다시 시도] 때)만 읽는다.
  // 저장된 거주지 코드(수정·이력서)로 고르기를 다시 채운다.
  const initialAreaCode = useRef(draft.homeAreaCode);
  const area = useAreaPicker(initialAreaCode.current, 2);
  const [roots, children] = area.lists;
  const location = area.location;
  const areaBusy = area.busy;
  const areaError = area.error;
  function change<K extends keyof CandidateInput>(
    key: K,
    value: CandidateInput[K],
  ) {
    setDraft((old) => ({ ...old, [key]: value }));
    // 칸 아래 오류는 고치기 시작하면 지운다. 전화번호·이메일을 모두 지우면 동의 오류도 지운다.
    const other =
      key === "phone"
        ? currentDraft.current.email
        : key === "email"
          ? currentDraft.current.phone
          : "x";
    setErrors((current) => {
      const left = { ...current };
      delete left[key];
      if (!String(value ?? "").trim() && !other?.trim())
        delete left.privacyAgreement;
      return left;
    });
  }
  const close = () => {
    if (!pending.current && !extracting) dirty ? setLeaving(true) : onCancel();
  };
  function selectArea(level: number, code: string) {
    if (level === 1) {
      const selected = children.find((area) => area.areaCode === code);
      const parent = roots.find((area) => area.areaCode === location[0]);
      change("homeAreaCode", area.select(1, code));
      change(
        "homeAddress",
        selected
          ? `${parent?.areaName || ""} ${selected.areaName}`.trim()
          : parent?.areaName || null,
      );
      return;
    }
    change("homeAreaCode", area.select(0, code));
    change(
      "homeAddress",
      roots.find((area) => area.areaCode === code)?.areaName || null,
    );
  }
  const focusFirstError = () =>
    setTimeout(
      () =>
        formRef.current
          ?.querySelector<HTMLElement>("[aria-invalid='true']")
          ?.focus(),
      0,
    );
  async function save() {
    if (pending.current || extracting || areaBusy || !!areaError) return;
    const invalid = candidateErrors(draft);
    setErrors(invalid);
    setError("");
    if (Object.keys(invalid).length) {
      focusFirstError();
      return;
    }
    pending.current = true;
    setBusy(true);
    try {
      const saved = candidate
        ? await putCommonCandidate(
            candidate.candidateId,
            draft,
            candidate.modifiedAt,
          )
        : await postCommonCandidate(draft);
      setDirty("UI-33", false);
      onSaved(saved);
    } catch (reason) {
      if (reason instanceof CandidateRequestError && reason.status === 409) {
        setDirty("UI-33", false);
        onConflict();
        return;
      }
      setError(developerFailure(reason));
      if (reason instanceof CandidateRequestError && reason.errors.length) {
        setErrors(
          Object.fromEntries(
            reason.errors.map((item) => [item.field, item.message]),
          ),
        );
        focusFirstError();
      }
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  async function extract(file?: File) {
    if (!file || pending.current || extracting) return;
    setError("");
    // 그림 파일·10MB 넘는 파일은 올리지 않는다(시안 candResumeRead).
    const invalid = resumeFileError(file);
    if (invalid) {
      setError(invalid);
      return;
    }
    const controller = new AbortController();
    extraction.current = controller;
    setExtracting(true);
    try {
      const result = await postCommonCandidateResumeExtraction(
        file,
        controller.signal,
      );
      if (controller.signal.aborted) return;
      const merged = mergeCandidateExtraction(
        currentDraft.current,
        result,
        defaults.current,
      );
      setDraft(merged.input);
      setSkillText(merged.input.skills.join(", "));
      setMemoLength([...(merged.input.memo || "")].length);
      setErrors((old) => {
        const left = { ...old };
        for (const key of merged.applied) delete left[key];
        return left;
      });
      setResume({
        text: resumeResultText(result.fileName || file.name, merged),
        marks: merged.marks,
        careerYears:
          result.careerYears != null && Number(result.careerYears) > 0
            ? Math.floor(Number(result.careerYears))
            : null,
      });
      if (merged.applied.includes("homeAreaCode"))
        area.show(merged.input.homeAreaCode);
    } catch (reason) {
      if (controller.signal.aborted) return;
      setError(
        reason instanceof CandidateRequestError
          ? reason.errors.find((item) => item.field === "file")?.message ||
              reason.message
          : developerFailure(reason),
      );
    } finally {
      if (extraction.current === controller) {
        extraction.current = null;
        setExtracting(false);
      }
    }
  }
  const cancelExtraction = () => {
    extraction.current?.abort();
    extraction.current = null;
    setExtracting(false);
  };
  const markText = (mark: ResumeMark) =>
    `${mark.kind === "ok" ? messages.ok : messages.warn}${mark.raw ? ` · 원문: ${mark.raw.slice(0, 60)}` : ""}`;
  function field(
    key: string,
    label: string,
    control: ReactNode,
    {
      required = true,
      help,
      htmlFor = `candidate-${key}`,
      extra,
    }: {
      required?: boolean;
      help?: ReactNode;
      htmlFor?: string;
      extra?: ReactNode;
    } = {},
  ) {
    const mark = resume?.marks[key];
    return (
      <div className="candidate-field" key={key}>
        <label htmlFor={htmlFor}>
          {label}
          {required && <span className="field-required">*</span>}
        </label>
        {control}
        {help && <span className="candidate-help">{help}</span>}
        {mark && (
          <span
            className={`candidate-mark candidate-mark-${mark.kind}`}
            data-testid={`candidate-mark-${key}`}
          >
            {markText(mark)}
          </span>
        )}
        {extra}
        {errors[key] && (
          <small
            className="field-error"
            role="alert"
            data-testid={`candidate-error-${key}`}
          >
            {errors[key]}
          </small>
        )}
      </div>
    );
  }
  const testId = (key: string) =>
    `candidate-${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`;
  const attrs = (key: keyof CandidateInput) => ({
    id: `candidate-${key}`,
    className: "ls-in",
    "data-testid": testId(key),
    "aria-invalid": !!errors[key],
    disabled: busy || extracting,
  });
  function select(
    key:
      | "sourceCode"
      | "employeeTypeCode"
      | "primaryJobCode"
      | "secondaryJobCode"
      | "gradeCode",
    group: string,
    label: string,
    extra?: { required?: boolean; help?: ReactNode; extra?: ReactNode },
  ) {
    const items =
      key === "employeeTypeCode"
        ? candidateEmployeeTypes(options)
        : options.codes[group] || [];
    const optional = key === "secondaryJobCode";
    return field(
      key,
      label,
      <select
        {...attrs(key)}
        value={draft[key] || ""}
        onChange={(event) =>
          change(key, optional ? event.target.value || null : event.target.value)
        }
      >
        {optional ? (
          <option value="">선택 안 함</option>
        ) : (
          !draft[key] && <option value="" />
        )}
        {draft[key] && !items.some((item) => item.code === draft[key]) && (
          <option value={draft[key] || ""}>{draft[key]}</option>
        )}
        {items.map((item) => (
          <option key={item.code} value={item.code}>
            {item.codeName}
          </option>
        ))}
      </select>,
      { required: !optional, ...extra },
    );
  }
  const disabled = busy || extracting;
  const careerHint =
    resume?.careerYears != null ? (
      <span
        className="candidate-mark candidate-mark-warn"
        data-testid="candidate-career-hint"
      >
        {markText({
          kind: "warn",
          raw: `경력 약 ${resume.careerYears}년, 등급은 직접 고르세요`,
        })}
      </span>
    ) : null;
  return (
    <>
      <Dialog
        title={candidate ? `${candidate.name} 정보 수정` : "외부 인력 후보 등록"}
        onClose={close}
        testId="candidate-form-dialog"
        closable
      >
        <form
          ref={formRef}
          className="candidate-form"
          data-testid="candidate-form"
          data-screen-id={candidate ? "UI-33-M2" : "UI-33-M1"}
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          {resume && (
            <p
              className="candidate-resume-result"
              role="status"
              data-testid="candidate-resume-result"
            >
              {resume.text}
            </p>
          )}
          {extracting && (
            <p className="candidate-resume-result" role="status">
              불러오는 중…{" "}
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="candidate-resume-cancel"
                onClick={cancelExtraction}
              >
                취소
              </button>
            </p>
          )}
          <div className="candidate-form-grid">
            {field(
              "name",
              "이름",
              <input
                {...attrs("name")}
                ref={nameRef}
                maxLength={50}
                value={draft.name}
                onChange={(event) => change("name", event.target.value)}
              />,
            )}
            {select("sourceCode", "CAND_SRC", "출처")}
          </div>
          <div className="candidate-form-grid">
            {select("employeeTypeCode", "EMP_TYPE", "구분")}
            {draft.employeeTypeCode === "OS" ? (
              field(
                "companyId",
                "소속 업체",
                <select
                  {...attrs("companyId")}
                  value={draft.companyId || ""}
                  onChange={(event) =>
                    change("companyId", event.target.value || null)
                  }
                >
                  <option value="">협력사를 고르세요</option>
                  {options.companies.map((company) => (
                    <option key={company.clientId} value={company.clientId}>
                      {company.clientName}
                    </option>
                  ))}
                </select>,
                {
                  help: "거래처에 협력사로 등록된 업체만 나옵니다. 없으면 「기준정보 › 거래처·영업 파트」에서 먼저 등록하세요.",
                },
              )
            ) : (
              <div />
            )}
          </div>
          <div className="candidate-form-grid">
            {field(
              "phone",
              "전화번호",
              <input
                {...attrs("phone")}
                type="tel"
                inputMode="tel"
                maxLength={20}
                placeholder="010-1234-5678"
                value={draft.phone || ""}
                onChange={(event) => change("phone", event.target.value)}
              />,
              { required: false },
            )}
            {field(
              "email",
              "이메일",
              <input
                {...attrs("email")}
                type="email"
                maxLength={100}
                placeholder="name@example.com"
                value={draft.email || ""}
                onChange={(event) => change("email", event.target.value)}
              />,
              { required: false },
            )}
          </div>
          <div className="candidate-field">
            <label className="candidate-consent" htmlFor="candidate-privacyAgreement">
              <input
                {...attrs("privacyAgreement")}
                className="candidate-check"
                type="checkbox"
                checked={draft.privacyAgreement}
                onChange={(event) =>
                  change("privacyAgreement", event.target.checked)
                }
              />{" "}
              후보에게 개인정보(전화번호·이메일) 수집·이용 동의를 받았습니다{" "}
              <span className="candidate-cap">(전화번호·이메일을 넣으면 필수)</span>
            </label>
            {errors.privacyAgreement && (
              <small
                className="field-error"
                role="alert"
                data-testid="candidate-error-privacyAgreement"
              >
                {errors.privacyAgreement}
              </small>
            )}
          </div>
          <div className="candidate-form-grid">
            {select("primaryJobCode", "JOB", "주 직무")}
            {select("secondaryJobCode", "JOB", "보조 직무")}
          </div>
          <div className="candidate-form-grid">
            {field(
              "skills",
              "기술",
              <input
                {...attrs("skills")}
                maxLength={100}
                placeholder="예: Java, Spring, Oracle"
                value={skillText}
                onChange={(event) => {
                  setSkillText(event.target.value);
                  change("skills", event.target.value.split(","));
                }}
              />,
              {
                help: "쉼표(,)로 구분해 여러 개 적습니다. 개발자 풀의 보유 스킬과 같습니다.",
              },
            )}
            {select("gradeCode", "GRADE", "등급", { extra: careerHint })}
          </div>
          <div className="candidate-form-grid">
            {field(
              "desiredMonthlyRate",
              "희망 단가(원/월)",
              <input
                {...attrs("desiredMonthlyRate")}
                className="ls-in candidate-money"
                inputMode="numeric"
                value={money(draft.desiredMonthlyRate)}
                onChange={(event) => {
                  const plain = event.target.value.replace(/\D/g, "").slice(0, 15);
                  change(
                    "desiredMonthlyRate",
                    plain === "" ? Number.NaN : Number(plain),
                  );
                }}
              />,
            )}
            {field(
              "availableDate",
              "투입 가능일",
              <input
                {...attrs("availableDate")}
                className="ls-in candidate-date"
                type="date"
                value={draft.availableDate}
                onChange={(event) =>
                  change("availableDate", event.target.value)
                }
              />,
            )}
          </div>
          <div className="candidate-form-grid">
            {[
              ["homeAreaCode", "거주지(시·도)"],
              ["homeAddress", "거주지(시·군·구)"],
            ].map(([key, label], level) =>
              field(
                key,
                label,
                <select
                  id={`candidate-area-${level}`}
                  className="ls-in"
                  data-testid={`candidate-area-${level}`}
                  aria-invalid={!!errors[key]}
                  disabled={areaBusy || disabled || (level === 1 && !location[0])}
                  value={location[level]}
                  onChange={(event) =>
                    void selectArea(level, event.target.value)
                  }
                >
                  <option value="">
                    {level === 1 && !location[0]
                      ? "시·도를 먼저 고르세요"
                      : "선택 안 함"}
                  </option>
                  {(level === 0 ? roots : children).map((area) => (
                    <option key={area.areaCode} value={area.areaCode}>
                      {area.areaName}
                    </option>
                  ))}
                </select>,
                { required: false, htmlFor: `candidate-area-${level}` },
              ),
            )}
          </div>
          {areaError && (
            <p role="alert" className="candidate-error">
              {areaError}{" "}
              <button
                type="button"
                className="ls-btn ls-btn-sm"
                data-testid="candidate-area-retry"
                onClick={() => area.retry(currentDraft.current.homeAreaCode)}
              >
                다시 시도
              </button>
            </p>
          )}
          {field(
            "memo",
            "메모",
            <>
              <textarea
                {...attrs("memo")}
                maxLength={500}
                rows={3}
                value={draft.memo || ""}
                onChange={(event) => {
                  setMemoLength([...event.target.value].length);
                  change("memo", event.target.value);
                }}
              />
              <span className="candidate-counter" data-testid="candidate-memo-counter">
                {memoLength}/500
              </span>
            </>,
            {
              required: false,
              help: "후보에 대해 계속 알아 둘 내용(예: 3월까지 다른 프로젝트 마무리). 연락할 때마다 남기는 내용은 [연락 기록]에 적습니다.",
            },
          )}
          {error && (
            <p role="alert" className="candidate-error" data-testid="candidate-form-error">
              {error}
            </p>
          )}
          <div className="app-actions candidate-form-foot">
            {!candidate && (
              <>
                <button
                  type="button"
                  className="ls-btn candidate-resume-button"
                  data-testid="candidate-resume-button"
                  disabled={disabled}
                  onClick={() => {
                    setError("");
                    fileRef.current?.click();
                  }}
                >
                  이력서 등록
                </button>
                <input
                  ref={fileRef}
                  id="candidate-resume"
                  data-testid="candidate-resume"
                  type="file"
                  hidden
                  accept={RESUME_ACCEPT}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    event.target.value = "";
                    void extract(file);
                  }}
                />
              </>
            )}
            <button
              type="button"
              className="ls-btn"
              data-testid="candidate-form-cancel"
              onClick={close}
              disabled={busy}
            >
              취소
            </button>
            <button
              type="submit"
              className="ls-btn ls-btn-p"
              data-testid="candidate-form-save"
              disabled={disabled || areaBusy || !!areaError}
            >
              {candidate ? "저장" : "등록"}
            </button>
          </div>
        </form>
      </Dialog>
      {leaving && (
        <InlineConfirmation
          title="저장하지 않고 나갈까요?"
          description="바꾼 내용이 사라집니다."
          onCancel={() => setLeaving(false)}
          onProceed={onCancel}
          testId="candidate-discard"
        />
      )}
    </>
  );
}

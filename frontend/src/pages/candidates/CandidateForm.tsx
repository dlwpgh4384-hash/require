import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog, InlineConfirmation } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import {
  developerFailure,
  getCommonDeveloperAreas,
  type DeveloperArea,
} from "../developers/developers";
import {
  CandidateRequestError,
  candidateDefaults,
  mergeCandidateExtraction,
  postCommonCandidate,
  putCommonCandidate,
  postCommonCandidateResumeExtraction,
  type Candidate,
  type CandidateInput,
  type CandidateOptions,
  type CandidateExtraction,
} from "./candidates";
import "./candidates.css";

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
  const [draft, setDraft] = useState<CandidateInput>(() =>
    candidate ? { ...candidate } : candidateDefaults(options),
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
  const [areas, setAreas] = useState<DeveloperArea[][]>([[], []]);
  const [location, setLocation] = useState(["", ""]);
  const [areaBusy, setAreaBusy] = useState(true);
  const [areaError, setAreaError] = useState("");
  const [areaRevision, setAreaRevision] = useState(0);
  const areaRequest = useRef(0);
  const [extracting, setExtracting] = useState(false);
  const extractionPending = useRef(false);
  const [extraction, setExtraction] = useState<{
    result: CandidateExtraction;
    applied: string[];
  } | null>(null);
  const currentDraft = useRef(draft);
  currentDraft.current = draft;
  useEffect(() => {
    setDirty("UI-33", dirty);
    return () => setDirty("UI-33", false);
  }, [dirty, setDirty]);
  useEffect(() => {
    let active = true;
    const load = async () => {
      setAreaBusy(true);
      setAreaError("");
      try {
        const root = (await getCommonDeveloperAreas()).items;
        const code = draft.homeAreaCode;
        const parent = code
          ? root.find((area) => area.areaCode.slice(0, 2) === code.slice(0, 2))
              ?.areaCode || ""
          : "";
        const children = parent
          ? (await getCommonDeveloperAreas(parent)).items
          : [];
        const selected =
          children.find(
            (area) => area.areaCode.slice(0, 5) === code?.slice(0, 5),
          )?.areaCode || "";
        if (active) {
          setAreas([root, children]);
          setLocation([parent, selected]);
        }
      } catch (reason) {
        if (active) setAreaError(developerFailure(reason));
      } finally {
        if (active) setAreaBusy(false);
      }
    };
    void load();
    return () => {
      active = false;
      ++areaRequest.current;
    };
  }, [areaRevision, draft.homeAreaCode]);
  function change<K extends keyof CandidateInput>(
    key: K,
    value: CandidateInput[K],
  ) {
    setDraft((old) => ({ ...old, [key]: value }));
    setErrors((old) => {
      const next = { ...old };
      delete next[key];
      return next;
    });
  }
  const close = () => {
    if (!pending.current && !extractionPending.current)
      dirty ? setLeaving(true) : onCancel();
  };
  async function selectArea(level: number, code: string) {
    const request = ++areaRequest.current;
    const next = level === 0 ? [code, ""] : [location[0], code];
    setLocation(next);
    const selected = areas[level].find((area) => area.areaCode === code);
    change("homeAreaCode", code || next[0] || null);
    change("homeAddress", selected?.areaName || null);
    if (level !== 0) return;
    setAreas((old) => [old[0], []]);
    if (!code) return;
    setAreaBusy(true);
    setAreaError("");
    try {
      const response = await getCommonDeveloperAreas(code);
      if (request === areaRequest.current)
        setAreas((old) => [old[0], response.items]);
    } catch (reason) {
      if (request === areaRequest.current)
        setAreaError(developerFailure(reason));
    } finally {
      if (request === areaRequest.current) setAreaBusy(false);
    }
  }
  async function save() {
    if (pending.current || extractionPending.current || areaBusy || !!areaError)
      return;
    const invalid: Record<string, string> = {};
    for (const key of [
      "name",
      "employeeTypeCode",
      "primaryJobCode",
      "gradeCode",
      "availableDate",
      "sourceCode",
    ] as const)
      if (!draft[key]?.trim()) invalid[key] = "입력 값을 확인해 주세요.";
    if (
      !Number.isSafeInteger(draft.desiredMonthlyRate) ||
      draft.desiredMonthlyRate < 0 ||
      draft.desiredMonthlyRate > 999999999999999
    )
      invalid.desiredMonthlyRate = "입력 값을 확인해 주세요.";
    if (!draft.skills.some((value) => value.trim()))
      invalid.skills = "입력 값을 확인해 주세요.";
    if (draft.employeeTypeCode === "OS" && !draft.companyId)
      invalid.companyId = "입력 값을 확인해 주세요.";
    if ((draft.phone?.trim() || draft.email?.trim()) && !draft.privacyAgreement)
      invalid.privacyAgreement = "입력 값을 확인해 주세요.";
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    pending.current = true;
    setBusy(true);
    setError("");
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
      if (reason instanceof CandidateRequestError)
        setErrors(
          Object.fromEntries(
            reason.errors.map((item) => [item.field, item.message]),
          ),
        );
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  async function extract(file?: File) {
    if (!file || pending.current || extractionPending.current) return;
    extractionPending.current = true;
    setExtracting(true);
    setError("");
    try {
      const result = await postCommonCandidateResumeExtraction(file);
      const merged = mergeCandidateExtraction(currentDraft.current, result);
      setDraft(merged.input);
      setSkillText(merged.input.skills.join(", "));
      setExtraction({ result, applied: merged.applied });
    } catch (reason) {
      setError(developerFailure(reason));
    } finally {
      extractionPending.current = false;
      setExtracting(false);
    }
  }
  function field(key: keyof CandidateInput, label: string, control: ReactNode) {
    const evidence = extraction?.applied.includes(key)
      ? extraction.result.fields.find((item) => item.field === key)
      : null;
    return (
      <div className="candidate-field" key={key}>
        <label htmlFor={`candidate-${key}`}>{label}</label>
        {control}
        {evidence && (
          <small>
            {evidence.estimated
              ? `⚠ 확인해 주세요: ${evidence.originalText || ""}`
              : "✓ 이력서에서 가져옴"}
          </small>
        )}
        {errors[key] && (
          <small className="field-error" role="alert">
            {errors[key]}
          </small>
        )}
      </div>
    );
  }
  const attrs = (key: keyof CandidateInput) => ({
    id: `candidate-${key}`,
    "data-testid": `candidate-${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`,
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
  ) {
    let items = options.codes[group] || [];
    if (key === "employeeTypeCode")
      items = items.filter((item) => ["FL", "OS"].includes(item.code));
    return field(
      key,
      label,
      <select
        {...attrs(key)}
        value={draft[key] || ""}
        onChange={(event) => change(key, event.target.value)}
      >
        <option value="">선택</option>
        {draft[key] && !items.some((item) => item.code === draft[key]) && (
          <option value={draft[key] || ""}>{draft[key]}</option>
        )}
        {items.map((item) => (
          <option key={item.code} value={item.code}>
            {item.codeName}
          </option>
        ))}
      </select>,
    );
  }
  return (
    <>
      <Dialog
        title={candidate ? "후보 정보 수정" : "후보 등록"}
        onClose={close}
        testId="candidate-form-dialog"
        closable
      >
        <form
          className="candidate-form"
          data-testid="candidate-form"
          data-screen-id={candidate ? "UI-33-M2" : "UI-33-M1"}
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          {!candidate && (
            <div className="candidate-resume">
              <label htmlFor="candidate-resume">이력서 등록</label>
              <input
                id="candidate-resume"
                data-testid="candidate-resume"
                type="file"
                accept=".pdf,.doc,.docx,.ppt,.pptx,.hwp,.txt"
                disabled={busy || extracting}
                onChange={(event) => {
                  void extract(event.target.files?.[0]);
                  event.target.value = "";
                }}
              />
              {extracting && <p role="status">불러오는 중…</p>}
              {extraction && (
                <p>
                  {extraction.result.fileName} · 채운 칸{" "}
                  {extraction.applied.length}개 · 확실{" "}
                  {
                    extraction.result.fields.filter(
                      (item) =>
                        extraction.applied.includes(item.field) &&
                        !item.estimated,
                    ).length
                  }
                  개 · 추정{" "}
                  {
                    extraction.result.fields.filter(
                      (item) =>
                        extraction.applied.includes(item.field) &&
                        item.estimated,
                    ).length
                  }
                  개. 이력서 원본은 보관하지 않습니다. 확인해 주세요.
                </p>
              )}
              {extraction?.result.careerYears != null && (
                <p>경력 연수: {extraction.result.careerYears}</p>
              )}
            </div>
          )}
          <div className="candidate-form-grid">
            {field(
              "name",
              "이름*",
              <input
                {...attrs("name")}
                maxLength={50}
                value={draft.name}
                onChange={(event) => change("name", event.target.value)}
              />,
            )}
            {select("sourceCode", "CAND_SRC", "출처*")}
            {select("employeeTypeCode", "EMP_TYPE", "구분*")}
            {draft.employeeTypeCode === "OS" ? (
              field(
                "companyId",
                "소속 업체*",
                <>
                  <select
                    {...attrs("companyId")}
                    value={draft.companyId || ""}
                    onChange={(event) =>
                      change("companyId", event.target.value)
                    }
                  >
                    <option value="">선택</option>
                    {options.companies.map((company) => (
                      <option key={company.clientId} value={company.clientId}>
                        {company.clientName}
                      </option>
                    ))}
                  </select>
                  {!options.companies.length && (
                    <small>거래처에서 먼저 등록해 주세요.</small>
                  )}
                </>,
              )
            ) : (
              <div />
            )}
            {field(
              "phone",
              "전화번호",
              <input
                {...attrs("phone")}
                type="tel"
                maxLength={20}
                pattern="[0-9-]*"
                value={draft.phone || ""}
                onChange={(event) => change("phone", event.target.value)}
              />,
            )}
            {field(
              "email",
              "이메일",
              <input
                {...attrs("email")}
                type="email"
                maxLength={100}
                value={draft.email || ""}
                onChange={(event) => change("email", event.target.value)}
              />,
            )}
            <div className="candidate-span">
              {field(
                "privacyAgreement",
                "개인정보 수집·이용 동의",
                <input
                  {...attrs("privacyAgreement")}
                  type="checkbox"
                  checked={draft.privacyAgreement}
                  onChange={(event) =>
                    change("privacyAgreement", event.target.checked)
                  }
                />,
              )}
            </div>
            {select("primaryJobCode", "JOB", "주 직무*")}
            {select("secondaryJobCode", "JOB", "보조 직무")}
            {field(
              "skills",
              "기술*",
              <input
                {...attrs("skills")}
                value={skillText}
                onChange={(event) => {
                  setSkillText(event.target.value);
                  change("skills", event.target.value.split(","));
                }}
              />,
            )}
            {select("gradeCode", "GRADE", "등급*")}
            {field(
              "desiredMonthlyRate",
              "희망 단가*(원/월)",
              <input
                {...attrs("desiredMonthlyRate")}
                type="number"
                min="0"
                max="999999999999999"
                step="1"
                value={
                  Number.isNaN(draft.desiredMonthlyRate)
                    ? ""
                    : draft.desiredMonthlyRate
                }
                onChange={(event) =>
                  change(
                    "desiredMonthlyRate",
                    event.target.value === ""
                      ? Number.NaN
                      : Number(event.target.value),
                  )
                }
              />,
            )}
            {field(
              "availableDate",
              "투입 가능일*",
              <input
                {...attrs("availableDate")}
                type="date"
                value={draft.availableDate}
                onChange={(event) =>
                  change("availableDate", event.target.value)
                }
              />,
            )}
            {["시·도", "시·군·구"].map((label, level) => (
              <div className="candidate-field" key={label}>
                <label htmlFor={`candidate-area-${level}`}>{label}</label>
                <select
                  id={`candidate-area-${level}`}
                  data-testid={`candidate-area-${level}`}
                  disabled={
                    areaBusy ||
                    busy ||
                    extracting ||
                    (level === 1 && !location[0])
                  }
                  value={location[level]}
                  onChange={(event) =>
                    void selectArea(level, event.target.value)
                  }
                >
                  <option value="">선택</option>
                  {areas[level].map((area) => (
                    <option key={area.areaCode} value={area.areaCode}>
                      {area.areaName}
                    </option>
                  ))}
                </select>
              </div>
            ))}
            <div className="candidate-span">
              {field(
                "memo",
                "메모",
                <textarea
                  {...attrs("memo")}
                  maxLength={500}
                  value={draft.memo || ""}
                  onChange={(event) => change("memo", event.target.value)}
                />,
              )}
            </div>
          </div>
          {(errors.homeAreaCode || errors.homeAddress) && (
            <p className="field-error" role="alert">
              {errors.homeAreaCode || errors.homeAddress}
            </p>
          )}
          {areaError && (
            <p role="alert">
              {areaError}
              <button
                type="button"
                data-testid="candidate-area-retry"
                onClick={() => setAreaRevision((value) => value + 1)}
              >
                다시 시도
              </button>
            </p>
          )}
          {error && <p role="alert">{error}</p>}
          <footer>
            <button
              type="button"
              className="ls-btn"
              data-testid="candidate-form-cancel"
              onClick={close}
              disabled={busy || extracting}
            >
              취소
            </button>
            <button
              className="ls-btn ls-btn-p"
              data-testid="candidate-form-save"
              disabled={busy || extracting || areaBusy || !!areaError}
            >
              {candidate ? "저장" : "등록"}
            </button>
          </footer>
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

import { useCallback, useEffect, useRef, useState, type InputHTMLAttributes, type ReactNode } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { Banner } from "../../shared/list";
import { usePreferenceBusinessShortcuts } from "../userpreferences/UserPreferenceTools";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useScreenTitleOverride } from "../../shared/layout/ScreenTitleOverride";
import {
  DeveloperRequestError,
  developerFailure,
  directLabel,
  getCommonDeveloper,
  postCommonDeveloper,
  putCommonDeveloper,
  type Developer,
  type DeveloperInput,
  type DeveloperOptions,
} from "./developers";
import { AreaSelect, areaLevelLocked, useAreaPicker } from "../../shared/area/AreaPicker";
import "./developers.css";

const SELF = "__self";
/** 시안 문구: 「직원구분을 입력해 주세요.」「주 직무를 입력해 주세요.」(받침에 따라 을/를) */
const enter = (label: string) => {
  const code = label.charCodeAt(label.length - 1) - 0xac00;
  const particle = code < 0 || code > 11171 ? "을(를)" : code % 28 === 0 ? "를" : "을";
  return `${label}${particle} 입력해 주세요.`;
};
const labels: Record<string, string> = {
  name: "이름",
  employeeTypeCode: "직원구분",
  organization: "소속",
  primaryJobCode: "주 직무",
  salesPartId: "영업 파트",
  directClassCode: "직접/간접",
};
const required = ["name", "employeeTypeCode", "organizationId", "primaryJobCode", "salesPartId", "directClassCode"] as const;
const digits = (value: string) => value.replace(/[^0-9]/g, "");
const money = (value: number | null | undefined) => (value == null ? "" : value.toLocaleString("ko-KR"));

/** 시안 개발자 등록·수정(폼 화면): 제목줄 [‹ 목록], 카드 「기본 정보」·「금액」, 아래 [취소][저장]. */
export function DeveloperForm({
  developer,
  options,
  managementNumbers = [],
  onCancel,
  onSaved,
}: {
  developer?: Developer;
  options: DeveloperOptions;
  /** 시안: 관리번호 중복은 칸을 떠날 때 이미 아는 목록으로 먼저 본다(저장 때 서버가 다시 확인). */
  managementNumbers?: string[];
  onCancel: () => void;
  onSaved: (developer: Developer) => void;
}) {
  const skillName = (code: string) => options.codes.SKILL?.find((c) => c.code === code)?.codeName || code;
  const [draft, setDraft] = useState(() => ({
    name: developer?.name || "",
    managementNumber: developer?.managementNumber || "",
    employeeTypeCode: developer?.employeeTypeCode || "",
    organization: developer ? (developer.organizationId ? developer.organizationId : SELF) : "",
    gradeCode: developer?.gradeCode || "",
    primaryJobCode: developer?.primaryJobCode || "",
    secondaryJobCode: developer?.secondaryJobCode || "",
    skills: (developer?.skillCodes || []).map(skillName).join(", "),
    salesPartId: developer?.salesPartId || "",
    hireDate: developer?.hireDate || "",
    // 시안·명세: 새 등록은 직위 「팀원」, 직접/간접 「직접」이 처음 값
    positionCode: developer?.positionCode || options.codes.JBPS?.find((c) => c.codeName === "팀원")?.code || "",
    directClassCode: developer?.directClassCode || options.codes.DRCT_CLS?.find((c) => c.codeName === "직접")?.code || "",
    monthlyRate: money(developer?.monthlyRate),
    monthlyPay: money(developer?.monthlyPay),
    homeAreaCode: developer?.homeAreaCode || null as string | null,
  }));
  type Draft = typeof draft;
  const original = useRef(JSON.stringify(draft));
  const dirty = original.current !== JSON.stringify(draft);
  const setDirty = useScreenDirty();
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  /** FR-0106: 저장 때 돌려보낼 수정 시각. 409 뒤 [최신 버전 불러오기]로 새로 받는다(입력은 그대로). */
  const version = useRef(developer?.modifiedAt ?? null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const formRef = useRef<HTMLElement>(null);
  // 거주지 시·도 → 시·군·구 → 동(공통 행정구역 고르기, 프로젝트·영업 기회 근무지 지역과 같음)
  const area = useAreaPicker(developer?.homeAreaCode);
  const areaBusy = area.busy;
  const areaError = area.error;
  useEffect(() => {
    setDirty("UI-02", dirty);
    return () => setDirty("UI-02", false);
  }, [dirty, setDirty]);
  const leave = () => (dirty ? setLeaving(true) : onCancel());
  // 시안 제목 문구 그대로: 등록 「개발자 등록」, 수정 「개발자 등록 수정」
  useScreenTitleOverride("UI-02", developer ? "개발자 등록 수정" : "개발자 등록", leave);
  const change = <K extends keyof Draft>(key: K, value: Draft[K]) => {
    setDraft((d) => ({ ...d, [key]: value }));
    setErrors((e) => {
      if (!e[key as string]) return e;
      const next = { ...e };
      delete next[key as string];
      return next;
    });
  };
  /** 고르는 칸: 바꾸면 바로 검사(시안 change 이벤트의 validate). */
  const pick = (key: keyof Draft, value: string) => {
    change(key, value as never);
    check(key, { ...draft, [key]: value });
  };
  const selectArea = (level: number, value: string) => change("homeAreaCode", area.select(level, value));
  const serverErrors = error instanceof DeveloperRequestError ? error.errors : [];
  const fieldError = (key: string) =>
    errors[key] || serverErrors.find((e) => e.field === (key === "organization" ? "organizationId" : key === "skills" ? "skillCodes" : key))?.message;
  const fld = (key: string, label: string, isRequired: boolean, control: ReactNode, help?: ReactNode) => (
    <div className="developer-fld" key={key}>
      <label className={`developer-fl${isRequired ? " req" : ""}`} htmlFor={`developer-${key}`}>
        {label}
      </label>
      <div className="developer-fv">
        {control}
        {help && <span className="developer-help">{help}</span>}
        {fieldError(key) && (
          <small role="alert" className="field-error">
            {fieldError(key)}
          </small>
        )}
      </div>
    </div>
  );
  const codeSelect = (key: keyof Draft, label: string, group: string, isRequired = false, help?: string) =>
    fld(
      key,
      label,
      isRequired,
      <select
        id={`developer-${key}`}
        data-testid={`developer-${key}`}
        className="ls-in"
        aria-invalid={!!fieldError(key)}
        value={String(draft[key] || "")}
        disabled={busy}
        onChange={(e) => pick(key, e.target.value)}
      >
        <option value="">선택</option>
        {!!draft[key] && !options.codes[group]?.some((c) => c.code === draft[key]) && (
          <option value={String(draft[key])} disabled>
            {String(draft[key])} (기존값·새 선택 불가)
          </option>
        )}
        {options.codes[group]?.map((c) => (
          <option key={c.code} value={c.code}>
            {group === "DRCT_CLS" ? directLabel(c.codeName) : c.codeName}
          </option>
        ))}
      </select>,
      help,
    );
  /** 시안 validate(k): 한 칸의 오류 문구(없으면 ""). */
  const message = (field: string, d: Draft): string => {
    if (field === "name") return d.name.trim() ? "" : enter(labels.name);
    if (field === "managementNumber") {
      const number = d.managementNumber.trim();
      if (developer || !number) return "";
      if (!/^[A-Za-z0-9-]{1,20}$/.test(number)) return "관리번호는 영문·숫자·하이픈으로 20자 이내로 적어 주세요.";
      return managementNumbers.includes(number) ? "이미 등록된 관리번호입니다." : "";
    }
    if (field === "skills") {
      const unknown = d.skills
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
        .filter((name) => !options.codes.SKILL?.some((c) => c.codeName === name || c.code === name));
      return unknown.length ? `공통코드 「스킬」에 없는 이름입니다: ${unknown.join(", ")}` : "";
    }
    const key = field === "organization" ? "organizationId" : field;
    if ((required as readonly string[]).includes(key) && !d[field as keyof Draft]) return enter(labels[field]);
    return "";
  };
  // 시안: 글자 칸은 칸을 떠날 때, 고르는 칸·날짜는 바꿀 때 바로 그 칸 아래에 문구를 보인다.
  const check = (field: string, d: Draft = draft) => {
    const found = message(field, d);
    setErrors((e) => {
      if ((e[field] || "") === found) return e;
      const next = { ...e };
      if (found) next[field] = found;
      else delete next[field];
      return next;
    });
  };
  const validate = () => {
    const found: Record<string, string> = {};
    const fields = ["name", "managementNumber", ...required.map((k) => (k === "organizationId" ? "organization" : k)), "skills"];
    for (const field of fields) {
      const m = message(field, draft);
      if (m) found[field] = m;
    }
    setErrors(found);
    if (Object.keys(found).length) {
      // 공통 기능: 첫 오류 칸으로 커서를 옮긴다
      setTimeout(() => formRef.current?.querySelector<HTMLElement>("[aria-invalid='true']")?.focus(), 0);
      return false;
    }
    return true;
  };
  const payload = (): DeveloperInput => ({
    managementNumber: developer ? undefined : draft.managementNumber.trim() || undefined,
    name: draft.name,
    employeeTypeCode: draft.employeeTypeCode,
    organizationId: draft.organization === SELF ? "" : draft.organization,
    gradeCode: draft.gradeCode || null,
    primaryJobCode: draft.primaryJobCode,
    secondaryJobCode: draft.secondaryJobCode || null,
    positionCode: draft.positionCode || undefined,
    directClassCode: draft.directClassCode,
    salesPartId: draft.salesPartId,
    hireDate: draft.hireDate || null,
    monthlyRate: draft.monthlyRate ? Number(digits(draft.monthlyRate)) : null,
    monthlyPay: draft.monthlyPay ? Number(digits(draft.monthlyPay)) : null,
    skillCodes: [
      ...new Set(
        draft.skills
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean)
          .map((name) => options.codes.SKILL?.find((c) => c.codeName === name || c.code === name)?.code || name),
      ),
    ],
    homeAreaCode: draft.homeAreaCode,
  });
  const save = async () => {
    if (saving.current || areaBusy || areaError || !validate()) return;
    saving.current = true;
    setBusy(true);
    setError(null);
    try {
      const saved = developer
        ? await putCommonDeveloper(developer.developerId, payload(), version.current)
        : await postCommonDeveloper(payload());
      original.current = JSON.stringify(draft);
      setDirty("UI-02", false);
      onSaved(saved);
    } catch (reason) {
      setError(reason);
    } finally {
      saving.current = false;
      setBusy(false);
    }
  };
  const currentSave = useRef(save);
  currentSave.current = save;
  // 공통 기능 Ctrl+S: 공통 단축키 훅(숨은 화면 탭이면 넘김)
  const saveShortcut = useCallback(() => void currentSave.current(), []);
  usePreferenceBusinessShortcuts(
    { onSave: saveShortcut, scope: formRef, dialogTarget: formRef },
    !leaving,
  );
  const conflict = error instanceof DeveloperRequestError && error.status === 409;
  const reloadVersion = () => {
    if (!developer) return;
    getCommonDeveloper(developer.developerId)
      .then((latest) => {
        version.current = latest.modifiedAt ?? null;
        setError(null);
      })
      .catch((reason) => setError(reason));
  };
  const textInput = (key: "name" | "managementNumber" | "skills", max: number, extra?: Partial<InputHTMLAttributes<HTMLInputElement>>) => (
    <input
      id={`developer-${key}`}
      data-testid={`developer-${key === "managementNumber" ? "management-number" : key}`}
      className="ls-in"
      aria-invalid={!!fieldError(key)}
      value={draft[key]}
      maxLength={max}
      disabled={busy}
      onChange={(e) => change(key, e.target.value)}
      onBlur={() => check(key)}
      {...extra}
    />
  );
  const moneyInput = (key: "monthlyRate" | "monthlyPay") => (
    <span className="developer-row">
      <input
        id={`developer-${key}`}
        data-testid={`developer-${key}`}
        className="ls-in developer-money"
        inputMode="numeric"
        value={draft[key]}
        disabled={busy}
        onChange={(e) => {
          const plain = digits(e.target.value);
          change(key, plain ? Number(plain).toLocaleString("ko-KR") : "");
        }}
      />
      <span className="developer-unit">원</span>
    </span>
  );
  const areaSelect = (level: number, key: string, label: string, help: string) =>
    fld(
      key,
      label,
      false,
      <AreaSelect
        picker={area}
        level={level}
        id={`developer-${key}`}
        testId={`developer-area-${level}`}
        disabled={busy || areaLevelLocked(area, level)}
        onPick={selectArea}
      />,
      help,
    );
  return (
    <section ref={formRef} className="developer-form" data-testid="developer-form" data-screen-id="UI-02">
      <Banner
        kind="info"
        icon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01" />
          </svg>
        }
      >
        보유 스킬·거주지는 이 화면에서만 입력합니다(「내 정보」에서는 고치지 않음). 거주지는 시·도, 시·군·구, 동까지 받습니다(번지는 받지 않음). 연락처는 받지 않습니다.
      </Banner>
      <section className="developer-card">
        <h2>기본 정보</h2>
        <div className="developer-fgrid">
          {fld("name", "이름", true, textInput("name", 50))}
          {fld(
            "managementNumber",
            "관리번호",
            false,
            textInput("managementNumber", 20, { readOnly: !!developer }),
            developer
              ? "관리번호는 바꿀 수 없습니다. 잘못 넣었으면 사용 중지 후 새로 등록하세요."
              : "비우면 저장할 때 K-0001처럼 자동으로 붙습니다. 직접 넣으면 영문·숫자·하이픈 20자 이내이고, 등록한 뒤에는 바꿀 수 없습니다",
          )}
          {codeSelect("employeeTypeCode", "직원구분", "EMP_TYPE", true)}
          {fld(
            "organization",
            "소속",
            true,
            <select
              id="developer-organization"
              data-testid="developer-organization"
              className="ls-in"
              aria-invalid={!!fieldError("organization")}
              value={draft.organization}
              disabled={busy}
              onChange={(e) => pick("organization", e.target.value)}
            >
              <option value="">선택</option>
              <option value={SELF}>자사</option>
              {options.organizations
                .filter((o) => o.useYn === "Y" || o.id === developer?.organizationId)
                .map((o) => (
                  <option key={o.id} value={o.id} disabled={o.useYn !== "Y"}>
                    {o.name}
                    {o.useYn !== "Y" ? " (중지)" : ""}
                  </option>
                ))}
            </select>,
          )}
          {codeSelect("gradeCode", "등급", "GRADE")}
          {codeSelect("primaryJobCode", "주 직무", "JOB", true)}
          {codeSelect("secondaryJobCode", "보조 직무", "JOB", false, "선택. AI 추천에서 직무 일치로 함께 봅니다.")}
          {fld(
            "skills",
            "보유 스킬",
            false,
            textInput("skills", 100),
            "공통코드 「스킬」(UI-27)에 있는 이름을 쉼표(,)로 구분해 여러 개 적습니다. AI 인력 추천의 스킬 점수에 씁니다.",
          )}
          {areaSelect(0, "homeSido", "거주지(시·도)", "추천의 근무지 점수는 시·도가 근무지와 같은지로 봅니다.")}
          {areaSelect(1, "homeSgg", "거주지(시·군·구)", "시·도를 먼저 고르면 그 시·도의 시·군·구가 나옵니다. 선택.")}
          {areaSelect(2, "homeDong", "거주지(동)", "시·군·구를 먼저 고르면 그 시·군·구의 읍·면·동이 나옵니다. 선택.")}
          {fld(
            "salesPartId",
            "영업 파트",
            true,
            <select
              id="developer-salesPartId"
              data-testid="developer-salesPartId"
              className="ls-in"
              aria-invalid={!!fieldError("salesPartId")}
              value={draft.salesPartId}
              disabled={busy}
              onChange={(e) => pick("salesPartId", e.target.value)}
            >
              <option value="">선택</option>
              {options.parts
                .filter((p) => p.useYn === "Y" || p.id === developer?.salesPartId)
                .map((p) => (
                  <option key={p.id} value={p.id} disabled={p.useYn !== "Y"}>
                    {p.name}
                  </option>
                ))}
            </select>,
            "이 사람 이익금이 어느 파트 정산으로 갈지 정합니다. 본사 소속·관리직이면 「본사(직접 영업)」(파트 정산에서 빠짐). 배정할 때 프로젝트 파트와 다르면 경고가 뜹니다.",
          )}
          {fld(
            "hireDate",
            "입사일",
            false,
            <input
              id="developer-hireDate"
              data-testid="developer-hireDate"
              className="ls-in developer-date"
              type="date"
              value={draft.hireDate}
              disabled={busy}
              onChange={(e) => change("hireDate", e.target.value)}
            />,
          )}
          {codeSelect("positionCode", "직위", "JBPS", false, "공통코드(UI-27) 「직위」. 팀장·그룹장 수당 계산에 씁니다.")}
          {codeSelect(
            "directClassCode",
            "직접/간접",
            "DRCT_CLS",
            true,
            "관리직만 간접. 간접 인력은 투입 배정·인력 운영 보드·가동률·개인 원가 화면에서 빠지고 인건비는 판관비로 잡힙니다.",
          )}
        </div>
      </section>
      <section className="developer-card">
        <h2>금액</h2>
        <div className="developer-fgrid">
          {fld(
            "monthlyRate",
            "월 단가",
            false,
            moneyInput("monthlyRate"),
            "PM에게는 보이지 않습니다(관리자·경영진·재무·파트장만). 수정하면 저장한 달부터 적용되고, 언제 바뀌었는지는 [변경 이력]에 남습니다",
          )}
          {fld(
            "monthlyPay",
            "월 급여·지급액",
            false,
            moneyInput("monthlyPay"),
            "민감 항목이라 관리자·경영진·재무만 보입니다. 수정하면 저장한 달부터 적용되고, 언제 바뀌었는지는 [변경 이력]에 남습니다",
          )}
        </div>
      </section>
      {(areaError || error != null) && (
        <div role="alert" className="developer-error">
          {areaError || (conflict
            ? "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."
            : developerFailure(error))}
          {!areaError && conflict && developer && (
            <button type="button" data-testid="developer-version-refresh" onClick={reloadVersion}>
              최신 버전 불러오기 (입력 유지)
            </button>
          )}
          {areaError && (
            <button type="button" data-testid="developer-area-retry" onClick={() => area.retry()}>
              다시 시도
            </button>
          )}
        </div>
      )}
      <p className="developer-note">
        <span className="developer-bad">*</span> 표시는 꼭 입력해야 하는 항목입니다.
      </p>
      <footer className="developer-form-foot">
        <button type="button" className="ls-btn" data-testid="developer-cancel" disabled={busy} onClick={leave}>
          취소
        </button>
        <button type="button" className="ls-btn ls-btn-p" data-testid="developer-save" disabled={busy || areaBusy} onClick={() => void save()}>
          {busy ? "저장 중…" : "저장"}
        </button>
      </footer>
      {leaving && (
        <Dialog title="저장하지 않은 내용이 있습니다" closable testId="developer-leave-confirm" onClose={() => setLeaving(false)}>
          <p>나가면 입력한 내용이 사라집니다.</p>
          <div className="app-actions">
            <button type="button" data-testid="developer-leave-cancel" onClick={() => setLeaving(false)}>
              취소
            </button>
            <button type="button" className="primary-button" data-testid="developer-leave-go" onClick={onCancel}>
              나가기
            </button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
